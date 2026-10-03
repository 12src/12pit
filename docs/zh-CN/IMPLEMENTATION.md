<!-- Source: docs/IMPLEMENTATION.md; Based on: 1cdbbfe0fc6343ec94633a6bbf3f70b454c5f936 -->

# 12pit 实现

本文件介绍项目中的类及其使用规则。每一节介绍项目中的一个部分。如果接口或规则过时，请修改本文。包的职责见[架构](ARCHITECTURE.md)。

## 生命周期

`ClientLifecycle` 提供初始化资源的 `start()` 和释放资源的 `stop()`。管理资源的组件实现此接口。不管理资源的辅助工具不应实现它。

`ClientBootstrap` 创建组件，并通过构造器注入为它们提供依赖。添加生命周期组件的提供者后，将组件添加到此对象的 `components` 字段。Bootstrap 按列表中的顺序启动组件，并按相反顺序停止它们。对象在构造器中定义和初始化；监听器和工作线程应放在 `start()` 中。

Bootstrap 注册组件并调用其 `start()` 方法。如果 `start()` 引发异常，Bootstrap 会停止此组件和之前已启动的所有组件。`stop()` 应在任何情况下释放资源，包括仅完成部分初始化或被调用多次的情况。如果某个组件在 `stop()` 中抛出异常，该异常会被记录，但其他所有组件仍会停止。

保留监听器的引用，以便注销同一个对象。以下示例展示了具有 `client` 和 `configs` 依赖、保存的 `configListener` 以及 `started` 标志的组件：

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

组件启动和 `enabled` 设置是不同的事情。即使功能被禁用，它仍可以保持对配置变更的订阅和在 HUD 编辑器中的注册。只有功能启用时才需要的工作，应在 `enabled` 设置变化时启动和停止。监听器、项目监听器和绑定应在其所有者停止时注销。

服务器断开连接或世界变化会重置会话数据，而不停止功能。在 tick 之间进行的工作应管理自己的超时、取消和恢复。Minecraft 正常关闭时，会在世界和图形上下文释放前调用 Bootstrap。此时应释放工作线程和渲染资源。强制终止进程则无法完成这些操作。

## 客户端线程

`ClientThread` 检查线程访问并分派回调。Bootstrap 使用 Minecraft 的线程检查器和任务分派器创建它。通过构造器接收它；如果配置目录已经是依赖，也可以使用 `configs.clientThread()`。

在实时 API 边界调用 `client.check()`。从错误的线程调用会抛出 `IllegalStateException`。`client.execute(Runnable)` 通过提供的分派器分派回调，并检查线程。它不会创建工作线程，也不会检查结果是否仍然有效。

磁盘 I/O、HTTP 请求和开销大的计算在所属的工作线程上执行，并使用数据副本。通过 `client.execute` 返回结果。检查所有者的启动状态，以及结果对应的请求或代次。如果工作依赖会话和世界，也检查它们的身份标识。

`ClientThread.current()` 获取调用线程，并直接在该线程上执行回调。从另一个线程调用此 current 实例的 `execute()` 会抛出异常。它适用于测试和不需要跨线程分派的代码。它不会查找 Minecraft 的客户端线程。

## 配置

`FeatureConfig` 表示功能的设置及其 Web UI 元数据。在其子类构造器中定义设置：

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

`ConfigCategory` 提供分类 ID、显示名称和顺序编号。相关功能复用同一分类。`subcategory()` 调用将其后的设置分组。设置按定义顺序排列。

四参数的 `FeatureConfig` 构造器会添加默认值为 `true` 的 `enabled` 设置。带有 `toggleable` 参数的构造器允许不添加 `enabled` 设置；在这种情况下，查询功能是否启用时，它始终返回 true。带有 `defaultEnabled` 参数的构造器选择初始值。`setEnabled` 方法改变设置，而非生命周期。

以下辅助方法创建设置，并在功能中注册其元数据：

