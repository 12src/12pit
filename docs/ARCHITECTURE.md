# 12pit architecture

12pit is a client-side Forge mod. Each feature owns one user-facing part of the mod, and shared runtime code stays small. This document describes the current layout; it is not a class template that every feature must follow.

## Packages and dependencies

`Pit12` receives Forge lifecycle events and passes them to `ClientBootstrap`. `ClientBootstrap` creates the top-level components, wires them together, and manages their lifecycles. Feature behavior stays in the features.

Each package directly under `feature` owns one user-facing capability. It can keep its settings, rules, commands, rendering, and storage together. A small feature does not need a `Feature` class if it has no resources to start or stop. Feature-specific Forge listeners can stay with that feature.

`runtime` is for live data and capabilities used by more than one feature. Put something there only if it has its own owner and lifecycle or if multiple features need it. `shared` is for small reusable contracts and implementations; it does not own live game state. `platform` is for adapters to external mechanisms such as Mixin. Keep feature rules in the feature package so their owner stays clear.

Features can depend on `runtime` and `shared`. `runtime` can depend on `shared`, but not on a feature. `shared` must not depend on higher layers. Use narrow contracts for platform adapters. A feature can use another feature only through its public `api` package; `ClientBootstrap` wires that dependency. Avoid package cycles and global service lookup.

## Lifetimes and state

The component that creates a resource must release it. `ClientBootstrap` starts providers before consumers. If startup fails, it stops everything that was started, in reverse order. Components must tolerate partial startup, and calling `stop()` more than once must be safe.

`ClientSession` owns the current connection and world identities. Trackers subscribe to it instead of handling disconnects separately. It dispatches network events to the client thread, ignores events from replaced connections, and publishes both identities before notifying subscribers. `ClientThread` checks live API access and dispatches copied worker results. Storage and identity lookup dependencies can be replaced in tests.

The Minecraft shutdown adapter calls `ClientBootstrap.stop()` before Minecraft releases the world and graphics context. Features stop their workers and flush pending changes during that call. Forced process termination cannot run this cleanup.

Starting a feature with the client is not the same as enabling it in settings. Disabling a feature releases work that is only needed while it is active. A server disconnect ends the session, not the feature or mod. World state ends when the world is replaced. A multi-tick operation owns its temporary state, timeout, cancellation, and recovery.

Treat Minecraft as the source of truth for current Tab entries, loaded entities, scoreboards, and inventory state. Keep your own copy only when Minecraft does not retain the information, when it must outlive the game object, or when calculating it again is meaningfully expensive. Give each mutable fact one owner and a clear reset rule. Keep unknown information separate from confirmed absence. Prefer UUIDs for player identity. Entity IDs are valid only in their world. Display snapshots are views derived from that state, not a second source of truth.

## External input and work

Forge listeners and Mixin classes should pass observations or decisions to their owner. They should not do blocking IO or coordinate unrelated features. Put Mixins under `platform.mixin`; a feature-specific Mixin goes under `platform.mixin.feature`. Register every Mixin in `mixins.pit12.json`. Keep ordinary classes out of Mixin packages. A small, stateless adjustment can stay in a Mixin when moving it would not make the code clearer.

Use a direct call for a query or a local synchronous action. Notify listeners when several consumers care about a meaningful change. Use an explicit operation for work that spans ticks, can fail, or needs recovery. Add an event bus or scheduler only when the code actually needs one.

Public mutations return explicit success or failure results for expected input and readiness failures. Thread violations remain programming errors. State changes finish before subscribers are notified. Listener failures are logged without stopping delivery to other subscribers.

Read and change Minecraft objects on the client thread unless an API says otherwise. For disk IO, HTTP, or expensive calculations, give background workers copied plain data. Apply their results on the client thread only after checking that the feature, request, session, and world are still current. Every worker needs an owner and a way to shut down.

## Rendering and saved data

Build display snapshots when their inputs change. Renderers read those snapshots each frame and restore any rendering state they change. Do not do blocking IO, broad world scans, or large parsing jobs in render callbacks. Add a cache only when it addresses a measured or obvious cost, and give it an owner and an invalidation rule.

Keep configuration and persistent data with the feature that uses them. Update derived display state when settings change. Validate saved data when loading it, and handle older formats when changing it. A failed write must not silently discard live state or damage saved data. Older save results must not clear newer unsaved changes. Disconnecting must not delete user data.

## Changing these boundaries

Put new behavior in the feature that needs it. Extract shared code only when its owner and reset rules are clear. [ArchitectureTest](../src/test/java/pit12/architecture/ArchitectureTest.java) checks package dependencies and Mixin registration. Update this document and the relevant tests when an architectural decision changes.
