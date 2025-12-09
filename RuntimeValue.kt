package finlite

/**
 * Sealed hierarchy representing all values that can exist at runtime in FinLite.
 * Replaces the loose Any? type with a strongly-typed system.
 * 
 * All runtime values must be instances of RuntimeValue or null (for uninitialized vars).
 * This enables exhaustive pattern matching and prevents type confusion.
 */
sealed class RuntimeValue {
    
    /** Numeric value (Double) */
    data class Number(val value: Double) : RuntimeValue() {
        override fun toString() = value.toString()
    }
    
    /** String value */
    data class String(val value: kotlin.String) : RuntimeValue() {
        override fun toString() = "\"$value\""
    }
    
    /** Boolean value */
    data class Bool(val value: Boolean) : RuntimeValue() {
        override fun toString() = value.toString()
    }
    
    /** List of runtime values */
    data class ListValue(val elements: kotlin.collections.List<RuntimeValue?>) : RuntimeValue() {
        override fun toString() = "[${elements.joinToString(", ") { element: RuntimeValue? -> element?.toString() ?: "null" }}]"
    }
    
    /** Financial table: typed columns of numbers */
    data class Table(val columns: kotlin.collections.Map<kotlin.String, kotlin.collections.List<kotlin.Double>>) : RuntimeValue() {
        init {
            if (columns.isNotEmpty()) {
                val firstSize = columns.values.first().size
                require(columns.values.all { col: kotlin.collections.List<kotlin.Double> -> col.size == firstSize }) {
                    "All table columns must have the same length"
                }
            }
        }
        
        fun getColumn(name: kotlin.String): kotlin.collections.List<kotlin.Double> =
            columns[name] ?: error("Unknown column '$name'")
        
        override fun toString(): kotlin.String {
            if (columns.isEmpty()) return "TABLE()"

            // Prepare headers and rows as strings
            val colNames = columns.keys.toList()
            val nCols = colNames.size
            val nRows = columns.values.first().size

            // Convert numeric values to nicely formatted strings
            val rows: List<List<kotlin.String>> = (0 until nRows).map { r ->
                colNames.map { col ->
                    val v = columns[col]!![r]
                    // Format doubles: drop .0 for whole numbers
                    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
                }
            }

            // Compute column widths
            val widths = IntArray(nCols)
            for (i in 0 until nCols) {
                val nameLen = colNames[i].length
                val maxCell = rows.map { it[i].length }.maxOrNull() ?: 0
                widths[i] = maxOf(nameLen, maxCell)
            }

            // Build header (column names padded with one space on each side)
            val headerRow = colNames.mapIndexed { i, name -> " ${name.padEnd(widths[i])} " }.joinToString("|")

            // Build divider matching padded widths
            val divider = widths.map { "-".repeat(it + 2) }.joinToString("+")

            // Build formatted rows with one-space padding around each cell
            val body = rows.joinToString("\n") { row ->
                row.mapIndexed { i, cell -> " ${cell.padEnd(widths[i])} " }.joinToString("|")
            }

            return "TABLE(${colNames.joinToString(", ")})\n" + headerRow + "\n" + divider + "\n" + body
        }
    }
    
    /** Financial portfolio: assets and weights */
    data class Portfolio(
        val assets: kotlin.collections.List<Double>,
        val weights: kotlin.collections.List<Double>
    ) : RuntimeValue() {
        init {
            require(assets.size == weights.size) { "Assets and weights must have same size" }
            require(weights.all { w -> w >= 0.0 }) { "Weights must be non-negative" }
            require(kotlin.math.abs(weights.sum() - 1.0) < 1e-9) { "Weights must sum to 1.0" }
        }
        override fun toString() = "PORTFOLIO(assets=${assets.size}, weights=${weights.size})"
    }
    
    /** Cashflow sequence */
    data class Cashflow(val flows: kotlin.collections.List<kotlin.Double>) : RuntimeValue() {
        override fun toString() = "CASHFLOW(${flows.size} periods)"
    }
    
    /** Ledger entry (accounting record) */
    data class LedgerEntry(
        val date: kotlin.String,
        val debit: Double?,
        val credit: Double?,
        val description: kotlin.String
    ) : RuntimeValue() {
        override fun toString() = "LedgerEntry($date: $description)"
    }
    
    /** Ledger (collection of entries) */
    data class Ledger(val entries: MutableList<LedgerEntry> = mutableListOf()) : RuntimeValue() {
        override fun toString() = "LEDGER(${entries.size} entries)"
    }
    
    /** Block (code block for lambda/closure) */
    data class Block(val block: Stmt.Block) : RuntimeValue() {
        override fun toString() = "<block>"
    }
    
    /** Callable function */
    data class Function(val callable: Callable) : RuntimeValue() {
        override fun toString() = "<function>"
    }
    
    /** Money value with currency */
    data class Money(val amount: Double, val currency: kotlin.String) : RuntimeValue() {
        override fun toString() = "$currency $amount"
    }
    
    /** Interest rate (0.0 to 1.0) */
    data class Rate(val value: Double) : RuntimeValue() {
        init {
            require(value in 0.0..1.0) { "Rate must be between 0.0 and 1.0, got $value" }
        }
        override fun toString() = "${value * 100}%"
    }
    
    /** Percentage (0.0 to 100.0) */
    data class Percentage(val value: Double) : RuntimeValue() {
        init {
            require(value in 0.0..100.0) { "Percentage must be between 0.0 and 100.0, got $value" }
        }
        override fun toString() = "$value%"
    }
    
