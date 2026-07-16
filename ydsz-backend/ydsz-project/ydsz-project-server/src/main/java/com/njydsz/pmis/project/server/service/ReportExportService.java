package com.njydsz.project.server.service;

import java.util.List;
import java.util.Map;

/**
 * 报表导出服务（P2-6 体验增强）
 *
 * <p>支持将基础/高级报表导出为 Excel (xlsx) 或 CSV 文件。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ReportExportService {

    /**
     * 导出报表
     *
     * @param type   报表类型：PROFIT/COST_DETAIL/PAYMENT_LEDGER/RISK_MATRIX
     * @param format 输出格式：XLSX/CSV
     * @param params 业务参数（initiationId/period/department 等）
     * @return 字节内容 + 文件名
     */
    ExportResult export(String type, String format, Map<String, Object> params);

    /**
     * 导出结果
     */
    record ExportResult(byte[] data, String filename, String contentType) {
    }

    /**
     * 表头定义
     */
    record ColumnDef(String name, String header, int width) {
    }

    /**
     * 导出列定义
     */
    List<ColumnDef> columnsOf(String type);
}
