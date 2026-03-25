package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.gdx.Gdx
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

class Character(private val sprite: Sprite, val speed: Float) {
    var posX: Float = 0f
        private set
    var posY: Float = 0f
        private set
    var targetX: Float = 0f
    var targetY: Float = 0f

    fun update(delta: Float) {
        val current = Vector2(posX, posY)
        val target = Vector2(targetX, targetY)
        val distance = current.dst(target)

        if (distance > 0.5f) {
            val movementAmount = speed * delta

            if (movementAmount >= distance) {
                snapTo(targetX, targetY)
            } else {
                val move = target.sub(current).nor().scl(movementAmount)
                posX += move.x
                posY += move.y
            }
        }
    }

    fun snapTo(x: Float, y: Float) {
        posX = x
        posY = y
        targetX = x
        targetY = y
    }

    fun draw(batch: SpriteBatch) {
        sprite.setPosition(posX - sprite.width / 2, posY - sprite.height / 2)
        sprite.draw(batch)
    }
}

class GameScreen : KtxScreen {
    private val playerTexture = Texture("circle.png".toInternalFile(), true).apply {  }
    private val image = Texture("logo.png".toInternalFile(), true).apply { setFilter(Linear, Linear) }
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
    private val playerColours = listOf(
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
            InetAddress.getByName("10.0.2.2"), 4301))

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
            sprite.setSize(128f, 128f)
            val character = Character(sprite, 300.toFloat())
            character.snapTo(spawnPoints[i].x, spawnPoints[i].y)
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
        draw()
    }

    private fun logic(delta: Float) {
        if (Gdx.input.isTouched) {
            val touch = viewport.unproject(Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat()))

            players[playerID].targetX = touch.x
            players[playerID].targetY = touch.y

            coroutineScope.launch(Dispatchers.IO) {
                val message = "$playerID,${touch.x},${touch.y}"
                tcpSocket.getOutputStream().write(message.toByteArray())
            }
        }

        for (player in players) {
            player.update(delta)
        }

        while (queue.isNotEmpty()) {
            val queueMsg = queue.poll()
            if (queueMsg != null && queueMsg.id != playerID) {
                players[queueMsg.id].targetX = queueMsg.posX
                players[queueMsg.id].targetY = queueMsg.posY
            }
        }
    }

    private fun draw() {
        clearScreen(red = 0.7f, green = 0.7f, blue = 0.7f)
        batch.projectionMatrix = camera.combined
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
