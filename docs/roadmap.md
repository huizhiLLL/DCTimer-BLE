# DCTimer-Android 路线图与决策备忘

日期：`2026-07-15`

## 文档职责

本文只维护后续计划、暂不推进事项和已收口技术决策；当前状态放在 `docs/project.md`，架构边界放在 `docs/architecture.md`。

## 近期

- 保持长期文档收口在 `docs/project.md`、`docs/architecture.md`、`docs/roadmap.md`。
- `GAN Robot` 已以独立协同执行设备接入；后续在具备设备条件时补充维护者真机复测，并持续确认协议和动作编排细节。
- 智能魔方 3D 预览已落地统一四元数姿态入口；MoYu32 陀螺仪视角跟随已完成真机验证，GAN Gen2 / Gen4 已完成姿态协议接入和单元测试，分别的真机验收待完成；QiYi / Tornado V4 姿态协议接入和单元测试已完成，`XMD-TornadoV4-i` 与 `QY-QYSC` 分型号真机验收待完成；GAN Gen3 继续按当前连接是否收到有效姿态运行时识别。
- `3阶 CFOP` 与 `3阶 Roux` 智能魔方专项训练已完成代码落地和本地验证，后续重点做 MoYu32、QiYi / Tornado V4、GAN v2/v3/v4 真机验证。

## 中期

- 如果继续扩展蓝牙计时器品牌，按“独立 timer 协议类 + `SmartTimerProtocol` 分发”的方式接入。
- 如果继续扩展智能魔方品牌，按“独立智能魔方协议类 + `SmartCubeProtocol` 分发”的方式接入。
- 如果后续出现多个执行设备或明确的多设备连接需求，再评估统一连接管理；当前不为小众的 `GAN Robot` 改造 `BluetoothTools` 主连接链。

## 暂不推进

- 暂不承诺支持所有蓝牙魔方或所有蓝牙计时器。
- 暂不引入 Rajawali；当前 3D 预览先沿用 `GLSurfaceView + OpenGL ES 2.0` 自定义渲染。

## 已收口决策

### Android 底座

- 已升级到 `AndroidX + AGP 8.9.2 + Gradle 8.11.1 + JDK 17`。
- 已将 `compileSdk / targetSdk` 提升到 `35`。
- 文件导入导出、打乱导入导出、背景图选择已迁移到 `SAF / Uri`。
- 旧 `FileSelectorDialog`、APK 自更新入口、安装权限与 `FileProvider` 已退出主线。
- BLE 权限链按 Android 新权限模型和定位兼容模式收口。

### Release 打包

- release 签名入口标准化，支持 `key.properties` 和 `-P` 参数注入。
- 未提供签名信息时直接报错，不生成不可分发包。
- release APK 输出名固定为 `DCTimer-BLE-v版本号.apk`。
- `rel.bat` 打包成功后同步 APK 到 `website/assets`，供网站直链下载使用。

### 智能魔方

