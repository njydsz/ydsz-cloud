# PMIS 部署与 DevOps 目录
# --------------------------------------------------------------------------
# 用途：PMIS 项目所有部署、运维、CI/CD、安全合规资源。
# 适用：本地开发 → 集成测试 → 预发灰度 → 生产上线的全链路。
# 工具栈：Docker Compose / Kubernetes + Helm / Argo Rollouts / Ansible。
# --------------------------------------------------------------------------

## 目录结构

```
deploy/
├── ansible/                # 操作系统初始化与运维自动化
│   ├── inventory.yml
│   ├── playbook-os.yml
│   └── README.md
│
├── argo-rollouts/          # Kubernetes 渐进式发布（蓝绿/金丝雀）
│   ├── base/
│   ├── overlays/
│   ├── ops-commands.md
│   └── README.md
│
├── backup/                 # PostgreSQL 备份策略（全量 + 增量 + 验证）
│   ├── cron.d/
│   ├── pg_backup.sh
│   ├── pg_incremental.sh
│   ├── verify_last_backup.sh
│   └── README.md
│
├── canary/                 # 蓝绿 / 金丝雀发布脚本
│   ├── canary-rollback.sh
│   ├── canary-shift.sh
│   └── README.md
│
├── docker/                 # Docker Compose 编排（开发/测试环境）
│   ├── docker-compose.yml
│   ├── docker-compose.base.yml
│   ├── docker-compose.apps.yml
│   ├── Dockerfile.*
│   ├── nginx-frontend.conf
│   ├── docker-entrypoint.sh
│   ├── build-images.sh
│   └── README.md
│
├── migration/              # 历史数据迁移 + 字段加密迁移
│   ├── legacy-*.sh
│   ├── encrypted_field_migration*.sql
│   ├── finance-coa-mapping.sh
│   ├── monthly-reconcile-job.sh
│   └── README.md
│
├── monitoring/             # Prometheus + Grafana + Alertmanager
│   ├── alertmanager/
│   ├── grafana/
│   ├── prometheus/
│   ├── docker-compose.monitoring.yml
│   └── README.md
│
├── nacos/                  # Nacos 配置中心文件
│   ├── config/             # 各微服务 Nacos 配置（dev/sit/uat/prod）
│   ├── sync-nacos.ps1
│   ├── push-log.txt
│   └── README.md
│
├── nginx/                  # 反向代理 + HTTPS 证书自动续期
│   ├── nginx.conf
│   ├── conf.d/pmis.conf
│   ├── certbot-cron
│   └── README.md
│
├── redis/                  # Redis Cluster 集群（3 主 3 从）
│   ├── redis-cluster.yml
│   ├── cluster-verify.sh
│   └── README.md
│
├── cronjob/                # XXL-Job 分布式调度
│   ├── docker-compose.yml
│   ├── verify-xxl.sh
│   └── README.md
│
├── seata/                  # Seata 分布式事务（AT 模式）
│   ├── docker-compose.yml
│   ├── seata-client.properties
│   ├── verify-seata.sh
│   └── README.md
│
├── security/               # OWASP 安全扫描 + 等保测评
│   ├── crypto-verify.sh
│   ├── dependency-check.sh
│   ├── reports/
│   └── README.md
│
├── sentinel/               # Sentinel 限流/熔断规则
│   ├── flow-rules.json
│   ├── degrade-rules.json
│   └── README.md
│
├── sql/                    # 数据库 Schema、迁移与调优
│   ├── V1.0.0_*.sql        # Flyway 迁移
│   ├── index-tuning.sql
│   ├── postgresql.conf
│   ├── pg_hba.conf
│   └── README.md
│
└── README.md               # 本文件
```

## 快速开始（本地开发）

```bash
# 1. 启动基础环境（Nacos + PostgreSQL + Redis + RocketMQ + MinIO）
cd deploy/docker
docker compose -f docker-compose.yml -f docker-compose.base.yml up -d

# 2. 查看服务状态
docker compose ps

# 3. 初始化数据库（首次启动）
psql -U pmis_app -d pmis -f deploy/sql/index-tuning.sql
# Flyway 会在应用启动时自动执行 V*.sql

# 4. 推送 Nacos 配置
pwsh deploy/nacos/sync-nacos.ps1 -Env dev

# 5. 启动后端服务
cd ../../ydsz-pmis-backend
mvn install -DskipTests
mvn -pl ydsz-pmis-gateway spring-boot:run

# 6. 启动前端
cd ../ydsz-pmis-frontend
pnpm install && pnpm dev
```

