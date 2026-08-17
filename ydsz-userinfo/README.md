# ydsz-userinfo

> 用户信息中心（User Info Center）— 登录认证 / RBAC / 组织架构 / OAuth2

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9002**（按构建顺序 3/10） |
| **服务名** | `ydsz-userinfo` |
| **构建顺序** | 3/10 |
| **数据库** | PostgreSQL（共享主库） |
| **依赖** | Nacos、PostgreSQL、Redis、Gateway |
| **公共依赖** | common-web / common-auth / common-redis / common-safe / common-jdbc |

## 核心职责

| 业务域 | 说明 |
|---|---|
| **登录认证** | 账号密码 + 图形验证码 + LDAP/ADFS 域认证 |
| **Token 管理** | JWT 签发 / 刷新 / 失效（common-auth TokenService） |
| **账号安全** | 登录失败计数 + 自动锁定（5 次失败锁 30 分钟） + 密码策略校验 + 弱口令字典 + 风险评分 + 密码历史（防重用） |
| **RBAC** | 用户 / 角色 / 权限 6 要素（用户-角色-权限关联表精确查询） |
| **组织架构** | 部门树形结构 + 公司 + 岗位 |
| **菜单权限** | 菜单树 + 按钮/API 权限码分类 |
| **OAuth2** | 授权码模式（authorize + token 端点）+ PKCE |
| **登录历史** | 登录/密码变更历史（`ydsz_user_login_history` / `ydsz_user_password_history`） |
| **国际化** | 语言 CRUD（`ydsz_language`） |
| **全局搜索** | `UserinfoSearchController` 基于用户/部门/角色维度 |

## DDD 分层结构

```
ydsz-userinfo/
├── pom.xml
├── ydsz-userinfo-api/                 # API 层：Feign Client + Fallback
│   └── src/main/java/com/njydsz/userinfo/api/
│       ├── client/                    # OrgQueryClient（15 个方法）
│       └── fallback/                  # Feign 降级实现
├── ydsz-userinfo-domain/              # 领域层：Entity + DTO + VO
│   └── src/main/java/com/njydsz/userinfo/domain/
│       ├── entity/                    # 实体（14 个，无 DO 后缀，符合 entity-naming 规范）
│       │   ├── UserAccount.java       # 用户账号
│       │   ├── Role.java              # 角色
│       │   ├── RolePermission.java    # 角色-权限关联
│       │   ├── UserRole.java          # 用户-角色关联
│       │   ├── Menu.java              # 菜单/按钮/API 权限定义
│       │   ├── Department.java        # 部门（树形）
│       │   ├── Company.java           # 公司
│       │   ├── CompanyDept.java       # 公司-部门关联
│       │   ├── Post.java              # 岗位
│       │   ├── UserDept.java          # 用户-部门关联
│       │   ├── UserPost.java          # 用户-岗位关联
│       │   ├── UserLoginHistory.java  # 登录历史
│       │   ├── UserPasswordHistory.java # 密码变更历史
│       │   └── Language.java          # 语言
│       ├── dto/                       # create/update 子目录 + 查询/操作 DTO
│       ├── query/                     # 分页查询对象（5 个）
│       └── vo/                        # 视图对象（10 个，含 TreeVO）
├── ydsz-userinfo-infra/               # 基础设施层：Mapper + Repository
│   └── src/main/java/com/njydsz/userinfo/infra/mapper/
├── ydsz-userinfo-server/              # 应用层：Service + Config + Health
│   └── src/main/java/com/njydsz/userinfo/server/
│       ├── auth/                      # 认证/风控（RiskScoringService / WeakPasswordDictionary / LoginAttemptContext 等）
│       ├── config/                    # UserInfoProperties + UserInfoConfiguration
│       ├── service/                   # Auth/RBAC/Org/Menu/OAuth2/Excel/登录历史 Service
│       ├── search/                    # UserinfoSearchProvider
│       ├── event/                     # UserDomainEventPublisher
│       ├── metrics/                   # UserInfoMetrics
│       └── health/                    # UserInfoHealthIndicator
└── ydsz-userinfo-web/                 # Web 层：Controller + Bootstrap
    └── src/main/java/com/njydsz/userinfo/web/
        ├── UserInfoApplication.java
        └── controller/                # 12 个 Controller
            ├── AuthController.java          # /api/v1/auth
            ├── CaptchaController.java       # /api/v1/captcha
            ├── UserAccountController.java   # /api/v1/user
            ├── RoleController.java          # /api/v1/role
            ├── DepartmentController.java    # /api/v1/dept
            ├── MenuController.java          # /api/v1/menu
            ├── CompanyController.java       # /api/v1/company
            ├── PostController.java          # /api/v1/post
            ├── LanguageController.java      # /api/v1/language
            ├── OAuth2Controller.java        # /api/v1/oauth2
            ├── UserinfoSearchController.java # /api/v1/userinfo/search
            └── InternalApiController.java   # /api/internal（Feign 内部调用）
```

