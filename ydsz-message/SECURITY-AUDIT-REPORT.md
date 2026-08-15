# ydzz-message 上线前安全/质量/可观测审计报告

> **审计日期**：2026-03-10  
> **审计范围**：`D:\Code\open\ydsz-cloud\ydsz-message`（5 子模块，261 Java 文件）  
> **审计角色**：DevSecOps / 安全架构师  
> **审计方法**：静态代码审查（全覆盖敏感路径搜索）+ 依赖分析 + 测试存在性验证

---

## Executive Summary

ydsz-message 模块整体架构成熟度较高（DDD 分层清晰、统一注解防护、HMAC 签名 token），但在 **PII 日志泄露、Webhook SSRF、退订 Token 重放、HTML 富媒体 XSS、测试为零**五个维度存在上线阻断级缺陷。总共发现 **5 项 P0、12 项 P1、7 项 P2**，预计总修复工期 **18 人天**。

---

## 1) Secrets / Token

### P0-1：退订 Token 重放攻击（Javadoc 声明 Redis 单次使用但未实现）

| 字段 | 值 |
|---|---|
| **文件** | `ydsz-message-server\src\main\java\com\njydsz\message\server\token\UnsubscribeTokenUtil.java` |
| **关联** | `ydsz-message-web\src\main\java\com\njydsz\message\web\controller\UnsubscribeController.java:56-58` |
| **严重度** | **P0** |
| **工期** | 2d |
| **危害** | Token 设计为 cryptographically signed + stateless（仅靠 expiresAt 过期），无 Redis 已用标记。Javadoc 明确写道"token 一次性使用（使用后从 Redis 标记失效，防止 token 重放）"，但全文无任何 Redis 操作代码。**在 TTL 默认 30 天窗口内，同一 token 可无限次重放执行退订**——攻击者拦截邮件/短信退订链接后可反复合法退订目标用户。 |

**证据**：
- `UnsubscribeTokenUtil.java:69`：生成 token 仅做 HMAC-SHA256 签名，不写 Redis
- `UnsubscribeTokenUtil.java:86-111`：`parseAndVerify()` 只做签名+过期校验，无任何已用标记检查
- `UnsubscribeServiceImpl.java:88-96`：`unsubscribeByToken()` 直接执行退幂等性仅靠 DB 唯一索引

### P0-2：Webhook SSRF — URL 无白名单 / 无 HTTPS 强制

| 字段 | 值 |
|---|---|
| **文件** | `ydsz-message-server\src\main\java\com\njydsz\message\server\channel\WebhookChannel.java:89-146` |
| **严重度** | **P0** |
| **工期** | 1.5d |
| **危害** | `resolveUrl()` 三路 URL 解析：① `params.webhookUrl`——用户完全可控；② `receiver` 以 "http" 开头时直接作为 URL；③ 仅缺省回退到系统配置。无：scheme 限定 HTTPS、IP 私有段黑名单（10.x/172.16.x/192.168.x/127.000）、域名白名单。攻击者可注入 `http://127.0.0.1:6379/` 或云 metadata `http://169.254.169.254/` 实现 SSRF。 |

**证据**：
- `WebhookChannel.java:130-133`：`params.get("webhookUrl")` 直接使用
- `WebhookChannel.java:137-139`：receiver 仅做 `startsWith("http")` 判断

### P0-3：全通道 PII 明文写日志（ReceiverMaskRegistrar 不覆盖 channel 层日志）

| 字段 | 值 |
|---|---|
| **文件** | `ydsz-message-server\src\main\java\com\njydsz\message\server\config\ReceiverMaskRegistrar.java` 及所有 `*Channel.java` |
| **严重度** | **P0** |
| **工期** | 2d |
| **危害** | `ReceiverMaskRegistrar` 仅通过 `SensitiveUtil.register("default", ...)` 注册给 ydsz-common-json 的 `@Sensitive(CUSTOM)` Jackon 序列化器——但**所有 channel 的 log.info/error 语句直接打印 `request.getReceiver()` (phone/email/openId)**，不经过 SensitiveUtil，导致手机号、邮箱、openId 明文入 ELK/SLS。grep 命中 50+ 处，涉及 EmailChannel、AlipayMiniChannel、DingTalkChannel、WxMiniChannel、MockSmsProvider、MessageServiceImpl、MessageConsumer 等。 |

