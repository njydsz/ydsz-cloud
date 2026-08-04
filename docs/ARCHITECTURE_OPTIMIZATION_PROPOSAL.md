# ydsz-backend 架构优化完善建议书

> **审计时间**：2026-08-04 | **审计范围**：ydsz-backend 全量代码 + 部署配置 + 数据库 Schema  
> **对标参考**：阿里巴巴(HSF/Nacos/Dubbo)、腾讯(北极星/TSF)、字节跳动(Kitex/Hertz)、美团(Octo/Leaf)、Google(SRE/Microservices)、Netflix(Hystrix/Zuul)、AWS Well-Architected  
> **版本**：v1.0

---

## 目录

- [一、总体评估](#一总体评估)
- [二、架构优化（14 项）](#二架构优化)
- [三、功能增强（12 项）](#三功能增强)
- [四、性能提升（10 项）](#四性能提升)
- [五、体验改善（10 项）](#五体验改善)
- [六、实施路线图](#六实施路线图)

---

## 一、总体评估

### 1.1 项目现状评级

| 维度 | 评级 | 说明 |
|------|------|------|
| **架构设计** | ⭐⭐⭐⭐☆ (4/5) | DDD 五层 + L1-L6 公共库分层，超越 80% 同类项目 |
| **工程质量** | ⭐⭐⭐⭐ (4/5) | 六重质量门禁，但 CI/CD 流水线缺失拉低评分 |
| **安全性** | ⭐⭐⭐⭐☆ (4.5/5) | JWT+RBAC+多租户+密钥分离+Nonce防重放，安全体系扎实 |
| **可观测性** | ⭐⭐⭐ (3/5) | OTel/SkyWalking 双备选但未端到端打通，缺少业务指标看板 |
| **性能** | ⭐⭐⭐☆ (3.5/5) | 缓存体系完善，但 JWT 缓存击穿、限流降级分布式协调等需修复 |
| **DevOps** | ⭐⭐☆ (2.5/5) | Docker/K8s/Helm 配置完善，但**无 CI/CD 流水线**是重大短板 |
| **测试** | ⭐⭐⭐ (3/5) | 105+ 测试类，但覆盖率刚过 60% 及格线 |

### 1.2 对标差距总览

当前架构比肩中小型互联网公司水平（3-4 线），与大厂（美团/字节/腾讯）产研规范的主要差距集中在：

1. **CI/CD 自动化缺失** — 大厂标准：Push-to-Deploy 全自动
2. **可观测性未闭环** — 大厂标准：Tracing + Metrics + Logging 三支柱全链路打通
3. **性能压测体系缺失** — 大厂标准：每次上线前全链路压测
4. **API 治理薄弱** — 大厂标准：API 版本管理 + 兼容性检测 + 自动 SDK 生成
5. **高可用演练缺失** — 大厂标准：定期混沌工程 + 容灾演练

---

## 1.3 实施进度（2026-08-04 更新）

| 编号 | 项目 | 状态 | 交付物 |
|------|------|------|--------|
| 2.1 | 补齐 CI/CD 流水线 | ✅ 已完成 | `.github/workflows/backend-ci.yml` |
| 2.2 | JWT 缓存击穿防护 | ✅ 已完成 | `CachedJwtValidator` 接入 `CacheProtectionGuard` |
| 2.3 | 网关 CORS 配置规范化 | ✅ 已完成 | `GatewayCorsConfig` + `CorsProperties` |
| 2.4 | 数据库 Schema 版本化管理 | ✅ 已完成 | `deploy/sql/`（schema/seed/verify）+ `schema_check.sh` |
| 2.5 | 限流降级分布式协调 | ✅ 已完成 | `RateLimitFilter` 实例数自适应分摊 + fallback 指标 |
| 2.6 | API 版本管理机制 | ✅ 已完成（原有注解）+ 响应头 | `ApiVersionHeaderFilter` 注入 `X-API-Version` |
| 2.10 | 网关主动健康检查 | ✅ 已完成 | `GrayLoadBalancerConfig` 叠加 HealthCheck 装饰器 |
| 3.1 | 数据库初始化脚本 | ✅ 框架完成（需导出真实 DDL） | `deploy/sql/schema/V1.0.0__init.sql` 占位 |
| 3.2 | 链路追踪端到端打通 | ✅ 已完成 | `docker-compose.observability.yaml` + `TRACING_INTEGRATION_GUIDE.md` |
| 3.3 | 业务监控大盘与告警 | ✅ 已完成 | Prometheus 规则 + Grafana Dashboard + AlertManager |
| 4.1 | 数据库连接池调优 | ✅ 已完成 | `deploy/config/ydsz-common-datasource.yaml` |
| 4.2 | 慢 SQL 治理 | ✅ 已完成 | Druid 慢 SQL 监控 + 索引/分区脚本 `V1.0.1` |
| 4.3 | 全链路压测体系 | ✅ 已完成 | `load-test/`（K6 场景 + 执行脚本） |
| 4.4 | 缓存策略优化 | ✅ 已完成 | `docs/CACHE_BEST_PRACTICES.md` |
| 5.1 | 本地开发环境一键启动 | ✅ 已完成 | `docker-compose.dev.yml` + `dev-start.sh` |
| 5.2 | 运维 Runbook | ✅ 已完成 | `docs/runbooks/`（5 篇 SOP） |
| 5.8 | Changelog 自动生成 | ✅ 已完成 | `.github/workflows/changelog.yml` + `cliff.toml` |
| — | **构建阻断修复（额外）** | ✅ 已完成 | common-jdbc 33+ 类恢复、CoreHealthIndicator 重建、BOM 清理、Checkstyle 修复等 8 处 |

> 注：2.7（gRPC）/2.8（CQRS）/3.4（工作流增强）/3.5（Agent 扩展）等涉及产品决策或
> 大改造的 P2/P3 项，建议按业务优先级另行立项评估，本阶段未实施。

---

## 二、架构优化（14 项）

### 2.1 【P0-紧急】补齐 CI/CD 流水线

**现状**：后端代码库中**无任何 CI/CD 配置文件**（无 Jenkinsfile、无 .gitlab-ci.yml、无 GitHub Actions）。Dockerfile 注释引用了 `.github/workflows/backend-ci.yml` 但该文件不存在。

**对标**：阿里 Aone + 云效、腾讯蓝盾、美团 MCI，均要求代码提交即触发自动构建→质量扫描→镜像打包→部署。

**建议方案**：

```yaml
# .github/workflows/backend-ci.yml
name: Backend CI/CD
on:
  push:
    branches: [main, develop]
    paths: ['ydsz-backend/**']
  pull_request:
    paths: ['ydsz-backend/**']

jobs:
  quality-gate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - name: Maven Enforcer + CheckStyle + SpotBugs
        run: mvn validate -pl ydsz-backend
      - name: OWASP Dependency Check
        run: mvn dependency-check:check -DskipDependencyCheck=false
      - name: JaCoCo Coverage
        run: mvn verify -DskipJacoco=false
      - name: ArchUnit Test
        run: mvn verify -DskipArchUnit=false

  build-and-push:
    needs: quality-gate
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [gateway, system, userinfo, project, message, cronjob, workflow, agent, literule, nextwiki]
    steps:
      - name: Docker Build & Push
        run: |
          docker build --build-arg APP_NAME=ydsz-${{ matrix.service }} \
            -t registry.cn-hangzhou.aliyuncs.com/ydsz/ydsz-${{ matrix.service }}:${{ github.sha }} .
          docker push ...

  deploy-dev:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - name: Helm Deploy to Dev
        run: |
          helm upgrade ydsz-backend deploy/helm/ydsz-backend \
            --set image.tag=${{ github.sha }} \
            --set env=dev --namespace ydsz-dev
```

**预期收益**：代码提交到部署全自动化，消除人工构建误操作风险，提效 80%+。

---

### 2.2 【P0-紧急】JWT 缓存击穿防护

**现状**：`CachedJwtValidator` 使用 Caffeine `expireAfterWrite(5, SECONDS)`，但未使用 `LoadingCache` 或分布式锁保护，JWT 过期瞬间大量请求会同时执行 JWT 解析（缓存击穿）。

**对标**：美团叶子节点缓存、阿里 Tair 缓存均使用互斥锁 + 永不过期 + 异步刷新策略。

**建议方案**：

```java
// 当前（有问题）
Cache<String, Jws<Claims>> cache = Caffeine.newBuilder()
    .expireAfterWrite(5, TimeUnit.SECONDS)
    .maximumSize(10000)
    .build();

// 建议（防击穿）
LoadingCache<String, Jws<Claims>> cache = Caffeine.newBuilder()
    .expireAfterWrite(5, TimeUnit.SECONDS)
    .refreshAfterWrite(4, TimeUnit.SECONDS)   // 异步提前刷新
    .maximumSize(10000)
    .recordStats()
    .build(key -> {
        // 分布式锁仅允许一个节点刷新
        if (redissonClient.tryLock("jwt:refresh:" + key, 100, TimeUnit.MILLISECONDS)) {
            try {
                return jwtParser.parseSignedClaims(token);
            } finally { lock.unlock(); }
        }
        // 其他节点等待 100ms 后从缓存读取
        Thread.sleep(100);
        return cache.getIfPresent(key);
    });
```

**预期收益**：消除 JWT 解析的瞬时 CPU 尖峰，网关 P99 延迟降低 30%+。

---

### 2.3 【P1-重要】网关 CORS 配置规范化

**现状**：`AuthGlobalFilter` 中简单放行所有 OPTIONS 请求，未声明 `CorsWebFilter` Bean，无 `Access-Control-Allow-Origin` 等标准 CORS 响应头。

**对标**：Spring 官方推荐使用 `CorsWebFilter`，腾讯 TSF 网关要求显式声明可信任 Origin。

**建议方案**：

```java
@Bean
public CorsWebFilter corsWebFilter(CorsProperties corsProperties) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(corsProperties.getAllowedOrigins()); // 不使用 *
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setExposedHeaders(List.of(
        "X-Trace-Id", "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset"
    ));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return new CorsWebFilter(source);
}
```

**预期收益**：符合 CORS 安全规范，消除前端跨域相关偶发问题，提升浏览器兼容性。

---

### 2.4 【P1-重要】数据库 Schema 变更管理规范化

**现状**：禁止 Flyway/Liquibase，所有变更编辑 `deploy/sql/V1.0.0.sql` 单文件——但**该文件在磁盘上不存在**。当前 126+ 张表无版本化 Schema 管理。

**对标**：字节使用自研 Atlas-Go 做声明式 Schema 管理，美团使用 DBLE + 版本化 SQL 脚本。无论用哪种方案，"单大文件"都不是可维护的方案。

**建议方案**（不改动"禁止 Flyway"的规范前提）：

```
deploy/sql/
├── schema/
│   ├── V1.0.0__init.sql           # 初始建表（当前应补充）
│   ├── V1.0.1__add_alert_tables.sql
│   └── V1.0.2__alter_contract.sql
├── seed/
│   └── seed_data.sql              # 种子数据（字典/菜单/默认配置）
└── verify/
    └── schema_check.sh            # CI 中校验 Schema 一致性
```

同时在 CI 中增加 Schema 校验步骤：

```bash
# schema_check.sh
# 1. 在测试 PG 容器中执行所有 schema/*.sql
# 2. 用 pg_dump --schema-only 导出实际 Schema
# 3. 与期望 Schema 对比，不一致则 CI 失败
```

**预期收益**：Schema 变更可追溯、可回滚、可验证，消除"表到底长什么样"的认知差。

---

### 2.5 【P1-重要】限流降级的分布式协调

**现状**：`RateLimitFilter` Redis 不可用时降级到**单机内存**令牌桶，多实例部署时各实例独立计数，实际限流效果被实例数稀释。

**对标**：Sentinel 支持集群流控（Token Server 模式），Nginx 使用共享内存 `ngx.shared.DICT`。

**建议方案**：

```java
// 方案 A（推荐）：Sentinel 集群流控
// 配置 Sentinel Token Server（选一个实例或独立部署）
// 所有实例向 Token Server 请求令牌
FlowRule rule = new FlowRule("api-gateway")
    .setCount(1000)
    .setGrade(RuleConstant.FLOW_GRADE_QPS)
    .setClusterMode(true)
    .setClusterConfig(new ClusterFlowConfig()
        .setFlowId(1L)
        .setThresholdType(ClusterRuleConstant.FLOW_THRESHOLD_AVG_LOCAL)
        .setFallbackToLocalWhenFail(true));

// 方案 B（小规模替代）：Redis 集群令牌桶
// 使用 Redisson RAtomicLong + Lua 脚本实现集群共享计数
```

**预期收益**：多实例场景下限流精度从 ±N 倍误差 收敛到 ±5%，保障限流有效性。

---

### 2.6 【P1-重要】API 版本管理机制

**现状**：所有 API 统一 `/api/v1/**`，无版本升级路径，无 API 废弃通知机制。

**对标**：Google API Design Guide 要求所有 API 带版本号且向后兼容至少 12 个月，Kubernetes API 使用 `apiVersion` + `deprecated` 警告。

**建议方案**：

```java
// 1. 版本注解
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiVersion {
    String since();        // 引入版本
    String deprecatedAt(); // 废弃版本（可选）
    String sunsetAt();     // 下线日期（可选）
    String migrateTo();    // 迁移目标 API 路径
}

// 2. 版本响应头注入
// 网关层自动注入 X-API-Version + X-API-Deprecated + Sunset 头

// 3. 多版本共存路由
// /api/v1/projects -> ProjectV1Controller
// /api/v2/projects -> ProjectV2Controller
// Gateway 按路径版本号自动路由
```

**预期收益**：API 变更可管理、可追溯，避免"改了 API 前端集体报错"的事故。

---

### 2.7 【P2-优化】服务间通信协议升级（gRPC/Protobuf）

**现状**：服务间调用使用 OpenFeign + JSON 序列化，文本协议在高频调用场景下序列化开销大。

**对标**：字节 Kitex 默认 Protobuf，阿里 Dubbo3 支持 Triple(Protobuf over HTTP/2)，Google 内部全 gRPC。

**建议方案**（渐进式，非全局替换）：

```protobuf
// 仅对高频/大对象场景引入 Protobuf
// 例如：project 查询、workflow 状态同步
syntax = "proto3";
package ydsz.project.v1;
message ProjectQueryRequest {
    int64 project_id = 1;
    repeated string fields = 2;
}
message ProjectResponse {
    int64 id = 1;
    string name = 2;
    // ...
}
```

- 新增 `ydsz-common-grpc` 模块封装 Spring gRPC starter
- 先在 project ↔ workflow 关键调用路径试点
- 保留 Feign 作为默认方案，Protobuf 用于性能敏感场景

**预期收益**：高频调用场景序列化性能提升 3-5x，网络传输体积减少 40-60%。

---

### 2.8 【P2-优化】引入 CQRS 模式优化报表查询

**现状**：`ydsz-project` 中的 EVM 挣值分析（PV/EV/AC/CPI/SPI/EAC/VAC）、利润核算、报表分析等复杂查询，直接在 OLTP 表上做聚合计算。

**对标**：Axon Framework CQRS、阿里 Cobar 读写分离 + 异构查询库。

**建议方案**：

```
┌──────────────┐      ┌───────────────────┐
│  Command 模型  │ ──→  │  PostgreSQL (OLTP) │  写模型
│  (立项/合同)    │      │  按租户+业务域分表   │
└──────────────┘      └───────────────────┘
                              │ CDC (Debezium)
                              ▼
┌──────────────┐      ┌───────────────────┐
│  Query 模型    │ ←──  │  PostgreSQL 只读副本  │  读模型
│  (报表/看板)    │      │  预聚合 + 物化视图     │
└──────────────┘      └───────────────────┘
```

- 对 EVM 指标预计算并定时刷新物化视图
- 报表查询走独立的只读数据源
- 复杂分析引入 DuckDB 嵌入式 OLAP（可选）

**预期收益**：复杂报表查询性能提升 10-50x，OLTP 写入不受分析查询影响。

---

### 2.9 【P2-优化】多租户资源配额与计费

**现状**：支持 SINGLE/MULTI/ISOLATE_DB 三种多租户策略，但无租户级资源配额控制（如单租户最大 API 调用量、最大存储量、最大并发任务数）。

**对标**：Salesforce Governor Limits、AWS Organizations SCP、阿里云 RAM 资源配额。

**建议方案**：

```sql
CREATE TABLE ydsz_tenant_quota (
    tenant_id   VARCHAR(32) PRIMARY KEY,
    max_users   INT DEFAULT 100,        -- 最大用户数
    max_api_qps INT DEFAULT 500,        -- 最大 API QPS
    max_storage_gb INT DEFAULT 50,     -- 最大存储(GB)
    max_workflow_instances INT DEFAULT 1000,
    max_agent_tokens_per_day INT DEFAULT 100000,
    -- ...
);

-- 结合 Sentinel 实现租户级流控
-- RateLimitFilter 增加租户配额维度
```

**预期收益**：SaaS 化能力基础，防止单租户资源滥用影响整体稳定性。

---

### 2.10 【P2-优化】API Gateway 主动健康检查

**现状**：`GrayLoadBalancer` 注释提到 `HealthCheckServiceInstanceListSupplier` 但实际未集成。当前依赖 Nacos 心跳（15s 间隔）摘除异常实例，存在故障发现延迟。

**对标**：Envoy 支持主动健康检查（Active Health Checking），Kubernetes 支持 Readiness Probe + EndpointSlice。

**建议方案**：

```java
@Bean
public HealthCheckServiceInstanceListSupplier healthCheckSupplier(
        DiscoveryClient discoveryClient, HealthIndicator healthIndicator) {
    return new HealthCheckServiceInstanceListSupplier(
        discoveryClient,
        Duration.ofSeconds(5),   // 健康检查间隔
        Duration.ofSeconds(3),   // 超时时间
        3,                        // 连续失败阈值
        "/actuator/health"        // 健康检查路径
    );
}
```

**预期收益**：故障实例摘除延迟从 15s 降到 5s，减少请求失败数。

---

### 2.11 【P3-建议】事件驱动架构增强

**现状**：已有 Domain Events + Outbox 模式（`ydsz-common-event`），但仅用于跨模块异步通知。未使用 Event Sourcing 或 Change Data Capture。

**对标**：Axon Framework（Event Sourcing）、Debezium（CDC）、阿里 Canal（MySQL Binlog）。

**建议方案**：

1. **CD​C 审计日志**：使用 Debezium 捕获 PostgreSQL WAL，将数据变更事件写入审计日志存储
2. **物化视图自动刷新**：当源表变更时，通过 Outbox 事件触发物化视图增量刷新
3. **跨系统数据同步**：项目状态变更 → 自动同步到外部 CRM/ERP

**预期收益**：审计追踪完整性提升，实时数据同步延迟从轮询级降到毫秒级。

---

### 2.12 【P3-建议】配置中心治理增强

**现状**：Nacos 作为配置中心，但缺少配置变更审批流程、灰度发布、配置回滚自动化、配置审计日志。

**对标**：Apollo 配置中心（携程开源）有完善的审批流程和灰度发布，Nacos 2.x 支持配置加密和版本管理但未在项目中利用。

**建议方案**：

1. **变更审批**：敏感配置（数据库密码、第三方 Key）变更需二次确认
2. **灰度发布**：配置先推送给 1 个实例验证，再全量推送
3. **配置审计**：记录所有配置变更历史到独立审计表
4. **配置校验**：`@ConfigurationProperties` 启动时校验 + CI 中的配置一致性检查

**预期收益**：消除"改了配置导致线上故障"的人为风险。

---

### 2.13 【P3-建议】Token 黑名单布隆过滤器分布式化

**现状**：`TokenBlacklistBloomFilter` 使用 `java.util.BitSet` 纯内存存储，多实例各自独立维护，依赖 Redis Pub/Sub 广播补充但启动时无预热。

**对标**：RedisBloom 模块（`BF.RESERVE`/`BF.ADD`/`BF.EXISTS`）支持分布式布隆过滤器。

**建议方案**：

```java
// 方案 A：RedisBloom（简单，需要 RedisBloom 模块）
public class RedisBloomTokenBlacklist {
    private final RedissonClient redissonClient;
    // Redisson 3.21+ 内置 RBloomFilter
    RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("token:blacklist:bloom");
    bloomFilter.tryInit(100_000L, 0.001); // 10万容量，0.1% 误判率
}

// 方案 B：启动时从 Redis 加载全量黑名单摘要重建 BloomFilter
// - 维护最近 7 天黑名单 Set，服务启动时批量 ADD
// - 防止重启后 BloomFilter 为空导致 Redis 压力突变
```

**预期收益**：多实例 BloomFilter 一致性保障，服务重启后 Redis 查询压力不突变。

---

### 2.14 【P3-建议】离线环境部署适配

**现状**：Maven 仓库限定 aliyun-public + aliyun-spring + central，Dockerfile 使用公共镜像。若需部署到内网离线环境（如政务云、军工），需额外适配。

**建议方案**：

1. **私有 Maven 仓库代理**（Nexus/Artifactory）
2. **离线 Docker 镜像**：`docker save/load` 流程 + 私有 Harbor 仓库
3. **离线 Helm Chart**：`helm package` + 私有 Chart Museum

---

## 三、功能增强（12 项）

### 3.1 【P0-紧急】补充缺失的数据库初始化脚本

**现状**：`deploy/sql/V1.0.0.sql` 被 pom.xml 注释引用但**文件不存在**。当前 126+ 张表无法从零初始化部署。

**建议**：**立即**从现有数据库导出完整 DDL 并补充到仓库，同时按 2.4 节的版本化方案拆分。

---

### 3.2 【P1-重要】分布式链路追踪端到端打通

**现状**：有 W3C Trace Context + OTel Agent 注入，但：
- OTel Collector 配置存在，但未被正式使用
- SkyWalking 备选但未见集成代码
- 各服务间链路未形成统一的 Trace 视图
- 无业务方法级 Span 埋点

**对标**：阿里 EagleEye 全链路追踪（RPC + DB + Cache + MQ 全覆盖），美团 CAT 做到方法级耗时分析。

**建议方案**：

```java
// 1. 关键业务方法增加 @WithSpan 埋点
@WithSpan("ProjectService.createProject")
public ProjectVO createProject(ProjectCreateDTO dto) {
    Span.current().setAttribute("project.name", dto.getName());
    Span.current().setAttribute("project.budget", dto.getBudget());
    // ...
}

// 2. MyBatis 拦截器自动 Span（已有 TraceId 注入，补充为 OTel Span）
// 3. Redis/Redisson 操作注入 Span
// 4. RocketMQ 消息生产/消费链路串联

// 5. 部署 OTel Collector + Jaeger/Tempo 后端，配置 Grafana Trace 看板
```

**预期收益**：线上问题定位从"查多个服务的日志"变成"一条 Trace 看全貌"，MTTR 降低 60%+。

---

### 3.3 【P1-重要】业务监控大盘与告警规则

**现状**：有 Micrometer + Prometheus + Sentry 基础设施，但缺少：
- 业务维度指标定义（如：每小时立项数、审批通过率、Agent Token 消耗）
- Grafana Dashboard JSON 模板
- 告警规则定义（PrometheusRule CRD）

**对标**：阿里 Sunfire 监控平台,美团 Falcon 业务大盘，均有开箱即用的业务监控模板。

**建议方案**：

```java
// 业务指标注册
@Component
public class ProjectMetrics {
    private final Counter projectCreatedCounter;
    private final Timer contractApprovalTimer;
    private final Gauge activeWorkflowInstances;

    public ProjectMetrics(MeterRegistry registry) {
        this.projectCreatedCounter = Counter.builder("ydsz.project.created")
            .description("立项数")
            .tag("tenant", tenantId)
            .register(registry);
        this.contractApprovalTimer = Timer.builder("ydsz.contract.approval.duration")
            .description("合同审批耗时")
            .register(registry);
    }
}
```

同时补充 `deploy/observability/` 下的：
- `grafana-dashboards/` — 各业务域 Dashboard JSON
- `prometheus-rules/` — 告警规则 YAML
- `alertmanager-config.yaml` — 告警路由配置

**预期收益**：从"用户反馈才知道出问题"到"系统主动告警"，问题发现提前 5-15 分钟。

---

### 3.4 【P2-优化】工作流引擎能力增强

**现状**：工作流支持 BPMN 2.0 解析 + DMN 决策表，节点类型丰富。但缺少：
- 多级审批（会签/或签/比例签）的完善实现
- 流程版本在线对比（Diff）
- 流程热更新而不影响运行中实例
- 子流程嵌套能力

**对标**：Flowable 的 CMMN + DMN 完整支持，Camunda 的流程热部署。

**建议方案**（按优先级）：

1. **流程版本管理**：新版本发布后，运行中实例继续使用原版本，新实例使用新版本
2. **流程 Diff**：模板编辑时可视化展示新旧版本差异
3. **边界事件**：超时边界事件、信号边界事件
4. **子流程**：Call Activity 调用子流程

---

### 3.5 【P2-优化】AI Agent 能力扩展

**现状**：5 种执行器（Simple/ReAct/Router/Plan-Execute/RAG）+ DAG 编排 + 调试器，能力完整。可增强方向：

| 增强方向 | 方案 | 对标 |
|---------|------|------|
| **Multi-Agent 协作** | Agent 间消息传递 + 共享记忆 | AutoGen / CrewAI |
| **Function Calling 生态** | 内置 50+ Tool（数据库查询/Excel/邮件/审批/...） | LangChain Tools |
| **模型路由** | 简单问题用小模型（qwen2.5:0.5b），复杂问题用大模型 | OpenRouter |
| **Prompt 版本管理** | Prompt 模板 Git 化管理 + A/B 测试 | LangSmith |
| **安全护栏增强** | PII 检测 + 越狱检测 + 内容审核 | Guardrails AI |
| **成本归因** | 按租户/项目/Agent 统计 Token 消耗 | OpenAI Usage API |

---

### 3.6 【P2-优化】开放 API 平台

**现状**：API Key 认证（`ApiKeyAuthFilter`）存在但 Key 是静态字符串明文存储，无 Key 管理界面。

**对标**：阿里云 API 网关（签名认证 + 流量控制 + 计量计费），腾讯云 API 3.0。

**建议方案**：

```sql
CREATE TABLE ydsz_api_key (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   VARCHAR(32) NOT NULL,
    key_name    VARCHAR(100),
    access_key  VARCHAR(64) UNIQUE,   -- AK (明文存储)
    secret_key  VARCHAR(256),         -- SK (BCrypt 哈希存储)
    permissions JSONB,                -- 允许的 API 列表
    rate_limit  INT DEFAULT 100,
    expired_at  TIMESTAMP,
    last_used_at TIMESTAMP,
    status      VARCHAR(16) DEFAULT 'ACTIVE',
    created_at  TIMESTAMP DEFAULT NOW()
);
```

- AK/SK 签名认证替代静态 Key（HMAC-SHA256 + 时间戳防重放）
- API Key 管理界面：创建/禁用/删除/用量统计
- 开放 API 文档门户（基于 SpringDoc + 自定义页面）

---

### 3.7 【P2-优化】数据导入导出增强

**现状**：EasyExcel 4.0.3 已集成。可增强：
- 大数据量异步导出（避免 HTTP 超时）
- 导入校验反馈（Excel 中标注错误行）
- 字段映射模板（用户自定义列映射）

**建议方案**：

```
用户请求导出 → 创建异步任务 → 后台生成 Excel → 上传到 MinIO → 
生成临时下载链接 → 消息通知用户（站内信/邮件）
```

---

### 3.8 【P2-优化】数据库备份与恢复自动化

**现状**：K8s 部署但未见数据库备份策略定义。

**建议方案**：
- PostgreSQL 使用 `pg_dump` + CronJob 定时备份
- 备份文件存储到 MinIO/S3
- 备份保留策略：最近 7 天日备 + 最近 4 周周备 + 最近 12 月月备
- 恢复演练脚本（每月自动执行一次）

---

### 3.9 【P2-优化】第三方集成标准化

**现状**：`ydsz-common-notify` 支持钉钉/企业微信/飞书/邮件/短信，`ydsz-workflow` 有 `thirdparty/` 包。但第三方集成方式不统一。

**建议方案**：
- 抽象 `ThirdPartyConnector` 接口，统一第三方调用的认证/重试/熔断/审计
- 增加 Webhook 回调标准格式
- 增加第三方集成健康检查

---

### 3.10 【P3-建议】移动端适配准备

**现状**：`ydsz-common-app` 模块定义了移动端基座（API 签名验证），但未在业务服务中实例化。

**建议方案**：
- 完善 `ydsz-common-app`：移动端专属安全策略 + 消息推送 + 离线支持
- 工作流审批、消息通知等高频移动场景优先适配 API

---

### 3.11 【P3-建议】数据安全合规增强

**现状**：已有 @Sensitive 脱敏、@Xss 过滤、SQL 注入防护、OWASP HTML Sanitizer。可增强：
- 数据导出审批流程
- 数据访问水印（屏幕水印 + Excel 水印）
- GDPR/个保法合规：数据删除请求支持、数据导出（Data Portability）

---

### 3.12 【P3-建议】国际化（i18n）完善

**现状**：有 `Accept-Language` 头传递和 i18n 异常消息。但：
- 无翻译管理平台集成
- 数据库存储的字典项不支持多语言
- 前端无 i18n 框架统一方案

**建议方案**：
- 集成 i18n 翻译文件管理系统
- 数据库多语言字段使用 JSONB 存储 `{"zh":"中文", "en":"English"}`
- 前端统一使用 vue-i18n

---

## 四、性能提升（10 项）

### 4.1 【P0-紧急】数据库连接池调优

**现状**：Druid 1.2.28，未见详细连接池参数调优。

**对标**：阿里数据库规范：连接池初始化 5-10，最大 20-50，验证超时 10s 内，定期清理空闲连接。

**建议配置**：

```yaml
spring:
  datasource:
    druid:
      initial-size: 10
      min-idle: 10
      max-active: 50             # 按 10 服务 × 50 = 500 总连接，需评估 PG 最大连接数
      max-wait: 5000             # 获取连接超时 5s
      time-between-eviction-runs-millis: 60000
      min-evictable-idle-time-millis: 300000  # 5 分钟空闲回收
      validation-query: SELECT 1
      test-while-idle: true
      test-on-borrow: false
      test-on-return: false
      filters: stat,wall         # 监控 + SQL 防火墙
      filter:
        stat:
          slow-sql-millis: 1000  # 慢 SQL 阈值 1s
          log-slow-sql: true
```

---

### 4.2 【P1-重要】慢 SQL 治理

**现状**：无慢 SQL 监控和优化记录。

**对标**：阿里数据库规范要求所有 SQL 执行时间 < 100ms（OLTP），美团 DB 平台自动分析慢 SQL 并生成优化建议。

**建议方案**：

1. **启用 Druid 慢 SQL 监控**（见 4.1）
2. **补充关键索引**：对 EVM 查询、报表聚合查询涉及的字段建立复合索引
3. **大表分区**：`ydsz_flow_history`（流程历史）按月分区
4. **查询优化**：对 20 个业务域的数据库操作逐一 Review SQL 执行计划

---

### 4.3 【P1-重要】全链路压测体系建设

**现状**：完全缺失。无 JMeter/Gatling/K6 等压测脚本，无容量评估数据。

**对标**：阿里双十一全链路压测、美团 SET 化压测。

**建议方案**：

```
jmeter/
├── scenarios/
│   ├── gateway-benchmark.jmx       # 网关层基准压测
│   ├── project-crud.jmx            # 项目CRUD场景
│   ├── workflow-approval.jmx       # 工作流审批场景
│   └── agent-chat.jmx              # AI对话场景
├── data/
│   └── test-users.csv             # 测试用户数据
└── run.sh                          # 压测执行脚本
```

**压测分级**：
- **基准压测**：每次上线前执行，验证不退化
- **容量压测**：每月一次，更新容量模型
- **全链路压测**：每季度一次，模拟峰值 3x 流量

**预期收益**：从"上线后才知道扛不住"到"上线前已知容量边界"。

---

### 4.4 【P1-重要】缓存策略优化

**现状**：三级缓存体系（本地 TinyLFU + Redis + DB）设计合理。可优化点：

| 优化项 | 方案 |
|-------|------|
| **缓存预热** | 启动时加载热点数据到本地缓存，避免冷启动缓存穿透 |
| **缓存空值** | 对不存在的数据也缓存空值（TTL 较短），防止缓存穿透 |
| **热点 Key 发现** | Redis `--hotkeys` 或客户端计数，自动晋升为本地缓存 |
| **缓存雪崩防护** | Redis TTL 加随机偏移（±10%），避免同时过期 |

---

### 4.5 【P2-优化】Feign 调用池化与连接复用

**现状**：Feign 使用默认的 `HttpClient`（JDK `HttpURLConnection`），不支持连接池和 HTTP/2。

**对标**：美团使用 OkHttp 连接池 + HTTP/2 多路复用。

**建议方案**：

```yaml
spring:
  cloud:
    openfeign:
      httpclient:
        hc5:
          enabled: true           # 启用 Apache HttpClient 5
      compression:
        request:
          enabled: true
          mime-types: application/json
          min-request-size: 2048
        response:
          enabled: true
      client:
        config:
          default:
            connect-timeout: 3000
            read-timeout: 10000
```

或使用 OkHttp 3/4（项目已有依赖）：

```java
@Bean
public OkHttpClient okHttpClient() {
    return new OkHttpClient.Builder()
        .connectionPool(new ConnectionPool(50, 5, TimeUnit.MINUTES))
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(new RetryInterceptor(2))
        .build();
}
```

---

### 4.6 【P2-优化】数据库读写分离深化

**现状**：baomidou `dynamic-datasource` 已集成但未见读写分离的实际路由配置验证。

**建议方案**：

```yaml
spring:
  datasource:
    dynamic:
      primary: master
      strict: true
      datasource:
        master:
          url: jdbc:postgresql://pg-master:5432/ydsz
        slave1:
          url: jdbc:postgresql://pg-slave1:5432/ydsz
        slave2:
          url: jdbc:postgresql://pg-slave2:5432/ydsz
      strategy: org.baomidou.dynamic.datasource.strategy.LoadBalanceDynamicDataSourceStrategy
```

```java
// 使用注解强制指定数据源
@DS("slave")   // 读操作
public List<ProjectVO> listProjects() { ... }

@DS("master")  // 写操作（默认）
public void createProject(ProjectDTO dto) { ... }
```

---

### 4.7 【P2-优化】JSON 序列化性能优化

**现状**：自研 `ydsz-common-json` 模块（ASM 字节码 + SIMD 向量化 + Schema 校验），设计先进。建议：
- 补充 **JMH Benchmark 报告**，与 fastjson2/jackson 的量化对比
- 对 Web 层的 Request/Response 统一使用 `ydsz-common-json` 替换默认 Jackson
- 评估 Java 21 Virtual Thread 场景下的 JSON 序列化行为

---

### 4.8 【P2-优化】JVM 参数精细化调优

**现状**：Dockerfile 使用 `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC`。

**对标**：阿里 Dragonwell JDK 调优、美团 JVM 参数实践。

**建议补充**：

```bash
JAVA_OPTS="
  -XX:+UseContainerSupport
  -XX:MaxRAMPercentage=75.0
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
  -XX:G1HeapRegionSize=4m
  -XX:InitiatingHeapOccupancyPercent=45
  -XX:G1ReservePercent=10
  -XX:+ParallelRefProcEnabled
  -XX:+ExitOnOutOfMemoryError
  -XX:+HeapDumpOnOutOfMemoryError
  -XX:HeapDumpPath=/tmp/heapdump.hprof
  -Xlog:gc*:file=/tmp/gc.log:time,level,tags:filecount=10,filesize=100M
"
```

---

### 4.9 【P3-建议】静态资源 CDN 化

**现状**：未见前端静态资源加速方案。

**建议**：前端 build 产物上传到阿里云 OSS + CDN，或使用 EdgeOne Pages。

---

### 4.10 【P3-建议】Gateway 过滤器链优化

**现状**：9 个自定义 GlobalFilter 按固定 Order 执行，每个都做完整的请求路径处理。

**建议**：
- 将高频过滤逻辑（鉴权、限流）前置到 Netty 路由层
- 对静态资源/健康检查路径短路跳过完整过滤链
- 使用 `ServerWebExchange.getAttributes()` 缓存跨过滤器计算结果

---

## 五、体验改善（10 项）

### 5.1 【P1-重要】本地开发环境一键启动

**现状**：本地开发需手动启动 Nacos + Redis + PostgreSQL + RocketMQ + 10 个微服务。

**对标**：Spring Modulith 一键启动, 字节 Mono 开发环境（docker-compose 一键拉起）。

**建议方案**：

```yaml
# docker-compose.dev.yml
version: '3.9'
services:
  postgres:
    image: pgvector/pgvector:pg17
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: ydsz
      POSTGRES_USER: ydsz
      POSTGRES_PASSWORD: ydsz123
    volumes:
      - ./deploy/sql/:/docker-entrypoint-initdb.d/

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

  nacos:
    image: nacos/nacos-server:v2.3.2
    ports: ["8848:8848", "9848:9848"]
    environment:
      MODE: standalone

  rocketmq:
    image: apache/rocketmq:5.3.3
    # ...

  otel-collector:
    image: otel/opentelemetry-collector-contrib:latest
    # ...
```

```bash
# scripts/dev-start.sh
#!/bin/bash
docker-compose -f docker-compose.dev.yml up -d
# 等待基础设施就绪
./mvnw spring-boot:run -pl ydsz-gateway &
./mvnw spring-boot:run -pl ydsz-system &
# ...
```

**预期收益**：新成员入职搭建环境从 2 天缩短到 30 分钟。

---

### 5.2 【P1-重要】补充运维 Runbook

**现状**：无运维操作手册。

**对标**：Google SRE 要求每个服务有完整的 Runbook（故障处理 SOP + 操作手册）。

**建议创建**：

```
docs/runbooks/
├── README.md                  # 总览
├── deployment.md              # 部署操作（首次部署/滚动更新/回滚）
├── failure-handling.md       # 故障处理 SOP
│   ├── gateway-503.md        # 网关 503 故障
│   ├── nacos-unavailable.md  # Nacos 不可用
│   ├── redis-outage.md       # Redis 故障
│   ├── postgres-slow.md      # 数据库变慢
│   └── rocketmq-backlog.md   # 消息积压
├── capacity-planning.md      # 容量规划指南
├── backup-restore.md         # 备份恢复
└── security-incident.md      # 安全事件响应
```

---

### 5.3 【P2-优化】代码生成器（新模块脚手架）

**现状**：创建新业务模块需手动复制 5 层结构 + 配置文件。10 个部署单元均手工创建。

**对标**：JHipster / 阿里云效初始化器。

**建议方案**：

```bash
# 命令行交互式生成新模块
./scripts/gen-module.sh

> 请输入模块名: ydsz-invoice
> 请输入端口号: 9010
> 请输入业务域(逗号分隔): invoice,invoice_item
> 是否需要消息队列? [y/N]: y
> 是否需要工作流集成? [y/N]: y
> 生成中...
> ✓ ydsz-invoice-api
> ✓ ydsz-invoice-domain
> ✓ ydsz-invoice-infra
> ✓ ydsz-invoice-server
> ✓ ydsz-invoice-web
> 模块已生成: ydsz-backend/ydsz-invoice/
```

使用 Maven Archetype 或 Yeoman Generator 实现。

**预期收益**：新模块创建从 2-4 小时降到 5 分钟。

---

### 5.4 【P2-优化】测试覆盖率提升

**现状**：Line Coverage >= 60%, Branch >= 50%，仅刚过及格线。

**对标**：Google 要求 85%+，阿里核心链路 90%+。

**提升路径**：

| 阶段 | 目标 | 措施 |
|------|------|------|
| **Phase 1** | Line 75% / Branch 65% | 补齐核心 Service 层单测 |
| **Phase 2** | Line 85% / Branch 75% | 补齐 Controller 层集成测试（@WebMvcTest） |
| **Phase 3** | Line 90% / Branch 80% | 补齐跨服务场景端到端测试 |

---

### 5.5 【P2-优化】集成测试环境自动化

**现状**：有 `AbstractIntegrationTest` + Testcontainers，但仅在 CI 中执行。

**建议方案**：
- **本地集成测试**：开发阶段本地跑 Testcontainers（自动启动 PG + Redis + RocketMQ）
- **CI 集成测试**：PR 阶段运行完整集成测试套件
- **预发环境**：部署到 K8s Dev/Staging Namespace 后运行 smoke test
- **契约测试**：引入 Spring Cloud Contract 验证服务间 API 兼容性

---

### 5.6 【P2-优化】API 文档增强

**现状**：Knife4j 4.5.0 + SpringDoc，但：
- 各服务 API 文档分散，无统一文档门户
- 缺少请求/响应示例自动生成
- 缺少 API 变更日志自动生成

**建议方案**：

1. 网关层聚合各服务 OpenAPI 文档为统一门户
2. `@Operation` 注解补充完整的 `summary` + `description` + `example`
3. 每个接口补充 Markdown 格式的调用示例
4. 自动生成 API SDK（TypeScript/Java）

---

### 5.7 【P2-优化】错误码体系完善

**现状**：有统一错误码 `ProblemDetail`（RFC 7807），但：
- 错误码定义分散在各模块
- 缺少错误码文档自动生成
- 前端无统一错误码映射表

**建议方案**：

```java
// 枚举化错误码，便于管理和文档生成
public enum ErrorCode {
    PROJECT_NOT_FOUND("PJ-001", "项目不存在", "Project with ID %s not found"),
    CONTRACT_AMOUNT_INVALID("CT-001", "合同金额无效", "Contract amount must be positive"),
    // ...
}

// CI 中自动生成错误码 Markdown 文档
// docs/error-codes.md
```

---

### 5.8 【P2-优化】Git 提交规范与 Changelog 自动生成

**现状**：Lefthook 有 commitlint 但未见 Changelog 自动生成。

**对标**：Conventional Commits + semantic-release。  

**建议**：
- `commitlint` 规则改为 Conventional Commits（`feat:`/`fix:`/`docs:`/`refactor:`）
- CI 中基于 commit message 自动生成 CHANGELOG.md
- 结合 Git Tag 自动打版本号

---

### 5.9 【P3-建议】开发者门户/内部文档站

**现状**：30 个 common 模块均有 README，但缺少统一索引和搜索能力。

**建议方案**：
- 集成 ydsz-nextwiki 知识库模块（自举：用自己的 Wiki 服务管理自己的文档）
- 或用 MkDocs/Vitepress 生成静态文档站（CI 自动部署）

---

### 5.10 【P3-建议】依赖版本自动化更新

**现状**：30+ 依赖项版本均由 pom.xml 手工维护。

**对标**：Dependabot / Renovate 自动检测依赖更新并提 PR。

**建议方案**：

```yaml
# .github/renovate.json
{
  "$schema": "https://docs.renovatebot.com/renovate-schema.json",
  "extends": ["config:base"],
  "packageRules": [
    {
      "matchPackagePatterns": ["org.springframework.*"],
      "groupName": "Spring Framework"
    }
  ]
}
```

---

## 六、实施路线图

### 6.1 优先级定义

| 级别 | 定义 | 建议周期 |
|------|------|---------|
| **P0-紧急** | 影响系统可用性或存在重大工程风险 | 1-2 周内 |
| **P1-重要** | 直接影响研发效率或线上稳定性 | 1-2 月内 |
| **P2-优化** | 显著提升系统能力或性能 | 3-6 月内 |
| **P3-建议** | 长期竞争力提升 | 6-12 月内 |

### 6.2 分阶段实施计划

#### Phase 1：补短板（第 1-4 周）— 6 项 P0

| 序号 | 项目 | 类别 |
|------|------|------|
| 2.1 | 补齐 CI/CD 流水线 | 架构优化 |
| 2.2 | JWT 缓存击穿防护 | 架构优化 |
| 3.1 | 补充数据库初始化脚本 | 功能增强 |
| 4.1 | 数据库连接池调优 | 性能提升 |

#### Phase 2：提能力（第 5-8 周）— 8 项 P1

| 序号 | 项目 | 类别 |
|------|------|------|
| 2.3 | 网关 CORS 配置规范化 | 架构优化 |
| 2.4 | 数据库 Schema 版本化管理 | 架构优化 |
| 2.5 | 限流降级分布式协调 | 架构优化 |
| 2.6 | API 版本管理机制 | 架构优化 |
| 3.2 | 分布式链路追踪端到端打通 | 功能增强 |
| 3.3 | 业务监控大盘与告警规则 | 功能增强 |
| 4.2 | 慢 SQL 治理 | 性能提升 |
| 4.3 | 全链路压测体系建设 | 性能提升 |
| 4.4 | 缓存策略优化 | 性能提升 |
| 5.1 | 本地开发环境一键启动 | 体验改善 |
| 5.2 | 补充运维 Runbook | 体验改善 |

#### Phase 3：建优势（第 9-24 周）— P2 + P3

按业务优先级选择性实施架构优化（2.7-2.14）、功能增强（3.4-3.12）、性能提升（4.5-4.10）、体验改善（5.3-5.10）中的各项。

### 6.3 风险提示

1. **gRPC 改造**（2.7）风险较高，建议仅在 1-2 个高频调用路径试点
2. **CQRS 引入**（2.8）增加架构复杂度，建议先在 EVM/报表场景试点
3. **全链路压测**（4.3）需在测试环境执行，首次建议在低峰期小流量开始
4. **数据库 Schema 版本化**（2.4）需与现有"禁止 Flyway"规范协调，建议先用脚本版本化过渡

---

## 附录：对标参考清单

| 对标企业 | 参考项 | 相关建议 |
|---------|--------|---------|
| **阿里巴巴** | Aone CI/CD、Tair 缓存、Dubbo3 Triple、EagleEye 链路追踪、Sentinel 集群流控 | 2.1, 2.2, 2.5, 2.7, 3.2 |
| **腾讯** | 北极星服务治理、蓝盾 CI/CD、TSF 微服务平台 | 2.1, 2.3 |
| **字节跳动** | Kitex RPC、Mono 开发环境、Atlas-Go Schema 管理 | 2.4, 2.7, 5.1 |
| **美团** | OCTO 服务治理、CAT 监控、DB 慢 SQL 治理、全链路压测 | 3.2, 3.3, 4.2, 4.3 |
| **携程** | Apollo 配置中心 | 2.12 |
| **Google** | SRE Runbook、API Design Guide、gRPC、CQRS/Event Sourcing | 2.8, 2.11, 5.2 |
| **Netflix** | Hystrix 熔断、Zuul 网关、Chaos Monkey | 2.10, 3.2 |
| **AWS** | Well-Architected Framework | 全文 |
