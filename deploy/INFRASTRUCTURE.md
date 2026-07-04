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
./deploy/ubuntu/scripts/import-nacos-config.sh
./deploy/ubuntu/scripts/start-all.sh

# 二、Ubuntu 一键党（推荐内网测试用）------------------------
sudo ./deploy/ubuntu/install-pmis-infra.sh
./deploy/ubuntu/infra-manager.sh status
./deploy/ubuntu/scripts/import-nacos-config.sh
./deploy/ubuntu/scripts/start-all.sh

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
sudo cp deploy/common/conf/postgres/postgresql.conf /etc/postgresql/18/main/
sudo cp deploy/common/conf/postgres/pg_hba.conf /etc/postgresql/18/main/

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
sudo cp deploy/common/conf/redis/redis.conf /etc/redis/redis.conf
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

## 4. Nacos 2.4

### 4.1 元数据

| 项 | 值 |
|---|---|
| 官方下载 | https://github.com/alibaba/nacos/releases |
| 镜像 | `nacos/nacos-server:v2.4.3` |
| 端口 | **8848** (HTTP) / **9848** (gRPC) / **7848** (RPC) |
| 默认账号 | `nacos` / `nacos`（**生产必须改**） |
| 配置文件 | `conf/application.properties` |
| 启动模式 | standalone（单机） / cluster（集群） |
| 持久化 | 默认 derby（内嵌），生产推荐 MySQL |

### 4.2 方式 A：Docker Compose

```bash
docker compose -f deploy/docker/docker-compose.dev.yml up -d nacos

# 验证
curl http://127.0.0.1:8848/nacos/actuator/health
# {"status":"UP"}
```

**访问控制台**：http://127.0.0.1:8848/nacos （nacos / nacos）

### 4.3 方式 B：Ubuntu 原生

#### 步骤 1：安装 JDK 21

```bash
sudo apt-get install -y openjdk-21-jdk
java -version
# openjdk version "21.0.x"
```

#### 步骤 2：下载 Nacos

```bash
cd /tmp
wget https://github.com/alibaba/nacos/releases/download/2.4.3/nacos-server-2.4.3.tar.gz
sudo tar -xzf nacos-server-2.4.3.tar.gz -C /opt
sudo mv /opt/nacos /opt/nacos
sudo useradd -r -s /bin/bash pmis   # 已存在则忽略
sudo chown -R pmis:pmis /opt/nacos
```

#### 步骤 3：应用配置

```bash
# 备份
sudo cp /opt/nacos/conf/application.properties /opt/nacos/conf/application.properties.bak

# 复制 PMIS 配置
sudo cp deploy/common/conf/nacos/application.properties /opt/nacos/conf/

# 创建数据/日志目录
sudo mkdir -p /opt/nacos/data /var/log/pmis/nacos
sudo chown -R pmis:pmis /opt/nacos/data /var/log/pmis/nacos

# 修改数据目录（已在模板里改）
sudo sed -i 's|^nacos.home=.*|nacos.home=/opt/nacos/data|' /opt/nacos/conf/application.properties
```

#### 步骤 4：注册 systemd 服务

创建 `/etc/systemd/system/nacos.service`：

```ini
[Unit]
Description=Nacos Server
After=network.target

[Service]
Type=forking
User=pmis
Group=pmis
Environment=JAVA_HOME=/opt/jdk-21   # 或 $(dirname $(dirname $(readlink -f $(which java))))
ExecStart=/opt/nacos/bin/startup.sh -m standalone
ExecStop=/opt/nacos/bin/shutdown.sh
Restart=on-failure
RestartSec=10
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable nacos
sudo systemctl start nacos

# 等待启动
for i in {1..30}; do
  if curl -sf http://127.0.0.1:8848/nacos/actuator/health >/dev/null 2>&1; then
    echo "Nacos 已就绪"; break
  fi
  sleep 2
done
```

#### 步骤 5：MySQL 持久化（生产推荐）

```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE nacos DEFAULT CHARACTER SET utf8mb4;"
mysql -u root -p -e "CREATE USER 'nacos'@'%' IDENTIFIED BY 'nacos123';"
mysql -u root -p -e "GRANT ALL ON nacos.* TO 'nacos'@'%';"

# 2. 导入 schema
mysql -u nacos -p nacos < /opt/nacos/conf/mysql-schema.sql

# 3. 修改 application.properties
sudo vim /opt/nacos/conf/application.properties
```

将 derby 段：
```properties
spring.datasource.platform=derby
```

改为 mysql 段：
```properties
spring.datasource.platform=mysql
db.num=1
db.url.0=jdbc:mysql://127.0.0.1:3306/nacos?characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=Asia/Shanghai
db.user.0=nacos
db.password.0=nacos123
```

```bash
sudo systemctl restart nacos
```

### 4.4 方式 C：Windows 原生

#### 步骤 1：下载解压

```powershell
Invoke-WebRequest -Uri "https://github.com/alibaba/nacos/releases/download/2.4.3/nacos-server-2.4.3.zip" `
  -OutFile "$env:TEMP\nacos.zip"
