// Lexical Scanner 
/** Implement a complete lexical scanner that can take source code written in a simple programming language and convert it into a stream of tokens. 
Your scanner will need to handle:

- Single-character tokens (parentheses, operators, punctuation)     --> Done
- Multi-character operators (like == and <=)
- String literals enclosed in quotes --> Done
- Numeric literals (both integers and decimals) --> Done
- Identifiers and keywords --> Done
- Comments (which should be ignored) --> Done
- Whitespace handling and error reporting                          --> Done

The expected, testable output for this laboratory activity is a Read-eval-print loop (REPL) in the command line. 
This has your scanner implementation at its heart. 
The scanner must also provide some form of error handling for invalid or malformed lexemes. 

you're not allowed to use regular expressions in your implementation.

Steps:
1. Define token types using an enum
2. Create a token class
3. Create the scanner
4. Create the REPL for interactive testing
**/

                                                                                                // Define all possible token types our language understands
enum class TokenType {
    IDENTIFIER, STRING, NUMBER,                                                                      // Literals

    SET, FN, VAL, CLASS, IF, ELSE, FOR, WHILE, RETURN, AND, OR, NOT, TRUE, FALSE,  // Keywords

    EQUAL_EQUAL, LESS, GREATER, MINUS_MINUS, PLUS_PLUS, LESS_EQUAL, 
    GREATER_EQUAL, BANG_EQUAL, BANG, EQUAL,                                                         // Operators (single and multi-character)

    LEFT_PAREN, RIGHT_PAREN, PLUS, MINUS, LEFT_BRACE, RIGHT_BRACE, LEFT_BRACKET, RIGHT_BRACKET, 
    COMMA, SEMICOLON, DOT, SLASH, STAR, DOUBLE_QUOTE, COLON,                                        // Symbols

    NULL, EOF, ERROR,                                                                               // Special markers
    
    INDENT, DEDENT, NEWLINE                                                                         // Indentation tokens
}
