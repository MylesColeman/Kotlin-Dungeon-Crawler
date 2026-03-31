package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.g2d.Animation
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor

class TransformComponent : Component, Pool.Poolable {
    val position = Vector2()
    var z = 0f

    override fun reset() {
        position.set(0f, 0f)
        z = 0f
    }
    companion object { val mapper = mapperFor<TransformComponent>() }
}

class TextureComponent : Component, Pool.Poolable {
    var region: TextureRegion? = null

    override fun reset() { region = null }
    companion object { val mapper = mapperFor<TextureComponent>() }
}

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

class PathComponent : Component, Pool.Poolable {
    val nodes = mutableListOf<Vector2>()

    override fun reset() {
        nodes.clear()
    }
    companion object { val mapper = mapperFor<PathComponent>() }
}

class MovementComponent : Component, Pool.Poolable {
    var target = Vector2()
    var targetTile: Vector2? = null
    var speed = 5f
    override fun reset() {
        target.set(0f, 0f)
        speed = 5f
    }
    companion object { val mapper = mapperFor<MovementComponent>() }
}

class PlayerComponent : Component, Pool.Poolable {
    var id = -1
    override fun reset() { id = -1 }
    companion object { val mapper = mapperFor<PlayerComponent>() }
}
