package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import ktx.ashley.allOf

class AnimationSystem : IteratingSystem(allOf(AnimationComponent::class, TextureComponent::class).get()) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val anim = AnimationComponent.mapper[entity] ?: return
        val tex = TextureComponent.mapper[entity] ?: return

        val currentAnim = anim.animations[anim.currentState] ?: return

        if (anim.isMoving) {
            anim.stateTime += deltaTime
        } else {
            anim.stateTime = 0f
        }

        tex.region = currentAnim.getKeyFrame(anim.stateTime)
    }
}
