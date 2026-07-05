# PMIS · 部署与运维目录

> YDSZ PMIS 项目的全链路部署资源入口
> 覆盖:Docker / Ubuntu / Windows / K8S 四种部署形态,以及 8 大中间件配置模板
> 文档版本:v2.0 · 2026-07-04(按环境拆分重组)

---

## 目录

1. [目录结构](#1-目录结构)
2. [环境选型决策树](#2-环境选型决策树)
3. [一键快速开始(3 步)](#3-一键快速开始3-步)
4. [8 大中间件](#4-8-大中间件)
5. [子目录快速参考](#5-子目录快速参考)
6. [7 个微服务端口约定](#6-7-个微服务端口约定)
7. [环境变量(.env)](#7-环境变量env)
8. [占位符约定(common/conf)](#8-占位符约定commonconf)
9. [数据库初始化](#9-数据库初始化)
10. [相关文档](#10-相关文档)

---

## 1. 目录结构

```
deploy/
├── README.md                       # 本文件(总入口)
├── .env.example                    # 环境变量模板
│
├── common/                         # 跨环境共享资源
│   ├── conf/                       # 7 中间件原生部署的配置模板
│   ├── nacos/                      # PMIS 业务 Nacos 共享配置
│   ├── sql/                        # 通用 SQL(XXL-Job PG 表)
│   └── README.md
│
├── docker/                         # Docker Compose 部署(11 容器)
│   ├── docker-compose.dev.yml      # 8 中间件 + 3 辅助 容器编排
│   ├── rocketmq/broker.conf
│   └── README.md
│
├── helm/                           # Helm Chart 部署(K8s 参数化方案)
│   └── ydsz-pmis/                  # Chart 包(含 4 环境覆盖值)
│       ├── Chart.yaml
│       ├── values.yaml             # 默认值
│       ├── values-{dev,sit,uat,prod}.yaml
│       └── templates/              # deployment/svc/ingress/hpa/pdb 等
│
├── scripts/                        # 跨环境部署辅助脚本
│   ├── build-images.sh/.ps1        # 批量构建 7 后端 + 1 前端镜像
│   └── smoke-test.sh/.ps1          # 部署后冒烟测试(9 项关键检查)
│
├── ubuntu/                         # Ubuntu 原生部署 + systemd
│   ├── install-pmis-infra.sh
│   ├── infra-manager.sh
│   ├── scripts/                    # start-all / stop-all / import-nacos
│   └── README.md
│
├── windows/                        # Windows 原生部署 + NSSM
│   ├── install-pmis-infra.ps1
│   ├── infra-manager.ps1
│   ├── scripts/                    # start-all / stop-all / import-nacos
│   └── README.md
│
├── k8s/                            # Kubernetes 部署(Kustomize)
│   ├── base/                       # 7 微服务公共 base
│   ├── overlays/{dev,sit,uat,prod}/# 多环境差异
│   └── README.md
│
└── sql/                            # 主库 SQL
    └── V1.0.0.sql                  # 126 表 + 5 视图(含中文注释)
```

> **设计原则**:`common/` 是单一事实源(中间件配置模板),`docker/ubuntu/windows` 分别走三种部署形态,`k8s/` 是生产推荐形态,所有环境共享同一份 SQL。

---

## 2. 环境选型决策树

```
                        ┌─ 你的目标环境是?
                        │
            ┌───────────┼────────────┬─────────────┐
            │           │            │             │
         容器化       Linux       Windows       K8S 集群
            │           │            │             │
        [docker/]   [ubuntu/]    [windows/]     [k8s/]
            │           │            │             │
        开发/测试    准生产/单机   演示/Windows   生产/HA
```

| 场景 | 推荐 | 原因 |
|---|---|---|
| 本地开发 / 联调 | [docker/](docker/README.md) | 一行命令拉起 11 容器,零依赖 |
| 内网测试 / 准生产(单机) | [ubuntu/](ubuntu/README.md) | 性能最佳,systemd 托管 |
| 客户演示 / Windows-only | [windows/](windows/README.md) | 与开发机一致 |
| 生产 / HA | [k8s/](k8s/README.md) | HPA + PDB + 多副本 |
| 纯配置参考 | [common/](common/README.md) | 7 中间件配置模板 |

---

## 3. 一键快速开始(3 步)

### 3.1 Docker(最快,推荐用于开发)

```bash
# 1. 启动 11 个中间件容器(8 中间件 + 3 辅助)
docker compose -f deploy/docker/docker-compose.dev.yml up -d

# 2. 初始化主库(126 张表)
PGPASSWORD=Limw1020 psql -h 127.0.0.1 -U postgres -d ydsz-pmis \
  -f deploy/sql/V1.0.0.sql

# 3. 启动 PMIS 7 个后端 + 前端
./deploy/ubuntu/scripts/start-all.sh    # Ubuntu
# 或 .\deploy\windows\scripts\start-all.bat   # Windows
```

### 3.2 Ubuntu 原生(准生产)

```bash
sudo ./deploy/ubuntu/install-pmis-infra.sh   # 安装 8 中间件
./deploy/ubuntu/scripts/start-all.sh         # 启动应用
```

### 3.3 Windows 原生

```powershell
.\deploy\windows\install-pmis-infra.ps1      # 安装 8 中间件
.\deploy\windows\scripts\start-all.bat       # 启动应用
```

### 3.4 K8S

```bash
kubectl apply -k deploy/k8s/overlays/dev
```

---

## 4. 8 大中间件

| # | 中间件 | 版本 | 端口 | 必要性 | 用途 |
|---|---|---|---|---|---|
| 1 | PostgreSQL | 18 | 5432 | **必装** | 主数据库(126 张表) |
| 2 | Redis | 8 | 6379 | **必装** | 缓存 / 分布式锁 |
| 3 | Nacos | 2.3.2 | 8848 / 9848 | **必装** | 服务注册 + 配置中心 |
| 4 | MinIO | latest | 9100 / 9101 | **必装** | 对象存储 |
| 5 | Seata | 2.5 | 8091 / 7091 | 推荐 | 分布式事务 |
| 6 | RocketMQ | 5.3 | 9876 / 10911 | 推荐 | 消息中间件 |
| 7 | XXL-Job | 2.4 | 9100 | 推荐 | 分布式任务调度 |
| 8 | Elasticsearch | 8.15 | 9200 | 可选 | 全文搜索(默认用 PG tsvector) |

> Docker 环境实际启动 **11 个容器**(8 中间件 + 3 辅助:minio-init / rocketmq-console / 内置 namesrv-broker 角色分离),详见 [docker/README.md §3](docker/README.md#3-11-个容器清单)。

---

## 5. 子目录快速参考

### 5.1 [common/](common/README.md) · 跨环境共享

- `conf/` — 7 个中间件原生部署的配置模板(Nacos / PG / Redis / RocketMQ / Seata / XXL-Job / MinIO)
- `nacos/ydsz-pmis-common.yaml` — 7 微服务启动时从 Nacos 拉取的共享配置
- `sql/tables_xxl_job_pg.sql` — XXL-Job 的 PG 表

### 5.2 [docker/](docker/README.md) · 容器化

- `docker-compose.dev.yml` — 10 容器编排
- 适合:开发 / 内网测试 / 快速验证
- **不适合生产**(性能损耗 2-5%)

### 5.3 [ubuntu/](ubuntu/README.md) · Linux 原生 + systemd

- `install-pmis-infra.sh` — 一键安装 7 中间件
- `infra-manager.sh` — 中间件启停/状态管理
- 适合:准生产(单机) / 小规模生产

### 5.4 [windows/](windows/README.md) · Windows 原生 + NSSM

- `install-pmis-infra.ps1` — 一键安装 7 中间件
- `infra-manager.ps1` — 中间件启停/状态管理
- 适合:Windows 内网测试 / 演示

### 5.5 [k8s/](k8s/README.md) · Kubernetes 部署

- `base/` — 7 微服务公共 base
- `overlays/{dev,sit,uat,prod}/` — 多环境差异
- 适合:生产 / HA

---

## 6. 7 个微服务端口约定

> 端口分配原则(2026-07-03 修订):9000 网关固定;9001-9006 按"基础→用户→业务→调度→流程→AI"依赖顺序连续编排;9007-9099 保留给未来模块。

| 服务 | 端口 | 协议 | 说明 |
|---|---|---|---|
| API 网关 | 9000 | HTTP | ydsz-pmis-gateway(统一入口) |
| 系统基础服务 | 9001 | HTTP | ydsz-pmis-system(原 file + config + audit + notification + message) |
| 用户信息中心 | 9002 | HTTP | ydsz-pmis-userinfo(原 user + auth) |
| 项目服务 | 9003 | HTTP | ydsz-pmis-project(原 project + execution) |
| 调度服务 | 9004 | HTTP | ydsz-pmis-cronjob(XXL-Job Executor) |
| 工作流 | 9005 | HTTP | ydsz-pmis-workflow |
| AI Agent | 9006 | HTTP | ydsz-pmis-agent |
| 前端(Vite dev) | 5173 | HTTP | 开发服务器 |

---

## 7. 环境变量(.env)

复制 `deploy/.env.example` 为 `deploy/.env`,按需修改:

```bash
cp deploy/.env.example deploy/.env
```

**关键变量**:

| 变量 | 默认 | 说明 |
|---|---|---|
| `POSTGRES_HOST` / `DB_HOST` | 127.0.0.1 | PG 地址 |
| `POSTGRES_PASSWORD` / `DB_PASSWORD` | Limw1020 | PG 密码(**生产必须改**) |
| `REDIS_PASSWORD` | Limw1020 | Redis 密码(**生产必须改**) |
| `NACOS_SERVER_ADDR` | 127.0.0.1:8848 | Nacos 地址 |
| `NACOS_NAMESPACE` | pmis | 命名空间 |
| `NACOS_PROFILE` | dev | dev / sit / uat / prod |
| `MINIO_ENDPOINT` | http://127.0.0.1:9100 | MinIO API |
| `XXL_JOB_ADMIN_ADDRESSES` | http://127.0.0.1:9100/xxl-job-admin | 调度中心 |
| `ROCKETMQ_NAME_SERVER` | 127.0.0.1:9876 | 消息队列 |
| `SEATA_ENABLED` | false | 分布式事务开关 |
| `JWT_SECRET` | (空) | **必须 ≥ 32 字节随机字符串** |
| `CORS_ALLOWED_ORIGINS` | (空) | 生产必须显式域名白名单 |

> 完整变量清单见 [deploy/.env.example](.env.example)。
> docker compose 会自动读取 `deploy/.env`;后端服务启动也会通过 `set -a; source deploy/.env; set +a` 加载。

---

## 8. 占位符约定(common/conf)

`ubuntu/` 和 `windows/` 的安装脚本会做以下占位符替换(`common/conf/*` 模板 → 目标位置):

| 占位符 | 含义 | Ubuntu 实际值 | Windows 实际值 |
|---|---|---|---|
| `__PMIS_DATA_HOME__` | 数据根目录 | `/opt/pmis/data/` | `C:\pmis\data\` |
| `__PMIS_LOG_HOME__` | 日志根目录 | `/var/log/pmis/` | `C:\pmis\logs\` |
| `__PG_DATA__` | PG 数据目录 | `/var/lib/postgresql/18/main/` | `C:\Program Files\PostgreSQL\18\data\` |
| `__NACOS_DATA__` | Nacos 数据目录 | `/opt/nacos/data/` | `C:\pmis\nacos\data\` |

> **修改流程**:改 `common/conf/{middleware}/*` → 重跑对应环境的 install 脚本。

---

## 9. 数据库初始化

| 用途 | 文件 | 位置 |
|---|---|---|
| 主库(126 表 + 5 视图) | `V1.0.0.sql` | `deploy/sql/V1.0.0.sql` |
| XXL-Job PG 表 | `tables_xxl_job_pg.sql` | `deploy/common/sql/tables_xxl_job_pg.sql` |

**主库初始化**:

```bash
PGPASSWORD=Limw1020 psql -h 127.0.0.1 -U postgres -d ydsz-pmis \
  -f deploy/sql/V1.0.0.sql
```

> ⚠️ 脚本是**幂等**的,可重复执行(已通过 PostgreSQL 18.4 验证)。
> MinIO、Seata、RocketMQ、ES 等中间件的元数据由各自容器/服务首次启动自动创建,无需手灌。

---

## 10. 相关文档

### 本目录文档(各子目录 README)

- [common/README.md](common/README.md) — 跨环境共享资源(conf 模板 + Nacos 共享配置 + SQL)
- [docker/README.md](docker/README.md) — Docker 部署(11 容器清单)
- [ubuntu/README.md](ubuntu/README.md) — Ubuntu 原生 + systemd
- [windows/README.md](windows/README.md) — Windows 原生 + NSSM
- [k8s/README.md](k8s/README.md) — Kubernetes 部署(Kustomize)
- [helm/ydsz-pmis/README.md](helm/ydsz-pmis/README.md) — Helm Chart 部署(K8s 参数化方案)

### 本目录其他文件

- [.env.example](.env.example) — 环境变量模板(复制为 `.env` 后修改)
- [sql/V1.0.0.sql](sql/V1.0.0.sql) — 主库初始化脚本(126 表 + 5 视图,含中文注释,已通过 PG 18.4 验证)
- [common/nacos/ydsz-pmis-common.yaml](common/nacos/ydsz-pmis-common.yaml) — 7 微服务共享的 Nacos 配置
- [common/conf/](common/conf/) — 7 中间件原生部署的配置模板
- [scripts/build-images.sh](scripts/build-images.sh) — 批量构建 7 后端 + 1 前端 Docker 镜像
- [scripts/build-images.ps1](scripts/build-images.ps1) — Windows PowerShell 批量构建镜像
- [scripts/smoke-test.sh](scripts/smoke-test.sh) — 部署后冒烟测试(9 项关键检查)
- [scripts/smoke-test.ps1](scripts/smoke-test.ps1) — Windows PowerShell 冒烟测试
- [../ydsz-pmis-backend/Dockerfile](../ydsz-pmis-backend/Dockerfile) — 后端统一多阶段 Dockerfile
- [../ydsz-pmis-frontend/Dockerfile](../ydsz-pmis-frontend/Dockerfile) — 前端 Nginx 多阶段 Dockerfile

### 仓库根 README

- [../README.md](../README.md) — 项目级 README
