import java.awt.Color
import kotlin.math.absoluteValue
import kotlin.system.exitProcess

fun bindCommands(f:Frontend,commands:HashMap<String, (String) -> Unit>,s:State){
    commands["exit"] = { exitProcess(0) }
    commands["quit"] = { exitProcess(0) }
    commands["draw"] = {
        val trimmed = it.trim()
        if (trimmed in s.functions) {
            val func = s.functions[trimmed]!!
            f.functions.addToDraw(trimmed) { n -> func(arrayOf(n), s) }
        } else {
            val state = s.cloneVars()
            f.functions.draw { n -> parseExpr(trimmed, IntHolder(0)).evaluate(state.also { state.setVar("x", n) }) }
        }
        f.canvas.repaint()
    }
    commands["redraw"] = {
        f.functions.redraw()
        f.canvas.repaint()
    }
    commands["clear"] = {
        f.functions.toDraw.clear()
        f.functions.redraw()
        f.canvas.repaint()
    }
    commands["undraw"] =
        {
            f.functions.removeFromDraw(it.trim())
            f.canvas.repaint()
        }
    commands["xmin"] = {
        f.functions.xmin = it.toDouble()
        f.functions.redraw()
        f.canvas.repaint()
    }
    commands["xmax"] = {
        f.functions.xmax = it.toDouble()
        f.functions.redraw()
        f.canvas.repaint()
    }
    commands["ymin"] = {
        f.functions.ymin = it.toDouble()
        f.functions.redraw()
        f.canvas.repaint()
    }
    commands["ymax"] = {
        f.functions.ymax = it.toDouble()
        f.functions.redraw()
        f.canvas.repaint()
    }
    commands["axis"] = {
        with(f)
        {
            functions.axis = !functions.axis
            if (functions.axis)
                functions.drawAxis()
            else
                functions.redraw()
            canvas.repaint()
        }
    }
    commands["vline"]={
        val x=((it.toDouble()-f.functions.xmin)*f.functions.width/(f.functions.xmax-f.functions.xmin)).toInt()
        with(f) {
            functions.pixels.graphics.also { it.color= Color.BLACK }.drawLine(x,0,x, functions.height-1)
            canvas.repaint()
        }
    }
    commands["solve"] = {
        with(f.functions)
        {
            val state = s.cloneVars()
            val expr = parseExpr(it.trim(), IntHolder(0))
            var prev = expr.evaluate(s.also { it.changeConstant("x", xmin) })
            for (i in 1..width) {
                val current = expr.evaluate(s.also { it.changeConstant("x", scaleX(i)) })
                if (current * prev <= 0) {
                    val x = scaleX(if (prev.absoluteValue < current.absoluteValue) i - 1 else i)
                    f.previous.text += "${x}\n"
                    s.saveResult(x)
                    return@with
                }
                prev = current
            }

            f.previous.text += "${Double.NaN}\n"
            s.saveResult(Double.NaN)
        }
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
                
                Commands:
                \help
                \exit
                \quit
                \draw draws either a function accepting one argument or an expression where x is the current x position. Only functions will stay after a redraw
                \clear clears the drawing area
                \axis toggle the axis. This redraws everything
                \redraw redraws all functions
                \undraw stop drawing a function, this too redraws everything
                These set viewport size
                \xmax
                \xmin
                \ymax
                \ymin
                
                Access previous results:
                ans for last result
                ans1-9 for anything before that
                
            """.trimIndent()
    }
}

fun main() {
    val f = Frontend(600,600)
    val commands = HashMap<String, (String) -> Unit>()
    val s = State(commands)
    bindCommands(f,commands,s)
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
//    while(true) {
//        val input = readLine()!!
//        if(input=="\\exit")
//            return
//        evaluate(input, s)?.apply { println("$this") }?:println("No value")
//    }
}