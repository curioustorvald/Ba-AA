package net.torvald.aa

import net.torvald.aa.demoplayer.BaAA
import org.newdawn.slick.*
import java.util.*

/**
 * Created by minjaesong on 16-08-10.
 */
class ImageToAA : AsciiAlgo {

    private var w = 0
    private var h = 0
    private lateinit var fontSheet: SpriteSheet
    override var fontW = 0
    override var fontH = 0
    private var inverted = false
    private var gamma = 0.0
    private var ditherAlgo = 0
    private var colourAlgo = 0

    private lateinit var fontRange: IntRange

    private lateinit var colourMap: Array<Double>

    fun setProp(
            w: Int, h: Int,
            font: SpriteSheet, fontW: Int, fontH: Int,
            inverted: Boolean, gamma: Double, dither: Int, fullCodePage: Boolean, colourAlgo: Int) {
        this.w = w
        this.h = h
        this.fontW = fontW
        this.fontH = fontH
        this.fontSheet = font
        this.inverted = inverted
        this.gamma = gamma
        ditherAlgo = dither
        this.colourAlgo = colourAlgo

        if (fullCodePage)
            fontRange = 1..255
        else
            fontRange = 32..126

        imageBuffer = Image(w, h)

        // re-invert colourmap so that the calculation stay normal when inverted
        colourMap = Array<Double>(BaAA.colors.size, { 0.0 })

        ditherBuffer = Array<IntArray>(h, { IntArray(w, { 0 }) })

        if (BaAA.colors.size == BaAA.RANGE_CGA) {
            //if (!inverted)
            BaAA.colcga.forEachIndexed { i, c -> colourMap[i] = Color(c, c, c).getLuminance(colourAlgo, gamma) }
            //else
            //    BadAppleDemo.colcgaInv.forEachIndexed { i, c -> colourMap[i] = Color(c, c, c).getLuminance() }
        }
        else if (BaAA.colors.size == BaAA.RANGE_EXT) {
            //if (!inverted)
            BaAA.hexadecaGrey.forEachIndexed { i, c -> colourMap[i] = Color(c, c, c).getLuminance(colourAlgo, gamma) }
            //else
            //    BadAppleDemo.hexadecaGreyInv.forEachIndexed { i, c -> colourMap[i] = Color(c, c, c).getLuminance() }
        }
        else if (BaAA.colors.size == 2) {
            if (!inverted) {
                colourMap[0] = BaAA.getColor(0).getLuminance(colourAlgo, gamma)
                colourMap[1] = BaAA.getColor(1).getLuminance(colourAlgo, gamma)
            }
            else {
                colourMap[0] = BaAA.getColor(1).getLuminance(colourAlgo, gamma)
                colourMap[1] = BaAA.getColor(0).getLuminance(colourAlgo, gamma)
            }
        }
        else {
            throw IllegalStateException("Colour mode unknown; unexpected colour range: ${BaAA.colors.size}")
        }
    }

    //              Brightness, Raw frame (0bXXXXXXX_00_0000000)
    private val brightnessMap = ArrayList<Pair<Int, Char>>()
    private val glyphColMap = HashMap<Char, Int>()
    private val sameLumStartIndices = HashMap<Int, Int>()
    private val sameLumEndIndices = HashMap<Int, Int>()
    private val lumMap = ArrayList<Int>()

    private lateinit var imageBuffer: Image

    private var precalcDone = false

    private var fontLumMin = 0
    private var fontLumMax = 0

    private val exclude = arrayOf(
            '_'.getAscii(),
            '\\'.getAscii(),
            '/'.getAscii()
    )

    /**
     * (' ' - '\') * number of colours
     */
    private var totalGlyphCount = 0

