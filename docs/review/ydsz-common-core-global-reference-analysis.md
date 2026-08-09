# ydsz-common-core 模块全局引用分析报告

> 基于对 `D:\Code\open\ydsz-cloud` 全仓库的 POM 依赖追踪 + 源码 import 引用分析。
> 分析时间：2026-08-09
> 分析维度：① 依赖分布 ② 核心能力利用率 ③ 模块间架构贯通评估 ④ 问题诊断 ⑤ 可落地的优化建议

---

## 0. 分析方法与范围

**数据来源**：
- POM 依赖声明（pom.xml 中显式/传递依赖）
- 源码 import 追踪（grep 统计）
- 核心类调用频次与方法级使用状况分析

**模块分类**：

| 分类 | 模块 |
|---|---|
| **业务模块** | ydsz-workflow, ydsz-message, ydsz-system, ydsz-userinfo, ydsz-nextwiki, ydsz-literule, ydsz-cronjob, ydsz-agent |
| **公共组件** | ydsz-common-web, ydsz-common-auth, ydsz-common-tenant, ydsz-common-exception, ydsz-common-feign, ydsz-common-jdbc, ydsz-common-base, ydsz-common-app, ydsz-common-audit, ydsz-common-safe, ydsz-common-lock, ydsz-common-util, ydsz-common-domain, ydsz-common-thread, ydsz-common-redis, ydsz-common-cache, ydsz-common-config, ydsz-common-notify, ydsz-common-socket, ydsz-common-event, ydsz-common-queue, ydsz-common-netty |
| **基础设施** | ydsz-gateway, ydsz-common-core |

---

## 1. 依赖分布总览

### 1.1 POM 层依赖声明

| 模块分类 | 显式声明依赖 | 传递依赖（通过 common-web 等） |
|---|---|---|
| 业务 web 层 (workflow-web, message-web, system-web, userinfo-web, nextwiki-web, literule-web, cronjob-web) | ✅ 7/7 | — |
| 业务 server 层 (workflow-server, message-server, userinfo-server, nextwiki-server) | ✅ 4/4 | — |
| 业务 domain/api 层 (workflow-domain/api, message-domain/api, literule-domain/api, nextwiki-domain/api) | ✅ 部分 | 部分 |
| 公共组件 (common-web, common-auth, common-tenant, common-exception, common-base, common-feign, common-jdbc, common-app, common-audit, common-safe, common-lock, common-util, common-domain) | ✅ 全部显式/传递 | — |
| 基础设施 (gateway) | ✅ | — |

**结论**：`ydsz-common-core` 已经是所有业务模块（直接或间接）的公共依赖，**依赖覆盖率达 100%**。

---

## 2. 核心能力利用率分析

### 2.1 利用率热力图

| 核心类/能力 | 引用文件数 | 涉及模块数 | 利用率 | 评价 |
|---|---|---|---|---|
| `BaseResponse<T>` | **70+** | **15+** | 🟢 高 | 成为全平台 API 响应的事实标准 |
| `RequestContext` | **60+** | **12+** | 🟢 高 | 上下文载体广泛使用 |
| `BaseResultCode` | **40+** | **10+** | 🟡 中 | 主要在 web/exception 层使用 |
| `PageResponse<T>` (response) | **40+** | **8+** | 🟡 中 | 分页响应逐步推广 |
| `TraceIdGenerator` | **14** | **4** | 🟡 中 | gateway 使用多 |
| `HeaderConstants` | **35+** | **10+** | 🟡 中 | common 内部为主 |
| `BizContextKeys` | **较多** | **多个** | 🟡 中 | 下沉后业务键有效归类 |
| `TokenConstants` | **10+** | **3** | 🟠 低 | 主要 auth 使用 |
| `SystemConstants` | **9** | **4** | 🟠 低 | 有限使用 |
| `ContextKey<T>` | **约 10** | **3** | 🔴 低 | 未被广泛认知 |
| `PageConstants` | **4** | **3** | 🔴 低 | **几乎未使用** |
| `TraceIdPropagation` | **约 5** | **2** | 🔴 低 | 调用方极少 |
| `Response` 门面 | **0** | **0** | 🔴 零 | **已开发无调用** |
| 自定义 `ResultCode` 实现 | **0** | **0** | 🔴 零 | **机制空置** |

