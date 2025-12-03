package finlite


data class FinTable(
    val columns: Map<String, List<Double>>
) {
    fun getColumn(name: String): List<Double> =
        columns[name] ?: error("Unknown column '$name'")
}

data class Cashflow(
    val flows: List<Double>
)

data class Portfolio(
    val assets: List<Double>, 
    val weights: List<Double>
)

data class LedgerEntry(
    val date: String,
    val debit: Double?,
    val credit: Double?,
    val description: String
)

data class Ledger(
    val entries: MutableList<LedgerEntry> = mutableListOf()
)
