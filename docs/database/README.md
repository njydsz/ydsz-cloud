# 数据库读写分离 — 现有实现梳理与生产深化

> P2-优化：ydsz-common-jdbc 已有完整的读写分离实现，本文档梳理现有能力并给出生产就绪验证清单

## 一、现有实现概览

### 1.1 核心模块位置

```
ydsz-common/ydsz-common-jdbc/
├── annotation/
│   └── DS.java                              # 数据源切换注解（支持 SpEL）
├── datasource/
│   ├── DynamicRoutingDataSource.java         # 动态路由数据源（栈式嵌套）
│   ├── DynamicDataSourceContextHolder.java   # ThreadLocal 上下文（ArrayDeque 栈）
│   ├── DynamicDataSourceAutoConfiguration.java
│   └── DsAnnotationInterceptor.java          # @DS 注解 AOP 拦截器
├── interceptor/
│   └── ReadWriteSplittingInterceptor.java    # MyBatis 自动读写分离拦截器
├── config/
│   ├── ReadWriteSplittingProperties.java     # 配置属性
│   ├── ReadWriteSplittingAutoConfiguration.java
│   ├── DataSourceLoadBalanceStrategy.java    # 负载均衡策略接口
│   ├── RandomLoadBalanceStrategy.java        # 随机策略
│   ├── RoundRobinLoadBalanceStrategy.java    # 轮询策略（默认）
│   └── WeightedLoadBalanceStrategy.java      # 权重策略
├── monitor/
│   └── DatabaseCircuitBreaker.java           # 轻量熔断器（CLOSED/OPEN/HALF_OPEN）
├── health/
│   ├── DataSourceHealthIndicator.java        # 数据源健康检查
│   └── DynamicDataSourceHealthIndicator.java
└── constant/
    └── DataSourceConstants.java
```

### 1.2 已有能力矩阵

| 能力 | 实现状态 | 说明 |
|------|----------|------|
| 动态数据源路由 | ✅ 完整 | `AbstractRoutingDataSource` + `DynamicDataSourceContextHolder` |
| 栈式嵌套切换 | ✅ 完整 | `ArrayDeque` 支持方法级覆盖类级 |
| @DS 注解 | ✅ 完整 | 类级 + 方法级，支持 SpEL 表达式 |
| MyBatis 自动读写分离 | ✅ 完整 | `ReadWriteSplittingInterceptor` 自动识别 SQL 类型 |
| 事务感知 | ✅ 完整 | `@Transactional` 强制读主库，避免读写不一致 |
| 负载均衡 | ✅ 完整 | 轮询（默认）/ 随机 / 权重 三种策略 |
| 熔断器 | ✅ 完整 | 自研轻量熔断器 + Micrometer 指标 |
| 健康检查 | ✅ 完整 | Spring Boot Actuator 集成 |
| 动态增删数据源 | ✅ 完整 | `addDataSource` / `removeDataSource` 运行时操作 |
| 多租户数据源 | ✅ 支持 | `tenant-ds` 路由（common-tenant 模块） |

### 1.3 核心代码说明

#### DynamicRoutingDataSource

`ydsz-common-jdbc/datasource/DynamicRoutingDataSource.java` — 已实现的动态路由数据源，无需新建：

- 继承 `AbstractRoutingDataSource`
- `determineCurrentLookupKey()` 从 `DynamicDataSourceContextHolder` 栈顶读取
- 支持运行时 `addDataSource` / `removeDataSource`
- 使用 `ConcurrentHashMap` 保证线程安全

#### DynamicDataSourceContextHolder

`ydsz-common-jdbc/datasource/DynamicDataSourceContextHolder.java` — 栈式 ThreadLocal 上下文：

- 使用 `ArrayDeque` 实现栈结构（push/poll/peek）
- 支持方法级 `@DS` 嵌套覆盖类级
- 使用 `NamedThreadLocal` 自动清理，避免线程池复用泄漏

#### @DS 注解

`ydsz-common-jdbc/annotation/DS.java` — 支持类级 / 方法级，SpEL 表达式：

```java
@Service
@DS("slave")                       // 类级：默认走从库
public class ReportQueryService {
    
    @DS("master")                  // 方法级：覆盖类级
    public void updateReport(...) { }
    
    @DS("#tenant.dbSource")        // SpEL 动态解析
    public List<Data> queryByTenant() { }
}
```

