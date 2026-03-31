package uk.ac.tees.e4109732.mam_dungeon_crawler

object Constants {
    const val IP_ADDRESS = "172.31.196.190"

    const val UNIT_SCALE = 1f / 8f
    const val MAP_WIDTH = 20
    const val MAP_HEIGHT = 11

    const val BIT_PLAYER: Short = 0x0001
    const val BIT_WALL: Short = 0x0002
    const val BIT_ENEMY: Short = 0x0004
    const val BIT_COLLECTABLE: Short = 0x0008
}
