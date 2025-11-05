val keywords = mapOf(
    // Variables & Functions
    "may" to TokenType.VAR,
    "pak" to TokenType.SET,
    "solid" to TokenType.CONST,
    "eksena" to TokenType.FUN,
    "pumapapel" to TokenType.CALL,
    "balik" to TokenType.RETURN,

    // Conditionals
    "kung" to TokenType.IF,
    "hala" to TokenType.ELSE,

    // Loops
    "awra" to TokenType.WHILE,
    "forda" to TokenType.FOR,
    "bet" to TokenType.FOREACH,

    // Control
    "amaccana" to TokenType.BREAK,
    "game" to TokenType.CONTINUE,

    // Booleans
    "omsim" to TokenType.TRUE,
    "charot" to TokenType.FALSE,
    "olats" to TokenType.NULL,

    "dehins" to TokenType.NOT,

    "amp" to TokenType.AND,
    "baka" to TokenType.OR,

    // Object-oriented
    "peg" to TokenType.CLASS,
    "naol" to TokenType.EXTENDS,
    "simula" to TokenType.CONSTRUCTOR,
    "keratin" to TokenType.THIS,

    // IO and error
    "chika" to TokenType.PRINT,
    "DANGEROUS" to TokenType.ERROR,

    // Imports and Exports
    "ermats" to TokenType.IMPORT,
    "erpats" to TokenType.EXPORT
)