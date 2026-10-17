# 12pit implementation

This guide covers the shared APIs, calling conventions, and lifecycle rules used to develop features. For package responsibilities, see [Architecture](ARCHITECTURE.md).

## Lifecycle

`ClientLifecycle` defines `start()` to initialize resources and `stop()` to release them. Components that manage resources implement this interface.

`ClientBootstrap` creates the components in its constructor. Each feature has a private `register...` method that creates and registers its config when needed, creates the feature, and adds it to `components`. Pass dependencies as arguments. Return the feature's API only when a later registration method needs it.

Organize the constructor into shared runtime, feature providers, other features, and platform integrations. Create runtime components and platform adapters directly in their sections. Group feature registration calls and method declarations by category in this order: Combat, Player, Render, Misc, Patch. Within each category, order methods alphabetically by name. Put features without a feature config, including those with only global settings, in the first `Category: No config` group. Use matching group and category comments in both places. Dependency order takes priority over category and name. Bootstrap starts components in list order and stops them in reverse. Registration methods only create and register objects. Register listeners and start workers in `start()`.

Bootstrap registers the component and calls its `start()` method. If `start()` causes an exception, then Bootstrap will stop this component and all components that were started before it. `stop()` should release resources in any case, including partial initialization and even if it was called multiple times. An exception thrown by one of the components in `stop()` will be logged but all the others will be stopped.

Keep a reference to each listener so you can unregister the same object:

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

A disconnect or world change resets session data without stopping features. Work that spans ticks should manage its timeout, cancellation, and recovery. Normal Minecraft shutdown calls Bootstrap before releasing the world and graphics context. Release worker and rendering resources at that point.

## Client thread

`ClientThread` checks thread access and dispatches callbacks. Bootstrap creates it with Minecraft's thread checker and task dispatcher. Receive it in a constructor or use `configs.clientThread()` if the config catalog is already a dependency.

Call `client.check()` at a live API boundary. Calls from the wrong thread throw `IllegalStateException`. `client.execute(Runnable)` dispatches the callback through the provided dispatcher and checks the thread.

Disk I/O, HTTP requests, expensive computations are done on owned workers with a copy of data. Return results with `client.execute`. Check the started state of the owner and the request or generation of the result. Check the session and world identity too if the work depends on them.

`ClientThread.current()` captures the calling thread and runs callbacks directly on it. Calling its `execute()` from another thread throws. Use it for tests and code that needs no cross-thread dispatch.

## Input

`InputBlocker` routes key events before Minecraft handles them. Features register an `InputHandler` on start and remove it on stop. `key(...)` returns true when it consumes an event. `consumesKey(...)` identifies keys whose held states must not be restored after blocking ends.

While input is locked, only lock owners receive `blockedKey(key, modifiers)`. It receives new key presses without repeats. Use `Keybind.matches(binding, key, modifiers)` to check a binding against the captured modifier state. The input device queues these presses and dispatches them at tick start, outside LWJGL polling.

Use `lock(owner)` to block keyboard and mouse input. The platform supplies an `InputDevice` that masks LWJGL events and polled states. Minecraft's key, movement, and smooth camera states are cleared once when blocking starts. Use `unlock(owner, resume)` when the operation finishes or is cancelled. Each operation releases only its own lock. `blockedFor(owner)` reports whether another operation holds a lock. Input resumes only after all locks are released. Buffered events are discarded before restoring the input backend. When `resume` is true, held keys are restored if the game has focus. `held(key)` reads the current LWJGL state, which is false while blocked.

Use `runAction(owner, action)` for an automatic right-click or container click while holding the input lock. The action passes through vanilla hooks without restoring physical input. `blocksActions()` tells interaction hooks whether to block a call. Another owner's lock still blocks the action.

Use `releaseCursor()` while holding a lock to let the cursor leave the game window. `blocksFocus()` keeps menus from grabbing it again until the user clicks inside the window or all input locks are released. Clicking while a menu is open allows focus to return when that menu closes.

