# FinLite Programming Language

**A domain-specific language for financial modeling and analysis**

## Overview

FinLite is a programming language designed specifically for finance students and analysts. It combines the familiarity of spreadsheets with the power of code, enabling users to:

- Build financial models and perform scenario analysis
- Automate calculations and portfolio management
- Analyze time-series data and risk metrics
- Work with cashflows, tables, and ledgers natively

Rather than struggling with complex syntax, students can describe financial concepts using clear, finance-native language.

## Creators

- Eleah Joy Melchor (levi00sa)
- Christel Hope Ong (ChristelHope)

---

## Language Features

### Core Language

| Feature | Description |
|---------|-------------|
| **Variables** | LET, SET for declaration and assignment |
| **Functions** | FUNC for reusable code blocks with closures |
| **Control Flow** | IF/ELSE/ELSEIF, WHILE, FOR loops |
| **Lambdas** | Anonymous functions with closure capture |
| **Data Types** | Numbers, Strings, Booleans, Lists, Objects |

### Financial Features

| Feature | Description |
|---------|-------------|
| **Tables** | Structured data with named columns |
| **Cashflows** | DEBIT/CREDIT entries for cash management |
| **Portfolios** | Asset allocation with weights and ledger tracking |
| **Time Series** | TIMESERIES for moving averages and windowed analysis |
| **Finance Functions** | NPV, IRR, PV, FV, CAPM, WACC, VAR, SMA, EMA, AMORTIZE |

### File I/O

| Function | Purpose |
|----------|---------|
| **LOAD_CSV(path)** | Load data from CSV file into TABLE |
| **SAVE_CSV(table, path)** | Write TABLE to CSV file |
| **LOAD_FILE(path)** | Load text content from file |
| **SAVE_FILE(content, path)** | Write text to file |
| **LOAD_JSON(path)** | Load JSON file content |
| **SAVE_JSON(data, path)** | Save object/list as JSON |

### Utility Functions

| Function | Purpose |
|----------|---------|
| **PRINT** | Output to console with multi-arg support |
| **LOG** | Logging with [LOG] prefix |
| **MAP** | Apply function to list elements |
| **FILTER** | Select elements matching predicate |
| **REDUCE** | Accumulate values with function |
| **AGGREGATE** | Sum, average, min, max operations |
| **len()** | List/string length |
| **sum()** | Sum of numeric list |
| **avg()** | Average of numeric list |

---

## Keywords

### Declaration & Control

```
LET         - Variable declaration
SET         - Variable assignment  
FUNC        - Function declaration
RETURN      - Return from function
IF, THEN    - Conditional (THEN optional)
ELSE        - Else branch
ELSEIF      - Else-if branch
END         - Block terminator
WHILE       - While loop
FOR, IN     - For-each loop
DO          - Loop keyword (optional)
INC, DEC    - Increment/decrement statements
```

### Finance

```
TABLE       - Define tabular data
CASHFLOW    - Cash flow block
PORTFOLIO   - Portfolio with assets/weights
DEBIT       - Ledger debit entry
CREDIT      - Ledger credit entry
TIMESERIES  - Time-based windowed data
SCENARIO    - What-if scenario
RUN         - Execute scenario
SIMULATE    - Run simulation
NPV, IRR    - Net present value, internal rate of return
PV, FV      - Present/future value
CAPM, WACC  - Capital asset pricing, weighted avg cost capital
VAR         - Value at risk
SMA, EMA    - Simple/exponential moving average
AMORTIZE    - Amortization schedule
```

### I/O & Utility

```
PRINT       - Print to console
LOG         - Log output
IMPORT      - Load module
EXPORT      - Export values
AS          - Module alias
MAP         - List transformation
FILTER      - List filtering
REDUCE      - List accumulation
AGGREGATE   - Aggregation (SUM, AVG, MIN, MAX)
```

### Operators

```
Arithmetic:    +  -  *  /  %
Comparison:    ==  !=  <  >  <=  >=
Logical:       &&  ||  !
Assignment:    =
```

### Literals

```
Numbers:       42, 3.14, 1.2e6, -50
Strings:       "text", "multi\nline"
Booleans:      true, false
Null:          null
Lists:         [1, 2, 3], [["a"], ["b"]]
Objects:       { x: 10, y: 20 }
Lambdas:       x -> x * 2, (a, b) -> a + b
```

---

## Quick Start Examples

### Basic Variables and Math

