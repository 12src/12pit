# 12pit architecture

## Components

`Pit12` is the Forge entry point. It relays Forge lifecycle events to `ClientBootstrap`, which instantiates the top-level components, wires up their dependencies, and handles start-up/shut-down.

Ordinary features live under `feature.combat`, `feature.render`, `feature.misc`, and `feature.patch`. Each owns one user-facing capability, including its settings, logic, commands, rendering, storage, and Forge listeners.

`feature.client` provides client services, such as player relations and name lookups. `feature.provider` supplies configurable rules and results that other features use, such as timer tracking and Telebow prediction.

Client services and providers have no master switch.

`runtime` owns shared services and basic live state, such as the client session, the arrow tracker, the config catalog, and the HUD registry. It does not own configurable feature rules.

`shared` contains utilities and interfaces for threading, listeners, rendering, file writing. The live game state should be kept in `runtime` or `feature`, but not in `shared`.

`platform` wraps the third party frameworks, client extensions, native APIs. It deals with registration, protocol specifics and compatibility hooks, delegates calls from external systems to corresponding `feature`, `runtime` or `shared` interfaces. The adapters are organized by system they are integrating with.

Features define their commands and actions. `runtime.command` handles routing, help, tab completion, and availability checks. `platform.forge` registers the commands with Forge.

`feature.client.webui` runs a local HTTP server that connects the configuration interface (`web-ui`) to config and feature APIs.

## Dependencies

`shared` does not depend on other layers. `runtime` can use other runtime modules and `shared`, but cannot depend on features.

All feature modules may use `runtime` and `shared`. When they depend on another feature, they must use its `api` package.

Client modules may depend on other client modules and ordinary features, but not providers.

Providers may depend on client modules and other providers, but not ordinary features.

Ordinary features may depend on client modules and providers, but not on other ordinary features.

`platform` may depend on `runtime`, `feature` and `shared`. Feature Mixin packages follow the same dependency rules as `platform`. Platform services and binding hooks are provided to the rest of the system through the interfaces that belong to the corresponding layer. Native APIs and optional framework types are kept in `platform` and feature Mixin packages. `runtime.command` handles commands independently of Forge.

`bootstrap` depends on `platform`, `runtime`, `feature`, and `shared` and wires the components together. `Pit12` depends only on `bootstrap` and the compile-time constants in `shared.build.BuildConfig`.

The packages directly under `runtime`, `shared`, and `platform` are modules. Each package at `feature.<category>.<feature>` is a feature module. Modules within each layer have no circular dependencies. Shared Mixins are in `platform.mixin`. Feature-specific Mixins are in `feature.<category>.<feature>.mixin`, respecting the feature boundary.

## State and resources

Each piece of mutable state belongs to a single owner. Each component properly cleans up the workers, subscriptions and rendering resources created by itself.

Each feature owns its settings and any extra saved data. `ConfigCatalog` tracks live settings. The profile service in `runtime.config.profile` saves and restores snapshots of those settings.

## Architecture checks

[ArchitectureTest](../src/test/java/pit12/ArchitectureTest.java) ensures the proper package boundaries, dependency direction, module cycles, lifecycle ownership, and Mixin package rules. [BootstrapLayoutTest](../src/test/java/pit12/BootstrapLayoutTest.java) ensures correct order of component registration and section grouping in `ClientBootstrap`.
