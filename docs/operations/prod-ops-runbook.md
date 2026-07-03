<!--
  ===========================================================================
  文件名: prod-ops-runbook.md
  路径:   docs/operations/prod-ops-runbook.md
  作用:   PMIS 生产环境（pmis-prod）运维 Runbook，含日常巡检、故障应急、回滚、数据恢复
  适用:   SRE / DBA / 运维 / 7x24 oncall
  关联:   post-deploy-checklist.md  /  helm/  /  deploy/backup/  /  deploy/monitoring/
  ===========================================================================
-->

# PMIS 生产环境运维 Runbook

> 批次 21 P4-2 | 适用: PMIS 14 微服务 + 1 前端 生产 (pmis-prod) 集群
> 文档版本: V1.0 | 编制: 2026-07-01 | 最近更新: 2026-07-03
> 维护: SRE 团队 / DBA 团队

> 📌 **本 Runbook 是生产故障应急的唯一权威手册**，任何线上异常处理必须优先查阅对应章节，事后补充进本手册。

## 0. 紧急情况速查

| 现象 | 立即行动 | 联系 |
|------|----------|------|
| 业务 502/504 雪崩 | 看 [§6.1 限流降级] | oncall SRE |
| 数据库连接耗尽 | 看 [§6.2 连接池] | oncall DBA |
| Redis 集群脑裂 | 看 [§6.3 Redis 故障] | oncall SRE |
| Sentry 红色 issue 激增 | 看 [§6.4 紧急回滚] | oncall 开发 + SRE |
| 误操作删表/删库 | 看 [§6.5 数据恢复] | oncall DBA + GM |

7×24 联系方式见 [§9 责任分工]。

## 1. 日常运维节奏

### 1.1 每日 (09:00 / 17:00)
- [ ] 看 Grafana 仪表盘: 错误率 / p99 延迟 / CPU / 内存 / DB 连接
- [ ] 看 Sentry: 当日新 issue 数 / 回归 issue
- [ ] 看 PMIS 经营驾驶舱: 健康度评分 / EVM 红黄项目 / Bench 成本
- [ ] 看账单: 阿里云 RDS / Redis / 容器服务昨日账单

### 1.2 每周 (周一 10:00)
- [ ] DailyReconcile 6 维度对账检查 (PMIS `/execution/reconcile`)
- [ ] 备份有效性: `verify_last_backup.sh` 校验 RPO ≤ 1h
- [ ] SonarQube: 新增代码异味 / 漏洞
- [ ] SLO 月度报告: 99.5% SLA 是否达标

### 1.3 每月 (第一个周一)
- [ ] 全量灾备演练: 主备切换 + RPO / RTO 验证
- [ ] 安全扫描: OWASP ZAP 主动扫描 + dependency-check
- [ ] 容量规划: 当月资源使用率 → Q+1 资源申请
- [ ] 证书检查: 90 天内到期的 SSL 提前续期

## 2. 服务清单

| 模块 | 端口 | 副本数(基线) | 关键依赖 | 备注 |
|------|------|------------|----------|------|
| gateway | 9000 | 4 | Nacos, Redis | 唯一对外入口 |
| system | 9001 | 2 | Nacos, Redis, MinIO, MySQL | 文件/配置/审计/通知/消息模板（合并） |
| userinfo | 9002 | 3 | Nacos, MySQL, Redis | 用户/认证/权限/部门/资源池/Bench（合并） |
| project | 9003 | 5 | Nacos, MySQL, Redis, Feign(userinfo) | 商机/立项/合同/执行/财务/报表/驾驶舱（合并），核心域副本最多 |
| cronjob | 9004 | 1 | XXL-JOB, MySQL | 单实例避免并发 |
| workflow | 9005 | 2 | Nacos, MySQL | 自研工作流引擎 |
| agent | 9006 | 2 | Nacos, Feign(project) | AI 编排 |
| frontend | 80 | 4 | Nginx | Vue 静态 |

> 端口分配（2026-07-03 修订）：9000 网关；9001-9006 按"基础→用户→业务→调度→流程→AI"依赖顺序连续编排；9007-9099 保留给未来模块。

## 3. 容量规划 (HPA 阈值)

```yaml
# 以 execution 服务为例 (其他服务类似)
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: execution
spec:
  minReplicas: 3
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300  # 5min 缓冲, 避免抖动
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
        - type: Percent
          value: 100
          periodSeconds: 30  # 30s 翻倍, 紧急扩容
```

调度器 (XXL-JOB) **禁止 HPA** 副本=1, 否则并发触发。

## 4. 部署发布 SOP

