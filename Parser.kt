package finlite
import finlite.Callable.*
import finlite.TokenType.*

// Error reporting classes
class ErrorReporter {
    private val errors = mutableListOf<ScanError>()

    fun report(line: Int, column: Int, message: String) {
        errors.add(ScanError(line, column, message))
        System.err.println("[line $line, col $column] Error: $message")
    }

    fun hasErrors() = errors.isNotEmpty()

    fun getErrors(): List<ScanError> = errors
}

data class ScanError(val line: Int, val column: Int, val message: String)

class Parser(private val tokens: List<Token>) {

    private var current = 0
    private val financeParser = FinanceParser(this)

    fun parse(): List<Stmt> {
        val statements = mutableListOf<Stmt>()
        while (!isAtEnd()) {
            skipNewlines()
            if (isAtEnd()) break
            statements.add(statement())
        }
        return statements
    }

    // ============================
    // STATEMENTS
    // ============================

    fun statement(): Stmt {
        skipNewlines()

        return try {
            when {
                match(FUNC) -> functionDecl()
                match(LET) -> letStmt()
                match(SET) -> setStmt()
                match(PRINT, LOG) -> printStmt()
                match(IF) -> ifStmt()
                match(WHILE) -> whileStmt()
                match(FOR) -> forStmt()
                match(IMPORT) -> importStmt()
                check(RUN) -> {
                    val financeStmt = financeParser.parseFinanceStatement()
                    financeStmt ?: exprStmt()
                }
                check(SIMULATE) -> {
                    val financeStmt = financeParser.parseFinanceStatement()
                    financeStmt ?: exprStmt()
                }
                match(RETURN) -> returnStmt()
                check(SCENARIO) -> {
                    val financeStmt = financeParser.parseFinanceStatement()
                    financeStmt ?: scenarioStatement()
                }
                check(PORTFOLIO) -> {
                    val financeStmt = financeParser.parseFinanceStatement()
                    financeStmt ?: exprStmt()
                }
                match(INDENT) -> {
                    // Anonymous block (INDENT already consumed)
                    parseIndentedBlock("message")
                }
                match(INC) -> incStmt()
                match(DEC) -> decStmt()
                else -> exprStmt()
            }
        } catch (e: RuntimeException) {
            synchronize()
            Stmt.ErrorStmt
        }
    }

    private fun incStmt(): Stmt {
        val name = consumeIdentifierLike("Expect variable name after 'inc'.")
        consume(NEWLINE, "Expect newline after INC statement.")
        // Build assignment: name = name + 1
        val one = Expr.Literal(1.0)
        val plusTok = Token(TokenType.PLUS, "+", null, name.line)
        val assignExpr = Expr.Binary(Expr.Variable(name), plusTok, one)
        return Stmt.SetStmt(name, assignExpr)
    }

    private fun decStmt(): Stmt {
        val name = consumeIdentifierLike("Expect variable name after 'dec'.")
        consume(NEWLINE, "Expect newline after DEC statement.")
        val one = Expr.Literal(1.0)
        val minusTok = Token(TokenType.MINUS, "-", null, name.line)
        val assignExpr = Expr.Binary(Expr.Variable(name), minusTok, one)
        return Stmt.SetStmt(name, assignExpr)
    }

    private fun importStmt(): Stmt {
        val filepath = consume(STRING, "Expect string filepath after IMPORT.")
        val filepathStr = filepath.literal as String
        consume(AS, "Expect AS after filepath.")
        val moduleName = consume(IDENTIFIER, "Expect module name after AS.")
        consume(NEWLINE, "Expect newline after IMPORT statement.")
        
        // Create a placeholder for an import statement
        // The actual loading will be done by the interpreter
        return Stmt.Let(moduleName, Expr.Literal(filepathStr))  // Temporary; we'll add proper ImportStmt if needed
    }

