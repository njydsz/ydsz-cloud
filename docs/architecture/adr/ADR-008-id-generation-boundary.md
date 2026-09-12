# ADR-008：分布式 ID 生成场景边界（ydsz-common-util IdGenerator vs UUID）

**状态：** 已接受
**日期：** 2026-09-12
**决策者：** ydsz-team

## 背景与问题陈述

仓内主键/标识生成存在双轨：common-util `IdGenerator`（Snowflake，含 WorkerId 分配链与健康指示器）与 JDK `UUID.randomUUID` 并存（实测各约 22 / 32 个文件使用）。两者能力定位不同但边界从未成文，导致：

1. 部分数据库主键使用 UUID（索引膨胀 + 页分裂写入放大），部分使用雪花 ID
2. 雪花 ID 与 UUID 混用于同类实体，跨库关联与排序无一致性
3. `UUID.randomUUID` 也被用于纯随机值场景（nonce、CSRF），该场景合法但从未区分

## 决策驱动因素

* 数据库主键对有序性、长度、索引友好性有硬要求，UUID v4 不满足
* Snowflake 依赖 WorkerId 分配与时钟回拨处理，common-util 已提供完整方案（`IpHashWorkerIdAllocator` / `PodOrdinalWorkerIdAllocator` / `SnowflakeHealthIndicator`）
* UUID 在"全局随机、无需有序、无需入库索引"场景（nonce、CSRF Token、临时文件名）仍是正确选择
* 规范 §33.7：不以引用热度评判，以场景适配性裁决

## 考虑的选项

* **全仓统一雪花 ID：** 覆盖所有场景，但 nonce 等随机场景引入不必要的分配器依赖与可预测性
* **全仓统一 UUID：** 放弃主键有序性收益，不可取
* **按场景划界（本 ADR）：** 明确各自适用场景，边界成文

## 决策结果

**按场景划界，双轨保留但边界强制：**

| 场景 | 必须使用 |
|---|---|
| 数据库主键 / 业务流水号 / 需按时间排序的实体 ID | common-util `IdGenerator`（Snowflake） |
| 需要跨实例无协调唯一且入库的标识 | `IdGenerator`，禁止 UUID 主键 |
| 安全随机值（nonce、CSRF、临时令牌、一次性验证码） | `UUID.randomUUID` 或 `SecureRandom`/`RandomUtils` |
| 对外暴露的不可枚举资源标识（如分享提取码） | 高熵随机（`RandomUtils`/`SecureRandom`），非雪花 |

* 新代码违反划界由评审 + `../../../sqls/scripts/check-common-reuse.py` 后续规则拦截
* 存量混合主键不做追溯性重写（改主键代价远超收益），仅在涉及表重构时按本 ADR 执行

### 正面后果

* 主键有序、索引友好、跨实例可生成
* 随机场景继续使用无协调开销的 UUID/SecureRandom，不引入虚假依赖
* 边界成文，评审有据

### 负面后果

* 雪花 ID 依赖时钟与 WorkerId 分配，需保持 `SnowflakeHealthIndicator` 告警接入
* 双轨并存本身仍需开发者理解场景边界（本 ADR 为唯一依据）

## 关联

* `ydsz-common-util` `id` 包：`IdGenerator` / `SnowflakeIdGenerator` / `WorkerIdAllocatorChain`
* 规范章节：《云顶编码规范》§33.7
