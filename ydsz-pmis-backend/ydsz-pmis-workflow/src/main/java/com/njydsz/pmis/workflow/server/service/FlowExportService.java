package com.njydsz.pmis.workflow.server.service.instance;

import java.util.Map;

/**
 * P1-1/P1-2: 审批单导出 Service
 *
 * <p>对标钉钉/飞书"审批单导出/打印"能力。
 * 提供带水印的审批单 PDF/HTML 导出，包含审批流程轨迹、表单数据、审批意见等。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
public interface FlowExportService {

    /**
     * P1-1/P1-2: 导出审批单为 HTML（带水印）
     *
     * <p>生成包含以下内容的 HTML 文档：
     * <ul>
     *   <li>审批单标题 + 流程名称</li>
     *   <li>表单数据（key-value 表格）</li>
     *   <li>审批轨迹（时间线：发起 → 各节点审批 → 完成）</li>
     *   <li>审批意见汇总</li>
     *   <li>P1-1: 水印（操作人姓名 + 时间戳，全页面覆盖）</li>
     * </ul>
     *
     * @param instanceId 流程实例 ID
     * @param userId     操作人 ID（用于水印）
     * @param userName   操作人姓名（用于水印）
     * @return HTML 字符串
     */
    String exportHtml(String instanceId, String userId, String userName);

    /**
     * P1-2: 导出审批单为可打印格式（HTML + 打印脚本）
     *
     * @param instanceId 流程实例 ID
     * @param userId     操作人 ID
     * @param userName   操作人姓名
     * @return 包含 HTML 内容和自动打印脚本的完整页面
     */
    Map<String, Object> exportForPrint(String instanceId, String userId, String userName);
}
