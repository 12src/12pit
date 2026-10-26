# 12pit architecture

## Components

Each platform has a `Pit12` entry point. It starts `ClientBootstrap`, which creates the top-level components, wires up their dependencies, and handles startup and shutdown.

The Ornithe entry point binds startup to the end of game initialization, when settings and renderers are ready. With Pylon installed, its input adapter uses Mixins on the compatibility input classes. Other environments use the LWJGL 2 adapter.

Each package in `feature` owns one user-facing capability, including its settings, logic, commands, rendering, storage, and event subscriptions.

`runtime` is for shared services and live state with its own lifecycle, like the client session, game state trackers, command registry, config catalog, and hud registry.

`shared` contains utilities and interfaces for threading, listeners, rendering, file writing. The live game state should be kept in `runtime` or `feature`, but not in `shared`.

`platform` wraps the third party frameworks, client extensions, native APIs. It deals with registration, protocol specifics and compatibility hooks, delegates calls from external systems to corresponding `feature`, `runtime` or `shared` interfaces. The adapters are organized by system they are integrating with.

For commands, features declare their commands and actions. `runtime.command` handles routing, help, and tab completion. The platform registers them with its client command handler.

`src/main` holds shared sources and resources. `src/forge` and `src/ornithe` hold each platform's entry point, services, and Mixin configuration. Both builds use MCP names. The Forge build uses Essential Loom, and the included Ornithe build uses Fabric Loom with Ploceus.

The Ornithe build keeps SRG names for members that MCP leaves unnamed and restores the GUI exception declarations used by the shared screen. These changes apply to its development Minecraft jar.

`shared.event.ClientEvents` holds typed event channels. Features subscribe while active and remove their subscriptions when they stop. The Forge adapter forwards events at their original priorities. Ornithe supplies the hooks through Mixins. Dispatch reads a stable listener list so callbacks can change subscriptions.

`feature.webui` runs a local HTTP server that connects the configuration interface (`web-ui`) to config and feature APIs.

## Dependencies

`Pit12` depends only on `bootstrap` and the compile-time constants in `shared.build.BuildConfig`. `bootstrap` depends on `platform`, `runtime`, `feature` and `shared`, passing dependencies straight to the components.

`feature` may depend on `runtime` and `shared`. If some feature needs access to another feature, it goes through the `api` package of that feature. `runtime` depends only on `shared`, and `shared` depends on nothing.

`platform` may depend on `runtime`, `feature` and `shared`, but the opposite direction of dependencies is not allowed. Platform services and binding hooks are provided to the rest of the system through the interfaces that belong to the corresponding layer. Native APIs and optional framework types are kept in `platform`, `runtime.command` does not depend on Forge.

The packages directly under each layer are modules and do not have circular dependencies between each other. The Mixins are in `platform.mixin` and feature-specific Mixins are in `platform.mixin.feature.<feature>`, respecting the feature boundary.

## State and resources

Each piece of mutable state belongs to a single owner. Each component properly cleans up the workers, subscriptions and rendering resources created by itself.

Each feature has its own settings. `ConfigCatalog` keeps track of all the live configs, `profile` feature handles config snapshots in the file system, and individual features manage any additional saved data they use.

## Architecture checks

[ArchitectureTest](../src/test/java/pit12/architecture/ArchitectureTest.java) ensures the proper package boundaries, dependency direction, module cycles, lifecycle ownership, and proper Mixin registration. [BootstrapLayoutTest](../src/test/java/pit12/architecture/BootstrapLayoutTest.java) ensures correct order of component registration and section grouping in `ClientBootstrap`.
