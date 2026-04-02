package uk.ac.tees.e4109732.mam_dungeon_crawler

import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.async.KtxAsync

class Main : KtxGame<KtxScreen>() {
    override fun create() {
        KtxAsync.initiate() // Allows for multi-threading

        addScreen(GameScreen())
        setScreen<GameScreen>() // Sets the screen to the actual game screen
    }
}
