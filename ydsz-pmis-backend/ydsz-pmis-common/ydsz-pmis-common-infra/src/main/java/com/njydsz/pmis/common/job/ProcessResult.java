package com.njydsz.pmis.common.job;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务处理结果（P0-4 MapReduce）。
 *
 * <p>success=true 表示处理成功，result 为业务结果（可空）；
 * success=false 表示处理失败，errorMessage 携带错误信息。
 *
 * <p>对标 PowerJob 的 ProcessResult，业务侧在 {@link MapProcessor#process(MapContext)}
 * 或 {@link MapReduceProcessor#reduce(MapContext, java.util.List)} 中返回本对象。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class ProcessResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否处理成功 */
    private final boolean success;

    /** 业务结果（可空） */
    private final String result;

    /** 错误信息（失败时填充） */
    private final String errorMessage;

    public ProcessResult(boolean success, String result) {
        this(success, result, null);
    }

    public ProcessResult(boolean success, String result, String errorMessage) {
        this.success = success;
        this.result = result;
        this.errorMessage = errorMessage;
    }

    /**
     * 构造成功结果（无业务返回值）。
     *
     * @return 成功结果
     */
    public static ProcessResult success() {
        return new ProcessResult(true, null);
    }

    /**
     * 构造成功结果（携带业务返回值）。
     *
     * @param result 业务结果
     * @return 成功结果
     */
    public static ProcessResult success(String result) {
        return new ProcessResult(true, result);
    }

    /**
     * 构造失败结果。
     *
     * @param errorMessage 错误信息
     * @return 失败结果
     */
    public static ProcessResult failed(String errorMessage) {
        return new ProcessResult(false, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getResult() {
        return result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
