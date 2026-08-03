# YDSZ-Frontend 全面代码评审与优化建议报告

> 评审对象：`ydsz-frontend`（Qiankun + Vue 3 + Element Plus 微前端 Monorepo，9 个子应用 + 1 个主应用）
> 评审基准：行业主流中后台竞品（阿里云 console、蚂蚁金服 Ant Design Pro 系、字节 Arco Design Pro、腾讯 TDesign Starter）与互联网大厂前端研发规范（阿里《前端开发规约》、Google Web Vitals、OWASP 前端安全基线）
> 评审日期：2026-08-03

---

## 一、总体评价

**当前成熟度评分（5 分制）**

| 维度 | 评分 | 说明 |
|---|---|---|
| 架构设计 | 4.0 | Monorepo + catalog 依赖治理 + 统一共享包分层，达到大厂水准 |
| 工程化规范 | 4.2 | lefthook / commitlint / cspell / turbo pipeline 完善 |
| 类型安全 | 3.0 | tsconfig 高严格度，但 522 处 any 且 eslint 主动关闭 no-explicit-any |
| 测试质量 | 1.5 | **最大短板**：34 个单测全部在 comm/conf，9 个业务应用 0 测试；e2e 仅 5 个用例且可静默跳过 |
| 性能优化 | 3.2 | 分包/懒加载/CDN importmap 已做，但压缩、图片优化、预算管控缺失 |
| 安全合规 | 2.5 | secure-ls 伪加密、v-html 无消毒、e2e 硬编码账号 |
| 体验一致性 | 3.5 | 兜底页/水印/i18n/暗黑模式齐全，缺统一空状态与全局错误监控闭环 |

**一句话结论**：架构骨架是大厂级的，但「测试体系、安全细节、性能预算、契约落地」四件事与竞品存在代差，属于典型的"重基建、轻闭环"阶段。

---

## 二、架构优化建议

### A1. OpenAPI 契约未落地，应打通"后端驱动"闭环 【高优先级】
- **现状**：`openapi-generator-config.yaml` 存在（typescript-axios → `src/api/sdk`），但代码库无 sdk 产物、package.json 无生成脚本，各 app 的 `src/api/*.ts` 全部手写。
- **对标**：阿里/字节中后台普遍采用 OpenAPI → TS 类型 + client 自动生成，接口变更在 CI 中被阻断。
- **落地步骤**：
  1. 在根 package.json 增加 `gen:api` 脚本，按子应用拆分生成（每个后端微服务一个 sdk 输出目录）；
  2. 手写 api 层逐步改为「sdk 类型 + 薄封装」，新需求强制使用生成类型；
  3. CI 增加契约检查 job：`pnpm gen:api && git diff --exit-code`，契约漂移直接失败（配置注释中已提到该意图，但未执行）。
- **收益**：消除手写类型与后端不一致导致的线上 bug；522 处 any 中 API 相关部分可批量消除。

### A2. Qiankun 沙箱与通信机制的现代化评估
- **现状**：`main/src/bootstrap.ts` 使用 `experimentalStyleIsolation: true` + `prefetch: false` + 自研 hover prefetch（137-227 行）；通信用 `initGlobalState`（user/theme/locale/notificationCount/tenantId）。
- **风险点**：
  - `experimentalStyleIsolation` 是实验性 API，qiankun 官方已多年不维护（`vite-plugin-qiankun@1.0.15` 同样停更），大厂新项目多转向 **micro-app（京东）/ wujie（腾讯）/ Module Federation**；
  - globalState 是字符串广播，无类型约束，子应用各自注册监听器易出现时序 bug。
- **建议**：
  1. 短期（不换框架）：给 globalState 加 TS 类型声明与版本字段，封装 `useGlobalState<T>()` 类型安全 hook；通信事件建立枚举注册表（`comm/constants`），禁止散写字符串 key；
  2. 中期（立项评估）：对 **wujie**（腾讯、保活/降级更好）或 **Module Federation + 独立部署** 做 POC。若 qiankun 暂无痛点，保持现状但锁定版本并关注 CVE；
  3. `vite-plugin-qiankun` 停更是供应链风险，建议 fork 内化到 `conf/` 或替换为社区活跃分支。

