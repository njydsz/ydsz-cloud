# ydsz-pmis 项目记忆

## 项目架构
- Spring Boot 4.1.0 + JDK 21
- DDD 分层架构：L1(core/json) → L2(util) → L3(domain/exception) → L4(jdbc/redis/cache) → L5(auth/feign/audit) → L6(base/web/app)
- ydrsz-common-core 是 L1 基础设施层，最小依赖原则

## 代码规范
- 错误码：A/B/C 三段式（阿里规范），每个枚举显式声明 HTTP 状态码，禁止前缀推断
- 请求上下文：TransmittableThreadLocal（alibaba TTL）
- 响应体：BaseResponse<T> 统一封装，支持 i18n + RFC 7807 ProblemDetail
- 工具类：final class + private constructor + UnsupportedOperationException

## 优化记录 (2026-08-04)
- TraceId：UUID.randomUUID() → ThreadLocalRandom + HexFormat
- 新增 W3C TraceContext 支持（traceparent header）
- 新增 @Sensitive 脱敏注解体系
- 新增 DDD 契约接口（Command/Query/DTO/VO/Event）
- 新增 DomainEvent + DomainEventPublisher
- PageConstants：以 CoreProperties 为单一数据源
- BaseResponse：新增 ok()/fail() 精简 API
- ADR 决策记录已创建在 docs/adr/
