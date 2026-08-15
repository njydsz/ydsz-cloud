# ydsz-userinfo 深度审查报告（2026 Q3）

> 分析基准：最新代码（2026-08-15）
> 模块形态：部署单元（端口 9002，构建序 3/10），DDD 五层（api / domain / infra / server / web）
> 覆盖范围：登录认证、RBAC、组织架构、菜单权限、OAuth2、用户字段、国际化、全局搜索、工作流审批人展开
> 对标对象：Keycloak、若依 RuoYi、Okta/PingIdentity（IAM）、阿里云 RAM / 腾讯云 CAM（RBAC+数据权限）、Spring Security OAuth2、NIST SP 800-63B、OWASP ASVS、美团/阿里研发规范（`docs/云顶编码规范.md`）

---

## 0. 总评

模块整体已达到**企业级 IAM 服务的合格基线**：DDD 分层清晰、依赖方向正确（domain 不依赖 infra）、安全基线完备（BCrypt + 密码策略 + 锁定 + IP 封禁 + Token 轮换 + 黑名单 + 验证码）、跨服务解耦到位（Feign + Fallback、SPI、Outbox 事件）、可观测性齐全（Micrometer 指标 + 健康检查 + 审计 + 幂等 + 限流注解）。

相较同类竞品，`ydsz-userinfo` 的短板不在"基础 CRUD/鉴权"，而在三处：

1. **正确性收口**：登录失败计数的并发竞态、角色变更后的缓存失效错位（存在死代码 + 未失效项）。
2. **能力声明 vs 代码实现的错位**（与用户长期关注的"前后端能力不匹配"同源）：MFA/2FA、OAuth2 PKCE、会话踢下线在文档/异常码/注释中已"声明"，但代码无实现。
3. **测试覆盖为零**：`spring-boot-starter-test` 已引入，但模块内 `src/test` 不存在任何用例，安全链路完全依赖人工验证。

---

## 一、架构优化

### 1.1 🔴 登录失败计数存在并发竞态（read-modify-write 非原子）

`AuthServiceImpl.recordLoginFailure()` 采用"内存 +1 后 UPDATE"：

```java
int failCount = (user.getLoginFailCount() == null ? 0 : user.getLoginFailCount()) + 1;
updateWrapper.set(UserAccount::getLoginFailCount, failCount);   // 非原子
```

同一账号并发登录失败时，两个请求读到同一旧值，后写覆盖先写，导致**锁定阈值永远达不到**（密码爆破的防护可被并发击穿）。`updateLoginSuccess` 同理存在并发覆盖（登录成功重置计数与并发失败写互相覆盖）。

**建议**：改为数据库原子自增，避免先查后写：

```sql
UPDATE ydsz_user_account SET login_fail_count = login_fail_count + 1 WHERE id = ?
```

并配合 `locked_until` 判定，把"达到阈值则锁定"下沉到 SQL 条件（`WHERE login_fail_count >= threshold`）或依赖唯一约束/分布式锁兜底。对标若依：其登录失败计数即用原子 `UPDATE`。

### 1.2 🔴 角色变更缓存失效错位 + 死代码

`UserAccountServiceImpl.assignRoles()` 变更用户角色后只调用了：

```java
private void evictWorkflowCacheForUser(String userId) {
    redisStringOps.del("userinfo:workflow:leader:" + userId);   // 只删了 leader，未删 role:*
}
```

但工作流 `role:xxx` 审批人展开的缓存 key 是 **`userinfo:workflow:role:{roleCode}`**（按角色编码缓存），与用户 ID 无关。因此**给用户重新分配角色后，`/user/list-by-role` 的旧缓存继续生效**，工作流审批人解析拿到的是过期名单。

同时 `WorkflowApproverCacheService` 提供了 `evictRoleCache / evictUserCache / evictDeptLeaderCache / evictAllWorkflowCache` 四个失效方法，**但全模块零调用**（死代码）。失效链路实际是残缺的。