### A3. 共享包分层需要"防腐层"
- **现状**：`comm/` 分层清晰（@core / effects / 基础包），主应用依赖 14 个共享包，子应用额外依赖 shared-auth/shared-business。
- **隐患**：9 个子应用共用 `shared-business`，业务组件共享是双刃剑——跨应用业务耦合会使独立部署失去意义。
- **建议**：
  1. 在 `vsh check-dep` 中增加规则：`apps/*` 之间禁止互相 import（用 dependency-cruiser 强制）；
  2. `shared-business` 内的组件按归属标注 owner，新增组件需评审"是否真的跨应用"；
  3. 为每个共享包补 README 与最小示例（对标 vben / tdesign-starter 的内部包文档规范）。

---

## 三、功能增强建议

### F1. 全局错误监控仅有采集、缺少闭环 【高优先级】
- **现状**：`comm/effects/monitor` 存在，但未接入 Source Map 上传与告警。
- **落地**：
  1. 接入 Sentry 或自建（采集已有基础），CI 构建时上传 sourcemap 并随即删除产物中的 map 文件；
  2. 前端异常 → 企微/钉钉告警（与 message-web 的 notification 能力打通，可自产自用）；
  3. 增加接口慢请求埋点（>2s 上报），与 `X-Trace-Id`（request 拦截器已注入）串联后端 APM。

### F2. 中后台标配能力补齐清单
对照 Ant Design Pro / TDesign Starter，当前缺失或半成品的项：
| 能力 | 现状 | 建议 |
|---|---|---|
| 统一空状态组件 | 兜底页有，业务空状态无统一组件 | 在 `common-ui` 增加 `Empty` 系列（无数据/无权限/网络异常/搜索无结果） |
| 全局搜索 | `global-search.vue` 存在但用 v-html 渲染高亮 | 消毒后保留；增加菜单+页面内容的索引搜索 |
| 页面级操作日志 | 后端有 auditLog（literule-web） | 前端关键操作（删除/导出/审批）统一埋点上报 |
| 新手引导/功能公告 | 未见 | 低优先级，可用 driver.js 轻量实现 |
| 数据大屏/报表 | echarts 已按需封装 | 借 project-web 的 evm 模块沉淀通用图表组件到 `common-ui/charts` |

### F3. AI 能力的场景化落地（差异化机会）
- `agent-web`（agent/approval/dag/definition/rag）是竞品中少有的差异化资产。建议：
  1. 把 AI 助手做成主应用级悬浮入口（而非独立子应用页面），在任意页面可唤起，对接 rag 模块回答操作问题；
  2. 审批场景（workflow-web）与 agent 打通：审批意见智能生成、风险摘要（risk 模块数据）自动附在审批单上。

---

## 四、性能提升建议

### P1. 压缩与静态资源优化 【投入小、收益立竿见影】
- **现状**：`compress: false`（装配位已留）、无图片优化插件、`reportCompressedSize: false`。
- **落地**（1 人日）：
  1. 开启 `vite-plugin-compression` 生成 `.gz` 与 `.br`，Nginx 配置 `gzip_static on; brotli_static on;`（Nginx 已在用，docker-compose 有现成镜像可改）；
  2. 引入 `vite-plugin-imagemin` 或构建外统一走 tinypng 脚本，对 `main/src/assets` 与各 app 静态图压缩；
  3. Nginx 对 `assets/` 开启长缓存（hash 文件名 + `Cache-Control: immutable`），HTML `no-cache`。