## 服务端口清单

> 服务合并重构后保留 7 个核心微服务（+ 1 调度）：
> - `user` + `auth` → `userinfo`（端口 9002）
> - `file` + `config` + `audit` + `notification` + `message` → `system`（端口 9001）
> - `project` + `execution` → `project`（端口 9003）
>
> **端口分配原则（2026-07-03 修订）**: 9000 网关固定；9001-9006 按"基础→用户→业务→调度→流程→AI"依赖顺序连续编排；9007-9099 保留给未来模块。

| 服务 | 端口 | 协议 | 说明 |
|------|------|------|------|
| 前端 (Vite) | 5173 | HTTP | 开发服务器 |
| API 网关 | 9000 | HTTP | ydsz-pmis-gateway（统一入口） |
| 系统基础服务 | 9001 | HTTP | ydsz-pmis-system（原 file + config + audit + notification + message） |
| 用户信息中心 | 9002 | HTTP | ydsz-pmis-userinfo（原 user + auth） |
| 项目服务 | 9003 | HTTP | ydsz-pmis-project（原 project + execution） |
| 调度服务 | 9004 | HTTP | ydsz-pmis-cronjob（XXL-JOB Executor） |
| 工作流 | 9005 | HTTP | ydsz-pmis-workflow |
| AI Agent | 9006 | HTTP | ydsz-pmis-agent |
| Nacos | 8848 | HTTP | 注册/配置中心 |
| Seata | 8091/7091 | HTTP | 分布式事务（7091=Admin） |
| XXL-Job | 9100 | HTTP | 调度管理后台 |
| Sentinel | 8858 | HTTP | 限流/熔断 Dashboard |
| PostgreSQL | 5432 | TCP | 主数据库 |
| Redis | 6379 | TCP | 缓存/会话（Cluster 6 节点） |
| RocketMQ | 9876 | TCP | 消息队列 |
| Prometheus | 9090 | HTTP | 监控指标 |
| Grafana | 3000 | HTTP | 可视化面板 |
| Alertmanager | 9093 | HTTP | 告警聚合 |

## 关键环境变量

| 变量 | 默认 | 说明 |
|------|------|------|
| `NACOS_SERVER_ADDR` | 127.0.0.1:8848 | Nacos 地址 |
| `NACOS_NAMESPACE` | pmis-dev | 命名空间（dev/sit/uat/prod） |
| `DB_HOST` | 127.0.0.1 | 数据库地址 |
| `DB_PORT` | 5432 | 数据库端口 |
| `DB_NAME` | pmis | 数据库名 |
| `DB_USER` | pmis_app | 数据库用户 |
| `DB_PASSWORD` | pmis@2026 | 数据库密码（生产从 Vault 注入） |
| `REDIS_HOST` | 127.0.0.1 | Redis 地址 |
| `REDIS_PORT` | 6379 | Redis 端口 |
| `REDIS_PASSWORD` | pmis@2026 | Redis 密码 |
| `JWT_SECRET` | (内置) | JWT 签名密钥（生产必须 ≥ 32 字节且注入 Vault） |
| `SEATA_HOST` | 127.0.0.1 | Seata 服务地址 |
| `XXL_JOB_HOST` | 127.0.0.1 | XXL-Job Admin 地址 |

## 多环境策略

PMIS 通过 Nacos namespace + 配置文件后缀区分环境：

| 环境 | Nacos namespace | 配置后缀 | 部署方式 | 数据隔离 |
|------|-----------------|----------|----------|----------|
| dev | pmis-dev | -dev | Docker Compose | 独立 DB |
| sit | pmis-sit | -sit | Docker Compose / K8s | 独立 DB |
| uat | pmis-uat | -uat | K8s（预发） | 独立 DB |
| prod | pmis | (无) | K8s + Argo Rollouts | 主从 DB |

