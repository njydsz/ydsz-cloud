package com.remisoft.common.json;

import com.remisoft.common.json.autotype.AutoTypeChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AutoType 安全机制扩展测试（P0）。
 *
 * <p>补充黑名单覆盖、SafeMode 关闭行为、白名单通配符等场景。
 */
class AutoTypeExtendedTest {

    @BeforeEach
    void setUp() {
        AutoTypeChecker.setSafeMode(true);
    }

    @AfterEach
    void tearDown() {
        AutoTypeChecker.setSafeMode(true);
    }

    @Test
    void dangerousJdkTypesRejected() {
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.Runtime"),
            "java.lang.Runtime must be rejected");
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.ProcessBuilder"),
            "java.lang.ProcessBuilder must be rejected");
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.ProcessImpl"),
            "java.lang.ProcessImpl must be rejected");
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.ClassLoader"),
            "java.lang.ClassLoader must be rejected");
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.Thread"),
            "java.lang.Thread must be rejected");
    }

    @Test
    void dangerousReflectionTypesRejected() {
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.reflect.Method"),
            "java.lang.reflect.Method must be rejected");
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.reflect.Field"),
            "java.lang.reflect.Field must be rejected");
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.reflect.Constructor"),
            "java.lang.reflect.Constructor must be rejected");
    }

    @Test
    void dangerousIoTypesRejected() {
        assertFalse(AutoTypeChecker.isTypeAllowed("java.io.FileOutputStream"),
            "java.io.FileOutputStream must be rejected");
        assertFalse(AutoTypeChecker.isTypeAllowed("java.io.FileInputStream"),
            "java.io.FileInputStream must be rejected");
        assertFalse(AutoTypeChecker.isTypeAllowed("java.io.FileWriter"),
            "java.io.FileWriter must be rejected");
    }

    @Test
    void safeJdkTypesAllowed() {
        assertTrue(AutoTypeChecker.isTypeAllowed("java.lang.String"),
            "java.lang.String must be allowed");
        assertTrue(AutoTypeChecker.isTypeAllowed("java.lang.Integer"),
            "java.lang.Integer must be allowed");
        assertTrue(AutoTypeChecker.isTypeAllowed("java.lang.Long"),
            "java.lang.Long must be allowed");
        assertTrue(AutoTypeChecker.isTypeAllowed("java.util.ArrayList"),
            "java.util.ArrayList must be allowed");
        assertTrue(AutoTypeChecker.isTypeAllowed("java.util.HashMap"),
            "java.util.HashMap must be allowed");
        assertTrue(AutoTypeChecker.isTypeAllowed("java.time.LocalDateTime"),
            "java.time.LocalDateTime must be allowed");
    }

    @Test
    void safeModeOffStillRejectsBlacklistedTypes() {
        // 黑名单检查在 safeMode 检查之前执行，即使 safeMode=false 也会拒绝黑名单类型
        AutoTypeChecker.setSafeMode(false);
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.Runtime"),
            "blacklisted types must be rejected even when safeMode is off");
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.ProcessBuilder"),
            "blacklisted types must be rejected even when safeMode is off");
    }

    @Test
    void safeModeOffAllowsNonBlacklistedNonWhitelisted() {
        AutoTypeChecker.setSafeMode(false);
        // safeMode=false 时，非黑名单类型直接放行（无需在白名单中）
        assertTrue(AutoTypeChecker.isTypeAllowed("com.example.NonExistingClass"),
            "when safeMode is off, non-blacklisted types should be allowed");
    }

    @Test
    void whitelistAdditionAllowsType() {
        AutoTypeChecker.addToWhitelist("com.example.MyBean");
        assertTrue(AutoTypeChecker.isTypeAllowed("com.example.MyBean"),
            "explicitly whitelisted type must be allowed");
    }

    @Test
    void arrayOfTypeInheritsPolicy() {
        assertFalse(AutoTypeChecker.isTypeAllowed("[Ljava.lang.Runtime;"),
            "array of blacklisted type must be rejected");
        assertFalse(AutoTypeChecker.isTypeAllowed("[Ljava.lang.ProcessBuilder;"),
            "array of blacklisted type must be rejected");
        assertFalse(AutoTypeChecker.isTypeAllowed("[Ljava.lang.Thread;"),
            "array of blacklisted type must be rejected");
        assertTrue(AutoTypeChecker.isTypeAllowed("[Ljava.lang.String;"),
            "array of safe type must be allowed");
        assertTrue(AutoTypeChecker.isTypeAllowed("[Ljava.lang.Integer;"),
            "array of safe type must be allowed");
    }

    @Test
    void nullAndEmptyTypeAllowedByDesign() {
        // AutoTypeChecker 设计上对 null 和空字符串返回 true（视为无类型信息，不拦截）
        assertTrue(AutoTypeChecker.isTypeAllowed(""),
            "empty type name returns true by design (no type info to check)");
        assertTrue(AutoTypeChecker.isTypeAllowed((String) null),
            "null type name returns true by design (no type info to check)");
    }

    @Test
    void innerClassOfBlacklistedRejected() {
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.Runtime$Foo"),
            "inner class of blacklisted type must be rejected");
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.ProcessBuilder$Bar"),
            "inner class of blacklisted type must be rejected");
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.ProcessImpl$Foo$Bar"),
            "nested inner class of blacklisted type must be rejected");
    }

    @Test
    void whitelistedPackagePrefixAllowed() {
        AutoTypeChecker.addWhitelistPackage("com.remisoft.common.json");
        assertTrue(AutoTypeChecker.isTypeAllowed("com.remisoft.common.json.TestBean"),
            "type in whitelisted package prefix must be allowed");
        assertTrue(AutoTypeChecker.isTypeAllowed("com.remisoft.common.json.sub.SomeClass"),
            "type in whitelisted package prefix must be allowed");
    }
}
