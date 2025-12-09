//FinanceParser.kt
//separated finance-specific logic from parser.kt

package finlite
import finlite.TokenType.*

class FinanceParser(private val parser: Parser) {

    fun parseFinanceStatement(): Stmt? {
        return when {
            parser.check(RUN) -> {
                parser.advance() // consume RUN
                val scenarioName = parser.consume(IDENTIFIER, "Expect scenario name after RUN.")
                parser.consume(ON, "Expect ON in RUN statement.")
                val modelName = parser.consume(IDENTIFIER, "Expect model name after ON.")
                FinanceStmt.RunStmt(scenarioName, modelName)
            }
            parser.check(SIMULATE) -> {
                parser.advance() // consume SIMULATE
                val scenarioName = parser.consume(IDENTIFIER, "Expect scenario name after SIMULATE.")
                var runs: Expr? = null
                var step: Expr? = null

                // Optional RUNS <expr> or plain numeric runs following the name
                if (parser.match(RUNS)) {
                    runs = parser.parseExpression()
                } else if (!parser.check(NEWLINE) && !parser.check(INDENT) && !parser.check(DEDENT)) {
                    // Allow SIMULATE <name> <runs> (e.g. SIMULATE Example 3 1)
                    runs = parser.parseExpression()
                }

                // Optional STEP <expr> or plain numeric step following the runs
                if (parser.match(STEP)) {
                    step = parser.parseExpression()
                } else if (!parser.check(NEWLINE) && !parser.check(INDENT) && !parser.check(DEDENT)) {
                    step = parser.parseExpression()
                }

                // Expect newline after SIMULATE statement
                parser.consume(NEWLINE, "Expect newline after SIMULATE statement.")

                FinanceStmt.SimulateStmt(scenarioName, runs, step)
            }
            
            parser.check(SCENARIO) -> {
                // Finance-style SCENARIO with an indented body
                parser.advance() // consume SCENARIO
                val name = parser.consume(IDENTIFIER, "Expect scenario name after SCENARIO.")
                parser.consume(NEWLINE, "Expect newline after SCENARIO header.")
                parser.consume(INDENT, "Expect indented block for SCENARIO body.")

                val stmts = mutableListOf<Stmt>()
                while (!parser.check(DEDENT) && !parser.isAtEnd()) {
                    parser.skipNewlines()
                    if (parser.check(DEDENT)) break
                    stmts.add(parser.statement())
                }

                parser.consume(DEDENT, "Expect end of SCENARIO block.")
                FinanceStmt.ScenarioStmt(name, Stmt.Block(stmts))
            }
            parser.check(PORTFOLIO) -> {
                parser.advance() // consume PORTFOLIO
                val name = parser.consume(IDENTIFIER, "Expect portfolio name.")
                parser.consume(COLON, "Expect ':' after portfolio name.")
                parser.consume(NEWLINE, "Expect newline after PORTFOLIO header.")
                parser.consume(INDENT, "Expect indented block for PORTFOLIO entries.")

                val entries = parseLedgerEntries(named = true)

                parser.consume(DEDENT, "Expect end of PORTFOLIO block.")
                parser.consume(END, "Expect END after PORTFOLIO.")
                parser.consume(NEWLINE, "Expect newline after END.")

                FinanceStmt.PortfolioStmt(name, entries)
            }
            
            else -> null
        }
    }

    fun parseFinanceExpression(): Expr? {
        return when {
            parser.check(TABLE) -> {
                parser.advance() // consume TABLE
                parser.consume(LEFT_PAREN, "Expect '(' after TABLE.")
                parser.skipNewlines()

                val values = linkedMapOf<String, Expr>()
                val columns = mutableListOf<String>()

                if (!parser.check(RIGHT_PAREN)) {
                    do {
                        parser.skipNewlines()
                        if (parser.check(RIGHT_PAREN)) break

                        val name = parser.consume(IDENTIFIER, "Expect column name.")
                        if (values.containsKey(name.lexeme)) {
                            throw parser.error(name, "Duplicate column '${name.lexeme}' in TABLE.")
                        }
                        columns.add(name.lexeme)
                        parser.consume(COLON, "Expect ':' after column name.")
                        val listExpr = parser.parseExpression()
                        values[name.lexeme] = listExpr

                        parser.skipNewlines()

                    } while (parser.match(COMMA))
                }

                parser.consume(RIGHT_PAREN, "Expect ')' after TABLE entries.")
                FinanceExpr.TableLiteral(columns, values)
            }
            parser.check(CASHFLOW) -> {
                parser.advance() // consume CASHFLOW
                parser.consume(NEWLINE, "Expect newline after CASHFLOW.")
                parser.consume(INDENT, "Expect indented block for CASHFLOW entries.")

                val entries = parseLedgerEntries(named = false)

                parser.consume(DEDENT, "Expect end of CASHFLOW block.")
                FinanceExpr.CashflowLiteral(entries)
            }
            parser.check(PORTFOLIO) -> {
                parser.advance() // consume PORTFOLIO
                parser.consume(NEWLINE, "Expect newline after PORTFOLIO.")
                parser.consume(INDENT, "Expect indented block for PORTFOLIO.")

                // assets
                parser.consume(LEFT_BRACKET, "Expect '[' for assets list.")
                val assetsList = readExprList()
                val assets = Expr.ListLiteral(assetsList)
                parser.consume(RIGHT_BRACKET, "Expect ']' after assets.")
                parser.consume(NEWLINE, "Expect newline after assets list.")

                // weights
                parser.consume(LEFT_BRACKET, "Expect '[' for weights list.")
                val weightsList = readExprList()
                val weights = Expr.ListLiteral(weightsList)
                parser.consume(RIGHT_BRACKET, "Expect ']' after weights.")
                parser.skipNewlines()

                parser.consume(DEDENT, "Expect end of PORTFOLIO block (dedent).")

                FinanceExpr.PortfolioLiteral(assets, weights)
            }
            
            else -> null
        }
    }

