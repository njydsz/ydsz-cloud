# PMIS Sentinel 限流/熔断规则
# --------------------------------------------------------------------------
# 用途：基于 Sentinel 1.8.x 的流量治理规则，涵盖限流（flow）与熔断（degrade）。
# 模式：规则下发到 Nacos 配置中心，客户端动态订阅，秒级生效。
# 客户端：ydsz-pmis-gateway（粗粒度入口限流）+ 14 个业务模块（细粒度方法限流）。
# --------------------------------------------------------------------------

## 部署方式

Sentinel 1.8.x 支持通过 Nacos 配置中心动态下发规则，需在 `ydsz-pmis-gateway` 和
各业务模块的 `application.yml` 中启用：

```yaml
spring:
  cloud:
    sentinel:
      # 本地 Dashboard 地址（开发/排障用，生产可不填）
      transport:
        dashboard: 127.0.0.1:8858
        port: 8719   # 与应用通信的 API 端口
      datasource:
        # 限流规则：data-id = pmis-sentinel-flow
        flow:
          nacos:
            server-addr: 127.0.0.1:8848
            namespace: pmis
            group-id: SENTINEL_GROUP
            data-id: pmis-sentinel-flow
            rule-type: flow
        # 熔断规则：data-id = pmis-sentinel-degrade
        degrade:
          nacos:
            server-addr: 127.0.0.1:8848
            namespace: pmis
            group-id: SENTINEL_GROUP
            data-id: pmis-sentinel-degrade
            rule-type: degrade
        # 授权规则（按调用方黑白名单）：data-id = pmis-sentinel-authority
        authority:
          nacos:
            server-addr: 127.0.0.1:8848
            namespace: pmis
            group-id: SENTINEL_GROUP
            data-id: pmis-sentinel-authority
            rule-type: authority
        # 系统规则（CPU/Load/入口 QPS/出口 QPS）：data-id = pmis-sentinel-system
        system:
          nacos:
            server-addr: 127.0.0.1:8848
            namespace: pmis
            group-id: SENTINEL_GROUP
            data-id: pmis-sentinel-system
            rule-type: system
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

## 资源命名规范

PMIS 统一使用 `<HTTP方法>:<URL模式>` 作为 Sentinel 资源名（resource）：

- 通过 `@SentinelResource("POST:/api/v1/auth/login")` 注解在代码侧声明
- 通过 Spring Cloud 自动生成的 URL 资源（`{@link com.alibaba.cloud.sentinel.SentinelWebInterceptor}`）
- `*` 通配符匹配子路径，如 `POST:/api/v1/execution/import/*` 匹配所有 import 下的接口

## 阈值设计原则

- **认证类**（登录 / 2FA / 找回密码）：低 QPS / IP 维度，抵御密码爆破
- **写操作**（导入 / 结项 / 合同）：用户维度 1~5 QPS，保护后端资源
- **读操作**（报表 / 驾驶舱 / 审计）：用户维度 10~30 QPS，平衡体验与稳定性
- **AI 类**（Agent / 模型推理）：用户维度 1~2 QPS，防止 Token 滥用与超时
- **批量作业**（重算 / 同步）：分钟级 1 QPS，避免定时任务并发冲撞

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

## 监控与告警

Sentinel 规则触发后，会通过以下方式联动：

1. **Dashboard 实时监控**：左侧"实时监控" → 选择应用名 → 观察 QPS / RT / 拒绝数
2. **Metrics 暴露**：通过 Spring Boot Actuator `/actuator/sentinel/metrics` 拉取指标
3. **Micrometer 桥接**：自动注册 `sentinel_flow_*` / `sentinel_degrade_*` 指标到 Prometheus
4. **告警规则**（deploy/monitoring/prometheus/rules/sentinel.yml）：
   - 5 分钟内某资源拒绝率 > 50% → 告警到企微
   - 某资源持续熔断 > 5 分钟 → P1 告警

## 调优建议

- **业务高峰期**：将 `cockpit/*` 从 10 QPS 提到 30 QPS
- **AI 模型升级后**：将 `agent/run` 从 2 QPS 提到 5 QPS
- **触发熔断时**：Sentinel Dashboard "实时监控" 查看慢调用 RT 分布，针对性扩容
- **大促前**：将限流阈值提高 30%，熔断阈值（minRequestAmount）提高 50%

## 灰度发布配合

- 蓝绿：通过 `AuthorityRule` 控制仅允许蓝/绿一侧流量
- 灰度：配合 Nacos 配置中心 namespace 隔离，灰度环境用独立 `data-id`（如 `pmis-sentinel-flow-gray`）

## 注意事项

- `_comment` 字段为 PMIS 自定义注解，Sentinel 解析时会忽略，下发前可保留
- 修改规则后 Nacos 自动推送，客户端通常 1~3 秒内生效，无需重启
- 限流生效后客户端返回 `BlockException`，需在 `GlobalExceptionHandler` 统一处理为 429 响应
