# ADR-001: 通知双渠道体系收敛策略

> **状态**：ACCEPTED  
> **决策日期**：2026-08-16  
> **决策者**：ydsz-team  
> **背景**：ydsz-common-notify 与 ydsz-message 各自维护了一套完整的通道实现体系，
> 导致能力重复、维护成本翻倍、新开发者认知负荷高。

---

## 1. 问题陈述

项目中存在两套平行的消息通知通道体系：

| 维度 | ydsz-common-notify | ydsz-message |
|------|-------------------|--------------|
| 渠道枚举 | `NotifyChannel`(6 值) | `MessageChannelEnum`(12 值) |
| 策略接口 | `NotifyChannelStrategy` | `MessageChannel` |
| 内置实现 | Email/SMS/WeCom/DingTalk/Feishu | Email/Sms/DingTalk+DingTalkWork/WeCom+WeComApp/Feishu/InApp |
| 路由器 | `SendChain`（去重→熔断→限流→执行→降级） | `ChannelRouter`（Resilience4j 熔断） |
| 横切能力 | 聚合、事务安全、异步、死信、审计 | 回执拉取、消息日志 |

两套体系通过 `NotifyChannelBridgeConfiguration` 在运行期桥接（message 的 `MessageChannel` 适配为 `NotifyChannelStrategy`），但这只是表面统一——实际代码维护量、配置项、熔断/策略均存在两套。

---

## 2. 决策

**采用方案 B：以 `ydsz-common-notify` 为唯一业务入口，`ydsz-message` 退化为通道 Provider。**

### 2.1 分层定位

```
┌─────────────────────────────────────────────────────┐
│  业务模块 (workflow / cronjob / gateway / literule)  │
│       ↓ 仅使用                                        │
│  NotifyHelper  ← 统一便捷 API（sendInApp/sendEmail/  │
│                  sendDingTalk/sendFeishu/sendWeCom/  │
│                  sendSystemAlert/send）              │
│       ↓ 委托                                          │
│  NotifyService / UnifiedAlertEvent                   │
│       ↓ 横切处理                                      │
│  SendChain（去重→熔断→限流→执行→指标→审计→降级）      │
│       ↓ 策略分发                                      │
│  NotifyChannelStrategy                               │
│       ↓ 两种 Provider                                 │
│  ┌─────────────────┐  ┌─────────────────────────┐   │
│  │ common-notify   │  │ message (via Bridge)     │   │
│  │ 内置 Sender     │  │ MessageChannel → Adapter │   │
│  │ (默认兜底)       │  │ (增强实现)               │   │
│  └─────────────────┘  └─────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

### 2.2 职责边界

| 模块 | 职责 | 不再做 |
|------|------|--------|
| `ydsz-common-notify` | 统一 API（NotifyHelper）、事件总线（UnifiedAlertEvent）、横切能力（SendChain）、默认 Provider | 直接暴露底层 `NotifyChannelStrategy` 给业务模块 |
| `ydsz-message` | 通道 Provider（通过 `NotifyChannelBridgeConfiguration` 桥接到 common-notify）、消息日志、回执 | 自建 `ChannelRouter`/`NotificationClient` Feign 对业务模块暴露 |
| 业务模块 | 仅通过 `NotifyHelper` 发送通知 | 直接注入 `NotifyService`、自建通知 Helper、使用 `NotificationClient` Feign |

### 2.3 选择理由

1. **横切能力集中**：`SendChain` 的去重、聚合、事务安全、限流熔断只在 common-notify 一处实现，message 模块的 `ChannelRouter` 仅保留与消息日志/回执相关的分发逻辑
2. **SPI 模式自然**：`NotifyChannelStrategy` 是天然 SPI 接口，message 模块的 `MessageChannel` 通过 `NotifyChannelStrategyAdapter` 适配后自动注入，无需业务模块感知
3. **默认/增强分离**：common-notify 提供默认 Sender（开箱即用），message 提供增强 Provider（高可用/大吞吐场景），通过 Spring Bean 自动覆盖

---

## 3. 实施约束

### 3.1 业务模块强制规则（写入编码规范）

| 禁止行为 | 正确做法 |
|---------|---------|
| 直接注入 `NotifyService` 自行构造 `NotifyRequest` | 注入 `NotifyHelper`，调用便捷方法 |
| 使用 `NotificationClient` Feign 直调 message 模块 | 通过 `NotifyHelper` 发送，由 common-notify 层做横切处理 |
| 自建 `XxxNotificationService` 封装发送逻辑 | 直接委托 `NotifyHelper` |
| 自行构造 `UnifiedAlertEvent` 寻找不存在的 `UnifiedAlertDispatcher` | 直接调用 `NotifyHelper.send*()` 方法 |

### 3.2 渐进式收敛路径

| 阶段 | 行动 | 状态 |
|------|------|------|
| Phase 1 | 清理幽灵引用（UnifiedAlertDispatcher） | ✅ 已完成 |
| Phase 2 | 补全 `NotifyHelper` 便捷方法 | 🔄 进行中 |
| Phase 3 | 各模块迁移到 `NotifyHelper` | 待启动 |
| Phase 4 | 移除业务模块对 `NotificationClient` Feign 的直接依赖 | 待启动 |

---

## 4. 影响分析

### 4.1 性能

无负面影响。`NotifyHelper` 方法内部委托 `NotifyService.send()`，与直接调用 `NotifyService` 性能一致（仅多一层 try-catch 包装用于日志）。

### 4.2 兼容性

- 现有的 `NotificationClient` Feign 接口（message 模块对外）仍保留，供第三方集成场景使用
- 业务模块的 `NotifyService` 注入仍可用，但不推荐（Helper 是便捷超集）

### 4.3 风险

- `ChannelRouter` 中的 Resilience4j 熔断能力未与 common-notify 的 `SendChain` 熔断合并。
  **应对**：message 的 `MessageChannel` 实现内部可保留熔断，但与 `SendChain` 的熔断形成双层保护；后续 Phase 4 评估是否合并。

---

## 5. 参考

- `NotifyHelper.java` — 统一便捷 API
- `NotifyChannelBridgeConfiguration.java` — 通道桥接自动配置
- `UnifiedAlertEvent.java` — 统一告警事件载体
- `NotifyServiceImpl.java` — SendChain 核心实现