#### ReadWriteSplittingInterceptor

`ydsz-common-jdbc/interceptor/ReadWriteSplittingInterceptor.java` — MyBatis 自动读写分离：

- 拦截 `Executor.query` 和 `Executor.update`
- `SELECT` + 非事务 → 从库（负载均衡策略选择）
- `SELECT` + 事务中 → 主库（保证读写一致性）
- `INSERT/UPDATE/DELETE` → 主库
- `try-finally` 保证 `DynamicDataSourceContextHolder.poll()` 一定执行

#### DatabaseCircuitBreaker

`ydsz-common-jdbc/monitor/DatabaseCircuitBreaker.java` — 自研轻量熔断器：

- 状态机：`CLOSED` → `OPEN` → `HALF_OPEN` → `CLOSED`
- 连续失败 N 次后自动触发熔断
- 熔断持续时间后进入半开探测
- 提供 `bindTo(MeterRegistry)` 暴露 Prometheus 指标

---

## 二、配置属性说明

### 2.1 完整配置参考

```yaml
ydsz:
  jdbc:
    # 读写分离主开关
    read-write-splitting:
      enabled: true
      master-ds: master                    # 主库数据源名
      slave-ds-list:                       # 从库列表
        - slave1
        - slave2
      load-balance-strategy: round-robin   # 负载均衡：round-robin | random | weighted
      weights:                             # weighted 策略下的权重
        slave1: 2
        slave2: 1
    
    # 动态数据源开关（@DS 注解）
    dynamic-datasource:
      enabled: true
    
    # 熔断器配置
    circuit-breaker:
      enabled: true
      failure-threshold: 10          # 连续失败次数阈值
      open-duration-millis: 30000    # 熔断持续时间
      half-open-probe-size: 3        # 半开探测请求数
```

### 2.2 启用方式

在 Nacos 配置或 `application.yml` 中添加：

```yaml
ydsz:
  jdbc:
    read-write-splitting:
      enabled: true
      master-ds: master
      slave-ds-list: [slave1, slave2]
```

启用后框架自动：
1. 注册 `ReadWriteSplittingInterceptor` 到 MyBatis
2. SELECT 自动路由到 slave（按负载均衡策略选择）
3. INSERT/UPDATE/DELETE 自动路由到 master
4. 事务内的 SELECT 强制路由到 master

---

## 三、监控与健康检查

### 3.1 已有的监控指标

| 指标 | 类型 | 说明 |
|------|------|------|
| `dbc.circuitbreaker.state` | Gauge | 熔断器状态（0=CLOSED, 1=OPEN, 2=HALF_OPEN） |
| `dbc.circuitbreaker.consecutive.failures` | Gauge | 连续失败次数 |
| `datasource.{name}.active` | Gauge | 连接池活跃连接数 |
| `datasource.{name}.idle` | Gauge | 连接池空闲连接数 |
| `datasource.{name}.max` | Gauge | 连接池最大连接数 |
| 健康检查端点 | HTTP | `/actuator/health` 含数据源健康状态 |

### 3.2 Prometheus 告警规则（建议补充）

```yaml
# deploy/observability/prometheus-rules/datasource-alerts.yml
groups:
  - name: datasource-read-write-splitting
    rules:
      # 熔断器打开时告警
      - alert: DataSourceCircuitBreakerOpen
        expr: dbc.circuitbreaker.state == 1
        for: 30s
        labels:
          severity: critical
        annotations:
          summary: "数据库熔断器打开"
          description: "{{ $labels.datasource }} 熔断器已打开，数据库请求被拒绝"
      
      # 从库不可用（健康检查失败）
      - alert: DataSourceSlaveDown
        expr: datasource_healthy{name=~"slave.*"} == 0
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "从库 {{ $labels.name }} 不可用"
      
      # 从库复制延迟过高
      - alert: ReplicationLagHigh
        expr: pg_replication_lag_seconds > 5
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "复制延迟过高: {{ $value }}s"
```

---

## 四、生产就绪验证清单

### 4.1 功能验证（建议在 SIT 环境执行）

