package finlite

enum class TokenType {

    // Single-character tokens
    LEFT_PAREN, RIGHT_PAREN,
    LEFT_BRACKET, RIGHT_BRACKET,
    LEFT_BRACE, RIGHT_BRACE,
    COMMA, DOT, COLON,

    PLUS, MINUS, STAR, SLASH, PERCENT, CARET,

    // One or two character tokens
    EQUAL, EQUAL_EQUAL,
    BANG, BANG_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,
    AND_AND, OR_OR,

    // Literals
    IDENTIFIER,
    NUMBER,
    STRING,
    MULTILINE_STRING,
    MONEY,
    DATE,
    TRUE, FALSE,
    NULL,

    // Keywords
    LET, SET,
    IF, THEN, ELSE, ELSEIF, END,
    PRINT, LOG,

    TABLE, CASHFLOW, TIMESERIES,
    SCENARIO, RUN, ON, SIMULATE,
    PORTFOLIO, ENTRY, DEBIT, CREDIT, LEDGER,

    FROM, TO, STEP,

    // Finance built-ins
    NPV, IRR, PV, FV, WACC, CAPM, VAR,
    SMA, EMA, AMORTIZE,

    // Logical words
    AND, OR, NOT,

    // Misc
    NEWLINE, INDENT, DEDENT, EOF, ERROR
} 