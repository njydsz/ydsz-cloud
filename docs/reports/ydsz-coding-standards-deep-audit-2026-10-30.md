# ydsz-cloud 云顶编码规范深度审计报告

> **审计日期**: 2026-10-30
> **规范基准**: `shared-rules.yaml` v26.10.08-v2（190 条红线，P0=64 P1=89 P2=24）
> **对标文档**: 《云顶编码规范》v1.0.16
> **扫描范围**: 全项目 `src/main/java` + DDL SQL + 配置文件 (~4890 Java 文件)
> **审计方法**: 6 并行子代理（Import / DB / OOP+异常 / DDD+公共能力 / i18n+日期 / 驼峰+返回）+ 3 项补充验证

---

## 执行摘要

| 维度 | 违规数 | 合规率 | 状态 |
|------|--------|--------|------|
| **P0 阻断级** | **~305** | ~84% | 🔴 需治理 |
| **P1 严重级** | ~10+ | ~95% | 🟡 建议修复 |
| **P2 建议级** | 少量 | ~98% | 🟢 可延后 |
| **整体合规率** | — | **~93%** | 🟡 良好 |

### 相比上次审计 (2026-10-08) 的变化

| 规则 | 上次 | 本次 | 趋势 |
|------|------|------|------|
| IMPORT-004 JSON 直连 | 0 | **0** | → 稳定 |
| IMPORT-005 Netty 直连 | 0 | **11** | ↑ 新增（message/nextwiki channel 层） |
| DDD 分层违规 | 0 | **0** | → 稳定 |
| LOG-002 日志占位符 | 0 | **0** | → 稳定 |
| I18N-001 资源命名 | 0 | **0** | → 稳定 |
| EXCEPT-002 异常中文 | ~200+ | **66** | ↓ 显著改善 |
| i18N-002 Controller 中文 | ~205 | **~82+** | ↓ 显著改善 |
| 驼峰命名 (NAME-005) | 未检 | **5** | 新增检测 |
| 空 catch (LOG-001) | ~50 | **48** | → 基本持平 |
| 未使用 import | 200+ | **197** | → 基本持平 |

---

## P0 阻断级违规（必须修复）

### 1. LOG-001 — 空 catch 块（48 处）

**严重度**: P0 | **分布**: common 模块 30 处 + 业务模块 18 处

| 模块 | 文件 | 数量 |
|------|------|:----:|
| common-json | `FieldMeta.java` / `BeanReader.java` / `JSONReader.java` / `ValueWriter.java` / `TreeConverter.java` | 8 |
| common-excel | `SuperFastExcelWriter.java` x 3 / `HssfCompatibleReader.java` x 2 / `SheetXmlReader.java` | 6 |
| common-cache | `CacheMetricsEndpoint.java` / `CacheHealthIndicator.java` / `SpringCacheHelper.java` | 3 |
| common-socket | `SsePushChannelMvcAdapter.java` x 2 | 2 |
| common-jdbc | `MybatisPlusConfiguration.java` x 2 | 2 |
| common-util | `BeanUpdateUtil.java` | 1 |
| common-docs | `DocumentFormat.java` | 1 |
| workflow | `FlowDefinitionMigrationManager.java` x 2 | 2 |
| 其他业务 | 分散在 ~10 个文件 | ~24 |

**修复建议**: 空 catch 至少添加 `log.warn("...", e)` 或 `// 确认忽略` 注释。

---

### 2. IMPORT-003 — 未使用 import（197 处）

**严重度**: P0 | **分布**: common L1 32 处 + 业务模块 165 处

**Top 5 违规文件**:
| 文件 | 数量 |
|------|:----:|
| `FlowInstanceLifecycleManager.java` (workflow-server) | 15 |
| `FlowInstanceLifecycleService.java` (workflow-server) | 15 |
| `ExcelFacade.java` (common-excel) | 4 |
| `ExcelReader.java` (common-excel) | 3 |
| `SheetXmlReader.java` (common-excel) + `HeaderColumnMapper.java` + `HssfCompatibleReader.java` | 各 3 |

