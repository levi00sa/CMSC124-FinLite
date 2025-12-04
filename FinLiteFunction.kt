package finlite
import finlite.Stmt.*

class FinLiteFunction(
val declaration: Function<Unit>?,
    val arity: Int,
    private val body: (List<Any?>) -> Any?
) {
    fun call(args: List<Any?>): Any? {
        require(args.size == arity) { "Expected $arity arguments but got ${args.size}." }

        return try {
            body(args)
        } catch (r: ReturnValue) {
            r.value
        }
    }

}

