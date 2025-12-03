package finlite

class FinanceRuntime {

    fun npv(rate: Double, cashflows: List<Double>): Double =
        cashflows.withIndex().sumOf { (i, cf) ->
            cf / Math.pow(1 + rate, i.toDouble())
        }

    fun irr(cashflows: List<Double>): Double {
        var guess = 0.1
        repeat(100) {
            val f = npv(guess, cashflows)
            val fPrime = cashflows.withIndex().sumOf { (i, cf) ->
                -i * cf / Math.pow(1 + guess, i.toDouble() + 1)
            }
            guess -= f / fPrime
        }
        return guess
    }

    fun pv(rate: Double, nper: Int, pmt: Double?, fv: Double?): Double =
        (pmt ?: 0.0) * (1 - (1 / Math.pow(1 + rate, nper.toDouble()))) / rate -
            (fv ?: 0.0) / Math.pow(1 + rate, nper.toDouble())

    fun fv(rate: Double, nper: Int, pmt: Double?, pv: Double?): Double =
        (pv ?: 0.0) * Math.pow(1 + rate, nper.toDouble()) +
            (pmt ?: 0.0) * ((Math.pow(1 + rate, nper.toDouble()) - 1) / rate)

    fun wacc(ew: Double, dw: Double, ce: Double, cd: Double, tax: Double): Double =
        ew * ce + dw * cd * (1 - tax)

    fun capm(rf: Double, beta: Double, rm: Double): Double =
        rf + beta * (rm - rf)

    fun varCalc(portfolio: Any?, confidence: Double): Double {
        val list = convertToListOfDouble(portfolio)
        val mean = list.average()
        val std = Math.sqrt(list.map { (it - mean) * (it - mean) }.average())
        val z = when (confidence) {
            0.95 -> 1.65
            0.99 -> 2.33
            else -> 1.0
        }
        return z * std
    }

    fun sma(values: List<Double>, period: Int): List<Double> =
        values.windowed(period).map { it.average() }

    fun ema(values: List<Double>, period: Int): List<Double> {
        val k = 2.0 / (period + 1)
        val result = mutableListOf(values.first())
        for (i in 1 until values.size) {
            val prev = result.last()
            result.add(values[i] * k + prev * (1 - k))
        }
        return result
    }

    fun amortize(principal: Double, rate: Double, periods: Int): List<Map<String, Double>> {
        val payment = (principal * rate) / (1 - Math.pow(1 + rate, -periods.toDouble()))
        var balance = principal
        val table = mutableListOf<Map<String, Double>>()

        repeat(periods) {
            val interest = balance * rate
            val principalPay = payment - interest
            balance -= principalPay
            table.add(
                mapOf(
                    "payment" to payment,
                    "interest" to interest,
                    "principal" to principalPay,
                    "balance" to balance
                )
            )
        }
        return table
    }

    /**
     * Safely converts a value to List<Double>.
     * Handles various input types and converts them to Double.
     */
    private fun convertToListOfDouble(value: Any?): List<Double> {
        val list = value as? List<*> ?: throw IllegalArgumentException("Expected a list")
        return list.mapNotNull { 
            when (it) {
                is Double -> it
                is Number -> it.toDouble()
                else -> it.toString().toDoubleOrNull()
            }
        }
    }
}

