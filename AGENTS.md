# AGENTS.md — AI Agent 操作手册

> **本文件是 AI Agent（CatPaw / Claude Code / Codex / Gemini CLI 等）的操作指南。**
> 当 Agent 进入本项目时，本文件提供上下文地图、操作决策树和常见任务 SOP。
>
> **优先级**：本文件 > 默认 Agent 行为。当两者冲突时，以本文件为准。
>
> **事实核验（2026-10-27）**：下文出现的每一个类名、注解、方法签名、脚本路径与目录，均已逐条 grep
> 校验过代码。此前本文件写过 `JsonUtils`、`StringOps`、`ExceptionCodeEnum`、`@RequiresPermission`、
> `ExcelFacade.export`、`FileTypeValidator.validate(InputStream, String)` 等**不存在的符号**
> （照做会编译不过或凭空造类），已全部替换为真实符号。修改本文件时请同样先 grep 再落笔。

---

## 1. 项目上下文地图

### 1.1 关键文件索引

| 文件 | 作用 | 何时查阅 |
|------|------|---------|
| `CLAUDE.md` | 核心编码红线规则（always 规则）| 所有编码任务 |
| `docs/ai-rules/shared-rules.yaml` | 红线规则完整定义（单一权威源；`total_rules: 190`）| 违规判定时 |
| `docs/云顶编码规范.md` | 完整编码规范文档（v1.0.12）| CLAUDE.md 未覆盖的场景 |
| `docs/checkstyle.xml` | Checkstyle 规则配置（注意：无 phase 绑定，构建期不执行）| checkstyle 相关查询 |
| `data/checkstyle-baseline.json` | error 级违规存量棘轮基线 | 门禁报"存量上涨"时 |
| `data/scripts/check-checkstyle-delta.py` | 改动文件 0 违规 + 每模块存量只减不增 | 提交前自查 |
| `data/scripts/check-diff-coverage.py` | 增量行覆盖率 ≥ 40%（云顶规范 §14.7）；区间 = `merge-base(<ref>) → 工作区`，未跟踪的 `src/main/java` 直接判红 | 提交前自查 |
| `pom.xml` | 父 POM 依赖版本参考 | 新增依赖前 |
| `README.md` | 项目概览 + 模块说明 | 理解项目全局 |

> `shared-rules.yaml` 内部计数并不自洽（`total_rules: 190`、`meta.summary` 合计 177、`stats.total` 155、
> 实际 `- id:` 189 条），以 `- id:` 条目为准；差异待规则维护者核对，**不要**在别处引用"140 条"。

### 1.2 目录功能速查

```
ydsz-cloud/
├── docs/
│   ├── ai-rules/shared-rules.yaml     # 红线规则权威源（190 条声明）
│   ├── 云顶编码规范.md                  # 完整规范文本
│   ├── common-capability-registry.yaml # common 模块能力清单
│   ├── checkstyle.xml                  # 自动检查配置（无 phase 绑定）
│   └── reports/                        # 分析/门禁报告
├── data/
│   ├── scripts/                        # 运维/构建/门禁脚本
│   ├── sqls/flyway/                    # 版本化 SQL 脚本（V{version}.sql）
│   └── checkstyle-baseline.json        # 违规存量棘轮基线
├── ydsz-common/                        # 30 个公共子模块
└── ydsz-{module}/                      # 9 个网关外业务微服务
```

---

## 2. 操作决策树

### 2.1 编写新代码时

```
[编写新代码]
  │
  ├─ 文件编码必须为 UTF-8 无 BOM
  ├─ 所有类型必须通过顶部 import 声明（禁止 FQN）
  ├─ 禁止 import * 通配符
  ├─ 浮点金额使用 BigDecimal
  ├─ 布尔字段使用 is 前缀（Java isXxx，库表 is_xxx）
  ├─ 日志使用 {} 占位符
  ├─ 日期时间优先用 DateUtils（com.njydsz.common.util.date）；不得不用 JDK 时用
  │   DateTimeFormatter，禁止 new SimpleDateFormat
  ├─ JSON 用 YdszJson（com.njydsz.common.json），禁止 ObjectMapper / JSONObject 直连
  └─ 通用能力优先查 common 模块 → 找不到再自建 → 确保不自建重复轮子
```

### 2.2 新增数据库表时

