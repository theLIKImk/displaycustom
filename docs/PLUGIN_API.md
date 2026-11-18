# 自定义展示实体 · Paper API 0.0.1

其他 Paper 插件可以通过 Bukkit 的 `ServicesManager` 获取 `io.github.displaycustom.api.DisplayCustomService`。服务在 DisplayCustom 启用后注册，停用时注销。

## 接入

在调用方的 `plugin.yml` 中声明依赖，确保 API 由 DisplayCustom 的类加载器提供。

```yaml
depend: [DisplayCustom]
```

编译时将本项目安装到本地 Maven 仓库，并在调用方使用 `provided` 依赖。

```xml
<dependency>
  <groupId>io.github.displaycustom</groupId>
  <artifactId>display-custom</artifactId>
  <version>0.0.1</version>
  <scope>provided</scope>
</dependency>
```

## 生成和控制模型

```java
import io.github.displaycustom.api.DisplayCustomService;

var service = getServer().getServicesManager().load(DisplayCustomService.class);
if (service == null) {
    throw new IllegalStateException("DisplayCustom service is unavailable");
}

String id = service.spawn("pelican_bicycle", location);
service.play(id, "ride", 1.0);
// service.stop(id);
// service.remove(id);
```

- `modelIds()` 和 `animationNames(modelId)` 返回当前已加载定义的只读快照。
- 在 Paper 主线程调用 API。生成使用 `Location` 的坐标和水平朝向，并检查模型与展示实体配额。
- `spawn(modelId, location)` 生成临时实例，随区块卸载或服务端停止清理。第三个参数传入 `true` 可持久化实例。
- `play`、`stop` 和 `remove` 使用返回的实例 ID。移除也支持尚未恢复的持久化实例。
- 这些方法由服务端插件调用，权限检查由调用方负责。玩家执行 `/dc` 时仍应用原有权限节点。
