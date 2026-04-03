package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.Vector2
import ktx.ashley.allOf

// Used to move an entity from their current position to a target, translating the pathfinding nodes into smooth movement
// Only moves entities with transform, movement, path and animation components
class MovementSystem : IteratingSystem(
    allOf(TransformComponent::class, MovementComponent::class, PathComponent::class, AnimationComponent::class, PhysicsComponent::class).get()) {
    private val tempVec = Vector2() // Used for moving the entity, to avoid creating new objects every frame

    // Updates the entities transform, smoothly moving them towards a pathfinding node
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val transform = TransformComponent.mapper[entity] ?: return
        val movement = MovementComponent.mapper[entity] ?: return
        val path = PathComponent.mapper[entity] ?: return
        val anim = AnimationComponent.mapper[entity] ?: return
        val physics = PhysicsComponent.mapper[entity] ?: return

        // Checks whether there's nodes to move towards and the distance isn't negligible
        if (path.nodes.isNotEmpty() && transform.position.dst(movement.target) < 0.1f) {
            val nextNode = path.nodes.removeAt(0) // Removes the first node, as the entity is there
            // Sets the target to the next node
            movement.target.set(nextNode)
            movement.targetTile = nextNode
        }

        val distance = transform.position.dst(movement.target) // Distance between current position and the target

        // Checks whether distance isn't negligible
        if (distance > 0.05f) {
            anim.isMoving = true // Sets the entity into movement state
            // Uses the movement speed and delta time to ensure entity moves the same amount no matter fps
            val movementAmount = movement.speed * deltaTime

            // Checks whether the movement amount is greater than or equal to the distance left to cover
            if (movementAmount >= distance) {
                transform.position.set(movement.target) // Just moves the entity to target as distance is too small for movement amount
                movement.targetTile = null // Resets target tile
            } else {
                // Calculates the direction to the target and scales it by the movement amount
                tempVec.set(movement.target).sub(transform.position).nor().scl(movementAmount)
                transform.position.add(tempVec) // Moves the entity by the just calculated vector
            }
        } else {
            transform.position.set(movement.target) // Just moves the entity to target as distance is too small for movement amount
            anim.isMoving = false // Takes the entity out of the movement state
            movement.targetTile = null // Resets target tile
        }
        physics.body?.setTransform(transform.position, 0f) // Syncs the physics body with the transform
    }
}
