# 云顶云平台全栈对标复核与优化建议（2026-10-25）

> **范围**：`D:\Code\open\ydsz-cloud`（后端 11 模块 / 5,024 主 Java 文件）+ `D:\Code\open\ydsz-micro`（前端 10 应用 + main 壳 / apps≈146k LOC）
> **对标基准**：阿里巴巴 Java 开发手册（嵩山版）、RuoYi-Cloud / RuoYi-Vue-Plus、pig、Spring Cloud Alibaba 参考栈、DDD 分层实践；前端对标 vue-vben-admin v5、Ant Design Pro、shadcn/Radix 体系
> **方法**：7 个独立维度审计 + 主线逐条复核。**本文标记体系**：✅ = 本报告作者亲自复核过文件/行号/计数；◇ = 子体审计统计，口径见证据列，未在主线二次复核。
> **与上一版关系**：本报告是对 `fullstack-optimization-analysis-2026-10-13.md`（自称"49/49 全部完成"）的**增量复核**，并新增 10-14 之后代码的事实校正。

---

## 零、结论摘要

1. **平台广度确实高于 RuoYi/pig**：多租户、分布式锁、通知中心、流程引擎、AI Agent、SSO/OAuth2/SAML/CAS/WebAuthn/SCIM 矩阵齐备；DDD 分层在 import 层面 0 违例、`@Transactional` 非 public 仅 1 处 —— 这两项是同类开源项目普遍做不到的。
2. **风险不在"缺能力"，而在"能力与门禁没有闭环"**：覆盖率门禁卡在 10%（规范写 40–60%）、契约测试插件只存在于注释里、CI 指向已被重命名的脚本、前端 type-check 不覆盖任何 `.vue`、体积预算路径对不上产物目录。**六条自建的"质量红线"当前都是空转状态。**
3. **上一版报告的"全部完成"存在系统性失真**：49 项中至少 4 项交付的是**零接线骨架**（`ElasticsearchSearchStrategy`、`CollaborationService`、`ProcessMiningService`、`SpeechService` 引用数分别为 0/0/仅自引用/仅自引用），以及若干**"文件存在≠生效"** 的判定。本报告按"可被调用 / 可被观测 / 可被门禁阻断"三条件重判。

---

## 一、发布阻断与安全（P0，本迭代必须清零）

| # | 问题 | 证据 | 影响 | 落地动作 |
|---|------|------|------|---------|
| S1 | ✅ **真实数据库口令作为环境变量默认值提交进仓库** | `docs/nacos-config/ydsz-common.yml:83`、`ydsz-generator-web.yml:11`、`docs/本地开发环境.md`（4 处明文），共 **5 个受版本控制文件**；引入自 commit `f8a65ea2c2` | `${VAR:默认值}` 是**失败即放行**：生产漏配环境变量时不会崩溃，而是用已泄露凭据静默连库 | 轮换 DB/Nacos/Redis 口令 → 删除默认值改为 fail-closed → 迁移到 Jasypt `ENC()`（`pom.xml:1011` 已声明却未使用）→ `git filter-repo` 清历史 → `.idea/workspace.xml` 出库 |
| S2 | ✅ **网关 JWT 签名密钥是字面量而非占位符** | `ydsz-gateway/src/main/resources/application.yml:170` `secret-key: "ydsz-cloud-local-dev-secret-key-20261009-at-least-32-bytes-long"`，且 `config/ydsz-gateway-{dev,sit,uat,prod}.yaml` 无覆盖 | 任何有仓库读权限者可**自行签发 token**，等同鉴权体系失效 | 改 `${YDSZ_JWT_SECRET:}` + 启动期 `@PostConstruct` 断言非空且非 dev 默认值 |
| S3 | ✅ **Actuator 暴露面含 `beans,env`，且四处配置源互相漂移**（原报告"两份白名单矛盾"的因果判断有误，见下方校正） | 暴露侧 `docs/nacos-config/ydsz-common.yml`、`docs/nacos-config/ydsz-gateway.yml`、`ydsz-gateway/.../application.yml`、`config/ydsz-gateway-{dev,sit,uat,prod}.yaml` 四份 include 各不相同，dev/sit/uat 模板还保留 `management.endpoint.gateway.enabled: true` + include `gateway`。**校正**：`/actuator/**` 匹配不到任何 `RouteLocator`，GlobalFilter（含 `AuthGlobalFilter`）根本不会执行，`PathGuard.isWhiteList()` 实际只被 `CircuitBreakerGlobalFilter` 消费——所以"白名单"从来不是控制点，**include 是唯一控制手段** | `/actuator/env`、`/actuator/beans` 匿名可读，S1/S2 凭据可被直接 dump，这是**外泄通道**；`/actuator/gateway/**` 可读写路由定义（SCG SpEL RCE 家族，如 CVE-2022-22947） | include 收敛为 `health,info,metrics,prometheus,cache-metrics`，四源取同一值；`gateway` 端点在生产/共享环境 `enabled: false`；`show-details` 由 `always` 降为 `when-authorized` 并留 `YDSZ_HEALTH_SHOW_DETAILS` 排障开关；顺带修复未命中路径 500→404（详见 §十一） |
| S4 | ✅ **11 个模块把配置导入写成 Windows 绝对路径**（共 **22 处** `file:D:/Code/open/ydsz-cloud/...`），含网关与生成器的**基础** `application.yml`（非 dev profile），且无 `optional:` 前缀 | grep 计数 22 | Linux / Docker / CI 上**根本起不来**；也解释了为何只有本机 `mvn compile` 能过 | 改 `optional:nacos:` + `file:${YDSZ_CONFIG_DIR:./config}/…`，配置目录入环境变量 |
| S5 | ✅ **子应用资源 base 路径多源漂移，深链必然 404/HTML-as-JS**（复核后为 **5 类事实源、6 个口径值**，且无注册项声明 `prodPath`，回退值即线上生效值） | 小写回退 `comm/constants/src/micro-apps.ts:203` `/ydsz-${name}/`；大写回退 `registry-adapter.ts:275`、`bash/gen-nginx-conf.mts:90`、`gen-registry.mts:82`、`speculation-rules.ts:61`；第 5 种 `/YDSZ-{name去-web}/` 在 `plugin-sw.ts:56`（该 SW/PWA 插件**全仓零调用**）；构建被 9 个 `apps/*/vite.config.mts` `base:'/'` 覆盖；`.env.production` 的 `VITE_BASE=/system-web/`（缺 `ydsz-` 前缀）成死配置；`bash/deploy/Dockerfile` 用小写目录但**漏 COPY generator-web** | 子应用 `jse/index-*.js` 落到 `location /` 的 `try_files … /index.html`，**返回 HTML 当 JS 执行**，白屏 | 单一口径 `SUB_APP_BASE_PREFIX` + `defaultProdPath()`；构建期按 `readSubAppName()` 强制生产 base；6 个回退点归一；Dockerfile 补齐；新增 `pnpm check:base` 门禁断言 5 类事实源 + 产物引用一致（详见 §十一 B0-4） |
| S6 | ✅ **nginx 静态缓存两层皆错 + 安全头静默丢失（实施中把根因从"snippet 一行"升级为"结构缺陷"）** | ① `bash/deploy/nginx-sub-apps.conf` 对整段 location 下 `immutable 1y`，而该 location 的 `try_files` 兜底正是 `index.html`；② 主应用 `location /` 反过来对**一切**（含 hash JS）下 `no-store`；③ `nginx.conf:165` 两条 **server 级扩展名正则**——nginx 在"最佳前缀匹配块不含嵌套 location"时让正则胜出，故它们同时劫持主应用与 9 个子应用的静态请求，块内既无 `root` 也无 `=404`，缺文件即由兜底返回 HTML；④ 同一块声明自有 `add_header`，按**不跨层继承**规则使 http 层 CSP/XFO/nosniff/Referrer/Permissions/HSTS 全部静默失效；⑤ 硬编码 `X-CDN-Cache "HIT"` 与真实命中无关 | 发布需强刷/清 CDN；主应用长期缓存反向劣化；静态响应裸奔（无 CSP）；`jse/index-*.js` 缺失时**HTML 当 JS 执行**白屏；观测数据误导排障 | 三层缓存（HTML `no-store` / hash 资源 `immutable`+`=404` / 图片字体 30d / `.map` 404）；`alias`→`root`；安全头单一来源 `security-headers.conf` 并在每个声明 `add_header` 的块重新 include；删除 server 级正则；新增 `pnpm check:nginx`（7 项不变量 + `--self-test` 用修复前反例证伪，8/8 命中）——详见 §十一 B0-5 |
| S7 | ✅ **设计令牌管线断裂：146 个 `--ydsz-*` 定义从未被应用加载** | 定义在 `comm/@core/ui-kit/design-tokens/src/styles/variables.css`（**146 个变量**，无业务入口 import）；应用实际 import 的 `@ydsz/styles` → `base/design/src/design-tokens/default.css` / `dark.css` 各自 `--ydsz-` 计数为 **0**；消费侧 **29 个** `ydsz-ui` 组件文件用 `var(--ydsz-*)` 且**无 fallback** | kit 组件渲染在**未定义自定义属性**上（取初值），kit 暗色模式失效；且暗色选择器两套机制冲突（`.dark` vs `[data-theme='dark']`） | 把 `variables.css` 接入 `comm/styles` 入口（或生成进 default.css），统一暗色选择器，验收：10/10 构建产物 CSSOM 无未解析 `--ydsz-*` |
| S8 | ✅ **契约/错误码 CI 门禁指向不存在的文件，恒失败** | `.github/workflows/ci.yml:184` `node bash/gen-error-codes.mjs --check`、`:237` `node bash/contract-notify.mjs`，而 commit `b67c45e67` 已把两者重命名为 `.mts`（磁盘仅 `gen-error-codes.mts`/`contract-notify.mts`）；CI 锁 Node 22，`.mts` 需 `--experimental-strip-types` | 前后端契约漂移检测**事实上从未运行**；`notify` 作业（`needs: contract`）连带失效 | 改文件名 + 加 `--experimental-strip-types`（或 Node 24）；把 `pnpm gen:api:check` 一并纳入门禁 |
| S9 | ✅ **网关路由双源漂移 + 前端"双剥 `/api`"→ 生产全量 404**（新发现，非原 S 系列） | `git show HEAD` 实测两份路由表已各自漂移：`routes-nacos.yaml` 53 个 pattern vs `application.yml` 66 个 `Path=`，**只在 manifest 15 条、只在 application.yml 28 条**；`/api/notify/**`→9004、`/api/job/**`→9006、`/api/wiki/**`→9003 三条只存在于 application.yml 且后端 `@RequestMapping("/(notify\|job\|wiki)")` 命中 **0**；`bash/deploy/nginx.conf` 的 `location /api/ { rewrite … }` + **10 个** `vite.config.mts` 的 `rewrite(/^\/api/,'')` + 网关 `StripPrefix=1` = 剥两次；`META-INF/spring-configuration-metadata.json` 仍声明 5 个 5.0.2 已删除的 `spring.cloud.gateway.*` 旧键（jar 自带 metadata 里旧键数为 0，即 IDE 会补全**不绑定**的键）；后端 `.github/workflows/ci.yml` Step 3 调 `data/scripts/fix-file-encoding.py`——脚本不存在，job 自该步恒红，**任何后置门禁都不会执行**；同 job 的 Step 4a/4b/4d 另引用 3 个不存在的脚本；前端交叉检出写成 `njydsz/ydsz-micro`（origin 实为自建 Gitea `…/ydszopen/ydsz-micro`）+ `continue-on-error` → R9/R10 从未在前端跑过 | 死路由与错前缀在运行期一律表现为 404，服务侧日志无异常，排障指向错误方向；写错前缀的网关配置静默失效；"门禁已接"是错觉——接在一个必然提前失败的 job 里等于没接 | 路由清单单一来源 + `gen-gateway-routes.py --check` 生成块 + `check-gateway-routes.py` R1–R10（57/57 反例自检，实测 20 路由/1056 路径/9 服务红 0）；nginx 与 10 个 vite 停止剥 `/api`；`/api/v1` 238 行清理并修掉 `gen-contract.py:1205` 的模板再生源；metadata 改为 6 条 error 级 deprecation；新写 `check-file-encoding.py`（28/28 自检，实测 5928 文件红 0）并把两道新门禁排在 Checkstyle/Maven 之前、移除 3 个伪门禁步骤（详见 §十一 B0-9） |

---

## 二、数据库表设计

**先纠正基准**：生产库是 **PostgreSQL 18**（✅ `docs/nacos-config/ydsz-common.yml:81` `jdbc:postgresql`、✅ `postgresql` 驱动声明 12 处 vs mysql 2 处、`docs/云顶编码规范.md:8`），而 `AGENTS.md` 通篇按 MySQL 口径书写并**自相矛盾**（§2.2 要求"使用 Flyway 管理"，§4 又把 Flyway 列为禁用项）。

**已核实的良好面**（主线亲自解析 `data/sqls/postgre/` 平衡括号统计）：153 张表全部 `ydsz_` 前缀、**153/153 具备 `tenant_id` + `is_deleted` + `created_at`**、主键 100% `varchar(36)` 无类型漂移、`sort` 而非 `sort_order`、411 条 `CREATE INDEX`。审计侧另计得 435 索引 / 89 UNIQUE（含 flyway 增量口径）。命名一致性明显优于同类开源项目。

**真实差距**：

| 级别 | 问题 | 证据 |
|---|---|---|
| **P0** | ✅ **迁移脚本从不自动执行**：Flyway 不在任何 pom 依赖中，`ydsz-gateway/.../application.yml:130` 还显式排除 `FlywayAutoConfiguration`；11 个 `V26.10.*.sql` 靠人工 apply | schema 变更运行期不可追踪，环境漂移无解 |
| **P0** | ◇ `tenant_id` 可空 **30/153** 张表（含 `ydsz_comm_audit_log`、`ydsz_comm_outbox`、`ydsz_msg_outbox`、`ydsz_idm_auth_policy`）；定义口径 **6 种**（`NOT NULL DEFAULT '0'` 121 / `DEFAULT '1'` 15 / `DEFAULT NULL` 12 …） | 跨租户读写泄漏与拦截器旁路风险 |
| **P1** | ✅ 唯一约束几乎缺失（**411 普通索引 vs 仅 2 条 `CREATE UNIQUE INDEX`**）；◇ 89 个 UNIQUE 中 **77 个未过滤软删行**，仅 8 个 partial index（`V26.10.06.sql`）；**64 个 UNIQUE 未带 `tenant_id`**、17 个把 `tenant_id` 放在末列 | 软删后 `role_code`/`template_code` 永久无法重建；租户内唯一实为全局唯一 |
| **P1** | ◇ 186/344 个 `_id/_code/_key` 连接列**无前导索引**；12 个外键列中 6 个无支撑索引（`ydsz_flow_instance.definition_id` 等）；45 个索引以低基数列（`status`×26）打头；**120 个复合索引把 `is_deleted` 放在末列**（零选择性、只增键宽）；7 组前缀冗余索引 + 1 组完全重复 | 子表删除触发父表顺序扫描；索引膨胀 |
| **P1** | ◇ 实体/DDL 漂移：`MpSimpleEntity.java:103-106` `@TableLogic Boolean isDeleted` vs DDL `smallint`，仅靠 JDBC URL 的 `stringtype=unspecified` 掩盖；7 个 `Boolean↔smallint`；`MpSimpleEntity.java:93-96` 自带 `@Version revision`，但注释宣称已拆出 `MpVersionedEntity` **而该类不存在** → 全部 `MpBaseEntity` 子类静默获得乐观锁 | 静默行为差异，最难查的一类 Bug |
| **P2** | ◇ 类型治理：`timestamp without time zone` **413** vs `with time zone` **3**（无 UTC 纪律）；`numeric(20,6)` 过度精度 11 列；`cost` 同语义两种精度（12,6 / 20,6）；271 个枚举型 `varchar` 仅 **2 个 CHECK 约束**、144 个状态列可空 | 时区/审计正确性、约束缺失 |
| **P2** | ◇ 方言树已不可执行：`legacy-dialects/mysql` 有 **330 处缺逗号** + 87 处 `COMMENT '…' DEFAULT`（MySQL 语法非法）；Oracle 138 表**零分区**（PG 有 `ydsz_comm_audit_log` 13 分区等），比 PG 少 15 张表 | 维护 4 套 schema 副本（`postgre/` 9,827 行、`flyway/` 10,897 行、`legacy-dialects/` 12,578 行、根 dump）纯属负债 |

**建议**：① 真正接入 Flyway（命名已经吻合 `V*.sql`），把 11 处 `CREATE INDEX CONCURRENTLY`（`V26.10.08.sql:77-99`，**在事务内非法**）改 `noTransaction=true`；② 补齐 `V26.10.11/12/13` 版本空洞（当前 01–10 直跳 14）并为每个迁移配 undo 脚本；③ 一次性 DDL 治理包：`tenant_id NOT NULL DEFAULT '0'`（30 表）+ 软删 partial unique（77）+ 租户前导唯一（64/17）+ 186 连接列索引 + 删冗余索引；④ **只保留 PostgreSQL 一套方言**，删除 mysql/oracle legacy 树，需要时用翻译器再生。

---

## 三、接口单元测试

✅ 亲自复核的硬事实：全仓 **0 个 `assertThrows`**；**163 个 `*SmokeTest.java`**；`*IT.java` 仅 3 个；根 `pom.xml:97-98` 覆盖率门禁 `line=0.10 / branch=0.05`；`pom.xml:1392` 反常识地**禁用 `@Disabled` 处理**（`junit.jupiter.conditions.deactivate = …DisabledCondition`）。
> **状态（2026-10-26）**：后两条已由 **B1-2** 修掉 —— 全局阈值换成 21 个模块各自的实测棘轮（根默认 0.01/0.005）+ 新 `check-diff-coverage.py` 增量 ≥40%；`DisabledCondition` 禁用与 `<skipITs>` 硬编码均已移除。`assertThrows`/smoke 结构问题属 B1-3~B1-5。

