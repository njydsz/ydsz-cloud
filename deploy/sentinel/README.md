# PMIS Sentinel 限流/熔断规则

## 部署方式

Sentinel 1.8.x 支持通过 Nacos 配置中心动态下发规则，需在 `ydsz-pmis-gateway` 和
各业务模块的 `application.yml` 中启用：

```yaml
spring:
  cloud:
    sentinel:
      datasource:
        flow:
          nacos:
            server-addr: 127.0.0.1:8848
            data-id: pmis-sentinel-flow
            rule-type: flow
        degrade:
          nacos:
            server-addr: 127.0.0.1:8848
            data-id: pmis-sentinel-degrade
            rule-type: degrade
```

将本目录下的 `flow-rules.json` 和 `degrade-rules.json` 上传到 Nacos 对应 data-id 即可。

## 规则总览

| 资源 | 类型 | 阈值 | 触发原因 |
|------|------|------|----------|
| `POST:/api/v1/auth/login` | 限流 | 5 QPS/IP/min | 防爆破 |
| `POST:/api/v1/auth/2fa/verify` | 限流 | 10 QPS/IP/min | 防爆破 |
| `POST:/api/v1/execution/import/*` | 限流 | 1 QPS/用户/秒 | 防 OOM |
| `GET:/api/v1/execution/cockpit/*` | 限流 | 10 QPS/用户/秒 | 跨模块聚合昂贵 |
| `GET:/api/v1/execution/report-export/download` | 限流 | 2 QPS/用户/秒 | 大数据量导出 |
| `GET:/api/v1/execution/advanced/*` | 限流 | 5 QPS/用户/秒 | 复杂报表 |
| `POST:/api/v1/execution/closure/*` | 限流 | 3 QPS/用户/秒 | 业务约束 |
| `POST:/api/v1/project/contracts/*` | 限流 | 5 QPS/用户/秒 | 业务约束 |
| `POST:/api/v1/agent/run` | 限流 | 2 QPS/用户/秒 | 模型推理昂贵 |
| `POST:/api/v1/agent/orchestration/coordinate` | 限流 | 1 QPS/5秒 | 多 Agent 协同 |
| `GET:/api/v1/audit/login-audit/page` | 限流 | 20 QPS/用户/秒 | 审计查询 |
| `GET:/api/v1/notification/messages` | 限流 | 30 QPS/用户/秒 | 高频轮询 |
| `POST:/api/v1/notification/messages/*` | 限流 | 50 QPS/用户/秒 | 通知写入 |
| `POST:/api/v1/workflow/processes/start` | 限流 | 5 QPS/用户/秒 | 流程发起 |
| `GET:/api/v1/execution/billable-utilization/recompute` | 限流 | 1 QPS/分钟 | 重算昂贵 |
| `POST:/api/v1/auth/login` | 熔断 | 1s 10 异常 → 30s 熔断 | 登录风暴 |
| `POST:/api/v1/execution/import/*` | 熔断 | 30s 慢调用 ≥60% | 导入缓慢 |
| `GET:/api/v1/agent/run` | 熔断 | 10s 错误率 ≥30% | 模型故障 |

## 验证步骤

1. 启动 Sentinel Dashboard：
   ```bash
   docker run -d -p 8858:8858 \
     -e AUTH_USERNAME=sentinel \
     -e AUTH_PASSWORD=sentinel \
     bladex/sentinel-dashboard:1.8.6
   ```

2. 访问 `http://localhost:8858`，登录后查看"流控规则"和"熔断规则"。

3. 触发限流（连续 6 次登录失败）：
   ```bash
   for i in {1..6}; do
     curl -X POST http://localhost:9000/api/v1/auth/login \
       -H "Content-Type: application/json" \
       -d '{"username":"wrong","password":"wrong"}'
   done
   # 期望第 6 次返回 429 Too Many Requests
   ```

## 调优建议

- **业务高峰期**：将 `cockpit/*` 从 10 QPS 提到 30 QPS
- **AI 模型升级后**：将 `agent/run` 从 2 QPS 提到 5 QPS
- **触发熔断时**：Sentinel Dashboard "实时监控" 查看慢调用 RT 分布，针对性扩容
