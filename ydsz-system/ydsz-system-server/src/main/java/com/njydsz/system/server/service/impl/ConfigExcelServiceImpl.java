package com.njydsz.system.server.service.impl;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.context.AnalysisContext;
import com.njydsz.common.excel.core.listener.ReadListener;
import com.njydsz.common.excel.helper.ExcelExportHelper;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.locales.util.I18n;
import com.njydsz.common.util.id.IdGenerator;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.enums.ConfigValueType;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.repository.ConfigRepository;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.server.cache.CacheKeyBuilder;
import com.njydsz.system.server.constant.SystemCacheConstants;
import com.njydsz.system.server.service.ConfigExcelService;
import com.njydsz.system.server.vo.ConfigExcelVO;
import com.njydsz.system.server.vo.ImportResultVO;




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
 * @since 26.10.01
 * @see ConfigExcelService 接口
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigExcelServiceImpl implements ConfigExcelService {

  /** Excel 导入分批大小（每批处理行数，避免全量常驻内存） */
  private static final int IMPORT_BATCH_SIZE = 500;

  /** 配置仓储 */
  private final ConfigRepository configRepository;

  /** Spring Cache 管理器（导入后精准失效缓存） */
  private final CacheManager cacheManager;

  /** 租户感知缓存键构造器 */
  private final CacheKeyBuilder cacheKeyBuilder;

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
  public ImportResultVO importConfigs(InputStream inputStream) {
    // 流式分批读取 Excel（每 IMPORT_BATCH_SIZE 行回调一次，校验通过即按批落库）
    ImportAccumulator accumulator = new ImportAccumulator();
    readExcelInBatches(inputStream, accumulator);
    if (accumulator.totalCount == 0) {
      return ImportResultVO.builder()
          .totalCount(0)
          .successCount(0)
          .failCount(0)
          .skipCount(0)
          .message(I18n.message("system.excel.file.empty"))
          .build();
    }

    int total = accumulator.totalCount;
    int successCount = accumulator.savedCount;
    int skipCount = accumulator.skipCount;
    return ImportResultVO.builder()
        .totalCount(total)
        .successCount(successCount)
        .failCount(total - successCount - skipCount)
        .skipCount(skipCount)
        .errors(accumulator.errors)
        .message(
            String.format(
                "导入完成: 成功 %d 条, 跳过 %d 条, 失败 %d 条",
                successCount, skipCount, total - successCount - skipCount))
        .build();
  }

  // ============================== 私有方法 ==============================

  /**
   * 流式分批读取 Excel 配置数据（私有）。
   *
   * <p>每 {@link #IMPORT_BATCH_SIZE} 行触发一次回调，回调内完成「批量预加载 DB 唯一键 + 逐行校验 + 按批落库」。
   *
   * @param inputStream Excel 输入流
   * @param accumulator 导入过程累加器
   */
  private void readExcelInBatches(InputStream inputStream, ImportAccumulator accumulator) {
    try {
      ExcelFacade.read(inputStream, ConfigExcelVO.class)
          .sheet(0)
          .batchSize(IMPORT_BATCH_SIZE)
          .doRead(
              new ReadListener<ConfigExcelVO>() {
                @Override
                public void onStart(AnalysisContext context) {}

                @Override
                public void onData(AnalysisContext context, ConfigExcelVO data) {
                  // 批量模式下不会被调用（由 onBatchData 承担）
                }

                @Override
                public void onBatchData(AnalysisContext context, List<ConfigExcelVO> batch) {
                  if (batch == null || batch.isEmpty()) {
                    return;
                  }
                  int baseRow = accumulator.totalCount;
                  Set<String> existingKeys = loadExistingGroupKeys(batch);
                  List<ConfigVO> batchValid = new ArrayList<>(batch.size());
                  for (int i = 0; i < batch.size(); i++) {
                    int rowNum = baseRow + i + 2;
                    accumulator.totalCount++;
                    String error =
                        validateExcelRow(batch.get(i), rowNum, existingKeys, accumulator.seenKeys);
                    if (error != null) {
                      accumulator.errors.add(error);
                      accumulator.skipCount++;
                    } else {
                      batchValid.add(toConfigVO(batch.get(i)));
                    }
                  }
                  if (!batchValid.isEmpty()) {
                    accumulator.merge(saveValidItemsBatch(batchValid, accumulator.errors));
                  }
                }

                @Override
                public void onEnd(AnalysisContext context) {}
              });
    } catch (Exception e) {
      log.warn("[ConfigExcelService] Excel 读取失败: {}", e.getMessage(), e);
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR)
          .data("reason", "Excel 文件读取失败: " + e.getMessage());
    }
  }

  /**
   * 导入过程累加器（私有内部类）。
   */
  private static final class ImportAccumulator {
    private int totalCount;
    private int skipCount;
    private int savedCount;
    private final List<String> errors = new ArrayList<>(64);
    private final Set<String> seenKeys = new HashSet<>(IMPORT_BATCH_SIZE);

    private void merge(int saved) {
      this.savedCount += saved;
    }
  }

  /**
   * 批量预加载本批次涉及的配置唯一键（私有）。
   *
   * @param excelRows Excel 行列表
   * @return 已存在的 {@code configGroup/configKey} 集合
   */
  private Set<String> loadExistingGroupKeys(List<ConfigExcelVO> excelRows) {
    Set<String> groups =
        excelRows.stream()
            .map(ConfigExcelVO::getConfigGroup)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    return configRepository.loadGroupKeySet(groups);
  }

  /**
   * 校验单条 Excel 行。
   *
   * <p>校验必填字段、值类型、DB 唯一性（内存比对，含文件内重复检测）；通过返回 null，否则返回错误描述。
   *
   * @param excelRow Excel 行数据
   * @param rowNum Excel 行号（从 2 开始，第 1 行为表头）
   * @param existingKeys 已存在的 {@code configGroup/configKey} 集合
   * @param seenKeys 本批次已处理的 key 集合（用于文件内重复检测）
   * @return 错误描述；校验通过返回 null
   */
  private String validateExcelRow(
      ConfigExcelVO excelRow,
      int rowNum,
      Set<String> existingKeys,
      Set<String> seenKeys) {
    String requiredError = validateRequiredFields(excelRow, rowNum);
    if (requiredError != null) {
      return requiredError;
    }
    String typeError = validateExcelValueType(excelRow, rowNum);
    if (typeError != null) {
      return typeError;
    }
    String key = excelRow.getConfigGroup() + "/" + excelRow.getConfigKey();
    if (existingKeys.contains(key) || !seenKeys.add(key)) {
      String rowPrefix = I18n.message("system.excel.rowPrefix", new Object[] {rowNum});
      return rowPrefix + I18n.message("system.excel.configKey.exists", new Object[] {key});
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
    String rowPrefix = I18n.message("system.excel.rowPrefix", new Object[] {rowNum});
    if (excelRow.getConfigGroup() == null || excelRow.getConfigGroup().isBlank()) {
      return rowPrefix + I18n.message("system.config.group_required");
    }
    if (excelRow.getConfigKey() == null || excelRow.getConfigKey().isBlank()) {
      return rowPrefix + I18n.message("system.config.key_required");
    }
    if (excelRow.getConfigValue() == null || excelRow.getConfigValue().isBlank()) {
      return rowPrefix + I18n.message("system.config.value_required");
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
      String rowPrefix = I18n.message("system.excel.rowPrefix", new Object[] {rowNum});
      return rowPrefix + I18n.message("system.excel.valueType.invalid", new Object[] {excelRow.getValueType()});
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
vo.setSort(excelRow.getSort());
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
    dto.setId(IdGenerator.nextIdStr());
    dto.setConfigGroup(vo.getConfigGroup());
    dto.setConfigKey(vo.getConfigKey());
    dto.setConfigValue(vo.getConfigValue());
    dto.setValueType(vo.getValueType());
    dto.setDefaultValue(vo.getDefaultValue());
dto.setDescription(vo.getDescription());
dto.setIsPublic(vo.getIsPublic());
dto.setSort(vo.getSort());
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
excelVO.setSort(vo.getSort());
    excelVO.setStatus(vo.getStatus());
    return excelVO;
  }

  /** 失效「按配置分组批量查询」缓存。 */
  private void evictConfigGroup(String configGroup) {
    if (configGroup == null) {
      return;
    }
    cacheManager.getCache(SystemCacheConstants.SYSTEM_CONFIG_CACHE).evict(cacheKeyBuilder.configGroup(configGroup));
  }

  /** 失效「公开配置」缓存。 */
  private void evictConfigPublic() {
    cacheManager.getCache(SystemCacheConstants.SYSTEM_CONFIG_CACHE).evict(cacheKeyBuilder.configPublic());
  }
}