**建议**：
- `assignRoles` 改为调用 `workflowCacheService.evictRoleCache(null)`（失效全部 role 缓存）或按变更前后 roleCode 精确失效；
- 删除 `userinfo:role:permissions:*` 与 `userinfo:workflow:*` 之外的死方法，或将失效统一收敛到一个 `UserInfoCacheInvalidator` 组件，避免"两套缓存、两套失效"。

### 1.3 🟡 权限加载双路径，边界模糊

角色权限的读取存在两条独立链路：

| 链路 | 是否缓存 | 用途 |
|---|---|---|
| `DbRolePermissionLoader`（RolePermissionLoader SPI） | ❌ 每次直查 role→role_permission→menu 三表 | 网关/鉴权（`@PreAuthorize` 权限判定） |
| `RoleServiceImpl.getRolePermissionIds` | ✅ `userinfo:role:permissions:{roleId}` 10min | 前端展示（`RoleController` 查询接口） |

而 `common-auth` 的 `AuthConfiguration` 还默认装配了第三个 `RedisRolePermissionLoader`。**鉴权主链路走的是无缓存路径**，`evictRolePermissionCache` 保护的却是仅用于前端展示的缓存——缓存失效投入与实际读路径不匹配。

**建议**：统一权限读取入口为单一 `RolePermissionLoader`（DB 加载 + Redis 缓存 + 失效），删除 `getRolePermissionIds` 的独立缓存体系；确认 `RedisRolePermissionLoader` 与 `DbRolePermissionLoader` 的装配优先级（避免两个 bean 同时存在导致歧义）。

### 1.4 🟡 状态字段类型不统一（历史技术债）

`UserAccount.status` 为整数列（`0/1`），经 `IntegerStringTypeHandler` 桥接为 String；而 `Role/Menu/Department/Company/Post/Language.status` 均为 String（`"ENABLED"/"DISABLED"`）。业务代码里因此出现 `"0".equals(user.getStatus())` 与 `entity.setStatus("1")` 的魔数并存，易错、难维护。

**建议**：DB 迁移将 `ydsz_user_account.status` 收敛为 `ENABLED/DISABLED` 字符串（或在实体层彻底封装成枚举 `EnableStatusEnum`），消除 TypeHandler 桥接与魔数。

### 1.5 🟡 AuthenticationProvider SPI 反模式

`LdapAuthenticationProvider` 实现 `AuthenticationProvider` SPI，但 `authenticate()` 方法**恒返回 null**（注释自述"Token 签发由 AuthServiceImpl 统一处理，SPI 方法返回 null"）。实现接口却返回 null，说明 SPI 契约与真实调用方式（`AuthServiceImpl` 直接调 `authenticateLdap()`）已经分叉，抽象失真。

**建议**：要么让 SPI 语义闭环（authenticate 返回认证结果/上下文，由框架统一签发），要么取消该 SPI 实现、将 LDAP 定位为纯组件由 `AuthService` 编排调用，二选一。

---

## 二、功能增强

### 2.1 🔴 MFA/2FA 只声明未实现（文档/代码错位）

- 异常码已定义 `MFA_REQUIRED(A20108)` / `MFA_INVALID(A20109)` / `MFA_NOT_BOUND(B30009)`；
- `README.md` 声明了 `ydsz_user_2fa` 双因子密钥表；
- `UserInfoApplication` 注释声称"复用 common-auth（JWT/RBAC/**TOTP**）"。

但**模块内无任何 TOTP/2FA 实现类、无 `User2fa` 实体、无绑定/校验接口**。这是一个典型"能力声明超前于实现"的缺口。

**建议**：按 P0 补齐 TOTP（RFC 6238）绑定 + 登录二次校验 + 恢复码；若暂不做，应先从 README/注释中移除误导性声明，避免"声明即能力"的偏差。

### 2.2 🟡 会话管理缺失（无法按用户踢下线）

`logout` 仅把**当前 access_token** 加入黑名单，Redis 会话 key 为 `accessToken`（token 维度），无 `userId → 会话集合` 的索引。因此无法实现"管理员强制下线某用户的所有设备"、"改密/禁用后全端失效"。`UserInfoMetrics` 里的 `onlineSessions` 也只是本地 JVM 计数，非真实分布式会话数。

