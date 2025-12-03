FinLite Programming Language


Creator
Eleah Joy Melchor
Christel Hope Ong


[GRAMMAR]
    Lab2 CFG:
        program        → declaration* EOF ;
        declaration    → "LET" IDENTIFIER "=" expression
                    | statement ;
        statement      → "SET" IDENTIFIER "=" expression
                    | "PRINT" expression
                    | ifStmt
                    | scenarioStmt
                    | expression ";" ;
        ifStmt         → "IF" expression "THEN" statement* "END" ;
        scenarioStmt   → "SCENARIO" IDENTIFIER ":" declaration* "END" ;
        expression     → equality ;
        equality       → comparison ( ( "==" | "!=" ) comparison )* ;
        comparison     → term ( ( "<" | "<=" | ">" | ">=" ) term )* ;
        term           → factor ( ( "+" | "-" ) factor )* ;
        factor         → unary ( ( "*" | "/" | "%" ) unary )* ;

        unary          → ( "!" | "-" ) unary
                    | primary ;

        primary        → NUMBER
                    | STRING
                    | TRUE
                    | FALSE
                    | NULL
                    | IDENTIFIER ( "." IDENTIFIER )?
                    | tableLiteral
                    | "(" expression ")" ;

        tableLiteral   → "TABLE" "(" field ( "," field )* ")" ;
        field          → IDENTIFIER ":" "[" exprList "]" ;
        exprList       → expression ( "," expression )* ;

                   


