package com.njydsz.common.json;

import com.njydsz.common.json.autotype.AutoTypeChecker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AutoType 白名单/黑名单安全机制测试（P1 安全回归闸门）。
 */
class AutoTypeSecurityTest {

    @Test
    void arrayTypeNotBypassesBlacklist() {
        // [Ljava.lang.Runtime; 不应绕过黑名单（修复前直接 return true）
        assertFalse(AutoTypeChecker.isTypeAllowed("[Ljava.lang.Runtime;"),
            "array of blacklisted type must be rejected");
        assertFalse(AutoTypeChecker.isTypeAllowed("[Ljava.lang.ProcessBuilder;"),
            "array of blacklisted type must be rejected");
    }

    @Test
    void arrayOfSafeTypeAllowed() {
        assertTrue(AutoTypeChecker.isTypeAllowed("[Ljava.lang.String;"),
            "array of safe type must be allowed in safe-mode");
    }

    @Test
    void whitelistedTopLevelBeanAllowed() {
        AutoTypeChecker.addToWhitelist("com.njydsz.common.json.TestBean");
        assertTrue(AutoTypeChecker.isTypeAllowed("com.njydsz.common.json.TestBean"),
            "explicitly whitelisted type must be allowed");
    }

    @Test
    void innerClassRecursiveBlacklistCheck() {
        // java.lang.ProcessImpl 在黑名单中
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.ProcessImpl"),
            "blacklisted class must be rejected");
        // A$B$C where A$B 在黑名单? ProcessImpl 本身在黑名单，ProcessImpl$Inner 应被阻
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.ProcessImpl$Foo"),
            "inner class of blacklisted type must be rejected");
        // 两级内类也阻
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.ProcessImpl$Foo$Bar"),
            "nested inner class of blacklisted type must be rejected");
    }
}
