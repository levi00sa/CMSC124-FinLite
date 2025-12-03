package finlite
import finlite.Callable

interface Callable {
    fun arity(): Int
    fun call(interpreter: Interpreter, arguments: List<Any?>): Any?
}

fun builtin(fn: (List<Any?>) -> Any?): Callable {
    return object : Callable {
        override fun arity(): Int = -1
        override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
            return fn(arguments)
        }
        override fun toString(): String = "<builtin>"
    }
}