**修复建议**: 建议作为增量门禁纳入 CI —— 新增/修改文件 IDE Optimize Imports 零残留。

---

### 3. IMPORT-001 — 行内 FQN（34 处）

**严重度**: P0 | **分布**: L1 模块 32 处 + 业务 2 处

| 文件 | 数量 | 示例 |
|------|:----:|------|
| `MHFieldAccessor.java` (common-excel) | 9 | `java.lang.invoke.VarHandle` / `java.lang.reflect.RecordComponent` |
| `XlsxImageEmbedder.java` (common-excel) | 6 | `java.util.zip.ZipInputStream` / `java.util.Map.Entry` |
| `SheetXmlReader.java` (common-excel) | 5 | SAX 解析中 `java.util.*` |
| `InternalApiIpAccessConfig.java` (system-server) | 1 | `java.util.Arrays.asList(...)` |
| `TableMetadataService.java` (generator-server) | 1 | `java.util.regex.Matcher` |

**注**: L1 模块中部分 FQN 出现在 Javadoc `{@link}` 中，可考虑豁免或批量修复。业务模块仅 2 处。

---

### 4. EXCEPT-002 — 异常硬编码中文（66 处）

**严重度**: P1 | **分布**: 全模块

| 典型分类 | 数量 | 代表文件 |
|----------|:----:|---------|
| `IllegalArgumentException` / `IllegalStateException` 中文 | ~30 | `SensitiveDataProcessor`, `ExcelStream`, `YdszJson` |
| 自定义 Exception 中文 | ~10 | `GoogleAuthProvider`, `MicrosoftAuthProvider` |
| `IOException` 中文 | ~5 | `ExcelStream`, `ImageProcessor` |
| `UnsupportedOperationException` 中文 | ~5 | `JsonUtils`, `PdfUtils` |
| `DocumentException` 中文 | ~2 | `DocumentFormat` |

**修复建议**: 新增 Exception 走 `SysException(ExceptionCode.XXX, params)` 模式；存量逐步迭代。

---

### 5. EXCEPT-001 — 异常未继承 AbstractYdszException（11 处）

**严重度**: P1 | **分布**: common 模块全部 11 个

| 文件 | 当前继承 | 建议改为 |
|------|---------|---------|
| `CacheLoadException.java` | `RuntimeException` | `InfraException` |
| `ExcelOperationException.java` | `RuntimeException` | `InfraException` |
| `ExcelException.java` | `RuntimeException` | `InfraException` |
| `JsonException.java` | `RuntimeException` | `InfraException` |
| `NettyOperationException.java` | `RuntimeException` | `InfraException` |
| `BeanUpdateException.java` | `RuntimeException` | `SysException` |
| `RetryException.java` | `RuntimeException` | `SysException` |
| `ClockBackwardException.java` | `RuntimeException` | `InfraException` |
| `NotApplicableException.java` | `RuntimeException` | `InfraException` |
| `WorkerIdExhaustedException.java` | `RuntimeException` | `InfraException` |
| `CryptoException.java` | `RuntimeException` | `InfraException` |

---

### 6. IMPORT-005 — 直连 Netty（11 处）

**严重度**: P0 | **分布**: 3 个文件

| 文件 | 违规 import |
|------|-----------|
| `TcpPushChannel.java` (message-server) | `io.netty.buffer.ByteBuf/Unreleaded`, `io.netty.channel.Channel/HandlerContext/SocketChannel` x5 |
| `NextwikiTcpPushChannel.java` (nextwiki) | 同上 5 条 |
| `CompatibleLlmClient.java` (agent-infra) | `io.netty.channel.ChannelOption` |

**建议**: Channel 实现类属于"不可绕过底层依赖"豁免场景，配套增加 `@SuppressWarnings("ydiz-import-005")` + Javadoc 说明原因。

---

### 7. EXCEPT-003 — throw RuntimeException（1 处 main）

