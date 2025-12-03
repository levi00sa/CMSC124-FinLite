package finlite

class FinanceInterpreter(private val core: Evaluator) {

    private val finance = FinanceRuntime()
    fun eval(expr: Expr): Any? {
        return when (expr) {

        // TABLE(...)
        is FinanceExpr.TableLiteral -> {
            val evaluatedColumns = mutableMapOf<String, List<Any?>>()

            for ((colName, exprList) in expr.values) {
                val rawList = evalValue(exprList)

                val list = (rawList as? List<*>)?.toList()
                    ?: throw RuntimeError(
                        Token(TokenType.ERROR, colName, null, 0),
                        "TABLE column '$colName' must be a list."
                    )

                evaluatedColumns[colName] = list
            }

            // return FinTable(columns, data)
            FinanceExpr.FinTable(expr.columns, evaluatedColumns)
        }

        // Built-in Financial Functions
            //net present value
        is FinanceExpr.NPV -> finance.npv(
            // Language syntax: NPV(cashflows, rate)
            rate = evalNumber(expr.rate, context = "NPV rate"),
            cashflows = resolveCashflow(expr.cashflows)
        )
            //internal rate of return
        is FinanceExpr.IRR -> finance.irr(
            cashflows = resolveCashflow(expr.cashflows)
        )
            //present value
        is FinanceExpr.PV -> finance.pv(
            rate = evalNumber(expr.rate, context = "PV rate"),
            nper = evalNumber(expr.nper, context = "PV nper").toInt(),
            pmt = expr.pmt?.let { evalNumber(it, context = "PV pmt") },
            fv = expr.fv?.let { evalNumber(it, context = "PV fv") }
        )
            //future value
        is FinanceExpr.FV -> finance.fv(
            rate = evalNumber(expr.rate, context = "FV rate"),
            nper = evalNumber(expr.nper, context = "FV nper").toInt(),
            pmt = expr.pmt?.let { evalNumber(it, context = "FV pmt") },
            pv = expr.pv?.let { evalNumber(it, context = "FV pv") }
        )
            // weighted average cost of capital
        is FinanceExpr.WACC -> finance.wacc(
            ew = evalNumber(expr.equityWeight, context = "WACC equity weight"),
            dw = evalNumber(expr.debtWeight, context = "WACC debt weight"),
            ce = evalNumber(expr.costOfEquity, context = "WACC cost of equity"),
            cd = evalNumber(expr.costOfDebt, context = "WACC cost of debt"),
            tax = evalNumber(expr.taxRate, context = "WACC tax rate")
        )

        is FinanceExpr.CAPM -> {
            // Language usage: CAPM(beta, rf, prem)
            val betaVal = evalValue(expr.beta)
            val rf = evalNumber(expr.riskFree, context = "CAPM risk-free rate")
            val prem = evalNumber(expr.premium, context = "CAPM premium")

            when (betaVal) {
                is Number -> finance.capm(rf, betaVal.toDouble(), rf + prem)
                is List<*> -> {
                    val betas = betaVal.mapIndexed { index, v ->
                        (v as? Number)?.toDouble() ?: throw RuntimeError(
                            Token(TokenType.ERROR, "CAPM", null, 0),
                            "CAPM beta list element at index $index must be numeric."
                        )
                    }
                    betas.map { b -> finance.capm(rf, b, rf + prem) }
                }
                else -> throw RuntimeError(
                    Token(TokenType.ERROR, "CAPM", null, 0),
                    "CAPM beta must be a number or list of numbers."
                )
            }
        }

        is FinanceExpr.VAR -> finance.varCalc(
            portfolio = evalValue(expr.portfolio),
            confidence = evalNumber(expr.confidence, context = "VAR confidence")
        )

        is FinanceExpr.SMA -> finance.sma(
            values = convertList(expr.series),
            period = evalNumber(expr.period, context = "SMA period").toInt()
        )

        is FinanceExpr.EMA -> finance.ema(
            values = convertList(expr.series),
            period = evalNumber(expr.period, context = "EMA period").toInt()
        )

        is FinanceExpr.Amortize -> {
            // Build a FinTable so that expressions like sched.payment and
            // sched.interest work with ColumnAccess.
            val rows = finance.amortize(
                principal = evalNumber(expr.principal, context = "Amortize principal"),
                rate = evalNumber(expr.rate, context = "Amortize rate"),
                periods = evalNumber(expr.periods, context = "Amortize periods").toInt()
            )

            if (rows.isEmpty()) {
                FinanceExpr.FinTable(emptyList(), emptyMap())
            } else {
                // Pivot List<Map<String, Double>> into Map<String, List<Any?>>
                val columns = rows.first().keys.toList()
                val data: MutableMap<String, MutableList<Any?>> = mutableMapOf()
                for (col in columns) {
                    data[col] = mutableListOf()
                }
                for (row in rows) {
                    for (col in columns) {
                        data[col]!!.add(row[col])
                    }
                }
                FinanceExpr.FinTable(columns, data)
            }
        }
            //add FN
        // ======================================================
        // Cashflow Literal
        // ======================================================
        is FinanceExpr.CashflowLiteral -> evalCashflow(expr)

        // ======================================================
        // Column Access: CF.INFLOW
        // ======================================================
        is FinanceExpr.ColumnAccess -> {
            val table = evalValue(expr.tableExpr) as? FinanceExpr.FinTable
                ?: throw RuntimeError(
                    expr.column,
                    "Expression before '.' must evaluate to a TABLE."
                )
            table.data[expr.column.lexeme]
                ?: throw RuntimeError(expr.column, "Unknown column ${expr.column.lexeme}")
        }

        // ======================================================
        // TimeSeries
        // ======================================================
        is FinanceExpr.TimeSeriesLiteral -> evalTimeSeries(expr)

        // Fallback: delegate non-finance expressions to the core evaluator
        else -> evalValue(expr)
    }
    
    }

