package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.Vector2
import ktx.ashley.allOf

class MovementSystem : IteratingSystem(
    allOf(TransformComponent::class, MovementComponent::class, AnimationComponent::class).get()) {
    private val tempVec = Vector2()

    override fun processEntity(entity: Entity?, deltaTime: Float) {
        val transform = TransformComponent.mapper[entity] ?: return
        val movement = MovementComponent.mapper[entity] ?: return
        val anim = AnimationComponent.mapper[entity] ?: return

        val distance = transform.position.dst(movement.target)

        if (distance > 0.1f) {
            anim.isMoving = true
            val movementAmount = movement.speed * deltaTime

            if (movementAmount >= distance) {
                transform.position.set(movement.target)
            } else {
                tempVec.set(movement.target).sub(transform.position).nor().scl(movementAmount)
                transform.position.add(tempVec)
            }
        } else { anim.isMoving = false }
    }
}
