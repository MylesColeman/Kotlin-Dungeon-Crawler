package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import ktx.ashley.allOf

class AnimationSystem : IteratingSystem(allOf(AnimationComponent::class, TextureComponent::class).get()) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val anim = AnimationComponent.mapper[entity] ?: return
        val tex = TextureComponent.mapper[entity] ?: return
        val transComp = TransformComponent.mapper[entity] ?: return
        val moveComp = MovementComponent.mapper[entity] ?: return

        val currentAnim = anim.animations[anim.currentState] ?: return

        if (anim.isMoving) {
            anim.stateTime += deltaTime

            val dx = moveComp.target.x - transComp.position.x
            val dy = moveComp.target.y - transComp.position.y

            anim.currentState = when {
                kotlin.math.abs(dy) > kotlin.math.abs(dx) -> {
                    if (dy > 0) "walk_up" else "walk_down"
                }
                else -> "walk_horizontal"
            }

            val region = anim.animations[anim.currentState]?.getKeyFrame(anim.stateTime)
            if (anim.currentState == "walk_horizontal" && region != null) {
                if ((dx < 0 && !region.isFlipX) || (dx > 0 && region.isFlipX)) {
                    region.flip(true, false)
                }
            }
            tex.region = currentAnim.getKeyFrame(anim.stateTime)
        } else {
            anim.stateTime = 0f
            tex.region = anim.animations[anim.currentState]?.getKeyFrame(0f)
        }
    }
}
