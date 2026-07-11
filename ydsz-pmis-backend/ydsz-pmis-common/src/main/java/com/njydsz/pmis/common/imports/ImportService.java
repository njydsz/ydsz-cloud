package com.njydsz.pmis.common.imports;

import com.njydsz.pmis.common.controller.common.ImportExportController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 批量导入服务（统一路由）
 *
 * <p>支持模板下载与文件解析，路由由 bizType 决定具体 DTO + 业务 Service。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ImportService {

    /**
     * 下载模板
     *
     * @param bizType 业务类型 rate-card / rate-internal / time-entry / employee / initiation
     * @return 模板包（headClass 用于前端预解析，bytes 为 xlsx 流，filename 中文友好）
     */
    ImportExportController.TemplateBundle buildTemplate(String bizType);

    /**
     * 导入文件
     *
     * @param bizType 业务类型
     * @param file    上传的 xlsx 文件
     * @return 导入结果（成功行数 / 失败行数 / 失败原因明细）
     */
    ImportResult importFile(String bizType, org.springframework.web.multipart.MultipartFile file) throws IOException;

    /**
     * 导入结果
     */
    record ImportResult(int totalCount, int successCount, int failedCount, List<FailureRow> failures) {
    }

    /**
     * 失败行
     */
    record FailureRow(int rowIndex, Map<String, String> rowData, String reason) {
    }
}