Expand-Archive "$env:TEMP\nacos.zip" -DestinationPath "C:\pmis"
Rename-Item "C:\pmis\nacos" "C:\pmis\nacos"
```

#### 步骤 2：应用配置

```powershell
Copy-Item "deploy\infra\nacos\application.properties" "C:\pmis\nacos\conf\application.properties" -Force
# 修改 nacos.home
(Get-Content "C:\pmis\nacos\conf\application.properties") `
  -replace '^nacos\.home=.*', 'nacos.home=C:\\pmis\\nacos\\data' |
  Set-Content "C:\pmis\nacos\conf\application.properties"
```

#### 步骤 3：NSSM 注册服务

```powershell
# 前提：已装 NSSM（见 PostgreSQL 章节）
nssm install nacos "C:\pmis\nacos\bin\startup.cmd" "-m standalone"
nssm set nacos AppDirectory "C:\pmis\nacos\bin"
nssm set nacos DisplayName "Nacos Server"
nssm set nacos Start SERVICE_AUTO_START
nssm set nacos AppEnvironmentExtra "JAVA_HOME=$env:JAVA_HOME"
Start-Service nacos

# 验证
Start-Sleep -Seconds 30
Invoke-RestMethod http://127.0.0.1:8848/nacos/actuator/health
```

### 4.5 验证

```bash
# 1. 端口
nc -zv 127.0.0.1 8848

# 2. 健康
curl http://127.0.0.1:8848/nacos/actuator/health

# 3. 登录
# 浏览器: http://127.0.0.1:8848/nacos (nacos / nacos)
```

### 4.6 故障排查

| 现象 | 原因 | 解决 |
|---|---|---|
| 启动报 `JAVA_HOME is not set` | 环境变量缺失 | `export JAVA_HOME=/opt/jdk-21` 或在 systemd 配置 |
| `9848 port is in use` | gRPC 端口冲突 | 改 `nacos.remote.client.grpc.port` |
| 登录页空白 | 静态资源加载失败 | 检查 `nacos.home` 配置 |
| 服务发现不到注册的服务 | gRPC 端口没开放 | 防火墙放开 9848 |
| 集群模式脑裂 | 节点数 < 3 | 至少 3 节点，或用 standalone |

---

## 5. MinIO

### 5.1 元数据

| 项 | 值 |
|---|---|
| 官方下载 | https://min.io/download |
| 镜像 | `minio/minio:latest` |
| API 端口 | **9100**（容器内）/ 默认 9000（原生） |
| Console 端口 | **9101** |
| 默认账号 | `minioadmin` / `minioadmin`（**生产必须改**） |
| 数据目录 | `/var/lib/pmis/minio`（原生）<br>挂载卷（容器） |
| PMIS 桶 | `pmis` |

### 5.2 方式 A：Docker Compose

```bash
docker compose -f deploy/docker/docker-compose.dev.yml up -d minio minio-init

# 验证桶
docker logs pmis-minio-init
# 应包含 "MinIO bucket [pmis] initialized"
```

**访问控制台**：http://127.0.0.1:9101 （minioadmin / minioadmin）

### 5.3 方式 B：Ubuntu 原生

#### 步骤 1：下载二进制

```bash
wget https://dl.min.io/server/minio/release/linux-amd64/minio
sudo install -m 755 minio /usr/local/bin/
rm minio
```

#### 步骤 2：创建数据目录

```bash
sudo mkdir -p /var/lib/pmis/minio/data
sudo chown -R pmis:pmis /var/lib/pmis/minio
```

#### 步骤 3：注册 systemd 服务

创建 `/etc/systemd/system/minio.service`：

```ini
[Unit]
Description=MinIO Object Storage
After=network.target

[Service]
Type=simple
User=pmis
Group=pmis
Environment="MINIO_ROOT_USER=minioadmin"
Environment="MINIO_ROOT_PASSWORD=minioadmin"
ExecStart=/usr/local/bin/minio server /var/lib/pmis/minio/data --console-address ":9001"
Restart=on-failure
RestartSec=10
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
```

> ⚠️ MinIO API 默认端口是 9000。如需改 9100，添加 `--address ":9100"` 参数。

```bash
sudo systemctl daemon-reload
sudo systemctl enable minio
sudo systemctl start minio
```

#### 步骤 4：创建 pmis 桶

```bash
# 下载 mc 客户端
wget https://dl.min.io/client/mc/release/linux-amd64/mc
sudo install -m 755 mc /usr/local/bin/

# 配置
mc alias set local http://127.0.0.1:9100 minioadmin minioadmin
# 创建桶
mc mb --ignore-existing local/pmis
# 设访问权限
mc anonymous set download local/pmis
```

### 5.4 方式 C：Windows 原生

```powershell
# 1. 下载
Invoke-WebRequest -Uri "https://dl.min.io/server/minio/release/windows-amd64/minio.exe" `
  -OutFile "C:\pmis\minio\minio.exe"
New-Item -ItemType Directory -Force -Path "C:\pmis-data\minio" | Out-Null