## Config

`FeatureConfig` represents the settings of a feature and their interface metadata. Settings are defined in its subclass constructor:

```java
import static pit12.runtime.languages.Languages.source;

public final class StatusConfig extends FeatureConfig {
    private final BooleanSetting showNames;

    public StatusConfig() {
        super("status", source("Status"), new ConfigCategory("render", source("Render")),
                source("Shows player status."));
        subcategory("appearance", source("Appearance"));
        subsubcategory("display", source("Display"));
        showNames = booleanSetting("show_names", source("Show names"),
                source("Shows player names."), true);
    }

    public BooleanSetting showNames() {
        return showNames;
    }
}
```

`ConfigCategory` defines a category's ID and display name. Reuse the same category for related features.

In the configuration interface, `subcategory()` puts the following settings under a button at the top of the feature page. `subsubcategory()` groups settings within that page. Both levels are optional. Without `subcategory()`, groups appear directly on the feature page. Settings without a subsubcategory have no section heading. Settings defined before the first subcategory stay visible under every button. Starting a new subcategory clears the current subsubcategory. Settings and groups follow their definition order.

The four-argument `FeatureConfig` constructor adds `enabled` setting with default value `true`. Constructor with `toggleable` parameter allows to not add `enabled` setting; in this case the feature always returns true when asked whether it is enabled. Constructor with `defaultEnabled` parameter chooses the initial value. Method `setEnabled` changes the setting, not the lifecycle.

The following helpers create settings and register their metadata in the feature:

