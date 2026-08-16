package com.njydsz.common.json.provider;

import java.util.Set;
import com.njydsz.common.json.naming.PropertyNamingStrategy;
import com.njydsz.common.json.parser.JsonParserUtil;
import com.njydsz.common.json.provider.SerializationProvider.SerializationContext;
import com.njydsz.common.json.reader.JSONReader;

/**
 * ThreadLocal 快照（用于单次配置序列化的线程安全保存/恢复）。
 *
 * <p>使用 {@link SerializationContext} 合并多个 ThreadLocal 为单一实例，
 * 构造时捕获当前线程的 SerializationContext 配置字段快照，
 * 调用 {@link #restore()} 恢复原始值。避免修改全局单例。</p>
 *
 * <p>注意：仅保存/恢复配置类字段（writeNulls、prettyPrint、circularRefStrategy、
 * serializeEnumUsingOrdinal、excludedFields、dateFormat、failOnError、namingStrategy），
 * 不保存运行时状态字段（sbPool、fastWriterPool、serializingObjects、currentViewClass），
 * 因为运行时状态仅在单次序列化调用内有意义。</p>
 *
 * <p><b>namingStrategy 说明：</b>命名策略存放在 {@link FieldMetadataLoader#NAMING_STRATEGY}
 * 这个独立 ThreadLocal 中（不在 SerializationContext 内）。此前快照未保存它，
 * 导致 {@code YdszJson.toJson(obj, config)} 用不同命名策略序列化后未回滚，
 * 后续默认调用仍使用旧命名策略（配置泄漏）。现已补全。</p>
 *
 * <p><b>useBigDecimal 说明（P0-2 并发安全修复，2026-08-04）：</b>useBigDecimal
 * 从全局 volatile static 改为 ThreadLocal，快照中保存/恢复其值，
 * 确保不同配置的 Mapper 在使用后相互隔离，避免某 Mapper 开启 BigDecimal
 * 后永久影响所有线程、所有 Mapper 的解析行为。</p>
 *
 * <p>1.2.1 起从 {@link SerializationProvider} 内部类提取为独立类，
 * 降低上帝类的复杂度。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ThreadLocalSnapshot {

    private final boolean savedWriteNulls;
    private final boolean savedPrettyPrint;
    private final String savedCircularRefStrategy;
    private final boolean savedSerializeEnumUsingOrdinal;
    private final Set<String> savedExcludedFields;
    private final String savedDateFormat;
    private final boolean savedFailOnError;
    private final PropertyNamingStrategy savedNamingStrategy;
    private final boolean savedUseBigDecimal;

    /**
     * 深度覆盖快照（P0-3：多 Mapper 实例隔离）。
     *
     * <p>保存调用前的线程级 maxDepth / maxGenericDepth / maxParseDepth 覆盖值
     * （null 表示未设置），restore 时原样写回，保证嵌套调用与调用后状态一致。</p>
     */
    private final Integer savedCallMaxDepth;
    private final Integer savedCallMaxGenericDepth;
    private final Integer savedCallParseDepth;

    /**
     * 捕获当前线程的 ThreadLocal 序列化参数快照。
     */
    public ThreadLocalSnapshot() {
        SerializationContext ctx = SerializationContext.CONTEXT.get();
        this.savedWriteNulls = ctx.writeNulls;
        this.savedPrettyPrint = ctx.prettyPrint;
        this.savedCircularRefStrategy = ctx.circularRefStrategy;
        this.savedSerializeEnumUsingOrdinal = ctx.serializeEnumUsingOrdinal;
        this.savedExcludedFields = ctx.excludedFields;
        this.savedDateFormat = ctx.dateFormat;
        this.savedFailOnError = ctx.failOnError;
        this.savedNamingStrategy = FieldMetadataLoader.NAMING_STRATEGY.get();
        this.savedUseBigDecimal = JsonParserUtil.isUseBigDecimal();
        this.savedCallMaxDepth = JSONReader.getCallMaxDepthOverride();
        this.savedCallMaxGenericDepth = JSONReader.getCallMaxGenericDepthOverride();
        this.savedCallParseDepth = JsonParserUtil.getCallParseDepthOverride();
    }

    /**
     * 恢复快照中保存的 ThreadLocal 序列化参数。
     */
    public void restore() {
        SerializationContext ctx = SerializationContext.CONTEXT.get();
        ctx.writeNulls = savedWriteNulls;
        ctx.prettyPrint = savedPrettyPrint;
        ctx.circularRefStrategy = savedCircularRefStrategy;
        ctx.serializeEnumUsingOrdinal = savedSerializeEnumUsingOrdinal;
        ctx.excludedFields = savedExcludedFields;
        ctx.dateFormat = savedDateFormat;
        ctx.failOnError = savedFailOnError;
        FieldMetadataLoader.NAMING_STRATEGY.set(savedNamingStrategy);
        JsonParserUtil.setUseBigDecimal(savedUseBigDecimal);
        JSONReader.setCallDepthOverride(savedCallMaxDepth, savedCallMaxGenericDepth);
        JsonParserUtil.setCallParseDepthOverride(savedCallParseDepth);
    }
}