◇ 结构画像（子体审计）：201 个真实测试文件 / 573 个 `@Test`（另有 25 个测试文件位于被提交的 `data/scripts/backup_logs/` 源码副本内）；81% 测试文件是 smoke（平均每控制器 1.5 个用例）；纯 Mockito 单测 12 个；`@SpringBootTest` 6 个；Testcontainers 实际使用 2 个文件；真实"业务行为"服务单测约 **18 个**。比例失衡点：`ydsz-common` 1,589 文件 / **4** 测试；`ydsz-workflow` **41 个 SvcImpl / 4 个测试**；`ydsz-gateway` 19 个 filter / 1 个测试。

**与自有规范的对账（这是最刺眼的差距）**：`docs/云顶编码规范.md:2304-2328` 已强制五层金字塔（70/15/5/5/5）与**分模块门禁**（userinfo 增量≥60%、workflow≥55%、literule≥50%、message/cronjob≥45%…），`:2568-2586` 要求增量行覆盖≥40% 阻断 PR、全量≥60%、Pitest≥60%。**实际 CI 只执行了那个 10% 全局 BUNDLE 门禁**；增量门禁、per-module 门禁、Pitest 在 pom 与 workflow 中**均不存在**。此外 ADR-005（前端）仍写着"全仓禁止测试代码（含 `@playwright/test`、`@axe-core/playwright`）"—— 该禁令已在规范 v1.0.8 被反转，ADR 过期未修订。
> **修正（B1-2 实测，2026-10-26）**：比原判断更糟——那 10% 全局门禁**也从没执行过**。CI Step 4 的 `mvn checkstyle:check` 面对 4155 条存量 error 恒红，而 Actions 在首个失败步骤即停止，`mvn verify`（含 `jacoco:check`）从未运行；该 10%/5% 阈值本身有 20/21 个模块达不到，属"恒红的假门禁"。现已换成三条可证伪门禁：每模块覆盖率棘轮（只减不增）、checkstyle 增量红线 + 存量棘轮、增量行覆盖率 ≥40%。详见 §十一「B1-2」小节。

**测试价值缺陷（不是数量问题）**：
- **反例覆盖为 0**：163 个 smoke 只断言 `status().isOk()` + `$.code`，无 4xx/5xx、无权限（`@PreAuthorize`）、无租户隔离、无错误码断言 —— 等于只验证"路由存在"。
- **同义反复测试**：`FlowInstanceServiceImplTest.java:50-136` 断言 `when(lifecycleManager.start(dto)).thenReturn("inst-001")` 后检查返回值等于 `"inst-001"`，任何透传实现都能通过；`MessageServiceImplTest.java:43-95` 为 3 个 `@Test` 声明了 **18 个 `@Mock`**。
- **契约测试是空壳**：✅ `spring-cloud-contract-maven-plugin` 的唯一出现位置是 `ydsz-agent-server/pom.xml:177` 的**XML 注释**，插件未声明 → 2 份 `contracts/*.yml` 永不生成测试；且 DSL 在 `-server`、基类在 `-web`，即使加插件也找不到彼此；`BaseContractTest.java:38` 用 `@ActiveProfiles("contract")` 但 `application-contract.yml` 不存在。
- **生成器系统性放大缺口**：`ydsz-generator-server/src/main/resources/templates/default/` 17 个 `.vm` 模板**无任何测试模板、不产出 `src/test`** → 每个脚手架模块从 0 测试起步。
- **测试基建已有但没人用**：`BaseControllerMockTest`、`TestcontainersBase`（PG18+Redis7、`@DynamicPropertySource`）设计良好，但 `ydsz-system`/`ydsz-userinfo` 各自**重新实现**同名容器基类；9 份 `DddLayerArchTest` md5 各不相同（规则已漂移）。

**建议路线（按性价比排序）**：
1. **先补反例，而非补数量**（P0）：给 `BaseControllerMockTest` 加 `.setControllerAdvice(new GlobalExceptionHandler())`，为 userinfo 的 RBAC/认证 15 个高流量端点各补 ≥3 个 4xx + `$.code` 断言。这是当前**唯一能提升缺陷拦截率**的动作。
2. **门禁改为分模块棘轮 + 增量门禁**（P0）：按 §14.1.1 落地每模块 jacoco rule，CI 加 `diff-cover`（阈值 40%，棘轮每季 +2% 且禁止回退）；顺手删掉 `DisabledCondition` 禁用。
3. **让契约测试真的跑起来**（P0）：声明插件、同模块放 DSL、补 `application-contract.yml`，验证 `target/generated-tests` 出现 `*ContractTest`，再推 userinfo（SCIM/OAuth2）、workflow。
4. **生成器输出测试骨架**（P0，系统性）：新增 `controllerSmokeTest.vm` + `serviceImplTest.vm`（正例 + 2 反例 + `@ParameterizedTest` 边界）。
5. **补 P0 模块真实服务单测**（P0）：workflow 状态机（流转/跳转/超时回滚/补偿）、message 12 渠道路由；每类 **≤6 个 `@Mock`**，禁止纯委派断言。
6. **`ydsz-common` 优先测纯逻辑高扇出件**（P1）：`YdszJson`/`DateUtils`/`MaskUtils`/`FileOps`/`StringOps` 租户前缀/`YdszCache#getWithProtection`/`ExceptionCodeEnum↔i18n` 完备性。
7. **引入 Pitest 夜间作业**（P2，规范已要求）：先限 userinfo/workflow —— 这是唯一能自动检出同义反复测试的工具。

**覆盖率阶梯**：基线（真约 8–12%）→ Q1 line 20% + 增量 30% → Q2 35%/40% → Q3 45%/45% → Q4 55%/50% 且 P0 模块达 60/55/50%（规范值）。结构性目标：从"81% smoke"迁到 70/15/5/5/5 金字塔。

---

## 四、前后端能力贯通

**做得对的部分**：`YdszResponse`（`code`/`msg`/`level`/`data`/`traceId`/`requestId`/`spanId`/`timestamp`/`extensions`，无 `success`，成功码 `A00000`）与前端 `request.ts:179` 的 `successCode`/`codeField`/`dataField` 语义一致；`PageResponse` 扁平 `total/pageNum/pageSize` + `nextCursor/isMore`，两侧**分页参数名无混用**；`X-Tenant-Id` 两侧一致（`request.ts:150-152` ↔ `HttpHeaderConstants.java:53`）。信封层面这是少见的高质量。

**断裂点**（多数会直接产生线上 404 或静默失配）：

| 级别 | 问题 | 证据 |
|---|---|---|
| **P0** | ✅ OpenAPI 单一事实源不成立：`pom.xml:1529` URL 字面量**括号缺失** `http://localhost:${server.port:/v3/api-docs`；插件在 `compile` 阶段做 HTTP 抓取，**要求服务已启动**，构建期无法自洽；产物落 `target/`（非可发布 artifact） | 契约无从生成，只能读工作树 |
| **P0** | ✅ 跨仓硬耦合本机路径：`bash/unified-contract.mts:41` `CLOUD_ROOT = process.env.YDSZ_OPENAPI_ROOT \|\| 'D:\\Code\\open\\ydsz-cloud'`；CI 注入的却是 `YDSZ_CLOUD_ROOT`（`ci.yml:176/182`）→ CI 下新轨即便被调用也回落到作者本机路径 | 换机器/换人就断 |
| **P0** | ◇ **网关路由缺口 = 前端调用真实 404**：`routes-nacos.yaml` 全部 15 条路由不含 `/api/search/**`、`/api/file/**`、`/api/feature-flag/**`（只有 `/api/feature-flags/**`）、`/api/error-codes/**`、`/api/repeat-submit/**`，且**无 `/api/**` 兜底**；而 `apps/system-web/src/api/{globalSearch,fileMultipart,featureFlag}.ts` 精确调用这些路径，vite proxy 亦指向网关 | dev 与 prod 同断 |
| **P1** | ◇ **双轨 codegen，新轨零消费者**：Track A（`unified-contract.mts` → `apps/*/src/api/sdk/`，openapi-typescript + openapi-fetch）已提交但**无任何源文件 import `#/api/sdk`**（≈1MB 死 artifact）；真正在用的是 Track B `bash/gen-contract.py`（76KB Python，产出 244 个 `api/*.ts` + 9 个 `models.ts`），CI 只守 B 轨，`gen:api:check` 从未运行 | 两套契约故事，新人必困惑 |
| **P1** | ◇ 静默放行漏洞：`unified-contract.mts:534` `if (oldHash !== newHash && oldHash)` —— 新 app 缺 lock 时**永不报漂移** | 门禁形同虚设 |
| **P1** | ◇ 后端存在但契约缺失的能力：`GlobalSearchController`（`/search/aggregations`、`/suggest`、`/analytics/hot|zero`）、`FeatureFlagAdminController`、`FileMultipartController`、`LockAdminRestController`、`AsyncTaskController`、`PromptDebugController`（前端零引用）、cronjob `ExportTaskController`（前端命中 0）、workflow 分析导出；**`ydsz-generator`（8 控制器/30 op-path）完全不在 `SERVICE_MAP`** | 后端能力做出来但前端用不到 |
| **P1** | ◇ **字段级校验错误到不了表单**：`ValidationExceptionHandler.java:103,138-145` 把 `BindingResult` 压平成单个 `msg` 字符串；前端全仓 `grep fieldErrors` **0 命中** | 表单体验退化为"一行红字" |
| **P1** | ◇ 错误码文案双轨：`error-codes.generated.ts` 自动抽 443 码，但 `comm/locales/errors/{zh-CN,en-US}.ts` 与后端 `i18n/*-messages*.properties` **人工并行**、无生成关系；ADR-009 命名约定已 ACCEPTED（2026-10-06，457 常量）未见落地校验 | 文案永远滞后 |
| **P2** | ◇ traceId 响应侧断链：请求生成 `X-Trace-Id`（`request.ts:155`）、后端注入双头（`TraceIdPropagation.java:72,91`），但成功响应被拦截器剥成 `data`（`preset-interceptors.ts:90-92`），`traceId/requestId/spanId` 丢弃；错误分支只回读**请求侧自建 id** | 前后端日志对不齐 |
| **P2** | ◇ **WebSocket 把 access token 拼进 URL query**（`use-websocket.ts:155`）→ 落入 access log / 代理日志；心跳走应用层 `__heartbeat__`，重连有退避但未带 tenant 头 | 凭据泄露面 |

**贯通落地包**：① springdoc 改为**构建期离线产出**（MockMvc 切片或 `generate` 不依赖运行实例），`mvn deploy` 出 `ydsz-{module}-openapi:{git-sha}` artifact，前端按坐标拉取，彻底去掉 `D:\Code` 依赖；② 二选一收敛 codegen 轨（建议保留 B 轨生成 per-endpoint 函数、把 A 轨的类型并入），`gen:api:check` + lock 缺失即失败；③ 由后端 `@RequestMapping` 反射导出路由清单生成 `gateway-routes.json`，CI 比对"controller 根路径 ∉ 网关路由"即失败；④ `extensions.fields` 输出 `{field:msg}` 并让前端 `BusinessError.fields` 回填 `YdFormMessage`；⑤ 错误码 + i18n 文案同源生成，`check:i18n` 校验键集相等；⑥ WS 改一次性 ticket（60s、绑租户）。

---

## 五、前后端架构优化

**后端（高于基线的部分）**：JDK21 + Spring Boot 4.1.0 + Spring Cloud 2025.1.2 + SCA 2025.1.0.0，代际领先 RuoYi(2.7)/pig(3.x)；✅ `server→infra` import 违例 **0**、`domain→infra/Mapper` **0**、web 直接 autowire Repository **0**；✅ 563 个 `@Transactional` 中仅 **1** 个落在非 public 方法；OWASP Dependency-Check 已接 `failBuildOnCVSS=7`；`docs/common-capability-registry.yaml` 与 30 个 common 子模块**零差异**。

**后端真实架构债**：
- **绕过分层的三处后门**（ArchUnit 结构上看不见）：◇ `DirectDbRuleConfigProvider.java:31` 用**行内 FQN** 持有 `com.njydsz.literule.infra.mapper.RuleDefinitionMapper`，同时规避"禁止 FQN"与"禁止 infra import"两条红线，其 pom 注释自称"权宜之计"；◇ `ydsz-generator-server/pom.xml` 只声明 `-infra` 却 import 19 个 `entity.*` + 11 个 `repository.*`（**未声明即使用**，infra 一旦剥离 domain 就崩）；◇ `ydsz-workflow-infra/pom.xml` 依赖 `ydsz-agent-api` + `ydsz-agent-domain`（跨服务 infra 耦合），`ydsz-userinfo-infra` 依赖本模块 `-api`（方向倒置）。9 份 `DddLayerArchTest` **只覆盖 `*-server`**，web/domain 无守护。
- **能力重复造轮（隐性成本最高）**：◇ `CacheKeyBuilder` **11 份**（common 内部 2 + 9 个模块各自实现）；◇ 限流相关类 **35 个**横跨 safe/redis/queue/notify/search/socket/tenant/feign/gateway，含一对重复 `GatewayRateLimitProperties` / `GatewayRateLimiterProperties`；◇ IP ACL **三套**（`common-safe/filter/IpAccessFilter` + gateway 两个 filter）；◇ 幂等 **4 套**；◇ ID 生成 **5 套**，而全局 MyBatis-Plus 是 `id-type: auto`（`docs/nacos-config/ydsz-common.yml:134`）；◇ JSON 门面 `YdszJson` / `JsonMapper` / `TolerantJsonUtils` 并存。
- **✅ 红线指向了不存在的类**：`CLAUDE.md`/`AGENTS.md`/`shared-rules.yaml` 共 **11 处**强制"JSON 使用 `JsonUtils`"，但 `find JsonUtils.java` = **0**。红线不可执行，AI 与新人按文档写必编译失败。
- ◇ **`common-json` 手写序列化器是最大正确性风险面**：88 文件，`JSONReader` 1667 行 / `JsonParserUtil` 1550 / `SerializationProvider` 1425 / `JSONWriter` 1361 / `JsonMapper` 1205，通过 `JsonHttpMessageConverter` **替换了全部业务流量的 Jackson**；`JacksonExclusionEnvironmentPostProcessor` 显示曾因 springdoc/actuator 破坏而被迫回退为"共存"。日期/泛型/`BigDecimal`/null/多态都是自实现。
- ◇ 依赖健康：`dynamic-datasource-spring-boot**3**-starter:4.3.1` 用在 Boot **4** 上（starter 代际不匹配）；`openpdf 1.3.30`（iText4 分支）、`disruptor 3.4.4`（2017 线）、`tika 2.9.2` 偏旧；✅ Checkstyle 引擎在 `pom.xml:1254` **钉在 9.3**（早于 JDK21 语法），红线虽开着（`checkstyle.skip=false`）但可能漏检新语法 —— 需实测而非假设。
- ◇ 文档/部署漂移：`ydsz-gateway/.../application.yml:4` 声称 SkyWalking 配置、`docker-compose.yml:138` 设 `SW_ES_PASSWORD`，但**无 SkyWalking 依赖**；网关 9 条基础路由硬编码 `uri: http://localhost:90xx` 且 `discovery.locator.enabled: false`（`lb://` 只存在于 `routes-nacos.yaml`）→ 灰度 `GrayLoadBalancer` 在默认配置下不生效。
- ◇ 线程上下文安全：`DataScopeContextHolder.java:48` 用**普通 `ThreadLocal`**（非 TTL）承载行级数据权限 → 异步/队列线程拿不到，且池化线程间可能串请求；`allow-circular-references: true`（gateway `application.yml:22`）+ 新增 `@Lazy` 说明 bean 图在长环。

