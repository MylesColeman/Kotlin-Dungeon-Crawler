package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.BinaryHeap
import kotlin.math.abs

object Pathfinding {
    private val grid = Array(Constants.MAP_WIDTH) { x ->
        Array(Constants.MAP_HEIGHT) { y -> Node(x, y) }
    }

    fun findPath(startX: Int, startY: Int, goalX: Int, goalY: Int,
                 isBlocked: (Int, Int) -> Boolean): List<Vector2> {
        for (x in 0 until Constants.MAP_WIDTH) {
            for (y in 0 until Constants.MAP_HEIGHT) {
                val n = grid[x][y]
                n.g = 0
                n.h = 0f
                n.parent = null
                n.isInOpenSet = false
            }
        }

        val openSet = BinaryHeap<Node>()
        val closedSet = mutableSetOf<Node>()

        val startNode = grid[startX][startY]
        startNode.g = 0
        startNode.h = calculateManhattan(startX, startY, goalX, goalY)

        startNode.isInOpenSet = true
        openSet.add(startNode, startNode.f)

        while (openSet.size > 0) {
            val current = openSet.pop()
            current.isInOpenSet = false

            if (current.x == goalX && current.y == goalY) { return reconstructPath(current) }

            closedSet.add(current)

            val neighbors = listOf(
                Pair(current.x + 1, current.y),
                Pair(current.x - 1, current.y),
                Pair(current.x, current.y + 1),
                Pair(current.x, current.y - 1)
            ).sortedBy { calculateManhattan(it.first, it.second, goalX, goalY) }

            for ((nx, ny) in neighbors) {
                if (nx !in 0 until Constants.MAP_WIDTH || ny !in 0 until Constants.MAP_HEIGHT) continue

                val neighbor = grid[nx][ny]

                if (isBlocked(nx, ny) || neighbor in closedSet) continue

                val tentativeG = current.g + 1

                if (!neighbor.isInOpenSet || tentativeG < neighbor.g) {
                    neighbor.g = tentativeG
                    neighbor.h = calculateManhattan(nx, ny, goalX, goalY)
                    neighbor.parent = current

                    val fCost = neighbor.f
                    if (neighbor.isInOpenSet) {
                        openSet.setValue(neighbor, fCost)
                    } else {
                        neighbor.isInOpenSet = true
                        openSet.add(neighbor, neighbor.f)
                    }
                }
            }
        }
        return emptyList()
    }

    private fun calculateManhattan(x1: Int, y1: Int, x2: Int, y2: Int): Float {
        return (abs(x1 - x2) + abs(y1 - y2)) * 1.001f
    }

    private fun reconstructPath(goalNode: Node): List<Vector2> {
        return generateSequence(goalNode) { it.parent }
            .map { Vector2(it.x + 0.5f, it.y + 0.5f) }
            .toList()
            .reversed()
    }
}
