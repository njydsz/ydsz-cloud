<!--
  ===========================================================================
  文件名: post-deploy-checklist.md
  路径:   docs/operations/post-deploy-checklist.md
  作用:   PMIS 上线后必检清单（10 项），任一未通过立即回滚
  适用:   任何 PMIS 14 微服务或前端的发布 (Canary / 全量 / Hotfix) 后 30 分钟内
  关联:   prod-ops-runbook.md
  ===========================================================================
-->

# PMIS 上线后 必检清单 (Post-Deploy Checklist)

> 批次 21 P4-3 | 适用: 任何 PMIS 14 微服务或前端的发布 (Canary / 全量 / Hotfix) 后 30 分钟内
> 文档版本: V1.0 | 编制: 2026-07-01 | 最近更新: 2026-07-03

每项必检, 任一未通过 → 立即回滚 → 故障复盘。

## 上线元信息 (人工填写)

- 发布版本: `_________`
- 涉及服务: `_________`
- 部署时间: `_________`
- 部署人: `_________`
- 部署类型: `[ ] Canary 5/25/50/100  [ ] Hotfix  [ ] ConfigOnly  [ ] DataMigration`

---

## 必检 1/10: Pod 状态

```bash
kubectl get pods -n pmis-prod -l app=<service> -o wide
```

**判定**:
- [ ] `STATUS = Running`, 所有 Pod `READY = 1/1`
- [ ] 副本数 ≥ 预期
- [ ] 无 `CrashLoopBackOff` / `ImagePullBackOff` / `OOMKilled`
- [ ] 启动时间在 60s 内 (有 readiness probe 验证)

**未通过**: `kubectl describe pod`, 看 `Events` 和 `Last State`, 多数是配置错误或镜像拉取失败。

---

## 必检 2/10: 健康检查

```bash
curl -fsS http://<service>:<port>/actuator/health
```

**判定**:
- [ ] HTTP 200 + `status: UP`
- [ ] 所有 component UP: db / redis / diskSpace / ping
- [ ] 启动时间 < 60s (无冷启动卡顿)

**未通过**: 检查日志中的 WARN/ERROR, 多数是 DB / Redis 连接超时。

---

## 必检 3/10: 核心 API 冒烟

至少调用 3 个核心业务接口验证 200:

| 服务 | 冒烟接口 | 预期 |
|------|----------|------|
| gateway | `GET /actuator/gateway/routes` | 路由数 ≥ 200 |
| auth | `POST /auth/login` (admin/admin) | 返回 token |
| project | `GET /project/initiation/page?pageNum=1&pageSize=10` | 分页返回 |
| execution | `GET /execution/cockpit/overview` | 6 个 KPI |
| user | `GET /user/info?userId=1` | 用户信息 |
| agent | `GET /agent/recent?limit=10` | 预测列表 |
| notification | `GET /notification/unread-count?userId=1` | 计数 |
| workflow | `GET /workflow/list?pageNum=1&pageSize=10` | 流程定义 |
| config | `GET /config/dict/list?code=PROJECT_STATUS` | 枚举值 |
| file | `GET /file/list?bizType=test&bizId=1` | 文件列表 |
| audit | `GET /audit/log/page?pageNum=1&pageSize=10` | 日志分页 |
| message | `GET /message/template/list?pageNum=1&pageSize=10` | 模板列表 |
| cronjob | `GET /cronjob/job/list?pageNum=1&pageSize=10` | 任务列表 |

**判定**:
- [ ] 所有 HTTP 200, 响应 < 500ms
- [ ] 返回数据非空 (非占位符)

