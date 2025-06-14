(ns electric.workbench
  "Electric Clojure integration for real-time backtesting workbench"
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            [missionary.core :as m]
            [backtesting.core :as bt]
            [clojure.core.async :as async :refer [chan go >! <!]]
            [java-time :as jt]
            [clojure.data.json :as json])
  (:import [java.time Instant]))

;; ============================================================================
;; Electric State Management
;; ============================================================================

(e/def !backtest-state 
  "Global reactive state for backtesting workbench"
  (e/atom {:strategies {}
           :active-backtest nil
           :results {}
           :market-data {}
           :realtime-portfolio {}
           :trading-signals []
           :system-status :ready}))

(e/def !strategy-params
  "Reactive strategy parameters"
  (e/atom {:strategy-type :sma
           :sma {:short-window 10 :long-window 20}
           :mean-reversion {:lookback-period 14 :threshold 0.02}
           :momentum {:momentum-period 10 :rsi-period 14 :overbought 70 :oversold 30}}))

(e/def !backtest-config
  "Reactive backtest configuration"
  (e/atom {:symbols ["AAPL" "GOOGL" "MSFT"]
           :start-date (jt/minus (jt/instant) (jt/days 365))
           :end-date (jt/instant)
           :initial-cash 100000
           :commission 0.001}))

;; ============================================================================
;; Strategy Management
;; ============================================================================

(e/defn create-strategy [strategy-type params]
  (let [factory bt/strategy-factory]
    (case strategy-type
      :sma ((:sma factory) (:short-window params) (:long-window params))
      :mean-reversion ((:mean-reversion factory) (:lookback-period params) (:threshold params))
      :momentum ((:momentum factory) (:momentum-period params) (:rsi-period params) 
                                     (:overbought params) (:oversold params)))))

(e/defn update-strategy-state [strategy-id strategy]
  (e/swap! !backtest-state assoc-in [:strategies strategy-id] strategy))

;; ============================================================================
;; Backtesting Engine Integration
;; ============================================================================

(e/defn run-electric-backtest []
  (let [config (e/watch !backtest-config)
        strategy-params (e/watch !strategy-params)
        strategy (create-strategy (:strategy-type strategy-params)
                                 (get strategy-params (:strategy-type strategy-params)))
        data-provider (bt/->CSVDataProvider "/data/market")
        engine (bt/create-backtest-engine strategy data-provider 
                                         (:initial-cash config)
                                         (:start-date config)
                                         (:end-date config))]
    
    (e/swap! !backtest-state assoc :system-status :running)
    
    ;; Run backtest asynchronously
    (go
      (let [results (<! (bt/run-backtest engine (:symbols config)))]
        (e/swap! !backtest-state 
                 (fn [state]
                   (-> state
                       (assoc :active-backtest results)
                       (assoc :system-status :completed)
                       (update :results assoc (str (gensym "backtest-")) results))))))))

;; ============================================================================
;; Real-time Data Processing
;; ============================================================================

(e/defn market-data-stream [symbol]
  "Create reactive market data stream for a symbol"
  (e/fn []
    (let [data-chan (chan)]
      (go
        ;; Simulate real-time market data
        (loop []
          (let [price (+ 100 (- 10 (rand 20)))
                volume (+ 1000 (rand-int 9000))
                bar {::bt/timestamp (jt/instant)
                     ::bt/symbol symbol
                     ::bt/open price
                     ::bt/high (+ price (rand 2))
                     ::bt/low (- price (rand 2))
                     ::bt/close (+ price (- 1 (rand 2)))
                     ::bt/volume volume}]
            (>! data-chan bar)
            (<! (async/timeout 1000))
            (recur))))
      
      ;; Return missionary flow
      (m/observe (fn [!]
                   (go
                     (loop []
                       (when-let [data (<! data-chan)]
                         (! data)
                         (recur)))))))))

(e/defn live-portfolio-tracking []
  "Track portfolio performance in real-time"
  (let [portfolio-value (e/atom 100000)
        pnl (e/atom 0)
        positions (e/atom {})]
    
    ;; Update portfolio based on market data
    (e/for [symbol (:symbols (e/watch !backtest-config))]
      (e/fn []
        (let [market-bar (market-data-stream symbol)]
          (e/swap! !backtest-state 
                   update-in [:market-data symbol] 
                   (fnil conj []) 
                   market-bar))))
    
    {:portfolio-value portfolio-value
     :pnl pnl
     :positions positions}))

;; ============================================================================
;; UI Components
;; ============================================================================

