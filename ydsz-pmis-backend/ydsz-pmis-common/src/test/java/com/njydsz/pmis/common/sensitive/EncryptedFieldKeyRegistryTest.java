package com.njydsz.pmis.common.sensitive;

import com.njydsz.pmis.common.util.CryptoUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EncryptedFieldKeyRegistry 密钥注册中心测试
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
    @DisplayName("register 入参 null / 空应忽略")
    void register_nullSafe() {
        EncryptedFieldKeyRegistry.register(null, CryptoUtil.randomBytes(32));
        EncryptedFieldKeyRegistry.register("", CryptoUtil.randomBytes(32));
        EncryptedFieldKeyRegistry.register("k", null);
        assertThat(EncryptedFieldKeyRegistry.has("k")).isFalse();
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
