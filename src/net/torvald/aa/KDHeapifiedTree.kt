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
 */
class KDHeapifiedTree(points: List<Pair<Luminosity, Char>>, val dimension: Int) {

    private val nodes = Array<Luminosity?>(points.size * 4, { null }) // plain array seems bit faster

    private val root: Int = 0

    init {
        //kotlin.repeat(points.size * 4, { nodes.add(null) })

        create(points, 0, 0)
    }

    //fun findNearest(query: Luminosity) = getNearest(root!!, query, 0).location
    fun findNearest(query: Luminosity) = getNearest(root, query, 0).get()!!

    private fun Int.get() = nodes[this]
    private fun Int.getLeft() = this * 2 + 1
    private fun Int.getRight() = this * 2 + 2
    private fun Int.set(value: Luminosity?) { nodes[this] = value }
    private fun Int.setLeftChild(value: Luminosity?) { nodes[this.getLeft()] = value }
    private fun Int.setRightChild(value: Luminosity?) { nodes[this.getRight()] = value }
    private fun Int.getLeftChild() = nodes[this.getLeft()]
    private fun Int.getRightChild() = nodes[this.getRight()]

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

    private fun getNearest(currentNode: Int, query: Luminosity, depth: Int): Int {
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

    fun Int.compare(other: Luminosity, dimension: Int) = other[dimension] - this.get()!![dimension]

    private fun Luminosity.dimDistSqr(other: Luminosity, dimension: Int) = other[dimension].minus(this[dimension]).sqr()
}