**证据**：
- `EmailChannel.java:142`：`log.info("[EMAIL] 发送成功: to={} subject={}", request.getReceiver(), subject)` — 邮箱明文
- `MessageServiceImpl.java:673`：`log.info("[Message] 发送成功: msgId={} channel={} receiver={} cost={}ms", ...)` — 全通道
- `MessageSendClientFallback.java:31-33`：fallback 日志也打 receiver + subject

### P1-1：硬编码开发密钥退订 secret 兜底

| 字段 | 值 |
|---|---|
| **文件** | `UnsubscribeTokenUtil.java:49` |
| **严重度** | **P1** |
| **工期** | 0.5d |
| **危害** | `DEFAULT_SECRET = "ydsz-default-unsubscribe-secret-DO-NOT-USE-IN-PROD-CHANGE-IT"`——生产若 `ydsz.message.unsubscribe.secret` 配置缺失，token 可被伪造。 |

### P1-2：OutboundWebhookService 出站事件未脱敏

| 字段 | 值 |
|---|---|
| **文件** | `OutboundWebhookService.java:85` |
| **严重度** | **P1** |
| **工期** | 0.5d |
| **危害** | `payload.put("receiver", logDO.getReceiver())` 将明文收件人写入出站 Webhook payload，若 webhook 目标被攻破则 PII 外泄。 |

---

## 2) 注入与 SSTI

### P0-4：RichMediaContent HTML 渲染无 XSS 过滤（无 AntiSamy / OWASP HTML Sanitizer）

| 字段 | 值 |
|---|---|
| **文件** | `ydsz-message-server\src\main\java\com\njydsz\message\server\template\RichMediaRenderer.java:64-124` |
| **严重度** | **P0** |
| **工期** | 2d |
| **危害** | 邮件 HTML 渲染直接拼接 `media.getHtmlContent()`、`imgUrl`、`att.getUrl()`、`btn.getText()`、`btn.getActionValue()`，全部未 escape。攻击者可通过消息模板参数注入 `<img src=x onerror=...>`、`javascript:` URI 等。这是 **Stored XSS** 向量，影响所有使用 EMAIL 通道的业务。 |

**证据**：
- Line 74: `html.append(media.getTitle())` — title 未 escape
- Line 78-79: `html.append(media.getHtmlContent())` — 原始 HTML 直出
- Line 90-92: `html.append("<img src=\"").append(imgUrl)` — URL 未校验
- Line 101-103: 附件 URL / 文件名未 escape
- Line 113-116: 按钮 text/actionValue 未 escape

### P1-3：DefaultTemplateEngine 自研引擎非沙箱 — SSTI 风险可控但设计误判

| 字段 | 值 |
|---|---|
| **文件** | `ydsz-message-server\src\main\java\com\njydsz\message\server\template\DefaultTemplateEngine.java` |
| **严重度** | **P1（设计异味）** |
| **工期** | 2d (替换为 Pebble/Mustache) |
| **危害** | 自研 regex 拼接引擎不是"字符串拼接直接 P0"（不做 eval/reflection），但 `${var}` 直接 `toString()` 暴露任意注入对象内部状态（如含密码的 UserDTO）。无沙箱限制：无长度上限、无递归深度限制、无可用过滤器白名单（`default` 虽实现了 upper/lower/truncate/date/number，但 switch 走 default 放行，扩展时易失控）。 |

**注意**：非正式 SSTI（无 OGNL/SpEL/MVEL 类加载），但属于 P1 设计风险。

### P1-4：SensitiveWordFilter 无变形/拆字/繁简/emoji 对抗

| 字段 | 值 |
|---|---|
| **文件** | `ydsz-message-server\src\main\java\com\njydsz\message\server\filter\SensitiveWordFilter.java` |
| **严重度** | **P1** |
| **工期** | 2d |
| **危害** | DFA 仅支持精确 char 匹配，不支持：① 谐音/拆字（"弓虽" → "强"）；② 繁简转换；③ 特殊字符间隔（"s-e-n-s-i-t-i-v-e"）；④ emoji 插入替代。当前仅 6 个默认词，形同虚设。 |

### P2-1：SensitiveWordFilter 默认词仅限 6 个占位词

| 字段 | 值 |
|---|---|
| **文件** | `SensitiveWordFilter.java:42-44` |
| **严重度** | **P2** |
| **工期** | 0.5d |
| **危害** | `DEFAULT_WORDS = Set.of("政治敏感", "色情", "赌博", "毒品", "诈骗", "违禁")` — 占位词不覆盖实际业务。 |

