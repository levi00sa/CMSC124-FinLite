
val keywords = mapOf(
    // Variables & Functions
    "may" to TokenType.MAY,
    "pak" to TokenType.PAK,
    "solid" to TokenType.SOLID,
    "eksena" to TokenType.EKSENA,
    "pumapapel" to TokenType.PUMAPAPEL,
    "balik" to TokenType.BALIK,

  // Conditionals
    "kung" to TokenType.KUNG,
    "hala" to TokenType.HALA,
  //Loops
    "awra" to TokenType.AWRA,
    "forda" to TokenType.FORDA,
    "bet" to TokenType.BET,
    "amaccana" to TokenType.AMACCANA,
    "game" to TokenType.GAME,
  
    "omsim" to TokenType.OMSIM,
    "olats" to TokenType.OLATS,
    "charot" to TokenType.CHAROT,
    "dehins" to TokenType.DEHINS,
    "amp" to TokenType.AMP,
    "baka" to TokenType.BAKA,

    // Object-oriented
    "peg" to TokenType.PEG,
    "naol" to TokenType.NAOL,
    "simula" to TokenType.SIMULA,
    "keratin" to TokenType.KERATIN,

    // IO and error
    "chika" to TokenType.CHIKA,
    "DANGEROUS" to TokenType.DANGEROUS,

    // Imports and Exports
    "ermats" to TokenType.ERMATS,
    "erpats" to TokenType.ERPATS
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
