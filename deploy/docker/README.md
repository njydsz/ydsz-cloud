# Docker · 容器化部署

> Docker Compose 编排 8 大中间件 + 开发环境基础设施
> 适用:开发 / 内网测试 / 快速验证

---

## 目录结构

```
docker/
├── docker-compose.dev.yml    # 8 容器编排(PostgreSQL/Redis/Nacos/MinIO/Seata/RocketMQ/XXL-Job/ES)
└── rocketmq/
    └── broker.conf           # RocketMQ Broker 专属配置
```

## 启动

```bash
# 一键启动(8 容器)
docker compose -f deploy/docker/docker-compose.dev.yml up -d

# 单独启动某个
docker compose -f deploy/docker/docker-compose.dev.yml up -d postgres

# 查看状态
docker compose -f deploy/docker/docker-compose.dev.yml ps

# 查看日志
docker compose -f deploy/docker/docker-compose.dev.yml logs -f nacos

# 停止(保留数据卷)
docker compose -f deploy/docker/docker-compose.dev.yml down

# 停止并清理数据卷
docker compose -f deploy/docker/docker-compose.dev.yml down -v
```

## 8 大中间件

| 容器 | 镜像 | 容器内端口 | 宿主机端口 |
|---|---|---|---|
| pmis-postgres | postgres:18-alpine | 5432 | 5432 |
| pmis-redis | redis:7-alpine | 6379 | 6379 |
| pmis-nacos | nacos/nacos-server:v2.4.3 | 8848/9848 | 8848/9848 |
| pmis-minio | minio/minio:latest | 9000/9001 | 9100/9101 |
| pmis-seata | seataio/seata-server:2.5 | 8091/7091 | 8091/7091 |
| pmis-rocketmq-namesrv | apache/rocketmq:5.3 | 9876 | 9876 |
| pmis-rocketmq-broker | apache/rocketmq:5.3 | 10911/10909 | 10911/10909 |
| pmis-xxl-job | xuxueli/xxl-job-admin:2.4 | 9100 | 9100 |
| pmis-elasticsearch | elasticsearch:8.15 | 9200/9300 | 9200/9300 |

## 访问入口

| 服务 | URL | 账号 |
|---|---|---|
| Nacos | http://127.0.0.1:8848/nacos | nacos/nacos |
| MinIO Console | http://127.0.0.1:9101 | minioadmin/minioadmin |
| Seata Console | http://127.0.0.1:7091 | admin/admin |
| XXL-Job Admin | http://127.0.0.1:9100/xxl-job-admin | admin/123456 |
| Elasticsearch | http://127.0.0.1:9200 | — |

## 数据持久化

容器数据通过 Docker Volume 保留:

```bash
# 查看卷
docker volume ls | grep pmis

# 备份
docker run --rm -v pmis-postgres-data:/data -v $(pwd):/backup alpine tar czf /backup/pg.tar.gz /data
```

## 与应用层联用

启动中间件后:

```bash
# 1. 导入 Nacos 共享配置
./deploy/ubuntu/scripts/import-nacos-config.sh pmis dev

# 2. 启动 7 个后端服务 + 前端
./deploy/ubuntu/scripts/start-all.sh
```

## 性能说明

- Docker 容器性能损耗约 2-5%(对比原生)
- 适合开发 / 内网测试,**不适合生产**
- 生产推荐 K8S(见 `../k8s/`)
