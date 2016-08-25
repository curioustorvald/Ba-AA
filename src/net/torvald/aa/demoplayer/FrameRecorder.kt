package net.torvald.aa.demoplayer

import net.torvald.aa.AAFrame
import net.torvald.aa.roundInt
import org.newdawn.slick.Image
import java.io.File
import java.lang.management.ManagementFactory

/**
 * Created by minjaesong on 16-08-24.
 */
class FrameRecorder(
        private val ref: String,
        private val w: Int,
        private val h: Int,
        private val prefix: String,
        private val preCalcRate: Int) : FrameFetcher {

    private val framesList: Array<File>
    override val frameCount: Int

    private var loadCount = 0

    private val asciiBuffer = AAFrame(w, h)

    private lateinit var frameInput: Image

    private lateinit var outfile: File

    private val estmSize: Int

    init {
        val f = File(ref)
        framesList = f.listFiles { dir, name ->
            name.startsWith(prefix) && (name.endsWith(".jpg") || name.endsWith(".bmp")
                    || name.endsWith("" + ".png") || name.endsWith("" + ".tga"))
        }

        frameCount = framesList.size
        estmSize = (w * h * 2 * frameCount + 13).ushr(20)

        /******************************************
         * Generate file to write, write metadata *
         ******************************************/

        val filename =
                "${BaAA.getFramename()}_" +
                        "${BaAA.getFontFileName()}_" +
                        "${w}x${h}_" +
                        "C${BaAA.getColorsCount()}_" +
                        "A${BaAA.getAlgorithm()}${BaAA.getDitherAlgo()}" +
                        (if (BaAA.isFullCodePage()) "_fullcp" else "") +
                        ".aarec"

        outfile = File("./$filename")

        println("Recording frame to $outfile")

        serialiseMetadata(outfile)
    }

    override fun setFrameBuffer(framebuffer: AAFrame, frameNo: Int) {
        throw UnsupportedOperationException("Record only; TODO: quit applet with message")
    }

    private var startNano: Long = 0

    override fun init() {
        startNano = System.nanoTime()
    }

    override fun preJob(framebuffer: AAFrame) {
        /*************************************************************
         * Write image to AA framebuffer, serialise THAT framebuffer *
         *************************************************************/

        try {
            for (k in 0..preCalcRate - 1) {
                frameInput = Image(framesList[loadCount].absolutePath)
                // note that getScaleCopy() does not actually resize image as Photoshop would do. It does in the OpenGL context.

                // convert image to ascii and store it
                BaAA.imageToAA.toAscii(frameInput.copy(), asciiBuffer)

                // serialise the frame
                serialiseFrame(outfile, asciiBuffer)



                // message
                val msgConsole = "Recording frame ${loadCount + 1} of $frameCount"
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

                // some more information
                framebuffer.drawString("Filename: ${outfile.name}", 3, 12, BaAA.colors.size - 1)
                framebuffer.drawString("Estimated size: $estmSize Mbytes", 3, 14, BaAA.colors.size - 1)

                if (loadCount > 0) {
                    val elapsedTime = System.nanoTime() - startNano
                    val remainingTime =
                            ((frameCount.minus(loadCount).toDouble() / loadCount) * elapsedTime)
                                    .div(1000).div(1000).div(1000).roundInt()

                    framebuffer.drawString("Estimated remaining: ${getEstimated(remainingTime)}          ",
                            3, 16, BaAA.colors.size - 1)
                }

                loadCount++
            }
        } catch (e1: ArrayIndexOutOfBoundsException) {
            // a fluke; do nothing
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getEstimated(seconds: Int): String {
        fun pluralise(i: Int) = "${if (i != 1) "s" else ""}"

        if (seconds >= 3600) {
            val hours = seconds.div(3600.0).roundInt()
            return "$hours hour${pluralise(hours)}"
        }
        else if (seconds >= 60) {
            val minutes = seconds.div(60.0).roundInt()
            return "${Math.max(1, minutes)} minute${pluralise(minutes)}"
        }
        else {
            return "about a minute"
        }
    }

    override fun preJobDone() = loadCount >= frameCount


    /*
    Format documentation
    *   Big endian.
    - "BaAArecD"(magic)
    - int8      Glyph width
    - int8      Glyph height
    - int8      Playback rate (FPS)
    - int8      # of colours
    - int8      algorithm index
    - int16     Terminal width
    - int16     Terminal height
    - int32     # of frames
    - Array<AAFrame>    Array of data (aaFrame.framebuffer)
     */

    fun serialiseMetadata(file: File) {
        val i8s = byteArrayOf(
                BaAA.imageToAA.fontW.toByte(),
                BaAA.imageToAA.fontH.toByte(),
                BaAA.framerate.toByte(),
                BaAA.getColorsCount().toByte(),
                BaAA.getAlgorithm().times(10).plus(BaAA.getDitherAlgo()).toByte()
        )
        val i16s = byteArrayOf(
                asciiBuffer.width.ushr(8).and(0xFF).toByte(), // msb
                asciiBuffer.width.and(0xFF).toByte(),         // lsb
                asciiBuffer.height.ushr(8).and(0xFF).toByte(), // msb
                asciiBuffer.height.and(0xFF).toByte()          // lsb
        )
        val nFramesBigEndian = byteArrayOf(
                frameCount.ushr(24).and(0xFF).toByte(),
                frameCount.ushr(16).and(0xFF).toByte(),
                frameCount.ushr(8).and(0xFF).toByte(),
                frameCount.and(0xFF).toByte()
        )

        // make sure it starts with magic
        file.writeBytes(magic)

        file.appendBytes(i8s)
        file.appendBytes(i16s)
        file.appendBytes(nFramesBigEndian)
    }

    fun serialiseFrame(file: File, aaFrame: AAFrame) {
        val frameBytes = ByteArray(aaFrame.sizeof)
        for (i in 0..frameBytes.size - 1 step 2) {
            val glyph = aaFrame.frameBuffer[i.shr(1)].toInt()
            frameBytes[i] = glyph.ushr(8).and(0xFF).toByte() // msb
            frameBytes[i + 1] = glyph.and(0xFF).toByte()     // lsb
        }
        file.appendBytes(frameBytes)
    }

    companion object {
        /** ASCII bytes of "BaAArecD" */
        val magic = byteArrayOf(0x42, 0x61, 0x41, 0x41, 0x72, 0x65, 0x63, 0x44)
    }
}