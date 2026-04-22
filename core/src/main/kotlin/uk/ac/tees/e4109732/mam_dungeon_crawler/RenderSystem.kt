package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.SortedIteratingSystem
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.graphics.use

// Used to render sprites, uses a sorted iterating system to sort sprites on the z-axis to prevent z fighting
// Only renders entities with both transform and texture components, lower z's are rendered first
class RenderSystem(private val batch: SpriteBatch, private val camera: OrthographicCamera, var localPlayerId: Int = -1)
    : SortedIteratingSystem(allOf(TransformComponent::class, TextureComponent::class).get(),
    compareBy { entity -> entity[TransformComponent.mapper]?.z }) {

        // Sorts the sprites on the z-axis and updates them all
        override fun update(deltaTime: Float) {
            forceSort()
            batch.use(camera.combined) {
                super.update(deltaTime)
            }
        }

        // Draws all the sprites using the transform (position) and texture component
        override fun processEntity(entity: Entity, deltaTime: Float) {
            val healthComponent = HealthComponent.mapper[entity]
            if (healthComponent != null && healthComponent.currentHearts <= 0) return // Player is dead, don't draw them

            val transform = TransformComponent.mapper[entity] ?: return
            val texture = TextureComponent.mapper[entity] ?: return
            val region = texture.region ?: return

            val effect = EffectComponent.mapper[entity]

            // Checks whether the current entity is a temporary effect
            if (effect != null) {
                val progress = effect.currentLife / effect.lifeTime // Progress of the effect

                val currentScale = progress * effect.maxScale // Uses the effects life span and max scale to scale the entity

                batch.setColor(0f, 1f, 1f, 1f - progress) // Cyan with fade based on progress

                val width = region.regionWidth * Constants.UNIT_SCALE * currentScale
                val height = region.regionHeight * Constants.UNIT_SCALE * currentScale

                batch.draw(region, transform.position.x - (width / 2f), transform.position.y - (height / 2f), width, height) // Divides transform by 2, ensuring they're drawn in the centre of tiles

                batch.setColor(1f, 1f, 1f, 1f) // Reset colour for other entities
            } else {
                texture.region?.let { region ->
                    // Scales pixel dimensions to world units
                    val width = region.regionWidth * Constants.UNIT_SCALE
                    val height = region.regionHeight * Constants.UNIT_SCALE

                    val player = PlayerComponent.mapper[entity]
                    // Gives the second player a green tint
                    if (player != null && player.id != localPlayerId && localPlayerId != -1)
                        batch.setColor(1f, 0.7f, 1f, 1f) // Red

                    batch.draw(region, transform.position.x - (width / 2f), transform.position.y - 0.5f, width, height) // Divides transform by 2, ensuring they're drawn in the centre of tiles

                    batch.setColor(1f, 1f, 1f, 1f) // Reset colour for other entities
                }
            }
        }

    }
