package net.torvald.aa

import java.util.*

/**
 * k-d Tree that uses binary heap instead of binary tree to improve data locality
 *
 *
 * -- I couldn't observe any significant boost in performance but this one seems
 *    to give 3-4 more frames per second.
 *
 * Created by SKYHi14 on 2017-01-02.
 *
 *
 * Remarks:
 * - NOT using the fullCodePage with 2x2 mode makes it slower... skewed tree generation?
 */
class KDHeapifiedTree(
        points: List<Pair<Luminosity, Char>>, val dimension: Int, val noApproximate: Boolean,
        maxPossibleLum: Int, customMaxDepth: Int?) {

    private val nodes = Array<Luminosity?>(points.size * 4, { null })

    private val root: Int = 0

    fun findNearest(query: Luminosity) = // (mostly) Bad Apple-specific hacks
            if (query.distSqr(zeroLum) < roundToBlackDist && !noApproximate)
                zeroLum
            else if (query.distSqr(realWhiteLum) < roundToWhiteDist && !noApproximate)
                realWhiteLum
            else
                getNearest(root, query, 0, maxSearchDepth).get()!!

    private fun Int.get() = nodes[this]
    private fun Int.getLeft() = this * 2 + 1
    private fun Int.getRight() = this * 2 + 2
    private fun Int.set(value: Luminosity?) { nodes[this] = value }
    private fun Int.setLeftChild(value: Luminosity?) { nodes[this.getLeft()] = value }
    private fun Int.setRightChild(value: Luminosity?) { nodes[this.getRight()] = value }

    /** used to approximate the solution (doesn't go deeper than 8 -- 9 steps) */
    private val maxSearchDepth = customMaxDepth ?:
            if (noApproximate) intLog2(points.size)
            else               Math.round(intLog2(points.size).times(0.75)).toInt()
    private val roundToBlackDist = 24
    private val roundToWhiteDist = 16
    private val zeroLum = Luminosity(dimension, { 0 })
    private val realWhiteLum: Luminosity

    init {
        println("Approximated calculation: ${if (customMaxDepth != null) "no idea" else !noApproximate}")
        println("Max recursion depth: $maxSearchDepth")
        create(points, 0, 0)
        realWhiteLum = getNearest(root, Luminosity(dimension, { maxPossibleLum }), 0, intLog2(points.size)).get()!!
    }

    private fun create(points: List<Pair<Luminosity, Char>>, depth: Int, index: Int): Luminosity? {
        if (points.isEmpty()) {
            index.set(null)

            return null
        }
        else {
            val items = points.sortedBy { it.first[depth % dimension] }
            val halfItems = items.size shr 1

            index.setLeftChild(create(items.subList(0, halfItems), depth + 1, index.getLeft()))
            index.setRightChild(create(items.subList(halfItems + 1, items.size), depth + 1, index.getRight()))
            index.set(items[halfItems].first)

            return index.get()
        }
    }

    private fun getNearest(currentNode: Int, query: Luminosity, depth: Int, maxDepth: Int): Int {
        //println("depth, $depth")

        val direction = currentNode.compare(query, depth % dimension)

        val next  = if (direction < 0) currentNode.getLeft()  else currentNode.getRight()
        val other = if (direction < 0) currentNode.getRight() else currentNode.getLeft()
        var best  = if (next.get() == null || (depth >= maxDepth))
            currentNode
        else
            getNearest(next, query, depth + 1, maxDepth) // traverse to leaf

        if (currentNode.get()!!.distSqr(query) < best.get()!!.distSqr(query)) {
            best = currentNode
        }

        if (other.get() != null) {
            if (currentNode.get()!!.dimDistSqr(query, depth % dimension) < best.get()!!.distSqr(query) &&
                    (depth < maxDepth)) {
                val bestCandidate = getNearest(other, query, depth + 1, maxDepth)
                if (bestCandidate.get()!!.distSqr(query) < best.get()!!.distSqr(query)) {
                    best = bestCandidate
                }
            }
        }

        return best // work back up
    }

    fun Int.compare(other: Luminosity, dimension: Int) = other[dimension] - this.get()!![dimension]

    private fun Luminosity.dimDistSqr(other: Luminosity, dimension: Int) = other[dimension].minus(this[dimension]).sqr()
}

fun intLog2(number: Int): Int {
    var number = number
    if (number == 0) return 0
    var log = 0
    if (number and 0xffff0000.toInt() != 0) {
        number = number ushr 16
        log = 16
    }
    if (number >= 256) {
        number = number ushr 8
        log += 8
    }
    if (number >= 16) {
        number = number ushr 4
        log += 4
    }
    if (number >= 4) {
        number = number ushr 2
        log += 2
    }
    return log + number.ushr(1)
}