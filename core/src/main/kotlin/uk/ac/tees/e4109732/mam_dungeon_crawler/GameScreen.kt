package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.maps.objects.PointMapObject
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.Viewport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely
import ktx.assets.toInternalFile
import ktx.graphics.use
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import com.badlogic.gdx.maps.tiled.TmxMapLoader
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer
import com.badlogic.gdx.utils.viewport.FitViewport
import ktx.ashley.add
import ktx.tiled.type
import ktx.tiled.x
import ktx.tiled.y
import java.nio.ByteBuffer
import java.nio.ByteOrder
import ktx.ashley.allOf

class GameScreen : KtxScreen {
    private val map = TmxMapLoader().load("Maps/PathfindingDemoRoom.tmx")
    private val renderer = OrthogonalTiledMapRenderer(map, Constants.UNIT_SCALE)
    private val image = Texture("logo.png".toInternalFile(), true).apply { setFilter(Linear, Linear) }
    private val batch = SpriteBatch()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private val queue = ConcurrentLinkedQueue<Message>()
    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: Viewport
    private var mapWidth: Float = 20f
    private var mapHeight: Float = 11f
    private var playerID: Int = 0
    private lateinit var tcpSocket: Socket

    private val engine = PooledEngine()
    private lateinit var atlas: TextureAtlas
    private lateinit var factory: EntityFactory

    override fun show() {
        camera = OrthographicCamera()
        viewport = FitViewport(mapWidth, mapHeight, camera)
        camera.position.set(mapWidth / 2, mapHeight / 2, 0f)
        camera.update()
        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)

        val datagramSocket = DatagramSocket()
        datagramSocket.send(DatagramPacket("HELLO".toByteArray(), 5,
            InetAddress.getByName(Constants.IP_ADDRESS), 4301))

        val buffer = ByteArray(1024)
        val packet = DatagramPacket(buffer, buffer.size)
        datagramSocket.receive(packet)
        tcpSocket = Socket(packet.address, 4300)
        val inputStream = tcpSocket.getInputStream()
        val idBytes = ByteArray(4)
        var totalIdBytesRead = 0

        while (totalIdBytesRead < 4) {
            val read = inputStream.read(idBytes, totalIdBytesRead, 4 - totalIdBytesRead)
            if (read == -1) throw Exception("Socket closed before Player ID was received")
            totalIdBytesRead += read
        }

        val idBuffer = ByteBuffer.wrap(idBytes).order(ByteOrder.LITTLE_ENDIAN)
        playerID = idBuffer.int
        Gdx.app.log("NETWORK", "Assigned Player ID: $playerID")

        coroutineScope.launch {
            try {
                val factory = GameMessageFactory()
                while (true) {
                    val readBuffer = ByteArray(13)
                    var bytesRead = 0

                    while (bytesRead < 13) {
                        val result = inputStream.read(readBuffer, bytesRead, 13 - bytesRead)
                        if (result == -1) break
                        bytesRead += result
                    }

                    if (bytesRead == 13) {
                        val msg = factory.create(readBuffer)

                        if (msg is GameMessage.PlayerMoveMessage && msg.id != playerID) {
                            queue.add(Message(msg.id, msg.posX, msg.posY))
                        }
                    }
                }
            } catch (e: Exception) {
                Gdx.app.error("NETWORK", "Connection Lost: ${e.message}")
            }

        }

        atlas = TextureAtlas("DungeonCrawlerEntities.atlas".toInternalFile())
        factory = EntityFactory(engine, atlas)
        engine.addSystem(MovementSystem())
        engine.addSystem(AnimationSystem())
        engine.addSystem(RenderSystem(batch, camera))

        val spawnLayer = map.layers["Spawn_Points"]?.objects ?: throw Exception("Spawn_Points layer not found")
        val spawnPoints = spawnLayer.filterIsInstance<PointMapObject>().filter { it.type == "SpawnPoint" }
        spawnPoints.forEachIndexed { index, spawnPoint ->
            factory.createPlayer(index, spawnX = spawnPoint.x * Constants.UNIT_SCALE, spawnY = spawnPoint.y * Constants.UNIT_SCALE)
        }
    }

    override fun render(delta: Float) {
        clearScreen(red = 0f, green = 0f, blue = 0f)
        renderer.setView(camera)
        renderer.render()

        engine.update(delta)

        logic()
    }

    private fun logic() {
        if (Gdx.input.isTouched) {
            val touch = viewport.unproject(Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat()))

            engine.getEntitiesFor(allOf(PlayerComponent::class).get()).forEach { entity ->
                val pComp = PlayerComponent.mapper[entity]
                if (pComp?.id == playerID) {
                    MovementComponent.mapper[entity]?.target?.set(touch.x, touch.y)
                }
            }

            coroutineScope.launch(Dispatchers.IO) {
                val moveMsg = GameMessage.PlayerMoveMessage(playerID, touch.x, touch.y)

                tcpSocket.getOutputStream().write(moveMsg.serialise())
                tcpSocket.getOutputStream().flush()
            }
        }

        while (queue.isNotEmpty()) {
            val queueMsg = queue.poll() ?: continue
            engine.getEntitiesFor(allOf(PlayerComponent::class).get()).forEach { entity ->
                val pComp = PlayerComponent.mapper[entity]
                if (pComp?.id == queueMsg.id) {
                    MovementComponent.mapper[entity]?.target?.set(queueMsg.posX, queueMsg.posY)
                }
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
        atlas.disposeSafely()
        batch.disposeSafely()
    }
}
