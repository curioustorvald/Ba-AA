package net.torvald.aa

import org.newdawn.slick.Image

/**
 * Created by minjaesong on 16-08-12.
 */
interface AsciiAlgo {
    fun precalcFont()
    fun toAscii(rawImage: Image, aaframe: AAFrame)
    var fontW: Int
    var fontH: Int
}