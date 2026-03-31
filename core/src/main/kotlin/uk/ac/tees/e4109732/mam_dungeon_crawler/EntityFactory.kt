package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.graphics.g2d.Animation
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Array as GdxArray
import ktx.ashley.entity
import ktx.ashley.with

class EntityFactory(private val engine: PooledEngine, private val atlas: TextureAtlas) {

    fun createPlayer(id: Int, spawnX: Float, spawnY: Float) = engine.entity {
        with<PlayerComponent> { this.id = id }
        with<TransformComponent> {
            position.set(spawnX, spawnY)
            z = 1f
        }
        with<MovementComponent>() {
            target.set(spawnX, spawnY)
        }
        with<PathComponent>()
        with<TextureComponent>()
        with<AnimationComponent> {
            animations["walk_down"] = createWalkingAnimation("walk_down")
            animations["walk_horizontal"] = createWalkingAnimation("walk_horizontal")
            animations["walk_up"] = createWalkingAnimation("walk_up")

            currentState = "walk_down"
            isMoving = false
        }
    }

    private fun createWalkingAnimation(regionName: String): Animation<TextureRegion> {
        val regions = atlas.findRegions(regionName)

        val frames = GdxArray<TextureRegion>().apply {
            add(regions[0])
            add(regions[1])
            add(regions[0])
            add(regions[2])
        }

        return Animation(0.15f, frames, Animation.PlayMode.LOOP)
    }
}
