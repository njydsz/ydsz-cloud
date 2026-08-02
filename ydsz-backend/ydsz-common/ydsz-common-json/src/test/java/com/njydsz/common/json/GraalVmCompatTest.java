package com.njydsz.common.json;

import java.lang.reflect.Field;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.njydsz.common.json.asm.AsmBeanCodecGenerator;
import com.njydsz.common.json.asm.GraalVmDetector;
import com.njydsz.common.json.autotype.AutoTypeChecker;
import com.njydsz.common.json.cache.AsmCodecCache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GraalVM 兼容性与 ASM 回退路径验证（阶段三-3.2）。
 *
 * <p>验证目标：</p>
 * <ol>
 *   <li>{@link GraalVmDetector} 能正确检测当前环境（非 Native Image 下应允许 ASM）</li>
 *   <li>{@link AsmBeanCodecGenerator#isAsmAvailable()} 接入 GraalVmDetector 后逻辑正确</li>
 *   <li>当 ASM 被禁用时（模拟 Native Image 场景），序列化/反序列化能正确回退到反射路径</li>
 *   <li>ASM 路径与反射路径的 round-trip 结果一致（GraalVM 降级后行为不变形）</li>
 * </ol>
 *
 * <p><b>无法在常规 JVM 中真正模拟 GraalVM Native Image</b>，因此本测试通过
 * 反射强制将 {@code degradedToReflection=true} 来模拟"ASM 不可用"场景，
 * 验证回退路径的功能正确性。真正的 GraalVM 集成测试需在 native-image 环境中执行。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class GraalVmCompatTest {

    @BeforeEach
    void setUp() {
        AutoTypeChecker.setSafeMode(false);
        AsmCodecCache.clearCache();
    }

    @AfterEach
    void tearDown() {
        AutoTypeChecker.setSafeMode(true);
        // 恢复 ASM 可用状态，避免污染其他测试
        restoreAsmAvailable();
        AsmCodecCache.clearCache();
    }

    /**
     * 验证在常规 JVM 环境下，GraalVmDetector 不触发 Native Image 模式。
     */
    @Test
    void graalVmDetectorReturnsFalseInRegularJvm() {
        assertFalse(GraalVmDetector.isInNativeImage(),
            "在常规 JVM 中 isInNativeImage() 必须返回 false");
        assertTrue(GraalVmDetector.isAsmAllowed(),
            "在常规 JVM 中 isAsmAllowed() 必须返回 true");
    }

    /**
     * 验证 GraalVmDetector 接入 isAsmAvailable 后，常规环境下 ASM 仍可用。
     *
     * <p>这是阶段三-3.2 的核心修复验证：此前 GraalVmDetector 是死代码，
     * 修复后 isAsmAvailable() 应在常规环境下返回 true。</p>
     */
    @Test
    void asmAvailableInRegularJvmAfterGraalVmIntegration() {
        assertTrue(AsmBeanCodecGenerator.isAsmAvailable(),
            "常规 JVM 环境下 ASM 必须可用（GraalVmDetector 接入后不应误判）");
    }

    /**
     * 模拟 Native Image 场景：当 ASM 被禁用时，序列化必须回退到反射并保持正确性。
     *
     * <p>通过反射设置 degradedToReflection=true 模拟"ASM 不可用"，
     * 验证 round-trip 结果与 ASM 路径一致。</p>
     */
    @Test
    void reflectionFallbackRoundTripCorrectWhenAsmDisabled() throws Exception {
        // 先用 ASM 路径生成一次基准结果
        TestBean asmBean = new TestBean();
        asmBean.setId(100);
        asmBean.setName("asm-baseline");
        String asmJson = YdszJson.toJson(asmBean);
        AsmCodecCache.clearCache();

        // 模拟 ASM 不可用（等同于 GraalVM Native Image 中的行为）
        forceDegradedToReflection(true);

        // 验证 ASM 确实被禁用
        assertFalse(AsmBeanCodecGenerator.isAsmAvailable(),
            "降级后 isAsmAvailable() 必须返回 false");

        // 反射路径 round-trip
        TestBean reflectionBean = new TestBean();
        reflectionBean.setId(100);
        reflectionBean.setName("asm-baseline");
        String reflectionJson = YdszJson.toJson(reflectionBean);

        // 两路径输出的 JSON 应一致（字段顺序、值均相同）
        assertEquals(asmJson, reflectionJson,
            "反射路径与 ASM 路径的序列化结果必须一致");

        TestBean back = YdszJson.toObject(reflectionJson, TestBean.class);
        assertNotNull(back, "反射路径反序列化结果不能为 null");
        assertEquals(100, back.getId(), "反射路径 id 必须正确");
        assertEquals("asm-baseline", back.getName(), "反射路径 name 必须正确");
    }

    /**
     * 验证 ASM 禁用→恢复的过程可逆，不会永久影响全局状态。
     */
    @Test
    void asmStateRestorableAfterDegradation() throws Exception {
        assertTrue(AsmBeanCodecGenerator.isAsmAvailable(),
            "初始状态 ASM 必须可用");

        forceDegradedToReflection(true);
        assertFalse(AsmBeanCodecGenerator.isAsmAvailable(),
            "降级后 ASM 必须不可用");

        restoreAsmAvailable();
        assertTrue(AsmBeanCodecGenerator.isAsmAvailable(),
            "恢复后 ASM 必须再次可用");
    }

    // ==================== 辅助方法：通过反射控制 ASM 状态 ====================

    /**
     * 强制设置 AsmBeanCodecGenerator.degradedToReflection 字段值。
     *
     * <p>用于模拟 GraalVM Native Image 中 ASM 不可用的场景。
     * 使用反射访问 private static 字段，仅限测试使用。</p>
     */
    private static void forceDegradedToReflection(boolean value) throws Exception {
        Field field = AsmBeanCodecGenerator.class.getDeclaredField("degradedToReflection");
        field.setAccessible(true);
        field.setBoolean(null, value);
    }

    /**
     * 恢复 degradedToReflection 为 false。
     */
    private static void restoreAsmAvailable() {
        try {
            forceDegradedToReflection(false);
        } catch (Exception ignored) {
            // 恢复失败不影响测试结论
        }
    }
}
