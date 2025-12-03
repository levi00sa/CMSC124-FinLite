package finlite

class Scanner(private val source: String) {
    val tokens = mutableListOf<Token>()
    private var start = 0                // start index of current lexeme
    private var current = 0              // index currently being read
    private var line = 1                 // line counter (for error reporting)


    private val indentStack = ArrayDeque<Int>()  // Track indentation levels
    private var indentLevel = 0
    private var atStartOfLine = true     // Track if we're at start of a line
    private var parenDepth = 0           // Track parentheses nesting
    private var bracketDepth = 0         // Track bracket nesting
    private var braceDepth = 0           // Track brace nesting

    fun scanTokens(): List<Token> {
        indentStack.addLast(0)

        while (!isAtEnd()) {
            start = current
            scanToken()
        }

        addToken(TokenType.EOF)

        return tokens
    }

    private fun scanToken() {
        
        if (atStartOfLine) {
            handleIndentation()
            atStartOfLine = false
        }
        val c = advance()
        when (c) {

            // Delimiters
            '(' -> {parenDepth++; addToken(TokenType.LEFT_PAREN)}
            ')' -> {parenDepth--;addToken(TokenType.RIGHT_PAREN)}
            '[' -> {bracketDepth++;addToken(TokenType.LEFT_BRACKET)}
            ']' -> {bracketDepth--;addToken(TokenType.RIGHT_BRACKET)}
            '{' -> {braceDepth++;addToken(TokenType.LEFT_BRACE)}
            '}' -> {braceDepth--;addToken(TokenType.RIGHT_BRACE)}
            ',' -> addToken(TokenType.COMMA)
            ':' -> addToken(TokenType.COLON)
            '.' -> addToken(TokenType.DOT)
            // Arithmetic
            '+' -> addToken(TokenType.PLUS)
            '-' -> addToken(TokenType.MINUS)
            '*' -> addToken(TokenType.STAR)
            '%' -> addToken(TokenType.PERCENT)
            '^' -> addToken(TokenType.CARET)

            // Slash / comments
            '/' -> {
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) advance()
                } else if (match('*')) {
                    handleBlockComment()
                } else {
                    addToken(TokenType.SLASH)
                }
            }
            '#' -> {
                while (peek() != '\n' && !isAtEnd()) advance()
            }

            // Comparison
            '=' -> addToken(if (match('=')) TokenType.EQUAL_EQUAL else TokenType.EQUAL)
            '!' -> addToken(if (match('=')) TokenType.BANG_EQUAL else TokenType.BANG)
            '<' -> addToken(if (match('=')) TokenType.LESS_EQUAL else TokenType.LESS)
            '>' -> addToken(if (match('=')) TokenType.GREATER_EQUAL else TokenType.GREATER)

            // Logical operators
            '&' -> {
                if (match('&')) addToken(TokenType.AND_AND)
                else addToken(TokenType.ERROR, "&")
            }
            '|' -> {
                if (match('|')) addToken(TokenType.OR_OR)
                else addToken(TokenType.ERROR, "|")
            }

            // Strings
            '"' -> {
                if (match('"') && match('"')) multilineString()
                else string()
            }

            // Whitespace
            ' ', '\r', '\t' -> {/*ignored*/}
            
            // Newline
            '\n' -> {
                line++
                addToken(TokenType.NEWLINE)
                atStartOfLine = true
                start = current
                return
            }

