fun main() {                                                                              // REPL: lets the user interactively type code & see the tokens generated
    println("Type in your prompt below and see the tokens generated.")    

    while (true) {
        print("> ")                                                                            // as indicator to insert prompt after this symbol
        val line = readLine() ?: break 
        if(line.trim().isEmpty()) continue
                                                                                              // to read input (exit if null / Ctrl+D)
        val scanner = Scanner(line)                                                           // to create scanner for that line
        val tokens = scanner.scanTokens()                                                     // to scan tokens

        val parser = Parser(tokens)                                                           // to create parser for those tokens
        val statements = parser.parse()                                                       // to parse tokens into statements (AST)

        if(statements.isNotEmpty()) {                                                         // if parsing was successful
            for (stmt in statements) {
                println(AstPrinter.print(stmt))                                              // print the AST in parenthesized format
            }
        } else {
            println("Parsing error occurred.")                                                // if there was a parsing error
        }
    }
}

/*
 This file serves as the main entry point of the program. It integrates the
 Scanner and Parser modules to process a source code input, convert it into a stream of tokens, and then parse these tokens into an Abstract Syntax Tree (AST).
 The resulting AST is printed using the AstPrinter for verification.
 
 Purpose:
 -to connect all major components (Scanner, Parser, AST Printer)
 -to execute the full lexical + syntactic analysis flow
 -to simulate a REPL-like environment for user testing and debugging
 
  Key components:
  -main(): initializes the scanner and parser, handles user input, and prints results.
 
Ex:
 >kotlin MainKt
 Input:dehins omsim
 Output:(! true)
 */
