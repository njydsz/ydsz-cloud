package com.njydsz.cronjob.server.core.connector;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 连接器导出结果（P2-3）。
 *
 * @param total      总任务数
 * @param success    成功数
 * @param failed     失败数
 * @param skipped    跳过数
 * @param errors     错误详情列表
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ConnectorExportResult {
    private int total;
    private int success;
    private int failed;
    private int skipped;
    private List<String> errors = new ArrayList<>();

    /**
     * 创建成功结果。
     */
    public static ConnectorExportResult success(int total, int success) {
        ConnectorExportResult result = new ConnectorExportResult();
        result.setTotal(total);
        result.setSuccess(success);
        result.setFailed(0);
        result.setSkipped(total - success);
        return result;
    }

    /**
     * 添加错误信息。
     */
    public void addError(String error) {
        errors.add(error);
    }
}
