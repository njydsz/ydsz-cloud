package com.njydsz.pmis.common.core.job;

/**
 * 任务执行结果。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class ProcessResult {

    /** 是否成功 */
    private final boolean success;
    /** 结果数据 */
    private final String result;
    /** 错误消息 */
    private final String errorMessage;

    /**
     * 构造处理结果。
     *
     * @param success      是否成功
     * @param result       结果数据
     * @param errorMessage 错误消息
     */
    public ProcessResult(boolean success, String result, String errorMessage) {
        this.success = success;
        this.result = result;
        this.errorMessage = errorMessage;
    }

    /**
     * 成功结果。
     *
     * @return 成功结果
     */
    public static ProcessResult success() {
        return new ProcessResult(true, "success", null);
    }

    /**
     * 成功结果（带消息）。
     *
     * @param message 结果消息
     * @return 成功结果
     */
    public static ProcessResult success(String message) {
        return new ProcessResult(true, message, null);
    }

    /**
     * 失败结果。
     *
     * @param message 错误消息
     * @return 失败结果
     */
    public static ProcessResult failed(String message) {
        return new ProcessResult(false, null, message);
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

    @Override
    public String toString() {
        return "ProcessResult{success=" + success + ", result='" + result + "', errorMessage='" + errorMessage + "'}";
    }
}
