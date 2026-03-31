package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.SortedIteratingSystem
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.graphics.use

class RenderSystem(private val batch: SpriteBatch, private val camera: OrthographicCamera)
    : SortedIteratingSystem(allOf(TransformComponent::class, TextureComponent::class).get(),
    compareBy { entity -> entity[TransformComponent.mapper]?.z }) {

        override fun update(deltaTime: Float) {
            forceSort()
            batch.use(camera.combined) {
                super.update(deltaTime)
            }
        }

        override fun processEntity(entity: Entity, deltaTime: Float) {
            val transform = TransformComponent.mapper[entity] ?: return
            val texture = TextureComponent.mapper[entity] ?: return

            texture.region?.let { region ->
                var width = region.regionWidth * Constants.UNIT_SCALE
                var height = region.regionHeight * Constants.UNIT_SCALE

                if (transform.z == 0.5f) {
                    width *= 0.1f
                    height *= 0.1f
                }

                batch.draw(
                    region,
                    transform.position.x - (width / 2f),
                    transform.position.y - (height / 2f),
                    width,
                    height
                )

            }
        }

    }
