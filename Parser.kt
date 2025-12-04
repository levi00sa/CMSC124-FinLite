package finlite
import finlite.Callable.*
import finlite.TokenType.*

class Parser(private val tokens: List<Token>) {

    private var current = 0

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

    private fun statement(): Stmt {
        skipNewlines()

        return when {
            match(LET) -> letStmt()
            match(SET) -> setStmt()
            match(PRINT, LOG) -> printStmt()
            match(IF) -> ifStmt()
            match(WHILE)->whileStmt()
            match(RUN) -> runStmt()
            match(RETURN) -> returnStmt()
            match(SCENARIO) -> scenarioStatement()
            match(PORTFOLIO) -> portfolioStmt()
            match(INDENT) -> {
                // Anonymous block (INDENT already consumed)
                val stmts = mutableListOf<Stmt>()
                while (!check(DEDENT) && !isAtEnd()) {
                    skipNewlines()
                    if (check(DEDENT)) break
                    stmts.add(statement())
                }
                consume(DEDENT, "Expect block end.")
                Stmt.Block(stmts)
            }
            else -> exprStmt()
        }
    }

    private fun runStmt(): Stmt {
        val scenarioName = consume(IDENTIFIER, "Expect scenario name after RUN.")
        consume(ON, "Expect ON in RUN statement.")
        val modelName = consume(IDENTIFIER, "Expect model name after ON.")
        return FinanceStmt.RunStmt(scenarioName, modelName)
    }

    private fun returnStmt(): Stmt {
        val value = if (!check(NEWLINE)) expression() else null
        consume(NEWLINE, "Expect newline after RETURN.")
        return Stmt.ReturnStmt(value)
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
        val expr = expression()
        consume(NEWLINE, "Expect newline after PRINT.")
        return Stmt.Print(expr)
    }

    private fun exprStmt(): Stmt {
        val expr = expression()
        consume(NEWLINE, "Expect newline after expression.")
        return Stmt.ExpressionStmt(expr)
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
        match(THEN) // optional THEN
        consume(NEWLINE, "Expect newline after IF condition or THEN.")

        val thenBranch = if (match(INDENT)) {
            val stmts = mutableListOf<Stmt>()
            while (!check(DEDENT) && !isAtEnd()) {
                skipNewlines()
                if (check(DEDENT)) break
                stmts.add(statement())
            }
            consume(DEDENT, "Expect DEDENT to close THEN block.")
            Stmt.Block(stmts)
        } else {
            block()
        }

        var elseBranch: Stmt? = null
        if (match(ELSE)) {
            consume(NEWLINE, "Expect newline after ELSE.")
            elseBranch = if (match(INDENT)) {
                val stmts = mutableListOf<Stmt>()
                while (!check(DEDENT) && !isAtEnd()) {
                    skipNewlines()
                    if (check(DEDENT)) break
                    stmts.add(statement())
                }
                consume(DEDENT, "Expect DEDENT to close ELSE block.")
                Stmt.Block(stmts)
            } else {
                block()
            }
        }

        consume(END, "Expect END after IF block.")
        consume(NEWLINE, "Expect newline after END.")

        return Stmt.IfStmt(condition, thenBranch, elseBranch)
    }

        private fun whileStmt(): Stmt {
        val condition = expression()
        match(DO) // optional DO
        consume(NEWLINE, "Expect newline after WHILE condition.")

        val body = if (match(INDENT)) {
            val stmts = mutableListOf<Stmt>()
            while (!check(DEDENT) && !isAtEnd()) {
                skipNewlines()
                if (check(DEDENT)) break
                stmts.add(statement())
            }
            consume(DEDENT, "Expect end of WHILE block.")
            Stmt.Block(stmts)
        } else {
            block()
        }

        consume(END, "Expect END after WHILE.")
        consume(NEWLINE, "Expect newline after END.")
        return Stmt.WhileStmt(condition, body)
    }


    private fun block(): Stmt {
        val statements = mutableListOf<Stmt>()
        while (!check(END) && !check(ELSE) && !isAtEnd() && !check(DEDENT)) {
            skipNewlines()
            if (check(END) || check(ELSE) || check(DEDENT)) break
            statements.add(statement())
        }
        return Stmt.Block(statements)
    }

