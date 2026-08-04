package com.remisoft.common.json;

import org.junit.jupiter.api.Test;

import com.remisoft.common.json.asm.AsmBeanCodecGenerator;
import com.remisoft.common.json.asm.AsmSerializer;
import com.remisoft.common.json.autotype.AutoTypeChecker;
import com.remisoft.common.json.cache.AsmCodecCache;

/**
 * 诊断测试：直接调用 ASM 生成方法，捕获并打印真实异常。
 */
class AsmDiagnosisTest {

    @Test
    void diagnoseAsmGenerationFailure() {
        AutoTypeChecker.setSafeMode(false);
        AsmCodecCache.clearCache();

        System.out.println("=== ASM 诊断开始 ===");
        System.out.println("isAsmAvailable: " + AsmBeanCodecGenerator.isAsmAvailable());
        System.out.println("GeneratedClassCount: " + AsmBeanCodecGenerator.getGeneratedClassCount());

        try {
            System.out.println("尝试直接调用 generateSerializer(TestBean.class)...");
            Class<? extends AsmSerializer<?>> clazz = AsmBeanCodecGenerator.generateSerializerForType(TestBean.class);
            System.out.println("生成成功: " + clazz.getName());
        } catch (Throwable e) {
            System.out.println("生成失败: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(System.out);
            Throwable cause = e.getCause();
            while (cause != null) {
                System.out.println("  Caused by: " + cause.getClass().getName() + ": " + cause.getMessage());
                cause = cause.getCause();
            }
        }

        System.out.println("=== ASM 诊断结束 ===");
        AutoTypeChecker.setSafeMode(true);
    }
}