### P2. 建立性能预算（Performance Budget）机制
- **对标**：Google Web Vitals + 字节的"性能门禁"实践。`lighthouserc.json` 已有断言（perf≥0.8、LCP≤3s），但**未接入 CI 强制阻断**。
- **落地**：
  1. CI 中跑 lighthouse-ci，不达标 PR 标红；
  2. 增加 bundle 预算：单 chunk > 500KB 构建告警（`build:analyze` 已有，需接 CI 产出报告）；
  3. 按子应用监控首屏 LCP/INP，接入 monitor 包做 RUM 上报。

### P3. CDN importmap 方案的稳定性加固
- **现状**：vue/pinia/vue-router/dayjs 走 esm.sh importmap（`plugins/importmap.ts`，改造自 vite-plugin-jspm），es-module-shims 锁定 1.10.0。
- **风险**：esm.sh 是公共服务，企业内网/弱网环境是单点故障；且 es-module-shims polyfill 在低端机有额外开销。
- **建议**：
  1. 将 CDN 产物**内化到自有 Nginx**（构建期下载 vendor 产物随 dist 发布，importmap 指向自有域名），兼具 CDN 缓存收益与可用性；
  2. 目标浏览器明确为 Chrome ≥ 89 后评估移除 es-module-shims。

### P4. 子应用加载策略优化
- `prefetch: false` + 自研 hover prefetch（200ms 延迟）思路正确，但建议补两刀：
  1. 按用户角色预取：登录后根据权限菜单空闲预取**首个必经子应用**（`requestIdleCallback`）；
  2. 子应用切换时保留 keep-alive（tabbar store 已支持），确认 SubAppContainer 销毁时机不重复销毁微应用实例（qiankun 保活可显著降低二次进入白屏）。

---

## 五、体验改善建议

### U1. 统一"加载-错误-空"三态规范
- 现状：Loading 组件统一，但表格/卡片级 loading、接口错误重试、空状态三者没有统一约定，9 个应用 13–27 个页面各自实现必然漂移。
- 落地：在 `common-ui` 提供 `PageStatus` 组合组件（loading/error/empty/success 四态），新页面强制使用；老页面按 app 逐个替换（每个 app 半天）。

### U2. 表单与表格体验对齐大厂
- vxe-table 已懒加载，建议补齐：列宽记忆（localStorage）、密度切换、全屏模式、导出 CSV（消息/日志类页面刚需，如 cronjob-web 的 jobLog、message-web 的 deadLetter）；
- 表单：统一 `useForm` 封装（校验错误自动滚动定位、离开页面脏数据拦截提示）。

### U3. 无障碍（a11y）
- lighthouse 已断言 a11y≥0.9，保持即可；额外补：element-plus 组件 aria 属性审查、键盘可达性（尤其弹窗焦点圈定）。政企/国企项目招投标常以此为加分项，成本低收益明确。

### U4. 移动端/窄屏降级
- 中后台通常不做移动端，但审批类场景（workflow-web task）移动端诉求真实存在。建议最小实现：审批列表+详情做响应式单页，或输出企微 H5 入口。

---

## 六、过度设计与不必要复杂度（做减法）

> 减法和加法同等重要。以下项建议**简化或下线**：

### O1. secure-ls 加密 localStorage 是伪安全 【建议移除】
- **现状**：token 持久化经 secure-ls AES 加密，密钥 `VITE_APP_STORE_SECURE_KEY` **构建期注入并随 bundle 分发**——任何拿到 bundle 的人都能解密，纯属安全幻觉，还带来：加密/解密 CPU 开销、调试困难、存储体积膨胀。
- **大厂做法**：token 走 HttpOnly Cookie（SameSite=Lax）由后端下发；前端持久化只放非敏感的用户偏好。
- **落地**：与后端协商改为 Cookie 方案（request 拦截器改为 `withCredentials`），移除 secure-ls 与 token 的 localStorage 持久化。这是**安全与简化的双重收益**。

### O2. 自研 hover prefetch（bootstrap.ts 137–227 行）过度定制
- 90 行自研代码实现的功能，qiankun 自带 `prefetch: 'all'` 或 `prefetchApps` 数组即可覆盖 80% 场景。自研逻辑需随 qiankun 升级维护， ROI 低。
- **建议**：改为配置式 prefetch（登录后按角色 prefetch 列表），删除自研 hover 逻辑。

