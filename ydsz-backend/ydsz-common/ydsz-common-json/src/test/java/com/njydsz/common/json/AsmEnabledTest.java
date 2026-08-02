package com.njydsz.common.json;

import com.njydsz.common.json.asm.AsmBeanCodecGenerator;
import com.njydsz.common.json.asm.AsmSerializer;
import com.njydsz.common.json.autotype.AutoTypeChecker;
import com.njydsz.common.json.cache.AsmCodecCache;
import com.njydsz.common.json.provider.SerializationProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 ASM 字节码生成路径真正生效（P0-A1 回归闸门）。
 *
 * <p>历史：SecureAsmClassLoader.defineInternal 强制类名以 "generated." 开头而
 * 实际生成类名是 {@code <beanType>_ASM_Serializer}，defineClass 必抛 SecurityException，
 * 被 catch(Throwable) 吞掉 → ASM 永远降级反射。修复：改为后缀校验。
 */
class AsmEnabledTest {

    @BeforeEach
    void setUp() {
        AutoTypeChecker.setSafeMode(false);
        AsmCodecCache.clearCache();
    }

    @AfterEach
    void tearDown() {
        AutoTypeChecker.setSafeMode(true);
    }

    @Test
    void asmSerializerGeneratedForTopLevelBean() throws Exception {
        // 直接调 generateSerializer 捕获异常，定位 ASM 失败根因
        // （SLF4J 无 provider，debug 日志不输出，异常被内部 catch(Exception) 吞掉）
        try {
            long before = SerializationProvider.getAsmDowngradeCount();
            AsmBeanCodecGenerator.generateSerializer(TestBean.class);
            assertTrue(SerializationProvider.getAsmDowngradeCount() == before,
                "generateSerializer should not downgrade");
            // generateSerializer 只生成/define class，不 newInstance
            // getOrCreate 会 additionally newInstance 并缓存
            AsmSerializer<?> ser = AsmCodecCache.getOrCreateSerializerForType(TestBean.class);
            assertNotNull(ser, "ASM serializer should be generated for top-level bean");
        } catch (Exception e) {
            e.printStackTrace();
            fail("ASM generateSerializer failed: " + e.getClass().getName() + ": " + e.getMessage());
        }

    @Test
    void noAsmDowngradeOnFreshBean() {
        long before = SerializationProvider.getAsmDowngradeCount();
        AsmCodecCache.getOrCreateSerializerForType(TestBean.class);
        long after = SerializationProvider.getAsmDowngradeCount();
        assertEquals(before, after,
            "ASM downgrade delta must be 0 (actual=" + (after - before) + ")");
    }

    @Test
    void asmRoundTripCorrect() {
        TestBean bean = new TestBean();
        bean.setId(42);
        bean.setName("alice");
        String json = YdszJson.toJson(bean);
        assertTrue(json.contains("\"id\":42"));
        assertTrue(json.contains("\"name\":\"alice\""));

        TestBean back = YdszJson.toObject(json, TestBean.class);
        assertNotNull(back);
        assertEquals(42, back.getId());
        assertEquals("alice", back.getName());
    }
}