    override fun precalcFont() {
        if (!precalcDone) {

            val fontSheetW = fontSheet.width / fontW

            for (bKey in 0..colourMap.size - 1) {
                for (i in fontRange) {
                    if (exclude.contains(i)) continue

                    val glyph = fontSheet.getSubImage(i % fontSheetW, i / fontSheetW).copy()
                    totalGlyphCount++

                    var glyphBrightness = 0
                    var perturb = 0
                    val perturbMod = 8

                    if (fontRange.endInclusive < 128 && i == 32) { // exclude ' ' if in ASCII mode
                        glyphBrightness = glyph.height * glyph.width * colourMap[0].oneTo256()
                    }
                    else {
                        for (fy in 0..glyph.height - 1) {
                            for (fx in 0..glyph.width - 1) {
                                val pixel = glyph.getColor(fx, fy).a
                                val b: Int
                                if (pixel == 0f)
                                    b = colourMap[0].oneTo256()
                                else {
                                    b = colourMap[bKey].oneTo256()
                                    perturb = (perturb xor (fx xor fy)) % perturbMod
                                }

                                glyphBrightness += b.toInt()// - perturb
                            }
                        }
                    }

                    brightnessMap.add(
                            Pair(
                                    glyphBrightness,
                                    (bKey.shl(8) or i.and(0xFF)).toChar()
                            ))
                    glyphColMap.put(
                            (bKey.shl(8) or i.and(0xFF)).toChar(),
                            glyphBrightness
                    )
                }
            }

            brightnessMap.sortBy { it.first }

            fontLumMin = brightnessMap.first().first

            // fix for negative brightness
            for (i in 0..brightnessMap.size - 1) {
                val first = brightnessMap[i].first - fontLumMin
                val second = brightnessMap[i].second

                brightnessMap[i] = Pair(first, second)
            }

            fontLumMin = brightnessMap.first().first
            fontLumMax = brightnessMap.last().first

            // test print
            brightnessMap.forEachIndexed { index, it ->
                println("Brightness ${it.first}\t" +
                        "glyph ${it.second.getAscii().toChar()} " +
                        "col ${it.second.getColourKey()}" +
                        " [$index]")
            }

            // make sameLumStartIndices and LumMap
            //    define starting point
            sameLumStartIndices.put(brightnessMap.first().first, 0)
            sameLumEndIndices.put(brightnessMap.last().first, brightnessMap.size - 1)
            for (i in 0..brightnessMap.size - 2) {
                // if (this != next), mark 'this' as endpoint, 'next' as start point
                val thisLum = brightnessMap[i].first
                val nextLum = brightnessMap[i + 1].first
                if (thisLum != nextLum) {
                    sameLumEndIndices.put(thisLum, i)
                    sameLumStartIndices.put(nextLum, i + 1)
                    lumMap.add(thisLum)
                }
            }
            // for edge case where there's only one brightest glyph
            if (!lumMap.contains(fontLumMax)) lumMap.add(fontLumMax)

            println("Total glyphs: $totalGlyphCount")

            // test print
            //sameLumStartIndices.forEach { lum, index -> println("Lum $lum, starts at $index") }
            //sameLumEndIndices.forEach { lum, index -> println("Lum $lum, ends at $index") }
            //lumMap.forEach { println(it) }

            println("Max brightness: $fontLumMax")

            precalcDone = true
        }
    }

    lateinit var ditherBuffer: Array<IntArray>

    val FLOYD_STEINBERG = 1
    val SIERRA_LITE = 2
    val SIERRA_2 = 3
    val SIERRA_3 = 4

    val bayer4Map = arrayOf(1, 9, 3, 11, 13, 5, 15, 7, 4, 12, 2, 10, 16, 8, 14, 6)
    val bayer4Divisor = bayer4Map.size - 1
    val bayer4MapSize = Math.sqrt(bayer4Map.size.toDouble()).toInt()

    private fun getBayer4(x: Int, y: Int) = bayer4Map[y * bayer4MapSize + (x % bayer4MapSize)]

