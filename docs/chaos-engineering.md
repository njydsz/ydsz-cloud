# PMIS 混沌工程 (Chaos Engineering)

> 批次 20 P3-5 | 适用: PMIS 全量 14 个 Spring Cloud 微服务

## 1. 目标与原则

### 1.1 目标
通过 **受控的故障注入** 验证 PMIS 系统的容错能力 (熔断 / 降级 / 重试 / fallback),
确保线上发生真实故障时, 系统能 **优雅降级** 而非雪崩。

### 1.2 原则 (Netflix chaos principles)
1. **建立稳态假设** — 先确定正常时关键指标 (错误率 / 延迟 / 转化率)
2. **多样化实验** — 真实世界故障分布 (LATENCY 50%, EXCEPTION 30%, NETWORK 15%, OOM 5%)
3. **生产环境演练** — staging 通过 ≠ 生产通过, 但要渐进
4. **自动化持续演练** — 季度专项演练 + 每日随机小实验
5. **最小爆炸半径** — 一个实验只影响一个 service / 一类请求
6. **围绕稳态假设** — 实验通过 = 关键指标未越界

## 2. 实验类型

| Type | 实现 | 典型场景 |
|------|------|----------|
| `LATENCY` | Thread.sleep(N) | 下游慢响应, 验证超时与重试 |
| `EXCEPTION` | throw RuntimeException | 业务异常, 验证统一异常处理 |
| `ERROR_RATE` | 按概率抛错 | 部分失败, 验证熔断器 |
| `RESOURCE_EXHAUSTION` | throw OOM | 资源耗尽, 验证限流 |
| `NETWORK_PARTITION` | throw ConnectException | 网络抖动, 验证 FallbackFactory |

> 所有实验通过 `ChaosService.maybeInject(target)` 注入, 受 `FeatureFlag.CANARY_DEPLOY` 保护,
> **生产环境默认关闭**, 需 admin 显式开启。

## 3. 与金丝雀发布联动 SOP (核心)

### 3.1 流程图

```
┌─────────────────────┐
│ 1. 代码合并到 main  │
│    CI: 单测 + E2E   │
└──────────┬──────────┘
           ▼
┌─────────────────────┐
│ 2. 部署 canary 镜像  │  ← Canary 5% / 25% / 50%
│    观察 1h*3 阶段    │
└──────────┬──────────┘
           ▼
┌─────────────────────┐
│ 3. 在 canary 上注入   │  ← 目标: 验证新版本在故障下行为正确
│    LATENCY 500ms     │
│    EXCEPTION 5%      │
│    NETWORK 1%        │
└──────────┬──────────┘
           ▼
┌─────────────────────┐
│ 4. 关键指标对比      │
│    错误率 / p99 /   │
│    熔断命中 / fallback│
└──────────┬──────────┘
           ▼
     ┌─────┴─────┐
     │ 容错达标? │
     └─────┬─────┘
        拒绝  通过
         │     │
         ▼     ▼
     全量回滚  提升 canary 至 100%
              (转为 stable, 即金丝雀阶段 4)
                 │
                 ▼
         ┌──────────────────────┐
         │ 5. stable 版本上跑     │  ← 真实线上验证
         │    每日随机小实验      │     验证 FallbackFactory / Sentinel
         │    (latency 100ms)    │     不影响业务
         └──────────────────────┘
```

### 3.2 关键指标 (金丝雀 + 混沌)

| 指标 | 阈值 | 越界行动 |
|------|------|----------|
| 错误率 | < 1% (注入时允许比基线 +0.5%) | 立即撤销实验 + 回滚金丝雀 |
| 熔断器触发率 | < 10% (说明过度敏感) | 调高 Sentinel 阈值 |
| Fallback 命中率 | > 95% (故障时) | 验证 fallback 是否正常返回 |
| p99 延迟 | < 1.5s (注入 500ms 后应 < 1.5s) | 排查下游超时配置 |
| Sentry 新 issue | 0 | 阻断发版 |

### 3.3 实验登记

每次混沌实验前, 在 `docs/operations/chaos-experiments/<date>-<service>.md` 记录:

