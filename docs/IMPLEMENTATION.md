# 12pit implementation

This guide covers the project classes and their usage. For package responsibilities, see [Architecture](ARCHITECTURE.md).

## Lifecycle

`ClientLifecycle` has `start()` which initializes the resources and `stop()` which releases them. Components that manage some resources implement this interface. There should be no implementation in the helper with no managed resources.

Components are created by `ClientBootstrap` in the constructor. There is a private `register...` method for each feature which creates and registers its config if required, builds the feature, and registers it in `components`. Pass required dependencies as arguments. Return only the API of the feature if subsequent registration methods require it.

Divide the constructor in three parts: shared runtime, feature providers, and other features. Create shared runtime within the constructor itself. Call feature methods and declare them sorted in order of `ConfigCategory.displayOrder()` followed by alphabetical order of their names. Sort the features without configuration in the group `Category: No config`. The same comment should be used in both groups and categories. Prioritize dependencies over category and name. Bootstrap initializes components in list order and stops them in reverse order. Registration methods only create and register objects, not listeners and workers, which should be done in `start()`.

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

## Config

`FeatureConfig` represents the settings of a feature and their Web UI metadata. Settings are defined in its subclass constructor:

```java
import static pit12.runtime.languages.Languages.source;

public final class StatusConfig extends FeatureConfig {
    private final BooleanSetting showNames;

    public StatusConfig() {
        super("status", source("Status"), new ConfigCategory("render", source("Render"), 100),
                source("Shows player status."));
        subcategory("appearance", source("Appearance"));
        subsubcategory("display", source("Display"));
        showNames = booleanSetting("show_names", source("Show names"),
                source("Shows player names."), true);
    }

    public boolean showNames() {
        return showNames.get();
    }
}
```

`ConfigCategory` provides category id, display name and order number. Reuse the category for related features. The Features page shows category buttons and an All button. All shows category headings. Selecting a category shows its features without repeating the heading.

`subcategory()` puts the following settings under a button at the top of the feature page. `subsubcategory()` groups settings within that page. Both levels are optional. Without `subcategory()`, groups appear directly on the feature page. Settings without a subsubcategory have no section heading. Settings defined before the first subcategory stay visible under every button. Starting a new subcategory clears the current subsubcategory. Settings and groups follow their definition order.

The four-argument `FeatureConfig` constructor adds `enabled` setting with default value `true`. Constructor with `toggleable` parameter allows to not add `enabled` setting; in this case the feature always returns true when asked whether it is enabled. Constructor with `defaultEnabled` parameter chooses the initial value. Method `setEnabled` changes the setting, not the lifecycle.

The following helpers create settings and register their metadata in the feature:

- `booleanSetting` creates a boolean.
- `integerSliderSetting` and `doubleSliderSetting` create a numeric setting with default value, minimum, maximum and step parameters.
- `keybindSetting` stores the key code from `0` to `255`. The feature is responsible for handling the input.
- `colorSetting` stores RGB color as `0xRRGGBB`. `colorPickerSetting` stores ARGB color as `0xAARRGGBB`.
- `choiceSetting` creates a choice with default id and `ChoiceSetting.Choice` instances with stable ids.
- `hudConfig` adds anchor, offset, scale, text shadow and monospace font settings to the HUD.

Feature ids are unique in the catalog. Ids of the settings and HUD configurations are unique in their feature. Id starts with a lowercase ASCII letter or digit; the rest can also be `.`, `-`, or `_`. Preserve saved ids while changing the display names. HUD configuration ids are prefixes like `status.offset_x`; do not create settings with ids conflicting with them.

Register this config in the register method of the feature in Bootstrap, then pass the same config to the feature:

```java
private void registerTooltip(ConfigCatalog configs) {
    TooltipConfig config = new TooltipConfig();
    configs.register(config);
    components.add(new TooltipFeature(configs, config));
}
```

Define all settings before registering the config. Registration binds their reads and writes to the catalog's client thread. Bootstrap calls `configs.freeze()` once after all feature registration methods return. This prevents new config registrations while allowing setting changes. Profiles capture the settings schema in `start()` after the catalog is frozen.

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
CommandNode command = CommandNode.command("message", source("Message commands"))
        .child(CommandNode.command("send", source("Show a message")).aliases("say")
                .arguments("<text>", 1, 1)
                .executes((sender, args) -> ChatFeedback.reply(sender, Tone.INFO, args[0]))
                .suggests((sender, args) -> Arrays.asList("hello", "test")))
        .build();