    /** Time period with unit (days, months, years) */
    data class TimePeriod(val value: Double, val unit: kotlin.String) : RuntimeValue() {
        init {
            require(unit in listOf("days", "months", "years")) { "Invalid time unit: $unit" }
        }
        override fun toString() = "$value $unit"
    }

    /** Object with named fields */
    data class Object(val fields: kotlin.collections.Map<kotlin.String, RuntimeValue?>) : RuntimeValue() {
        fun getField(name: kotlin.String): RuntimeValue? = fields[name]
        override fun toString(): kotlin.String {
            if (fields.isEmpty()) return "{}"
            val entries = fields.entries.joinToString(", ") { (k, v) -> "$k: ${v?.toString() ?: "null"}" }
            return "{ $entries }"
        }
    }

    /** Lambda value (closure) with captured environment */
    data class Lambda(
        val params: kotlin.collections.List<Token>,
        val body: Expr,
        val capturedEnv: Environment
    ) : RuntimeValue() {
        override fun toString() = "<lambda(${params.size} params)>"
    }
}

/**
 * Type extraction helpers to safely convert RuntimeValue to Kotlin types.
 * These throw descriptive errors on type mismatches.
 */

fun RuntimeValue?.asNumber(context: kotlin.String = ""): Double = when (this) {
    is RuntimeValue.Number -> this.value
    null -> throw RuntimeError(null, "Expected number$context, got null")
    else -> throw RuntimeError(null, "Expected number$context, got ${this::class.simpleName}")
}

fun RuntimeValue?.asString(context: kotlin.String = ""): kotlin.String = when (this) {
    is RuntimeValue.String -> this.value
    null -> throw RuntimeError(null, "Expected string$context, got null")
    else -> throw RuntimeError(null, "Expected string$context, got ${this::class.simpleName}")
}

fun RuntimeValue?.asBool(context: kotlin.String = ""): Boolean = when (this) {
    is RuntimeValue.Bool -> this.value
    null -> throw RuntimeError(null, "Expected boolean$context, got null")
    else -> throw RuntimeError(null, "Expected boolean$context, got ${this::class.simpleName}")
}

fun RuntimeValue?.asTable(context: kotlin.String = ""): RuntimeValue.Table = when (this) {
    is RuntimeValue.Table -> this
    null -> throw RuntimeError(null, "Expected table$context, got null")
    else -> throw RuntimeError(null, "Expected table$context, got ${this::class.simpleName}")
}

fun RuntimeValue?.asPortfolio(context: kotlin.String = ""): RuntimeValue.Portfolio = when (this) {
    is RuntimeValue.Portfolio -> this
    null -> throw RuntimeError(null, "Expected portfolio$context, got null")
    else -> throw RuntimeError(null, "Expected portfolio$context, got ${this::class.simpleName}")
}

fun RuntimeValue?.asCallable(context: kotlin.String = ""): Callable = when (this) {
    is RuntimeValue.Function -> this.callable
    is Callable -> this  // Allow raw Callable for backward compat during migration
    null -> throw RuntimeError(null, "Expected callable$context, got null")
    else -> throw RuntimeError(null, "Expected callable$context, got ${this::class.simpleName}")
}

fun RuntimeValue?.asRate(context: kotlin.String = ""): Double = when (this) {
    is RuntimeValue.Rate -> this.value
    is RuntimeValue.Number -> this.value.also { 
        if (it !in 0.0..1.0) throw RuntimeError(null, "Rate must be 0-1, got $it")
    }
    null -> throw RuntimeError(null, "Expected rate$context, got null")
    else -> throw RuntimeError(null, "Expected rate$context, got ${this::class.simpleName}")
}

fun RuntimeValue?.asPercentage(context: kotlin.String = ""): Double = when (this) {
    is RuntimeValue.Percentage -> this.value
    is RuntimeValue.Number -> this.value.also {
        if (it !in 0.0..100.0) throw RuntimeError(null, "Percentage must be 0-100, got $it")
    }
    null -> throw RuntimeError(null, "Expected percentage$context, got null")
    else -> throw RuntimeError(null, "Expected percentage$context, got ${this::class.simpleName}")
}

fun RuntimeValue?.asList(context: kotlin.String = ""): kotlin.collections.List<RuntimeValue?> = when (this) {
    is RuntimeValue.ListValue -> this.elements
    null -> throw RuntimeError(null, "Expected list$context, got null")
    else -> throw RuntimeError(null, "Expected list$context, got ${this::class.simpleName}")
}

/**
 * Coercion helpers - convert compatible types when appropriate
 */

fun RuntimeValue?.toNumber(): Double = when (this) {
    is RuntimeValue.Number -> this.value
    is RuntimeValue.Rate -> this.value
    is RuntimeValue.Percentage -> this.value / 100.0
    else -> this.asNumber()
}

fun RuntimeValue?.toDoubleList(): kotlin.collections.List<Double> = when (this) {
    is RuntimeValue.ListValue -> this.elements.map { element: RuntimeValue? -> element.toNumber() }
    is RuntimeValue.Table -> error("Use getColumn() for tables")
    else -> throw RuntimeError(null, "Cannot convert ${this?.let { it::class.simpleName } ?: "null"} to number list")
}

fun isTruthy(value: RuntimeValue?): Boolean = when (value) {
    null -> false
    is RuntimeValue.Bool -> value.value
    else -> true
}

fun isEqual(a: RuntimeValue?, b: RuntimeValue?): Boolean {
    if (a == null && b == null) return true
    if (a == null || b == null) return false
    return a == b
}
