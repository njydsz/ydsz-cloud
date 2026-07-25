# ydsz-userinfo

> 用户信息中心（User Info Center）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9001**（按构建顺序 2/8） |
| **服务名** | `ydsz-userinfo` |
| **构建顺序** | 2/8 |
| **数据库** | PostgreSQL（共享主库） |
| **依赖** | Nacos、PostgreSQL、Redis、Gateway |

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
cd ydsz-backend
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
      domain: "@wuxibio"
  userinfo:
    health-enabled: true
```