**前端架构债**：
- 血缘：确认 **vue-vben-admin 派生**且深度定制（`main/` 作宿主、`@ydsz/*` 作用域、wujie/micro-app 换成自研 kernel、Ant/Element 换成自研 kit）。✅ README 仍写"Vue 3 + Element Plus"（`README.md:21`），而 `element-plus` 在 **0 个 package.json** 中出现 —— ADR-008 已宣布 2026-09-19 收官，文档未跟。
- 自研 `@ydsz/micro-kernel` 67 文件 / 15,452 行；importmap 外置仅 9 个包（`micro-shared-deps.ts:34-56`）—— 注意 ✅ `importmap.lock.json` 记 **vue-router 5.3.0 / pinia 4.0.3 / echarts 6.1.0 / vue 3.5.42**，而 catalog 是 `^4.5.1 / ^3.0.3 / ^5.6.0 / ^3.5.17`，**锁与目录版本不一致**，运行时共享版本不可控。
- ~~◇ **turbo 缓存键失效**：27 个 workspace 包**没有 build 脚本**并直接导出 `./src/index.ts`（`ydsz-ui/package.json` 的 `dist` 入口被注释掉），`globalDependencies` 覆盖 `conf/vite-config/src`、`bash/*/src`、`**/tsconfig*.json` 但**不含 `comm/**`** → 改共享源码不会使 app 构建缓存失效（陈旧产物）。~~ **已证伪**（见 §十一 B1-7；条数实测为 44 个 `comm` 包中 34 个无 build 脚本，非 27）：Turbo 2.10.8 给无 build 脚本的工作区依赖仍生成 `<NONEXISTENT>` 任务节点并按**整包目录**计入 `inputs`，`^build` 已覆盖——实测改 `comm/effects/mock-service` 一行，只有 3 个真实依赖方 app 的缓存失效（正是声明它的 3 个）；把 `comm/**` 加进 `globalDependencies` 反而变成 10/10 无差别失效，属命中率倒退。本仓真实缺陷是另一半：10 个入口各自重复转译 744 个 SFC（ydsz-ui 413 + ydsz-vue 331），`main/vite.config.mts:29-70` 甚至手写了个 `resolveId` 插件补 `ydsz-vue` 的内部 `@/` 引用——这是构建耗时问题，不是缓存正确性问题。
- ◇ **type-check 是空壳**：`turbo type-check` 只有 4 个包声明（全是纯 TS 包 `tsc --noEmit`）；**main 与 9 个 app 一个都没有**，`vue-tsc` 只作为根 devDep 存在**从未被调用** → ~146k LOC 的 `.vue` **零类型覆盖**，CI"类型检查"步骤证明不了任何事。
- ◇ **体积预算路径对不上产物**：`conf/budget.config.json` 检查 `main/dist/assets`、`apps/*/dist/assets`，但构建输出目录是 `js/`、`jse/`、`css/`（`application.ts:129-135`，未设 `assetsDir`）→ `pnpm check:size` **匹配不到任何文件**。**已修**：目录口径与判据收进 `budget-config.ts` 单一来源，预算改棘轮，CI 缺产物即红，另查出真正的性能根因是 `manualChunks` 的 catch-all 单 vendor chunk（详见 B1-8 体积轨小节）。
- ◇ 测试金字塔倒挂：137 个 `.test.ts` 中 **132 个在 comm/（kit 原语），业务侧仅 5 个**（agent 2、system 3，**cronjob/generator/literule/message/nextwiki/userinfo/workflow = 0**）；根 `vitest.config.ts:52` 只把 `#` 别名指到 `main/src`，导致 `apps/system-web/src/views/config/index.test.ts:19` 的 `#/adapter/vxe-table` **解析到错误包**；覆盖率阈值 25/15/20；12 个 e2e spec **从未进 CI**，且 `pnpm test:e2e` 未指定 config 会解析到根 `playwright.config.ts`（`testDir: ./tests/a11y`）跑错套件；`playwright.visual.config.ts` 声称门禁，但 ✅ `git ls-files tests/visual` 显示**零张基线 PNG 被提交**，CI 的 visual 作业断言基线存在 → 该门禁不可能有意义地通过。
- ◇ 工程卫生：23 个文件在 9/9 应用里各存一份（多为薄 shim，代价是**漂移**而非 LOC —— `adapter/form.ts` 166 行 vs 378 行不一致）；`: any` 196、`as any` 83、`as unknown as` 118、`@ts-ignore` 8、TODO/FIXME 122（89 在生成的 `models.ts`）；god 组件 `cron-builder/index.vue` 995、`system-web/views/preference/index.vue` 918、`search-panel.vue` 869、`IconPicker.vue` 806、`rule-pack/index.vue` 801；`hmr-guard.ts:1-90` 用 monkey-patch **吞掉** `unregisterHMR` TypeError（掩盖而非修复）；CSP `script-src 'self' 'strict-dynamic'`（`nginx.conf:71`）**没有 nonce 变量**而 `main/index.html` 有内联脚本与注入式 loading → 生产被拦；`.dev-logs/`（202 文件 / 2MB）、根 `junit.xml`、`batch-write.cjs`、`gen-final.js`、`data_part1.json`、`comm/effects/micro-kernel/.write_test` 等垃圾入库。三套表格体系（vxe + `@tanstack/vue-table` + ydsz-ui primitives）。

---

## 六、前后端功能增强

**先做一次"完成"对账**（回应上一版报告的 49/49）：

| 上一版声称 | 复核结论 |
|---|---|
| P1-4 工作流可视化设计器 | ✅ 真接线：`apps/workflow-web/src/views/designer/bpmn-{canvas,designer,property-panel}.vue` 已注册于 `router/routes/modules/workflow.ts` |
| P2-12 租户配额 UI | ✅ 存在：`apps/system-web/src/views/tenant-quota/tenant-quota.vue` |
| P2-3 OnboardingTour / P2-13 偏好导入导出 | ✅ 存在：`comm/@core/components/src/onboarding-tour.vue`、`use-preference-io.ts` |
| P1-5 ElasticsearchSearchStrategy | ⚠ **零引用骨架**（✅ referencing_files=0），未接入检索链路 |
| P2-10 协作文档框架 | ⚠ **零引用骨架**（referencing_files=0） |
| P2-11 Process Mining | ⚠ 仅接口+实现自引用；源码自带 `// TODO: 基于 ydsz_flow_audit_log…当前仅返回占位结果`（`ProcessMiningServiceImpl.java:53-54,87,99`）→ **返回占位数据**且无调用方 |
| P2-9 ASR/TTS 语音 | ⚠ 仅接口 + Provider，无消费者；且 `SpeechService`/`AlertRuleEngine` 被放进 **`ydsz-common-sentry`**（模块职责漂移：Sentry 模块承载 ASR/TTS 与告警规则引擎） |
| P1-14 API 契约测试 | ⚠ **永不执行**（见 §3） |
| P1-15 CI 覆盖率门禁 | ⚠ 存在但阈值 10%，与规范 40–60% 不符（见 §3） |

**结论**：真正缺的不是"再建 20 个骨架"，而是**把已有骨架接上电并配可观测验收**。

**增强优先级**：
1. **P0 收尾类**（把 6 个孤儿骨架要么接线、要么删除，勿留中间态）：Process Mining 需事件日志数据源才有意义 —— 若排期不足，应删除占位实现并在 README 标注"实验性未启用"，**当前返回假数据比没有数据更危险**。
2. **P1 后端能力前端化**：分布式锁（仅 `LockAdminRestController`、未进 spec）、错误码文档端点 `ExceptionCodeDocEndpoint`、防重复提交、文件秒传/去重、i18n 检索 —— 全部存在但前端**类型不安全或完全不可达**（见 §4 缺口清单）。
3. **P1 鉴权自研风险**：◇ 无 Sa-Token、无 Spring Security OAuth2，认证栈为自研 `ydsz-common-auth` + jjwt 0.12.6（`spring-boot-starter-security` 仅 2 个 pom）。功能矩阵齐全（OAuth2 授权码/设备码、SAML、CAS、WebAuthn、SCIM、LDAP、API-key，各 10–34 文件），但**安全实现的总线系数（bus factor）极高**；建议至少引入外部评审 + 对 S2/S3 做红队自测，而非重写。
4. **P2 边界签名/防重放在网关缺位**：◇ `REPLAY_DETECTED` 错误码已声明（`GatewayErrorCode.java:99`）但边缘从未校验；`ApiSignatureProperties`/nonce 只存在于 servlet 侧 `common-safe`。开放 API 场景需补边缘 filter。
5. **P2 前端已建而未用的交互能力**（详见 §8）：`YdVirtualList`、`YdBulkActions`、`useKeyboardShortcut`、`useRouteSkeleton`、`mobile-bridge` 采用率 0/1。

---

## 七、前后端性能提升

**后端 —— 先给正确的控制项（勿重复劳动）**：✅ 全局分页 `maxLimit` 钳制（`MybatisPlusConfiguration.java:291-300`）、深翻页告警 10k/拒绝 50k（`SafeQueryInnerInterceptor.java:80-86`）、网关 gzip、网关阻塞 Redis 已下沉到受限 Elastic、Agent SSE 已离线程 + 心跳、虚拟线程 LLM 扇出、LLM 击穿用 `getWithProtection`；◇ 多行扫描"`@Transactional` 内含 Feign/HTTP/存储调用"**零命中** —— 这块是真的干净。

**必须修的性能项**：

| 级别 | 问题 | 证据与量化 |
|---|---|---|
| **Critical** | ◇ NextWiki 搜索把整篇文档正文塞进列表响应，且谓词不可用索引 | `SearchIndexMapper.xml:62-71`（✅ 亲验：SELECT 含 `content`，`content ILIKE '%kw%'`）；GIN 索引 `idx_ydsz_file_search_index_ft_search_name_content` 是 tsvector，**ILIKE 用不上**；`pg_trgm` 已 `CREATE EXTENSION`（`V26.10.01.sql:5`）但**没有 `gin_trgm_ops` 索引**；`ORDER BY CASE WHEN name ILIKE …` 强制全匹配集排序；`countSearchResults`（`:90-111`）**每页重复一次全扫**；`selectAll`（`:55-58`）带 `content` 拉 1000 行。修：trigram GIN + 列表去 `content`（详情端点 + `LEFT(content,400)` 摘要）+ `similarity()` 排序 + 第 2 页起免 COUNT。目标 1M 行 P95<400ms、响应字节 −95%、每查询 seq-scan 2→0 |
| **High** | ✅ **共享 Nacos 配置把业务包日志打在 DEBUG** | `docs/nacos-config/ydsz-common.yml:167` `com.njydsz: DEBUG`（唯一共享文件、无 profile 覆盖），`ydsz-generator-web/.../application.yml:173` 重复；叠加 `log-impl: Slf4jImpl`（`:130`）+ `sql-audit.log-parameters: true`（`:274`，每个绑定参数都格式化）。改 INFO 后预期 CPU −15~30%、分配率 −20~40%、日志量 −90% |
| **High** | ◇ 规则引擎每次访问全表加载 | `DirectDbRuleConfigProvider.java:35,44` `selectList(null)` 后在 Java 里过滤 PUBLISHED/isEnabled，`entityToDto` 物化 27 列；`DecisionTableRepositoryImpl.java:43` 同样。修：谓词下推 + 只取所需列（冷启行数 −80%、冷路径 P95<50ms） |
| **High** | ◇ 仪表盘 `/overview` 为算 `.size()` 拉 3 张全表 | `DashboardServiceImpl.java:76-80` → `DictRepositoryImpl.java:83,178`、`VariableRepositoryImpl.java:92`、`AppInfoRepositoryImpl.java:83` 均为无界 `selectList(null)`；`DashboardController.java:58-60` 每次登录/首页都打。修：单条聚合 `COUNT(*)` + 60s 缓存（4 次全表 → 1 次计数） |
| **High** | ◇ 任务依赖巡检嵌套循环查库 | `DependencyPatrolScanner.java:96` 遍历启用 DAG，`:105-106` 每节点一次 `findByJobKey`，每 10 分钟一轮；`:151` `findAllNormal()` 物化全部 NORMAL 任务只为判一个字符串字段。修：批量 `findByJobKeys(Set)` + 条件 SQL（D×K 次查询 → 2 次） |
| **High** | ◇ Webhook 重试串行阻塞 HTTP + 逐条 UPDATE | `WebhookRetryScanTask.java:115-160` 循环内 `doSend()` 阻塞（`:193` 每请求超时）后单条 `markSuccess/markDead/updateForRetry`；**一个挂死端点会串行化整批**（N×timeout）。修：虚拟线程/Semaphore 并发 + 批量 UPDATE（100 条×10s：~1000s → ~30s） |
| **Medium** | ◇ 循环插入 | `JobLogContentServiceImpl.java:32-40` 逐行 `insert`（每次任务 tick 的写路径）；`VariableServiceImpl.java:554-559` 循环插入 + 逐键缓存失效 + 逐键事件。可抄的正确先例：`GenTemplateRepositoryImpl.java:93-105`（`ExecutorType.BATCH` + 周期 flush）、`reWriteBatchedInserts: true` 已开 |
| **Medium** | ✅ **多级缓存造好了，业务采用 0 次** | `MultiLevelCacheable`/`Aspect`/`Template` 在业务模块引用数 **0**；全仓仅 63 个 `@Cacheable/@CacheEvict`（31 文件），字典/配置/菜单/变量是 **L1-only** → 每个 pod 各自 miss、TTL 到期一起打库。另 ✅ `virtual-by-default: false` + `pools: {}` —— 统一线程池功能配了但**没声明任何池** |
| **Medium** | ◇ `HttpA2aClient` 把阻塞 HTTP 扔进 `ForkJoinPool.commonPool` | `HttpA2aClient.java:131,150` `CompletableFuture.supplyAsync(supplier)` **未传 executor**，包的是阻塞 JSON-RPC；commonPool 只有 `cores-1`，会饿死其他 `parallelStream`/缓存刷新（对照 `EnhancedLoadingCache.java:578`）。修：传模块 I/O executor（现成范式 `AgentFacadeImpl.java:130`） |
| **Medium** | ◇ 启动与 JVM/连接池卫生 | 实测启动：Message 32.4s / Nextwiki 29.8s / Agent 27.9s / System 27.5s / Gateway 25.1s vs UserInfo 9.6s / Workflow 10.7s（**同框架 3 倍离散**）；`Dockerfile:75` 生产镜像仍带 `-Djdk.tracePinnedThreads=short`（项目自己的注释 `:68` 说生产必须去掉），无 `ExitOnOutOfMemoryError`/GC 日志/heap dump；`connection-test-query: SELECT 1`（`:72`）每次借连接多一次往返（JDBC4 `isValid()` 更省）、泄漏检测常驻（`:74`）、`beans,env` 无条件暴露（`:181`，同 S3） |
| **Medium** | ✅ **两处分页上限互相矛盾** | `PageConstants.java:52` `MAX_PAGE_SIZE = 5000` vs 拦截器 `maxLimit = 500`（◇ `ydsz-common.yml:261`）—— 校验放行 5000，拦截器静默钳到 500。二者应对齐到 200，因为本文第 1 项已证明"宽上限 + 大字段"就是 OOM 配方 |

**前端性能**：配置层面确实成熟（✅ 路由 100% 懒加载、`manualChunks`、`target: es2022`、`drop:['console','debugger']`、生产 `sourcemap:'hidden'` + 上传脚本、nginx brotli+gzip+precompressed、Iconify 构建期 + 本地 SVG 池、Web Vitals 预算 `main/.perf-budget.json` LCP 2500/FCP 1800/CLS 0.05/TTFB 800/INP 200 并有 per-PR Playwright 门）。但：◇ **仓库内没有任何 `dist/` 或 `build:analyze` 产物**，预算是否通过**不可验证**（叠加上一条 §5 的路径错配，门禁实为空转）；◇ 虚拟列表/`@tanstack/vue-virtual` 零采用、markdown/diff 无 Web Worker、`shallowRef` 仅 5 文件、`addEventListener` 102 处 vs `removeEventListener` 73 处（kit 的 echarts/SSE 清理是对的，泄漏点在 `YdFileUpload.vue`、`use-chunk-upload.ts`、`use-theme.ts`、`YdSliderTranslateCaptcha.vue`、`use-app-tour.ts`、`use-keyboard-shortcut.ts`，`canary-manager.ts` 有 `setInterval` 无 `clearInterval`）。

---

## 八、前后端用户体验

**基线之上**：`YdErrorBoundary`（9 处）、`YdNetworkStatus` + 重试按钮、离线错误队列、Service Worker、speculation-rules 预取、`not-found/forbidden/internal-error/offline/coming-soon` 五件套齐备、kit 层 a11y 采用 reka 式 `FocusScope`/`RovingFocus`/`DismissableLayer`/`VisuallyHidden`。这套骨架比多数开源 admin 完整。

**最后一公里没接上**：

| 级别 | 问题 | 证据 | 验收目标 |
|---|---|---|---|
| **P1** | ◇ **页面状态容器 0 采用**：`YdPageStatus`（统一 loading/empty/error+retry）在业务侧引用 **0**；**129 个数据列表视图里 54 个（42%）既无 loading 也无 empty/error/retry**（`system-web/views/tenant`、`dict-item`、`message-web/views/message`、`userinfo-web/views/system/role`、`nextwiki-web/views/trash`、`workflow-web/views/task`）；骨架屏仅 1 个 app 视图 | 100% 列表页三态齐备；加 lint 规则禁止裸 `<YdTable>`/`<Grid>` |
| **P1** | ✅ **筛选/分页/排序完全不进 URL**：`useRouteQuery` 0、`router.replace(…query` 0、`route.query` 仅 4 文件 | 刷新/分享/后退不丢状态；≥90% 列表页可用；e2e 深链断言 |
| **P1** | ◇ **无未保存更改守卫**：`onBeforeRouteLeave` 0，表单无 `beforeunload` | 误关不丢数据（e2e 覆盖前 20 个 CRUD 表单） |
| **P0** | ✅ **禁用缩放**：`main/index.html:12` `maximum-scale=1.0,user-scalable=0` | WCAG 1.4.4 全站失败 —— 删这两个指令 → **已落地，见 §十一 B0-8**（9 个入口归一 + `check:head` 门禁，含 minimum-scale 变体） |
| **P1** | ◇ i18n 覆盖虚高：6 语言目录，zh-CN 叶键 2645 / en-US 2598（差 47）/ es·fr·ja·ko 各 **2341（缺 304）**，而 `bash/check-locales.mts:35` **只对 `['zh-CN','en-US']` 设门禁** → 4 种语言无约束；模板可见文本里 **3555 个硬编码中文字符**（agent-web 705、cronjob 479、message 426…）；16 条校验规则文案硬编码中文（如 `cronjob-web/src/views/job/job-form.vue:46-66`）；无 RTL（`dir="rtl"` 0）；`formatDate` 写死 `'YYYY-MM-DD'`（`base/shared/src/utils/date.ts:41`），无 `dayjs/locale` 引入、无 `Intl.NumberFormat` | `check:i18n` 覆盖 6 语言且键集相等；模板可见 CJK 硬编码 = 0；日期/数字 locale-aware |
| **P1** | ◇ 表单细节：服务端错误按 INFO/WARN/ERROR/FATAL 分层 + 每码 `i18nKey` 已生成（`error-codes.generated.ts` 1181 行）**但无字段级回填**（与 §4 的 `fieldErrors` 断链同因）；无"跳到首个错误"；`autocomplete`/`inputmode`/`type="tel|email"` 在全部 app **0 处** | 100% 400/422 渲染到字段并聚焦首个无效项；语义 `type/autocomplete` 全覆盖 |
| **P1** | ✅ **CSP 自堵字体与分析**：CSP 在 B0-5 后单一来源为 `bash/deploy/security-headers.conf:17`，其中 `font-src 'self' data:` + `style-src 'self' 'unsafe-inline'` 拦掉 `main/index.html:26` 的 `fonts.googleapis.com` 样式与 `:19` 的 `fonts.gstatic.com` 字体（后者本身也是境内延迟 SPOF）；`script-src 'self' 'strict-dynamic'` 拦掉 `:43` 的内联百度统计（8 个子应用 `apps/*-web/index.html:17` 同写法） | 首屏 0 CSP 违规；字体自托管子集化；FCP ≤1800ms → **已落地，见 §十一 B0-8**（三段死代码/无效探测移除 + 恢复路径写进注释；`rel=preconnect/dns-prefetch` 不受 CSP 约束这一事实已写入门禁消息） |
| **P1** | ✅ **壳层无启动画面**：`main/index.html` body 仅 `<div id="app">`，importmap+shims+entry 执行期间纯白 | 首绘 ≤1000ms 且有 skeleton/boot splash + entry `modulepreload` |
| **P2** | ◇ 响应式与桌面能力：`@media` 仅 3 处（apps+main+comm），`mobile-bridge` 只有 1 个消费者；无打印样式；无 browserslist；表格列持久化未找到；批量选择全站 5 处；防抖搜索 10 个 app 里只有 1 个 | 断点体系 + 列配置持久化 + 批量栏（`YdBulkActions`）≥80% 列表页 |
| **P2** | ◇ 质量门禁打在 fixture 上：axe 只扫 `tests/a11y/fixtures/`（`page-a11y.spec.ts` 的 CORE_PAGES = login/dashboard/list），**从不扫真实子应用页面**；ADR-005 自己承认失去了运行时扫描能力；`vitest.config.ts:52` 覆盖率阈值 25% | 真实运行页面 axe critical=0（≥5 页）；阈值 25→60% |