```finlite
LET principal = 100000
LET rate = 0.06
LET years = 10

LET fv = FV(rate, years, 0, principal)
PRINT "Future Value:", fv
```

### Financial Calculations

```finlite
LET cashflows = [-100000, 30000, 35000, 40000, 45000]
LET discount_rate = 0.10

LET npv = NPV(cashflows, discount_rate)
LET irr = IRR(cashflows)

PRINT "NPV:", npv
PRINT "IRR:", irr
```

### Tables and Data

```finlite
LET sales = TABLE(
    year: [2020, 2021, 2022, 2023],
    revenue: [100000, 120000, 145000, 175000],
    profit: [15000, 22000, 31000, 42000]
)

PRINT sales

LET avg_profit = avg(sales.profit)
PRINT "Average Profit:", avg_profit
```

### Scenario Analysis

```finlite
LET base_rate = 0.05
LET base_growth = 0.08

SCENARIO Bullish:
    LET rate = 0.04
    LET growth = 0.12
END

SCENARIO Bearish:
    LET rate = 0.08
    LET growth = 0.02
END

RUN Bullish ON valuation_model
RUN Bearish ON valuation_model
```

### Portfolios

```finlite
PORTFOLIO my_portfolio:
    DEBIT stocks 0.60
    DEBIT bonds 0.30
    DEBIT cash 0.10
END

PRINT my_portfolio
```

### File I/O

```finlite
# Load financial data
LET data = LOAD_CSV("sales.csv")
PRINT data

# Process and save
LET result = MAP(data, x -> x * 1.1)
SAVE_CSV(result, "forecast.csv")
```

### Functions and Closures

```finlite
FUNC add(a, b) {
    RETURN a + b
}

LET result = add(10, 5)
PRINT result

# Higher-order function
FUNC apply_twice(fn, x) {
    RETURN fn(fn(x))
}

PRINT apply_twice((y -> y * 2), 5)  # Output: 20
```

### List Operations

```finlite
LET numbers = [1, 2, 3, 4, 5]

# Map: apply function to each element
LET squared = MAP(numbers, x -> x * x)
PRINT squared  # [1, 4, 9, 16, 25]

# Filter: keep elements matching condition
LET evens = FILTER(numbers, x -> x % 2 == 0)
PRINT evens  # [2, 4]

# Reduce: accumulate a single value
LET sum = REDUCE(numbers, (a, x) -> a + x, 0)
PRINT sum  # 15
```

---

## Design Philosophy

FinLite removes the barriers to financial programming by:

1. **Finance-First Syntax** — Concepts like cashflows, portfolios, and scenarios are first-class citizens
2. **Spreadsheet Familiarity** — Tables, rows, and columns work intuitively
3. **Minimal Complexity** — No type declarations, no memory management, no import paths
4. **Built-in Finance** — Common calculations (NPV, IRR, CAPM) included without libraries
5. **Interactive & Scriptable** — Works as both REPL and batch processing

FinLite is **not** Excel (because code is repeatable), **not** Python (because focus is narrow), and **not** R (because it's beginner-friendly).

---

## Project Structure

```
.
├── Scanner.kt                    # Tokenizer
├── TokenType.kt                  # Token definitions
├── Parser.kt                     # Syntax parsing
├── Ast.kt                        # AST node definitions
├── Interpreter.kt                # Evaluation engine
├── Environment.kt                # Variable scoping
├── RuntimeValue.kt               # Runtime type system
├── FinanceRuntime.kt             # Finance computations
├── FinanceParser.kt              # Finance syntax
├── FinanceStatementExecutor.kt   # Finance statement execution
├── FinLiteStandardLib.kt         # Built-in functions
├── Main.kt                       # Entry point
├── test.txt                      # Example programs
└── README.md                     # This file
```

---

## Building & Running

### Compile

```bash
kotlinc *.kt -include-runtime -d build/FinLite.jar
```

### Run Program

```bash
java -jar build/FinLite.jar < program.txt
```

### Example

```bash
# Create a program
echo 'LET x = 10
PRINT "x =", x' > hello.txt

# Run it
java -jar build/FinLite.jar < hello.txt
```

---

## Future Enhancements

- **Multi-currency** support with automatic exchange
- **Date arithmetic** and financial calendars
- **Optimization** for portfolio allocation problems
- **Integration** with external data sources
- **Dashboard** visualization for results
- **Standard library** modules for common strategies

---

## Contributing

This is a CMSC 124 (Programming Languages) course project. Contributions welcome!

---

## License

Educational use. See course guidelines.
