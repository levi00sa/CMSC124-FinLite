package finlite

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
    data class TimeSeriesLiteral(
        val values: Expr,
        val from: Expr?,
        val to: Expr?
    ) : FinanceExpr()

    data class FinTable(
        val columns: List<String>,
        // Allow arbitrary column types; numeric functions will coerce as needed.
        val data: Map<String, List<Any?>>
    )
}
