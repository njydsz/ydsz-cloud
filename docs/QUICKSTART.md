# YDSZ PMIS · 5 分钟快速启动

> 南京云顶数字科技 · 项目运营管理系统
> 适用：开发联调 / 内网演示 / 内部测试环境

---

## 0. 你需要什么

| 工具 | 版本 | 必须吗 | 备注 |
|---|---|---|---|
| **Docker Desktop** | 24+ | ✅ | 提供 PostgreSQL / Redis / Nacos / MinIO 容器 |
| **JDK** | 21 | ✅ | Spring Boot 4.x 强制要求 |
| **Maven** | 3.9+ | ✅ | 后端编译 |
| **Node.js** | 20+ | ✅ | 前端 |
| **pnpm** | 9+ | ✅ | 前端包管理（`npm i -g pnpm`） |
| 内存 | ≥ 8GB | 推荐 | 7 个后端服务 + 4 个容器 ≈ 4-6GB |

> 💡 **不安装** Maven 也可以 —— Docker 容器里包含后端构建好的镜像。
> 💡 **不安装** Node 也可以 —— 前端用 `pnpm preview` 跑构建产物。

---

## 1. 一行命令启动（最简单）

```bash
# Linux / macOS
git clone <repo> ydsz-pmis && cd ydsz-pmis
./deploy/scripts/start-all.sh

# Windows (PowerShell)
git clone <repo> ydsz-pmis
cd ydsz-pmis
.\deploy\scripts\start-all.bat
```

脚本会自动：
1. 检查环境（Docker / JDK / Maven / Node / pnpm）
2. 复制 `deploy/.env.example` → `deploy/.env`
3. 拉起 4 个基础设施容器（PG / Redis / Nacos / MinIO）
4. 编译公共模块（`ydsz-pmis-common` + `ydsz-pmis-literule`）
5. 后台启动 7 个后端服务（端口 9000-9006）
6. 启动前端（端口 5173）
7. 打印访问地址

**预计耗时**：
- 首次：5-10 分钟（拉镜像 + 灌 SQL + 编译 Maven 依赖）
- 后续：1-2 分钟（缓存命中）

---

## 2. 启动后访问

| 地址 | 说明 | 默认账号 |
|---|---|---|
| **http://localhost:5173** | 前端（从这里开始） | admin / 123456 |
| http://localhost:9000 | API 网关 | — |
| http://localhost:9000/swagger-ui.html | 在线 API 文档 | — |
| http://127.0.0.1:8848/nacos | Nacos 控制台 | nacos / nacos |
| http://127.0.0.1:9101 | MinIO 控制台 | minioadmin / minioadmin |
| http://127.0.0.1:5432 | PostgreSQL | pmis / pmis123 |

默认登录账号（开发环境预置）：

| 账号 | 密码 | 角色 |
|---|---|---|
| `admin` | `123456` | 系统管理员 |
| `pmis` | `pmis123` | 普通用户 |

---

## 3. 启动组件说明

### 3.1 基础设施（4 个 Docker 容器）

| 容器 | 端口 | 数据持久化 | 重置命令 |
|---|---|---|---|
| pmis-postgres | 5432 | `pmis-postgres-data` | `docker volume rm pmis-postgres-data` |
| pmis-redis | 6379 | `pmis-redis-data` | `docker volume rm pmis-redis-data` |
| pmis-nacos | 8848 / 9848 | `pmis-nacos-data` | `docker volume rm pmis-nacos-data` |
| pmis-minio | 9100 / 9101 | `pmis-minio-data` | `docker volume rm pmis-minio-data` |

**仅启动基础设施**：

```bash
# Linux / macOS
./deploy/scripts/start-all.sh --infra

# Windows
.\deploy\scripts\start-all.bat infra
```

### 3.2 后端服务（7 个 Spring Boot 应用）

| 模块 | 端口 | 启动顺序 | 日志 |
|---|---|---|---|
| ydsz-pmis-gateway | 9000 | 1（先启动） | `.run-logs/ydsz-pmis-gateway.log` |
| ydsz-pmis-system | 9001 | 2 | `.run-logs/ydsz-pmis-system.log` |
| ydsz-pmis-userinfo | 9002 | 3 | `.run-logs/ydsz-pmis-userinfo.log` |
| ydsz-pmis-project | 9003 | 4 | `.run-logs/ydsz-pmis-project.log` |
| ydsz-pmis-cronjob | 9004 | 5 | `.run-logs/ydsz-pmis-cronjob.log` |
| ydsz-pmis-workflow | 9005 | 6 | `.run-logs/ydsz-pmis-workflow.log` |
| ydsz-pmis-agent | 9006 | 7 | `.run-logs/ydsz-pmis-agent.log` |