    private fun functionDecl(): Stmt.FunctionDecl {
        val name = consume(IDENTIFIER, "Expect function name.")
        consume(LEFT_PAREN, "Expect '(' after function name.")
        val params = mutableListOf<Token>()
        if (!check(RIGHT_PAREN)) {
            do {
                params.add(consumeIdentifierLike("Expect parameter name in function declaration."))
            } while (match(COMMA))
        }
        consume(RIGHT_PAREN, "Expect ')' after parameter list.")

        consume(LEFT_BRACE, "Expect '{' before function body.")
        val statements = mutableListOf<Stmt>()
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(statement())
        }
        consume(RIGHT_BRACE, "Expect '}' after function body.")
        // optional newline after closing brace
        if (match(NEWLINE)) { /* consume optional newline */ }
        return Stmt.FunctionDecl(name, params, Stmt.Block(statements))
    }

    private fun letStmt(): Stmt {
        val name = consumeIdentifierLike("Expect variable name.")
        consume(EQUAL, "Expect '=' after variable name.")
        val initializer = expression()
        consume(NEWLINE, "Expect newline after LET statement.")
        return Stmt.Let(name, initializer)
    }

    private fun setStmt(): Stmt {
        val name = consumeIdentifierLike("Expect variable name.")
        consume(EQUAL, "Expect '=' after variable name.")
        val value = expression()
        consume(NEWLINE, "Expect newline after SET statement.")
        return Stmt.SetStmt(name, value)
    }

    private fun printStmt(): Stmt {
        val exprs = mutableListOf<Expr>()
        exprs.add(expression())
        while (match(COMMA)) {
            exprs.add(expression())
        }
        consume(NEWLINE, "Expect newline after PRINT.")
        
        // If single expression, use it directly; otherwise concatenate with spaces
        val finalExpr = if (exprs.size == 1) {
            exprs[0]
        } else {
            // Build a concatenation chain: expr1 + " " + expr2 + " " + expr3 + ...
            var result = exprs[0]
            for (i in 1 until exprs.size) {
                val spaceTok = Token(TokenType.PLUS, "+", null, peek().line)
                val space = Expr.Literal(" ")
                result = Expr.Binary(result, spaceTok, space)
                result = Expr.Binary(result, spaceTok, exprs[i])
            }
            result
        }
        return Stmt.Print(finalExpr)
    }

    private fun exprStmt(): Stmt {
        val expr = expression()
        consume(NEWLINE, "Expect newline after expression.")
        return Stmt.ExpressionStmt(expr)
    }

    private fun returnStmt(): Stmt{
        val value = if (!check(NEWLINE)) expression() else null
        consume(NEWLINE, "Expect newline after RETURN.")
        return Stmt.ReturnStmt(value)
    }

    private fun scenarioStatement(): Stmt {
        val name = consume(IDENTIFIER, "Expect scenario name.")
        consume(LEFT_BRACE, "Expect '{' before scenario body.")

        val statements = mutableListOf<Stmt>()
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(statement())
        }

        consume(RIGHT_BRACE, "Expect '}' after scenario body.")
        return Stmt.Scenario(name, statements)
    }

    private fun ifStmt(): Stmt {
        val condition = expression()
        match(THEN)                     //optional THEN
        consume(NEWLINE, "Expect newline after IF condition or THEN.")

        val thenBranch: Stmt = if (match(INDENT)) {
            parseIndentedBlock("Expect end of IF block.")
        } else {
            // single-line then branch
            statement()
        }

        val elseifBranches = mutableListOf<Pair<Expr, Stmt>>()
        
        // Parse all ELSEIF branches
        while (match(ELSEIF)) {
            val elseifCondition = expression()
            match(THEN)  // optional THEN after ELSEIF
            consume(NEWLINE, "Expect newline after ELSEIF condition or THEN.")
            
            val elseifBranch: Stmt = if (match(INDENT)) {
                parseIndentedBlock("Expect end of ELSEIF block.")
            } else {
                // single-line elseif branch
                statement()
            }
            
            elseifBranches.add(Pair(elseifCondition, elseifBranch))
        }

        var elseBranch: Stmt? = null

        if (match(ELSE)) {
            consume(NEWLINE, "Expect newline after ELSE.")
            elseBranch = if (match(INDENT)) {
                parseIndentedBlock("Expect end of ELSE block.")
            } else {
                // single-line else branch
                statement()
            }
        }

        consume(END, "Expect END after IF block.")
        consume(NEWLINE, "Expect newline after END.")

        return Stmt.IfStmt(condition, thenBranch, elseifBranches, elseBranch)
    }
    //looping recursion
    private fun whileStmt(): Stmt {
        val condition = expression()

        // Optional DO or THEN
        match(TokenType.DO)
        match(TokenType.THEN)

        consume(TokenType.NEWLINE, "Expect newline after WHILE condition.")

        val body =
            if (match(TokenType.INDENT)) {
                parseIndentedBlock("Expect DEDENT after WHILE block.")
            } else {
                statement()
            }

        // Support END (file mode)
        if (match(TokenType.END)) {
            consume(TokenType.NEWLINE, "Expect newline after END.")
        }

        return Stmt.WhileStmt(condition, body)
    }


    
    private fun forStmt(): Stmt {
        val variable = consumeIdentifierLike("Expect variable name after FOR.")
        consume(IN, "Expect 'IN' after loop variable.")

        val iterable = expression()
        consume(NEWLINE, "Expect newline after FOR ... IN ...")

        val body =
            if (match(INDENT)) parseIndentedBlock("Expect DEDENT after FOR block.")
            else statement()

        return Stmt.ForEach(variable, iterable, body)
    }



    // ==========================================================
    // EXPRESSIONS (Pratt Parser)
    // ==========================================================

    fun parseExpression(): Expr = assignment()

    private fun expression(): Expr = assignment()

    private fun assignment(): Expr {
        val expr = logicOr()

        if (match(EQUAL)) {
            val equals = previous()
            val value = assignment()

            if (expr is Expr.Variable) {
                return Expr.Assign(expr.name, value)
            }

            throw error(equals, "Invalid assignment target.")
        }

        return expr
    }

    private fun logicOr(): Expr {
        var expr = logicAnd()
        while (match(OR, OR_OR)) {
            val op = previous()
            val right = logicAnd()
            expr = Expr.Binary(expr, op, right)
        }
        return expr
    }

    private fun logicAnd(): Expr {
        var expr = equality()
        while (match(AND, AND_AND)) {
            val op = previous()
            val right = equality()
            expr = Expr.Binary(expr, op, right)
        }
        return expr
    }

    private fun equality(): Expr {
        var expr = comparison()
        while (match(EQUAL_EQUAL, BANG_EQUAL)) {
            val op = previous()
            val right = comparison()
            expr = Expr.Binary(expr, op, right)
        }
        return expr
    }

    private fun comparison(): Expr {
        var expr = term()
        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            val op = previous()
            val right = term()
            expr = Expr.Binary(expr, op, right)
        }
        return expr
    }

    private fun term(): Expr {
        var expr = factor()
        while (match(PLUS, MINUS)) {
            val op = previous()
            val right = factor()
            expr = Expr.Binary(expr, op, right)
        }
        return expr
    }

    private fun factor(): Expr {
        var expr = unary()
        while (match(STAR, SLASH, PERCENT)) {
            val op = previous()
            val right = unary()
            expr = Expr.Binary(expr, op, right)
        }
        return expr
    }

    private fun unary(): Expr {
        if (match(BANG, NOT, MINUS)) {
            val op = previous()
            val right = unary()
            return Expr.Unary(op, right)
        }

        
        return call()
    }

    private fun call(): Expr {
        var expr = primary()
        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr)
            } else if (match(DOT)) {
                val name = consume(IDENTIFIER, "Expect property name after '.'.")
                expr = Expr.Get(expr, name)
            } else if (match(LEFT_BRACKET)) {
                var start: Expr? = null
                var end: Expr? = null
                var isSlice = false
                
                // Check if this is a slice starting with :
                if (check(COLON)) {
                    start = Expr.Literal(0) // Default start to 0
                    isSlice = true
                } else {
                    start = expression()
                }
                
                if (match(COLON)) {
                    isSlice = true
                    // Allow empty end for open-ended slices like s[2:]
                    if (!check(RIGHT_BRACKET)) {
                        end = expression()
                    }
                }
                val rb = consume(RIGHT_BRACKET, "Expect ']' after index or slice.")
                expr = Expr.Subscript(expr, start, end, rb, isSlice)
            } else {
                
                break
            }
        }
        return expr
    }

    private fun primary(): Expr {
        if (match(TRUE)) return Expr.Literal(true)
        if (match(FALSE)) return Expr.Literal(false)
        if (match(NULL)) return Expr.Literal(null)
        if (match(NUMBER, STRING, MULTILINE_STRING)) {
            return Expr.Literal(previous().literal)
        }
        // Scope resolution: global::var or parent::var
        if (check(IDENTIFIER)) {
            val scopeToken = peek()
            val next1 = peekNext(1)
            val next2 = peekNext(2)
            if (next1?.type == COLON && next2?.type == COLON) {
                advance() // consume identifier
                val scopeName = scopeToken.lexeme.lowercase()
                advance() // consume first colon
                advance() // consume second colon
                val name = consumeIdentifierLike("Expect variable name after '::'.")
                val scopeType = when (scopeName) {
                    "global" -> Expr.ScopeType.GLOBAL
                    "parent" -> Expr.ScopeType.PARENT
                    else -> throw error(scopeToken, "Unknown scope qualifier '$scopeName'. Use 'global' or 'parent'.")
                }
                return Expr.ScopeResolution(scopeType, name)
            }
        }
        // Lambda: single-param form `x -> expr`
        if (check(IDENTIFIER) && peekNext(1)?.type == ARROW) {
            val param = advance() // consume IDENTIFIER
            advance() // consume ARROW
            val body = expression()
            return Expr.Lambda(listOf(param), body)
        }

        // Parenthesized parameter list lambda: `(a, b) -> expr`
        if (check(LEFT_PAREN)) {
            // lookahead to see if this is a lambda parameter list followed by ARROW
            if (isLambdaAfterParen()) {
                advance() // consume LEFT_PAREN
                val params = parseParameterList()
                consume(RIGHT_PAREN, "Expect ')' after parameter list.")
                consume(ARROW, "Expect '->' after parameter list.")
                val body = expression()
                return Expr.Lambda(params, body)
            }
        }
        if (match(
                IDENTIFIER,
                NPV, IRR, PV, FV,
                WACC, CAPM, VAR,
                SMA, EMA, AMORTIZE,
                PRINT, LOG
            )) {
            return Expr.Variable(previous())
        }

        if (match(TIMESERIES)) {
            return parseTimeSeriesCall()
        }

        if (match(LEFT_PAREN)) {
            val expr = expression()
            consume(RIGHT_PAREN, "Expect ')' after expression.")
            return Expr.Grouping(expr)
        }

        if (match(LEFT_BRACKET)) return parseListLiteral()

        if (match(LEFT_BRACE)) return parseObjectLiteral()

        // Try finance expression parsing
        if (check(TABLE)) {
            val financeExpr = financeParser.parseFinanceExpression()
            if (financeExpr != null) return financeExpr
        }
        if (check(CASHFLOW)) {
            val financeExpr = financeParser.parseFinanceExpression()
            if (financeExpr != null) return financeExpr
        }
        if (check(PORTFOLIO)) {
            val financeExpr = financeParser.parseFinanceExpression()
            if (financeExpr != null) return financeExpr
        }

        val err = error(peek(), "Expected expression.")
        throw err
    }

    // =======================================
    // Finance-specific literals & statements
    // =======================================

    private fun parseTimeSeriesCall(): Expr {
        consume(LEFT_PAREN, "Expect '(' after 'timeseries'.")
        if (check(RIGHT_PAREN)) {
            throw error(peek(), "timeseries() requires exactly 2 arguments: source and window.")
        }
        val source = expression()
        if (!match(COMMA)) {
            throw error(peek(), "timeseries() requires exactly 2 arguments: source and window.")
        }
        val window = expression()
        // Check for extra arguments
        if (match(COMMA)) {
            throw error(previous(), "timeseries() requires exactly 2 arguments, but more were provided.")
        }
        consume(RIGHT_PAREN, "Expect ')' after timeseries arguments.")
        return Expr.TimeSeries(source, window)
    }

    private fun finishCall(callee: Expr): Expr {
        val args = mutableListOf<Expr>()
        if (!check(RIGHT_PAREN)) {
            do {
                if (args.size >= 255) {
                    throw error(peek(), "Can't have more than 255 arguments.")
                }
                args.add(expression())
            } while (match(COMMA))
        }
        val parenToken = consume(RIGHT_PAREN, "Expect ')' after arguments.")

        // Try finance function call delegation
        if (callee is Expr.Variable) {
            val financeExpr = financeParser.parseFinanceCall(callee, args)
            if (financeExpr != null) return financeExpr
        }

        return Expr.Call(callee, parenToken, args)
    }

    // Lookahead helper to detect `( ... ) ->` lambda without consuming tokens
    private fun isLambdaAfterParen(): Boolean {
        if (!check(LEFT_PAREN)) return false
        var depth = 0
        var offset = 0
        while (true) {
            val t = peekNext(offset) ?: return false
            if (t.type == LEFT_PAREN) depth++
            if (t.type == RIGHT_PAREN) {
                depth--
                if (depth == 0) {
                    return peekNext(offset + 1)?.type == ARROW
                }
            }
            offset++
        }
    }

    private fun parseParameterList(): List<Token> {
        val params = mutableListOf<Token>()
        if (check(RIGHT_PAREN)) return params
        do {
            val name = consumeIdentifierLike("Expect parameter name in lambda parameter list.")
            params.add(name)
        } while (match(COMMA))
        return params
    }

    // ===========================
    // HELPERS
    // ===========================

    private fun consumeIdentifierLike(message: String): Token {
        if (check(IDENTIFIER) ||
            check(NPV) || check(IRR) ||
            check(PV) || check(FV) ||
            check(WACC) || check(CAPM) ||
            check(VAR) || check(SMA) ||
            check(EMA) || check(AMORTIZE)
        ) {
            return advance()
        }
        throw error(peek(), message)
    }

    // Public for FinanceParser delegation
    fun match(vararg types: TokenType): Boolean {
        for (t in types) {
            if (check(t)) {
                advance()
                return true
            }
        }
        return false
    }

    // Public for FinanceParser delegation
    fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw error(peek(), message)
    }

    // Public for FinanceParser delegation
    fun check(type: TokenType): Boolean =
        if (isAtEnd()) false else peek().type == type

    // Public for FinanceParser delegation
    fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    // Public for FinanceParser delegation
    fun isAtEnd(): Boolean =
        peek().type == EOF

    // Public for FinanceParser delegation
    fun peek(): Token = tokens[current]
    
    // Public for FinanceParser delegation
    fun peekNext(offset: Int = 1): Token? {
        val index = current + offset
        return if (index < tokens.size) tokens[index] else null
    }

    // Public for FinanceParser delegation
    fun previous(): Token = tokens[current - 1]

    // Public for FinanceParser delegation
    fun error(token: Token, message: String): RuntimeException {
        System.err.println("[line ${token.line}] Error at '${token.lexeme}': $message")
        return RuntimeException(message)
    }

    private fun synchronize() {
        advance()
        while (!isAtEnd()) {
            if (previous().type == NEWLINE) return
            when (peek().type) {
                LET, SET, IF, PRINT, LOG, RUN, PORTFOLIO, SCENARIO -> return
                DEDENT -> return
                else -> {}
            }
            advance()
        }
    }
    //newline skipper
    // Public for FinanceParser delegation
    fun skipNewlines() {
        while(match(TokenType.NEWLINE)) {}
    }
    //indent-dedent block parser
    private fun parseIndentedBlock(expectMessage: String): Stmt.Block {
        val stmts = mutableListOf<Stmt>()
        while (!check(DEDENT) && !isAtEnd()) {
            skipNewlines()
            if(check(TokenType.DEDENT)) break
            stmts.add(statement())
        }
        consume(DEDENT, expectMessage)
        return Stmt.Block(stmts)
    }

    private fun parseListLiteral(): Expr {
        val elements = mutableListOf<Expr>()

        if (!check(RIGHT_BRACKET)) {
            do {
                skipNewlines()
                elements.add(expression())
                skipNewlines()
            } while (match(COMMA))
        }

        consume(RIGHT_BRACKET, "Expect ']' after list elements.")
        return Expr.ListLiteral(elements)
    }

    private fun parseObjectLiteral(): Expr {
        val fields = linkedMapOf<String, Expr>()

        if (!check(RIGHT_BRACE)) {
            do {
                skipNewlines()
                val nameToken = consume(IDENTIFIER, "Expect field name in object literal.")
                consume(COLON, "Expect ':' after field name.")
                val valueExpr = expression()
                fields[nameToken.lexeme] = valueExpr
                skipNewlines()
            } while (match(COMMA))
        }

        consume(RIGHT_BRACE, "Expect '}' after object literal.")
        return Expr.ObjectLiteral(fields)
    }
}
