# 12pit implementation

This file contains information about project classes and the rules for using them. One section contains information about each part of the project. Modify it if the interfaces or the rules become outdated. For package responsibilities, refer to [Architecture](ARCHITECTURE.md).

## Lifecycle

`ClientLifecycle` has `start()` which initializes the resources and `stop()` which releases them. Components that manage some resources implement this interface. There should be no implementation in the helper with no managed resources.

`ClientBootstrap` creates the components and provides them with their dependencies through constructor injections. Add lifecycle components to the `components` field of this object after adding their providers. Bootstrap will start the components in the order specified in the list and stop them in reverse order. Objects are defined and initialized in constructors; listeners and workers should be in `start()`.

Bootstrap registers the component and calls its `start()` method. If `start()` causes an exception, then Bootstrap will stop this component and all components that were started before it. `stop()` should release resources in any case, including partial initialization and even if it was called multiple times. An exception thrown by one of the components in `stop()` will be logged but all the others will be stopped.

Keep the reference to the listener to unregister the same object. The following example illustrates components with `client` and `configs` dependencies, the saved `configListener` and `started` flag:

```java
@Override
public void start() {
    client.check();
    if (started) {
        return;
    }
    started = true;
    configs.addListener(configListener);
}

@Override
public void stop() {
    client.check();
    configs.removeListener(configListener);
    started = false;
}
```

Startup of the component and `enabled` setting are different things. Feature can keep itself subscribed to config changes and registered in the HUD editor even when it is disabled. Work that is required only if the feature is enabled should be started and stopped on `enabled` setting change. Unregister listeners, project listeners and bindings on the stop of their owner.

Disconnection of the server or world changes will reset session data without stopping the features. Work that is done between ticks should manage its own timeout, cancellation and recovery. Regular Minecraft shutdown call Bootstrap before the world and graphics context are released. Resources for workers and rendering should be disposed at that point. Forced process termination won't allow to do it.

## Client thread

`ClientThread` checks thread access and dispatches callbacks. Bootstrap creates it with Minecraft's thread checker and task dispatcher. Receive it in a constructor or use `configs.clientThread()` if the config catalog is already a dependency.

Call `client.check()` in a live API boundary. Calling from the wrong thread will throw `IllegalStateException`. `client.execute(Runnable)` dispatches the callback through the provided dispatcher and checks the thread. It will not create workers nor will check if the result is still valid.

Disk I/O, HTTP requests, expensive computations are done on owned workers with a copy of data. Return results with `client.execute`. Check the started state of the owner and the request or generation of the result. Check the session and world identity too if the work depends on them.

`ClientThread.current()` finds the calling thread and executes callbacks directly on it. Calling `execute()` of the current from another thread will throw. It is useful for tests and code which does not require cross-thread dispatching. It does not find Minecraft's client thread.

## Config

`FeatureConfig` represents the settings of a feature and their Web UI metadata. Settings are defined in its subclass constructor:

```java
public final class StatusConfig extends FeatureConfig {
    private final BooleanSetting showNames;

    public StatusConfig() {
        super("status", "Status", new ConfigCategory("render", "Render", 100),
                "Shows player status.");
        subcategory("display", "Display");
        showNames = booleanSetting("show_names", "Show names",
                "Shows player names.", true);
    }

    public boolean showNames() {
        return showNames.get();
    }
}
```

`ConfigCategory` provides category id, display name and order number. Reuse the category for related features. `subcategory()` call groups settings after it. Settings are sorted in their definition order.

The four-argument `FeatureConfig` constructor adds `enabled` setting with default value `true`. Constructor with `toggleable` parameter allows to not add `enabled` setting; in this case the feature always returns true when asked whether it is enabled. Constructor with `defaultEnabled` parameter chooses the initial value. Method `setEnabled` changes the setting, not the lifecycle.

The following helpers create settings and register their metadata in the feature:

- `booleanSetting` creates a boolean.
- `integerSliderSetting` and `doubleSliderSetting` create a numeric setting with default value, minimum, maximum and step parameters.
- `keybindSetting` stores the key code from `0` to `255`. The feature is responsible for handling the input.
- `colorSetting` stores RGB color as `0xRRGGBB`. `colorPickerSetting` stores ARGB color as `0xAARRGGBB`.
- `choiceSetting` creates a choice with default id and `ChoiceSetting.Choice` instances with stable ids.
- `hudConfig` adds anchor, offset, scale and text shadow settings to the HUD.

Feature ids are unique in the catalog. Ids of the settings and HUD configurations are unique in their feature. Id starts with a lowercase ASCII letter or digit; the rest can also be `.`, `-`, or `_`. Preserve saved ids while changing the display names. HUD configuration ids are prefixes like `status.offset_x`; do not create settings with ids conflicting with them.