```markdown
# 混沌实验: ExecutionService - LATENCY 500ms

- **日期**: 2026-07-15
- **Owner**: sre-team-lead
- **目标服务**: ydsz-pmis-execution
- **金丝雀版本**: v1.1.0-rc1
- **实验类型**: LATENCY
- **注入位置**: ContractService.getContract()
- **参数**: latencyMs=500, errorRate=null (必注入)
- **开启时间**: 10:00
- **关闭时间**: 11:00
- **观察指标**: 错误率 0.12% → 0.18% (符合预期)
- **结论**: ✅ 容错达标, 熔断器命中 8%, fallback 返回空集合
- **Action Item**: PR-#234 添加 ContractService 的 Sentinel 规则
```

## 4. 常用实验配方

### 4.1 验证熔断器 (Sentinel)
```bash
# 实验 1: 单次慢调用
curl -X POST http://pmis-config:9008/api/v1/chaos/experiments \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{
    "target": "ContractService.getContract",
    "type": "LATENCY",
    "latencyMs": 500,
    "enabled": true,
    "description": "验证 Sentinel 慢调用熔断"
  }'
# 观察 1min, Sentinel QPS 应下降, fallback 应生效
```

### 4.2 验证 FallbackFactory
```bash
# 实验 2: 网络分区
curl -X POST http://pmis-config:9008/api/v1/chaos/experiments \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{
    "target": "UserService.getById",
    "type": "NETWORK_PARTITION",
    "enabled": true,
    "description": "验证 Feign FallbackFactory 返回默认用户"
  }'
# 预期: 业务调用方接收到 fallback 数据, 页面显示 "数据暂不可用"
```

### 4.3 紧急回滚
```bash
# 关闭所有实验
curl -X POST http://pmis-config:9008/api/v1/chaos/history/clear
# 通过 feature flag 整体关停
curl -X PUT http://pmis-config:9008/api/v1/feature-flags/CANARY_DEPLOY/enabled?enabled=false
```

## 5. 自动化 (进阶)

### 5.1 每日随机实验 (Cron)
每天 02:00-04:00 在 stable 副本 (1 个 Pod) 上注入 5min LATENCY 100ms:
```yaml
# deploy/canary/chaos-daily-job.yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: chaos-daily-latency
spec:
  schedule: "0 2 * * *"
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: chaos
            image: curlimages/curl
            command: ["/bin/sh","-c"]
            args:
            - |
              curl -X POST http://pmis-config:9008/api/v1/chaos/experiments \
                -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
                -d '{"target":"ContractService.getContract","type":"LATENCY","latencyMs":100,"enabled":true}'
              sleep 300
              curl -X DELETE http://pmis-config:9008/api/v1/chaos/experiments/ContractService.getContract
          restartPolicy: OnFailure
```

### 5.2 季度专项演练
- **Q1**: 数据库故障 (主从切换 + binlog 延迟)
- **Q2**: Redis 故障 (哨兵切换 + 缓存击穿)
- **Q3**: Nacos 故障 (集群脑裂 + 配置丢失)
- **Q4**: 全链路演练 (多服务同时故障)

## 6. 责任分工

| 角色 | 责任 |
|------|------|
| SRE | 实验编排 / 监控 / 紧急回滚 |
| 开发 | 业务容错 (FallbackFactory / Sentinel) + 故障假设 |
| QA | 验证容错符合 PRD 描述 |
| PM | 业务影响评估 (降级是否可接受) |

## 7. 反模式 (禁止事项)

- ❌ **生产环境无 feature flag 保护直接注入** — 必须先开 `CANARY_DEPLOY`
- ❌ **实验不限定 target, 全局生效** — 违反最小爆炸半径
- ❌ **不记录实验结果** — 无法复盘
- ❌ **业务高峰期 (9-18 点) 跑大规模实验** — 影响真实用户
- ❌ **关闭监控后跑实验** — 等于盲飞

## 8. 与金丝雀发布的边界

| 维度 | 金丝雀 | 混沌工程 |
|------|--------|----------|
| 粒度 | 版本 / Pod 副本 | target 方法 / 类 |
| 流量 | 真实流量按权重 | 程序主动注入 |
| 时间 | 数小时-数天 | 5min-30min |
| 工具 | Istio / SCG | ChaosService |
| 目标 | 验证新版本稳定 | 验证容错能力 |
| 责任 | SRE 主导 | SRE + 开发 |

> 简言之: **金丝雀解决"新代码能不能上", 混沌工程解决"线上出故障能不能扛"**。
> 两者互补, 缺一不可。
