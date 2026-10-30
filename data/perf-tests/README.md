# 云顶云平台 Gatling 性能测试

本目录包含云顶云平台（ydsz-cloud）的 Gatling 性能测试框架，用于验证核心接口的响应时间和吞吐量。

---

## 目录结构

```
data/perf-tests/
├── pom.xml                        # Gatling 子模块 POM
├── run-perf-test.ps1             # PowerShell 一键压测脚本
├── src/
│   └── test/
│       └── scala/
│           └── CloudPerfTest.scala  # Simulation 定义（轻载 + 压力）
└── target/
    └── gatling-results/           # 压测报告输出目录（HTML + CSV）
```

---

## 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 21+ | 与项目主版本一致 |
| Maven | 3.9+ | 用于依赖管理 |
| Gatling | 3.10.5 | 在 pom.xml 中声明 |
| Scala | 2.13.14 | Gatling 运行时（scala-maven-plugin 管理） |
| PowerShell | 5.1+ / 7.x | 运行 `run-perf-test.ps1` |

---

## 快速开始

### 方式一：PowerShell 一键启动（推荐）

```powershell
# 默认压测本地网关 http://localhost:9000
.\run-perf-test.ps1

# 指定目标环境
.\run-perf-test.ps1 -BaseUrl "http://sit.example.com:9000"

# 跳过依赖下载（已下载过时加速）
.\run-perf-test.ps1 -SkipDependencyDownload
```

### 方式二：Maven 命令行

```bash
# 全部 Simulation
mvn test -pl data/perf-tests -P perf-test -f ../../pom.xml

# 仅运行轻载模拟（50 并发 x 1min）
mvn gatling:test -pl data/perf-tests -P perf-test \
  -Dgatling.simulationClass=com.njydsz.perf.CloudPerfTest

# 指定目标环境
mvn gatling:test -pl data/perf-tests -P perf-test \
  -DbaseUrl=http://sit.example.com:9000
```

### 方式三：IDE 直接运行

在 IntelliJ IDEA 中打开 `CloudPerfTest.scala`，右键 `class CloudPerfTest extends Simulation`，
选择 **Run 'CloudPerfTest'**。需先执行 `mvn test-compile -P perf-test` 生成 class。

---

## 测试方案说明

### Simulation 1：LightLoad（轻载基线）

| 参数 | 值 |
|------|-----|
| 并发用户 | 50 |
| Ramp-up | 5 秒 |
| 持续时间 | 1 分钟 |
| Ramp 策略 | 恒定并发 |

**通过标准：**

- P95 响应时间 < 500ms
- 错误率 < 1%
- 平均 QPS ≥ 100

### Simulation 2：StressLoad（压力测试）

| 参数 | 值 |
|------|-----|
| 并发用户 | 0 → 200（阶梯） |
| 阶梯 | 0 → 50 → 100 → 150 → 200，每级 30s |
| 阶梯间隔 | 10s |
| 持续时间 | ~3 分钟 |

**观测指标：**

- 找到 QPS 线性增长的拐点（并发数）
- 拐点后响应时间是否急剧上升（> 2s）
- 服务是否出现 5xx 错误（服务崩溃标志）

---

## 测试端点

> **重要说明**：为避免污染业务数据或触发熔断，本框架默认仅压测 Actuator 健康检查端点。

| 端点 | 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|------|
| 健康检查 | GET | `/actuator/health` | 无 | 验证网关 + Spring Boot 存活 |
| 应用信息 | GET | `/actuator/info` | 无 | 验证 Actuator 正常响应 |

如需压测业务接口（如 `/api/system/dict/list`、`/api/user/menu`）：

1. 确认目标服务已启动且数据库连接正常
2. 在 `CloudPerfTest.scala` 中添加对应 scenario
3. 如有认证要求，补充 `.header("Authorization", "Bearer ${token}")` 和 token 获取 feeder
4. 在小范围验证后再加入持续集成

---

## 基线参考值（初始参考，实际以首次运行为准）

> 以下数据基于本地开发环境（16GB RAM / 8核 / PostgreSQL 18 HikariCP=10 连接），
> 仅作首次运行的参照基线，不作为生产 SLA。

| 指标 | 轻载目标 | 压力峰值期望 |
|------|---------|-------------|
| P95 响应时间 | < 500ms | < 2000ms |
| P99 响应时间 | < 800ms | < 5000ms |
| 平均 QPS | ≥ 100 | ≥ 500 |
| 错误率 | < 0.1% | < 1% |
| CPU 使用率 | < 30% | < 80% |
| 内存增长率 | < 10%/min | < 20%/min |

---

## 解读压测报告

### HTML 报告结构

每次运行后，在 `target/gatling-results/<simulation-timestamp>/` 下生成：

```
gatling-results/
└── gatling-perftest-20261028-143012/
    ├── index.html          # 主报告（浏览器打开）
    ├── js/                 # Highcharts 图表数据
    ├── css/                # 样式
    └── simulation.log      # 原始请求日志（用于排查）
```

关键图表解读：

1. **响应时间分布（Response Time Percentiles）**：P50/P95/P99 曲线
   - 平稳：服务稳定
   - 锯齿状：GC 抖动或连接池等待
   - 持续上升：资源瓶颈（CPU / DB 连接 / 线程池）

2. **活跃用户数（Active Users）**：确认实际并发是否符合注入策略

3. **吞吐量（Requests per Second）**：QPS 曲线
   - 线性增长：服务未达瓶颈
   - 平台期：到达吞吐量上限
   - 下降：服务过载（需排查错误）

4. **错误率（Error Percentage）**：5xx 视为严重问题

### CSV 输出

如需进一步分析（导入 Excel / Python），可在代码中追加：

```scala
// 在 httpProtocol 后追加
.extraInfoExtractor { extraInfo =>
    List(extraInfo.request.getUrl, extraInfo.response.statusCode.getOrElse(-1))
}
```

---

## 踩坑与注意事项

1. **文件描述符限制**：Linux/macOS 默认 ulimit 较小（1024），压测前执行：
   ```bash
   ulimit -n 65536
   ```

2. **端口占用**：确保目标端口（默认 9000）未被防火墙阻断

3. **热机（Warm-up）**：首次运行可能有 JIT 编译开销，建议丢弃前 30 秒数据

4. **数据库连接**：压测间接经过数据库的接口时，确认 HikariCP max pool size 足够

5. **生产环境**：**严禁**直接压测生产环境。必须在 SIT / UAT 环境经负责人批准后方可执行。

---

## 扩展：添加自定义 Scenario

```scala
// 1. 在 CloudPerfTest.scala 中添加新 scenario
val customApiScenario = scenario("CustomAPI")
    .feed(tenantFeeder)
    .exec(
        http("api-endpoint")
            .get("/api/your/endpoint")
            .queryParam("tenantId", "${tenantId}")
            .header("Authorization", "Bearer ${token}") // 需补充 token 获取逻辑
            .check(status.is(200))
            .check(jsonPath("$.code").is("0"))
    )

// 2. 在 setUp() 中注入
setUp(
    customApiScenario.inject(
        rampUsers(100).during(30.seconds)
    ).protocols(httpProtocol)
)
```

## 相关资源

- [Gatling 官方文档](https://gatling.io/docs/gatling/)
- [Gatling HTTP 参考](https://gatling.io/docs/gatling/reference/current/http/protocol/)
- [项目 CLAUDE.md - 编译与验证](../ydsz-cloud/CLAUDE.md)
