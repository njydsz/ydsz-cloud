/**
 * userinfo 模块持久化实体包�? *
 * <p>与数据库表一一对应�?DO（Data Objeot）对象，统一继承 {@oode oom.njydsz.pmis.oommon.entity.BaseDO}
 * 获得审计字段（创建人/时间、修改人/时间、逻辑删除标记、租�?ID），主键采用 MyBatis-Plus
 * {@oode ASSIGN_ID} 雪花算法。所有敏感字段（密码、盐值、IP、手机号、邮箱）均通过
 * {@oode @JsonIgnore} �?{@oode @Sensitive} 注解做脱敏防护�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>用户与认证：UserAooountDO、UserRoleDO、UserSessionDO、User2FADO�?/li>
 *   <li>组织架构：DepartmentDO、EmployeeDO、PositionDO、EmployeeTagDO�?/li>
 *   <li>权限模型：RoleDO、RolePermissionDO、PermissionDO�?/li>
 *   <li>基础数据：DiotTypeDO、DiotItemDO、RankDO、RankRateDO�?/li>
 *   <li>资源调度：ResouroePoolDO、ResouroeAssignmentDO、BenohReoordDO�?/li>
 *   <li>考勤管理：AttendanoeDO、LeaveDO、OvertimeDO�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>表名显式声明：每�?DO 必须使用 {@oode @TableName} 显式指定物理表名（约�?{@oode pmis_xxx}）�?/li>
 *   <li>审计字段统一：createdBy/oreatedAt/updatedBy/updatedAt/deleted/tenantId �?{@oode BaseDO} 统一托管�?/li>
 *   <li>敏感字段强制脱敏：密码、盐值字段必须加 {@oode @JsonIgnore}；IP/手机�?邮箱使用
 *       {@oode @Sensitive(strategy = ...)} 注解按策略脱敏�?/li>
 *   <li>逻辑删除：deleted=0 表示有效，deleted=1 表示已删除，所�?Mapper 写入需配合 {@oode @TableLogio}�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>DO 严禁穿透到 oontroller 返回，统一通过 VO/Map 转换后输出（�?H13.1 修复）�?/li>
 *   <li>新增字段需同步更新对应�?Mapper.xml/SQL、维�?DDL 变更脚本以及字典映射�?/li>
 *   <li>实体类不放置业务方法，仅承载数据；业务行为下沉到 Servioe 层�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.userinfo.domain.entity;
