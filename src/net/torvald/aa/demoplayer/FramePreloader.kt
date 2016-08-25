package net.torvald.aa.demoplayer

import net.torvald.aa.AAFrame
import net.torvald.aa.ImageToAA
import org.newdawn.slick.Image
import java.io.File
import java.lang.management.ManagementFactory

/**
 * Created by minjaesong on 16-08-11.
 */
class FramePreloader(
        private val ref: String,
        private val w: Int,
        private val h: Int,
        private val prefix: String,
        private val preCalcRate: Int) : FrameFetcher {


    private val framesList: Array<File>
    override val frameCount: Int

    private var loadCount = 0

    private val asciiFrames: Array<AAFrame>

    init {
        val f = File(ref)
        framesList = f.listFiles { dir, name ->
            name.startsWith(prefix) && (name.endsWith(".jpg") || name.endsWith(".bmp")
                    || name.endsWith("" + ".png") || name.endsWith("" + ".tga"))
        }

        frameCount = framesList.size

        asciiFrames = Array<AAFrame>(frameCount, { AAFrame(w, h) })
    }

    private lateinit var frameInput: Image

    override fun init() {
    }

    override fun preJob(framebuffer: AAFrame) {
        for (k in 0..preCalcRate - 1) {
            frameInput = Image(framesList[loadCount].absolutePath)
            // note that getScaleCopy() does not actually resize image as Photoshop would do. It does in the OpenGL context.

            // convert image to ascii and store it
            BaAA.imageToAA.toAscii(frameInput.copy(), asciiFrames[loadCount])

            // message
            val msgConsole = "Precalculating frame ${loadCount + 1} of $frameCount"
            val msgWindow = "${BaAA.appname} — MEM: " +
                    "${ManagementFactory.getMemoryMXBean().heapMemoryUsage.used ushr 20}" +
                    "M/${Runtime.getRuntime().maxMemory() ushr 20}M — " +
                    "$msgConsole"

            // printout message
            framebuffer.drawString(msgConsole, 3, 2, BaAA.colors.size - 1)

            BaAA.appgc.setTitle(msgWindow)

            // progress bar
            val barwidth = w - 6
            for (i in 0..barwidth - 1) {
                if (i < Math.round(loadCount.toFloat() / frameCount * barwidth)) {
                    framebuffer.drawString("#", 3 + i, 4, BaAA.colors.size - 1)
                } else {
                    framebuffer.drawString(".", 3 + i, 4, BaAA.colors.size - 1)
                }
            }

            loadCount++

            if (preJobDone()) break
        }
    }

    override fun preJobDone() = loadCount >= frameCount

    override fun setFrameBuffer(framebuffer: AAFrame, frameNo: Int) {
        framebuffer.drawFromOther(asciiFrames[frameNo])
    }
}