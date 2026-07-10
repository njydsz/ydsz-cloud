/**
 * userinfo 模块数据访问层（Mapper）包。
 *
 * <p>所有 Mapper 基于 MyBatis-Plus {@code BaseMapper} 扩展，复杂业务查询通过 {@code @Select}
 * 注解或同名 XML 文件承载。Mapper 接口不包含业务逻辑，只负责 SQL 拼装与结果集映射，事务与
 * 逻辑删除交由 MyBatis-Plus 拦截器统一处理。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>用户域：UserAccountMapper、UserRoleMapper、UserSessionMapper、User2FAMapper。</li>
 *   <li>权限域：RoleMapper、RolePermissionMapper、PermissionMapper。</li>
 *   <li>组织架构域：DepartmentMapper、EmployeeTagMapper。</li>
 *   <li>基础数据域：DictTypeMapper、DictItemMapper、RankMapper、RankRateMapper。</li>
 *   <li>资源调度域：ResourcePoolMapper、ResourceAssignmentMapper、BenchRecordMapper。</li>
 *   <li>考勤域：AttendanceMapper、LeaveMapper、OvertimeMapper。</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>继承 BaseMapper：单表 CRUD 复用 {@code BaseMapper} 提供的方法，禁止自行实现等价方法。</li>
 *   <li>复杂 SQL 外置：多表关联/动态条件 SQL 统一放在 {@code resources/mapper/userinfo/} 下 XML 文件中。</li>
 *   <li>显式租户隔离：所有查询需考虑多租户拦截器自动追加的 {@code tenant_id} 条件。</li>
 *   <li>结果集列名稳定：{@code @Select} 注解返回列名与 DO 字段名保持一致，避免手动映射。</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>Mapper 方法命名遵循 {@code selectXxxByYyy} / {@code insertXxx} / {@code updateXxxByYyy} / {@code deleteXxxByYyy} 模式。</li>
 *   <li>参数超过 1 个必须使用 {@code @Param("name")} 显式命名，便于 XML 引用。</li>
 *   <li>新增表/字段时同步更新 BaseDO、对应 DDL 脚本与权限点（若涉及权限校验）。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.userinfo.mapper;
