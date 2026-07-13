package com.njydsz.pmis.common.json;

import com.njydsz.pmis.common.json.annotation.YdszJsonClass;
import com.njydsz.pmis.common.json.autotype.AutoTypeChecker;
import com.njydsz.pmis.common.json.exception.JsonDeserializationException;
import com.njydsz.pmis.common.json.reader.JSONReader;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import com.njydsz.pmis.common.json.config.DeserializationConfig;

@DisplayName("YdszJson 安全测试")
class YdszJsonSecurityTest {

    @BeforeEach
    void resetAutoType() {
        AutoTypeChecker.reset();
    }

    @AfterEach
    void cleanupAutoType() {
        AutoTypeChecker.reset();
    }

    // ==================== AutoTypeChecker 黑名单 ====================

    @Nested
    @DisplayName("AutoTypeChecker 黑名单测试")
    class BlacklistTests {

        @Test
        @DisplayName("阻止 JdbcRowSetImpl")
        void blockJdbcRowSetImpl() {
            assertFalse(AutoTypeChecker.isTypeAllowed("com.sun.rowset.JdbcRowSetImpl"));
        }

        @Test
        @DisplayName("阻止 TemplatesImpl")
        void blockTemplatesImpl() {
            assertFalse(AutoTypeChecker.isTypeAllowed("org.apache.xalan.xsltc.trax.TemplatesImpl"));
        }

        @Test
        @DisplayName("阻止 ProcessBuilder")
        void blockProcessBuilder() {
            assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.ProcessBuilder"));
        }

        @Test
        @DisplayName("阻止 Runtime")
        void blockRuntime() {
            assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.Runtime"));
        }

        @Test
        @DisplayName("阻止 InitialContext")
        void blockInitialContext() {
            assertFalse(AutoTypeChecker.isTypeAllowed("javax.naming.InitialContext"));
        }

        @Test
        @DisplayName("阻止 InvokerTransformer")
        void blockInvokerTransformer() {
            assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.apache.commons.collections.functors.InvokerTransformer"));
        }

        @Test
        @DisplayName("checkType 对黑名单类抛出异常")
        void checkTypeThrowsForBlacklisted() {
            assertThrows(JsonDeserializationException.class,
                () -> AutoTypeChecker.checkType("com.sun.rowset.JdbcRowSetImpl"));
        }

        @Test
        @DisplayName("黑名单在 SafeMode=false 时仍然生效")
        void blacklistStillWorksWhenSafeModeOff() {
            try {
                AutoTypeChecker.setSafeMode(false);
                assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.Runtime"));
            } finally {
                AutoTypeChecker.setSafeMode(true);
            }
        }
    }

    // ==================== AutoTypeChecker 白名单 ====================

    @Nested
    @DisplayName("AutoTypeChecker 白名单测试")
    class WhitelistTests {

        @Test
        @DisplayName("允许 String 类")
        void allowString() {
            assertTrue(AutoTypeChecker.isTypeAllowed("java.lang.String"));
        }

        @Test
        @DisplayName("允许 Integer 类")
        void allowInteger() {
            assertTrue(AutoTypeChecker.isTypeAllowed("java.lang.Integer"));
        }

        @Test
        @DisplayName("允许 HashMap 类")
        void allowHashMap() {
            assertTrue(AutoTypeChecker.isTypeAllowed("java.util.HashMap"));
        }

        @Test
        @DisplayName("允许 ArrayList 类")
        void allowArrayList() {
            assertTrue(AutoTypeChecker.isTypeAllowed("java.util.ArrayList"));
        }

        @Test
        @DisplayName("允许 LocalDateTime 类")
        void allowLocalDateTime() {
            assertTrue(AutoTypeChecker.isTypeAllowed("java.time.LocalDateTime"));
        }

        @Test
        @DisplayName("允许 BigDecimal 类")
        void allowBigDecimal() {
            assertTrue(AutoTypeChecker.isTypeAllowed("java.math.BigDecimal"));
        }

        @Test
        @DisplayName("checkType 对白名单类不抛异常")
        void checkTypeNoThrowForWhitelisted() {
            assertDoesNotThrow(() -> AutoTypeChecker.checkType("java.lang.String"));
        }
    }

    // ==================== 安全模式 ====================

    @Nested
    @DisplayName("安全模式测试")
    class SafeModeTests {

        @Test
        @DisplayName("默认启用安全模式")
        void safeModeEnabledByDefault() {
            assertTrue(AutoTypeChecker.isSafeMode());
        }

        @Test
        @DisplayName("SafeMode=true 时拒绝未知类")
        void safeModeRejectsUnknown() {
            assertFalse(AutoTypeChecker.isTypeAllowed("com.example.UnknownClass"));
        }

        @Test
        @DisplayName("SafeMode=false 时允许未知类（非黑名单）")
        void safeModeOffAllowsUnknown() {
            try {
                AutoTypeChecker.setSafeMode(false);
                assertTrue(AutoTypeChecker.isTypeAllowed("com.example.UnknownClass"));
            } finally {
                AutoTypeChecker.setSafeMode(true);
            }
        }
    }

    // ==================== 显式白名单 ====================

    @Nested
    @DisplayName("显式白名单测试")
    class ExplicitWhitelistTests {

        @Test
        @DisplayName("addToWhitelist 添加自定义类")
        void addToWhitelist() {
            String className = "com.example.MySafeClass";
            assertFalse(AutoTypeChecker.isTypeAllowed(className));
            AutoTypeChecker.addToWhitelist(className);
            assertTrue(AutoTypeChecker.isTypeAllowed(className));
        }

