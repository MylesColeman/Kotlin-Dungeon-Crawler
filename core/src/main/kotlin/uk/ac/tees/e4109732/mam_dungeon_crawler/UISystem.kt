package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import ktx.ashley.allOf
import ktx.graphics.use

class UISystem(private val batch: SpriteBatch, private val hudcamera: OrthographicCamera, atlas: TextureAtlas, private val localPlayerId: Int)
    : IteratingSystem(allOf(PlayerComponent::class, HealthComponent::class).get()) {
        private val baseHeart = atlas.findRegion("ui_heart")

        private val heartSize = 1.0f
        private val padding = 0.2f

        private val colourFull = Color(1.0f, 0.1f, 0.1f, Constants.UI_OPACITY)
        private val colourEmpty = Color(0.3f, 0.3f, 0.3f, Constants.UI_OPACITY)

        override fun update(deltaTime: Float) {
            batch.projectionMatrix = hudcamera.combined

            batch.use {
                super.update(deltaTime)
                batch.color = Color.WHITE
            }
        }

        override fun processEntity(entity: Entity, deltaTime: Float) {
            val playerComp = PlayerComponent.mapper[entity] ?: return
            val healthComp = HealthComponent.mapper[entity] ?: return

            // Local player's health is top left, other players are top right
            val startX = if (playerComp.id == localPlayerId) 0.5f else Constants.MAP_WIDTH - (healthComp.maxHearts * (heartSize + padding)) - 0.5f
            val startY = Constants.MAP_HEIGHT - 1.5f

            for (i in 0 until healthComp.maxHearts) {
                // Tints the heart dependent on its state
                batch.color = if (i < healthComp.currentHearts) colourFull else colourEmpty

                if (baseHeart != null) {
                    batch.draw(baseHeart, startX + (i * (heartSize + padding)), startY, heartSize, heartSize)
                }
            }
    }
}
