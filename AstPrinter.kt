package finlite
import finlite.TokenType.*
import finlite.Stmt.*
import finlite.Expr.*


object AstPrinter {
    fun print(stmt: Stmt): String =
        when (stmt) {
            is Stmt.Let -> "(let ${stmt.name.lexeme} ${printExpr(stmt.initializer)})"
            is Stmt.SetStmt -> "(set ${stmt.name.lexeme} ${printExpr(stmt.value)})"
            is Stmt.ExpressionStmt -> printExpr(stmt.expression)
            is Stmt.Print -> "(print ${printExpr(stmt.value)})"
            is Stmt.Block -> {
                val body = stmt.statements.joinToString(" ") { print(it) }
                "(block $body)"
            }
            is Stmt.IfStmt -> {
                val elsePart = stmt.elseBranch?.let { " ${print(it)}" } ?: ""
                "(if ${printExpr(stmt.condition)} ${print(stmt.thenBranch)}$elsePart)"
            }
            is Stmt.Scenario -> {
                val body = stmt.statements.joinToString(" ") { print(it) }
                "(scenario ${stmt.name.lexeme} $body)"
            }
            is FinanceStmt.LedgerEntryStmt -> printLedgerEntry(stmt)
            is FinanceStmt.PortfolioStmt  -> printPortfolio(stmt)
            is FinanceStmt.ScenarioStmt   -> printScenario(stmt)
            is FinanceStmt.SimulateStmt   -> printSimulate(stmt)
            is FinanceStmt.RunStmt -> printRun(stmt)
        }
    fun printExpr(expr: Expr): String =
        when (expr) {
            is Expr.Literal -> literalToString(expr.value)
            is Expr.Variable -> expr.name.lexeme
            is Expr.Grouping -> "(group ${printExpr(expr.expression)})"
            is Expr.Unary -> {
                val symbol = when (expr.operator.type) {
                    TokenType.BANG -> "!"
                    TokenType.MINUS -> "-"
                    else -> expr.operator.lexeme
                }
                "($symbol ${printExpr(expr.right)})"
            }
            is Expr.Binary -> "(${expr.operator.lexeme} ${printExpr(expr.left)} ${printExpr(expr.right)})"
            is Expr.Call -> {
                val args = expr.arguments.joinToString(", ") { printExpr(it) }
                "(call ${printExpr(expr.callee)} ($args))"
            }
            is Expr.ListLiteral -> {
                val elems = expr.elements.joinToString(", ") { printExpr(it) }
                "[ $elems ]"
            }
            else -> expr.toString()
        }

    private fun literalToString(value: Any?): String =
        when (value) {
            null -> "nil"
            is Double -> value.toString()
            is Boolean -> if (value) "true" else "false"
            is String -> "\"$value\""
            else -> value.toString()
        }
    // ============
    // HELPERS
    // ============
    private fun printLedgerEntry(entry: FinanceStmt.LedgerEntryStmt): String =
        "(ledger ${entry.type.name.lowercase()} ${entry.account.lexeme} ${printExpr(entry.amount)})"

    private fun printPortfolio(stmt: FinanceStmt.PortfolioStmt): String {
        val entries = stmt.entries.joinToString(" ") { printLedgerEntry(it) }
        return "(portfolio ${stmt.name.lexeme} $entries)"
    }

    private fun printScenario(stmt: FinanceStmt.ScenarioStmt): String =
        "(scenario ${stmt.name.lexeme} ${print(stmt.body)})"

    private fun printSimulate(stmt: FinanceStmt.SimulateStmt): String {
        val runs = stmt.runs?.let { " runs=${printExpr(it)}" } ?: ""
        val step = stmt.step?.let { " step=${printExpr(it)}" } ?: ""
        return "(simulate ${stmt.scenarioName.lexeme}$runs$step)"
    }
    private fun printRun(stmt: FinanceStmt.RunStmt): String {
        return "(run ${stmt.scenarioName.lexeme} on ${stmt.modelName.lexeme})"
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
