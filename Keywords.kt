
package finlite

val keywords: Map<String, TokenType> = mapOf(

    // Core keywords
    "let" to TokenType.LET,
    "set" to TokenType.SET,

    "if" to TokenType.IF,
    "then" to TokenType.THEN,
    "else" to TokenType.ELSE,
    "elseif" to TokenType.ELSEIF,
    "end" to TokenType.END,

    "print" to TokenType.PRINT,
    "log" to TokenType.LOG,

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