**仅启动后端**（基础设施已就绪）：

```bash
./deploy/scripts/start-all.sh --backend
```

### 3.3 前端

| 路径 | 启动命令 |
|---|---|
| `ydsz-pmis-frontend/` | `pnpm install && pnpm dev` |
| 默认地址 | http://localhost:5173 |

---

## 4. 常用命令速查

```bash
# === 启动 / 停止 ===
./deploy/scripts/start-all.sh              # 启动全部
./deploy/scripts/stop-all.sh               # 停止后端 + 前端
./deploy/scripts/stop-all.sh --with-infra  # 停止全部（含基础设施）

# === 查看状态 ===
docker ps                                  # 看容器状态
tail -f .run-logs/ydsz-pmis-gateway.log    # 实时看后端日志
curl http://localhost:9000/actuator/health # 健康检查

# === 重新导入数据库（清空重灌）===
docker compose -f deploy/docker/docker-compose.dev.yml down -v
docker compose -f deploy/docker/docker-compose.dev.yml up -d
# 容器首次启动会自动执行 docs/V1.0.0.sql 初始化 126 张表

# === 重新导入 Nacos 共享配置 ===
./deploy/scripts/import-nacos-config.sh

# === 编译/测试/覆盖率 ===
cd ydsz-pmis-backend
mvn test                                  # 跑 9 个模块所有测试
mvn -pl ydsz-pmis-gateway spring-boot:run # 单服务启动

# === 前端 ===
cd ydsz-pmis-frontend
pnpm dev                                  # 开发服务器
pnpm test                                 # vitest 单元测试
pnpm type-check                           # vue-tsc 类型检查
pnpm build                                # 生产构建
```

---

## 5. 故障排查

### ❌ 启动时端口被占用

```bash
# 查找占用
lsof -i:9000          # Linux/macOS
netstat -ano | findstr :9000   # Windows

# 方案 A: 杀掉占用进程
kill -9 <PID>         # Linux/macOS
taskkill /PID <PID> /F   # Windows

# 方案 B: 修改端口（修改 deploy/.env 后重启）
echo "POSTGRES_PORT=5433" >> deploy/.env
```

### ❌ 后端连不上 Nacos

```bash
# 1. 检查 Nacos 容器是否健康
docker ps
docker logs pmis-nacos

# 2. 检查 8848 / 9848 端口是否都可达
curl http://127.0.0.1:8848/nacos/
curl http://127.0.0.1:9848/

# 3. 检查 namespace 是否一致
# 7 个 application.yml 都用 namespace=pmis
```

### ❌ 后端连不上数据库

```bash
# 1. 检查容器健康
docker ps | grep pmis-postgres

# 2. 看启动日志（确认 126 张表已建）
docker logs pmis-postgres | grep "V1.0.0"
docker exec -it pmis-postgres psql -U pmis -d ydsz_pmis -c "\dt" | head -30

# 3. 确认 deploy/.env 中 DB_HOST/DB_PORT/DB_PASSWORD 与容器一致
```

### ❌ 前端 401 / 网络错误

```bash
# 1. 确认 gateway 已起来
curl http://127.0.0.1:9000/actuator/health

# 2. 检查 .env.development 的代理配置
# ydsz-pmis-frontend/.env.development 应包含:
#   VITE_API_BASE_URL=http://localhost:9000
#   VITE_API_PREFIX=/api/v1
```

### ❌ Docker 镜像拉取慢

```bash
# 配置镜像加速
# Linux: /etc/docker/daemon.json
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com"
  ]
}

# Windows: Docker Desktop → Settings → Docker Engine
```

### ❌ 第一次启动失败 / 数据残留

```bash
# 一键重置（删除所有数据卷，重新初始化）
docker compose -f deploy/docker/docker-compose.dev.yml down -v
./deploy/scripts/start-all.sh
```

---

## 6. 接下来的步骤

- 📖 **详细部署手册**：见 [DEPLOY.md](DEPLOY.md)
- 🏗 **架构设计**：见 [../README.md](../README.md) 第 4 章
- 🔧 **二次开发**：见 [../ydsz-pmis-backend/README.md](../ydsz-pmis-backend/)（待补充）
- 📊 **业务规则**：见 [../README.md](../README.md) 第 7 章

---

> 文档版本：v1.0 · 2026-07-04 · 维护：PMIS 研发部
