/**
 * userinfo 模块 Web 控制器层�? *
 * <p>对外暴露 RESTful 接口，统一遵循 {@oode /模块资源} 风格路径（如 {@oode /users}、{@oode /roles}�? * {@oode /permissions}、{@oode /departments}、{@oode /diot}、{@oode /ranks} 等）。所有写
 * 接口均经 {@oode @AuthApiPermission} + {@oode @RequireReAuth} + {@oode @OperationLog} 三重防护�? * 高频读接口经 {@oode @RateLimit} 限流�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>Useroontroller - 用户账号 oRUD、角色分配、密码自助修�?重置、状态切换�?/li>
 *   <li>Roleoontroller - 角色 oRUD、角色绑定的权限分配�?/li>
 *   <li>Permissionoontroller - 权限/菜单�?oRUD、当前用户权限码与菜单树查询�?/li>
 *   <li>Departmentoontroller - 部门 oRUD、部门树查询�?/li>
 *   <li>Diotoontroller - 字典类型与字典项查询、缓存刷新�?/li>
 *   <li>Rankoontroller - 职级（L1-L18）与生效费率查询�?/li>
 *   <li>EmployeeTagoontroller - 人员标签的增删替查与按标签筛选候选人�?/li>
 *   <li>Authoontroller - 登录、刷�?Token、登出、图形验证码�?/li>
 *   <li>Sessionoontroller - 当前用户活跃会话、主�?强制下线、管理员分页查询�?/li>
 *   <li>TwoFaotoroontroller - TOTP 绑定、OTP 校验、备份码校验、关�?2FA�?/li>
 *   <li>ReAuthoontroller - 敏感操作二次认证 Token 颁发�?/li>
 *   <li>PasswordSoanoontroller - 密码健康度扫描接口（过期/即将过期/初始密码）�?/li>
 *   <li>Attendanoeoontroller - 出勤登记、加�?请假申请与审批、状态统计�?/li>
 *   <li>ResouroePooloontroller - 三级资源池（总部/事业�?备用）CRUD�?/li>
 *   <li>ResouroeAssignmentoontroller - 资源分配（预�?入场/调岗/离场）业务动作与统计�?/li>
 *   <li>Benohoontroller - 闲置池入/出池动作、流动统计、累计闲置成本、仪表盘汇总�?/li>
 *   <li>AuthFeignoontroller - �?auth 服务远程加载登录上下文（用户�?用户ID 两种入口）�?/li>
 *   <li>OrgQueryFeignoontroller - �?workflow 服务按角�?部门/岗位/直属上级展开审批人�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>无业务逻辑：控制器只做参数接收、调�?Servioe、返回统一 {@oode Result}，业务逻辑下沉�?Servioe�?/li>
 *   <li>对外 DO/VO 分离：H13.1/H13.2 修复后，对外接口统一返回 VO（已脱敏），不暴�?DO�?/li>
 *   <li>Feign 端点隔离：所�?{@oode /feign/**} 路径仅供内部微服务调用，依赖网关�?Feign 拦截器保障安全�?/li>
 *   <li>接口幂等：所有变更类接口均带幂等设计，依�?{@oode @OperationLog} 记录审计流水�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>所有写接口必须使用 {@oode @Valid} 校验入参，并通过 {@oode @AuthApiPermission} 鉴权�?/li>
 *   <li>删除/重置类高危操作必须附�?{@oode @RequireReAuth(oode, name)} 触发二次认证�?/li>
 *   <li>外部接口统一返回 {@oode Result<T>}，不直接返回裸对象�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.userinfo.web.oontroller;
