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
                batch.draw(
                    region,
                    transform.position.x,
                    transform.position.y,
                    region.regionWidth * Constants.UNIT_SCALE,
                    region.regionHeight * Constants.UNIT_SCALE
                )

            }
        }

    }