# 2. NSSM 注册
nssm install minio "C:\pmis\minio\minio.exe" "server C:\pmis-data\minio --console-address '":9001'""
nssm set minio AppDirectory "C:\pmis\minio"
nssm set minio DisplayName "MinIO Object Storage"
nssm set minio Start SERVICE_AUTO_START
nssm set minio AppEnvironmentExtra "MINIO_ROOT_USER=minioadmin`nMINIO_ROOT_PASSWORD=minioadmin"
Start-Service minio

# 3. 验证
Start-Sleep -Seconds 5
Invoke-RestMethod http://127.0.0.1:9100/minio/health/live
```

### 5.5 验证

```bash
# 1. 健康
curl http://127.0.0.1:9100/minio/health/live

# 2. 桶列表
mc ls local/
# 应输出 pmis

# 3. 测试上传
mc cp /etc/hosts local/pmis/test.txt
mc cat local/pmis/test.txt
```

### 5.6 故障排查

| 现象 | 原因 | 解决 |
|---|---|---|
| `minio: permission denied` | 数据目录权限 | `chown -R pmis:pmis /var/lib/pmis/minio` |
| 上传 401 | 密钥错误 | 检查 MINIO_ROOT_USER/PASSWORD |
| Console 打不开 | 9001 端口未开放 | 加 `--console-address ":9001"` |

---

## 6. Seata 2.5

### 6.1 元数据

| 项 | 值 |
|---|---|
| 官方下载 | https://github.com/apache/incubator-seata/releases |
| 镜像 | `apache/seata-server:2.5.0` |
| TCP 端口 | **8091** |
| HTTP Console | **7091**（2.x 才有） |
| 默认账号 | `admin` / `admin`（**生产必须改**） |
| 配置文件 | `conf/application.yml`、`conf/file.conf`、`conf/registry.conf` |
| 存储模式 | file（默认） / db / redis |
| 集群模式 | 默认单机（生产用 Nacos 注册中心） |

### 6.2 方式 A：Docker Compose

```bash
docker compose -f deploy/docker/docker-compose.dev.yml up -d seata

# 验证
nc -zv 127.0.0.1 8091
curl http://127.0.0.1:7091/api/v1/auth/login -X POST -H "Content-Type: application/json" -d '{"username":"admin","password":"admin"}'
```

**访问控制台**：http://127.0.0.1:7091 （admin / admin）

### 6.3 方式 B：Ubuntu 原生

#### 步骤 1：下载

```bash
cd /tmp
wget https://github.com/apache/incubator-seata/releases/download/v2.5.0/apache-seata-2.5.0-incubating-bin.tar.gz
sudo tar -xzf apache-seata-2.5.0-incubating-bin.tar.gz -C /opt
sudo mv /opt/apache-seata-2.5.0-incubating /opt/seata
sudo chown -R pmis:pmis /opt/seata
```

#### 步骤 2：应用配置

```bash
cd /opt/seata/conf
for f in application.yml file.conf registry.conf; do
  cp $f $f.bak
done
cp deploy/common/conf/seata/application.yml .
cp deploy/common/conf/seata/file.conf .
cp deploy/common/conf/seata/registry.conf .
```

#### 步骤 3：注册服务

```bash
sudo tee /etc/systemd/system/seata.service > /dev/null <<EOF
[Unit]
Description=Seata Distributed Transaction Server
After=network.target

[Service]
Type=simple
User=pmis
Group=pmis
Environment=JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
ExecStart=/opt/seata/bin/seata-server.sh
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable seata
sudo systemctl start seata
```

#### 步骤 4：DB 持久化（生产推荐）

```bash
# 1. 创建 seata 数据库
sudo -u postgres psql -c "CREATE DATABASE seata OWNER pmis ENCODING 'UTF8';"

# 2. 初始化 schema（seata 官方提供）
psql -h 127.0.0.1 -U pmis -d seata -f /opt/seata/script/server/db/postgresql.sql

# 3. 修改 file.conf 切换为 db 模式
sed -i 's|mode = "file"|mode = "db"|' /opt/seata/conf/file.conf

# 4. 重启
sudo systemctl restart seata
```

### 6.4 方式 C：Windows 原生

```powershell
# 1. 下载
Invoke-WebRequest -Uri "https://github.com/apache/incubator-seata/releases/download/v2.5.0/apache-seata-2.5.0-incubating-bin.zip" `
  -OutFile "$env:TEMP\seata.zip"
Expand-Archive "$env:TEMP\seata.zip" -DestinationPath "C:\pmis"
Rename-Item "C:\pmis\apache-seata-2.5.0-incubating" "C:\pmis\seata"

# 2. 配置
Copy-Item "deploy\infra\seata\application.yml" "C:\pmis\seata\conf\application.yml" -Force
Copy-Item "deploy\infra\seata\file.conf" "C:\pmis\seata\conf\file.conf" -Force
Copy-Item "deploy\infra\seata\registry.conf" "C:\pmis\seata\conf\registry.conf" -Force

# 3. NSSM
nssm install seata "C:\pmis\seata\bin\seata-server.bat"
nssm set seata AppDirectory "C:\pmis\seata\bin"
nssm set seata DisplayName "Seata Server"
nssm set seata Start SERVICE_AUTO_START
Start-Service seata
```

