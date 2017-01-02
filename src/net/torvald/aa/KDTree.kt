package net.torvald.aa

/**
 * Created by SKYHi14 on 2017-01-02.
 */
class KDTree(points: List<Pair<Luminosity, Char>>, val dimension: Int) {

    private val root: KDNode?

    init {
        root = create(points, 0)
    }

    fun findNearest(query: Luminosity) = getNearest(root!!, query, 0).location

    private fun create(points: List<Pair<Luminosity, Char>>, depth: Int): KDNode? {
        if (points.isEmpty()) {
            return null
        }
        else {
            val items = points.sortedBy { it.first[depth % dimension] }
            val currentIndex = items.size shr 1

            return KDNode(
                    create(items.subList(0, currentIndex), depth + 1),
                    create(items.subList(currentIndex + 1, items.size), depth + 1),
                    items[currentIndex].first
            )
        }
    }

    private fun getNearest(currentNode: KDNode, query: Luminosity, depth: Int): KDNode {
        val direction = currentNode.compare(query, depth % dimension)

        val next  = if (direction < 0) currentNode.left  else currentNode.right
        val other = if (direction < 0) currentNode.right else currentNode.left
        var best  = if (next == null) currentNode else getNearest(next, query, depth + 1) // traverse to leaf

        if (currentNode.location.distSqr(query) < best.location.distSqr(query)) {
            best = currentNode
        }

        if (other != null) {
            if (currentNode.location.dimDistSqr(query, depth % dimension) < best.location.distSqr(query)) {
                val bestCandidate = getNearest(other, query, depth + 1)
                if (bestCandidate.location.distSqr(query) < best.location.distSqr(query)) {
                    best = bestCandidate
                }
            }
        }

        return best // work back up
    }

    private data class KDNode(val left: KDNode?, val right: KDNode?, val location: Luminosity) {
        fun compare(other: Luminosity, dimension: Int) = other[dimension] - this.location[dimension] // (wrong order causes slowdown!!)
    }

    private fun Luminosity.dimDistSqr(other: Luminosity, dimension: Int) = other[dimension].minus(this[dimension]).sqr()
}