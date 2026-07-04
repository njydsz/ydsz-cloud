# YDSZ PMIS · 部署手册

> 适用场景：内网测试环境 / 客户演示环境 / 准生产环境
> 文档版本：v1.0 · 2026-07-04

---

## 目录

- [1. 部署架构总览](#1-部署架构总览)
- [2. 基础设施要求](#2-基础设施要求)
- [3. 快速部署（推荐）](#3-快速部署推荐)
- [4. 手动部署](#4-手动部署)
- [5. 环境变量详解](#5-环境变量详解)
- [6. Nacos 共享配置](#6-nacos-共享配置)
- [7. 数据库初始化](#7-数据库初始化)
- [8. 内网部署最佳实践](#8-内网部署最佳实践)
- [9. 升级与回滚](#9-升级与回滚)
- [10. 备份与恢复](#10-备份与恢复)
- [11. 监控与告警](#11-监控与告警)
- [12. 常见问题 FAQ](#12-常见问题-faq)
- [📖 附录: 8 大中间件详细部署](./INFRASTRUCTURE.md)

---

## 1. 部署架构总览

### 1.1 部署拓扑

```
┌────────────────────────────────────────────────────────────────┐
│                     客户端浏览器（Vue 3 SPA）                   │
└────────────────────────────┬───────────────────────────────────┘
                             │ HTTPS
                             ▼
┌────────────────────────────────────────────────────────────────┐
│          Nginx (反向代理 + 静态资源 + SSL 卸载)                  │
│   域名: pmis.yourcompany.local  →  :443                        │
└────────────────────────────┬───────────────────────────────────┘
                             │ HTTP
                             ▼
┌────────────────────────────────────────────────────────────────┐
│      API Gateway (ydsz-pmis-gateway :9000)                      │
│      - 路由  - 限流  - CORS  - 鉴权                            │
└──┬────────┬────────┬────────┬────────┬────────┬───────────────┘
   │        │        │        │        │        │
   ▼        ▼        ▼        ▼        ▼        ▼
┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐
│system││userin││projec││cronjo││workfl││agent │
│:9001 ││:9002 ││:9003 ││:9004 ││:9005 ││:9006 │
└──┬───┘└──┬───┘└──┬───┘└──┬───┘└──┬───┘└──┬───┘
   │       │       │       │       │       │
   └───────┴───────┴───────┴───────┴───────┘
                    │
        ┌───────────┼───────────┐
        ▼           ▼           ▼
   ┌────────┐  ┌────────┐  ┌────────┐
   │Postgres│  │ Redis  │  │  Nacos │ (注册+配置)
   │  :5432 │  │  :6379 │  │  :8848 │
   └────────┘  └────────┘  └────────┘
        │
        └──────┐
               ▼
           ┌────────┐
           │ MinIO  │ (文件存储)
           │  :9100 │
           └────────┘
```

### 1.2 模块清单

| # | 模块 | 端口 | 启动顺序 | 可降级 |
|---|---|---|---|---|
| 1 | ydsz-pmis-gateway | 9000 | 1 | — |
| 2 | ydsz-pmis-system | 9001 | 2 | — |
| 3 | ydsz-pmis-userinfo | 9002 | 3 | — |
| 4 | ydsz-pmis-project | 9003 | 4 | — |
| 5 | ydsz-pmis-cronjob | 9004 | 5 | 可改为 Spring @Scheduled |
| 6 | ydsz-pmis-workflow | 9005 | 6 | — |
| 7 | ydsz-pmis-agent | 9006 | 7 | LLM 失败时降级 mock |

### 1.3 资源占用参考（10 用户场景）

| 组件 | CPU | 内存 | 磁盘 |
|---|---|---|---|
| PostgreSQL 18 | 1 核 | 512MB | 5GB（初始）+ 0.5GB/年 |
| Redis 7 | 0.5 核 | 256MB | 1GB |
| Nacos 2.4 | 0.5 核 | 512MB | 1GB |
| MinIO | 0.5 核 | 256MB | 10GB（按业务增长） |
| 7 × 后端服务 | 2 核（峰值） | 2GB | — |
| 前端 (Nginx) | 0.1 核 | 64MB | 50MB |
| **合计** | **≈ 5 核** | **≈ 4GB** | **≈ 17GB** |

> **内网推荐配置**：4 核 8GB 即可流畅运行；2 核 4GB 也能跑（关闭 agent 模块即可）。

---

## 2. 基础设施要求

### 2.1 硬件最低要求

| 维度 | 开发环境 | 内网测试 | 准生产 |
|---|---|---|---|
| CPU | 4 核 | 8 核 | 16 核 |
| 内存 | 8 GB | 16 GB | 32 GB |
| 系统盘 | 50 GB SSD | 100 GB SSD | 200 GB SSD |
| 数据盘 | — | 500 GB | 2 TB（RAID 10） |
| 网络 | 100 Mbps | 1 Gbps | 1 Gbps × 2 聚合 |

### 2.2 软件要求

| 软件 | 版本 | 说明 |
|---|---|---|
| OS | CentOS 7+ / Ubuntu 20.04+ / Windows Server 2019+ | 内核 ≥ 5.4 |
| Docker | 24+ | 推荐 Docker CE |
| Docker Compose | v2 (内置) | `docker compose version` 验证 |
| JDK | 21 LTS | OpenJDK / Temurin |
| Maven | 3.9+ | 3.9.6 已验证 |
| Node.js | 20 LTS | 20.x 已验证 |
| pnpm | 9+ | `npm i -g pnpm` |
| Nginx | 1.24+ | 反向代理 |
| PostgreSQL Client | 15+ | 备份恢复需要 |
| Redis Client | 6+ | 调试用 |

### 2.3 端口规划

| 端口 | 用途 | 防火墙策略 |
|---|---|---|
| 80 / 443 | Nginx HTTP/HTTPS | 对外开放 |
| 5173 | 前端 dev server | 仅内网 |
| 9000-9006 | 后端服务 | 仅内网 |
| 5432 | PostgreSQL | 仅内网 |
| 6379 | Redis | 仅内网 |
| 8848 / 9848 | Nacos | 仅内网 |
| 9100 / 9101 | MinIO | 仅内网 |
| 9100/xxl-job-admin | XXL-Job（可选） | 仅内网 |
| 9876 | RocketMQ（可选） | 仅内网 |

---

## 3. 快速部署（推荐）

### 3.1 一键脚本

参见 [QUICKSTART.md](QUICKSTART.md) 第 1 节。

```bash
# 1. 克隆代码
git clone https://gitlab.njydsz.com/ydsz/oursource/ydsz-pmis.git
cd ydsz-pmis

# 2. 配置（修改默认账号密码）
cp deploy/.env.example deploy/.env
vim deploy/.env

# 3. 一键启动
./deploy/ubuntu/scripts/start-all.sh
```

### 3.2 启动后必做

1. **修改默认密码**（admin / 123456 → 强密码）
2. **修改 Nacos 密码**（nacos/nacos → 强密码）
3. **修改 JWT 密钥**（`JWT_SECRET` 必须 ≥ 32 字节）
4. **配置 HTTPS**（见 [8.3 节](#83-https-配置)）
5. **配置备份**（见 [10. 节](#10-备份与恢复)）

---

## 4. 手动部署

### 4.1 启动基础设施

```bash
cd deploy/docker
docker compose -f docker-compose.dev.yml up -d

# 等待健康
docker compose -f docker-compose.dev.yml ps
```

### 4.2 初始化数据库

**方式 A：自动（推荐）**

`docker-compose.dev.yml` 已挂载 `docs/V1.0.0.sql` 到 `docker-entrypoint-initdb.d/`，**首次启动容器时自动执行**。

**方式 B：手动**

```bash
# 复制 SQL 到容器
docker cp docs/V1.0.0.sql pmis-postgres:/tmp/

# 进入容器执行
docker exec -it pmis-postgres psql -U pmis -d ydsz_pmis -f /tmp/V1.0.0.sql
```

### 4.3 导入 Nacos 共享配置

```bash
./deploy/ubuntu/scripts/import-nacos-config.sh pmis dev
```

或在 Nacos 控制台手动操作：
- 命名空间：`pmis`（如不存在需先创建）
- DataId：`ydsz-pmis-common.yaml`
- Group：`dev`
- 配置格式：YAML
- 内容：复制 `deploy/common/nacos/ydsz-pmis-common.yaml`

### 4.4 编译并启动后端

```bash
cd ydsz-pmis-backend

# 1. 编译公共模块
mvn -pl ydsz-pmis-common,ydsz-pmis-literule -am install -DskipTests

# 2. 启动 gateway（按依赖顺序）
mvn -pl ydsz-pmis-gateway spring-boot:run &

# 3. 启动其他服务（每个开一个终端）
mvn -pl ydsz-pmis-system spring-boot:run &
mvn -pl ydsz-pmis-userinfo spring-boot:run &
mvn -pl ydsz-pmis-project spring-boot:run &
mvn -pl ydsz-pmis-cronjob spring-boot:run &
mvn -pl ydsz-pmis-workflow spring-boot:run &
mvn -pl ydsz-pmis-agent spring-boot:run &
```

### 4.5 启动前端

```bash
cd ydsz-pmis-frontend
pnpm install
pnpm dev   # http://localhost:5173
```

---

## 5. 环境变量详解

所有环境变量集中在 `deploy/.env`，启动脚本会注入到所有进程。

### 5.1 必填项

| 变量 | 说明 | 示例 |
|---|---|---|
| `POSTGRES_PASSWORD` | 数据库密码 | 强密码 |
| `REDIS_PASSWORD` | Redis 密码 | 强密码 |
| `NACOS_PASSWORD` | Nacos 密码 | 强密码 |
| `JWT_SECRET` | JWT 签名密钥 | ≥ 32 字节随机串 |
| `MINIO_ACCESS_KEY` | MinIO 用户名 | minioadmin |
| `MINIO_SECRET_KEY` | MinIO 密码 | 强密码 |

### 5.2 高级项

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SEATA_ENABLED` | false | 是否启用 Seata 分布式事务 |
| `LLM_PROVIDER` | mock | AI Agent LLM 类型：mock/openai/dashscope/qianfan |
| `OPENAI_API_KEY` | 空 | OpenAI 密钥（启用时必填） |
| `CAPTCHA_REQUIRED` | false | 登录是否强制图形验证码 |
| `ROCKETMQ_CONSUMER_ENABLED` | true | RocketMQ 消费者开关 |
| `LOG_ROOT_LEVEL` | INFO | 根日志级别 |
| `LOG_PMIS_LEVEL` | DEBUG | 业务代码日志级别 |

### 5.3 关键默认值

| 变量 | 值 | 含义 |
|---|---|---|
| `POSTGRES_HOST` | 127.0.0.1 | Docker host 网络下访问宿主机 |
| `NACOS_SERVER_ADDR` | 127.0.0.1:8848 | 同上 |
| `MINIO_ENDPOINT` | http://127.0.0.1:9100 | 同上 |

> ⚠️ **容器内访问宿主机**：在 Linux 上用 `172.17.0.1` 或 `host.docker.internal`；Windows / Mac 用 `host.docker.internal`。

---

## 6. Nacos 共享配置

### 6.1 共享配置机制

7 个微服务都通过 `spring.cloud.nacos.config.shared-configs` 引用 `ydsz-pmis-common.yaml`：

```yaml
spring:
  cloud:
    nacos:
      config:
        shared-configs:
          - data-id: ydsz-pmis-common.yaml
            group: ${spring.profiles.active}
            refresh: true
```

**作用**：
- 数据源、Redis、MyBatis-Plus、日志等通用配置只写一份
- Nacos 推送后所有服务实时刷新（@RefreshScope）

### 6.2 配置命名规范

| DataId | Group | 说明 |
|---|---|---|
| `ydsz-pmis-common.yaml` | dev/sit/uat/prod | **所有服务共享** |
| `ydsz-pmis-{module}.yaml` | dev/sit/uat/prod | 单服务独有（如有） |
| `pmis-flow-engine.yaml` | dev/sit/uat/prod | 工作流引擎配置 |

### 6.3 命名空间

| Namespace | 用途 |
|---|---|
| `pmis` | 业务配置 |
| `pmis-public` | 公共配置（密钥等） |
| `pmis-gray` | 灰度发布 |

### 6.4 配置热更新

```bash
# 1. 在 Nacos 控制台修改 ydsz-pmis-common.yaml 的某项
# 2. 等待 30 秒
# 3. 7 个服务自动刷新（无需重启）
# 4. 验证：日志会输出 "Refresh keys changed"
```

---

## 7. 数据库初始化

### 7.1 首次初始化

`docs/V1.0.0.sql` 包含 **126 张表 + 5 个视图**，约 504KB。

**重要**：
- ✅ 脚本支持**幂等**（CREATE TABLE IF NOT EXISTS / ON CONFLICT DO NOTHING）
- ✅ 已包含中文表/字段注释
- ⚠️ 不会自动创建**初始数据**（如管理员账号、字典），需手动 INSERT 或走初始化任务

### 7.2 初始数据

**方式 A：通过 application 启动时自动插入**（如已实现）

**方式 B：手动执行**

```sql
-- 1. 创建管理员账号（密码 123456 的 BCrypt 哈希）
INSERT INTO sys_user (username, password, real_name, status, created_at)
VALUES ('admin', '$2a$10$...', '系统管理员', 1, NOW())
ON CONFLICT (username) DO NOTHING;

-- 2. 分配角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (...);

-- 3. 初始化字典
INSERT INTO sys_dict (type, code, label, sort) VALUES ...;
```

**方式 C：通过 Flyway / Liquibase 版本化**（推荐生产使用，目前未启用）

### 7.3 升级迁移

未来若新增表/字段，**新增文件** `V1.0.1_001__add_xxx.sql` 即可（数字递增），不会影响现有数据。

### 7.4 数据导出

```bash
# 完整导出
docker exec pmis-postgres pg_dump -U pmis -d ydsz_pmis > backup_$(date +%Y%m%d).sql

# 仅结构
docker exec pmis-postgres pg_dump -U pmis -d ydsz_pmis --schema-only > schema.sql

# 恢复
cat backup_20260704.sql | docker exec -i pmis-postgres psql -U pmis -d ydsz_pmis
```

---

## 8. 内网部署最佳实践

### 8.1 内网环境特点

- 无外网访问（不能拉 Docker 镜像、不能调 LLM API）
- 客户端通过内网 IP 访问
- 通常一台物理机部署多套环境
- 用户量小（10-50 人）

### 8.2 推荐配置

**最低配置（10 用户）**：
- 单台物理机：4 核 8GB
- 不启用 AI Agent（`LLM_PROVIDER=mock`）
- 不启用 RocketMQ（用 Spring Event 替代）
- 不启用 ES（用 PG 全文搜索）

**推荐配置（50 用户）**：
- 2 台物理机：1 台应用 + 1 台数据库
- 4 核 16GB × 2
- 启用全部基础组件

### 8.3 HTTPS 配置

**方式 A：Nginx 反代 + 自签证书**

```bash
# 1. 生成自签证书
mkdir -p /etc/nginx/ssl
openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
  -keyout /etc/nginx/ssl/pmis.key \
  -out /etc/nginx/ssl/pmis.crt \
  -subj "/CN=pmis.yourcompany.local"

# 2. 配置 Nginx /etc/nginx/conf.d/pmis.conf
```

```nginx
upstream pmis_backend {
    server 127.0.0.1:9000;
    keepalive 32;
}

upstream pmis_frontend {
    server 127.0.0.1:5173;
    keepalive 16;
}

server {
    listen 80;
    server_name pmis.yourcompany.local;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl;
    server_name pmis.yourcompany.local;

    ssl_certificate     /etc/nginx/ssl/pmis.crt;
    ssl_certificate_key /etc/nginx/ssl/pmis.key;
    ssl_protocols       TLSv1.2 TLSv1.3;

    # 前端
    location / {
        proxy_pass http://pmis_frontend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # API
    location /api/ {
        proxy_pass http://pmis_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

**方式 B：使用 mkcert 生成本地可信证书**

```bash
mkcert pmis.yourcompany.local
# 输出 pmis.yourcompany.local.pem 和 -key.pem，配置到 Nginx
```

### 8.4 内网 DNS / Hosts

所有内网客户端需要将 `pmis.yourcompany.local` 解析到 Nginx 服务器 IP：

```bash
# Linux/Mac
echo "192.168.1.100 pmis.yourcompany.local" | sudo tee -a /etc/hosts

# Windows (管理员 PowerShell)
Add-Content C:\Windows\System32\drivers\etc\hosts "192.168.1.100 pmis.yourcompany.local"
```

或在内网 DNS 服务器添加 A 记录。

### 8.5 端口精简

内网部署可**关闭不需要的端口**减少攻击面：

```bash
# 编辑 deploy/.env
POSTGRES_PORT=5432          # 数据库仅内网访问即可
REDIS_PORT=6379             # 同上
NACOS_PORT=8848             # 同上
MINIO_API_PORT=9100         # 同上
MINIO_CONSOLE_PORT=9101     # 可关闭（如不需要 Web UI）
```

---

## 9. 升级与回滚

### 9.1 升级流程

```bash
# 1. 拉取新代码
cd /opt/ydsz-pmis
git pull origin main

# 2. 备份数据库（重要！）
docker exec pmis-postgres pg_dump -U pmis -d ydsz_pmis > /backup/pre-upgrade-$(date +%Y%m%d-%H%M).sql

# 3. 执行数据库迁移（如有）
docker exec -i pmis-postgres psql -U pmis -d ydsz_pmis < deploy/sql/V1.0.1_001__add_xxx.sql

# 4. 重新构建并滚动重启
./deploy/ubuntu/scripts/start-all.sh
# 或使用蓝绿发布（见 9.3）
```

### 9.2 回滚流程

```bash
# 1. 停止所有服务
./deploy/ubuntu/scripts/stop-all.sh --with-infra

# 2. 恢复数据库
cat /backup/pre-upgrade-20260704.sql | docker exec -i pmis-postgres psql -U pmis -d ydsz_pmis

# 3. 切换代码
git checkout v1.0.0

# 4. 重新启动
./deploy/ubuntu/scripts/start-all.sh
```

### 9.3 蓝绿发布

```bash
# 启动新版本（不同端口）
SERVER_PORT_GATEWAY=19000 ./deploy/ubuntu/scripts/start-all.sh --backend

# Nginx 切流量
sed -i 's/9000/19000/g' /etc/nginx/conf.d/pmis.conf
nginx -s reload

# 确认新版本无问题
curl http://localhost:19000/actuator/health

# 停止旧版本
./deploy/ubuntu/scripts/stop-all.sh

# 恢复端口
sed -i 's/19000/9000/g' /etc/nginx/conf.d/pmis.conf
nginx -s reload
```

---

## 10. 备份与恢复

### 10.1 自动备份脚本

```bash
#!/bin/bash
# /opt/scripts/backup-pmis.sh - 加入 crontab 每天凌晨 2 点执行
BACKUP_DIR=/backup/pmis
DATE=$(date +%Y%m%d-%H%M)
KEEP_DAYS=30

mkdir -p $BACKUP_DIR

# 1. PostgreSQL 全量备份
docker exec pmis-postgres pg_dump -U pmis -d ydsz_pmis -Fc > $BACKUP_DIR/pgis-$DATE.dump

# 2. MinIO 文件备份
docker exec pmis-minio mc mirror /data $BACKUP_DIR/minio-$DATE/

# 3. Nacos 配置备份
curl -s "http://127.0.0.1:8848/v1/cs/configs?dataId=ydsz-pmis-common.yaml&group=dev&namespaceId=pmis" > $BACKUP_DIR/nacos-$DATE.json

# 4. 清理 30 天前
find $BACKUP_DIR -mtime +$KEEP_DAYS -delete

echo "Backup completed: $BACKUP_DIR/*-$DATE"
```

```bash
# 加入 crontab
crontab -e
# 添加:
0 2 * * * /opt/scripts/backup-pmis.sh >> /var/log/pmis-backup.log 2>&1
```

### 10.2 恢复

```bash
# 恢复数据库
docker exec -i pmis-postgres pg_restore -U pmis -d ydsz_pmis --clean < /backup/pgis-20260704.dump

# 恢复 MinIO
docker exec pmis-minio mc mirror /backup/minio-20260704/ /data/

# 恢复 Nacos 配置
curl -X POST "http://127.0.0.1:8848/v1/cs/configs" \
  -d "dataId=ydsz-pmis-common.yaml&group=dev&namespaceId=pmis&content=$(cat /backup/nacos-20260704.json)"
```

### 10.3 异地备份

推荐用 `rsync` 把 `/backup/pmis/` 同步到异地：

```bash
rsync -avz /backup/pmis/ backup-server:/data/pmis-backup/
```

---

## 11. 监控与告警

### 11.1 基础健康检查

```bash
# 7 个后端服务 + 4 个中间件
for url in \
  http://localhost:9000/actuator/health \
  http://localhost:9001/actuator/health \
  http://localhost:9002/actuator/health \
  http://localhost:9003/actuator/health \
  http://localhost:9004/actuator/health \
  http://localhost:9005/actuator/health \
  http://localhost:9006/actuator/health; do
  echo -n "$url: "
  curl -s -o /dev/null -w "%{http_code}\n" $url
done
```

### 11.2 Prometheus 指标

7 个服务都暴露了 `/actuator/prometheus`：

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'pmis-backend'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
        - 'localhost:9000'
        - 'localhost:9001'
        - 'localhost:9002'
        - 'localhost:9003'
        - 'localhost:9004'
        - 'localhost:9005'
        - 'localhost:9006'
```

### 11.3 日志聚合

**简单方案：ELK**

```bash
# 启动 ELK（外部）
docker run -d --name elasticsearch -p 9200:9200 -e "discovery.type=single-node" elasticsearch:8.15.0
docker run -d --name kibana -p 5601:5601 kibana:8.15.0
docker run -d --name logstash -p 5044:5044 logstash:8.15.0
```

后端日志已经用 `logback-spring.xml` 配置好 JSON 格式，可直接对接。

**极简方案：仅文件**

```bash
# 后端日志统一收集
tail -F /opt/ydsz-pmis/.run-logs/*.log | grep -i "error\|exception" | mail -s "PMIS Error Alert" admin@yourcompany.local
```

### 11.4 告警规则示例

```yaml
# alertmanager 告警规则
groups:
- name: pmis
  rules:
  - alert: PmisServiceDown
    expr: up{job="pmis-backend"} == 0
    for: 1m
    annotations:
      summary: "服务 {{ $labels.instance }} 已宕机"

  - alert: PmisJvmHighMemory
    expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.85
    for: 5m
    annotations:
      summary: "{{ $labels.instance }} 堆内存使用率 > 85%"

  - alert: PmisDbConnectionsHigh
    expr: hikaricp_connections_active / hikaricp_connections_max > 0.8
    for: 3m
    annotations:
      summary: "数据库连接池使用率 > 80%"
```

---

## 12. 常见问题 FAQ

### Q1: 启动后某个服务一直重启

**A**: 查看日志（`.run-logs/ydsz-pmis-{module}.log`），通常是：
- 依赖的中间件没起来（PG/Redis/Nacos）
- 端口被占用
- 共享配置 `ydsz-pmis-common.yaml` 没导入 Nacos

### Q2: 前端 401 未授权

**A**:
1. 检查 `application.yml` 中 `pmis.auth.captcha-required` 与前端是否一致
2. 检查 `JWT_SECRET` 是否配置（不配置会启动失败）
3. 浏览器清缓存后重试

### Q3: 文件上传失败

**A**:
1. MinIO 容器是否健康：`docker ps | grep minio`
2. MinIO 桶是否创建：访问 `http://127.0.0.1:9101`，用 `minioadmin/minioadmin` 登录看 `pmis` 桶
3. 检查 `minio-init` 容器日志：`docker logs pmis-minio-init`

### Q4: AI Agent 模块能关闭吗？

**A**: 可以。在 `deploy/.env` 设置 `LLM_PROVIDER=mock`，所有 LLM 调用走内置 mock，不会调外部 API。

要彻底不启动 agent 模块：注释掉 `start-all.sh` 中的 `ydsz-pmis-agent` 一行。

### Q5: 7 个服务太多，能合并吗？

**A**: 当前架构已经做了**模块合并**（批次 28），从 15 模块收敛到 7 部署单元。进一步合并需要重构代码，**不建议**。

如果硬件资源紧张，可以：
- 用 `nohup java -jar` 替代 `mvn spring-boot:run` 节省 JVM 启动开销
- 给 JVM 设置小堆（`-Xms128m -Xmx256m`）

### Q6: PostgreSQL 18 在内网没源怎么办？

**A**:
1. 用 Docker 镜像（`postgres:18-alpine`）—— 镜像里 PG 已经编译好
2. 离线安装：从 PostgreSQL 官网下载离线包（`postgresql-18.x.x-linux-x64.tar.gz`）

### Q7: 没有 Docker，能纯 Java 部署吗？

**A**: 可以，但需要本机装：
- PostgreSQL 18
- Redis 7
- Nacos 2.4（下载 release zip 解压启动）
- MinIO（下载二进制启动）

参考各组件官方文档。

### Q8: 怎么二次开发？

**A**: 见 [QUICKSTART.md](QUICKSTART.md#3-启动组件说明) 第 3 节。修改后端代码用 `mvn spring-boot:run`（devtools 自动重启）；修改前端代码 Vite 自动 HMR。

### Q9: Spring Boot 4 兼容性

**A**: 当前使用 **Spring Boot 4.0.7**（基于 Jakarta EE 10+），最低需要 **JDK 21**。不支持 Java 17 及以下。

### Q10: 部署在内网服务器上需要注意什么？

**A**:
1. **时间同步**：所有服务器用 NTP 同步（`ntpdate time1.aliyun.com`）
2. **时区设置**：所有容器和 JVM 统一 `Asia/Shanghai`
3. **字符集**：所有文件用 UTF-8 编码
4. **防火墙**：仅对外开放 80/443，其他端口仅内网互通
5. **SELinux**：CentOS 需关闭或配置 `permissive`

---

## 附录 A: 完整部署清单

部署前请逐项确认：

- [ ] 服务器硬件满足 [2.1](#21-硬件最低要求)
- [ ] 操作系统时间同步（`date` 命令查看）
- [ ] Docker / Docker Compose 已安装
- [ ] JDK 21 已安装（`java -version`）
- [ ] Maven 3.9+ 已安装
- [ ] Node 20+ 与 pnpm 已安装
- [ ] 端口 80/443/9000-9006/5432/6379/8848/9100 未被占用
- [ ] `.env` 文件已修改默认密码
- [ ] 数据库已初始化（`docs/V1.0.0.sql`）
- [ ] Nacos 共享配置已导入
- [ ] HTTPS 证书已配置
- [ ] Nginx 反向代理已配置
- [ ] 备份脚本已部署
- [ ] 监控告警已配置（可选）

---

## 附录 B: 联系与支持

- **项目仓库**：https://gitlab.njydsz.com/ydsz/oursource/ydsz-pmis
- **文档反馈**：在仓库提交 Issue
- **紧急支持**：PMIS 研发部内部群

---

> 文档版本：v1.0 · 2026-07-04 · 维护：PMIS 研发部