```
[新增数据库表]
  │
  ├─ 表名必须 ydsz_ 前缀
  ├─ 禁止 Flyway / Liquibase（pom.xml:61 明文禁止，不引入任何 schema-migration 框架）
  ├─ SQL 脚本放 data/sqls/flyway/ 目录，命名 V{version}.sql（人工按序执行，无迁移框架代跑）
  ├─ Entity 类放在 domain/entity/ 包下
  ├─ 排序字段用 sort（禁用 sort_order）
  ├─ 布尔字段用 is_xxx（Java isXxx）
  ├─ 审计字段：created_at / updated_at / created_by / updated_by
  ├─ 逻辑删除字段：is_deleted (BOOLEAN DEFAULT false)
  └─ 租户字段：tenant_id（多租户模块需包含）
```

### 2.3 新增 API 端点时

```
[新增 API 端点]
  │
  ├─ Controller 在 web 层
  ├─ 返回类型必须为 YdszResponse<T> 或 PageResponse<T>
  ├─ 参数校验使用 @Valid + DTO 验证注解
  ├─ 异常交给全局 advice 统一处理（MvcExceptionHandler / BaseExceptionHandler），禁止 Controller 内 try-catch
  ├─ @Operation / @ApiResponse 注解标注 OpenAPI 文档
  ├─ 权限注解：@PreAuthorize，或 ydsz-common-auth 的 @AuthApiPermission
  └─ i18n key 预填到至少 zh_CN + en_US 两个 properties
```

### 2.4 引入第三方依赖时

```
[引入第三方依赖]
  │
  ├─ 先查父 pom dependencyManagement 是否已声明
  ├─ 查 ydsz-common 中是否有对应封装
  │   ├─ 有 → 必须使用封装（如 JSON/Excel/Date/Netty）
  │   └─ 无 → 在对应层级模块添加
  ├─ api / domain 模块的依赖使用 provided scope
  └─ 避免引入与已有功能的重复库
```

### 2.5 修改已有代码时

```
[修改已有代码]
  │
  ├─ grep 确认修改点无其他引用
  ├─ 确认所属 DDD 层的职责边界
  ├─ 禁止跨层直接访问（server 严禁 import infra 类）
  ├─ 新增数据访问 → Repository 接口加在 domain/repository/，实现在 infra/repository/
  ├─ 删除代码前 grep 确认无测试引用
  ├─ 修改后 mvn compile 验证
  └─ @SuppressWarnings 不可去除（WARN-001 要求）
```

## 2.6 跨模块依赖速查（基于 pom 实际结构）

```
web ──→ server ──→ domain ←── infra
 │                          ↑
 └──────────────────────────┘ （web 同时依赖 infra + domain，充当组合根）

api ──→ domain（provided，Feign 契约）
app ──→ domain（按需，移动端入口）

domain 零内部模块依赖（仅依赖 common）
server 不依赖 infra（P1-2 整改后）
```

**禁止行为**：
- server 层 import infra 的 Mapper / RepositoryImpl（编译已通过 pom 阻断，但不排除反射或 Spring 注入绕过）
- domain 层 import 任何项目内部其他模块
- web 层绕过 server 直接调用 infra Repository（应经 server 层编排）

---

## 3. 常见任务 SOP

### 3.1 SOP-1：为 Service 方法添加异常处理

**步骤**:
1. 确认异常场景（业务异常 vs 系统异常）
2. 在对应模块 `ExceptionCode`（`com.njydsz.common.exception.enums`）中添加新错误码
3. 在 i18n properties 文件中添加中英 key
4. 用 `throw new BusinessException(ExceptionCode.XXX, params)` 抛出
5. i18n 异常消息用 `I18n.message()` 或注入 `I18nMessages`

### 3.2 SOP-2：新建 Feign Client

**步骤**:
1. 在 `{module}-api` 包中创建 Client 接口
2. 添加 `@FeignClient(name = "xxx", fallback = XxxFallback.class)` 注解
3. 方法签名与目标服务 Controller 保持一致
4. 创建对应的 Fallback 类实现降级逻辑
5. 返回类型使用 `YdszResponse<T>` 统一包装

### 3.3 SOP-3：新增 Excel 导出端点

**步骤**:
1. 在 web 层 Controller 添加 GET 端点
2. 创建对应 ExportVO（XXExportVO），标注 EasyExcel 注解
3. 用 `ExcelFacade.write(fileName | OutputStream, XXExportVO.class)` 拿到 `ExcelWriter` 写入
   （**没有** `ExcelFacade.export(...)` 这个方法）
