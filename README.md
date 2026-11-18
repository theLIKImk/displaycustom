# 自定义展示实体 · Display Custom

Paper **26.3** 插件 **0.0.1** 使用方块、物品和文字展示实体拼装模型，支持动画、玩家绑定和持久化。Blockbench 扩展可以编辑 JSON v1–v9 的模型、骨骼和关键帧。

## 从源码构建

使用 JDK 25、Maven 3.9+ 和 Node.js 22+，在仓库根目录运行：

```sh
npm ci --prefix editor
node scripts/build-resource-pack.mjs
mvn -B -ntp clean package
node scripts/test-blockbench-bridge.mjs
node scripts/build-blockbench-plugin.mjs
```

- 服务端插件生成在 `target/display-custom-0.0.1.jar`。需要本地 Maven 依赖时，将 `package` 换成 `install`。
- Blockbench 扩展生成在 `release/display_custom_bridge.js`，可在 Blockbench 的插件管理界面从文件加载。
- `editor/src/` 提供两个插件构建与测试所用的共享模型协议和资源包代码。

## 安装和配置

把 JAR 放入 Paper 的 `plugins/` 后启动。Paper 插件 ID 为 `DisplayCustom`，中文显示名为「自定义展示实体」。默认范例可用 `/dc spawn` 生成。执行 `/dc help` 查看其他命令。

服务器发给控制台的语言由 `plugins/DisplayCustom/config.yml` 中的 `language` 设置，默认 `zh_cn`。启用 `use-player-locale` 时，玩家消息优先使用客户端语言。首次启动会生成 `lang/zh_cn.properties` 和 `lang/en_us.properties`。可以编辑这两个 UTF-8 文件，或添加如 `lang/fr_fr.properties` 的语言文件。保留 `{0}` 这样的参数占位符，执行 `/dc reload` 应用。

- [Paper 插件 API](docs/PLUGIN_API.md)
- [Blockbench 使用说明](docs/blockbench-bridge.md)
