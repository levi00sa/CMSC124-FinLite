package finlite

object FinLiteStandardLib {

    fun loadInto(env: Environment, interpreter: Interpreter) {
        // -------------------------
        // VALUATION MODEL
        // -------------------------
        env.define("valuation_model", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(ctx: Any?, arguments: List<Any?>): Any? {
                // Prefer the provided context environment when available (e.g., RUN uses a child env as ctx)
                val lookupEnv = when (ctx) {
                    is Environment -> ctx
                    else -> interpreter.getCurrentEnvironment()
                }

                val rate = when (val v = lookupEnv.get("rate")) {
                    is RuntimeValue.Number -> v.value
                    else -> 0.0
                }
                val growth = when (val v = lookupEnv.get("growth")) {
                    is RuntimeValue.Number -> v.value
                    else -> 0.0
                }
                println("Model: rate=$rate, growth=$growth")
                return rate * growth
            }
        }))

        // -------------------------
        // BASIC FUNCTIONS
        // -------------------------
        env.define("show", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(ctx: Any?, arguments: List<Any?>) = run {
                println(arguments.joinToString(" "))
                arguments.firstOrNull()  // Return the first argument after printing
            }
        }))

        env.define("len", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(ctx: Any?, arguments: List<Any?>): Any? {
                if (arguments.isEmpty()) throw RuntimeException("len() requires 1 argument")
                return convertToDoubleList(arguments[0]).size.toDouble()
            }
        }))

        env.define("sum", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(ctx: Any?, arguments: List<Any?>): Any? {
                if (arguments.isEmpty()) throw RuntimeException("sum() requires 1 argument")
                return convertToDoubleList(arguments[0]).sum()
            }
        }))
 
        env.define("avg", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(ctx: Any?, arguments: List<Any?>): Any? {
                if (arguments.isEmpty()) throw RuntimeException("avg() requires 1 argument")
                return convertToDoubleList(arguments[0]).average()
            }
        }))

        // -------------------------
        // TIME SERIES HELPERS
        // -------------------------
        env.define("returns", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(ctx: Any?, arguments: List<Any?>): Any? {
                if (arguments.isEmpty()) throw RuntimeException("returns() requires 1 argument")
                val doubles = convertToDoubleList(arguments[0])
                // Return as List<RuntimeValue.Number> for compatibility
                return doubles.zipWithNext { a, b -> RuntimeValue.Number((b - a) / a) }
            }
        }))

        env.define("volatility", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(ctx: Any?, arguments: List<Any?>): Any? {
                if (arguments.isEmpty()) throw RuntimeException("volatility() requires 1 argument")
                val r = convertToDoubleList(arguments[0])
                return Math.sqrt(r.map { it * it }.average())
            }
        }))

        // -------------------------
        // FINANCE SHORTCUTS
        // -------------------------
        env.define("CAPM", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(ctx: Any?, arguments: List<Any?>): Any? {
                if (arguments.size < 3) throw RuntimeException("CAPM() requires 3 arguments")
                val rf = toDouble(arguments[0])
                val beta = toDouble(arguments[1])
                val rm = toDouble(arguments[2])
                return rf + beta * (rm - rf)
            }
        }))

        env.define("WACC", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(ctx: Any?, arguments: List<Any?>): Any? {
                if (arguments.size < 5) throw RuntimeException("WACC() requires 5 arguments")
                val ew = toDouble(arguments[0])
                val dw = toDouble(arguments[1])
                val ce = toDouble(arguments[2])
                val cd = toDouble(arguments[3])
                val tax = toDouble(arguments[4])
                return ew * ce + dw * cd * (1 - tax)
            }
        }))

        env.define("FV", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(ctx: Any?, arguments: List<Any?>): Any? {
                if (arguments.size < 2) throw RuntimeException("FV() requires at least 2 arguments")
                val rate = toDouble(arguments[0])
                val nper = toDouble(arguments[1]).toInt()
                val pmt = if (arguments.size > 2) toDouble(arguments[2]) else 0.0
                val pv  = if (arguments.size > 3) toDouble(arguments[3]) else 0.0
                return pv * Math.pow(1 + rate, nper.toDouble()) +
                        pmt * ((Math.pow(1 + rate, nper.toDouble()) - 1) / rate)
            }
        }))

        // -------------------------
        // UTILITY FUNCTIONS
        // -------------------------
        env.define("ASSERT", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(ctx: Any?, arguments: List<Any?>): Any? {
                if (arguments.isEmpty()) throw RuntimeException("ASSERT() requires at least 1 argument")
                val cond = isTruthy(arguments[0])
                val msg = if (arguments.size > 1) arguments[1]?.toString() else "Assertion failed."
                if (!cond) throw RuntimeException(msg ?: "Assertion failed.")
                return null
            }
        }))

        env.define("ERROR", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(ctx: Any?, arguments: List<Any?>): Any? {
                throw RuntimeException(arguments.joinToString(" "))
            }
        }))

        env.define("MAP", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(ctx: Any?, arguments: List<Any?>): Any? {
                if (arguments.size < 2) throw RuntimeException("MAP() requires 2 arguments")
                
                val listArg = arguments[0]
                val fnArg = arguments[1]
                
                // Extract the actual list
                val list: List<*> = when (listArg) {
                    is RuntimeValue.ListValue -> listArg.elements
                    is List<*> -> listArg
                    else -> throw RuntimeException("MAP() first argument must be a list")
                }
                
                // Handle RuntimeValue.Lambda or Callable
                return when (fnArg) {
                    is RuntimeValue.Lambda -> {
                        val interp = ctx as? Interpreter
                            ?: throw RuntimeException("MAP with lambda requires interpreter context")
                        list.map { element ->
                            interp.callLambda(fnArg, listOf(element as? RuntimeValue))
                        }
                    }
                    is RuntimeValue.Function -> {
                        list.map { element ->
                            fnArg.callable.call(ctx, listOf(element)) as? RuntimeValue
                        }
                    }
                    is Callable -> {
                        list.map { element ->
                            fnArg.call(ctx, listOf(element)) as? RuntimeValue
                        }
                    }
                    else -> throw RuntimeException("MAP() second argument must be a function or lambda")
                }
            }
        }))

        env.define("FILTER", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(ctx: Any?, arguments: List<Any?>): Any? {
                if (arguments.size < 2) throw RuntimeException("FILTER() requires 2 arguments")

                val listArg = arguments[0]
                val fnArg = arguments[1]

                val list: List<*> = when (listArg) {
                    is RuntimeValue.ListValue -> listArg.elements
                    is List<*> -> listArg
                    else -> throw RuntimeException("FILTER() first argument must be a list")
                }

                val result = mutableListOf<RuntimeValue?>()
                
                when (fnArg) {
                    is RuntimeValue.Lambda -> {
                        val interp = ctx as? Interpreter
                            ?: throw RuntimeException("FILTER with lambda requires interpreter context")
                        for (element in list) {
                            val predicateResult = interp.callLambda(fnArg, listOf(element as? RuntimeValue))
                            if (isTruthy(predicateResult)) {
                                result.add(element as? RuntimeValue)
                            }
                        }
                    }
                    is RuntimeValue.Function -> {
                        for (element in list) {
                            val predicateResult = fnArg.callable.call(ctx, listOf(element))
                            if (isTruthy(predicateResult)) {
                                result.add(element as? RuntimeValue)
                            }
                        }
                    }
                    is Callable -> {
                        for (element in list) {
                            val predicateResult = fnArg.call(ctx, listOf(element))
                            if (isTruthy(predicateResult)) {
                                result.add(element as? RuntimeValue)
                            }
                        }
                    }
                    else -> throw RuntimeException("FILTER() second argument must be a function or lambda")
                }

                return RuntimeValue.ListValue(result)
            }
        }))

        env.define("REDUCE", RuntimeValue.Function(object : Callable {
            override fun arity() = -1

            override fun call(ctx: Any?, arguments: List<Any?>): Any? {
                if (arguments.size < 2) throw RuntimeException("REDUCE() requires at least 2 arguments: list and function")

                val listArg = arguments[0]
                val fnArg = arguments[1]

                val list: List<*> = when (listArg) {
                    is RuntimeValue.ListValue -> listArg.elements
                    is List<*> -> listArg
                    else -> throw RuntimeException("REDUCE() first argument must be a list")
                }

                if (list.isEmpty()) {
                    if (arguments.size > 2) return arguments[2]
                    throw RuntimeException("REDUCE() on empty list requires an initial value")
                }

                // Helper to coerce raw elements to RuntimeValue
                fun toRuntimeValue(elem: Any?): RuntimeValue? {
                    return when (elem) {
                        is RuntimeValue -> elem
                        is Double -> RuntimeValue.Number(elem)
                        is Number -> RuntimeValue.Number(elem.toDouble())
                        is String -> RuntimeValue.String(elem)
                        is Boolean -> RuntimeValue.Bool(elem)
                        null -> null
                        else -> throw RuntimeException("Unsupported element type in REDUCE list: ${'$'}{elem::class.simpleName}")
                    }
                }

                // Determine initial accumulator (coerced)
                var accRaw: Any? = if (arguments.size > 2) {
                    arguments[2]
                } else {
                    // use first element as initial accumulator
                    list[0]
                }
                var acc: Any? = accRaw

                val startIndex = if (arguments.size > 2) 0 else 1

                when (fnArg) {
                    is RuntimeValue.Lambda -> {
                        val interp = ctx as? Interpreter
                            ?: throw RuntimeException("REDUCE with lambda requires interpreter context")
                        for (i in startIndex until list.size) {
                            val element = list[i]
                            val accArg = toRuntimeValue(acc)
                            val elemArg = toRuntimeValue(element)
                            acc = interp.callLambda(fnArg, listOf(accArg, elemArg))
                        }
                    }
                    is RuntimeValue.Function -> {
                        for (i in startIndex until list.size) {
                            val element = list[i]
                            val accArg = toRuntimeValue(acc)
                            val elemArg = toRuntimeValue(element)
                            acc = fnArg.callable.call(ctx, listOf(accArg, elemArg))
                        }
                    }
                    is Callable -> {
                        for (i in startIndex until list.size) {
                            val element = list[i]
                            val accArg = toRuntimeValue(acc)
                            val elemArg = toRuntimeValue(element)
                            acc = fnArg.call(ctx, listOf(accArg, elemArg))
                        }
                    }
                    else -> throw RuntimeException("REDUCE() second argument must be a function or lambda")
                }

                return acc
            }
        }))


        env.define("AGGREGATE", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(ctx: Any?, arguments: List<Any?>): Any? {
                if (arguments.size < 2) throw RuntimeException("AGGREGATE() requires 2 arguments")
                
                val list = convertToDoubleList(arguments[0])
                val operationArg = arguments[1]
                val operation = when (operationArg) {
                    is RuntimeValue.String -> operationArg.value.uppercase()
                    else -> operationArg?.toString()?.uppercase() ?: throw RuntimeException("AGGREGATE() second argument must be a string")
                }
                return when (operation) {
                    "SUM" -> list.sum()
                    "AVG" -> list.average()
                    "MIN" -> list.minOrNull() ?: throw RuntimeException("Cannot find MIN of empty list")
                    "MAX" -> list.maxOrNull() ?: throw RuntimeException("Cannot find MAX of empty list")
                    else -> throw RuntimeException("Unknown AGGREGATE operation: $operation")
                }
            }
        }))

        env.define("LOG", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(ctx: Any?, arguments: List<Any?>): Any? {
                println("[LOG] " + arguments.joinToString(" "))
                return null
            }
        }))
    }

    // -------------------------
    // HELPER FUNCTIONS
    // -------------------------
    
    private fun convertToDoubleList(value: Any?): List<Double> {
        // Handle RuntimeValue.ListValue
        val list: List<*> = when (value) {
            is RuntimeValue.ListValue -> value.elements
            is List<*> -> value
            else -> throw IllegalArgumentException("Expected list, got ${value?.javaClass?.simpleName}")
        }
        
        return list.map { element ->
            when (element) {
                is RuntimeValue.Number -> element.value
                is Double -> element
                is Number -> element.toDouble()
                else -> element?.toString()?.toDoubleOrNull() 
                    ?: throw IllegalArgumentException("Cannot convert to double: $element")
            }
        }
    }
    
    private fun toDouble(value: Any?): Double {
        return when (value) {
            is RuntimeValue.Number -> value.value
            is Double -> value
            is Number -> value.toDouble()
            else -> value?.toString()?.toDoubleOrNull() 
                ?: throw IllegalArgumentException("Cannot convert to double: $value")
        }
    }
    
    private fun isTruthy(value: Any?): Boolean {
        return when (value) {
            null -> false
            is RuntimeValue.Bool -> value.value
            is Boolean -> value
            is RuntimeValue.Number -> value.value != 0.0
            is Number -> value.toDouble() != 0.0
            is RuntimeValue.String -> value.value.isNotEmpty()
            is String -> value.isNotEmpty()
            is RuntimeValue.ListValue -> value.elements.isNotEmpty()
            is List<*> -> value.isNotEmpty()
            else -> true
        }
    }
}