commands.register(command);
```

Use `register(command)` to make `/12pit message` available. If `/message` is supposed to be available too, use `register(command, true)` instead; both will use the same tree. Do not register a command twice. Names and aliases use only lowercase letters, digits, `-` and `_`; `12pit` is reserved as a root name. Names and aliases of siblings mustn't coincide.

`arguments(usage, minimum, maximum)` describes the argument display usage and number range. Default is no arguments. `executes` gets an `ICommandSender` and an array of `String` arguments without the matched command path. Registry checks the argument number; feature can check the values, readiness and result of the action.

A node with children is a group. Without arguments it displays auto-generated help, even if there's a handler. Leaf with a handler executes it, if the argument number is correct. Child's name or alias matches case-insensitively and overrides group's handler. Group handler can accept extra unmatched arguments. Adding a single `help` argument will show group help unless there's an explicit `help` child.

`requires(Predicate<ICommandSender>)` restricts use of a node. Help and completion hide children that cannot be used by the sender. Suggestion callback gets the arguments after the command path, including the current word. Return the complete set of suggestions; the registry filters it by the current prefix case-insensitively and removes duplicates. Returning null or empty list results in no suggestions.

Bootstrap provides [ForgeCommandAdapter](../src/main/java/pit12/platform/command/ForgeCommandAdapter.java) through the `Registrar` of the registry. Features don't register Forge command classes manually. There's no way to unregister commands in Forge. `stop()` disallows execution and completion and `start()` will reuse existing entry points. Registration is closed after the first start.

## Chat feedback

To send a message to the local player use [ChatFeedback](../src/main/java/pit12/shared/chat/ChatFeedback.java) and call `ChatFeedback.reply(sender, ChatFeedback.Tone.INFO, message)`. The tone should be `INFO`, `SUCCESS`, `WARNING` or `ERROR`, depending on the outcome. The shared utility will include `[12pit] »` prefix with gray brackets, bold aqua `12` and bold dark aqua `pit` followed by gray separator. Colors of the body text are white, green, yellow and red.

It is called from the client thread. In case of messages not related to a command, ensure that there is a local player before calling and sending him/her the message.

## Languages

Bootstrap uses one [Languages](../src/main/java/pit12/runtime/languages/Languages.java) instance and passes it to its consumers. `WebUiConfig` stores the selected language along with other settings. Language names and stable setting values are defined in `src/main/resources/assets/pit12/languages/languages.json`. Each translation catalog is loaded from its respective TXT resource on first use and cached.

Import the static `source(...)` method as shown in the config example for names, descriptions, choice labels, enum labels, and command descriptions. It will return the English text unmodified and mark it for the script. Config classes store the original text and its translated version separately. Bootstrap updates those values via `ConfigCatalog.localize(language)` whenever language is changed. Command registry translates command descriptions when displaying help. Do not modify saved IDs, command names, and aliases.

Inject `Languages` via the constructor for messages to be generated at runtime. Use `translate(...)` for messages that do not depend on parameters and `format(...)` for messages that do:

```java
ChatFeedback.reply(sender, Tone.WARNING, language.translate("No matching bindings"));
ChatFeedback.reply(sender, Tone.SUCCESS,
        language.format("Bound {0} to {1}", itemName, keyName));
```

Pass an English literal as the first argument to `format(...)` and `translate(...)`. Dynamic values go to the subsequent arguments. `{0}` corresponds to the first value, `{1}` to the second, and so on. Translations may reorder parameters. Translation is performed once when preparing display text and passed to the renderer or chat feedback helper. Absent or empty translation is equivalent to English. `translate(...)` will just return the original text in English; `format(...)` will still substitute its parameters.

To update cached HUD text, hold a language listener, register it in `start()` and unregister in `stop()`. `addListener(...)` will immediately call the listener and again after every language change. Update both the cached text and its measured layout at the same time. Prepare dynamic texts while updating the display snapshot. Render callback receives prepared text. Language switch and listener registration/removal occur on the client thread.

Frontend components import `t` from `./languages` and call `t('Settings')` or `t('Remove {0} players', count)`. Application will follow the server state language, load catalogs once, and update display text when language changes.

After adding or changing marked source text, run `python scripts/languages.py sync` from the repository root. The script finds Java `source(...)`, `translate(...)`, numbered `format(...)` calls, and frontend `t(...)` calls. It reads literals and literal concatenations, not variables. Sync preserves translations and leaves new entries untranslated. `python scripts/languages.py status` shows progress and file errors. See [Contributing translations](TRANSLATING.md) for editing and adding a language.

## HUD

[HudConfig](../src/main/java/pit12/runtime/config/HudConfig.java) stores settings related to HUD's placement and scale. Define it with `hudConfig` in FeatureConfig subclass' constructor and retain the result:

```java
hud = hudConfig("status", source("Status"), HudAnchor.TOP_LEFT, 6, 6, true);
```

The helper defines these settings: `status.anchor`, `status.offset_x`, `status.offset_y`, `status.scale`, `status.text_shadow`, `status.use_monospace_font`, and `status.translate_text`. Scale is stored as a percentage and defaults to `100`. The HUD editor controls placement and scale. Text shadow, font, and translation switches appear in Web UI. The font switch defaults to off and uses bundled Monocraft when enabled. The translation switch defaults to on; use `language.translate(text, hud.translateText().get())` when preparing HUD text and refresh cached text and layout when it changes.

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

Editing uses `editing=true`. Provide preview content when live data is empty or the HUD is disabled, and ensure the measured bounds match the content.

[UiRenderer](../src/main/java/pit12/shared/rendering/UiRenderer.java) provides the project text, rectangles and textures. Use `HudRenderer.text`, `textWidth` and `fontHeight` with the HUD's config to draw and measure its text. Pass the element's pixel scale to its `resize`. Reuse it. Monocraft uses logical size `9` to match its pixel grid; system fallback text uses `8`. Custom glyphs use nearest-neighbor sampling and screen-pixel positions. Keep the render origin in integer GUI coordinates before applying HUD scale. Call `close()` on it when its owner stops or releases those resources. It can create resources again after resizing. For custom UI outside of `HudRenderer`, use `begin()` from [UiRenderState](../src/main/java/pit12/shared/rendering/UiRenderState.java) together with `end()` in `finally` block. Restore the extra rendering state that your code modifies.

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

It creates parent directories and a temporary file near the target. It writes UTF-8, flushes and syncs the temporary file, then replaces the target. If atomic moves are unsupported, it falls back to a normal replacement move. On error it tries to delete the temporary file and throws; cleanup errors are attached to the error.

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
