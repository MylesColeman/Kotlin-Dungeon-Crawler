package uk.ac.tees.e4109732.mam_dungeon_crawler

import java.nio.ByteBuffer
import java.nio.ByteOrder

interface Serialisable {
    fun serialise(): ByteArray
}

enum class GameMessageType(val id: Byte) {
    PLAYER_MOVE(1);

    companion object {
        fun fromByte(id: Byte) = entries.first { it.id == id }
    }
}

sealed class GameMessage(val type: GameMessageType) : Serialisable {

    data class PlayerMoveMessage(val id: Int, val posX: Float, val posY: Float): GameMessage(GameMessageType.PLAYER_MOVE) {
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
}

class GameMessageFactory {
    fun create(ba: ByteArray): GameMessage {
        val bb = ByteBuffer.wrap(ba).order(ByteOrder.LITTLE_ENDIAN)
        val type = GameMessageType.fromByte(bb.get())

        return when (type) {
            GameMessageType.PLAYER_MOVE -> GameMessage.PlayerMoveMessage.deserialise(bb)
        }
    }
}
