package com.njydsz.workflow.server.service;

import java.util.Map;

/**
 * 流程导出服务。
 *
 * <p>导出流程定义为 BPMN/JSON 文件。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FlowExportService {

  /**
   * P1-1/P1-2: 导出审批单为 HTML（带水印）
   *
   * <p>生成包含以下内容的 HTML 文档：
   *
   * <ul>
   *   <li>审批单标题 + 流程名称
   *   <li>表单数据（key-value 表格）
   *   <li>审批轨迹（时间线：发起 → 各节点审批 → 完成）
   *   <li>审批意见汇总
   *   <li>P1-1: 水印（操作人姓名 + 时间戳，全页面覆盖）
   * </ul>
   *
   * @param instanceId 流程实例 ID
   * @param userId 操作人 ID（用于水印）
   * @param userName 操作人姓名（用于水印）
   * @return HTML 字符串
   */
  String exportHtml(String instanceId, String userId, String userName);

  /**
   * P1-2: 导出审批单为可打印格式（HTML + 打印脚本）
   *
   * @param instanceId 流程实例 ID
   * @param userId 操作人 ID
   * @param userName 操作人姓名
   * @return 包含 HTML 内容和自动打印脚本的完整页面
   */
  Map<String, Object> exportForPrint(String instanceId, String userId, String userName);
}