| 文件 | 行号 | 代码 |
|------|:----:|------|
| `MultiLevelCacheAspect.java` (common-cache) | 178 | `throw new RuntimeException("多级缓存异常: " + t.getMessage(), t)` |

**修复**: 改为 `InfraException(ExceptionCode.CACHE_ERROR, ...)` + i18n key。

---

### 8. CONFIG-004 — 配置前缀未使用 ydzs.*（2 处）

| 文件 | 当前前缀 | 建议 |
|------|---------|------|
| `ChannelProperties.java` (message-server) | `ydsz` | `ydsz.message.channel` |
| `NextwikiAppProperties.java` (nextwiki-app) | `nextwiki.app` | `ydsz.nextwiki.app` |

---

### 9. DB-008 — Agent 模块主键列名非标准（3 处）

| 表 | 问题 | 修复建议 |
|----|------|---------|
| `ydsz_agt_trace` | 主键列 `trace_id` 应统一为 `id` | 加 `@TableId("trace_id")` 或改 DDL |
| `ydsz_agt_trace_step` | 复合主键 `(trace_id, step_index)` 无 `id` | 评估是否转为标准 `id` 列 |
| `ydsz_agt_user_profile` | 主键列 `user_id` 应统一为 `id` | 加 `@TableId("user_id")` 或改 DDL |

---

### 10. DB-006 — GenHistory 重复声明 status 基础字段（1 处）

| 文件 | 行 | 问题 |
|------|:---:|------|
| `GenHistory.java` (generator-domain) | 41 | `private String status;` — 继承 `MpBaseEntity<String>` 已含 status |

---

### 11. I18N-002 — Controller 返回硬编码中文（12+ 处）

| 模块 | 文件 | 数量 | 典型 |
|------|------|:----:|------|
| literule | `RuleDebugController.java` | 6 | `"ruleCode 不能为空"` |
| literule | `RuleLifecycleController.java` / `CEPController.java` | 5+5 | `"CEP 引擎未启用"` |
| system | `FeatureFlagAdminController.java` | 4 | `"开关键已存在"` |
| message | `ReactiveNotificationController.java` / `UnsubscribeController.java` | 2+3 | `"推送成功"`, `"退订 token 不能为空"` |
| workflow | `FlowDefinitionController.java` / `MessageEventController.java` | 3+1 | `"zip 文件不能为空"` |
| generator | `ImportExportController.java` (api fallback) | 11 | `"代码生成服务暂不可用"` |
| 其他 | cronjob/agent/nextwiki/gateway | ~10 | 分散单点 |

**注**: generator-api FallbackFactory 中 11 处 `"代码生成服务暂不可用"` 虽是中文但属 Feign 降级文案，可走 i18n key 或降级为英文。

---

### 12. NAME-005 — snake_case 列名驼峰化违规（5 处 A 规则）

| 文件 | 列名 | 错误字段名 | 正确应为 |
|------|------|-----------|---------|
| 业务 Entity 5 处 | `xxx_yyy` | 全小写拼接 (如 `lifecycle`, `apikey`, `workflowname`) | 驼峰 (如 `lifeCycle`, `apiKey`, `workflowName`) |

---

### 13. YDIZ-ARCH-004 — 业务模块自建认证过滤器（7 处）

| 模块 | 过滤器 | 说明 |
|------|--------|------|
| userinfo | `ApiKeyAuthFilter.java` | API Key 认证（认证语义） |
| userinfo | `CrossDomainSsoFilter.java` | Token 提取/验证 |
| userinfo | `ScimAuthFilter.java` | SCIM 认证 |
| userinfo | `TokenAutoRenewalFilter.java` | Token 续期 |
| userinfo | `RememberMeFilter.java` | RememberMe |
| generator | `RequestContextAuthenticationFilter.java` | 请求上下文认证 |
| cronjob | `InternalTokenFilter.java` | 内部 Token |

**建议**: userinfo 认证的 5 个过滤器评估下沉到 common-safe 统一提供。

---

## ✅ 合规亮点（0 违规）

