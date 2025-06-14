# ⚡ Numerai Electric Trading Lab

Advanced AI-driven quantitative research platform combining Electric Clojure's reactive programming with real-time trading strategy development and backtesting.

## 🚀 Live Demo

**Running System**: [http://45.90.121.59:42857](http://45.90.121.59:42857)

Current deployment shows a Python Flask wrapper serving the Electric Clojure workbench interface. The system demonstrates:
- Real-time reactive UI with Electric Clojure
- Advanced backtesting engine with multiple strategies
- Live market data simulation and portfolio tracking
- Interactive strategy parameter optimization

## 🏗️ Architecture

### Hybrid Stack Design

**Frontend**: Electric Clojure reactive UI
- Real-time parameter adjustment
- Live backtest visualization
- Interactive strategy development
- Reactive data binding

**Backend**: Clojure + Python Integration
- Core backtesting engine in Clojure
- Flask API server for external integration
- Real-time data processing with core.async
- Strategy optimization with parallel execution

**Infrastructure**: 
- Contabo VPS (45.90.121.59)
- Electric Clojure server on port 42857
- Global access with domain mapping
- Automated deployment and monitoring

## 📊 Features

### Advanced Backtesting Engine

- **Multiple Strategy Types**:
  - Simple Moving Average (SMA)
  - Mean Reversion
  - Momentum with RSI
  - Custom strategy framework

- **Real-time Metrics**:
  - Total return & Sharpe ratio
  - Maximum drawdown
  - Win/loss ratios
  - Risk-adjusted performance

- **Portfolio Management**:
  - Position sizing with risk management
  - Commission and slippage modeling
  - Multi-asset portfolio tracking
  - Real-time P&L calculation

### Reactive UI Components

- **Strategy Builder**: Visual parameter adjustment with instant feedback
- **Results Dashboard**: Real-time backtest metrics and visualization
- **Live Trading Panel**: Simulated real-time market data and signals
- **Configuration Manager**: Dynamic symbol and timeframe selection

## 🛠️ Development Setup

### Prerequisites

- Java 17+ (for Clojure)
- Node.js 18+ (for ClojureScript compilation)
- Python 3.10+ (for Flask integration)
- Clojure CLI tools

### Local Development

```bash
# Clone and setup
git clone https://github.com/uprootiny/numerai-electric.git
cd numerai-electric

# Start Clojure REPL
clj -M:dev:repl

# In another terminal, start ClojureScript compilation
npx shadow-cljs watch electric-workbench

# Start Python Flask server (for integration)
python app.py

# Access the application
open http://localhost:8280  # Electric Clojure UI
open http://localhost:42857 # Flask integration layer
```

### Production Deployment

```bash
# Build ClojureScript for production
npx shadow-cljs release electric-workbench

# Create production JAR
clj -T:build jar

# Deploy to server
scp target/numerai-electric.jar user@45.90.121.59:/opt/numerai-electric/
scp app.py user@45.90.121.59:/opt/numerai-electric/

# Start production services
java -jar /opt/numerai-electric/numerai-electric.jar &
python /opt/numerai-electric/app.py &
```

## 🔧 API Endpoints

### Core System

- `GET /` - Main Electric Clojure interface
- `GET /health` - System health and status
- `GET /api/status` - Detailed system metrics

### Trading & Backtesting

- `GET /api/numerai` - Tournament data and model status
- `POST /api/backtest` - Run backtest with parameters
- `GET /api/strategies` - Available strategy types
- `GET /api/results/{id}` - Backtest results by ID

### Real-time Data

- `GET /api/signals` - Current trading signals
- `GET /api/portfolio` - Live portfolio status
- `WebSocket /ws/market-data` - Real-time market feed

## 📈 Usage Examples

### Basic Strategy Development

```clojure
;; Create a simple moving average strategy
(def sma-strategy 
  (bt/create-strategy :sma {:short-window 10 :long-window 20}))

;; Run backtest
(def results 
  (<! (bt/run-backtest 
        (bt/create-backtest-engine sma-strategy data-provider 100000 start-date end-date)
        ["AAPL" "GOOGL"])))

;; Access results
(::bt/total-return (::bt/metrics results))
(::bt/sharpe-ratio (::bt/metrics results))
```

### Real-time Strategy Monitoring

```clojure
;; Subscribe to real-time signals
(e/watch !backtest-state)  ; Reactive state updates

;; Live portfolio tracking
(def portfolio-stream (live-portfolio-tracking))
(e/watch (:portfolio-value portfolio-stream))
```

### Strategy Optimization

```clojure
;; Optimize strategy parameters
(def optimization-results
  (<! (bt/optimize-strategy 
        base-strategy 
        data-provider 
        ["AAPL" "GOOGL"]
        {:short-window [5 10 15 20]
         :long-window [20 30 40 50]}
        100000 
        start-date 
        end-date)))
```

## 🧠 Advanced Features

### Electric Clojure Integration

- **Reactive State Management**: Real-time UI updates with Electric atoms
- **Component Composition**: Modular UI components with automatic reactivity
- **Data Flow**: Unidirectional data flow with missionary streams
- **Performance**: Optimized rendering with minimal DOM updates

### Backtesting Engine

- **Vectorized Operations**: High-performance calculations with Tablecloth
- **Parallel Execution**: Multi-threaded strategy optimization
- **Memory Efficiency**: Lazy sequence processing for large datasets
- **Extensibility**: Protocol-based architecture for custom strategies

### Risk Management

- **Position Sizing**: Dynamic position sizing based on volatility
- **Drawdown Protection**: Automatic position reduction during drawdowns
- **Correlation Analysis**: Multi-asset correlation monitoring
- **Stress Testing**: Monte Carlo simulation for robust testing

## 🚀 Deployment Status

### Current Infrastructure

- **Server**: Contabo VPS (vmi2065296.contaboserver.net)
- **Public IP**: 45.90.121.59
- **Port**: 42857 (high port for security)
- **Domain**: electric.lab.uprootiny.dev
- **Uptime**: 13+ hours continuous operation
- **Requests Served**: 400+ and counting

### Monitoring & Health

```bash
# Check system health
curl http://45.90.121.59:42857/health

# Validate core features
curl -s http://45.90.121.59:42857/api/status | jq '.features'

# Monitor live metrics
curl http://45.90.121.59:42857/api/logs
```

## 🔬 Research Applications

### Numerai Tournament Integration

- **Model Training**: Automated feature engineering and model training
- **Prediction Generation**: Real-time prediction pipeline
- **Performance Tracking**: Tournament score monitoring and analysis
- **Strategy Evolution**: Adaptive strategy development based on tournament feedback

### Academic Research

- **Market Microstructure**: High-frequency trading pattern analysis
- **Behavioral Finance**: Sentiment analysis integration
- **Quantitative Methods**: Advanced statistical modeling
- **Risk Management**: Dynamic hedging and portfolio optimization

## 🤝 Contributing

1. Fork the repository
2. Create feature branch with enhanced backtesting capabilities
3. Implement new strategy types or optimization algorithms
4. Add comprehensive tests and documentation
5. Submit pull request with performance benchmarks

## 📄 License

MIT License - see LICENSE file for details.

---

**⚡ Electric Clojure + Numerai = Next-Generation Trading Research Platform**

*Real-time reactive programming meets quantitative finance for unprecedented development velocity and system reliability.*