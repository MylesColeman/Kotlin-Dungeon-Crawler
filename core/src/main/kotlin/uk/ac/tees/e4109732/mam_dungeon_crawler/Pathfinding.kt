package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.BinaryHeap
import kotlin.math.abs

// Implementation of A* pathfinding, done so as an Object as only one instance is required - Singleton
object Pathfinding {
    // Pre-allocates a 2D grid of Nodes, for efficiency
    private val grid = Array(Constants.MAP_WIDTH) { x ->
        Array(Constants.MAP_HEIGHT) { y -> Node(x, y) }
    }

    // Finds the shortest path from start position to goal,
    // has knowledge of which 'Node's are blocked so they can be ignored for final path
    fun findPath(startX: Int, startY: Int, goalX: Int, goalY: Int,
                 isBlocked: (Int, Int) -> Boolean): List<Vector2> {
        // Resets the grid each time its called, ensuring a clean slate
        for (x in 0 until Constants.MAP_WIDTH) {
            for (y in 0 until Constants.MAP_HEIGHT) {
                val n = grid[x][y]
                n.g = 0
                n.h = 0f
                n.parent = null
                n.isInOpenSet = false
            }
        }

        val openSet = BinaryHeap<Node>() // Nodes that need checking, instance of 'BinaryHeap', automatically sorted
        val closedSet = mutableSetOf<Node>() // Nodes that have been checked

        val startNode = grid[startX][startY]
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

            closedSet.add(current) // Added to closed set, this node has been checked

            // Creates a list of all neighbours to the current node, four in the cardinal directions
            val neighbours = listOf(
                Pair(current.x + 1, current.y),
                Pair(current.x - 1, current.y),
                Pair(current.x, current.y + 1),
                Pair(current.x, current.y - 1)
            )

            // Loops through all neighbours, assigns nx and ny to the pairs values
            for ((nx, ny) in neighbours) {
                // Skips neighbours that are out of bounds
                if (nx !in 0 until Constants.MAP_WIDTH || ny !in 0 until Constants.MAP_HEIGHT) continue

                val neighbour = grid[nx][ny] // Assigns the neighbour a cell in the 2D array

                // Checks whether the cell has already been checked, or is blocked by an obstacle/wall
                if (isBlocked(nx, ny) || neighbour in closedSet) continue

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
        // and the absolute vertical difference (ignoring whether up or down) then adds them together.
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
