package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.gdx.utils.BinaryHeap

// Node used by A* 'Pathfinding' represents an individual tile/cell in the grid
// Has position, and inherits from 'BinaryHeap.Node' to allow the algorithm to efficiently sort nodes by cost
class Node(val x: Int, val y: Int) : BinaryHeap.Node(0f) {
    var g: Int = 0 // Goal - how many steps currently taken
    var h: Float = 0f // Heuristic - educated guess on distance to target
    val f: Float get() = g + h // Final cost - goal + heuristic
    var parent: Node? = null // Holds the previous node, acting as a breadcrumb trail back to the start with the shortest path

    var isInOpenSet: Boolean = false // By default unchecked, only moved to open set if relevant for checking

    // Checks whether an equal node is already present, based on coordinates - ignoring: 'g', 'h', 'f', and 'parent'
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Node) return false
        return x == other.x && y == other.y
    }

    // Used to help identify specific nodes, 31 chosen as its prime and helps with efficiency
    override fun hashCode(): Int {
        return 31 * x + y
    }
}