(e/defn strategy-selector []
  "Strategy selection and parameter configuration"
  (dom/div
    (dom/props {:class "strategy-selector"})
    
    (dom/h3 (dom/text "Strategy Configuration"))
    
    ;; Strategy type selection
    (dom/div
      (dom/props {:class "strategy-type"})
      (dom/label (dom/text "Strategy Type: "))
      (ui/select 
        (e/fn [v] (e/swap! !strategy-params assoc :strategy-type (keyword v)))
        (e/fn [] (name (:strategy-type (e/watch !strategy-params))))
        (e/fn [] [["sma" "Simple Moving Average"]
                  ["mean-reversion" "Mean Reversion"]
                  ["momentum" "Momentum"]])))
    
    ;; Dynamic parameter inputs based on strategy type
    (let [strategy-type (:strategy-type (e/watch !strategy-params))
          params (get (e/watch !strategy-params) strategy-type)]
      
      (case strategy-type
        :sma
        (dom/div
          (dom/props {:class "sma-params"})
          (dom/div
            (dom/label (dom/text "Short Window: "))
            (ui/input (:short-window params)
                     (e/fn [v] (e/swap! !strategy-params assoc-in [:sma :short-window] (js/parseInt v)))))
          (dom/div
            (dom/label (dom/text "Long Window: "))
            (ui/input (:long-window params)
                     (e/fn [v] (e/swap! !strategy-params assoc-in [:sma :long-window] (js/parseInt v))))))
        
        :mean-reversion
        (dom/div
          (dom/props {:class "mean-reversion-params"})
          (dom/div
            (dom/label (dom/text "Lookback Period: "))
            (ui/input (:lookback-period params)
                     (e/fn [v] (e/swap! !strategy-params assoc-in [:mean-reversion :lookback-period] (js/parseInt v)))))
          (dom/div
            (dom/label (dom/text "Threshold: "))
            (ui/input (:threshold params)
                     (e/fn [v] (e/swap! !strategy-params assoc-in [:mean-reversion :threshold] (js/parseFloat v))))))
        
        :momentum
        (dom/div
          (dom/props {:class "momentum-params"})
          (dom/div
            (dom/label (dom/text "Momentum Period: "))
            (ui/input (:momentum-period params)
                     (e/fn [v] (e/swap! !strategy-params assoc-in [:momentum :momentum-period] (js/parseInt v)))))
          (dom/div
            (dom/label (dom/text "RSI Period: "))
            (ui/input (:rsi-period params)
                     (e/fn [v] (e/swap! !strategy-params assoc-in [:momentum :rsi-period] (js/parseInt v)))))
          (dom/div
            (dom/label (dom/text "Overbought: "))
            (ui/input (:overbought params)
                     (e/fn [v] (e/swap! !strategy-params assoc-in [:momentum :overbought] (js/parseInt v)))))
          (dom/div
            (dom/label (dom/text "Oversold: "))
            (ui/input (:oversold params)
                     (e/fn [v] (e/swap! !strategy-params assoc-in [:momentum :oversold] (js/parseInt v)))))))
      
      ;; Run backtest button
      (dom/div
        (dom/props {:class "run-backtest"})
        (ui/button 
          (e/fn [] (run-electric-backtest))
          (dom/text "Run Backtest"))))))