**建议**：登录时维护 `userinfo:session:user:{userId} → Set<accessToken>`，登出/禁用/改密时遍历清空；`onlineSessions` Gauge 改为读 Redis 集合大小。

### 2.3 🟡 密码找回 / 账号生命周期缺失

现有密码能力只有：管理员 `resetPassword`、用户自助 `changePassword`。缺少：
- **忘记密码**（邮箱/短信验证码找回流程）；
- **密码过期策略 / 首次登录强制改密**（`UserPasswordHistory` 有历史记录，但无 `password_expired_at` / `must_change` 字段）；
- **账号过期时间**（只有 `status` 启用/禁用，无 `valid_until`）。

对标若依：`sys_user` 含 `pwd_update_time`，支持"密码 N 天未改提醒"。建议补齐上述字段与流程。

### 2.4 🟡 OAuth2 PKCE 声明与实现不符

`OAuth2Controller` 注释称"授权码端点不校验 clientSecret（PKCE 替代）"，但 `authorize` 端点**未实现 code_challenge/code_verifier**（无 `code_challenge` 入参、无 S256 校验）。当前等价于"公共客户端无任何防护"。

**建议**：补齐 RFC 7636 PKCE（S256），公共客户端强制 `code_challenge`；同时补 `token revocation` 端点与 `scope` 粒度（当前 `scope` 硬编码 `"read write"`）。

### 2.5 🟡 数据权限未按角色配置（对标若依/CAM 的差距）

`@DataScope` 是全局 AOP 追加固定部门过滤，**无法按角色配置数据范围**（全部 / 本部门 / 本部门及子部门 / 仅本人 / 自定义）。对标若依 RuoYi 的 `DataScope`（按角色 dataScope 字段动态决定过滤粒度）与云厂商 CAM 的"资源+策略"，当前实现粒度不足。

**建议**：在 `ydsz_role` 增加 `data_scope` 字段，`AuthRowPermissionAspect` 读取当前用户角色集合求并集，动态生成 WHERE 范围。

### 2.6 🟡 异常登录检测缺失

已记录 `loginIp / userAgent`，但无"异地登录 / 新设备登录"的二次验证或告警。建议结合 `lastLoginIp` 与登录历史，对异常 IP 跳变触发 MFA 或通知（`common-notify` 已引入，可复用）。

---

## 三、性能提升

### 3.1 🟡 BCrypt 同步校验阻塞登录热点

BCrypt cost=10 单次校验约 80–120ms，`login` 接口同步执行，高并发下 CPU 打满、拖垮整个认证服务。

**建议**：
- 隔离密码校验到独立线程池，配合登录接口限流（已有限流可保留）；
- 评估 Argon2id 或按账号风险分级降低 cost；
- 对 BCrypt 校验加**快速失败短路**（先查用户/状态再算 BCrypt，当前顺序已正确，但应确保校验前不泄露用户是否存在）。

### 3.2 🟡 IP 封禁每登录一次查一次 DB

`LoginHistoryServiceImpl.isIpBlocked()` 每次登录都 `selectCount` 扫描 `ydsz_login_history`。高频登录下该查询成为额外 DB 压力，且无索引保障时全表扫。

**建议**：改用 Redis 滑动窗口计数（`INCR` + `EXPIRE`，key=`userinfo:ip:block:{ip}`），失败达阈值则封禁；DB 仅保留审计明细，异步落库。

### 3.3 🟡 登录历史同步写库

`recordLoginAttempt()` 在登录主线程内同步 `insert`（虽有 try-catch 兜底，但仍占用连接与耗时）。建议改为异步队列/线程池（`common-thread` 已引入）落库，登录主链路只做 Redis 计数。

### 3.4 🟡 菜单树无缓存（部门树已有）

`MenuServiceImpl.tree()` 每次全表查 + 内存建树，**无缓存**；`DepartmentServiceImpl.tree()` 已做 Redis 缓存。菜单树是网关/前端权限渲染的高频读，应补齐缓存 + 变更失效（`MenuServiceImpl` 写操作目前也不失效任何缓存，因为根本没缓存）。

### 3.5 🟡 全量 list 无上限保护

