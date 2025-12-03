package finlite
import finlite.Callable

class Environment(
    private val enclosing: Environment? = null
) {
    private val values = mutableMapOf<String, Any?>()
    private val functions = mutableMapOf<String, FinLiteFunction>()

    fun define(name: String, value: Any?) { 
        values[name] = value 
    }

    fun defineFunc(name: String, fn: FinLiteFunction) { functions[name] = fn }

    fun assign(name: String, value: Any?) {
        if (values.containsKey(name)) {
            values[name] = value
            return
        }
        if (enclosing != null) {
            enclosing.assign(name, value)
            return
        }
        // Create a dummy token for error reporting
        throw RuntimeError(
            Token(TokenType.IDENTIFIER, name, null, 0),
            "Undefined variable '$name'"
        )
    }
    
    fun get(name: String): Any? {
        // Check current scope first
        if (values.containsKey(name)) {
            return values[name]
        }
        // Check enclosing scope
        if (enclosing != null) {
            return enclosing.get(name)
        }
        // Create a dummy token for error reporting
        throw RuntimeError(
            Token(TokenType.IDENTIFIER, name, null, 0),
            "Undefined variable '$name'"
        )
    }
    fun createChild(): Environment{
        return Environment(this)
    }

    fun getOrNull(name: String): Any? = 
        if (values.containsKey(name)) values[name] else null

}

