package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.gdx.math.Vector2
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Creates an interface used by 'GameMessage' this means each message must be serialisable -
// turnable into a ByteArray for efficient message sending
interface Serialisable {
    fun serialise(): ByteArray
}

// Holds all game message types and assigns them an ID
// This ID is used by the server, allowing it to recognise what message has been received nad how to deserialise it
enum class GameMessageType(val id: Byte) {
    PLAYER_MOVE(1),
    PLAYER_ATTACK(2),
    MAP_DATA(3),
    WORLD_STATE(4);

    // Companion object to help recognise message type, looking at the assigned byte
    companion object {
        fun fromByte(id: Byte) = entries.first { it.id == id }
    }
}

// Defines game messages
sealed class GameMessage(val type: GameMessageType) : Serialisable {
    // --------------------------------------------------------------------------------------------------
    // Message serialise function converts the byte order to little endian to match the C++ server
    // First byte is the message ID used to identify the message
    // Converts to byte array
    // Each message contains a companion object which has a deserialise function, this returns the message back into usable data
    // This can't be an interface as object doesn't exist at this point
    // --------------------------------------------------------------------------------------------------

    // Player movement - sends position
    data class PlayerMoveMessage(val id: Int, val posX: Float, val posY: Float): GameMessage(GameMessageType.PLAYER_MOVE) {
        // Capacity 13 as, Byte(1) + Int(4) + Float(4) + Float(4)
        override fun serialise(): ByteArray = ByteBuffer.allocate(13).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put(type.id)
            putInt(id)
            putFloat(posX)
            putFloat(posY)
        }.array()

        companion object {
            fun deserialise(bb: ByteBuffer): PlayerMoveMessage {
                return PlayerMoveMessage(bb.int, bb.float, bb.float)
            }
        }
    }

    // Player attack - sends position
    data class PlayerAttackMessage(val id: Int): GameMessage(GameMessageType.PLAYER_ATTACK) {
        // Capacity 5 as, Byte(1) + Int(4)
        override fun serialise(): ByteArray = ByteBuffer.allocate(5).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put(type.id)
            putInt(id)
        }.array()

        companion object {
            fun deserialise(bb: ByteBuffer): PlayerAttackMessage {
                return PlayerAttackMessage(bb.int)
            }
        }
    }

    // Map Data - sends the collision grid to the server
    data class MapDataMessage(val grid: ByteArray): GameMessage(GameMessageType.MAP_DATA) {
        // Capacity 221 as, Byte(1) + Bytes(220)
        // 220 = Grid Width (20) * Height (11)
        override fun serialise(): ByteArray = ByteBuffer.allocate(221).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put(type.id)
            put(grid)
        }.array()

        // Compares content and not memory address
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MapDataMessage) return false
            return grid.contentEquals(other.grid)
        }
        override fun hashCode(): Int {
            return grid.contentHashCode()
        }

        // Unlikely to be needed, no harm in adding though
        companion object {
            fun deserialise(bb: ByteBuffer): MapDataMessage {
                val g = ByteArray(220) // Fixed size 20 * 11
                bb.get(g)
                return MapDataMessage(g)
            }
        }
    }

    // World State - for receiving all player/entity positions
    data class WorldStateMessage(val positions: Map<Int, Vector2>) : GameMessage(GameMessageType.WORLD_STATE) {
        override fun serialise(): ByteArray {
            // Byte(1) + Int(4) + (count * Int(4) + Float(4) + Float(4))
            val capacity = 1 + 4 + (positions.size * 12)
            return ByteBuffer.allocate(capacity).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                put(type.id)
                putInt(positions.size)
                positions.forEach { (id, pos) ->
                    putInt(id)
                    putFloat(pos.x)
                    putFloat(pos.y)
                }
            }.array()
        }

        companion object {
            fun deserialise(bb: ByteBuffer): WorldStateMessage {
                val count = bb.int
                val positions = mutableMapOf<Int, Vector2>()
                repeat(count) {
                    val id = bb.int
                    val x = bb.float
                    val y = bb.float
                    positions[id] = Vector2(x, y)
                }
                return WorldStateMessage(positions)
            }
        }
    }
}

// Converts incoming messages from the server back into 'GameMessage's
class GameMessageFactory {
    // Creates 'GameMessage's from incoming ByteArrays
    fun create(ba: ByteArray): GameMessage? {
        val bb = ByteBuffer.wrap(ba).order(ByteOrder.LITTLE_ENDIAN) // Allows the array to be read

        val typeByte = bb.get()
        val type = try { GameMessageType.fromByte(typeByte) } catch (_: Exception) { return null } // Looks at the first byte and decides what type of message it is

        // Using that first byte assigns the correct message
        return when (type) {
            // Deserialises messages so they can be used
            GameMessageType.PLAYER_MOVE -> GameMessage.PlayerMoveMessage.deserialise(bb)
            GameMessageType.PLAYER_ATTACK -> GameMessage.PlayerAttackMessage.deserialise(bb)
            GameMessageType.MAP_DATA -> GameMessage.MapDataMessage.deserialise(bb)
            GameMessageType.WORLD_STATE -> GameMessage.WorldStateMessage.deserialise(bb)
        }
    }
}
