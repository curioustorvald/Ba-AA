package net.torvald.aa

import net.torvald.aa.demoplayer.toUint
import org.newdawn.slick.*

import java.util.Random

/**
 * Created by minjaesong on 16-08-10.
 */
class AAFrame @Throws(SlickException::class)
constructor(var width: Int, var height: Int) {

    /**
     * xx_000000_00000000

     * Upper bit: Colour 0 black 1 dark grey 2 grey 3 white || 0-31 arbitrary index
     * Lower 8 bits: ASCII
     */
    internal val frameBuffer: CharArray

    private val testrng = Random()

    val sizeof = 2 * width * height // magic number 2: indicator that we're using char

    init {
        frameBuffer = CharArray(width * height)
    }

    fun drawBuffer(x: Int, y: Int, c: Char, brightness: Int) {
        frameBuffer[y * width + x] = (c.toInt() or (brightness and 0x1F shl 8)).toChar()
    }

    fun drawBuffer(x: Int, y: Int, raw: Char): Boolean =
        if (checkOOB(x, y))
            false
        else {
            frameBuffer[y * width + x] = raw
            true
        }

    fun drawFromBytes(other: ByteArray) {
        for (i in 0..other.size - 1 step 2) {
            val char = (other[i].toUint().shl(8) or other[i + 1].toUint()).toChar()
            frameBuffer[i.ushr(1)] = char
        }
    }

    fun getColorKey(x: Int, y: Int): Int {
        return frameBuffer[y * width + x].toInt().ushr(8) and 0x1F
    }

    fun getChar(x: Int, y: Int): Char {
        return (frameBuffer[y * width + x].toInt() and 0xFF).toChar()
    }

    fun getRaw(x: Int, y: Int): Char? =
        if (checkOOB(x, y))
            null
        else
            frameBuffer[y * width + x]

    fun clear() {
        for (y in 0..height - 1) {
            for (x in 0..width - 1) {
                frameBuffer[y * width + x] = 0.toChar()
            }
        }
    }

    fun drawString(s: String, x: Int, y: Int, col: Int) {
        if (checkOOB(x, y)) return

        for (i in 0..s.length - 1) {
            if (checkOOB(x + i, y)) return

            frameBuffer[y * width + x + i] =
                    (s[i].toInt() or
                            ((if (s[i] == 0.toChar() || s[i] == 32.toChar()) 0 else col) shl 8)).toChar()
        }
    }

    fun drawFromOther(other: AAFrame) {
        //this.framebuffer = other.getFrameBuffer();
        for (y in 0..height - 1) {
            for (x in 0..width - 1) {
                frameBuffer[y * width + x] = other.getRaw(x, y)!!
            }
        }
    }

    private fun checkOOB(x: Int, y: Int) = (x < 0 || y < 0 || x >= width || y >= height)
}
