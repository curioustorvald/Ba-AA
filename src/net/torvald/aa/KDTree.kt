package net.torvald.aa

import java.util.*

/**
 * Created by SKYHi14 on 2017-01-02.
 */
class KDTree(points: List<Pair<Luminosity, Char>>, val dimension: Int) {

    private val nodes = ArrayList<Luminosity?>(points.size * 2) // binary tree on a heap

    private val root: KDNode?
    //private val root: Int = 0

    init {
        //kotlin.repeat(points.size * 2, { nodes.add(null) })

        root = create(points, 0)
        //create(points, 0, 0)
    }

    fun findNearest(query: Luminosity) = getNearest(root!!, query, 0).location
    //fun findNearest(query: Luminosity) = getNearest(root, query, 0).get()!!

    // TODO implement binary heap to increase data locality and thus, performance

    private fun Int.get() = nodes[this]
    private fun Int.getLeft() = this * 2 + 1
    private fun Int.getRight() = this * 2 + 2
    private fun Int.set(value: Luminosity?) { nodes[this] = value }
    private fun Int.setLeftChild(value: Luminosity?) { nodes[this.getLeft()] = value }
    private fun Int.setRightChild(value: Luminosity?) { nodes[this.getRight()] = value }
    private fun Int.getLeftChild() = nodes[this.getLeft()]
    private fun Int.getRightChild() = nodes[this.getRight()]

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

    /*private fun create(points: List<Pair<Luminosity, Char>>, depth: Int, currentIndex: Int): Luminosity? {
        if (points.isEmpty()) {
            currentIndex.set(null)

            return null
        }
        else {
            val items = points.sortedBy { it.first[depth % dimension] }
            val currentIndex = items.size shr 1

            currentIndex.setLeftChild(create(items.subList(0, currentIndex), depth + 1, currentIndex.getLeft()))
            currentIndex.setRightChild(create(items.subList(currentIndex + 1, items.size), depth + 1, currentIndex.getRight()))
            currentIndex.set(items[currentIndex].first)

            return currentIndex.get()
        }
    }*/

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
    /*private fun getNearest(currentNode: Int, query: Luminosity, depth: Int): Int {
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
    }*/

    private data class KDNode(val left: KDNode?, val right: KDNode?, val location: Luminosity) {
        fun compare(other: Luminosity, dimension: Int) = other[dimension] - this.location[dimension] // (wrong order causes slowdown!!)
    }

    //fun Int.compare(other: Luminosity, dimension: Int) = other[dimension] - this.get()!![dimension]

    private fun Luminosity.dimDistSqr(other: Luminosity, dimension: Int) = other[dimension].minus(this[dimension]).sqr()
}