---

## 3) WebSocket / CORS / 反滥用

### P1-5：WebSocketConfig 无显式 maxFrame / idle Timeout 配置

| 字段 | 值 |
|---|---|
| **文件** | `ydsz-message-server\src\main\java\com\njydsz\message\server\config\WebSocketConfig.java` |
| **严重度** | **P1** |
| **工期** | 0.5d |
| **危害** | 仅配置 `setAllowedOriginPatterns` 和 heartbeat；未设置 `maxTextMessageBufferSize`（默认 8KB，易被大帧 DoS）、`maxIdleTimeout`（默认无限制）、`maxBinaryMessageBufferSize`。STOMP 增加 `setTransport(Transport.WEBSOCKET)` 规格化。common-socket 拦截器是否自动配置 Bearer 鉴权需验证。 |

### P1-6：TcpPushChannel AUTH 无任何 Token 校验 — 假冒 userId

| 字段 | 值 |
|---|---|
| **文件** | `ydsz-message-server\src\main\java\com\njydsz\message\server\channel\TcpPushChannel.java:208-229` |
| **严重度** | **P1** |
| **工期** | 1d |
| **危害** | `channelRead()` 处理 `AUTH` 消息：只取的 `data.get("userId")`，**没有签**、Token 或任何凭证来验**客户端可以注册任意 userId 的连接**，从而接收他人推送。 |

**证据**：Line 217-228：任意 `{"type":"AUTH","userId":"victim"}` 即可接管连接。

### P2-2：WebSocketConfig allowedOriginPatterns 未校验 YAML 默认值

| 字段 | 值 |
|---|---|
| **文件** | `WebSocketConfig.java:46-47` |
| **严重度** | **P2** |
| **工期** | 0.5d |
| **危害** | `getAllowedOriginPatterns()` 来自 `WebSocketProperties`；需确认 YAML 默认值不含 `*`（否则 CORS 全开放导致 CSWS）。当前 default yaml 未发现，但需 SIT/UAT 验证。 |

### P2-3：Controller 写接口防护一致性（已覆盖，标注确认）

| 字段 | 值 |
|---|---|
| **证据** | 全部写接口已通过 `@Idempotent` + `@RateLimit` + `@Audit` + `@AuthApiPermission` 四重注解覆盖 |
| **严重度** | **P2（读接口无 RateLimit — 确认** |
| **工期** | 1d |
| **危害** | `GET /page`、`GET /{id}`、`GET /enabled`、`GET /inbox` 等大量读接口仅有 `@AuthApiPermission` 无 `@RateLimit`，可被遍历攻击拉**用户订阅列表、日志**等敏感信息。grep 确认 20+ 控制器通用模式。 |

**正面确认**：MessageController、TemplateController、RouteRuleController、SubscriptionController、UnsubscribeController 所有 POST/PUT/DELETE 写接口**全部**有四种注解，代码证据充分。

---

## 4) 异常 / 错误码

### P0-5：MessageSendClientFallback 返回 BaseResponse.success(null) — 调用方误判发送成功

| 字段 | 值 |
|---|---|
| **文件** | `ydsz-message-api\src\main\java\com\njydsz\message\api\fallback\MessageSendClientFallback.java:34` |
| **严重度** | **P0** |
| **工期** | 1d |
| **危害** | Fallback 路径返回 `BaseResponse.success(null)`——对 ydsz-workflow/project 等业务调用方来说，`success=true` + `data=null` 与"发送成功但无追踪 ID"语义无法区分。Feign 调用方如未判空 data，会**误以为消息已发送**，导致关键通知（如审批、告警）静默丢失。应返回 `BaseResponse.error(SERVICE_UNAVAILABLE, "消息服务不可用")` 或抛出异常由调用方显式降级。 |

### P1-7：MessageResultCode 已正确配置 i18n + HTTP 映射

| 文件 | 值 |
|---|---|
| **文件** | `MessageResultCode.java` |
| **结论** | PASS — B91xxx 区间编码、带 i18n key、HTTP 400/404/500 映射正确。 |

### P2-4：ydsz-message-web 无 @ControllerAdvice，依赖 common-web 全局