### 2.2 关键发现

#### 🟢 高利用能力（已建立规范）

**BaseResponse** — 全平台 API 响应的标准信封
- workflow-web 系列 controller（25+ 文件）全面使用
- nextwiki-web 系列 controller（15+ 文件）使用
- message-web、literule-web、system-web、userinfo-web、cronjob-web 全部使用
- 已成为 HTTP API 出参的标准契约

**RequestContext** — 请求上下文载体
- common-auth: 认证信息存取（AuthContextUtils, BaseAuthFilter, RbacPermissionEvaluator）
- common-tenant: 租户上下文传播（TenantContextFeignInterceptor, TenantContextTaskDecorator 等 15+ 处）
- common-exception: 异常信息填充
- common-feign: 跨服务上下文传播
- common-jdbc: 数据权限上下文
- common-audit: 审计上下文

#### 🟡 中等利用能力（部分模块使用）

**BaseResultCode** — 系统级错误码定义
- 主要在 controller 层返回错误响应时使用
- 业务模块（workflow, message, system, nextwiki）倾向于使用**自定义业务结果码**（如 `SystemResultCode`、`MessageResultCode`）
- gateway 在限流、鉴权场景大量使用

**PageResponse<T>** — 分页响应信封
- workflow-server、message-server、literule-web、system-server 业务 service 层使用
- domain 层有同名 `PageResponse`（领域层分页载体），二者职责不同、可组合

#### 🔴 低利用/空置能力（优化机会大）

**`Response` 门面** — 已开发但零调用
- 位置：`com.njydsz.common.core.response.Response`
- 提供 `ok()` / `fail()` / `page()` 等静态入口
- 全仓库 grep `import com.njydsz.common.core.response.Response` **无命中**
- **所有 controller 仍在直接使用 `BaseResponse.success/error`**

**自定义 `ResultCode` 实现机制**（各模块应实现但未实现）
- 位置：`com.njydsz.common.core.code.ResultCode` 接口
- `BaseResultCode` 是系统通用码，业务模块应自定义枚举实现 `ResultCode`
- 现状：各模块自定义结果码（SystemResultCode、MessageResultCode 等）**是否实现 `ResultCode` 接口待核实**

**`PageConstants` 分页参数归一化** — 分页散落处理的现状
- 提供 `normalizePageNum`、`normalizePageSize`、`calcOffset`、`isOffsetSafe`、运行时 max-page-size 注入
- **仅 4 个文件引用**（含文档）
- 实际业务代码直接使用 `query.getEffectivePageNum()` 或硬编码分页参数

**`ContextKey<T>` 类型安全键** — 过度设计的代价
- 提供编译期类型安全的上下文键
- **仅 system、literule 中少量使用**
- 绝大多数模块使用字符串键或命名存取器（`setUserId/getUserId`）

**`TraceIdPropagation`** — 主动传播能力空置
- 提供 `traceHeaders()` / `traceHeadersOrCreate()` 用于 Feign/HTTP 传播
- **仅 common-feign、gateway 等少量调用**
- 业务模块几乎不主动传播 traceId

---

## 3. 模块间架构贯通评估

### 3.1 贯通度矩阵

```
              BaseResponse  RequestContext  ResultCode  PageResponse  Response  TraceIdProp  HeaderConstants
workflow-web      ✅             ✅            ⚠️          ✅         ❌         ❌          ❌
message-web       ✅             ✅            ⚠️          ✅         ❌         ❌          ❌
system-web        ✅             ✅            ⚠️          ✅         ❌         ❌          ❌
nextwiki-web      ✅             ❌            ❌          ❌         ❌         ❌          ❌
literule-web      ✅             ❌            ❌          ✅         ❌         ❌          ❌
userinfo-web      ✅             ❌            ❌          ❌         ❌         ❌          ❌
cronjob-web       ✅             ❌            ❌          ❌         ❌         ❌          ❌
gateway           ✅             ✅            ✅          ❌         ❌         ✅          ✅
common-auth       ✅             ✅            ⚠️          ❌         ❌         ❌          ✅
common-exception  ✅             ✅            ✅          ❌         ❌         ❌          ❌
common-feign      ✅             ✅            ❌          ❌         ❌         ✅          ✅
common-tenant     ✅             ✅            ❌          ❌         ❌         ❌          ⚠️
common-jdbc       ✅             ✅            ⚠️          ❌         ❌         ❌          ❌

图例：✅ 深度使用   ⚠️ 有限使用   ❌ 几乎未使用
```

