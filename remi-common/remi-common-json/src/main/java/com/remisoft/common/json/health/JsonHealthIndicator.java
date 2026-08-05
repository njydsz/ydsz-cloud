package com.remisoft.common.json.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.remisoft.common.json.asm.AsmBeanCodecGenerator;
import com.remisoft.common.json.asm.GraalVmDetector;
import com.remisoft.common.json.autotype.AutoTypeChecker;
import com.remisoft.common.json.internal.JsonConfig;
import com.remisoft.common.json.provider.SerializationProvider;

/**
 * RemiJson 健康检查指标。
 *
 * <p>检查 RemiJson 引擎的运行状态：
 * <ul>
 *   <li>AutoType SafeMode 是否开启（安全关键）</li>
 *   <li>GraalVM Native Image 检测 + ASM 可用状态（兼容性）</li>
 *   <li>核心配置摘要（命名策略、大小限制、深度限制）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class JsonHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        JsonConfig config = JsonConfig.getInstance();
        boolean safeMode = AutoTypeChecker.isSafeMode();

        Health.Builder builder = Health.up();
        builder.withDetail("safeMode", safeMode);
        builder.withDetail("namingStrategy", config.getNamingStrategy());
        builder.withDetail("maxJsonSize", config.getMaxJsonSize());
        // 反序列化深度限制（新增泛型递归深度上限，P0 已实施）
        builder.withDetail("maxDepth", config.getMaxDepth());
        builder.withDetail("maxGenericDepth", config.getMaxGenericDepth());

        // ASM 运行时状态
        builder.withDetail("graalVmNativeImage", GraalVmDetector.isInNativeImage());
        builder.withDetail("asmAvailable", AsmBeanCodecGenerator.isAsmAvailable());
        builder.withDetail("asmDowngradeCount", SerializationProvider.getAsmDowngradeCount());

        if (!safeMode) {
            builder.withDetail("warning", "AutoType SafeMode is disabled; RCE risk exists. "
                + "Enable via remi.json.safe-mode=true.");
        }

        return builder.build();
    }
}
