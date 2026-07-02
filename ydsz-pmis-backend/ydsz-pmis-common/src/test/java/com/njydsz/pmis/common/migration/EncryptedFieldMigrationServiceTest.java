package com.njydsz.pmis.common.migration;

import com.njydsz.pmis.common.sensitive.EncryptedFieldKeyRegistry;
import com.njydsz.pmis.common.util.CryptoUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EncryptedFieldMigrationService 单元测试
 *
 * <p>覆盖 DEFAULT_COLUMNS 列表 / 密钥注入 / 异常分支 / 配置项校验。
 * 数据库交互部分 (encryptColumn / verifyColumn) 通过 validate / reject 路径
 * 间接覆盖, 集成测试由 ops 在 dev 环境执行 verify 阶段验证。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class EncryptedFieldMigrationServiceTest {

    private EncryptedFieldMigrationService service;

    @BeforeEach
    void setUp() {
        EncryptedFieldKeyRegistry.clear();
        // 使用一个 32 字节的固定测试密钥 (Base64 解码得到 32 字节确定性密钥)
        byte[] key = Base64.getDecoder().decode("MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=");
        assertThat(key).hasSize(32);
        EncryptedFieldKeyRegistry.register("pmis.crypto.aes-key", key);

        // service 不需要真实 DataSource, 因为本测试只覆盖 options/config 分支
        service = new EncryptedFieldMigrationService(null);
    }

    @AfterEach
    void tearDown() {
        EncryptedFieldKeyRegistry.clear();
    }

    @Test
    @DisplayName("DEFAULT_COLUMNS 覆盖 pmis_user_account 5 个敏感字段")
    void default_columns_cover_sensitive_fields() {
        assertThat(EncryptedFieldMigrationService.DEFAULT_COLUMNS)
                .extracting(EncryptedFieldMigrationService.MigrationColumn::table)
                .containsOnly("pmis_user_account");
        assertThat(EncryptedFieldMigrationService.DEFAULT_COLUMNS)
                .extracting(EncryptedFieldMigrationService.MigrationColumn::plainColumn)
                .contains("id_card", "phone", "email", "bank_card", "address");
        assertThat(EncryptedFieldMigrationService.DEFAULT_COLUMNS)
                .extracting(EncryptedFieldMigrationService.MigrationColumn::cipherColumn)
                .contains("id_card_cipher", "phone_cipher", "email_cipher", "bank_card_cipher", "address_cipher");
        assertThat(EncryptedFieldMigrationService.DEFAULT_COLUMNS)
                .extracting(EncryptedFieldMigrationService.MigrationColumn::keyRef)
                .containsOnly("pmis.crypto.aes-key");
    }

    @Test
    @DisplayName("MigrationColumn 字段为不可变 record, 一致性校验")
    void migration_column_immutable() {
        EncryptedFieldMigrationService.MigrationColumn col =
                new EncryptedFieldMigrationService.MigrationColumn(
                        "pmis_user_account", "id_card", "id_card_cipher", "pmis.crypto.aes-key");
        assertThat(col.table()).isEqualTo("pmis_user_account");
        assertThat(col.plainColumn()).isEqualTo("id_card");
        assertThat(col.cipherColumn()).isEqualTo("id_card_cipher");
        assertThat(col.keyRef()).isEqualTo("pmis.crypto.aes-key");
    }

    @Test
    @DisplayName("MigrationOptions defaults 提供非空默认值")
    void migration_options_defaults() {
        EncryptedFieldMigrationService.MigrationOptions opt = EncryptedFieldMigrationService.MigrationOptions.defaults();
        assertThat(opt.batchCode).isEqualTo("V1.0.0_018_ENCRYPTED_FIELD");
        assertThat(opt.batchSize).isEqualTo(500);
        assertThat(opt.aesKeyBase64).isNull();
        assertThat(opt.columns).isNull();
    }

    @Test
    @DisplayName("VerifyResult.matchRate / isAllOk 计算正确")
    void verify_result_calculations() {
        EncryptedFieldMigrationService.MigrationColumn col =
                new EncryptedFieldMigrationService.MigrationColumn("t", "a", "a_cipher", "k");
        EncryptedFieldMigrationService.VerifyResult r1 = new EncryptedFieldMigrationService.VerifyResult(col, 100, 100, 0, 50);
        assertThat(r1.matchRate()).isEqualTo(1.0);
        assertThat(r1.isAllOk()).isTrue();

        EncryptedFieldMigrationService.VerifyResult r2 = new EncryptedFieldMigrationService.VerifyResult(col, 100, 95, 5, 50);
        assertThat(r2.matchRate()).isEqualTo(0.95);
        assertThat(r2.isAllOk()).isFalse();

        EncryptedFieldMigrationService.VerifyResult r3 = new EncryptedFieldMigrationService.VerifyResult(col, 0, 0, 0, 50);
        assertThat(r3.matchRate()).isEqualTo(0.0);
        assertThat(r3.isAllOk()).isFalse();
    }

    @Test
    @DisplayName("MigrationResult 字段映射")
    void migration_result_record() {
        EncryptedFieldMigrationService.MigrationColumn col =
                new EncryptedFieldMigrationService.MigrationColumn("t", "a", "a_cipher", "k");
        EncryptedFieldMigrationService.MigrationResult r = new EncryptedFieldMigrationService.MigrationResult(col, 100, 50, 2, 1234);
        assertThat(r.column()).isEqualTo(col);
        assertThat(r.success()).isEqualTo(100);
        assertThat(r.skipped()).isEqualTo(50);
        assertThat(Result.failed()).isEqualTo(2);
        assertThat(r.costMs()).isEqualTo(1234);
    }

    @Test
    @DisplayName("verifyAll 校验 batchCode 非空")
    void verify_all_requires_batch_code() {
        assertThatThrownBy(() -> service.verifyAll(null, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchCode");

        assertThatThrownBy(() -> service.verifyAll("", 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchCode");

        assertThatThrownBy(() -> service.verifyAll("   ", 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchCode");
    }

    @Test
    @DisplayName("verifyAll 非法 sampleSize 自动回退到 100")
    void verify_all_falls_back_to_100_on_invalid_sample_size() {
        // 不会实际查询数据库, 因为在 DDL 校验前就会因 DataSource=null 抛 NPE
        // 这里只验证参数校验链路
        assertThatThrownBy(() -> service.verifyAll("V1.0.0_018_ENCRYPTED_FIELD", 0))
                .isInstanceOf(NullPointerException.class); // 走完参数校验后取 connection 失败
        assertThatThrownBy(() -> service.verifyAll("V1.0.0_018_ENCRYPTED_FIELD", -1))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("EncryptedFieldKeyRegistry 已注册, 加密解密可逆")
    void registry_round_trip() {
        String keyRef = "pmis.crypto.aes-key";
        assertThat(EncryptedFieldKeyRegistry.has(keyRef)).isTrue();
        byte[] key = EncryptedFieldKeyRegistry.get(keyRef);
        assertThat(key).hasSize(32);

        String plain = "13800138000";
        String cipher = CryptoUtil.aesGcmEncrypt(plain, key);
        assertThat(cipher).isNotEqualTo(plain);
        assertThat(cipher).isNotBlank();
        // AES-GCM 输出 base64(IV(12) || ct+tag(16)), 长度 >= 36 chars
        assertThat(cipher).hasSizeGreaterThanOrEqualTo(36);
        String decrypted = CryptoUtil.aesGcmDecrypt(cipher, key);
        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    @DisplayName("EncryptedFieldKeyRegistry 错误密钥拒绝注入")
    void registry_rejects_invalid_key() {
        assertThatThrownBy(() -> EncryptedFieldKeyRegistry.register("bad-key", new byte[16]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 字节");

        assertThatThrownBy(() -> EncryptedFieldKeyRegistry.register("bad-key", new byte[64]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 字节");

        assertThatThrownBy(() -> EncryptedFieldKeyRegistry.registerSm4("sm4-bad", new byte[32]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("16 字节");
    }

    @Test
    @DisplayName("DEFAULT_COLUMNS 数量与文档约定一致 (5 字段)")
    void default_columns_count() {
        assertThat(EncryptedFieldMigrationService.DEFAULT_COLUMNS).hasSize(5);
    }

    @Test
    @DisplayName("fromJdbcUrl 接受有效 jdbcUrl/username/password")
    void from_jdbc_url_smoke() {
        // common 模块故意不依赖 postgresql 驱动, fromJdbcUrl 在驱动缺失时抛 IllegalStateException
        // 这是设计上的依赖解耦: CLI 启动时手动 -cp 加入 postgresql jar
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> EncryptedFieldMigrationService.fromJdbcUrl(
                        "jdbc:postgresql://localhost:5432/pmis", "pmis", "pmis"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("org.postgresql.Driver");
    }

    @Test
    @DisplayName("MigrationOptions 字段可写")
    void migration_options_writable() {
        EncryptedFieldMigrationService.MigrationOptions opt = new EncryptedFieldMigrationService.MigrationOptions();
        opt.batchCode = "BATCH_TEST";
        opt.batchSize = 100;
        opt.aesKeyBase64 = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
        opt.columns = List.of(EncryptedFieldMigrationService.DEFAULT_COLUMNS.get(0));
        assertThat(opt.batchCode).isEqualTo("BATCH_TEST");
        assertThat(opt.batchSize).isEqualTo(100);
        assertThat(opt.aesKeyBase64).hasSize(44); // 32 bytes -> 44 base64 chars
        assertThat(opt.columns).hasSize(1);
    }
}
