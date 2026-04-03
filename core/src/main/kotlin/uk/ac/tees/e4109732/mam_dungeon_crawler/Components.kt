package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.g2d.Animation
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor

// --------------------------------------------------------------------------------------------------
// All components are poolable, so when an entity is destroyed/disabled they can just be reused
// This is why they contain a reset function
// --------------------------------------------------------------------------------------------------

// Transform component, holds normal position as well as 'z' coordinate to help prevent z fighting
class TransformComponent : Component, Pool.Poolable {
    val position = Vector2()
    var z = 0f

    override fun reset() {
        position.set(0f, 0f)
        z = 0f
    }
    companion object { val mapper = mapperFor<TransformComponent>() }
}

// Texture component, holds the texture region
class TextureComponent : Component, Pool.Poolable {
    var region: TextureRegion? = null

    override fun reset() { region = null }
    companion object { val mapper = mapperFor<TextureComponent>() }
}

// Animation component, holds a map of a specific animation set, how long an animation has been playing
// the current animation and whether the entity is moving
class AnimationComponent : Component, Pool.Poolable {
    val animations = mutableMapOf<String, Animation<TextureRegion>>()
    var stateTime = 0f
    var currentState = "walk_down"

    var isMoving = false

    override fun reset() {
        animations.clear()
        stateTime = 0f
        currentState = "walk_down"

        isMoving = false
    }
    companion object { val mapper = mapperFor<AnimationComponent>() }
}

// Pathfinding component, contains a vector of nodes to be used whilst pathfinding - the current path
class PathComponent : Component, Pool.Poolable {
    val nodes = mutableListOf<Vector2>()

    override fun reset() {
        nodes.clear()
    }
    companion object { val mapper = mapperFor<PathComponent>() }
}

// Movement component, contains the target destination and the target tile as well as the speed to get there
class MovementComponent : Component, Pool.Poolable {
    var target = Vector2() // Where the player is currently headed, interpolation
    var targetTile: Vector2? = null // The end target goal - not necessarily the final target tile though

    var speed = 5f

    override fun reset() {
        target.set(0f, 0f)
        targetTile = null
        speed = 5f
    }
    companion object { val mapper = mapperFor<MovementComponent>() }
}

// Player component, contains an ID used to differentiate players
class PlayerComponent : Component, Pool.Poolable {
    var id = -1

    override fun reset() { id = -1 }
    companion object { val mapper = mapperFor<PlayerComponent>() }
}

// AOE attack component, contains the range and damage of an attack as well as the cooldown between attacks
class AOEAttackComponent : Component, Pool.Poolable {
    var range = 2.0f // Radius of Box2D
    var damage = 10f

    var cooldown = 2f
    var currentCooldown = 0f

    override fun reset() {
        range = 2.0f
        damage = 10f

        cooldown = 2f
        currentCooldown = 0f
    }
    companion object { val mapper = mapperFor<AOEAttackComponent>() }
}

// Physics component, contains the Box2D body
class PhysicsComponent : Component, Pool.Poolable {
    var body: Body? = null

    override fun reset() {
        // Body must be destroyed from the world before being nullified
        body?.world?.destroyBody(body)
        body = null
    }
    companion object { val mapper = mapperFor<PhysicsComponent>() }
}
