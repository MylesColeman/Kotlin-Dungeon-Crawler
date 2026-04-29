package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.graphics.g2d.Animation
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.Array as GdxArray
import ktx.ashley.entity
import ktx.ashley.with

// Used to create entities, uses object pooling to reuse components - for efficiency and memory safety
class EntityFactory(private val engine: PooledEngine, private val atlas: TextureAtlas, private val world: World) {
    // Used to create players, assigning the correct components
    fun createPlayer(id: Int, spawnX: Float, spawnY: Float) = engine.entity {
        with<PlayerComponent> { this.id = id } // Passes in the assigned ID for the component
        with<TransformComponent> {
            position.set(spawnX, spawnY) // Sets default transform using spawn points assigned on Tiled map
            z = 1f // Above most other entities so they're always visible
        }
        with<PhysicsComponent> {
            // Creates the player physics body, the instructions used to create the box2D
            val bodyDef = BodyDef().apply {
                type = BodyDef.BodyType.DynamicBody
                position.set(spawnX, spawnY)
                fixedRotation = true
            }

            // Creates the player's body, the box2D
            body = world.createBody(bodyDef).apply {
                userData = this@entity.entity // Attach this entity to the Box2D body as a tag

                // Body is a circle, slightly smaller than the tile size to prevent snagging
                val shape = CircleShape().apply { radius = 0.4f }
                createFixture(shape, 1f).apply {
                    isSensor = true // Used for detecting overlaps, not blocking collisions
                }
                shape.dispose() // Disposed as no longer needed, helps to prevent memory leaks
            }
        }
        with<MovementComponent> {
            target.set(spawnX, spawnY) // Default target, redundant
        }
        with<PathComponent>()
        with<HealthComponent>()
        with<AOEAttackComponent>()
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

    fun createAOERing(centreX: Float, centreY: Float, range: Float) = engine.entity {
        with<TransformComponent> {
            position.set(centreX, centreY)
            z = 0.5f // Behind player but above floor
        }
        with<TextureComponent> {
            region = atlas.findRegion("aoeAttack_ring")
        }
        with<EffectComponent> {
            val region = atlas.findRegion("aoeAttack_ring")
            // Converts to world units, can be divided by just width as its square
            val nativeWidthUnits = region.regionWidth * Constants.UNIT_SCALE
            // Multiplied by 2 to cover the diameter, then divided by the native width to get the appropriate scale
            maxScale = (range * 2f) / nativeWidthUnits
        }
    }
}