## 关键 Controller

| 路径前缀 | 作用 |
|---|---|
| `/api/v1/auth/login` `/logout` `/refresh` | 登录/登出/Token 刷新 |
| `/api/v1/user` | 用户 CRUD + 分页 + 密码管理 + 角色分配 + 批量操作 + Excel 导入导出 |
| `/api/v1/user/{userId}/login-history` | 用户登录历史查询 |
| `/api/v1/role` | 角色 CRUD + 权限分配 |
| `/api/v1/dept` | 部门 CRUD + 树形结构 |
| `/api/v1/menu` | 菜单 CRUD + 树形结构 |
| `/api/v1/company` | 公司 CRUD |
| `/api/v1/post` | 岗位 CRUD |
| `/api/v1/language` | 语言 CRUD |
| `/api/v1/captcha` | 图形验证码生成/校验 |
| `/api/v1/oauth2/authorize` `/token` | OAuth2 授权码模式（支持 PKCE） |
| `/api/v1/userinfo/search` | 用户/部门/角色搜索 |
| `/api/internal/*` | 内部 Feign 调用接口（用户查询/部门树/审批人缓存等 15 个端点） |

## 数据库表设计

| 表名 | 说明 |
|---|---|
| `ydsz_user_account` | 用户账号（含登录失败计数/锁定时间/最后登录信息） |
| `ydsz_role` | 角色定义 |
| `ydsz_menu` | 菜单/按钮/API 权限定义 |
| `ydsz_user_role` | 用户-角色关联 |
| `ydsz_role_permission` | 角色-权限关联 |
| `ydsz_department` | 部门（树形结构） |
| `ydsz_company` | 公司 |
| `ydsz_company_dept` | 公司-部门关联 |
| `ydsz_post` | 岗位 |
| `ydsz_user_dept` | 用户-部门关联 |
| `ydsz_user_post` | 用户-岗位关联 |
| `ydsz_user_login_history` | 登录历史 |
| `ydsz_user_password_history` | 密码变更历史 |
| `ydsz_language` | 语言 |
| `sys_audit_log` | 审计日志（物理表在 common-audit，写操作经 @Audit 记录） |
| `ydsz_user_session` | 用户会话（Redis 存储，`userinfo:session:user:{userId}`） |

## 安全特性

| 特性 | 说明 |
|---|---|
| **密码加密** | BCrypt（PasswordEncoder，强度可配置） |
| **密码策略** | 最少 8 位 + 大小写/数字/特殊字符 3 选 4 + 禁止连续重复 + 禁止包含用户名 + 弱口令字典校验 + 密码历史（防近期重用） |
| **账号锁定** | 5 次密码错误自动锁定 30 分钟，登录成功自动解锁（原子 SQL 计数，无并发竞态） |
| **双因素认证（MFA）** | TOTP（RFC 6238，兼容 Google/Microsoft Authenticator）+ 短信验证码降级；登录风险 HIGH 时强制校验（P0-2） |
| **动态认证策略** | 风险 MEDIUM+ 强制图形验证码，HIGH 追加 MFA，CRITICAL 拒绝登录（P0-2） |
| **JWT 黑名单** | 登出后 access_token + refresh_token 一并加入 Redis 黑名单（SHA-256 摘要 + 分布式锁，common-auth TokenBlacklistService） |
| **OAuth2** | 授权码模式 + PKCE + refresh_token 轮换 + revoke（RFC 7009）+ introspect（RFC 7662）+ userinfo（P1-4），授权码 5 分钟有效、一次性使用 |
| **验证码** | 4 位字母数字混合，Base64 PNG 图片，5 分钟有效 |
| **LDAP** | 可选 LDAP/ADFS 域认证（@ConfigurationProperties 配置注入） |
| **登录风控** | 风险评分（RiskScoringService）+ 登录尝试上下文（LoginAttemptContext）+ Redis 计数器统一采集（P1-2/P1-5） |
| **权限缓存失效** | 菜单/角色变更发布 PermissionChangedEvent（common-auth）+ 角色权限 DB 结果缓存主动失效（P0-1） |
| **指标埋点** | Micrometer 计数器/计时器（登录成功/失败/认证耗时/在线会话数） |
| **健康检查** | Redis + JWT + 数据库连通性 + 用户/角色计数 |

## Feign 接口

