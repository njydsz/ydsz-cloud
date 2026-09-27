package com.njydsz.system.domain.vo;

import com.njydsz.common.excel.annotation.ExcelProperty;

import lombok.Data;

/**
 * 字典项 Excel 导入 VO
 *
 * <p>用于 ydsz-common-excel 映射 Excel 列到字典项字段，支撑运营批量导入字典数据。
 *
 * <p><b>必填列：</b>typeCode / itemCode / itemValue
 *
 * <p><b>选填列：</b>parentId（默认 "0"）/ sort（默认 0）/ description / status（默认 ENABLED）
 *
 * <p>导入流程：
 *
 * <ol>
 *   <li>前端上传 .xlsx 文件（POST /dict/item/import）</li>
 *   <li>后端使用 ExcelFacade.read() 解析文件为 DictItemImportVO 列表</li>
 *   <li>逐条校验必填字段与字段长度</li>
 *   <li>转换为 DictItemVO 后委托 batchService.batchSave() 执行批量持久化</li>
 * </ol>
 *
 * <p>单次导入上限 500 条，超出时返回参数错误。
 *
 * @author ydsz-team
 * @since 26.09.27
 */
@Data
public class DictItemImportVO {

  /** 字典类型编码（必填） */
  @ExcelProperty(value = "字典类型编码", order = 1, width = 20)
  private String typeCode;

  /** 字典项编码（必填） */
  @ExcelProperty(value = "字典项编码", order = 2, width = 20)
  private String itemCode;

  /** 字典项展示值（必填） */
  @ExcelProperty(value = "字典项展示值", order = 3, width = 20)
  private String itemValue;

  /** 父级 ID（选填，默认 "0"） */
  @ExcelProperty(value = "父级ID", order = 4, width = 15)
  private String parentId;

  /** 排序号（选填，默认 0） */
  @ExcelProperty(value = "排序号", order = 5, width = 10)
  private Integer sort;

  /** 字典项说明（选填） */
  @ExcelProperty(value = "说明", order = 6, width = 30)
  private String description;

  /** 启用状态 ENABLED/DISABLED（选填，默认 ENABLED） */
  @ExcelProperty(value = "状态", order = 7, width = 12)
  private String status;
}