- `booleanSetting` 创建布尔值。
- `integerSliderSetting` 和 `doubleSliderSetting` 创建数值设置，参数包括默认值、最小值、最大值和步长。
- `keybindSetting` 存储从 `0` 到 `255` 的按键代码。功能负责处理输入。
- `colorSetting` 将 RGB 颜色存储为 `0xRRGGBB`。`colorPickerSetting` 将 ARGB 颜色存储为 `0xAARRGGBB`。
- `choiceSetting` 使用默认 ID 和具有稳定 ID 的 `ChoiceSetting.Choice` 实例创建选项设置。
- `hudConfig` 为 HUD 添加锚点、偏移、缩放和文字阴影设置。

功能 ID 在目录中唯一。设置和 HUD 配置的 ID 在其功能中唯一。ID 以小写 ASCII 字母或数字开头；其余字符还可以是 `.`、`-` 或 `_`。改变显示名称时，保留已保存的 ID。HUD 配置 ID 是 `status.offset_x` 这样的前缀；不要创建 ID 与它们冲突的设置。

`ConfigCatalog` 在 Bootstrap 中注册所有配置对象。在现有的 `freeze()` 调用之前添加新注册，并向其使用者提供同一个配置对象：

```java
StatusConfig statusConfig = new StatusConfig();
configs.register(statusConfig);
```

所有设置都应在注册前定义。注册会将它们的读写操作绑定到目录的客户端线程。`freeze()` 阻止后续功能注册，但不阻止修改其设置。配置方案在启动时记录设置结构。Web UI 读取目录和设置元数据，因此普通设置不需要单独的页面或保存逻辑。

使用设置的 `get()` 和 `set()` 方法访问其实时值。`set()` 校验值，并在值发生变化时发送通知。`Setting` 的子类在初始化 `requireValue` 使用的字段后校验默认值。

将配置监听器保存为字段，在 `start()` 中注册，在 `stop()` 中注销。要得到已标记为需要更新的显示数据快照，监听器可以将其标记为需要更新：

```java
private boolean snapshotDirty = true;
private final ConfigChangeListener configListener = changes -> {
    if (changes.affects("status", "show_names")) {
        snapshotDirty = true;
    }
};
```

`ConfigChangeSet.affects(featureId, settingId)` 判断变更的 ID。监听器在变更处理后被调用。`snapshot()` 复制所有已注册的值。`apply(snapshot)` 在更新实时设置前校验所有已知值，然后为整组变更发送一次通知。缺失的已知值会重置为默认值；这是完整快照，不是补丁。未知 ID 会被忽略。空快照不会触发通知。

`recoverSavedValues(snapshot, problems)` 将无效的已保存值恢复为默认值，并通过回调返回这些值。它创建规范化的快照，但不应用它。实时输入改用普通校验。配置方案功能负责保存和切换配置快照。

## 命令

[CommandNode](../../src/main/java/pit12/runtime/command/CommandNode.java) 是不可变的命令树。功能在构造器中接收共享的 [CommandRegistry](../../src/main/java/pit12/runtime/command/CommandRegistry.java)，并在注册表首次 `start()` 前向其中注册定义。

以下命令树包含分组、子节点、别名、必需参数、处理器和补全候选项：

```java
CommandNode command = CommandNode.command("message", "Message commands")
        .child(CommandNode.command("send", "Show a message").aliases("say")
                .arguments("<text>", 1, 1)
                .executes((sender, args) -> CommandRegistry.reply(sender, args[0]))
                .suggests((sender, args) -> Arrays.asList("hello", "test")))
        .build();
commands.register(command);
```

使用 `register(command)` 使 `/12pit message` 可用。如果 `/message` 也应可用，改用 `register(command, true)`；两者会使用同一棵树。不要重复注册命令。名称和别名只能使用小写字母、数字、`-` 和 `_`；`12pit` 是保留的根名称。同级节点的名称和别名不得相同。

`arguments(usage, minimum, maximum)` 描述显示给用户的参数用法和数量范围。默认没有参数。`executes` 接收一个 `ICommandSender` 和一个不包含已匹配命令路径的 `String` 参数数组。注册表检查参数数量；功能可以检查值、就绪状态和操作结果。`CommandRegistry.reply` 添加共享的聊天前缀。

