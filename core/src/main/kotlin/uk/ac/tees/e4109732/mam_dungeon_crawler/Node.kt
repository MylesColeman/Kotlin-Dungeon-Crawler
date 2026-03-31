package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.gdx.utils.BinaryHeap

class Node(val x: Int, val y: Int) : BinaryHeap.Node(0f) {
    var g: Int = 0
    var h: Int = 0
    val f: Int get() = g + h
    var parent: Node? = null

    var isInOpenSet: Boolean = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Node) return false
        return x == other.x && y == other.y
    }

    override fun hashCode(): Int {
        return 31 * x + y
    }
}