`ConfigCatalog` registers all config objects in Bootstrap. Add the new registration before existing `freeze()` call and provide the same config object to its consumers:

```java
StatusConfig statusConfig = new StatusConfig();
configs.register(statusConfig);
```

All settings should be defined before registration. Registration will bind their read/write operations to the client thread of the catalog. `freeze()` prevents future registrations of the feature but does not prevent changing its settings. Profiles record the setting schema at startup time. Web UI reads the catalog and metadata of settings, so normal settings do not require separate page or saving.

Use `get()` and `set()` methods of a setting for its live value. `set()` validates the value and sends the notification if it changed. The subclasses of `Setting` validate default value after initializing fields used by `requireValue`.

Save the config listener as field, register it in `start()` and unregister in `stop()`. To get the dirty snapshot of display data, listener can mark it dirty:

```java
private boolean snapshotDirty = true;
private final ConfigChangeListener configListener = changes -> {
    if (changes.affects("status", "show_names")) {
        snapshotDirty = true;
    }
};
```

`ConfigChangeSet.affects(featureId, settingId)` evaluates the changed IDs. Listeners will be called after changes are processed. `snapshot()` takes a copy of all registered values. `apply(snapshot)` validates all known values before updating live settings, then sends one notification for the whole set. Missing known values will be reset to their defaults; it's a complete snapshot, not a patch. Unknown IDs are ignored. Empty snapshot triggers no notifications.

`recoverSavedValues(snapshot, problems)` restores default values for saved values that are not valid and returns them through the callback. It creates a normalized snapshot but does not apply it. Live input uses a normal validation instead. Profile feature handles saving and switching config snapshots.

## Command

[CommandNode](../src/main/java/pit12/runtime/command/CommandNode.java) is an immutable command tree. Features get the shared [CommandRegistry](../src/main/java/pit12/runtime/command/CommandRegistry.java) in constructor and register their definitions there before the first `start()` of the registry.

Here's a tree with a group, child, alias, required argument, a handler and completion candidates:

```java
CommandNode command = CommandNode.command("message", "Message commands")
        .child(CommandNode.command("send", "Show a message").aliases("say")
                .arguments("<text>", 1, 1)
                .executes((sender, args) -> CommandRegistry.reply(sender, args[0]))
                .suggests((sender, args) -> Arrays.asList("hello", "test")))
        .build();
commands.register(command);
```

Use `register(command)` to make `/12pit message` available. If `/message` is supposed to be available too, use `register(command, true)` instead; both will use the same tree. Do not register a command twice. Names and aliases use only lowercase letters, digits, `-` and `_`; `12pit` is reserved as a root name. Names and aliases of siblings mustn't coincide.

`arguments(usage, minimum, maximum)` describes the argument display usage and number range. Default is no arguments. `executes` gets an `ICommandSender` and an array of `String` arguments without the matched command path. Registry checks the argument number; feature can check the values, readiness and result of the action. `CommandRegistry.reply` adds the shared chat prefix.

A node with children is a group. Without arguments it displays auto-generated help, even if there's a handler. Leaf with a handler executes it, if the argument number is correct. Child's name or alias matches case-insensitively and overrides group's handler. Group handler can accept extra unmatched arguments. Adding a single `help` argument will show group help unless there's an explicit `help` child.

`requires(Predicate<ICommandSender>)` restricts use of a node. Help and completion hide children that cannot be used by the sender. Suggestion callback gets the arguments after the command path, including the current word. Return the complete set of suggestions; the registry filters it by the current prefix case-insensitively and removes duplicates. Returning null or empty list results in no suggestions.

Bootstrap provides [ForgeCommandAdapter](../src/main/java/pit12/platform/command/ForgeCommandAdapter.java) through the `Registrar` of the registry. Features don't register Forge command classes manually. There's no way to unregister commands in Forge. `stop()` disallows execution and completion and `start()` will reuse existing entry points. Registration is closed after the first start.

## HUD

[HudConfig](../src/main/java/pit12/runtime/config/HudConfig.java) stores settings related to HUD's placement and scale. Define it with `hudConfig` in FeatureConfig subclass' constructor and retain the result:

```java
hud = hudConfig("status", "Status", HudAnchor.TOP_LEFT, 6, 6, true);
```

The helper defines the following settings: `status.anchor`, `status.offset_x`, `status.offset_y`, `status.scale`, and `status.text_shadow`. Scale is stored in percentages and defaults to `100`. The HUD editor controls placement and scale. Text shadow is exposed as a setting in Web UI. Profile feature saves all these values with the rest of feature's settings.

A [HudElement](../src/main/java/pit12/runtime/hud/HudElement.java) implementation should satisfy the following contract:

