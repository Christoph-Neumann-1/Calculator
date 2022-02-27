import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.BoxLayout
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField


//TODO: scaling
//TODO: allow removing one function from draw
class Frontend(width: Int, height: Int) {
    val window = JFrame("Calculator")
    val functions = FunctionDrawer(width, height)
    val canvas = Canvas(functions)
    val layout = BoxLayout(window.contentPane, BoxLayout.Y_AXIS)
    val input = JTextField().also { it.maximumSize = Dimension(Int.MAX_VALUE, 30) }
    val previous = JTextArea().also { it.isEditable = false }

    class Canvas(val functions: FunctionDrawer) : JPanel() {
        init {
            val dim=Dimension(width,height)
                minimumSize = dim
                preferredSize = dim
        }

        override fun paint(g: Graphics?) {
            g ?: return
            g.drawImage(functions.pixels, 0, 0, null)
        }
    }

    init {
        with(window)
        {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            layout = this@Frontend.layout
            add(
                JScrollPane(
                    previous,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                ).also {
                    it.preferredSize =
                        Dimension(width, 160)
                    it.maximumSize =
                        Dimension(Int.MAX_VALUE, 300)
                })
            add(input)
            add(canvas)
            isVisible = true
            size = Dimension(width, height + 160 + 24)
            isResizable = false
        }
    }

    class FunctionDrawer(val width: Int, val height: Int) {
        //bool should also work as return type
        var toDraw = HashMap<String, (Double) -> Double>()
        val cells = Array<Double>((width + 2)) { 0.0 }
        var pixels = BufferedImage(width, height, BufferedImage.TYPE_USHORT_555_RGB)
        var xmin = -10.0
        var xmax = 10.0
        var ymin = -10.0
        var ymax = 10.0
        var axis = true

        fun scaleX(x: Int) = xmin + (xmax - xmin) / width * x
        fun scaleY(y: Int) = ymin + (ymax - ymin) / height * y

        fun redraw() {
            clear()
            for (f in toDraw)
                draw(f.value)
            if (axis)
                drawAxis()
        }

        fun drawAxis() {
            val x = (width / (xmax - xmin) * -xmin).toInt()
            val y = (height / (ymax - ymin) * -ymin).toInt()
            /*
            The designers of swing are morons, the graphics context returned by getGraphics is NOT the same between invocations, then there is the fact that they felt the need to implement to different ways to draw stuff onto one surface by providing both
            getGraphics and createGraphics both of which create a graphics context. Who thought this was a good idea???
             */
            val g = pixels.createGraphics()
            g.color = Color.BLACK
            if (x in 0 until height)
                g.drawLine(x, 0, x, height - 1)
            if (y in 0 until height)
                g.drawLine(0, y, width - 1, y)
        }

        fun addToDraw(name: String, f: (Double) -> Double) {
            if (name in toDraw) {
                toDraw[name] = f
                redraw()
                return
            }
            toDraw[name] = f
            draw(f)
        }

        fun removeFromDraw(name: String) {
            toDraw.remove(name) ?: throw ParserException("Tried removing undefined function")
            redraw()
        }

        //Draws function to internal array, you still need to call paint on whatever surface you want the result on
        fun draw(f: (Double) -> Double) {
            for (x in -1..width) {
                cells[x + 1] = f(scaleX(x))
            }
            for (x in 0 until width)
                for (y in 0 until height) {
                    val fx = cells[x + 1]- scaleY(y)
                    loop@ for (x1 in -1 until 1)
                        for (y1 in -1 until 1)
                            if (fx * (cells[x - x1 + 1] - scaleY(y - y1)) < 0 || fx==0.0) {
                                pixels.setRGB(x, height - y - 1, Color.BLACK.rgb)
                                break@loop
                            }

                }
        }

        fun clear() {
            pixels.createGraphics().fillRect(0, 0, width, height)
        }

//        fun removeFromDraw(name:String){}

        init {
            redraw()
        }

    }
}

