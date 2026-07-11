/**
 * 数据访问层：基于 MyBatis Plus 暴露的 Mapper 接口，封装对各业务表的 CRUD 操作。
 *
 * <p>本包所有 Mapper 继承 {@code BaseMapper<T>}，获得 MyBatis Plus 通用 CRUD 能力
 * （{@code insert/update/delete/selectById/selectPage} 等），业务专属查询方法通过
 * 自定义方法 + {@code @Select}/{@code @Update} 注解或 {@code mapper.xml} 实现。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@code FileMapper} - 文件表 Mapper，扩展 {@code selectByBiz}（按业务类型+单据 ID）
 *       与 {@code selectByHash}（SHA-256 查重）</li>
 *   <li>{@code ConfigMapper} - 动态配置 Mapper，支持按 namespace/key 查询与批量加载</li>
 *   <li>{@code MessageLogMapper} - 消息发送日志 Mapper，支持按通道/业务类型/状态分页过滤</li>
 *   <li>{@code MessageTemplateMapper} - 消息模板 Mapper，按租户隔离</li>
 *   <li>{@code NotificationMapper} - 站内通知 Mapper，扩展 {@code markRead/markAllRead/countUnread} 等用户级操作</li>
 *   <li>{@code OperationLogMapper} - 操作日志 Mapper，扩展 {@code insertLog} 支持前后数据 diff</li>
 *   <li>{@code LoginAuditMapper} - 登录审计 Mapper</li>
 *   <li>{@code DataExportAuditMapper} - 数据导出审计 Mapper</li>
 *   <li>{@code SensitiveOperationMapper} - 敏感操作审计 Mapper</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>接口而非实现</b>：所有 Mapper 定义为 {@code interface}，由 MyBatis 通过 JDK 动态代理生成实现</li>
 *   <li><b>租户隔离</b>：多租户表的查询由 MyBatis Plus 多租户拦截器（{@code TenantLineInnerInterceptor}）
 *       自动注入 {@code tenant_id} 条件，业务代码无需关心</li>
 *   <li><b>逻辑删除</b>：含 {@code @TableLogic} 字段的表，删除操作自动转为 {@code UPDATE ... SET deleted=1}</li>
 *   <li><b>分页统一</b>：分页方法使用 {@code Page<T>} 入参 + {@code IPage<T>} 出参，
 *       由 {@code PaginationInnerInterceptor} 拦截并拼接 LIMIT</li>
 *   <li><b>自定义 SQL 优先 XML</b>：复杂查询（多表 JOIN、动态条件）写在 {@code resources/mapper/} 下的 XML 中，
>       Mapper 接口通过 {@code @Select} 简单注解</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>禁止在 Service/Controller 中拼接 SQL，所有查询必须经 Mapper</li>
 *   <li>自定义方法须使用 {@code @Param} 显式命名参数，便于 XML 引用与多数据库兼容</li>
 *   <li>Mapper 方法命名遵循"动词+对象"模式（{@code selectByXxx/insertXxx/updateXxx/markXxx}）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.system.infra.mapper;
