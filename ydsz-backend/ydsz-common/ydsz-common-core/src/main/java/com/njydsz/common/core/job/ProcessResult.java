package com.njydsz.common.core.job;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MapReduce 任务处理结果
 *
 * <p>封装 {@link MapProcessor#process(MapContext)} 与 {@link MapReduceProcessor} 的返回值，
 * 框架根据 {@link #success} 决定是否触发重试 / 告警 / 归档。
 *
 * <p><b>字段语义：</b>
 * <ul>
 *   <li>{@link #success}：true-成功（可能含业务警告），false-失败（触发重试或人工介入）</li>
 *   <li>{@link #result}：JSON 格式的业务结果，Reduce 阶段可用于跨节点汇总</li>
 *   <li>{@link #errorMessage}：失败时的错误描述，框架会记录到 ydsz_job_log 表</li>
 * </ul>
 *
 * <p><b>使用规范：</b>
 * <ul>
 *   <li>业务方必须通过 {@link #success()} / {@link #success(String)} / {@link #failed(String)} 构造结果</li>
 *   <li>成功也建议填写 {@link #result}，便于 Reduce 阶段统计</li>
 *   <li>{@link #errorMessage} 应包含足够上下文（如失败原因、相关 ID），避免仅写 "fail"</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see MapProcessor
 * @see MapContext
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否成功 */
    private boolean success;

    /** 执行结果 JSON */
    private String result;

    /** 错误消息 */
    private String errorMessage;

    /**
     * 构造成功结果（无附加数据）
     *
     * @return 成功的 {@link ProcessResult}
     */
    public static ProcessResult success() {
        return new ProcessResult(true, null, null);
    }

    /**
     * 构造带结果的成功结果
     *
     * @param result 结果 JSON 字符串
     * @return 成功的 {@link ProcessResult}
     */
    public static ProcessResult success(String result) {
        return new ProcessResult(true, result, null);
    }

    /**
     * 构造失败结果
     *
     * @param errorMessage 错误消息（应包含失败原因 + 上下文）
     * @return 失败的 {@link ProcessResult}
     */
    public static ProcessResult failed(String errorMessage) {
        return new ProcessResult(false, null, errorMessage);
    }

    /**
     * 构造带部分结果的失败结果
     *
     * <p>用于 Reduce 阶段：部分分片已处理完，将中间结果一并上报，便于排查失败原因。
     *
     * @param result       部分结果 JSON
     * @param errorMessage 错误消息
     * @return 失败的 {@link ProcessResult}
     */
    public static ProcessResult failed(String result, String errorMessage) {
        return new ProcessResult(false, result, errorMessage);
    }
}
