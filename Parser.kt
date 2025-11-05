//recursive descent parser
//parser throws parse errors that print a message and return
// null from parse() so the REPL continues

//it uses tokens exactly as your scanner emits them

//Take the list of tokens from your scanner as input
//Build an Abstract Syntax Tree representing the parsed expression
//Handle operator precedence correctly
//Support grouping with parentheses
//Include basic error reporting for malformed expressions (unbalanced parenthesis, etc.)

// Parser.kt
class ParseError(message: String) : RuntimeException(message)

class Parser(private val tokens: List<Token>) {
    private var current = 0

    fun parse(): Stmt? {
        return try {
            val declarations = mutableListOf<Stmt>()
            while (!isAtEnd()) {
                declarations.add(declaration())
            }
            Stmt.Block(declarations) // wrap program as a block statement
        } catch (e: ParseError) {
            null
        }
    }

    fun parseExpressionForREPL(): Expr? {
        return try {
            val expr = expression()
            if (!isAtEnd()) {
                throw error(peek(), "Unexpected token after expression.")
            }
            expr
        } catch (e: ParseError) {
            null
        }
    }

    // -----------------------
    // DECLARATIONS & STATEMENTS
    // -----------------------
    private fun declaration(): Stmt {
        try {
            if (match(TokenType.CLASS)) return classDeclaration()
            if (match(TokenType.FUN, TokenType.EKSENA)) return functionDeclaration("function")
            if (match(TokenType.VAR, TokenType.MAY)) return varDeclaration()
            return statement()
        } catch (err: ParseError) {
            synchronize()
            // return an empty statement so the program continues
            return Stmt.Expression(Expr.Literal(null))
        }
    }

    private fun varDeclaration(): Stmt {
        val name = consume(TokenType.IDENTIFIER, "Expect variable name.")
        var initializer: Expr? = null
        if (match(TokenType.EQUAL)) {
            initializer = expression()
        }
        consumeOptionalSemicolon()
        return Stmt.Var(name, initializer)
    }

    private fun functionDeclaration(kind: String): Stmt.Function {
        val name = consume(TokenType.IDENTIFIER, "Expect $kind name.")
        consume(TokenType.LEFT_PAREN, "Expect '(' after $kind name.")
        val parameters = mutableListOf<Token>()
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (parameters.size >= 255) {
                    throw error(peek(), "Can't have more than 255 parameters.")
                }
                parameters.add(consume(TokenType.IDENTIFIER, "Expect parameter name."))
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.")
        val body = blockStatement()
        return Stmt.Function(name, parameters, body)
    }

    private fun statement(): Stmt {
        if (match(TokenType.IF, TokenType.KUNG)) return ifStatement()
        if (match(TokenType.FOR, TokenType.AWRA)) return forStatement()
        if (match(TokenType.WHILE, TokenType.FORDA)) return whileStatement()
        if (match(TokenType.RETURN, TokenType.BALIK)) return returnStatement()
        if (match(TokenType.LEFT_BRACE)) return blockStatement()
        // expression statement / print / throw / try etc.
        if (match(TokenType.PRINT, TokenType.CHIKA)) {
            val value = expression()
            consumeOptionalSemicolon()
            return Stmt.Print(value)
        }
        // default: expression statement
        val expr = expression()
        consumeOptionalSemicolon()
        return Stmt.Expression(expr)
    }

    private fun ifStatement(): Stmt {
        if (match(TokenType.LEFT_PAREN)) {
            val condition = expression()
            consume(TokenType.RIGHT_PAREN, "Expect ')' after condition.")
            val thenBranch = statement()
            var elseBranch: Stmt? = null
            if (match(TokenType.ELSE, TokenType.HALA)) {
                elseBranch = statement()
            }
            return Stmt.If(condition, thenBranch, elseBranch)
        } else {
            val condition = expression()
            val thenBranch = statement()
            var elseBranch: Stmt? = null
            if (match(TokenType.ELSE, TokenType.HALA)) {
                elseBranch = statement()
            }
            return Stmt.If(condition, thenBranch, elseBranch)
        }
    }

    private fun forStatement(): Stmt {
        // Support: awra IDENTIFIER in expression { ... }  or classical C-style for if scanner emits tokens
        if (match(TokenType.LEFT_PAREN)) {
            // C-style for (init; cond; inc) - optional
            val initializer = if (!match(TokenType.SEMICOLON)) {
                val init = expression()
                consume(TokenType.SEMICOLON, "Expect ';' after loop initializer.")
                init
            } else null
            val condition = if (!check(TokenType.SEMICOLON)) expression() else Expr.Literal(true)
            consume(TokenType.SEMICOLON, "Expect ';' after loop condition.")
            val increment = if (!check(TokenType.RIGHT_PAREN)) expression() else null
            consume(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.")
            val body = statement()
            // Desugar to while with initializer/increment
            return Stmt.For(initializer, condition, increment, body)
        } else {
            // For-each: awra IDENTIFIER in expression block
            val iteratorName = consume(TokenType.IDENTIFIER, "Expect iterator name after 'awra'.")
            consume(TokenType.IN, "Expect 'in' after iterator variable.")
            val iterable = expression()
            val body = statement()
            return Stmt.ForEach(iteratorName, iterable, body)
        }
    }

    private fun whileStatement(): Stmt {
        // Optional paren handling
        if (match(TokenType.LEFT_PAREN)) {
            val condition = expression()
            consume(TokenType.RIGHT_PAREN, "Expect ')' after condition.")
            val body = statement()
            return Stmt.While(condition, body)
        } else {
            val condition = expression()
            val body = statement()
            return Stmt.While(condition, body)
        }
    }

    private fun returnStatement(): Stmt {
        val value = if (!check(TokenType.SEMICOLON)) expression() else null
        consumeOptionalSemicolon()
        return Stmt.Return(previous(), value)
    }

        private fun blockStatement(): Stmt.Block {
        val statements = mutableListOf<Stmt>()
        // Accept either braces or INDENT/DEDENT mode
        if (previous().type == TokenType.LEFT_BRACE || check(TokenType.LEFT_BRACE)) {
            // if this call happened after matching LEFT_BRACE, then previous() might be the brace; else consume it
            if (check(TokenType.LEFT_BRACE)) advance()
            while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
                statements.add(declaration())
            }
            consume(TokenType.RIGHT_BRACE, "Expect '}' after block.")
            return Stmt.Block(statements)
        } else if (check(TokenType.INDENT)) {
            // indentation-style block
            consume(TokenType.INDENT, "Expect INDENT to start block.")
            while (!check(TokenType.DEDENT) && !isAtEnd()) {
                statements.add(declaration())
            }
            consume(TokenType.DEDENT, "Expect DEDENT after block.")
            return Stmt.Block(statements)
        } else {
            // single statement block fallback
            val single = statement()
            return Stmt.Block(listOf(single))
        }
    }

