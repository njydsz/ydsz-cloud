package com.njydsz.common.seata.api;

/**
 * TCC 事务日志数据库方言提供者接口
 *
 * <p>用于适配不同关系型数据库的 UPSERT 语法差异，解决以下兼容性问题：
 * <ul>
 *   <li><b>MySQL</b>：{@code INSERT ... ON DUPLICATE KEY UPDATE}</li>
 *   <li><b>PostgreSQL 9.5+</b>：{@code INSERT ... ON CONFLICT (columns) DO UPDATE SET}</li>
 *   <li><b>H2</b>：{@code MERGE INTO ... KEY (columns)}</li>
 *   <li><b>Oracle/MSSQL</b>：{@code MERGE INTO ... WHEN MATCHED THEN UPDATE ... WHEN NOT MATCHED THEN INSERT}</li>
 * </ul>
 *
 * <p><b>P0-3 修复</b>：原 MySQL 专有语法与"兼容 PG"声明矛盾，引入方言提供者接口解耦 SQL 差异。
 *
 * <p>使用方式：
 * <pre>{@code
 * // 根据数据源类型选择合适的方言提供者
 * dialectProvider = databaseProductName.contains("postgresql")
 *     ? new PostgresqlDialectProvider(tableName)
 *     : new MysqlDialectProvider(tableName);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.3.0
 */
public interface TccTransactionDialectProvider {

    /**
     * 获取 UPSERT 操作的 SQL 语句
     *
     * <p>该 SQL 实现"存在则更新，不存在则插入"语义，保证 TCC 事务日志的幂等写入。
     * XID + branch_id 构成唯一键约束。
     *
     * <p>参数绑定顺序：
     * <ol>
     *   <li>xid (VARCHAR)</li>
     *   <li>branch_id (VARCHAR)</li>
     *   <li>transaction_name (VARCHAR)</li>
     *   <li>status (VARCHAR)</li>
     *   <li>context_snapshot (TEXT)</li>
     *   <li>try_started_at (TIMESTAMP)</li>
     *   <li>try_completed_at (TIMESTAMP)</li>
     *   <li>finished_at (TIMESTAMP)</li>
     *   <li>retry_count (INT)</li>
     *   <li>last_error (VARCHAR)</li>
     * </ol>
     *
     * @return UPSERT 操作的预编译 SQL
     */
    String getUpsertSql();

    /**
     * 获取更新状态的 SQL 语句
     *
     * <p>参数绑定顺序：
     * <ol>
     *   <li>status (VARCHAR) - 新状态</li>
     *   <li>finished_at (TIMESTAMP) - 完成时间，终态时填充</li>
     *   <li>xid (VARCHAR)</li>
     *   <li>branch_id (VARCHAR)</li>
     * </ol>
     *
     * @return 更新状态的预编译 SQL
     */
    String getUpdateStatusSql();

    /**
     * 获取根据 XID 和 branch_id 查询的 SQL
     *
     * <p>查询列顺序：xid, branch_id, transaction_name, status, context_snapshot,
     * try_started_at, try_completed_at, finished_at, retry_count, last_error
     *
     * 参数绑定顺序：
     * <ol>
     *   <li>xid (VARCHAR)</li>
     *   <li>branch_id (VARCHAR)</li>
     * </ol>
     *
     * @return 按主键查询的 SQL
     */
    String getFindByXidBranchSql();

    /**
     * 获取查询超时未完成事务的 SQL（分页版本，P1-2 新增）
     *
     * <p>参数绑定顺序：
     * <ol>
     *   <li>status (VARCHAR) - 应为 TRIED</li>
     *   <li>try_completed_at (TIMESTAMP) - 超时阈值</li>
     * </ol>
     *
     * @return 查询超时未完成事务的 SQL（无 LIMIT 分页）
     */
    String getFindTimeoutPendingSql();

    /**
     * 获取分页查询超时未完成事务的 SQL（P1-2 新增）
     *
     * <p>参数绑定顺序（以 MySQL 为例）：
     * <ol>
     *   <li>status (VARCHAR) - 应为 TRIED</li>
     *   <li>try_completed_at (TIMESTAMP) - 超时阈值</li>
     *   <li>limit (INT) - 单次返回最大记录数</li>
     * </ol>
     *
     * @return 带回积分页的查询 SQL
     */
    String getFindTimeoutPendingPagedSql();

    /**
     * 获取统计超时未完成事务数量的 SQL
     *
     * <p>参数绑定顺序：
     * <ol>
     *   <li>status (VARCHAR) - 应为 TRIED</li>
     *   <li>try_completed_at (TIMESTAMP) - 超时阈值</li>
     * </ol>
     *
     * @return 统计超时未完成事务数量的 SQL
     */
    String getCountTimeoutPendingSql();

    /**
     * 获取删除事务日志的 SQL
     *
     * <p>参数绑定顺序：
     * <ol>
     *   <li>xid (VARCHAR)</li>
     *   <li>branch_id (VARCHAR)</li>
     * </ol>
     *
     * @return 删除事务日志的 SQL
     */
    String getDeleteSql();
}