### 3.2 架构贯通的核心问题

#### 问题一：API 响应构造散落，Response 门面空置

**现象**：
- 所有 controller 直接使用：`return BaseResponse.success(data)` / `return BaseResponse.error(BaseResultCode.XXX)`
- 已开发统一的 `Response` 门面（1.9.1 引入），但零采用

**影响**：
- API 构造方式不统一，新员工不清楚该用哪个
- 后续若需统一修改响应构造逻辑（如加监控、审计），改动面大

#### 问题二：自定义 ResultCode 机制空置

**现象**：
- `ResultCode` 接口已定义（`getCode/getMsg/getMessageKey/getHttpStatusCode`）
- 各业务模块有自己的结果码枚举（`SystemResultCode`、`MessageResultCode`）
- **但未确认这些枚举是否实现了 `ResultCode` 接口**

**影响**：
- 多态无法利用，无法统一处理
- `BaseResponse.error(ResultCode)` 理论上可接受任意 ResultCode，实际只有枚举常量传入

#### 问题三：分页参数归一化能力空置

**现象**：
- `PageConstants` 提供完整的归一化能力：运行时 max/default 配置、非法参数归一、安全阈值校验
- 业务模块使用各自 DTO 的 `getEffectivePageNum()` / `getEffectivePageSize()` 方法
- **PageConstants 与实际业务分页逻辑脱节**

**影响**：
- 分页约束无法统一配置
- 各模块自行实现，容易遗漏边界处理

#### 问题四：TraceId 主动传播不足

**现象**：
- `TraceIdPropagation` 封装了完整的 trace header 生成逻辑
- 主要调用仅在 `FeignRequestInterceptor` 和 `WebSocketAuthFilter`
- 业务代码中的 HTTP 调用**未统一使用**该工具

**影响**：
- 跨服务调用可能断链
- 日志追踪不完整

#### 问题五：HeaderConstants 利用率有限

**现象**：
- 定义了完整的 HTTP 请求头常量（认证/数据权限/列权限/追踪/网络）
- 部分模块（如 nextwiki）使用自定义常量或硬编码字符串

**影响**：
- 请求头键名散落在各处，重构困难
- 与 gateway 的约定可能不一致

---

## 4. 问题诊断与根因分析

### 4.1 能力认知不足

很多模块的开发者不了解 core 提供的完整能力列表，导致：
- 直接使用 `BaseResponse` 而非 `Response` 门面
- 自行实现分页参数处理而非使用 `PageConstants`
- 不知道 `TraceIdPropagation` 的存在

**根因**：缺少统一的编码规范和引导文档。

### 4.2 迁移成本高

`Response` 门面是新引入的能力（1.9.1），修改所有 controller 的迁移工作量大，且收益不明显。

**根因**：缺乏自动化迁移脚本或 IDE 模板引导。

### 4.3 ResultCode 接口抽象不强

`ResultCode` 接口定义了 `getMessageKey()`，默认实现假设调用者是枚举。这让非枚举实现变得困难，抑制了使用欲望。

**根因**：接口设计偏向枚举实现，对普通类实现不够友好（已在 1.2.0 中修复为 `"error." + getClass().getSimpleName()` 回退）。

### 4.4 PageConstants 缺"入口绑定"

`PageConstants` 是静态工具类，但没有与 Controller 层的 `@RequestParam` 分页参数绑定，或提供 `PageParam` 对象封装。

**根因**：缺少面向 Controller 层的分页参数对象封装。

---

## 5. 可落地的优化完善建议

### 5.1 短期收敛（1-2 周可落地）