| 客户端 | 方法（节选） | 返回类型 |
|---|---|---|
| `OrgQueryClient` | `queryUserById(userId)` | `BaseResponse<UserAccountVO>` |
| `OrgQueryClient` | `getUserInfo(userId)` | `BaseResponse<UserAccountVO>` |
| `OrgQueryClient` | `getDeptTree()` | `BaseResponse<List<DepartmentTreeVO>>` |
| `OrgQueryClient` | `getDeptList()` | `BaseResponse<List<DepartmentVO>>` |
| `OrgQueryClient` | `listUserIdsByRoleCode` / `getLeaderByUserId` / batch-names 等 | 共 15 个方法 |

## 启动顺序

依赖 `common` + `nacos`，**应在 `gateway` 之后**启动。

```bash
cd ydsz-cloud
mvn -pl ydsz-common -am install -DskipTests
mvn -pl ydsz-userinfo spring-boot:run
```

## 配置

```yaml
ydsz:
  userinfo:
    health-enabled: true
    token-ttl-seconds: 7200
    max-login-fail-count: 5
    lock-duration-minutes: 30
    captcha-enabled: true
    password-min-length: 8
    password-max-length: 32
    password-min-category-count: 3
    bcrypt-strength: 10
    oauth2-clients:
      - client-id: "demo"
        client-secret: "..."
    password-history-count: 5
  auth:
    token:
      enabled: true
      secret-key: "your-jwt-secret-key-at-least-32-chars"
      access-token-expire-seconds: 7200
      refresh-token-expire-seconds: 604800
    ldap:
      enabled: false
      host: 10.248.3.56
      port: 389
      domain: "@ydszsoft"
```

### 配置项（节选，`ydsz.userinfo.*`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.userinfo.health-enabled` | `true` | 是否启用健康检查 |
| `ydsz.userinfo.token-ttl-seconds` | `7200` | 会话 Token 有效期（秒） |
| `ydsz.userinfo.max-login-fail-count` | `5` | 连续失败锁定阈值 |
| `ydsz.userinfo.lock-duration-minutes` | `30` | 锁定时长（分钟） |
| `ydsz.userinfo.captcha-enabled` | `true` | 是否启用图形验证码 |
| `ydsz.userinfo.password-min-length` | `8` | 密码最小长度 |
| `ydsz.userinfo.password-min-category-count` | `3` | 密码字符类别数下限 |
| `ydsz.userinfo.password-history-count` | `5` | 禁止重用的历史密码条数 |
| `ydsz.userinfo.bcrypt-strength` | `10` | BCrypt 强度（4-31） |
| `ydsz.userinfo.oauth2-clients` | `[]` | OAuth2 客户端注册（client-id/secret） |
| `ydsz.auth.token.secret-key` | （必填） | JWT 签名密钥（至少 32 字符） |
| `ydsz.auth.token.access-token-expire-seconds` | `7200` | Access Token 有效期（秒） |
| `ydsz.auth.token.refresh-token-expire-seconds` | `604800` | Refresh Token 有效期（秒） |
| `ydsz.auth.ldap.enabled` | `false` | 是否启用 LDAP/ADFS 域认证 |
| `ydsz.auth.ldap.host` | — | LDAP 服务器地址 |
| `ydsz.auth.ldap.port` | `389` | LDAP 端口 |
| `ydsz.auth.ldap.domain` | — | LDAP 域后缀（如 `@ydszsoft`） |

## 常见问题

### Q1：登录失败 "账号或密码错误" 但密码正确

1. 检查 `ydsz_user_account` 中 `status = 'ENABLED'` 且 `deleted = 0`
2. 检查 `lock_time` 是否在未来（账号被锁定）
3. 检查 `password` 字段是否为 BCrypt 哈希值

### Q2：JWT Token 刷新失败

1. Refresh Token 已过期（默认 7 天）
2. Token 已加入黑名单（用户已登出）
3. `ydsz.auth.token.secret-key` 被修改过

### Q3：LDAP 认证失败

1. 检查 `ydsz.auth.ldap.enabled=true`
2. 检查 LDAP 服务器连通性
3. 用户名需去掉域后缀（系统自动拼接 `domain`）

### Q4：菜单树不显示

1. 检查 `ydsz_menu` 表是否有数据且 `status = 'ENABLED'`
2. 检查用户角色是否关联了对应菜单权限
3. 菜单树按 `parent_id` 递归构建，根节点 `parent_id = 0`

---

> 本模块是 YDSZ 的**身份认证与组织架构中心**，所有业务模块通过 Feign 调用获取用户/部门信息。
> 严禁在业务模块中重复实现用户/权限查询逻辑。
