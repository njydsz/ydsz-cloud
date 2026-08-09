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
| **账号安全** | 登录失败计数 + 自动锁定（5 次失败锁 30 分钟） + 密码策略校验 |
| **RBAC** | 用户 / 角色 / 权限 6 要素（用户-角色-权限关联表精确查询） |
| **组织架构** | 部门树形结构 + 公司 + 岗位 |
| **菜单权限** | 菜单树 + 按钮/API 权限码分类 |
| **OAuth2** | 授权码模式（authorize + token 端点） |
| **用户字段** | 用户自定义字段扩展（`ydsz_user_field`） |
| **国际化** | 语言 CRUD（`ydsz_language`） |
| **全局搜索** | `UserinfoSearchController` 基于用户/部门/角色维度 |

## DDD 分层结构

```
ydsz-userinfo/
├── pom.xml
├── ydsz-userinfo-api/                 # API 层：Feign Client + Fallback
│   └── src/main/java/com/njydsz/userinfo/api/
│       ├── client/                    # UserServiceClient / OrgQueryClient
│       └── fallback/                  # Feign 降级实现
├── ydsz-userinfo-domain/              # 领域层：Entity + DTO + VO
│   └── src/main/java/com/njydsz/userinfo/domain/
│       ├── entity/                    # 实体（13 个，无 DO 后缀，符合 entity-naming 规范）
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
│       │   ├── UserField.java         # 用户自定义字段
│       │   └── Language.java          # 语言
│       ├── dto/                       # post/put 子目录 + 7 个查询/操作 DTO
│       ├── query/                     # 分页查询对象（6 个）
│       └── vo/                        # 视图对象（10 个，含 TreeVO）
├── ydsz-userinfo-infra/               # 基础设施层：Mapper + Repository
│   └── src/main/java/com/njydsz/userinfo/infra/mapper/
├── ydsz-userinfo-server/              # 应用层：Service + Config + Health
│   └── src/main/java/com/njydsz/userinfo/server/
│       ├── config/                    # UserInfoProperties + UserInfoConfiguration
│       ├── service/                   # Auth/RBAC/Org/Menu/OAuth2 Service
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
| `/api/v1/user` | 用户 CRUD + 分页 + 密码管理 + 角色分配 |
| `/api/v1/role` | 角色 CRUD + 权限分配 |
| `/api/v1/dept` | 部门 CRUD + 树形结构 |
| `/api/v1/menu` | 菜单 CRUD + 树形结构 |
| `/api/v1/company` | 公司 CRUD |
| `/api/v1/post` | 岗位 CRUD |
| `/api/v1/language` | 语言 CRUD |
| `/api/v1/captcha` | 图形验证码生成/校验 |
| `/api/v1/oauth2/authorize` `/token` | OAuth2 授权码模式 |
| `/api/v1/userinfo/search` | 用户/部门/角色搜索 |
| `/api/internal/user/query` `/dept/tree` | 内部 Feign 调用接口 |

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
| `ydsz_user_field` | 用户自定义字段 |
| `ydsz_language` | 语言 |
| `ydsz_login_audit` | 登录审计（物理表在 ydsz-system） |
| `ydsz_user_session` | 用户会话（Redis 存储） |
| `ydsz_user_2fa` | 双因子认证密钥 |

## 安全特性

| 特性 | 说明 |
|---|---|
| **密码加密** | BCrypt（PasswordEncoder） |
| **密码策略** | 最少 8 位 + 大小写/数字/特殊字符 3 选 4 + 禁止连续重复 + 禁止包含用户名 |
| **账号锁定** | 5 次密码错误自动锁定 30 分钟，登录成功自动解锁 |
| **JWT 黑名单** | 登出后 Token 加入 Redis 黑名单（Bloom Filter 前置过滤） |
| **OAuth2** | 标准授权码模式，授权码 5 分钟有效，一次性使用 |
| **验证码** | 4 位字母数字混合，Base64 PNG 图片，5 分钟有效 |
| **LDAP** | 可选 LDAP/ADFS 域认证（@ConfigurationProperties 配置注入） |
| **指标埋点** | Micrometer 计数器/计时器（登录成功/失败/认证耗时） |
| **健康检查** | Redis + JWT + 数据库连通性 + 用户/角色计数 |

## Feign 接口

| 客户端 | 方法 | 返回类型 |
|---|---|---|
| `UserServiceClient` | `getUserInfo(userId)` | `BaseResponse<UserAccountVO>` |
| `OrgQueryClient` | `queryUserById(userId)` | `BaseResponse<UserAccountVO>` |
| `OrgQueryClient` | `getDeptTree()` | `BaseResponse<List<DepartmentTreeVO>>` |
| `OrgQueryClient` | `getDeptList()` | `BaseResponse<List<DepartmentTreeVO>>` |

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
  userinfo:
    health-enabled: true
```

### 配置项

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.userinfo.health-enabled` | `true` | 是否启用健康检查 |
| `ydsz.auth.token.enabled` | `true` | 是否启用 JWT Token 服务 |
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