- `booleanSetting` creates a boolean. Use `booleanSetting(id, defaultValue)` for a saved flag that has no interface control.
- `integerSliderSetting` and `doubleSliderSetting` create a numeric setting with default value, minimum, maximum and step parameters.
- `keybindSetting` creates a `KeybindSetting` for one key and an optional Shift, Ctrl, Alt, or Meta key. The main key may also be a modifier. The saved integer keeps the main key in the low byte and the modifier in the next byte. Features use `Keybind.matches` for key events and `Keybind.held` for held input.
- `colorSetting` stores RGB color as `0xRRGGBB`. `colorPickerSetting` stores ARGB color as `0xAARRGGBB`.
- `choiceSetting` creates a choice with default id and `ChoiceSetting.Choice` instances with stable ids.
- `hudConfig` adds grouped anchor, text shadow, and translation options. It also stores placement and scale for the HUD editor. See [HUD](#hud) for grouping and storage details.

Feature IDs are unique in the catalog. Setting and HUD config IDs are unique within their feature. An ID starts with a lowercase ASCII letter or digit; the remaining characters can also include `.`, `-`, or `_`. Preserve saved IDs when changing display names. A HUD config reserves setting IDs under its prefix, such as `status.offset_x`; do not define settings with conflicting IDs.

Register this config in the register method of the feature in Bootstrap, then pass the same config to the feature:

```java
private void registerStatus(ConfigCatalog configs) {
    StatusConfig config = new StatusConfig();
    configs.register(config);
    components.add(new StatusFeature(configs, config));
}
```

Define all settings before registering the config. Registration binds their reads and writes to the catalog's client thread. Bootstrap calls `configs.freeze()` once after all feature registration methods return. This prevents new config registrations while allowing setting changes.

The feature reads the example setting with `config.showNames().get()` and changes it with `config.showNames().set(false)`. A setting's `set()` validates the value and notifies listeners if it changed. When implementing a `Setting` subclass, validate its default value after initializing the fields used by `requireValue`.

Keep the config listener in a field, register it with `configs.addListener(configListener)` in `start()`, and unregister it with `configs.removeListener(configListener)` in `stop()`. The listener can mark display data for rebuilding:

```java
private boolean snapshotDirty = true;
private final ConfigChangeListener configListener = changes -> {
    if (changes.affects("status", "show_names")) {
        snapshotDirty = true;
    }
};
```

`ConfigChangeSet.affects(featureId, settingId)` checks which settings changed. Listeners run after all changes are applied. `snapshot()` copies all registered values. `apply(snapshot)` validates all known values before changing live settings, then sends one notification for the whole set. It applies a complete snapshot: missing known values reset to their defaults, and unknown IDs are ignored. An empty snapshot resets all settings to their defaults. No notification is sent if the resulting values are unchanged.

`recoverSavedValues(snapshot, problems)` restores default values for saved values that are not valid and returns them through the callback. It creates a normalized snapshot but does not apply it. Profile feature handles saving and switching config snapshots.

## Command

[CommandNode](../src/main/java/pit12/runtime/command/CommandNode.java) is an immutable command tree. Features get the shared [CommandRegistry](../src/main/java/pit12/runtime/command/CommandRegistry.java) in constructor and register their definitions there before the first `start()` of the registry.

Here's a tree with a group, child, alias, required argument, a handler and completion candidates:

```java
CommandNode command = CommandNode.command("message", source("Message commands"))
        .child(CommandNode.command("send", source("Show a message")).aliases("say")
                .arguments("<text>", 1, 1)
                .executes((sender, args) -> ChatFeedback.reply(sender, Tone.INFO, args[0]))
                .suggests((sender, args) -> Arrays.asList("hello", "test")))
        .build();
commands.register(command);
```

Use `register(command)` to make `/12pit message` available. If `/message` is supposed to be available too, use `register(command, true)` instead; both will use the same tree. Do not register a command twice. Names and aliases use only lowercase letters, digits, `-` and `_`; `12pit` is reserved as a root name. Names and aliases of siblings mustn't coincide.

Use `register(command, config, standalone)` for commands that belong to a toggleable feature. Call `commands.setActive(config, true)` after the feature starts and `commands.setActive(config, false)` when it stops. These commands are available only while their owner is started and enabled. Otherwise standalone names and aliases are removed and the whole subtree is hidden from `/12pit` help and completion.

`arguments(usage, minimum, maximum)` describes the argument display usage and number range. Default is no arguments. `executes` gets an `ICommandSender` and an array of `String` arguments without the matched command path. Registry checks the argument number; feature can check the values, readiness and result of the action.

A node with children is a group. Without arguments it displays auto-generated help, even if there's a handler. Leaf with a handler executes it, if the argument number is correct. Child's name or alias matches case-insensitively and overrides group's handler. Group handler can accept extra unmatched arguments. Adding a single `help` argument will show group help unless there's an explicit `help` child.

`requires(Predicate<ICommandSender>)` restricts use of a node. Help and completion hide children that cannot be used by the sender. The suggestion callback gets the arguments after the command path, including the current word. Return the complete set of suggestions. The registry filters it by the current prefix case-insensitively and removes duplicates. Return an empty list for no suggestions.

## Chat feedback

Use [ChatFeedback](../src/main/java/pit12/shared/chat/ChatFeedback.java) for local chat messages: `ChatFeedback.reply(sender, Tone.INFO, message)`. Import `ChatFeedback.Tone` and choose `INFO`, `SUCCESS`, `WARNING`, or `ERROR` based on the outcome. The helper adds the shared prefix and tone color.

Call it on the client thread. Outside a command, check that the local player exists and pass that player as the sender.

## Languages

Bootstrap creates one [Languages](../src/main/java/pit12/runtime/languages/Languages.java) instance and passes it to components that need it.

Import the static `source(...)` method as shown in the config example for names, descriptions, choice labels, enum labels, and command descriptions. It returns the English text and retains its usage location for split translations. Config classes store the original text and its translated version separately. Bootstrap updates those values via `ConfigCatalog.localize(language)` whenever language is changed. Command registry translates command descriptions when displaying help. Do not modify saved IDs, command names, and aliases.

Inject `Languages` via the constructor for messages to be generated at runtime. Use `translate(...)` for messages that do not depend on parameters and `format(...)` for messages that do:

```java
ChatFeedback.reply(sender, Tone.WARNING, language.translate("No matching bindings"));
ChatFeedback.reply(sender, Tone.SUCCESS,
        language.format("Bound {0} to {1}", itemName, keyName));
```

Pass an English literal as the first argument to `format(...)` and `translate(...)`. Dynamic values go to the subsequent arguments. `{0}` corresponds to the first value, `{1}` to the second, and so on. Translations may reorder parameters. Translation is performed once when preparing display text and passed to the renderer or chat feedback helper. For missing or empty translations, `translate(...)` returns the English source and `format(...)` substitutes its parameters.

To update cached HUD text, hold a language listener, register it in `start()` and unregister in `stop()`. `addListener(...)` will immediately call the listener and again after every language change. Update both the cached text and its measured layout at the same time. Prepare dynamic texts while updating the display snapshot. Render callback receives prepared text. Language switch and listener registration/removal occur on the client thread.

Frontend components import `t` from `./languages` and call `t('Settings')` or `t('Remove {0} players', count)`. Use these calls in reactive display code so the text updates when the selected language changes.

Split translations need Java source filenames and line numbers in compiler debug information. Put separate uses of the same Java source on separate lines. For a split label translated repeatedly in an update loop, mark it once with `source(...)` and retain the returned string to avoid repeated caller lookups.

Use `Languages.sourceText(...)` when sending marked text through an API field that the frontend passes to `t(...)`. It retains the usage location across JSON.

The extraction script finds Java `source(...)`, `translate(...)`, numbered `format(...)` calls, and frontend `t(...)` calls. CI runs `python scripts/languages.py extract` to generate a temporary POT and upload it to Crowdin.

Each PO entry contains its source, translation, and usage location. Its generated `msgctxt` contains the file path and the order of that source's uses in the file, such as `src/main/java/pit12/feature/autofish/AutoFishConfig.java#1`. The line and column stay in the `#:` reference. Line changes keep the identifier. Moving to another file or adding, removing, or reordering repeated uses can change their translation matches. See [Contributing translations](TRANSLATING.md) for translating and maintaining language files.

## HUD

[HudConfig](../src/main/java/pit12/runtime/config/HudConfig.java) stores settings related to HUD's placement and scale. Define it with `hudConfig` in FeatureConfig subclass' constructor and retain the result:

```java
hud = hudConfig("status", HudAnchor.TOP_LEFT, 6, 6, true);
```

The arguments specify the ID, initial screen anchor, horizontal and vertical offsets, and default text shadow. The anchor aligns the same point of the HUD and the screen. Offsets are in GUI pixels. Anchor mode defaults to Auto.

The default group name is `HUD`. To specify the group level and name, use the overload:

```java
hud = hudConfig("status", HudAnchor.TOP_LEFT, 6, 6, true,
        HudGroup.SUBSUBCATEGORY, source("Status appearance"));
```

Use `HudGroup.AUTO` for a subcategory when the feature declares other subcategories, or a subsubcategory otherwise. `SUBCATEGORY` creates a separate subcategory. `SUBSUBCATEGORY` uses the active subcategory at the call site, or the feature root if there is none. Pass null for the name to use `HUD`. The call preserves the active groups for later settings.

The returned `HudConfig` exposes settings through `textShadow()`, `translateText()`, and `scale()`. Read or change their values with `get()` and `set()`. Scale is a percentage and defaults to `100`.

Read the current placement with `placement()`. To change it, pass a [HudPlacement](../src/main/java/pit12/runtime/config/HudPlacement.java) containing the anchor, offsets, and anchor mode:

```java
hud.placement(new HudPlacement(HudAnchor.TOP_RIGHT, -6, 6, true));
```

The last argument selects Auto when true and a fixed anchor when false. `HudPlacement.anchor()` returns the resolved screen anchor in either mode. `fromOrigin(x, y, elementWidth, elementHeight, screenWidth, screenHeight)` creates an Auto placement from GUI coordinates and the element's scaled dimensions. To restore the default position and Auto mode, call `hud.placement(hud.defaultPlacement())`.

The translation switch defaults to on. Use `language.translate(text, hud.translateText().get())` when preparing HUD text and refresh cached text and layout when it changes.

Keep one [HudRenderer](../src/main/java/pit12/runtime/hud/HudRenderer.java) in a field and pass it to the element for drawing and measuring content:

```java
private final HudRenderer hudRenderer = new HudRenderer();
```

Implement [HudElement](../src/main/java/pit12/runtime/hud/HudElement.java) with the following contract:

- `id()` is unique in the HUD registry and doesn't change during registration. `displayName()` and `config()` must not be null.
- `enabled()` returns true when the live HUD is enabled.
- `resize(pixelScale)` receives the Minecraft GUI scale times the HUD's scale setting. Pass this value to the renderer's `resize` before measuring or drawing content.
- `prepare(pixelScale, editing)` prepares live content when `editing=false` and sample content when `editing=true`, before bounds are read. Its default implementation calls `resize()`.
- `width()` and `height()` provide cheap unscaled logical size of the element's content.
- `render(partialTicks, editing)` draws the element from logical point `0,0` according to the caller's transformation.

HUD text uses the Minecraft font. Use the renderer's `text` method with the HUD config to draw text, and `textWidth(text)` and `fontHeight()` to measure it. Prepare display snapshots when inputs change. Keep expensive parsing, IO, and large world scans out of render callbacks.

For `editing=true`, prepare and render fixed sample content, including for disabled HUDs. Refresh its text and bounds when display settings or the language change. Measured bounds must match the rendered content.

The feature receives [HudRegistry](../src/main/java/pit12/runtime/hud/HudRegistry.java) from Bootstrap. Create the element once and register it in `start()`:

```java
hudRegistry.register(element);
```

Registration gives the HUD editor access to the element. The feature still owns its normal overlay callback and display data.

This is the regular overlay path, where `resolution` and `partialTicks` come from the overlay event:

```java
if (!hudRegistry.editing() && element.enabled()) {
    HudBounds bounds = HudRenderer.layout(element, resolution.getScaledWidth(),
            resolution.getScaledHeight(), resolution.getScaleFactor(), false);
    hudRenderer.render(element, bounds, partialTicks, false);
}
```

`layout` calls `prepare`, applies placement and scaling and keeps the resolved origin inside the screen if the element fits. `render` applies the transform and restores the UI rendering state.

In `stop()`, unregister the same element:

```java
hudRegistry.unregister(element);
```

Clear the element's cached rendering state before reuse.

For custom UI, [UiRenderer](../src/main/java/pit12/shared/rendering/UiRenderer.java) provides text measurements, rectangles, and textures. Construct it with Minecraft and call `resize(pixelScale)` before drawing shapes or textures. Use Minecraft's `FontRenderer` to draw text.

Keep the render origin in integer GUI coordinates before applying HUD scale.

Outside `HudRenderer.render`, use [UiRenderState](../src/main/java/pit12/shared/rendering/UiRenderState.java) to protect rendering state. Call `begin()` before drawing and `end()` in a `finally` block. Restore any extra rendering state your code changes.

## Game state

Bootstrap provides query contracts to features. Their live queries and subscriptions need the client thread.

[ClientSession](../src/main/java/pit12/runtime/session/ClientSession.java) provides `connection()`, `world()` and `revision()`. The connection and world can be `null`. Both identities are updated before the listeners run.

Keep `Runnable` in a field for session changes. Register it with `session.addListener(sessionListener)` in `start()` and read the current state once. Unregister it with `session.removeListener(sessionListener)` in `stop()`. Reset the state according to what it belongs to: the connection data lives for the connection, and the world data lives for the world.

[PlayerEntityTracker](../src/main/java/pit12/runtime/player/PlayerEntityTracker.java) provides `loadedPlayer(UUID)`. It returns the current loaded player entity, or `null` if the entity is absent or removed. Entities belong to Minecraft and must stay on the client thread. The UUID index updates when entities join, leave, or replace one another. World changes rebuild it from loaded players. Read the entity's ID or coordinates directly. Use `world.getEntityByID(id)` for ID queries.

Keep a [PlayerEntityListener](../src/main/java/pit12/runtime/player/PlayerEntityListener.java) in a field. Register it with `players.addListener(listener)` in `start()`, then query the players you need. Callbacks run on the client thread after the index changes. A replacement calls `onPlayerEntityRemoved(previous)` before `onPlayerEntityAdded(current)`, with the new entity already indexed. Handle `onPlayerEntitiesReset()` by clearing derived entity data and querying current players again. Unregister the same listener with `players.removeListener(listener)` in `stop()`.

[TabPresence](../src/main/java/pit12/runtime/player/TabPresence.java) separates membership from known names. Use `contains(UUID)` to check membership and `players()` to get the current UUID-to-profile-name map. A player with an unknown name may be present without appearing in that map.

Keep a [TabPresenceListener](../src/main/java/pit12/runtime/player/TabPresenceListener.java) in a field. In `start()`, register it with `tabPresence.addListener(tabListener)`, then read the current state. Handle `onPlayerSeen(playerId, name, joined)` for observed players, `onPlayerLeft(playerId)` for departures, and `onTabDisplayChanged(playerId)` for display changes. The name may be null, and `joined=false` means the entry was already present. In `stop()`, call `tabPresence.removeListener(tabListener)` with the same listener.

[PlayerNameCache](../src/main/java/pit12/runtime/player/PlayerNameCache.java) provides `displayName(UUID)` and `shortName(UUID)` for current Tab entries. Both return `null` when the entry is absent or its name is unknown. Display names keep formatting codes. Short names remove bounty suffixes and keep the rest of the display name. Departed players are removed, and connection changes clear the cache.

Subscribe to name changes with `addListener(Runnable)` in `start()`, then read the current names. In `stop()`, pass the same listener to `removeListener(Runnable)`.

Use `bindDisplayNameObserver(UUID, Consumer<String>)` to watch one current Tab entry. Each entry has one observer, called on the client thread after its display name changes. Name queries can also refresh an entry and call its observer. The name keeps formatting codes and may be `null`. Pass `null` as the observer to detach it. The subscription ends when the entry leaves Tab or the connection changes.

[NickTracker](../src/main/java/pit12/runtime/nick/NickTracker.java) provides `isNicked(UUID)` for players in Tab. NPCs are not Nicks.

Keep a [NickListener](../src/main/java/pit12/runtime/nick/NickListener.java) in a field. Register it with `nickTracker.addListener(listener)` in `start()`, then read the current Tab state, because existing entries are not replayed. `onNickChanged(playerId, name, nicked)` runs on the client thread when the status changes. `name` is the profile name. A Nick leaving Tab reports `nicked=false`. Unregister the same listener with `nickTracker.removeListener(listener)` in `stop()`.

[SidebarCache](../src/main/java/pit12/runtime/scoreboard/SidebarCache.java) provides `lines()` for the server sidebar. It returns an immutable snapshot of up to 15 lines in display order. Lines include footer text and keep formatting codes. The objective title is excluded. Each [SidebarLine](../src/main/java/pit12/runtime/scoreboard/SidebarLine.java) provides the entry ID, text, and sorting score through `entry()`, `text()`, and `score()`.

`formattedText(minecraft.fontRendererObj)` removes formatting codes and characters with a width of zero in the Minecraft font. Call it on the client thread. It computes the text on the first call and caches it for that line. Score changes retain the cache. Text changes create a new line with an empty cache.

Keep a [SidebarListener](../src/main/java/pit12/runtime/scoreboard/SidebarListener.java) in a field. Register it with `sidebar.addListener(listener)` in `start()`, then read `sidebar.lines()`. `onLineChanged(previous, current)` runs after the cache is updated. `previous` is null for an added line, and `current` is null for a removed line. A score change may move other lines, so read `lines()` for the current order. Retained snapshots and lines stay unchanged. Unregister the same listener in `stop()` with `sidebar.removeListener(listener)`.

[PlayerEquipmentAccess](../src/main/java/pit12/runtime/player/PlayerEquipmentAccess.java) provides `loadedEquipment(UUID)`. It returns `null` if the entity is unloaded or has not been observed yet. In a snapshot, check `heldItemKnown()` or `leggingsKnown()` before treating those slots as empty. Use `heldEnchantments()` or `leggingsEnchantments()` for already parsed data.

`copyEquipment(slot)` returns a copy for slots `0` through `4`: held item, boots, leggings, chestplate, and helmet. Empty and unobserved slots return `null`.

Keep a [PlayerEquipmentListener](../src/main/java/pit12/runtime/player/PlayerEquipmentListener.java) in a field. Register it with `equipment.addListener(equipmentListener)` in `start()`, then query the current players your feature needs. In `onPlayerEquipmentChanged(playerId, changedSlots, revision)`, query `loadedEquipment(playerId)` again to update the feature's data. The `changedSlots` mask uses `PlayerEquipmentCache.HELD_ITEM`, `BOOTS`, `LEGGINGS`, `CHESTPLATE`, and `HELMET`. Handle `onPlayerEquipmentRemoved(playerId)` by discarding that player's equipment data, and `onPlayerEquipmentReset()` by clearing derived equipment data. Unregister the same listener with `equipment.removeListener(equipmentListener)` in `stop()`.

`onPlayerEquipmentReceived(playerId, stack)` runs for each equipment packet before the tick combines changes. The stack may be `null` and belongs to Minecraft. Only read it during the callback. Copy it before retaining or changing it. Use this callback when intermediate equipment must be kept.

[PitContext](../src/main/java/pit12/runtime/pit/PitContext.java) provides `current()`. The returned `PitSnapshot` provides the Pit state and `spawnStateAt(x, y, z)`. The state is `UNKNOWN` until the map is identified. Pass snapshots to workers. Read live providers on the client thread.

[PitEnchantmentReader](../src/main/java/pit12/runtime/item/PitEnchantmentReader.java) reads the arbitrary item stacks with `read`, `contains` and `levelOf`. The missing data causes an empty result or level `0`. The entries with missing keys or non-positive levels are ignored. Use the equipment snapshot's parsed enchantments if they are already available.

Players are identified with UUIDs. The entity IDs are valid only in their world. Separate the unknown state from the confirmed absence.

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

Profile feature stores the settings registered in `ConfigCatalog`. Data other than the settings is owned by the feature's own store. Bootstrap provides storage paths under the Minecraft game directory.

Validate the data being loaded. Implement handling of older formats when changing file format and preserving unknown fields if possible. Failing loads must not overwrite the original data silently. Keep the live state and unsaved changes after a write failure and report the error.

[AtomicFile](../src/main/java/pit12/shared/storage/AtomicFile.java) needs the target `Path` and the whole contents as a string. A store calls it on its IO worker:

```java
AtomicFile.write(path, encodedJson);
```

It creates missing parent directories and writes UTF-8. It replaces the target atomically when supported, falling back to a regular replacement move otherwise. On failure, it throws with any cleanup errors attached.

The feature is responsible for its worker and for the order of the writes. On the client thread make a copy of the data being saved and pass it to the worker. Handle completion with `ClientThread.execute`. Verify the worker or generation producing the result. Verify the save revisions before clearing the unsaved changes, so that an old completion could not clear newer changes. Session and world checks are needed only for work dependent on them.

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

Do all the state change before notifying the listeners. Changes in the listener collection during the notification affect future notifications, not the current copy. The thrown `RuntimeException` from a listener is logged and the delivery continues. The helper runs synchronously on the calling thread. Callers handle thread dispatch and synchronization. Use direct calls for local queries and actions, and listeners for change notifications needed by several consumers.
