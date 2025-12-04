package finlite

sealed class FinanceStmt : Stmt() {

    // ----------------------------------------------------------
    // PORTFOLIO / LEDGER
    // ----------------------------------------------------------

    data class PortfolioStmt(
        val name: Token,
        val entries: List<FinanceStmt.LedgerEntryStmt>
    ) : FinanceStmt()

    data class LedgerEntryStmt(
        val account: Token,
        val amount: Expr,
        val type: EntryType
    ) : FinanceStmt()

    enum class EntryType { DEBIT, CREDIT }

    // ----------------------------------------------------------
    // SCENARIO definitions and simulations
    // ----------------------------------------------------------

    data class ScenarioStmt(
        val name: Token,
        val body: Stmt.Block
    ) : FinanceStmt()

    data class RunStmt(
        val scenarioName: Token,
        val modelName: Token
    ) : FinanceStmt()

    data class SimulateStmt(
        val scenarioName: Token,
        val runs: Expr?,
        val step: Expr?
    ) : FinanceStmt()

    data class ReturnStmt(val value: Expr?) : Stmt()

}