### O3. openapi-generator 配置"尸位素餐"
- 配置文件存在但从未执行——要么是半拉子工程，要么是被放弃的方案。**要么落地（见 A1），要么删除**，避免误导新人。

### O4. 工具链自研面过宽，注意维护成本
- `vsh`（自研 CLI：check-circular/check-dep/lint/publint/code-workspace）、自研 importmap 插件（改造自停更的 vite-plugin-jspm）、archiver 打包插件、dev warmup 等，个性化程度已接近 vben 全家桶。
- **建议**：保持现状可用，但确立原则——**新增能力优先用社区成熟方案，自研只补差异化**；对 vsh 各命令记录"为什么不用社区方案"的决策文档（ADR），防止变成无文档黑盒。

### O5. e2e 测试的"假绿"比没有更危险
- `e2e/core-flows.spec.ts` 断言包在 `if (isVisible)` 里可静默跳过，且硬编码 `admin/admin123`。
- **建议**：改为 `expect(...).toBeVisible()` 硬断言；测试账号改用环境变量注入；e2e 从 5 个用例扩充到覆盖 9 个应用的 P0 核心流（登录→进入子应用→列表加载→退出），每应用 1-2 条即可。

---

## 七、安全合规专项

| 问题 | 风险 | 整改 |
|---|---|---|
| secure-ls 伪加密 token | 安全幻觉，密钥随包分发 | 改 HttpOnly Cookie（见 O1） |
| `v-html` 5 处无消毒 | XSS | 引入 DOMPurify，封装 `v-safe-html` 指令全局替换 |
| eslint `no-explicit-any: off` | 类型安全防线失守 | 改 `warn`→存量清零后改 `error`，新增代码 CI 拦截 |
| e2e 硬编码 admin/admin123 | 凭据泄露 | 环境变量 + CI secret |
| 无 CSP 头 | XSS 纵深缺失 | Nginx 增加 CSP（先 report-only 模式观察） |

---

## 八、落地路线图（按 ROI 排序）

| 阶段 | 事项 | 预估投入 | 收益 |
|---|---|---|---|
| **Sprint 1（立即）** | P1 压缩+图片优化+缓存头；O5 e2e 硬断言；安全表 XSS/CSP | 3-4 人日 | 性能/安全立竿见影 |
| **Sprint 2-3** | O1 token Cookie 化改造（需后端配合）；A1 OpenAPI 生成落地；eslint any 收紧 | 1-2 周 | 安全实质提升 + 契约闭环 |
| **Sprint 4-6** | 测试体系补齐（各 app P0 单测 + e2e 扩充）；F1 监控闭环 + sourcemap | 2-3 周 | 补上最大短板 |
| **季度规划** | P2 性能预算门禁；U1 三态统一；A2 微前端框架 POC 评估；F3 AI 悬浮助手 | 1 季度 | 对标一线大厂完成度 |

---

## 附：现状关键数据索引

- 微应用注册：`main/src/qiankun/index.ts:42-97`
- Qiankun 启动：`main/src/bootstrap.ts:93-128`（hover prefetch 137-227）
- 全局状态：`main/src/qiankun/global-state.ts:12-33`
- 动态路由：`main/src/router/guard.ts`、`access.ts:32-50`
- 请求封装：`comm/effects/shared-auth/src/request.ts:64-105`
- 持久化加密：`comm/stores/src/setup.ts:43-62`
- 构建分包：`conf/vite-config/src/config/application.ts:81-85`
- CDN importmap：`conf/vite-config/src/plugins/importmap.ts`
- 测试配置：`vitest.config.ts`（阈值 70/60）、`e2e/core-flows.spec.ts`（5 用例）
- Lighthouse：`lighthouserc.json`（perf≥0.8 / LCP≤3s）
- any 统计：apps/main/comm 共 522 处；单测文件 34 个（全部在 comm/conf，业务应用 0）
