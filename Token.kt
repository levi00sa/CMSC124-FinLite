package finlite

data class Token(                                                                              // token holds info about each recognized piece of source code
    val type: TokenType,                                                                           // category/type of token (from enum above)
    val lexeme: String,                                                                            // the actual text matched from the source
    val literal: Any?,                                                                             // literal value (e.g. number value, string contents), null if none
    val line: Int,                                                                                 // line number in source (for error reporting)
)


