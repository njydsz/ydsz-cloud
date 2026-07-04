# Docker · 容器化部署

> Docker Compose 编排 11 个容器(8 中间件 + 3 辅助)
> 适用:开发 / 内网测试 / 快速验证
> 性能:相比原生损耗约 2-5%,**不适合生产**

---

## 目录

1. [目录结构](#1-目录结构)
2. [快速开始](#2-快速开始)
3. [11 个容器清单](#3-11-个容器清单)
4. [访问入口](#4-访问入口)
5. [数据持久化](#5-数据持久化)
6. [与 PMIS 应用联用](#6-与-pmis-应用联用)
7. [常见问题](#7-常见问题)
8. [相关链接](#8-相关链接)

---

## 1. 目录结构

```
docker/
├── docker-compose.dev.yml    # 11 容器编排(PostgreSQL/Redis/Nacos/MinIO/Seata/RocketMQ/XXL-Job/ES + 3 辅助)
└── rocketmq/
    └── broker.conf           # RocketMQ Broker 专属配置(挂载到容器)
```

---

## 2. 快速开始

```bash
# 启动全部(首次约 3-5 分钟拉镜像)
docker compose -f deploy/docker/docker-compose.dev.yml up -d

# 单独启动某个中间件
docker compose -f deploy/docker/docker-compose.dev.yml up -d postgres
docker compose -f deploy/docker/docker-compose.dev.yml up -d nacos

# 查看状态(健康检查)
docker compose -f deploy/docker/docker-compose.dev.yml ps

# 实时查看日志
docker compose -f deploy/docker/docker-compose.dev.yml logs -f nacos

# 停止(数据卷保留)
docker compose -f deploy/docker/docker-compose.dev.yml down

# 停止 + 清理数据卷(危险:会删除所有数据!)
docker compose -f deploy/docker/docker-compose.dev.yml down -v
```

---

## 3. 11 个容器清单

### 3.1 8 大中间件

| 容器名 | 镜像 | 容器内端口 | 宿主机端口 | 用途 |
|---|---|---|---|---|
| `pmis-postgres` | `postgres:18-alpine` | 5432 | 5432 | 主数据库 |
| `pmis-redis` | `redis:7-alpine` | 6379 | 6379 | 缓存/分布式锁 |
| `pmis-nacos` | `nacos/nacos-server:v2.4.3` | 8848 / 9848 / 7848 | 8848 / 9848 | 注册/配置中心 |
| `pmis-minio` | `minio/minio:latest` | 9000 / 9001 | **9100 / 9101** | 对象存储 |
| `pmis-seata` | `seataio/seata-server:2.5` | 8091 / 7091 | 8091 / 7091 | 分布式事务 |
| `pmis-rocketmq-namesrv` | `apache/rocketmq:5.3` | 9876 | 9876 | NameServer |
| `pmis-rocketmq-broker` | `apache/rocketmq:5.3` | 10911 / 10909 | 10911 / 10909 | Broker |
| `pmis-xxl-job` | `xuxueli/xxl-job-admin:2.4` | 8080 | **9100** | 任务调度 |
| `pmis-elasticsearch` | `elasticsearch:8.15` | 9200 / 9300 | 9200 / 9300 | 全文搜索 |

### 3.2 3 个辅助容器

| 容器名 | 作用 |
|---|---|
| `pmis-minio-init` | 首次启动自动创建 MinIO bucket |
| `pmis-rocketmq-console` | RocketMQ Web 控制台(8080 端口) |
| (内置) | 同一镜像内的 namesrv + broker 角色分离 |

> ⚠️ **端口冲突提示**:XXL-Job 宿主机端口 **9100** 与 MinIO API 端口 **9100** 是不同容器,通过 Docker 端口映射隔离(XXL-Job 用 9100,MinIO 用 9100+9101);原生部署需调整其中之一。

---

## 4. 访问入口

| 服务 | URL | 账号 |
|---|---|---|
| 前端(Vite dev) | http://127.0.0.1:5173 | — |
| API 网关 | http://127.0.0.1:9000 | — |
| Nacos | http://127.0.0.1:8848/nacos | `nacos` / `nacos` |
| MinIO Console | http://127.0.0.1:9101 | 见 [`../.env.example`](../.env.example) 的 `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` |
| Seata Console | http://127.0.0.1:7091 | `admin` / `admin` |
| XXL-Job Admin | http://127.0.0.1:9100/xxl-job-admin | `admin` / `123456` |
| Elasticsearch | http://127.0.0.1:9200 | — |
| RocketMQ Console | http://127.0.0.1:8080 | — |
| PostgreSQL | `127.0.0.1:5432` | 见 [`../.env.example`](../.env.example) 的 `POSTGRES_USER` / `POSTGRES_PASSWORD` |
| Redis | `127.0.0.1:6379` | 见 [`../.env.example`](../.env.example) 的 `REDIS_PASSWORD` |

> **重要**:Docker 实际账号密码由 `deploy/.env`(从 `.env.example` 复制)决定。
> 容器内 `docker-compose.dev.yml` 用 `${VAR:-default}` 兜底,`.env` 已设置的值会覆盖默认。

---

## 5. 数据持久化

容器数据通过 Docker Volume 保留(`docker-compose.dev.yml` 中定义):

```bash
# 列出 PMIS 相关的卷
docker volume ls | grep pmis

# 备份 PG(示例)
docker run --rm \
  -v pmis-postgres-data:/data \
  -v ${PWD}:/backup \
  alpine tar czf /backup/pg-backup-$(date +%F).tar.gz /data

# 恢复
docker run --rm -v pmis-postgres-data:/data -v ${PWD}:/backup \
  alpine tar xzf /backup/pg-backup-2026-07-04.tar.gz -C /
```

> 默认数据卷名:`pmis-{postgres,redis,nacos,minio,seata,rocketmq,xxl-job,elasticsearch}-data`

---

## 6. 与 PMIS 应用联用

```bash
# 1. 启动中间件(本目录)
docker compose -f deploy/docker/docker-compose.dev.yml up -d

# 2. 导入 Nacos 共享配置(Ubuntu 命令,Docker 容器内同样适用)
./deploy/ubuntu/scripts/import-nacos-config.sh pmis dev

# 3. 启动 7 个 PMIS 后端 + 前端
./deploy/ubuntu/scripts/start-all.sh
```

Windows 上:

```powershell
docker compose -f deploy/docker/docker-compose.dev.yml up -d
.\deploy\windows\scripts\import-nacos-config.bat pmis dev
.\deploy\windows\scripts\start-all.bat
```

---

## 7. 常见问题

| 现象 | 原因 | 解决 |
|---|---|---|
| 启动报 `port is already allocated` | 宿主机端口被占用 | `netstat -ano` 找占用进程,杀掉或改 docker-compose 端口 |
| 容器一直 `Restarting` | 健康检查失败 | `docker logs pmis-nacos` 看具体报错 |
| ES 启动报 `max virtual memory areas vm.max_map_count` | 宿主机 mmap 上限太低 | Linux:`sudo sysctl -w vm.max_map_count=262144` |
| Nacos 启动报 `Unable to start embedded Tomcat` | JVM 内存不足 | 至少分配 1G 给 Nacos |
| MinIO Console 报 `AccessDenied` | 未创建 bucket | 等待 `pmis-minio-init` 跑完(约 30s) |

---

## 8. 相关链接

- [deploy/ 总入口](../README.md)
- [common/](../common/README.md) · 共享配置(原生部署从这里读)
- [k8s/](../k8s/README.md) · 生产推荐
- [ubuntu/](../ubuntu/README.md) · [windows/](../windows/README.md) · 原生部署
- 8 中间件详细步骤见 [`../README.md §4`](../README.md#4-8-大中间件) + 各子目录 § 故障排查
