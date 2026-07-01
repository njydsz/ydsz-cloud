# 性能压测目录（批次 19 补全）

PMIS 生产环境的 JMeter 压测脚本与 24h Soak 测试。

## 目录结构

```
deploy/perf/
├── jmeter/
│   ├── 01-core-read.jmx      # 核心读接口（500 并发 / 10 分钟）
│   ├── 02-write-mix.jmx      # 写混合（200 并发 / 5 分钟）
│   ├── 03-ai-agent.jmx       # AI Agent 编排（100 并发 / 5 分钟）
│   └── 04-websocket.jmx      # WebSocket 长连接（1k 并发 / 10 分钟）
├── 24h.sh                   # 24h Soak 自动化脚本
├── baseline/soak-report.md  # 24h Soak 报告模板
└── README.md
```

## 4 个压测脚本

### 01-core-read.jmx
- 场景：立项 / 合同 / 商机 / 客户 / 驾驶舱分页查询
- 负载：500 并发 / 10 分钟
- 目标：P99 < 200ms，错误率 < 0.1%

### 02-write-mix.jmx
- 场景：工时填报 / 项目变更 / 采购
- 负载：200 并发 / 5 分钟
- 目标：P99 < 500ms，错误率 < 0.5%
- 关注：锁等待、事务超时、索引命中

### 03-ai-agent.jmx
- 场景：SEQUENTIAL / PARALLEL 4 Agent 编排
- 负载：100 并发 / 5 分钟
- 目标：SEQUENTIAL P99 < 3s，PARALLEL P99 < 2s
- 关注：LLM 限流、降级行为（无 token 返回 0）

### 04-websocket.jmx
- 场景：通知 / AI 推送 WebSocket
- 负载：1k 并发连接 / 10 分钟
- 目标：P99 < 100ms，断线重连 < 5s
- 依赖：[WebSocket Samplers by Peter Doornbosch](https://github.com/cbtagungumukoro/websocket-samplers)

## 快速使用

### 单次压测

```bash
# 安装 JMeter 5.6
brew install jmeter   # macOS
apt install jmeter    # Ubuntu

# 运行（生成 HTML 报告）
cd deploy/perf/jmeter
jmeter -n -t 01-core-read.jmx \
    -JbaseUrl=http://staging.pmis.example.com \
    -l result.jtl \
    -e -o report/

# 报告位置：report/index.html
```

### 24h Soak

```bash
# 1. 准备：50 个 perf-test 账号
psql -f deploy/perf/prepare-accounts.sql

# 2. 启动
cd deploy/perf
chmod +x 24h.sh
./24h.sh

# 3. 实时观察
# - Grafana: https://grafana.pmis.example.com/d/pmis-soak
# - 日志: deploy/perf/baseline/soak-YYYYMMDD-HHMMSS/soak.log

# 4. 完成后生成报告
cat baseline/soak-YYYYMMDD-HHMMSS/soak-report.txt
```

## 性能基线（生产环境）

| 指标 | 读 | 写 | AI 编排 |
|------|----|----|---------|
| 吞吐量 | 5000+ r/s | 500+ tps | 100+ 编排/s |
| P50 | < 50ms | < 100ms | < 1s |
| P95 | < 150ms | < 300ms | < 2s |
| P99 | < 200ms | < 500ms | < 5s |
| 错误率 | < 0.1% | < 0.5% | < 1% |

## 与 CI/CD 集成

```yaml
# GitLab CI
stages:
  - build
  - test
  - perf-test          # 部署到 staging 后跑
  - promote-prod

perf-test:
  stage: perf-test
  image: jmeter:5.6
  script:
    - jmeter -n -t deploy/perf/jmeter/01-core-read.jmx
        -JbaseUrl=$STAGING_URL -l result.jtl -e -o report/
  artifacts:
    paths:
      - report/
    expire_in: 7 days
  only:
    - main
  allow_failure: true  # 性能回归不阻塞，但需告警
```

## 性能调优依据

压测结果用于：

1. **容量规划**：根据 P99 反推每个微服务的副本数
2. **限流参数**：Nginx `limit_req` rate/burst
3. **连接池大小**：HikariCP `maximum-pool-size` / Lettuce `max-active`
4. **JVM 参数**：堆大小 / GC 策略 / OOM 行为
5. **告警阈值**：P99 / 错误率 / 内存使用