有子节点的节点是分组。没有参数时，它显示自动生成的帮助，即使存在处理器。带有处理器的叶节点在参数数量正确时执行处理器。子节点的名称或别名匹配不区分大小写，并覆盖分组的处理器。分组处理器可以接收额外的未匹配参数。添加单个 `help` 参数会显示分组帮助，除非存在显式的 `help` 子节点。

`requires(Predicate<ICommandSender>)` 限制节点的使用。帮助和补全会隐藏发送者无法使用的子节点。补全回调接收命令路径之后的参数，包括当前单词。返回完整的补全建议集合；注册表按当前前缀过滤，不区分大小写，并去除重复项。返回 null 或空列表表示没有建议。

Bootstrap 通过注册表的 `Registrar` 提供 [ForgeCommandAdapter](../../src/main/java/pit12/platform/command/ForgeCommandAdapter.java)。功能不手动注册 Forge 命令类。Forge 无法注销命令。`stop()` 禁止执行和补全，`start()` 会复用现有入口。首次启动后关闭注册。

## HUD

[HudConfig](../../src/main/java/pit12/runtime/config/HudConfig.java) 存储 HUD 位置和缩放相关设置。在 FeatureConfig 子类的构造器中使用 `hudConfig` 定义它，并保留结果：

```java
hud = hudConfig("status", "Status", HudAnchor.TOP_LEFT, 6, 6, true);
```

该辅助方法定义以下设置：`status.anchor`、`status.offset_x`、`status.offset_y`、`status.scale` 和 `status.text_shadow`。缩放以百分比存储，默认值为 `100`。HUD 编辑器控制位置和缩放。文字阴影作为 Web UI 中的设置提供。配置方案功能将这些值与功能的其他设置一起保存。

[HudElement](../../src/main/java/pit12/runtime/hud/HudElement.java) 的实现应满足以下契约：

- `id()` 在 HUD 注册表中唯一，且在注册期间不变。`displayName()` 和 `config()` 不得为 null。
- 实时 HUD 启用时，`enabled()` 返回 true。
- `resize(pixelScale)` 接收 Minecraft GUI 缩放与 HUD 缩放设置的乘积。
- `prepare(pixelScale, editing)` 在访问边界前准备实时或预览内容。其默认实现调用 `resize()`。
- `width()` 和 `height()` 以低开销提供元素内容未经缩放的逻辑尺寸。
- `render(partialTicks, editing)` 根据调用者的变换，从逻辑点 `0,0` 开始绘制元素。

功能从 Bootstrap 获得 [HudRegistry](../../src/main/java/pit12/runtime/hud/HudRegistry.java)。它只创建一次元素，并在 `start()` 中注册：

```java
hudRegistry.register(hud);
```

在 `stop()` 中注销同一个元素：

```java
hudRegistry.unregister(hud);
```

注册使 HUD 编辑器能够访问元素。它不安排元素的常规渲染。功能拥有自己的叠加层回调和显示数据。根据输入变化生成显示快照，不要在渲染回调中执行开销大的解析、IO 操作和大范围世界扫描。

在字段中保留一个 [HudRenderer](../../src/main/java/pit12/runtime/hud/HudRenderer.java)：

```java
private final HudRenderer hudRenderer = new HudRenderer();
```

以下是常规叠加层路径，其中 `resolution` 和 `partialTicks` 来自叠加层事件：

```java
if (!hudRegistry.editing() && hud.enabled()) {
    HudBounds bounds = HudRenderer.layout(hud, resolution.getScaledWidth(),
            resolution.getScaledHeight(), resolution.getScaleFactor(), false);
    hudRenderer.render(hud, bounds, partialTicks, false);
}
```

`layout` 调用 `prepare`，应用位置和缩放，并在元素能放入屏幕时将计算得到的原点保持在屏幕内。`render` 应用变换，并在 `finally` 块中恢复 UI 渲染状态。常规叠加层应在编辑期间跳过渲染，因为编辑器会自行渲染元素。

