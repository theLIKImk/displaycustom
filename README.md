# 自定义展示实体 · Display Custom

使用方块、物品和文字展示实体拼装模型，支持动画、玩家绑定和持久化。
（Blockbench 扩展可以编辑 JSON v1–v9 的模型、骨骼和关键帧。）

## 效果展示
![玩家绑定模型](/doc/img1.png "玩家绑定模型")  

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

## 安装

把 JAR 放入 Paper 的 `plugins/` 后启动。

- [Paper 插件 API](docs/PLUGIN_API.md)
- [Blockbench 使用说明](docs/blockbench-bridge.md)
