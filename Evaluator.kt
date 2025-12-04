package finlite
import finlite.Callable.*

class Evaluator (val environment: Environment){

    fun evaluate(expr: Expr): Any? {
        return when (expr) {
            is Expr.Literal -> when (expr.value) {
                is String -> expr.value
                is Double -> expr.value
                is String -> expr.value
                else -> expr.value
            }

            is Expr.Variable -> environment.get(expr.name.lexeme)

            is Expr.Assign -> {
                val value = evaluate(expr.value)
                environment.assign(expr.name.lexeme, value)
                value
            }

            is Expr.Grouping -> evaluate(expr.expression)

            is Expr.Unary -> {
                val right = evaluate(expr.right)

                when (expr.operator.type) {
                    TokenType.MINUS -> {
                        if (right is Double) {
                            -right
                        } else {
                            throw RuntimeError(expr.operator, "Operand must be a number.")
                        }
                    }

                    TokenType.BANG, TokenType.NOT -> !isTruthy(right)

                    else -> throw RuntimeError(expr.operator, "Unknown unary operator.")
                }
            }

            is Expr.Binary -> {
                val left = evaluate(expr.left)
                val right = evaluate(expr.right)

                when (expr.operator.type) {

                    // Arithmetic
                    TokenType.PLUS -> {
                        if (left is Double && right is Double) {
                            return left + right
                        }
                        if (left is String && right is String) {
                            return left + right
                        }
                        if (left is List<*> && right is List<*>) {
                            return elementWiseNumericOp(expr.operator, left, right) { a, b -> a + b }
                        }
                        throw RuntimeError(
                            expr.operator,
                            "Operands must be two numbers, two strings, or numeric lists."
                        )
                    }

                    TokenType.MINUS -> {
                        if (left is Double && right is Double) {
                            return left - right
                        }
                        if (left is List<*> && right is List<*>) {
                            return elementWiseNumericOp(expr.operator, left, right) { a, b -> a - b }
                        }
                        checkNumberOperands(expr.operator, left, right)
                        return (left as Double) - (right as Double)
                    }

                    TokenType.STAR -> {
                        checkNumberOperands(expr.operator, left, right)
                        return (left as Double) * (right as Double)
                    }

                    TokenType.SLASH -> {
                        checkNumberOperands(expr.operator, left, right)
                        if ((right as Double) == 0.0)
                            throw RuntimeError(expr.operator, "Division by zero.")
                        return (left as Double) / right
                    }

                    // Comparison
                    TokenType.GREATER -> {
                        checkNumberOperands(expr.operator, left, right)
                        return (left as Double) > (right as Double)
                    }
                    TokenType.GREATER_EQUAL -> {
                        checkNumberOperands(expr.operator, left, right)
                        return (left as Double) >= (right as Double)
                    }
                    TokenType.LESS -> {
                        checkNumberOperands(expr.operator, left, right)
                        return (left as Double) < (right as Double)
                    }
                    TokenType.LESS_EQUAL -> {
                        checkNumberOperands(expr.operator, left, right)
                        return (left as Double) <= (right as Double)
                    }

                    // Equality
                    TokenType.EQUAL_EQUAL -> return isEqual(left, right)
                    TokenType.BANG_EQUAL -> return !isEqual(left, right)

                    else -> throw RuntimeError(expr.operator, "Unknown binary operator.")
                }
            }

            is Expr.ListLiteral -> expr.elements.map { evaluate(it) }

            is Expr.Subscript -> {
                val targetVal = evaluate(expr.container)
                val startVal = evaluate(expr.index)
                val endVal = expr.end?.let { evaluate(it) }

                val list = targetVal as? List<*>
                    ?: throw RuntimeError(expr.bracket, 
                    "Subscript target must be a list.")

                val startIndex = (startVal as? Number)?.toInt()
                    ?: throw RuntimeError(expr.bracket, 
                    "Start index must be a number.")

                if (endVal == null) {
                    // Simple index
                    return list.getOrNull(startIndex)
                        ?: throw RuntimeError(expr.bracket, 
                        "Index out of bounds.")
                }

                val endIndex = (endVal as? Number)?.toInt()
                    ?: throw RuntimeError(expr.bracket, 
                    "End index must be a number.")

                if (startIndex < 0 || endIndex < startIndex || startIndex > list.size) {
                    throw RuntimeError(expr.bracket,
                    "Invalid slice range.")
                }
                val to = minOf(endIndex, list.size)
                return list.subList(startIndex, to)
            }

            is Expr.Call -> throw RuntimeError(
                Token(TokenType.ERROR, "", null, 0),
                "Unsupported expression in Evaluator: ${expr::class.simpleName}"
            )

            else -> throw RuntimeError(
                Token(TokenType.ERROR, "", null, 0),
                "Unsupported expression in Evaluator."
            )
        }
    }

    private fun isTruthy(value: Any?): Boolean {
        if (value == null) return false
        if (value is Boolean) return value
        return true
    }

    private fun isEqual(a: Any?, b: Any?): Boolean {
        if (a == null && b == null) return true
        if (a == null) return false
        return a == b
    }

    private fun checkNumberOperands(operator: Token, left: Any?, right: Any?) {
        if (left is Double && right is Double) return
        throw RuntimeError(operator, "Operands must be numbers.")
    }

    private fun elementWiseNumericOp(
        operator: Token,
        left: List<*>,
        right: List<*>,
        op: (Double, Double) -> Double
    ): List<Double> {
        val size = minOf(left.size, right.size)
        if (size == 0) return emptyList()
        val result = ArrayList<Double>(size)
        for (i in 0 until size) {
            val a = (left[i] as? Number)?.toDouble()
                ?: throw RuntimeError(operator, "Operands must be numbers.")
            val b = (right[i] as? Number)?.toDouble()
                ?: throw RuntimeError(operator, "Operands must be numbers.")
            result.add(op(a, b))
        }
        return result
    }
}
