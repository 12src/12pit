# 12pit architecture

[Implementation](IMPLEMENTATION.md) covers the shared APIs and calling conventions used to develop features.

## Components

`Pit12` is the Forge entry point. It routes Forge lifecycle events to `ClientBootstrap`. Bootstrap builds top-level components, wires their dependencies, and is responsible for application start and shutdown.

Each package immediately under `feature` owns one user-facing capability. Its settings, rules, commands, rendering, and storage are owned by that feature. Also feature-specific Forge listeners may be owned by the feature.

`runtime` owns live data and services that have their own lifecycle or are used by multiple features. These are the client session, game state trackers, command registry, config catalog, and HUD registry.

`shared` owns small helper interfaces and utilities for threading, listeners, rendering, and file writes. It does not own live game state.

`platform` adapts external mechanisms such as Forge commands and Mixin. Adapters route external requests to their owners through narrow interfaces. An adapter can implement `ClientLifecycle` to manage its own subscriptions and registrations.

Commands definitions and actions are owned by features. `runtime.command` owns routing, help, completion, and availability of commands. `platform.command` owns Forge registration.

`feature.webui` owns the local HTTP server and connects the browser interface to config and feature APIs. `web-ui` owns the browser pages.

## Dependencies

`Pit12` depends on Bootstrap and reads compile-time build constants from `shared.build.BuildConfig`. Bootstrap may depend on `platform`, `runtime`, `feature`, and `shared`. It wires dependencies between the components; there is no global service lookup.

Features may depend on `runtime` and `shared`. A feature may use another feature only through the public `api` package of that feature. `runtime` may depend on `shared`. `shared` does not depend on other project layers.

`platform` may depend on `runtime`, `feature`, and `shared`. Features and runtime code use binding interfaces instead of depending on platform classes. Command runtime code does not depend on Forge.

Packages immediately under a project layer form modules. Modules do not contain any cycles in their dependencies. A feature-specific Mixin belongs to the corresponding feature boundary and does not depend on internals of another feature.

## State and resources

`ClientSession` owns connection and world identities. Trackers own state related to those identities. A disconnect ends the session, and a world replacement ends the state of the old world. Neither event influences the life cycle of the mod.

Minecraft owns current Tab entries, loaded entities, scoreboards, and inventory state. Runtime trackers own data that Minecraft does not keep and derived data shared by features. Each mutable piece of data has one owner. Display snapshots are views on that data.

A component owns the resources it allocates, such as workers, subscriptions, and rendering resources. Temporary operations are responsible for their progress, cancellation, and recovery. Startup of a feature and enabled flag of the feature are separate pieces of state.

Each feature defines its settings. `ConfigCatalog` owns the registered live config, and the profile feature owns config snapshots and their storage. Other saved data is owned by the feature using it. The user data survives disconnects.

## Architecture checks

[ArchitectureTest](../src/test/java/pit12/architecture/ArchitectureTest.java) checks package ownership, dependency direction, module cycles, lifecycle ownership, and Mixin registration.