Language Overview [Provide a brief description of your programming language - what it's a designed for, its main characteristics]
FinLite is a domain-specific programming language designed specifically for finance students and beginner analysts. It aims to simplify learning financial modeling by combining the familiarity of spreadsheets with essential programming concepts. FinLite removes the complexity of traditional languages while allowing its users to automate calculations, build models, and perform scenario and risk analysis using a clear, finance-native syntax.

The language supports the following:
Variables, expressions, and assignments
Tables and cashflow modeling
Financial operations
Conditionals and control flow
Scenarios, simulations, and time-series data
Portfolio and ledger operations
Easy printing, logging, and validation

Keywords [List all reserved words that cannot be used as identifiers - include the keyword and a brief description of its purpose]

    LET                 - used to declare a variable
    SET                 - Used to assign or reassign a value to an existing variable
    TABLE               - used to define a table
    ROW                 - Used to define a single row inside a table
    IF, ELSE, ELSEIF    - conditionals
    RETURN              - returns a value from function
    END                 - Used to close blocks
    IMPORT, EXPORT      - Imports and exports
    PRINT               - prints a value to console
    LOG                 - Writes to logs or report sections
    ASSERT              - checks correctness of financial and logical conditions
    ERROR               - Raise an error
    TRUE, FALSE         - Boolean literals
    NULL                - Empty value
    CASHFLOW            - Define cashflow block
    SCENARIO            - Declare scenario
    RUN	Execute         - scenario/model
    DISCOUNT            - Perform discounting
    YEAR                - Built-in for time-index rows
    RATE                - Reserved inside finance operations
    PORTFOLIO           - Portfolio block
    ENTRY               - Accounting entry
    DEBIT / CREDIT      - Ledger operations
    LEDGER              - Interact with ledger
    FROM / TO / STEP    - Range specifications
    SENSITIVITY	        - Generate sensitivity tables
    SIMULATE            - Run simulations
    TIMESERIES          - Time-based dataset
    DATE                - Date literal/index
    FILTER              - Filter table rows
    MAP	Row-wise        - transform
    AGGREGATE           - Sum/avg/min/max

Operators [List all operators organized by category (arithmetic, comparison, logical, assignment, etc.)]

    Addition
    +
    Subtraction
    -
    Multiplication
    *
    Division
    /
    Remainder/Rem
    %

    equal
    ==
    Not equal
    !=
    Less than
    <
    Greater than
    >
    Less than or equal to
    <=
    Greater than or equal to
    >=

    Not
    !
    Logical And
    &&
    Logical Or
    ||

    Equal
    =
    Multiplication assignment
    *=
    Addition assignment
    +=
    Subtraction assignment
    -=
    Division assignment
    /=

    Increment
    ++
    Decrement
    –
    Colon
    :


Literals [Describe the format and syntax for each type of literal value (e.g., numbers, strings, characters, etc.) your language supports]
    Integers: 42, 4.2, 0, -42, -1500.75, 0.12, 1.2e6, 5e-3
    Money: 1000 PHP, 2000 USD, 5000 JPY
    Strings: use double-quotations with \n and \t
    Multi-line string:
    """
    This is a
    multi-line string
    """
    Characters: single characters inside single quotes
    Boolean literals: TRUE, FALSE
    Null: NULL
    Date literals: YYYY-MM-DD
    Time series literals: 
    TIMESERIES(
        start="2024-01-01",
        end="2024-12-31",
        freq="monthly"
    )
    Table literals: 
    TABLE(
        YEAR: [1, 2, 3],
        INFLOW: [50000, 60000, 70000],
        OUTFLOW: [20000, 30000, 35000]
    )
    Collections: 
        Arrays: [a, b, c], [[1,2],[3,4]]


Identifiers [Define the rules for valid identifiers (variable names, function names, etc.) and whether they are case-sensitive]
    
    Rules for variable names: 
        Start with A-Z, a-z, and underscore
        May contain letters, digits, underscores
        **Case sensitive** for simplifying implementation and reducing ambiguity 
        Naming conventions: lowerCamelCase for variables and functions, UPPERCASE for constants
        Identifiers cannot contain dots .

Comments [Describe the syntax for comments and whether nested comments are supported]
    Single-line comments: # comment here
    Multi-line comments: 
            ###
            block comment
            ###

Syntax Style [Describe whether whitespace is significant, how statements are terminated, and what delimiters are used for blocks and grouping]
Whitespace insignificant

    Statements end with semicolon or newline
    Blocks end with END
    Uses () and [] for grouping
    Indentation optional (non-significant)
    Imports: IMPORT lib FROM "lib"

Sample Code [Provide a few examples of valid code in your language to demonstrate the syntax and features]

    Variable Declarations
        LET rate = 0.12 
        LET years = 5 
        LET principal = 100000 
        LET futureValue = FV(rate, years, principal, 0) PRINT futureValue

    Cashflow Table + NPV
        LET CF = TABLE(
        YEAR:    [1, 2, 3, 4],
        INFLOW:  [50000, 60000, 70000, 80000],
        OUTFLOW: [20000, 25000, 30000, 35000]
        )
        LET rate = 0.12
        LET netCF = CF.INFLOW - CF.OUTFLOW
        LET npvValue = NPV(netCF, rate)


        PRINT "Net Present Value: " + npvValue

    Conditionals
        LET CF = TABLE(
            YEAR:    [1, 2, 3, 4],
            INFLOW:  [50000, 60000, 70000, 80000],
            OUTFLOW: [20000, 25000, 30000, 35000]
        )


        LET rate = 0.12
        LET netCF = CF.INFLOW - CF.OUTFLOW
        LET npvValue = NPV(netCF, rate)


        IF npv > 0 THEN
            PRINT "Project is profitable. Accept."
        ELSE
        PRINT "Project is not profitable. Reject."
        END


    Scenario
        LET rate = 0.10
        LET growth = 0.08

        SCENARIO Bull {
            LET rate = 0.09
            LET growth = 0.12
        }

        SCENARIO Bear {
            LET rate = 0.13
            LET growth = 0.03
        }

        RUN Bull ON valuation_model
        RUN Bear ON valuation_model

    Full Example
        LET CF = TABLE(
    YEAR:    [1, 2, 3],
    INFLOW:  [80000, 85000, 90000],
    OUTFLOW: [30000, 35000, 40000]
    )

    LET netCF = CF.INFLOW - CF.OUTFLOW

    LET rate = 0.11
    LET npv = NPV(netCF, rate)

    PRINT "NPV =" + npv

    IF npv > 0 THEN
        PRINT "Decision: ACCEPT project."
    ELSE
        PRINT "Decision: REJECT project."
    END





Design Rationale [Explain the reasoning behind your design choices]
    Finance students often struggle with syntax, complex data structures, learning Python/R, and fear of “traditional coding”. With the current advancement in finance tech, it is essential to aid them in learning and understanding financial modeling, risk analysis, model automations, data analysis, data manipulation, and more. Finance students typically think in cash flows, formulas, tables, transactions, scenarios, and risk, so we designed FinLite to be spreadsheet-native or Excel-like. By combining the familiarity of spreadsheets with the structure and clarity of code, FinLite makes financial modeling more intuitive and accurate. Instead of manually handling long formulas or error-prone cell references, students can describe financial scenarios, transactions, portfolios, and cash flow using readable and financial-friendly syntax.
    The language includes built-in functions for common tasks–NPV, IRR, discounting, amortization, risk metrics–so users can compute advanced financial values without reinventing formulas. 

    The language is not:
        Excel - because it is scriptable and repeatable
        Python - because it is simple and domain-focused
    
    The language supports scenarios, sensitivity analysis, and time series operations so students can perform “what-if” analyses that require complicated Excel calculations. More advanced features such as multi-currency, date systems, and full portfolio simulations are planned for future versions.
 

