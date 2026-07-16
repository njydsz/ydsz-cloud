package com.njydsz.common.json;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.njydsz.common.json.asm.GraalVmDetector;

/**
 * GraalVM Native Image 环境检测器测试。
 *
 * @since 1.4.0
 */
class GraalVmDetectorTest {

    @Test
    void testIsInNativeImageReturnsFalseOnJVM() {
        // 在常规 JVM 环境中运行，应返回 false
        assertFalse(GraalVmDetector.isInNativeImage());
    }

    @Test
    void testIsAsmAllowedReturnsTrueOnJVM() {
        // 在常规 JVM 环境中 ASM 应可用
        assertTrue(GraalVmDetector.isAsmAllowed());
    }

    @Test
    void testConsistency() {
        // 多次调用应返回一致的结果
        boolean first = GraalVmDetector.isInNativeImage();
        boolean second = GraalVmDetector.isInNativeImage();
        assertEquals(first, second);

        boolean asmFirst = GraalVmDetector.isAsmAllowed();
        boolean asmSecond = GraalVmDetector.isAsmAllowed();
        assertEquals(asmFirst, asmSecond);
    }

    @Test
    void testAsmAllowedIsOppositeOfNativeImage() {
        // ASM 允许 == 非 Native Image
        assertEquals(!GraalVmDetector.isInNativeImage(), GraalVmDetector.isAsmAllowed());
    }
}