    // =============================================
    // Helpers
    // =============================================

    private fun evalValue(expr: Expr): Any? =
        if (expr is FinanceExpr) eval(expr) else core.evaluate(expr)

    private fun evalNumber(
        expr: Expr,
        token: Token? = null,
        context: String
    ): Double {
        val value = evalValue(expr)
        val num = (value as? Number)?.toDouble()
        if (num != null) return num

        val errorToken = token ?: Token(TokenType.ERROR, context, null, 0)
        throw RuntimeError(errorToken, "$context must be numeric.")
    }

    private fun resolveCashflow(expr: Expr): List<Double> {
        // Support arithmetic combinations of cashflow-like expressions, e.g. CF.INFLOW - CF.OUTFLOW
        if (expr is Expr.Binary &&
            (expr.operator.type == TokenType.PLUS || expr.operator.type == TokenType.MINUS)
        ) {
            val left = resolveCashflow(expr.left)
            val right = resolveCashflow(expr.right)
            val size = minOf(left.size, right.size)
            val result = MutableList(size) { 0.0 }
            for (i in 0 until size) {
                result[i] = if (expr.operator.type == TokenType.MINUS) {
                    left[i] - right[i]
                } else {
                    left[i] + right[i]
                }
            }
            return result
        }

        // Fallback: evaluate expression and coerce to list of doubles
        return when (val v = evalValue(expr)) {
            is Cashflow -> v.flows
            is List<*> -> v.map {
                (it as? Number)?.toDouble() ?: throw RuntimeError(
                    Token(TokenType.ERROR, "cashflow", null, 0),
                    "Cashflow list elements must be numeric."
                )
            }
            else -> error("Invalid cashflow expression: $expr")
        }
    }

    private fun convertList(expr: Expr): List<Double> {
        val value = evalValue(expr)
        val list = value as? List<*>
            ?: throw RuntimeError(
                Token(TokenType.ERROR, "list", null, 0),
                "Expected a list expression."
            )
        return list.mapIndexed { index, v ->
            (v as? Number)?.toDouble() ?: throw RuntimeError(
                Token(TokenType.ERROR, "list", null, 0),
                "List element at index $index must be numeric."
            )
        }
    }

    private fun evalCashflow(expr: FinanceExpr.CashflowLiteral): Cashflow {
        val list = expr.entries.map { entry ->
            when (entry.type) {
                FinanceStmt.EntryType.DEBIT -> evalNumber(entry.amount, context = "Cashflow amount")
                FinanceStmt.EntryType.CREDIT -> -evalNumber(entry.amount, context = "Cashflow amount")
            }
        }
        return Cashflow(list)
    }

    private fun evalTimeSeries(expr: FinanceExpr.TimeSeriesLiteral): List<Double> {
        return convertList(expr.values)
    }
}
