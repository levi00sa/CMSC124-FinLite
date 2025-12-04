package finlite

sealed class Expr {
    data class Literal(val value: Any?) : Expr()
    data class Variable(val name: Token) : Expr()
    data class Unary(val operator: Token, val right: Expr) : Expr()
    data class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr()
    data class Grouping(val expression: Expr) : Expr()
    data class Call(val callee: Expr, val paren: Token, val arguments: List<Expr>) : Expr()
    data class ListLiteral(val elements: List<Expr>) : Expr()
    data class Assign(val name: Token, val value: Expr) : Expr()
    data class Subscript(val container: Expr, val index: Expr, val end: Expr?, val bracket: Token) : Expr()
    data class Get(val obj: Expr, val name: Token) : Expr()
}

sealed class Stmt {
    data class Let(val name: Token, val initializer: Expr) : Stmt()
    data class SetStmt(val name: Token, val value: Expr) : Stmt()
    data class ExpressionStmt(val expression: Expr) : Stmt()
    data class Print(val value: Expr) : Stmt()

    data class Block(val statements: List<Stmt>) : Stmt()

    data class IfStmt(
        val condition: Expr,
        val thenBranch: Stmt,
        val elseBranch: Stmt?
    ) : Stmt()

    data class WhileStmt(
        val condition: Expr,
        val body: Stmt
    ) : Stmt()

    data class Scenario(val name: Token, val statements: List<Stmt>) : Stmt()
}

/*
 this file defines all Abstract Syntax Tree node classes used by the parser.
 it combines both Expr, Stmt hierarchies in one file for easier organization during early dev
 
 purpose:
  -to represent the structure of parsed source code in tree form
  -to model the hierarchical structure of source code for further processing (printing, evaluation, or interpretation)
  -to provide a unified representation of both expressions and statements
  -to support different kinds of nodes such as:
            *Expr.Binary
            *Expr.Unary
            *Expr.Grouping
            *Expr.Literal
            *Stmt.Expression
            *Stmt.Print (if implemented)
 *importance:
 *the AST is the core structure passed from the Parser to future processing
 *stages such as interpretation or bytecode generation.
  Keeping both Expr and Stmt in a single file simplifies maintenance for smaller projects or labs.
 */
