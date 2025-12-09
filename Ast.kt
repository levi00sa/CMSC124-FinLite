package finlite

sealed class Expr {
    data class Literal(val value: Any?) : Expr()
    data class Variable(val name: Token) : Expr()
    data class ScopeResolution(val scope: ScopeType, val name: Token) : Expr()
    
    enum class ScopeType {
        GLOBAL,  // global::var
        PARENT   // parent::var
    }
    
    data class Unary(val operator: Token, val right: Expr) : Expr()
    data class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr()
    data class Grouping(val expression: Expr) : Expr()
    data class Call(val callee: Expr, val paren: Token, val arguments: List<Expr>) : Expr()
    data class ListLiteral(val elements: List<Expr>) : Expr()
    data class Assign(val name: Token, val value: Expr) : Expr()
    data class Subscript(val container: Expr, val index: Expr, val end: Expr?, val bracket: Token, val isSlice: Boolean = false) : Expr()
    data class Get(val obj: Expr, val name: Token) : Expr()
    data class TimeSeries(val source: Expr, val window: Expr) : Expr()
    data class Lambda(val params: List<Token>, val body: Expr) : Expr()
    data class ObjectLiteral(val fields: Map<String, Expr>) : Expr()
}

sealed class Stmt {
    object ErrorStmt: Stmt()
    data class Let(val name: Token, val initializer: Expr) : Stmt()
    data class SetStmt(val name: Token, val value: Expr) : Stmt()
    data class ExpressionStmt(val expression: Expr) : Stmt()
    data class Print(val value: Expr) : Stmt()
    data class ReturnStmt(val value: Expr?) : Stmt()

    data class Block(val statements: List<Stmt>) : Stmt()

    data class IfStmt(
        val condition: Expr,
        val thenBranch: Stmt,
        val elseifBranches: List<Pair<Expr, Stmt>> = emptyList(),
        val elseBranch: Stmt? = null
    ) : Stmt()

    data class WhileStmt(
        val condition: Expr,
        val body: Stmt
    ) : Stmt()

    data class For(
        val variable: Token,
        val start: Expr,
        val end: Expr,
        val step: Expr?,
        val body: Stmt
    ) : Stmt()

    data class ForEach(
        val variable: Token,
        val iterable: Expr,
        val body: Stmt
    ) : Stmt()

    data class Scenario(val name: Token, val statements: List<Stmt>) : Stmt()
}

// ============================================
// Finance Statements
// ============================================

sealed class FinanceStmt : Stmt() {
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
}

// ============================================
// Finance Expressions
// ============================================

sealed class FinanceExpr : Expr() {

    // NPV(cashflows, rate)
    data class NPV(val cashflows: Expr, val rate: Expr) : FinanceExpr()
    data class IRR(val cashflows: Expr) : FinanceExpr()
    data class PV(val rate: Expr, val nper: Expr, val pmt: Expr?, val fv: Expr?) : FinanceExpr()
    data class FV(val rate: Expr, val nper: Expr, val pmt: Expr?, val pv: Expr?) : FinanceExpr()

    data class WACC(
        val equityWeight: Expr,
        val debtWeight: Expr,
        val costOfEquity: Expr,
        val costOfDebt: Expr,
        val taxRate: Expr
    ) : FinanceExpr()

    data class CAPM(
        // In language usage: CAPM(beta, rf, prem)
        //  - beta: number or list of betas
        //  - rf: risk‑free rate
        //  - prem: market risk premium (Rm - Rf)
        val beta: Expr,
        val riskFree: Expr,
        val premium: Expr
    ) : FinanceExpr()

    data class VAR(val portfolio: Expr, val confidence: Expr) : FinanceExpr()

    data class SMA(val series: Expr, val period: Expr) : FinanceExpr()
    data class EMA(val series: Expr, val period: Expr) : FinanceExpr()

    data class Amortize(
        val principal: Expr,
        val rate: Expr,
        val periods: Expr
    ) : FinanceExpr()

    // ----------------------------------------------------------
    // Table and Cashflow Expressions
    // ----------------------------------------------------------

    data class TableLiteral(
        val columns: List<String>,
        val values: Map<String, Expr>   // columnName → list expression
    ) : FinanceExpr()

    data class CashflowLiteral(
        val entries: List<FinanceStmt.LedgerEntryStmt>
    ) : FinanceExpr()

    // Portfolio with assets and weights
    data class PortfolioLiteral(
        val assets: Expr,      // ListLiteral of asset values
        val weights: Expr      // ListLiteral of weight values
    ) : FinanceExpr()

    // Access CF.INFLOW or TABLE.YEAR
    data class ColumnAccess(val tableExpr: Expr, val column: Token) : FinanceExpr()

    // TimeSeries([values], FROM date TO date)
    /*
    data class TimeSeriesLiteral(
        val values: Expr,
        val from: Expr?,
        val to: Expr?
    ) : FinanceExpr()
    */
    data class FinTable(
        val columns: List<String>,
        // Allow arbitrary column types; numeric functions will coerce as needed.
        val data: Map<String, List<Any?>>
    ): FinanceExpr()
}

