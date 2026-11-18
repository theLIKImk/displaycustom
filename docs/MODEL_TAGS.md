# 建模标签基准

适用于 **自定义展示实体 0.0.1**。制作带玩家绑定、武器动作和受伤表情的模型时，使用 Display Custom **JSON v9**。文件名必须与根字段 `name` 一致，例如 `my_model.json` 对应 `"name": "my_model"`。模型正面朝本地 **+Z**，高度沿 **+Y**。关键帧的 `time` 使用秒，Minecraft 的 1 tick 等于 0.05 秒。

## 部位标签

`parts[].tags` 或 `groups[].tags` 可给每个节点设置一个部位标签。建议在头部骨骼设置 `"tags": ["head"]`，把脸、头发和头饰挂在该骨骼下。

| 标签 | 用途 |
| --- | --- |
| `body` | 身体或模型根 |
| `head` | 头部跟随玩家相对视角。嵌套时由最外层带 `head` 的节点应用视角旋转，子节点继承 |
| `arm` | 手臂 |
| `leg` | 腿 |
| `foot` | 脚 |

整体按玩家身体的水平朝向放置。四肢仍需通过动画轨道驱动。

## 玩家状态与事件

在 `animations.<动画名>.tags` 上设置状态。同一个标签在整份模型里只能出现一次。动作名称可以自定义，例如名为 `walk_cycle` 的动作可设置 `"tags": ["walk"]`。

| 标签 | 触发条件 |
| --- | --- |
| `stand` | 站立 |
| `walk` | 行走 |
| `run` | 疾跑 |
| `sneak` | 潜行静止 |
| `sneak_walk` | 潜行移动 |
| `sit` | 乘坐 |
| `climb` | 攀爬 |
| `jump` | 离地上升 |
| `fall` | 离地下落 |
| `swim` | 游泳 |
| `fly` | 飞行或鞘翅滑翔 |
| `sleep` | 睡觉 |
| `hurt` | 玩家实际受到伤害。一次性并行覆盖所列轨道，再次受伤从头播放 |

基础姿态优先级为睡眠、乘坐、飞行、游泳、攀爬、离地、潜行、地面移动、站立。插件先查状态标签，再查同名动画。`run` 可回退到 `walk`，`sneak_walk` 可回退到 `sneak` 或 `walk`，`fall` 可回退到 `jump`。最终尝试 `stand`、旧名称 `idle` 或模型的基础姿态。

- **受伤**使用 `"tags": ["hurt"], "loop": false, "parallel": true`。该动作只使用 `hurt` 标签，无需 `itemUse`。做受伤表情时只给眼睛和嘴巴设轨道，跑步和武器动作便可继续。要瞬间切换五官可在轨道中使用 `visible` 和 `"interpolation": "step"`。编辑器默认允许 32 段普通动画另加一段受伤动画，所有轨道共用关键帧额度。
- **死亡**按动画名称 `death` 触发，是单独的事件。建议使用 `"loop": false`；`death` 不可写入 `tags`。

## 物品动作与握点

物品动作的触发写在 `animations.<动画名>.itemUse`。示例：

```json
{
  "duration": 0.4,
  "loop": false,
  "parallel": true,
  "itemUse": { "state": "use", "items": ["#sword", "my_pack:steel_sabre"] },
  "tracks": {}
}
```

| 字段 | 允许值及含义 |
| --- | --- |
| `state` | `hold` 握持、`charge` 正在使用或蓄力、`use` 挥动或释放等插件识别的使用事件 |
| `items` | 精确的资源包物品模型键、原版物品 ID、类别选择器或 `*`。示例为 `my_pack:steel_sabre`、`minecraft:crossbow`、`#sword` |
| `timing` | 可选的 `kinetic` 计时。用于 v9 的非循环 `charge` 动作，实际物品有 `kinetic_weapon` 时按长矛的三个阶段采样 |

支持的类别选择器为 `#sword`、`#spear`、`#axe`、`#trident`、`#mace`、`#shield`、`#pickaxe`、`#fishing_rod`、`#bow`、`#elytra`、`#crossbow`、`#other`。匹配优先级从高到低为自定义物品模型键、原版 ID、类别、`*`。同一个 `state` 与选择器组合只能绑定一段动画。上例的 `tracks` 留空，制作时需写入实际姿态。

物品动画默认并行。显式设置 `"parallel": true` 时只覆盖自身关键帧轨道。设置 `false` 会独占模型，未写轨道的节点采用默认姿态。常见做法是把武器动作限制到手臂与握点轨道，步态由基础动作提供。

左右手物品握点使用独立的 `type: "item"` 部件，按物理手侧设置 `"heldItem": "left"` 或 `"heldItem": "right"`。每侧最多一个，省略静态 `itemModel`。把握点挂在对应的手或前臂骨骼下，按原版第三人称手持方式调整局部变换。物品动作以右手制作时，成对节点的 ID 用 `left_` / `right_` 前缀，插件可在左手触发时交换轨道并镜像姿态。

## 可加载的最小示例

保存为 `tag_demo.json`。模型睁眼时显示两个白色像素块，受伤时短暂隐藏，实际角色可以把同组的深色五官重新定位成受伤表情。

```json
{
  "version": 9,
  "name": "tag_demo",
  "groups": [
    { "id": "body", "tags": ["body"], "position": [0, 0, 0], "rotation": [0, 0, 0], "scale": [1, 1, 1] },
    { "id": "head", "group": "body", "tags": ["head"], "position": [0, 1.4, 0], "rotation": [0, 0, 0], "scale": [1, 1, 1] }
  ],
  "parts": [
    { "id": "face", "group": "head", "material": "CYAN_CONCRETE", "position": [0, 0, 0], "rotation": [0, 0, 0], "scale": [0.6, 0.5, 0.4] },
    { "id": "eye_left", "group": "head", "material": "WHITE_CONCRETE", "position": [0.13, 0.04, 0.21], "rotation": [0, 0, 0], "scale": [0.12, 0.12, 0.03] },
    { "id": "eye_right", "group": "head", "material": "WHITE_CONCRETE", "position": [-0.13, 0.04, 0.21], "rotation": [0, 0, 0], "scale": [0.12, 0.12, 0.03] }
  ],
  "animations": {
    "idle_pose": { "tags": ["stand"], "duration": 1, "loop": true, "tracks": {} },
    "hit_face": {
      "tags": ["hurt"], "duration": 0.35, "loop": false, "parallel": true, "interpolation": "step",
      "tracks": {
        "eye_left": [
          { "time": 0, "visible": false, "position": [0.13, 0.04, 0.21], "rotation": [0, 0, 0], "scale": [0.12, 0.12, 0.03] },
          { "time": 0.35, "visible": true, "position": [0.13, 0.04, 0.21], "rotation": [0, 0, 0], "scale": [0.12, 0.12, 0.03] }
        ],
        "eye_right": [
          { "time": 0, "visible": false, "position": [-0.13, 0.04, 0.21], "rotation": [0, 0, 0], "scale": [0.12, 0.12, 0.03] },
          { "time": 0.35, "visible": true, "position": [-0.13, 0.04, 0.21], "rotation": [0, 0, 0], "scale": [0.12, 0.12, 0.03] }
        ]
      }
    }
  }
}
```

放入服务端 `plugins/DisplayCustom/models/`，执行 `/dc reload`。用 `/dc modelinfo tag_demo` 查看状态映射与动画，用 `/dc use tag_demo` 绑定测试。