        @Test
        @DisplayName("removeFromWhitelist 移除自定义类")
        void removeFromWhitelist() {
            String className = "com.example.TempClass";
            AutoTypeChecker.addToWhitelist(className);
            assertTrue(AutoTypeChecker.isTypeAllowed(className));
            AutoTypeChecker.removeFromWhitelist(className);
            assertFalse(AutoTypeChecker.isTypeAllowed(className));
        }
    }

    // ==================== 自定义黑名单 ====================

    @Nested
    @DisplayName("自定义黑名单测试")
    class CustomBlacklistTests {

        @Test
        @DisplayName("addToBlacklist 添加自定义危险类")
        void addToBlacklist() {
            String className = "com.example.DangerousClass";
            AutoTypeChecker.addToBlacklist(className);
            assertFalse(AutoTypeChecker.isTypeAllowed(className));
        }

        @Test
        @DisplayName("removeFromBlacklist 移除自定义类")
        void removeFromBlacklist() {
            String className = "com.example.TempDangerous";
            AutoTypeChecker.addToBlacklist(className);
            assertFalse(AutoTypeChecker.isTypeAllowed(className));
            AutoTypeChecker.removeFromBlacklist(className);
            // 移除后，SafeMode=true 下仍不允许（不在白名单中）
            assertFalse(AutoTypeChecker.isTypeAllowed(className));
        }
    }

    // ==================== null 和空值处理 ====================

    @Nested
    @DisplayName("null 和空值处理")
    class NullHandlingTests {

        @Test
        @DisplayName("isTypeAllowed null 类名返回 true")
        void isTypeAllowedNullClassName() {
            assertTrue(AutoTypeChecker.isTypeAllowed((String) null));
        }

        @Test
        @DisplayName("isTypeAllowed 空字符串返回 true")
        void isTypeAllowedEmptyClassName() {
            assertTrue(AutoTypeChecker.isTypeAllowed(""));
        }

        @Test
        @DisplayName("isTypeAllowed null Class 返回 true")
        void isTypeAllowedNullClass() {
            assertTrue(AutoTypeChecker.isTypeAllowed((Class<?>) null));
        }

        @Test
        @DisplayName("checkType null 不抛异常")
        void checkTypeNullNoThrow() {
            assertDoesNotThrow(() -> AutoTypeChecker.checkType((Class<?>) null));
            assertDoesNotThrow(() -> AutoTypeChecker.checkType((String) null));
        }
    }

    // ==================== 重置 ====================

    @Nested
    @DisplayName("重置测试")
    class ResetTests {

        @Test
        @DisplayName("reset 恢复初始状态")
        void resetRestoresInitialState() {
            AutoTypeChecker.addToWhitelist("com.example.Test");
            AutoTypeChecker.addToBlacklist("com.example.Bad");
            AutoTypeChecker.setSafeMode(false);

            AutoTypeChecker.reset();

            assertTrue(AutoTypeChecker.isSafeMode());
            assertFalse(AutoTypeChecker.isTypeAllowed("com.example.Test"));
        }
    }

    // ==================== 深度限制 ====================

    @Nested
    @DisplayName("深度限制测试")
    class DepthLimitTests {

        @Test
        @DisplayName("JSONReader Feature.LimitDepth 默认启用")
        void limitDepthEnabledByDefault() {
            assertTrue(JSONReader.Feature.LimitDepth.isEnabledByDefault());
        }

        @Test
        @DisplayName("超深嵌套 JSON 解析应抛出异常")
        void deeplyNestedJsonThrows() {
            // 设置较低的最大深度限制
            DeserializationConfig config =
                DeserializationConfig.getInstance();
            int originalMaxDepth = config.getMaxDepth();
            try {
                config.setMaxDepth(10);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 20; i++) {
                    sb.append("{\"a\":");
                }
                sb.append("1");
                for (int i = 0; i < 20; i++) {
                    sb.append("}");
                }
                assertThrows(Exception.class, () -> YdszJson.toObject(sb.toString(), Map.class));
            } finally {
                config.setMaxDepth(originalMaxDepth);
            }
        }
    }

    // ==================== JSON 大小限制 ====================

    @Nested
    @DisplayName("JSON 大小限制测试")
    class JsonSizeLimitTests {

        @Test
        @DisplayName("JSONReader Feature.LimitStringLength 默认启用")
        void limitStringLengthEnabledByDefault() {
            assertTrue(JSONReader.Feature.LimitStringLength.isEnabledByDefault());
        }

        @Test
        @DisplayName("JSONReader Feature.LimitObjectSize 默认启用")
        void limitObjectSizeEnabledByDefault() {
            assertTrue(JSONReader.Feature.LimitObjectSize.isEnabledByDefault());
        }

        @Test
        @DisplayName("JSONReader Feature.LimitArraySize 默认启用")
        void limitArraySizeEnabledByDefault() {
            assertTrue(JSONReader.Feature.LimitArraySize.isEnabledByDefault());
        }
    }

    // ==================== 循环引用检测 ====================

    @YdszJsonClass
    static class CircularNode {
        String name;
        CircularNode next;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public CircularNode getNext() { return next; }
        public void setNext(CircularNode next) { this.next = next; }
    }

    @Test
    @DisplayName("循环引用序列化不应无限递归")
    void circularReferenceShouldNotInfiniteLoop() {
        CircularNode a = new CircularNode();
        a.setName("A");
        CircularNode b = new CircularNode();
        b.setName("B");
        a.setNext(b);
        b.setNext(a); // 循环引用

        assertDoesNotThrow(() -> {
            String json = YdszJson.toJson(a);
            assertNotNull(json);
        });
    }
}
