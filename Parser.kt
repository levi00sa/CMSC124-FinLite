// Parser.kt
// Converts a list of tokens into an Abstract Syntax Tree (AST)
// Implements a recursive descent parser for the Teleris language

class ParseError(message: String) : RuntimeException(message)

class Parser(private val tokens: List<Token>) {
    private var current = 0

    fun parse(): List<Stmt> {
        val statements = mutableListOf<Stmt>()
        while (!isAtEnd()) {
            statements.add(declaration())
        }
        return statements
    }

    // ===============================
    // DECLARATIONS & STATEMENTS
    // ===============================

    private fun declaration(): Stmt {
        try {
            if (match(TokenType.CLASS)) return classDeclaration()
            if (match(TokenType.FUN, TokenType.FN, TokenType.EKSENA)) return functionDeclaration("function")
            if (match(TokenType.VAR, TokenType.MAY)) return varDeclaration()
            return statement()
        } catch (err: ParseError) {
            synchronize()
            // Return an empty expression to allow continued parsing after an error
            return Stmt.Expression(Expr.Literal(null))
        }
    }

    private fun varDeclaration(): Stmt {
        val name = consume(TokenType.IDENTIFIER, "Expect variable name.")
        var initializer: Expr? = null
        if (match(TokenType.EQUAL, TokenType.PAK)) {
            initializer = expression()
        }
        consumeOptionalSemicolon()
        return Stmt.Var(name, initializer)
    }

    private fun statement(): Stmt {
        if (match(TokenType.IF, TokenType.KUNG)) return ifStatement()
        if (match(TokenType.FOR, TokenType.AWRA)) return forStatement()
        if (match(TokenType.WHILE, TokenType.FORDA)) return whileStatement()
        if (match(TokenType.RETURN, TokenType.BALIK)) return returnStatement()
        if (match(TokenType.LEFT_BRACE)) return blockStatement()
        if (match(TokenType.PRINT, TokenType.CHIKA)) {
            val value = expression()
            consumeOptionalSemicolon()
            return Stmt.Print(value)
        }

        // Default case: Expression statement
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
        // Support both C-style for loops and for-each
        if (match(TokenType.LEFT_PAREN)) {
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
            return Stmt.For(initializer, condition, increment, body)
        } else {
            val iteratorName = consume(TokenType.IDENTIFIER, "Expect iterator name after 'awra'.")
            consume(TokenType.IN, "Expect 'in' after iterator variable.")
            val iterable = expression()
            val body = statement()
            return Stmt.ForEach(iteratorName, iterable, body)
        }
    }