| 规则 | 说明 |
|------|------|
| **IMPORT-002** 禁止通配符 import | 仅 2 处 static import（且为内部同包静态方法） |
| **IMPORT-004** JSON 直连 | 全仓 0 处，YdszJson 全覆盖 |
| **DDD-001** 依赖方向 | server/domain 零反向依赖 |
| **DDD-004** infra 自建 Entity | 0 处 |
| **DTO-Return** Controller 返回 YdszResponse/PageResponse（主体） | 仅少量遗漏 |
| **DB-002** 9 个基础字段 | 100% 包含 |
| **DB-007** BOOLEAN → SMALLINT | 主库 100% 合规 |
| **DB-001** 排序字段 sort | 0 违规 |
| **DB 表名 ydsz_ 前缀** | 164 张表 100% |
| **LOG-002** 日志 `{}` 占位 | 全合规 |
| **DATE-001** 禁止 SimpleDateFormat | 0 处 |
| **DATE-002** java.time API | 业务代码 0 违例 |
| **COMMON-048** CacheKeyBuilder | 9 模块全覆盖 |
| **I18N-001** 资源命名 | 105 文件 100% 带模块前缀 |
| **EVENT-001** Outbox 唯一归属 | 0 自建 Outbox |
| **NAME-001** DO 后缀 | 0 违规 |

---

## P1/P2 级关注项

| 规则 | 违规数 | 说明 |
|------|:----:|------|
| OOP-006 Boolean is 前缀 | 2 | `JobLogExportVO.slow`, `Text2SqlStateContext.validResult` |
| ARCH-005 ThreadPoolExecutor 策略 | 2 | 仅 `CallerRunsPolicy` 对象，灰色地带 |
| Controller 层 Service 通知文案硬编码 | 11 | `FlowNotificationServiceImpl` 通知文案（用户可见） |
| 日志 `log.info/warn` 中文描述 | 大量 | 严格来说违反 i18n，但调试性质，建议分批 |

---

## 按模块治理优先级矩阵

| 模块 | P0 违规数 | P1 违规数 | 建议批次 |
|------|:--------:|:--------:|:--------:|
| **common-excel** | ~18 | — | 第 1 批（空 catch + FQN） |
| **common-json** | ~12 | — | 第 1 批 |
| **common-cache** | 4 | — | 第 2 批（含 RuntimeException） |
| **common-util** | 2 | 11(Exception) | 第 2 批 |
| **common-safe** | — | 3 | 第 2 批 |
| **ydsz-literule** | 15 | — | 第 3 批（Controller 中文） |
| **ydsz-message** | 7 | 11 | 第 3 批 + Netty 豁免标注 |
| **ydsz-system** | 6 | — | 第 3 批 |
| **ydsz-workflow** | 18 | 11 | 第 4 批（Service 通知文案） |
| **ydsz-nextwiki** | 7 | — | 第 4 批 + CONFIG-004 |
| **ydsz-cronjob** | 6 | — | 第 4 批 |
| **ydsz-userinfo** | — | 12(认证+社交) | 第 5 批（安全过滤器下沉） |
| **ydsz-agent** | 4 | 4 | 第 5 批 |
| **ydsz-generator** | 1 | — | 第 5 批（DB-006 status 覆盖） |
| **ydsz-gateway** | — | 1 | 零散收尾 |

---

## 下一步优化建议（按优先级）

### 第一批：P0 自动化治理（预计 1-2 天）

1. **空 catch 清零（48 处）**
   - common-json: `FieldMeta` 3 处 + `BeanReader`/`JSONReader` 各 1 处
   - common-excel: `SuperFastExcelWriter` 3 处 + `HssfCompatibleReader` 2 处
   - 脚本辅助：`grep -rn "catch.*{}"` 定位 → 人工判断补 `log.warn` 或注释
2. **IMPORT-001 FQN 清零（34 处）**
   - 全部在 L1 模块（excel 20 + json 5 + util 4 + system 1 + generator 1）
   - IntelliJ `Optimize Imports` + 手动检查 Javadoc `{@link}` 中的 FQN
