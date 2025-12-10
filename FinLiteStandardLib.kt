package finlite

object FinLiteStandardLib {

    fun loadInto(env: Environment, interpreter: Interpreter) {
        // -------------------------
        // VALUATION MODEL
        // -------------------------
        env.define("valuation_model", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                val lookupEnv = interpreter?.getCurrentEnvironment() ?: env

                val rate = when (val v = lookupEnv.get("rate")) {
                    is RuntimeValue.Number -> v.value
                    else -> 0.0
                }
                val growth = when (val v = lookupEnv.get("growth")) {
                    is RuntimeValue.Number -> v.value
                    else -> 0.0
                }
                println("Model: rate=$rate, growth=$growth")
                return RuntimeValue.Number(rate * growth)
            }
        }))

        // -------------------------
        // BASIC FUNCTIONS
        // -------------------------
        env.define("show", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                println(arguments.joinToString(" ") { it?.toString() ?: "null" })
                return arguments.firstOrNull()  // Return the first argument after printing
            }
        }))

        env.define("len", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                if (arguments.isEmpty()) throw RuntimeException("len() requires 1 argument")
                return RuntimeValue.Number(convertToDoubleList(arguments[0]).size.toDouble())
            }
        }))

        env.define("sum", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                if (arguments.isEmpty()) throw RuntimeException("sum() requires 1 argument")
                return RuntimeValue.Number(convertToDoubleList(arguments[0]).sum())
            }
        }))
 
        env.define("avg", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                if (arguments.isEmpty()) throw RuntimeException("avg() requires 1 argument")
                return RuntimeValue.Number(convertToDoubleList(arguments[0]).average())
            }
        }))

        // -------------------------
        // FUNC: define named function at runtime
        // Usage: FUNC(nameString, fn)
        // where fn is a Lambda or a Function/Callable
        // Defines the named function in the current environment and returns it
        env.define("FUNC", RuntimeValue.Function(object : Callable {
            override fun arity() = 2
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                if (arguments.size < 2) throw RuntimeException("FUNC() requires 2 arguments: name and function")

                val nameArg = arguments[0]
                val fnArg = arguments[1]

                val name = when (nameArg) {
                    is RuntimeValue.String -> nameArg.value
                    else -> nameArg?.toString() ?: throw RuntimeException("FUNC() first argument must be a string name")
                }

                // Build a callable to register
                val callable: Callable = when (fnArg) {
                    is RuntimeValue.Function -> fnArg.callable
                    is RuntimeValue.Lambda -> object : Callable {
                        override fun arity() = -1
                        override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                            val interp = interpreter
                                ?: throw RuntimeException("FUNC created function requires interpreter context to call lambda")
                            return interp.callLambda(fnArg, arguments)
                        }
                    }
                    is Callable -> fnArg
                    else -> throw RuntimeException("FUNC() second argument must be a function or lambda")
                }

                // Define in the current interpreter environment if available, otherwise in the env passed to loadInto
                val targetEnv = interpreter?.getCurrentEnvironment() ?: env
                targetEnv.define(name, RuntimeValue.Function(callable))

                return RuntimeValue.Function(callable)
            }
        }))

        // -------------------------
        // TIME SERIES HELPERS
        // -------------------------
        env.define("returns", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                if (arguments.isEmpty()) throw RuntimeException("returns() requires 1 argument")
                val doubles = convertToDoubleList(arguments[0])
                // Return as List<RuntimeValue.Number> for compatibility
                return RuntimeValue.ListValue(doubles.zipWithNext { a, b -> RuntimeValue.Number((b - a) / a) })
            }
        }))

        env.define("volatility", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                if (arguments.isEmpty()) throw RuntimeException("volatility() requires 1 argument")
                val r = convertToDoubleList(arguments[0])
                return RuntimeValue.Number(Math.sqrt(r.map { it * it }.average()))
            }
        }))

        // -------------------------
        // FINANCE SHORTCUTS
        // -------------------------
        env.define("CAPM", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                if (arguments.size < 3) throw RuntimeException("CAPM() requires 3 arguments")
                val rf = toDouble(arguments[0])
                val beta = toDouble(arguments[1])
                val rm = toDouble(arguments[2])
                return RuntimeValue.Number(rf + beta * (rm - rf))
            }
        }))

        env.define("WACC", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                if (arguments.size < 5) throw RuntimeException("WACC() requires 5 arguments")
                val ew = toDouble(arguments[0])
                val dw = toDouble(arguments[1])
                val ce = toDouble(arguments[2])
                val cd = toDouble(arguments[3])
                val tax = toDouble(arguments[4])
                return RuntimeValue.Number(ew * ce + dw * cd * (1 - tax))
            }
        }))

        env.define("FV", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                if (arguments.size < 2) throw RuntimeException("FV() requires at least 2 arguments")
                val rate = toDouble(arguments[0])
                val nper = toDouble(arguments[1]).toInt()
                val pmt = if (arguments.size > 2) toDouble(arguments[2]) else 0.0
                val pv  = if (arguments.size > 3) toDouble(arguments[3]) else 0.0
                return RuntimeValue.Number(pv * Math.pow(1 + rate, nper.toDouble()) +
                        pmt * ((Math.pow(1 + rate, nper.toDouble()) - 1) / rate))
            }
        }))

        // -------------------------
        // UTILITY FUNCTIONS
        // -------------------------
        env.define("ASSERT", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                if (arguments.isEmpty()) throw RuntimeException("ASSERT() requires at least 1 argument")
                val cond = isTruthy(arguments[0])
                val msg = if (arguments.size > 1) arguments[1]?.toString() else "Assertion failed."
                if (!cond) throw RuntimeException(msg ?: "Assertion failed.")
                return null
            }
        }))

        env.define("ERROR", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                throw RuntimeException(arguments.joinToString(" "))
            }
        }))

        env.define("MAP", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
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
                        val interp = interpreter
                            ?: throw RuntimeException("MAP with lambda requires interpreter context")
                        RuntimeValue.ListValue(list.map { element ->
                            interp.callLambda(fnArg, listOf(element as? RuntimeValue))
                        })
                    }
                    is RuntimeValue.Function -> {
                        RuntimeValue.ListValue(list.map { element ->
                            fnArg.callable.call(interpreter, listOf(element as? RuntimeValue))
                        })
                    }
                    is Callable -> {
                        RuntimeValue.ListValue(list.map { element ->
                            fnArg.call(interpreter, listOf(element as? RuntimeValue))
                        })
                    }
                    else -> throw RuntimeException("MAP() second argument must be a function or lambda")
                }
            }
        }))

        env.define("FILTER", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
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
                        val interp = interpreter
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
                            val predicateResult = fnArg.callable.call(interpreter, listOf(element as? RuntimeValue))
                            if (isTruthy(predicateResult)) {
                                result.add(element as? RuntimeValue)
                            }
                        }
                    }
                    is Callable -> {
                        for (element in list) {
                            val predicateResult = fnArg.call(interpreter, listOf(element as? RuntimeValue))
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

            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                if (arguments.size < 2) throw RuntimeException("REDUCE() requires at least 2 arguments: list and function")

                // Helper to coerce raw elements to RuntimeValue (defined early for use below)
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

                // Determine initial accumulator (coerced)
                var acc: RuntimeValue? = if (arguments.size > 2) {
                    arguments[2] as? RuntimeValue
                } else {
                    // use first element as initial accumulator
                    toRuntimeValue(list[0])
                }

                val startIndex = if (arguments.size > 2) 0 else 1

                when (fnArg) {
                    is RuntimeValue.Lambda -> {
                        val interp = interpreter
                            ?: throw RuntimeException("REDUCE with lambda requires interpreter context")
                        for (i in startIndex until list.size) {
                            val element = list[i]
                            val accArg = acc
                            val elemArg = toRuntimeValue(element)
                            acc = interp.callLambda(fnArg, listOf(accArg, elemArg))
                        }
                    }
                    is RuntimeValue.Function -> {
                        for (i in startIndex until list.size) {
                            val element = list[i]
                            val accArg = acc
                            val elemArg = toRuntimeValue(element)
                            acc = fnArg.callable.call(interpreter, listOf(accArg, elemArg))
                        }
                    }
                    is Callable -> {
                        for (i in startIndex until list.size) {
                            val element = list[i]
                            val accArg = acc
                            val elemArg = toRuntimeValue(element)
                            acc = fnArg.call(interpreter, listOf(accArg, elemArg))
                        }
                    }
                    else -> throw RuntimeException("REDUCE() second argument must be a function or lambda")
                }

                return acc
            }
        }))


        env.define("AGGREGATE", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                if (arguments.size < 2) throw RuntimeException("AGGREGATE() requires 2 arguments")
                
                val list = convertToDoubleList(arguments[0])
                val operationArg = arguments[1]
                val operation = when (operationArg) {
                    is RuntimeValue.String -> operationArg.value.uppercase()
                    else -> operationArg?.toString()?.uppercase() ?: throw RuntimeException("AGGREGATE() second argument must be a string")
                }
                return RuntimeValue.Number(when (operation) {
                    "SUM" -> list.sum()
                    "AVG" -> list.average()
                    "MIN" -> list.minOrNull() ?: throw RuntimeException("Cannot find MIN of empty list")
                    "MAX" -> list.maxOrNull() ?: throw RuntimeException("Cannot find MAX of empty list")
                    else -> throw RuntimeException("Unknown AGGREGATE operation: $operation")
                })
            }
        }))

        env.define("LOG", RuntimeValue.Function(object : Callable {
            override fun arity() = -1
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                println("[LOG] " + arguments.joinToString(" "))
                return null
            }
        }))

        // -------------------------
        // FILE I/O FUNCTIONS
        // -------------------------
        env.define("LOAD_CSV", RuntimeValue.Function(object : Callable {
            override fun arity() = 1
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                val filepath = (arguments[0] as? RuntimeValue.String)?.value
                    ?: throw RuntimeException("LOAD_CSV() requires a string filepath")
                
                val lines = try {
                    java.io.File(filepath).readLines()
                } catch (e: Exception) {
                    throw RuntimeException("LOAD_CSV() failed to read file: ${e.message}")
                }
                
                if (lines.isEmpty()) return RuntimeValue.Table(emptyMap())
                
                val headers = lines[0].split(",").map { it.trim() }
                val data = mutableMapOf<String, MutableList<Double>>()
                for (header in headers) data[header] = mutableListOf()
                
                for (i in 1 until lines.size) {
                    val values = lines[i].split(",").map { it.trim().toDoubleOrNull() ?: 0.0 }
                    for (j in 0 until minOf(headers.size, values.size)) {
                        data[headers[j]]?.add(values[j])
                    }
                }
                
                return RuntimeValue.Table(data.mapValues { it.value as kotlin.collections.List<Double> })
            }
        }))

        env.define("SAVE_CSV", RuntimeValue.Function(object : Callable {
            override fun arity() = 2
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                val table = arguments[0] as? RuntimeValue.Table
                    ?: throw RuntimeException("SAVE_CSV() first argument must be a TABLE")
                val filepath = (arguments[1] as? RuntimeValue.String)?.value
                    ?: throw RuntimeException("SAVE_CSV() second argument must be a string filepath")
                
                if (table.columns.isEmpty()) {
                    java.io.File(filepath).writeText("")
                    return null
                }
                
                val headers = table.columns.keys.toList()
                val nRows = table.columns.values.first().size
                val csv = StringBuilder()
                csv.append(headers.joinToString(",")).append("\n")
                
                for (i in 0 until nRows) {
                    val row = headers.map { col -> table.columns[col]!![i].toString() }
                    csv.append(row.joinToString(",")).append("\n")
                }
                
                try {
                    java.io.File(filepath).writeText(csv.toString())
                } catch (e: Exception) {
                    throw RuntimeException("SAVE_CSV() failed to write file: ${e.message}")
                }
                
                return null
            }
        }))

        env.define("LOAD_JSON", RuntimeValue.Function(object : Callable {
            override fun arity() = 1
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                val filepath = (arguments[0] as? RuntimeValue.String)?.value
                    ?: throw RuntimeException("LOAD_JSON() requires a string filepath")
                
                // Simple JSON parsing for basic objects/arrays
                val content = try {
                    java.io.File(filepath).readText()
                } catch (e: Exception) {
                    throw RuntimeException("LOAD_JSON() failed to read file: ${e.message}")
                }
                
                // For now, return a simple message (full JSON parsing would require a library)
                println("[LOAD_JSON] Loaded: $filepath")
                return RuntimeValue.String(content)
            }
        }))

        env.define("SAVE_JSON", RuntimeValue.Function(object : Callable {
            override fun arity() = 2
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                val data = arguments[0]
                val filepath = (arguments[1] as? RuntimeValue.String)?.value
                    ?: throw RuntimeException("SAVE_JSON() second argument must be a string filepath")
                
                val json = when (data) {
                    is RuntimeValue.Object -> {
                        val entries = data.fields.entries.joinToString(", ") { (k, v) ->
                            "\"$k\": ${valueToJson(v)}"
                        }
                        "{ $entries }"
                    }
                    is RuntimeValue.ListValue -> {
                        "[${data.elements.joinToString(", ") { valueToJson(it) }}]"
                    }
                    else -> valueToJson(data)
                }
                
                try {
                    java.io.File(filepath).writeText(json)
                } catch (e: Exception) {
                    throw RuntimeException("SAVE_JSON() failed to write file: ${e.message}")
                }
                
                return null
            }
        }))

        env.define("LOAD_FILE", RuntimeValue.Function(object : Callable {
            override fun arity() = 1
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                val filepath = (arguments[0] as? RuntimeValue.String)?.value
                    ?: throw RuntimeException("LOAD_FILE() requires a string filepath")
                
                val content = try {
                    java.io.File(filepath).readText()
                } catch (e: Exception) {
                    throw RuntimeException("LOAD_FILE() failed to read file: ${e.message}")
                }
                
                return RuntimeValue.String(content)
            }
        }))

        env.define("SAVE_FILE", RuntimeValue.Function(object : Callable {
            override fun arity() = 2
            override fun call(interpreter: Interpreter?, arguments: List<RuntimeValue?>): RuntimeValue? {
                val content = arguments[0]?.toString() ?: ""
                val filepath = (arguments[1] as? RuntimeValue.String)?.value
                    ?: throw RuntimeException("SAVE_FILE() second argument must be a string filepath")
                
                try {
                    java.io.File(filepath).writeText(content)
                } catch (e: Exception) {
                    throw RuntimeException("SAVE_FILE() failed to write file: ${e.message}")
                }
                
                return null
            }
        }))
    }

    // -------------------------
    // HELPER FUNCTIONS
    // -------------------------
    
    private fun convertToDoubleList(value: RuntimeValue?): List<Double> {
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
    
    private fun toDouble(value: RuntimeValue?): Double {
        return when (value) {
            is RuntimeValue.Number -> value.value
            null -> 0.0
            else -> throw IllegalArgumentException("Cannot convert to double: $value")
        }
    }
    
    private fun isTruthy(value: RuntimeValue?): Boolean {
        return when (value) {
            null -> false
            is RuntimeValue.Bool -> value.value
            is RuntimeValue.Number -> value.value != 0.0
            is RuntimeValue.String -> value.value.isNotEmpty()
            is RuntimeValue.ListValue -> value.elements.isNotEmpty()
            else -> true
        }
    }

    private fun valueToJson(value: RuntimeValue?): String {
        return when (value) {
            is RuntimeValue.String -> "\"${value.value.replace("\"", "\\\"")}\""
            is RuntimeValue.Number -> value.value.toString()
            is RuntimeValue.Bool -> value.value.toString()
            is RuntimeValue.ListValue -> "[${value.elements.joinToString(", ") { valueToJson(it) }}]"
            is RuntimeValue.Object -> {
                val entries = value.fields.entries.joinToString(", ") { (k, v) ->
                    "\"$k\": ${valueToJson(v)}"
                }
                "{ $entries }"
            }
            null -> "null"
            else -> "\"${value.toString()}\""
        }
    }
}