| 验证项 | 方法 | 预期结果 |
|--------|------|----------|
| SELECT 路由到从库 | 查看 MyBatis 日志（参数 `endpoint=slave`） | 查询走 slave1/slave2 |
| INSERT/UPDATE/DELETE 路由到主库 | 查看日志确认 master | 写入走 master |
| 事务内 SELECT 走主库 | `@Transactional` 中执行查询 | 事务内始终 master |
| 负载均衡生效 | 多次查询观察分布 | slave1/slave2 均衡 |
| 熔断器触发 | 模拟从库宕机 | 熔断器打开 → 告警 |
| 熔断器恢复 | 从库恢复后 | HALF_OPEN → CLOSED |
| @DS 生效 | 方法标注 `@DS("master")` | 强制走 master |
| 动态增删数据源 | 调用 `addDataSource()` | 运行时生效 |

### 4.2 PostgreSQL 主从配置建议

```sql
-- 主库 postgresql.conf
wal_level = replica
max_wal_senders = 10
wal_keep_size = 1GB
hot_standby = on

-- 创建复制用户
CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD 'xxx';
```

### 4.3 性能基线建议

| 场景 | 目标 P99 | 备注 |
|------|----------|------|
| 单主库写 | ≤ 50ms | 写操作 |
| 主从读（从库） | ≤ 30ms | 查询操作 |
| 主从读（主库） | ≤ 40ms | 事务内查询 |
| 路由切换 | ≤ 1ms | 仅 ThreadLocal 操作 |

---

## 五、与现有 Nacos 配置的关系

### 5.1 共享 Nacos 配置片段

现有 `ydsz-common-datasource.yaml` 需要补充读写分离配置：

```yaml
# Nacos data-id: ydsz-common-datasource.yaml 追加
spring:
  datasource:
    druid:
      master:
        url: jdbc:postgresql://${PG_MASTER_HOST:127.0.0.1}:5432/ydsz
        username: ${PG_USER:ydsz}
        password: ${PG_PASSWORD:ydsz}
        initial-size: 10
        max-active: 30
        # ... 现有 Druid 配置保持不变
      
      slave1:
        url: jdbc:postgresql://${PG_SLAVE1_HOST:127.0.0.1}:5433/ydsz?targetSessionAttrs=read-only
        username: ${PG_USER:ydsz}
        password: ${PG_PASSWORD:ydsz}
        initial-size: 10
        max-active: 50
      
      slave2:
        url: jdbc:postgresql://${PG_SLAVE2_HOST:127.0.0.1}:5434/ydsz?targetSessionAttrs=read-only
        username: ${PG_USER:ydsz}
        password: ${PG_PASSWORD:ydsz}
        initial-size: 10
        max-active: 50

# 启用读写分离
ydsz:
  jdbc:
    read-write-splitting:
      enabled: true
      master-ds: master
      slave-ds-list: [slave1, slave2]
      load-balance-strategy: round-robin
```

### 5.2 独立配置示例

完整可独立部署的配置示例见：`deploy/config/datasource/ydsz-common-datasource-readwrite.yaml`

---

## 六、常见问题

### Q: 读写分离已内置，还需要做什么？

A: **ydsz-common-jdbc 已实现自动读写分离的核心逻辑，无需新建代码**。需要做的：
1. 在 Nacos 配置中启用 `ydsz.jdbc.read-write-splitting.enabled=true`
2. 搭建 PostgreSQL 从库（DBA 执行）
3. 补充 Prometheus 告警规则
4. SIT 环境验证路由正确性

### Q: 多个从库如何配置权重？

A: 使用 `weighted` 策略：
```yaml
ydsz:
  jdbc:
    read-write-splitting:
      load-balance-strategy: weighted
      weights:
        slave1: 3
        slave2: 1
```

### Q: 特定查询强制走主库？

A: 使用 `@DS("master")` 注解：
```java
public class OrderService {
    
    @DS("master")  // 强制走主库
    public Order getOrderFresh(Long id) {
        return orderMapper.selectById(id);
    }
}
```

### Q: 事务内为什么读主库？

A: 保证读写一致性。`@Transactional` 内如果读到从库（复制延迟），可能读到旧数据导致业务逻辑错误。`ReadWriteSplittingInterceptor` 已内置此逻辑。

---

## 七、总结

| 项 | 结论 |
|----|------|
| 是否需要新建代码 | **否**，ydsz-common-jdbc 已完整实现 |
| 需要做什么 | Nacos 配置启用 + 从库搭建 + 监控告警 + 验证 |
| 预估工作量 | DBA 搭建从库 1-2 天 + 配置启用 0.5 天 + 验证 1 天 |

---

> 文档更新: 2026-08-04 | 维护人: ydsz-team
