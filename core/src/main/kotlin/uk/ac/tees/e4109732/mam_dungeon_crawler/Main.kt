package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.utils.viewport.Viewport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely
import ktx.assets.toInternalFile
import ktx.async.KtxAsync
import ktx.graphics.use
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue

class Main : KtxGame<KtxScreen>() {
    override fun create() {
        KtxAsync.initiate()

        addScreen(GameScreen())
        setScreen<GameScreen>()
    }
}

data class Message(var id: Int, var posX: Float, var posY: Float)

class Character(
    private val sprite: Sprite,
    val speed: Int
) {
    var posX: Float = 0f
        private set
    var posY: Float = 0f
        private set

    fun update(message: Message) {
        posX = message.posX
        posY = message.posY
    }

    fun draw(batch: SpriteBatch) {
        sprite.x = posX - sprite.texture.width / 2
        sprite.y = posY - sprite.texture.height / 2
        sprite.draw(batch)
    }
}

class GameScreen : KtxScreen {
    private val playerTexture = Texture("circle.png".toInternalFile(), true).apply {  }
    private val image = Texture("logo.png".toInternalFile(), true).apply { setFilter(Linear, Linear) } // Maybe remove
    private val batch = SpriteBatch()
    private lateinit var players: List<Character>
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private val queue = ConcurrentLinkedQueue<Message>()
    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: Viewport
    private var mapWidth: Float = 2400f
    private var mapHeight: Float = 1080f
    private var playerID: Int = 0
    private lateinit var tcpSocket: Socket
    private val playerColours = ListOf(
        com.badlogic.gdx.graphics.Color.BROWN,
        com.badlogic.gdx.graphics.Color.GREEN,
        com.badlogic.gdx.graphics.Color.BLUE,
    )
    private val spawnPoints = listOf(
        Vector2(700f, 500f),
        Vector2(700f, 100f),
        Vector2(100f, 500f),
    )

    override fun show() {
        camera = OrthographicCamera()
        mapWidth = Gdx.graphics.width.toFloat()
        mapHeight = Gdx.graphics.height.toFloat()
        camera = OrthographicCamera(mapWidth, mapHeight)
        camera.setToOrtho(false)
        viewport = ScreenViewport(camera)
        viewport.update(mapWidth.toInt(), mapHeight.toInt(), true)

        val datagramSocket = DatagramSocket()
        datagramSocket.send(DatagramPacket("HELLO".toByteArray(), 5,
            InetAddress.getByName("localhost"), 4301))

        val buffer = ByteArray(1024)
        val packet = DatagramPacket(buffer, buffer.size)
        datagramSocket.receive(packet)
        tcpSocket = Socket(packet.address, 4300)
        val inputStream = tcpSocket.getInputStream()
        var bytes = ByteArray(4)
        inputStream.read(bytes)
        playerID = bytes[0].toInt()
        players = List(3) { i ->
            val sprite = Sprite(playerTexture)
            sprite.color = playerColours[i]
            val character = Character(sprite, 300)
            character.update(Message(i, spawnPoints[i].x, spawnPoints[i].y))
            character
        }

        coroutineScope.launch {
            while (true) {
                bytes = ByteArray(1024)
                val length = inputStream.read(bytes)
                if (length > 0) {
                    val message = String(bytes, 0, length).split(",")
                    if (message.size == 3) {
                        val id = message[0].toInt()
                        val posX = message[1].toFloat()
                        val posY = message[2].toFloat()
                        queue.add(Message(id, posX, posY))
                    }
                }
            }
        }
    }

    override fun render(delta: Float) {
        logic(delta)
        draw(delta)
    }

    private fun logic(delta: Float) {
        super.show()
        val moveSpeed = delta * players[playerID].speed
        val oldPosition = Vector2(players[playerID].posX, players[playerID].posY)
        val newPosition = Vector2(players[playerID].posX, players[playerID].posY)
        if (Gdx.input.isKeyPressed(Input.Keys.W)) newPosition.y += moveSpeed
        if (Gdx.input.isKeyPressed(Input.Keys.A)) newPosition.x -= moveSpeed
        if (Gdx.input.isKeyPressed(Input.Keys.S)) newPosition.y -= moveSpeed
        if (Gdx.input.isKeyPressed(Input.Keys.D)) newPosition.x += moveSpeed

        newPosition.x = newPosition.x.coerceIn(playerTexture.width/2f, mapWidth - playerTexture.width/2f)
        newPosition.y = newPosition.y.coerceIn(playerTexture.height/2f, mapWidth - playerTexture.height/2f)

        if (oldPosition != newPosition) {
            val message = Message(playerID, newPosition.x, newPosition.y)
            players[playerID].update(message)
            val byes = "${playerID},${newPosition.x},${newPosition.y}".toByteArray()
            coroutineScope.launch {
                delay(100)
                tcpSocket.getOutputStream().write(byes)
            }
        }

        while (queue.isNotEmpty()) {
            val queueMsg = queue.poll()
            if (queueMsg != null) {
                players[queueMsg.id].update(queueMsg)
            }
        }
    }

    private fun draw(delta: Float) {
        clearScreen(red = 0.7f, green = 0.7f, blue = 0.7f)
        batch.use {
            for (player in players) {
                player.draw(it)
            }
        }
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        viewport.update(width, height)
        camera.update()
        mapWidth = width.toFloat()
        mapHeight = height.toFloat()
    }

    override fun dispose() {
        image.disposeSafely()
        playerTexture.disposeSafely()
        batch.disposeSafely()
    }
}