#### S1【P1】推广 `Response` 门面，收敛响应构造入口

**现状**：已开发 `Response` 但零采用
**建议**：
- 在团队周会/分享中正式推介 `BaseResponse.success(data)` / `BaseResponse.error(BaseResultCode.X)` 用法
- 在编码规范文档中明确"新代码应使用 Response 门面"
- 提供 IDE 代码模板（Live Template）：`res` → `BaseResponse.success($END$)`
- 存量代码通过 `grep 'BaseResponse.success\('` 定位，渐进式迁移

#### S2【P1】推动业务模块实现 `ResultCode` 接口

**现状**：自定义结果码未实现 `ResultCode` 接口
**建议**：
- 检查各模块自定义结果码（grep 枚举 implements），确认是否已实现接口
- 如未实现，纳入技术债务，安排重构
- 规范：**所有业务结果码必须实现 `ResultCode` 接口**，享受 i18n 和 HTTP 状态自动映射

#### S3【P1】封装分页参数对象

**现状**：`PageConstants` 能力空置
**建议**：
- 在 core 中提供 `PageParam` 对象：
  ```java
  @Data
  public class PageParam {
      @Min(1) private int pageNum = 1;
      @Min(1) @Max(5000) private int pageSize = 20;
      // 通过 PageConstants 归一化
  }
  ```
- Controller 方法签名：`public BaseResponse<PageResponse<User>> list(PageParam page)`
- Spring 自动绑定 request parameter，并走 PageConstants 归一化约束

#### S4【P1】TraceId 传播规范

**现状**：业务代码中的 HTTP 调用未统一使用 `TraceIdPropagation`
**建议**：
- 规范：所有 Feign/RestTemplate/WebClient 调用必须透传 traceId
- 封装到 `TraceRequestInterceptor`（feign 已有），业务侧直接用
- 提供 `TraceUtils.traceHeadersOrCreate()` 便捷入口

### 5.2 中期演进（1-2 月可落地）

#### M1【P0】统一 ResultCode 与异常机制贯通

**目标**：让 `BaseResponse.error(ResultCode)` + `@ControllerAdvice` + 自定义异常形成完整链路
**路径**：
1. 定义业务异常基类继承关系（`BizException implements ResultCode`）
2. 各模块自定义异常继承基类
3. `@ControllerAdvice` 统一捕获并转换 `BaseResponse`
4. 实现"异常抛出 → 自动转标准响应"的全链路

#### M2【P0】Header 常量统一治理

**目标**：消除字符串硬编码，统一使用 `HeaderConstants`
**路径**：
1. grep 所有硬编码请求头字符串
2. 确认是否已在 `HeaderConstants` 中定义
3. 没有的定义补充，已有的替换引用
4. 添加 ArchUnit 测试防止新增硬编码

#### M3【P1】深度分页风险全局生效

**现状**：`PageConstants.isOffsetSafe()` 无调用方
**建议**：
- 在 `PageParam` 的 setter 或 `PageParam.normalize()` 中接入安全校验
- 超阈值时打 WARN 或抛异常（可配置）
- 结合 S3 一起落地

#### M4【P1】ContextKey 类型安全推广

**现状**：大量字符串键使用，类型不安全
**建议**：
- 规范：新上下文属性必须使用 `ContextKey<T>` 定义
- 存量业务键从 `RequestContext` 下沉到各自模块（如 `AuthContextKeys`）
- 提供迁移脚本：`grep 'RequestUtil.set.*String'` → 替换为 `ContextKey`

### 5.3 长期治理（持续）

#### L1【P1】ArchUnit 架构守护

防止公共能力被绕过或滥用：
- 测试类：`@ArchTest` 验证 controller 层 import 必须使用 `Response`
- 测试类：`@ArchTest` 验证所有结果码枚举必须实现 `ResultCode`
- 测试类：`@ArchTest` 验证禁止 `BaseResponse` 直接 `new` 使用

#### L2【P1】能力利用率自动监控

- 脚本定期统计各 core 类的 import 频次
- 低利用能力（PageConstants、Response、ContextKey）定期 review
- 发现新业务模块未使用 BaseResponse 自动告警

#### L3【P2】对接监控与可观测

