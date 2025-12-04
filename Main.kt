package finlite
import finlite.Callable.*
import java.lang.RuntimeException
import java.io.BufferedReader
import java.io.InputStreamReader

fun main() {
    val globalEnvironment = Environment()
    val coreEvaluator = Evaluator(globalEnvironment)
      val interpreter = Interpreter(globalEnvironment, financeInterpreter)
    
    // Load standard library with interpreter
    FinLiteStandardLib.loadInto(globalEnvironment, interpreter)
    
    // Define your valuation model
    globalEnvironment.define("valuation_model") { env: Environment ->
        val rate = env.get("rate") as Double
        val growth = env.get("growth") as Double
        println("Model: rate=$rate, growth=$growth")
        rate * growth
    }
    
    // Check if input is piped or interactive
    val isInteractive = System.console() != null
    
    if (isInteractive) {
        // Interactive REPL mode
        println("=== FinLite REPL ===")
        println("Type your code and press Enter twice to execute")
        println("Type 'exit' or 'quit' to leave")
        println()
        
        while (true) {
            val input = readMultilineInput() ?: break
            if (input.trim().isEmpty()) continue
            if (input.trim().lowercase() in listOf("exit", "quit")) break

            executeCode(input, interpreter)
            println()
        }
    } else {
        // Piped input mode - read all at once
        val reader = BufferedReader(InputStreamReader(System.`in`))
        val input = reader.readText()
        
        if (input.trim().isNotEmpty()) {
            executeCode(input, interpreter)
        }
    }
}

fun executeCode(input: String, interpreter: Interpreter) {
    val scanner = Scanner(input)
    scanner.scanTokens()
    val tokens = scanner.tokens

    try {
        val parser = Parser(tokens)
        val statements = parser.parse()

        for (statement in statements) {
            interpreter.execute(statement)
        }
    } catch (e: RuntimeError) {
        reportRuntimeError(e)
    } catch (e: RuntimeException) {
        System.err.println("Error: ${e.message}")
    } catch (e: Exception) {
        System.err.println("Error: ${e.message}")
    }
}

fun reportRuntimeError(error: RuntimeError) {
    System.err.println("[line ${error.token.line}] Runtime error: ${error.message}")
}

fun readMultilineInput(): String? {
    print("> ")
    val lines = mutableListOf<String>()
    
    while (true) {
        val line = readLine() ?: return null
        
        // Check for exit commands
        if (line.trim().lowercase() in listOf("exit", "quit")) {
            return line
        }
        
        // Empty line signals end of input
        if (line.trim().isEmpty()) {
            if (lines.isEmpty()) {
                // First line is empty, prompt again
                print("> ")
                continue
            } else {
                // End of multiline input
                break
            }
        }
        
        lines.add(line)
        
        // Show continuation prompt
        print("  ")
    }
    
    return lines.joinToString("\n") + "\n"
}

fun isInputComplete(input: String): Boolean {
    var parenCount = 0
    var bracketCount = 0
    var braceCount = 0
    var blockDepth = 0
    var inString = false
    var inMultilineString = false
    var i = 0

    fun isIdentifierChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_'

    while (i < input.length) {
        val c = input[i]

        if (i + 2 < input.length && input.substring(i, i + 3) == "\"\"\"") {
            inMultilineString = !inMultilineString
            i += 3
            continue
        }
        if (inMultilineString) {
            i++
            continue
        }

        if (c == '"') {
            var escaped = false
            var j = i - 1
            while (j >= 0 && input[j] == '\\') {
                escaped = !escaped
                j--
            }
            if (!escaped) inString = !inString
            i++
            continue
        }
        if (inString) {
            i++
            continue
        }

        when (c) {
            '(' -> parenCount++
            ')' -> parenCount--
            '[' -> bracketCount++
            ']' -> bracketCount--
            '{' -> braceCount++
            '}' -> braceCount--
        }

        if (c.isLetter()) {
            val start = i
            var end = i + 1

            while (end < input.length && isIdentifierChar(input[end])) {
                end++
            }

            val keyword = input.substring(start, end)

            when (keyword.lowercase()) {
                "if", "while", "for", "scenario", "portfolio", "block" ->
                    blockDepth++

                "end" ->
                    blockDepth--
            }

            i = end
            continue
        }

        i++
    }

    return parenCount == 0 &&
            bracketCount == 0 &&
            braceCount == 0 &&
            blockDepth == 0 &&
            !inString &&
            !inMultilineString
}