# GAN Robot 协议备注

本文只保留现场观察到的协议事实和未确认项。项目定位、连接边界和业务语义以 `project.md`、`architecture.md`、`roadmap.md` 为准。

## 已确认行为

- 设备名通常以 `GANBOT-` 开头；扫描记录中含 `fff0` service 时可进一步确认，含 GAN 智能魔方 service 时应排除。
- Robot 使用独立 `GanRobotBleClient` 扫描、连接和管理 GATT，不经过 `BluetoothTools` 主连接链。
- `fff3` 用于动作写入，动作以 `4-bit nibble` 编码；一个包最多 18 bytes，即 36 个动作。
- `fff2` 的首字节当前作为剩余动作数使用；`fff4` 的 `02 FF` 通知当前作为实体按钮单击事件使用。
- 设备没有原生 `U` 面指令，应用侧会把 `U / U2 / U'` 展开为可执行动作序列。

## 相关代码

- `GanRobotProtocol`：设备识别、UUID、通知与动作编码。
- `GanRobotBleClient`：扫描、连接、GATT 读写和通知分发。
- `GanRobotExecutor`：公式执行、状态到状态编排与 Robot 空闲等待。
- `GanRobotController`：实体按钮动作；`GanRobotSessionState`：与主页面的协同状态桥。

## 未确认项

- `fff1` 和 `fff5` 至 `fff8` 的完整含义尚未确认。
- 当前只稳定接入实体按钮单击；双击和长按由固件绑定。
- 与官方 App 相比，动作编排和速度仍有差异，需要后续真机继续确认。
- 本实现已有贡献者真机演示与测试说明，维护者暂无设备复测。
