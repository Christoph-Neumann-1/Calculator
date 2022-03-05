import kotlin.math.*

class NodeEvaluationException(why: String) : Exception(why)
class ParserException(why: String) : Exception(why)

//TODO handle errors gracefully
//TODO modulo
//TODO: compile expressions for extra efficiency
//TODO: switch to big decimal

/**
 * This class is used to hold the state necessary for evaluation of expressions.
 *
 */
class State(val commandCallbacks: HashMap<String, (String) -> Unit>) : Cloneable {

    public override fun clone() = (super.clone() as State).apply {
        @Suppress("UNCHECKED_CAST")
        this.vars = vars.clone() as HashMap<String, Variable>
        @Suppress("UNCHECKED_CAST")
        this.functions = functions.clone() as HashMap<String, (Array<Double>, State) -> Double>
    }

    @Suppress("UNCHECKED_CAST")
    fun cloneVars() = (super.clone() as State).apply { this.vars = vars.clone() as HashMap<String, Variable> }

    data class Variable(var value: Double, val mutable: Boolean = true)

    private var vars = HashMap<String, Variable>()

    /**
     * Get the value of a variable if it exists
     *
     * @return the value of the variable or null
     */
    fun getVar(name: String): Double? =
        vars[name]?.value

    /**
     * Attempts to modify a variable. A new variable is created if it does not exist
     *
     */
    fun setVar(name: String, value: Double): Double? =
        if (vars[name]?.mutable == false)
            null
        else
            vars.put(name, Variable(value))?.value

    /**
     * This is used for setting variables like x or similar, which are immutable for expressions, but need to change in functions for example
     */
    fun changeConstant(name: String, value: Double) {
        vars[name] = Variable(value, false)
    }

    /**
     * This is a wrapper that asserts that the correct number of arguments was passed
     */

    companion object {
        fun createWrappedFunction(
            func: (Array<Double>, State) -> Double,
            validator: (Int) -> Boolean
        ): (Array<Double>, State) -> Double =
            { args: Array<Double>, s: State ->
                if (validator(args.size)) func(
                    args,
                    s
                ) else throw NodeEvaluationException("Failed to call function. Reason invalid number of arguments")
            }

        fun createWrappedFunction(min: Int, max: Int, func: (Array<Double>, State) -> Double) =
            createWrappedFunction(func) { n -> n >= min && (n <= max || max == -1) }
    }

    var functions = HashMap<String, (Array<Double>, State) -> Double>()

    fun saveResult(r: Double) {
        for (i in 9 downTo 1)
            vars["ans$i"]!!.value = vars["ans${if (i > 1) i - 1 else ""}"]!!.value
        vars["ans"]!!.value = r
    }

    init {
        for (i in 0..9) {
            vars["ans${if (i != 0) i else ""}"] = Variable(0.0, false)
        }
        //TODO: use hashmapOf
        vars["pi"] = Variable(PI, false)
        vars["e"] = Variable(E, false)
        functions["sin"] = createWrappedFunction(1, 1) { args, _ -> sin(args[0]) }
        functions["cos"] = createWrappedFunction(1, 1) { args, _ -> cos(args[0]) }
        functions["asin"] = createWrappedFunction(1, 1) { args, _ -> asin(args[0]) }
        functions["acos"] = createWrappedFunction(1, 1) { args, _ -> acos(args[0]) }
        functions["tan"] = createWrappedFunction(1, 1) { args, _ -> tan(args[0]) }
        functions["atan"] = createWrappedFunction(1, 1) { args, _ -> atan(args[0]) }
        functions["sqrt"] = createWrappedFunction(1, 1) { args, _ -> sqrt(args[0]) }
        functions["root"] = createWrappedFunction(2, 2) { args, _ -> args[0].pow(1.0 / args[1]) }
        functions["abs"] = createWrappedFunction(1, 1) { args, _ -> args[0].absoluteValue }
        functions["min"] = createWrappedFunction(1, -1) { args, _ ->
            var v = args[0]
            for (i in 1 until args.size)
                if (args[i] < v)
                    v = args[i]
            v
        }
        functions["max"] = createWrappedFunction(1, -1) { args, _ ->
            var v = args[0]
            for (i in 1 until args.size)
                if (args[i] > v)
                    v = args[i]
            v
        }
    }
}


