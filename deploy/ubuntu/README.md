# Ubuntu · Linux 原生部署

> Ubuntu 22.04 / 24.04 上的原生中间件安装 + systemd 托管
> 适用:准生产 / 生产(单机或主备)
> 优点:性能最佳,与 Linux 运维生态契合

---

## 目录结构

```
ubuntu/
├── install-pmis-infra.sh    # 一键安装 8 中间件
├── infra-manager.sh          # 中间件启停/状态管理
└── scripts/                  # 应用层启停脚本(.sh)
    ├── start-all.sh
    ├── stop-all.sh
    ├── check-env.sh
    └── import-nacos-config.sh
```

## 前置

| 工具 | 版本 |
|---|---|
| OS | Ubuntu 22.04 LTS / 24.04 LTS |
| 用户 | root 或 sudo 权限 |
| 网络 | 能访问 PostgreSQL PGDG / Redis 官方源 |

## 1. 一键安装 8 中间件

```bash
sudo ./deploy/ubuntu/install-pmis-infra.sh
```

脚本会:

1. 创建 `pmis` 系统用户
2. 安装 PostgreSQL 18 / Redis 7
3. 安装 JDK 21 + Nacos / XXL-Job / Seata(Java 中间件)
4. 部署 MinIO / RocketMQ / Elasticsearch(原生二进制)
5. 复制 `common/conf/` 模板并替换占位符
6. 注册 systemd 服务
7. 启动并验证

预计耗时:15-30 分钟(取决于网络)

## 2. 中间件管理

```bash
# 查看所有中间件状态
./deploy/ubuntu/infra-manager.sh status

# 启动 / 停止 / 重启
./deploy/ubuntu/infra-manager.sh start postgres
./deploy/ubuntu/infra-manager.sh stop redis
./deploy/ubuntu/infra-manager.sh restart nacos

# 启停全部
./deploy/ubuntu/infra-manager.sh start-all
./deploy/ubuntu/infra-manager.sh stop-all
```

支持 8 个中间件短名:`postgres` / `redis` / `nacos` / `minio` / `seata` / `rocketmq` / `xxl-job` / `elasticsearch`

## 3. 启动 PMIS 应用

中间件就绪后:

```bash
# 1. 导入 Nacos 共享配置
./deploy/ubuntu/scripts/import-nacos-config.sh pmis dev

# 2. 一键启动 7 个后端 + 前端
./deploy/ubuntu/scripts/start-all.sh

# 3. 仅启动后端(开发时省时)
./deploy/ubuntu/scripts/start-all.sh --backend

# 4. 停止
./deploy/ubuntu/scripts/stop-all.sh
./deploy/ubuntu/scripts/stop-all.sh --with-infra   # 含中间件
```

## 4. 数据/日志目录

| 用途 | 路径 |
|---|---|
| 数据 | `/opt/pmis/data/` |
| 日志 | `/var/log/pmis/` |
| 公共配置 | `/etc/pmis/` |
| Nacos data | `/opt/nacos/data/` |
| 启动日志 | `/var/log/pmis/{middleware}.log` |

## 5. systemd 单元位置

服务注册在:

```
/etc/systemd/system/
├── pmis-postgres.service
├── pmis-redis.service
├── pmis-nacos.service
├── pmis-minio.service
├── pmis-seata.service
├── pmis-rocketmq-namesrv.service
├── pmis-rocketmq-broker.service
├── pmis-xxl-job.service
└── pmis-elasticsearch.service
```

`install-pmis-infra.sh` 会自动创建,无需手动写。

## 6. 故障排查

```bash
# 查看某个服务的 systemd 日志
sudo journalctl -u pmis-nacos -f

# 查看启动脚本输出
cat /var/log/pmis/nacos.log

# 检查端口监听
ss -tlnp | grep 8848
```

详见 [`docs/INFRASTRUCTURE.md`](../../docs/INFRASTRUCTURE.md) Ubuntu 章节。
