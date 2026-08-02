package com.njydsz.common.json;

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
 * <p>修复清单：
 * <ol>
 *   <li>SecureAsmClassLoader.defineInternal 前缀检查改后缀校验（_ASM_Serializer/_ASM_Deserializer）</li>
 *   <li>ASM_FLAGS 改用 COMPUTE_MAXS 避免 ASM 9.x 的 NegativeArraySizeException（COMPUTE_FRAMES bug）</li>
 * </ol>
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
    void asmSerializerGeneratedForTopLevelBean() {
        AsmSerializer<?> ser = AsmCodecCache.getOrCreateSerializerForType(TestBean.class);
        assertNotNull(ser,
            "ASM serializer must be non-null — defineClass should pass suffix check and COMPUTE_FRAMES avoided");
    }

    @Test
    void noAsmDowngradeOnFreshBean() {
        long before = SerializationProvider.getAsmDowngradeCount();
        AsmCodecCache.getOrCreateSerializerForType(TestBean.class);
        long after = SerializationProvider.getAsmDowngradeCount();
        assertEquals(before, after, "downgrade delta=" + (after - before));
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