**未通过**: 看响应体 errorCode, 见 [BizErrorCode 文档](file:///d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/src/main/java/com/njydsz/pmis/common/api/BizErrorCode.java)

---

## 必检 4/10: 错误率 (SLO)

```promql
# Prometheus
sum(rate(http_requests_total{service="pmis-<service>",status=~"5.."}[5m]))
  / sum(rate(http_requests_total{service="pmis-<service>"}[5m]))
```

**判定**:
- [ ] 错误率 < 0.5% (基线) — 接近 0% 最佳
- [ ] 无 5xx 错误激增

**未通过**:
- 5xx < 1%: 监控, 通知开发排查
- 5xx ≥ 1%: 立即回滚, 见 [prod-ops-runbook §6.4](file:///d:/Code/ydsz/ydsz-pmis/docs/operations/prod-ops-runbook.md)

---

## 必检 5/10: 延迟 (SLO)

```promql
# p99
histogram_quantile(0.99, sum(rate(http_request_duration_seconds_bucket{service="pmis-<service>"}[5m])) by (le))
```

**判定**:
- [ ] p99 < 800ms (基线)
- [ ] p95 < 500ms
- [ ] 无尖刺 (spike > 2x 基线)

**未通过**: 看 SkyWalking 链路追踪, 锁定慢在哪一跳。

---

## 必检 6/10: 资源使用率

```bash
kubectl top pods -n pmis-prod -l app=<service>
```

**判定**:
- [ ] CPU < 70% (平均)
- [ ] 内存 < 80% (避免 OOM)
- [ ] 无内存持续上涨 (泄漏迹象)

**未通过**:
- 持续上涨: 立即回滚, 怀疑内存泄漏
- 临时峰值: 触发 HPA 扩容, 持续观察

---

## 必检 7/10: 日志与异常

```bash
# 实时错误
stern -n pmis-prod <service> | grep -E "ERROR|FATAL"

# 或 kubectl
kubectl logs -n pmis-prod -l app=<service> --tail=200 | grep -E "ERROR|FATAL"
```

**判定**:
- [ ] 5min 内无新 ERROR
- [ ] 无新 FATAL / Exception
- [ ] 异常数 < 基线 + 50%

**未通过**: 把完整 stacktrace 贴飞书 oncall 群, @ 该服务 owner。

---

## 必检 8/10: Sentry 监控

**判定**:
- [ ] 5min 内无新 issue
- [ ] 5min 内无 unhandled exception
- [ ] release version 显示为本次发布版本

**未通过**: 立即 [Sentry.io](https://sentry.io) 看 issue 详情, 决定回滚或 hotfix。

---

## 必检 9/10: 业务功能回归

人工 / 自动化各跑 1 个核心业务场景:

| 服务 | 回归场景 | 通过 |
|------|----------|------|
| auth | 登录 + 2FA 绑定 + 退出 | [ ] |
| user | 修改密码 → 用新密码登录 | [ ] |
| project | 创建商机 → 转立项 → 提交审批 | [ ] |
| execution | 填报工时 → 审批 → 归集成本 | [ ] |
| agent | 触发一次 AI 编排 | [ ] |
| notification | 站内消息收件箱 | [ ] |
| workflow | 提交一个请假流程 → 审批通过 | [ ] |

**判定**: 全部通过, 数据一致。

**未通过**: 该服务为高风险, 立即回滚。

---

## 必检 10/10: 依赖链路

跨服务调用冒烟 (Feign 链路):

```bash
# 创建一个商机 (走 user Feign 装配客户名 + workflow Feign 启动流程)
curl -X POST http://gateway:9000/api/v1/project/opportunity \
  -H "Authorization: Bearer $TOKEN" \
  -d '{ ... }'

# 确认返回数据中 customerName / ownerName / pmName 都已自动装配
```

**判定**:
- [ ] Feign 调用未降级 (无 NPE)
- [ ] NameAssembler 返回的名称字段非空
- [ ] workflow 流程已启动 (有 process_instance_id)

**未通过**: 多数是 NameAssembler 或 Feign Fallback 失败, 查链路日志。

---

## 签收

| 角色 | 姓名 | 签收 | 时间 |
|------|------|------|------|
| 部署人 | | | |
| oncall SRE | | | |
| 开发 owner | | | |
| QA 验证 | | | |

---

## 异常记录

如有任何检查项未通过, 在此记录:

```
[时间] [服务] [检查项] [现象] [行动] [结果]
```

---

## 关联文档

- 运维 Runbook: [prod-ops-runbook.md](file:///d:/Code/ydsz/ydsz-pmis/docs/operations/prod-ops-runbook.md)
- 灾备 SOP: [dr-sop.md](file:///d:/Code/ydsz/ydsz-pmis/deploy/smoke-test/dr-sop.md)
- 金丝雀发布: [canary-deployment.md](file:///d:/Code/ydsz/ydsz-pmis/docs/canary-deployment.md)
- 混沌工程: [chaos-engineering.md](file:///d:/Code/ydsz/ydsz-pmis/docs/chaos-engineering.md)
- 业务规则总册: [rule-verify.md](file:///d:/Code/ydsz/ydsz-pmis/docs/rules/rule-verify.md)
