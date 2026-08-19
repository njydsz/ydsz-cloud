package com.njydsz.system.server.service.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.helper.ExcelExportHelper;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.enums.ConfigValueType;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.repository.ConfigRepository;
import com.njydsz.system.server.vo.ConfigExcelVO;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.domain.vo.ImportResult;
import com.njydsz.system.server.cache.CacheKeyBuilder;
import com.njydsz.system.server.search.SearchIndexSyncer;
import com.njydsz.system.server.service.ConfigExcelService;

/**
 * 系统配置 Excel 导入导出服务实现（P1-1 从 ConfigServiceImpl 拆分）。
 *
 * <p>职责单一：配置的 Excel 环境迁移。不承担 CRUD / 缓存管理 / 事件发布等其它职责。
 *
 * <p><b>实现要点：</b>
 *
 * <ul>
 *   <li>导出走 {@link ExcelExportHelper#export}（注解驱动，符合《云顶编码规范》22.6）
 *   <li>导入逐条校验（必填 / 值类型 / DB 唯一性），单条错误跳过并收集，不中断整体
 *   <li>批量插入使用 {@code insertBatch}（1 次 SQL，消除 N+1）
 *   <li>导入后按涉及 {@code configGroup} 精准失效缓存 + 同步搜索索引（可选）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.10.0
 * @see ConfigExcelService 接口
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigExcelServiceImpl implements ConfigExcelService {

  /** 配置仓储 */
  private final ConfigRepository configRepository;

  /** Spring Cache 管理器（导入后精准失效缓存） */
  private final CacheManager cacheManager;

  /** 租户感知缓存键构造器 */
  private final CacheKeyBuilder cacheKeyBuilder;

  /** 搜索索引同步器（可选能力，未启用搜索模块时静默跳过） */
  private final SearchIndexSyncer searchIndexSyncer;

  /** Excel 导出辅助类 */
  private final ExcelExportHelper excelExportHelper;

  @Override
  public byte[] exportConfigs(String configGroup) {
    // 1. 查询配置数据（含分组过滤）
    List<ConfigVO> configs = configRepository.findForExport(configGroup);

    // 2. 转换为 Excel VO 并导出
    List<ConfigExcelVO> excelRows =
        configs.stream().map(this::toExcelVO).collect(Collectors.toList());
    return excelRows.isEmpty()
        ? new byte[0]
        : excelExportHelper.export("系统配置", ConfigExcelVO.class, excelRows);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public ImportResult importConfigs(InputStream inputStream) {
    // 1. 读取 Excel 文件
    List<ConfigExcelVO> excelRows = readExcel(inputStream);
    if (excelRows.isEmpty()) {
      return ImportResult.builder()
          .totalCount(0)
          .successCount(0)
          .failCount(0)
          .skipCount(0)
          .message("Excel 文件为空")
          .build();
    }

    // 2. 逐条校验并转换（必填 / 值类型 / DB 唯一性）
    List<String> errors = new ArrayList<>();
    List<ConfigVO> validItems = new ArrayList<>();
    int skipCount = 0;
    for (int i = 0; i < excelRows.size(); i++) {
      String error = validateExcelRow(excelRows.get(i), i + 2);
      if (error != null) {
        errors.add(error);
        skipCount++;
      } else {
        validItems.add(toConfigVO(excelRows.get(i)));
      }
    }

    // 3. 批量保存有效数据（使用 insertBatch 消除 N+1）
    int successCount = saveValidItemsBatch(validItems, errors);

    // 4. 构建导入结果
    return ImportResult.builder()
        .totalCount(excelRows.size())
        .successCount(successCount)
        .failCount(excelRows.size() - successCount - skipCount)
        .skipCount(skipCount)
        .errors(errors)
        .message(
            String.format(
                "导入完成: 成功 %d 条, 跳过 %d 条, 失败 %d 条",
                successCount, skipCount, excelRows.size() - successCount - skipCount))
        .build();
  }

  // ============================== 私有方法 ==============================

  /**
   * 读取 Excel 配置数据。
   *
   * @param inputStream Excel 输入流
   * @return 配置 Excel 行列表
   */
  private List<ConfigExcelVO> readExcel(InputStream inputStream) {
    try {
      List<ConfigExcelVO> rows =
          ExcelFacade.read(inputStream, ConfigExcelVO.class).sheet(0).doReadAll();
      return rows != null ? rows : List.of();
    } catch (Exception e) {
      log.warn("[ConfigExcelService] Excel 读取失败: {}", e.getMessage());
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR)
          .data("reason", "Excel 文件读取失败: " + e.getMessage());
    }
  }

  /**
   * 校验单条 Excel 行。
   *
   * <p>校验必填字段、值类型、DB 唯一性；通过返回 null，否则返回错误描述。
   *
   * @param excelRow Excel 行数据
   * @param rowNum Excel 行号（从 2 开始，第 1 行为表头）
   * @return 错误描述；校验通过返回 null
   */
  private String validateExcelRow(ConfigExcelVO excelRow, int rowNum) {
    String requiredError = validateRequiredFields(excelRow, rowNum);
    if (requiredError != null) {
      return requiredError;
    }
    String typeError = validateExcelValueType(excelRow, rowNum);
    if (typeError != null) {
      return typeError;
    }
    if (configRepository.existsByGroupAndKey(excelRow.getConfigGroup(), excelRow.getConfigKey())) {
      return "第 " + rowNum + " 行: 配置已存在("
          + excelRow.getConfigGroup() + "/" + excelRow.getConfigKey() + ")";
    }
    return null;
  }

  /**
   * 校验 Excel 行必填字段。
   *
   * @param excelRow Excel 行数据
   * @param rowNum Excel 行号
   * @return 错误描述；通过返回 null
   */
  private String validateRequiredFields(ConfigExcelVO excelRow, int rowNum) {
    if (excelRow.getConfigGroup() == null || excelRow.getConfigGroup().isBlank()) {
      return "第 " + rowNum + " 行: 配置分组不能为空";
    }
    if (excelRow.getConfigKey() == null || excelRow.getConfigKey().isBlank()) {
      return "第 " + rowNum + " 行: 配置键不能为空";
    }
    if (excelRow.getConfigValue() == null || excelRow.getConfigValue().isBlank()) {
      return "第 " + rowNum + " 行: 配置值不能为空";
    }
    return null;
  }

  /**
   * 校验 Excel 行值类型。
   *
   * @param excelRow Excel 行数据
   * @param rowNum Excel 行号
   * @return 错误描述；通过返回 null
   */
  private String validateExcelValueType(ConfigExcelVO excelRow, int rowNum) {
    if (excelRow.getValueType() == null || excelRow.getValueType().isBlank()) {
      return null;
    }
    try {
      ConfigValueType.validate(excelRow.getValueType());
      return null;
    } catch (IllegalArgumentException e) {
      return "第 " + rowNum + " 行: 值类型不合法: " + excelRow.getValueType();
    }
  }

  /**
   * Excel 行转换为配置 VO。
   *
   * @param excelRow Excel 行数据
   * @return 配置 VO
   */
  private ConfigVO toConfigVO(ConfigExcelVO excelRow) {
    ConfigVO vo = new ConfigVO();
    vo.setConfigGroup(excelRow.getConfigGroup());
    vo.setConfigKey(excelRow.getConfigKey());
    vo.setConfigValue(excelRow.getConfigValue());
    vo.setValueType(excelRow.getValueType());
    vo.setDefaultValue(excelRow.getDefaultValue());
    vo.setDescription(excelRow.getDescription());
    vo.setIsPublic(excelRow.getIsPublic());
    vo.setSortOrder(excelRow.getSortOrder());
    vo.setStatus(excelRow.getStatus());
    return vo;
  }

  /**
   * 批量保存有效配置（使用 insertBatch 消除 N+1）。
   *
   * <p>批量 XML 不走 MyBatis-Plus 拦截器（租户拦截器、审计字段自动填充均不生效），
   * 需在此处手动预生成 ID；审计字段由仓储层实现内部处理。
   *
   * @param validItems 校验通过的配置列表
   * @param errors 错误收集器（保存失败时追加）
   * @return 保存成功条数
   */
  private int saveValidItemsBatch(List<ConfigVO> validItems, List<String> errors) {
    if (validItems.isEmpty()) {
      return 0;
    }
    try {
      // 1. VO 转 DTO，预生成 ID
      List<ConfigDTO> dtos =
          validItems.stream().map(this::toDtoForImport).collect(Collectors.toList());

      // 2. 批量插入（1 次 SQL 完成全部写入）
      configRepository.insertBatch(dtos);

      // 3. 精准失效缓存：按涉及 configGroup 逐一失效
      dtos.stream()
          .map(ConfigDTO::getConfigGroup)
          .distinct()
          .forEach(this::evictConfigGroup);
      evictConfigPublic();

      // 4. 同步搜索索引（可选能力，未启用时静默跳过）
      dtos.forEach(dto -> searchIndexSyncer.upsert("config", dto));

      return dtos.size();
    } catch (Exception e) {
      errors.add("批量导入失败: " + e.getMessage());
      return 0;
    }
  }

  /**
   * VO 转 DTO + 预生成雪花 ID（导入场景专用）。
   *
   * <p>批量 XML 插入不走 MyBatis-Plus 拦截器（CombinedFieldFillInterceptor、租户拦截器、
   * IdentifierGenerator 均不生效），需在此处手动预生成 ID。审计字段由仓储层实现内部处理。
   *
   * <p>缺省 {@code status="ENABLED"}。
   *
   * @param vo 配置 VO
   * @return 配置 DTO（含预生成 ID）
   */
  private ConfigDTO toDtoForImport(ConfigVO vo) {
    ConfigDTO dto = new ConfigDTO();
    dto.setId(IdWorker.getIdStr());
    dto.setConfigGroup(vo.getConfigGroup());
    dto.setConfigKey(vo.getConfigKey());
    dto.setConfigValue(vo.getConfigValue());
    dto.setValueType(vo.getValueType());
    dto.setDefaultValue(vo.getDefaultValue());
    dto.setDescription(vo.getDescription());
    dto.setIsPublic(vo.getIsPublic());
    dto.setSortOrder(vo.getSortOrder());
    dto.setStatus(vo.getStatus() != null ? vo.getStatus() : "ENABLED");
    return dto;
  }

  /**
   * ConfigVO 转换为 Excel VO。
   *
   * @param vo 配置 VO
   * @return Excel VO
   */
  private ConfigExcelVO toExcelVO(ConfigVO vo) {
    ConfigExcelVO excelVO = new ConfigExcelVO();
    excelVO.setConfigGroup(vo.getConfigGroup());
    excelVO.setConfigKey(vo.getConfigKey());
    excelVO.setConfigValue(vo.getConfigValue());
    excelVO.setValueType(vo.getValueType());
    excelVO.setDefaultValue(vo.getDefaultValue());
    excelVO.setDescription(vo.getDescription());
    excelVO.setIsPublic(vo.getIsPublic());
    excelVO.setSortOrder(vo.getSortOrder());
    excelVO.setStatus(vo.getStatus());
    return excelVO;
  }

  /** 失效「按配置分组批量查询」缓存。 */
  private void evictConfigGroup(String configGroup) {
    if (configGroup == null) {
      return;
    }
    cacheManager.getCache(CacheConstants.SYSTEM_CONFIG_CACHE).evict(cacheKeyBuilder.configGroup(configGroup));
  }

  /** 失效「公开配置」缓存。 */
  private void evictConfigPublic() {
    cacheManager.getCache(CacheConstants.SYSTEM_CONFIG_CACHE).evict(cacheKeyBuilder.configPublic());
  }
}
