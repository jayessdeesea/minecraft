# Stack and target

What the project is built with and against, how dependency versions are pinned, and what's out of
scope.

## Stack

- **Java 25** (Amazon Corretto)
- **Maven**
- **Spring AI 2.0** — all LLM calls go through the `ChatModel`/`ChatClient` abstraction. Each
  echelon picks its provider in config (`minecraft.general.chat-model`,
  `minecraft.squad-leader.chat-model`: a local model served by Ollama, `compose.yaml`, or
  Anthropic Claude). The intended split is a frontier general over open-weight squad leaders.
  Because of that: keep provider selection in config, never hardcode
  Anthropic-specific request/response shapes, message formats, or tool-call conventions into
  General/Squad Leader logic. If it only works with Claude, it's not written against the
  abstraction correctly.
- **Tests: JUnit 6 and ArchUnit; no property-testing library.** Variation testing uses our own
  seeded scenario generators (`bot`'s `scenario` test package, shared with `app` as a test jar).
  jqwik was rejected: its maintainer asks AI coding agents not to use it.
- **Netty**, hand-rolled protocol implementation. No MCProtocolLib, no PacketEvents, no NBT/world
  libraries of any kind — VarInt/VarLong, packet framing, compression, encryption, and the full
  connection state machine (Handshake → Status/Login → Configuration → Play) are all written here.

## Target environment

- **Protocol version: pinned, not negotiated.** Pinned to Minecraft Java Edition 26.3 (protocol
  777). Track the current release, but bump the pin deliberately, never let it drift. No
  multi-version support, no version-negotiation/compat layer — one version, one protocol.
- **Auth: self-hosted, offline-mode dev server only.** No Mojang/Xbox Live OAuth device-code flow,
  no session server, no login encryption request/response — offline mode skips that handshake
  entirely. Revisit this decision if bots ever need to join a real online-mode server.

## Dependency version pinning

The root `pom.xml` is its own parent — it does **not** inherit from `spring-boot-starter-parent`.
Instead its `dependencyManagement` imports `spring-boot-dependencies` and `spring-ai-bom` as BOMs
(`<scope>import</scope>`), which gives Boot's whole tested compatibility matrix "for free" without
inheriting Boot's plugin defaults/parent chain. `log4j-bom`, `junit-bom`, and `netty-bom` are
imported *above* those two platform BOMs specifically so our own pins for those three libraries
win — for two imported BOMs managing the same `groupId:artifactId`, the first declared in
`dependencyManagement` wins, so order in that block is load-bearing, not cosmetic.

The pins themselves live in the root `pom.xml`'s `<properties>` - bump them there, deliberately,
not silently. Spring AI's 2.0 line requires Boot 4.0/4.1 (it won't load under Boot 3.x), so bump
those two together; Netty stays on the 4.2.x line (new project, no 4.1.x migration burden).

**`spring-boot-maven-plugin` needs an explicit `<execution>` binding `repackage`.** Without
`spring-boot-starter-parent`, that goal isn't bound to the `package` phase automatically — omit
the execution block and `mvn package` silently produces a ~3KB thin jar with no dependencies
instead of a bootable fat jar. `app/pom.xml` binds it explicitly; keep that if the plugin
declaration ever gets refactored.

**If your working copy sits inside a cloud-sync folder** (OneDrive, Dropbox, Google Drive, etc.):
`mvn clean` can intermittently fail to delete `target/` because the sync client has a file open
mid-sync. Not a POM problem — if it happens, delete `target/` manually and build without `clean`,
or move the working copy outside the synced folder.

## Explicitly out of scope (for now)

- Supporting more than one protocol version at a time
- Real Microsoft account authentication / online-mode servers
- Any third-party Minecraft protocol, NBT, or world-format library
