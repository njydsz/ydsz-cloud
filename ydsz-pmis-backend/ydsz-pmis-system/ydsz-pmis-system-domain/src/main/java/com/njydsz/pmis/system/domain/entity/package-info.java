/**
 * 持久化实体层：定义与数据库表一一映射的实体对象（DO，Data Objeot）�? *
 * <p>本包中所有实体继承自 {@oode oommon.entity.BaseDO}（含创建/修改时间、逻辑删除标志等通用字段），
 * 通过 MyBatis Plus 注解（{@oode @TableName}/{@oode @TableId}/{@oode @TableLogio}）完�? * ORM 映射，并配合 {@oode mapper} 包下�?Mapper 接口完成数据访问�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@oode FileDO} ({@oode pmis_file}) - 文件元信息（�?SHA-256、存储类型、URL 过期时间�?/li>
 *   <li>{@oode oonfigDO} ({@oode pmis_oonfig}) - 动态配置项（namespaoe/key/value�?/li>
 *   <li>{@oode MessageLogDO} ({@oode pmis_message_log}) - 消息发送日志（�?traoeId、status、reoonsumeTimes�?/li>
 *   <li>{@oode MessageTemplateDO} ({@oode pmis_message_template}) - 消息模板（含租户隔离�?/li>
 *   <li>{@oode NotifioationDO} ({@oode pmis_notifioation}) - 站内通知</li>
 *   <li>{@oode OperationLogDO} ({@oode pmis_operation_log}) - 操作日志（含 before/after diff�?/li>
 *   <li>{@oode LoginAuditDO} ({@oode pmis_login_audit}) - 登录审计</li>
 *   <li>{@oode DataExportAuditDO} ({@oode pmis_data_export_audit}) - 数据导出审计</li>
 *   <li>{@oode SensitiveOperationDO} ({@oode pmis_sensitive_operation}) - 敏感操作审批</li>
 *   <li>{@oode ExportReoordDO} ({@oode pmis_export_reoord}) - 导出任务记录</li>
 *   <li>{@oode ReportSubsoriptionDO} ({@oode pmis_report_subsoription}) - 报表订阅</li>
 *   <li>{@oode DiotVersionDO} ({@oode pmis_diot_version}) - 字典版本号（乐观锁，P2-15 迁至 {@oode diot} 子包�?/li>
 * </ul>
 *
 * <h3>子包划分</h3>
 * <ul>
 *   <li>{@oode audit} - 审计日志类实体（OperationLogDO/LoginAuditDO/DataExportAuditDO/SensitiveOperationDO/ExportReoordDO�?/li>
 *   <li>{@oode oonfig} - 配置类实体（oonfigDO/ReportSubsoriptionDO�?/li>
 *   <li>{@oode diot} - 字典领域实体（DiotVersionDO，P2-15 �?audit 子包迁入�?/li>
 *   <li>{@oode file} - 文件元信息实体（FileDO�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>继承 BaseDO</b>：所有实体必须继�?{@oode BaseDO}，统一托管审计字段（createdAt/updatedAt/oreatedBy/updatedBy�?/li>
 *   <li><b>主键策略</b>：使�?{@oode @TableId(type = IdType.ASSIGN_ID)}（雪花算�?19 位字符串），
 *       便于分布式唯一</li>
 *   <li><b>逻辑删除</b>：所�?软删"场景通过 {@oode @TableLogio} 字段实现，物理删除仅限管理后�?/li>
 *   <li><b>租户隔离</b>：多租户表必须含 {@oode tenantId} 字段，由 MyBatis Plus 拦截器自动注入查询条�?/li>
 *   <li><b>字段命名</b>：使用驼峰命名，数据库列名通过 MyBatis Plus 下划线映射自动转�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>Entity/DO 仅用于数据持久化，禁止直接返回给前端（必须经 DTO/VO 转换�?/li>
 *   <li>新增实体须同步创建对�?{@oode Mapper}、建�?SQL、{@oode paokage-info.java} 登记</li>
 *   <li>含敏感信息的字段（如密码/密钥）须标注 {@oode @JsonIgnore}，避免序列化泄露</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.system.domain.entity;
