class ParseError(message: String) : RuntimeException(message)

class Parser(private val tokens: List<Token>) {
    private var current = 0

    fun parse(): Expr? {
        return try {
            val expr = expression()                                       
            if (!isAtEnd()) {                                                   //Check to see  we've consumed all tokens except EOF
                throw error(peek(), "Unexpected token after expression.")
            }
            expr
        } catch (e: ParseError) {
            null
        }
    }

    // expression → block | equality
    private fun expression(): Expr {                                            //
        if (match(TokenType.INDENT)) {
            return block()
        }
        return equality()
    }
    
    // block → INDENT statement* DEDENT
    private fun block(): Expr {
        val statements = mutableListOf<Expr>()
        
        while (!isAtEnd() && !check(TokenType.DEDENT) && !check(TokenType.EOF)) {
            if (match(TokenType.NEWLINE)) {
                continue // Skip empty lines
            }
            val stmt = equality()
            statements.add(stmt)
        }
        
        consume(TokenType.DEDENT, "Expect DEDENT after block.")
        return Expr.Block(statements)
    }
    //MULTI-CHARACTER OPERATOR HANDLING 
    // equality → comparison ( ( "!=" | "==" ) comparison )*
    private fun equality(): Expr {
        var expr = comparison()
        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            val op = previous()
            val right = comparison()
            expr = Expr.Binary(expr, op, right)
        }
        return expr
    }

    // comparison → term ( ( ">" | ">=" | "<" | "<=" ) term )*
    private fun comparison(): Expr {
        var expr = term()
        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            val op = previous()
            val right = term()
            expr = Expr.Binary(expr, op, right)
        }
        return expr
    }

    // term → factor ( ( "-" | "+" ) factor )*
    private fun term(): Expr {
        var expr = factor()
        while (match(TokenType.MINUS, TokenType.PLUS)) {
            val op = previous()
            val right = factor()
            expr = Expr.Binary(expr, op, right)
        }
        return expr
    }

    // factor → unary ( ( "/" | "*" ) unary )*
    private fun factor(): Expr {
        var expr = unary()
        while (match(TokenType.SLASH, TokenType.STAR)) {
            val op = previous()
            val right = unary()
            expr = Expr.Binary(expr, op, right)
        }
        return expr
    }

    // unary → ( "!" | "-" ) unary | primary
    private fun unary(): Expr {
        if (match(TokenType.BANG, TokenType.MINUS)) {
            val op = previous()
            val right = unary()
            return Expr.Unary(op, right)
        }
        return primary()
    }

    // NOTE: primary → NUMBER | STRING | "true" | "false" | "null" | IDENTIFIER | "(" expression ")"
    private fun primary(): Expr {
        if (match(TokenType.NUMBER)) {
            return Expr.Literal(previous().literal) // scanner stores Double
        }
        if (match(TokenType.STRING)) {
            return Expr.Literal(previous().literal)
        }
        if (match(TokenType.TRUE)) return Expr.Literal(true)
        if (match(TokenType.FALSE)) return Expr.Literal(false)
        if (match(TokenType.NULL)) return Expr.Literal(null)
        if (match(TokenType.IDENTIFIER)) { return Expr.Variable(previous())}

        if (match(TokenType.LEFT_PAREN)) {
            val expr = expression()
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.")
            return Expr.Grouping(expr)
        }

        throw error(peek(), "Expect expression.")
    }

    // HELPERS
    private fun match(vararg types: TokenType): Boolean {
        for (t in types) {
            if (check(t)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw error(peek(), message)
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    private fun peek(): Token = tokens[current]

    private fun previous(): Token = tokens[current - 1]

    private fun error(token: Token, message: String): ParseError {
        val where = if (token.type == TokenType.EOF) "at end" else "at '${token.lexeme}'"
        val full = "[line ${token.line}] Error $where: $message"
        System.err.println(full)
        return ParseError(full)
    }
}