- `id()` is unique in the HUD registry and doesn't change during registration. `displayName()` and `config()` must not be null.
- `enabled()` returns true when the live HUD is enabled.
- `resize(pixelScale)` receives the Minecraft GUI scale times HUD's scale setting.
- `prepare(pixelScale, editing)` prepares either live or preview contents before the bounds are accessed. Its default implementation calls `resize()`.
- `width()` and `height()` provide cheap unscaled logical size of the element's content.
- `render(partialTicks, editing)` draws the element from logical point `0,0` according to the caller's transformation.

The feature gets [HudRegistry](../src/main/java/pit12/runtime/hud/HudRegistry.java) from Bootstrap. It creates its element once and registers it in `start()`:

```java
hudRegistry.register(hud);
```

Unregisters the same element in `stop()`:

```java
hudRegistry.unregister(hud);
```

The registration provides access to the element through the HUD editor. It does not schedule the normal rendering of the element. The feature owns its overlay callback and its display data. Generate display snapshots in response to input changes and keep expensive parsing, IO operations, and large world scanning out of render callbacks.

Keep one [HudRenderer](../src/main/java/pit12/runtime/hud/HudRenderer.java) in a field:

```java
private final HudRenderer hudRenderer = new HudRenderer();
```

This is the regular overlay path, where `resolution` and `partialTicks` come from the overlay event:

```java
if (!hudRegistry.editing() && hud.enabled()) {
    HudBounds bounds = HudRenderer.layout(hud, resolution.getScaledWidth(),
            resolution.getScaledHeight(), resolution.getScaleFactor(), false);
    hudRenderer.render(hud, bounds, partialTicks, false);
}
```

`layout` calls `prepare`, applies placement and scaling and keeps the resolved origin inside the screen if the element fits. `render` applies the transform and restores the UI rendering state in `finally` block. The regular overlay should skip rendering during editing, as the editor renders the elements itself.

Editing happens with `editing=true`. Provide preview content when live data is empty or when the HUD is disabled and ensure that the measured bounds match the content. An enabled HUD should be registered so it could be positioned in the editor.

[UiRenderer](../src/main/java/pit12/shared/rendering/UiRenderer.java) provides the project text, rectangles and textures. Pass the element's pixel scale to its `resize`. Reuse it and keep a small set of font sizes; each size manages its own font resources. Call `close()` on it when its owner stops or releases those resources. It can create resources again after resizing. For custom UI outside of `HudRenderer`, use `begin()` from [UiRenderState](../src/main/java/pit12/shared/rendering/UiRenderState.java) together with `end()` in `finally` block. Restore the extra rendering state that your code modifies.

## Game state

Bootstrap provides query contracts to features. Their live queries and subscriptions need the client thread. Use the existing providers instead of the second tracker or session owner.

[ClientSession](../src/main/java/pit12/runtime/session/ClientSession.java) provides `connection()`, `world()` and `revision()`. The connection and world can be `null`. Both identities are updated before the listeners run. No old disconnect and unload events can clear a replacement connection or world.

Keep `Runnable` in a field for session changes. Register it with `session.addListener(sessionListener)` in `start()` and read the current state once; adding a listener does not cause the initial callback. Unregister it with `session.removeListener(sessionListener)` in `stop()`. Reset the state according to what it belongs to: the connection data lives for the connection, and the world data lives for the world. Do not delete the user data on either change.

[TabPresence](../src/main/java/pit12/runtime/player/TabPresence.java) separates the membership from the known names. Use `contains(UUID)` to check the membership. Use `players()` to get the current UUID-to-profile-name map; the player may be present but not in that map if its name is unknown. [TabPresenceListener](../src/main/java/pit12/runtime/player/TabPresenceListener.java) reports seen players, departures and display changes. Store and unregister the listener with its owner.

[PlayerEquipmentAccess](../src/main/java/pit12/runtime/player/PlayerEquipmentAccess.java) provides `loadedEquipment(UUID)` and equipment listeners. A query returns `null` if the entity is not loaded or has not been observed yet. In a snapshot, check `heldItemKnown()` or `leggingsKnown()` before considering a slot as empty. `copyHeldItem()` and `copyLeggings()` return defensive copies but `null` can be considered as either an unknown slot or a known empty slot. Use `heldEnchantments()` or `leggingsEnchantments()` for already parsed data. Equipment listeners report the changes, removal and reset.

[PitContext](../src/main/java/pit12/runtime/pit/PitContext.java) provides `current()`. The returned `PitSnapshot` provides the map, Pit state, revision and `spawnStateAt(x, y, z)`. The state is `UNKNOWN` until the map is identified. The snapshots can be passed to the workers; live providers cannot.

