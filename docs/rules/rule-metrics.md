<!--
  ===========================================================================
  文件名: rule-metrics.md
  路径:   docs/rules/rule-metrics.md
  作用:   LiteRule 1.4.0+ Prometheus 指标说明：启用方式、配置项、关键指标
  关联:   ydsz-pmis-literule 源码  /  rule-canary.md
  ===========================================================================
-->

# LiteRule 规则引擎 Prometheus 指标说明（1.4.0 起）

## 启用方式

### 1. 引入依赖

`ydsz-pmis-literule` 已在 `pom.xml` 中以 `optional` 形式引入 `micrometer-registry-prometheus`。
消费方（如 execution 模块）只要确保 classpath 有 `spring-boot-starter-actuator` + `micrometer-registry-prometheus`，LiteRule 会自动桥接。

### 2. 配置

`application.yaml` 中：

```yaml
pmis:
  literule:
    enabled: true
    stats-enabled: true        # 启用内存统计（已有）
    trace-enabled: true        # 启用 Trace 持久化（1.4.0）
    rule-timeout-ms: 3000      # 单规则超时（毫秒，0=不限制）
    circuit-breaker-error-rate: 0.5
    circuit-breaker-min-evaluations: 100

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    tags:
      application: ydsz-pmis-execution
```

### 3. 验证

访问 `http://localhost:8080/actuator/prometheus`，应能看到 `literule_*` 指标。

## 指标清单

| 指标 | 类型 | 标签 | 说明 |
|---|---|---|---|
| `literule_rule_evaluations_total` | Counter | rule_code, scenario | 评估总次数 |
| `literule_rule_triggered_total` | Counter | rule_code, severity | 触发总次数 |
| `literule_rule_errors_total` | Counter | rule_code | 异常总次数 |
| `literule_rule_eval_duration_seconds` | Timer | rule_code | 评估耗时分布（P50/P95/P99） |
| `literule_breaker_state` | Gauge | rule_code | 熔断状态（0=CLOSED,1=OPEN,2=HALF_OPEN） |
| `literule_trace_queue_size` | Gauge | — | Trace 异步队列积压 |

## PromQL 常用查询

```promql
# 1. 每秒评估 QPS（按规则）
sum(rate(literule_rule_evaluations_total[1m])) by (rule_code)

# 2. 命中率
sum(rate(literule_rule_triggered_total[5m])) by (rule_code)
  / sum(rate(literule_rule_evaluations_total[5m])) by (rule_code)

# 3. P95 评估延迟
histogram_quantile(0.95, rate(literule_rule_eval_duration_seconds_bucket[5m]))

# 4. 异常率
sum(rate(literule_rule_errors_total[5m])) by (rule_code)
  / sum(rate(literule_rule_evaluations_total[5m])) by (rule_code)

# 5. 当前处于 OPEN 状态的规则数
count(literule_breaker_state == 1)

# 6. Trace 队列积压
literule_trace_queue_size
```

## 告警规则示例

将以下内容追加到 `deploy/monitoring/prometheus/rules/pmis-alerts.yml`：

```yaml
- alert: LiteRuleHighErrorRate
  expr: |
    sum(rate(literule_rule_errors_total[5m])) by (rule_code)
      / sum(rate(literule_rule_evaluations_total[5m])) by (rule_code) > 0.1
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "规则 {{ $labels.rule_code }} 错误率 > 10%"

- alert: LiteRuleCircuitBreakerOpen
  expr: literule_breaker_state == 1
  for: 1m
  labels:
    severity: critical
  annotations:
    summary: "规则 {{ $labels.rule_code }} 熔断器 OPEN"

- alert: LiteRuleTraceQueueBacklog
  expr: literule_trace_queue_size > 1000
  for: 2m
  labels:
    severity: warning
  annotations:
    summary: "Trace 队列积压 > 1000，可能写入瓶颈"

- alert: LiteRuleSlowEvaluation
  expr: |
    histogram_quantile(0.95, rate(literule_rule_eval_duration_seconds_bucket[5m])) > 1
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "规则 {{ $labels.rule_code }} P95 评估延迟 > 1s"
```

## Grafana 看板

可基于上述指标自建看板，建议面板：
1. 概览：总 QPS / 总命中率 / 总错误率 / OPEN 熔断数
2. 规则明细表：按 rule_code 分组的 QPS / 命中率 / P95 延迟 / 错误率
3. 熔断状态热力图：rule_code × 时间
4. Trace 队列积压趋势