编辑时 `editing=true`。当实时数据为空或 HUD 被禁用时，提供预览内容，并确保测量的边界与内容一致。启用的 HUD 应已注册，以便在编辑器中定位。

[UiRenderer](../../src/main/java/pit12/shared/rendering/UiRenderer.java) 提供项目的文字、矩形和纹理。将元素的像素缩放传给它的 `resize`。复用它，并只保留少量字体大小；每种大小管理自己的字体资源。当其所有者停止或释放这些资源时，对它调用 `close()`。它可以在调整大小后再次创建资源。对于 `HudRenderer` 之外的自定义 UI，使用 [UiRenderState](../../src/main/java/pit12/shared/rendering/UiRenderState.java) 的 `begin()`，并在 `finally` 块中配合调用 `end()`。恢复代码额外修改的渲染状态。

## 游戏状态

Bootstrap 为功能提供查询契约。其实时查询和订阅需要客户端线程。使用现有提供者，而不创建第二个跟踪器或会话所有者。

[ClientSession](../../src/main/java/pit12/runtime/session/ClientSession.java) 提供 `connection()`、`world()` 和 `revision()`。连接和世界可以为 `null`。两个身份标识都会在监听器运行前更新。旧的断开连接和卸载事件都不能清除替换后的连接或世界。

在字段中保留用于会话变化的 `Runnable`。在 `start()` 中通过 `session.addListener(sessionListener)` 注册它，并读取一次当前状态；添加监听器不会触发初始回调。在 `stop()` 中通过 `session.removeListener(sessionListener)` 注销它。按状态的归属重置状态：连接数据的存续期与连接一致，世界数据的存续期与世界一致。不要在任何一种变化时删除用户数据。

[TabPresence](../../src/main/java/pit12/runtime/player/TabPresence.java) 将成员关系与已知名称分开。使用 `contains(UUID)` 检查成员关系。使用 `players()` 获取当前 UUID 到档案名称的映射；如果玩家名称未知，玩家可能存在，但不在该映射中。[TabPresenceListener](../../src/main/java/pit12/runtime/player/TabPresenceListener.java) 报告已观察到的玩家、离开和显示变化。监听器由其所有者保存和注销。

[PlayerEquipmentAccess](../../src/main/java/pit12/runtime/player/PlayerEquipmentAccess.java) 提供 `loadedEquipment(UUID)` 和装备监听器。如果实体未加载或尚未被观察到，查询返回 `null`。在快照中，将槽位视为空之前，先检查 `heldItemKnown()` 或 `leggingsKnown()`。`copyHeldItem()` 和 `copyLeggings()` 返回防御性副本，但 `null` 既可能表示未知槽位，也可能表示已知的空槽位。使用 `heldEnchantments()` 或 `leggingsEnchantments()` 获取已解析的数据。装备监听器报告变化、移除和重置。

[PitContext](../../src/main/java/pit12/runtime/pit/PitContext.java) 提供 `current()`。返回的 `PitSnapshot` 提供地图、Pit 状态、修订号和 `spawnStateAt(x, y, z)`。在地图被识别前，状态为 `UNKNOWN`。快照可以传给工作线程；实时提供者不可以。

[PitEnchantmentReader](../../src/main/java/pit12/runtime/item/PitEnchantmentReader.java) 使用 `read`、`contains` 和 `levelOf` 读取任意物品堆叠。缺失数据会产生空结果或等级 `0`。缺少键或等级为非正数的条目会被忽略。如果装备快照的已解析附魔已经可用，就使用它们。

玩家通过 UUID 标识。实体 ID 仅在其所在世界中有效。区分未知状态和已确认的不存在。为每个缓存指定所有者和重置规则；只有为了解决明确的问题才添加缓存。

## Mixin

Mixin 类位于 `platform.mixin` 下。特定功能专用的 Mixin 放在 `platform.mixin.feature.<feature>` 下，与该功能的包名对应。其他类不应放在那里，因为 Mixin 类不能作为普通辅助工具加载。

绑定接口允许接收来自被注入对象的调用，而不依赖 Mixin 类。将接口绑定到调用的所有者。Bootstrap 将目标转换为绑定接口，并传给使用者。例如，gamma 功能以 `GammaBinding` 接收渲染器：