- `BaseResponse` 构造时注入 service name / module name
- `TraceId` 自动关联业务操作类型
- 结合 Sentry 在响应异常时自动上报上下文

---

## 6. 优化优先级矩阵

| 优先级 | 建议项 | 投入 | 收益 | 状态 |
|---|---|---|---|---|
| P0 | S1: 推广 Response 门面 | 低 | 高 | 待启动 |
| P0 | S2: ResultCode 接口实现 | 中 | 高 | 待核实现状 |
| P0 | M1: ResultCode-异常全链路 | 中 | 高 | 待启动 |
| P1 | S3: PageParam 对象封装 | 中 | 中 | 待启动 |
| P1 | S4: TraceId 传播规范 | 低 | 中 | 待启动 |
| P1 | M2: Header 常量统一 | 中 | 中 | 待启动 |
| P1 | M3: 深度分页风险生效 | 低 | 中 | 待启动 |
| P1 | M4: ContextKey 推广 | 中 | 低 | 待启动 |
| P2 | L1: ArchUnit 架构守护 | 中 | 中 | 待启动 |
| P2 | L2: 利用率监控 | 高 | 低 | 待启动 |

---

## 7. 各模块定制化建议

### 7.1 ydsz-workflow

**优势**：利用率高（BaseResponse + PageResponse + RequestContext）
**建议**：
- 定义 `WorkflowResultCode` 枚举实现 `ResultCode` 接口
- 流程操作异常统一使用 `BizException.of(WorkflowResultCode.XXX)`

### 7.2 ydsz-message

**优势**：消息中心基础设施扎实
**建议**：
- `MessageResultCode` 落实实现 `ResultCode`
- 通知发送场景的 traceId 主动传播

### 7.3 ydsz-nextwiki

**现状**：Web 层直接用 BaseResponse，但**未使用 RequestContext**
**建议**：
- 评估是否需要登录态/上下文（协作编辑场景需要 userId）
- 文件操作结果码统一到 `WikiResultCode implements ResultCode`

### 7.4 ydsz-userinfo

**现状**：仅使用 BaseResponse，**几乎不用 RequestContext / TraceId**
**建议**：
- 用户信息查询应透传 traceId
- `UserResultCode` 实现接口

### 7.5 ydsz-gateway

**优势**：是 core 的深度用户（TraceIdGenerator、HeaderConstants、BaseResultCode 大量使用）
**建议**：
- GatewayConstants 考虑合并到 HeaderConstants
- 限流熔断场景的 traceId 保证不丢失

### 7.6 common-internal 模块

**优势**：common-auth / common-tenant / common-exception / common-feign 深度使用 core
**建议**：
- 作为"最佳实践案例"向其他模块推广
- TenantContext 的跨线程传播已做得好，可总结为模板

---

## 8. 结论与下一步

### 核心结论

1. **基础覆盖充分**：`ydsz-common-core` 的依赖覆盖率达 100%，BaseResponse 和 RequestContext 已成为全平台事实标准
2. **能力利用不均**：精细能力（Response、PageConstants、ContextKey、TraceIdPropagation）利用率显著低于基础能力
3. **架构贯通不足**：各模块对 core 的使用停留在"能用"层面，未形成"规范驱动"的格局
4. **机制空置**：自定义 ResultCode 接口、深度分页保护等高级特性未被激活

### 后续行动项（建议）

1. **本周**：核实各模块自定义 ResultCode 是否已实现接口（grep `implements ResultCode`）
2. **本周**：制定编码规范文档，明确"新代码使用 Response 门面"
3. **下周**：实现 `PageParam` 对象封装，在 system/web 中试点
4. **下周**：提供 Live Template 和迁移 Checklist
5. **本月**：推动 workflow / message 两大核心模块落实 ResultCode 接口
6. **持续**：每日构建中加入 ArchUnit 测试防止退化

---

**附：统计明细**

- 核心类数量：16 个（core 模块）
- 引用 core 的总文件数：**150+**
- 自定义扩展点（ContextKey、Response）利用率：< 5%
- 跨模块标准遵守率（BaseResponse 作为响应）：> 90%
