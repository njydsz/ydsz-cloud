# JVM 调优指南（YDSZ PMIS）

> 本文件记录 JVM GC 策略选择、参数模板与压测验证方案。
> 与 Dockerfile JAVA_OPTS 和 K8s deployment-template.yaml 对齐。

---

## GC 策略决策树

```
堆内存大小？
├── < 2GB → G1GC（默认，低内存场景最优）
├── 2GB ~ 4GB → G1GC（调优） 或 ZGC（若 P99 延迟要求 < 50ms）
└── >= 4GB → ZGC（推荐大幅降低 GC 停顿）
```

## GC 算法对比（JDK 21）

| 维度 | G1GC | ZGC（分代） |
|---|---|---|
| **停顿时间** | 默认 200ms（可调） | < 1ms（亚毫秒） |
| **吞吐量损失** | 3-5% | 8-15% |
| **内存 overhead** | ~5-8% | ~10-15% |
| **适用堆大小** | 64MB ~ 16GB | 8MB ~ 16TB |
| **HeapDump on OOM** | ✅ 稳定支持 | ⚠️ JDK 21 已支持但不稳定 |
| **推荐场景** | 默认/<4GB 堆 | >=4GB 堆 + 严格延迟要求 |

---

## 参数模板

### 模板 A：G1GC 通用（适用于 < 4GB 堆，默认）

```yaml
# Dockerfile JAVA_OPTS / K8s container env
JAVA_OPTS: >
  -XX:+UseContainerSupport
  -XX:MaxRAMPercentage=75.0
  -XX:InitialRAMPercentage=50.0
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
  -XX:G1HeapRegionSize=16m
  -XX:+ExitOnOutOfMemoryError
  -XX:+HeapDumpOnOutOfMemoryError
  -XX:HeapDumpPath=/app/logs/heapdump.hprof
  -Xlog:gc*:file=/app/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=20m
  -Djava.security.egd=file:/dev/./urandom
```

**推荐服务：** ydzs-gateway, ydzs-userinfo, ydzs-system, ydzs-literule, ydzs-nextwiki

### 模板 B：ZGC 大堆（适用于 >= 4GB 堆）

```yaml
JAVA_OPTS: >
  -XX:+UseContainerSupport
  -XX:MaxRAMPercentage=75.0
  -XX:InitialRAMPercentage=50.0
  -XX:+UseZGC
  -XX:+ZGenerational
  -XX:+ExitOnOutOfMemoryError
  -Xlog:gc*:file=/app/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=20m
  -Djava.security.egd=file:/dev/./urandom
```

**⚠️ 注意事项：**
- ZGC 不支持 `-XX:+HeapDumpOnOutOfMemoryError`（JDK 21 虽部分支持但不稳定，建议移除）
- `ZGenerational` 在 JDK 21 是默认开启的，显式声明不影响
- ZGC 会使用更多内存做染色指针和预留空间，MaxRAMPercentage 建议保持 ≤ 75%

**推荐服务：** ydzs-project, ydzs-workflow, ydzs-message, ydzs-agent（堆内存 >= 4GB）

### 模板 C：高吞吐批处理（适用于 cronjob / 离线任务）

```yaml
JAVA_OPTS: >
  -XX:+UseContainerSupport
  -XX:MaxRAMPercentage=80.0
  -XX:InitialRAMPercentage=60.0
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=500
  -XX:G1HeapRegionSize=32m
  -XX:+ExitOnOutOfMemoryError
  -XX:+HeapDumpOnOutOfMemoryError
  -XX:HeapDumpPath=/app/logs/heapdump.hprof
  -Xlog:gc*:file=/app/logs/gc.log:time,uptime,level,tags:filecount=3,filesize=10m
  -Djava.security.egd=file:/dev/./urandom
```

**适用：** ydzs-cronjob（批处理任务更关注吞吐，放宽 GC 暂停目标换取更高吞吐）

---

## K8s 调优配套设置

### 容器资源限制建议

| 服务 | 堆内存 | CPU request | CPU limit | memory limit |
|---|---|---|---|---|
| gateway | 1Gi | 250m | 1000m | 2Gi |
| userinfo | 1Gi | 250m | 1000m | 2Gi |
| system | 512Mi | 125m | 500m | 1Gi |
| project | 4Gi | 1000m | 2000m | 6Gi |
| workflow | 2Gi | 500m | 1000m | 3Gi |
| message | 2Gi | 500m | 1000m | 3Gi |
| agent | 4Gi | 1000m | 2000m | 6Gi |
| cronjob | 1Gi | 250m | 1000m | 2Gi |
| literule | 512Mi | 125m | 500m | 1Gi |
| nextwiki | 1Gi | 250m | 1000m | 2Gi |

> **原则：** memory limit ≈ heap × 1.3（考虑非堆内存、线程栈、JIT code cache、DirectBuffer）

### GC 日志采集（Filebeat / Fluent Bit sidecar）

所有服务的 GC 日志统一输出到 `/app/logs/gc.log`，建议通过 sidecar 容器采集到 ELK/Loki：

```yaml
# 部署时挂载
volumeMounts:
  - name: logs
    mountPath: /app/logs

# Filebeat 配置
filebeat.inputs:
  - type: log
    paths:
      - /var/log/containers/*gc.log
    multiline.pattern: '^\d{4}-\d{2}-\d{2}T'
    multiline.negate: true
    multiline.match: after
```

---

## 压测验证方案

### 压测前置准备

```bash
# 1. 部署压测版本到 SIT（与生产资源配比对齐）
helm upgrade --install ydsz-backend ./deploy/helm/ydsz-backend \
  --set image.tag=benchmark-zgc \
  --set env=sit

# 2. 基线测试（G1GC，收集 24h 指标采集数据）
# 观察 Prometheus 指标：
# - jvm.gc.pause（G1 Young/Old GC 停顿时间分布）
# - jvm.gc.memory.promoted（晋升量）
# - process.cpu.usage
```

### ZGC 切换压测

```bash
# 3. 切换 target service 到 ZGC 参数
# 通过 K8s ConfigMap 动态更新 JAVA_OPTS，滚动重启

# 4. 压测工具（JMeter / wrk / k6）
# 参考 CI workflow 增加回归性能测试

# 5. 关键对比指标
```

### 压测对比指标

| 指标 | G1GC 基线 | ZGC 目标 | 说明 |
|---|---|---|---|
| 平均 GC 停顿 | X ms | < 1ms | 核心收益 |
| P99 GC 停顿 | Y ms | < 5ms | 消除长尾 |
| 吞吐量 | Z1 | Z2 × 0.92 | GC 计算开销 |
| OOM 风险 | 无 | 监控 | 需关注 RSS 增长 |

---

## 决策记录（ADR）

- **ADR-001**: 为什么默认保持 G1GC？
  - G1GC 已通过多年生产验证，与 HeapDump 兼容性好
  - < 4GB 堆场景 G1GC 与 ZGC 差距不明显（G1 停顿 ~50ms 可接受）
  - ZGC 内存 overhead 对小堆不划算

- **ADR-002**: 何时升级到 ZGC？
  - 堆内存配置 ≥ 4GB（当前：project, agent）
  - 出现 GC 停顿导致的超时告警（G1 调优无法解决）
  - 压测验证 ZGC 在该业务模型下稳定性

---

*最后更新：2026-08-04*
