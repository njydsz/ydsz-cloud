/**
 * 加密字段迁移工具层。
 *
 * <p>为存量数据库中的明文字段批量升级为密文（AES / 国密 SM4）提供一次性迁移工具。
 * 通过 Spring Boot CLI（{@code spring-boot:run -Dspring-boot.run.arguments=...}）执行，
 * 执行完毕通过退出码区分成功 / 失败。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.migration.EncryptedFieldMigrationService} - 迁移核心逻辑（分批 / 事务 / 断点续传）</li>
 *   <li>{@link com.njydsz.pmis.common.migration.EncryptedFieldMigrationCli}     - CLI 入口</li>
 * </ul>
 *
 * <h3>使用约束</h3>
 * <ul>
 *   <li>迁移前必须备份数据库（{@code pg_dump}）</li>
 *   <li>迁移期间禁止业务写入对应表</li>
 *   <li>迁移过程中产生的密钥轮转由 {@code SecretManager} 统一管理</li>
 *   <li>迁移完成后运行 {@code SELECT count(*)} 抽样校验</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.migration;
