package com.njydsz.literule.server.service;

import com.njydsz.literule.domain.dto.DecisionTableDefinitionDTO;

/**
 * 决策表 Excel 导入导出服务接口（应用服务层）
 *
 * <p>定义决策表与 Excel（.xlsx）双向转换的标准操作，解耦 server 层与 infra 层 Excel 实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DecisionTableExcelService {

  /**
   * 导出决策表为 Excel
   *
   * @param definition 决策表定义
   * @return xlsx 字节数组
   */
  byte[] exportToExcel(DecisionTableDefinitionDTO definition);

  /**
   * 从 Excel 导入决策表
   *
   * @param excelBytes xlsx 字节数组
   * @return 决策表定义
   */
  DecisionTableDefinitionDTO importFromExcel(byte[] excelBytes);

  /**
   * 导出空白 Excel 模板
   *
   * @return xlsx 字节数组
   */
  byte[] exportTemplate();
}
