# 12pit architecture

## Components

`Pit12` is the Forge entry point. It passes Forge lifecycle events to `ClientBootstrap`. Bootstrap creates the top-level components, connects their dependencies, and starts and shuts down the application.

Each package directly under `feature` owns one user-facing capability, including its settings, rules, commands, rendering, and storage. It may also own Forge listeners specific to that feature.

`runtime` owns live data and services that have their own lifecycle or are shared by several features. These include the client session, game state trackers, command registry, config catalog, and HUD registry.

`shared` provides small helper interfaces and utilities for threading, listeners, rendering, and file writes. Live game state belongs to runtime and feature components.

`platform` adapts external frameworks, client extensions, and native services. It handles registration, protocol details, and compatibility hooks. Adapters pass external requests to their owners through interfaces owned by features, runtime, or shared code. Group adapters by the system they adapt. An adapter can implement `ClientLifecycle` to manage its own subscriptions and registrations.

Features own command definitions and actions. `runtime.command` handles routing, help, completion, and command availability. `platform.forge` handles Forge registration.

`feature.webui` owns the local HTTP server and connects the configuration interface to config and feature APIs. `web-ui` contains the browser pages.

## Dependencies

`Pit12` depends on Bootstrap and reads compile-time build constants from `shared.build.BuildConfig`. Bootstrap may depend on `platform`, `runtime`, `feature`, and `shared`. It passes dependencies directly to the components that need them.

Features may depend on `runtime` and `shared`. Access to another feature goes through that feature's public `api` package. `runtime` may depend on `shared`. `shared` stays independent of the other project layers.

`platform` may depend on `runtime`, `feature`, and `shared`. Features, runtime, and shared code stay independent of platform classes. They receive platform services and bind hooks through interfaces they own. Native APIs and optional framework types stay in platform adapters. Command runtime code stays independent of Forge.

Packages directly under a project layer form modules. Keep module dependencies free of cycles. A feature-specific Mixin belongs to that feature and accesses other features through their public APIs.

## State and resources

`ClientSession` owns connection and world identities. Trackers own the state tied to those identities. Disconnecting ends the session, and replacing the world ends the old world state. The mod keeps running through both events.

Minecraft owns current Tab entries, loaded entities, scoreboards, and inventory state. Runtime trackers own data beyond what Minecraft stores and derived data shared by features. Each piece of mutable data has one owner. Display snapshots provide views of that data.

A component owns the resources it creates, such as workers, subscriptions, and rendering resources. Temporary operations manage their own progress, cancellation, and recovery. A feature tracks its startup state and enabled flag separately.

Each feature defines its settings. `ConfigCatalog` owns the registered live config. The profile feature owns config snapshots and their storage. Each feature owns the other saved data it uses. User data is kept across disconnects.

## Architecture checks

[ArchitectureTest](../src/test/java/pit12/architecture/ArchitectureTest.java) checks package ownership, dependency direction, module cycles, lifecycle ownership, and Mixin registration.
