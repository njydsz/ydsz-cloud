package com.njydsz.common.core.job;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MapReduce 任务处理结果。
 *
 * @author ydsz-team
 * @since 1.0.0
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
     * 构造成功结果。
     *
     * @return 成功结果
     */
    public static ProcessResult success() {
        return new ProcessResult(true, null, null);
    }

    /**
     * 构造带结果的成功结果。
     *
     * @param result 结果 JSON
     * @return 成功结果
     */
    public static ProcessResult success(String result) {
        return new ProcessResult(true, result, null);
    }

    /**
     * 构造失败结果。
     *
     * @param errorMessage 错误消息
     * @return 失败结果
     */
    public static ProcessResult failed(String errorMessage) {
        return new ProcessResult(false, null, errorMessage);
    }

    /**
     * 构造带结果的失败结果。
     *
     * @param result       结果 JSON
     * @param errorMessage 错误消息
     * @return 失败结果
     */
    public static ProcessResult failed(String result, String errorMessage) {
        return new ProcessResult(false, result, errorMessage);
    }
}
