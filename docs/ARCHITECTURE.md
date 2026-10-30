# 12pit architecture

## Components

`Pit12` is the Forge entry point. It relays Forge lifecycle events to `ClientBootstrap`, which instantiates the top-level components, wires up their dependencies, and handles start-up/shut-down.

Features are grouped under `feature.client`, `feature.combat`, `feature.render`, `feature.misc`, and `feature.patch`. Each feature package owns one user-facing capability, including its settings, logic, commands, rendering, storage, and Forge listeners.

`runtime` is for shared services and live state with its own lifecycle, like the client session, game state trackers, command registry, config catalog, and hud registry.

`shared` contains utilities and interfaces for threading, listeners, rendering, file writing. The live game state should be kept in `runtime` or `feature`, but not in `shared`.

`platform` wraps the third party frameworks, client extensions, native APIs. It deals with registration, protocol specifics and compatibility hooks, delegates calls from external systems to corresponding `feature`, `runtime` or `shared` interfaces. The adapters are organized by system they are integrating with.

Features define their commands and actions. `runtime.command` handles routing, help, tab completion, and availability checks. `platform.forge` registers the commands with Forge.

`feature.client.webui` runs a local HTTP server that connects the configuration interface (`web-ui`) to config and feature APIs.

## Dependencies

`Pit12` depends only on `bootstrap` and the compile-time constants in `shared.build.BuildConfig`. `bootstrap` depends on `platform`, `runtime`, `feature` and `shared`, passing dependencies straight to the components.

`feature` may depend on `runtime` and `shared`. If some feature needs access to another feature, it goes through the `api` package of that feature. `runtime` depends only on `shared`, and `shared` depends on nothing.

`platform` may depend on `runtime`, `feature` and `shared`. Feature Mixin packages follow the same dependency rules as `platform`. Platform services and binding hooks are provided to the rest of the system through the interfaces that belong to the corresponding layer. Native APIs and optional framework types are kept in `platform` and feature Mixin packages. `runtime.command` handles commands independently of Forge.

The packages directly under `runtime`, `shared`, and `platform` are modules. Each package at `feature.<category>.<feature>` is a feature module. Modules within each layer have no circular dependencies. Shared Mixins are in `platform.mixin`. Feature-specific Mixins are in `feature.<category>.<feature>.mixin`, respecting the feature boundary.

Each Mixin package has its own configuration. `src/main/resources/mixins.pit12.list` sets the configuration order for development, packaged builds, and architecture checks. The configurations share one refmap and plugin.

## State and resources

Each piece of mutable state belongs to a single owner. Each component properly cleans up the workers, subscriptions and rendering resources created by itself.

Each feature has its own settings. `ConfigCatalog` keeps track of all the live configs, `profile` feature handles config snapshots in the file system, and individual features manage any additional saved data they use.

## Architecture checks

[ArchitectureTest](../src/test/java/pit12/architecture/ArchitectureTest.java) ensures the proper package boundaries, dependency direction, module cycles, lifecycle ownership, and proper Mixin registration. [BootstrapLayoutTest](../src/test/java/pit12/architecture/BootstrapLayoutTest.java) ensures correct order of component registration and section grouping in `ClientBootstrap`.
