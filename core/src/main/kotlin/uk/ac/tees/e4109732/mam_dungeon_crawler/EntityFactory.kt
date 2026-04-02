package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.graphics.g2d.Animation
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Array as GdxArray
import ktx.ashley.entity
import ktx.ashley.with

// Used to create entities, uses object pooling to reuse components - for efficiency and memory safety
class EntityFactory(private val engine: PooledEngine, private val atlas: TextureAtlas) {
    // Used to create players, assigning the correct components
    fun createPlayer(id: Int, spawnX: Float, spawnY: Float) = engine.entity {
        with<PlayerComponent> { this.id = id } // Passes in the assigned ID for the component
        with<TransformComponent> {
            position.set(spawnX, spawnY) // Sets default transform using spawn points assigned on Tiled map
            z = 1f // Above most other entities so they're always visible
        }
        with<MovementComponent> {
            target.set(spawnX, spawnY) // Default target, redundant
        }
        with<PathComponent>()
        with<TextureComponent>()
        with<AnimationComponent> {
            // Creates the player animations
            animations["walk_down"] = createWalkingAnimation("walk_down")
            animations["walk_horizontal"] = createWalkingAnimation("walk_horizontal")
            animations["walk_up"] = createWalkingAnimation("walk_up")

            // Default idle state
            currentState = "walk_down"
            isMoving = false
        }
    }

    // Creates the animations, finding the regions
    private fun createWalkingAnimation(regionName: String): Animation<TextureRegion> {
        val regions = atlas.findRegions(regionName)

        // Repeats the first frame, saving memory space on copied sprites
        val frames = GdxArray<TextureRegion>().apply {
            add(regions[0])
            add(regions[1])
            add(regions[0])
            add(regions[2])
        }

        // Animation has 0.15f delay between frames and loops, this looks good
        return Animation(0.15f, frames, Animation.PlayMode.LOOP)
    }
}