---

## 九、路线图（按"先止血、再通链、后提效"排）

**批次 0（≤1 周，发布阻断 + 安全，全部为小改动高收益）**
S1 凭据轮换与出库 · S2 JWT 密钥占位符化 + fail-fast · S3 Actuator 白名单/暴露面收敛 · S4 去 Windows 绝对路径 · S5 子应用 base 路径单一来源 · S6 nginx 缓存分层 · S7 令牌 CSS 接入 · S8 CI 脚本名/Node 参数修复 · CSP 字体与分析 · `user-scalable` 移除。
*验收*：`mvn -o verify` 与 `pnpm build && pnpm check:size` 在 Linux runner 上通过；`curl -I` 证明 `index.html` 为 `no-store`、hash 资源为 `immutable`；10/10 产物无未解析 `--ydsz-*`；首屏 0 CSP 违规。

**批次 1（2–6 周，让"红线"变成"门禁"）**
接入 Flyway（含 `noTransaction=true` 处理 11 处 CONCURRENTLY + 版本空洞）· ~~覆盖率改分模块棘轮 + `diff-cover` 增量 40%~~（**已完成**：21 模块实测棘轮 + 自研 `check-diff-coverage.py` 16/16 自检 + checkstyle 增量门禁 19/19 自检，见 §十一 B1-2；顺带发现 CI Step 4 `mvn checkstyle:check` 恒红遮断下游全部检查，已换口径）· 补反例断言（userinfo RBAC 15 端点 × ≥3）· 契约测试真正生成并执行 · 生成器输出测试模板 · 前端 `vue-tsc` 进 main+9 app · ~~turbo `globalDependencies` 加 `comm/**`~~（**已实测证伪并从本批次移除**，见 §十一 B1-7）· 体积预算路径修正 · 视觉基线提交 + e2e 进 CI · `gen:nginx:check`。
*验收*：故意改一个 DTO 字段 / 漏一个 i18n key / 引入一次 N+1，CI 必须有作业变红 —— 这是"门禁有效"的唯一判据。

**批次 2（6–10 周，贯通链路与数据治理）**
OpenAPI artifact 化（`ydsz-{module}-openapi:{git-sha}`）· codegen 单轨收敛 · 网关路由清单反向校验 · `extensions.fields` 字段级错误贯通 · 错误码+i18n 文案同源 · DB 治理包（tenant_id NOT NULL 30 表 / 软删 partial unique 77 / 租户前导唯一 64 / 186 连接列索引 / 删冗余索引）· WS 一次性 ticket · traceId 响应侧回传。
*验收*：后端新增端点 → 前端类型可用 → 网关有路由，三者由同一条 CI 保证；`pg_stat_user_indexes` 显示目标索引被使用。

**批次 3（持续，性能与体验）**
NextWiki 搜索 trigram 化并去 `content` · DEBUG 日志降级 · 规则引擎/仪表盘/巡检/Webhook 四类热点改造 · 循环插入批量化 · 多级缓存落地（字典/配置/菜单/变量）· `HttpA2aClient` executor 修正 · 启动时长与 JVM 参数治理 · 前端 42% 零状态列表页补齐 + URL 状态同步 + 未保存守卫 + i18n 硬编码清零 + 虚拟列表/Worker 引入。
*验收*：搜索 P95<400ms@1M 行；首页 `/overview` P95<50ms；A2A 扇出不再受 `cores-1` 限制；LCP≤2500/INP≤200 在 per-PR 门禁上持续达标。

---

## 十、研发流程治理（防止报告再次失真的机制性建议）

1. **"完成"必须定义成可证伪状态**：建议团队统一为 —— 可被调用（有非自引用消费者）∧ 可被观测（有指标/日志/告警）∧ 可被门禁阻断（有一条 CI 断言）。本次 4 个零接线骨架若按此定义即自动判为未完成。
2. **文档一致性纳入自动检查**：`AGENTS.md` 的 DB 方言（MySQL vs PostgreSQL）、Flyway 自相矛盾（§2.2 要求 / §4 禁用）、`JsonUtils` 幽灵类（11 处引用 / 0 个实现）、前端 README 的 Element Plus、ADR-005 的测试禁令、SkyWalking 配置与依赖缺失 —— 建议加一个 `docs-consistency` CI 作业，把"文档提到的类/配置/依赖是否存在"变成断言。
3. **减少副本数量级**：4 套 schema 副本、11 份 `CacheKeyBuilder`、35 个限流类、4 套幂等、5 套 ID 生成、3 套 IP ACL、3 套表格库、9 份 App 骨架薄 shim、`data/scripts/backup_logs/` 整份源码副本（435 文件）—— 每一项都已在制造漂移。**收敛比新增更有价值。**
4. **删除"占位返回假数据"的实现**（Process Mining 类）：宁缺毋假。
5. **安全项走独立通道**：S1 已进入 git 历史，需按凭据泄露事件处理（轮换 + 历史清除 + 审计访问日志），不要作为普通技术债排期。

---

## 十一、批次 0 落地记录（滚动更新）

> 判定口径同 §十.1：改完必须给出**可复核的验证动作**，不以"文件已改"作为完成。

### B0-1 凭据 fail-closed（S1/S2）— 已完成

| 动作 | 落点 | 验证 |
|------|------|------|
| DB 口令去默认值 | `docs/nacos-config/ydsz-common.yml`、`ydsz-generator-web.yml:11`、`ydsz-generator-web/.../application.yml:20` → `${NACOS_DB_PASSWORD:}` / `${NACOS_GEN_DB_PASSWORD:}` | 空值时 HikariCP 认证失败即启动失败，不再静默用泄露凭据连库 |
| JWT / 内部签名密钥占位符化 | `ydsz-gateway/.../application.yml`（`${YDSZ_JWT_SECRET:}`、`${YDSZ_GATEWAY_INTERNAL_SIGN_SECRET:}`）+ 4 份 `config/ydsz-gateway-{dev,sit,uat,prod}.yaml`；`ydsz-userinfo-web/.../application-dev.yml` 明文 dev 密钥删除 | 网关新增 `AuthGlobalFilter#validateSecret()`（`@PostConstruct`）：空值/短于 32 字节直接抛 `IllegalStateException`；与 JWT 密钥同值时 `log.warn` 提示域分离。实测启动日志 `内部头签名密钥已加载, source=config-file, length=48`，7.2s 起来 |
| 本地凭据改为未入库 override | 新增 `docs/nacos-config/ydsz-config.local.yml.example`（入库模板，值全为 `""`）；真实 `ydsz-config.local.yml` 由本机生成、`chmod 600`、经 `.gitignore:31 *.local.*` 排除；`.gitignore` 补 `!docs/nacos-config/*.local.yml.example` 例外 | `git check-ignore` 复核：local 文件不入库、模板入库 |
| 顺带纠正的隐性配置漂移 | 网关与 userinfo 原各自持有**不同的**已提交 JWT 密钥 → 本地 dev 下 userinfo 签发的 token 网关验不过。现两处同读 `${YDSZ_JWT_SECRET:}` | 密钥单一来源，文档已写明 |
| 文档 | `docs/本地开发环境.md` 全量重写：无明文口令、一次性 `cp` + `openssl rand -base64 48`、MySQL 标注遗留、`YDSZ_NACOS_CONFIG_DIR` 说明 | — |
| 移交项（非本 Agent 权限） | ①已入库口令**轮换**；②`git filter-repo` 清历史；③访问日志审计 | 属凭据泄露事件处置，需持有者/运维执行，见 §十.5 |

### B0-3 去 Windows 绝对路径（S4）— 已完成

22 处 `file:D:/Code/open/ydsz-cloud/docs/nacos-config/…` 全部改为
`optional:file:${YDSZ_NACOS_CONFIG_DIR:../docs/nacos-config}/…`（网关用 `../`，9 个嵌套 `*-web` 用 `../../`），并追加本地凭据 override 的导入。`grep -c` 复核残留 **0**。
> 设计要点：占位符默认值留空 + 本地 override 文件提供值 → **不依赖 import 顺序**（根级大写 key 与环境变量等价），Linux/Docker/CI 首次可启动。

### B0-2 Actuator 暴露面收敛（S3）— 已完成（含 1 个新发现缺陷）

收敛动作：
- 四源统一为 `health,info,metrics,prometheus,cache-metrics`，全部剔除 `beans,env`；
- `config/ydsz-gateway-{dev,sit,uat,prod}.yaml`：`endpoint.gateway.enabled` → `false`，include 去掉 `gateway`；
- `show-details`：`always` → `${YDSZ_HEALTH_SHOW_DETAILS:when-authorized}`（生产默认收紧，本地排障显式开启）；
- 复核：全仓 `show-details: always` 计数 **0**、include 含 `beans|env|configprops|heapdump|threaddump|loggers|gateway|shutdown` 计数 **0**（`data/scripts/backup_logs/` 内的整份源码副本除外，见下）。

**新发现并已修**：WebFlux 侧未命中路径 / 未暴露端点返回 **HTTP 500 + `B01052`**，而非 404。根因是 `WebFluxExceptionHandler` 缺 `ResponseStatusException` 处理器，`DispatcherHandler` 抛出的 `NoResourceFoundException`（其父类即 `ResponseStatusException`，已用 javap 核实）落到兜底 `Exception` 分支 → `SYSTEM_ERROR`。MVC 侧 `MvcExceptionHandler:403` 本就有 `NoHandlerFoundException` → 404 `A04054` 的正确映射，两侧行为不对齐。
修复：`ydsz-common-exception/.../WebFluxExceptionHandler.java` 新增 `handleResponseStatusException` + `mapResponseStatus`（按状态码映射 `RESOURCE_NOT_FOUND`/`METHOD_NOT_ALLOWED`/`CONFLICT`/`FILE_SIZE_EXCEEDED`/`RESOURCE_EXHAUSTED`/`SERVICE_UNAVAILABLE`/`BAD_GATEWAY`/`GATEWAY_TIMEOUT`…，i18n 复用 `resource.not.found.detail`，4xx 走 WARN 且不打栈、5xx 走 ERROR）。
实测（网关 19010 短生命周期实例）：`/actuator/env`、`/actuator/beans`、`/api/nope-xyz`、`/nope` 均 **404 + `A04054` + `接口 /xxx 不存在`**，HTTP 状态与 body 内 `httpStatus` 一致；`/actuator/health`、`/actuator/health/{liveness,readiness}` 均 **200**。
> 附带澄清：堆栈仅在 dev/test profile 下入 body（`ExceptionResponseBuilder` 的 `isDevOrTestProfile()`），**prod/sit/uat 并未泄露 stackTrace**，前次描述不得升级严重度。

**排障副产物（供后续批次）**：
- `/actuator/health` 曾整体 503，元凶是 `notify` 指示器 —— 但源码 `NotifyHealthIndicator:200-213` 已把"全部渠道未启用"判为 UP，**本机 Maven 仓库里装的是旧 jar** 才复现 DOWN。结论：这是**本地制品陈旧**而非线上缺陷；同时提示 `docs/本地开发环境.md` 应写明"改 common 后需 `mvn install` 对应模块"。
- `discoveryComposite` / `lock` 报 UNKNOWN 源于 `bootstrap.yml` 中 `nacos.discovery.enabled: false`（网关按 IP 直连）；UNKNOWN 不会拉低复合状态，暂不处理。

### B0-4 子应用 base 路径单一来源（S5）— 已完成（配置级），产物级验证被 B0-10 阻断

原判定"四套并存"复核后实为 **5 类事实源、6 个口径值**，且**没有任何注册项声明 `prodPath`**，因此各处回退字面量就是线上生效值：

| 事实源 | 修改前 | 修改后 |
|--------|--------|--------|
| `comm/constants/src/micro-apps.ts:203` `getProdEntry()` | 回退 `/ydsz-${name}/`（小写，正确） | 新增 `SUB_APP_BASE_PREFIX` + `defaultProdPath()`，`getProdEntry` 复用之；纠正 2 处仍写 `/YDSZ-` 的文档注释 |
| `comm/effects/micro-kernel/src/registry-adapter.ts:275`（基座运行时 entry） | `/YDSZ-${name}/` ❌ | `/ydsz-${name}/` + 指向单一来源的注释 |
| `comm/effects/micro-kernel/src/speculation-rules.ts:61` | `/YDSZ-${name}/` ❌ | 同上 |
| `bash/gen-registry.mts:82`、`bash/gen-nginx-conf.mts:90` | `/YDSZ-${name}/` ❌ | `/ydsz-${name}/`（Node 侧无法 import TS，保留字面量但由门禁锁死） |
| `conf/vite-config/src/plugin-sw.ts:56` | `/YDSZ-${name去-web}/` ❌（第 5 种变体） | `/ydsz-${appName}/`；**并发现 swPlugin 全仓零调用**（PWA 缓存层从未接线） |
| `conf/vite-config/src/config/application.ts`（构建 base） | 子应用 `vite.config.mts` 写死 `base:'/'`，压掉 env → 产物发根路径 | 生产构建按 `readSubAppName()` 强制 `base=/ydsz-{name}-web/`（dev 与 main-web 不变），冲突时 warn |
| `apps/*/.env.production` `VITE_BASE` | `/{name}-web/` ❌（且被 `base:'/'` 覆盖，形同死配置） | 9 个文件改为 `/ydsz-{name}-web/`（每文件仅 1 行 diff） |
| `bash/deploy/Dockerfile` | 只 COPY 8 个子应用，**漏 `generator-web`** | 补齐 COPY |
| 新增门禁 `bash/check-subapp-base.mts`（`pnpm check:base`） | — | 断言 canonical 前缀、6 个回退点无大写 `/YDSZ-`、`VITE_BASE`、Dockerfile COPY、nginx location、（若已构建）`dist/index.html` 资源前缀 |

验证：`node bash/check-subapp-base.mts` 修复前报 **10 项失败**（9 个 VITE_BASE + 1 个 Dockerfile 缺 COPY），修复后 ✅；`gen-nginx-conf.mts --check` ✅（此前生成器出大写、已提交片段是小写，检查必然红）；`tsc --noEmit` 对改动文件除既有 `@ydsz/micro-kernel` 类型解析报错外无新增错误；顺带修掉 `application.ts` 里 `getAvailableStrategies` 未导入（一旦某 app 声明非法 `shareStrategy` 就 ReferenceError 的潜伏 Bug）。
**未完成部分（诚实标注）**：子应用生产构建在 HEAD 即失败（`lucide-vue-next` 未被子应用声明，Rollup 解析期报错，先于产物生成，与本次 base 改动无关）→ 门禁第 6 项"产物 index.html 前缀"当时无法生效；**已在 B0-10 修复后补齐**——`check:base` 现实测覆盖 9 个子应用的已构建产物。

### B0-5 nginx 静态缓存与安全头分层（S6）— 已完成（语法验证受限于本地无 nginx）

**改前四类缺陷**（`bash/deploy/nginx.conf`，均为"看一眼看不出"的那类）：

| # | 缺陷 | 机理 | 后果 |
|---|------|------|------|
| 1 | server 级正则 `location ~* \.(?:js\|css\|woff2?\|svg\|png…)$` | nginx 在"最佳前缀匹配块**不含嵌套 location**"时让 server 级正则胜出，于是它同时劫持主应用 `location /`、9 个子应用前缀块，甚至 `/api/`、`/ws/` 的静态化请求 | 该块只有 `expires 1y` 而无 `root/alias`，路径推导与应用目录错位；命中即拿不到应用级语义 |
| 2 | 同一正则无差别 `max-age=31536000, immutable` | 与"入口 HTML 必须协商"冲突；主应用 `location /` 又对**一切**（含 hash JS）下发 `no-store` | 缓存策略两头都错：该长存的每次回源，不该长存的存一年 |
| 3 | 块内声明了自有 `add_header` | `add_header` **不跨层继承**：当前层一旦声明，上层全部失效 | CSP、X-Frame-Options、nosniff、Referrer-Policy、Permissions-Policy、HSTS 在静态资源响应上静默消失 |
| 4 | `add_header X-CDN-Cache "HIT"` / `CDN-Cache-Status "HIT"` | 硬编码，与真实命中无关 | 误导性观测数据（排障时首先怀疑错的地方） |

**落地（6 文件 + 1 新门禁）**：

