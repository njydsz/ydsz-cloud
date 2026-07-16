# ydsz-gateway

> API 网关（Spring Cloud Gateway）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9000**（按构建顺序 1/8） |
| **服务名** | `ydsz-gateway` |
| **构建顺序** | 1/8（Maven 构建第一个部署单元） |
| **Nacos 注册** | ✅ 是（注册中心 + 配置中心） |
| **数据库** | ❌ 不直接访问 |
| **作用** | 统一入口、路由分发、限流/熔断、CORS、认证、Sentinel、灰度 |

## 核心职责

1. **路由分发**：根据 `path` 转发到对应微服务（基于 Nacos 服务发现）
2. **鉴权拦截**：解析 JWT、转发 `X-User-Id` / `X-Tenant-Id` / `X-Trace-Id` 内部头
3. **限流/熔断**：Sentinel Dashboard 对接（端口 8719）
4. **CORS**：按环境白名单放行（生产必须显式域名）
5. **IP 白名单**：`pmis.security.ip-whitelist` 可配置
6. **灰度路由**：基于 `X-Gray-Tag` 头 + Nacos `metadata.version` 元数据
7. **WebSocket**：转发到 `message` 服务的通知推送通道

## 数据库表设计

本模块为**纯路由网关**，**不直接访问任何业务数据库**，仅作为流量入口与横切关注点（限流/熔断/灰度）的执行点。

- ✅ 注册中心：Nacos（仅做服务发现 + 配置中心）
- ✅ 缓存：Redis（限流计数 / IP 白名单缓存）
- ❌ 业务 DB：**不持有任何 `pmis_*` 表**
- ❌ 业务实体：模块内**不定义 `*DO.java`**，所有业务数据均通过路由转发到下游服务（userinfo / system / project / message / workflow / cronjob / agent）

> **设计原则**：
> - 网关注入业务表会带来分布式事务与数据一致性风险，违反"网关无状态"约束；
> - 所有审计 / 操作日志下沉到 `ydsz-system` 的 `pmis_operation_log`（由下游业务服务经 Feign 写入）；
> - 限流统计写入 Redis（`pmis:gateway:ratelimit:*`），不落库；
> - 灰度标签仅作为请求头/Metadata 透传，不持久化。

## 启动顺序

```
gateway (9000) ─── 入口，必须最先启动
   ↓
userinfo (9001) ─┐
system (9002) ───┼─→ 可并行启动
project (9003) ───┘
   ↓
message (9004) ─┐
cronjob (9005) ─┼─→ 可并行启动
workflow (9006)─┤
agent (9007) ───┘
```

## 目录结构

```
ydsz-gateway/
├── pom.xml
└── src/main/
    ├── java/com/njydsz/gateway/
    │   ├── GatewayApplication.java       # 启动类
    │   ├── config/
    │   │   ├── GatewaySentinelConfig.java
    │   │   ├── IpWhitelistProperties.java
    │   │   └── RouteConfig.java
    │   ├── filter/
    │   │   ├── AuthGlobalFilter.java     # JWT 解析
    │   │   ├── GrayLoadBalancerRequestFilter.java
    │   │   └── IpWhitelistFilter.java
    │   └── loadbalancer/
    │       ├── GrayLoadBalancer.java
    │       └── GrayLoadBalancerConfig.java
    └── resources/
        ├── bootstrap.yml                 # Nacos 连接 + 端口（9000）
        └── config/                       # 原 nacos-config（已重命名）
            ├── ydsz-gateway-dev.yaml
            ├── ydsz-gateway-sit.yaml
            └── ydsz-gateway-uat.yaml
```

## 配置文件

| 文件 | 用途 |
|---|---|
| `bootstrap.yml` | Nacos 连接 + 端口（9000）+ shared-configs 引用 |
| `config/ydsz-gateway-dev.yaml` | dev 环境 Nacos 配置（DEBUG 日志 / 文档 UI 开） |
| `config/ydsz-gateway-sit.yaml` | sit 环境（INFO 日志 / 文档 UI 关） |
| `config/ydsz-gateway-uat.yaml` | uat 环境（INFO 日志 / 文档 UI 关） |

**环境变量覆盖**：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | Nacos 地址 |
| `NACOS_NAMESPACE` | `pmis` | 命名空间 |
| `NACOS_USERNAME` | `nacos` | 鉴权用户名 |
| `NACOS_PASSWORD` | （空） | 鉴权密码 |
| `CORS_ALLOWED_ORIGINS` | `*`（dev）/ 显式域名（prod） | CORS 白名单 |
| `SENTINEL_DASHBOARD` | `127.0.0.1:8858` | Sentinel 控制台 |
| `SPRING_PROFILES_ACTIVE` | `dev` | 激活的 profile |

## 启动

### 本地启动（前提：基础设施已启动）

```bash
# 1. 确保 Nacos 已启动
curl http://127.0.0.1:8848/nacos/actuator/health

# 2. 编译公共模块（首次）
cd ydsz-backend
mvn -pl ydsz-common -am install -DskipTests

# 3. 启动 gateway
mvn -pl ydsz-gateway spring-boot:run
```

### 一键启动（推荐）

```bash
# Linux / macOS
./deploy/ubuntu/scripts/start-all.sh

# Windows
.\deploy\windows\scripts\start-all.bat
```

### 验证

```bash
# 健康检查
curl http://localhost:9000/actuator/health

# 路由到 userinfo（需要先启动 userinfo）
curl http://localhost:9000/ydsz-userinfo/actuator/health
```

## 测试

```bash
# 仅测试 gateway
mvn -pl ydsz-gateway -am test

# 集成测试（需基础设施）
mvn -pl ydsz-gateway -am verify
```

## 常见问题

### Q1：Gateway 启动报 "Unable to find GatewayFilterFactory with name ..."

某个自定义 Filter 未声明。检查 `filter/` 目录下的 Filter 类是否带 `@Component` 或在 `RouteConfig` 中注册。

### Q2：跨域 CORS 报错

dev 环境默认 `*`，但生产必须设置 `CORS_ALLOWED_ORIGINS=https://example.com,https://*.example.com`。

### Q3：灰度路由不生效

Nacos 实例 metadata 必须有 `version: gray`，且请求头带 `X-Gray-Tag: gray`。

---

> 任何路由变更必须同步更新前端 `vite.config.ts` 的 proxy 配置。