### 4.1 标准发布 (Canary → 全量)
见 [docs/canary-deployment.md](file:///d:/Code/ydsz/ydsz-pmis/docs/canary-deployment.md)。

### 4.2 紧急发布 (Hotfix)
跳过金丝雀, 直接发版:
```bash
# 1. 创建 hotfix 分支
git checkout -b hotfix/p0-xxx main

# 2. 修复 + 单测 + Sonar 阻断
mvn -pl <module> test

# 3. 推送 + 触发 CI (gitlab-ci.yml 中的 hotfix pipeline)
git push origin hotfix/p0-xxx

# 4. CI 完成后到 staging 灰度 5min (与 oncall 共同确认)

# 5. kubectl set image 紧急升级 (绕过金丝雀)
kubectl set image deployment/pmis-<service> <service>=<image>:<tag> -n pmis-prod
kubectl rollout status deployment/pmis-<service> -n pmis-prod --timeout=300s

# 6. 监控 30min, 通知业务方
```

Hotfix 后必须在 24h 内:
- 补 MR 走正常流程
- 补 changelog
- 补单元测试 (必须 100% 覆盖修复代码)

### 4.3 灰度配置更新
仅修改 Nacos config (无需重启服务):
```bash
# 1. 备份当前配置
curl -X GET "http://nacos:8848/nacos/v1/cs/configs?dataId=xxx&group=DEFAULT_GROUP" \
  -o /tmp/config-backup-$(date +%Y%m%d-%H%M%S).json

# 2. 推送新配置
curl -X PUT "http://nacos:8848/nacos/v1/cs/configs" \
  -d "dataId=xxx&group=DEFAULT_GROUP&namespaceId=pmis-prod" \
  --data-urlencode "content=@new-config.yaml"

# 3. 验证 (各服务 actuator/refresh 间隔 5s)
curl http://<service>:<port>/actuator/configprops | grep <key>
```

## 5. 数据库运维

### 5.1 慢 SQL 排查
```bash
# 1. 实时慢 SQL
psql -h pg-host -U pmis -d pmis -c \
  "SELECT pid, now()-query_start AS duration, state, query
   FROM pg_stat_activity
   WHERE state='active' AND now()-query_start > interval '3 seconds'
   ORDER BY duration DESC LIMIT 20;"

# 2. 强制终止
psql -c "SELECT pg_terminate_backend(<pid>);"

# 3. 写入慢日志, 后续用 pg_stat_statements 分析
```

### 5.2 锁等待
```sql
SELECT
  blocked_locks.pid AS blocked_pid,
  blocking_locks.pid AS blocking_pid,
  blocked_activity.query AS blocked_query,
  blocking_activity.query AS blocking_query
FROM pg_catalog.pg_locks blocked_locks
JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
JOIN pg_catalog.pg_locks blocking_locks ON blocking_locks.locktype = blocked_locks.locktype
  AND blocking_locks.pid != blocked_locks.pid
  AND blocking_locks.relation IS NOT NULL
  AND blocking_locks.granted
JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid
WHERE NOT blocked_locks.granted;
```

### 5.3 Flyway 迁移
```bash
# 1. 备份 (关键变更前必做)
./deploy/backup/pg_backup.sh full

# 2. 提交新 SQL 脚本到 deploy/sql/V1.0.0_XXX__name.sql
#    ⚠️ 命名必须递增, 不能改已发布脚本

# 3. CI 自动在 deploy 阶段执行 mvn flyway:migrate

# 4. 监控: 15min 内必须完成, 否则告警
#    失败回滚: mvn flyway:repair (V1__ 之前)
```

## 6. 故障应急

### 6.1 限流降级
```bash
# Sentinel 实时降级 (dashboard 或 API)
curl -X POST "http://sentinel-dashboard:8718/degradeRules" \
  -d "app=pmis-execution&resource=/api/v1/execution/contract/page" \
  -d "grade=1&count=100&timeWindow=10"

# 紧急关闭 AI 编排 (使用 FeatureFlag)
curl -X PUT "http://pmis-system:9001/api/v1/feature-flags/AGENT_ORCHESTRATION/enabled" \
  -H "Content-Type: application/json" -d "false"
```

### 6.2 连接池耗尽
```bash
# 1. 看当前活跃连接
kubectl exec -it deployment/pmis-execution -- \
  curl -s http://localhost:9006/actuator/metrics/hikaricp.connections.active

# 2. 临时扩容: 加副本 + 调 max-pool-size
kubectl scale deployment/pmis-execution --replicas=8 -n pmis-prod

# 3. 慢 SQL 排查 (见 §5.1)
```

### 6.3 Redis 故障
```bash
# 1. 看集群状态
redis-cli -h redis-cluster-1 -p 6379 cluster info | grep cluster_state
# 期望: cluster_state:ok

# 2. 故障 master 切换 (30s 内自动 failover)
redis-cli -h redis-cluster-1 -p 6379 cluster nodes | grep master

# 3. 业务降级: PMIS 已实现 Cache Aside + Fallback, 大部分场景自动恢复
#    如有异常, 见 Sentry 报警

# 4. 持久化: 检查 AOF 文件大小
redis-cli -h redis-cluster-1 -p 6379 info persistence | grep aof_size
```

### 6.4 紧急回滚
```bash
# 方案 A: 回滚镜像 (30s)
kubectl rollout undo deployment/pmis-<service> -n pmis-prod
kubectl rollout status deployment/pmis-<service> -n pmis-prod --timeout=300s

# 方案 B: 回滚到指定版本
kubectl rollout undo deployment/pmis-<service> --to-revision=<n> -n pmis-prod

# 方案 C: 关闭 FeatureFlag (无需重启)
curl -X PUT "http://pmis-system:9001/api/v1/feature-flags/<KEY>/enabled?enabled=false"

# 方案 D: 关闭混沌工程
curl -X POST "http://pmis-system:9001/api/v1/chaos/history/clear"
```

### 6.5 数据恢复
```bash
# 1. 查看最近可用备份
ls -lh /backup/pg/full/ | tail -10

# 2. 验证备份完整性
./deploy/backup/verify_last_backup.sh

# 3. 恢复 (新建临时实例避免污染生产)
./deploy/backup/pg_backup.sh restore \
  --file=/backup/pg/full/pmis_20261215_0200.sql.gz \
  --target-host=pg-restore.pmis-prod \
  --target-db=pmis_restore

# 4. 比对数据
psql -h pg-restore -U pmis -d pmis_restore -c \
  "SELECT count(*) FROM pmis_initiation;" 
# 与生产比对

# 5. 决定:
#    - 整体误操作: 主备切换 (RPO 即最新备份到故障点的差距)
#    - 单表误操作: binlog point-in-time recovery
```

## 7. 监控告警规则

### 7.1 业务告警 (Sentry + Prometheus)
| 规则 | 阈值 | 通知 |
|------|------|------|
| HTTP 5xx 错误率 | > 1% 持续 5min | oncall SRE |
| p99 延迟 | > 1500ms 持续 5min | oncall SRE |
| Sentry 新 issue | > 5/小时 | oncall 开发 |
| PMIS 健康度评分 | < 75 (B 级以下) | GM + CFO |
| EVM 红色项目 | ≥ 3 个 | PMO + SRE |

### 7.2 基础设施告警
| 规则 | 阈值 | 通知 |
|------|------|------|
| 节点 CPU | > 80% 持续 10min | oncall SRE |
| 节点内存 | > 85% 持续 10min | oncall SRE |
| DB 连接使用率 | > 80% | oncall DBA |
| Redis 内存 | > 75% | oncall SRE |
| 磁盘空间 | < 20% | oncall SRE |
| 备份失败 | 任意 | oncall DBA |

### 7.3 告警升级路径
- 1st level: oncall SRE (5min 内响应)
- 2nd level: SRE lead (15min 内)
- 3rd level: GM + 全员 (业务影响 > 30min)

## 8. 故障复盘 SOP

故障解决后 48h 内:
1. 召集 5-Why 会议 (SRE + 开发 + PM + QA)
2. 输出 Post-Mortem 文档: `docs/operations/post-mortem-<date>-<ticket>.md`
3. 必须包含:
   - 时间线 (发现 → 升级 → 解决)
   - 根本原因 (不是表象)
   - 影响面 (业务 / 客户 / 收入)
   - 改进 Action Item (含 owner + deadline)
4. Action Item 进入下个迭代, 必须有验证

## 9. 责任分工

| 角色 | 值班时段 | 联系电话 |
|------|----------|----------|
| oncall SRE | 周一-周日 7×24 | (内部飞书) |
| oncall DBA | 周一-周日 7×24 | (内部飞书) |
| SRE Lead | 工作日 9-22 | (内部飞书) |
| GM | 工作日 9-22 | (内部飞书) |
| 业务方 PM | 各项目群 | — |

飞书值班机器人会自动 @ 当前 oncall。

## 10. 附录

- 部署脚本: [deploy/](file:///d:/Code/ydsz/ydsz-pmis/deploy/)
- 灾备 SOP: [deploy/smoke-test/dr-sop.md](file:///d:/Code/ydsz/ydsz-pmis/deploy/smoke-test/dr-sop.md)
- 监控面板: Grafana (生产内网)
- 链路追踪: SkyWalking (生产内网)
- 灾备演练记录: [docs/operations/backup-drill-record-2026-12.md](file:///d:/Code/ydsz/ydsz-pmis/docs/operations/backup-drill-record-2026-12.md)
- 金丝雀发布: [docs/canary-deployment.md](file:///d:/Code/ydsz/ydsz-pmis/docs/canary-deployment.md)
- 混沌工程: [docs/chaos-engineering.md](file:///d:/Code/ydsz/ydsz-pmis/docs/chaos-engineering.md)
- CI/CD: [.gitlab-ci.yml](file:///d:/Code/ydsz/ydsz-pmis/.gitlab-ci.yml)
