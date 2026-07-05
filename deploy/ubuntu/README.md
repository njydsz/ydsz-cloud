# Ubuntu · Linux 原生部署

> Ubuntu 22.04 / 24.04 上的原生中间件安装 + systemd 托管
> 适用:准生产(单机) / 小规模生产 / 内网测试
> 优点:性能最佳,与 Linux 运维生态契合

---

## 目录

1. [目录结构](#1-目录结构)
2. [前置](#2-前置)
3. [一键安装 8 中间件](#3-一键安装-8-中间件)
4. [中间件管理](#4-中间件管理)
5. [启动 PMIS 应用](#5-启动-pmis-应用)
6. [数据/日志目录](#6-数据日志目录)
7. [systemd 单元](#7-systemd-单元)
8. [故障排查](#8-故障排查)
9. [相关链接](#9-相关链接)

---

## 1. 目录结构

```
ubuntu/
├── install-pmis-infra.sh        # 一键安装 7 中间件
├── infra-manager.sh              # 中间件启停/状态管理
└── scripts/                      # 应用层启停脚本(.sh)
    ├── start-all.sh              # 一键启动 7 后端 + 前端
    ├── stop-all.sh               # 一键停止
    ├── check-env.sh              # 环境检查
    └── import-nacos-config.sh    # Nacos 共享配置导入
```

---

## 2. 前置

| 项 | 要求 |
|---|---|
| OS | Ubuntu 22.04 LTS / 24.04 LTS |
| 用户 | root 或 sudo 权限 |
| 网络 | 能访问 PostgreSQL PGDG / Redis 官方源 / GitHub release |
| 内存 | 建议 ≥ 8GB |
| 磁盘 | `/opt` ≥ 20GB |

---

## 3. 一键安装 7 中间件

```bash
sudo ./deploy/ubuntu/install-pmis-infra.sh
```

脚本会自动完成:

1. 创建 `pmis` 系统用户
2. 安装 PostgreSQL 18 / Redis 8(apt)
3. 安装 JDK 21(apt)
4. 部署 Nacos / XXL-Job / Seata(Java 中间件,下载 release 包)
5. 部署 MinIO / RocketMQ(原生二进制)
6. 复制 [`../common/conf/`](../common/README.md) 模板并替换占位符
7. 注册 systemd 服务
8. 启动并验证

**预计耗时**:15-30 分钟(取决于网络)

可选参数:

```bash
sudo ./deploy/ubuntu/install-pmis-infra.sh --no-start       # 只安装不启动
sudo ./deploy/ubuntu/install-pmis-infra.sh --skip=minio     # 跳过指定中间件
```

---

## 4. 中间件管理

```bash
# 查看所有中间件状态
./deploy/ubuntu/infra-manager.sh status

# 启动 / 停止 / 重启 单个
./deploy/ubuntu/infra-manager.sh start postgres
./deploy/ubuntu/infra-manager.sh stop redis
./deploy/ubuntu/infra-manager.sh restart nacos

# 启停全部
./deploy/ubuntu/infra-manager.sh start-all
./deploy/ubuntu/infra-manager.sh stop-all
```

支持的 8 个短名:`postgres` / `redis` / `nacos` / `minio` / `seata` / `rocketmq` / `xxl-job` / `elasticsearch`,以及 `all`。

---

## 5. 启动 PMIS 应用

中间件就绪后:

```bash
# 1. 导入 Nacos 共享配置
./deploy/ubuntu/scripts/import-nacos-config.sh pmis dev

# 2. 一键启动 7 个后端 + 前端(后台)
./deploy/ubuntu/scripts/start-all.sh

# 3. 仅启动后端(开发时省时)
./deploy/ubuntu/scripts/start-all.sh --backend

# 4. 仅启动基础设施(不常用)
./deploy/ubuntu/scripts/start-all.sh --infra

# 5. 停止
./deploy/ubuntu/scripts/stop-all.sh
./deploy/ubuntu/scripts/stop-all.sh --with-infra   # 含中间件
```

> 启动日志位于 `$ROOT/.run-logs/`,每个服务一个 `.log` + `.pid` 文件。

---

## 6. 数据/日志目录

| 用途 | 路径 |
|---|---|
| PMIS 数据 | `/opt/pmis/data/` |
| PMIS 日志 | `/var/log/pmis/` |
| 启动脚本输出 | `$ROOT/.run-logs/{service}.log` |
| PostgreSQL data | `/var/lib/postgresql/18/main/` |
| Nacos data | `/opt/nacos/data/` |
| Redis data | `/var/lib/redis/` |
| MinIO data | `/opt/pmis/data/minio/` |
| RocketMQ data | `/opt/pmis/data/rocketmq/` |
| XXL-Job 日志 | `/var/log/pmis/xxl-job.log` |
| ES data | `/opt/pmis/data/elasticsearch/` |

---

## 7. systemd 单元

`install-pmis-infra.sh` 会在 `/etc/systemd/system/` 注册 8 个服务(7 中间件 + rocketmq 拆为 2 个):

```
/etc/systemd/system/
├── pmis-postgres.service
├── pmis-redis.service
├── pmis-nacos.service
├── pmis-minio.service
├── pmis-seata.service
├── pmis-rocketmq-namesrv.service
├── pmis-rocketmq-broker.service
└── pmis-xxl-job.service
```

也可以直接用 systemd 命令:

```bash
sudo systemctl status pmis-nacos
sudo systemctl restart pmis-redis
sudo journalctl -u pmis-nacos -f       # 实时日志
```

---

## 8. 故障排查

| 现象 | 排查命令 |
|---|---|
| 服务起不来 | `sudo systemctl status pmis-{name}` |
| 启动失败 | `sudo journalctl -u pmis-{name} -n 100` |
| 端口未监听 | `ss -tlnp \| grep 8848` |
| PG 连不上 | `sudo -u postgres psql -c "SELECT version();"` |
| Nacos 502 | `tail -f /var/log/pmis/nacos.log` |
| 磁盘满 | `du -sh /opt/pmis/* \| sort -h` |

详细排查见 [`../README.md`](../README.md#8-占位符约定commonconf) 占位符约定 + [`../README.md §4`](../README.md#4-8-大中间件)。

---

## 9. 相关链接

- [deploy/ 总入口](../README.md)
- [common/](../common/README.md) · 共享配置(本目录脚本会从这里读)
- [docker/](../docker/README.md) · 容器化(替代方案,11 容器)
- [k8s/](../k8s/README.md) · K8S 部署(生产推荐)
- [windows/](../windows/README.md) · Windows 等价方案
- 8 中间件详细步骤见 [`../README.md §4`](../README.md#4-8-大中间件)
