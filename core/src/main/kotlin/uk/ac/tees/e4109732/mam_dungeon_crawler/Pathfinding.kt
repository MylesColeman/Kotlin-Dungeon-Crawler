package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.BinaryHeap
import kotlin.math.abs

object Pathfinding {
    fun findPath(startX: Int, startY: Int, goalX: Int, goalY: Int,
                 isBlocked: (Int, Int) -> Boolean): List<Vector2> {
        val grid = Array(Constants.MAP_WIDTH) { x ->
            Array(Constants.MAP_HEIGHT) { y -> Node(x, y) }
        }

        val openSet = BinaryHeap<Node>()
        val closedSet = mutableSetOf<Node>()

        val startNode = grid[startX][startY]
        startNode.g = 0
        startNode.h = calculateManhattan(startX, startY, goalX, goalY)

        startNode.isInOpenSet = true
        openSet.add(startNode, startNode.f.toFloat())

        while (openSet.size > 0) {
            val current = openSet.pop()
            current.isInOpenSet = false

            if (current.x == goalX && current.y == goalY) {
                return reconstructPath(current)
            }

            closedSet.add(current)

            val neighbors = listOf(
                Pair(current.x + 1, current.y),
                Pair(current.x - 1, current.y),
                Pair(current.x, current.y + 1),
                Pair(current.x, current.y - 1)
            )

            for ((nx, ny) in neighbors) {
                if (nx !in 0 until Constants.MAP_WIDTH || ny !in 0 until Constants.MAP_HEIGHT) continue

                val neighbor = grid[nx][ny]

                if (isBlocked(nx, ny) || neighbor in closedSet) continue

                val tentativeG = current.g + 1

                if (!neighbor.isInOpenSet || tentativeG < neighbor.g) {
                    neighbor.g = tentativeG
                    neighbor.h = calculateManhattan(nx, ny, goalX, goalY)
                    neighbor.parent = current

                    val fCost = neighbor.f.toFloat()
                    if (neighbor.isInOpenSet) {
                        openSet.setValue(neighbor, fCost)
                    } else {
                        openSet.add(neighbor)
                    }
                }
            }
        }

        return emptyList()
    }

    private fun calculateManhattan(x1: Int, y1: Int, x2: Int, y2: Int): Int {
        return abs(x1 - x2) + abs(y1 - y2)
    }

    private fun reconstructPath(goalNode: Node): List<Vector2> {
        return generateSequence(goalNode) { it.parent }
            .map { Vector2(it.x + 0.5f, it.y + 0.5f) }
            .toList()
            .reversed()
    }
}