`/user/list`、`/role/list` 等 `list()` 返回全量，无分页/limit。数据量大时内存与网络压力陡增（注释自述">500 建议缓存"但无代码保护）。建议加 `LIMIT` 或强制走 `page`。

### 3.6 🟡 batch-names 未截断（注释与实现不符）

`InternalApiController` 注释称"≤500，超过会被 Service 层截断"，但 `UserAccountServiceImpl.batchUserNames()` 只做 distinct/filter，**无 500 截断**。超大批量 IN 会导致 SQL 过长。建议统一加 `subList(0, 500)` 或分批 IN。

### 3.7 🟡 用户导出同步阻塞

`UserExcelServiceImpl.exportUsers()` 同步查询全量并生成 Excel，`UserAccountController` 注释自认"建议异步导出，当前实现为同步"。建议改为"提交导出任务 + 轮询下载"（`common-file` 已引入，可落文件走 OSS 下载链接）。

---

## 四、体验改善

### 4.1 🟡 登录失败信息泄露（用户名枚举）

`AuthServiceImpl.login()` 区分返回 `USER_NOT_FOUND` 与 `PASSWORD_INCORRECT`，攻击者可据此枚举有效用户名（虽有 IP 封禁缓解，仍是信息泄露）。对标大厂统一返回"用户名或密码错误"，并仅在审计日志里区分真实原因。

### 4.2 🟡 i18n 消息覆盖不完整

`UserInfoExceptionCode` 定义了 40+ 个 `key`，但 `message_zh.properties` / `message_en.properties` 仅 13 条，其余 key 无译文，回退为裸 key。建议按错误码枚举补齐中英双语。

### 4.3 🟡 导入/导出异常处理粗糙

`importUsers` / `exportUsers` 的 Controller 层 `catch (Exception e)` 后直接 `setStatus(500)`，前端只能拿到通用错误，无法定位具体行。建议返回结构化错误（含行号、原因），并在导入场景落"失败明细导出"。

---

## 五、过度设计 / 冗余

### 5.1 🟡 WorkflowApproverCacheService 的 evict 系列方法零调用（死代码）

`evictRoleCache / evictUserCache / evictDeptLeaderCache / evictAllWorkflowCache` 四个方法全模块无调用方（见 §1.2）。要么接入真实失效链路，要么删除。

### 5.2 🟡 两套角色权限缓存体系并存

`userinfo:role:permissions:{roleId}`（RoleServiceImpl）与 `userinfo:workflow:role:{roleCode}`（WorkflowApproverCacheService）+ common-auth 的 `RedisRolePermissionLoader` 缓存，三处角色相关缓存职责重叠、失效链路不一致，是缓存一致性问题（§1.2/§1.3）的根源。建议收敛为单一权限缓存 + 单一失效器。

### 5.3 🟡 两套 i18n 机制并存

`ydsz_language` 表（Language CRUD，业务"语言"数据）与 `message_*.properties`（系统异常/提示 i18n）是两套独立机制，语义易混淆。建议明确区分"业务多语言数据"与"系统消息 i18n"，避免同一概念双实现。

### 5.4 🟢 UserField 自定义字段采用率待盘点

`ydsz_user_field` 实体 + `UserFieldServiceImpl` 存在，但全模块/跨模块未见明确消费方（对标 common-util 的"零采用即过度设计"教训）。建议盘点采用率：若属规划内能力则补消费场景，否则标记 `@Deprecated` 或延后。

---

## 六、落地路线图

### P0（本迭代，正确性/安全/一致性收口）

| # | 事项 | 证据位置 | 验证方式 |
|---|---|---|---|
| 1 | 登录失败计数改原子自增（消除并发竞态） | `AuthServiceImpl#recordLoginFailure` | 并发压测 6 次失败必锁定 |
| 2 | 角色变更失效 `role:*` 缓存 + 清理死方法 | `UserAccountServiceImpl#assignRoles`、`WorkflowApproverCacheService` | 改角色后 `/list-by-role` 立即返回新名单 |
| 3 | 权限加载收敛为单一入口 + 缓存 | `DbRolePermissionLoader`、`RoleServiceImpl#getRolePermissionIds` | 网关权限判定命中缓存 |
| 4 | 登录失败统一返回（消除用户名枚举） | `AuthServiceImpl#login` | 扫描不存在/错误密码响应一致 |
| 5 | 补齐 MFA/2FA 或移除误导性声明 | 异常码、`README.md`、`UserInfoApplication` | 二选一，文档与代码对齐 |