    override fun toAscii(rawImage: Image, aaframe: AAFrame) {
        // draw scale-flagged (getScaledCopy) image to the buffer
        imageBuffer.graphics.drawImage(rawImage.getScaledCopy(w, h), 0f, 0f)

        // fill buffer
        for (y in 0..h - 1) {
            for (x in 0..w - 1) {
                if (ditherAlgo == 0) {
                    aaframe.drawBuffer(x, y, pickRandomGlyphByLumNoQnt(quantiseLum(bufferPixelFontLum(x, y))))
                }
                else {
                    ditherBuffer.set(x, y, bufferPixelFontLum(x, y))
                }
            }
        }

        // dither
        // ref: http://www.tannerhelland.com/4660/dithering-eleven-algorithms-source-code/
        if (ditherAlgo > 0) {
            for (y in 0..h - 1) {
                for (x in 0..w - 1) {
                    val oldPixel = ditherBuffer.get(x, y)
                    val newPixel = quantiseLum(oldPixel.toInt())

                    //println("$oldPixel, $newPixel")

                    ditherBuffer.set(x, y, newPixel)

                    val error = oldPixel - newPixel

                    // floyd-steinberg
                    if (ditherAlgo == FLOYD_STEINBERG) { //                           no ushr or else it won't work
                        ditherBuffer.set(x + 1, y    , ditherBuffer.get(x + 1, y    ) + (error.times(7).shr(4)))
                        ditherBuffer.set(x - 1, y + 1, ditherBuffer.get(x - 1, y + 1) + (error.times(3).shr(4)))
                        ditherBuffer.set(x    , y + 1, ditherBuffer.get(x    , y + 1) + (error.times(5).shr(4)))
                        ditherBuffer.set(x + 1, y + 1, ditherBuffer.get(x + 1, y + 1) + (error.times(1).shr(4)))
                    }
                    // sierra lite
                    else if (ditherAlgo == SIERRA_LITE) {
                        ditherBuffer.set(x + 1, y    , ditherBuffer.get(x + 1, y    ) + (error.times(2).shr(2)))
                        ditherBuffer.set(x - 1, y + 1, ditherBuffer.get(x - 1, y + 1) + (error.times(1).shr(2)))
                        ditherBuffer.set(x    , y + 1, ditherBuffer.get(x    , y + 1) + (error.times(1).shr(2)))
                    }
                    // sierra-2
                    else if (ditherAlgo == SIERRA_2) {
                        ditherBuffer.set(x + 1, y    , ditherBuffer.get(x + 1, y    ) + (error.times(4).shr(4)))
                        ditherBuffer.set(x + 2, y    , ditherBuffer.get(x + 2, y    ) + (error.times(3).shr(4)))
                        ditherBuffer.set(x - 2, y + 1, ditherBuffer.get(x - 2, y + 1) + (error.times(1).shr(4)))
                        ditherBuffer.set(x - 1, y + 1, ditherBuffer.get(x - 1, y + 1) + (error.times(2).shr(4)))
                        ditherBuffer.set(x    , y + 1, ditherBuffer.get(x    , y + 1) + (error.times(3).shr(4)))
                        ditherBuffer.set(x + 1, y + 1, ditherBuffer.get(x + 1, y + 1) + (error.times(2).shr(4)))
                        ditherBuffer.set(x + 2, y + 1, ditherBuffer.get(x + 2, y + 1) + (error.times(1).shr(4)))
                    }
                    // sierra-3
                    else if (ditherAlgo == SIERRA_3) {
                        ditherBuffer.set(x + 1, y    , ditherBuffer.get(x + 1, y    ) + (error.times(5).shr(5)))
                        ditherBuffer.set(x + 2, y    , ditherBuffer.get(x + 2, y    ) + (error.times(3).shr(5)))
                        ditherBuffer.set(x - 2, y + 1, ditherBuffer.get(x - 2, y + 1) + (error.times(2).shr(5)))
                        ditherBuffer.set(x - 1, y + 1, ditherBuffer.get(x - 1, y + 1) + (error.times(4).shr(5)))
                        ditherBuffer.set(x    , y + 1, ditherBuffer.get(x    , y + 1) + (error.times(5).shr(5)))
                        ditherBuffer.set(x + 1, y + 1, ditherBuffer.get(x + 1, y + 1) + (error.times(4).shr(5)))
                        ditherBuffer.set(x + 2, y + 1, ditherBuffer.get(x + 2, y + 1) + (error.times(2).shr(5)))
                        ditherBuffer.set(x - 1, y + 2, ditherBuffer.get(x - 1, y + 2) + (error.times(2).shr(5)))
                        ditherBuffer.set(x    , y + 2, ditherBuffer.get(x    , y + 2) + (error.times(3).shr(5)))
                        ditherBuffer.set(x + 1, y + 2, ditherBuffer.get(x + 1, y + 2) + (error.times(2).shr(5)))
                    }
                    else {
                        throw IllegalArgumentException("Unknown dithering algorithm: $ditherAlgo")
                    }
                }
            }

            // ...and draw
            for (y in 0..h - 1) {
                for (x in 0..w - 1) {
                    aaframe.drawBuffer(x, y, pickRandomGlyphByLumNoQnt(quantiseLum(ditherBuffer.get(x, y))))
                }
            }
        }

        // clear buffer
        imageBuffer.graphics.flush()
    }

    fun Array<IntArray>.set(x: Int, y: Int, value: Int) {
        if (x >= 0 && y >= 0 && x < w && y < h)
            this[y][x] = value
    }

    fun Array<IntArray>.get(x: Int, y: Int): Int {
        if (x >= 0 && y >= 0 && x < w && y < h)
            return this[y][x]
        else return 0
    }

    fun bufferPixelFontLum(x: Int, y: Int): Int {
        val lum = // [0.0 - 1.0]
                imageBuffer.getColor(x, y).getLuminance(colourAlgo, gamma)

        val delta = fontLumMax - fontLumMin
        return (fontLumMin + delta * lum).roundInt()
    }

