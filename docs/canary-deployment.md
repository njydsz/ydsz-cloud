# 金丝雀发布 SOP (批次 20 P3-2)

> 本文档描述 PMIS 系统金丝雀发布的完整流程, 包括流量切换 / 监控观察 / 决策回滚.

---

## 1. 金丝雀发布流程图

```
[Build] → [Stage 环境] → [Canary 5%] → [Canary 25%] → [Canary 50%] → [100%]
                              ↓ 1h 监控     ↓ 1h 监控    ↓ 1h 监控      ↓
                          告警/回滚      告警/回滚    告警/回滚       稳定运行
```

## 2. 流量切分策略

### 2.1 基于 Feature Flag 的灰度

通过 `FeatureFlagService.setRolloutPercentage(flag, percentage)` 设置灰度比例,
灰度逻辑由后端 `LocalFeatureFlagService.isUserInRollout(userId, percentage)` 实现:

```java
// 灰度分桶: userId % 100 < percentage 即命中
long bucket = Math.floorMod(userId, 100L);
boolean inRollout = bucket < percentage;
```

特点:
- 粘性: 同一用户多次访问结果一致
- 均匀: hash 分桶保证各 userId 均匀分布
- 无需网关支持: 应用层判断即可

### 2.2 基于网关 Header 的灰度

在 Nginx / Spring Cloud Gateway 层根据 `X-Canary` header 路由:

```nginx
map $cookie_canary $canary_group {
    default    "stable";
    "1"        "canary";
}

split_clients "$remote_addr" $canary_bucket {
    5%    "canary";
    *      "stable";
}

upstream stable_backend { server app-stable:8080; }
upstream canary_backend { server app-canary:8080; }
```

## 3. 监控观察指标

| 指标 | 阈值 | 数据源 |
|------|------|--------|
| HTTP 5xx 错误率 | < 0.5% | Prometheus + Grafana |
| 平均响应时间 | < 200ms (95分位) | Prometheus |
| CPU 使用率 | < 75% | Node Exporter |
| 内存使用率 | < 80% | Node Exporter |
| JVM GC 暂停 | < 500ms | Actuator + Prometheus |
| 业务异常率 | < 0.1% | Sentry |

## 4. 回滚 SOP

### 4.1 自动回滚 (Prometheus AlertManager)

```yaml
groups:
  - name: canary
    rules:
      - alert: CanaryErrorRateHigh
        expr: |
          sum(rate(http_requests_total{status=~"5..",app="pmis-canary"}[5m]))
          / sum(rate(http_requests_total{app="pmis-canary"}[5m])) > 0.005
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "金丝雀实例 5xx 错误率超过 0.5%"
          runbook: "回滚 canary deployment 到上一版本"
```

### 4.2 手动回滚

```bash
# Kubernetes: 回滚到上一版本
kubectl rollout undo deployment/pmis-canary -n pmis

# Helm: 切回 stable
helm upgrade pmis ./helm/pmis --reuse-values --set canary.enabled=false

# Feature Flag: 一键关停灰度
curl -X PUT 'http://config:9010/api/v1/feature-flags/AGENT_ORCHESTRATION/rollout?percentage=0' \
  -H 'Authorization: Bearer xxx'
```

## 5. 决策矩阵

| 阶段 | 持续时间 | 决策点 | 行动 |
|------|----------|--------|------|
| Canary 5% | 1h | 错误率 < 0.5%? | 是 → 25% / 否 → 回滚 |
| Canary 25% | 1h | 错误率 < 0.5%? | 是 → 50% / 否 → 回滚 |
| Canary 50% | 1h | 错误率 < 0.5%? | 是 → 100% / 否 → 回滚 |
| Full 100% | 持续监控 | SLA 正常? | 是 → 归档 / 否 → 回滚 |

## 6. 灰度发布配套能力

- **Feature Flag 平台**: `ydsz-pmis-config` 模块 `/api/v1/feature-flags/{key}/rollout`
- **混沌工程**: `ChaosService` 在 canary 阶段主动注入故障, 验证容错
- **Sentry 监控**: 自动上报 canary 实例异常, 实时告警
- **Lighthouse CI**: 前端性能基线监控, 防止 canary 引入性能回退

## 7. 关联文档

- [Lighthouse CI 配置](../ydsz-pmis-frontend/lighthouserc.json)
- [混沌工程接入](../ydsz-pmis-backend/ydsz-pmis-common/src/main/java/com/njydsz/pmis/common/chaos/)
- [Feature Flag 平台](../ydsz-pmis-backend/ydsz-pmis-common/src/main/java/com/njydsz/pmis/common/featureflag/)
