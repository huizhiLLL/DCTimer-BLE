# 智能魔方 3阶 CFOP / Roux 专项训练状态

日期：`2026-07-04`

状态：已完成代码落地；`testDebugUnitTest` 和 `assembleDebug` 已通过，真机待验证。

## 当前范围

`3阶 CFOP` 与 `3阶 Roux` 是智能魔方链路的独立专项训练分组，不包含 `WCA` 子项，也不复用普通 `3阶` 或 `3阶子集` 的子项语义。

真实分组索引保持追加在末尾：

- `3阶 CFOP` 真实 group：`21`
- `3阶 Roux` 真实 group：`22`
- 真实 `scrambleIdx`：`group << 5 | sub`
- 一级展示顺序：由 `ScrambleGroupDisplay` 单独映射，当前显示为普通 `3阶`、`3阶 CFOP`、`3阶 Roux`，不改变真实索引、偏好、分组或成绩数据。
- `3阶 CFOP` 二级展示顺序：由 `ScrambleSubitemDisplay` 单独映射为 `F2L / OLL / PLL / 顶层 / CLL / ELL / COLL / EOCP / 2GLL / OLLCP / ZZLL / ZBLS / ZBLL`，不改变真实 sub 索引、偏好、分组或成绩数据；其他分组二级子项仍按资源数组顺序展示。

## 已支持专项

### 3阶 CFOP

下表按真实 sub 索引记录，非 UI 展示顺序。

| sub | 子项 | 打乱生成 | 完成判断 |
| --- | --- | --- | --- |
| `0` | `OLL` | `Tools.randomLastLayer()` | OLL 完成 |
| `1` | `PLL` | `Tools.randomPLL()` | 完整复原 |
| `2` | `顶层` | `Tools.randomLastLayer()` | 完整复原 |
| `3` | `F2L` | `Tools.randomCrossSolved()` | F2L 完成 |
| `4` | `ZBLL` | `Tools.randomZBLastLayer()` | 完整复原 |
| `5` | `ZZLL` | `Tools.randomZZLastLayer()` | 完整复原 |
| `6` | `2GLL` | 普通 `2GLL` 的 `randomState(...)` 约束 | 完整复原 |
| `7` | `ELL` | `Tools.randomEdgeOfLastLayer()` | 完整复原 |
| `8` | `ZBLS` | `Tools.randomZBLastSlot()`，参考 `cstimer` 的 `zbls / getLSLLScramble`，顶层棱和活动 LS 棱朝向可乱 | EOLL 完成 |
| `9` | `COLL` | 独立 COLL 语义 `randomState(...)` | CPLL 完成 |
| `10` | `OLLCP` | `Tools.randomLastLayer()`，过滤 OLL 已完成状态 | CPLL 完成 |
| `11` | `EOCP` | `Tools.randomLastLayer()`，过滤 OLL 已完成和 EOLL 已完成状态 | EOLL + 顶层角块相对顺序完成 |
| `12` | `CLL` | `Tools.randomLastLayer()`，过滤 OLL 已完成和 CLL 已完成状态 | F2L 保持，顶层角块完成，顶层棱块不作要求 |

### 3阶 Roux

| sub | 子项 | 打乱生成 | 完成判断 |
| --- | --- | --- | --- |
| `0` | `CMLL` | 参考 `cstimer` 的 `cmll` mask，生成 FB/SB 保持、CMLL 未完成、LSE 可乱的状态 | Roux CMLL 完成 |
| `1` | `LSE` | 参考 `cstimer` 的 `lse` mask，生成 CMLL 完成、LSE 未完成的状态 | 完整复原 |
| `2` | `L10P` | 复用 `CMLL` 的 FB/SB 保持、后十块可乱状态 | 完整复原 |

## 关键边界

- 专项训练统一使用独立训练朝向，默认 `黄顶绿前`；内部 `SmartCube` 仍保存物理状态和物理转动。
- 阶段完成判定由 `SmartCubeTraining` 配置，`SmartCube` 设备模型不硬编码具体训练模式。
- 阶段训练完成后只清理本次 solve tracking，保留当前物理 `cubeState`，不强制 `markSolved()`。
- `ZBLS` 生成只要求 Cross + 前三组 F2L 已完成，顶层棱和活动 LS 棱朝向不预先完成；完成判断使用 `EOLL_MASK`，表示 F2L 已完成且顶层棱朝向已好。
- `COLL` 使用 `CPLL_MASK`，表示 F2L 已完成、顶面完成且顶层角块关系已正确，允许剩余 EPLL。
- `OLLCP` 生成复用随机 OLL 起点，即 F2L 完成、最后一层朝向未固定，并过滤 OLL 已完成状态；完成判断复用 `CPLL_MASK`，表示最终只允许剩余 EPLL。
- `EOCP` 生成复用随机 OLL 起点，即 F2L 完成、最后一层朝向未固定，并过滤 OLL 已完成和 EOLL 已完成状态，减少与 COLL / OLLCP 起点重合；完成判断要求顶层棱朝向正常且四个顶层角块相对顺序正确，允许 U 层整体偏移、角朝向和顶层棱排列未完成，可作为 `2GLL` 起点。
- `CLL` 生成复用随机 OLL 起点，即 F2L 完成、最后一层朝向未固定，并过滤 OLL 已完成和 CLL 已完成状态；完成判断使用 `CLL_MASK`，要求 F2L 保持、顶层角块状态与 COLL 完成态一致，只放开顶层棱块朝向和排列。
- `CMLL` 使用 `ROUX_CMLL_MASK`，与 `cstimer` 的 `roux3Mask` 保持一致，允许剩余 LSE。
- `LSE` 与 `L10P` 使用完整复原作为完成态。
- 新增或调整专项文案时，需要同步 `values`、`values-zh`、`values-zh-rTW` 三套资源数组。

## 验证记录

- `.\gradlew.bat testDebugUnitTest`：已通过。
- `.\gradlew.bat assembleDebug`：已通过。

## 待真机验证

- MoYu32、QiYi / Tornado V4、GAN v2/v3/v4 在阶段完成停表上的一致性。
- 训练朝向下状态弹窗、计时页 3D 预览和实际物理转动方向一致性。
- 连续多条专项训练从非 solved 状态接续时，打乱进度、READY 和首转起表是否稳定。
