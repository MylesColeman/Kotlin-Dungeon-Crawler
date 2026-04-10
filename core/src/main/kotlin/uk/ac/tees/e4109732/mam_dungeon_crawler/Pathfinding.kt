package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.BinaryHeap
import kotlin.math.abs

// Implementation of A* pathfinding, done so as an Object as only one instance is required - Singleton
object Pathfinding {
    // Pre-allocates a 1D grid of Nodes, for efficiency
    private val grid = Array(Constants.MAP_WIDTH * Constants.MAP_HEIGHT) { i ->
        Node(i % Constants.MAP_WIDTH, i / Constants.MAP_WIDTH)
    }

    // Static directional arrays for node neighbours
    private val dx = intArrayOf(1, -1, 0, 0)
    private val dy = intArrayOf(0, 0, 1, -1)

    // Finds the shortest path from start position to goal,
    // has knowledge of which 'Node's are blocked so they can be ignored for final path
    fun findPath(startX: Int, startY: Int, goalX: Int, goalY: Int,
                 isBlocked: (Int, Int) -> Boolean): List<Vector2> {
        // Checks whether the start or goal is out of bounds
        if (startX !in 0 until Constants.MAP_WIDTH || startY !in 0 until Constants.MAP_HEIGHT ||
            goalX !in 0 until Constants.MAP_WIDTH || goalY !in 0 until Constants.MAP_HEIGHT) {
            return emptyList()
        }

        // Resets the grid each time its called, ensuring a clean slate
        for (i in grid.indices) {
            val n = grid[i]
            n.g = 0
            n.h = 0f
            n.parent = null
            n.isInOpenSet = false
        }

        val openSet = BinaryHeap<Node>() // Nodes that need checking, instance of 'BinaryHeap', automatically sorted
        val closedSet = BooleanArray(Constants.MAP_WIDTH * Constants.MAP_HEIGHT) // Nodes that have been checked

        // Sets the start node
        val startIndex = startY * Constants.MAP_WIDTH + startX
        val startNode = grid[startIndex]
        startNode.g = 0 // Start node is 0, as its the only explored tile
        // Calculates the heuristic, estimation of distance, from start to finish
        startNode.h = calculateManhattan(startX, startY, goalX, goalY)

        // Moved to open set, so it can be checked
        startNode.isInOpenSet = true
        openSet.add(startNode, startNode.f) // Passes in the final cost, so the 'BinaryHeap' knows how to sort it

        // Whilst theres still nodes to check
        while (openSet.size > 0) {
            // Takes the fist node, the one with the lowest final cost, and removes it from the open set - about to be checked
            val current = openSet.pop()
            current.isInOpenSet = false

            if (current.x == goalX && current.y == goalY) { return reconstructPath(current) } // Found path

            closedSet[current.y * Constants.MAP_WIDTH + current.x] = true // Added to closed set, this node has been checked

            // Loops through all neighbours
            for (i in 0 until 4) {
                // Assigns coords to node for neighbour
                val nx = current.x + dx[i]
                val ny = current.y + dy[i]

                // Skips neighbours that are out of bounds
                if (nx !in 0 until Constants.MAP_WIDTH || ny !in 0 until Constants.MAP_HEIGHT) continue

                val nIdx = ny * Constants.MAP_WIDTH + nx // Translates the 2D grid to a 1D grid

                // Checks whether the cell has already been checked, or is blocked by an obstacle/wall
                if (closedSet[nIdx] || isBlocked(nx, ny)) continue

                val neighbour = grid[nIdx] // Assigns node index to check neighbour
                val tentativeG = current.g + 1 // Potential cost, from current tile to neighbour

                // Checks whether the neighbour is not in the open set, i.e. its a new tile
                // Also checks whether this is a shortcut, potential is lower than current 'g' - goal
                if (!neighbour.isInOpenSet || tentativeG < neighbour.g) {
                    neighbour.g = tentativeG // As this is a shortcut, assign the potential g to it
                    // Calculates heuristic, using Manhattan distance and assigns it
                    neighbour.h = calculateManhattan(nx, ny, goalX, goalY)
                    neighbour.parent = current // Sets parent to the current, the original node that was being checked
                    val fCost = neighbour.f // Calculates the final cost

                    // Checks whether the node is already in the open set, waiting to be checked; likely found a cheaper path
                    if (neighbour.isInOpenSet) {
                        openSet.setValue(neighbour, fCost) // Updates node
                    }
                    // New tile
                    else {
                        // Add to open set so it can be evaluated
                        neighbour.isInOpenSet = true
                        openSet.add(neighbour, neighbour.f)
                    }
                }
            }
        }
        // Finished checking without finding path to goal - impossible. Returns an empty list so the player doesn't move
        return emptyList()
    }

    // Calculates the Manhattan distance, optimistic distance from a Node to the goal ignoring obstacles
    private fun calculateManhattan(x1: Int, y1: Int, x2: Int, y2: Int): Float {
        // Looks at the absolute horizontal difference (ignoring whether left or right),
        // and the absolute vertical difference (ignoring whether up or down) then adds them together
        val dx = abs(x1 - x2).toFloat()
        val dy = abs(y1 - y2).toFloat()
        return dx + dy + (dx * 0.001f) // Multiplies by an arbitrary value, this helps break ties by skewing towards y-axis
    }

    // Reconstructs the path from goal back to the starting position
    private fun reconstructPath(goalNode: Node): List<Vector2> {
        // Uses the parent node of each node as a breadcrumb going back through the list till back at the start position,
        // when hitting null
        return generateSequence(goalNode) { it.parent }
            .map { Vector2(it.x + 0.5f, it.y + 0.5f) } // Adds 0.5f to x and y so player is in the centre of a tile
            .toList()
            .reversed() // Reversed so path starts at the start
    }
}
