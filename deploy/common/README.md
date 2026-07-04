# Common · 跨环境共享资源

> 7 个中间件配置模板 + Nacos 共享配置 + 通用 SQL
> 所有部署环境(Docker / Ubuntu / Windows / K8S)都从这里读取

---

## 目录

1. [目录结构](#1-目录结构)
2. [中间件配置 conf/](#2-中间件配置-conf)
3. [Nacos 共享配置 nacos/](#3-nacos-共享配置-nacos)
4. [通用 SQL sql/](#4-通用-sql-sql)
5. [修改流程](#5-修改流程)
6. [相关链接](#6-相关链接)

---

## 1. 目录结构

```
common/
├── conf/                            # 7 个中间件原生部署的配置模板
│   ├── elasticsearch/               #  ES(elasticsearch.yml + jvm.options.d/heap.options)
│   ├── nacos/                       #  Nacos(application.properties)
│   ├── postgres/                    #  PG(postgresql.conf + pg_hba.conf)
│   ├── redis/                       #  Redis(redis.conf)
│   ├── rocketmq/                    #  RocketMQ(broker.conf)
│   ├── seata/                       #  Seata(application.yml + file.conf + registry.conf)
│   └── xxl-job/                     #  XXL-Job(application.properties)
├── nacos/                           # PMIS 业务 Nacos 配置
│   └── ydsz-pmis-common.yaml        #  7 微服务共享的 spring.* / nacos / redis / feign 配置
└── sql/                             # 通用 SQL(非主库初始化)
    └── tables_xxl_job_pg.sql        #  XXL-Job 的 PG 表
```

> **注**:MinIO 通过环境变量启动(`MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` / `MINIO_VOLUMES`),无需配置文件,所以 `conf/` 下没有 minio 目录。

---

## 2. 中间件配置 conf/

`conf/{middleware}/*` 是**中间件原生部署**(apt / Windows 安装 / K8S ConfigMap)时使用的配置文件。

### 2.1 谁会读这些文件

| 环境 | 读取方式 |
|---|---|
| [ubuntu/](../ubuntu/README.md) | `install-pmis-infra.sh` 复制到目标位置并做占位符替换 |
| [windows/](../windows/README.md) | `install-pmis-infra.ps1` 复制并做 Windows 路径占位符替换 |
| [docker/](../docker/README.md) | **不读**;docker 用 `docker-compose.dev.yml` 内嵌配置 |
| [k8s/](../k8s/README.md) | **不直接读**;中间件 K8S 化走 Helm chart 或云厂商 |

### 2.2 占位符约定

`ubuntu/` 和 `windows/` 的安装脚本会做以下占位符替换(完整列表见 [deploy/README.md §4.2](../README.md#42-占位符约定commonconf)):

| 占位符 | 含义 |
|---|---|
| `__PMIS_DATA_HOME__` | 数据根目录 |
| `__PMIS_LOG_HOME__` | 日志根目录 |
| `__PG_DATA__` | PG 数据目录 |
| `__ES_DATA__` | ES 数据目录 |
| `__NACOS_DATA__` | Nacos 数据目录 |

---

## 3. Nacos 共享配置 nacos/

`ydsz-pmis-common.yaml` 是 PMIS 7 个微服务启动时从 Nacos 拉取的**共享配置**。内容覆盖:

| 分类 | 配置项 |
|---|---|
| 数据源 | `spring.datasource.*` (PG / Druid) |
| 缓存 | `spring.redis.*` / `spring.redisson.*` |
| 注册与配置中心 | `spring.cloud.nacos.*` |
| Feign 客户端 | `ydsz-pmis.feign.{userinfo,project,workflow,system}.url` |
| 限流 | `spring.cloud.sentinel.transport.*` |
| 分布式事务 | `spring.cloud.alibaba.seata.*` |
| 消息 | `spring.cloud.stream.rocketmq.*` / `rocketmq.*` |
| 调度 | `xxl.job.*` |
| 存储 | `minio.*` |

### 3.1 导入命令

```bash
# Ubuntu / Docker
./deploy/ubuntu/scripts/import-nacos-config.sh pmis dev

# Windows
.\deploy\windows\scripts\import-nacos-config.bat pmis dev
```

参数:`namespace`(默认 `pmis`)、`group`(默认 `dev`,可改 `sit`/`uat`/`prod`)。

### 3.2 修改生效

修改 `ydsz-pmis-common.yaml` 后:

```bash
# 1. 重新导入
./deploy/ubuntu/scripts/import-nacos-config.sh pmis dev

# 2. 重启 PMIS 7 个服务(因为 Nacos 配置默认不自动刷新)
./deploy/ubuntu/scripts/start-all.sh
```

---

## 4. 通用 SQL sql/

非主库表结构,通常用于中间件自身初始化:

| 文件 | 用途 |
|---|---|
| `tables_xxl_job_pg.sql` | XXL-Job 的 PostgreSQL 表(主库用 PG 时) |

主库初始化走 [`docs/V1.0.0.sql`](../../docs/V1.0.0.sql)(126 表 + 5 视图),**不在本目录**。

执行示例(Ubuntu):

```bash
PGPASSWORD=pmis123 psql -h 127.0.0.1 -U pmis -d ydsz_pmis \
  -f deploy/common/sql/tables_xxl_job_pg.sql
```

---

## 5. 修改流程

| 改动 | 影响范围 | 后续操作 |
|---|---|---|
| 修改 `conf/*` 模板 | 后续 ubuntu/windows 安装 | 重跑安装脚本覆盖 |
| 修改 `nacos/ydsz-pmis-common.yaml` | 所有从 Nacos 拉配置的微服务 | 重新 `import-nacos-config.sh` + 重启服务 |
| 修改 `sql/*` | 通常只对新部署生效 | 手动执行 SQL |
| 修改任何文件 | **不要把 .env 密码提交** | 见仓库根 `.gitignore` |

---

## 6. 相关链接

- [deploy/ 总入口](../README.md)
- [docker/](../docker/README.md) · [k8s/](../k8s/README.md) · [ubuntu/](../ubuntu/README.md) · [windows/](../windows/README.md)
- [docs/INFRASTRUCTURE.md](../../docs/INFRASTRUCTURE.md) · 中间件部署详细步骤
