# YDSZ PMIS · 中间件部署手册

> 8 大中间件的 Docker / Windows / Ubuntu 三种部署方式
> 适用：内网测试 / 准生产 / 生产环境
> 文档版本：v1.0 · 2026-07-04

---

## 目录

1. [总览](#1-总览)
2. [PostgreSQL 18](#2-postgresql-18)
3. [Redis 7](#3-redis-7)
4. [Nacos 2.4](#4-nacos-24)
5. [MinIO](#5-minio)
6. [Seata 2.5](#6-seata-25)
7. [RocketMQ 5.x](#7-rocketmq-5x)
8. [XXL-Job 2.4](#8-xxl-job-24)
9. [Elasticsearch 8.15](#9-elasticsearch-815)
10. [快速验证清单](#10-快速验证清单)
11. [常见问题 FAQ](#11-常见问题-faq)

---

## 1. 总览

### 1.1 中间件清单

| # | 中间件 | 版本 | 端口 | 必要性 | 用途 |
|---|---|---|---|---|---|
| 1 | PostgreSQL | 18 | 5432 | **必装** | 主数据库（126 张表） |
| 2 | Redis | 7 | 6379 | **必装** | 缓存 / 会话 / 分布式锁 |
| 3 | Nacos | 2.4 | 8848 / 9848 | **必装** | 服务注册 + 配置中心 |
| 4 | MinIO | latest | 9100 / 9101 | **必装** | 对象存储 |
| 5 | Seata | 2.5 | 8091 / 7091 | 推荐 | 分布式事务 |
| 6 | RocketMQ | 5.3 | 9876 / 10911 | 推荐 | 消息中间件 |
| 7 | XXL-Job | 2.4 | 9100 | 推荐 | 分布式任务调度 |
| 8 | Elasticsearch | 8.15 | 9200 | 可选 | 全文搜索 |

### 1.2 三种部署方式

| 方式 | 适用场景 | 优点 | 缺点 |
|---|---|---|---|
| **Docker Compose** | 开发 / 测试 / 快速验证 | 一行命令拉起，零依赖 | 性能损耗 2-5%，不适合生产 |
| **Ubuntu 原生** | 准生产 / 生产 | 性能最佳，运维工具齐全 | 需要一台 Linux 主机 |
| **Windows 原生** | Windows 内网 / 演示 | 与开发机一致 | 配置繁琐，Java 中间件要 NSSM |

### 1.3 部署方式选择

```bash
# 一、D键党（推荐开发用） ----------------------------------
cd ydsz-pmis
docker compose -f deploy/docker/docker-compose.dev.yml up -d
./deploy/scripts/import-nacos-config.sh
./deploy/scripts/start-all.sh

# 二、Ubuntu 一键党（推荐内网测试用）------------------------
sudo ./deploy/scripts/ubuntu/install-pmis-infra.sh
./deploy/scripts/ubuntu/infra-manager.sh status
./deploy/scripts/import-nacos-config.sh
./deploy/scripts/start-all.sh

# 三、Windows 一键党（Windows 内网服务器）-------------------
# 管理员 PowerShell
.\deploy\scripts\windows\install-pmis-infra.ps1
.\deploy\scripts\windows\infra-manager.ps1 status
.\deploy\scripts\import-nacos-config.bat
.\deploy\scripts\start-all.bat
```

### 1.4 端口总表

| 中间件 | HTTP / API | 管理 / Console | 内部通信 |
|---|---|---|---|
| PostgreSQL | **5432** | — | — |
| Redis | **6379** | — | — |
| Nacos | **8848** | 8848 (含) | 9848 (gRPC), 7848 (RPC) |
| MinIO | **9100** | 9101 (Console) | — |
| Seata | **8091** (TCP) | 7091 (HTTP Console) | — |
| RocketMQ NameServer | — | — | **9876** |
| RocketMQ Broker | — | 8080 (Proxy/Console) | 10911, 10909 |
| XXL-Job | **9100** | 同左 | — |
| Elasticsearch | **9200** | — | 9300 (transport) |

> ⚠️ **XXL-Job 与 MinIO API 都是 9100**。已约定：XXL-Job 占用 9100，MinIO API 改用 9100（容器内）/ 9100（原生）。**容器内通过 Docker 网络隔离，宿主机端口可相同**；原生部署需要调整其中之一。

---

## 2. PostgreSQL 18

### 2.1 元数据

| 项 | 值 |
|---|---|
| 官方下载 | https://www.postgresql.org/download/ |
| 镜像 | `postgres:18-alpine` |
| 默认端口 | 5432 |
| 配置文件 | `/etc/postgresql/18/main/postgresql.conf`（Ubuntu）<br>`C:\Program Files\PostgreSQL\18\data\postgresql.conf`（Windows） |
| 数据目录 | `/var/lib/postgresql/18/main/`（Ubuntu）<br>`C:\Program Files\PostgreSQL\18\data\`（Windows） |
| 默认账号 | `postgres` / 空密码（安装时设置） |
| 本项目账号 | `pmis` / `pmis123`（**生产必须改**） |
| 本项目数据库 | `ydsz_pmis` |
| 初始化脚本 | `docs/V1.0.0.sql`（126 张表 + 5 视图，504KB） |

### 2.2 方式 A：Docker Compose（推荐）

```bash
# 1. 启动（已包含在 docker-compose.dev.yml）
docker compose -f deploy/docker/docker-compose.dev.yml up -d postgres

# 2. 验证
docker ps | grep pmis-postgres
docker exec -it pmis-postgres pg_isready -U pmis -d ydsz_pmis
# 应输出: 127.0.0.1:5432 - accepting connections

# 3. 连接测试
docker exec -it pmis-postgres psql -U pmis -d ydsz_pmis
# 进入 psql 后: \dt   查看 126 张表
#                \q    退出

# 4. 数据持久化
docker volume inspect pmis-postgres-data
# 数据保留在 /var/lib/docker/volumes/pmis-postgres-data
```

**首次启动会自动**：
- 用 `POSTGRES_INITDB_ARGS="--encoding=UTF8 --locale=C"` 初始化
- 创建 `ydsz_pmis` 数据库
- 执行 `docs/V1.0.0.sql` 创建 126 张表

### 2.3 方式 B：Ubuntu 原生安装

#### 步骤 1：添加 PGDG 源

```bash
# 1. 确认 Ubuntu 版本
cat /etc/lsb-release
# Ubuntu 20.04 / 22.04 / 24.04

# 2. 添加 PostgreSQL 官方源（PGDG）
echo "deb http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" \
  | sudo tee /etc/apt/sources.list.d/pgdg.list

# 3. 导入 GPG 密钥
wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc \
  | sudo gpg --dearmor -o /etc/apt/trusted.gpg.d/pgdg.gpg

sudo apt-get update
```

#### 步骤 2：安装 PostgreSQL 18

```bash
sudo apt-get install -y postgresql-18 postgresql-client-18
```

#### 步骤 3：应用 PMIS 配置

```bash
# 1. 备份原配置
sudo cp /etc/postgresql/18/main/postgresql.conf /etc/postgresql/18/main/postgresql.conf.bak
sudo cp /etc/postgresql/18/main/pg_hba.conf /etc/postgresql/18/main/pg_hba.conf.bak

# 2. 复制 PMIS 配置
sudo cp deploy/infra/postgres/postgresql.conf /etc/postgresql/18/main/
sudo cp deploy/infra/postgres/pg_hba.conf /etc/postgresql/18/main/

# 3. 设置权限
sudo chown postgres:postgres /etc/postgresql/18/main/postgresql.conf
sudo chown postgres:postgres /etc/postgresql/18/main/pg_hba.conf
```

#### 步骤 4：重启

```bash
sudo systemctl restart postgresql
sudo systemctl enable postgresql

# 验证状态
sudo systemctl status postgresql
sudo -u postgres psql -c "SELECT version();"
# 应输出: PostgreSQL 18.x ...
```

#### 步骤 5：创建数据库与用户

```bash
sudo -u postgres psql <<'EOF'
CREATE USER pmis WITH PASSWORD 'pmis123';
CREATE DATABASE ydsz_pmis OWNER pmis ENCODING 'UTF8';
GRANT ALL PRIVILEGES ON DATABASE ydsz_pmis TO pmis;
EOF
```

#### 步骤 6：导入初始化 SQL

```bash
PGPASSWORD=pmis123 psql -h 127.0.0.1 -U pmis -d ydsz_pmis -f docs/V1.0.0.sql 2>&1 | tail -3
# 应输出: CREATE TABLE / CREATE INDEX / COMMENT 等

# 验证
PGPASSWORD=pmis123 psql -h 127.0.0.1 -U pmis -d ydsz_pmis -c "\dt" | wc -l
# 应输出 130 行左右（126 表 + 4 行表头/分隔）
```

### 2.4 方式 C：Windows 原生安装

#### 步骤 1：下载安装包

从 EDB 或 BigSQL 下载：
- **EDB 官方**：https://www.enterprisedb.com/download-postgresql-binaries
- 选择 **postgresql-18.x-x-windows-x64.exe** 或 zip 包

#### 步骤 2：安装（图形向导）

1. 双击 `postgresql-18.x-x-windows-x64.exe`
2. 安装目录：`C:\Program Files\PostgreSQL\18\`
3. 数据目录：`C:\Program Files\PostgreSQL\18\data\`
4. 端口：5432
5. 设置 postgres 密码（**记住**）
6. Locale：留默认（`English_United States.1252`）
7. 完成安装（自动注册 `postgresql-x64-18` 服务并启动）

#### 步骤 3：应用 PMIS 配置

```powershell
# 管理员 PowerShell
$pgData = "C:\Program Files\PostgreSQL\18\data"

# 备份
Copy-Item "$pgData\postgresql.conf" "$pgData\postgresql.conf.bak" -Force
Copy-Item "$pgData\pg_hba.conf" "$pgData\pg_hba.conf.bak" -Force

# 应用 PMIS 配置（注意 Windows 路径用 \\ 或字符串拼接）
# postgresql.conf 是 Linux 风格，复制后需手动调整 listen_addresses
Copy-Item deploy\infra\postgres\postgresql.conf "$pgData\postgresql.conf" -Force
Copy-Item deploy\infra\postgres\pg_hba.conf "$pgData\pg_hba.conf" -Force
```

> ⚠️ Windows 版的 `postgresql.conf` 格式略不同（路径用单引号即可），但我们的模板已兼容。

#### 步骤 4：重启服务

```powershell
Restart-Service postgresql-x64-18

# 验证
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -c "SELECT version();"
```

#### 步骤 5：创建数据库与导入 SQL

```powershell
# 创建用户
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -c "CREATE USER pmis WITH PASSWORD 'pmis123';"
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -c "CREATE DATABASE ydsz_pmis OWNER pmis ENCODING 'UTF8';"
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE ydsz_pmis TO pmis;"

# 导入 SQL
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h 127.0.0.1 -U pmis -d ydsz_pmis -f "docs\V1.0.0.sql" 2>&1 | Select-Object -Last 5

# 设置信任连接（开发用）
$env:PGPASSWORD = 'pmis123'
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h 127.0.0.1 -U pmis -d ydsz_pmis -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='public';"
# 应输出 126
```

### 2.5 验证

```bash
# 1. 端口检查
nc -zv 127.0.0.1 5432     # Linux/Mac
Test-NetConnection 127.0.0.1 -Port 5432   # Windows PowerShell

# 2. 服务状态
sudo systemctl status postgresql   # Linux
Get-Service postgresql-x64-18      # Windows

# 3. 表数量
PGPASSWORD=pmis123 psql -h 127.0.0.1 -U pmis -d ydsz_pmis -c \
  "SELECT count(*) AS table_count FROM information_schema.tables WHERE table_schema='public';"
# 应输出 126
```

### 2.6 故障排查

| 现象 | 原因 | 解决 |
|---|---|---|
| 启动报 `locale not found` | 系统 locale 缺失 | `sudo locale-gen en_US.UTF-8` 或在 postgresql.conf 中设置 `lc_messages = 'C'` |
| `pg_isready` 一直 false | 端口冲突或数据目录权限 | `ls -la /var/lib/postgresql/18/main/` 检查属主 |
| 中文表注释乱码 | 数据库字符集不对 | `\l` 查看编码，必须 UTF8 |
| 远程连接被拒 | pg_hba.conf 没开 | 改 `host all all 0.0.0.0/0 md5`（生产慎用） |
| 导入 SQL 报 `relation already exists` | 非首次导入 | 脚本已用 `CREATE TABLE IF NOT EXISTS`，忽略该警告即可 |
| Windows 启动报 `data directory has wrong ownership` | 用管理员账户启动服务 | 用 `NT AUTHORITY\NetworkService` 账户启动 |

---

## 3. Redis 7

### 3.1 元数据

| 项 | 值 |
|---|---|
| 官方下载 | https://redis.io/download/ |
| Ubuntu 包 | `redis-server`（apt 装的是 6.x，需手动装 7.x） |
| 镜像 | `redis:7-alpine` |
| 默认端口 | 6379 |
| 配置文件 | `/etc/redis/redis.conf`（Ubuntu）<br>`C:\redis\redis.conf`（Windows） |
| 本项目密码 | `pmis123`（生产必须改） |

### 3.2 方式 A：Docker Compose

```bash
docker compose -f deploy/docker/docker-compose.dev.yml up -d redis

# 验证
docker exec -it pmis-redis redis-cli -a pmis123 ping
# 应输出: PONG
```

### 3.3 方式 B：Ubuntu 原生

```bash
# 1. 安装（Ubuntu 22.04+ 自带 redis-server 7.x）
sudo apt-get install -y redis-server

# 2. 备份并应用配置
sudo cp /etc/redis/redis.conf /etc/redis/redis.conf.bak
sudo cp deploy/infra/redis/redis.conf /etc/redis/redis.conf
sudo chown redis:redis /etc/redis/redis.conf

# 3. 重启
sudo systemctl restart redis-server
sudo systemctl enable redis-server

# 4. 验证
redis-cli -a pmis123 ping
# PONG
```

> **老版本 Ubuntu（20.04）装 6.x**：参考 [Redis 官网](https://redis.io/docs/getting-started/installation/install-redis-on-linux/) 添加 Redis 源

### 3.4 方式 C：Windows 原生

> Windows 官方不支持 Redis。推荐两种方式：
> 1. **预编译包**：https://github.com/tporadowski/redis/releases 下载 zip
> 2. **WSL**：在 WSL 中按 Ubuntu 方式装

**预编译包方式**：

```powershell
# 1. 下载并解压
Invoke-WebRequest -Uri "https://github.com/tporadowski/redis/releases/download/v5.0.10/Redis-x64-5.0.10.zip" `
  -OutFile "$env:TEMP\redis.zip"
Expand-Archive "$env:TEMP\redis.zip" -DestinationPath "C:\redis"

# 2. 应用配置
Copy-Item deploy\infra\redis\redis.conf "C:\redis\redis.conf" -Force

# 3. 用 NSSM 注册服务
nssm install Redis "C:\redis\redis-server.exe" "C:\redis\redis.conf"
nssm set Redis AppDirectory "C:\redis"
nssm set Redis DisplayName "Redis"
nssm set Redis Start SERVICE_AUTO_START
Start-Service Redis

# 4. 验证
& "C:\redis\redis-cli.exe" -a pmis123 ping
# PONG
```

### 3.5 验证

```bash
# 1. 端口
nc -zv 127.0.0.1 6379

# 2. 密码
redis-cli -a pmis123 ping

# 3. 内存与配置
redis-cli -a pmis123 info server | grep -E "redis_version|os|process_id"
redis-cli -a pmis123 config get maxmemory
```

### 3.6 故障排查

| 现象 | 原因 | 解决 |
|---|---|---|
| `NOAUTH Authentication required` | 密码未配置 | `redis-cli -a <password> ping` 加密码 |
| `maxmemory limit reached` | 内存满 | 调整 `maxmemory` 或 `maxmemory-policy` |
| Windows 启动报 `WSARecv failed` | Redis 在 Windows 兼容性差 | 换 Memurai 或用 WSL |

---

