package com.njydsz.pmis.common.sensitive;

import com.njydsz.pmis.common.util.CryptoUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EncryptedFieldKeyRegistry 密钥注册中心测试
 *
 * <p>覆盖 AES/SM4 密钥注册、默认密钥回退、长度校验与清空逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("EncryptedFieldKeyRegistry 密钥注册中心测试")
class EncryptedFieldKeyRegistryTest {

    @AfterEach
    void tearDown() {
        EncryptedFieldKeyRegistry.resetForTest();
    }

    @Test
    @DisplayName("register/get 应能正确存取")
    void register_get() {
        byte[] key = CryptoUtil.randomBytes(32);
        EncryptedFieldKeyRegistry.register("k1", key);
        assertThat(EncryptedFieldKeyRegistry.get("k1")).containsExactly(key);
        assertThat(EncryptedFieldKeyRegistry.has("k1")).isTrue();
    }

    @Test
    @DisplayName("未注册 keyRef 应回退到默认密钥 (32 字节)")
    void get_unknownFallback() {
        byte[] dk = EncryptedFieldKeyRegistry.get("nope");
        assertThat(dk).hasSize(32);
    }

    @Test
    @DisplayName("setDefaultKey 必须 32 字节")
    void setDefaultKey_mustBe32() {
        assertThatThrownBy(() -> EncryptedFieldKeyRegistry.setDefaultKey(new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("setDefaultKey 替换后再 get 应返回新密钥")
    void setDefaultKey_replace() {
        byte[] custom = CryptoUtil.randomBytes(32);
        EncryptedFieldKeyRegistry.setDefaultKey(custom);
        assertThat(EncryptedFieldKeyRegistry.get("nope")).containsExactly(custom);
    }

    @Test
    @DisplayName("register 入参 null / 空应抛 IllegalArgumentException")
    void register_nullSafe() {
        assertThatThrownBy(() -> EncryptedFieldKeyRegistry.register(null, CryptoUtil.randomBytes(32)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EncryptedFieldKeyRegistry.register("", CryptoUtil.randomBytes(32)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EncryptedFieldKeyRegistry.register("k", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(EncryptedFieldKeyRegistry.has("k")).isFalse();
    }

    @Test
    @DisplayName("register 长度非 32 应抛 IllegalArgumentException")
    void register_invalidLength() {
        assertThatThrownBy(() -> EncryptedFieldKeyRegistry.register("k", new byte[16]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 字节");
        assertThatThrownBy(() -> EncryptedFieldKeyRegistry.register("k", new byte[64]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 字节");
    }

    @Test
    @DisplayName("registerSm4 接受 16 字节密钥, 长度非法抛错")
    void registerSm4() {
        EncryptedFieldKeyRegistry.registerSm4("sm4-k", CryptoUtil.randomBytes(16));
        assertThat(EncryptedFieldKeyRegistry.has("sm4-k")).isTrue();
        assertThat(EncryptedFieldKeyRegistry.get("sm4-k")).hasSize(16);

        assertThatThrownBy(() -> EncryptedFieldKeyRegistry.registerSm4("sm4-bad", new byte[32]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("16 字节");
        assertThatThrownBy(() -> EncryptedFieldKeyRegistry.registerSm4(null, CryptoUtil.randomBytes(16)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EncryptedFieldKeyRegistry.registerSm4("sm4-null", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("clear 应清空所有已注册密钥与默认密钥")
    void clear() {
        EncryptedFieldKeyRegistry.register("k1", CryptoUtil.randomBytes(32));
        EncryptedFieldKeyRegistry.setDefaultKey(CryptoUtil.randomBytes(32));
        EncryptedFieldKeyRegistry.clear();
        assertThat(EncryptedFieldKeyRegistry.has("k1")).isFalse();
    }
}
