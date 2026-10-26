# Kotlin-Dungeon-Crawler

# Dungeon Crawler (WIP)

Dungeon Crawler game, developed as part of my Mobile and Multiplayer module at Teesside University. 

*This repository will eventually be merged into the Dungeon Crawler Game Server.*

A [libGDX](https://libgdx.com/) project generated with [gdx-liftoff](https://github.com/libgdx/gdx-liftoff).

This project was generated with a Kotlin project template that includes Kotlin application launchers and [KTX](https://libktx.github.io/) utilities.

## Platforms

- `core`: Main module with the application logic shared by all platforms.
- `android`: Android mobile platform. Needs Android SDK.

## Gradle

This project uses [Gradle](https://gradle.org/) to manage dependencies.
The Gradle wrapper was included, so you can run Gradle tasks using `gradlew.bat` or `./gradlew` commands.
Useful Gradle tasks and flags:

- `--continue`: when using this flag, errors will not stop the tasks from running.
- `--daemon`: thanks to this flag, Gradle daemon will be used to run chosen tasks.
- `--offline`: when using this flag, cached dependency archives will be used.
- `--refresh-dependencies`: this flag forces validation of all dependencies. Useful for snapshot versions.
- `android:lint`: performs Android project validation.
- `build`: builds sources and archives of every project.
- `cleanEclipse`: removes Eclipse project data.
- `cleanIdea`: removes IntelliJ project data.
- `clean`: removes `build` folders, which store compiled classes and built archives.
- `eclipse`: generates Eclipse project data.
- `idea`: generates IntelliJ project data.
- `test`: runs unit tests (if any).

Note that most tasks that are not specific to a single project can be run with `name:` prefix, where the `name` should be replaced with the ID of a specific project.
For example, `core:clean` removes `build` folder only from the `core` project.

# CPP-Game-Server-Dungeon-Crawler (WIP)
CPP game server created for mobile/multiplayer Dungeon Crawler Project. 

# MAM Dungeon Crawler

## How to Run
1. **Start the Server:** Navigate to the `server` folder and execute `chmod +x run_server.sh` followed by `./run_server.sh`.
2. **Start the Clients:** Install and launch the provided `.apk` on two Android devices.

## Controls
* **Movement:** Tap a navigable tile on the screen to pathfind towards it. 
* **Combat:** Shake the device to perform an AoE attack (Note: Combat is disabled in the Starting Lobby).

## Gameplay Guide
* **The Lobby:** To start the game, both players must navigate to the two floor buttons. If both buttons are pressed at the same time the door on the northern wall will open. Walking through the door will lead to a transition to a random dungeon room. 
* **Death & Respawning:** If a player is killed they will respawn on the next room's load. 

## Technical Notes & Troubleshooting
* **Emulator Extended Controls:** Emulators struggle to register accelerometer input when using the extended controls menu. 
* **Lab Networking:** The UDP discovery broadcast doesn't work in the labs, presumably being blocked by firewall; IP may need to be manually set in the `Constants.kt` file. 
* **ADB Port Forwarding:** Similar commands may need to be used to allow communication:
`adb -s <Device_ID> reverse tcp:4300 tcp:4300`
`adb -s <Device_ID> reverse tcp:4301 tcp:4301`

## References
* ***KENNEY (2021)*** *Micro Roguelike. Available at: [https://kenney.nl/assets/micro-roguelike](https://kenney.nl/assets/micro-roguelike) (Accessed: 25 March 2026).*
* ***Nicktuti (2025)*** *Mini Odyssey NPC Characters Pack1. Available at: [https://nicktuti.itch.io/mini-odyssey-ncp-characters-pack1](https://nicktuti.itch.io/mini-odyssey-ncp-characters-pack1) (Accessed: 25 March 2026).*
* ***Yogendra Pratap Singh (Tech Architect) (2025)*** A\* Search Algorithm Explained with Kotlin: Graph. Available at: [https://medium.com/@ys.yogendra22/a-search-algorithm-explained-with-kotlin-graph-3b4c3c556f80](https://medium.com/@ys.yogendra22/a-search-algorithm-explained-with-kotlin-graph-3b4c3c556f80) (Accessed: 31 March 2026).

## GitHub
* **Client:** [Kotlin-Dungeon-Crawler](https://github.com/MylesColeman/Kotlin-Dungeon-Crawler)
* **Server:** [CPP-Game-Server-Dungeon-Crawler](https://github.com/MylesColeman/CPP-Game-Server-Dungeon-Crawler)
