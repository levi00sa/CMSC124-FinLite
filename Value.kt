package finlite

sealed class Value {
    data class NumberVal(val value: Double) : Value()
    data class StringVal(val value: String) : Value()
    data class LedgerVal(val entries: MutableList<Double>) : Value()
    data class PortfolioVal(val accounts: Map<String, Double>) : Value()
    data class BlockVal(val block: Stmt.Block) : Value()
    data class TableValue(val columns: List<String>, val data: Map<String, List<Value>>) : Value()
    data class DateVal(val value: String) : Value()
}