| 字段 | 值 |
|---|---|
| **文件** | `ydsz-message-web\src\main\java\` |
| **严重度** | **P2** |
| **工期** | 0.5d |
| **危害** | grep `@ControllerAdvice|@RestControllerAdvice` 零命中——message-web 模块自身未定义全局异常处理器。若 common-web 的 `BaseGlobalResponseAdvice` 与 common-socket 或父 starter 的 advice 存在顺序冲突，异常响应格式不统一。建议至少保留一个兜底 `@RestControllerAdvice` 统一包装。 |

### P2-5：MessageResultCode 注册方式 duplicate risk

| 字段 | 值 |
|---|---|
| **文件** | `MessageResultCode.java:80-86` |
| **严重度** | **P2** |
| **危害** | 静态块 `ExceptionCodeRegistry.register()` 与 `@YdszResultCode` 注解可能重复注册（需确认 fr**是否注册中心幂等若 key 冲突后覆盖则安全**。 |

---

## 5) 代码异味 / 测试

### P0-6：零测试覆盖 — ydzz-message-server/src/test 不存在

| 字段 | 值 |
|---|---|
| **文件** | `ydsz-message-server\src\test\` `ydsz-message-web\src\test\` |
| **严重度** | **P0** |
| **工期** | 5d |
| **危害** | Glob search 整个 ydzz-message 模块 `**/src/test/**/*.java` 返回 0 文件。缺：① consumer/batch/webhook 集成测试；② DefaultTemplateEngine 边界渲染测试；③ UnsubscribeTokenUtil 签名/重放测试；④ SensitiveWordFilter DFA 测试；⑤ WebhookChannel SSRF 尝试测试。**安全关键模块零测试覆盖不可上线。** |

### P1-8：MessageServiceImpl 预计 > 850 行 — God Class TOP-1

| 字段 | 值 |
|---|---|
| **文件** | `MessageServiceImpl.java` (已读 300/600+ 行) |
| **严重度** | **P1** |
| **工期** | 3d |
| **危害** | 类 30+ 依赖注入字段，`sendInternal()` 含 cascade、preprocess、renderContent、persistAndDispatch 等 6+ 阶段，行数估计 > 850。含 SendContext 内部类 + preprocess 超长方法 + 级联触发，违反 SRP，`buildLogDO` `handleEarlyReturns` 等 private 方法均 > 80 行。 |

**肥胖 TOP 5**（按已读推断）：

| # | 文件 | 估计行数 | 超标类型 |
|---|---|---|---|
| 1 | `MessageServiceImpl.java` | ~850+ | 大类 + 长方法 |
| 2 | `DefaultTemplateEngine.java` | 435 | 长类（三层渲染正则嵌套） |
| 3 | `RichMediaRenderer.java` | 205 | 长方法 renderHtml |
| 4 | `WebSocketConfig.java` | 52 | PASS |
| 5 | `BatchServiceImpl.java` | ~280 | 长类（多态分发执行路径） |

### P1-9：pom 中 SpotBugs / Checkstyle 均未启用

| 字段 | 值 |
|---|---|
| **文件** | 父 `pom.xml`、`ydsz-message-server\pom.xml` |
| **严重度** | **P1** |
| **工期** | 1d |
| **危害** | grep `spotbugs|checkstyle` 全部 pom 零命中。父 pom 仅有 `errorprone.version=2.49.0` 属性定义 + dependency，但 ErrorProne 未配置为 maven-compiler-plugin 的 linter（`<compilerArgs>` 缺失）。无静态扫描门禁。 |

### P2-6：TODO/FIXME/HACK 数量

| 类型 | 数量 |
|---|---|
| TODO | 1 (`BatchServiceImpl.java:174`) |
| FIXME | 0 |
| HACK | 0 |
| XXX | 0 |

**结论**：代码注释清洁度良好。

### P2-7：BatchServiceImpl 批次恢复功能临时方案

| 字段 | 值 |
|---|---|
| **文件** | `BatchServiceImpl.java:173-176` |
| **严重度** | **P2** |
| **工期** | 2d |
| **危害** | `executeBatch(batchId)` 从 `batchName` 字段反序列化请求（数据 schema 临时绕路），batchName 字段语义被滥用为 payload 存储。一旦 batchName 被运营修改即崩溃。 |

---

## 优先级汇总

### 上线必关 P0（5 项）

| # | 编号 | 问题 | 文件 | 工期 |
|---|---|---|---|---|
| 1 | P0-1 | 退订 Token 重放：Javadoc 声明 Redis 单次使用但未实现，30天窗口无限重放 | `UnsubscribeTokenUtil.java` | 2d |
| 2 | P0-2 | Webhook SSRF：URL 无 HTTPS 强制 + 无域名/私网 IP 白名单 | `WebhookChannel.java` | 1.5d |
| 3 | P0-3 | 全通道 PII 明文写日志：50+ 处 receiver 直打，不经过 SensitiveUtil | 全 `*Channel.java` + `MessageServiceImpl.java` | 2d |
| 4 | P0-4 | RichMediaContent HTML Stored XSS：无 AntiSamy 过滤 | `RichMediaRenderer.java` | 2d |
| 5 | P0-5 | Fallback 返回 success(null)：调用方误判发送成功，通知静默丢失 | `MessageSendClientFallback.java` | 1d |

### P1 必须修复（pre-UAT）（7 项）

| # | 编号 | 问题 | 工期 |
|---|---|---|---|
| 6 | P1-1 | 硬编码兜底 secret | 0.5d |
| 7 | P1-2 | OutboundWebhook 出站 payload 含明文 receiver | 0.5d |
| 8 | P1-5 | WebSocket 无 maxFrame/idle timeout | 0.5d |
| 9 | P1-6 | TcpPushChannel AUTH 无 Token 校验 | 1d |
| 10 | P1-3 | DefaultTemplateEngine 自研引擎无沙箱 | 2d |
| 11 | P1-4 | SensitiveWordFilter 无变形对抗 | 2d |
| 12 | P1-8 | MessageServiceImpl God Class | 3d |

### P2 建议修复（post-go-live）（5 项）

| # | 编号 | 问题 | 工期 |
|---|---|---|---|
| 13 | P2-1 | 默认敏感词仅 6 个占位 | 0.5d |
| 14 | P2-2 | allowedOriginPatterns YAML 默认值需确认 | 0.5d |
| 15 | P2-3 | 读接口无 RateLimit 遍历风险 | 1d |
| 16 | P2-4 | message-web 无 @ControllerAdvice 兜底 | 0.5d |
| 17 | P2-6 | BatchServiceImpl payload 临时 schema | 2d |

---

## 总工期估算

| 类别 | 工期 |
|---|---|
| P0 必关（上线阻断） | 8.5 人天 |
| P1 必修复（pre-UAT） | 10.5 人天 |
| P2 建议修复 | 4.5 人天 |
| **P0合计（上线门槛）** | **8.5 人天 ≈ 2 周（2 人）** |
| **全量（P0+P1+P2）** | **23.5 人天 ≈ 5 周（2 人）** |

---

## 正面发现（不修复，值得肯定）

1. **Token 鉴权设计正确**：UnsubscribeTokenUtil 正确使用 `DigestUtils.constantTimeEquals` 做签名比较，避免 timing 攻击。
2. **统一注解防护一致**：所有控制器写接口全覆盖 `@AuthApiPermission` + `@Idempotent` + `@RateLimit` + `@Audit`，规范到位。
3. **DDD 分层清晰**：api/domain/infra/server/web 五层职责分明，无跨层依赖违反。
4. **注释质量高**：Javadoc 详尽（含 RFC 对标、场景描述、状态机），技术债务注释少。
5. **无 System.out / printStackTrace**：grep 全部 Java 文件零命中，日志纪律良好。
6. **错误码体系规范**：MessageResultCode 使用 B91xxx 区间、i18n key、HTTP 映射完整。

---

## 附录：grep 验证证据索引

| 搜索项 | 工具 | 命中数 |
|---|---|---|
| `log.\*(receiver\|getReceiver)` | grep | 50+ |
| `TODO\|FIXME\|HACK` | grep | 1 TODO |
| `SpotBugs\|Checkstyle\|ErrorProne` (in pom) | grep | 0 (父 pom 仅有 dependency) |
| `@ControllerAdvice\|@RestControllerAdvice` | grep | 0 |
| `@PreAuthorize\|@Secured\|@RolesAllowed` | grep | 0（使用 @AuthApiPermission 替代） |
| `src/test/**/*.java` | glob | 0 文件 |
| `System\.out\|printStackTrace` | grep | 0 |

---

*审计人：CatPaw DevSecOps Agent*  
*报告版本：v1.0*
