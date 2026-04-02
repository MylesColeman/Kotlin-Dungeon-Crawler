package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import ktx.ashley.allOf
import kotlin.math.abs

// Used to animate entities, only those with both animation and texture components
class AnimationSystem : IteratingSystem(allOf(AnimationComponent::class, TextureComponent::class).get()) {
    // Animates the entities, deciding which animation state they're in and which animation to play
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val anim = AnimationComponent.mapper[entity] ?: return
        val tex = TextureComponent.mapper[entity] ?: return
        val transComp = TransformComponent.mapper[entity] ?: return
        val moveComp = MovementComponent.mapper[entity] ?: return

        val currentAnim = anim.animations[anim.currentState] ?: return // Gets the current state from the animation component

        // Checks whether the entity is moving, determining whether the animation should play
        if (anim.isMoving) {
            anim.stateTime += deltaTime // Updates the animation based on delta time, ensuring its consistent no matter fps

            // Used to determine difference from current position to destination, which way the entity is moving
            val dx = moveComp.target.x - transComp.position.x
            val dy = moveComp.target.y - transComp.position.y

            // Checks which direction entity is moving
            anim.currentState = when {
                // Difference in y is greater than difference in x, entity moving up or down
                abs(dy) > abs(dx) -> {
                    if (dy > 0) "walk_up" else "walk_down" // Checks whether y difference is positive or negative, up or down
                }
                else -> "walk_horizontal"  // Otherwise moving horizontally, sprites are just flipped for this to save on memory
            }

            val region = anim.animations[anim.currentState]?.getKeyFrame(anim.stateTime)
            // Checks whether entity is moving horizontal and region has been correctly assigned
            if (anim.currentState == "walk_horizontal" && region != null) {
                // Checks whether x difference is positive or negative, and whether the sprite is already flipped;
                // then decides whether to flip or not. Flips when heading left
                if ((dx < 0 && !region.isFlipX) || (dx > 0 && region.isFlipX)) {
                    region.flip(true, false)
                }
            }
            tex.region = currentAnim.getKeyFrame(anim.stateTime)
        } else {
            // Not moving, stop playing animation and reset to first frame on animation set
            anim.stateTime = 0f
            tex.region = anim.animations[anim.currentState]?.getKeyFrame(0f)
        }
    }
}