4. **禁止循环多次 doWrite 到同一输出流**（必须聚合后单次写入）
5. 文件名通过 `HttpServletResponse.setHeader` 设置，中文用 `URLEncoder.encode`

### 3.4 SOP-4：新增缓存使用

**步骤**:
1. 确认使用本地缓存（`YdszCache`）还是分布式缓存（`Redis*Ops`）
2. 本地缓存：`YdszCache.newBuilder().maximumSize(N).expireAfterWrite(T, UNIT).build()`
3. 防击穿：`Cache#getWithProtection(key, loader, minExpireMs, maxExpireMs)` —— **四参**，
   后两个是过期抖动区间，不存在 `getWithProtection(key, loader)` 两参重载
4. Redis 通过 `com.njydsz.common.redis.service.ops` 下的封装操作：
   `RedisStringOps` / `RedisHashOps` / `RedisCollectionOps` / `RedisAdvancedOps` 等
   （**不存在** `StringOps` / `HashOps` / `ListOps` 这些短名）
5. Key 必须带租户前缀（由 `TenantRedisKeyPrefixer` 负责，禁止手拼）

### 3.5 SOP-5：新增定时任务

**步骤**:
1. 使用 ydsz-cronjob 引擎，禁止自建 @Scheduled
2. 在 cronjob 模块中创建 JobHandler
3. 任务幂等设计（防重复执行）
4. 通过 notify 发送任务结果通知

### 3.6 SOP-6：新增文件上传端点

**步骤**:
1. 在 web 层 Controller 添加 POST 端点（`@PostMapping("/upload")`），参数使用 `@RequestParam("file") MultipartFile`
2. 注入 `IFileStorageProvider` 获取存储实例：`fileStorageProvider.getStorage()`
3. 文件名净化：`String safeName = FileOps.sanitizeFileName(file.getOriginalFilename())`
   （保证：长度 ≤255、不含 `/ \ ..`、任何入参不抛异常）
4. 提取后缀：`String suffix = FileOps.extractSuffix(safeName)`
5. 真实类型校验（P1 强制，YDIZ-COMMON-061）：
   - 注入 `FileTypeValidator`，调用 `fileTypeValidator.validate(file)` —— 入参是 `MultipartFile`，
     **不是** `(InputStream, String)`
   - 或 `FileTypeDetector.detect(InputStream)` 验证 MIME 与后缀匹配
   - 拒绝 `FileTypeDetector.isDangerousType()` 返回 true 的上传
6. 生成存储 Key：`String key = FileOps.generateStorageKey("模块前缀", namespace, safeName)`
   - 多模块共享同一存储桶时使用自定义前缀（如 "wiki"/"workflow-attachment"）
   - **`namespace` 不净化**（原样拼进 key）：只能传内部标识（userId/tenantId），禁止传请求参数
7. 秒传优化（可选）：注入 `FileDedupService`，通过 SHA-256 判断重复文件
8. 调用 `storage.upload(bucket, key, file)` 获取 `FileStorage` 返回元信息
9. 文件元信息存入业务表（如 nextwiki 的 `FileNode`），记录 `storageKey`、`bucketName`、`mimeType`、`size`

**禁止事项**:
- 禁止提取后缀用 `filename.lastIndexOf('.')` 自实现 → 使用 `FileOps.extractSuffix()`
- 禁止生成存储 Key 用 `"file/" + uuid` 自实现 → 使用 `FileOps.generateStorageKey()`
- 禁止净化文件名用 `filename.replace("/", "_")` 自实现 → 使用 `FileOps.sanitizeFileName()`
- 禁止直接 `new MinioClient()` / `new OSSClient()` → 通过 `IFileStorageProvider` 获取
- 禁止在后缀白名单通过后不再校验 Magic Number（YDIZ-COMMON-061 P1 强制）

---

## 4. 项目已废弃/禁止的技术

| 技术 | 替代方案 |
|------|---------|
| Flyway / Liquibase | 版本化 SQL 脚本管理（data/sqls/flyway/ 目录）|
| Druid 连接池 | HikariCP（Spring Boot 默认）|
| 直接 new ThreadPoolExecutor | ydsz-common-thread 封装 |
| Caffeine 直连 | ydsz-common-cache (YdszCache) |
| BeanUtils.copyProperties | MapStruct 或直接 setter |
| `@Scheduled` 注解 | ydsz-cronjob 分布式调度引擎 |
| JSONObject / ObjectMapper | YdszJson |
| Apache POI 直连 | ExcelFacade.write(...) |
| 手动 new RedisTemplate | RedisStringOps / RedisHashOps 封装 |

