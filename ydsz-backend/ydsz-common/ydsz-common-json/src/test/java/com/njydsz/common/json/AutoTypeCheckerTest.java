package com.njydsz.common.json;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.njydsz.common.json.autotype.AutoTypeChecker;
import com.njydsz.common.json.exception.JsonDeserializationException;

/**
 * AutoTypeChecker 安全检查测试。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
class AutoTypeCheckerTest {

    @AfterEach
    void cleanup() {
        // 恢复默认安全模式
        AutoTypeChecker.setSafeMode(true);
    }

    @Test
    void testBuiltinWhitelistAllowed() {
        assertTrue(AutoTypeChecker.isTypeAllowed(String.class));
        assertTrue(AutoTypeChecker.isTypeAllowed(Integer.class));
        assertTrue(AutoTypeChecker.isTypeAllowed(Long.class));
        assertTrue(AutoTypeChecker.isTypeAllowed(HashMap.class));
        assertTrue(AutoTypeChecker.isTypeAllowed(UUID.class));
    }

    @Test
    void testBuiltinBlacklistBlocked() {
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.Runtime"));
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.ProcessBuilder"));
        assertFalse(AutoTypeChecker.isTypeAllowed("java.io.File"));
        assertFalse(AutoTypeChecker.isTypeAllowed("java.net.URL"));
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.ClassLoader"));
    }

    @Test
    void testNullAndEmptyAllowed() {
        assertTrue(AutoTypeChecker.isTypeAllowed((Class<?>) null));
        assertTrue(AutoTypeChecker.isTypeAllowed(""));
        assertTrue(AutoTypeChecker.isTypeAllowed((String) null));
    }

    @Test
    void testCheckTypeThrowsForBlocked() {
        assertThrows(JsonDeserializationException.class,
                () -> AutoTypeChecker.checkType("java.lang.Runtime"));
        assertThrows(JsonDeserializationException.class,
                () -> AutoTypeChecker.checkType("java.lang.ProcessBuilder"));
    }

    @Test
    void testCheckTypeDoesNotThrowForAllowed() {
        // 内置白名单类型不应抛出异常
        assertDoesNotThrow(() -> AutoTypeChecker.checkType(String.class));
        assertDoesNotThrow(() -> AutoTypeChecker.checkType(Integer.class));
    }

    @Test
    void testSafeModeFalseAllowsAllNonBlacklisted() {
        AutoTypeChecker.setSafeMode(false);
        // 黑名单仍然生效
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.Runtime"));
        // 非 blacklist 类型在 SafeMode=false 时全部允许
        assertTrue(AutoTypeChecker.isTypeAllowed("com.example.AnyClass"));
    }

    @Test
    void testSafeModeTrueBlocksUnknown() {
        AutoTypeChecker.setSafeMode(true);
        assertFalse(AutoTypeChecker.isTypeAllowed("com.example.UnknownClass"));
    }

    @Test
    void testPrimitiveArrayAllowed() {
        // 数组类型以 [ 开头，在 SafeMode 下允许
        assertTrue(AutoTypeChecker.isTypeAllowed("[Ljava.lang.String;"));
        assertTrue(AutoTypeChecker.isTypeAllowed("[I"));
    }

    @Test
    void testExplicitWhitelist() {
        String className = "com.example.TestWhitelistClass";
        try {
            AutoTypeChecker.addToWhitelist(className);
            assertTrue(AutoTypeChecker.isTypeAllowed(className));
        } finally {
            // 清理（如果有 removeFromWhitelist 方法的话）
        }
    }

    @Test
    void testExplicitBlacklist() {
        String className = "com.example.TestBlacklistClass";
        AutoTypeChecker.addToBlacklist(className);
        // 黑名单优先级最高，即使 SafeMode=false 也拒绝
        AutoTypeChecker.setSafeMode(false);
        assertFalse(AutoTypeChecker.isTypeAllowed(className));
    }
}
