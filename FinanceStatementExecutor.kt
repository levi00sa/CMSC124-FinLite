package finlite

class FinanceStatementExecutor(
    private val environment: Environment,
    private val interpreter: Interpreter
) {

    fun execute(stmt: Stmt) {
        when (stmt) {
            is FinanceStmt.LedgerEntryStmt -> executeLedgerEntry(stmt)
            is FinanceStmt.PortfolioStmt -> executePortfolio(stmt)
            is FinanceStmt.ScenarioStmt -> executeScenario(stmt)
            is FinanceStmt.SimulateStmt -> executeSimulate(stmt)
            is FinanceStmt.RunStmt -> executeRun(stmt)
            else -> {} // Not a finance statement
        }
    }

    private fun executeLedgerEntry(stmt: FinanceStmt.LedgerEntryStmt) {
        val amountVal = interpreter.evaluateExpr(stmt.amount)

        val numeric = (amountVal as? RuntimeValue.Number)?.value
            ?: throw RuntimeError(stmt.account, "Ledger amount must be numeric.")

        val signedAmount = when (stmt.type) {
            FinanceStmt.EntryType.DEBIT -> numeric
            FinanceStmt.EntryType.CREDIT -> -numeric
        }

        val account = (environment.getOrNull(stmt.account.lexeme) as? RuntimeValue.Ledger)
            ?: RuntimeValue.Ledger()

        account.entries.add(RuntimeValue.LedgerEntry(
            date = java.time.LocalDate.now().toString(),
            debit = if (signedAmount > 0) signedAmount else null,
            credit = if (signedAmount < 0) -signedAmount else null,
            description = "Entry"
        ))
        environment.define(stmt.account.lexeme, account)
    }

    private fun executePortfolio(stmt: FinanceStmt.PortfolioStmt) {
        val entries = stmt.entries.map { entry ->
            val amountVal = interpreter.evaluateExpr(entry.amount)
            val numeric = (amountVal as? RuntimeValue.Number)?.value
                ?: throw RuntimeError(null, "Portfolio amount must be numeric.")

            val signed = when (entry.type) {
                FinanceStmt.EntryType.DEBIT -> numeric
                FinanceStmt.EntryType.CREDIT -> -numeric
            }

            entry.account.lexeme to signed
        }
        // store portfolio as RuntimeValue.Portfolio with equal weights
        val map = entries.toMap()
        if (map.isNotEmpty()) {
            val weight = 1.0 / map.size
            environment.define(stmt.name.lexeme, RuntimeValue.Portfolio(
                assets = map.values.toList(),
                weights = List(map.size) { weight }
            ))
        }
    }

    private fun executeScenario(stmt: FinanceStmt.ScenarioStmt) {
        // store scenario as a RuntimeValue.Block to be used in simulation
        environment.define(stmt.name.lexeme, RuntimeValue.Block(stmt.body))
    }

    private fun executeSimulate(stmt: FinanceStmt.SimulateStmt) {
        val scenarioBlock = (environment.get(stmt.scenarioName.lexeme) as? RuntimeValue.Block)?.block
            ?: throw RuntimeError(
                null, "Unknown scenario '${stmt.scenarioName.lexeme}'."
            )

        val runs = stmt.runs?.let { (interpreter.evaluateExpr(it) as? RuntimeValue.Number)?.value?.toInt() } ?: 1
        val step = stmt.step?.let { (interpreter.evaluateExpr(it) as? RuntimeValue.Number)?.value } ?: 1.0
        val results = mutableListOf<RuntimeValue?>()

        repeat(runs) {
            val child = environment.createChild()
            val local = Interpreter(child)

            // Execute scenario statements directly in the child environment so variables persist
            local.executeBlock(scenarioBlock.statements, child)
            results.add(child.getOrNull("result"))
        }

        println("Simulation results: $results")
        environment.define("lastSimulation", RuntimeValue.ListValue(results))
    }

    private fun executeRun(stmt: FinanceStmt.RunStmt) {
        val scenarioBlock = (environment.get(stmt.scenarioName.lexeme) as? RuntimeValue.Block)?.block
            ?: throw RuntimeError(
                null,
                "Unknown scenario '${stmt.scenarioName.lexeme}'."
            )

        val model = environment.get(stmt.modelName.lexeme)
            ?: throw RuntimeError(null, "Unknown model '${stmt.modelName.lexeme}'.")

        // Create child environment and execute scenario in it
        val child = environment.createChild()
        val local = Interpreter(child)

        // Execute scenario statements directly in the child environment so variables persist
        local.executeBlock(scenarioBlock.statements, child)

        // Get the result and apply model if it's a function
        val result = child.getOrNull("result")
        val finalResult = if (model is RuntimeValue.Function) {
            val callable = (model as RuntimeValue.Function).callable
            callable.call(child, listOf(result))
        } else {
            result
        }
        println("Run ${stmt.scenarioName.lexeme} ON ${stmt.modelName.lexeme}: $finalResult")
    }
}


