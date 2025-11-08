
val keywords = mapOf(
    // Variables & Functions
    "may" to TokenType.VAR,
    "pak" to TokenType.SET,
    "solid" to TokenType.CONST,
    "eksena" to TokenType.FUN,
    "pumapapel" to TokenType.CALL,
    "balik" to TokenType.RETURN,
    
//    "may" to TokenType.SET,
//    "solid" to TokenType.VAL,
//    "eksena" to TokenType.FN,

  // Conditionals
    "kung" to TokenType.IF,
    "hala" to TokenType.ELSE,
  //Loops
    "awra" to TokenType.WHILE,
    "forda" to TokenType.FOR,
    "bet" to TokenType.FOREACH,
    "amaccana" to TokenType.BREAK,
    "game" to TokenType.CONTINUE,
  
    "omsim" to TokenType.TRUE,
    "olats" to TokenType.NULL,
    "charot" to TokenType.FALSE,
    "dehins" to TokenType.BANG,
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

/*

Desc:
 This file defines the mapping of localized language keywords to their
 corresponding TokenType entries. It allows the Scanner to recognize reserved words in the custom dialect (omsim, dehins, etc).

 purpose:
  -to associate language keywords (custom or standard) with token categories
  - to enable support for localized or domain-specific vocabulary
 
  Ex mappings:
 -"omsim"-> TokenType.TRUE
 -"charot"-> TokenType.FALSE
 -"dehins"-> TokenType.BANG (logical NOT)
 
 importance:
  this modular keyword dictionary keeps the Scanner implementation clean, and allows easy expansion or localization of the language vocabulary.
 */