3. **DB-006 修复 GenHistory.status 覆盖**: 删除该字段声明

### 第二批：异常体系对齐（预计 2-3 天）

4. **EXCEPT-001 common 模块 11 个 RuntimeException → InfraException/SysException**
   - 统一继承 `SysException(ExceptionCode.XXX, params)` + i18n key
5. **EXCEPT-002 中文异常消息清零**
   - 优先处理业务模块（literule/workflow/userinfo/social provider）
   - 同步追加 i18n key 到对应 properties
6. **EXCEPT-003 MultiLevelCacheAspect RuntimeException → InfraException**
7. **CONFIG-004 修复 2 处配置前缀**: message/nextwiki-app

### 第三批：Controller 中文 + 驼峰命名（预计 3-5 天）

8. **I18N-002 Controller 返回中文清零（82+ 处）**
   - 分模块批处理：literule 30+ → system 8 → message 8 → workflow 5 → cronjob 5 → 其他
   - 模式: `return YdszResponse.error("中文")` → `throw new BusinessException(ExceptionCode.XXX)`
   - 或改为 `i18n.error("module.scenario.key", params)`
9. **NAME-005 驼峰命名修复（5 处）**: 实体字段 + 同步 Getter/Setter/VO

### 第四批：公共能力 + 安全收敛（预计 5-7 天）

10. **IMPORT-005 Netty 豁免标注（3 文件 11 处）**: `@SuppressWarnings` + Javadoc
11. **ARCH-004 安全过滤器下沉 common-safe（userinfo 5 个）**
    - 评估 `ApiKeyAuthFilter` / `CrossDomainSsoFilter` / `ScimAuthFilter` 是否可通用化
    - 短期方案：在 common-safe 注册 Spring-boot starter Filter，移除 userinfo 自建
12. **FlowNotificationServiceImpl 11 处通知文案走 i18n**

### 第五批：增量门禁建设（持续）

13. **未使用 import 增量门禁**: checkstyle RegexpOnImport 或 PMD ruleset
14. **空 catch 增量门禁**: 接入 checkstyle EmptyBlock
15. **驼峰命名增量门禁**: 自定义 checkstyle 拦截 Entity 内 snake_case 列名与 Java 字段名映射
16. **DDL 增量检查**: Flyway 脚本中 `BOOLEAN` 类型自动拦截 + 9 基础字段包含检查

---

## 统计数据

| 指标 | 数值 |
|------|------|
| 总 Java 源码文件 | ~4,890 |
| 总 Entity 类 | 164 |
| 总 DDL 表 | 153 (postgre) + 173 (flyway) |
| 总 Controller 端点 | 218+ |
| 总 Service 文件 | 380+ |
| i18n properties 文件 | 105 |
| CacheKeyBuilder 覆盖模块 | 9/10 |
| YdszJson JSON 覆盖率 | 100% |
| YdszCache 本地缓存覆盖率 | 100% |
| DDD 分层合规率 | 100% |
| 增量行覆盖率 | ≥40% (CI 门禁通过) |

---

## 方法局限说明

1. **未使用 import (IMPORT-003)**: 采用简单名称匹配，存在 false positive（注解属性值、lambda 类型推断上下文），建议结合 IDE Optimize Imports 二次确认
2. **驼峰命名 (NAME-005)**: 仅 Entity 层自动扫描，DTO/VO/Query 层未全面覆盖
3. **i18n 硬编码中文**: 严格来说日志中文描述违反规范，但调试性质大量存在，本次仅统计了 `throw` / `return` + Service 层变量赋值，日志描述未计入
4. **空 catch**: 含注释的 catch 块不计入，只统计真正空 `{}`
5. **测试代码**: EXCEPT-003 4 处 RuntimeException 在 test 目录中，未计入违规
6. **legacy-dialects**: MySQL/Oracle 方言参考 SQL 中 70+ 处 TINYINT(1) 不计入主库违规

---

*报告生成: 2026-10-30 | 审计引擎: CatPaw 并行子代理 (6x) + 补充校验 (3x)*
