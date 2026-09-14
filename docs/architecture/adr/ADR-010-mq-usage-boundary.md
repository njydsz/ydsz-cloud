# ADR-010：MQ 使用边界（common-queue 抽象 vs RocketMQ 直连）

**状态：** 已接受
**日期：** 2026-09-14
**决策者：** ydsz-team

## 背景与问题陈述

ydsz-message 全链路直连 RocketMQ SDK（`RocketMQTemplate` / `RocketMQListener` / 事务监听 / DLQ，约 18 处 import、5 个文件），同时模块又 import common-queue 抽象 19 次——抽象与直连并存，无成文定界，复用守护脚本（E2 规则）持续命中。

## 决策驱动因素

* common-queue 的 `mq.rocket.RocketMQPublisher / RocketMQSubscriber` 抽象面向"普通收发"场景
* message 作为**消息中心**是 MQ 重度用户：事务消息、死信队列（DLQ）、批量消费、顺序消费——抽象层未覆盖这些高级语义
* 强行收敛到抽象需抽象层反向膨胀（为单一重度用户加 API），违反封装经济性

## 考虑的选项

* **全量收敛抽象：** message 高级 MQ 语义被迫塞入 common-queue，抽象层为单一用户膨胀
* **全量直连，删除 message 对 common-queue 的依赖：** 简单，但 message 内非高级场景（普通通知投递）失去统一收发监控点
* **按场景定界（本 ADR）：** 高级语义直连合法化，普通收发走抽象

## 决策结果

**按场景定界，message 的 RocketMQ 直连合法化，边界成文：**

| 场景 | 必须使用 |
|---|---|
| 事务消息（TransactionListener / 半消息） | RocketMQ SDK 直连（抽象层无此能力） |
| 死信队列消费（DLQ Consumer） | RocketMQ SDK 直连 |
| 批量 / 顺序消费（ConsumeMode 精细控制） | RocketMQ SDK 直连 |
| 普通投递与订阅（其他业务模块的一般场景） | common-queue 抽象（`RocketMQPublisher` / `RocketMQSubscriber`）或 common-event Outbox 门面 |

* 其他业务模块（非 message）出现 RocketMQ SDK import 仍视为 E2 违规，由 `scripts/check-common-reuse.py` 拦截
* message 模块的直连文件清单（已列入守护脚本豁免）：`RocketMQMessageProducer` / `MessageConsumer` / `BatchMessageConsumer` / `MessageDlqConsumer` / `MessageTransactionListener`
* 若未来 message 出现"普通收发"新增代码，优先评估走 common-queue 抽象

### 正面后果

* 重度用户不被抽象层绑架；抽象层保持精简
* 新增 MQ 使用有明确判断依据，评审可依

### 负面后果

* message 仍持有 SDK 版本升级与收发监控的独立成本（接受，属消息中心职责内）
* 抽象层与 SDK 能力差异需持续维护本表的场景清单

## 关联

* `ydsz-common-queue` `mq.rocket` 包
* 守护规则：`scripts/check-common-reuse.py` KNOWN_EXCEPTIONS
* ADR-009（公共能力收敛总纲）
