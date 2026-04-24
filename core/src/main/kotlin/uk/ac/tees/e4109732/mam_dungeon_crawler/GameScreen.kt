package uk.ac.tees.e4109732.mam_dungeon_crawler

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Gdx.input
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
import kotlinx.coroutines.*
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely
import ktx.assets.toInternalFile
import java.net.Socket
import com.badlogic.gdx.maps.tiled.TmxMapLoader
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.viewport.FitViewport
import ktx.tiled.type
import ktx.tiled.x
import ktx.tiled.y
import java.nio.ByteBuffer
import java.nio.ByteOrder
import ktx.ashley.allOf
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

// Main game screen
class GameScreen : KtxScreen {
    // Default game logo - should be changed
    private val image = Texture("logo.png".toInternalFile(), true).apply { setFilter(Linear, Linear) }

    // Tiled map variables
    private val map = TmxMapLoader().load("Maps/PathfindingDemoRoom.tmx")
    val collisionGrid = BooleanArray(Constants.MAP_WIDTH * Constants.MAP_HEIGHT) // Used to determine obstacle locations
    private val renderer = OrthogonalTiledMapRenderer(map, Constants.UNIT_SCALE) // Orthogonal like original Zelda, 1 / 8 unit scale

    // Rendering pipeline
    private lateinit var camera: OrthographicCamera // Converts world units to screen coordinates, orthogonal like original Zelda
    private lateinit var hudCamera: OrthographicCamera // Stationary camera for the HUD
    private lateinit var viewport: Viewport
    private val batch = SpriteBatch()
    // Memory efficient collection of texture regions, uses one large image opposed to multiple small ones
    private lateinit var atlas: TextureAtlas

