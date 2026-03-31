package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.maps.objects.PointMapObject
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer
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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import com.badlogic.gdx.maps.tiled.TmxMapLoader
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer
import com.badlogic.gdx.utils.viewport.FitViewport
import ktx.tiled.type
import ktx.tiled.x
import ktx.tiled.y
import java.nio.ByteBuffer
import java.nio.ByteOrder
import ktx.ashley.allOf

class GameScreen : KtxScreen {
    private val image = Texture("logo.png".toInternalFile(), true).apply { setFilter(Linear, Linear) }

    private val map = TmxMapLoader().load("Maps/PathfindingDemoRoom.tmx")
    val collisionGrid = BooleanArray(20 * 11)
    private val renderer = OrthogonalTiledMapRenderer(map, Constants.UNIT_SCALE)

    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: Viewport
    private val batch = SpriteBatch()
    private lateinit var atlas: TextureAtlas

    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var tcpSocket: Socket

    private val engine = PooledEngine()
    private lateinit var factory: EntityFactory
    private var playerID: Int = 0

    override fun show() {
        camera = OrthographicCamera()
        viewport = FitViewport(Constants.MAP_WIDTH.toFloat(), Constants.MAP_HEIGHT.toFloat(), camera)
        camera.position.set(Constants.MAP_WIDTH.toFloat() / 2, Constants.MAP_HEIGHT.toFloat() / 2, 0f)
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
                            Gdx.app.postRunnable {
                                val remoteEntity = engine.getEntitiesFor(allOf(PlayerComponent::class).get())
                                    .find { PlayerComponent.mapper[it]?.id == msg.id } ?: return@postRunnable

                                val startPos = TransformComponent.mapper[remoteEntity]!!.position
                                val startX = startPos.x.toInt()
                                val startY = startPos.y.toInt()

                                coroutineScope.launch(Dispatchers.Default) {
                                    val path = Pathfinding.findPath(startX, startY, msg.posX.toInt(), msg.posY.toInt()) { x, y ->
                                        if (x !in 0 until Constants.MAP_WIDTH || y !in 0 until Constants.MAP_HEIGHT) true
                                        else collisionGrid[y * Constants.MAP_WIDTH + x]
                                    }

                                    Gdx.app.postRunnable {
                                        PathComponent.mapper[remoteEntity]?.nodes?.apply {
                                            clear()
                                            addAll(path)
                                        }
                                    }
                                }
                            }
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

        val wallLayer = map.layers["Walls"] as? TiledMapTileLayer
        val doorLayer = map.layers["Doors_Closed"] as? TiledMapTileLayer

        for (y in 0 until Constants.MAP_HEIGHT) {
            for (x in 0 until Constants.MAP_WIDTH) {
                val isBlocked = wallLayer?.getCell(x, y) != null || doorLayer?.getCell(x, y) != null
                collisionGrid[y * Constants.MAP_WIDTH + x] = isBlocked
            }
        }
    }

    override fun render(delta: Float) {
        clearScreen(red = 0f, green = 0f, blue = 0f)
        renderer.setView(camera)
        renderer.render()

        engine.update(delta)

        update()
    }

    private fun update() {
        if (Gdx.input.justTouched()) {
            val touch = viewport.unproject(Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat()))
            val tileX = touch.x.toInt()
            val tileY = touch.y.toInt()

            if (tileX < 0 || tileX >= Constants.MAP_WIDTH || tileY < 0 || tileY >= Constants.MAP_HEIGHT) { return }
            if (collisionGrid[tileY * Constants.MAP_WIDTH + tileX]) return

            val currentReserved = mutableSetOf<Int>()
            engine.getEntitiesFor(allOf(PlayerComponent::class, TransformComponent::class).get()).forEach { entity ->
                val pComp = PlayerComponent.mapper[entity]
                if (pComp?.id != playerID) {
                    val pos = TransformComponent.mapper[entity]!!.position
                    currentReserved.add(pos.y.toInt() * 20 + pos.x.toInt())
                }
            }

            val playerEntity = engine.getEntitiesFor(allOf(PlayerComponent::class).get()).find {
                PlayerComponent.mapper[it]?.id == playerID
            } ?: return
            val startPos = TransformComponent.mapper[playerEntity]!!.position
            val startX = startPos.x.toInt()
            val startY = startPos.y.toInt()

            coroutineScope.launch(Dispatchers.Default) {
                val newPath = Pathfinding.findPath(startX, startY, tileX, tileY) { x, y ->
                    if (x !in 0 until Constants.MAP_WIDTH || y !in 0 until Constants.MAP_HEIGHT) true
                    else collisionGrid[y * Constants.MAP_WIDTH + x] || currentReserved.contains(y * Constants.MAP_WIDTH + x)
                }

                Gdx.app.postRunnable {
                    PathComponent.mapper[playerEntity]?.nodes?.apply {
                        clear()
                        addAll(newPath)
                    }
                }
                }

            coroutineScope.launch(Dispatchers.IO) {
                val moveMsg = GameMessage.PlayerMoveMessage(playerID, tileX + 0.5f, tileY + 0.5f)

                tcpSocket.getOutputStream().write(moveMsg.serialise())
                tcpSocket.getOutputStream().flush()
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
