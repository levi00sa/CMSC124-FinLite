package finlite
import finlite.Callable.*

class Interpreter (
    private val environment: Environment,
    private val finance: FinanceInterpreter
){

    fun execute(stmt: Stmt) {
        when (stmt) {
            is Stmt.Let -> {
                val value = evaluate(stmt.initializer)
                environment.define(stmt.name.lexeme, value)
            }

            is Stmt.SetStmt -> {
                val value = evaluate(stmt.value)
                environment.assign(stmt.name.lexeme, value)
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
                // Execute statements in current environment, not a new child
                for (statement in stmt.statements) {
                    execute(statement)
                }
            }

            is Stmt.IfStmt -> {
                val condition = evaluate(stmt.condition)
                if (isTruthy(condition)) {
                    execute(stmt.thenBranch)
                } else {
                    stmt.elseBranch?.let { execute(it) }
                }
            }

            is Stmt.Scenario -> {
                // Store scenario as its statements for later execution
                environment.define(stmt.name.lexeme, Stmt.Block(stmt.statements))
            }
            // ===================
            // FINANCE STATEMENTS
            // ===================

            is FinanceStmt.LedgerEntryStmt -> {
                val amountVal = evaluate(stmt.amount)

                val numeric = (amountVal as? Number)?.toDouble()
                ?: throw RuntimeError(stmt.account, "Ledger amount must be numeric.")

                val signedAmount = when(stmt.type) {
                    FinanceStmt.EntryType.DEBIT -> numeric
                    FinanceStmt.EntryType.CREDIT -> -numeric
                }

                val account = environment.getOrNull(stmt.account.lexeme) as? LedgerAccount
                    ?: LedgerAccount().also {
                        environment.define(stmt.account.lexeme, it)
                    }

                account.entries.add(signedAmount)

            }

            is FinanceStmt.PortfolioStmt -> {
                val entries = stmt.entries.map { entry ->
                    val amountVal = evaluate(entry.amount)
                    val numeric = (amountVal as? Number)?.toDouble()
                        ?: throw RuntimeError(entry.account, "Portfolio amount must be numeric.")

                    val signed = when(entry.type) {
                        FinanceStmt.EntryType.DEBIT -> numeric
                        FinanceStmt.EntryType.CREDIT -> -numeric
                    }

                entry.account.lexeme to signed
                }
                // store portfolio as map<String, Double>
                environment.define(stmt.name.lexeme, entries.toMap())
            }
            //store scenario as a user-defined callable block to be used in simulation
            is FinanceStmt.ScenarioStmt -> {
                environment.define(stmt.name.lexeme, stmt.body)
            }

            is FinanceStmt.SimulateStmt -> {
                val scenarioBlock = environment.get(stmt.scenarioName.lexeme)
                        as? Stmt.Block ?: throw RuntimeError(
                    stmt.scenarioName, "Unknown scenario '${stmt.scenarioName.lexeme}'."
                )

                val runs = stmt.runs?.let { (evaluate(it) as Number).toInt() } ?: 1
                val step = stmt.step?.let { (evaluate(it) as Number).toDouble() } ?: 1.0
                val results = mutableListOf<Any?>()

                repeat(runs) {
                    val child = environment.createChild()
                    val local = Interpreter(child, finance)

                    local.execute(scenarioBlock)
                    results.add(child.getOrNull("result"))
                }

                println("Simulation results: $results")
                environment.define("lastSimulation", results)
            }

            is FinanceStmt.RunStmt -> {
                val scenarioBlock = environment.get(stmt.scenarioName.lexeme)
                    as? Stmt.Block ?: throw RuntimeError(
                        stmt.scenarioName,
                        "Unknown scenario '${stmt.scenarioName.lexeme}'."
                    )
                
                @Suppress("UNCHECKED_CAST")
                val modelFn = environment.get(stmt.modelName.lexeme)
                    as? (Environment) -> Any? ?: throw RuntimeError(
                        stmt.modelName,
                        "Unknown model '${stmt.modelName.lexeme}'."
                    )

                // Create child environment and execute scenario in it
                val child = environment.createChild()
                val local = Interpreter(child, finance)
                local.execute(scenarioBlock)

                val result = modelFn(child)
                println("Run ${stmt.scenarioName.lexeme} ON ${stmt.modelName.lexeme}: $result")
            }
        }
    }

    private fun evaluate(expr: Expr): Any? =
        when (expr) {

            // used FinanceExpr for FINANCE EXPRESSIONS
            is FinanceExpr -> finance.eval(expr)
            is Expr.Subscript -> evaluateSubscript(expr)
            is Expr.Literal -> expr.value
            is Expr.Variable -> environment.get(expr.name.lexeme)
            is Expr.Assign -> {
                val value = evaluate(expr.value)
                environment.assign(expr.name.lexeme, value)
                value
            }
            is Expr.Grouping -> evaluate(expr.expression)
            is Expr.Unary -> {
                val right = evaluate(expr.right)
                when (expr.operator.type) {
                    TokenType.MINUS -> {
                        if (right is Double) {
                            -right
                        } else {
                            throw RuntimeError(expr.operator, "Operand must be a number.")
                        }
                    }

                    TokenType.BANG, TokenType.NOT -> !isTruthy(right)
                    else -> throw RuntimeError(expr.operator, "Unknown unary operator.")
                }
            }

            is Expr.Binary -> {
                val left = evaluate(expr.left)
                val right = evaluate(expr.right)
                when (expr.operator.type) {
                    TokenType.PLUS -> when {
                        left is Double && right is Double -> left + right
                        left is String || right is String ->
                            valueToString(left) + valueToString(right)
                        left is List<*> && right is List<*> ->
                            elementWiseNumericOp(expr.operator, left, right) { a, b -> a + b }
                        else -> throw RuntimeError(
                            expr.operator,
                            "Operands must be numbers, strings, or numeric lists."
                        )
                    }

                    TokenType.MINUS -> when {
                        left is Double && right is Double -> left - right
                        left is List<*> && right is List<*> ->
                            elementWiseNumericOp(expr.operator, left, right) { a, b -> a - b }
                        else -> {
                            checkNumberOperands(expr.operator, left, right)
                            (left as Double) - (right as Double)
                        }
                    }

                    TokenType.STAR -> {
                        checkNumberOperands(expr.operator, left, right)
                        (left as Double) * (right as Double)
                    }

                    TokenType.SLASH -> {
                        checkNumberOperands(expr.operator, left, right)
                        if ((right as Double) == 0.0) {
                            throw RuntimeError(expr.operator, "Division by zero.")
                        }
                        (left as Double) / right
                    }

                    TokenType.GREATER -> {
                        checkNumberOperands(expr.operator, left, right)
                        (left as Double) > (right as Double)
                    }

                    TokenType.GREATER_EQUAL -> {
                        checkNumberOperands(expr.operator, left, right)
                        (left as Double) >= (right as Double)
                    }

                    TokenType.LESS -> {
                        checkNumberOperands(expr.operator, left, right)
                        (left as Double) < (right as Double)
                    }

                    TokenType.LESS_EQUAL -> {
                        checkNumberOperands(expr.operator, left, right)
                        (left as Double) <= (right as Double)
                    }

                    TokenType.EQUAL_EQUAL -> isEqual(left, right)
                    TokenType.BANG_EQUAL -> !isEqual(left, right)
                    else -> throw RuntimeError(expr.operator, "Unknown binary operator.")
                }
            }

            is Expr.ListLiteral -> expr.elements.map { evaluate(it) }
            is Expr.Call -> throw RuntimeError(
                Token(TokenType.ERROR, "", null, 0),
                "Unsupported expression: ${expr::class.simpleName}"
            )
            else -> throw RuntimeError(
                Token(TokenType.ERROR, "", null, 0),"Unsupported expression: ${expr::class.simpleName}"
            )
        }

    private fun evaluateSubscript(expr: Expr.Subscript): Any? {
        val container = evaluate(expr.container)
        val indexVal = evaluate(expr.index)

        val index = (indexVal as? Number)?.toInt()
            ?: throw RuntimeError(expr.bracket, "Index must be numeric.")

        return when (container) {
            is List<*> -> container.getOrNull(index)
            is Map<*, *> -> container[indexVal]
            else -> throw RuntimeError(expr.bracket, "Object is not subscriptable.")
        }
    }


    private fun isTruthy(value: Any?): Boolean {
        if (value == null) return false
        if (value is Boolean) return value
        return true
    }

    private fun isEqual(a: Any?, b: Any?): Boolean {
        if (a == null && b == null) return true
        if (a == null) return false
        return a == b
    }

    private fun checkNumberOperands(operator: Token, left: Any?, right: Any?) {
        if (left is Double && right is Double) return
        throw RuntimeError(operator, "Operands must be numbers.")
    }

    fun builtin(fn: (List<Any?>) -> Any?): Callable {
        return object : Callable {
            override fun arity(): Int = -1  // variable arity
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                return fn(arguments)
            }
            override fun toString(): String = "<builtin>"
        }
    }

    private fun elementWiseNumericOp(
        operator: Token,
        left: List<*>,
        right: List<*>,
        op: (Double, Double) -> Double
    ): List<Double> {
        val size = minOf(left.size, right.size)
        if (size == 0) return emptyList()
        val result = ArrayList<Double>(size)
        for (i in 0 until size) {
            val a = (left[i] as? Number)?.toDouble()
                ?: throw RuntimeError(operator, "Operands must be numbers.")
            val b = (right[i] as? Number)?.toDouble()
                ?: throw RuntimeError(operator, "Operands must be numbers.")
            result.add(op(a, b))
        }
        return result
    }

    private fun valueToString(value: Any?): String {
        return when (value) {
            null -> "nil"
            is Double ->
                if (value % 1.0 == 0.0) {
                    value.toInt().toString()
                } else {
                    value.toString()
                }

            else -> value.toString()
        }
    }

}