    // Networking and asynchrony
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job()) // Used to manage background tasks
    private var tcpSocket: Socket? = null // Connection to game server, tcp so its persistent

    // Component variables
    private val engine = PooledEngine() // Manages entities, their systems and components
    private lateinit var factory: EntityFactory // Used to create entities

    // Box2D physics
    private val world = World(Vector2(0f, 0f), true) // Gravity set to 0 as 2D topdown game

    // Player variables
    private var playerID: Int = -1 // Defaults to -1, i.e. not set
    private var pathValidationTimer = 0f // Timer to check whether path is still valid every so often
    private var lastServerTick: Int = 0 // Last tick received from the server for lag compensation

    // Shouts across the LAN to find the server's IP
    private fun discoverServerIP(): String {
        var serverIp = Constants.IP_ADDRESS
        try {
            val udpSocket = DatagramSocket()
            udpSocket.broadcast = true
            udpSocket.soTimeout = 2000 // Gives up after two seconds

            val sendData = "PING".toByteArray()
            val sendPacket = DatagramPacket(sendData, sendData.size, InetAddress.getByName("255.255.255.255"), 4301) // Sends across the LAN to the server's port
            udpSocket.send(sendPacket)

            // Listens for the server's echo
            val receiveData = ByteArray(1024)
            val receivePacket = DatagramPacket(receiveData, receiveData.size)
            udpSocket.receive(receivePacket)

            serverIp = receivePacket.address.hostAddress // Update IP to be used by the client

            udpSocket.close()
        } catch (_: Exception) {
            Gdx.app.error("NETWORK", "UDP Discovery failed, falling back to constant IP.")
        }
        return serverIp
    }

    // Sets up the initial state of the game screen
    // The view, texture atlas, entity factory and it's systems, the grid logic (obstacles etc),
    // and networking done so via a background coroutine used to connect to the server
    override fun show() {
        // Sets up the camera and viewport to cover only the Tiled grid, with an orthographic view similar to the original Zelda's
        camera = OrthographicCamera()
        // Uses fit viewport to ensure nothing is lost, scaled correctly - using black bars if viewport too small for screen
        viewport = FitViewport(Constants.MAP_WIDTH.toFloat(), Constants.MAP_HEIGHT.toFloat(), camera)
        camera.position.set(Constants.MAP_WIDTH.toFloat() / 2, Constants.MAP_HEIGHT.toFloat() / 2, 0f) // Centres the camera in the grid
        camera.update()
        // Centres the camera on the viewport, and ensures it uses the screen's size not the world's
        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)

        // Sets up the HUD camera to only cover the screen, with a orthographic view
        hudCamera = OrthographicCamera()
        hudCamera.setToOrtho(false, Constants.MAP_WIDTH.toFloat(), Constants.MAP_HEIGHT.toFloat())

        atlas = TextureAtlas("DungeonCrawler.atlas".toInternalFile()) // Atlas holding all textures, more efficient than individual images
        factory = EntityFactory(engine, atlas, world) // Creates an instance of the entity factory to create entities for this screen
        // Adds the used systems to the engine
        engine.addSystem(MovementSystem())
        engine.addSystem(AnimationSystem())
        engine.addSystem(EffectSystem())
        engine.addSystem(RenderSystem(batch, camera))

        val spawnPoints = setupCollisionGrid() // Uses the Tiled map to setup a collision grid, using the obstacle layers

        // Launches a coroutine in the background, this is used to establish a connection with the server - tcp so its persistent
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val discoveredIP = discoverServerIP()
                Gdx.app.log("NETWORK", "Connecting to $discoveredIP.")
                // Creates a socket which attempts to connect to the server, it has a 5 second timeout before failing
                val socket = Socket()
                socket.connect(InetSocketAddress(discoveredIP, 4300), 5000)
                tcpSocket = socket

                val inputStream = socket.getInputStream() // Data flowing in from the server

                val idBytes = ByteArray(4) // The size of an int, which determines player ID
                var totalIdBytesRead = 0

                // Continues listening till 4 bytes are received
                while (totalIdBytesRead < 4) {
                    // Fills the idBytes array from totalIdBytesRead till we have 4 total
                    val read = inputStream.read(idBytes, totalIdBytesRead, 4 - totalIdBytesRead)
                    if (read == -1) throw Exception("Socket closed before Player ID was received")
                    totalIdBytesRead += read
                }

                // Takes the 4 collected bytes (ensuring they're read the same way the server wrote them (little endian)) and combines them into an int
                playerID = ByteBuffer.wrap(idBytes).order(ByteOrder.LITTLE_ENDIAN).int
                Gdx.app.log("NETWORK", "Connected! Assigned ID: $playerID")

                // Sets up the initial position of the player for the server
                val mySpawn = spawnPoints.getOrElse(playerID) { spawnPoints.first() }
                val startX = mySpawn.x * Constants.UNIT_SCALE
                val startY = mySpawn.y * Constants.UNIT_SCALE

                // Updates the render system with the local player's ID, adds the UI system once the player has an assigned ID, and creates the player
                Gdx.app.postRunnable {
                    engine.getSystem(RenderSystem::class.java).localPlayerId = playerID
                    engine.addSystem(UISystem(batch, hudCamera, atlas, playerID))
                    factory.createPlayer(playerID, startX, startY)
                }

                // Sends the initial player start pos to the server
                val initialPosMsg = GameMessage.PlayerMoveMessage(playerID, startX, startY)
                val initialPosBytes = initialPosMsg.serialise()
                GameMessage.applyXor(initialPosBytes) // Encrypts the message
                tcpSocket?.getOutputStream()?.write(initialPosBytes)
                Gdx.app.log("NETWORK", "Initial position synced: ($startX, $startY)")

                val gridBytes = collisionGrid.map { if (it) 1.toByte() else 0.toByte() }.toByteArray()
                val mapMsg = GameMessage.MapDataMessage(gridBytes)
                val mapBytes = mapMsg.serialise()
                GameMessage.applyXor(mapBytes) // Encrypts the message
                tcpSocket?.getOutputStream()?.write(mapBytes)

                Gdx.app.log("NETWORK", "Map Data Synced: ${gridBytes.size} tiles")

                // Adds the system once the ID is received, ensuring the correct player's attack is handled
                Gdx.app.postRunnable {
                    engine.addSystem(AttackSystem(playerID, factory, { lastServerTick }) { msg ->
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val attackBytes = msg.serialise()
                                GameMessage.applyXor(attackBytes) // Encrypts the message

                                tcpSocket?.getOutputStream()?.write(attackBytes)
                            } catch (e: Exception) {
                                Gdx.app.error("NETWORK", "Failed to send attack: ${e.message}")
                            }
                        }
                    })
                }

                runNetworkListener(inputStream) // Once player ID is read the input stream is passed to this function to be used elsewhere
            } catch (e: Exception) {
                Gdx.app.error("NETWORK", "Connection Lost: ${e.message}")
            }
        }
    }

    // Sets up the collision grid and handles player spawn points
    private fun setupCollisionGrid(): List<PointMapObject> {
        // Looks at the specific layer, specific objects of specific type which is used for player spawn points and puts them in a list
        val spawnLayer = map.layers["Spawn_Points"]?.objects ?: throw Exception("Spawn_Points layer not found")
        val spawnPoints = spawnLayer.filterIsInstance<PointMapObject>().filter { it.type == "SpawnPoint" }

        // Gets reference to the layers used as obstacles
        val wallLayer = map.layers["Walls"] as? TiledMapTileLayer
        val doorLayer = map.layers["Doors_Closed"] as? TiledMapTileLayer

        // Loops through the 2D tiled grid converting it to a 1D boolean array
        for (y in 0 until Constants.MAP_HEIGHT) {
            for (x in 0 until Constants.MAP_WIDTH) {
                // Tiles are blocked if they contain an object of this layers
                val isBlocked = wallLayer?.getCell(x, y) != null || doorLayer?.getCell(x, y) != null
                collisionGrid[y * Constants.MAP_WIDTH + x] = isBlocked // Turns the 2D tiled grid into a 1D boolean array
            }
        }

        return spawnPoints
    }

    // Continues running in the background listening to messages from the server
    private fun runNetworkListener(inputStream: InputStream) {
        val msgFactory = GameMessageFactory() // Creates an instance of the message factory, to deserialise incoming messages
        try {
            // Loops whilst the coroutine is still active in the background
            while (coroutineScope.isActive) {
                // Reads the type of message to determine the length
                val typeByte = inputStream.read()
                if (typeByte == -1) return // Message not read/invalid

                val type = try { GameMessageType.fromByte(typeByte.toByte()) } catch(_: Exception) { continue }

                // Checks if the message is dynamic or fixed
                val fullMsg = if (type == GameMessageType.WORLD_STATE) {
                    // Buffer to read the header
                    val headerBuffer = ByteArray(9) // Holds the header of the message
                    headerBuffer[0] = typeByte.toByte()
                    var headerRead = 0

                    // Continues reading till the header is read
                    while (headerRead < 8) {
                        // Fills the 'headerBuffer' array from countRead till we have the total
                        val result = inputStream.read(headerBuffer, 1 + headerRead, 8 - headerRead)
                        if (result == -1) return
                        headerRead += result
                    }

                    GameMessage.applyXor(headerBuffer) // Decrypts only the header, so count can be read

                    // Convert those 4 raw bytes into a readable Integer using Little Endian (C++ standard)
                    val bbHeader = ByteBuffer.wrap(headerBuffer).order(ByteOrder.LITTLE_ENDIAN)
                    bbHeader.get() // Skips the type byte
                    lastServerTick = bbHeader.int
                    val count = bbHeader.int

                    // Calculates how many more remaining bytes there are, each position is 12 bytes
                    val remainingSize = count * 12
                    val readBuffer = ByteArray(remainingSize)
                    var bytesRead = 0
                    // Loops again till all entities data is received
                    while (bytesRead < remainingSize) {
                        val result = inputStream.read(readBuffer, bytesRead, remainingSize - bytesRead)
                        if (result == -1) return
                        bytesRead += result
                    }

                    // Constructs the final buffer which contains everything
                    ByteBuffer.allocate(9 + remainingSize).apply {
                        put(headerBuffer)
                        put(readBuffer)
                    }.array()
                } else {
                    val remainingSize = when (type) {
                        GameMessageType.PLAYER_MOVE -> 12 // ID (4) + xPos (4) + yPos (4)
                        GameMessageType.PLAYER_ATTACK -> 8 // ID (4) + tick (4)
                        GameMessageType.MAP_DATA -> 220 // Width (20) * Height (11)
                        GameMessageType.ENTITY_DAMAGED -> 8 // TargetID (4) + Health (4)
                        else -> 0 // To ignore dynamic sized messages
                    }

                    val readBuffer = ByteArray(remainingSize) // The size of a message
                    var bytesRead = 0

                    // Continues listening till all 13 bytes are read
                    while (bytesRead < remainingSize) {
                        // Fills the readBuffer array from bytesRead till we have the total
                        val result = inputStream.read(readBuffer, bytesRead, remainingSize - bytesRead)
                        if (result == -1) return
                        bytesRead += result
                    }

                    // Reconstructs and deserialises the message
                    val assembledArray = ByteArray(remainingSize + 1)
                    assembledArray[0] = typeByte.toByte()
                    System.arraycopy(readBuffer, 0, assembledArray, 1, remainingSize)
                    assembledArray
                }

                val decryptStart = if (type == GameMessageType.WORLD_STATE) 9 else 1 // Determines when to start the encryption, as to skip the header
                GameMessage.applyXor(fullMsg, decryptStart) // Decrypts the message

                val msg = msgFactory.create(fullMsg) // Message full, send to factory to be deserialised

                // If the message isn't null handles messages from the server
                if (msg != null) {
                    Gdx.app.postRunnable {
                        when (msg) {
                            is GameMessage.PlayerMoveMessage -> if (msg.id != playerID) handleRemoteMove(msg)
                            is GameMessage.PlayerAttackMessage -> if (msg.id != playerID) handleRemoteAttack(msg)
                            is GameMessage.WorldStateMessage -> reconcileWorldState(msg)
                            is GameMessage.EntityDamagedMessage -> handleEntityDamaged(msg)
                            else -> {}
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Gdx.app.error("NETWORK", "Listener Lost: ${e.message}")
        }
    }

    // Handles the movement of remote players
    private fun handleRemoteMove(msg: GameMessage.PlayerMoveMessage) {
        // Moving player's is not thread safe, this moves it to be done so on the main thread at the start of the next frame
        Gdx.app.postRunnable {
            // Find the entity whose ID matches the one from the message
            val remoteEntity = engine.getEntitiesFor(allOf(PlayerComponent::class).get())
                .find { PlayerComponent.mapper[it]?.id == msg.id } ?: return@postRunnable

            val startPos = TransformComponent.mapper[remoteEntity]!!.position

            // If initial movement message, i.e. move to spawn point; just teleport
            if (startPos.x < 0.5f && startPos.y < 0.5f) {
                startPos.set(msg.posX, msg.posY)

                MovementComponent.mapper[remoteEntity]?.target?.set(msg.posX, msg.posY) // Sets the target to the new position
                AnimationComponent.mapper[remoteEntity]?.currentState = "walk_down" // Force default idle state

                PathComponent.mapper[remoteEntity]?.nodes?.clear() // Clear path
                return@postRunnable
            }

            // Launches a coroutine in the background, one optimised for CPU tasks to keep fps high,
            // done so to find a path for the remote player
            coroutineScope.launch(Dispatchers.Default) {
                // Finds a path from the remote player's start pos to their received target from the server
                val path = Pathfinding.findPath(startPos.x.toInt(), startPos.y.toInt(),
                    msg.posX.toInt(), msg.posY.toInt()) { x, y ->
                    // Checks their path against the collision grid, avoiding obstacles
                    if (x !in 0 until Constants.MAP_WIDTH || y !in 0 until Constants.MAP_HEIGHT) true
                    else collisionGrid[y * Constants.MAP_WIDTH + x]
                }
                // Return to the main thread to apply the new path for the entity, as doing so on a thread is not safe
                Gdx.app.postRunnable {
                    PathComponent.mapper[remoteEntity]?.nodes?.apply {
                        // Clears the current path, replacing it with the new one
                        clear()
                        addAll(path)
                    }
                }
            }
        }
    }

    // Handles the attacks of remote players
    private fun handleRemoteAttack(msg: GameMessage.PlayerAttackMessage) {
        val remoteEntity = engine.getEntitiesFor(allOf(PlayerComponent::class).get())
            .find { PlayerComponent.mapper[it]?.id == msg.id } ?: return

        val pos = TransformComponent.mapper[remoteEntity]?.position ?: return
        val attackComp = AOEAttackComponent.mapper[remoteEntity] ?: return

        // Draw the visual ring at the remote player's location
        factory.createAOERing(pos.x, pos.y, attackComp.range)

        Gdx.app.log("COMBAT", "Remote player ${msg.id} performed an attack.")
    }

    // Keeps clients in sync with the server's logic
    private fun reconcileWorldState(msg: GameMessage.WorldStateMessage) {
        val playerEntities = engine.getEntitiesFor(allOf(PlayerComponent::class, TransformComponent::class).get())

        msg.positions.forEach { (id, serverPos) ->
            val entity = playerEntities.find { PlayerComponent.mapper[it].id == id }

            // Checks if the entity exists
            if (entity != null) {
                val transform = TransformComponent.mapper[entity]
                val localPos = transform.position

                Gdx.app.log("DEBUG_SYNC", "Drift: $localPos.dst2(serverPos) | Local: $localPos | Server: $serverPos")

                // Local player prioritises local pathfinding, unless massively wrong
                val isLocalPlayer = (id == playerID)
                val pathComp = PathComponent.mapper[entity]
                val isMoving = pathComp != null && pathComp.nodes.isNotEmpty()

                var isCollidingLocally = false
                if (isLocalPlayer) {
                    playerEntities.forEach { other ->
                        if (other != entity) {
                            val otherHealth = HealthComponent.mapper[other]
                            if (otherHealth == null || otherHealth.currentHearts > 0) {
                                val otherPos = TransformComponent.mapper[other].position
                                if (localPos.dst2(otherPos) < 0.8f) {
                                    isCollidingLocally = true
                                }
                            }
                        }
                    }
                }

                val allowedDrift = when {
                    !isMoving -> 0.001f
                    isCollidingLocally -> 0.1f
                    isLocalPlayer -> 2.25f
                    else -> 0.25f
                }

                // If distance is too far away, correct it
                if (localPos.dst2(serverPos) > allowedDrift) {
                    if (isLocalPlayer) {
                        localPos.set(serverPos)
                        MovementComponent.mapper[entity]?.target?.set(serverPos) // Update target to match server

                        val pathComp = PathComponent.mapper[entity]
                        if (pathComp != null && pathComp.nodes.isNotEmpty()) {
                            val finalGoal = pathComp.nodes.last()
                            pathComp.nodes.clear()
                            requestPath(entity, finalGoal.x.toInt(), finalGoal.y.toInt()) // Requests a new path
                        } else
                            pathComp?.nodes?.clear()
                    } else {
                        localPos.lerp(serverPos, 0.3f)
                        MovementComponent.mapper[entity]?.target?.set(serverPos) // Update target to match server
                    }
                }
            } else {
                // The server knows someone we don't create them at the coords
                factory.createPlayer(id, serverPos.x, serverPos.y)
                Gdx.app.log("NETWORK", "New player $id synced into world.")
            }
        }
    }

    //
    private fun handleEntityDamaged(msg: GameMessage.EntityDamagedMessage) {
        // Find the entity whose ID matches the target
        val entity = engine.getEntitiesFor(allOf(PlayerComponent::class).get()).find { PlayerComponent.mapper[it]?.id == msg.targetId } ?: return

        val healthComponent = HealthComponent.mapper[entity]
        if (healthComponent != null) {
            healthComponent.currentHearts = msg.health // Updates the health with the authoritative value from the server
            Gdx.app.log("COMBAT", "Entity ${msg.targetId} health sync: ${msg.health} hearts")
        }
    }

    // Handles rendering for the screen, as well as calling game updates
    override fun render(delta: Float) {
        clearScreen(red = 0f, green = 0f, blue = 0f) // Clears the screen - black, ensuring a clean slate each time render is called
        renderer.setView(camera) // So only visible tiles are drawn
        renderer.render() // Draws the actual Tiled map

        // Advances the world 60 times per second, 6 and 2 chosen as industry-standard - allows for good performance on top of good physics
        world.step(1/60f, 6, 2)

        engine.update(delta) // Loops through the added systems in order, updating them

        update(delta) // Handles player input
    }

    // Handles player update and calls validate paths, to ensure paths remain valid
    private fun update(deltaTime: Float) {
        val playerEntity = engine.getEntitiesFor(allOf(PlayerComponent::class).get()).find {
            PlayerComponent.mapper[it]?.id == playerID
        } ?: return

        val healthComp = HealthComponent.mapper[playerEntity]
        if (healthComp != null && healthComp.currentHearts <= 0) return // Player is dead, don't update

        // Checks whether the screen was JUST touched, this prevents holding/redundant calls
        if (input.justTouched()) {
            // Translates the touch coordinates to where it relates to in the game world
            val touch = viewport.unproject(Vector2(input.x.toFloat(), input.y.toFloat()))
            val tileX = touch.x.toInt()
            val tileY = touch.y.toInt()

            // Checks whether the touch is within the game grid
            if (tileX in 0 until Constants.MAP_WIDTH && tileY in 0 until Constants.MAP_HEIGHT) {
                val isBlocked = collisionGrid[tileY * Constants.MAP_WIDTH + tileX]

                // Check to ensure path isn't blocked
                if (!isBlocked) {
                    // Locate the player entity using the component, looking for a matching ID
                    val playerEntity = engine.getEntitiesFor(allOf(PlayerComponent::class).get())
                        .find { PlayerComponent.mapper[it]?.id == playerID } ?: return

                    requestPath(playerEntity, tileX, tileY) // Requests a path to where was clicked from the current position


                    val socket = tcpSocket
                    // Checks whether the socket is active, connected, and not closed - so the message can actually be sent
                    if (socket != null && socket.isConnected && !socket.isClosed) {
                        // Launches a background coroutine to send a message to the server, containing the player's target position
                        // Done here as writing to a network is blocking
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                // Adds 0.5f to 'x' and 'y' so it's the centre of a tile
                                val moveMsg = GameMessage.PlayerMoveMessage(playerID, tileX + 0.5f, tileY + 0.5f)
                                val moveBytes = moveMsg.serialise()

                                GameMessage.applyXor(moveBytes) // Encrypts the message

                                // Lock the stream so rapid double-taps queue safely
                                synchronized(socket.getOutputStream()) {
                                    socket.getOutputStream().write(moveBytes) // Sends the serialised message to the server
                                    socket.getOutputStream().flush()
                                }
                            } catch (e: Exception) {
                                Gdx.app.error("NETWORK", "Send Error: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
        validatePaths(deltaTime) // Checks whether a path is valid every 0.2 seconds
    }

    // Requests a safe path from start pos to goal
    private fun requestPath(entity: Entity, goalX: Int, goalY: Int) {
        val moveComp = MovementComponent.mapper[entity] ?: return

        // Uses the target tile position to avoid floats
        val startX = moveComp.target.x.toInt()
        val startY = moveComp.target.y.toInt()

        val currentReserved = mutableSetOf<Int>() // Mutable set of reserved tiles, currently occupied
        // Find all players using their components
        engine.getEntitiesFor(allOf(PlayerComponent::class, TransformComponent::class).get()).forEach { other ->
            // Ignore this client's player
            if (other != entity) {
                val health = HealthComponent.mapper[other]
                // Don't pathfind around dead players
                if (health == null || health.currentHearts > 0) {
                    val pos = TransformComponent.mapper[other]!!.position
                    currentReserved.add(pos.y.toInt() * Constants.MAP_WIDTH + pos.x.toInt()) // Adds other client's players to the reserved set
                }
            }
        }

        // Launches a background coroutine, one optimised for CPU tasks to keep fps high, to run the pathfinding algorithm
        coroutineScope.launch(Dispatchers.Default) {
            Gdx.app.log("DEBUG_SYNC", "CLIENT A* START: ($startX, $startY) to ($goalX, $goalY)")
            // Generates a new path, ensuring its within the grid and avoids obstacles
            val newPath = Pathfinding.findPath(startX, startY, goalX, goalY) { x, y ->
                if (x !in 0 until Constants.MAP_WIDTH || y !in 0 until Constants.MAP_HEIGHT) true // Checks whether within grid
                else collisionGrid[y * Constants.MAP_WIDTH + x] || currentReserved.contains(y * Constants.MAP_WIDTH + x) // Avoids obstacles and other players
            }

            // Return to the main thread to apply the new path for the entity, as doing so on a thread is not safe
            Gdx.app.postRunnable {
                val pathComp = PathComponent.mapper[entity]
                pathComp?.nodes?.apply {
                    // Clears the current path, replacing it with the new one
                    clear()
                    addAll(newPath)
                }

                // Forced ignore target, align with new path
                if (newPath.isNotEmpty()) {
                    MovementComponent.mapper[entity]?.target?.set(newPath.first())
                }
            }
        }
    }

    // Checks whether a path is still valid, not blocked by other player for example, every 0.2 seconds
    private fun validatePaths(deltaTime: Float) {
        pathValidationTimer += deltaTime // Increases timer for checking whether path is valid
        if (pathValidationTimer < 0.1f) return // Checks whether 0.1 seconds have elapsed, only checks that often as to not waste computation
        pathValidationTimer = 0f // Resets timer once 0.2 seconds have elapsed

        val localPlayer = engine.getEntitiesFor(allOf(PlayerComponent::class, PathComponent::class).get()).find { PlayerComponent.mapper[it]?.id == playerID } ?: return

        val path = PathComponent.mapper[localPlayer]?.nodes ?: return
        if (path.isEmpty()) return // No path to validate

        val isPathObstructed = path.any { node -> isTileOccupiedByOther(node.x.toInt(), node.y.toInt(), localPlayer) } // Is another player blocking a tile in the path

        if (isPathObstructed) {
            val goal = path.last() // Same destination, different path

            requestPath(localPlayer, goal.x.toInt(), goal.y.toInt())

            val socket = tcpSocket
            if (socket != null && socket.isConnected && !socket.isClosed) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val moveMsg = GameMessage.PlayerMoveMessage(playerID, goal.x, goal.y)
                        val moveBytes = moveMsg.serialise()

                        GameMessage.applyXor(moveBytes) // Encrypts the message

                        // Locks for thread safety
                        synchronized(socket.getOutputStream()) {
                            socket.getOutputStream().write(moveBytes) // Sends the serialised message to the server
                            socket.getOutputStream().flush()
                        }
                    } catch (e: Exception) {
                        Gdx.app.error("NETWORK", "Send Error: ${e.message}")
                    }
                }
            }
        }
    }

    // Checks whether a tile is occupied by another player
    private fun isTileOccupiedByOther(x: Int, y: Int, currentEntity: Entity): Boolean {
        // Returns true or false for whether a player with transform component is in the same position as the client's target, not checking against itself
        // Stops checking once one occupation is found, saving computational cycles
        return engine.getEntitiesFor(allOf(TransformComponent::class, PlayerComponent::class).get()).any {
            it != currentEntity && TransformComponent.mapper[it]!!.position.x.toInt() == x && TransformComponent.mapper[it]!!.position.y.toInt() == y
        }
    }

    // Called whenever the window dimensions change to update the scaling logic
    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        viewport.update(width, height) // Recalculates and ensures the camera remains centred
        camera.update()
    }

    // Manually dispose native resources to prevent memory leaks
    override fun dispose() {
        coroutineScope.cancel() // Kills background tasks
        tcpSocket?.close() // Closes the network server connection

        // Release GPU heavy resources
        map.disposeSafely()
        renderer.disposeSafely()
        image.disposeSafely()
        atlas.disposeSafely()
        batch.disposeSafely()
    }
}
