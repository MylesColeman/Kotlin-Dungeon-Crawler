package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import ktx.ashley.allOf

// Handles game effects, i.e. attack ring
class EffectSystem : IteratingSystem(allOf(EffectComponent::class, TransformComponent::class).get()) {
    // Handles the life span of the entity
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val effect = EffectComponent.mapper[entity]!!
        effect.currentLife += deltaTime

        // If the effect has outlived its lifetime, remove it from the engine
        if (effect.currentLife >= effect.lifeTime) {
            engine.removeEntity(entity)
        }
    }
}
