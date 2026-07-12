package com.njydsz.pmis.common.tracing;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 链路追踪 Span 记录器 SPI（P2-1 架构优化）。
 *
 * <p>各模块（agent / message / cronjob / literule）实现此接口，
 * 将本模块的追踪数据写入统一的存储（DB / ES / Loki 等）。
 *
 * <p>替代各模块各自定义 TraceContext / TraceService 的重复实现。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public interface TraceSpanRecorder {

    /**
     * 记录一个追踪 Span。
     *
     * @param span 追踪 Span 数据
     */
    void record(TraceSpan span);

    /**
     * 批量记录追踪 Span。
     *
     * @param spans 追踪 Span 列表
     */
    default void recordBatch(Iterable<TraceSpan> spans) {
        if (spans == null) return;
        spans.forEach(this::record);
    }

    /**
     * 检查是否支持指定模块。
     *
     * @param module 模块名
     * @return true 表示本记录器可以处理该模块的追踪数据
     */
    boolean supports(String module);
}
