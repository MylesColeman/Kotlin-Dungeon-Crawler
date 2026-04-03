package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.Gdx.app
import com.badlogic.gdx.Gdx.input
import com.badlogic.gdx.physics.box2d.World
import ktx.ashley.allOf
import kotlin.math.sqrt

// Handles player attacks
class AttackSystem(private val localPlayerID: Int, private val world: World, private val factory: EntityFactory)
    : IteratingSystem(allOf(PlayerComponent::class, AOEAttackComponent::class).get()){
    private val gravity = FloatArray(3) // Gravity is always acting on the accelerometer, this is used to ignore it
    private val linearAcceleration = FloatArray(3) // Actual movement, with gravity subtracted
    private val alpha = 0.15f // How responsive to phone movements it is

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val playerComp = PlayerComponent.mapper[entity] ?: return
        val attackComp = AOEAttackComponent.mapper[entity] ?: return

        if (playerComp.id != localPlayerID) return // Check to ensure this is the local player that initiated the attack

        // Timer so the player can only attack once every set time
        if (attackComp.currentCooldown > 0f) {
            attackComp.currentCooldown -= deltaTime
            return
        }

        // Reads input from all directions, all phone shaking
        val accelInput = floatArrayOf(
            input.accelerometerX,
            input.accelerometerY,
            input.accelerometerZ
        )

        // Loops through all shake inputs and calculates the gravity to be subtracted from the input
        for (i in 0..2) {
            gravity[i] = gravity[i] + alpha * (accelInput[i] - gravity[i]) // Updates gravity by only a small amount (alpha) to prevent jittering
            linearAcceleration[i] = accelInput[i] - gravity[i] // Subtracts gravity from the input, using only deliberate movement
        }

        // Calculates the magnitude of the movement, total movement with all directions combined
        val magnitude = sqrt(
            linearAcceleration[0] * linearAcceleration[0] +
                linearAcceleration[1] * linearAcceleration[1] +
                linearAcceleration[2] * linearAcceleration[2]
        )

        // Checks if the magnitude, scale of movement, has met the required threshold to initiate an attack
        if (magnitude > 12.0f) {
            triggerAOEAttack(entity, attackComp)
        }
    }

    // Starts the attack once the threshold is met
    private fun triggerAOEAttack(player: Entity, attack: AOEAttackComponent) {
        attack.currentCooldown = attack.cooldown // Attacking - reset cooldown
        val playerPos = TransformComponent.mapper[player]?.position ?: return

        factory.createAOERing(playerPos.x, playerPos.y, attack.range) // Creates the visual effect

        app.log("COMBAT", "Shake detected! Triggering AOE Attack: ${attack.damage} damage")

        // Defines bounds for the AOE attack
        val lowerX = playerPos.x - attack.range
        val lowerY = playerPos.y - attack.range
        val upperX = playerPos.x + attack.range
        val upperY = playerPos.y + attack.range

        // Queries the world for any entities within the bounds
        world.QueryAABB({ fixture ->
            val body = fixture.body
            val entity = body.userData as? Entity // Retrieve the Ashley Entity attached to the Box2D

            // Ensure the entity isn't a player and isn't null
            if (entity != null && entity != player && !PlayerComponent.mapper.has(entity)) {
                val enemyPos = TransformComponent.mapper[entity]?.position ?: return@QueryAABB true

                // Check whether the entity is within the attack range
                if (playerPos.dst(enemyPos) <= attack.range) {
                    applyDamage(entity, attack.damage) // Apply damage to the entity
                }
            }
            true // Continues checking for other collisions
        }, lowerX, lowerY, upperX, upperY)
    }

    // Temporary for testing
    private fun applyDamage(target: Entity, amount: Float) {
        val targetID = PlayerComponent.mapper[target]?.id
        app.log("COMBAT", "Entity $targetID took $amount damage!")
    }
}