    // -----------------------
    // Expressions (precedence)
    // -----------------------
    // expression → assignment
    private fun expression(): Expr = assignment()

    // assignment → IDENTIFIER "=" assignment | equality
    private fun assignment(): Expr {
        val expr = equality()
        if (match(TokenType.EQUAL, TokenType.PAK)) {
            val equals = previous()
            val value = assignment()
            if (expr is Expr.Variable) {
                val name = expr.name
                return Expr.Assign(name, value)
            }
            throw error(equals, "Invalid assignment target.")
        }
        return expr
    }

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
        return call()
    }

    // call → primary ( "(" arguments? ")" | "." IDENTIFIER )*
    private fun call(): Expr {
        var expr = primary()
        while (true) {
            if (match(TokenType.LEFT_PAREN)) {
                val args = mutableListOf<Expr>()
                if (!check(TokenType.RIGHT_PAREN)) {
                    do {
                        if (args.size >= 255) throw error(peek(), "Can't have more than 255 arguments.")
                        args.add(expression())
                    } while (match(TokenType.COMMA))
                }
                consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments.")
                expr = Expr.Call(expr, previous(), args)
            } else if (match(TokenType.DOT)) {
                val name = consume(TokenType.IDENTIFIER, "Expect property name after '.'.")
                expr = Expr.Get(expr, name)
            } else {
                break
            }
        }
        return expr
    }

    // NOTE: primary → NUMBER | STRING | "true" | "false" | "null" | IDENTIFIER | "keratin" | "(" expression ")"
    private fun primary(): Expr {
        if (match(TokenType.NUMBER)) {
            return Expr.Literal(previous().literal) // scanner stores Double
        }
        if (match(TokenType.TRUE)) return Expr.Literal(true)
        if (match(TokenType.FALSE)) return Expr.Literal(false)
        if (match(TokenType.NULL)) return Expr.Literal(null)
         if (match(TokenType.NUMBER)) return Expr.Literal(previous().literal)
        if (match(TokenType.STRING)) return Expr.Literal(previous().literal)
        if (match(TokenType.IDENTIFIER)) { return Expr.Variable(previous())}
        if (match(TokenType.THIS, TokenType.AKO, TokenType.SELFIE)) return Expr.This(previous())
        if (match(TokenType.LEFT_PAREN)) {
            val expr = expression()
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.")
            return Expr.Grouping(expr)
        }

        throw error(peek(), "Expect expression.")
    }

    // -----------------------
    // Class specific parsing
    // -----------------------
    private fun classDeclaration(): Stmt {
        val name = consume(TokenType.IDENTIFIER, "Expect class name after 'peg'.")
        var superclass: Expr.Variable? = null
        if (match(TokenType.EXTENDS, TokenType.NAOL)) {
            consume(TokenType.IDENTIFIER, "Expect superclass name after 'naol'.")
            superclass = Expr.Variable(previous())
        }
        consume(TokenType.LEFT_BRACE, "Expect '{' before class body.")
        val methods = mutableListOf<Stmt.Function>()
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            methods.add(functionDeclaration("method"))
        }
        consume(TokenType.RIGHT_BRACE, "Expect '}' after class body.")
        return Stmt.Class(name, superclass, methods)
    }

    // function used inside classes or top level
    private fun functionDeclaration(kind: String): Stmt.Function {
        val name = consume(TokenType.IDENTIFIER, "Expect $kind name.")
        consume(TokenType.LEFT_PAREN, "Expect '(' after $kind name.")
        val parameters = mutableListOf<Token>()
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (parameters.size >= 255) throw error(peek(), "Can't have more than 255 parameters.")
                parameters.add(consume(TokenType.IDENTIFIER, "Expect parameter name."))
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.")
        val body = blockStatement()
        val isConstructor = name.lexeme == "simula"
        return Stmt.Function(name, parameters, body, isConstructor)
    }

    // -----------------------
    // HELPERS
    // -----------------------
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

    private fun consumeOptionalSemicolon() {
            if (match(TokenType.SEMICOLON) || match(TokenType.NEWLINE)) {
            // accepted terminators
        }
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
    
    private fun synchronize() {
        advance()
        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON) RETURN
            when (peek().type) {
                TokenType.CLASS, TokenType.FUN, TokenType.VAR, TokenType.FOR,
                TokenType.IF, TokenType.WHILE, TokenType.RETURN -> return
                else -> advance()  
            }
        }
    }
    
}