| 文件 | 变更 |
|------|------|
| `ydsz-micro/bash/deploy/security-headers.conf`（新增） | 安全头单一来源；删除已废弃的 `X-XSS-Protection` 与空值头 `X-CSRF-Token ""`；注释写明"为何不能只放 http 层" |
| `ydsz-micro/bash/deploy/nginx.conf:65` | http 层安全头改为 `include`；`:108` `location /` 改为分层块（HTML no-store / JS·CSS·WASM immutable+`=404` / 图片字体 30d / `.map` 404）；删除 2 条 server 级资源正则（原位留注释说明为何不得恢复）；`/health` 用 `default_type` 取代 `add_header Content-Type`（后者会连带丢安全头且与响应头重复） |
| `ydsz-micro/bash/gen-nginx-conf.mts:99` `generateNginxSnippet()` | `alias` → `root /usr/share/nginx/html`（`alias` 在带嵌套 location 的块中语义不可靠）；输出三层缓存 + 嵌套 `.map` 404 + `try_files $uri =404` |
| `ydsz-micro/bash/deploy/nginx-sub-apps.conf` | 由生成器重出（9 个应用 × 4 块） |
| `ydsz-micro/bash/deploy/Dockerfile:59` | 新增 `COPY bash/deploy/security-headers.conf`（漏拷则 include 失败，容器起不来） |
| `ydsz-micro/package.json:36` | 新增 `check:nginx`、`check:nginx:self-test` |
| `ydsz-micro/bash/check-nginx-cache.mts`（新增） | 花括号配对解析 location 块与嵌套深度，校验 7 项不变量：I1 声明 `add_header` 必须 include 安全头；I2 一年缓存只允许出现在扩展名正则块且必须 `=404`；I3 HTML 兜底块必须 no-store 且无正数 max-age；I4 `.map` 必须 404；I5 禁止 `X-CDN-Cache`；I6 禁止 `expires`；I7 server 最外层禁止保留带 Cache-Control 的扩展名正则块 |

**门禁自检（本项的核心增量）**：`--self-test` 用"修复前配置形态 + 1 条人为 `.map` 违规块"的临时夹具跑同一套检查，断言 8 类不变量**逐一命中**，命中不到即 exit 1。当前结果：反例检出 19 条缺陷、8/8 命中；真实配置 ✅ 通过。加这一步的原因见 §十——本仓已有若干"能跑但永不失败"的门禁，新写的检查若不证伪就等于没写。

**未验证项（如实标注）**：本机无 nginx 二进制，`nginx -t` 未执行，嵌套 location / include 的**语法**正确性需在镜像构建或 CI 阶段确认；本脚本只保证**结构不变量**。解析器已处理 `return 200 '{"status":"UP"}'` 这类引号内成对花括号。

**移交**：① CSP 与页面实际资源自相矛盾（`main/index.html:26` 引 Google Fonts、`:43` 内联百度统计脚本，在 `script-src 'self' 'strict-dynamic'` 与 `style-src 'self'` 下**都会被浏览器拦截**，即生产上这两段本就是死的）→ 归入 **B0-8**；② `check:base`/`check:nginx`/`gen:nginx:check` 尚未接入 CI，`.github/workflows/ci.yml` 目前只有 `pnpm check:size` 一处（`:310`）→ 归入 **B0-7**。

### B0-6 设计令牌 CSS 接入（S7）— 已完成（浏览器渲染复核待做）

**根因是两层叠加，单独看任何一层都像"没问题"**：

| 层 | 事实 | 为什么隐蔽 |
|----|------|-----------|
| 1 无引用 | `@ydsz-core/design-tokens/src/styles/variables.css` 定义了 116 个 `--ydsz-*` 令牌，但全仓只有 `ydsz-ui` 的 playground 引过它 | 组件里到处是 `var(--ydsz-x, 兜底值)`，缺定义时**静默落到兜底值**，页面照常渲染，只是主题/暗色切换对这些组件完全无效 |
| 2 构建外置 | 补上 import 后重新构建，产物 `dist/design.css` 里仍只有 1 个 `--ydsz-` 名 | `conf/vite-config/src/config/library.ts:67` 的 `external` 回调把"声明过的依赖 + `${pkg}/` 子路径"一律外置，**CSS 子路径也被外置** → 只在 `dist/index.mjs` 留一条 `import '…/variables.css'`，而应用按 `exports` 取的是 `dist/design.css` |

**落地（10 文件 + 1 新门禁）**：

| 文件 | 变更 |
|------|------|
| `comm/@core/ui-kit/design-tokens/src/styles/compat-aliases.css`（新增） | 19 条历史命名 → 规范令牌的别名；全部写成 `var()` 间接引用（不复制色值），暗色自动跟随。头部说明 `variables.css` 是 Figma 同步落盘目标、别名不得写进它 |
| `comm/@core/ui-kit/design-tokens/src/styles/variables.css:208` | 暗色覆盖块选择器 `:root[data-theme='dark']` → `:root.dark, [data-theme='dark']`：两条主题链路写入 `data-theme` 的词表不同（`preferences` 写预设名 default/violet/…，`micro-kernel/use-theme-sync` 写 auto/light/dark），**唯一共同信号是 `.dark` 类**，且 `auto` 模式下 `data-theme` 根本对不上 |
| `comm/@core/ui-kit/design-tokens/src/tokens/motion.ts:61` | 新增 `cssVarSuffix()`，`durationVar`/`easingVar` 经 camelCase→kebab 后再插值（门禁抓出的真实缺陷，见下） |
| `comm/@core/ui-kit/design-tokens/src/tokens/transition-utils.ts:54` | `injectTransition().duration/easing` 原本自己又拼了一遍 `var(--ydsz-motion-…)`，与 `motion.ts` 的两个 builder 并存 → 改为委托，消除"同一 token 两条拼法" |
| `comm/@core/ui-kit/design-tokens/package.json` | 补 `./styles/variables.css`、`./styles/compat-aliases.css` 两个 exports 子路径（该包无 build 脚本，`dist` 永不存在，只能按源码子路径暴露） |
| `comm/@core/base/design/package.json` | 声明 `@ydsz-core/design-tokens: workspace:*`（此前跨包 import 属幽灵依赖） |
| `comm/@core/base/design/src/design-tokens/index.ts:23` | 入口 import 两个令牌 CSS，接线闭合 |
| `conf/vite-config/src/config/library.ts:67` | `external` 回调对 `.css` 一律返回 false，CSS 打进产物样式（根因 2 的修复点，全仓库包共享） |
| `comm/@core/ui-kit/mobile-bridge/src/breakpoints/index.ts:36` | 删除 `BREAKPOINT_CSS_VARS`：`@media (min-width: var(--x))` 在 CSS 中非法、`--ydsz-bp-*` 从未定义、零消费者——这是一个**永远不可能生效的 API**，注释改为给出 `matchMedia` 正确写法；`src/index.ts` 同步去掉导出 |
| `comm/@core/base/design/src/css/global.css:234` | 注释里的 `/views/*/` 提前闭合了块注释（esbuild `css-syntax-error` 告警来源） |
| `package.json:38` | 新增 `check:tokens`、`check:tokens:self-test` |
| `bash/check-design-tokens.mts`（新增） | 见下 |

**产物级证据**：`comm/@core/base/design/dist/design.css` 中 `--ydsz-*` 名字数 **1 → 136**，`--ydsz-comp-bg` 与 `:root.dark` 覆盖块均在；消费链路已核实闭合：`main/src/setup/app.ts:21` + 9 个子应用 `src/main.ts` → `@ydsz/styles`（`comm/styles/src/index.ts:8`）→ `@ydsz-core/design` → 两个令牌 CSS。

**门禁（断言"接线"而不是"文件存在"）**：A 入口 import 两个 CSS 且依赖已声明；B 全仓静态 `var(--ydsz-X)` 必须有定义（注释行不计，docs-site 的局部定义单独并入其定义集）；C **从 token 模块的模板字符串反推** JS 能拼出的变量名并逐个验证——取值集合来自形参类型声明（字面量联合 / `keyof typeof 对象` / 具名别名），支持 `var(${VAR_PREFIX}-…)` 常量展开与 `.toLowerCase()`、camel→kebab 变换，解析不出来则**硬失败**（不接受静默跳过）；D 暗色块必须命中 `.dark`；E 若 `dist/design.css` 存在则必须真的含全部定义（防根因 2 复发）；F 无消费的别名给警告。JS 侧自声明的运行时变量（`densityCSSVars` 拼出的 `--ydsz-density-*`）计入**定义**而非消费。

`--self-test` 结果：反例命中 **4/4** 类缺陷（入口未 import / 未定义令牌 / 模板拼出名缺定义 / 类型枚举不出来），并含 2 条正向断言（常量前缀确实展开、JS 自声明不被误判）；真实配置 ✅ 通过（140 个定义覆盖 92 个静态消费名 + 100 个 JS 模板拼出名）。

**门禁当场抓出的真实缺陷**：`EASING_TOKENS` 的 key 是 camelCase（`easeIn`/`easeOut`），`easingVar()` 直接插值 → 运行期拼出 `var(--ydsz-motion-easing-easeIn)`，而 CSS 定义是 `--ydsz-motion-easing-ease-in` → 所有走 `easingVar('easeIn')` 的过渡取空值、静默失效。已按上面两行修掉（builder 归一 + 委托）。

**未验证项（如实标注）**：未启动 dev server 做浏览器渲染复核（颜色/暗色/密度切换的视觉确认）；本轮只做到"定义进入产物 + 消费名全部有定义"这一层。`pnpm gen:api:check` 目前因 i18n 缺键失败，与本项无关 → 见 B0-7。

### B0-7 CI 接线 + 契约/错误码门禁（S8）— 已完成（一次性写盘再生待人工复核）

**缺陷分三层，越往下越难看见**：

| 层 | 缺陷 | 为什么一直没暴露 |
|----|------|------------------|
| L1 引用漂移 | `.github/workflows/ci.yml` 两处仍调 `bash/gen-error-codes.mjs`、`bash/contract-notify.mjs`，而脚本早已改名 `.mts` | contract job 长期 `Cannot find module`，团队把 job 红当噪声，真需要卡的契约漂移反而没人看 |
| L2 新门禁自己漏扫 | 逐行解析器把 `run: \|` 判成"内容为 `\|` 的行内命令"（`\|` 属 `\S`），块体缩进行全部逃出扫描 | 修好后扫描命令 **43 → 74 条**，立刻抓出 L3 里三条 performance job 的缺陷（它们全写在 `run: \|` 块里） |
| L3 被引用的东西本身坏了 | ① `bash/check-performance-regression.py` **根本不存在**；② 该 job 不带 `--config` 跑 `tests/performance/*`，拿到的是 a11y 配置（`testDir: tests/a11y` + webServer 起 4310 的 fixture 页）→ 要么过滤成 0 条用例，要么测的是 fixture 而非 `main/dist`；③ 基线步骤 `git show main:… \|\| echo "{}"`：PR 检出的是 `refs/pull/N/merge`，本地 `main` 常不存在 → 空基线，下游无阈值可比却照样"通过" | ① 由 L2 遮蔽；②③ 即使 job 跑绿也不产生任何断言 |

**契约 / 错误码生成器本身（事故驱动，逐条已修）**：

| # | 缺陷 | 后果 / 证据 |
|---|------|-------------|
| 1 | `args = [a for a in argv if not a.startswith('--')]` —— 未知 flag 静默丢弃，而"无 flag 即写盘" | **已发生事故**：一次 `python bash/gen-contract.py --help` 改写 41 个生成文件（16202 行）后才崩溃；已 `git checkout` 全量还原（diff 备查：`%TEMP%\ydsz-accidental-contract-regen.patch`，27920 行）。现 `parse_cli()` 白名单 flag，未知即 exit 2 并打印"本脚本默认写盘，参数不确定时绝不猜" |
| 2 | `form_params` 存的是渲染好的 TS 字段**字符串**，FormData 组装循环却按 dict 取 `p["name"]` | 任何"multipart + 普通表单字段"的服务直接 `TypeError: string indices must be integers`（userinfo 命中）→ 整个 contract job 无法跑完，`--check` 的失败提示"请执行 pnpm gen:contract"把使用者推向一条必崩的命令 |
| 3 | 上传函数只把 query 放进签名（`sig_parts` 从不含 form） | 产物函数体引用 `params?.xxx` 而 `params` 从未声明 → 生成的 SDK 不可编译 |
| 4 | 无注解参数一律默认 `kind="query"` | `HttpServletRequest`/`HttpServletResponse` 等**容器注入参数进入客户端契约**，基线里还留下这两个 schema；已用 `SERVER_INJECTED_TYPES` 跳过（`gen-contract.py:826` 上方） |
| 5 | `CLOUD_ROOT` 默认本机 `D:\Code\open\ydsz-cloud`；服务目录不存在时 `continue` 而不失败 | CI 忘设 `YDSZ_CLOUD_ROOT` → 8 个服务全部跳过、`--check` 退出 0：**契约门禁空跑且假绿**。现 `processed == 0` 即 exit 1 并打印实际 CLOUD_ROOT |
| 6 | 文件头 docstring 里 `@path bash\gen-contract.py` 的 `\g` | `SyntaxWarning`（Python 3.12+ 升级为错误）；docstring 改 raw |
| 7 | `--check` 失败只说"漂移了" | 现输出结构化差异清单（接口 ±、数据模型 ±、字段级差异给首个不同行号）+ 单服务重生成命令 |
| 8 | `gen-error-codes.mts` 的 `collectJavaFiles` 无根约束 + `readdirSync` 不排序 + 去重"先扫到者胜" | `data/scripts/backup_logs/ydsz-message-20261010_180452/` 里的**旧树快照同样带 `@YdszExceptionCode`**，与活代码一起扫 → 产物内容与文件系统枚举顺序相关（跨机器 hash 可能不同），甚至可能由备份生成。现顶层只进 `ydsz-/ydzs-` 模块、跳过 `src/test`、目录名排序，重复码保留/忽略**打警告**并显示两侧消息 |

**落地（13 文件）**：`bash/check-ci.mts`（新增，反例自检 7/7）、`bash/check-performance-regression.py`（新增，`--self-test` 8/8 正反例）、`playwright.perf.config.ts`（新增，testDir=tests/performance + webServer 起 `vite preview` 的 `main/dist`）、`tests/performance/web-vitals.spec.ts`（阈值改读 `main/.perf-budget.json`，消除与 Python 门的双源；指标写出路径改由 `configFile` 定位，不再依赖 `outputDir` 层级）、`.github/workflows/ci.yml`（`.mjs`→`.mts` 两处 + 新 `wiring` job）、`.github/workflows/performance.yml`（`pnpm test:perf` + 基线取不到即红）、`package.json`（`check:ci*`、`test:perf`）、`bash/gen-contract.py`、`bash/gen-error-codes.mts`。

**为什么新开 `wiring` job 而不并进 `verify`**：`pnpm lint` 目前**完全无法运行**（`eslint.config.mjs` 加载即 `SyntaxError: Unexpected token '.'`，且 `@ydsz/eslint-config` 需先构建出 dist），verify 仍是红的；把新门禁塞进一个已知会红的 job 等于再次制造噪声。

**当前门禁状态（逐条实测）**：`check:ci` ✅（9 job / 74 条 run 命令；B0-8 接线后为 9 job / 76 条）· `check:ci:self-test` ✅ 7/7 · `check:base` ✅ · `gen:nginx:check` ✅ · `check:nginx` ✅ · `check:nginx:self-test` ✅ 8/8 · `check:tokens` ✅ · `check:tokens:self-test` ✅ 4/4 · `python3 bash/check-performance-regression.py --self-test` ✅ 8/8 · `gen:error-codes --check` ❌ 1（真实漂移，待再生） · `gen-contract --check` ❌ 8 服务全部报漂移（生成器修正 + 基线陈旧）· `check:i18n` ❌（5 个 locale 文件 82 条缺键）。

**契约报红的构成（不是回归，是首次真跑）**：8 服务端到端解析全部成功（1107 endpoints / 528 schemas，此前 userinfo 崩溃），漂移 = 本次 servlet/schema 污染清除 + 基线本身陈旧（system 单服务就有 24 个后端接口从未进入基线，如 `GET /feature-flag/page`、`DELETE /file/multipart/{uploadId}`）。**修复动作是一次可复核的全量再生成**（`python bash/gen-contract.py`，写盘）——我没有执行：它一次触及 40+ 生成文件，且写盘产物需人工审阅（`servletRequest` 之类缺陷正是靠审阅发现的）。

**顺带抓出的后端真缺陷（新发现，需决策后再改）**：错误码命名空间冲突。`SystemExceptionCode.java:17-24` 自称拥有 B91–B96 段，而 message/cronjob/literule/agent 四个模块在 B91001/B92001/B93001/B94001… 上各自定义了完全不同的含义；`UserInfoExceptionCode.java:44` 与 `:110` 在同一条枚举里两次使用 `B30028`；`A00000` 在 `YdszResultCode` 与 `CoreExceptionCode` 各定义一次。产物级后果已核实：`comm/effects/request/src/error-codes.generated.ts:1174` 把 `B91001` 绑到 message 的 `TEMPLATE_NOT_FOUND`，system 的 `DICT_TYPE_NOT_FOUND`（同码）**根本没进产物** → 前端"字典类型不存在"会显示成 "Template not found"。归入 **B1-12**（改号涉及既有客户端兼容，须使用者拍板）。

**诚实清单（本项未修 / 未验证）**：`pnpm lint` 仍不可用；`check:i18n` 仍红（含 `cepPushTest`/`cepPushPush` 这种两侧互缺、疑似拼写反了的键，需作者本意）；`format:check` 3309 个文件不一致 + 6 个文件解析失败，且 `data` 侧 `.dev-logs/`（203 个临时脚本）已入库并被 prettier 扫描 → 三者只能进 MANUAL_ONLY 并附理由（不是"以后再说"，`check:ci` 会持续提醒直到接线）；`gen:contract --check` 在 CI 的真实通过与否，取决于后端仓库 main 与前端基线的实际差距，本地这一轮只能证明"脚本能跑完、会失败、失败可读"。

