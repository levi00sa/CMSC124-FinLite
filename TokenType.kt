package finlite

// Token data class
data class Token(
    val type: TokenType,
    val lexeme: String,
    val literal: Any?,
    val line: Int,
)

// Keywords map
val keywords: Map<String, TokenType> = mapOf(
    // Core keywords
    "let" to TokenType.LET,
    "set" to TokenType.SET,

    "if" to TokenType.IF,
    "then" to TokenType.THEN,
    "while" to TokenType.WHILE,
    "for" to TokenType.FOR,
    "in" to TokenType.IN,
    "end" to TokenType.END,
    "else" to TokenType.ELSE,
    "elseif" to TokenType.ELSEIF,
    "do" to TokenType.DO,

    "print" to TokenType.PRINT,
    "log" to TokenType.LOG,
    "return" to TokenType.RETURN,

    // Literals 
    "true" to TokenType.TRUE,
    "false" to TokenType.FALSE,
    "null" to TokenType.NULL,

    // Finance structures
    "table" to TokenType.TABLE,
    "cashflow" to TokenType.CASHFLOW,
    "timeseries" to TokenType.TIMESERIES,

    "scenario" to TokenType.SCENARIO,
    "run" to TokenType.RUN,
    "on" to TokenType.ON,
    "simulate" to TokenType.SIMULATE,
    "runs" to TokenType.RUNS,

    "portfolio" to TokenType.PORTFOLIO,
    "entry" to TokenType.ENTRY,
    "debit" to TokenType.DEBIT,
    "credit" to TokenType.CREDIT,
    "ledger" to TokenType.LEDGER,

    "from" to TokenType.FROM,
    "to" to TokenType.TO,
    "step" to TokenType.STEP,

    // Financial functions
    "npv" to TokenType.NPV,
    "irr" to TokenType.IRR,
    "pv" to TokenType.PV,
    "fv" to TokenType.FV,
    "wacc" to TokenType.WACC,
    "capm" to TokenType.CAPM,
    "var" to TokenType.VAR,

    "sma" to TokenType.SMA,
    "ema" to TokenType.EMA,
    "amortize" to TokenType.AMORTIZE,

    // Logical words
    "and" to TokenType.AND,
    "or" to TokenType.OR,
    "not" to TokenType.NOT
)

// Token type enum
enum class TokenType {

    // Single-character tokens
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACKET, RIGHT_BRACKET, LEFT_BRACE, RIGHT_BRACE, COMMA, DOT, COLON, SLASH,

    PLUS, MINUS, STAR, PERCENT, CARET,
    ARROW,

    // One or two character tokens
    EQUAL, EQUAL_EQUAL, BANG, BANG_EQUAL, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL, AND_AND, OR_OR,

    // Literals
    IDENTIFIER, NUMBER, STRING,MULTILINE_STRING,MONEY,DATE,TRUE, FALSE,NULL,

    // Keywords
    LET, SET,IF, THEN, WHILE, FOR, ELSE, ELSEIF, END,PRINT, LOG, RETURN,

    TABLE, CASHFLOW, TIMESERIES,COMPARE,SCENARIO, RUN, ON, SIMULATE, RUNS,PORTFOLIO, ENTRY, DEBIT, CREDIT, LEDGER,

    FROM, TO, STEP, IN, DO,

    // Finance built-ins
    NPV, IRR, PV, FV, WACC, CAPM, VAR,SMA, EMA, AMORTIZE,

    // Logical words
    AND, OR, NOT,

    // Misc
    NEWLINE, INDENT, DEDENT, EOF, ERROR
} 