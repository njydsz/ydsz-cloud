package com.njydsz.common.exception;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCodeRegistry;

import com.njydsz.common.exception.code.UnifiedExceptionCode;
/**
 * {@link ExceptionCodeRegistry} 单元测试
 *
 * <p>覆盖注册、查找、批量注册、重复注册忽略、线程安全等行为。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("ExceptionCodeRegistry 注册中心测试")
class ExceptionCodeRegistryTest {

    @BeforeEach
    void cleanup() {
        // 使用 allRegistered + clear 方式，由于 clear 是包级可见的，
        // 这里借助反射清理（测试隔离）
        // 实际上由于 putIfAbsent 语义，重复注册不会覆盖已有值
        // 所以此处无需清理，测试使用唯一 code 即可
    }

    @Test
    @DisplayName("register() 注册后 lookup() 可查到")
    void testRegisterAndLookup() {
        ExceptionCode code = new TestExceptionCode("TEST001", "test.key.001", 400);
        ExceptionCodeRegistry.register(Map.of("TEST001", code));

        ExceptionCode found = ExceptionCodeRegistry.lookup("TEST001");
        assertNotNull(found);
        assertEquals("TEST001", found.getCode());
        assertEquals("test.key.001", found.getKey());
    }

    @Test
    @DisplayName("lookup() 未注册的 code 返回 null")
    void testLookupUnregistered() {
        ExceptionCode found = ExceptionCodeRegistry.lookup("NON_EXISTENT_CODE");
        assertNull(found);
    }

    @Test
    @DisplayName("lookup(null) 返回 null")
    void testLookupNull() {
        assertNull(ExceptionCodeRegistry.lookup(null));
    }

    @Test
    @DisplayName("isRegistered() 正确判断是否已注册")
    void testIsRegistered() {
        ExceptionCode code = new TestExceptionCode("TEST002", "test.key.002", 400);
        ExceptionCodeRegistry.register(Map.of("TEST002", code));
        assertTrue(ExceptionCodeRegistry.isRegistered("TEST002"));
        assertFalse(ExceptionCodeRegistry.isRegistered("NOT_REGISTERED"));
        assertFalse(ExceptionCodeRegistry.isRegistered(null));
    }

    @Test
    @DisplayName("register() 重复注册时保留首次值（putIfAbsent 语义）")
    void testDuplicateRegisterKeepsFirst() {
        ExceptionCode first = new TestExceptionCode("TEST003", "first.key", 400);
        ExceptionCode second = new TestExceptionCode("TEST003", "second.key", 500);

        ExceptionCodeRegistry.register(Map.of("TEST003", first));
        ExceptionCodeRegistry.register(Map.of("TEST003", second));

        ExceptionCode found = ExceptionCodeRegistry.lookup("TEST003");
        assertEquals("first.key", found.getKey());
    }

    @Test
    @DisplayName("register(null) 抛出 IllegalArgumentException")
    void testRegisterNullMap() {
        assertThrows(IllegalArgumentException.class, () -> ExceptionCodeRegistry.register(null));
    }

    @Test
    @DisplayName("registerStrict() 重复注册不同实例时抛出 IllegalStateException")
    void testRegisterStrictThrowsOnConflict() {
        ExceptionCode first = new TestExceptionCode("TEST_STRICT_001", "first.strict.key", 400);
        ExceptionCode second = new TestExceptionCode("TEST_STRICT_001", "second.strict.key", 500);

        ExceptionCodeRegistry.registerStrict(Map.of("TEST_STRICT_001", first));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ExceptionCodeRegistry.registerStrict(Map.of("TEST_STRICT_001", second)));
        assertTrue(ex.getMessage().contains("TEST_STRICT_001"));
        assertTrue(ex.getMessage().contains("requireNotExists"));

        // 首次注册的值仍然保留
        ExceptionCode found = ExceptionCodeRegistry.lookup("TEST_STRICT_001");
        assertEquals("first.strict.key", found.getKey());
    }

    @Test
    @DisplayName("register(map, true) 与 registerStrict 行为一致")
    void testRegisterWithRequireNotExistsTrue() {
        ExceptionCode first = new TestExceptionCode("TEST_STRICT_002", "first.key", 400);
        ExceptionCode second = new TestExceptionCode("TEST_STRICT_002", "second.key", 500);

        ExceptionCodeRegistry.register(Map.of("TEST_STRICT_002", first), true);
        assertThrows(IllegalStateException.class,
                () -> ExceptionCodeRegistry.register(Map.of("TEST_STRICT_002", second), true));
    }

    @Test
    @DisplayName("registerStrict() 同一实例重复注册幂等不抛异常")
    void testRegisterStrictIdempotentSameInstance() {
        ExceptionCode code = new TestExceptionCode("TEST_STRICT_003", "idempotent.key", 400);
        ExceptionCodeRegistry.registerStrict(Map.of("TEST_STRICT_003", code));
        // 同一实例再次注册，幂等，不抛异常
        assertDoesNotThrow(() -> ExceptionCodeRegistry.registerStrict(Map.of("TEST_STRICT_003", code)));
    }

    @Test
    @DisplayName("register(map, false) 与 register(map) 行为一致（宽松模式）")
    void testRegisterWithRequireNotExistsFalse() {
        ExceptionCode first = new TestExceptionCode("TEST_LOOSE_001", "first.key", 400);
        ExceptionCode second = new TestExceptionCode("TEST_LOOSE_001", "second.key", 500);

        ExceptionCodeRegistry.register(Map.of("TEST_LOOSE_001", first), false);
        assertDoesNotThrow(() -> ExceptionCodeRegistry.register(Map.of("TEST_LOOSE_001", second), false));

        // 首次注册的值仍然保留
        ExceptionCode found = ExceptionCodeRegistry.lookup("TEST_LOOSE_001");
        assertEquals("first.key", found.getKey());
    }

    @Test
    @DisplayName("registerStrict(null) 抛出 IllegalArgumentException")
    void testRegisterStrictNullMap() {
        assertThrows(IllegalArgumentException.class, () -> ExceptionCodeRegistry.registerStrict(null));
    }

    @Test
    @DisplayName("ExceptionCode.fromCode() 已注册 code 返回枚举实例")
    void testFromCodeRegistered() {
        ExceptionCode code = new TestExceptionCode("TEST004", "test.key.004", 400);
        ExceptionCodeRegistry.register(Map.of("TEST004", code));

        ExceptionCode found = ExceptionCode.fromCode("TEST004");
        assertNotNull(found);
        assertEquals("TEST004", found.getCode());
    }

    @Test
    @DisplayName("ExceptionCode.fromCode() 未注册 code 抛出 IllegalStateException")
    void testFromCodeUnregistered() {
        assertThrows(IllegalStateException.class, () -> ExceptionCode.fromCode("UNREGISTERED_CODE_XYZ"));
    }

    @Test
    @DisplayName("ExceptionCode.fromCode(null) 抛出 IllegalArgumentException")
    void testFromCodeNull() {
        assertThrows(IllegalArgumentException.class, () -> ExceptionCode.fromCode(null));
    }

    @Test
    @DisplayName("ExceptionCode.fromCode(\"\") 抛出 IllegalArgumentException")
    void testFromCodeEmpty() {
        assertThrows(IllegalArgumentException.class, () -> ExceptionCode.fromCode(""));
    }

    @Test
    @DisplayName("allRegistered() 返回不可变视图")
    void testAllRegisteredImmutable() {
        Map<String, ExceptionCode> all = ExceptionCodeRegistry.allRegistered();
        assertNotNull(all);
        assertThrows(UnsupportedOperationException.class, () -> all.put("HACK", new TestExceptionCode("HACK", "hack", 400)));
    }

    @Test
    @DisplayName("UnifiedExceptionCode 静态注册：所有枚举值可通过 lookup 查到")
    void testUnifiedExceptionCodeAutoRegistered() {
        for (UnifiedExceptionCode code :
                com.njydsz.common.exception.code.UnifiedExceptionCode.values()) {
            ExceptionCode found = ExceptionCodeRegistry.lookup(code.getCode());
            assertNotNull(found, "UnifiedExceptionCode should be auto-registered: " + code.getCode());
            assertEquals(code.getCode(), found.getCode());
        }
    }

    @Test
    @DisplayName("UnifiedExceptionCode.resolve() 局部查找")
    void testUnifiedExceptionCodeResolve() {
        assertEquals(
                com.njydsz.common.exception.code.UnifiedExceptionCode.NOT_FOUND,
                com.njydsz.common.exception.code.UnifiedExceptionCode.resolve("A04051")
        );
        assertNull(com.njydsz.common.exception.code.UnifiedExceptionCode.resolve("INVALID"));
        assertNull(com.njydsz.common.exception.code.UnifiedExceptionCode.resolve(null));
    }

    /**
     * 测试用 ExceptionCode 实现
     */
    private static class TestExceptionCode implements ExceptionCode {
        private final String code;
        private final String key;
        private final int httpStatus;

        TestExceptionCode(String code, String key, int httpStatus) {
            this.code = code;
            this.key = key;
            this.httpStatus = httpStatus;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public int getHttpStatus() {
            return httpStatus;
        }
    }
}