/**
 * Any part of an expression is represented as a node. These nodes can then be evaluated
 */
interface Node {
    fun evaluate(state: State): Double
}

class NumberNode(private val value: Double) : Node {
    override fun evaluate(state: State) = value
}

class BinaryOperator(private val child1: Node, private val child2: Node, private val func: (Double, Double) -> Double) :
    Node {
    override fun evaluate(state: State) = func(child1.evaluate(state), child2.evaluate(state))
}

class UnaryOperator(private val child: Node, private val func: (Double) -> Double) : Node {
    override fun evaluate(state: State) = func(child.evaluate(state))
}

class VariableNode(private val name: String) : Node {
    override fun evaluate(state: State) =
        state.getVar(name) ?: throw NodeEvaluationException("Variable $name not found")
}

class FunctionNode(private val name: String, private val args: Array<Node>) : Node {
    override fun evaluate(state: State) =
        state.functions[name]?.invoke(Array(args.size) { args[it].evaluate(state) }, state)
            ?: throw NodeEvaluationException("Function $name not found")
}

//Assignments and function declarations do not need nodes, they are processed directly
//Commands will start with a backslash. They can either be at the start of the line, or in some cases can be inlined

/**
 * Since this language does not allow passing an Integer by reference, this is needed
 */
data class IntHolder(var i: Int)

//If a function or variable was declared, add it to state, otherwise return the root node of the expression
fun evaluate(expr: String, state: State): Double? {
    if (expr.isEmpty())
        return null
//The first step is to find out if this expression needs to be treated differently. This is the case if it assigns to a function or variable or if it is a command
    if (expr[0] == '\\') {
        var i: Int
        val command = state.commandCallbacks[expr.substring(
            1 until with(
                expr.indexOf(
                    ' ',
                    2
                )
            ) { if (this == -1) expr.length else this }.also {
                i = (it + 1).let { if (it < expr.length) it else expr.length }
            })]
        command?.invoke(expr.substring(i until expr.length))
            ?: throw ParserException("Unknown command")
        return null
    }
    if (expr.contains(":=")) {
        parseAssign(expr, state)
        return null
    }
    //TODO: assert all characters have been consumed
    return parseExpr(expr, IntHolder(0)).evaluate(state).also { state.saveResult(it) }
}

//TODO: profile substring and decide whether using it would increase readability

fun parseAssign(expr: String, state: State) {
    //TODO: prevent overriding certain functions
    if (expr[0] !in 'a'..'z') throw  ParserException("Expected variable name")
    val trimmed = expr.trim()
    var name: String
    var i = trimmed.indexOfFirst { c -> c !in 'a'..'z' && c !in '0'..'9' }.also { name = trimmed.substring(0 until it) }
    if (name.isEmpty())
        throw ParserException("Tried creating function/variable without name")
    while (i < expr.length && (expr[i] == ' ' || expr[i] == '\t'))
        ++i
    if (i < expr.length && expr[i] == '(')
        run {
            ++i
            val args = ArrayList<String>()
            while (true) {
                if (i >= expr.length) throw ParserException("Unexpected end of function name")
                if (expr[i] == ' ' || expr[i] == '\t' || expr[i] == ',') {
                    ++i
                    continue
                }
                if (expr[i] == ')')
                    break
                val begin = i
                if (expr[i] !in 'a'..'z') throw ParserException("Variables must start with a letter")
                while (i < expr.length && (expr[i] in 'a'..'z' || expr[i] in '0'..'9')) ++i
                args += expr.substring(begin until i)
            }
            ++i
            while (i < expr.length && (expr[i] == ' ' || expr[i] == '\t'))
                ++i
            if (i + 1 < expr.length && expr[i] == ':' && expr[i + 1] == '=') {
                val e = parseExpr(expr, IntHolder(i + 2))
                state.functions[name] = State.createWrappedFunction(args.size, args.size) { a, s ->
                    val s1 = s.cloneVars()
                    for (j in 0 until args.size)
                        s1.changeConstant(args[j], a[j])
                    e.evaluate(s1)
                }
            } else
                throw ParserException("Failed to process assignment")
        }
    //Regex might help for validation, but I am unsure of its performance
    else if (i + 1 < expr.length && expr[i] == ':' && expr[i + 1] == '=')
        state.setVar(name, parseExpr(expr, IntHolder(i + 2)).evaluate(state))
    else throw ParserException("Failed to process assignment")
}