```java
components.add(new GammaFeature(configs, gammaConfig,
        (GammaBinding) minecraft.entityRenderer));
```

所有者在启动时绑定其配置或观察者，并在停止时解除绑定。Mixin 负责未绑定状态。如果放在另一个类中也不会造成混淆，小型无状态改动可以保留在 Mixin 中。Mixin 中不允许阻塞 IO，也不允许无关功能之间的交互。

在 [mixins.pit12.json](../../src/main/resources/mixins.pit12.json) 的 `client` 列表中注册客户端 Mixin 名称。条目相对于其配置的包。对于 `pit12.platform.mixin.feature.gamma.EntityRendererMixin`，条目是 `feature.gamma.EntityRendererMixin`。不要修改该资源的 package 和 refmap 占位符。

[ArchitectureTest](../../src/test/java/pit12/architecture/ArchitectureTest.java) 检查 Mixin 包、功能归属、已注册与已实现的 Mixin 是否一致，以及项目依赖规则。

## 存储

配置方案功能存储在 `ConfigCatalog` 中注册的设置。其他功能不重复存储设置。设置以外的数据由功能自己的存储拥有。Bootstrap 提供 Minecraft 游戏目录下的存储路径。

校验加载的数据。更改文件格式时，实现对旧格式的处理，并尽可能保留未知字段。加载失败时不得静默覆盖原始数据。写入失败后保留实时状态和未保存的改动，并报告错误。

[AtomicFile](../../src/main/java/pit12/shared/storage/AtomicFile.java) 需要目标 `Path` 和作为字符串的完整内容。存储在其 IO 工作线程上调用它：

```java
AtomicFile.write(path, encodedJson);
```

它创建父目录和目标附近的临时文件。它以 UTF-8 写入，刷新并同步临时文件，然后替换目标。如果不支持原子移动，它会回退为普通替换移动。这种回退不保证原子替换。该辅助工具不会将多个文件写入合并为一个事务，也不控制它们的顺序。发生错误时，它尝试删除临时文件并抛出异常；清理错误会附加到该错误上。

功能负责自己的工作线程和写入顺序。在客户端线程上复制待保存的数据，并传给工作线程。使用 `ClientThread.execute` 处理完成结果。核验产生结果的工作线程或代次。在清除未保存的改动前核验保存修订号，防止旧的完成结果清除较新的改动。只有依赖会话和世界的工作才需要检查它们；已保存的用户数据在断开连接后仍然保留。

`stop()` 停止新工作，处理待完成的保存，并关闭工作线程。清理不得等待只能在同一个已被阻塞的客户端线程上调用的回调。存储和查找依赖必须能在测试中替换。

## 结果和监听器

[OperationResult](../../src/main/java/pit12/shared/result/OperationResult.java) 表示公开修改操作的预期结果。状态包括 `SUCCESS`、`UNAVAILABLE`、`INVALID_VALUE` 和 `NOT_FOUND`：

```java
return OperationResult.failure(OperationResult.Status.INVALID_VALUE,
        "The key is not valid");
```

`success(value)` 和 `success(value, message)` 包含可选的值。调用 `value()` 前检查 `succeeded()` 或 `status()`。发生错误时，值为 `null`；没有返回值的成功操作也可能返回 `null`。具有更具体结果类型的功能保持这一契约。在错误线程上的不当调用和内部契约被破坏都是编程错误。

[Listeners.notify](../../src/main/java/pit12/shared/event/Listeners.java) 从当前集合的副本中调用监听器：

```java
Listeners.notify(listeners, Runnable::run);
```

在通知监听器前完成所有状态变更。通知期间监听器集合的变化影响后续通知，不影响当前副本。监听器抛出的 `RuntimeException` 会被记录，通知继续传递。该辅助工具是同步的；它不在客户端线程上分派，也不会使集合具备线程安全性。所有者停止时必须移除订阅。本地查询和操作使用直接调用，多个使用者需要的变更通知使用监听器。
