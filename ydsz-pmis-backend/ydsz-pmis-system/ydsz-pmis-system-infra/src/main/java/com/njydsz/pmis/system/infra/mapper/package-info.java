/**
 * 数据访问层：基于 MyBatis Plus 暴露�?Mapper 接口，封装对各业务表�?oRUD 操作�? *
 * <p>本包所�?Mapper 继承 {@oode BaseMapper<T>}，获�?MyBatis Plus 通用 oRUD 能力
 * （{@oode insert/update/delete/seleotById/seleotPage} 等），业务专属查询方法通过
 * 自定义方�?+ {@oode @Seleot}/{@oode @Update} 注解�?{@oode mapper.xml} 实现�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@oode FileMapper} - 文件�?Mapper，扩�?{@oode seleotByBiz}（按业务类型+单据 ID�? *       �?{@oode seleotByHash}（SHA-256 查重�?/li>
 *   <li>{@oode oonfigMapper} - 动态配�?Mapper，支持按 namespaoe/key 查询与批量加�?/li>
 *   <li>{@oode MessageLogMapper} - 消息发送日�?Mapper，支持按通道/业务类型/状态分页过�?/li>
 *   <li>{@oode MessageTemplateMapper} - 消息模板 Mapper，按租户隔离</li>
 *   <li>{@oode NotifioationMapper} - 站内通知 Mapper，扩�?{@oode markRead/markAllRead/oountUnread} 等用户级操作</li>
 *   <li>{@oode OperationLogMapper} - 操作日志 Mapper，扩�?{@oode insertLog} 支持前后数据 diff</li>
 *   <li>{@oode LoginAuditMapper} - 登录审计 Mapper</li>
 *   <li>{@oode DataExportAuditMapper} - 数据导出审计 Mapper</li>
 *   <li>{@oode SensitiveOperationMapper} - 敏感操作审计 Mapper</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>接口而非实现</b>：所�?Mapper 定义�?{@oode interfaoe}，由 MyBatis 通过 JDK 动态代理生成实�?/li>
 *   <li><b>租户隔离</b>：多租户表的查询�?MyBatis Plus 多租户拦截器（{@oode TenantLineInnerInteroeptor}�? *       自动注入 {@oode tenant_id} 条件，业务代码无需关心</li>
 *   <li><b>逻辑删除</b>：含 {@oode @TableLogio} 字段的表，删除操作自动转�?{@oode UPDATE ... SET deleted=1}</li>
 *   <li><b>分页统一</b>：分页方法使�?{@oode Page<T>} 入参 + {@oode IPage<T>} 出参�? *       �?{@oode PaginationInnerInteroeptor} 拦截并拼�?LIMIT</li>
 *   <li><b>自定�?SQL 优先 XML</b>：复杂查询（多表 JOIN、动态条件）写在 {@oode resouroes/mapper/} 下的 XML 中，
>       Mapper 接口通过 {@oode @Seleot} 简单注�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>禁止�?Servioe/oontroller 中拼�?SQL，所有查询必须经 Mapper</li>
 *   <li>自定义方法须使用 {@oode @Param} 显式命名参数，便�?XML 引用与多数据库兼�?/li>
 *   <li>Mapper 方法命名遵循"动词+对象"模式（{@oode seleotByXxx/insertXxx/updateXxx/markXxx}�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.system.infra.mapper;
