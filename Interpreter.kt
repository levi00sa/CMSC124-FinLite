package finlite
import finlite.TokenType.*

class Interpreter (
    private val globalEnvironment: Environment
){
    private val financeExecutor = FinanceStatementExecutor(globalEnvironment, this)
    private val finance = FinanceRuntime()
    
    // Current environment (can be nested)
    private var currentEnvironment: Environment = globalEnvironment

    fun execute(stmt: Stmt) {
        executeStmt(stmt)
    }

    fun executeStmt(stmt: Stmt) {
        when (stmt) {
            is Stmt.Let -> {
                val value = evaluate(stmt.initializer)
                currentEnvironment.define(stmt.name.lexeme, value)
            }

            is Stmt.SetStmt -> {
                val value = evaluate(stmt.value)
                currentEnvironment.assign(stmt.name.lexeme, value)
            }

            is Stmt.Print -> {
                val value = evaluate(stmt.value)
                println(valueToString(value))
            }

            is Stmt.ExpressionStmt -> {
                val result = evaluate(stmt.expression)
                println(valueToString(result))
            }

            is Stmt.Block -> {
                // Create a new nested environment for this block
                executeBlock(stmt.statements, Environment(currentEnvironment))
            }

            is Stmt.IfStmt -> {
                val condition = evaluate(stmt.condition)
                if (isTruthy(condition)) {
                    execute(stmt.thenBranch)
                } else {
                    // Check elseif branches
                    var executed = false
                    for ((elseifCondition, elseifBranch) in stmt.elseifBranches) {
                        if (isTruthy(evaluate(elseifCondition))) {
                            execute(elseifBranch)
                            executed = true
                            break
                        }
                    }
                    // Execute else branch if no elseif matched
                    if (!executed) {
                        stmt.elseBranch?.let { execute(it) }
                    }
                }
            }

            is Stmt.WhileStmt -> {
                val loopEnv = Environment(currentEnvironment)
                val previousEnv = currentEnvironment
                currentEnvironment = loopEnv
                try {
                    while (isTruthy(evaluate(stmt.condition))) {
                        execute(stmt.body)
                    }
                } finally {
                    currentEnvironment = previousEnv
                }
            }

            is Stmt.For -> {
                val loopEnv = Environment(currentEnvironment)
                val previousEnv = currentEnvironment
                currentEnvironment = loopEnv

                try {
                    val start = evaluate(stmt.start).asNumber(" in FOR start").toInt()
                    val end = evaluate(stmt.end).asNumber(" in FOR end").toInt()
                    val step = stmt.step?.let { evaluate(it).asNumber(" in FOR step").toInt() } ?: 1

                    if (step == 0) {
                        throw RuntimeError(null, "FOR loop step cannot be zero.")
                    }

                    // define loop variable ONCE
                    loopEnv.define(stmt.variable.lexeme, RuntimeValue.Number(start.toDouble()))

                    var i = start
                    while (if (step > 0) i <= end else i >= end) {
                        // update loop variable
                        loopEnv.assign(stmt.variable.lexeme, RuntimeValue.Number(i.toDouble()))

                        execute(stmt.body)
                        i += step
                    }
                } finally {
                    currentEnvironment = previousEnv
                }
            }


            is Stmt.ForEach -> {
                val iterableValue = evaluate(stmt.iterable)
                val elements = when (iterableValue) {
                    is RuntimeValue.ListValue -> iterableValue.elements
                    is RuntimeValue.String -> iterableValue.value.map { RuntimeValue.String(it.toString()) }
                    is RuntimeValue.Table -> {
                        val table = iterableValue
                        val firstCol = table.columns.values.firstOrNull() ?: emptyList()
                        firstCol.indices.map { rowIndex ->
                            val rowData = table.columns.mapValues { (_, col) -> RuntimeValue.Number(col[rowIndex]) }
                            RuntimeValue.Table(mapOf())
                        }
                    }
                    else -> throw RuntimeError(
                        null,
                        "Cannot iterate over ${valueToString(iterableValue)}. Expected list, string, or table."
                    )
                }
                
                val loopEnv = Environment(currentEnvironment)
                val previousEnv = currentEnvironment
                currentEnvironment = loopEnv
                try {
                    for (element in elements) {
                        loopEnv.define(stmt.variable.lexeme, element)
                        execute(stmt.body)
                    }
                } finally {
                    currentEnvironment = previousEnv
                }
            }

            is Stmt.ReturnStmt -> {
                val value = stmt.value?.let { evaluate(it) }
                throw ReturnValue(value)
            }

            is Stmt.Scenario -> {
                // Store scenario as its statements wrapped in a Block
                currentEnvironment.define(
                    stmt.name.lexeme, 
                    RuntimeValue.Block(Stmt.Block(stmt.statements))
                )
            }

            // Delegate finance statements to modularized executor
            is FinanceStmt -> financeExecutor.execute(stmt)

            is Stmt.ErrorStmt -> {}
        }
    }

    fun executeBlock(statements: List<Stmt>, environment: Environment) {
        val previous = currentEnvironment
        try {
            currentEnvironment = environment
            for (statement in statements) {
                execute(statement)
            }
        } finally {
            // Always restore the previous environment, even if an exception occurs
            currentEnvironment = previous
        }
    }

    fun evaluate(expr: Expr): RuntimeValue? {
        return when (expr) {
            is Expr.Lambda -> {
                // Capture current environment for closure
                RuntimeValue.Lambda(expr.params, expr.body, currentEnvironment)
            }
            // Finance expressions
            is FinanceExpr -> evaluateFinanceExpr(expr)
            
            is Expr.Subscript -> evaluateSubscript(expr)
            
            is Expr.Literal -> when (val v = expr.value) {
                is Double -> RuntimeValue.Number(v)
                is String -> RuntimeValue.String(v)
                is Boolean -> RuntimeValue.Bool(v)
                is List<*> -> RuntimeValue.ListValue(
                    v.map { element -> 
                        when (element) {
                            is Double -> RuntimeValue.Number(element)
                            is Number -> RuntimeValue.Number(element.toDouble())
                            else -> throw RuntimeError(null, "List literal contains non-numeric value")
                        }
                    }
                )
                null -> null
                else -> throw RuntimeError(null, "Unsupported literal type: ${v::class.simpleName}")
            }
            
            is Expr.Variable -> currentEnvironment.get(expr.name.lexeme)
            
            is Expr.ScopeResolution -> {
                when (expr.scope) {
                    Expr.ScopeType.GLOBAL -> currentEnvironment.getGlobal(expr.name.lexeme)
                    Expr.ScopeType.PARENT -> currentEnvironment.getParent(expr.name.lexeme)
                }
            }
            
            is Expr.Assign -> {
                val value = evaluate(expr.value)
                currentEnvironment.assign(expr.name.lexeme, value)
                value
            }
            
            is Expr.Grouping -> evaluate(expr.expression)
            
            is Expr.Unary -> {
                val right = evaluate(expr.right)
                when (expr.operator.type) {
                    TokenType.MINUS -> {
                        val num = right.asNumber(" in unary minus")
                        RuntimeValue.Number(-num)
                    }
                    TokenType.BANG, TokenType.NOT -> 
                        RuntimeValue.Bool(!isTruthy(right))
                    else -> 
                        throw RuntimeError(expr.operator, "Unknown unary operator.")
                }
            }

            is Expr.Binary -> {
                val left = evaluate(expr.left)
                val right = evaluate(expr.right)

                when (expr.operator.type) {
                    TokenType.PLUS -> {
                        when {
                            left is RuntimeValue.Number && right is RuntimeValue.Number ->
                                RuntimeValue.Number(left.value + right.value)
                            left is RuntimeValue.String && right is RuntimeValue.String ->
                                RuntimeValue.String(left.value + right.value)
                            left is RuntimeValue.ListValue && right is RuntimeValue.ListValue -> {
                                val result = elementWiseNumericOp(
                                    expr.operator, 
                                    left.elements, 
                                    right.elements
                                ) { a, b -> a + b }
                                RuntimeValue.ListValue(result)
                            }
                            else -> throw RuntimeError(
                                expr.operator, 
                                "Operands must be two numbers, two strings, or two numeric lists."
                            )
                        }
                    }

                    TokenType.MINUS -> {
                        when {
                            left is RuntimeValue.Number && right is RuntimeValue.Number ->
                                RuntimeValue.Number(left.value - right.value)
                            left is RuntimeValue.ListValue && right is RuntimeValue.ListValue -> {
                                val result = elementWiseNumericOp(
                                    expr.operator, 
                                    left.elements, 
                                    right.elements
                                ) { a, b -> a - b }
                                RuntimeValue.ListValue(result)
                            }
                            else -> {
                                val leftNum = left.asNumber(" in binary minus")
                                val rightNum = right.asNumber(" in binary minus")
                                RuntimeValue.Number(leftNum - rightNum)
                            }
                        }
                    }

                    TokenType.STAR -> {
                        val a = left.asNumber(" in multiplication")
                        val b = right.asNumber(" in multiplication")
                        RuntimeValue.Number(a * b)
                    }

                    TokenType.SLASH -> {
                        val a = left.asNumber(" in division")
                        val b = right.asNumber(" in division")
                        if (b == 0.0) 
                            throw RuntimeError(expr.operator, "Division by zero.")
                        RuntimeValue.Number(a / b)
                    }

                    TokenType.GREATER -> {
                        val a = left.asNumber(" in comparison")
                        val b = right.asNumber(" in comparison")
                        RuntimeValue.Bool(a > b)
                    }

                    TokenType.GREATER_EQUAL -> {
                        val a = left.asNumber(" in comparison")
                        val b = right.asNumber(" in comparison")
                        RuntimeValue.Bool(a >= b)
                    }

                    TokenType.LESS -> {
                        val a = left.asNumber(" in comparison")
                        val b = right.asNumber(" in comparison")
                        RuntimeValue.Bool(a < b)
                    }

                    TokenType.LESS_EQUAL -> {
                        val a = left.asNumber(" in comparison")
                        val b = right.asNumber(" in comparison")
                        RuntimeValue.Bool(a <= b)
                    }

                    TokenType.EQUAL_EQUAL -> 
                        RuntimeValue.Bool(isEqual(left, right))
                        
                    TokenType.BANG_EQUAL -> 
                        RuntimeValue.Bool(!isEqual(left, right))

                    else -> 
                        throw RuntimeError(expr.operator, "Unknown binary operator.")
                }
            }

            is Expr.ListLiteral -> 
                RuntimeValue.ListValue(expr.elements.map { evaluate(it) })
            is Expr.ObjectLiteral -> {
                val evaluatedFields = mutableMapOf<String, RuntimeValue?>()
                for ((key, valueExpr) in expr.fields) {
                    evaluatedFields[key] = evaluate(valueExpr)
                }
                RuntimeValue.Object(evaluatedFields)
            }
            is Expr.Get -> {
                val objVal = evaluate(expr.obj)
                val propName = expr.name.lexeme
                when (objVal) {
                    is RuntimeValue.Object -> objVal.getField(propName) 
                        ?: throw RuntimeError(expr.name, "Object has no field '$propName'.")
                    is RuntimeValue.Table -> RuntimeValue.ListValue(
                        objVal.getColumn(propName).map { RuntimeValue.Number(it) }
                    )
                    else -> throw RuntimeError(expr.name, "Cannot access property on ${objVal?.javaClass?.simpleName ?: "null"}.")
                }
            }
            is Expr.TimeSeries -> evaluateTimeSeries(expr)
            is Expr.Call -> evaluateCall(expr)
        }
    }

    private fun evaluateCall(expr: Expr.Call): RuntimeValue? {
        val calleeVal = evaluate(expr.callee)

        val args = expr.arguments.map { evaluate(it) }

        // Handle RuntimeValue.Lambda directly
        if (calleeVal is RuntimeValue.Lambda) {
            return callLambda(calleeVal, args)
        }

        val callable = when (calleeVal) {
            is RuntimeValue.Function -> calleeVal.callable
            is Callable -> calleeVal  // Backward compat
            else -> throw RuntimeError(
                expr.paren, 
                "Can only call functions, got ${calleeVal?.javaClass?.simpleName ?: "null"}."
            )
        }

        // Variable arity functions use -1
        if (callable.arity() != -1 && args.size != callable.arity()) {
            throw RuntimeError(
                expr.paren, 
                "Expected ${callable.arity()} arguments but got ${args.size}."
            )
        }

        return try {
            when (val result = callable.call(this, args)) {
                is RuntimeValue -> result
                is Double -> RuntimeValue.Number(result)
                is String -> RuntimeValue.String(result)
                is Boolean -> RuntimeValue.Bool(result)
                is List<*> -> {
                    // Safe cast with proper handling
                    @Suppress("UNCHECKED_CAST")
                    val elements = result as List<RuntimeValue?>
                    RuntimeValue.ListValue(elements)
                }
                null -> null
                else -> result as? RuntimeValue
            }
        } catch (e: RuntimeError) {
            throw e  // Re-throw RuntimeErrors as-is
        } catch (e: Exception) {
            throw RuntimeError(expr.paren, "Error calling function: ${e.message}").apply {
                initCause(e)
            }
        }
    }

    fun callLambda(lambda: RuntimeValue.Lambda, args: List<RuntimeValue?>): RuntimeValue? {
        if (args.size != lambda.params.size) {
            throw RuntimeError(null, "Lambda expects ${lambda.params.size} arguments but got ${args.size}.")
        }
        val previousEnv = currentEnvironment
        try {
            currentEnvironment = Environment(lambda.capturedEnv)
            // Bind parameters
            for (i in lambda.params.indices) {
                currentEnvironment.define(lambda.params[i].lexeme, args[i])
            }
            return evaluate(lambda.body)
        } finally {
            currentEnvironment = previousEnv
        }
    }

    private fun evaluateSubscript(expr: Expr.Subscript): RuntimeValue? {
        val container = evaluate(expr.container)
        val startVal = evaluate(expr.index)
        val endVal = expr.end?.let { evaluate(it) }

        val startIndex = startVal.asNumber(" as subscript index").toInt()

        return when (container) {
            is RuntimeValue.ListValue -> {
                val list = container.elements
                
                if (!expr.isSlice) {
                    // Simple index access
                    if (startIndex < 0 || startIndex >= list.size) {
                        throw RuntimeError(expr.bracket, "Index $startIndex out of bounds for list of size ${list.size}.")
                    }
                    list[startIndex]
                } else {
                    // Slice access
                    val endIndex = endVal?.asNumber(" as subscript end index")?.toInt() ?: list.size
                    
                    if (startIndex < 0 || endIndex < startIndex || startIndex > list.size) {
                        throw RuntimeError(expr.bracket, "Invalid slice range [$startIndex:$endIndex] for list of size ${list.size}.")
                    }
                    
                    val to = minOf(endIndex, list.size)
                    RuntimeValue.ListValue(list.subList(startIndex, to))
                }
            }
        
            is RuntimeValue.String -> {
                val value = container.value

                if (!expr.isSlice) {
                    // Simple index access
                    if (startIndex < 0 || startIndex >= value.length) {
                        throw RuntimeError(expr.bracket, "Index $startIndex out of bounds.")
                    }
                    RuntimeValue.String(value[startIndex].toString())
                } else {
                    // Slice access: substring 
                    val endIndex = endVal?.asNumber(" as subscript end index")?.toInt() ?: value.length

                    if (startIndex < 0 || endIndex < startIndex || startIndex > value.length) {
                        throw RuntimeError(expr.bracket, "Invalid slice range [$startIndex:$endIndex] for string of length ${value.length}.")
                    }
                    val to = minOf(endIndex, value.length)
                    RuntimeValue.String(value.substring(startIndex, to))
                }
            }
            is RuntimeValue.Table -> {
                throw RuntimeError(expr.bracket, "Table subscript not yet implemented.")
            } 
            else -> throw RuntimeError(
                expr.bracket, 
                "Cannot subscript ${container?.javaClass?.simpleName ?: "null"}."
            )
        }
    }

    private fun evaluateTimeSeries(expr: Expr.TimeSeries): RuntimeValue {
        // Evaluate source
        val sourceVal = evaluate(expr.source)
        val sourceList = sourceVal as? RuntimeValue.ListValue
            ?: throw RuntimeError(null, "timeseries() source must be a list.")
        
        // Evaluate window
        val windowVal = evaluate(expr.window)
        val windowSize = windowVal.asNumber(" in timeseries window").toInt()
        
        // Validate window size
        if (windowSize < 1) {
            throw RuntimeError(null, "timeseries() window must be >= 1, got $windowSize.")
        }
        
        // Extract numeric values from source list
        val numbers = sourceList.elements.mapIndexed { index, element ->
            element.asNumber(" in timeseries source at index $index")
        }
        
        // Handle empty list
        if (numbers.isEmpty()) {
            return RuntimeValue.ListValue(emptyList())
        }
        
        // Compute moving averages
        val result = numbers.indices.map { i ->
            val start = maxOf(0, i - windowSize + 1)
            val end = i + 1
            val window = numbers.subList(start, end)
            val average = window.average()
            RuntimeValue.Number(average)
        }
        
        return RuntimeValue.ListValue(result)
    }

    // Helpers
    
    fun isTruthy(v: RuntimeValue?): Boolean = when (v) {
        null -> false
        is RuntimeValue.Bool -> v.value
        is RuntimeValue.Number -> v.value != 0.0
        is RuntimeValue.String -> v.value.isNotEmpty()
        is RuntimeValue.ListValue -> v.elements.isNotEmpty()
        else -> true
    }

    private fun isEqual(a: RuntimeValue?, b: RuntimeValue?): Boolean {
        if (a == null && b == null) return true
        if (a == null || b == null) return false
        return finlite.isEqual(a, b)
    }

    fun builtin(fn: (List<Any?>) -> Any?): Callable {
        return object : Callable {
            override fun arity(): Int = -1  // variable arity
            override fun call(ctx: Any?, arguments: List<Any?>): Any? {
                return fn(arguments)
            }
            override fun toString(): String = "<builtin>"
        }
    }

    private fun elementWiseNumericOp(
        operator: Token,
        left: List<RuntimeValue?>,
        right: List<RuntimeValue?>,
        op: (Double, Double) -> Double
    ): List<RuntimeValue> {
        val size = minOf(left.size, right.size)
        if (size == 0) return emptyList()
        
        val result = ArrayList<RuntimeValue>(size)
        for (i in 0 until size) {
            val a = left[i].asNumber(" in element-wise operation")
            val b = right[i].asNumber(" in element-wise operation")
            result.add(RuntimeValue.Number(op(a, b)))
        }
        return result
    }

    private fun valueToString(value: RuntimeValue?): String {
        return when (value) {
            null -> "nil"
            is RuntimeValue.Number -> {
                val v = value.value
                // Format nicely: remove .0 for whole numbers
                if (v == v.toLong().toDouble()) {
                    v.toLong().toString()
                } else {
                    v.toString()
                }
            }
            is RuntimeValue.String -> value.value
            is RuntimeValue.Bool -> value.value.toString()
            is RuntimeValue.ListValue -> "[${value.elements.joinToString(" ") { valueToString(it) }}]"
            is RuntimeValue.Table -> value.toString()
            is RuntimeValue.Cashflow -> "Cashflow(${value.flows.size} entries)"
            is RuntimeValue.Portfolio -> "Portfolio(${value.assets.size} assets)"
            is RuntimeValue.Function -> "<function>"
            is RuntimeValue.Block -> "<block>"
            else -> value.toString()
        }
    }

    // =============================================
    // Finance Interpreter
    // =============================================

    private fun evaluateFinanceExpr(expr: FinanceExpr): RuntimeValue? {
        return when (expr) {
            // TABLE(...)
            is FinanceExpr.TableLiteral -> {
                val evaluatedColumns = mutableMapOf<String, List<Any?>>()

                for ((colName, exprList) in expr.values) {
                    val rawList = evalValue(exprList)

                    val list = when (rawList) {
                        is RuntimeValue.ListValue -> rawList.elements.map { element ->
                            when (element) {
                                is RuntimeValue.Number -> element.value
                                is RuntimeValue.String -> element.value
                                is RuntimeValue.Bool -> element.value
                                else -> element
                            }
                        }
                        else -> throw RuntimeError(
                            null,
                            "TABLE column '$colName' must be a list."
                        )
                    }

                    evaluatedColumns[colName] = list
                }

                // Coerce values to Double and build runtime FinTable
                val coerced: MutableMap<String, List<Double>> = mutableMapOf()
                for ((k, v) in evaluatedColumns) {
                    coerced[k] = v.mapIndexed { idx, it ->
                        (it as? Number)?.toDouble() ?: throw RuntimeError(
                            null,
                            "TABLE column '$k' element at index $idx must be numeric."
                        )
                    }
                }
                RuntimeValue.Table(coerced)
            }

            // Built-in Financial Functions
            //net present value
            is FinanceExpr.NPV -> RuntimeValue.Number(finance.npv(
                // Language syntax: NPV(cashflows, rate)
                rate = evalNumber(expr.rate, context = "NPV rate"),
                cashflows = resolveCashflow(expr.cashflows)
            ))
            //internal rate of return
            is FinanceExpr.IRR -> RuntimeValue.Number(finance.irr(
                cashflows = resolveCashflow(expr.cashflows)
            ))
            //present value
            is FinanceExpr.PV -> RuntimeValue.Number(finance.pv(
                rate = evalNumber(expr.rate, context = "PV rate"),
                nper = evalNumber(expr.nper, context = "PV nper").toInt(),
                pmt = expr.pmt?.let { evalNumber(it, context = "PV pmt") },
                fv = expr.fv?.let { evalNumber(it, context = "PV fv") }
            ))
            //future value
            is FinanceExpr.FV -> RuntimeValue.Number(finance.fv(
                rate = evalNumber(expr.rate, context = "FV rate"),
                nper = evalNumber(expr.nper, context = "FV nper").toInt(),
                pmt = expr.pmt?.let { evalNumber(it, context = "FV pmt") },
                pv = expr.pv?.let { evalNumber(it, context = "FV pv") }
            ))
            // weighted average cost of capital
            is FinanceExpr.WACC -> RuntimeValue.Number(finance.wacc(
                ew = evalNumber(expr.equityWeight, context = "WACC equity weight"),
                dw = evalNumber(expr.debtWeight, context = "WACC debt weight"),
                ce = evalNumber(expr.costOfEquity, context = "WACC cost of equity"),
                cd = evalNumber(expr.costOfDebt, context = "WACC cost of debt"),
                tax = evalNumber(expr.taxRate, context = "WACC tax rate")
            ))

            is FinanceExpr.CAPM -> {
                // Language usage: CAPM(beta, rf, prem)
                val betaVal = evalValue(expr.beta)
                val rf = evalNumber(expr.riskFree, context = "CAPM risk-free rate")
                val prem = evalNumber(expr.premium, context = "CAPM premium")

                when (betaVal) {
                    is RuntimeValue.Number -> RuntimeValue.Number(finance.capm(rf, betaVal.value, rf + prem))
                    is RuntimeValue.ListValue -> {
                        val betas = betaVal.elements.mapIndexed { index: Int, v: RuntimeValue? ->
                            (v as? RuntimeValue.Number)?.value ?: throw RuntimeError(
                                null,
                                "CAPM beta list element at index $index must be numeric."
                            )
                        }
                        RuntimeValue.ListValue(betas.map { b: Double -> RuntimeValue.Number(finance.capm(rf, b, rf + prem)) })
                    }
                    else -> throw RuntimeError(
                        null,
                        "CAPM beta must be a number or list of numbers."
                    )
                } as RuntimeValue
            }

            is FinanceExpr.VAR -> RuntimeValue.Number(finance.varCalc(
                portfolio = evalValue(expr.portfolio),
                confidence = evalNumber(expr.confidence, context = "VAR confidence")
            ))

            is FinanceExpr.SMA -> RuntimeValue.ListValue(finance.sma(
                values = convertList(expr.series),
                period = evalNumber(expr.period, context = "SMA period").toInt()
            ).map { RuntimeValue.Number(it) })

            is FinanceExpr.EMA -> RuntimeValue.ListValue(finance.ema(
                values = convertList(expr.series),
                period = evalNumber(expr.period, context = "EMA period").toInt()
            ).map { RuntimeValue.Number(it) })

            is FinanceExpr.Amortize -> {
                // Build a RuntimeValue.Table so that expressions like sched.payment and
                // sched.interest work with ColumnAccess.
                val rows = finance.amortize(
                    principal = evalNumber(expr.principal, context = "Amortize principal"),
                    rate = evalNumber(expr.rate, context = "Amortize rate"),
                    periods = evalNumber(expr.periods, context = "Amortize periods").toInt()
                )

                if (rows.isEmpty()) {
                    RuntimeValue.Table(emptyMap<String, kotlin.collections.List<Double>>())
                } else {
                    // Pivot List<Map<String, Double>> into Map<String, List<Double>>
                    val columns = rows.first().keys.toList()
                    val data: MutableMap<String, MutableList<Double>> = mutableMapOf()
                    for (col in columns) {
                        data[col] = mutableListOf()
                    }
                    for (row in rows) {
                        for (col in columns) {
                            data[col]!!.add(row[col] ?: 0.0)
                        }
                    }
                    RuntimeValue.Table(data.mapValues { (_, value) -> value as kotlin.collections.List<Double> })
                }
            }

            // Cashflow Literal
            is FinanceExpr.CashflowLiteral -> {
                val flow = evalCashflow(expr)  // Cashflow(flows: List<Double>)
                RuntimeValue.Cashflow(flow.flows)
            }

            // Pre-built FinTable expression (rarely used; primarily an internal form)
            is FinanceExpr.FinTable -> {
                // Coerce arbitrary data map into RuntimeValue.Table with numeric columns
                val coerced: MutableMap<String, List<Double>> = mutableMapOf()
                for ((colName, values) in expr.data) {
                    coerced[colName] = values.mapIndexed { idx, v ->
                        when (v) {
                            is Number -> v.toDouble()
                            is RuntimeValue.Number -> v.value
                            else -> throw RuntimeError(
                                null,
                                "FinTable column '$colName' element at index $idx must be numeric."
                            )
                        }
                    }
                }
                RuntimeValue.Table(coerced)
            }

            // Portfolio Literal
            is FinanceExpr.PortfolioLiteral -> {
                val assetsList = convertList(expr.assets)
                val weightsList = convertList(expr.weights)
                if (assetsList.size != weightsList.size) {
                    throw RuntimeError(
                        null,
                        "PORTFOLIO assets and weights must have the same length."
                    )
                }
                RuntimeValue.Portfolio(assetsList, weightsList)
            }

            // Column Access (e.g., table.columnName)
            is FinanceExpr.ColumnAccess -> {
                val table = evalValue(expr.tableExpr) as? RuntimeValue.Table
                    ?: throw RuntimeError(
                        null,
                        "Expression before '.' must evaluate to a TABLE."
                    )
                val col = table.getColumn(expr.column.lexeme)
                RuntimeValue.ListValue(col.map { value ->
                    RuntimeValue.Number(value)
                })
            }
        }
    }

    // =============================================
    // Finance Expression Helpers
    // =============================================

    private fun evalValue(expr: Expr): RuntimeValue? =
        if (expr is FinanceExpr) evaluateFinanceExpr(expr) else evaluate(expr)

    private fun evalNumber(
        expr: Expr,
        token: Token? = null,
        context: String
    ): Double {
        val value = evalValue(expr)
        val num = (value as? RuntimeValue.Number)?.value
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
            is RuntimeValue.Cashflow -> v.flows  // Access the flows from RuntimeValue.Cashflow
            is RuntimeValue.ListValue -> v.elements.map {
                (it as? RuntimeValue.Number)?.value ?: throw RuntimeError(
                    Token(TokenType.ERROR, "cashflow", null, 0),
                    "Cashflow list elements must be numeric."
                )
            }
            else -> throw RuntimeError(
                Token(TokenType.ERROR, "cashflow", null, 0),
                "Invalid cashflow expression, expected Cashflow or List."
            )
        }
    }

    private fun convertList(expr: Expr): List<Double> {
        val value = evalValue(expr)
        val listValue = value as? RuntimeValue.ListValue
            ?: throw RuntimeError(
                null,
                "Expected a list expression."
            )
        return listValue.elements.mapIndexed { index, v ->
            v.asNumber(" in list element at index $index")
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

    // Public Accessors

    fun evaluateExpr(expr: Expr): RuntimeValue? = evaluate(expr)
    fun getCurrentEnvironment(): Environment = currentEnvironment
    fun getGlobalEnvironment(): Environment = globalEnvironment
}