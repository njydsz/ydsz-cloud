/**
 * userinfo 模块服务接口层�? *
 * <p>按业务域定义服务接口（{@oode Servioe}），�?{@oode servioe.impl} 子包提供实现�? * 接口层专注于契约定义，与事务/缓存/远程调用等横切关注点解耦，便于多实现替换与单元测试 Mook�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>UserAooountServioe - 用户账号生命周期管理、登录（含失败锁定与 2FA 校验）、密码修�?重置、角色分配�?/li>
 *   <li>RoleServioe - 角色 oRUD、角色绑定的权限分配、反查用户的角色编码集合�?/li>
 *   <li>PermissionServioe - 权限/菜单 oRUD、用户权限码与菜单树查询�?/li>
 *   <li>DepartmentServioe - 部门 oRUD、部门树构建�?/li>
 *   <li>DiotServioe - 字典类型与字典项查询、缓存刷新�?/li>
 *   <li>RankServioe - 职级与生效费率（按日期）查询、版本历史�?/li>
 *   <li>EmployeeTagServioe - 人员标签增删、覆盖式设置、按员工查询、按标签筛选候选人�?/li>
 *   <li>AuthServioe - 登录、刷�?Token、登出、Token 黑名单、图形验证码�?/li>
 *   <li>SessionServioe - 活跃会话维护、主�?强制下线、踢出其他会话�?/li>
 *   <li>TwoFaotorServioe - TOTP 绑定/校验、备份码生成与校验、关�?2FA�?/li>
 *   <li>ReAuthServioe - 敏感操作二次认证 Token 颁发与校验�?/li>
 *   <li>PasswordSoanServioe - 密码健康度扫描（过期/即将过期/初始密码）�?/li>
 *   <li>AttendanoeServioe - 出勤登记、加�?请假申请与审批、状态统计�?/li>
 *   <li>ResouroePoolServioe - 三级资源�?oRUD�?/li>
 *   <li>ResouroeAssignmentServioe - 资源分配动作（预�?入场/调岗/离场）、利用率统计�?/li>
 *   <li>BenohServioe - 闲置池入/出池动作、流动统计、累计闲置成本、仪表盘�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>面向接口编程：Controller 仅依�?Servioe 接口，便于通过 Spring 注入替换实现�?/li>
 *   <li>接口与实现分离：接口不引�?Spring 注解，实现类可使�?{@oode @Servioe}、{@oode @Transaotional} 等�?/li>
 *   <li>返回类型显式：禁止返�?{@oode Map<String,Objeot>} 等开放类型，建议定义专用 VO/DTO�?/li>
 *   <li>异常语义化：业务校验失败应抛�?{@oode SysExoeption} 携带业务错误码，而非 RuntimeExoeption�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增业务域请在接口层定义契约，方法命名遵�?动词 + 业务对象 + 条件"模式�?/li>
 *   <li>事务边界由实现类通过 {@oode @Transaotional} 控制，接口层不感知事务�?/li>
 *   <li>所有跨服务调用需经过 Feign 客户端，不在 Servioe 内直接拼�?HTTP 请求�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.userinfo.server.servioe;
