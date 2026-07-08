# ydsz-pmis-userinfo

> 用户信息中心（User Info Center）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9001**（按构建顺序 2/8） |
| **服务名** | `ydsz-pmis-userinfo` |
| **构建顺序** | 2/8 |
| **数据库** | PostgreSQL（共享主库） |
| **依赖** | Nacos、PostgreSQL、Redis、Gateway |

## 核心职责

| 业务域 | 说明 |
|---|---|
| **登录认证** | 账号密码 + 图形验证码 + 二次认证 + TOTP 2FA |
| **Token 管理** | JWT 签发 / 刷新 / 失效 |
| **会话管理** | 在线用户、强制下线、会话审计 |
| **登录审计** | 登录成功/失败/锁定/异地登录记录 |
| **RBAC** | 用户 / 角色 / 权限 6 要素 |
| **部门 / 人员** | 组织架构树 + 人员档案 + 职级 |
| **资源池** | 总部池 / 事业部池 / 备用池 + Bench 自动入出池 |
| **员工标签** | 自定义标签（用于资源调度） |
| **考勤** | 请假 / 加班 / 调休 |
| **外包台账** | 外包人员合同 + 成本归集 |

## 关键 Controller

| 路径前缀 | 作用 |
|---|---|
| `/auth/login` | 登录（密码 + 验证码 + 2FA 校验） |
| `/auth/2fa` | 二次认证 |
| `/auth/reauth` | 敏感操作再认证 |
| `/user` | 用户 CRUD |
| `/role` / `/permission` | RBAC |
| `/dept` | 部门 |
| `/position` | 职级 |
| `/dict` | 数据字典 |
| `/resource/pool` | 资源池 |
| `/resource/bench` | Bench 管理 |
| `/employee-tag` | 员工标签 |
| `/leave` | 请假 |
| `/overtime` | 加班 |
| `/attendance` | 考勤 |

## 启动顺序

依赖 `common` 库 + `nacos`，**应在 `gateway` 之后、`message` 之前**启动。

## 目录结构

```
ydsz-pmis-userinfo/
├── pom.xml
└── src/main/
    ├── java/com/njydsz/pmis/userinfo/
    │   ├── UserInfoApplication.java
    │   ├── controller/        # ~15 个 Controller
    │   ├── service/           # 业务实现
    │   ├── mapper/            # MyBatis-Plus Mapper
    │   ├── entity/            # DO / DTO
    │   ├── dto/ / vo/
    │   ├── enums/
    │   └── config/
    ├── resources/
    │   ├── bootstrap.yml
    │   ├── application.yml
    │   ├── mapper/            # XML 映射文件
    │   └── nacos-config/
    │       ├── ydsz-pmis-userinfo-dev.yaml
    │       ├── ydsz-pmis-userinfo-sit.yaml
    │       └── ydsz-pmis-userinfo-uat.yaml
    └── test/
        └── java/              # 单元测试
```

## 配置文件

| 文件 | 用途 |
|---|---|
| `bootstrap.yml` | Nacos 连接 + 端口 9001 |
| `application.yml` | 本地兜底 |
| `nacos-config/ydsz-pmis-userinfo-dev.yaml` | dev（DEBUG / 文档 UI 开） |
| `nacos-config/ydsz-pmis-userinfo-sit.yaml` | sit（INFO / 文档 UI 关） |
| `nacos-config/ydsz-pmis-userinfo-uat.yaml` | uat |

**环境变量**：

| 变量 | 说明 |
|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | 数据库连接 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接 |
| `JWT_SECRET` | JWT 签名密钥（生产 ≥ 32 字节） |
| `CAPTCHA_REQUIRED` | 登录是否强制图形验证码（默认 true） |

## 启动

```bash
# 1. 编译依赖
cd ydsz-pmis-backend
mvn -pl ydsz-pmis-common,ydsz-pmis-literule -am install -DskipTests

# 2. 启动（需先启动 Nacos、PostgreSQL、Redis）
mvn -pl ydsz-pmis-userinfo spring-boot:run

# 3. 验证
curl http://localhost:9001/actuator/health
```

### 一键启动

```bash
./deploy/ubuntu/scripts/start-all.sh
```

## 测试

```bash
# 仅测试 userinfo
mvn -pl ydsz-pmis-userinfo -am test

# 覆盖率报告
mvn -pl ydsz-pmis-userinfo -am verify
# 报告路径：target/site/jacoco/index.html
```

## Feign 接口（被其他服务调用）

本服务**不**主动调用其他业务服务，但被以下 Feign 客户端调用：

- `OrgQueryClient`（位于 `ydsz-pmis-common`）→ 跨服务查询人员/部门

> 修改本服务 API 时，必须同步更新 `OrgQueryClient` 及其 Fallback。

## 常见问题

### Q1：登录报 "账号已锁定"

5 次密码错误自动锁定 30 分钟。可通过 `pmis:auth:max-fail-count` 和 `lock-duration-minutes` 配置。

### Q2：JWT 过期

默认 2 小时。生产环境可对接 Nacos 动态调整 `pmis:jwt:expire-minutes`。

### Q3：资源池 Bench 自动入出池

定时任务每天凌晨 2 点扫描。手动触发：`POST /resource/bench/scan`。

---

> 任何 RBAC 变更必须走审批流（数据权限 + 业务权限双重校验）。