### P1（下个迭代，能力补齐与质量）

| # | 事项 |
|---|---|
| 1 | 会话管理：`userId → Set<token>`，支持踢下线/改密全端失效 |
| 2 | 密码找回流程 + 密码过期/强制改密字段 |
| 3 | OAuth2 PKCE（S256）+ revocation + scope |
| 4 | 数据权限按角色配置（`data_scope`） |
| 5 | 异地/新设备登录检测 + MFA/告警 |
| 6 | 状态字段统一为枚举（消除魔数与 TypeHandler 桥接） |
| 7 | 核心链路单测：AuthService、PasswordPolicyValidator、缓存失效（当前 0 覆盖） |
| 8 | AuthenticationProvider SPI 语义收敛 |

### P2（性能/体验/长期治理）

| # | 事项 |
|---|---|
| 1 | IP 封禁改 Redis 滑动窗口；登录历史异步落库 |
| 2 | BCrypt 校验线程池隔离 + Argon2id 评估 |
| 3 | 菜单树缓存 + 失效；全量 list 加 LIMIT |
| 4 | batch-names 截断 500；导出异步化 |
| 5 | i18n 消息补齐中英双语 |
| 6 | 导入/导出结构化错误返回 |
| 7 | 收敛双缓存/双 i18n；盘点 UserField 采用率 |

---

## 七、关键证据位置

| 发现 | 文件位置 |
|---|---|
| 失败计数竞态 | `server/auth/AuthServiceImpl.java#recordLoginFailure` |
| 缓存失效错位 + 死方法 | `server/service/impl/UserAccountServiceImpl.java#assignRoles/#evictWorkflowCacheForUser`、`server/service/WorkflowApproverCacheService.java#evictRoleCache` |
| 权限双路径 | `server/auth/DbRolePermissionLoader.java`、`server/service/impl/RoleServiceImpl.java#getRolePermissionIds` |
| MFA 只声明未实现 | `domain/enums/UserInfoExceptionCode.java`（MFA_*）、`README.md`、`web/UserInfoApplication.java` |
| 状态字段魔数 | `domain/entity/UserAccount.java#status`、`server/auth/AuthServiceImpl.java#login` |
| PKCE 声明不符 | `web/controller/OAuth2Controller.java#authorize` |
| 菜单树无缓存 | `server/service/impl/MenuServiceImpl.java#tree` |
| batch 未截断 | `server/service/impl/UserAccountServiceImpl.java#batchUserNames` |
| 同步导出 | `server/service/impl/UserExcelServiceImpl.java#exportUsers` |
| 测试 0 覆盖 | 全模块无 `src/test`（`spring-boot-starter-test` 已引入未使用） |

---

## 八、总结

`ydsz-userinfo` 在架构分层、安全基线、跨服务解耦、可观测性上已具备企业级 IAM 的**骨架与合格下限**，优于多数同阶段自研项目。当前需要优先解决的不是"缺功能"，而是**三类收口**：

1. **并发正确性**（失败计数竞态）与 **缓存一致性**（角色变更失效错位）——这是会直接造成安全与业务错误的硬伤。
2. **声明与实现对齐**（MFA、PKCE、会话踢下线）——与用户长期关注的"前后端能力不匹配"同源，必须先做"能力诚实化"。
3. **测试零覆盖**——安全链路目前完全依赖人工回归，是最高的隐性风险，应随 P0 修复同步补单测，形成回归护栏。

按 P0 → P1 → P2 推进后，模块可对齐若依的国内 RBAC 交付标准与 Keycloak/Okta 的 IAM 能力分层，具备向多租户物理隔离、风控引擎（与 ydsz-pmis-literule 联动做登录风控规则）延伸的基础。