切换环境：构建时通过 Maven Profile `spring.profiles.active` 或启动参数 `--env=prod`。

## 生产部署建议

### 基础设施

- 关闭 Nacos standalone 模式，使用集群模式（3 节点 + MySQL 持久化）
- PostgreSQL 主从架构（1 主 2 从）+ 每日全量 + 每小时增量备份
- Redis Cluster（3 主 3 从）+ 哨兵或 Cluster 模式
- RocketMQ 集群（2 主 2 从 NameServer + 多 Broker）
- MinIO 分布式存储（4 节点 erasure coded）
- 所有敏感配置从 Vault / KMS 注入，禁止明文落盘

### 应用层

- 启用 HTTPS / TLS（Let's Encrypt 或企业 CA）
- Nacos 配置中心开启鉴权（自定义 username/password）
- Sentinel 限流/熔断规则动态下发（Nacos 配置中心）
- Seata 分布式事务（AT 模式，db 存储）
- XXL-Job 调度管理（admin + executor）
- Spring Cloud Gateway 灰度路由（基于 Header / 用户标签）

### 可观测性

- Prometheus + Grafana 系统监控
- SkyWalking / OpenTelemetry 链路追踪
- Loki + Promtail 日志收集
- Alertmanager 告警（企微 / 钉钉 / 邮件）

## 监控与告警

监控栈已在 `monitoring/` 目录完整搭建：

- **Prometheus**：15s 抓取间隔，存储 15 天
- **Grafana**：预置 4 个 Dashboard（overview / jvm / db / business）
- **Alertmanager**：P0/P1 企微告警 + P2 邮件 + P3 看板

启动：
```bash
cd deploy/monitoring
docker compose -f docker-compose.monitoring.yml up -d
# 访问 http://localhost:3000（admin / pmis@2026）
```

## 安全合规

- 加密算法：BCrypt 密码 / HS256 JWT / AES-256-GCM 字段 / TLS 1.2+
- 依赖扫描：`deploy/security/dependency-check.sh`
- 加密校验：`deploy/security/crypto-verify.sh`
- 等保三级：参见 `docs/security/dengbao-2.0-3-level-checklist.md`

## 灾备与高可用

| 组件 | 备份策略 | RPO | RTO |
|------|----------|-----|-----|
| PostgreSQL | 每日全量 + 每小时增量 | < 1h | < 30min |
| Redis | AOF + RDB + 集群 | < 1min | < 5min |
| Nacos | MySQL 持久化 + 每日 dump | < 1h | < 10min |
| MinIO | 跨节点 erasure coded | 0 | < 5min |
| 应用镜像 | Harbor 镜像仓库 + 异地复制 | 0 | < 10min |

## 发布流程

```
1. CI 构建（GitHub Actions / GitLab CI）
   └─ mvn package → 推送镜像到 Harbor
2. 预发部署（K8s + Argo Rollouts 蓝绿）
   └─ 100% 流量到旧版本 → 0% 流量验证新版本
3. 灰度发布（Argo Rollouts 金丝雀）
   └─ 5% → 25% → 50% → 100%（每步观察 5min）
4. 监控观察
   └─ 错误率 < 0.1% & P99 RT < 200ms 才可继续
5. 异常回滚
   └─ kubectl argo rollouts abort <name>（秒级回滚到旧版本）
```

详细操作：`deploy/argo-rollouts/ops-commands.md`

## 关键路径速查

- 启动 Nacos：`docker compose -f deploy/docker/docker-compose.base.yml up -d nacos`
- 启动 Redis Cluster：`deploy/redis/cluster-verify.sh`
- 启动 Seata：`deploy/seata/verify-seata.sh`（先启动容器）
- 启动 XXL-Job：`deploy/cronjob/verify-xxl.sh`
- 启动监控：`docker compose -f deploy/monitoring/docker-compose.monitoring.yml up -d`
- 数据库备份：`deploy/backup/pg_backup.sh` / `pg_incremental.sh`
- 加密验证：`deploy/security/crypto-verify.sh <url>`

## 联系 & 故障支持

- 运维值班：ops@pmis.example.com
- 故障应急群：见企业微信
- 监控看板：<https://grafana.pmis.example.com>
- 工单系统：<https://jira.pmis.example.com>
