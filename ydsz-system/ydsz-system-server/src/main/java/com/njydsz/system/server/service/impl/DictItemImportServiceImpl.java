package com.njydsz.system.server.service.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.listener.ReadListener;
import com.njydsz.common.excel.core.context.AnalysisContext;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.server.vo.DictItemExcelVO;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.server.service.DictItemBatchService;
import com.njydsz.system.server.service.DictItemImportService;


/**
 * 字典项 Excel 导入 Service 实现
 *
 * <p>使用 ydsz-common-excel 流式读取 Excel 文件，逐行校验后委托 {@link DictItemBatchService} 批量入库。
 *
 * <p><b>约束：</b>
 *
 * <ul>
 *   <li>单次导入上限 500 条（防止超大文件 OOM / 长事务）</li>
 *   <li>流式读取：边解析边校验，不一次性加载全量数据到内存</li>
 *   <li>与 batchSave 共享事务语义（同一事务内任意一条失败全部回滚）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictItemImportServiceImpl implements DictItemImportService {

  /** 单次导入条数上限 */
  private static final int IMPORT_MAX_SIZE = 500;

  /** 结果 Map 初始容量（successCount, totalCount, failCount, message） */
  private static final int RESULT_MAP_CAPACITY = 4;

  private final DictItemBatchService batchService;

  /**
   * 从 Excel 输入流导入字典项
   *
   * <p>使用 ExcelFacade 流式读取（回调模式），逐行收集并通过 batchSave 入库。
   *
   * @param inputStream Excel 文件输入流（.xlsx 格式）
   * @return 导入结果 {successCount, totalCount, failCount, message}
   */
  @Override
  public Map<String, Object> importFromExcel(InputStream inputStream) {
    if (inputStream == null) {
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR).data("reason", "上传文件不能为空");
    }

    List<DictItemExcelVO> importList = new ArrayList<>();

    // 使用 ydsz-common-excel 流式读取 Excel（ExcelReadListener 回调收集数据）
    ExcelFacade.read(inputStream, DictItemExcelVO.class)
        .sheet(0)
        .doRead(
            new ReadListener<DictItemExcelVO>() {
              @Override
              public void onStart(AnalysisContext context) {
                log.debug("[DictImport] 开始读取 Excel，sheet={}", context.getCurrentSheetName());
              }

              @Override
              public void onData(AnalysisContext context, DictItemExcelVO data) {
                int currentRow = importList.size() + 1;
                validateRowData(data, currentRow);
                importList.add(data);

                // 超出上限立即阻断，写入异常信息到分析上下文
                if (importList.size() > IMPORT_MAX_SIZE) {
                  throw BusinessException.of(SystemExceptionCode.PARAM_ERROR)
                      .data("reason", "单次导入条数不能超过 " + IMPORT_MAX_SIZE + " 条");
                }
              }

              @Override
              public void onEnd(AnalysisContext context) {
                log.debug("[DictImport] Excel 读取完成，共 {} 行", importList.size());
              }
            });

    if (importList.isEmpty()) {
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR)
          .data("reason", "导入文件为空或没有有效数据行");
    }

    // 转换 ImportVO → VO，补齐默认值
    List<DictItemVO> voList =
        importList.stream().map(this::toVo).collect(java.util.stream.Collectors.toList());

    // 委托 batchSave 执行事务性批量插入（复用现有去重/唯一性校验/版本快照逻辑）
    Map<String, Object> batchResult = batchService.batchSave(voList);

    Map<String, Object> result = new HashMap<>(RESULT_MAP_CAPACITY);
    result.put("successCount", batchResult.get("successCount"));
    result.put("totalCount", importList.size());
    result.put("failCount", 0);
    result.put(
        "message",
        String.format("成功导入 %d 条字典项", batchResult.get("successCount")));
    return result;
  }

  /**
   * 校验单行数据必填字段
   *
   * @param data 行数据
   * @param rowNum 行号（用于错误提示）
   */
  private void validateRowData(DictItemExcelVO data, int rowNum) {
    if (data.getTypeCode() == null || data.getTypeCode().isBlank()) {
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR)
          .data("reason", "第 " + rowNum + " 行：字典类型编码不能为空");
    }
    if (data.getItemCode() == null || data.getItemCode().isBlank()) {
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR)
          .data("reason", "第 " + rowNum + " 行：字典项编码不能为空");
    }
    if (data.getItemValue() == null || data.getItemValue().isBlank()) {
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR)
          .data("reason", "第 " + rowNum + " 行：字典项展示值不能为空");
    }
    if (data.getTypeCode().length() > 64) {
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR)
          .data("reason", "第 " + rowNum + " 行：字典类型编码长度不能超过 64");
    }
    if (data.getItemCode().length() > 64) {
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR)
          .data("reason", "第 " + rowNum + " 行：字典项编码长度不能超过 64");
    }
  }

  /**
   * ImportVO → VO 转换（补齐默认值）
   *
   * @param importVO 导入 VO
   * @return 字典项 VO
   */
  private DictItemVO toVo(DictItemExcelVO importVO) {
    DictItemVO vo = new DictItemVO();
    vo.setTypeCode(importVO.getTypeCode());
    vo.setItemCode(importVO.getItemCode());
    vo.setItemValue(importVO.getItemValue());
    // 选填字段补齐默认值
    vo.setParentId(importVO.getParentId() != null && !importVO.getParentId().isBlank()
        ? importVO.getParentId() : "0");
    vo.setSort(importVO.getSort() != null ? importVO.getSort() : 0);
    vo.setDescription(importVO.getDescription());
    vo.setStatus(importVO.getStatus() != null && !importVO.getStatus().isBlank()
        ? importVO.getStatus() : "ENABLED");
    return vo;
  }
}