    // ==========================================================
    // EXPRESSIONS (Pratt Parser)
    // ==========================================================

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
                expr = FinanceExpr.ColumnAccess(expr, name)
            } else if (match(LEFT_BRACKET)) {
                val start = expression()
                var end: Expr? = null
                if (match(COLON)) {
                    end = expression()
                }
                val rb = consume(RIGHT_BRACKET, "Expect ']' after index or slice.")
                expr = Expr.Subscript(expr, start, end, rb)
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

        if (match(
                IDENTIFIER,
                NPV, IRR, PV, FV,
                WACC, CAPM, VAR,
                SMA, EMA, AMORTIZE
            )) {
            return Expr.Variable(previous())
        }

        if (match(LEFT_PAREN)) {
            val expr = expression()
            consume(RIGHT_PAREN, "Expect ')' after expression.")
            return Expr.Grouping(expr)
        }

        if (match(LEFT_BRACKET)) {
            val elements = mutableListOf<Expr>()
            if (!check(RIGHT_BRACKET)) {
                do {
                    while (match(NEWLINE)) {}
                    if (check(RIGHT_BRACKET)) break
                    elements.add(expression())
                    while (match(NEWLINE)) {}
                    if (!match(COMMA)) break
                    while (match(NEWLINE)) {}
                    if (check(RIGHT_BRACKET)) break
                } while (true)
            }
            consume(RIGHT_BRACKET, "Expect ']' after list elements.")
            return Expr.ListLiteral(elements)
        }

        if (match(TABLE)) return tableLiteral()
        if (match(CASHFLOW)) return cashflowLiteral()
        if (match(PORTFOLIO)) return portfolioLiteral()

        throw error(peek(), "Expected expression.")
    }

    // ===========================
    // Finance-specific literals & statements
    // ===========================

    private fun portfolioStmt(): Stmt {
        val name = consume(IDENTIFIER, "Expect portfolio name.")
        consume(COLON, "Expect ':' after portfolio name.")
        consume(NEWLINE, "Expect newline after PORTFOLIO header.")
        consume(INDENT, "Expect indented block for PORTFOLIO entries.")

        val entries = mutableListOf<FinanceStmt.LedgerEntryStmt>()

        while (!check(DEDENT) && !isAtEnd()) {
            while (match(NEWLINE)) {}
            if (check(DEDENT)) break

            val type = when {
                match(DEBIT) -> FinanceStmt.EntryType.DEBIT
                match(CREDIT) -> FinanceStmt.EntryType.CREDIT
                else -> throw error(peek(), "Expect DEBIT or CREDIT entry in PORTFOLIO.")
            }

            val account = consume(IDENTIFIER, "Expect asset name after DEBIT/CREDIT.")
            val amount = expression()
            consume(NEWLINE, "Expect newline after portfolio entry.")

            entries.add(FinanceStmt.LedgerEntryStmt(account, amount, type))
        }

        consume(DEDENT, "Expect end of PORTFOLIO block (dedent).")
        consume(END, "Expect END after PORTFOLIO block.")
        consume(NEWLINE, "Expect newline after END.")

        return FinanceStmt.PortfolioStmt(name, entries)
    }

    private fun tableLiteral(): Expr {
        consume(LEFT_PAREN, "Expect '(' after TABLE.")
        while (match(NEWLINE)) {}

        val values = linkedMapOf<String, Expr>()
        val columns = mutableListOf<String>()

        if (!check(RIGHT_PAREN)) {
            do {
                while (match(NEWLINE)) {}
                if (check(RIGHT_PAREN)) break

                val name = consume(IDENTIFIER, "Expect column name.")
                if (values.containsKey(name.lexeme)) {
                    throw error(name, "Duplicate column '${name.lexeme}' in TABLE.")
                }
                columns.add(name.lexeme)
                consume(COLON, "Expect ':' after column name.")
                val listExpr = expression()
                values[name.lexeme] = listExpr

                while (match(NEWLINE)) {}

            } while (match(COMMA))
        }

        if (match(COMMA)) {
            while (match(NEWLINE)) {}
        }

        consume(RIGHT_PAREN, "Expect ')' after TABLE entries.")
        return FinanceExpr.TableLiteral(columns, values)
    }

    private fun cashflowLiteral(): Expr {
        consume(NEWLINE, "Expect newline after CASHFLOW.")
        consume(INDENT, "Expect indented block for CASHFLOW entries.")

        val entries = mutableListOf<FinanceStmt.LedgerEntryStmt>()

        while (!check(DEDENT) && !isAtEnd()) {
            while (match(NEWLINE)) {}
            if (check(DEDENT)) break

            val type = when {
                match(DEBIT) -> FinanceStmt.EntryType.DEBIT
                match(CREDIT) -> FinanceStmt.EntryType.CREDIT
                else -> throw error(peek(), "Expect DEBIT or CREDIT entry.")
            }

            val amount = expression()
            consume(NEWLINE, "Expect newline after cashflow entry.")

            val accountToken = Token(IDENTIFIER, "entry", null, previous().line)
            entries.add(FinanceStmt.LedgerEntryStmt(accountToken, amount, type))
        }

        consume(DEDENT, "Expect end of CASHFLOW block (dedent).")
        return FinanceExpr.CashflowLiteral(entries)
    }

    private fun portfolioLiteral(): Expr {
        consume(NEWLINE, "Expect newline after PORTFOLIO.")
        consume(INDENT, "Expect indented block for PORTFOLIO.")

        // assets
        consume(LEFT_BRACKET, "Expect '[' for assets list.")
        val assetsList = readExprList()
        val assets = Expr.ListLiteral(assetsList)
        consume(RIGHT_BRACKET, "Expect ']' after assets.")
        consume(NEWLINE, "Expect newline after assets list.")

        // weights
        consume(LEFT_BRACKET, "Expect '[' for weights list.")
        val weightsList = readExprList()
        val weights = Expr.ListLiteral(weightsList)
        consume(RIGHT_BRACKET, "Expect ']' after weights.")
        while (match(NEWLINE)) {}

        consume(DEDENT, "Expect end of PORTFOLIO block (dedent).")

        return FinanceExpr.PortfolioLiteral(assets, weights)
    }

    private fun readExprList(): List<Expr> {
        val values = mutableListOf<Expr>()
        if (!check(RIGHT_BRACKET)) {
            values.add(expression())
            while (match(COMMA)) {
                values.add(expression())
            }
        }
        return values
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
        val paren = consume(RIGHT_PAREN, "Expect ')' after arguments.")

        if (callee is Expr.Variable) {
            val nameUpper = callee.name.lexeme.uppercase()
            return when (nameUpper) {
                "NPV" -> {
                    if (args.size != 2) throw error(paren, "NPV(cashflows, rate) requires 2 args.")
                    FinanceExpr.NPV(args[0], args[1])
                }
                "IRR" -> {
                    if (args.size != 1) throw error(paren, "IRR(cashflows) requires 1 arg.")
                    FinanceExpr.IRR(args[0])
                }
                "PV" -> {
                    if (args.size < 2 || args.size > 4) throw error(paren, "PV(rate, nper [, pmt [, fv]]) requires 2-4 args.")
                    FinanceExpr.PV(args[0], args[1], args.getOrNull(2), args.getOrNull(3))
                }
                "FV" -> {
                    if (args.size < 2 || args.size > 4) throw error(paren, "FV(rate, nper [, pmt [, pv]]) requires 2-4 args.")
                    when (args.size) {
                        2 -> FinanceExpr.FV(args[0], args[1], null, null)
                        3 -> FinanceExpr.FV(args[0], args[1], args[2], null)
                        else -> FinanceExpr.FV(args[0], args[1], args.getOrNull(2), args.getOrNull(3))
                    }
                }
                "WACC" -> {
                    if (args.size != 5) throw error(paren, "WACC(equity, debt, re, rd, tax) requires 5 args.")
                    FinanceExpr.WACC(args[0], args[1], args[2], args[3], args[4])
                }
                "CAPM" -> {
                    if (args.size != 3) throw error(paren, "CAPM(beta, rf, prem) requires 3 args.")
                    FinanceExpr.CAPM(args[0], args[1], args[2])
                }
                "VAR" -> {
                    if (args.size != 2) throw error(paren, "VAR(returns, confidence) requires 2 args.")
                    FinanceExpr.VAR(args[0], args[1])
                }
                "SMA" -> {
                    if (args.size != 2) throw error(paren, "SMA(values, window) requires 2 args.")
                    FinanceExpr.SMA(args[0], args[1])
                }
                "EMA" -> {
                    if (args.size != 2) throw error(paren, "EMA(values, alpha) requires 2 args.")
                    FinanceExpr.EMA(args[0], args[1])
                }
                "AMORTIZE" -> {
                    if (args.size != 3) throw error(paren, "AMORTIZE(principal, rate, periods) requires 3 args.")
                    FinanceExpr.Amortize(args[0], args[1], args[2])
                }
                else -> Expr.Call(callee, paren, args)
            }
        }

        return Expr.Call(callee, paren, args)
    }

    // ===========================
    // HELPERS
    // ===========================

    private fun skipNewlines() {
        while (match(NEWLINE)) {}
    }

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

    private fun check(type: TokenType): Boolean =
        if (isAtEnd()) false else peek().type == type

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd(): Boolean =
        peek().type == EOF

    private fun peek(): Token = tokens[current]

    private fun previous(): Token = tokens[current - 1]

    private fun error(token: Token, message: String): RuntimeException {
        System.err.println("[line ${token.line}] Error at '${token.lexeme}': $message")
        return RuntimeException(message)
    }

    private fun synchronize() {
        advance()
        while (!isAtEnd()) {
            if (previous().type == NEWLINE) return
            when (peek().type) {
                LET, SET, IF, PRINT, LOG -> return
                else -> {}
            }
            advance()
        }
    }
}
