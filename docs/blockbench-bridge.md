# Blockbench Bridge 0.1.0

适用 Blockbench 5.x 和 Display Custom 0.0.1。桥接读写 Display Custom JSON v1–v9。插件文件名为 `display_custom_bridge.js`。

## 安装与使用

1. 打开 Blockbench 的「文件 → 插件」，在插件窗口选择从文件加载，打开 `display_custom_bridge.js`。
2. 在「文件 → 导入」选择「导入 Display Custom JSON」，打开模型文件。也可以在新建页选择「Display Custom」，从空项目开始添加组和方盒。
3. 在编辑模式调整方盒与骨骼，在动画模式制作位移、旋转、缩放。每方块为 16 个 Blockbench 单位，+Z 朝前。导入的每个部件都有专用骨骼。移动或旋转该骨骼，方盒尺寸则围绕该骨骼的原点对称调整。
4. 用「编辑 → Display Custom 属性」编辑模型 ID、附加信息、选中的部件或当前动画。部件与动画属性使用 JSON，支持 `itemModel`、`heldItem`、部位或状态标签、`itemUse`、`parallel` 和声音事件。
5. 保存 `.bbmodel` 作为可继续编辑的工程，在「文件 → 导出」选择「导出 Display Custom JSON」。模型 ID 允许 1–48 个小写字母、数字、下划线或短横线，可独立于工程文件名设置。把导出文件放进服务端的 `plugins/DisplayCustom/models/` 后执行 `/dc reload`。

导入和导出都复用网页编辑器的协议校验。导入模型的状态标签、物品规则、握点、声音及非姿态关键帧数据会随工程保存。已有的文字与显隐关键帧会保留在原时间点。需要编辑这些事件时，用 Display Studio 的逐元素时间轴。

## 支持范围

- 方块和公共色使用近似色预览。自定义物品、文字和手持握点以方盒占位，导出保留原资源键。客户端仍需启用对应资源包。
- 骨骼组默认缩放为 `[1,1,1]`，动画中可改变缩放。骨骼动画使用局部 XYZ 欧拉角、数值关键帧和线性或定格插值。贝塞尔、表达式、网格、全局旋转及同一节点同一区间混用步进与线性会在导出时报错。IK 等约束需要先烘焙为支持的骨骼关键帧。
- 项目内的任意贴图不会自动转换成 Minecraft 资源包。实际贴图、命令与动画匹配规则见 Display Custom 的精简开发手册和模型协议。

开发时运行 `node scripts/build-blockbench-plugin.mjs` 生成单文件和独立 ZIP。运行 `node scripts/test-blockbench-bridge.mjs` 检查协议往返及姿态采样。
