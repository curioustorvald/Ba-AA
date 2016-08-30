package net.torvald.aa

import net.torvald.aa.demoplayer.BaAA
import org.newdawn.slick.*
import java.util.*

/**
 * How it works:
 *
 * Divide the single glyph into 2 by 2, pre-calculate the luminosity of each cell and try to find closest matching
 * one from the 2x2 pixels in input image.
 *
 *   Pre-calculation:
 *
 * Scan each glyph on the spritesheet provided, divide the single glyph into 2x2 and calculate the luminosity.
 *
 * Created by minjaesong on 16-08-10.
 */
class ImageToAASubGlyph4 : AsciiAlgo {

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
            width: Int, height: Int,
            font: SpriteSheet, fontW: Int, fontH: Int,
            inverted: Boolean, gamma: Double, dither: Int, fullCodePage: Boolean, colourAlgo: Int) {
        w = width * 2
        h = height * 2
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
            if (!inverted)
                BaAA.colcga.forEachIndexed { i, c -> colourMap[i] = Color(c, c, c).getLuminance(colourAlgo, gamma) }
            else
                BaAA.colcgaInv.forEachIndexed { i, c -> colourMap[i] = Color(c, c, c).getLuminance(colourAlgo, gamma) }
        }
        else if (BaAA.colors.size == BaAA.RANGE_EXT) {
            if (!inverted)
                BaAA.hexadecaGrey.forEachIndexed { i, c -> colourMap[i] = Color(c, c, c).getLuminance(colourAlgo, gamma) }
            else
                BaAA.hexadecaGreyInv.forEachIndexed { i, c -> colourMap[i] = Color(c, c, c).getLuminance(colourAlgo, gamma) }
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

    private val brightnessMap = ArrayList<Pair<Long, Char>>() // Long: stored TL-TR-BL-BR
    private val glyphColMap = HashMap<Char, Long>()
    private val sameLumStartIndices = HashMap<Long, Int>()
    private val sameLumEndIndices = HashMap<Long, Int>()
    private val lumMap = ArrayList<Long>() // Long: stored TL-TR-BL-BR; stores unique elems, regardless of position
    private val lumMapAll = ArrayList<Int>() // each element of Luminosity, unique values regardless of position
    private val lumSortByDim = Array<ArrayList<Long>>(4, { ArrayList() }) // [position][luma-glyph pair], pos0: top-left, stores combined lum (aka glyph)
    private val lumSortByDimElem = Array<ArrayList<Int>>(4, { ArrayList() }) // [position][luma-glyph pair], pos0: top-left, stores lum for each dimension

    private lateinit var imageBuffer: Image

    private var precalcDone = false

    private var lumMin = 0x7FFFFFFF
    private var lumMax = 0

    private val exclude = arrayOf(
            '_'.getAscii(),
            '-'.getAscii(),
            '|'.getAscii(),
            8, 9, 10
    )

    /**
     * (' ' - '\') * number of colours
     */
    private var totalGlyphCount = 0

    override fun precalcFont() {
        if (!precalcDone) {

            val fontSheetW = fontSheet.width / fontW

            for (colourKey in 0..colourMap.size - 1) {
                for (i in fontRange) {
                    if (exclude.contains(i)) continue

                    val glyph = fontSheet.getSubImage(i % fontSheetW, i / fontSheetW).copy()
                    totalGlyphCount++

                    var glyphLumTopLeft = 0
                    var glyphLumTopRight = 0
                    var glyphLumBottomLeft = 0
                    var glyphLumBottomRight = 0

                    if ((i == 0 || i == 32) && colourKey == 0) { // exclude ' ' if in ASCII mode
                        glyphLumTopLeft = if (!inverted) 0 else glyph.height * glyph.width * colourMap[0].oneTo256()

                        glyphLumTopRight = glyphLumTopLeft
                        glyphLumBottomLeft = glyphLumTopLeft
                        glyphLumBottomRight = glyphLumTopLeft
                    }
                    else if (colourKey >= 1 && i != 0 && i != 32) {
                        // TODO more weighting on clustered pixels

                        // top-left part
                        for (fx in 0..glyph.width / 2 - 1) {
                            for (fy in 0..glyph.height / 2 - 1) {
                                val pixel = glyph.getColor(fx, fy).a
                                val b: Int
                                if (pixel == 0f)
                                    b = colourMap[0].oneTo256()
                                else {
                                    b = colourMap[colourKey].oneTo256()
                                }

                                glyphLumTopLeft += b
                            }
                        }
                        // bottom-left part
                        for (fx in 0..glyph.width / 2 - 1) {
                            for (fy in glyph.height / 2..glyph.height - 1) {
                                val pixel = glyph.getColor(fx, fy).a
                                val b: Int
                                if (pixel == 0f)
                                    b = colourMap[0].oneTo256()
                                else {
                                    b = colourMap[colourKey].oneTo256()
                                }

                                glyphLumBottomLeft += b
                            }
                        }
                        // top-right part
                        for (fx in glyph.width / 2..glyph.width - 1) {
                            for (fy in 0..glyph.height / 2 - 1) {
                                val pixel = glyph.getColor(fx, fy).a
                                val b: Int
                                if (pixel == 0f)
                                    b = colourMap[0].oneTo256()
                                else {
                                    b = colourMap[colourKey].oneTo256()
                                }

                                glyphLumTopRight += b
                            }
                        }
                        // bottom-right part
                        for (fx in glyph.width / 2..glyph.width - 1) {
                            for (fy in glyph.height / 2..glyph.height - 1) {
                                val pixel = glyph.getColor(fx, fy).a
                                val b: Int
                                if (pixel == 0f)
                                    b = colourMap[0].oneTo256()
                                else {
                                    b = colourMap[colourKey].oneTo256()
                                }

                                glyphLumBottomRight += b
                            }
                        }
                    }

                    if ((colourKey == 0 && (i == 0 || i == 32)) || (colourKey >= 1 && i != 0 && i != 32)) {
                        val luminosityPacked: Long =
                                glyphLumTopLeft.toLong().shl(48) or
                                        glyphLumTopRight.toLong().shl(32) or
                                        glyphLumBottomLeft.toLong().shl(16) or
                                        glyphLumBottomRight.toLong()

                        brightnessMap.add(
                                Pair(
                                        luminosityPacked,
                                        (colourKey.shl(8) or i.and(0xFF)).toChar()
                                ))
                        glyphColMap.put(
                                (colourKey.shl(8) or i.and(0xFF)).toChar(),
                                luminosityPacked
                        )
                    }
                }
            }

            brightnessMap.sortBy { it.first } // will be sorted by TL then TR then BL then BR

            // fill lumMapX(top)/Y(bottom)
            brightnessMap.forEach {
                if (!lumMap.contains(it.first)) lumMap.add(it.first)

                if (!lumMapAll.contains(it.first.getTopLeft())) lumMapAll.add(it.first.getTopLeft())
                if (!lumMapAll.contains(it.first.getTopRight())) lumMapAll.add(it.first.getTopRight())
                if (!lumMapAll.contains(it.first.getBottomLeft())) lumMapAll.add(it.first.getBottomLeft())
                if (!lumMapAll.contains(it.first.getBottomRight())) lumMapAll.add(it.first.getBottomRight())
            }

            for (j in 0..lumSortByDim.size - 1) {
                for (i in 0..brightnessMap.size - 1) {
                    lumSortByDim[j].add(brightnessMap[i].first)
                    lumSortByDimElem[j].add(brightnessMap[i].first.ushr(16 * (3 - j)).and(0xFFFF).toInt())
                }
            }

            // sort everything required
            for (dim in 0..3) {
                lumSortByDim[dim].sortBy { it.ushr(16 * (3 - dim)).and(0xFFFF) }
                lumSortByDimElem[dim].sort()
            }

            lumMapAll.sort()
            lumMap.sort()

            // get min/max
            lumMin = lumMapAll.first()
            lumMax = lumMapAll.last()

            brightnessMap.forEachIndexed { index, it ->
                println("Brightness " +
                                "${it.first.getTopLeft()}+${it.first.getTopRight()}+" +
                                "${it.first.getBottomLeft()}+${it.first.getBottomRight()}\t" +
                        "glyph ${it.second.getAscii().toChar()} " +
                        "col ${it.second.getColourKey()}" +
                        " [$index]")
            }

            println("Min: $lumMin, Max: $lumMax")

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
                }
            }

            precalcDone = true
        }
    }

    private fun Long.getTopLeft() = this.ushr(48).toInt() and 0xFFFF
    private fun Long.getTopRight() = this.ushr(32).toInt() and 0xFFFF
    private fun Long.getBottomLeft() = this.ushr(16).toInt() and 0xFFFF
    private fun Long.getBottomRight() = this.toInt() and 0xFFFF

    lateinit var ditherBuffer: Array<IntArray>

    val FLOYD_STEINBERG = 1
    val SIERRA_LITE = 2
    val SIERRA_2 = 3

    private val ditherHack = 2

    override fun toAscii(rawImage: Image, aaframe: AAFrame) {
        // draw scale-flagged (getScaledCopy) image to the buffer
        imageBuffer.graphics.drawImage(rawImage.getScaledCopy(w, h), 0f, 0f)

        // fill buffer (scan size: W*H of aaframe)
        for (y in 0..h - 1 step 2) {
            for (x in 0..w - 1 step 2) {
                if (ditherAlgo == 0) {
                    val lumTL = bufferPixelFontLum(x, y)
                    val lumTR = bufferPixelFontLum(x + 1, y)
                    val lumBL = bufferPixelFontLum(x, y + 1)
                    val lumBR = bufferPixelFontLum(x + 1, y + 1)
                    val qnt = findNearestLum(lumTL, lumTR, lumBL, lumBR)
                    val char = pickRandomGlyphByLumNoQnt(qnt)

                    aaframe.drawBuffer(x.shr(1), y.shr(1), char)
                }
                else {
                    ditherBuffer.set(x    , y    , bufferPixelFontLum(x    , y    ))
                    ditherBuffer.set(x    , y + 1, bufferPixelFontLum(x    , y + 1))
                    ditherBuffer.set(x + 1, y    , bufferPixelFontLum(x + 1, y    ))
                    ditherBuffer.set(x + 1, y + 1, bufferPixelFontLum(x + 1, y + 1))
                }
            }
        }

        // dither
        // ref: http://www.tannerhelland.com/4660/dithering-eleven-algorithms-source-code/
        if (ditherAlgo > 0) {
            // scan for ditherBuffer that is strecthed to Y
            for (y in 0..h - 1 step ditherHack) {
                for (x in 0..w - 1 step ditherHack) {
                    val oldPixel = ditherBuffer.get(x, y)
                    val newPixel = findNearest(oldPixel)

                    ditherBuffer.set(x, y, newPixel)

                    val error = oldPixel - newPixel

                    // dither glyph-wise rather than pixel-wise
                    for (haxX in 0..ditherHack - 1) {
                        for (haxY in 0..ditherHack - 1) {
                            // floyd-steinberg
                            if (ditherAlgo == FLOYD_STEINBERG) {
                                ditherBuffer.set(x + 1 + haxX, y     + haxY, ditherBuffer.get(x + 1 + haxX, y     + haxY) + (error.times(7).shr(4)))
                                ditherBuffer.set(x - 1 + haxX, y + 1 + haxY, ditherBuffer.get(x - 1 + haxX, y + 1 + haxY) + (error.times(3).shr(4)))
                                ditherBuffer.set(x     + haxX, y + 1 + haxY, ditherBuffer.get(x     + haxX, y + 1 + haxY) + (error.times(5).shr(4)))
                                ditherBuffer.set(x + 1 + haxX, y + 1 + haxY, ditherBuffer.get(x + 1 + haxX, y + 1 + haxY) + (error.times(1).shr(4)))
                            }
                            // sierra lite
                            else if (ditherAlgo == SIERRA_LITE) {
                                ditherBuffer.set(x + 1 + haxX, y     + haxY, ditherBuffer.get(x + 1 + haxX, y     + haxY) + (error.times(2).shr(2)))
                                ditherBuffer.set(x - 1 + haxX, y + 1 + haxY, ditherBuffer.get(x - 1 + haxX, y + 1 + haxY) + (error.times(1).shr(2)))
                                ditherBuffer.set(x     + haxX, y + 1 + haxY, ditherBuffer.get(x     + haxX, y + 1 + haxY) + (error.times(1).shr(2)))
                            }
                            // sierra-2
                            else if (ditherAlgo == SIERRA_2) {
                                ditherBuffer.set(x + 1 + haxX, y     + haxY, ditherBuffer.get(x + 1 + haxX, y     + haxY) + (error.times(4).shr(4)))
                                ditherBuffer.set(x + 2 + haxX, y     + haxY, ditherBuffer.get(x + 2 + haxX, y     + haxY) + (error.times(3).shr(4)))
                                ditherBuffer.set(x - 2 + haxX, y + 1 + haxY, ditherBuffer.get(x - 2 + haxX, y + 1 + haxY) + (error.times(1).shr(4)))
                                ditherBuffer.set(x - 1 + haxX, y + 1 + haxY, ditherBuffer.get(x - 1 + haxX, y + 1 + haxY) + (error.times(2).shr(4)))
                                ditherBuffer.set(x     + haxX, y + 1 + haxY, ditherBuffer.get(x     + haxX, y + 1 + haxY) + (error.times(3).shr(4)))
                                ditherBuffer.set(x + 1 + haxX, y + 1 + haxY, ditherBuffer.get(x + 1 + haxX, y + 1 + haxY) + (error.times(2).shr(4)))
                                ditherBuffer.set(x + 2 + haxX, y + 1 + haxY, ditherBuffer.get(x + 2 + haxX, y + 1 + haxY) + (error.times(1).shr(4)))
                            }
                            else {
                                throw IllegalArgumentException("Unknown dithering algorithm: $ditherAlgo")
                            }
                        }
                    }
                }
            }

            // ...and draw
            for (y in 0..h - 1 step 2) {
                for (x in 0..w - 1 step 2) {
                    val lumTL = ditherBuffer.get(x, y)
                    val lumTR = ditherBuffer.get(x + 1, y)
                    val lumBL = ditherBuffer.get(x, y + 1)
                    val lumBR = ditherBuffer.get(x + 1, y + 1)
                    val char = pickRandomGlyphByLumNoQnt(findNearestLum(lumTL, lumTR, lumBL, lumBR))

                    aaframe.drawBuffer(x.shr(1), y.shr(1), char)
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

        val delta = lumMax - lumMin
        return (lumMin + delta * lum).roundInt()
    }

    /**
     * @param fontLum : int ranged 0..fontLumMax
     * @return indexed lum 0..fontLumMax
     */
    private fun findNearestLum(lumTopLeft: Int, lumTopRight: Int, lumBottomLeft: Int, lumBottomRight: Int): Long {
        // find closest: "Closest pair of points problem"
        // TODO better algorithm
        // brute force
        // for some reason, lambda expression is actually slower
        var distMin = 0x7FFFFFFF
        var lum = 0L
        var dist: Int = 0
        var otherLum: Long

        val lumPacked = lumTopLeft.and(0xFFFF).shl(48) +
                lumTopRight.and(0xFFFF).shl(32) +
                lumBottomLeft.and(0xFFFF).shl(16) +
                lumBottomRight.and(0xFFFF)

        //println("Lum: $lumTopLeft+$lumTopRight+$lumBottomLeft+$lumBottomRight")

        //println("$argMin, $argMax")

        for (cnt in 0..lumMap.size - 1) {
            otherLum = lumMap[cnt]
            dist = // euclidean norm on 2D, squared
                    (lumTopLeft     - otherLum.getTopLeft()    ) * (lumTopLeft     - otherLum.getTopLeft()    ) +
                    (lumTopRight    - otherLum.getTopRight()   ) * (lumTopRight    - otherLum.getTopRight()   ) +
                    (lumBottomLeft  - otherLum.getBottomLeft() ) * (lumBottomLeft  - otherLum.getBottomLeft() ) +
                    (lumBottomRight - otherLum.getBottomRight()) * (lumBottomRight - otherLum.getBottomRight())

            //println("dist: $dist")

            if (dist < distMin) {
                distMin = dist
                lum = otherLum
            }
        }

        return lum
    }

    private val POS_TL = 0
    private val POS_TR = 1
    private val POS_BL = 2
    private val POS_BR = 3

    private fun findNearest(lum: Int): Int {
        val interval = binarySearchInterval(lumMapAll, lum)

        if (interval.first == interval.second)
            return lumMapAll[interval.first]
        else {
            // compare two and return closest
            if (lum - lumMapAll[interval.first] < lumMapAll[interval.second] - lum)
                return lumMapAll[interval.first]
            else
                return lumMapAll[interval.second]
        }
    }

    private fun findNearest(sortedList: List<Int>, element: Int): Int {
        val interval = binarySearchInterval(sortedList, element)

        if (interval.first == interval.second)
            return sortedList[interval.first]
        else {
            // compare two and return closest
            if (element - sortedList[interval.first] < sortedList[interval.second] - element)
                return sortedList[interval.first]
            else
                return sortedList[interval.second]
        }
    }

    /**
     * e.g.
     *
     * 0 1 4 5 7 , find 3
     *
     * will return (1, 2), which corresponds value (1, 4) of which input value 3 is in between.
     * @return pair of index
     */
    private fun binarySearchInterval(list: List<Int>, lum: Int): Pair<Int, Int> {
        var low: Int = 0
        var high: Int = list.size - 1

        while (low <= high) {
            val mid = (low + high).ushr(1)
            val midVal = list[mid]

            if (lum < midVal)
                high = mid - 1
            else if (lum > midVal)
                low = mid + 1
            else
                return Pair(mid, mid)
        }

        return Pair(Math.max(high, 0), Math.min(low, list.size - 1))
    }

    private fun pickRandomGlyphByLumNoQnt(fontLum: Long): Char {
        if (fontLum == 0L && !inverted)
            return 0x20.toChar() // ' ' with colour index 'zero'

        val indexStart = sameLumStartIndices[fontLum]
        val indexEnd   = sameLumEndIndices[fontLum]

        if (indexStart == null && indexEnd == null)
            System.err.println("kotlin.KotlinNullPointerException: indexStart and indexEnd is null.")
        else if (indexStart == null)
            System.err.println("kotlin.KotlinNullPointerException: indexStart is null.")
        else if (indexEnd == null)
            System.err.println("kotlin.KotlinNullPointerException: indexEnd is null.")

        if (indexStart == null || indexEnd == null) {
            System.err.println("    fontLum: ${fontLum.getTopLeft()}+${fontLum.getTopRight()}+${fontLum.getBottomLeft()}+${fontLum.getBottomRight()}")
        }

        if (indexStart != null && indexEnd != null) {
            val index = Random().nextInt(indexEnd - indexStart + 1) + indexStart
            return brightnessMap[index].second
        }

        throw NullPointerException()
    }
    fun Char.getColourKey() = this.toInt().ushr(8).and(0x1F)
    fun Char.getAscii() = this.toInt().and(0xFF)
    fun Double.oneTo256() = this.times(255).roundInt()
    fun Long.toShorts() = arrayOf(this.getTopLeft(), this.getTopRight(), this.getBottomLeft(), this.getBottomRight())
}

fun ensureBoundary(size: Int, index: Int) = if (index >= size) size - 1 else if (index < 0) 0 else index