### 6.5 Nacos 注册中心集成（生产推荐）

修改 `application.yml`：

```yaml
seata:
  registry:
    type: nacos
    nacos:
      application: seata-server
      server-addr: 127.0.0.1:8848
      group: SEATA_GROUP
      namespace: pmis
      username: nacos
      password: nacos
  config:
    type: nacos
    nacos:
      server-addr: 127.0.0.1:8848
      namespace: pmis
      group: SEATA_GROUP
      username: nacos
      password: nacos
      data-id: seataServer.properties
```

在 Nacos 控制台 `pmis` 命名空间新建配置 `seataServer.properties`（group=SEATA_GROUP），内容参考 [seata 官方配置](https://seata.io/zh-cn/docs/user/configuration.html)。

### 6.6 验证

```bash
# 1. TCP 端口
nc -zv 127.0.0.1 8091

# 2. HTTP Console
curl -sf http://127.0.0.1:7091/ -I

# 3. 登录测试
curl -X POST http://127.0.0.1:7091/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'
```

### 6.7 故障排查

| 现象 | 原因 | 解决 |
|---|---|---|
| `Address already in use: 8091` | 端口冲突 | 改 `seata.server.port` |
| 启动慢 | derby 元数据大 | 切换为 db 模式 |
| Console 登录失败 | 密码不对 | 修改 `console.user.password` |
| 客户端连不上 | 客户端版本不一致 | 用同一版本（2.5.0） |

---

## 7. RocketMQ 5.x

### 7.1 元数据

| 项 | 值 |
|---|---|
| 官方下载 | https://rocketmq.apache.org/download/ |
| 镜像 | `apache/rocketmq:5.3.2` |
| NameServer 端口 | **9876** |
| Broker 端口 | **10911**（业务）/ 10909（vip） |
| Proxy 端口 | 8080 |
| Console | `apacherocketmq/rocketmq-console-ng:2.0.1`（http://localhost:8080） |
| 配置文件 | `conf/broker.conf` |

### 7.2 方式 A：Docker Compose（已含 NameServer + Broker + Console）

```bash
docker compose -f deploy/docker/docker-compose.dev.yml up -d rocketmq-namesrv rocketmq-broker rocketmq-console

# 验证
nc -zv 127.0.0.1 9876
nc -zv 127.0.0.1 10911

# 访问 Console
# http://127.0.0.1:8080
```

### 7.3 方式 B：Ubuntu 原生

#### 步骤 1：安装 JDK 21（如已装跳过）

```bash
sudo apt-get install -y openjdk-21-jdk
```

#### 步骤 2：下载 RocketMQ

```bash
cd /tmp
wget https://dist.apache.org/repos/dist/release/rocketmq/5.3.2/rocketmq-all-5.3.2-bin-release.zip
sudo apt-get install -y unzip
unzip rocketmq-all-5.3.2-bin-release.zip
sudo mv rocketmq-all-5.3.2-bin-release /opt/rocketmq
sudo chown -R pmis:pmis /opt/rocketmq
```

#### 步骤 3：应用 broker.conf

```bash
sudo cp deploy/common/conf/rocketmq/broker.conf /opt/rocketmq/conf/
sudo mkdir -p /opt/rocketmq/store
sudo chown -R pmis:pmis /opt/rocketmq/store
```

#### 步骤 4：注册 NameServer 服务

```bash
sudo tee /etc/systemd/system/rocketmq-namesrv.service > /dev/null <<EOF
[Unit]
Description=RocketMQ NameServer
After=network.target

[Service]
Type=simple
User=pmis
Group=pmis
Environment=JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
Environment=JAVA_OPT="-Xms512m -Xmx512m"
ExecStart=/opt/rocketmq/bin/mqnamesrv
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
```

#### 步骤 5：注册 Broker 服务

```bash
sudo tee /etc/systemd/system/rocketmq-broker.service > /dev/null <<EOF
[Unit]
Description=RocketMQ Broker
After=rocketmq-namesrv.service

[Service]
Type=simple
User=pmis
Group=pmis
Environment=JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
Environment=JAVA_OPT="-Xms512m -Xmx512m"
ExecStart=/opt/rocketmq/bin/mqbroker -n 127.0.0.1:9876 -c /opt/rocketmq/conf/broker.conf
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable rocketmq-namesrv rocketmq-broker
sudo systemctl start rocketmq-namesrv
sleep 5
sudo systemctl start rocketmq-broker
```

#### 步骤 6：安装 Console（可选）

```bash
docker run -d --name rocketmq-console \
  -p 8080:8080 \
  -e "JAVA_OPTS=-Drocketmq.namesrv.addr=127.0.0.1:9876" \
  apacherocketmq/rocketmq-console-ng:2.0.1

# 访问
# http://127.0.0.1:8080
```

### 7.4 方式 C：Windows 原生

```powershell
# 1. 下载解压
Invoke-WebRequest -Uri "https://dist.apache.org/repos/dist/release/rocketmq/5.3.2/rocketmq-all-5.3.2-bin-release.zip" `
  -OutFile "$env:TEMP\rocketmq.zip"
Expand-Archive "$env:TEMP\rocketmq.zip" -DestinationPath "C:\pmis"
Rename-Item "C:\pmis\rocketmq-all-5.3.2-bin-release" "C:\pmis\rocketmq"

# 2. 配置
Copy-Item "deploy\infra\rocketmq\broker.conf" "C:\pmis\rocketmq\conf\broker.conf" -Force
New-Item -ItemType Directory -Force -Path "C:\pmis-data\rocketmq" | Out-Null

# 3. NSSM 注册 NameServer
$rmqHome = "C:\pmis\rocketmq"
nssm install rocketmq-namesrv "$env:JAVA_HOME\bin\java.exe" "-jar `"$rmqHome\lib\rocketmq-namesrv-5.3.2.jar`""
nssm set rocketmq-namesrv AppDirectory $rmqHome
nssm set rocketmq-namesrv DisplayName "RocketMQ NameServer"
nssm set rocketmq-namesrv Start SERVICE_AUTO_START

# 4. NSSM 注册 Broker
nssm install rocketmq-broker "$env:JAVA_HOME\bin\java.exe" "-jar `"$rmqHome\lib\rocketmq-broker-5.3.2.jar`" -c `"$rmqHome\conf\broker.conf`""
nssm set rocketmq-broker AppDirectory $rmqHome
nssm set rocketmq-broker DisplayName "RocketMQ Broker"
nssm set rocketmq-broker Start SERVICE_AUTO_START

Start-Service rocketmq-namesrv
Start-Sleep -Seconds 5
Start-Service rocketmq-broker
```

### 7.5 验证

```bash
# 1. 端口
nc -zv 127.0.0.1 9876
nc -zv 127.0.0.1 10911

# 2. 集群信息
cd /opt/rocketmq
./bin/mqadmin clusterList -n 127.0.0.1:9876
# 应输出 broker-a 的信息

# 3. 消息发送测试
export NAMESRV_ADDR=127.0.0.1:9876
./bin/tools.sh org.apache.rocketmq.example.quickstart.Producer
# 应显示 SendResult [queueId=...]
./bin/tools.sh org.apache.rocketmq.example.quickstart.Consumer
```

### 7.6 故障排查

| 现象 | 原因 | 解决 |
|---|---|---|
| `RemotingTooMuchRequestException` | 发送过快 | 调大 `broker.brokerFastFailureEnable` |
| Broker 启动报 `Cannot allocate memory` | JVM 堆设置过大 | 减小 `JAVA_OPT=-Xms -Xmx` |
| `No route info of this topic` | Topic 不存在 | 启用 `autoCreateTopicEnable=true`（生产慎用） |
| Console 连接超时 | 防火墙 | 开放 9876 和 10911 |

---

## 8. XXL-Job 2.4

### 8.1 元数据

| 项 | 值 |
|---|---|
| 官方下载 | https://github.com/xuxueli/xxl-job/releases |
| 镜像 | `xuxueli/xxl-job-admin:2.4.2` |
| 端口 | **9100**（自定义，与 Spring Boot 配置一致） |
| 默认账号 | `admin` / `123456`（**生产必须改**） |
| 数据库 | 共用 PostgreSQL `ydsz_pmis` 库 |
| 配置文件 | `application.properties` |

### 8.2 方式 A：Docker Compose

```bash
docker compose -f deploy/docker/docker-compose.dev.yml up -d xxl-job

# 验证（启动较慢，等 60s）
for i in {1..30}; do
  if curl -sf http://127.0.0.1:9100/xxl-job-admin/actuator/health >/dev/null 2>&1; then
    echo "XXL-Job 已就绪"; break
  fi
  sleep 2
done
```

> ⚠️ Docker 镜像首次启动需要先建表。已通过 `xxl-job-admin-2.4.2.jar` 自带的 SQL 初始化（默认连 PostgreSQL 时自动建表，但实际需要手动执行）

手动初始化：
```bash
docker exec -i pmis-postgres psql -U pmis -d ydsz_pmis \
  < deploy/common/sql/tables_xxl_job_pg.sql
```

**访问控制台**：http://127.0.0.1:9100/xxl-job-admin （admin / 123456）

### 8.3 方式 B：Ubuntu 原生

#### 步骤 1：初始化数据库

```bash
PGPASSWORD=pmis123 psql -h 127.0.0.1 -U pmis -d ydsz_pmis \
  -f deploy/common/sql/tables_xxl_job_pg.sql
```

#### 步骤 2：下载 jar

```bash
wget https://github.com/xuxueli/xxl-job/releases/download/2.4.2/xxl-job-admin-2.4.2.jar
sudo mkdir -p /opt/xxl-job
sudo cp xxl-job-admin-2.4.2.jar /opt/xxl-job/
sudo cp deploy/common/conf/xxl-job/application.properties /opt/xxl-job/
sudo chown -R pmis:pmis /opt/xxl-job
```

#### 步骤 3：注册服务

```bash
sudo tee /etc/systemd/system/xxl-job.service > /dev/null <<EOF
[Unit]
Description=XXL-Job Admin
After=network.target postgresql.service

[Service]
Type=simple
User=pmis
Group=pmis
Environment=JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
WorkingDirectory=/opt/xxl-job
ExecStart=$JAVA_HOME/bin/java -Xms512m -Xmx512m -jar /opt/xxl-job/xxl-job-admin-2.4.2.jar --spring.config.location=/opt/xxl-job/application.properties
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable xxl-job
sudo systemctl start xxl-job
```

### 8.4 方式 C：Windows 原生

```powershell
# 1. 下载
Invoke-WebRequest -Uri "https://github.com/xuxueli/xxl-job/releases/download/2.4.2/xxl-job-admin-2.4.2.jar" `
  -OutFile "C:\pmis\xxl-job\xxl-job-admin-2.4.2.jar"
Copy-Item "deploy\infra\xxl-job\application.properties" "C:\pmis\xxl-job\application.properties" -Force

# 2. 初始化数据库
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h 127.0.0.1 -U pmis -d ydsz_pmis `
  -f "deploy\infra\xxl-job\tables_xxl_job_pg.sql"

# 3. NSSM
nssm install xxl-job "$env:JAVA_HOME\bin\java.exe" `
  "-Xms512m -Xmx512m -jar C:\pmis\xxl-job\xxl-job-admin-2.4.2.jar --spring.config.location=C:\pmis\xxl-job\application.properties"
nssm set xxl-job AppDirectory "C:\pmis\xxl-job"
nssm set xxl-job DisplayName "XXL-Job Admin"
nssm set xxl-job Start SERVICE_AUTO_START
Start-Service xxl-job
```

### 8.5 PMIS 服务端注册 Executor

在 Nacos 共享配置 `ydsz-pmis-common.yaml` 中已有默认配置（`cronjob` 模块）：

```yaml
xxl:
  job:
    admin:
      addresses: http://127.0.0.1:9100/xxl-job-admin
      username: admin
      password: 123456
    executor:
      appname: pmis-executor
      port: 9999
      logpath: ./logs/xxl-job
      logretentiondays: 30
      accessToken: default-token
```

启动 `ydsz-pmis-cronjob` 模块后会自动注册到 Admin。

### 8.6 验证

```bash
# 1. 健康
curl http://127.0.0.1:9100/xxl-job-admin/actuator/health

# 2. 登录
# 浏览器: http://127.0.0.1:9100/xxl-job-admin (admin / 123456)

# 3. 查看执行器
# 登录后: 执行器管理 → 应能看到 pmis-executor（启动 cronjob 模块后）
```

### 8.7 故障排查

| 现象 | 原因 | 解决 |
|---|---|---|
| `Communications link failure` | 数据库连接失败 | 检查 `spring.datasource.url` |
| 注册不上 Executor | accessToken 不一致 | 服务端与 PMIS 配置保持一致 |
| 启动慢 | 第一次加载 Spring | 等待 60-90s |

---

## 9. Elasticsearch 8.15

### 9.1 元数据

| 项 | 值 |
|---|---|
| 官方下载 | https://www.elastic.co/downloads/elasticsearch |
| 镜像 | `docker.elastic.co/elasticsearch/elasticsearch:8.15.3` |
| 端口 | **9200**（HTTP）/ 9300（transport） |
| 默认账号 | 无（开发用 `xpack.security.enabled=false`，生产必须开） |
| 配置文件 | `config/elasticsearch.yml` |
| JVM 堆 | `/etc/elasticsearch/jvm.options.d/heap.options`（Ubuntu）<br>`C:\elasticsearch\config\jvm.options.d\heap.options`（Windows） |
| 重要约束 | 不能以 root 启动；`vm.max_map_count ≥ 262144` |

### 9.2 方式 A：Docker Compose

```bash
docker compose -f deploy/docker/docker-compose.dev.yml up -d elasticsearch

# 验证（首次启动较慢）
for i in {1..30}; do
  if curl -sf http://127.0.0.1:9200/_cluster/health >/dev/null 2>&1; then
    echo "ES 已就绪"; break
  fi
  sleep 3
done

curl -s http://127.0.0.1:9200/_cluster/health?pretty
# 应输出 cluster_name: pmis-dev, status: green/yellow
```

### 9.3 方式 B：Ubuntu 原生

#### 步骤 1：系统参数

```bash
# 1. 创建专用用户（root 不能跑 ES）
sudo useradd -r -s /bin/bash elasticsearch-svc

# 2. 调整虚拟内存
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf
sudo sysctl -p

# 3. 调整文件描述符
echo "elasticsearch-svc - nofile 65535" | sudo tee -a /etc/security/limits.conf
```

#### 步骤 2：下载安装

```bash
cd /tmp
wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-8.15.3-linux-x86_64.tar.gz
tar -xzf elasticsearch-8.15.3-linux-x86_64.tar.gz
sudo mv elasticsearch-8.15.3 /opt/elasticsearch
sudo chown -R elasticsearch-svc:elasticsearch-svc /opt/elasticsearch
```

#### 步骤 3：应用配置

```bash
sudo -u elasticsearch-svc cp /opt/elasticsearch/config/elasticsearch.yml /opt/elasticsearch/config/elasticsearch.yml.bak
sudo cp deploy/common/conf/elasticsearch/elasticsearch.yml /opt/elasticsearch/config/
sudo mkdir -p /opt/elasticsearch/config/jvm.options.d
sudo cp deploy/common/conf/elasticsearch/jvm.options.d/heap.options /opt/elasticsearch/config/jvm.options.d/
sudo chown -R elasticsearch-svc:elasticsearch-svc /opt/elasticsearch/config
```

#### 步骤 4：注册服务

```bash
sudo tee /etc/systemd/system/elasticsearch.service > /dev/null <<EOF
[Unit]
Description=Elasticsearch
After=network.target

[Service]
Type=simple
User=elasticsearch-svc
Group=elasticsearch-svc
LimitNOFILE=65535
LimitNPROC=4096
LimitMEMLOCK=infinity
Environment=JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
Environment=ES_JAVA_OPTS="-Xms512m -Xmx512m"
ExecStart=/opt/elasticsearch/bin/elasticsearch
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable elasticsearch
sudo systemctl start elasticsearch

# 等待（首次启动较慢）
for i in {1..30}; do
  if curl -sf http://127.0.0.1:9200/_cluster/health >/dev/null 2>&1; then
    echo "ES 已就绪"; break
  fi
  sleep 3
done
```

### 9.4 方式 C：Windows 原生

```powershell
# 1. 下载
Invoke-WebRequest -Uri "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-8.15.3-windows-x86_64.zip" `
  -OutFile "$env:TEMP\elasticsearch.zip"
Expand-Archive "$env:TEMP\elasticsearch.zip" -DestinationPath "C:\pmis"
Rename-Item "C:\pmis\elasticsearch-8.15.3" "C:\pmis\elasticsearch"

# 2. 创建专用用户（不能用 LocalSystem）
$esUser = 'elasticsearch-svc'
if (-not (Get-LocalUser -Name $esUser -ErrorAction SilentlyContinue)) {
    $pwd = Read-Host -AsSecureString "请输入 elasticsearch-svc 密码"
    New-LocalUser -Name $esUser -Password $pwd -Description "Elasticsearch Service Account" | Out-Null
}

# 3. 配置
Copy-Item "deploy\infra\elasticsearch\elasticsearch.yml" "C:\pmis\elasticsearch\config\elasticsearch.yml" -Force
New-Item -ItemType Directory -Force -Path "C:\pmis\elasticsearch\config\jvm.options.d" | Out-Null
Copy-Item "deploy\infra\elasticsearch\jvm.options.d\heap.options" "C:\pmis\elasticsearch\config\jvm.options.d\heap.options" -Force

# 4. NSSM
nssm install elasticsearch "C:\pmis\elasticsearch\bin\elasticsearch.bat"
nssm set elasticsearch AppDirectory "C:\pmis\elasticsearch"
nssm set elasticsearch DisplayName "Elasticsearch"
nssm set elasticsearch Start SERVICE_AUTO_START
nssm set elasticsearch ObjectName ".\$esUser"
nssm set elasticsearch AppEnvironmentExtra "ES_JAVA_OPTS=-Xms512m -Xmx512m"
Start-Service elasticsearch
```

> ⚠️ Windows 必须设 `bootstrap.memory_lock: false`

### 9.5 PMIS 服务端集成

ES 已用于 `ydsz-pmis-project` 模块的全文搜索。在 `application.yml` 中：

```yaml
spring:
  elasticsearch:
    uris: http://127.0.0.1:9200
    connection-timeout: 5s
    socket-timeout: 30s
```

### 9.6 验证

```bash
# 1. 健康
curl -s "http://127.0.0.1:9200/_cluster/health?pretty"
# cluster_name: pmis-dev
# status: green/yellow

# 2. 节点信息
curl -s "http://127.0.0.1:9200/_cat/nodes?v"

# 3. 创建/查询索引
curl -X POST "http://127.0.0.1:9200/pmis_test/_doc" -H "Content-Type: application/json" -d '{"name":"test"}'
curl "http://127.0.0.1:9200/pmis_test/_search?pretty"
```

### 9.7 故障排查

| 现象 | 原因 | 解决 |
|---|---|---|
| `max virtual memory areas vm.max_map_count` | 系统参数不足 | `sudo sysctl -w vm.max_map_count=262144` |
| 启动报 `can not run elasticsearch as root` | root 用户运行 | 用专用用户 |
| `bootstrap checks failed` | 各种 | 见 [ES 官方排查](https://www.elastic.co/guide/en/elasticsearch/reference/current/bootstrap-checks.html) |
| 黄色集群状态 | 单节点副本未分配 | 正常（`index.number_of_replicas: 0`） |

---

## 10. 快速验证清单

启动全部 8 个中间件后，依次执行：

```bash
# ======== 端口健康检查 ========
for port in 5432 6379 8848 9100 9101 8091 7091 9876 10911 9100 9200; do
  echo -n "  端口 $port: "
  nc -zv 127.0.0.1 $port 2>&1 | tail -1
done
```

期望输出（开发环境，可有 1-2 个可选中间件未启）：

```
  端口 5432:   succeeded  ← PostgreSQL
  端口 6379:   succeeded  ← Redis
  端口 8848:   succeeded  ← Nacos
  端口 9100:   succeeded  ← XXL-Job
  端口 9100:   succeeded  ← MinIO API  ← ⚠️ 同端口冲突在容器内隔离
  端口 9101:   succeeded  ← MinIO Console
  端口 8091:   succeeded  ← Seata TCP
  端口 7091:   succeeded  ← Seata Console
  端口 9876:   succeeded  ← RocketMQ NS
  端口 10911:  succeeded  ← RocketMQ Broker
  端口 9200:   succeeded  ← Elasticsearch
```

```bash
# ======== 服务健康检查 ========
curl -sf http://127.0.0.1:8848/nacos/actuator/health    && echo "Nacos OK"
curl -sf http://127.0.0.1:9100/minio/health/live        && echo "MinIO OK"
curl -sf http://127.0.0.1:9100/xxl-job-admin/actuator/health && echo "XXL-Job OK"
curl -sf http://127.0.0.1:9200/_cluster/health          && echo "ES OK"
redis-cli -a pmis123 ping                               | grep PONG && echo "Redis OK"
sudo -u postgres psql -d ydsz_pmis -c "SELECT 1"        | grep -q 1 && echo "PostgreSQL OK"
```

```bash
# ======== 验证后端可连接 ========
cd ydsz-pmis-backend
mvn -pl ydsz-pmis-common -am install -DskipTests
mvn -pl ydsz-pmis-gateway spring-boot:run

# 另开终端
curl http://127.0.0.1:9000/actuator/health
# 看到 Nacos 已发现 7 个服务即可
```

---

## 11. 常见问题 FAQ

### Q1: 8 个中间件都装上会不会很重？

**答**：开发环境约 4GB 内存，生产环境约 12GB 内存。如果接受 7 个后端服务 + 8 个中间件的规模，是合理的。建议：
- 测试/演示：Docker Compose 一键起
- 准生产：Ubuntu 原生 + systemd
- 生产：Ubuntu 原生 + 分机部署 + 监控

### Q2: 中间件能不能选替代品？

**答**：可以，但要小范围验证：

| 原组件 | 替代品 | 注意点 |
|---|---|---|
| PostgreSQL | MySQL 8 / KingbaseES | 部分 JSONB 语法需调整 |
| Redis | KeyDB / Dragonfly | API 兼容 |
| Nacos | Eureka + Spring Cloud Config | 配置文件改动 |
| MinIO | Ceph / FastDFS | 客户端代码改 |
| Seata | 不用（@Transactional） | 失去跨服务事务 |
| RocketMQ | Kafka / Pulsar | API 完全不同 |
| XXL-Job | Spring @Scheduled + DB 表 | 失去可视化 |
| Elasticsearch | PostgreSQL tsvector | 大数据量性能差 |

### Q3: 启动顺序有要求吗？

**答**：
- **必须**：`PostgreSQL` 必须在 XXL-Job 前启动
- **必须**：`RocketMQ NameServer` 必须在 Broker 前
- **推荐**：`Nacos` 第一启动（其他服务都依赖它）
- **无要求**：Redis / MinIO / Seata / ES 互相独立

### Q4: 一个物理机能跑几套环境？

**答**：8GB 内存跑 1 套（开发），16GB 跑 2 套（开发 + 测试）。每套约 4GB 内存占用。

### Q5: 内网怎么访问？

**答**：
1. 所有中间件端口对内网开放
2. 控制台（9101/7091/8080/9100）建议加白名单或 Nginx Basic Auth
3. 数据库端口（5432）只允许 7 个后端服务机器访问

### Q6: 监控怎么搞？

**答**：推荐 Prometheus + Grafana + AlertManager。

- 每个中间件都有 `/actuator/prometheus`（Spring Boot 4 服务）
- 中间件自身 Prometheus 端点：
  - PostgreSQL: `postgres_exporter`
  - Redis: `redis_exporter`
  - Nacos: 9090 端口暴露 metrics
  - MinIO: 监控桶
  - ES: `_prometheus` 端点
  - RocketMQ: 1.4+ 有 `mq_exporter`

Grafana 仪表板 ID 推荐：
- PostgreSQL: 9628
- Redis: 11835
- Nacos: 13221
- JVM: 4701

### Q7: 怎么升级版本？

**答**：
1. 备份数据（特别是 PG 和 MinIO）
2. 看官方升级指南（特别是 ES 跨大版本）
3. 蓝绿发布：起新版本容器，切换流量
4. 验证通过后下掉老版本

### Q8: 没有 Docker 也没有 Linux 内网服务器怎么办？

**答**：本手册 [§1.3 方式三](#13-部署方式选择) 已提供 Windows 一键脚本。但生产环境强烈推荐 Linux。

---

> 文档版本：v1.0 · 2026-07-04 · 维护：PMIS 研发部


