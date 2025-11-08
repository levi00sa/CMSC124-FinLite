object AstPrinter {
    fun print(stmt: Stmt): String {
        return when (stmt) {
            is Stmt.Expression -> printExpr(stmt.expression)
            is Stmt.Function -> {
                val params = stmt.params.joinToString(", ") { it.lexeme }
                val body = stmt.body.joinToString(" ") { print(it) }
                "(function ${stmt.name.lexeme} ($params) { $body })"
            }
            is Stmt.Block -> {
                val statements = stmt.statements.joinToString(" ") { print(it) }
                "(block $statements)"
            }
            is Stmt.Print -> "(print ${printExpr(stmt.expression)})"
            is Stmt.Var -> "(var ${stmt.name.lexeme} ${stmt.initializer?.let { printExpr(it) } ?: ""})"
            is Stmt.If -> "(if ${printExpr(stmt.condition)} ${print(stmt.thenBranch)} ${stmt.elseBranch?.let { print(it) } ?: ""})"
            is Stmt.While -> "(while ${printExpr(stmt.condition)} ${print(stmt.body)})"
            is Stmt.For -> "(for ${stmt.initializer?.let { printExpr(it) } ?: ""}; ${stmt.condition?.let { printExpr(it) } ?: ""}; ${stmt.increment?.let { printExpr(it) } ?: ""} ${print(stmt.body)})"
            is Stmt.ForEach -> "(foreach ${stmt.iterator.lexeme} in ${printExpr(stmt.iterable)} ${print(stmt.body)})"
            is Stmt.Return -> "(return ${stmt.value?.let { printExpr(it) } ?: ""})"
            is Stmt.Class -> "(class ${stmt.name.lexeme})"
        }
    }

    fun printExpr(expr: Expr): String {
        return when (expr) {
            is Expr.Literal -> literalToString(expr.value)
            is Expr.Variable -> expr.name.lexeme
            is Expr.Grouping -> "(group ${print(expr.expression)})"
            is Expr.Unary -> {
                val symbol = when (expr.operator.type) {
                    TokenType.BANG -> "!"
                    TokenType.MINUS -> "-"
                    else -> expr.operator.lexeme
                }
                "($symbol ${print(expr.right)})"
            }
    //        is Expr.Grouping -> "(group ${printExpr(expr.expression)})"
    //        is Expr.Unary -> "(${expr.operator.lexeme} ${printExpr(expr.right)})"
            is Expr.Binary -> "(${expr.operator.lexeme} ${printExpr(expr.left)} ${printExpr(expr.right)})"
            is Expr.Call -> {
                val args = expr.arguments.joinToString(", ") { printExpr(it) }
                "(call ${printExpr(expr.callee)} ($args))"
            }
            is Expr.Assign -> "(${expr.name.lexeme} = ${print(expr.value)})"
            is Expr.Get -> "(get ${print(expr.obj)} ${expr.name.lexeme})"
            is Expr.This -> "this"
        }
    }

    private fun literalToString(value: Any?): String {
        return when (value) {
            null -> "nil"
            is Double -> value.toString()
            is Boolean -> if (value) "true" else "false"
            is String -> "\"$value\""
            else -> value.toString()
        }
    }
}
  

/*
this file implements the AstPrinter class, which traverses the Abst Syntax Tree & converts it into a readable, parenthesized string representation
 
 Purpose:
- to visualize the internal structure of the parsed syntax tree
- to assist with debugging and verifying parser output
 
 Ex:
>1 + 2 * 3
 (+ 1 (* 2 3))

key function:
 -print(expr: Expr): Returns a string representation of the expression tree
 
 importance:
  AstPrinter provides valuable insight into how the parser understands the input,
 ensuring correctness and aiding in debugging or later interpretation stages
 */
