package finlite

class RuntimeError(val token: Token, message: String) : RuntimeException(message)
