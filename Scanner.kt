class Scanner(private val source: String) {                                                      // scanner takes raw source code & breaks it into tokens
    private var start = 0                                                                           // index where the current token begins
    private var current = 0                                                                         // index of the character we are currently scanning
    private var line = 1                                                                            // line counter (helps w error reporting)
    private val tokens = mutableListOf<Token>()                                                     // dynamic list of tokens found
    
                                                                                                    // Indentation tracking
    private val indentStack = mutableListOf<Int>()                                                  // stack to track indentation levels
    private var atStartOfLine = true                                                                // whether we're at the start of a new line

    fun scanTokens(): List<Token> {                                                                 // scan the entire source into tokens
        while (!isAtEnd()) {                                                                       // loop until all characters are consumed
            start = current                                                                        // mark beginning of the next token
                                                                                                    // Handle indentation at start of line
            if (atStartOfLine) {
                handleIndentation()
            }
            scanToken()                                                                            // recognize the next token
        }
                                                                                                    // Add any remaining DEDENT tokens
        while (indentStack.isNotEmpty()) {
            indentStack.removeAt(indentStack.size - 1)
            tokens.add(Token(TokenType.DEDENT, "", null, line))
        }
        
        tokens.add(Token(TokenType.EOF, "", null, line))                                           // add special EOF token at the end
        return tokens
    }

    private fun isAtEnd(): Boolean {                                                                 // to check if we've read all characters
        return current >= source.length
    }

    private fun advance(): Char {                                                                     // to consume one character and move forward
        return source[current++]
    }

    private fun addToken(type: TokenType) {                                                          // add token w/o literal value
        addToken(type, null)
    }

    private fun addToken(type: TokenType, literal: Any?) {                                          // add token w optional literal value
        val text = source.substring(start, current)                                                 // extract substring for the lexeme
        tokens.add(Token(type, text, literal, line))                                               // push into token list
    }
    
    private fun handleIndentation() {
        var indentLevel = 0
        while (peek() == ' ' || peek() == '\t') {
            if (peek() == ' ') {
                indentLevel++
            } else if (peek() == '\t') {
                indentLevel += 4 // Treat tab as 4 spaces
            }
            advance()
        }
        
        // Skip if line is empty (only whitespace)
        if (peek() == '\n' || isAtEnd()) {
            atStartOfLine = false
            return
        }
        
        val currentIndent = if (indentStack.isEmpty()) 0 else indentStack.last()
        
        if (indentLevel > currentIndent) {
                                                                                                        // Indent
            indentStack.add(indentLevel)
            tokens.add(Token(TokenType.INDENT, "", indentLevel, line))
        } else if (indentLevel < currentIndent) {
                                                                                                        // Dedent
            while (indentStack.isNotEmpty() && indentStack.last() > indentLevel) {
                indentStack.removeAt(indentStack.size - 1)
                tokens.add(Token(TokenType.DEDENT, "", null, line))
            }
            if (indentStack.isEmpty() || indentStack.last() != indentLevel) {
                println("Error: inconsistent indentation at line $line")
            }
        }
        
        atStartOfLine = false
    }

    private fun scanToken() {                                                                       // determine what token the curr chara belongs to
        val c = advance()                                                                           // to get the next character using advance

        when (c) {
            '(' -> { atStartOfLine = false; addToken(TokenType.LEFT_PAREN) }
            ')' -> { atStartOfLine = false; addToken(TokenType.RIGHT_PAREN) }
            '{' -> { atStartOfLine = false; addToken(TokenType.LEFT_BRACE) }
            '}' -> { atStartOfLine = false; addToken(TokenType.RIGHT_BRACE) }
            '[' -> { atStartOfLine = false; addToken(TokenType.LEFT_BRACKET) }
            ']' -> { atStartOfLine = false; addToken(TokenType.RIGHT_BRACKET) }
            ',' -> { atStartOfLine = false; addToken(TokenType.COMMA) }
            '.' -> { atStartOfLine = false; addToken(TokenType.DOT) }
            '-' -> { atStartOfLine = false; addToken(TokenType.MINUS) }
            '+' -> { atStartOfLine = false; addToken(TokenType.PLUS) }
            ';' -> { atStartOfLine = false; addToken(TokenType.SEMICOLON) }
            '*' -> { atStartOfLine = false; addToken(TokenType.STAR) }
            '=' -> {
                atStartOfLine = false
                if (match('=')) {
                    addToken(TokenType.EQUAL_EQUAL)
                } else {
                    addToken(TokenType.EQUAL)
                }
            }
            '!' -> {
                atStartOfLine = false
                if (match('=')) {
                    addToken(TokenType.BANG_EQUAL)
                } else {
                    addToken(TokenType.BANG)
                }
            }
            '<' -> {
                atStartOfLine = false
                if (match('=')) {
                    addToken(TokenType.LESS_EQUAL)
                } else {
                    addToken(TokenType.LESS)
                }
            }
            '>' -> {
                atStartOfLine = false
                if (match('=')) {
                    addToken(TokenType.GREATER_EQUAL)
                } else {
                    addToken(TokenType.GREATER)
                }
            }
            ':' -> { atStartOfLine = false; addToken(TokenType.COLON) }
            '"' -> { atStartOfLine = false; string() }
            '/' -> {
                atStartOfLine = false
                if (match ('/')) { 
                    while (peek() != '\n' && !isAtEnd()) {
                        advance()
                    }                                          
                } else if (match('-')) {
                    while (true) {
                        if (isAtEnd()){
                            println("Error with $c at line $line")
                            return
                        }
                        if (peek() == '-' && peekNext() == '/') {
                            advance() //consume '-'
                            advance() //consume '/'
                            break     //skip comment
                        }
                        if (peek() == '\n') line++
                        advance()
                    }
                } else {
                    addToken(TokenType.SLASH)
                } 
            }
            
            ' ', '\r', '\t' -> {
                if (atStartOfLine) {
                    // Count indentation at start of line
                    handleIndentation()
                }
            }
            '\n' -> {
                line++
                atStartOfLine = true
                tokens.add(Token(TokenType.NEWLINE, "", null, line - 1))
            }

            else -> {                                                                               // if unrecognized character, we report it
                atStartOfLine = false                                                               // no longer at start of line
                if (c.isDigit()) {
                    number()
                } else if (c.isLetter() || c == '_' || c == '$') {
                    identifier()
                } else {
                    println("Error with $c at line $line")
                }
            }
        }
    }
    private fun identifier() {
        atStartOfLine = false
        while (peek().isLetterOrDigit() || peek() == '_') 
            advance()
        val text = source.substring(start, current)
        when (text) {
            "true" -> addToken(TokenType.TRUE, true)
            "false" -> addToken(TokenType.FALSE, false)
            "null" -> addToken(TokenType.NULL, null)
            "nil" -> addToken(TokenType.NULL, null)
            else -> {
                val type = keywords[text] ?: TokenType.IDENTIFIER
                 addToken(type, text)
            }
        }
    }
    private fun number() {
        atStartOfLine = false
        while (peek().isDigit()) advance()
        if (peek() == '.' && peekNext().isDigit()) {                                               // Handling float
            advance()                                                                               // Consume the '.'
            while (peek().isDigit()) advance()
        }
        addToken(TokenType.NUMBER, source.substring(start, current).toDouble())
    }

    private fun peek(): Char {
        if (isAtEnd()) return '\u0000'
        return source[current]
    }

    private fun string() {
        atStartOfLine = false
        while (peek() != '"' && !isAtEnd()){
            if (peek() == '\n') line++
            advance()
        }
        if (isAtEnd()) {
            println("Error string at line $line")
            return
        }
        advance()

        val value = source.substring(start + 1, current - 1)
        val lexeme = source.substring(start, current)
        tokens.add(Token(TokenType.STRING, lexeme, value, line))
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false
        current++
        return true
    }

    private fun peekNext(): Char {
        if (current + 1 >= source.length) return '\u0000'
        return source[current + 1]
    }
}

/*
this file implements the lexical analysis phase of the language processor.
it takes raw source code as input and breaks it into a sequence of tokens that represent the smallest meaningful units (keywords, literals, operators, symbols)
 
 Purpose:
  -to identify valid lexemes and classify them into token types
  -to ignore whitespace and comments
  -to provide clear error messages for invalid or unexpected characters
 
  Key functions:
  -scanTokens(): returns a list of tokens from the source input
  -scanToken(): handles the recognition of each individual token
  -string(), number(), identifier(): handle scanning of literals and keywords
 
 implementation notes:
  -uses manual character scanning instead of regex (per lab guidelines)
  -supports both single- and multi-character operators (e.g., ==, <=, !=)
  -recognizes localized keywords such as "dehins" (for logical NOT)
 */
