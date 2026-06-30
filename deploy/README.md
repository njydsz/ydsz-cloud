# 部署 & DevOps

本目录包含 PMIS 项目的所有部署、运维、CI/CD 资源。

## 目录结构

```
deploy/
├── docker/                # Docker Compose 编排
│   ├── docker-compose.yml # 基础环境 (Nacos + PostgreSQL + Redis)
│   └── README.md
├── nacos/                 # Nacos 配置文件
│   ├── README.md
│   └── config/            # 各微服务 Nacos 配置
├── sql/                   # 数据库初始化 & 迁移脚本
│   └── V1.0.0_001__init_pmis_schema.sql
└── README.md
```

## 快速开始

```bash
# 启动基础环境
cd docker && docker compose up -d

# 查看服务状态
docker compose ps

# 启动后端服务
cd ../../ydsz-pmis-backend
mvn install -DskipTests
mvn -pl ydsz-pmis-gateway spring-boot:run

# 启动前端
cd ../ydsz-pmis-frontend
pnpm install && pnpm dev
```

## 服务端口清单

| 服务 | 端口 | 协议 | 说明 |
|------|------|------|------|
| 前端 (Vite) | 5173 | HTTP | 开发服务器 |
| API 网关 | 9000 | HTTP | 统一入口 |
| 认证服务 | 9001 | HTTP | ydsz-pmis-auth |
| 用户服务 | 9002 | HTTP | ydsz-pmis-user |
| 项目服务 | 9003 | HTTP | ydsz-pmis-project |
| 财务服务 | 9004 | HTTP | ydsz-pmis-finance |
| 资源服务 | 9005 | HTTP | ydsz-pmis-resource |
| 工作流 | 9006 | HTTP | ydsz-pmis-workflow |
| 报表 | 9007 | HTTP | ydsz-pmis-report |
| AI | 9008 | HTTP | ydsz-pmis-agent |
| 通知 | 9009 | HTTP | ydsz-pmis-notification |
| Nacos | 8848 | HTTP | 注册/配置中心 |
| PostgreSQL | 5432 | TCP | 主数据库 |
| Redis | 6379 | TCP | 缓存/会话 |

## 关键环境变量

| 变量 | 默认 | 说明 |
|------|------|------|
| `NACOS_SERVER_ADDR` | 127.0.0.1:8848 | Nacos 地址 |
| `NACOS_NAMESPACE` | pmis-dev | 命名空间 |
| `DB_HOST` | 127.0.0.1 | 数据库地址 |
| `DB_PORT` | 5432 | 数据库端口 |
| `DB_NAME` | pmis | 数据库名 |
| `DB_USER` | pmis | 数据库用户 |
| `DB_PASSWORD` | pmis@2026 | 数据库密码 |
| `REDIS_HOST` | 127.0.0.1 | Redis 地址 |
| `REDIS_PORT` | 6379 | Redis 端口 |
| `REDIS_PASSWORD` | pmis@2026 | Redis 密码 |
| `JWT_SECRET` | (内置) | JWT 签名密钥 (生产必须修改) |

## 生产部署建议

- 关闭 Nacos standalone 模式，使用集群模式
- PostgreSQL 主从架构 + 每日全量备份
- Redis Cluster (3 主 3 从)
- 所有敏感配置从 Vault / KMS 注入
- Nacos 配置中心开启鉴权
- 启用 HTTPS / TLS

## 监控

待集成：
- Prometheus + Grafana 系统监控
- SkyWalking 链路追踪
- ELK 日志收集分析