- 智能魔方协议当前覆盖 `MoYu32`、`QiYi / Tornado V4`、`GAN v2 / v3 / v4`。
- 新协议统一收口到 `SmartCubeProtocol`。
- BLE 扫描弹窗以扫描结果直列和连接阶段自动识别为准。
- 智能魔方状态展示以自定义 3D 渲染控件为主。
- 打乱流程以“打乱进度提示 + 偏离纠错 + READY 等待首转”为准，不回退到连接后直接起表。
- `3阶 CFOP` 与 `3阶 Roux` 专项训练作为独立真实分组追加在末尾，打乱选择 UI 单独映射到普通 `3阶` 后展示；`3阶 CFOP` 当前显示为 `F2L / OLL / PLL / 顶层 / CLL / ELL / COLL / EOCP / 2GLL / OLLCP / ZZLL / ZBLS / ZBLL`，`3阶 Roux` 当前包含 `CMLL / LSE / L10P`，不包含 WCA 子项。
- 专项训练使用独立训练朝向，默认 `黄顶绿前`，不复用解法重建朝向。
- 专项训练完成判定按 `cstimer` 的 mask 思路收口：`OLL` 检查 OLL，`F2L` 检查 F2L，`ZBLS` 检查 EOLL，`COLL` 与 `OLLCP` 检查 CPLL，`EOCP` 检查 EOLL + 顶层角块相对顺序并允许 U 层整体偏移，`CLL` 检查 F2L 保持 + 顶层角块完成且只放开顶层棱块状态，`CMLL` 检查 Roux CMLL，其他 CFOP 专项、`LSE` 与 `L10P` 检查完整复原。
- 阶段训练完成后保留当前物理魔方状态，仅重置本次解法追踪；下一条专项训练从当前物理状态接续生成打乱。
- `GAN v4` MOVE 通知按 `72-bit` chunk 循环解析；`M / E / S` 快速双层转动不再依赖 `MOVE_HISTORY` 才补齐同包中的第二个转动。
- GAN Gen2 使用 `0x1` 陀螺仪事件和 bit `4` 四元数，Gen4 使用 `0xEC` 陀螺仪事件和 bit `16` 四元数；协议层按符号幅值解码后转换到渲染坐标系并归一化，再经 `MainActivity` 进入统一姿态入口。
- QiYi / Tornado V4 姿态使用解密后的独立 `0xCC 0x10` 固定 16 字节帧；前 14 字节计算 CRC16-Modbus，偏移 `6 / 8 / 10 / 12` 使用大端有符号 `int16` 并按 `1000` 缩放，以 `(ax, ay, az, aw)` 轴顺序归一化，再经 `MainActivity` 进入统一姿态入口。
- 陀螺仪能力按当前连接首次收到的合法姿态运行时识别，不按 GAN 通用设备类型固定白名单；连接切换、断开和回退普通计时器时清理最新姿态、校准姿态和能力状态。
- QiYi 同样按当前连接首次收到的合法姿态运行时识别，不按 `QY-QYSC` 设备类型或名称前缀固定声明支持；合法姿态优先于普通 `0xFE` 帧分流，校验失败的 `0xCC 0x10` 帧直接丢弃，不进入未知协议保护，也不影响 hello 和状态同步。
- `GAN v3 / v4` 的 `MOVE_HISTORY` 仅作为丢包兜底，尾部缺失时间戳按可用真实时间戳和本地触发时间估算。
- GAN v2 / v3 / v4 已支持实体重置；v2 / v3 已完成真机验证，v4 已由贡献者在 i4 / iC4 完成验证，其他型号和固件待验证。
- `QiYi / QYSC` 的状态帧可能提前携带 future history 步；future history 步只用于实时状态和 3D 更新，不参与打乱偏离累计。

### 魔方机器人

- `GAN Robot` 是可选协同执行设备，使用独立 `GanRobotBleClient` 管理扫描、连接和 GATT，不接入 `BluetoothTools` 的单设备连接流程。
- Robot 可与智能魔方并发连接，智能魔方只提供状态与目标；Robot 的物理转动不进入 `enterTime`、计时状态机或成绩保存。
- 当前支持基础打乱、复原和实体按钮单击；协议中的其它特征值含义、按钮双击/长按和与官方 App 的动作速度差异仍待继续确认。

### 解法重建

- `333-smart-cf4op` 分段按 `Cross / F2L 1-4 / OLL / PLL` 展示。
- 分段判定按 `cstimer` 的 `cf4op` 思路收口为 6 轴向进度计算，并先按阶段分桶原始转动再逐段重建。
- `333-smart-roux` 分段按 `FB / SB / CMLL / L6E` 展示，按 `cstimer` 的 `roux` 思路使用 24 轴向进度计算。
- 解法类型由智能设置中的“使用解法”手动选择，默认 `CFOP`；暂不在成绩保存时自动识别 CFOP / Roux。
- `AUF` 默认并入 `PLL`。
- `100ms` 内对向层组合识别为 `E / M / S`。
- TPS 使用重建后的步数统计，`U2` 与 `E / M / S` 按一步计，`x / y / z` 不计入主 TPS。
- 打乱完成后、首转起表前的视角变化按“初始化姿态 -> 起手姿态”的最短转体路径插入 `Cross` 开头。

### 蓝牙计时器

- `enterTime` 已从旧的单一“蓝牙设备”语义拆成“智能魔方 / 蓝牙计时器”两条入口。
- `SmartTimerProtocol` 是蓝牙计时器协议分发入口。
- `QiYi Smart Timer` 按独立 `QiyiSmartTimerProtocol` 接入。
- 蓝牙计时器模式不启用应用内 `WCA` 观察和观察提示。
- 智能魔方和蓝牙计时器断联后，应回退普通计时器。
