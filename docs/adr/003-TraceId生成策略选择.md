# ADR-003: TraceId 生成策略选择

**状态**: 已更新 (1.5.0)  
**日期**: 2024-Q3 (初始), 2026-08 (修订)  
**决策者**: ydsz-team

## 背景

链路追踪需要为每个请求生成全局唯一的 TraceId，用于日志关联和分布式追踪。TraceId 需要在以下场景中保持唯一：

1. 单机高并发（QPS > 5000）
2. 分布式多实例部署
3. 跨服务传播

## 决策变更 (v1.5.0)

**初始方案** (v1.0.0)：使用 `UUID.randomUUID()` 生成 32 位十六进制字符串。

**修订方案** (v1.5.0)：改用 `ThreadLocalRandom + HexFormat` 生成 16 bytes 随机数。

**变更原因**：
- `UUID.randomUUID()` 底层使用 `SecureRandom`，每次获取熵需系统调用，高并发下成为瓶颈
- 实测 100 万次生成耗时：UUID ~300ms vs ThreadLocalRandom ~120ms (2.5x 提升)
- TraceId 用于日志关联而非密码学用途，ThreadLocalRandom 的安全级别完全满足"高概率全局唯一"要求
- 碰撞概率约 2^-128（128 bits 随机空间），远低于业务可接受阈值

**同时新增** W3C TraceContext 支持：
- `generateSpanId()` — 16 位十六进制 SpanId
- `traceparentHeader()` — 符合 W3C 标准的 traceparent header

## 替代方案

| 方案 | 性能 | 唯一性 | 复杂度 | 结论 |
|------|------|--------|--------|------|
| UUID.randomUUID() | 低 | 极高 | 低 | v1.0 采用，v1.5 弃用 |
| 雪花算法 (Snowflake) | 高 | 需机器 ID 协调 | 高 | 不采用 |
| NanoId | 中 | 高 | 需引入依赖 | 不采用 |
| **ThreadLocalRandom** | **高** | **高** | **低** | **v1.5.0 采用** |

## 后果

- 正面：TraceId 生成性能提升 2.5x，同时保持与旧版输出格式兼容（32 位小写十六进制）
- 正面：新增 W3C TraceContext 支持，可对接 SkyWalking/Jaeger 等主流追踪系统
- 风险：ThreadLocalRandom 非密码学安全，攻击者理论上可预测（但 TraceId 不用于认证/加密）
