package com.njydsz.literule.domain.service;

import com.njydsz.literule.domain.dto.DecisionTableDefinitionDTO;


/**
 * 决策表 Excel 导入导出服务接口（领域层）
 *
 * <p>定义决策表与 Excel（.xlsx）双向转换的标准操作，解耦领域层与基础设施层 Excel 实现。
 *
 * <p>本接口位于 {@code domain.service} 包，供 infra 层 {@code DecisionTableExcelExporter} 实现，
 * server 层通过本接口调用 Excel 导入导出功能，避免直接依赖 infra 实现类。
 *
 * @author ydsz-team
 * @since 26.09.01
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