(e/defn backtest-config-panel []
  "Backtest configuration panel"
  (dom/div
    (dom/props {:class "backtest-config"})
    
    (dom/h3 (dom/text "Backtest Configuration"))
    
    (let [config (e/watch !backtest-config)]
      (dom/div
        ;; Initial cash
        (dom/div
          (dom/label (dom/text "Initial Cash: $"))
          (ui/input (:initial-cash config)
                   (e/fn [v] (e/swap! !backtest-config assoc :initial-cash (js/parseInt v)))))
        
        ;; Commission
        (dom/div
          (dom/label (dom/text "Commission: "))
          (ui/input (:commission config)
                   (e/fn [v] (e/swap! !backtest-config assoc :commission (js/parseFloat v)))))
        
        ;; Symbols
        (dom/div
          (dom/label (dom/text "Symbols (comma-separated): "))
          (ui/input (clojure.string/join ", " (:symbols config))
                   (e/fn [v] (e/swap! !backtest-config assoc :symbols 
                                     (map clojure.string/trim (clojure.string/split v #","))))))))))

(e/defn results-panel []
  "Display backtest results and metrics"
  (dom/div
    (dom/props {:class "results-panel"})
    
    (dom/h3 (dom/text "Backtest Results"))
    
    (let [state (e/watch !backtest-state)
          active-backtest (:active-backtest state)
          status (:system-status state)]
      
      (case status
        :ready
        (dom/div (dom/text "Ready to run backtest"))
        
        :running
        (dom/div 
          (dom/props {:class "loading"})
          (dom/text "Running backtest..."))
        
        :completed
        (when active-backtest
          (let [metrics (::bt/metrics active-backtest)]
            (dom/div
              (dom/props {:class "metrics"})
              
              ;; Key metrics
              (dom/div
                (dom/props {:class "metric-grid"})
                
                (dom/div
                  (dom/props {:class "metric"})
                  (dom/div (dom/props {:class "metric-label"}) (dom/text "Total Return"))
                  (dom/div (dom/props {:class "metric-value"}) 
                           (dom/text (str (* 100 (::bt/total-return metrics)) "%"))))
                
                (dom/div
                  (dom/props {:class "metric"})
                  (dom/div (dom/props {:class "metric-label"}) (dom/text "Sharpe Ratio"))
                  (dom/div (dom/props {:class "metric-value"}) 
                           (dom/text (str (Math/round (* 100 (::bt/sharpe-ratio metrics))) "%"))))
                
                (dom/div
                  (dom/props {:class "metric"})
                  (dom/div (dom/props {:class "metric-label"}) (dom/text "Max Drawdown"))
                  (dom/div (dom/props {:class "metric-value"}) 
                           (dom/text (str (* 100 (::bt/max-drawdown metrics)) "%"))))
                
                (dom/div
                  (dom/props {:class "metric"})
                  (dom/div (dom/props {:class "metric-label"}) (dom/text "Win Rate"))
                  (dom/div (dom/props {:class "metric-value"}) 
                           (dom/text (str (* 100 (::bt/win-rate metrics)) "%"))))
                
                (dom/div
                  (dom/props {:class "metric"})
                  (dom/div (dom/props {:class "metric-label"}) (dom/text "Total Trades"))
                  (dom/div (dom/props {:class "metric-value"}) 
                           (dom/text (str (::bt/total-trades metrics)))))))))
        
        (dom/div (dom/text "Unknown status"))))))

(e/defn live-dashboard []
  "Real-time trading dashboard"
  (dom/div
    (dom/props {:class "live-dashboard"})
    
    (dom/h3 (dom/text "Live Trading Dashboard"))
    
    (let [portfolio (live-portfolio-tracking)]
      (dom/div
        (dom/props {:class "live-metrics"})
        
        ;; Portfolio value
        (dom/div
          (dom/props {:class "portfolio-value"})
          (dom/text "Portfolio Value: $")
          (dom/text (e/watch (:portfolio-value portfolio))))
        
        ;; P&L
        (dom/div
          (dom/props {:class "pnl"})
          (dom/text "P&L: $")
          (dom/text (e/watch (:pnl portfolio))))
        
        ;; Market data feed
        (dom/div
          (dom/props {:class "market-feed"})
          (dom/h4 (dom/text "Live Market Data"))
          (e/for [symbol (:symbols (e/watch !backtest-config))]
            (dom/div
              (dom/props {:class "symbol-data"})
              (dom/text (str symbol ": "))
              (let [market-data (get-in (e/watch !backtest-state) [:market-data symbol])]
                (when-let [latest (last market-data)]
                  (dom/text (str "$" (::bt/close latest))))))))))))

;; ============================================================================
;; Main Application
;; ============================================================================

(e/defn electric-backtesting-workbench []
  "Main Electric Clojure backtesting workbench application"
  (dom/div
    (dom/props {:class "backtesting-workbench"})
    
    ;; Header
    (dom/header
      (dom/props {:class "workbench-header"})
      (dom/h1 (dom/text "⚡ Electric Backtesting Workbench"))
      (dom/p (dom/text "Real-time reactive trading strategy development & testing")))
    
    ;; Main content grid
    (dom/div
      (dom/props {:class "workbench-grid"})
      
      ;; Left panel - Configuration
      (dom/div
        (dom/props {:class "config-panel"})
        (strategy-selector)
        (backtest-config-panel))
      
      ;; Center panel - Results
      (dom/div
        (dom/props {:class "results-center"})
        (results-panel))
      
      ;; Right panel - Live dashboard
      (dom/div
        (dom/props {:class "live-panel"})
        (live-dashboard)))))

;; ============================================================================
;; CSS Styles (will be included in the HTML)
;; ============================================================================

(def workbench-styles
  "
  .backtesting-workbench {
    font-family: 'Monaco', 'Menlo', monospace;
    background: linear-gradient(135deg, #0a0a0a, #1a1a2e);
    color: #00ff41;
    min-height: 100vh;
    padding: 20px;
  }
  
  .workbench-header {
    text-align: center;
    margin-bottom: 30px;
  }
  
  .workbench-header h1 {
    font-size: 2.5rem;
    margin: 0;
    text-shadow: 0 0 20px #00ff41;
    animation: glow 2s ease-in-out infinite alternate;
  }
  
  .workbench-grid {
    display: grid;
    grid-template-columns: 300px 1fr 300px;
    gap: 20px;
    max-width: 1400px;
    margin: 0 auto;
  }
  
  .config-panel, .results-center, .live-panel {
    background: rgba(0, 255, 65, 0.05);
    border: 1px solid rgba(0, 255, 65, 0.3);
    border-radius: 15px;
    padding: 20px;
    min-height: 500px;
  }
  
  .strategy-selector, .backtest-config {
    margin-bottom: 30px;
  }
  
  .strategy-selector h3, .backtest-config h3 {
    color: #ffffff;
    margin-bottom: 15px;
    border-bottom: 1px solid rgba(0, 255, 65, 0.3);
    padding-bottom: 5px;
  }
  
  .strategy-type, .sma-params div, .mean-reversion-params div, .momentum-params div {
    margin-bottom: 15px;
  }
  
  label {
    display: block;
    margin-bottom: 5px;
    color: #cccccc;
    font-size: 0.9rem;
  }
  
  input, select {
    width: 100%;
    padding: 8px;
    background: rgba(0, 0, 0, 0.6);
    border: 1px solid #00ff41;
    border-radius: 5px;
    color: #00ff41;
    font-family: inherit;
  }
  
  input:focus, select:focus {
    outline: none;
    border-color: #00d2ff;
    box-shadow: 0 0 10px rgba(0, 210, 255, 0.3);
  }
  
  .run-backtest {
    margin-top: 20px;
  }
  
  button {
    background: linear-gradient(45deg, #00ff41, #00d2ff);
    color: #000000;
    padding: 12px 24px;
    border: none;
    border-radius: 8px;
    cursor: pointer;
    font-weight: bold;
    font-family: inherit;
    width: 100%;
    transition: all 0.3s ease;
  }
  
  button:hover {
    transform: translateY(-2px);
    box-shadow: 0 5px 15px rgba(0, 255, 65, 0.3);
  }
  
  .metric-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 15px;
  }
  
  .metric {
    background: rgba(0, 0, 0, 0.3);
    padding: 15px;
    border-radius: 8px;
    border: 1px solid rgba(0, 255, 65, 0.2);
    text-align: center;
  }
  
  .metric-label {
    font-size: 0.8rem;
    color: #cccccc;
    margin-bottom: 5px;
  }
  
  .metric-value {
    font-size: 1.5rem;
    font-weight: bold;
    color: #00ff41;
  }
  
  .loading {
    text-align: center;
    color: #00d2ff;
    font-style: italic;
  }
  
  .live-dashboard {
    height: 100%;
  }
  
  .live-metrics {
    display: flex;
    flex-direction: column;
    gap: 15px;
  }
  
  .portfolio-value, .pnl {
    background: rgba(0, 0, 0, 0.3);
    padding: 10px;
    border-radius: 5px;
    text-align: center;
  }
  
  .market-feed {
    margin-top: 20px;
  }
  
  .symbol-data {
    background: rgba(0, 0, 0, 0.2);
    padding: 8px;
    margin: 5px 0;
    border-radius: 3px;
    border-left: 3px solid #00ff41;
  }
  
  @keyframes glow {
    from { text-shadow: 0 0 20px #00ff41; }
    to { text-shadow: 0 0 30px #00ff41, 0 0 40px #00ff41; }
  }
  
  @media (max-width: 1200px) {
    .workbench-grid {
      grid-template-columns: 1fr;
    }
  }
  ")

;; ============================================================================
;; Integration with Python Flask Server
;; ============================================================================

(defn serialize-backtest-results
  "Serialize backtest results for JSON API"
  [results]
  (-> results
      (update ::bt/metrics #(into {} %))
      (update ::bt/trades #(map (fn [trade] (into {} trade)) %))
      (update ::bt/signals #(map (fn [signal] (into {} signal)) %))))

(defn export-to-flask
  "Export Electric state for Flask API consumption"
  []
  (let [state @!backtest-state
        config @!backtest-config
        strategy-params @!strategy-params]
    {:status (:system-status state)
     :active-backtest (when-let [backtest (:active-backtest state)]
                       (serialize-backtest-results backtest))
     :config config
     :strategy-params strategy-params
     :market-data (:market-data state)
     :trading-signals (:trading-signals state)}))

;; Export for use in Python integration
(defn init-electric-workbench
  "Initialize Electric workbench for integration"
  []
  {:state !backtest-state
   :config !backtest-config
   :strategy-params !strategy-params
   :export-fn export-to-flask
   :main-component electric-backtesting-workbench
   :styles workbench-styles})