    fun parseFinanceCall(callee: Expr, args: List<Expr>): Expr? {
        if (callee !is Expr.Variable) return null
        val nameUpper = callee.name.lexeme.uppercase()
        val paren = parser.peek()
        return when (nameUpper) {
            "NPV" -> {
                if (args.size != 2) throw parser.error(paren, "NPV(cashflows, rate) requires 2 args.")
                FinanceExpr.NPV(args[0], args[1])
            }
            "IRR" -> {
                if (args.size != 1) throw parser.error(paren, "IRR(cashflows) requires 1 arg.")
                FinanceExpr.IRR(args[0])
            }
            "PV" -> {
                if (args.size < 2 || args.size > 4) throw parser.error(paren, "PV(rate, nper [, pmt [, fv]]) requires 2-4 args.")
                FinanceExpr.PV(args[0], args[1], args.getOrNull(2), args.getOrNull(3))
            }
            "FV" -> {
                if (args.size < 2 || args.size > 4) throw parser.error(paren, "FV(rate, nper [, pmt [, pv]]) requires 2-4 args.")
                FinanceExpr.FV(args[0], args[1], args.getOrNull(2), args.getOrNull(3))
            }
            "WACC" -> {
                if (args.size != 5) throw parser.error(paren, "WACC(equity, debt, re, rd, tax) requires 5 args.")
                FinanceExpr.WACC(args[0], args[1], args[2], args[3], args[4])
            }
            "CAPM" -> {
                if (args.size != 3) throw parser.error(paren, "CAPM(beta, rf, prem) requires 3 args.")
                FinanceExpr.CAPM(args[0], args[1], args[2])
            }
            "VAR" -> {
                if (args.size != 2) throw parser.error(paren, "VAR(returns, confidence) requires 2 args.")
                FinanceExpr.VAR(args[0], args[1])
            }
            "SMA" -> {
                if (args.size != 2) throw parser.error(paren, "SMA(values, window) requires 2 args.")
                FinanceExpr.SMA(args[0], args[1])
            }
            "EMA" -> {
                if (args.size != 2) throw parser.error(paren, "EMA(values, alpha) requires 2 args.")
                FinanceExpr.EMA(args[0], args[1])
            }
            "AMORTIZE" -> {
                if (args.size != 3) throw parser.error(paren, "AMORTIZE(principal, rate, periods) requires 3 args.")
                FinanceExpr.Amortize(args[0], args[1], args[2])
            }
            else -> null
        }
    }

    // =====================================
    // Helpers
    // =====================================

    private fun parseLedgerEntries(named: Boolean): MutableList<FinanceStmt.LedgerEntryStmt> {
        val entries = mutableListOf<FinanceStmt.LedgerEntryStmt>()

        while (!parser.check(DEDENT) && !parser.isAtEnd()) {
            parser.skipNewlines()
            if (parser.check(DEDENT)) break

            val type = when {
                parser.match(DEBIT) -> FinanceStmt.EntryType.DEBIT
                parser.match(CREDIT) -> FinanceStmt.EntryType.CREDIT
                else -> throw parser.error(parser.peek(), "Expect DEBIT or CREDIT entry.")
            }

            val account = if (named) {
                parser.consume(IDENTIFIER, "Expect account name after DEBIT/CREDIT.")
            } else {
                Token(IDENTIFIER, "entry", null, parser.previous().line)
            }

            val amount = parser.parseExpression()
            parser.consume(NEWLINE, "Expect newline after entry.")

            entries.add(FinanceStmt.LedgerEntryStmt(account, amount, type))
        }

        return entries
    }

    private fun readExprList(): List<Expr> {
        val values = mutableListOf<Expr>()
        if (!parser.check(RIGHT_BRACKET)) {
            values.add(parser.parseExpression())
            while (parser.match(COMMA)) {
                values.add(parser.parseExpression())
            }
        }
        return values
    }
}