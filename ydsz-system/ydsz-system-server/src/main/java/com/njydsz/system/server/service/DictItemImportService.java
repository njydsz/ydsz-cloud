package com.njydsz.system.server.service;

import java.io.InputStream;
import java.util.Map;

/**
 * 字典项 Excel 导入 Service
 *
 * <p>提供 Excel 文件解析 + 校验 + 批量入库的一站式能力，支撑运营批量初始化字典数据。
 *
 * <p><b>导入流程：</b>
 *
 * <ol>
 *   <li>解析 Excel 文件为 {@link com.njydsz.system.domain.vo.DictItemImportVO} 列表</li>
 *   <li>逐条校验必填字段（typeCode / itemCode / itemValue 非空）</li>
 *   <li>转换为 {@link com.njydsz.system.domain.vo.DictItemVO} 后委托 {@link DictItemBatchService#batchSave}</li>
 * </ol>
 *
 * <p><b>性能约束：</b>单次导入上限 500 条，采用流式读取避免 OOM。
 *
 * @author ydsz-team
 * @since 26.09.27
 */
public interface DictItemImportService {

  /**
   * 从 Excel 输入流导入字典项
   *
   * <p>使用 ydsz-common-excel 流式读取，边解析边校验，最终通过 batchSave 在同一事务内完成入库。
   *
   * @param inputStream Excel 文件输入流（.xlsx 格式）
   * @return 导入结果 {successCount, totalCount, failCount, message}
   */
  Map<String, Object> importFromExcel(InputStream inputStream);
}
