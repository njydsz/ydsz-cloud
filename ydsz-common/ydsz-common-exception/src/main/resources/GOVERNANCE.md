# 错误码规范与治理（ydsz-common-exception Governance）

> 本文件由 AI 辅助编码时自动参照（catpaw-always 级），所有 PR 中涉及错误码新增/修改必须合规。

---

## ADR-001：保留 SecurityExceptionCode 独立模块（26.09.19 决策）

### 背景

原始分析报告建议将 `SecurityExceptionCode` 合并回 `CoreExceptionCode`，使用 `category` 属性标注。
经 26.09.19 架构评审，决策：**保持 `SecurityExceptionCode` 独立**。

### 决策理由

1. **码段空间独立**：安全码段（A02xxx/A03xxx/C01xxx）与业务码段（A01xxx/A04xxx/A05xxx）
   物理隔离，合并后将导致 A 段码空间碎片化。
2. **权限内聚**：安全模块例外（Spring Security Filter 层 + Controller AOP 鉴权）统一捕获
   `SecurityExceptionCode` 类型而非 `CoreExceptionCode`，独立枚举便于监控告警规则配置。
3. **分类标注已解决痛点**：通过 `@YdszExceptionCode(category = ExceptionCategory.SECURITY)` +
   `getCategory()` 覆盖，已消除基于 key 前缀推断的脆弱性，合并收益不再显著。

### 结论

- `SecurityExceptionCode` 继续作为独立枚举类维护。
- 不在 `CoreExceptionCode` 中新增安全码段常量。
- 业务模块新增安全相关码段时，**就近放入 `SecurityExceptionCode`** 而非自建枚举。

---

## ADR-002：静态门面模式（CoreErrorCode / SecurityErrorCode）

### 规则

业务代码引用错误码时：

```java
// ✅ 正确：编译时安全
throw BusinessException.of(CoreErrorCode.PARAM_ERROR);
throw new BusinessException(SecurityErrorCode.UNAUTHORIZED, cause);

// ❌ 禁止：手写字面量
throw BusinessException.builder().code("A01052").key("param.error").build();
```

### 一致性校验

启动时 `ExceptionCodeScanner.afterSingletonsInstantiated()` 自动调用
`CoreErrorCode.validateConsistency()`，校验枚举数量与门面数量一致，不一致时阻止启动。

**新增 `CoreExceptionCode` 枚举常量后必须：在 `CoreErrorCode` 中添加对应字段。**

---

## 错误码准入规范（X2）

### 分级标准

| 等级 | 范围 | 审批 | 新增阈值 |
|------|------|------|----------|
| **P0** | 跨模块公共码（CoreExceptionCode） | 架构评审 + CHANGELOG | 单模块 ≤ 30 |
| **P1** | 业务模块码（domain 枚举） | 模块 Owner 审批 | 全局 ≤ 200 |
| **P2** | 租户/客户自定义码（Runtime SPI） | 运行时注册 | 全局 ≤ 500 |

### 强制规则

1. **枚举类型使用 `implements ExceptionCode`**：禁止裸常量类作为错误码。
2. **必须标注 `@YdszExceptionCode`**：缺少注解则启动时 FAIL 校验阻止注册。
3. **码段前缀规则**：
   - `A01xxx` 业务错误 / `A02xxx` 认证 / `A03xxx` 权限 / `A04xxx` 资源/限流 / `A05xxx` 批量
   - `B01xxx` 系统 / `B02xxx` 外部 / `C01xxx` 安全 / `D01xxx` 限流 / `EInfra` 基础设施
4. **禁止码段混用**：单一枚举类内的码常量必须共享同一段前缀。

### 命名约束

- **错误码格式**：大写字母 + 下划线（`PARAM_ERROR`、`USER_NOT_FOUND`）
- **i18n key 格式**：小写 + 点分隔（`param.error`、`user.not.found`）
- **禁止无意义命名**：如 `ERROR_1`、`FAIL_A`、`CODE_X`

### 启动校验清单

`ExceptionCodeScanner.afterSingletonsInstantiated()` 校验：

- [x] 枚举码全局唯一性（重复码 fail-fast）
- [x] i18n key 在各语言资源文件中可解析
- [x] 已声明的 i18n basename 资源文件存在
- [x] 全局码数未突破 P2 软上限（WARN）/ P1 硬上限（ERROR）
- [x] CoreErrorCode 静态门面与 CoreExceptionCode 枚举数量一致

---

## 分类规范（ExceptionCategory）

| 分类 | 适用场景 | HTTP 范围 | 监控 SLO |
|------|----------|-----------|----------|
| `BUSINESS` | 业务参数校验 / 状态错误 | 400/409/422 | 计入误报率 |
| `SYSTEM` | 空指针 / DB 异常 / 基础设施 | 500/503 | 计入可用性 |
| `SECURITY` | 认证 / 权限 / CSRF | 401/403 | 单独告警（可能攻击） |
| `RATE_LIMIT` | 限流 / 熔断 / 降级 | 429/503 | 区分正常限流与故障 |
| `EXTERNAL` | 三方 HTTP / 网关 / MQ | 502/504 | 独立 SLO 跟踪 |

---

## i18n 规范

- **资源文件命名**：`{module}-messages{_locale}.properties`（YDIZ-I18N-001）
- **编码**：UTF-8 无 BOM（YDIZ-CODE-001）
- **新增码必须同步**：至少在 `exception-messages.properties`（ROOT）中提供兜底文案
- **禁止硬编码用户可见文案**（YDIZ-I18N-002）

---

## 拆分说明（O4 架构决策，26.09.19）

`BaseExceptionHandler`（750+ 行）已拆分为：

```
BaseExceptionHandler           ← 编排 / 状态 / 事件发布 / i18n
    ↓ 内部委托
ExceptionResponseBuilder       ← ProblemDetail / YdszResponse / ExceptionInfo 构造
```

所有子类（Mvc / WebFlux / Validation）public/protected API 保持不变，零侵入迁移。

---

*规范版本: 26.09.19 | 维护者: ydsz-team | 自动生成: shared-rules.yaml + catpaw-always.md*
