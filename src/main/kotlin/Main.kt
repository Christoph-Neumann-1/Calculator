import java.awt.Color
import kotlin.math.roundToInt
import kotlin.system.exitProcess

fun bindCommands(f: Frontend, commands: HashMap<String, (String) -> Unit>, s: State) {
    commands["exit"] = { exitProcess(0) }
    commands["quit"] = { exitProcess(0) }
    commands["draw"] = { it ->
        val trimmed = it.trim()
        if (trimmed in s.functions) {
            val func = s.functions[trimmed]!!
            f.functions.addToDraw(trimmed) { x, y -> func(arrayOf(x), s) - y }
        } else {
            val state = s.cloneVars()
            //This is declared outside so as not to parse the input every time
            val expr = parseExpr(trimmed, IntHolder(0))
            //TODO: caching of function results
            f.functions.addToDraw { x, y -> expr.evaluate(state.also { state.setVar("x", x) }) - y }
        }
        f.canvas.repaint()
    }
    //I have not even the slightest clue if this is the correct term
    commands["drawRelation"] = {
        val trimmed = it.trim()
        if (trimmed in s.functions) {
            val func = s.functions[trimmed]!!
            f.functions.addToDraw(trimmed) { x, y -> func(arrayOf(x, y), s) }
        } else {
            val state = s.cloneVars()
            val expr = parseExpr(trimmed, IntHolder(0))
            f.functions.draw { x, y ->
                expr.evaluate(state.also {
                    state.setVar(
                        "x",
                        x
                    );state.setVar("y", y)
                })
            }
        }
        f.canvas.repaint()
    }
    commands["redraw"] = {
        f.canvas.repaint()
    }
    commands["clear"] = {
        f.functions.clear()
        f.canvas.repaint()
    }
    //TODO: remove all anonymous functions
    commands["undraw"] =
        {
            f.functions.removeFromDraw(it.trim())
            f.canvas.repaint()
        }
    commands["xmin"] = {
        f.functions.xmin = it.toDouble()
        f.canvas.repaint()
    }
    commands["xmax"] = {
        f.functions.xmax = it.toDouble()
        f.canvas.repaint()
    }
    commands["ymin"] = {
        f.functions.ymin = it.toDouble()
        f.canvas.repaint()
    }
    commands["ymax"] = {
        f.functions.ymax = it.toDouble()
        f.canvas.repaint()
    }
    commands["axis"] = {
        f.functions.axis = !f.functions.axis
        f.canvas.repaint()
    }
    commands["vline"] = {
        val x = ((it.toDouble() - f.functions.xmin) * f.functions.width / (f.functions.xmax - f.functions.xmin)).toInt()
        with(f.functions) {
            anonymousFunctions +=
                {
                    pixels.graphics.also { it.color = Color.BLACK }.drawLine(x, 0, x, height - 1)
                    f.canvas.repaint()
                }.also { it.invoke() }
        }
    }
    commands["withColor"] = { TODO() }
    commands["solve"] = {
        with(f.functions)
        {
            val state = s.cloneVars()
            val pos = IntHolder(0)
            val str = it.trim()
            val expr = parseExpr(str, pos)
            val lowerBound = parseExpr(str, pos).evaluate(s)
            val upperBound = parseExpr(str, pos).evaluate(s)

            fun evalAt(x: Double) = expr.evaluate(state.also { state.setVar("x", x) })

            fun containsSolution(a: Double, b: Double): Boolean = evalAt(a) * evalAt(b) <= 0

            //TODO make recursive and find ALL solutions in interval
            fun findStartingInterval(): Pair<Double, Double>? {
                var nIntervals = 1
                while (true) {
                    if (nIntervals >= 4096)
                        return null
                    val step = (upperBound - lowerBound) / nIntervals
                    for (i in 0 until nIntervals) {
                        val lower = lowerBound + i * step
                        val upper = lower + step
                        if (containsSolution(lower, upper))
                            return Pair(lower, upper)
                    }
                    nIntervals *= 2
                }
            }

            fun findBetterSolution(interval: Pair<Double, Double>, n: Int = 0): Double {
                val (a, b) = interval
                //This can definitely be done more efficiently
                if (evalAt(a) == 0.0)
                    return a
                if (evalAt(b) == 0.0)
                    return b
                return if (n > 100000 || b - a < 0.0001)
                    (a + b) / 2
                else
                    if (containsSolution(a, (a + b) / 2))
                        findBetterSolution(Pair(a, (a + b) / 2))
                    else
                        findBetterSolution(Pair((a + b) / 2, b))
            }

            f.previous.text += "Found solution ${findStartingInterval()?.let { findBetterSolution(it) } ?: "None"}\n"
        }
    }
    commands["thickness"] = {
        f.functions.thickness = parseExpr(it, IntHolder(0)).evaluate(s).roundToInt()
        f.canvas.repaint()
    }
    commands["help"] = {
        f.previous.text +=
            """
                Usage:
                Enter an expression and it will be calculated
                Supported operations are +,-,*,/,^ and parentheses
                To define a variable of function use := 
                Example:
                f(x):=5x^2
                G:=9.8
                
                Builtin variables:
                pi
                e
                
                Builtin functions:
                sin
                cos
                tan
                asin
                acos
                atan
                sqrt
                root(value,order)
                abs
                min
                max
                
                Commands:
                \help
                \exit
                \quit
                \draw draws either a function accepting one argument or an expression where x is the current x position. Only functions will stay after a redraw
                \drawRelation draws a function of both x and y like circles e.g x^2+y^2-1
                \clear clears the drawing area
                \axis toggle the axis. This redraws everything
                \redraw redraws all functions
                \undraw stop drawing a function, this too redraws everything, if empty the last element of the anonymous functions is removed
                These set viewport size
                \xmax
                \xmin
                \ymax
                \ymin
                \thickness how many pixels from the center to color default=0
                
                \solve finds a solution to an equation in the form f(x)=0
                
                Access previous results:
                ans for last result
                ans1-9 for anything before that
                
            """.trimIndent()
    }
}

fun main() {
    //TODO: make axes appear in right place for an even number of pixels
    val f = Frontend(601, 601)
    val commands = HashMap<String, (String) -> Unit>()
    val s = State(commands)
    bindCommands(f, commands, s)
    f.input.addActionListener {
        f.previous.text += "${f.input.text}\n"
        evaluate(f.input.text, s).apply {
            this?.let {
                f.previous.text += "$this\n"
            }
        }
        f.input.text = ""
    }
    f.canvas.repaint()
}