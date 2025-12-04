package finlite

object FinLiteStandardLib {

    fun loadInto(env: Environment, interpreter: Interpreter) {
        // -------------------------
        // BASIC FUNCTIONS
        // -------------------------
        env.define("print", interpreter.builtin { args ->
            println(args.joinToString(" "))
            null
        })

        env.define("len", interpreter.builtin { args ->
            (args[0] as List<*>).size
        })

        env.define("sum", interpreter.builtin { args ->
            convertToDoubleList(args[0]).sum()
        })
 
        env.define("avg", interpreter.builtin { args ->
            convertToDoubleList(args[0]).average()
        })

        // -------------------------
        // TIME SERIES HELPERS
        // -------------------------
        env.define("returns", interpreter.builtin { args ->
            val doubles = convertToDoubleList(args[0])
            doubles.zipWithNext { a, b -> (b - a) / a }
        })

        env.define("volatility", interpreter.builtin { args ->
            val r = convertToDoubleList(args[0])
            Math.sqrt(r.map { it * it }.average())
        })

        // -------------------------
        // FN(...) SYSTEM
        // -------------------------
        env.define("FN", interpreter.builtin { args ->
            if (args.isEmpty())
                throw RuntimeException("FN expects at least one argument.")

            val name = args[0].toString()

            return@builtin when (name) {

                // ---------------- PV ----------------
                "PV" -> {
                    val rate = args[1].toString().toDouble()
                    val nper = args[2].toString().toDouble()
                    val pmt  = args[3].toString().toDouble()
                    val fv   = args.getOrNull(4)?.toString()?.toDouble() ?: 0.0

                    -(pmt * (1 - Math.pow(1 + rate, -nper)) / rate +
                            fv * Math.pow(1 + rate, -nper))
                }

                // ---------------- NPV ----------------
                "NPV" -> {
                    val rate = args[1].toString().toDouble()
                    val flows = convertToDoubleList(args[2])
                    flows.mapIndexed { i, v ->
                        v / Math.pow(1 + rate, (i + 1).toDouble())
                    }.sum()
                }

                // ---------------- IRR ----------------
                "IRR" -> {
                    val flows = convertToDoubleList(args[1])
                    var guess = 0.10

                    repeat(50) {
                        val npv = flows.mapIndexed { i, v ->
                            v / Math.pow(1 + guess, i.toDouble())
                        }.sum()

                        val deriv = flows.mapIndexed { i, v ->
                            -i * v / Math.pow(1 + guess, i + 1.0)
                        }.sum()

                        guess -= npv / deriv
                    }
                    guess
                }

                else -> throw RuntimeException("Unknown FN: '$name'")
            }
        })

        // -------------------------
        // FINANCE SHORTCUTS
        // -------------------------
        env.define("CAPM", interpreter.builtin { args ->
            val rf = args[0].toString().toDouble()
            val beta = args[1].toString().toDouble()
            val rm = args[2].toString().toDouble()
            rf + beta * (rm - rf)
        })

        env.define("WACC", interpreter.builtin { args ->
            val ew = args[0].toString().toDouble()
            val dw = args[1].toString().toDouble()
            val ce = args[2].toString().toDouble()
            val cd = args[3].toString().toDouble()
            val tax = args[4].toString().toDouble()
            ew * ce + dw * cd * (1 - tax)
        })

        env.define("FV", interpreter.builtin { args ->
            val rate = args[0].toString().toDouble()
            val nper = args[1].toString().toInt()
            val pmt = args.getOrNull(2)?.toString()?.toDouble() ?: 0.0
            val pv  = args.getOrNull(3)?.toString()?.toDouble() ?: 0.0

            pv * Math.pow(1 + rate, nper.toDouble()) +
                    pmt * ((Math.pow(1 + rate, nper.toDouble()) - 1) / rate)
        })

        env.define("ASSERT", interpreter.builtin { args ->
        val cond = args[0] as? Boolean ?: false
        val msg = args.getOrNull(1)?.toString() ?: "Assertion failed."
        if (!cond) throw RuntimeException(msg)
        null
    })

    env.define("ERROR", interpreter.builtin { args ->
        throw RuntimeException(args.joinToString(" "))
    })

    env.define("MAP", interpreter.builtin { args ->
        val list = args[0] as List<*>
        val fn = args[1] as Callable
        list.map { fn.call(interpreter, listOf(it)) }
    })

    env.define("AGGREGATE", interpreter.builtin { args ->
        val list = convertToDoubleList(args[0])
        when (args[1].toString().uppercase()) {
            "SUM" -> list.sum()
            "AVG" -> list.average()
            "MIN" -> list.minOrNull()
            "MAX" -> list.maxOrNull()
            else -> throw RuntimeException("Unknown AGGREGATE type.")
        }
    })

    env.define("LOG", interpreter.builtin { args ->
        println("[LOG] " + args.joinToString(" "))
        null
    })



    }

    // ------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------

    private fun convertToDoubleList(value: Any?): List<Double> {
        val list = value as? List<*> ?: throw IllegalArgumentException("Expected list")
        return list.map {
            when (it) {
                is Double -> it
                is Number -> it.toDouble()
                else -> it.toString().toDouble()
            }
        }
    }
}