/**
 * userinfo 模块 Web 控制器层。
 *
 * <p>对外暴露 RESTful 接口，统一遵循 {@code /模块资源} 风格路径（如 {@code /users}、{@code /roles}、
 * {@code /permissions}、{@code /departments}、{@code /dict}、{@code /ranks} 等）。所有写
 * 接口均经 {@code @AuthApiPermission} + {@code @OperationLog} 三重防护，
 * 高频读接口经 {@code @RateLimit} 限流。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>UserController - 用户账号 CRUD、角色分配、密码自助修改/重置、状态切换。</li>
 *   <li>RoleController - 角色 CRUD、角色绑定的权限分配。</li>
 *   <li>PermissionController - 权限/菜单树 CRUD、当前用户权限码与菜单树查询。</li>
 *   <li>DepartmentController - 部门 CRUD、部门树查询。</li>
 *   <li>DictController - 字典类型与字典项查询、缓存刷新。</li>
 *   <li>RankController - 职级（L1-L18）与生效费率查询。</li>
 *   <li>EmployeeTagController - 人员标签的增删替查与按标签筛选候选人。</li>
 *   <li>AuthController - 登录、刷新 Token、登出、图形验证码。</li>
 *   <li>SessionController - 当前用户活跃会话、主动/强制下线、管理员分页查询。</li>
 *   <li>TwoFactorController - TOTP 绑定、OTP 校验、备份码校验、关闭 2FA。</li>
 *   <li>PasswordScanController - 密码健康度扫描接口（过期/即将过期/初始密码）。</li>
 *   <li>AttendanceController - 出勤登记、加班/请假申请与审批、状态统计。</li>
 *   <li>ResourcePoolController - 三级资源池（总部/事业部/备用）CRUD。</li>
 *   <li>ResourceAssignmentController - 资源分配（预占/入场/调岗/离场）业务动作与统计。</li>
 *   <li>BenchController - 闲置池入/出池动作、流动统计、累计闲置成本、仪表盘汇总。</li>
 *   <li>AuthFeignController - 供 auth 服务远程加载登录上下文（用户名/用户ID 两种入口）。</li>
 *   <li>OrgQueryFeignController - 供 workflow 服务按角色/部门/岗位/直属上级展开审批人。</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>无业务逻辑：控制器只做参数接收、调用 Service、返回统一 {@code Result}，业务逻辑下沉到 Service。</li>
 *   <li>对外 DO/VO 分离：H13.1/H13.2 修复后，对外接口统一返回 VO（已脱敏），不暴露 DO。</li>
 *   <li>Feign 端点隔离：所有 {@code /feign/**} 路径仅供内部微服务调用，依赖网关与 Feign 拦截器保障安全。</li>
 *   <li>接口幂等：所有变更类接口均带幂等设计，依赖 {@code @OperationLog} 记录审计流水。</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>所有写接口必须使用 {@code @Valid} 校验入参，并通过 {@code @AuthApiPermission} 鉴权。</li>
 *   <li>外部接口统一返回 {@code Result<T>}，不直接返回裸对象。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.userinfo.web.controller;
