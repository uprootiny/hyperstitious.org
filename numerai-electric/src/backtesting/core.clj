(ns backtesting.core
  "Advanced backtesting workbench with reactive data flows and real-time strategy evaluation"
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.core.async :as async :refer [chan go >! <! timeout]]
            [java-time :as jt]
            [tablecloth.api :as tc]))

;; ============================================================================
;; Data Specifications
;; ============================================================================

(s/def ::timestamp inst?)
(s/def ::price (s/and number? pos?))
(s/def ::volume (s/and number? (partial >= 0)))
(s/def ::signal #{:buy :sell :hold})
(s/def ::confidence (s/and number? #(<= 0 % 1)))

(s/def ::ohlcv-bar
  (s/keys :req [::timestamp ::open ::high ::low ::close ::volume]))

(s/def ::trading-signal
  (s/keys :req [::timestamp ::signal ::confidence]
          :opt [::price ::reason]))

(s/def ::position
  (s/keys :req [::symbol ::quantity ::entry-price ::entry-time]
          :opt [::exit-price ::exit-time ::pnl ::status]))

;; ============================================================================
;; Market Data Management
;; ============================================================================

(defprotocol MarketDataProvider
  "Protocol for market data providers"
  (fetch-historical [this symbol start-date end-date])
  (subscribe-realtime [this symbol callback])
  (get-current-price [this symbol]))

(defrecord CSVDataProvider [data-path]
  MarketDataProvider
  (fetch-historical [this symbol start-date end-date]
    (let [file-path (str data-path "/" symbol ".csv")]
      (when (.exists (io/file file-path))
        (->> (csv/read-csv (io/reader file-path))
             (drop 1) ; Skip header
             (map (fn [[timestamp open high low close volume]]
                    {::timestamp (jt/instant timestamp)
                     ::open (Double/parseDouble open)
                     ::high (Double/parseDouble high)
                     ::low (Double/parseDouble low)
                     ::close (Double/parseDouble close)
                     ::volume (Double/parseDouble volume)
                     ::symbol symbol}))
             (filter #(jt/before? start-date (::timestamp %)))
             (filter #(jt/after? end-date (::timestamp %)))))))
  
  (subscribe-realtime [this symbol callback]
    ;; For backtesting, simulate real-time data
    (go
      (let [historical (fetch-historical this symbol (jt/minus (jt/instant) (jt/hours 24)) (jt/instant))]
        (doseq [bar historical]
          (<! (timeout 100)) ; Simulate 100ms delay between bars
          (callback bar)))))
  
  (get-current-price [this symbol]
    (-> (fetch-historical this symbol (jt/minus (jt/instant) (jt/hours 1)) (jt/instant))
        last
        ::close)))

;; ============================================================================
;; Trading Strategy Framework
;; ============================================================================

(defprotocol TradingStrategy
  "Protocol for trading strategies"
  (initialize [this market-data])
  (generate-signal [this market-data current-bar])
  (get-parameters [this])
  (update-parameters [this new-params]))

(defrecord SimpleMovingAverageStrategy [short-window long-window]
  TradingStrategy
  (initialize [this market-data]
    (assoc this ::initialized true))
  
  (generate-signal [this market-data current-bar]
    (let [recent-bars (take long-window (reverse market-data))
          short-ma (/ (reduce + (map ::close (take short-window recent-bars))) short-window)
          long-ma (/ (reduce + (map ::close recent-bars)) long-window)]
      (cond
        (> short-ma long-ma) {::signal :buy ::confidence 0.7 ::timestamp (::timestamp current-bar)}
        (< short-ma long-ma) {::signal :sell ::confidence 0.7 ::timestamp (::timestamp current-bar)}
        :else {::signal :hold ::confidence 0.5 ::timestamp (::timestamp current-bar)})))
  
  (get-parameters [this]
    {:short-window short-window :long-window long-window})
  
  (update-parameters [this {:keys [short-window long-window]}]
    (assoc this :short-window short-window :long-window long-window)))

(defrecord MeanReversionStrategy [lookback-period threshold]
  TradingStrategy
  (initialize [this market-data]
    (assoc this ::initialized true))
  
  (generate-signal [this market-data current-bar]
    (let [recent-bars (take lookback-period (reverse market-data))
          prices (map ::close recent-bars)
          mean-price (/ (reduce + prices) (count prices))
          current-price (::close current-bar)
          deviation (/ (- current-price mean-price) mean-price)]
      (cond
        (< deviation (- threshold)) {::signal :buy ::confidence 0.8 ::timestamp (::timestamp current-bar)}
        (> deviation threshold) {::signal :sell ::confidence 0.8 ::timestamp (::timestamp current-bar)}
        :else {::signal :hold ::confidence 0.3 ::timestamp (::timestamp current-bar)})))
  
  (get-parameters [this]
    {:lookback-period lookback-period :threshold threshold})
  
  (update-parameters [this {:keys [lookback-period threshold]}]
    (assoc this :lookback-period lookback-period :threshold threshold)))

(defrecord MomentumStrategy [momentum-period rsi-period overbought oversold]
  TradingStrategy
  (initialize [this market-data]
    (assoc this ::initialized true))
  
  (generate-signal [this market-data current-bar]
    (let [recent-bars (take momentum-period (reverse market-data))
          prices (map ::close recent-bars)
          returns (map (fn [[p1 p2]] (/ (- p2 p1) p1)) (partition 2 1 prices))
          momentum (reduce + returns)
          rsi-bars (take rsi-period recent-bars)
          rsi (calculate-rsi rsi-bars)]
      (cond
        (and (> momentum 0.02) (< rsi oversold)) {::signal :buy ::confidence 0.85 ::timestamp (::timestamp current-bar)}
        (and (< momentum -0.02) (> rsi overbought)) {::signal :sell ::confidence 0.85 ::timestamp (::timestamp current-bar)}
        :else {::signal :hold ::confidence 0.4 ::timestamp (::timestamp current-bar)})))
  
  (get-parameters [this]
    {:momentum-period momentum-period :rsi-period rsi-period 
     :overbought overbought :oversold oversold})
  
  (update-parameters [this params]
    (merge this params)))

(defn calculate-rsi [bars]
  "Calculate Relative Strength Index"
  (let [prices (map ::close bars)
        changes (map (fn [[p1 p2]] (- p2 p1)) (partition 2 1 prices))
        gains (map #(if (> % 0) % 0) changes)
        losses (map #(if (< % 0) (- %) 0) changes)
        avg-gain (/ (reduce + gains) (count gains))
        avg-loss (/ (reduce + losses) (count losses))
        rs (/ avg-gain avg-loss)]
    (- 100 (/ 100 (+ 1 rs)))))

;; ============================================================================
;; Portfolio Management
;; ============================================================================

(defrecord Portfolio [cash positions trades max-position-size commission]
  Object
  (toString [this]
    (str "Portfolio: Cash=" cash ", Positions=" (count positions) ", Trades=" (count trades))))

(defn create-portfolio 
  "Create a new portfolio with initial cash"
  [initial-cash & {:keys [max-position-size commission] 
                   :or {max-position-size 0.1 commission 0.001}}]
  (->Portfolio initial-cash {} [] max-position-size commission))

(defn execute-trade
  "Execute a trade and update portfolio"
  [portfolio signal market-data current-bar]
  (let [symbol (::symbol current-bar)
        price (::close current-bar)
        timestamp (::timestamp current-bar)
        position-value (* (:cash portfolio) (:max-position-size portfolio))
        quantity (int (/ position-value price))
        commission-cost (* price quantity (:commission portfolio))]
    
    (case (::signal signal)
      :buy
      (if (and (> (:cash portfolio) (+ (* price quantity) commission-cost))
               (not (get-in portfolio [:positions symbol])))
        (let [total-cost (+ (* price quantity) commission-cost)
              new-position {::symbol symbol
                           ::quantity quantity
                           ::entry-price price
                           ::entry-time timestamp
                           ::status :open}
              new-trade {::type :buy
                        ::symbol symbol
                        ::quantity quantity
                        ::price price
                        ::timestamp timestamp
                        ::commission commission-cost}]
          (-> portfolio
              (update :cash - total-cost)
              (assoc-in [:positions symbol] new-position)
              (update :trades conj new-trade)))
        portfolio)
      
      :sell
      (if-let [position (get-in portfolio [:positions symbol])]
        (let [proceeds (* price (::quantity position))
              commission-cost (* price (::quantity position) (:commission portfolio))
              net-proceeds (- proceeds commission-cost)
              pnl (- net-proceeds (* (::entry-price position) (::quantity position)))
              new-trade {::type :sell
                        ::symbol symbol
                        ::quantity (::quantity position)
                        ::price price
                        ::timestamp timestamp
                        ::commission commission-cost
                        ::pnl pnl}]
          (-> portfolio
              (update :cash + net-proceeds)
              (update :positions dissoc symbol)
              (update :trades conj new-trade)))
        portfolio)
      
      :hold portfolio)))

(defn calculate-portfolio-value
  "Calculate total portfolio value including positions"
  [portfolio current-prices]
  (let [cash (:cash portfolio)
        position-values (reduce-kv
                         (fn [acc symbol position]
                           (+ acc (* (::quantity position) 
                                    (get current-prices symbol (::entry-price position)))))
                         0
                         (:positions portfolio))]
    (+ cash position-values)))

;; ============================================================================
;; Backtesting Engine
;; ============================================================================

(defrecord BacktestEngine [strategy market-data-provider portfolio start-date end-date]
  Object
  (toString [this]
    (str "BacktestEngine: " start-date " to " end-date)))

(defn create-backtest-engine
  "Create a new backtesting engine"
  [strategy market-data-provider initial-cash start-date end-date]
  (->BacktestEngine 
    strategy
    market-data-provider
    (create-portfolio initial-cash)
    start-date
    end-date))

(defn run-backtest
  "Run complete backtest and return results"
  [engine symbols]
  (let [results-chan (chan)
        {:keys [strategy market-data-provider portfolio start-date end-date]} engine]
    
    (go
      (try
        (let [initialized-strategy (initialize strategy nil)
              all-market-data (apply merge-with concat
                                    (map #(hash-map % (fetch-historical market-data-provider % start-date end-date))
                                         symbols))
              
              ;; Sort all bars by timestamp across all symbols
              all-bars (->> all-market-data
                           vals
                           (apply concat)
                           (sort-by ::timestamp))
              
              ;; Process each bar sequentially
              final-state (reduce
                           (fn [{:keys [portfolio market-history signals]} current-bar]
                             (let [symbol (::symbol current-bar)
                                   updated-history (update market-history symbol (fnil conj []) current-bar)
                                   signal (generate-signal initialized-strategy 
                                                         (get updated-history symbol [])
                                                         current-bar)
                                   updated-portfolio (execute-trade portfolio signal updated-history current-bar)]
                               
                               {:portfolio updated-portfolio
                                :market-history updated-history
                                :signals (conj signals signal)}))
                           
                           {:portfolio portfolio
                            :market-history {}
                            :signals []}
                           
                           all-bars)
              
              ;; Calculate final metrics
              final-portfolio (:portfolio final-state)
              all-signals (:signals final-state)
              
              ;; Get final prices for portfolio valuation
              final-prices (reduce (fn [acc symbol]
                                    (assoc acc symbol 
                                           (-> (fetch-historical market-data-provider symbol 
                                                               (jt/minus end-date (jt/hours 1)) 
                                                               end-date)
                                               last
                                               ::close)))
                                  {}
                                  symbols)
              
              final-value (calculate-portfolio-value final-portfolio final-prices)
              initial-value (:cash portfolio)
              total-return (/ (- final-value initial-value) initial-value)
              
              trades (:trades final-portfolio)
              winning-trades (filter #(> (get % ::pnl 0) 0) trades)
              losing-trades (filter #(< (get % ::pnl 0) 0) trades)
              
              metrics {::total-return total-return
                      ::final-value final-value
                      ::initial-value initial-value
                      ::total-trades (count trades)
                      ::winning-trades (count winning-trades)
                      ::losing-trades (count losing-trades)
                      ::win-rate (if (pos? (count trades))
                                  (/ (count winning-trades) (count trades))
                                  0)
                      ::avg-win (if (pos? (count winning-trades))
                               (/ (reduce + (map ::pnl winning-trades)) (count winning-trades))
                               0)
                      ::avg-loss (if (pos? (count losing-trades))
                                (/ (reduce + (map ::pnl losing-trades)) (count losing-trades))
                                0)
                      ::sharpe-ratio (calculate-sharpe-ratio trades)
                      ::max-drawdown (calculate-max-drawdown trades initial-value)}]
          
          (>! results-chan {::status :success
                           ::metrics metrics
                           ::final-portfolio final-portfolio
                           ::trades trades
                           ::signals all-signals
                           ::strategy-params (get-parameters strategy)}))
        
        (catch Exception e
          (>! results-chan {::status :error
                           ::error (.getMessage e)}))))
    
    results-chan))

(defn calculate-sharpe-ratio
  "Calculate Sharpe ratio from trades"
  [trades]
  (if (> (count trades) 1)
    (let [returns (map ::pnl trades)
          mean-return (/ (reduce + returns) (count returns))
          variance (/ (reduce + (map #(Math/pow (- % mean-return) 2) returns)) (count returns))
          std-dev (Math/sqrt variance)]
      (if (pos? std-dev)
        (/ mean-return std-dev)
        0))
    0))

(defn calculate-max-drawdown
  "Calculate maximum drawdown"
  [trades initial-value]
  (if (empty? trades)
    0
    (let [cumulative-values (reductions (fn [acc trade]
                                         (+ acc (get trade ::pnl 0)))
                                       initial-value
                                       trades)
          peaks (reductions max cumulative-values)
          drawdowns (map (fn [peak value] (/ (- value peak) peak)) peaks cumulative-values)]
      (apply min drawdowns))))

;; ============================================================================
;; Strategy Optimization
;; ============================================================================

(defn optimize-strategy
  "Optimize strategy parameters using grid search"
  [base-strategy market-data-provider symbols parameter-ranges initial-cash start-date end-date]
  (let [parameter-combinations (apply combo/cartesian-product (vals parameter-ranges))
        parameter-keys (keys parameter-ranges)
        results-chan (chan)]
    
    (go
      (let [optimization-results
            (for [param-combo parameter-combinations]
              (let [params (zipmap parameter-keys param-combo)
                    optimized-strategy (update-parameters base-strategy params)
                    engine (create-backtest-engine optimized-strategy market-data-provider 
                                                 initial-cash start-date end-date)
                    result (<! (run-backtest engine symbols))]
                (assoc result ::parameters params)))]
        
        ;; Find best strategy by Sharpe ratio
        (let [best-strategy (apply max-key #(get-in % [::metrics ::sharpe-ratio] 0) optimization-results)]
          (>! results-chan {::status :success
                           ::best-strategy best-strategy
                           ::all-results optimization-results}))))
    
    results-chan))

;; ============================================================================
;; Reactive Real-time Processing
;; ============================================================================

(defn create-realtime-trader
  "Create real-time trading system with live data feeds"
  [strategy market-data-provider portfolio]
  (let [signal-chan (chan 100)
        trade-chan (chan 100)
        portfolio-atom (atom portfolio)]
    
    {:signal-chan signal-chan
     :trade-chan trade-chan
     :portfolio-atom portfolio-atom
     :stop-fn (atom nil)}))

(defn start-realtime-trading
  "Start real-time trading system"
  [trader symbols]
  (let [{:keys [signal-chan trade-chan portfolio-atom]} trader
        market-data-atom (atom {})]
    
    ;; Start market data subscriptions
    (doseq [symbol symbols]
      (subscribe-realtime (:market-data-provider trader) symbol
                         (fn [bar]
                           (swap! market-data-atom update symbol (fnil conj []) bar)
                           (go (>! signal-chan {:symbol symbol :bar bar})))))
    
    ;; Start signal processing
    (go
      (while true
        (when-let [{:keys [symbol bar]} (<! signal-chan)]
          (let [market-history (get @market-data-atom symbol [])
                signal (generate-signal (:strategy trader) market-history bar)]
            (>! trade-chan {:signal signal :bar bar})))))
    
    ;; Start trade execution
    (go
      (while true
        (when-let [{:keys [signal bar]} (<! trade-chan)]
          (swap! portfolio-atom execute-trade signal @market-data-atom bar))))
    
    trader))

;; ============================================================================
;; Visualization and Reporting
;; ============================================================================

(defn generate-backtest-report
  "Generate comprehensive backtest report"
  [backtest-results]
  (let [{:keys [::metrics ::trades ::signals]} backtest-results]
    {::summary metrics
     ::trade-analysis {::total-trades (count trades)
                      ::profitable-trades (count (filter #(> (::pnl %) 0) trades))
                      ::average-trade-duration (calculate-avg-trade-duration trades)}
     ::signal-analysis {::total-signals (count signals)
                       ::buy-signals (count (filter #(= (::signal %) :buy) signals))
                       ::sell-signals (count (filter #(= (::signal %) :sell) signals))
                       ::hold-signals (count (filter #(= (::signal %) :hold) signals))}
     ::risk-metrics {::max-drawdown (::max-drawdown metrics)
                    ::sharpe-ratio (::sharpe-ratio metrics)
                    ::volatility (calculate-volatility trades)}}))

(defn calculate-avg-trade-duration
  "Calculate average trade duration"
  [trades]
  (let [paired-trades (partition 2 trades)
        durations (map (fn [[entry exit]]
                        (jt/time-between (::timestamp entry) (::timestamp exit) :hours))
                      paired-trades)]
    (if (pos? (count durations))
      (/ (reduce + durations) (count durations))
      0)))

(defn calculate-volatility
  "Calculate portfolio volatility"
  [trades]
  (let [returns (map ::pnl trades)
        mean-return (/ (reduce + returns) (count returns))
        variance (/ (reduce + (map #(Math/pow (- % mean-return) 2) returns)) (count returns))]
    (Math/sqrt variance)))

;; ============================================================================
;; Export API
;; ============================================================================

(defn create-strategy-factory
  "Factory for creating different strategy types"
  []
  {:sma (fn [short long] (->SimpleMovingAverageStrategy short long))
   :mean-reversion (fn [lookback threshold] (->MeanReversionStrategy lookback threshold))
   :momentum (fn [momentum-period rsi-period overbought oversold] 
               (->MomentumStrategy momentum-period rsi-period overbought oversold))})

(def strategy-factory (create-strategy-factory))

;; Example usage:
;; (def sma-strategy ((:sma strategy-factory) 10 20))
;; (def data-provider (->CSVDataProvider "/path/to/data"))
;; (def engine (create-backtest-engine sma-strategy data-provider 100000 start-date end-date))
;; (def results (<! (run-backtest engine ["AAPL" "GOOGL"])))