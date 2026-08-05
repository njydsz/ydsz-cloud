# ADR-001: RemiJson 自研 JSON 引擎架构决策

> **状态**：已接受（Accepted）
> **日期**：2026-08-06
> **决策者**：Remi JSON 团队

## 背景

Remi Cloud 项目需要高性能 JSON 序列化/反序列化能力，对标 Jackson（业界标准）和 FastJSON2（国内高性能标杆）。

### 可选方案

| 方案 | 优势 | 劣势 |
|------|------|------|
| A. 直接使用 Jackson | 生态成熟、社区维护 | 包体积大（~1.5MB）、启动慢、与 FastJSON 行为差异需适配 |
| B. 直接使用 FastJSON2 | 极致性能、国内流行 | AutoType 历史漏洞、社区响应不确定 |
| C. 自研 RemiJson | 零外部依赖、完全控制、轻量级 | 维护成本高、生态弱 |

### 决策

**选择方案 C：自研 RemiJson JSON 引擎。**

### 理由

1. **零外部依赖**：Remi 框架定位为基础底座，避免传递依赖污染（如多个模块引入不同 Jackson 版本）
2. **统一规范**：公司级框架要求全仓库 JSON 行为一致（Long→String、日期格式、BigDecimal 精度），自研引擎天然规避"底层框架与业务引入库行为不一致"的问题
3. **性能可控**：ASM 字节码生成 + 零拷贝反序列化可按场景裁剪，无需为不用的功能（如 Jackson 的 Polymorphic Deserialization 的复杂安全模型）付出性能代价
4. **安全自主**：AutoType 安全模型可自行定义（如自动白名单扫描 `@JsonClass` 类），无需跟随 FastJSON 的黑名单更新节奏

### 架构设计原则

1. **不可变配置（Jackson 哲学）**：`JsonConfig` 字段全部 `final`，通过 `install()` 原子替换，避免运行期状态泄漏
2. **静态入口 + 实例化 Mapper（FastJSON2 便利 + Jackson 模式）**：`RemiJson` 提供开箱即用的静态方法，底层委托给 `JsonMapper` 实例
3. **ThreadLocal 池 + Config Snapshot 隔离**：同一 JVM 内多配置 Mapper 共存，每次序列化 apply 自己的配置并恢复快照
4. **性能分级**：ASM 字节码（热门路径）→ BeanReader（降级）→ ValueWriter（兜底）；字段缓存按 Class + 命名策略双层隔离

### 已知权衡

- **权衡 1**：ThreadLocal Snapshot 机制复杂度 vs 多配置并发安全性 → 选择安全性（已通过并发测试覆盖）
- **权衡 2**：完全兼容 Jackson 注解 vs 核心功能聚焦 → 80% 兼容（常用注解全覆盖，边缘特性如 `@JsonUnwrapped` 文档中标注使用建议）
- **权衡 3**：功能完整性（Schema/Path/Patch）vs 模块精简 → 标注 `@Experimental` 并文档化，使用率低的功能下沉为可选引用

### 参考

- Jackson `ObjectMapper` 不可变设计
- FastJSON2 静态入口 + ASM 字节码架构
- Gson 极简零配置模型（学习其 API 直观性）
