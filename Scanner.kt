package finlite

class Scanner(
    private val source: String,
    private val errorReporter: ErrorReporter = ErrorReporter()
    ) {
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

        // Ensure all brackets/parens/braces were closed
        if (parenDepth > 0) error("Unclosed parenthesis")
        if (bracketDepth > 0) error("Unclosed bracket")
        if (braceDepth > 0) error("Unclosed brace")

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
            '(' -> {
                parenDepth++
                addToken(TokenType.LEFT_PAREN)}
            ')' -> {
                parenDepth-- 
                reportIfNegativeDepth("Parenthesis", parenDepth)
                addToken(TokenType.RIGHT_PAREN)}
            '[' -> {
                bracketDepth++
                addToken(TokenType.LEFT_BRACKET)}
            ']' -> {
                bracketDepth--
                reportIfNegativeDepth("Bracket", bracketDepth)
                addToken(TokenType.RIGHT_BRACKET)}
            '{' -> {
                braceDepth++
                addToken(TokenType.LEFT_BRACE)}
            '}' -> {
                braceDepth--
                reportIfNegativeDepth("Brace", braceDepth)
                addToken(TokenType.RIGHT_BRACE)}
            ',' -> addToken(TokenType.COMMA)
            ':' -> addToken(TokenType.COLON)
            '.' -> addToken(TokenType.DOT)
            // Arithmetic
            '+' -> addToken(TokenType.PLUS)
            '*' -> addToken(TokenType.STAR)
            '%' -> addToken(TokenType.PERCENT)
            '^' -> addToken(TokenType.CARET)

            '#' -> {
                if (match('#') && match('#')) {
                    // matched ###
                    handleBlockComment()
                } else {
                    // single-line comment #
                    while (peek() != '\n' && !isAtEnd()) advance()
                }
            }

            '-' -> {
                if (match('>')) addToken(TokenType.ARROW) else addToken(TokenType.MINUS)
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
                    error("Unexpected character '$c' (Unicode: U+${c.code.toString(16).uppercase()})")
                    addToken(TokenType.ERROR, "unexpected:$c")
                }
            }
        }
    }

    

    // Basic helpers
    private fun isAtEnd(): Boolean = current >= source.length

    private fun advance(): Char {
        if (isAtEnd()) return '\u0000'
        return source[current++]
    }

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

        // Skip indentation handling for blank lines or end of file
        if (peek() == '\n' || isAtEnd()) return

        val currentIndent = indentStack.lastOrNull() ?: 0

        // Handle dedents (decrease in indentation)
        while (spaces < currentIndent && indentStack.size > 1) {
            indentStack.removeLastOrNull()
            addToken(TokenType.DEDENT)
        }
        
        // Handle indents (increase in indentation)
        if (spaces > currentIndent) {
            indentStack.addLast(spaces)
            addToken(TokenType.INDENT)
        }
    }

    // Block comment scanning
    private fun handleBlockComment() {
        while (!isAtEnd()) {
            if (peek() == '\n') line++

            if (peek() == '#' && 
                peekNext() == '#' && 
                source.getOrNull(current+2)=='#') {
                advance(); advance(); advance() //to consume ###
                return
            }
            advance()
        }
        error("Unterminated block comment: expected closing '###'")
        addToken(TokenType.ERROR, "unterminated-block-comment")
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

        val numericValue = normalized.toDoubleOrNull()
            ?: run {
                error("Invalid numeric literal: '$raw'")
                addToken(TokenType.ERROR, raw)
                return
            }

        // currency suffix (e.g., "1000 USD")
        skipWhitespace()
        val suffix = tryParseCurrencySuffix()
        if (suffix != null) {
            val currency = Currency.fromString(suffix)!!
            addToken(TokenType.MONEY, FinLiteMoneyLiteral(currency, numericValue))
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

    private fun isIsoCurrency(s: String): Boolean =
        Currency.fromString(s) != null


    private fun isDateLiteral(): Boolean {
        if (start + 9 >= source.length) return false
        val segment = source.substring(start, start + 10)
        return Regex("""\d{4}-\d{2}-\d{2}""").matches(segment)
    }

    // IDENTIFIERS, KEYWORDS, ISO MONEY PREFIXES
    private fun identifierOrKeywordOrMoney() {
        while (isIdentifier(peek())) advance()
        val text = source.substring(start, current)
        val lower = text.lowercase()
        // keyword match
        val keyword = keywords[lower]
        if (keyword != null) {
            addToken(keyword)
            return
        }

        // MONEY prefix - "USD 1000"
        val checkCurrency = Currency.fromString(text)
        if (checkCurrency != null) {
            skipWhitespace()
            val valueStart = current
            if (peek().isDigit()) {
                numberOrMoneyOrDate()
                val valueText = source.substring(valueStart, current)
                val numeric = valueText.replace(",", "").replace("_", "").toDoubleOrNull()
                if (numeric == null) {
                    error("Invalid money literal: '$valueText'")
                    addToken(TokenType.ERROR, valueText)
                    return
                }
                addToken(TokenType.MONEY, FinLiteMoneyLiteral(checkCurrency, numeric))
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
            error("Unterminated string at line $line: expected closing '\"'")
            addToken(TokenType.ERROR, "Unterminated string")
            return
        }

        advance()

        val value = source.substring(start + 1, current - 1)
        addToken(TokenType.STRING, value)
    }


    private fun multilineString() {
        // We have already consumed """.
        while (!isAtEnd()) {
            if (peek() == '\n') line++

            if (peek() == '"' && peekNext() == '"'
                && source.getOrNull(current + 2) == '"'
            ) {
                advance(); advance(); advance() // consume """
                val value = source.substring(start + 3, current - 3)
                addToken(TokenType.MULTILINE_STRING, value)
                return
            }
            advance()
        }
        error("Unterminated multiline string at line $line: expected closing \"\"\"")
        addToken(TokenType.ERROR, "Unterminated multiline string")

        while (!isAtEnd() && peek() != '\n') advance()
    }

    // Utility: skip whitespace (not newline)
    private fun skipWhitespace() {
        while (peek() == ' ' || peek() == '\t' || peek() == '\r') advance()
    }

    data class FinLiteMoneyLiteral(
        val currency: String,
        val amount: Double
    )

    private fun reportIfNegativeDepth(type: String, depth: Int) {
        if (depth < 0) {
            error("$type closed but never opened")
        }
    }

    private fun isIdentifier(c: Char): Boolean {
        return c.isLetterOrDigit() ||
            c == '_' ||
            c.category == CharCategory.NON_SPACING_MARK
    }

    private fun error(message: String) {
        val column = current - start
        errorReporter.report(line,column, message)
    }
    // Safe character access
    private fun CharSequence.getOrNull(i: Int): Char? =
        if (i in 0 until this.length) this[i] else null
}