fun parseExpr(expr: String, pos: IntHolder): Node = run {
    var result = parseTerm(expr, pos)
    while (pos.i < expr.length)
        when (expr[pos.i]) {
            ' ', '\t' -> ++pos.i
            '+' -> {
                ++pos.i
                result = BinaryOperator(result, parseTerm(expr, pos)) { a, b -> a + b }
            }
            '-' -> {
                ++pos.i
                result = BinaryOperator(result, parseTerm(expr, pos)) { a, b -> a - b }
            }
            ';' -> {
                ++pos.i
                break
            }
            else -> break
        }
    result
}

fun parseTerm(expr: String, pos: IntHolder): Node = run {
    var result = parseUnary(expr, pos)
    while (pos.i < expr.length)
        when (expr[pos.i]) {
            ' ', '\t' -> ++pos.i
            '/' -> {
                ++pos.i
                result = BinaryOperator(result, parseUnary(expr, pos)) { a, b -> a / b }
            }
            '*' -> {
                ++pos.i
                result = BinaryOperator(result, parseUnary(expr, pos)) { a, b -> a * b }
            }
            else -> break
        }
    result
}

fun parseUnary(expr: String, pos: IntHolder): Node = run {
    while (pos.i < expr.length && (expr[pos.i] == ' ' || expr[pos.i] == '\t'))
        ++pos.i
    if (pos.i < expr.length)
        when (expr[pos.i]) {
            '-' -> run {
                ++pos.i
                UnaryOperator(parseUnary(expr, pos)) { -it }
            }
            '+' -> run {
                ++pos.i
                parseUnary(expr, pos)
            }
            else -> parsePow(expr, pos)
        }
    else
        throw ParserException("Unexpected end of expression")
}

fun parsePow(expr: String, pos: IntHolder): Node = run {
    val lhs = parseFactor(expr, pos)
    while (pos.i < expr.length && (expr[pos.i] == ' ' || expr[pos.i] == '\t'))
        ++pos.i
    if (pos.i >= expr.length) lhs
    else
        when (expr[pos.i]) {
            '^' -> {
                ++pos.i
                BinaryOperator(lhs, parsePow(expr, pos)) { a, b -> a.pow(b) }
            }
            else -> lhs
        }
}


fun parseFactor(expr: String, pos: IntHolder): Node = run {
    while (pos.i < expr.length && (expr[pos.i] == ' ' || expr[pos.i] == '\t'))
        ++pos.i
    if (pos.i >= expr.length) throw ParserException("Unexpected end of expression")
    when (expr[pos.i]) {
        in '0'..'9' -> run {
            val start = pos.i
            var dotFound = false
            while (pos.i < expr.length)
                when (expr[pos.i]) {
                    in '0'..'9' -> ++pos.i
                    '.' -> if (dotFound) throw ParserException("More than one dot in number") else {
                        ++pos.i
                        dotFound = true
                    }
                    else -> break
                }
            NumberNode(expr.substring(start until pos.i).toDouble())
        }
        in 'a'..'z' -> {
            val start = pos.i
            while (pos.i < expr.length && (expr[pos.i] in 'a'..'z' || expr[pos.i] in '0'..'9'))
                ++pos.i
            val name = expr.substring(start until pos.i)
            if (pos.i < expr.length && expr[pos.i] == '(')
                run {
                    ++pos.i
                    val args = ArrayList<Node>()
                    while (true) {
                        if (pos.i >= expr.length) throw ParserException("Unexpected end of expression")
                        if (expr[pos.i] == ')')
                            break
                        args += parseExpr(expr, pos)
                        if (pos.i >= expr.length) throw ParserException("Unexpected end of expression")
                        if (expr[pos.i] == ',')
                            ++pos.i
                    }
                    ++pos.i
                    FunctionNode(name, args.toTypedArray())
                }
            else
                VariableNode(name)
        }
        '(' -> run {
            ++pos.i
            val v = parseExpr(expr, pos)
            while (pos.i < expr.length && (expr[pos.i] == ' ' || expr[pos.i] == '\t'))
                ++pos.i
            if (pos.i >= expr.length) throw ParserException("Unexpected end of expression")
            if (expr[pos.i++] != ')') throw  ParserException("Expected )")
            v
        }
        else -> throw ParserException("Unknown token")
    }
}

