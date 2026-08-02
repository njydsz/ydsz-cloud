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
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 验证 ASM 字节码生成路径真正生效（P0-A1 回归闸门）。
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
        try {
            long before = SerializationProvider.getAsmDowngradeCount();
            AsmBeanCodecGenerator.generateSerializer(TestBean.class);
            assertTrue(before == SerializationProvider.getAsmDowngradeCount(),
                "generateSerializer should not trigger downgrade");
            AsmSerializer<?> ser = AsmCodecCache.getOrCreateSerializerForType(TestBean.class);
            assertNotNull(ser, "ASM serializer must be non-null for top-level bean");
        } catch (Exception e) {
            e.printStackTrace();
            fail("generateSerializer failed: " + e.getClass().getName() + ": " + e.getMessage());
        }
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
    }
}
