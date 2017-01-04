package net.torvald.aa

/**
 * Created by SKYHi14 on 2017-01-03.
 */
class KDHeapifiedTreeLong(
        points: List<Pair<Long, Char>>, val dimension: Int, val noApproximate: Boolean,
        maxPossibleLum: Int, customMaxDepth: Int?) {

    private val nodes = Array<Long?>(points.size * 4, { null }) // plain array seems bit faster

    private val root: Int = 0

    fun findNearest(query: Long) = // (mostly) Bad Apple-specific hacks
            if (query.distSqr(zeroLum) < roundToBlackDist && !noApproximate)
                zeroLum
            else if (query.distSqr(realWhiteLum) < roundToWhiteDist && !noApproximate)
                realWhiteLum
            else
                getNearest(root, query, 0, maxSearchDepth).get()!!

    private fun Int.get() = nodes[this]
    private fun Int.getLeft() = this * 2 + 1
    private fun Int.getRight() = this * 2 + 2
    private fun Int.set(value: Long?) { nodes[this] = value }
    private fun Int.setLeftChild(value: Long?) { nodes[this.getLeft()] = value }
    private fun Int.setRightChild(value: Long?) { nodes[this.getRight()] = value }

    /** used to approximate the solution (doesn't go deeper than 8 -- 9 steps) */
    private val maxSearchDepth = customMaxDepth ?:
            if (noApproximate) intLog2(points.size)
            else               Math.round(intLog2(points.size).times(0.75)).toInt()
    private val roundToBlackDist = KDHeapifiedTree.roundToBlackDist
    private val roundToWhiteDist = KDHeapifiedTree.roundToWhiteDist
    private val zeroLum = 0L
    private val maxLumComp = maxPossibleLum.toLong().shr(48) +
            maxPossibleLum.toLong().shr(32) +
            maxPossibleLum.toLong().shr(16) +
            maxPossibleLum.toLong()
    private val realWhiteLum: Long

    init {
        println("Approximated calculation: ${if (customMaxDepth != null) "no idea" else !noApproximate}")
        println("Max recursion depth: $maxSearchDepth")
        create(points, 0, 0)
        realWhiteLum = getNearest(root, maxLumComp, 0, intLog2(points.size)).get()!!
    }

    private fun create(points: List<Pair<Long, Char>>, depth: Int, index: Int): Long? {
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

    private fun getNearest(currentNode: Int, query: Long, depth: Int, maxDepth: Int): Int {
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

    fun Int.compare(other: Long, dimension: Int) = other[dimension] - this.get()!![dimension]

    private fun Long.distSqr(other: Long): Int {
        var dist = 0
        for (i in 0..dimension - 1)
            dist += (this[i] - other[i]) * (this[i] - other[i])
        return dist
    }

    private fun Long.dimDistSqr(other: Long, dimension: Int) =
            other[dimension].minus(this[dimension]).sqr()

    private operator fun Long.get(index: Int) = this.ushr(48 - 16 * index).and(0xFFFF).toInt()
}