---

## 5. 多租户注意事项

- 新增业务表必须包含 `tenant_id` 字段（三种策略：SINGLE / MULTI / ISOLATE_DB）
- MQ 消费者中 `TenantContextHolder.getTenantId()` 可能为 null，使用 `AuthContextUtils.getTenantIdOrDefault("1")` 防护
- Redis Key 必须通过 `RedisStringOps` 等 ops 封装（租户前缀由 `TenantRedisKeyPrefixer` 追加），禁止直接使用 `redisTemplate`
- Feign 调用中 W3C Traceparent 已走 ydsz-common-feign 自动透传（`TraceRequestInterceptor`）

---

## 6. AI 必做清单

- [ ] 每次修改的模块必须 `mvn compile` 零错误
- [ ] 每次改动跑两条 CI 增量门禁：`mvn -o checkstyle:checkstyle` → `python3 data/scripts/check-checkstyle-delta.py --base <ref>`（改动文件 0 违规）；`mvn -o verify` → `python3 data/scripts/check-diff-coverage.py --base <ref>`（增量行覆盖率 ≥ 40%）
- [ ] 读门禁退出码不要隔着管道：`python3 … | tail` 取到的是 `tail` 的状态，用 `${PIPESTATUS[0]}` 或干脆不接管道
- [ ] 覆盖率门禁的 `--base <ref>` 度量的是 `merge-base(<ref>) → 工作区`（含未提交改动）；有未跟踪的 `src/main/java` 会直接判红 → 先 `git add`
- [ ] 新文件确认 UTF-8 无 BOM
- [ ] import 语句必须完整（禁止 FQN 和通配符）
- [ ] 日志使用 `{}` 占位符，禁止 `+` 拼接
- [ ] Entity 类放 domain/entity，禁止 infra 建 DO/PO/替身类
- [ ] Repository 接口定义在 domain/repository/，实现在 infra/repository/
- [ ] Controller 返回 `YdszResponse<T>` 包装类型
- [ ] 异常使用 `ExceptionCode` + i18n key
- [ ] 日期使用 `DateUtils`，JSON 使用 `YdszJson`
- [ ] 脱敏使用 `MaskUtils`，缓存使用 `YdszCache`
- [ ] server 不 import infra 类（依赖倒置 = infra 依赖 domain）
- [ ] web 是组合根：pom 声明 server+infra+domain，通过 Spring 装配 Repository 实现

---

## 7. "不要做"清单（常见 AI 陷阱）

1. **不要** 在 Controller 中写业务逻辑（Service 层职责）
2. **不要** 在 server 层 import infra 的 Mapper 或 RepositoryImpl（违反 DDD 依赖方向）
3. **不要** 在 domain 层引入 infra 的 Mapper（依赖倒置 = infra 依赖 domain，反过来不行）
4. **不要** 实现 common 中已有的功能（先搜索复用）
5. **不要** 使用 `e.printStackTrace()`（用 log.error）
6. **不要** 新建 `SimpleDateFormat` 字段（用 DateTimeFormatter 或直接 `DateUtils`）
7. **不要** 使用 `double` 表示金额（用 BigDecimal）
8. **不要** 直接用 `@TableName("table_name")`（缺 ydsz_ 前缀）
9. **不要** 在 Fallback 中使用懒加载（可能导致循环依赖）
10. **不要** 手动设置 `tenantId`（走 TenantContextHolder）
11. **不要** 跳过 `mvn compile` 验证就声称修改完成
12. **不要** 跨 web 层直接调用 infra 的 Repository（应经 server 层编排）
13. **不要** 因为"`checkstyle.skip=false`"就以为构建期做了静态检查——插件只在 pluginManagement、无 phase 绑定，`mvn verify` 从不跑 checkstyle；要么显式 `mvn -o checkstyle:checkstyle` 出报告，要么承认没跑
14. **不要** 凭记忆写本文件/规范里"看起来合理"的类名：先 `grep -rn "class 名字"` 再落笔（本文件曾因此写了 6 个不存在的符号）
