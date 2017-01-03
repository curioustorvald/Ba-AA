package net.torvald.aa

import net.torvald.aa.demoplayer.BaAA
import org.newdawn.slick.*
import java.util.*

/**
 * Generalised version of ImageToAASubGlyph4
 * @param divW horizontal division
 * @param divH vertical division
 *
 *
 *
 * Note: weird enough, if divW >= 4 || divH >= 4, it has tendency of pixelating picture instead of proper antialias.
 *
 *
 * Created by minjaesong on 16-08-10.
 */
class ImageToAASubGlyphArb(val divW: Int, val divH: Int) : AsciiAlgo {

    private var w = 0
    private var h = 0
    private lateinit var fontSheet: SpriteSheet
    override var fontW = 0
    override var fontH = 0
    private var inverted = false
    private var gamma = 0.0
    private var ditherAlgo = 0
    private var colourAlgo = 0

    private val divSize = divW * divH

    private lateinit var fontRange: IntRange

    private lateinit var colourMap: Array<Double>

    fun setProp(
            width: Int, height: Int,
            font: SpriteSheet, fontW: Int, fontH: Int,
            inverted: Boolean, gamma: Double, dither: Int, fullCodePage: Boolean, colourAlgo: Int) {
        w = width * divW
        h = height * divH
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

    private val brightnessMap = ArrayList<Pair<Luminosity, Char>>()
    private val glyphColMap = HashMap<Char, Luminosity>()
    private val sameLumStartIndices = HashMap<Luminosity, Int>()
    private val sameLumEndIndices = HashMap<Luminosity, Int>()
    private val lumMapMatrix = Array<ArrayList<Int>>(divSize, { ArrayList() }) // stores unique elems, by position lumMapMatrix[position][index]
    private val lumMap = ArrayList<Luminosity>() // stores unique elems, regardless of position
    private val lumMapAll = ArrayList<Int>() // each element of Luminosity, unique values regardless of position

    private lateinit var brightnessKDTree: KDHeapifiedTree

    private lateinit var imageBuffer: Image

    private var precalcDone = false

    private var lumMin = 0x7FFFFFFF
    private var lumMax = 0

    private val exclude = arrayOf(
            '|'.getAscii(),
            '_'.getAscii(),
            8, 9, 10
    )

    /**
     * (' ' - '\') * number of colours
     */
    private var totalGlyphCount = 0

    override fun precalcFont() {
        if (!precalcDone) {

            val fontSheetW = fontSheet.width / fontW
            val rangesX = Array(divW, {
                getStartAndEndInclusivePoints(fontW, divW, it) })
            val rangesY = Array(divH, {
                getStartAndEndInclusivePoints(fontH, divH, it) })


            rangesX.forEach { println("range x: $it") }
            rangesY.forEach { println("range y: $it") }

            for (colourKey in 0..colourMap.size - 1) {
                for (i in fontRange) {
                    if (exclude.contains(i)) continue

                    val glyph = fontSheet.getSubImage(i % fontSheetW, i / fontSheetW).copy()
                    totalGlyphCount++

                    var lumCalcBuffer = Luminosity(divSize, { 0 })

                    if ((i == 0 || i == 32) && colourKey == 0) { // exclude ' ' if in ASCII mode
                        if (!inverted)
                            lumCalcBuffer = Luminosity(divSize, { 0 })
                        else
                            lumCalcBuffer = Luminosity(divSize, { glyph.height * glyph.width * colourMap[0].oneTo256() })
                    }
                    else if (colourKey >= 1 && i != 0 && i != 32) {
                        for (area in 0..divSize - 1) {
                            for (fx in rangesX[area % divW]) {
                                for (fy in rangesY[area / divW]) {
                                    val pixel = glyph.getColor(fx, fy).a
                                    val b: Int
                                    if (pixel == 0f)
                                        b = colourMap[0].oneTo256()
                                    else {
                                        b = colourMap[colourKey].oneTo256()
                                    }

                                    lumCalcBuffer[area] += b
                                }
                            }
                        }
                    }

                    if ((colourKey == 0 && (i == 0 || i == 32)) || (colourKey >= 1 && i != 0 && i != 32)) {
                        brightnessMap.add(
                                Pair(
                                        lumCalcBuffer,
                                        (colourKey.shl(8) or i.and(0xFF)).toChar()
                                ))
                        glyphColMap.put(
                                (colourKey.shl(8) or i.and(0xFF)).toChar(),
                                lumCalcBuffer
                        )
                    }
                }
            }

            brightnessMap.sortBy { it.first } // will be sorted by TL then TR then BL then BR

            // fill lumMapX(top)/Y(bottom)
            brightnessMap.forEach {
                if (!lumMap.contains(it.first)) lumMap.add(it.first)

                it.first.forEachIndexed { i, value ->
                    if (!lumMapMatrix[i].contains(value)) lumMapMatrix[i].add(value)
                }

                it.first.forEach { if (!lumMapAll.contains(it)) lumMapAll.add(it) }
            }

            // sort everything required
            lumMapMatrix.forEach { it.sort() }
            lumMapAll.sort()
            lumMap.sort()

            // get min/max
            lumMin = lumMapAll.first()
            lumMax = lumMapAll.last()

            brightnessMap.forEachIndexed { index, it ->
                println("${it.first}\tglyph ${it.second.getAscii().toChar()}" +
                        " col ${it.second.getColourKey()}" +
                        " [$index]")
            }
            //lumMap.forEach { println(it) }

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

            // build k-d tree
            brightnessKDTree = KDHeapifiedTree(brightnessMap, divSize, BaAA.noApproximate, lumMax)

            //sameLumStartIndices.forEach { luminosity, i -> println("$luminosity, starts from $i") }
            //sameLumEndIndices.forEach { luminosity, i -> println("$luminosity, ends at $i") }

            precalcDone = true
        }
    }

    lateinit var ditherBuffer: Array<IntArray>

    val FLOYD_STEINBERG = 1
    val SIERRA_LITE = 2
    val SIERRA_2 = 3

    override fun toAscii(rawImage: Image, aaframe: AAFrame) {
        // draw scale-flagged (getScaledCopy) image to the buffer
        imageBuffer.graphics.drawImage(rawImage.getScaledCopy(w, h), 0f, 0f)

        // fill buffer (scan size: W*H of aaframe)
        for (y in 0..h - 1 step divH) {
            for (x in 0..w - 1 step divW) {
                if (ditherAlgo == 0) {
                    val lum = Luminosity(divSize, { bufferPixelFontLum(x + it % divW, y + it / divW) })
                    val qntLum = findNearestLum(lum)
                    //val qntLum = brightnessMap.last().first
                    val char = pickRandomGlyphByLumNoQnt(qntLum)

                    aaframe.drawBuffer(x / divW, y / divH, char)
                }
                else {
                    for (ySub in 0..divH - 1)
                        for (xSub in 0..divW - 1)
                            ditherBuffer.set(x + xSub, y + ySub, bufferPixelFontLum(x + xSub, y + ySub))
                }
            }
        }

        // dither
        // ref: http://www.tannerhelland.com/4660/dithering-eleven-algorithms-source-code/
        if (ditherAlgo > 0) {
            // scan for ditherBuffer that is strecthed to Y
            for (y in 0..h - 1 step divH) {
                for (x in 0..w - 1 step divW) {
                    val oldPixel = ditherBuffer.get(x, y)
                    val newPixel = findNearest(oldPixel)

                    ditherBuffer.set(x, y, newPixel)

                    val error = oldPixel - newPixel

                    // dither glyph-wise rather than pixel-wise
                    for (haxX in 0..divW - 1) {
                        for (haxY in 0..divH - 1) {
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
            for (y in 0..h - 1 step divH) {
                for (x in 0..w - 1 step divW) {
                    val lum = Luminosity(divSize, { bufferPixelFontLum(x + it % divW, y + it / divW) })
                    val char = pickRandomGlyphByLumNoQnt(findNearestLum(lum))

                    aaframe.drawBuffer(x / divW, y / divH, char)
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
    private fun findNearestLum(inputLum: Luminosity): Luminosity {
        // find closest: "Closest pair of points problem"
        // linear search
        /*var distMin = 0x7FFFFFFF
        var lum = Luminosity(divSize, { 0 }) // initial lum
        var dist: Int
        var otherLum: Luminosity
        for (i in 0..lumMap.size - 1) {
            otherLum = lumMap[i]
            dist = otherLum.distSqr(inputLum)
            if (dist < distMin) {
                distMin = dist
                lum = otherLum // redefine lum to currently nearest
            }
        }

        return lum*/

        // KD tree
        return brightnessKDTree.findNearest(inputLum)
    }

    private val zeroLum = Luminosity(divSize, { 0 })

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

    private fun pickRandomGlyphByLumNoQnt(fontLum: Luminosity): Char {
        if (fontLum.isZero() && !inverted)
            return 0x20.toChar() // ' ' with colour index 'zero'

        //println("Errenous call of $fontLum")

        val indexStart = sameLumStartIndices[fontLum]!!
        val indexEnd   = sameLumEndIndices[fontLum]!!

        val index = Random().nextInt(indexEnd - indexStart + 1) + indexStart
        return brightnessMap[index].second
    }

    fun Char.getColourKey() = this.toInt().ushr(8).and(0x1F)
    fun Char.getAscii() = this.toInt().and(0xFF)
    fun Double.oneTo256() = this.times(255).roundInt()
    fun getStartAndEndInclusivePoints(divideFrom: Int, divideBy: Int, i: Int) = (
            ((divideFrom.toDouble() / divideBy) * i).roundInt()
                    ..
                    ((divideFrom.toDouble() / divideBy) * i.plus(1)).roundInt() - 1
            )
}

class Luminosity(val size: Int, init: (Int) -> Int): Comparable<Luminosity> {
    private var luminosity: IntArray = IntArray(size, init)// = lum.toTypedArray()
    //constructor(size: Int, init: (Int) -> Int): this() { luminosity = Array<Int>(size, init) }
    operator fun get(i: Int) = luminosity[i]
    operator fun set(x: Int, value: Int) { luminosity[x] = value }
    fun contains(element: Int) = luminosity.contains(element)
    fun forEach(action: (Int) -> Unit) = luminosity.forEach(action)
    fun forEachIndexed(action: (Int, Int) -> Unit) = luminosity.forEachIndexed(action)
    override fun compareTo(other: Luminosity): Int {
        luminosity.forEachIndexed { i, lum -> if (lum != other[i]) return lum - other[i] }
        return 0 // this and the other is equal
    }
    override fun equals(other: Any?) = this.compareTo(other as Luminosity) == 0
    fun isZero(): Boolean {
        luminosity.forEach { if (it != 0) return false }
        return true
    }
    override fun toString(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append("Luma ")
        this.forEachIndexed { i, value ->
            if (i > 0) stringBuilder.append("+$value")
            else       stringBuilder.append("$value")
        }
        return stringBuilder.toString()
    }

    // from http://stackoverflow.com/questions/2351087/what-is-the-best-32bit-hash-function-for-short-strings-tag-names
    override fun hashCode(): Int {
        var h = 0
        for (elem in luminosity)
            h = 37 * h + elem
        return h
    }
    /**
     * (euclidean norm on 2D) ^ 2
     */
    fun distSqr(other: Luminosity): Int {
        var dist = 0
        for (i in 0..luminosity.size - 1)
            dist += (luminosity[i] - other.luminosity[i]) * (luminosity[i] - other.luminosity[i])
        return dist
    }

}
