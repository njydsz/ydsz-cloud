/**
 * 持久化实体层：定义与数据库表一一映射的实体对象（DO，Data Object）。
 *
 * <p>本包中所有实体继承自 {@code common.entity.BaseDO}（含创建/修改时间、逻辑删除标志等通用字段），
 * 通过 MyBatis Plus 注解（{@code @TableName}/{@code @TableId}/{@code @TableLogic}）完成
 * ORM 映射，并配合 {@code mapper} 包下的 Mapper 接口完成数据访问。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@code FileDO} ({@code pmis_file}) - 文件元信息（含 SHA-256、存储类型、URL 过期时间）</li>
 *   <li>{@code ConfigDO} ({@code pmis_config}) - 动态配置项（namespace/key/value）</li>
 *   <li>{@code MessageLogDO} ({@code pmis_message_log}) - 消息发送日志（含 traceId、status、reconsumeTimes）</li>
 *   <li>{@code MessageTemplateDO} ({@code pmis_message_template}) - 消息模板（含租户隔离）</li>
 *   <li>{@code NotificationDO} ({@code pmis_notification}) - 站内通知</li>
 *   <li>{@code OperationLogDO} ({@code pmis_operation_log}) - 操作日志（含 before/after diff）</li>
 *   <li>{@code LoginAuditDO} ({@code pmis_login_audit}) - 登录审计</li>
 *   <li>{@code DataExportAuditDO} ({@code pmis_data_export_audit}) - 数据导出审计</li>
 *   <li>{@code SensitiveOperationDO} ({@code pmis_sensitive_operation}) - 敏感操作审批</li>
 *   <li>{@code ExportRecordDO} ({@code pmis_export_record}) - 导出任务记录</li>
 *   <li>{@code ReportSubscriptionDO} ({@code pmis_report_subscription}) - 报表订阅</li>
 *   <li>{@code DictVersionDO} ({@code pmis_dict_version}) - 字典版本号（乐观锁，P2-15 迁至 {@code dict} 子包）</li>
 * </ul>
 *
 * <h3>子包划分</h3>
 * <ul>
 *   <li>{@code audit} - 审计日志类实体（OperationLogDO/LoginAuditDO/DataExportAuditDO/SensitiveOperationDO/ExportRecordDO）</li>
 *   <li>{@code config} - 配置类实体（ConfigDO/ReportSubscriptionDO）</li>
 *   <li>{@code dict} - 字典领域实体（DictVersionDO，P2-15 从 audit 子包迁入）</li>
 *   <li>{@code file} - 文件元信息实体（FileDO）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>继承 BaseDO</b>：所有实体必须继承 {@code BaseDO}，统一托管审计字段（createdAt/updatedAt/createdBy/updatedBy）</li>
 *   <li><b>主键策略</b>：使用 {@code @TableId(type = IdType.ASSIGN_ID)}（雪花算法 19 位字符串），
 *       便于分布式唯一</li>
 *   <li><b>逻辑删除</b>：所有"软删"场景通过 {@code @TableLogic} 字段实现，物理删除仅限管理后台</li>
 *   <li><b>租户隔离</b>：多租户表必须含 {@code tenantId} 字段，由 MyBatis Plus 拦截器自动注入查询条件</li>
 *   <li><b>字段命名</b>：使用驼峰命名，数据库列名通过 MyBatis Plus 下划线映射自动转换</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>Entity/DO 仅用于数据持久化，禁止直接返回给前端（必须经 DTO/VO 转换）</li>
 *   <li>新增实体须同步创建对应 {@code Mapper}、建表 SQL、{@code package-info.java} 登记</li>
 *   <li>含敏感信息的字段（如密码/密钥）须标注 {@code @JsonIgnore}，避免序列化泄露</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.system.entity;
