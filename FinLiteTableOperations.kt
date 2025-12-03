package finlite

object FinLiteTableOps {

    fun filter(table: FinTable, column: String, predicate: (Double) -> Boolean): FinTable {
        val target = table.getColumn(column)
        val mask = target.map(predicate)

        val newCols = table.columns.mapValues { (_, col) ->
            col.zip(mask).filter { it.second }.map { it.first }
        }

        return FinTable(newCols)
    }

    fun join(left: FinTable, right: FinTable): FinTable {
        val merged = left.columns + right.columns
        return FinTable(merged)
    }

    fun columnApply(table: FinTable, name: String, transform: (Double) -> Double): FinTable {
        val newCols = table.columns.toMutableMap()
        newCols[name] = table.getColumn(name).map(transform)
        return FinTable(newCols)
    }
}
