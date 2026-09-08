# ADR-001：DDD 分层架构选择

**状态：** 接受
**日期：** 2025-01-15
**决策者：** ydsz-team

## 背景与问题陈述

ydsz-cloud 作为中台型业务系统，需要支撑多个业务线（agent、workflow、message、system、cronjob 等）的长期演进。模块之间的边界划分不清、代码互相依赖是历史遗留的核心痛点。需要选择一个能够明确领域边界、支持模块物理隔离、且团队可接受的架构分层模型。

## 决策驱动因素

* 团队对 DDD（领域驱动设计）接受度高，具备战术建模基础
* 中台型系统需要清晰的领域边界和防腐层隔离
* 模块需要物理隔离（独立 Maven Module），降低编译耦合
* 六边形架构适配层与 Spring Boot 生态天然契合
* 编码规范 YDIZ-ARC-001 要求分层依赖单向传递

## 考虑的选项

* **传统三层 MVC（Controller-Service-Dao）：** 快速上手，但领域逻辑易泄漏到 Service 层，难以应对复杂业务
* **Clean Architecture（用例驱动）：** 层次分明但引入用例层概念较重，团队学习曲线陡峭
* **六边形无分层（Ports & Adapters）：** 概念灵活但物理边界不直观，调试与代码审查成本高
* **DDD 六边形 + api/domain/infra/server 四层模型：** 在六边形基础上增加四层物理映射，编译期强制分层

## 决策结果

选择 **DDD 六边形 + api/domain/infra/server 四层模型**，因为它在保持六边形架构灵活性的同时，通过四层物理边界使模块依赖关系在编译期可验证，适合中台系统多模块协作。

### 正面后果

* 模块间依赖方向严格单向：server → app → domain ← infra
* 领域模型（Entity、VO、DomainService）独立于框架，可复用性提升
* api 层独立发布，接口演进与实现解耦
* 与 Maven 多模块天然对齐，各层可独立版本管理
* 编码规范 YDIZ-ARC-001 可通过 Checkstyle 插件自动化校验

### 负面后果

* 每个业务实体需要拆分为 Entity/DTO/VO 三类对象，增加样板代码量
* 四层结构对简单 CRUD 场景存在过度设计的质疑需要团队内部对齐
* 跨模块调用需通过 api 层暴露，接口设计成本上升
* DDD 战术模式（Repository、Factory、DomainEvent）需要持续培训

## 各选项详细对比

### 传统三层 MVC

* **优点：** 上手快、社区资料丰富、多数团队成员熟悉
* **缺点：** Service 层易变成事务脚本容器，领域逻辑散落在 Mapper/Service 中，模块边界模糊
* **评价：** 适合小型 CRUD 系统，不满足中台多业务线隔离需求

### Clean Architecture

* **优点：** 层次最清晰，Use Case 层独立封装业务编排
* **缺点：** 概念过重，团队学习成本高；Spring Boot 场景下 Use Case 与 Service 边界易混淆
* **评价：** 理论优美但落地成本超出团队当前能力范围

### 六边形无分层（Ports & Adapters）

* **优点：** Port/Adapter 概念灵活，依赖倒置原则执行彻底
* **缺点：** 物理边界仅靠命名约定约束，缺乏编译期依赖校验，代码审查成本高
* **评价：** 适合单模块应用，多模块场景下不如四层结构直观

### DDD 六边形 + 四层模型

* **优点：** 编译期强制分层，Maven Module 天然映射；ddd-4-layer 规则可自动化扫描；中台多业务线场景实践成熟
* **缺点：** 新成员上手需要 DDD 培训；样板代码量增加
* **评价：** 契合 ydsz-cloud 多模块协作需求，综合成本最优

## 合规性对照

- [云顶编码规范 YDIZ-ARC-001] — 模块依赖单向传递、domain 层禁止引入第三方框架直接依赖
- [云顶编码规范 YDIZ-ARC-002] — 领域事件定义与 DomainEvent 基类使用
- [云顶编码规范 YDIZ-ARC-003] — Repository 接口定义于 domain、实现于 infra

## 参考

* [ADR-004](./ADR-004-seata-mode.md) — Seata 分布式事务模式选择（与 domain 层事务边界相关）
* [云顶编码规范 - 架构分层章节]()
* Vaughn Vaughan, *Implementing Domain-Driven Design*
