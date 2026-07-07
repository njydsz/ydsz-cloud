package com.njydsz.pmis.common.migration;

import com.njydsz.pmis.common.sensitive.EncryptedFieldKeyRegistry;
import com.njydsz.pmis.common.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * EncryptedField 历史数据回填迁移服务
 *
 * <p>批次18 / P3-4: 解决历史明文数据回填到密文列的问题。
 *
 * <p>使用流程:
 * <pre>
 *   1. 维护窗口执行 SQL 脚本: encrypted_field_migration.sql
 *   2. 应用启动时调用 {@link #encryptAll(MigrationOptions)} 加密所有明文
 *   3. 校验 {@link #verifyAll(String, int)}
 *   4. 切换列名: encrypted_field_migration_switch.sql
 * </pre>
 *
 * <p>设计要点:
 * <ul>
 *   <li>分批读取 + 批量 UPDATE, 避免大事务锁表 (默认 500 行/批)</li>
 *   <li>每批独立事务, 单批失败不影响历史批次</li>
 *   <li>使用 {@link CryptoUtil#aesGcmEncrypt} 加密, 保持与 {@code EncryptedFieldSerializer} 一致</li>
 *   <li>幂等: 密文列已非空的记录跳过 (避免重复加密覆盖)</li>
 *   <li>所有阶段写入 {@code pmis_migration_log}, 便于审计</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class EncryptedFieldMigrationService {

    /**
     * 默认迁移列配置 (P3-4 仅覆盖 pmis_user_account)
     */
    public static final List<MigrationColumn> DEFAULT_COLUMNS = List.of(
            new MigrationColumn("pmis_user_account", "id_card",   "id_card_cipher",   "pmis.crypto.aes-key"),
            new MigrationColumn("pmis_user_account", "phone",     "phone_cipher",     "pmis.crypto.aes-key"),
            new MigrationColumn("pmis_user_account", "email",     "email_cipher",     "pmis.crypto.aes-key"),
            new MigrationColumn("pmis_user_account", "bank_card", "bank_card_cipher", "pmis.crypto.aes-key"),
            new MigrationColumn("pmis_user_account", "address",   "address_cipher",   "pmis.crypto.aes-key")
    );

    /** 数据源（由 Spring 容器或 fromJdbcUrl 静态工厂注入） */
    private final DataSource dataSource;

    /**
     * @param dataSource JDBC 数据源
     */
    public EncryptedFieldMigrationService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 加密所有 DEFAULT_COLUMNS 列的明文数据
     *
     * @param options 迁移选项 (batchCode/batchSize/keyRef)
     * @return 各列处理结果
     */
    public List<MigrationResult> encryptAll(MigrationOptions options) throws SQLException {
        if (options == null) options = MigrationOptions.defaults();
        if (options.columns == null || options.columns.isEmpty()) {
            options.columns = DEFAULT_COLUMNS;
        }
        // 1) 注入密钥
        registerKeyIfNeeded(options);

        List<MigrationResult> results = new ArrayList<>();
        for (MigrationColumn col : options.columns) {
            MigrationResult r = encryptColumn(options.batchCode, col, options.batchSize);
            results.add(r);
        }
        return results;
    }

    /**
     * 解密校验: 抽样 N 行, 解密 *_cipher 与备份表对比
     *
     * @param batchCode  批次号
     * @param sampleSize 抽样行数 (每列)
     */
    public List<VerifyResult> verifyAll(String batchCode, int sampleSize) throws SQLException {
        if (!StringUtils.hasText(batchCode)) {
            throw new IllegalArgumentException("batchCode 不能为空");
        }
        if (sampleSize <= 0) sampleSize = 100;

        List<VerifyResult> results = new ArrayList<>();
        for (MigrationColumn col : DEFAULT_COLUMNS) {
            VerifyResult r = verifyColumn(batchCode, col, sampleSize);
            results.add(r);
        }
        return results;
    }

    // ==================== 单列加密 ====================

    /**
     * 加密单列：分批读取明文 → 跳过空值/已加密行 → 加密写回密文列，每批独立事务
     *
     * @param batchCode 迁移批次号
     * @param col       迁移列配置
     * @param batchSize 每批行数
     * @return 该列的迁移结果（成功/跳过/失败计数 + 耗时）
     * @throws SQLException SQL 执行异常
     */
    private MigrationResult encryptColumn(String batchCode, MigrationColumn col, int batchSize) throws SQLException {
        long start = System.currentTimeMillis();
        long logId = startLog(batchCode, "ENCRYPT", col.table, col.plainColumn);
        AtomicLong success = new AtomicLong(0);
        AtomicLong skipped = new AtomicLong(0);
        AtomicLong failed = new AtomicLong(0);
        AtomicLong processed = new AtomicLong(0);

        log.info("[EncryptedField] 开始加密 {}.{} -> {}.{}",
                col.table, col.plainColumn, col.table, col.cipherColumn);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int offset = 0;
                while (true) {
                    List<RowData> batch = readPlainBatch(conn, col, offset, batchSize);
                    if (batch.isEmpty()) break;
                    for (RowData row : batch) {
                        try {
                            if (isBlank(row.plainValue)) {
                                // 明文为空, 跳过
                                skipped.incrementAndGet();
                                continue;
                            }
                            // 幂等: 密文列已非空则跳过
                            if (!isBlank(row.cipherValue)) {
                                skipped.incrementAndGet();
                                continue;
                            }
                            String cipher = CryptoUtil.aesGcmEncrypt(row.plainValue, EncryptedFieldKeyRegistry.get(col.keyRef));
                            updateCipher(conn, col, row.id, cipher);
                            success.incrementAndGet();
                        } catch (Exception e) {
                            failed.incrementAndGet();
                            log.error("[EncryptedField] 加密失败 id={} col={} err={}",
                                    row.id, col.plainColumn, e.getMessage());
                        }
                    }
                    conn.commit();
                    processed.addAndGet(batch.size());
                    offset += batch.size();
                    log.info("[EncryptedField] {}.{} 进度: processed={} success={} skipped={} failed={}",
                            col.table, col.plainColumn, processed.get(), success.get(), skipped.get(), failed.get());
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }

        long cost = System.currentTimeMillis() - start;
        finishLog(logId, success.get() + skipped.get() + failed.get(), "SUCCESS",
                String.format("success=%d skipped=%d failed=%d cost=%dms", success.get(), skipped.get(), failed.get(), cost));

        log.info("[EncryptedField] 完成 {}.{} success={} skipped={} failed={} cost={}ms",
                col.table, col.plainColumn, success.get(), skipped.get(), failed.get(), cost);

        return new MigrationResult(col, success.get(), skipped.get(), failed.get(), cost);
    }

    // ==================== 校验 ====================

    /**
     * 校验单列：抽样 N 行明文与解密结果对比，统计匹配/不匹配数量
     *
     * @param batchCode  迁移批次号
     * @param col        迁移列配置
     * @param sampleSize 抽样行数
     * @return 该列的校验结果
     * @throws SQLException SQL 执行异常
     */
    private VerifyResult verifyColumn(String batchCode, MigrationColumn col, int sampleSize) throws SQLException {
        long start = System.currentTimeMillis();
        long logId = startLog(batchCode, "VERIFY", col.table, col.cipherColumn);

        long match = 0, mismatch = 0, sample = 0;
        try (Connection conn = dataSource.getConnection()) {
            String sql = String.format(
                    "SELECT t.id, t.\"%s\" AS plain, c.\"%s\" AS cipher " +
                    "FROM %s t " +
                    "LEFT JOIN %s c ON t.id = c.id " +
                    "WHERE t.\"%s\" IS NOT NULL AND t.\"%s\" <> '' " +
                    "AND c.\"%s\" IS NOT NULL AND c.\"%s\" <> '' " +
                    "ORDER BY t.id LIMIT ?",
                    col.plainColumn, col.cipherColumn,
                    col.table, col.table,
                    col.plainColumn, col.plainColumn,
                    col.cipherColumn, col.cipherColumn
            );
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, sampleSize);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        sample++;
                        String plain = rs.getString("plain");
                        String cipher = rs.getString("cipher");
                        try {
                            String decrypted = CryptoUtil.aesGcmDecrypt(cipher, EncryptedFieldKeyRegistry.get(col.keyRef));
                            if (decrypted != null && decrypted.equals(plain)) {
                                match++;
                            } else {
                                mismatch++;
                                log.warn("[EncryptedField] 校验不一致 id={} plain='{}' decrypted='{}'",
                                        rs.getLong("id"), plain, decrypted);
                            }
                        } catch (Exception e) {
                            mismatch++;
                            log.error("[EncryptedField] 解密失败 id={} cipher='{}...' err={}",
                                    rs.getLong("id"), cipher == null ? "null" : cipher.substring(0, Math.min(20, cipher.length())), e.getMessage());
                        }
                    }
                }
            }
        }

        long cost = System.currentTimeMillis() - start;
        boolean allOk = mismatch == 0;
        finishLog(logId, sample, allOk ? "SUCCESS" : "FAILED",
                String.format("sample=%d match=%d mismatch=%d cost=%dms", sample, match, mismatch, cost));
        return new VerifyResult(col, sample, match, mismatch, cost);
    }

    // ==================== SQL Helper ====================

    /**
     * 分页读取一批明文/密文行（用于 encryptColumn 逐批处理）
     *
     * @param conn   数据库连接
     * @param col    迁移列配置
     * @param offset 偏移量
     * @param limit  每批行数
     * @return 当前批次的行数据列表
     * @throws SQLException SQL 执行异常
     */
    private List<RowData> readPlainBatch(Connection conn, MigrationColumn col, int offset, int limit) throws SQLException {
        List<RowData> out = new ArrayList<>();
        String sql = String.format(
                "SELECT t.id, t.\"%s\" AS plain, c.\"%s\" AS cipher " +
                "FROM %s t " +
                "LEFT JOIN %s c ON t.id = c.id " +
                "ORDER BY t.id LIMIT ? OFFSET ?",
                col.plainColumn, col.cipherColumn,
                col.table, col.table
        );
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RowData r = new RowData();
                    r.id = rs.getLong("id");
                    r.plainValue = rs.getString("plain");
                    r.cipherValue = rs.getString("cipher");
                    out.add(r);
                }
            }
        }
        return out;
    }

    /**
     * 更新指定行的密文列
     *
     * @param conn   数据库连接
     * @param col    迁移列配置
     * @param id     行主键
     * @param cipher 密文值
     * @throws SQLException SQL 执行异常
     */
    private void updateCipher(Connection conn, MigrationColumn col, String id, String cipher) throws SQLException {
        String sql = String.format("UPDATE %s SET \"%s\" = ? WHERE id = ?", col.table, col.cipherColumn);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cipher);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /**
     * 在 pmis_migration_log 表插入一条 RUNNING 记录，返回自增主键
     *
     * @param batchCode 迁移批次号，为空时返回 -1（不记录日志）
     * @param phase     阶段（ENCRYPT / VERIFY）
     * @param table     表名
     * @param column    列名
     * @return 日志记录主键；未记录时返回 -1
     * @throws SQLException SQL 执行异常
     */
    private long startLog(String batchCode, String phase, String table, String column) throws SQLException {
        if (!StringUtils.hasText(batchCode)) return -1;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO pmis_migration_log(batch_code, phase, table_name, column_name, status) VALUES (?, ?, ?, ?, 'RUNNING')",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, batchCode);
            ps.setString(2, phase);
            ps.setString(3, table);
            ps.setString(4, column);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        }
        return -1;
    }

    /**
     * 更新迁移日志记录为最终状态（SUCCESS / FAILED），写入影响行数与备注
     *
     * @param logId    日志主键，小于 0 时直接返回
     * @param affected 影响行数
     * @param status   最终状态
     * @param remark   备注信息
     */
    private void finishLog(long logId, long affected, String status, String remark) {
        if (logId < 0) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE pmis_migration_log SET status = ?, affected_rows = ?, finished_at = ?, remark = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setLong(2, affected);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.setString(4, remark);
            ps.setLong(5, logId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("[EncryptedField] 更新迁移日志失败 logId={} err={}", logId, e.getMessage());
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 判断字符串是否为空（null 或空串）
     *
     * @param s 字符串
     * @return true 表示为空
     */
    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }

    /**
     * 若 options.aesKeyBase64 非空，则注册到 EncryptedFieldKeyRegistry；否则使用默认密钥
     *
     * @param options 迁移选项
     */
    private void registerKeyIfNeeded(MigrationOptions options) {
        if (options.aesKeyBase64 == null || options.aesKeyBase64.isEmpty()) {
            log.info("[EncryptedField] 未提供 aesKeyBase64, 使用 EncryptedFieldKeyRegistry 默认密钥");
            return;
        }
        try {
            byte[] key = Base64.getDecoder().decode(options.aesKeyBase64);
            if (key.length != 32) {
                throw new IllegalArgumentException("AES 密钥必须 32 字节, 实际: " + key.length);
            }
            EncryptedFieldKeyRegistry.register("pmis.crypto.aes-key", key);
            log.info("[EncryptedField] 已注册 AES 密钥 keyRef=pmis.crypto.aes-key (32 bytes)");
        } catch (IllegalArgumentException e) {
            log.error("[EncryptedField] AES 密钥非法: {}", e.getMessage());
            throw e;
        }
    }

    // ==================== 静态工厂: 供 Spring 容器外使用 ====================

    /**
     * 直接通过 JDBC URL/账号密码创建服务
     *
     * <p>由于 common 模块不依赖 postgresql 驱动 (避免污染公共类路径),
     * 本方法通过反射创建 {@code org.postgresql.ds.PGSimpleDataSource}。
     * 若运行时 postgresql 驱动不在 classpath, 抛 IllegalStateException。
     *
     * @param jdbcUrl  JDBC 连接 URL
     * @param username 数据库用户名
     * @param password 数据库密码
     * @return 已初始化的迁移服务实例
     * @throws IllegalStateException 当 postgresql 驱动不在 classpath 时抛出
     */
    public static EncryptedFieldMigrationService fromJdbcUrl(String jdbcUrl, String username, String password) {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "未找到 org.postgresql.Driver, 请在执行 CLI 时加入 -cp <postgresql.jar> 或确保 fat jar 已包含",
                    e);
        }
        try {
            Class<?> dsClass = Class.forName("org.postgresql.ds.PGSimpleDataSource");
            Object ds = dsClass.getDeclaredConstructor().newInstance();
            dsClass.getMethod("setUrl", String.class).invoke(ds, jdbcUrl);
            dsClass.getMethod("setUser", String.class).invoke(ds, username);
            dsClass.getMethod("setPassword", String.class).invoke(ds, password);
            return new EncryptedFieldMigrationService((javax.sql.DataSource) ds);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("缺少 org.postgresql.ds.PGSimpleDataSource", e);
        } catch (Exception e) {
            throw new IllegalStateException("初始化 PGSimpleDataSource 失败: " + e.getMessage(), e);
        }
    }

    // ==================== 内部类 ====================

    /** 单行明文/密文数据（内部读取/比对用） */
    private static class RowData {
        /** 行主键 */
        String id;
        /** 明文值 */
        String plainValue;
        /** 密文值 */
        String cipherValue;
    }

    /**
     * 迁移列配置
     */
    public record MigrationColumn(String table, String plainColumn, String cipherColumn, String keyRef) {
    }

    /**
     * 迁移选项
     */
    public static class MigrationOptions {
        /** 迁移批次号（写入 pmis_migration_log） */
        public String batchCode = "V1.0.0_018_ENCRYPTED_FIELD";
        /** 每批处理行数 */
        public int batchSize = 500;
        /** AES 密钥（Base64，32 字节）；为空时使用默认密钥 */
        public String aesKeyBase64;
        /** 自定义迁移列配置；为空时使用 DEFAULT_COLUMNS */
        public List<MigrationColumn> columns;

        /**
         * @return 默认选项实例
         */
        public static MigrationOptions defaults() {
            return new MigrationOptions();
        }
    }

    /**
     * 迁移结果
     */
    public record MigrationResult(MigrationColumn column, long success, long skipped, long failed, long costMs) {
    }

    /**
     * 校验结果
     */
    public record VerifyResult(MigrationColumn column, long sample, long match, long mismatch, long costMs) {
        /**
         * @return 匹配率（sample 为 0 时返回 0.0）
         */
        public double matchRate() {
            return sample == 0 ? 0.0 : (double) match / sample;
        }

        /**
         * @return true 表示全部抽样均匹配（mismatch 为 0 且 sample 大于 0）
         */
        public boolean isAllOk() {
            return mismatch == 0 && sample > 0;
        }
    }
}