### B0-8 viewport 禁缩放 + CSP 自堵（§八 P0/P1 两行）— 已完成（浏览器视觉复核待做）

**改前的真实状态（三层同时坏，且互相掩盖）**：

| 层 | 证据 | 后果 |
|----|------|------|
| 可访问性 | `main/index.html:12` 与 8 个子应用 `apps/*-web/index.html:12` 全部写 `minimum-scale=1.0,maximum-scale=1.0,user-scalable=0` | 低视力用户无法双指放大，违反 WCAG 2.1 AA 1.4.4（Resize Text）；9/9 入口一致 → 不是笔误，是模板级缺陷 |
| CSP 自堵 | `bash/deploy/security-headers.conf:17` 的 `script-src 'self' 'strict-dynamic'`、`style-src 'self' 'unsafe-inline'`、`font-src 'self' data:` vs `main/index.html:26` 的 Google Fonts 跨域样式表、`main/index.html:43` + 8 子应用 `:17` 的内联百度统计 | 这两段在生产**一直是死的**（浏览器直接拦），只在无 CSP 的 dev server 里跑 → "我们有站点统计"的错觉长期存在，实际一条数据都没上报 |
| 无效开销 | `main/index.html:18-19`（fonts 两域）、`:41-42`（hm 的 preconnect + dns-prefetch） | `rel=preconnect` / `dns-prefetch` **不受 CSP 约束**，第三方 DNS+TCP+TLS 探测照发；隐私与首屏竞争带宽全在，收益为零 |

**判定与取舍**：`_hmt` 在全仓无任何写入方（grep 命中 0），删除内联脚本不会留下悬空调用。第三方字体不改为自托管——`main/index.html:21-32` 已说明源字体尚未采购、产物不得 preload，且 `comm/@core/base/design/src/css/global.css:39` 的 `--ydsz-font-family: 'YDSZ Sans', 'Inter', system-ui` 本来就带系统回退；因此本项**只删掉"CSP 已拦掉的死代码"，把恢复路径写进注释**（统计改第一方外链 + 在 CSP 里显式放行分析域；字体改自托管 woff2），是否恢复由使用方决策，不由门禁替使用者拍板。附带收益：dev 与 prod 的字体表现从此一致（此前 dev 用 Inter、prod 静默回退）。

**待办项里那句"自堵是否也拦掉自有资源"已核实为否**：微前端入口走的是 `dynamic import()`（`comm/effects/micro-kernel/src/loader.ts:417` 附近，fetch 仅用于 sha256 验签，执行仍是 import），micro-kernel 全目录 grep `eval(` / `new Function` 命中 **0** → 现 CSP 不需要 `'unsafe-eval'`；子应用资源与主应用同源（B0-5 的 nginx 路径前缀布局）→ `script-src 'self'` 放行；运行时注入样式由 `style-src 'unsafe-inline'` 覆盖；跨域图片/接口分别由 `img-src … https:`、`connect-src 'self' https: wss:` 覆盖。**结论：CSP 拦掉的只有第三方字体与内联统计两类，自有链路无损失**，因此 H6 把"不得出现 'unsafe-eval'"作为前提断言是安全的。

**落盘（9 个入口 + 1 个新门禁 + 接线）**：`main/index.html`、`apps/{agent,cronjob,literule,message,nextwiki,system,userinfo,workflow}-web/index.html`（`-136 / +117`，viewport 统一为 `width=device-width, initial-scale=1.0`，两处防回归注释）；`bash/check-html-head.mts`（新增）；`package.json` 新增 `check:head` / `check:head:self-test`；`.github/workflows/ci.yml` 的 `wiring` job 新增 2 步。`apps/generator-web/index.html` 未改（本就合规，但纳入门禁扫描范围）。

**门禁断言的是"结构不变量 + 自身前提"，不是"文件存在"**：
- H1 viewport：必须存在、含 `width=device-width`；禁 `user-scalable=no|0`、禁**任何** `maximum-scale`、禁 `minimum-scale>=1`（三种写法都能封死缩放，只拦前两种等于留后门）；
- H2 必须声明字符集（CJK 页面缺 charset 是乱码级缺陷）；H3 `<title>` 非空；
- H4 禁无 `src` 的内联 `<script>`；H5 禁绝对/协议相对 URL 的 script src、`rel=stylesheet`、`rel=preconnect|dns-prefetch|prefetch`、`rel=preload as=font|script|style`，每条失败信息都写清"被哪条 CSP 指令拦"或"不受 CSP 约束但仍探测"；
- **H6 校验门禁自己的前提**：`security-headers.conf` 必须真的下发 CSP，且 `script-src` 不含 `'unsafe-inline'`/`'unsafe-eval'`、`style-src`/`font-src` 不放行跨域——若有人为了让内联脚本"复活"而放宽 CSP，这本身就是缺陷，门禁会红而不是失去判断依据；
- H7 `bash/gen-app.mts` 的 `indexHtml` 模板同样受 H1–H5 约束（**提取失败即报错**，不允许静默跳过；模板是缺陷的批量入口）；
- 扫描目录即来源：`main/` + `apps/*/` 下每个含 `index.html` 的目录自动纳入（硬编码列表会在新增子应用时静默漏检），当前 10 个入口；扫到 0 个即失败。
- 注释先剔除再判定——本仓的防回归说明正写着 `maximum-scale=1`，不剔注释就是门禁拦自己。

**证伪证据（两种，都不依赖夹具）**：① `--self-test` 命中 14/14 类缺陷 + 1 条正向断言（合法 head 且只在注释里出现禁用写法 → 0 失败）；② 直接把 `git show HEAD` 的改前文件还原到临时目录跑 `--root`：`main/index.html` 与 `apps/system-web/index.html` 共报 **14 条**、退出码 1，覆盖 H1 三条、H4、H5 四类。改前状态必红，改后全绿——这才是"门禁生效"的判据。

**实测**：`check:head` ✅（10 入口 + CSP 前提 + 脚手架模板）· `check:head:self-test` ✅ 14/14 · `check:ci` ✅（9 job / 76 条 run 命令全部可解析）· `check:ci:self-test` ✅ 7/7 · `check:nginx` / `check:nginx:self-test` ✅ 8/8 · `check:base` ✅ · `check:tokens` ✅。

**顺带发现（不属本项，未改）**：脚手架模板 `bash/gen-app.mts:185` 把 `VITE_APP_STORE_SECURE_KEY=YDSZ-pmis-2026-secure-key` 硬编码进每个新应用的 `.env`（`apps/generator-web/.env:4` 是同一字面量），该值经 `comm/stores/src/setup.ts:88` 作为 secure-ls 的 AES 密钥在构建期注入 bundle——即"密钥"本身不具备秘密性，但**所有应用共用同一字面量**使单点泄露变成全域泄露。归入凭据轨（B0-1 的延续），需使用方决定"每应用独立密钥 + 非入库 override"的具体口径。

**诚实清单（本项未修 / 未验证）**：未启动 dev server 做视觉复核（删掉 Google Fonts 后 dev 端字形回退到 system-ui，行高/字宽会微变，视觉基线属 B1-8）；百度统计"是否要恢复"未替使用方决定，只留下 CSP 兼容的恢复路径；`user-scalable` 修复只证明到"文本层写法正确 + 门禁会拦"这一层，真机上双指缩放是否可用需人工确认。

### B0-9 网关路由双源漂移（prod 404 / 错路由）— 已完成（5 项需使用方决策）

**问题不是"两份路由表不一致"，而是"没有任何东西能证明路由是通的"**：清单、生成块、Controller、注册名、端口、nginx/vite 代理六者各自可以改，改错了也一律显示 404，而 404 又被归因为"前端调错路径"或"服务没起"。

**改前状态（`git show HEAD`，即 89c79acc6e，可逐条复核）**：

| 层 | 证据 | 后果 |
|----|------|------|
| L1 双源漂移 | `routes-nacos.yaml` 53 个 pattern vs `application.yml` 66 个 `Path=` 断言：**只在 manifest 15 条、只在 application.yml 28 条** | 两条加载链路（静态路由 vs `NacosRouteDefinitionRepository`）各自生效，改一处等于没改 |
| L2 死路由 | `Path=/api/notify/**`→`http://localhost:9004`、`/api/job/**`→`9006`、`/api/wiki/**`→`9003` 只存在于 application.yml；后端 `@RequestMapping("/(notify\|job\|wiki)` grep 命中 **0** | 前端按这三条前缀调用永远 404，且服务是"活着"的，日志看不出问题在网关 |
| L3 危险的 manifest 条目 | 只在 manifest 侧有 `/api/v1/monitor/**`（P0-A2 已改 `X-Api-Version` 头协商，网关不该再路由版本段）、`/api/internal/**`（一把抓，抹掉按服务的 StripPrefix 粒度）、`/api/admin/**`（与 userinfo 侧 `/api/admin/{ldap,oauth2,security,sessions,users}/**` 抢注） | 一旦动态路由链路被打开，旧别名与过宽前缀立刻复活并覆盖细粒度路由 |
| L4 网关 5.x 前缀 | `spring-cloud-starter-gateway-server-webflux:5.0.2` 生效前缀是 `spring.cloud.gateway.server.webflux.*`，旧 `spring.cloud.gateway.*` **不再绑定**（yaml 已在 044d2c5fa8 修正）；但 `ydsz-gateway/src/main/resources/META-INF/spring-configuration-metadata.json` 仍声明 5 个旧前缀键，且 jar 自带 metadata 里旧前缀条目数为 0 | 写错前缀的 yaml **静默不生效**，而 IDE 会替这个错误前缀做补全——等于第三个漂移入口 |
| L5 前端双剥 | `bash/deploy/nginx.conf` 的 `location /api/` 内 `rewrite ^/api/(.*)$ /$1`，加上 10 个 `vite.config.mts` 的 `rewrite: (p) => p.replace(/^\/api/, '')`，网关再 `StripPrefix=1` | 后端收到的路径永远没有 `/api`，与 `Path=/api/**` 断言永不匹配 → 全量 404；dev（vite）与 prod（nginx）错得一模一样，所以"本地能复现"反而排除了网关嫌疑 |
| L6 `/api/v1` 残留 | 前端 diff 中 **238 行**含 `api/v1` 被删除：196 处是文件头"路径规范"注释，其余是真实调用字面量与生成器模板 | 注释是 AI 与新人批量产出错误路径的直接原因；本轮实测 196 处注释的再生源头是 `bash/gen-contract.py:1205` 的 SDK 文件头模板（改前写死 `/api/v1/{svc}/**`），已改为 Controller 相对路径 + 说明 `/api` 由 `requestClient` baseURL 注入、版本走 `X-Api-Version` |

**收敛后的单一来源与双向可证**：

| 机制 | 位置 | 断言什么 |
|------|------|---------|
| 唯一手维护清单 | `ydsz-gateway/src/main/resources/routes-nacos.yaml`（20 条 JSON `RouteDefinition`） | 只有这里可以新增/改前缀 |
| 生成块 | `application.yml:42-184` 的 `BEGIN/END GENERATED ROUTES` | `gen-gateway-routes.py --check` 阻断漂移（实测"20 条一致"） |
| 一致性门禁 | `data/scripts/check-gateway-routes.py` R1–R10 | R1 生成块=清单；R2 每个后端路径都有路由认领或登记豁免；R3 无死路由（反向：路由指向的 Controller 必须存在）；R4 `StripPrefix` 只给 `/api/**`；R5 Controller 里不得出现 `/api`；R6 服务注册名；R7 端口；R8 `NacosRouteDefinitionRepository` 真的被装配；R9 前端代理不剥 `/api`、端口与网关一致、全仓（含 `bash/`、`.github/`、`.py`）不得出现 `/api/v1`；R10 nginx 透传给网关的每个前缀都必须被路由认领 |
| Java 解析可信度 | 同一脚本的 `mask_comments` + `parse_controller` + `_split_attributes` | 注释/Javadoc/文本块里的 `@RequestMapping` 不参与判定；只取 `value`/`path`（`params="holdLock"` 不再被当成路径）；`${scim.base-path:/scim/v2}` 占位符默认值保留 |
| 反证历史 | 首版解析器把 Javadoc 当类级映射，产出 **35 条假 `/api/26.10.01/**`** 缺陷；`params` 属性被当路径产出假 `/api/internal/holdLock` | 两类假阳性各由一条 `--self-test` 反例钉住，现 **57/57** 通过 |
| 实测结果 | 20 路由 / 1056 后端路径 / 9 服务（网关 9000），退出码 0，**红 0、⚠️ 3** | 三条告警全部是"需要人决策"而非"待修"，见下方未决项 |

**配置面收口**：`META-INF/spring-configuration-metadata.json` 重写为 6 条 `level=error` 的 deprecation（`spring.cloud.gateway.routes` 等 → `…server.webflux.routes`），把"IDE 会补全一个不绑定的键"换成"IDE 直接报错并给出新键"；`bootstrap.yml:81` 的注释键名由通配写法 `spring.cloud.gateway.*.discovery.locator.enabled` 改为真实键名（通配写法正是错误前缀的来源）；全仓 yaml 复核：live 配置项已无旧前缀，余下 7 处 `spring.cloud.gateway` 命中均为注释文本。

**CI 接线（本项真正的"生效"环节，也是新发现的第 6 层缺陷）**：

| 发现 | 证据 | 处理 |
|------|------|------|
| 后端 `ci.yml` 自 Step 3 起**恒红** | Step 3 调 `data/scripts/fix-file-encoding.py`，Step 4a/4b/4d 调 `check-error-code-i18n.py` / `check-springdoc-coverage.py` / `check-ddl-dialect-sync.py`——四个脚本在两个仓库 `find` 命中 **0**（AGENTS.md 里的 `sync-ai-rules.py` 同样不存在） | 新写 `data/scripts/check-file-encoding.py`（E1 BOM / E2 非 UTF-8 / E3 **入库 blob** 非 LF，W1 文件末尾缺换行只告警；判断依据取 `git ls-files --eol` 的 index 字段，避免把本机 CRLF 当仓库缺陷）；三个伪门禁步骤移除并在原位写明归属（B1-9/B1-10/B1-12） |
| 门禁顺序会让新门禁失去执行机会 | Actions 默认首个失败步骤即终止 job，Step 3 恒红意味着路由门禁写在第 5 步等于没写 | 编码校验与路由门禁排为 Step 3 / 3a，置于 Checkstyle（Step 4）与 `mvn verify`（Step 5）之前 |
| 前端交叉检出的是不存在的仓库 | `repository: njydsz/ydsz-micro`，而 origin 实为自建 Gitea `http://192.168.31.88:6080/ydszopen/ydsz-micro`；`continue-on-error: true` 把恒失败吞成"跳过" → **R9/R10 从未在前端仓库上执行过** | 仓库名改 `ydszopen/ydsz-micro`，注释写明私有交叉仓库需配只读 token（不擅自引用未 provision 的 secret）；未检出时用 `::warning::` 显式打印，且 R9/R10 跳过时结论行不再谎称"通过（R1–R10）"而是"通过（R1–R8；R9/R10 未执行）" |
| 门禁自己的成功文案会撒谎 | `check-gateway-routes.py` 不传 `--frontend-root` 时也打印"✅ 通过（R1–R10）"；`--frontend-root` 指向不存在目录时只打一条 "R9 跳过"（R10 同样没跑） | 改为按实际执行范围输出结论 + 两条独立告警；`bash/deploy/nginx.conf` 缺失时由未捕获 `FileNotFoundError` 改为可读的 R10 判红 |

**实测（本轮全部重跑）**：`gen-gateway-routes.py --check` ✅ 20 条一致 · `check-gateway-routes.py --self-test` ✅ **57/57** · `check-gateway-routes.py --frontend-root ../ydsz-micro` ✅ 退出码 0（红 0 / ⚠️ 3）· 不带前端参数 ✅ 退出码 0 且明确标注 R9/R10 未执行 · `check-file-encoding.py --self-test` ✅ **28/28** · `check-file-encoding.py` ✅ 5928 个跟踪文件，**红 0 / 告警 18**（BOM 与非法 UTF-8 均为 0，index 换行符 5925 条 `i/lf`）· `mvn -o -q -pl ydsz-gateway compile` ✅ 退出码 0 · `python3 -m py_compile bash/gen-contract.py` ✅。

**未决项（需要决策，不由门禁替使用方拍板）**：

