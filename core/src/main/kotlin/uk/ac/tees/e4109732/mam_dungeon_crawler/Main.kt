package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Vector2
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
import com.badlogic.gdx.maps.tiled.TmxMapLoader
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer
import com.badlogic.gdx.utils.viewport.FitViewport

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
    private val map = TmxMapLoader().load("Maps/PathfindingDemoRoom.tmx")
    private val renderer = OrthogonalTiledMapRenderer(map, Constants.UNIT_SCALE)
    private val playerTexture = Texture("circle.png".toInternalFile(), true).apply {  }
    private val image = Texture("logo.png".toInternalFile(), true).apply { setFilter(Linear, Linear) }
    private val batch = SpriteBatch()
    private lateinit var players: List<Character>
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private val queue = ConcurrentLinkedQueue<Message>()
    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: Viewport
    private var mapWidth: Float = 20f
    private var mapHeight: Float = 11f
    private var playerID: Int = 0
    private lateinit var tcpSocket: Socket
    private val playerColours = listOf(
        com.badlogic.gdx.graphics.Color.BROWN,
        com.badlogic.gdx.graphics.Color.GREEN,
        com.badlogic.gdx.graphics.Color.BLUE,
    )
    private val spawnPoints = listOf(
        Vector2(5f, 5f),
        Vector2(2f, 2f),
        Vector2(18f, 9f),
    )

    override fun show() {
        camera = OrthographicCamera()
        viewport = FitViewport(mapWidth, mapHeight, camera)
        camera.position.set(mapWidth / 2, mapHeight / 2, 0f)
        camera.update()
        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)

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
            sprite.setSize(1f, 1f)
            val character = Character(sprite, 5f)
            character.snapTo(spawnPoints[i].x * Constants.UNIT_SCALE, spawnPoints[i].y * Constants.UNIT_SCALE)
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
        clearScreen(red = 0f, green = 0f, blue = 0f)
        renderer.setView(camera)
        renderer.render()

        batch.use(camera.combined) {
            for (player in players) {
                player.draw(it)
            }
        }

        logic(delta)
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

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        viewport.update(width, height)
        camera.update()
    }

    override fun dispose() {
        map.disposeSafely()
        renderer.disposeSafely()
        image.disposeSafely()
        playerTexture.disposeSafely()
        batch.disposeSafely()
    }
}