    private fun whileStatement(): Stmt {
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
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration())
        }
        consume(TokenType.RIGHT_BRACE, "Expect '}' after block.")
        return Stmt.Block(statements)
    }

    // ===============================
    // EXPRESSIONS
    // ===============================

    private fun expression(): Expr = assignment()

    private fun assignment(): Expr {
        val expr = equality()
        if (match(TokenType.EQUAL, TokenType.PAK)) {
            val equals = previous()
            val value = assignment()
            if (expr is Expr.Variable) {
                return Expr.Assign(expr.name, value)
            }
            throw error(equals, "Invalid assignment target.")
        }
        return expr
    }

    private fun equality(): Expr {
        var expr = comparison()
        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            val op = previous()
            val right = comparison()
            expr = Expr.Binary(expr, op, right)
        }
        return expr
    }

    private fun comparison(): Expr {
        var expr = term()
        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            val op = previous()
            val right = term()
            expr = Expr.Binary(expr, op, right)
        }
        return expr
    }

    private fun term(): Expr {
        var expr = factor()
        while (match(TokenType.MINUS, TokenType.PLUS)) {
            val op = previous()
            val right = factor()
            expr = Expr.Binary(expr, op, right)
        }
        return expr
    }

    private fun factor(): Expr {
        var expr = unary()
        while (match(TokenType.SLASH, TokenType.STAR)) {
            val op = previous()
            val right = unary()
            expr = Expr.Binary(expr, op, right)
        }
        return expr
    }

    private fun unary(): Expr {
        if (match(TokenType.BANG, TokenType.NOT, TokenType.MINUS, TokenType.DEHINS)) {
            val op = previous()
            val right = unary()
            return Expr.Unary(op, right)
        }
        return call()
    }

    private fun call(): Expr {
        var expr = primary()
        while (true) {
            if (match(TokenType.LEFT_PAREN)) {
                expr = finishCall(expr)
            } else break
        }
        return expr
    }

    private fun finishCall(callee: Expr): Expr {
        val arguments = mutableListOf<Expr>()
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (arguments.size >= 255)
                    error(peek(), "Can't have more than 255 arguments.")
                arguments.add(expression())
            } while (match(TokenType.COMMA))
        }
        val paren = consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments.")
        return Expr.Call(callee, paren, arguments)
    }

    private fun primary(): Expr {
        if (match(TokenType.NUMBER, TokenType.STRING))
            return Expr.Literal(previous().literal)
        if (match(TokenType.TRUE, TokenType.OMSIM)) return Expr.Literal(true)
        if (match(TokenType.FALSE, TokenType.DEHINS)) return Expr.Literal(false)
        if (match(TokenType.NIR, TokenType.NULL)) return Expr.Literal(null)
        if (match(TokenType.IDENTIFIER)) return Expr.Variable(previous())
        if (match(TokenType.LEFT_PAREN)) {
            val expr = expression()
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.")
            return Expr.Grouping(expr)
        }
        throw error(peek(), "Expect expression.")
    }

    // ===============================
    // CLASS & FUNCTION DECLARATIONS
    // ===============================

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

    private fun functionDeclaration(kind: String): Stmt.Function {
        val name = consume(TokenType.IDENTIFIER, "Expect $kind name.")
        consume(TokenType.LEFT_PAREN, "Expect '(' after $kind name.")
        val parameters = mutableListOf<Token>()
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (parameters.size >= 255)
                    throw error(peek(), "Can't have more than 255 parameters.")
                parameters.add(consume(TokenType.IDENTIFIER, "Expect parameter name."))
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.")
        val body = blockStatement()
        val isConstructor = name.lexeme == "simula"
        return Stmt.Function(name, parameters, body, isConstructor)
    }

    // ===============================
    // HELPERS
    // ===============================

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

    private fun synchronize() {
        advance()
        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON) return
            when (peek().type) {
                TokenType.CLASS, TokenType.FUN, TokenType.FOR, TokenType.IF, TokenType.WHILE, TokenType.RETURN -> return
                else -> advance()
            }
        }
    }

    private fun consumeOptionalSemicolon() {
        if (match(TokenType.SEMICOLON)) return
    }
}

/*
 This file implements the recursive descent parser for the Teleris language.
 It converts a token stream from the Scanner into an Abstract Syntax Tree (AST).

purpose:
 -to validate and structure syntax based on grammar rules.
 -to build AST nodes representing expressions, statements, and declarations.
 -to gracefully handle and recover from syntax errors.

summary:
 expression → assignment ;
 assignment → IDENTIFIER "=" assignment | equality ;
 equality   → comparison ( ( "!=" | "==" ) comparison )* ;
 comparison → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
 term       → factor ( ( "-" | "+" ) factor )* ;
 factor     → unary ( ( "/" | "*" ) unary )* ;
 unary      → ( "!" | "-" ) unary | call ;
 call       → primary ( "(" arguments? ")" )* ;
 primary    → NUMBER | STRING | "true" | "false" | "nir" | IDENTIFIER | "(" expression ")" ;

Key functions:
 -parse(): main entry point returning list of Stmt nodes
 -expression(), term(), factor(): recursive grammar rules
 -error(), synchronize(): error handling and recovery
 -functionDeclaration(), classDeclaration(): handle top-level constructs

importance:
 The parser ensures syntactic correctness and builds structured ASTs that
 can be later printed, interpreted, or compiled.
 */