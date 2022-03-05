import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.*


//TODO: resizing
//TODO: figure out why the window is not at the correct size sometimes
class Frontend(width: Int, height: Int) {
    val window = JFrame("Calculator")
    val functions = FunctionDrawer(width, height)
    val canvas = Canvas(functions)
    val layout = BoxLayout(window.contentPane, BoxLayout.Y_AXIS)
    val input = JTextField().also { it.maximumSize = Dimension(Int.MAX_VALUE, 30) }
    val previous = JTextArea().also { it.isEditable = false }

    class Canvas(val functions: FunctionDrawer) : JPanel() {
        init {
            val dim = Dimension(width, height)
            minimumSize = dim
            preferredSize = dim
        }

        override fun paint(g: Graphics?) {
            g ?: return
            functions.redraw()
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
        var toDraw = HashMap<String, (Double, Double) -> Double>()

        //This is kept as generic as possible to allow other commands like vline to be used from here
        var anonymousFunctions = ArrayDeque<() -> Unit>()
        private val corners = Array<Double>((width + 1) * (height + 1)) { 0.0 }
        var pixels = BufferedImage(width, height, BufferedImage.TYPE_USHORT_555_RGB)
        var xmin = -10.0
        var xmax = 10.0
        var ymin = -10.0
        var ymax = 10.0
        var axis = true
        var thickness = 0//additional pixels to color in each direction

        fun scaleX(x: Double) = xmin + (xmax - xmin) / width * x
        fun scaleY(y: Double) = ymin + (ymax - ymin) / height * y

        fun redraw() {
            pixels.createGraphics().fillRect(0, 0, width, height)
            for (f in toDraw)
                draw(f.value)
            for (f in anonymousFunctions)
                f()
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

        fun addToDraw(name: String = "", f: (Double, Double) -> Double) {
            if (name.isEmpty()) {
                anonymousFunctions += { draw(f) }
                draw(f)
                return
            }
            if (name in toDraw) {
                toDraw[name] = f
                return
            }
            toDraw[name] = f
            draw(f)
        }

        fun removeFromDraw(name: String) {
            toDraw.remove(name)
                ?: if (name.isEmpty()) anonymousFunctions.removeLastOrNull() else throw ParserException("Tried removing undefined function")
        }


        fun draw(f: (Double, Double) -> Double) {
            fun get(x: Int, y: Int) = corners[x * (width + 1) + y]
            for (x in 0..width)
                for (y in 0..height)
                    corners[x * (width + 1) + y] = f(scaleX(x - .5), scaleY(y - .5))
            val g = pixels.createGraphics()
            g.color = Color.BLACK
            for (x in 0 until width)
                for (y in 0 until height)
                    if (get(x, y) * get(x + 1, y + 1) + get(x, y + 1) * get(x + 1, y) <= 0)
                        g.fillRect(x - thickness, height - y - thickness - 1, 2 * thickness + 1, 2 * thickness + 1)
        }

        fun clear() {
            toDraw.clear()
            anonymousFunctions.clear()
        }

//        fun removeFromDraw(name:String){}

    }
}

