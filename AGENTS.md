# DCTimer-Android AGENTS

## 项目简介

- 本项目是基于 `DCTimer-Android` 的二次开发版本，当前主方向是完善蓝牙硬件计时能力。
- 当前硬件主线分为两类计时设备和一类可选执行设备：
  - `智能魔方`
  - `蓝牙计时器`
  - `魔方机器人`：`GAN Robot`，与智能魔方协同执行物理打乱或复原，不参与计时和成绩保存。
- 当前已接入设备：
  - 智能魔方：`MoYu32`、`QiYi / Tornado V4 智能版`、`GAN v2 / v3 / v4`
  - 蓝牙计时器：`QiYi Smart Timer`

## 当前基线

- Android 工程基线：`AndroidX + AGP 8.9.2 + Gradle 8.11.1 + JDK 17`
- `compileSdk / targetSdk`：`35`
- 文件导入导出、打乱导入导出、背景图选择已迁移到 `SAF / Uri`
- 蓝牙硬件入口已拆分为 `智能魔方` / `蓝牙计时器`

## 关键目录索引

- `docs/`：项目状态、架构边界和路线图；开始功能开发或排查前，优先阅读 `project.md`、`architecture.md`、`roadmap.md`。
- `docs/others/`：非主线协议记录、专项训练计划和功能设计索引，需要处理对应专项时再查阅。
- `app/`：Android 应用主模块；构建脚本、源码、资源和 Manifest 都在该模块内。
- `app/src/main/AndroidManifest.xml`：应用权限、Activity、组件声明和 Android 入口配置。
- `app/src/main/java/com/dctimer/`：DCTimer 主业务代码包，优先从这里定位应用逻辑。
- `app/src/main/java/com/dctimer/activity/`：主要页面与流程入口；`MainActivity.java` 是计时主界面，也是蓝牙设备状态、智能魔方预览和计时链路的重要汇合点。
- `app/src/main/java/com/dctimer/util/`：通用工具与蓝牙协议核心；`BluetoothTools.java` 负责智能魔方和蓝牙计时器的扫描、连接、基础分发，`SmartCubeProtocol.java` / `SmartTimerProtocol.java` 是对应协议入口。`GanRobotBleClient.java`、`GanRobotProtocol.java`、`GanRobotExecutor.java` 与 `GanRobotSessionState.java` 负责可选 GAN Robot 的独立 BLE 连接、协议、执行和协同状态。
- `app/src/main/java/com/dctimer/model/`：计时、成绩、设备、魔方状态等模型对象。
- `app/src/main/java/com/dctimer/database/`：本地数据库、成绩和配置持久化相关逻辑。
- `app/src/main/java/com/dctimer/dialog/`：设置、选择、设备连接等弹窗交互。
- `app/src/main/java/com/dctimer/adapter/`：列表、会话、成绩等 Recycler/List 适配器。
- `app/src/main/java/com/dctimer/view/`：自定义视图；`SmartCube3DView.java` 是智能魔方 3D 预览，`SmartCubeImageView.java` 是普通打乱图绘制入口。
- `app/src/main/java/com/dctimer/widget/`：复用 UI 控件与较小的界面组件。
- `app/src/main/java/com/dctimer/aes/`：兼容旧蓝牙链路的 AES/解密辅助代码。
- `app/src/main/java/cs/`、`app/src/main/java/scrambler/`、`app/src/main/java/solver/`：打乱生成、求解和魔方算法相关代码，通常只在改动打乱、状态校验或求解逻辑时进入。
- `app/src/main/java/com/dingmouren/`、`app/src/main/java/uz/`：第三方/移植的颜色选择器相关代码，除非处理对应 UI，不建议顺手改动。
- `app/src/main/res/`：Android 资源目录；布局在 `layout/`，菜单在 `menu/`，图片和形状在 `drawable*/`，文案和数组在 `values*`。
- `app/src/main/assets/`：内置字体与检测音等静态资源。
- `gradle/`、`gradlew`、`gradlew.bat`、`settings.gradle`、根目录 `build.gradle`：Gradle Wrapper 与项目构建入口；升级构建链路或依赖时再改。
- `ref/`：参考实现和协议资料辅助目录；只作对照，不作为当前真实实现。
- `.github/`：README 展示素材、ISSUE 模板
- `website/`：项目官网及部分素材，原生web三件套

## 开发约束

- 开始功能开发、修复或较大改动前，先阅读 `docs/` 下相关文档：
  - `docs/project.md`
  - `docs/architecture.md`
  - `docs/roadmap.md`
- 文档优先级：当前项目代码 > `docs/` 文档 > `ref/` 参考资料。
- 当前重点保障 `3x3` 智能魔方计时主流程，以及 `QiYi Smart Timer` 主链稳定性。
- 智能魔方协议继续沿用“独立协议类 + `BluetoothTools` 扫描/连接/分发”的结构。
- 蓝牙计时器和智能魔方业务链保持分离，不混用页面语义和协议状态。
- `GAN Robot` 是独立的可选执行设备，可与智能魔方并发连接；不接入 `BluetoothTools` 主连接链，不进入 `enterTime`、计时状态机或成绩保存。
- 对真机未验证的能力，文档和说明中必须明确标注为“待验证”或“未完成”。
- 优先做最小必要改动，避免为了顺手重构扩大提交面。
- 涉及资源文案改动时，需要同步更新 `values`、`values-zh`、`values-zh-rTW` 三种语言资源，避免中英繁文案或数组长度不一致。
- 编译验证策略：只有代码大量改动、跨模块改动、构建配置变更或发布前，才执行 `.\gradlew.bat assembleDebug`；小范围代码调整、纯文档更新或说明性修改默认不执行编译测试，并在回复中说明未执行。
