package net.torvald.aa

/**
 * Created by SKYHi14 on 2017-01-03.
 */
class KDHeapifiedTreeLong(points: List<Pair<Long, Char>>, val dimension: Int) {

    private val nodes = Array<Long?>(points.size * 4, { null }) // plain array seems bit faster

    private val root: Int = 0

    init {
        create(points, 0, 0)
    }

    fun findNearest(query: Long) = getNearest(root, query, 0).get()!!

    private fun Int.get() = nodes[this]
    private fun Int.getLeft() = this * 2 + 1
    private fun Int.getRight() = this * 2 + 2
    private fun Int.set(value: Long?) { nodes[this] = value }
    private fun Int.setLeftChild(value: Long?) { nodes[this.getLeft()] = value }
    private fun Int.setRightChild(value: Long?) { nodes[this.getRight()] = value }

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

    private fun getNearest(currentNode: Int, query: Long, depth: Int): Int {
        val direction = currentNode.compare(query, depth % dimension)

        val next  = if (direction < 0) currentNode.getLeft()  else currentNode.getRight()
        val other = if (direction < 0) currentNode.getRight() else currentNode.getLeft()
        var best  = if (next.get() == null) currentNode else getNearest(next, query, depth + 1) // traverse to leaf

        if (currentNode.get()!!.distSqr(query) < best.get()!!.distSqr(query)) {
            best = currentNode
        }

        if (other.get() != null) {
            if (currentNode.get()!!.dimDistSqr(query, depth % dimension) < best.get()!!.distSqr(query)) {
                val bestCandidate = getNearest(other, query, depth + 1)
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

    //private operator fun Long.get(index: Int) = this.ushr(48 - 16 * index).and(0xFFFF).toInt()
    private operator fun Long.get(index: Int) = this.ushr(16 * index).and(0xFFFF).toInt()
}