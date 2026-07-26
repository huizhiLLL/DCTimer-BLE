# GAN 智能魔方重置协议

更新日期：2026-07-26

## 范围

GAN v2 / v3 / v4 支持“重置魔方”。这里的版本按 BLE service、characteristic 和数据格式识别，不对应具体产品名或固件版本。

重置同时更新 App 本地状态和实体魔方的逻辑状态。App 侧先清理本地转动追踪，协议侧向设备写入完成状态。代码入口是 [`GanCubeProtocol.onLocalCubeReset()`](../app/src/main/java/com/dctimer/util/GanCubeProtocol.java)。

## 行为边界

- 重置数据固定表示完成魔方，不把任意 facelet 字符串编码为 GAN reset packet。
- `cubeState` 只用于上层状态通知。
- App 的本地重置不删除实体设备已有的转动记录。GAN v4 在 i4、iC4 上的验证中，官方 CubeStation 仍能看到重置前的实体转动记录。

## 验证状态

- GAN v2 / v3 已完成真机验证。
- GAN v4 已在 i4、iC4 上完成真机验证。
- 其他型号和固件版本待验证。
