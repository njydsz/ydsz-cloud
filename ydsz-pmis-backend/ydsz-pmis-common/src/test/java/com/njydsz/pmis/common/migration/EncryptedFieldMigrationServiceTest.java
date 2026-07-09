package com.njydsz.pmis.common.migration;

import com.njydsz.pmis.common.migration.EncryptedFieldMigrationService.MigrationColumn;
import com.njydsz.pmis.common.migration.EncryptedFieldMigrationService.MigrationOptions;
import com.njydsz.pmis.common.migration.EncryptedFieldMigrationService.MigrationResult;
import com.njydsz.pmis.common.migration.EncryptedFieldMigrationService.VerifyResult;
import com.njydsz.pmis.common.sensitive.EncryptedFieldKeyRegistry;
import com.njydsz.pmis.common.util.CryptoUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EncryptedFieldMigrationService 加密字段迁移服务单元测试
 *
 * <p>覆盖 encryptAll / verifyAll 方法的正常路径、边界条件与异常场景,
 * 通过 Mock JDBC DataSource/Connection/PreparedStatement/ResultSet 验证分批加密逻辑.
 *
 * <p>使用 LENIENT 严格度: JDBC 多分支流程中部分 stub 可能未被执行(如空表场景不触发 UPDATE),
 * 宽松模式避免 UnnecessaryStubbingException 误报.
 *
 * @author ydsz-pmis-team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EncryptedFieldMigrationService 加密字段迁移服务测试")
class EncryptedFieldMigrationServiceTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement psRead;

    @Mock
    private PreparedStatement psUpdate;

    @Mock
    private PreparedStatement psLog;

    @Mock
    private ResultSet rs;

    @Mock
    private ResultSet rsKeys;

    // ==================== encryptAll ====================

    @Test
    @DisplayName("正常场景：encryptAll 加密单列明文数据成功")
    void encryptAll_单列加密_成功() throws SQLException {
        MigrationColumn col = new MigrationColumn("test_table", "phone", "phone_cipher", "pmis.crypto.aes-key");
        MigrationOptions options = MigrationOptions.defaults();
        options.batchCode = null;
        options.columns = List.of(col);
        options.batchSize = 100;

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SELECT"))).thenReturn(psRead);
        when(connection.prepareStatement(contains("UPDATE"))).thenReturn(psUpdate);

        ResultSet emptyRs = mock(ResultSet.class);
        when(emptyRs.next()).thenReturn(false);
        when(psRead.executeQuery()).thenReturn(rs, emptyRs);

        when(rs.next()).thenReturn(true, false);
        when(rs.getString("id")).thenReturn("1");
        when(rs.getString("plain")).thenReturn("13800138000");
        when(rs.getString("cipher")).thenReturn(null);

        List<MigrationResult> results = new EncryptedFieldMigrationService(dataSource).encryptAll(options);

        assertEquals(1, results.size());
        MigrationResult r = results.get(0);
        assertEquals(1L, r.success());
        assertEquals(0L, r.skipped());
        assertEquals(0L, r.failed());
        verify(psUpdate).executeUpdate();
    }

    @Test
    @DisplayName("边界场景：encryptAll options 为 null 使用默认配置（空表）")
    void encryptAll_optionsNull_使用默认() throws SQLException {
        // 默认 5 列，batchCode 非空 → 每列 3 次 getConnection (startLog + main + finishLog)
        // 全部返回同一个 mock connection，所有查询返回空
        when(dataSource.getConnection()).thenReturn(connection);

        // startLog: prepareStatement(sql, RETURN_GENERATED_KEYS)
        when(connection.prepareStatement(anyString(), anyInt())).thenReturn(psLog);
        when(psLog.executeUpdate()).thenReturn(1);
        when(psLog.getGeneratedKeys()).thenReturn(rsKeys);
        when(rsKeys.next()).thenReturn(true, false);
        when(rsKeys.getLong(1)).thenReturn(1L);

        // main: SELECT (read)
        when(connection.prepareStatement(contains("SELECT"))).thenReturn(psRead);
        when(psRead.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        // finishLog: UPDATE pmis_migration_log
        when(connection.prepareStatement(contains("UPDATE pmis_migration_log"))).thenReturn(psLog);

        List<MigrationResult> results = new EncryptedFieldMigrationService(dataSource).encryptAll(null);

        assertEquals(EncryptedFieldMigrationService.DEFAULT_COLUMNS.size(), results.size());
        for (MigrationResult r : results) {
            assertEquals(0L, r.success());
            assertEquals(0L, r.skipped());
            assertEquals(0L, r.failed());
        }
    }

    @Test
    @DisplayName("边界场景：明文为空跳过加密")
    void encryptAll_明文为空_跳过() throws SQLException {
        MigrationColumn col = new MigrationColumn("t", "col", "col_cipher", "pmis.crypto.aes-key");
        MigrationOptions options = MigrationOptions.defaults();
        options.batchCode = null;
        options.columns = List.of(col);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(psRead);

        ResultSet emptyRs = mock(ResultSet.class);
        when(emptyRs.next()).thenReturn(false);
        when(psRead.executeQuery()).thenReturn(rs, emptyRs);

        when(rs.next()).thenReturn(true, false);
        when(rs.getString("id")).thenReturn("1");
        when(rs.getString("plain")).thenReturn("");
        when(rs.getString("cipher")).thenReturn(null);

        List<MigrationResult> results = new EncryptedFieldMigrationService(dataSource).encryptAll(options);

        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).skipped());
        assertEquals(0L, results.get(0).success());
        verify(psUpdate, never()).executeUpdate();
    }

    @Test
    @DisplayName("边界场景：密文列已非空跳过加密（幂等）")
    void encryptAll_密文已存在_跳过() throws SQLException {
        MigrationColumn col = new MigrationColumn("t", "col", "col_cipher", "pmis.crypto.aes-key");
        MigrationOptions options = MigrationOptions.defaults();
        options.batchCode = null;
        options.columns = List.of(col);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(psRead);

        ResultSet emptyRs = mock(ResultSet.class);
        when(emptyRs.next()).thenReturn(false);
        when(psRead.executeQuery()).thenReturn(rs, emptyRs);

        when(rs.next()).thenReturn(true, false);
        when(rs.getString("id")).thenReturn("1");
        when(rs.getString("plain")).thenReturn("data");
        when(rs.getString("cipher")).thenReturn("existing-cipher");

        List<MigrationResult> results = new EncryptedFieldMigrationService(dataSource).encryptAll(options);

        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).skipped());
        assertEquals(0L, results.get(0).success());
        verify(psUpdate, never()).executeUpdate();
    }

    @Test
    @DisplayName("边界场景：无数据时 encryptAll 返回空结果列")
    void encryptAll_无数据() throws SQLException {
        MigrationColumn col = new MigrationColumn("t", "col", "col_cipher", "pmis.crypto.aes-key");
        MigrationOptions options = MigrationOptions.defaults();
        options.batchCode = null;
        options.columns = List.of(col);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(psRead);
        when(psRead.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        List<MigrationResult> results = new EncryptedFieldMigrationService(dataSource).encryptAll(options);

        assertEquals(1, results.size());
        assertEquals(0L, results.get(0).success());
        assertEquals(0L, results.get(0).skipped());
        assertEquals(0L, results.get(0).failed());
    }

    @Test
    @DisplayName("异常场景：aesKeyBase64 长度非 32 字节抛 IllegalArgumentException")
    void encryptAll_密钥长度非法_抛异常() {
        MigrationOptions options = MigrationOptions.defaults();
        options.aesKeyBase64 = Base64.getEncoder().encodeToString(new byte[16]);
        options.columns = List.of(new MigrationColumn("t", "c", "c_cipher", "pmis.crypto.aes-key"));

        assertThrows(IllegalArgumentException.class,
                () -> new EncryptedFieldMigrationService(dataSource).encryptAll(options));
    }

    @Test
    @DisplayName("正常场景：aesKeyBase64 32 字节注册成功")
    void encryptAll_密钥合法_注册成功() throws SQLException {
        MigrationOptions options = MigrationOptions.defaults();
        options.batchCode = null;
        options.aesKeyBase64 = Base64.getEncoder().encodeToString(new byte[32]);
        options.columns = List.of(new MigrationColumn("t", "c", "c_cipher", "pmis.crypto.aes-key"));

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(psRead);
        when(psRead.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        List<MigrationResult> results = new EncryptedFieldMigrationService(dataSource).encryptAll(options);

        assertEquals(1, results.size());
        assertTrue(EncryptedFieldKeyRegistry.has("pmis.crypto.aes-key"));
    }

    // ==================== verifyAll ====================

    @Test
    @DisplayName("异常场景：verifyAll batchCode 为空抛 IllegalArgumentException")
    void verifyAll_batchCode为空_抛异常() {
        assertThrows(IllegalArgumentException.class,
                () -> new EncryptedFieldMigrationService(dataSource).verifyAll("", 100));
        assertThrows(IllegalArgumentException.class,
                () -> new EncryptedFieldMigrationService(dataSource).verifyAll(null, 100));
    }

    @Test
    @DisplayName("边界场景：verifyAll sampleSize<=0 默认为 100，空表返回全 0")
    void verifyAll_sampleSize默认() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);

        // startLog (5 列各一次: true→取 logId, false→结束)
        when(connection.prepareStatement(anyString(), anyInt())).thenReturn(psLog);
        when(psLog.executeUpdate()).thenReturn(1);
        when(psLog.getGeneratedKeys()).thenReturn(rsKeys);
        when(rsKeys.next()).thenReturn(true, false, true, false, true, false, true, false, true, false);
        when(rsKeys.getLong(1)).thenReturn(1L);

        // verify SELECT (5 列各返回空)
        when(connection.prepareStatement(contains("SELECT"))).thenReturn(psRead);
        when(psRead.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        // finishLog
        when(connection.prepareStatement(contains("UPDATE pmis_migration_log"))).thenReturn(psLog);

        List<VerifyResult> results = new EncryptedFieldMigrationService(dataSource).verifyAll("batch-001", 0);

        assertEquals(EncryptedFieldMigrationService.DEFAULT_COLUMNS.size(), results.size());
        for (VerifyResult r : results) {
            assertEquals(0L, r.sample());
            assertEquals(0L, r.match());
            assertEquals(0L, r.mismatch());
        }
    }

    @Test
    @DisplayName("正常场景：verifyAll 校验匹配数据")
    void verifyAll_校验匹配() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);

        // startLog (5 列: true,false × 5)
        when(connection.prepareStatement(anyString(), anyInt())).thenReturn(psLog);
        when(psLog.executeUpdate()).thenReturn(1);
        when(psLog.getGeneratedKeys()).thenReturn(rsKeys);
        when(rsKeys.next()).thenReturn(true, false, true, false, true, false, true, false, true, false);
        when(rsKeys.getLong(1)).thenReturn(1L);

        // verify SELECT → 每列 1 行匹配 (true,false × 5)
        when(connection.prepareStatement(contains("SELECT"))).thenReturn(psRead);
        when(psRead.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false, true, false, true, false, true, false, true, false);

        String plain = "13800138000";
        byte[] key = EncryptedFieldKeyRegistry.get("pmis.crypto.aes-key");
        String cipher = CryptoUtil.aesGcmEncrypt(plain, key);
        when(rs.getString("plain")).thenReturn(plain);
        when(rs.getString("cipher")).thenReturn(cipher);
        when(rs.getLong("id")).thenReturn(1L);

        // finishLog
        when(connection.prepareStatement(contains("UPDATE pmis_migration_log"))).thenReturn(psLog);

        List<VerifyResult> results = new EncryptedFieldMigrationService(dataSource).verifyAll("batch-002", 10);

        assertEquals(EncryptedFieldMigrationService.DEFAULT_COLUMNS.size(), results.size());
        for (VerifyResult r : results) {
            assertEquals(1L, r.sample());
            assertEquals(1L, r.match());
            assertEquals(0L, r.mismatch());
            assertTrue(r.isAllOk());
        }
    }

    @Test
    @DisplayName("异常场景：verifyAll 校验不匹配数据")
    void verifyAll_校验不匹配() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);

        when(connection.prepareStatement(anyString(), anyInt())).thenReturn(psLog);
        when(psLog.executeUpdate()).thenReturn(1);
        when(psLog.getGeneratedKeys()).thenReturn(rsKeys);
        when(rsKeys.next()).thenReturn(true, false, true, false, true, false, true, false, true, false);
        when(rsKeys.getLong(1)).thenReturn(1L);

        when(connection.prepareStatement(contains("SELECT"))).thenReturn(psRead);
        when(psRead.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false, true, false, true, false, true, false, true, false);
        when(rs.getString("plain")).thenReturn("original");
        when(rs.getString("cipher")).thenReturn("wrong-cipher");
        when(rs.getLong("id")).thenReturn(1L);

        when(connection.prepareStatement(contains("UPDATE pmis_migration_log"))).thenReturn(psLog);

        List<VerifyResult> results = new EncryptedFieldMigrationService(dataSource).verifyAll("batch-003", 10);

        assertEquals(EncryptedFieldMigrationService.DEFAULT_COLUMNS.size(), results.size());
        for (VerifyResult r : results) {
            assertEquals(1L, r.sample());
            assertEquals(1L, r.mismatch());
            assertEquals(0L, r.match());
        }
    }

    // ==================== VerifyResult / MigrationResult / Options ====================

    @Test
    @DisplayName("正常场景：VerifyResult matchRate 计算正确")
    void verifyResult_matchRate() {
        MigrationColumn col = new MigrationColumn("t", "c", "c_cipher", "k");
        VerifyResult r = new VerifyResult(col, 10, 8, 2, 100L);

        assertEquals(0.8, r.matchRate(), 0.001);
        assertEquals(false, r.isAllOk());
    }

    @Test
    @DisplayName("边界场景：VerifyResult sample=0 时 matchRate=0.0")
    void verifyResult_sample0_matchRate0() {
        MigrationColumn col = new MigrationColumn("t", "c", "c_cipher", "k");
        VerifyResult r = new VerifyResult(col, 0, 0, 0, 0L);

        assertEquals(0.0, r.matchRate(), 0.001);
        assertEquals(false, r.isAllOk());
    }

    @Test
    @DisplayName("正常场景：VerifyResult 全部匹配 isAllOk=true")
    void verifyResult_全匹配_isAllOk() {
        MigrationColumn col = new MigrationColumn("t", "c", "c_cipher", "k");
        VerifyResult r = new VerifyResult(col, 5, 5, 0, 50L);

        assertTrue(r.isAllOk());
        assertEquals(1.0, r.matchRate(), 0.001);
    }

    @Test
    @DisplayName("正常场景：MigrationResult 包含列配置和统计")
    void migrationResult_字段正确() {
        MigrationColumn col = new MigrationColumn("tbl", "plain_col", "cipher_col", "keyRef");
        MigrationResult r = new MigrationResult(col, 10, 5, 2, 200L);

        assertEquals(col, r.column());
        assertEquals(10L, r.success());
        assertEquals(5L, r.skipped());
        assertEquals(2L, r.failed());
        assertEquals(200L, r.costMs());
    }

    @Test
    @DisplayName("正常场景：MigrationOptions.defaults 返回默认配置")
    void migrationOptions_defaults() {
        MigrationOptions opts = MigrationOptions.defaults();

        assertNotNull(opts);
        assertEquals(500, opts.batchSize);
        assertEquals("V1.0.0_018_ENCRYPTED_FIELD", opts.batchCode);
    }

    @Test
    @DisplayName("正常场景：DEFAULT_COLUMNS 包含 pmis_user_account 的 5 列")
    void defaultColumns_包含5列() {
        List<MigrationColumn> cols = EncryptedFieldMigrationService.DEFAULT_COLUMNS;

        assertEquals(5, cols.size());
        assertEquals("pmis_user_account", cols.get(0).table());
        assertEquals("id_card", cols.get(0).plainColumn());
        assertEquals("id_card_cipher", cols.get(0).cipherColumn());
    }
}