            // Literals & identifiers
            else -> when {
                c.isDigit() -> numberOrMoneyOrDate()
                c.isLetter() || c == '_' -> identifierOrKeywordOrMoney()
                else -> {
                    error("Unexpected character '$c'")
                    addToken(TokenType.ERROR, c.toString())
                }
            }
        }
    }

    // Basic helpers
    private fun isAtEnd(): Boolean = current >= source.length

    private fun advance(): Char = source[current++]

    private fun peek(): Char =
        if (isAtEnd()) '\u0000' else source[current]

    private fun peekNext(): Char =
        if (current + 1 >= source.length) '\u0000' else source[current + 1]

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false
        current++
        return true
    }

    private fun addToken(type: TokenType, literal: Any? = null) {
        val text = source.substring(start, current)
        tokens.add(Token(type, text, literal, line))
    }

    private fun handleIndentation() {
        // Don't track indentation if we're inside parentheses, brackets, or braces
        if (parenDepth > 0 || bracketDepth > 0 || braceDepth > 0) return
        
        var spaces = 0
        var i = current

        while (i < source.length) {
            when (source[i]) {
                ' '  -> { spaces++; i++ }
                '\t' -> { spaces += 4; i++ }
                else -> break
            }
        }

        current = i
        start = current

        if (spaces > indentStack.last()) {
            indentStack.addLast(spaces)
            addToken(TokenType.INDENT)
        } else {
            while (spaces < indentStack.last()) {
                indentStack.removeLast()
                addToken(TokenType.DEDENT)
            }
        }
        
        if (peek() == '\n' || isAtEnd()) return
        }

    // Block comment scanning
    private fun handleBlockComment() {
        while (!isAtEnd()) {
            if (peek() == '*' && peekNext() == '/') {
                advance()
                advance()
                return
            }
            if (peek() == '\n') line++
            advance()
        }
        error("Unterminated block comment")
    }

    // NUMBERS, MONEY, DATES
    private fun numberOrMoneyOrDate() {

        while (peek().isDigit() || peek() == '_') advance()
        // Handle commas only when followed by digits (for thousand separators)
        while (peek() == ',' && peekNext().isDigit()) {
            advance() // consume comma
            while (peek().isDigit() || peek() == '_') advance()
        }

        // Decimal fraction
        if (peek() == '.' && peekNext().isDigit()) {
            advance()
            while (peek().isDigit() || peek() == '_') advance()
            // Handle commas in decimal part only when followed by digits
            while (peek() == ',' && peekNext().isDigit()) {
                advance() // consume comma
                while (peek().isDigit() || peek() == '_') advance()
            }
        }

        val raw = source.substring(start, current)
        val normalized = raw.replace(",", "").replace("_", "")

        // DATE literal check: YYYY-MM-DD
        if (isDateLiteral()) {
            val dateText = source.substring(start, start + 10)
            current = start + 10
            addToken(TokenType.DATE, dateText)
            return
        }

        val numericValue = normalized.toDouble()

        // currency suffix (e.g., "1000 USD")
        skipWhitespace()
        val suffix = tryParseCurrencySuffix()
        if (suffix != null) {
            addToken(TokenType.MONEY, FinLiteMoneyLiteral(suffix, numericValue))
            return
        }

        // Plain number
        addToken(TokenType.NUMBER, numericValue)
    }

    private fun tryParseCurrencySuffix(): String? {
        if (!peek().isLetter()) return null

        val isoStart = current
        while (peek().isLetter()) advance()

        val iso = source.substring(isoStart, current).uppercase()

        return if (isIsoCurrency(iso)) iso else null
    }

    private fun isIsoCurrency(s: String): Boolean {
        return s in listOf("USD", "PHP", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "CNY")
    }

    private fun isDateLiteral(): Boolean {
        if (start + 9 >= source.length) return false
        val segment = source.substring(start, start + 10)
        return Regex("""\d{4}-\d{2}-\d{2}""").matches(segment)
    }

    // IDENTIFIERS, KEYWORDS, ISO MONEY PREFIXES
    private fun identifierOrKeywordOrMoney() {

        while (peek().isLetterOrDigit() || peek() == '_') advance()

        val text = source.substring(start, current)
        val lower = text.lowercase()

        // keyword match
        val keyword = keywords[lower]
        if (keyword != null) {
            addToken(keyword)
            return
        }

        // MONEY prefix (e.g., "USD 1000")
        if (isIsoCurrency(text.uppercase())) {
            skipWhitespace()
            val valueStart = current
            if (peek().isDigit()) {
                numberOrMoneyOrDate()
                val last = tokens.removeLast()
                if (last.type == TokenType.NUMBER) {
                    val amount = last.literal as Double
                    addToken(TokenType.MONEY, FinLiteMoneyLiteral(text.uppercase(), amount))
                } else {
                    error("Currency prefix must be followed by number")
                }
                return
            }
        }

        // Regular identifier
        addToken(TokenType.IDENTIFIER, text)
    }

    // STRINGS
    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++
            advance()
        }
    // report missing quote in string
        if (isAtEnd()) {
            error("Unterminated string literal at line $line: missing closing quote")
            addToken(TokenType.ERROR, "Unterminated string")
            return
        }

        advance()

        val content = source.substring(start + 1, current - 1)
        addToken(TokenType.STRING, content)
    }


    private fun multilineString() {
        // We have already consumed """.
        while (!isAtEnd()) {
            if (peek() == '"' && peekNext() == '"'
                && source.getOrNull(current + 2) == '"'
            ) {
                advance(); advance(); advance() // consume """
                val content = source.substring(start + 3, current - 3)
                addToken(TokenType.MULTILINE_STRING, content)
                return
            }
            if (peek() == '\n') line++
            advance()
        }

        error("Unterminated multiline string at line $line: missing closing triple quotes")
        addToken(TokenType.ERROR, "Unterminated multiline string")
    }

    // Utility: skip whitespace (not newline)
    private fun skipWhitespace() {
        while (peek() == ' ' || peek() == '\t' || peek() == '\r') advance()
    }

    data class FinLiteMoneyLiteral(
        val currency: String,
        val amount: Double
    )

    private fun error(message: String) {
        System.err.println("[line $line] Error: $message")
    }

    // Safe character access
    private fun CharSequence.getOrNull(i: Int): Char? =
        if (i in 0 until this.length) this[i] else null
}
