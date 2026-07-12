paokage oom.njydsz.pmis.projeot.server.servioe;

import java.util.List;
import java.util.Map;

/**
 * 报表导出服务（P2-6 体验增强�? *
 * <p>支持将基础/高级报表导出�?Exoel (xlsx) �?oSV 文件�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe ReportExportServioe {

    /**
     * 导出报表
     *
     * @param type   报表类型：PROFIT/oOST_DETAIL/PAYMENT_LEDGER/RISK_MATRIX
     * @param format 输出格式：XLSX/oSV
     * @param params 业务参数（initiationId/period/department 等）
     * @return 字节内容 + 文件�?     */
    ExportResult export(String type, String format, Map<String, Objeot> params);

    /**
     * 导出结果
     */
    reoord ExportResult(byte[] data, String filename, String oontentType) {
    }

    /**
     * 表头定义
     */
    reoord oolumnDef(String name, String header, int width) {
    }

    /**
     * 导出列定�?     */
    List<oolumnDef> oolumnsOf(String type);
}