1. **`/ws/**` 无人认领**：nginx 把 `/ws/` 原样透传给网关，但清单里没有 WS 路由；6 个服务（agent/cronjob/message/nextwiki/userinfo/workflow-server）经 `ydsz-common-socket` 把 STOMP 端点**全部注册在同一个 `/ws`**（`WebSocketProperties.endpoint` 默认值，前缀 `ydsz.websocket`），按路径无法判定归属；且这些服务的 dev 配置 `ydsz.websocket.enabled: false`，实时链路本就未启用。可选口径：各服务改 `/ws/{service}` 端点后按服务加路由（推荐，能复用 R10 自动核对），或明确"WS 不经网关"并从 nginx 撤掉该 location。
2. **secondary-auth 抢注**：`ydsz-system` 的 `SecondaryAuthController(@RequestMapping("/auth/secondary-auth"))` 与 `ydsz-userinfo` 的 `AuthController(@PostMapping("/secondary-auth")` 等 3 个端点）同名，路由现归 system → userinfo 侧 `secondary-auth`、`secondary-auth/webauthn[/challenge]` 经网关不可达。必须由一侧改名（如 `/auth/2fa/**`），改路由解决不了。
3. **本地文件公网 URL 指向已废弃端口**：`docs/nacos-config/ydsz-common.yml:485` `ydsz.file.domain: http://localhost:9010/files`，而 9010 是网关旧端口（`docs/nacos-config/ydsz-gateway.yml:8` 记有 9010→9000 的统一），`bash/deploy/nginx.conf` 也没有 `location /files`。该值确有读取方（`AbstractFileStorage.java:168` 的 `domain`、`LocalStorage.java:82` 的 `nginxUrl`）→ `type: local` 部署下文件预览/下载链接必失效。两种口径：由 nginx `alias` 到 `YDSZ_LOCAL_FILE_DIR` 并给出公网前缀，或改走网关路由到某个文件读取端点（当前后端只有 `FileMultipartController(/file/multipart)`，**没有任何文件下载 Controller**）。
4. **前端调用而后端不存在的 4 条路径**（R2 抓不到，因为它们是"前端发起、无 Controller"方向）：`comm/effects/request/src/request-client/modules/uploader.ts:26` 秒传检查 `/api/storage/check`；`comm/effects/micro-kernel/src/preload-metrics.ts:22` 指标上报 `/api/monitor/preload-metrics`（`sendBeacon` 静默失败，"有指标"是错觉）；`main/src/api/search.ts:151` 的 `/search/history`（`GlobalSearchController` 只有 `/search` 与 `/search/analytics/hot`，全仓 `@*Mapping("/history")` 命中 agent/generator/nextwiki 三家，均不在 `/search` 下；该函数 `catch` 后返回 `[]`，且在 `main/src` 内**无任何调用方** → 死代码）；`comm/@core/ui-kit/form-designer/src/palette/component-palette.ts:95` 表单上传组件默认 `action: '/api/upload'`（无此路由，属"新表单默认就坏"）。要么补后端端点，要么把这些调用点显式关掉。
5. **`bash/gen-crud-skeleton.mts` 是死生成器**：产物 `import { xClient } from '../client'`，而全仓没有 `client.ts`（B1-5 生成器测试模板轨一并处理）。另 `Gitea 是否启用 Actions`、以及自建 runner 能否拉取 `actions/checkout@v4` / `dorny/test-reporter@v1` 无法匿名核实（该实例 API 需登录）→ 本轮只做到"接进 workflow 文件并按本地实测可跑"。

**诚实清单（本项未修 / 未验证）**：① R1–R10 是**静态接线证明**，没有起网关发真实 HTTP 请求做端到端验证（需 `mvn spring-boot:run` + Nacos 或 `docker compose` 环境）；② `nginx -t` 未在镜像内执行（本机无 nginx），`/api/` 剥层删除后的转发正确性靠 R9/R10 的路径断言与 `check:nginx` 的 8 条反例；③ 前端功能路径字面量改动（search/audit/web-vitals/notification-sse/dict-sse/分片上传 4 处/生成器与脚本模板）未经浏览器复核——本轮改动是 URL 字面量，`pnpm build` 与 R9 能证明"写对了"，不能证明"页面真的通"；④ `/api/v1` 残留仍有 2 处**有意保留**：`bash/gen-contract.py` 已清零，`bash/deploy/nginx.conf:125` 是说明"网关不路由版本段"的注释（`.conf` 不在 R9 的源码后缀集内），`docs/`、历史报告里的引用属历史事实，不清扫。

### B0-10 子应用生产构建失败 — 已完成（`pnpm build --force` 25/25 绿）

**三个独立成因，前两个都被"dev 全绿"掩盖**（dev 走 `development`/`types` 条件，prod 走 `default` 条件；这就是为什么本地一路正常、CI 从未构建子应用也没人发现）：

| 成因 | 证据 | 修法 |
|------|------|------|
| ① 未声明依赖（原判据） | `lucide-vue-next` 等只被 4 个库包声明，`apps/**` 15 个文件直接 import；pnpm 隔离 node_modules 下 Rollup 解析期即报错 | 按包补声明，错误 12 → 0 |
| ② 入口指向"永远不会存在的产物" | `comm/@core/ui-kit/{advanced-table,ai-builder,dashboard,form-designer,mobile-bridge,design-tokens}` 的 `main`/`module`/`exports.*.default` 写 `./dist/*.mjs`，但**包内没有任何 build/stub 脚本** → 实测报 `Failed to resolve entry for package "@ydsz-core/design-tokens"` | 6 包入口改指 `./src/*.ts`（与本仓 `@ydsz-core/ui`、`editor-ui` 的源码直发形态一致）；`files:["dist"]`+`publishConfig` 暂留为警告级债务（发布轨未接通） |
| ③ 库未先构建就跑 app 构建 | 手工单包构建时 `conf/vite-config/dist/**` 是 unbuild `--stub` 的 jiti 代理，jiti + `node:module`/`createRequire` 被打进浏览器包 → `… is not exported by __vite-browser-external` | 统一用 turbo `build`（`dependsOn:["^build"]`）作为 CI/验证入口，不再手跑单包 |

**一并修掉的构建红点**：`apps/agent-web/src/views/memory/index.vue:27` 同一标识符重复 import；`apps/system-web` 三处引用的 `#/composables/use-i18n` 别名文件根本不存在（新增 `src/composables/use-i18n.ts` 转发 `vue-i18n`）；`apps/userinfo-web` 的 `IconPicker.vue` 与 `defineOptions({name:'YdIconPicker'})`/`@path` 头/引用方三方不一致（`git mv` 归一）；`apps/workflow-web` 引了两条**不存在的** `bpmn-js-properties-panel/dist/assets/*.css`（v5 起样式由 `@bpmn-io/properties-panel` 提供，已改指并提为直接 devDep），且 `import bpmnPropertiesPanelModule from 'bpmn-js-properties-panel'` 在 v5 无 default 导出（改具名 `BpmnPropertiesPanelModule`，并把 camunda provider 显式注册进 `getPropertiesProvider()`，否则属性面板按旧注释假设的"已涵盖 camunda 字段"其实不再成立）。

**顺带发现的静默失效**：`conf/vite-config` 的 manifest 注入是 `try { await import('@ydsz/micro-kernel') } catch {}`（`config/application.ts:108-116`），但 `@ydsz/micro-kernel` 根本没在它依赖里 → 这段永远走 catch，子应用 manifest 插件从不注入；已补声明（micro-kernel 无反向依赖，不成环）。同类还有 prettier/stylelint config 的 `build.mts` 用 `typescript`、`ydsz-vue` 故事用 `@iconify/vue` 而未声明。

**新门禁 `bash/check-declared-deps.mts`**（规则 A 依赖声明 + 规则 B 入口可达性，`--self-test` 命中 8/8 错误分级 + 10/10 警告分级 + 4 条正向断言）：规则 B 断言"生产期会被选中的入口必须存在"，同时避开三个冤枉点——本仓 parked-key 约定（`#build`/`//default` 是显式停用，不是缺陷）、通配导出只校验 `*` 前的目录前缀、`types`/`development` 条件缺失降级为警告（不进产物）。已接线 `check:deps` / `check:deps:self-test` 进 `wiring` job。

**实测**：`pnpm build --force --output-logs=new-only` → 25/25 successful、EXIT=0，10 个应用逐个打印 `Bundle Budget 通过`；`pnpm install --frozen-lockfile` ✅（依赖改动已同步进 lockfile）；`check:deps` ✅ 0 错误 / 76 警告；`check:deps:self-test` ✅。

**诚实清单**：① 76 项警告是存量债（测试/故事文件的未声明依赖、`import type` 跨层、`files:["dist"]` 空包形态），已可用 `--strict` 收紧但尚未清零；② `micro-kernel` 的 `viteManifestPlugin` 补依赖后是否真注入，只在源码层确认了调用条件（`readSubAppName()` 认 `@ydsz/*-web`），日志级复核待做；③ CI 侧此前**从不构建子应用**（`bundle` job 只 `build:main`），本轮把该 job 改为构建全部 10 个应用——GHA runner 上的堆上限（4096）与耗时（timeout 已提到 30 分钟）尚未经一次真实 CI 运行验证。

### B1-8（体积轨）预算路径修正 + manualChunks 根因 + 棘轮化 — 已完成（预算与视觉/e2e 两子项除外）

**改前是"双向失灵"，不是"阈值太严"**：构建期 `YDSZ:bundle-budget` 按 scope（main/apps）匹配，把 10/10 应用全判红（预算总产出 512/384KB，实测 792–1858KB），于是所有人绕开它；CI 期 `bash/check-size.mts` 扫 `main/dist/assets` 与 `apps/*/dist/assets`——本仓产物实际在 `dist/js`、`dist/css`、`dist/jse`（`application.ts:124-126` 未设 `assetsDir`），且"目录不存在就 `continue`"，所以它永远扫到 0 个目标却报通过。**一条拦所有人，一条谁也不拦。**

**根因不在阈值数字，在分包策略**：`createManualChunks()` 的 `default` 分支把**所有**未外部化 node_modules 压进单个 `vendor` chunk → workflow-web 首屏必下 chunk gzip **1374KB**（bpmn-js、echarts、编辑器全在一块，且入口静态依赖它，路由级懒加载完全失效）。改为只保留 `@vueuse`/`echarts`/`lodash`/`async-validator`/`@ctrl|@popperjs` 几组"跨路由稳定共享"显式分组，其余交回 Rollup 默认分组（`conf/vite-config/src/config/application.ts:305-310`）。

**实测（gzip KB，同一份产物目录口径）**：

| 应用 | 最大 chunk 改前 → 改后 | 总产出 | JS 文件数 |
|------|----------------------|--------|-----------|
| workflow-web | 1377.3 → **427.6** | 1857.9 | 106 |
| message-web | 794.3 → **349.9** | 1204.0 | 106 |
| literule-web | 666.7 → **350.4** | 1088.5 | 91 |
| main-web | 556.7 → **308.2**（CSS 69.2 → 10.3） | 1053.9 | 144 |
| agent-web | → **400.1** | 1289.7 | 105 |
| cronjob / userinfo / system / nextwiki / generator | 350.7 / 349.9 / 356.0 / 348.9 / 254.1 | 909.6 / 906.5 / 870.4 / 866.8 / 792.7 | 101 / 104 / 85 / 83 / 80 |

拆分不减总量（各应用总产出与改前基本持平），减的是**首屏阻塞体积**：workflow-web 的 bpmn 设计器现在落在独立 chunk `js/bpmn-designer-*.js`，只有进设计器路由才下载。

**预算改棘轮并保持"新应用仍被理想值卡住"**：`conf/budget.config.json` 由 2 条 scope 目标改为 10 条精确 `appDir` 目标 + 1 条 `apps/*` 兜底（兜底沿用原理想值 384/256/96/60，新增应用未显式评审预算即红）；每条留 `baseline`（实测日期 + 四项实测值）证明阈值是棘轮不是拍脑袋，`aspirationGzipKB` 保留理想目标只在日志里提示。阈值 = 实测 + 头寸（总 +8% / chunk +10% / CSS +25% / 文件数 +15%），由 `pnpm size:report` 生成，不手改数字。

**判据单一来源**：阈值读取、目录口径、超限判据全部收进 `conf/vite-config/src/plugins/budget-config.ts`（新增 `expandBudgetTargets` / `measureDistDir` / `budgetViolations` / `suggestRatchet` / 严格 `isBudgetConfig`），构建期插件与 CI 脚本共用——`check:size` 现在直接 import 这份 `.ts`（Node 类型擦除，实测可跑），"两侧不一致"在结构上不可能再发生。插件另加两条：产物有但配置里没这个目录 → **红**（门禁逃逸）；JS 文件数纳入判定（此前只有 CI 侧看得到，两侧判据其实不同）。

**CI 脚本 fail-closed**：`bash/check-size.mts` 重写，CI 下"声明了应用但无产物"和"有目录但零 JS/CSS"都判红（正是旧实现 `continue` 掉的那条路径），本地只告警；`--list` 不断言、`--report` 出重新基线片段、`--self-test` 8 项反例断言。

**证伪证据**：`--self-test` 夹具里 `over-web` 的 chunk 预算 4KB、实际 30KB → 判红；把 `apps/cronjob-web/dist` 移走后 `CI=true pnpm check:size` 退出码 **1** 并点名该目录，恢复后 0；用旧 schema（无 `appDir`）构造的配置被 `isBudgetConfig` 拒绝。**夹具本身也返工过一次**：初版用 `'x'.repeat(9000)` 造"超预算"反例，gzip 后只剩几十字节，断言静默永不触发——现在用 xorshift 高位字节的不可压缩内容，并把"夹具内容确实不可压缩"作为第 8 条断言钉住。

**实测**：`check:size --list` ✅ 10/10 在预算内 · `check:size:self-test` ✅ 8/8 · 构建期 10 个应用逐个 `Bundle Budget 通过` · `check:ci` ✅（9 job / 79 条 run 命令）· `check:base`/`check:head`/`check:tokens`/`check:nginx`/`gen:nginx:check` 全绿。

**诚实清单（本项未修 / 未验证）**：① 理想值（main 512KB / 子应用 384KB 总产出）**尚未达成**，棘轮只是把"永远红"换成"回退即红"，继续瘦身要靠 echarts/monaco/vxe-table 按需与 workflow-web 设计器二次拆分；② B1-8 的另外两个子项——**视觉基线未提交**（`tests/visual/__screenshots__` 缺基线，CI 视觉 job 空跑）与 **e2e 门禁接线**——本轮未动；③ ~~`comm/**` 源码直发包的变更不会使 app 缓存失效，属 B1-7~~ **该前提已被 B1-7 小节实测证伪**：`^build` 的 `<NONEXISTENT>` 节点已按整包目录哈希覆盖，无需 `globalDependencies`；④ 单 chunk 最大 428KB（workflow）仍超理想红线，`maxChunkGzipKB=471` 是"实测+10%"而非目标值。

### B1-7（缓存轨）turbo `globalDependencies` 加 `comm/**` —— 实测证伪，改动已撤回

**原判据（§五 架构债第 3 条、§九 批次 1）**："`comm` 下大量包没有 build 脚本且直接导出 `./src/index.ts`，而 `globalDependencies` 不含 `comm/**` → 改共享源码不会使 app 构建缓存失效（陈旧产物）。" 我按此口径先改了 `turbo.json`（移除只命中 `bash/turbo-run/src`、`bash/vsh/src` 的 `bash/*/src/**` 两条，加入 `comm/**` 的 5 条扩展名），**随后用 A/B 哈希对照把它证伪，改动已全部撤回**。

**证伪机制**：Turbo 2.10.8 会为任务图里"没有该脚本"的工作区依赖照样生成任务节点，`command` 记为 `<NONEXISTENT>`，且该节点的 `inputs` 是**整包目录快照**（不限扩展名）。`build` 的 `dependsOn: ["^build"]` 因此把依赖包的目录哈希带进 app 任务哈希——与 `globalDependencies` 无关。`turbo build --dry-run=json` 实测：

| 节点 | package.json scripts | `command` | `inputs` 实际包含 |
|------|----------------------|-----------|-------------------|
| `@ydsz/request#build` | `null`（无 build） | `<NONEXISTENT>` | `src/index.ts`、`src/cache-adapter.ts`、`src/.error-codes.lock` … 整包 |
| `@ydsz/icons#build` | 无 build | `<NONEXISTENT>` | `src/index.ts`、`src/icons/empty-icon.vue`、`src/svg/*.svg`、`README.md` |
| `@ydsz/locales#build` | 无 build | `<NONEXISTENT>` | 整包，`dependents` 含 main-web 及 6 个 comm 包 |

44 个 `comm` 包中 **34 个无 build 脚本**（源码直发）、10 个有——按原口径这 34 个正是"缓存盲区"，但上表说明它们全部在图中且整目录参与哈希。

**A/B 实测**（同一命令 `npx turbo build --dry-run=json`，只换 `turbo.json`，探针为在 `comm/effects/mock-service/src/index.ts` 末尾追加一行注释；`mock-service` 仅被 generator-web / message-web / system-web 三个应用声明为依赖）：

| 配置 | 被改动的 app 任务哈希 | 结论 |
|------|----------------------|------|
| 现状（`globalDependencies` 不含 `comm/**`） | **3 / 10**（generator-web、message-web、system-web），另 7 个 app 哈希不变 | 精准失效，行为正确 |
| 我的改动（加 `comm/**` 5 条） | **10 / 10** 全部变化 | 全局键无差别命中所有任务 |

`globalDependencies` 命中的文件进入**全局缓存键**，对每个包 × 每个任务生效。因此加入 `comm/**` 不是补漏，而是把"依赖闭包精准失效"换成"任一 comm 文件改动 → 10 个应用的 build/type-check/test 缓存一起作废"，属缓存命中率倒退。**已撤回**：`turbo.json` 与探针文件均回到 HEAD（`git diff --stat -- turbo.json` 为空）。

**顺带排掉该假设唯一可能为真的分支**——若 app 以别名方式引用未声明的工作区包，图边缺失才会真漏。实测 main + 10 apps 源码内 `@ydsz/*` / `@ydsz-core/*` 的 import 包根，与各包 `package.json` 声明比对：**11 / 11 包 UNDECLARED=0**，任务图与真实 import 一致。

**复现命令**：`npx turbo build --dry-run=json` 后读 `tasks[]` 里 `package == "@ydsz/request"` 的 `command`（`<NONEXISTENT>`）与 `inputs`（含 `src/index.ts`）；A/B 对照 = 在 `comm/effects/mock-service/src/index.ts` 末尾追加一行注释，再比较 10 个 `-web` 任务的 `hash` 变化个数（现状 3/10，加 `comm/**` 后 10/10）。

**仍成立的次要点（未动，记录）**：`globalDependencies` 里的 `bash/*/src/**/*.ts`、`bash/*/src/**/*.json` 实际只命中 vendored 的 `bash/turbo-run/src`、`bash/vsh/src`——这两个是 CLI 工具包，其源码不进 app 构建产物，放进全局键同样是无差别失效；只是改动频率极低，本轮不为它产生 diff。

### B1-2 覆盖率/静态检查门禁：把三条"恒红 = 无门禁"改成可证伪的增量门禁

**原判据（§三 测试工程、§九 批次 1）**：CI 有覆盖率门禁与 checkstyle 门禁。实测两条都是**结构性恒红**，而 GitHub Actions 在第一个失败步骤即停止 → 它们不只是自己没用，还遮断了下游所有检查。