    fun Double.toFontLum(): Int {
        val delta = fontLumMax - fontLumMin
        return (fontLumMin + delta * this).roundInt()
    }

    /**
     * @param fontLum : int ranged 0..fontLumMax
     * @return indexed lum 0..fontLumMax
     */
    private fun quantiseLum(fontLum: Int): Int {
        val interval = binarySearchInterval(fontLum)

        if (interval.first == interval.second)
            return lumMap[interval.first]
        else {
            // compare two and return closest
            if (fontLum - lumMap[interval.first] < lumMap[interval.second] - fontLum)
                return lumMap[interval.first]
            else
                return lumMap[interval.second]
        }
    }

    fun Int.prevLum() = lumMap[binarySearchInterval(this).first]

    fun Int.nextLum() = lumMap[binarySearchInterval(this).second]

    /**
     * e.g.
     *
     * 0 1 4 5 7 , find 3
     *
     * will return (1, 2), which corresponds value (1, 4) of which input value 3 is in between.
     */
    private fun binarySearchInterval(lum: Int): Pair<Int, Int> {
        var low: Int = 0
        var high: Int = lumMap.size - 1

        while (low <= high) {
            val mid = (low + high).ushr(1)
            val midVal = lumMap[mid]

            if (lum < midVal)
                high = mid - 1
            else if (lum > midVal)
                low = mid + 1
            else
                return Pair(mid, mid)
        }

        return Pair(Math.max(high, 0), Math.min(low, lumMap.size - 1))
    }

    private fun pickRandomGlyphByLumNoQnt(fontLum: Int): Char {
        if ((fontLum == 0 && !inverted) || (fontLum == fontLumMax && inverted))
            return 0x20.toChar() // ' ' with colour index 'zero'

        val indexStart = sameLumStartIndices[fontLum]!!
        val indexEnd   = sameLumEndIndices[fontLum]!!

        val index = Random().nextInt(indexEnd - indexStart + 1) + indexStart
        return brightnessMap[index].second
    }

    fun Char.getColourKey() = this.toInt().ushr(8).and(0x1F)
    fun Char.getAscii() = this.toInt().and(0xFF)
    fun Double.oneTo256() = this.times(255).roundInt()
}




fun Double.ceilInt() = Math.ceil(this).toInt()
fun Double.floorInt() = Math.floor(this).toInt()
fun Double.roundInt() = Math.round(this).toInt()
fun Float.sqr() = this * this
fun Double.sqrt() = Math.sqrt(this)
fun Int.sqrt() = Math.sqrt(this.toDouble())
fun Int.abs() = Math.abs(this)
fun Double.powerOf(exp: Double) = Math.pow(this, exp)
fun Float.powerOf(exp: Double) = Math.pow(this.toDouble(), exp)
fun Int.sqr() = this * this
infix fun Int.min(other: Int) = Math.min(this, other)
infix fun Int.max(other: Int) = Math.max(this, other)
fun Color.getLuminance(colourAlgo: Int, gamma: Double): Double =
        if (redByte > 0xF8 && greenByte >= 0xF8 && blueByte >= 0xF8) // mask irregularity in white colour
            1.0
        else if (redByte < 0x8 && greenByte < 0x8 && blueByte < 0x8)
            0.0
        else
            if (colourAlgo == 1)
                (0.299 * r.sqr() + 0.587 * g.sqr() + 0.114 * b.sqr()).powerOf(0.5 * gamma)
            else if (colourAlgo == 0)
                Math.pow((3 * r + b + 4 * g) / 8.0, gamma)
            else
                throw IllegalArgumentException("Unknown luminance algorithm: $colourAlgo")

fun getLuminance(redByte: Int, greenByte: Int, blueByte: Int, colourAlgo: Int, gamma: Double): Double {
    val r = redByte / 255f
    val g = greenByte / 255f
    val b = blueByte / 255f

    return if (redByte > 0xF8 && greenByte >= 0xF8 && blueByte >= 0xF8) // mask irregularity in white colour
        1.0
    else if (redByte < 0x8 && greenByte < 0x8 && blueByte < 0x8)
        0.0
    else
        if (colourAlgo == 1)
            (0.299 * r.sqr() + 0.587 * g.sqr() + 0.114 * b.sqr()).powerOf(0.5 * gamma)
        else if (colourAlgo == 0)
            Math.pow((3 * r + b + 4 * g) / 8.0, gamma)
        else
            throw IllegalArgumentException("Unknown luminance algorithm: $colourAlgo")
}