[PitEnchantmentReader](../src/main/java/pit12/runtime/item/PitEnchantmentReader.java) reads the arbitrary item stacks with `read`, `contains` and `levelOf`. The missing data causes an empty result or level `0`. The entries with missing keys or non-positive levels are ignored. Use the equipment snapshot's parsed enchantments if they are already available.

Players are identified with UUIDs. The entity IDs are valid only in their world. Separate the unknown state from the confirmed absence. Assign the owner and the reset rule to each cache; add one only to solve a clear problem.

## Mixin

Mixin classes exist under `platform.mixin`. A mixin dedicated to a particular feature is placed under `platform.mixin.feature.<feature>`, matching the feature's package name. Classes other than Mixin classes are not supposed to go there since Mixin classes cannot be loaded as regular helpers.

A binding interface allows receiving calls from the injected object regardless of the Mixin class. Bind the interface with the owner of the calls. Bootstrap casts the target to the binding interface and passes it to the consumer. For example, the gamma feature receives the renderer as `GammaBinding`:

```java
components.add(new GammaFeature(configs, gammaConfig,
        (GammaBinding) minecraft.entityRenderer));
```

The owner binds its config or observer on start and unbinds it on stop. A Mixin is responsible for the unbound state. A small stateless change may stay in a Mixin if it would introduce no confusion in another class. Blocking IO and interaction between unrelated features are not allowed in Mixins.

Register client Mixin names in the `client` list in [mixins.pit12.json](../src/main/resources/mixins.pit12.json). Entries are relative to its configured package. For `pit12.platform.mixin.feature.gamma.EntityRendererMixin` the entry is `feature.gamma.EntityRendererMixin`. Do not modify the resource's package and refmap placeholders.

[ArchitectureTest](../src/test/java/pit12/architecture/ArchitectureTest.java) checks the Mixin package, feature ownership, consistency of the registered and implemented Mixins, and project dependency rules.

## Storage

Profile feature stores the settings registered in `ConfigCatalog`. Other features do not duplicate settings. Data other than the settings is owned by the feature's own store. Bootstrap provides storage paths under the Minecraft game directory.

Validate the data being loaded. Implement handling of older formats when changing file format and preserving unknown fields if possible. Failing loads must not overwrite the original data silently. Keep the live state and unsaved changes after a write failure and report the error.

[AtomicFile](../src/main/java/pit12/shared/storage/AtomicFile.java) needs the target `Path` and the whole contents as a string. A store calls it on its IO worker:

```java
AtomicFile.write(path, encodedJson);
```

It creates parent directories and a temporary file near the target. It writes UTF-8, flushes and syncs the temporary file, then replaces the target. If atomic moves are not supported, it makes a fallback to a normal replacement move. Such a fallback doesn't guarantee an atomic replacement. The helper does not group several file writes into one transaction and does not control their order. On error it tries to delete the temporary file and throws; cleanup errors are attached to the error.

The feature is responsible for its worker and for the order of the writes. On the client thread make a copy of the data being saved and pass it to the worker. Handle completion with `ClientThread.execute`. Verify the worker or generation producing the result. Verify the save revisions before clearing the unsaved changes, so that an old completion could not clear newer changes. Session and world checks are needed only for work dependent on them; saved user data survives disconnections.

`stop()` stops the new work, handles the pending saves and closes the worker. Cleanup must not wait for the callback, which can be called only on the same blocked client thread. Storage and lookup dependencies must be replaceable for tests.

## Results and listeners

[OperationResult](../src/main/java/pit12/shared/result/OperationResult.java) represents the expected results of public mutations. The statuses are `SUCCESS`, `UNAVAILABLE`, `INVALID_VALUE`, and `NOT_FOUND`:

```java
return OperationResult.failure(OperationResult.Status.INVALID_VALUE,
        "The key is not valid");
```

`success(value)` and `success(value, message)` contain an optional value. Verify `succeeded()` or `status()` before calling `value()`. The value is `null` on an error; successful operations without a value may also return `null`. Features with a more specific result type maintain that contract. Improper calls on a wrong thread and broken internal contracts are programming errors.

[Listeners.notify](../src/main/java/pit12/shared/event/Listeners.java) calls listeners from a copy of the current collection:

```java
Listeners.notify(listeners, Runnable::run);
```

Do all the state change before notifying the listeners. Changes in the listener collection during the notification affect future notifications, not the current copy. The thrown `RuntimeException` from a listener is logged and the delivery continues. The helper is synchronous; it doesn't dispatch on the client thread and doesn't make the collection thread-safe. Subscription removal is required for stopping the owner. Use direct calls for local queries and actions, and listeners for change notifications needed by several consumers.
