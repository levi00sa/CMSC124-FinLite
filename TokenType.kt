enum class TokenType {
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,               //single-chara tokens
    LEFT_BRACKET, RIGHT_BRACKET,
    COMMA, DOT, MINUS, PLUS, SEMICOLON, SLASH, STAR, COLON,

    BANG, BANG_EQUAL,                                             //1 or 2 chara tokens
    EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL, 

    IDENTIFIER, STRING, NUMBER,                                  //literals

    AND, CLASS, ELSE, FALSE, FUN, FOR, IF, NULL, OR,            //keywords english base
    PRINT, RETURN, SUPER, THIS, TRUE, VAR, WHILE, EOF,

    MAY, PAK, SOLID, EKSENA, PUMAPAPEL, BALIK, KUNG, HALA,      //Filipino keywords
    AWRA, FORDA, BET, AMACCANA, GAME, OMSIM, CHAROT, OLATS,
    DEHINS, AMP, BAKA, PEG, NAOL, SIMULA, KERATIN,
    CHIKA, DANGEROUS, ERMATS, ERPATS,
    AKO, SELFIE,

    EXTENDS, SET, CONST, CALL, FOREACH, BREAK, CONTINUE, NOT,          //other reserved keywords or mappings
    IN, NEWLINE, INDENT, DEDENT, ERROR,
    CONSTRUCTOR, IMPORT, EXPORT,
  
    EOF
}

/*
 file contains the TokenType enumeration -->which lists all possiblecategories of tokens recognized by the language (operators,literals,keywords,punctuation,etc.)
 
 Purpose:
  -to classify each token produced by the Scanner
  -to provide a consistent reference of all valid token types across modules
 
 Ex TokenType entries:
 -Arithmetic: PLUS, MINUS, STAR, SLASH
 -Comparison: LESS, GREATER, EQUAL_EQUAL
 -Logical: AND, OR, BANG    (mapped from dehins)
 -Literals: IDENTIFIER, STRING, NUMBER
 -Keywords: IF, ELSE, TRUE, FALSE, etc.
 
 importance:
  parser & scanner rely on this enumeration to maintain consistency in recognizing & interpreting diff language constructs
 */