**实测证据（2026-10-26）**

| 门禁 | 声称 | 实测 | 后果 |
|------|------|------|------|
| `mvn checkstyle:check -B`（CI Step 4）| 全仓 0 违规 | error 级 **4155** 条 / 68 个模块有违规（85 份报告）| Step 4 恒红 → Step 5 `mvn verify`（单测 + 覆盖率）**从未执行** |
| `jacoco:check` 全局 BUNDLE ≥ **0.10 行 / 0.05 分支** | §14.7 渐进基线 | 21 个可测模块里 **20 个达不到**（最低 workflow-server 行覆盖 0.3%）| 即便 Step 4 变绿，Step 5 也因覆盖率红灯恒红 |
| `mvn verify` 单测 | 全绿 | **132 个失败**（下表）| 与上一条叠加，`verify` 无法作为信号使用 |
| 根 POM `checkstyle.skip=false` | CLAUDE.md/规范 §23.2 称"构建期 validate 强制" | 插件只在 `pluginManagement`，**无 `<executions>`/phase 绑定** → `mvn compile|package|verify` 从不跑 checkstyle | 文档声称与流水线实际脱节（见下方"口径校正"）|
| `<skipITs>false</skipITs>` 硬编码在 failsafe XML | 可用 `-DskipITs=true` 跳过 | CLI 参数被 POM 配置覆盖，**跳过开关失效** | 无 Docker 的环境被迫跑 Testcontainers IT |
| `junit.jupiter.conditions.deactivate = …DisabledCondition` | — | 该配置使 `@Disabled` **完全失效**（禁用的测试照常执行）；全仓 `@Disabled` 出现 **0 次** | 纯风险、零收益 |

**改法一：checkstyle 从"全仓断言"换成两条可证伪判据**（新 `data/scripts/check-checkstyle-delta.py`，纯标准库，`--self-test` **19/19** 绿）

- **C1 增量红线**：本次改动涉及的 `.java` 文件 **0 违规**即红——新代码（含 AI 生成）当场拦住，与存量无关。
- **C2 存量棘轮**：每模块 error 级数量**只减不增**（基线 `data/checkstyle-baseline.json`，total 4155）；下降时打印"可下调基线"建议值，上调必须是一次显式提交。
- **C0 fail-closed**：基线模块本次没有报告 → 判红；全仓 0 报告 → `GateError` 退出码 2。**"扫不到"永远不被读成"没问题"**。
- 口径：只计 `severity=error`（与 POM `violationSeverity` 一致）；`NewlineAtEndOfFile` 244 条作为本机 CRLF 噪声排除（入库换行符由 `check-file-encoding.py` E3 管），`data/scripts/backup_logs/`（整份源码副本，属 B1-9）不参与门禁。
- Maven 侧改为 `mvn -B checkstyle:checkstyle`（只出报告不判失败），"量"归 Maven，"判"归脚本。
- **过程中真实拦下 7 条**（首轮运行即命中我自己改过的文件）：import 分组顺序 ×5（`GatewayFilterConfig`、`NacosRouteDefinitionRepository`、`CacheWarmer` 等）、行内 FQN ×1（`java.nio.charset.StandardCharsets` 内联）、分组错位 ×1 → 全部在源码修正，未用豁免注释压制；`AuthGlobalFilter` 的 `@lombok.RequiredArgsConstructor` 行内 FQN 一并改为 import（规范 5.1）。基线随之 4162 → 4155。

**改法二：覆盖率阈值改成"每模块棘轮 + 增量门禁"**

- 21 个叶子 POM 各写入 `<properties>` 覆盖 = **本模块 BUNDLE 实测值向下取整到 0.1%**（只减不增）：`system-web .284/.114`、`message-web .171/.038`、`nextwiki-web .150/.055`、`workflow-web .121/.083`、`agent-web .108/.024`、`userinfo-web .091/.014`、`cronjob-web .091/.038`、`literule-web .068/.017`、`generator-web .031/.000`、`common-safe .035/.032`、`message-server .024/.020`、`generator-server .023/.011`、`agent-server .048/.023`、`gateway .018/.004`、`literule-server .017/.012`、`system-server .013/.024`、`userinfo-server .011/.009`、`cronjob-server .008/.002`、`nextwiki-server .005/.005`、`workflow-server .003/.001`、`common-base .000/.000`。根 POM 默认降为 **0.01 / 0.005**（新模块起步线）。
- 语义变化：`jacoco:check` 从"永远红的理想值"变成"转红即说明本次改动真的降低了该模块覆盖率"。实测 `mvn -o verify -pl ydsz-gateway,ydsz-workflow/ydsz-workflow-server -DskipTests`（复用已有 exec 数据）**BUILD SUCCESS**，此前打印的 `Rule violated for bundle ydsz-gateway: 0.01 < 0.10` 消失。
- 新 `data/scripts/check-diff-coverage.py`（纯标准库，`--self-test` **16/16** 绿）落实 §14.7「增量行覆盖率 ≥ 40% 阻断 PR」：只统计本次 diff 的 `src/main/java` 新增行，且只有 jacoco.xml 里出现 `<line nr>` 的行算可执行（注释/import/接口签名不进分母，避免一行注释稀释指标）；**D3 fail-closed**：改动模块缺 `jacoco.xml` 直接判错退出。
- **该门禁第一次真实运行就抓到东西**：HEAD~1（`044d2c5fa8`）在 `AuthGlobalFilter` 新增 8 行可执行代码，覆盖 **0 行**。补 `AuthGlobalFilterSecretTest`（9 个用例：null/空串/纯空格拒绝、31 字节拒 / 32 字节放行、11 汉字 33 字节放行 / 10 汉字 30 字节拒绝、密钥与 JWT 同值只告警不阻断、JWT 密钥缺省空不短路校验）后，网关 42/42 绿，重跑门禁：**87.5%（7/8）**，通过 40% 阈值。
  > 顺带暴露一个真实缺陷类型：旧实现按 `String.length()` 判 32，多字节密钥会被**误拒**（11 个汉字 = 33 字节本已达标），且 `isEmpty()` 放行纯空格。现已按 UTF-8 字节数判定 + `isBlank()`，两条都由测试锁住。

**改法三：移除两个"负收益"配置** —— `<skipITs>false</skipITs>` 硬编码（改由根 POM 属性控制，`-DskipITs=true` 恢复可用）；`junit.jupiter.conditions.deactivate = …DisabledCondition`（会让 `@Disabled` 反向失效）。

**CI 接线**（`.github/workflows/ci.yml`）：Step 1 后端检出加 `fetch-depth: 0`（浅克隆下 `git merge-base` 失败，增量门禁会退化为"无从判定"）；新增 Step 1a 解析 `BASE_REF`（PR → `origin/$GITHUB_BASE_REF`，push → `HEAD~1`）；Step 4 换成报告 + 增量门禁；Step 5a 加增量覆盖率（`if: always()`，让 Step 5 因存量失败而红时覆盖率结论仍可见）；Step 7 `dorny/test-reporter` 的 `fail-on-error: false` → `true`（配 `if-no-files-found: warn` 时"报告为空"与"测试全过"不可区分）。

**口径校正（红线可执行化，同时是 B1-9 的一部分）**：`CLAUDE.md`（构建行 + "编译与验证"块）、`docs/云顶编码规范.md` §23.2 / 附录 B 前置条件 / 附录 D 说明与 D.1、`AGENTS.md`（关键文件索引 + §6 AI 必做清单 + §7 新增第 13 条）原称"构建期 validate 阶段默认强制执行 checkstyle（`checkstyle.skip=false`）"，实测**不成立**，已改为可复现表述：插件仅 `pluginManagement`、无 phase 绑定；CI 走 `checkstyle:checkstyle` + delta 门禁；并显式警告"存量 4155 条时不得把 `mvn checkstyle:check` 全仓结果当作合规证据"。

**过程中真实拦下的两个安全缺陷（不是风格问题）**

1. **`SqlInjectionFilter` 四条正则永不匹配**：跑 `mvn -o test -pl ydsz-gateway` 时 33 例中 7 例红，根因不是测试写错而是实现——`[OoRr]` 是**单字符**字符类（匹配一个 o/r，而不是 "OR"），故 `' OR 1=1` 系列靠它永不命中（此前那条用例之所以"绿"，是被无关的 `--` 注释规则顺手命中）；`s_p_executesql` 多了个下划线，永不匹配；`EXEC` 只匹配 `EXEC(`，漏掉 `EXEC xp_cmdshell`；且无任何时间盲注规则。已改为 `[Oo][Rr]`、`\bsp_executesql\b`、`\bEXEC(UTE)?\b(\s*\(|\s+\w)`，并新增 `\b(SLEEP|BENCHMARK)\s*\(` 时间盲注规则（STANDARD 档）→ **33/33 绿**，类 Javadoc 检测规则清单同步。
2. **`AuthGlobalFilter` 内部头签名密钥按字符数判强度**：见上文"改法二"的补测发现（多字节密钥误拒、纯空格放行）。

**未修清单（诚实边界）**

| 项 | 状态 |
|----|------|
| 132 个存量失败测试 | **未修**，归 B1-3（#18）。按根因分类：UnnecessaryStubbing **66**（Mockito 严格桩，纯测试卫生，机械可清）/ Controller 冒烟 `Request processing failed` **≈32**（内含 NPE 6、Mockito 误用 13、SysException 10）/ `@InjectMocks` 无法实例化 **4** / ArchUnit `failed to check any classes` **4**（`DddLayerArchTest` 在该模块扫不到 domain 类，规则空跑）+ 状态机断言失败若干（`FlowInstanceStatus*`）/ JUnit `ExtensionContext` `NoSuchMethodError` **2**（依赖版本冲突）。聚合口径：195 份历史 surefire 报告 → 781 用例 / 19 failures / 109 errors |
| `includeTestSourceDirectory=false` | **未改**。测试代码完全不参与 checkstyle——打开会把测试存量一次性引入棘轮，需先量再决定，暂记 |
| `mvn verify` 在 CI 仍预期为红 | **事实**：Step 5 红 = 存量 132 失败的真实反映，已在 CI 注释与本报告显式声明，不做 `continue-on-error` 掩盖 |
| 2 个 Testcontainers IT | 需要 Docker；`TestcontainersBase` 位于 `ydsz-common-core/src/main/java`（测试基类打进生产 jar，属 B1-11 分层问题） |
| 覆盖率提升 | 本轮只做"把量尺修准"，未新增任何模块的全量覆盖（除网关 9 个用例）|

**复现命令**

```bash
mvn -o -q checkstyle:checkstyle
python3 data/scripts/check-checkstyle-delta.py --self-test          # 19/19
python3 data/scripts/check-checkstyle-delta.py --base HEAD          # 实测：4155 条 / C1 12 文件 0 违规 → 绿
python3 data/scripts/check-diff-coverage.py --self-test             # 16/16
mvn -o verify -pl ydsz-gateway -DskipTests                          # jacoco:check 绿
python3 data/scripts/check-diff-coverage.py --base HEAD~1 --show-files  # 网关 7/8=87.5%，另报 common-exception 缺 jacoco.xml（D3）
```

### B0 进行中/待办

| 项 | 状态 |
|----|------|
| B0-4 子应用 base 路径单一来源（S5） | ✅ 已完成（配置级 + 门禁 + **产物级**：B0-10 解除后 `check:base` 已实测到 9 个应用的 dist 前缀） |
| B0-5 nginx 缓存分层（S6） | ✅ 已完成（分层 + 安全头单一来源 + `check:nginx` 含 8/8 反例自检；`nginx -t` 待镜像内验证） |
| B0-6 设计令牌 CSS 接入（S7） | ✅ 已完成（接线 + 构建外置根因修掉 + `check:tokens` 含 4/4 反例自检；顺带抓出 `easeIn`→`ease-in` 真实缺陷；浏览器渲染复核待做） |
| B0-7 CI 契约/错误码门禁（S8） | ✅ 已完成（`.mjs`→`.mts` 修好 + 新 `wiring` job 8 门禁全绿 + `check:ci` 7/7 反例自检 + 补上缺失的性能退化门禁并证伪 8/8 + 生成器 8 类缺陷修掉含"空跑假绿"与"未知参数即写盘"）；**遗留**：一次性 `gen:contract` 全量再生成待人工复核；`pnpm lint` 仍不可用（eslint.config.mjs 加载报错）故 verify job 仍红 |
| B0-11 错误码命名空间冲突（新发现，S8 副产物） | 待办 → 归 **B1-12**：system 的 B91/B92/B93/B94 段与 message/cronjob/literule/agent 重叠，userinfo 同枚举内 `B30028` 用两次，`A00000` 两处定义；产物里 `B91001` 已被 message 抢占，前端拿不到"字典类型不存在" |
| B0-12 i18n / format 存量债（S8 副产物） | 待办：`check:i18n` 5 个 locale 文件 82 条缺键（含 `cepPushTest`/`cepPushPush` 疑似拼写反了）；`format:check` 3309 文件不一致 + 6 文件解析失败 + `.dev-logs/` 203 个临时脚本已入库；两者暂列 `check-ci.mts` 的 MANUAL_ONLY 并附理由 → 归 B1-9 |
| B0-8 viewport 禁缩放 + CSP 自堵 | ✅ 已完成（9 个入口 viewport 归一 + 死掉的第三方引用移除并留恢复路径；`check:head` 含 14/14 反例自检，另用 `git show HEAD` 还原改前文件实测报 14 条退出码 1；顺带核实"CSP 未拦掉自有资源"）；**遗留**：dev 端字形回退的视觉复核待做、百度统计是否恢复由使用方决策 |
| B0-13 脚手架硬编码存储加密密钥（B0-8 副产物） | 待办：`bash/gen-app.mts:185` 把 `VITE_APP_STORE_SECURE_KEY=YDSZ-pmis-2026-secure-key` 硬编码进每个新应用 `.env`（`apps/generator-web/.env:4` 同值），经 `comm/stores/src/setup.ts:88` 作 secure-ls AES 密钥构建期注入 bundle；密钥随 bundle 公开已无秘密性，但**全应用共用同一字面量**使单点泄露变全域泄露 → 归凭据轨（B0-1 延续），口径需使用方拍板 |
| B0-9 网关路由双源漂移 | ✅ 已完成（路由清单单一来源 + `gen-gateway-routes.py --check` + `check-gateway-routes.py` R1–R10（57/57 反例自检，实测 20 路由/1056 路径/9 服务，红 0 ⚠️3）+ 网关 5.x 前缀的 metadata 反漂移 + 前端 nginx/10 vite 双剥修复 + `/api/v1` 238 行清理含生成器模板再生源 + CI 接线（新写 `check-file-encoding.py` 28/28、移除 3 个引用不存在脚本的伪门禁、修好恒红的交叉检出仓库名））；**待决策 5 项**：`/ws/**` 归属、secondary-auth 改名、`ydsz.file.domain` 指向废弃端口 9010、4 条"前端调用无后端"路径、`gen-crud-skeleton.mts` 死生成器；详见上文 B0-9 小节 |
| B0-10 子应用生产构建失败（三成因：未声明依赖 / 入口指向永不构建的 dist / 库未先构建） | ✅ 已完成（`pnpm build --force` 25/25 绿 + 新门禁 `check:deps` 双规则含 8/8+10/10 反例自检 + CI `bundle` job 改为构建全部 10 个应用）详见上文 B0-10 小节 |
| B1-7 turbo `globalDependencies` 加 `comm/**` | ❌ **误报，改动已撤回**（实测 `<NONEXISTENT>` 节点已按整包目录哈希覆盖 `^build`；改一行 mock-service 源码：现状精准失效 3/10 app，加 `comm/**` 后无差别失效 10/10；另核 11 个入口包 import 的 `@ydsz*` 包根全部已声明，图边无缺）详见上文 B1-7 小节 |
| B1-2 覆盖率/静态检查门禁 | ✅ 已完成（三条"恒红 = 无门禁"换成可证伪增量门禁：21 模块 jacoco 棘轮 + `check-diff-coverage.py`（16/16 自检）+ `check-checkstyle-delta.py`（19/19 自检）+ CI `fetch-depth: 0`/Step 4/5a 接线 + 移除 `DisabledCondition` 与 `<skipITs>` 硬编码；顺带把网关 SQL 注入过滤器 4 条永不匹配的正则修好（33/33 绿）并补 `AuthGlobalFilterSecretTest` 9 例，把增量覆盖率从 0% 拉到 87.5%）；**遗留**：132 个存量失败测试 → B1-3；`includeTestSourceDirectory=false` 使测试代码不受 checkstyle 约束（未改，需先量再定）详见上文 B1-2 小节 |
| 网关内部头签名**下游从不验签**：仅网关生成签名，无服务设置 `ydsz.security.internal-sign.secret`，`InternalSignatureProperties.isEnabled` 默认 false | 新发现 → 归入 B1-11 |
| `docs/nacos-config/ydsz-gateway.yml` 与 `application.yml` 的 include 曾不同（cache-metrics 有无） | 已随 B0-2 统一 |
| `effective-excel-pom.xml`（含本机绝对路径）被提交入库 | 归入 B1-9 文档/仓库卫生 |

---

*本报告基于 2026-10-25 两个仓库的工作树快照，§十一 随实施滚动更新到 2026-10-26；所有 ✅ 结论均可按所列 `文件:行号` 直接复核，◇ 结论口径见证据列。当前 `mvn -o -T 1C compile` 为 BUILD SUCCESS（✅，2026-10-26 复跑）。两仓工作树均有未提交改动：后端 30 项（网关路由单一来源改造与 R1–R10 门禁、`check-file-encoding.py`、CI 接线、P0 安全加固残留项），前端 513 项（B0-4/5/6/8/9/10 的门禁、生成器与 URL 接线）。提交与推送均未执行，实施前请先确认这些在途变更。*
