/**
 * userinfo 模块服务接口实现层。
 *
 * <p>{@code com.njydsz.pmis.userinfo.service} 接口的 Spring 管理实现，统一使用
 * {@code @Service} 标注，事务通过 {@code @Transactional(rollbackFor = Exception.class)}
 * 显式声明。复杂业务流程（密码升级、登录失败锁定、2FA 校验、考勤审批）通过编排
 * Mapper、Feign 客户端与引擎工具完成，并附带审计日志写入。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>UserAccountServiceImpl - 用户账号核心服务，处理登录态、密码 BCrypt 升级、登录失败锁定。</li>
 *   <li>AuthServiceImpl - 认证流程编排：图形验证码 → 用户名校验 → 密码校验 → 2FA → 颁发 Token。</li>
 *   <li>SessionServiceImpl - 会话生命周期：登录登记、心跳续期、主动/被动下线。</li>
 *   <li>TwoFactorServiceImpl - TOTP 密钥生成、备份码生成与校验、关闭 2FA。</li>
 *   <li>ReAuthServiceImpl - 二次认证 Token 颁发与有效期管理。</li>
 *   <li>PasswordScanServiceImpl - 密码健康度扫描（过期/即将过期/初始密码）聚合逻辑。</li>
 *   <li>RoleServiceImpl / PermissionServiceImpl - RBAC 模型增删改查与权限码聚合。</li>
 *   <li>DepartmentServiceImpl - 部门树构建、CRUD 与负责人维护。</li>
 *   <li>EmployeeTagServiceImpl - 标签覆盖式写入、按标签筛选候选人。</li>
 *   <li>AttendanceServiceImpl - 出勤/加班/请假登记与审批、状态统计聚合。</li>
 *   <li>ResourcePoolServiceImpl - 资源池 CRUD 与按类型/部门查询。</li>
 *   <li>ResourceAssignmentServiceImpl - 资源分配状态机推进、利用率统计。</li>
 *   <li>BenchServiceImpl - 闲置池业务动作、累计闲置成本聚合、仪表盘汇总。</li>
 *   <li>DictServiceImpl - 字典 CRUD、缓存预热与刷新。</li>
 *   <li>RankServiceImpl - 职级与生效费率查询、版本历史。</li>
 *   <li>JwtSimpleBuilder - 简化版 JWT 构造器（仅供测试/演示，生产建议使用 JwtTokenProvider）。</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>事务边界清晰：写操作必须在 {@code @Transactional} 方法内执行，跨服务调用置于事务外或使用 Saga 补偿。</li>
 *   <li>敏感数据保护：密码字段统一使用 BCrypt 编码，登录时检测到 MD5 旧格式自动升级。</li>
 *   <li>审计完整：所有变更操作应记录 {@code OperationLog}，并写入操作审计流水。</li>
 *   <li>幂等保证：基于业务主键的去重逻辑前置，避免重复写入造成脏数据。</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>实现类使用 {@code @RequiredArgsConstructor} 注入依赖，字段 {@code final}，便于测试与并发安全。</li>
 *   <li>业务校验失败统一抛出 {@code BizException(BizErrorCode, messageKey)}，由全局异常处理器翻译为 i18n 文案。</li>
 *   <li>不允许在 Service 内直接访问 {@code HttpServletRequest}，上下文参数通过方法入参显式传入。</li>
 *   <li>使用引擎工具（{@code engine} 包）完成复杂计算，Service 负责编排而非计算。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.userinfo.service.impl;
