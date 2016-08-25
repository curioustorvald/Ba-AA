package net.torvald.aa.demoplayer

import net.torvald.aa.AAFrame
import java.io.*
import java.lang.management.ManagementFactory

/**
 * Created by minjaesong on 16-08-24.
 */

class FrameRecordPreloader(file: File) : FrameFetcher {

    override val frameCount: Int
    private val inputStream = BufferedInputStream(FileInputStream(file))
    private val frameDataBuffer: ByteArray
    private val sizeofFrame: Int

    var glyphW = -1; private set
    var glyphH = -1; private set
    var framerate = -1; private set
    var nColour = -1; private set
    var nAlgo = -1; private set
    var width = -1; private set
    var height = -1; private set

    private val asciiFrames: Array<AAFrame>
    private var loadCount = 0

    private val preloadRate = 517



    init {
        val byteBuffer = ByteArray(8)

        println("Reading record ${file.path}")
        println("Filesize: ${file.length()} bytes")

        inputStream.readRelative(byteBuffer, 0x00, 8)
        if (!byteBuffer.assertMagic(FrameRecorder.magic)) throw RuntimeException("Not a valid record file!")

        inputStream.readRelative(byteBuffer, 0x08, 1); glyphW = byteBuffer.toInt(1)
        inputStream.readRelative(byteBuffer, 0x09, 1); glyphH = byteBuffer.toInt(1)
        inputStream.readRelative(byteBuffer, 0x0A, 1); framerate = byteBuffer.toInt(1)
        inputStream.readRelative(byteBuffer, 0x0B, 1); nColour = byteBuffer.toInt(1)
        inputStream.readRelative(byteBuffer, 0x0C, 1); nAlgo = byteBuffer.toInt(1)
        inputStream.readRelative(byteBuffer, 0x0D, 2); width = byteBuffer.toInt(2)
        inputStream.readRelative(byteBuffer, 0x0F, 2); height = byteBuffer.toInt(2)
        inputStream.readRelative(byteBuffer, 0x11, 4); frameCount = byteBuffer.toInt(4)

        sizeofFrame = width * height * 2

        println("glyph dimension: $glyphW x $glyphH")
        println("framerate: $framerate")
        println("colours: $nColour")
        println("algorithm: $nAlgo")
        println("dimension: $width x $height")
        println("frame data length: $sizeofFrame")
        println("# of frames: $frameCount")

        frameDataBuffer = ByteArray(sizeofFrame)

        asciiFrames = Array<AAFrame>(frameCount, { AAFrame(width, height) })
    }

    override fun setFrameBuffer(framebuffer: AAFrame, frameNo: Int) {
        framebuffer.drawFromOther(asciiFrames[frameNo])
    }

    override fun init() {
        BaAA.updateColours(nColour)
        BaAA.updateFramerate(framerate)
        BaAA.updateDisplayMode(width, height, glyphW, glyphH)
    }

    override fun preJob(framebuffer: AAFrame) {
        for (k in 0..preloadRate - 1) {
            inputStream.readRelative(frameDataBuffer, 0x15 + sizeofFrame * loadCount, sizeofFrame)
            asciiFrames[loadCount].drawFromBytes(frameDataBuffer)


            // message
            val msgConsole = "Loading frame data to RAM ${loadCount + 1} of $frameCount"
            val msgWindow = "${BaAA.appname} — MEM: " +
                    "${ManagementFactory.getMemoryMXBean().heapMemoryUsage.used ushr 20}" +
                    "M/${Runtime.getRuntime().maxMemory() ushr 20}M — " +
                    "$msgConsole"

            // printout message
            framebuffer.drawString(msgConsole, 3, 2, BaAA.colors.size - 1)

            BaAA.appgc.setTitle(msgWindow)

            // progress bar
            val barwidth = width - 6
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
    /**
     * Big endian
     */
    private fun ByteArray.toInt(nBytes: Int) =
            if (nBytes == 1)
                this[0].toUint()
            else if (nBytes == 2)
                this[0].toUint().shl(8) or this[1].toUint()
            else if (nBytes == 4)
                this[0].toUint().shl(24) or
                this[1].toUint().shl(16) or
                this[2].toUint().shl(8) or
                this[3].toUint()
            else
                throw IllegalArgumentException("Incompatible data size: $nBytes")
    private fun ByteArray.assertMagic(magic: ByteArray): Boolean {
        for (i in 0..magic.size - 1)
            if (this[i] != magic[i]) return false
        return true
    }

    /**
     * Still have to read from head to tail
     */
    fun InputStream.readRelative(b: ByteArray, off: Int, len: Int): Int {
        if (b == null) {
            throw NullPointerException()
        } else if (off < 0 || len < 0 || len > b.size) {
            throw IndexOutOfBoundsException()
        } else if (len == 0) {
            return 0
        }

        var c = read()
        if (c == -1) {
            return -1
        }
        b[0] = c.toByte()

        var i = 1
        try {
            while (i < len) {
                c = read()
                if (c == -1) {
                    break
                }
                b[i] = c.toByte()
                i++
            }
        } catch (ee: IOException) {
        }

        return i
    }

    fun ByteArray.toByteStrings(): String {
        val sb = StringBuilder()
        for (i in 0..this.size - 1) {
            sb.append(Integer.toHexString(this[i].toUint()).toUpperCase())
            sb.append(" ")
        }
        return sb.toString()
    }
}

fun Byte.toUint() = java.lang.Byte.toUnsignedInt(this)