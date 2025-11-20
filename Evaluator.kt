class Evaluator {

    fun evaluate(expr: Expr): Any? {
        return when (expr) {
            is Expr.Literal -> expr.value

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

                    TokenType.BANG, TokenType.NOT, TokenType.DEHINS -> !isTruthy(right)

                    else -> null
                }
            }

            is Expr.Binary -> {
                val left = evaluate(expr.left)
                val right = evaluate(expr.right)

                when (expr.operator.type) {

                    // Arithmetic
                    TokenType.PLUS -> {
                        // number + number
                        if (left is Double && right is Double) {
                            return left + right
                        }
                        // string + string
                        if (left is String && right is String) {
                            return left + right
                        }
                        throw RuntimeError(expr.operator,
                            "Operands must be two numbers or two strings.")
                    }

                    TokenType.MINUS -> {
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

                    else -> null
                }
            }

            // IGNORE all advanced nodes → Lab 3 does not evaluate them
            is Expr.Variable,
            is Expr.Assign,
            is Expr.Call,
            is Expr.Get,
            is Expr.This -> {
                throw RuntimeError(
                    Token(TokenType.ERROR, "", null, 0),
                    "Feature not supported in Lab 3 evaluator."
                )
            }
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
}

    fun formatNumber(x: Double): String {
        return if (x % 1.0 == 0.0) {
            x.toInt().toString()       // remove .0
        } else {
            x.toString()               // keep decimals
        }
    }


//LAB 3 files (evaluator.kt, RuntimeError.kt, Main.kt) 
//This is the core evaluator that computes values from expressions.
//Works with: Literal,Grouping,Unary,Binary, 
//And implements: truthiness rules, numeric checks, operator overloading for + (string concatenation), division by zero errors//
