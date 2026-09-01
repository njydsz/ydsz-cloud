package com.njydsz.system.server.service.impl;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.helper.ExcelExportHelper;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.message.MessageUtils;
import com.njydsz.system.domain.dto.EntityVersionDTO;
import com.njydsz.system.domain.dto.VariableDTO;
import com.njydsz.system.domain.enums.ConfigValueType;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.event.VersionSnapshotEvent;
import com.njydsz.system.domain.query.VariablePageQuery;
import com.njydsz.system.domain.repository.VariableRepository;
import com.njydsz.system.domain.vo.ImportResultVO;
import com.njydsz.system.domain.vo.VariableVO;
import com.njydsz.system.server.cache.CacheKeyBuilder;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.EntityVersionService;
import com.njydsz.system.server.service.VariableService;
import com.njydsz.system.server.service.rollback.VariableRollbackStrategy;
import com.njydsz.system.server.util.SystemVersionUtils;
import com.njydsz.system.server.vo.VariableExcelVO;




/**
 * 系统变量 Service 实现
 *
 * <p>对 {@link VariableService} 接口的完整实现，是「系统变量中心」的核心业务逻辑层。 与 {@link ConfigServiceImpl}
 * 能力对齐，但定位不同：Variable 用于业务侧动态参数 （如当前生效的会计年度、最近结算月份、流水号计数器等）， 业务方可通过 Feign 远程查询；Config
 * 用于系统级配置，由后端模块本地消费。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@link #page} / {@link #getById} / {@link #save} / {@link #updateById} / {@link
 *       #removeById}，全部走 {@code @Transactional} 事务保证
 *   <li><b>按 key 查询值</b>：{@link #getVariableValue}（走 ydsz-common-cache 本地缓存 + Spring Cache 注解）
 *   <li><b>分页 / 列表查询</b>：{@link #page} / {@link #list}，支持行级数据权限过滤 （{@code @DataScope}）
 *   <li><b>缓存失效</b>：写操作通过 {@code @CacheEvict(allEntries=true)} 主动清空
 * </ul>
 *
 * <p><b>缓存设计：</b>
 *
 * <ul>
 *   <li>缓存名称：{@link CacheConstants#SYSTEM_VARIABLE_CACHE}（ydsz-common-cache 本地缓存）
 *   <li>缓存键：{@code {tenantId}:{variableKey}}
 *   <li>TTL 与容量通过 {@code ydsz.cache.caches.system:variable} YAML 配置
 *   <li>写操作触发 {@code @CacheEvict(allEntries=true)} 主动失效
 * </ul>
 *
 * <p><b>事务边界：</b>
 *
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}
 *   <li>读方法不开启事务，依赖 MyBatis 自动提交
 *   <li>分页 / 列表查询使用 {@code @DataScope} 自动注入行级数据权限 SQL
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离， 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>与 ConfigService 的区别：</b>
 *
 * <table>
 *   <caption>Variable vs Config 定位差异</caption>
 *   <tr><th>维度</th><th>{@link ConfigServiceImpl Config}</th><th>Variable（本类）</th></tr>
 *   <tr><td>使用方</td><td>后端模块本地消费</td><td>业务方 + 跨服务 Feign 查询</td></tr>
 *   <tr><td>典型场景</td><td>数据库连接池参数、日志级别</td><td>会计年度、流水号、动态业务参数</td></tr>
 *   <tr><td>缓存粒度</td><td>单 key + 组批量 + 公开配置</td><td>仅单 key（无组批量）</td></tr>
 *   <tr><td>变更广播</td><td>发布 {@code ConfigChangeEvent}</td><td>不广播（业务方拉取即可）</td></tr>
 *   <tr><td>行级权限</td><td>无</td><td>有（{@code @DataScope}）</td></tr>
 * </table>
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>业务参数化</b>：业务硬编码值（如「每月 1 号出账」）可改为变量，由运营灵活调整
 *   <li><b>行级权限</b>：分页 / 列表查询走 {@code @DataScope}，自动按当前用户的部门 / 人员范围过滤
 *   <li><b>软删除</b>：{@code ydsz_sys_variable} 表采用 <b>逻辑删除</b>（{@code deleted} 字段）
 *   <li><b>启用过滤</b>：{@link #getVariableValue} 仅返回 {@code status=ENABLED} 的变量， 失效的变量视为不存在
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 业务方远程查询（跨服务）
 * String currentYear = variableClient.getVariableValue("finance.current_fiscal_year");
 *
 * // 后端模块本地查询
 * String waterNo = variableService.getVariableValue("serial.water_no_prefix");
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see VariableService 变量 Service 接口
 * @see ConfigServiceImpl 系统配置 Service（能力对齐但定位不同）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VariableServiceImpl implements VariableService {

  /** 变量仓储 */
  private final VariableRepository variableRepository;

  /** 系统监控指标采集器 */
  private final SystemMetrics metrics;

  /** 统一实体版本服务（写操作时创建版本快照） */
  private final EntityVersionService entityVersionService;

  /** Spring Cache 管理器（用于按 key 精准失效缓存） */
  private final CacheManager cacheManager;

  /** 租户感知缓存键构造器（SpEL 与手动 evict 共用） */
  private final CacheKeyBuilder cacheKeyBuilder;

  /** 统一领域事件发布门面（ObjectProvider 可选注入，common-event 未引入时安全降级，见《云顶编码规范》27.4） */
  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;

  /** 统一 Excel 导出辅助类 */
  private final ExcelExportHelper excelExportHelper;

  /** 变量回滚策略（从快照 JSON 反序列化并重建变量资源） */
  private final VariableRollbackStrategy rollbackStrategy;

  /** Spring 事件发布器（用于异步创建版本快照，P3-2 版本快照异步化） */
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 根据主键查询变量（不走缓存，直接走 DB）
   *
   * <p>适用场景：管理后台「变量详情」页，单次访问无缓存需求。 高频查询请使用 {@link #getVariableValue}。
   *
   * @param id 变量主键
   * @return 变量 VO，不存在返回 null
   */
  @Override
  public VariableVO getById(String id) {
    return variableRepository.findById(id).orElse(null);
  }

  /**
   * 按 variableKey 查询变量值（走缓存）
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>通过 Spring Cache {@code @Cacheable} 查本地缓存（{@link
   *       CacheConstants#SYSTEM_VARIABLE_CACHE}），命中直接返回
   *   <li>缓存未命中查 DB（方法体内仅执行此逻辑）
   *   <li>记录查询耗时指标（缓存命中时方法不执行，由 Micrometer 记录）
   * </ol>
   *
   * <p>本方法是高频读入口，跨服务 Feign 调用建议走本方法，避免直连 DB。
   *
   * @param variableKey 变量键
   * @return 变量值字符串，不存在时返回 null（SpringYdszCache 自动缓存 null 值防穿透）
   */
  @Override
  @Cacheable(value = CacheConstants.SYSTEM_VARIABLE_CACHE, key = "@cacheKeyBuilder.variable(#p0)")
  public String getVariableValue(String variableKey) {
    VariableVO vo = variableRepository.findEnabledByKey(variableKey).orElse(null);
    return vo != null ? vo.getVariableValue() : null;
  }

  /**
   * 分页查询变量（管理后台列表页）
   *
   * <p>支持按 {@code variableKey} 模糊匹配、{@code status} 精确匹配进行过滤， 按 {@code created_at} 倒序返回。
   *
   * <p><b>租户隔离：</b>本方法按当前租户自动过滤（MyBatis 拦截器注入 tenant_id）。
   *
   * @param query 分页查询条件（pageNum / pageSize / variableKey / status）
   * @return 分页结果（含总条数）
   */
  @Override
  public PageResponse<List<VariableVO>> page(VariablePageQuery query) {
    return variableRepository.findByPage(query);
  }

  /**
   * 查询全部变量（不区分状态）
   *
   * <p>典型调用方：管理后台「变量选择器」下拉框。
   *
   * <p><b>租户隔离：</b>本方法按当前租户自动过滤（MyBatis 拦截器注入 tenant_id）。
   *
   * <p><b>慎用：</b>全表扫描，变量一般 < 200 条，单次查询 < 20ms。
   *
   * @return 全部变量列表（按 createdAt 倒序）
   */
  @Override
  public List<VariableVO> list() {
    return variableRepository.findAll();
  }

  /**
   * 新增变量
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>DTO 转 DO，默认 {@code status=ENABLED}
   *   <li>插入 {@code ydsz_sys_variable} 表
   *   <li>精准失效该 {@code variableKey} 对应的缓存
   * </ol>
   *
   * @return 新创建的变量 ID
   */
  @Override
  @CacheEvict(
      value = CacheConstants.SYSTEM_VARIABLE_CACHE,
      key = "@cacheKeyBuilder.variable(#dto.variableKey)")
  @Transactional(rollbackFor = Exception.class)
  public String save(VariableDTO dto) {
    validateValueType(dto.getValueType());
    if (dto.getStatus() == null) {
      dto.setStatus("ENABLED");
    }
    variableRepository.insert(dto);
    publishVariableChangedEvent(dto.getVariableKey(), "创建变量");
    return dto.getId();
  }

  /**
   * 更新变量
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>DTO 转 DO
   *   <li>更新 {@code ydsz_sys_variable} 表
   *   <li>更新成功后精准失效该 {@code variableKey} 对应的缓存
   * </ol>
   *
   * <p><b>注意：</b>更新 {@code variableKey} 会导致所有依赖该键的下游缓存失效， 调用方需主动清理相关业务缓存。
   *
   * @return true=更新成功，false=记录不存在
   */
  @Override
  @CacheEvict(
      value = CacheConstants.SYSTEM_VARIABLE_CACHE,
      key = "@cacheKeyBuilder.variable(#dto.variableKey)")
  @Transactional(rollbackFor = Exception.class)
  public boolean updateById(VariableDTO dto) {
    validateValueType(dto.getValueType());
    // 版本快照：查询变更前状态
    VariableVO before = variableRepository.findByKeyIgnoreStatus(dto.getVariableKey()).orElse(null);
    String snapshotJson = before != null ? YdszJson.toJson(before) : null;
    boolean updated = variableRepository.updateById(dto);
    if (updated) {
      // 变量键变更时，旧键缓存一并失效
      if (before != null && !Objects.equals(before.getVariableKey(), dto.getVariableKey())) {
        evictVariable(before.getVariableKey());
      }
      // 创建版本快照（P3-2 异步化：事务提交后由监听器创建）
      eventPublisher.publishEvent(
          new VersionSnapshotEvent(
              this,
              EntityVersionDTO.builder()
                  .resourceType(EntityVersionService.RESOURCE_TYPE_VARIABLE)
                  .resourceKey(dto.getVariableKey())
                  .version(SystemVersionUtils.nextVersion())
                  .changeLog("更新变量: " + dto.getVariableKey())
                  .snapshotJson(snapshotJson)
                  .build()));
      publishVariableChangedEvent(dto.getVariableKey(), "更新变量");
    }
    return updated;
  }

  /**
   * 逻辑删除变量
   *
   * <p>采用<b>逻辑删除</b>（{@code deleted=1} + {@code status=DISABLED}）， 不真正从 DB 删除，便于审计回溯。
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>查询原实体（用于获取 variableKey）
   *   <li>逻辑删除记录
   *   <li>删除成功后精准失效该 {@code variableKey} 对应的缓存
   * </ol>
   *
   * @param id 变量主键
   * @return true=删除成功，false=记录不存在
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    VariableVO vo = variableRepository.findById(id).orElse(null);
    String snapshotJson = vo != null ? YdszJson.toJson(vo) : null;
    boolean removed = variableRepository.deleteById(id);
    if (removed && vo != null) {
      // 精准失效该变量键缓存（替代 allEntries 全量清空）
      evictVariable(vo.getVariableKey());
      // 创建版本快照（P3-2 异步化：事务提交后由监听器创建）
      eventPublisher.publishEvent(
          new VersionSnapshotEvent(
              this,
              EntityVersionDTO.builder()
                  .resourceType(EntityVersionService.RESOURCE_TYPE_VARIABLE)
                  .resourceKey(vo.getVariableKey())
                  .version(SystemVersionUtils.nextVersion())
                  .changeLog("删除变量: " + vo.getVariableKey())
                  .snapshotJson(snapshotJson)
                  .build()));
      publishVariableChangedEvent(vo.getVariableKey(), "删除变量");
    }
    return removed;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String rollbackTo(String resourceKey, String targetVersion, String operatorId) {
    return entityVersionService.rollbackTo(
        EntityVersionService.RESOURCE_TYPE_VARIABLE,
        resourceKey,
        targetVersion,
        operatorId,
        rollbackStrategy);
  }

  /**
   * 精准失效指定变量键的缓存（替代 allEntries 全量清空）。
   *
   * @param variableKey 变量键
   */
  private void evictVariable(String variableKey) {
    if (variableKey == null) {
      return;
    }
    cacheManager
        .getCache(CacheConstants.SYSTEM_VARIABLE_CACHE)
        .evict(cacheKeyBuilder.variable(variableKey));
  }

  /**
   * 发布变量变更事件（跨实例缓存同步）。
   *
   * @param variableKey 变量键
   * @param action 操作描述（创建变量/更新变量/删除变量）
   */
  private void publishVariableChangedEvent(String variableKey, String action) {
    DomainEventPublisher publisher = eventPublisherProvider.getIfAvailable();
    if (publisher == null) {
      return;
    }
    publisher.publish(
        DomainEvent.builder()
            .aggregateType("Variable")
            .aggregateId(variableKey)
            .eventType(DomainEventTypes.VARIABLE_CHANGED)
            .metadata("variableKey", variableKey)
            .metadata("action", action)
            .build());
  }

  /**
   * 校验变量值类型合法性。
   *
   * <p>委托 {@link ConfigValueType#validate} 完成，非法类型将抛出 {@link BusinessException}
   * （{@link SystemExceptionCode#VALUE_TYPE_INVALID}）阻止脏数据落库。
   *
   * @param valueType 值类型字符串
   */
  private void validateValueType(String valueType) {
    try {
      ConfigValueType.validate(valueType);
    } catch (IllegalArgumentException e) {
      throw BusinessException.of(SystemExceptionCode.VALUE_TYPE_INVALID)
          .data("valueType", valueType);
    }
  }

  // ============================== 导入导出 ==============================

  @Override
  public byte[] exportVariables() {
    // 1. 查询全部变量数据
    List<VariableVO> variables = variableRepository.findAll();

    // 2. 转换为 Excel VO 并导出
    List<VariableExcelVO> excelRows =
        variables.stream().map(this::toExcelVO).collect(Collectors.toList());
    return excelRows.isEmpty()
        ? new byte[0]
        : excelExportHelper.export("系统变量", VariableExcelVO.class, excelRows);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public ImportResultVO importVariables(InputStream inputStream) {
    // 1. 读取 Excel 文件
    List<VariableExcelVO> excelRows = readExcel(inputStream);
    if (excelRows.isEmpty()) {
      return ImportResultVO.builder()
          .totalCount(0)
          .successCount(0)
          .failCount(0)
          .skipCount(0)
          .message("Excel 文件为空")
          .build();
    }

    // 2. 逐条校验并转换（必填 / 值类型 / DB 唯一性）
    List<String> errors = new ArrayList<>(excelRows.size());
    List<VariableVO> validItems = new ArrayList<>(excelRows.size());
    int skipCount = 0;
    for (int i = 0; i < excelRows.size(); i++) {
      String error = validateExcelRow(excelRows.get(i), i + 2);
      if (error != null) {
        errors.add(error);
        skipCount++;
      } else {
        validItems.add(toVariableVO(excelRows.get(i)));
      }
    }

    // 3. 批量保存有效数据
    int successCount = saveValidItemsBatch(validItems, errors);

    // 4. 构建导入结果
    return ImportResultVO.builder()
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

  /**
   * 读取 Excel 变量数据（私有）。
   *
   * @param inputStream Excel 输入流
   * @return 变量 Excel 行列表
   */
  private List<VariableExcelVO> readExcel(InputStream inputStream) {
    try {
      List<VariableExcelVO> rows =
          ExcelFacade.read(inputStream, VariableExcelVO.class).sheet(0).doReadAll();
      return rows != null ? rows : List.of();
    } catch (Exception e) {
      log.warn("[VariableService] Excel 读取失败: {}", e.getMessage(), e);
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR)
          .data("reason", "Excel 文件读取失败: " + e.getMessage());
    }
  }

  /**
   * 校验单条 Excel 行（私有）。
   *
   * <p>校验必填字段、值类型、DB 唯一性；通过返回 null，否则返回错误描述。
   *
   * @param excelRow Excel 行数据
   * @param rowNum Excel 行号（从 2 开始，第 1 行为表头）
   * @return 错误描述；校验通过返回 null
   */
  private String validateExcelRow(VariableExcelVO excelRow, int rowNum) {
    String rowPrefix = MessageUtils.getMessage("system.excel.rowPrefix", new Object[] {rowNum}, "第 " + rowNum + " 行: ");
    if (excelRow.getVariableKey() == null || excelRow.getVariableKey().isBlank()) {
      return rowPrefix + MessageUtils.getMessage("system.excel.variableKey.required", "变量键不能为空");
    }
    if (excelRow.getVariableValue() == null || excelRow.getVariableValue().isBlank()) {
      return rowPrefix + MessageUtils.getMessage("system.excel.variableValue.required", "变量值不能为空");
    }
    // 值类型校验
    if (excelRow.getValueType() != null && !excelRow.getValueType().isBlank()) {
      try {
        ConfigValueType.validate(excelRow.getValueType());
      } catch (IllegalArgumentException e) {
        return rowPrefix + MessageUtils.getMessage("system.excel.valueType.invalid",
            new Object[] {excelRow.getValueType()}, "值类型不合法: " + excelRow.getValueType());
      }
    }
    // DB 唯一性校验
    VariableVO existing = variableRepository.findByKeyIgnoreStatus(excelRow.getVariableKey()).orElse(null);
    if (existing != null) {
      return rowPrefix + MessageUtils.getMessage("system.excel.variableKey.duplicate",
          new Object[] {excelRow.getVariableKey()}, "变量已存在(" + excelRow.getVariableKey() + ")");
    }
    return null;
  }

  /**
   * Excel 行转换为变量 VO（私有）。
   *
   * @param excelRow Excel 行数据
   * @return 变量 VO
   */
  private VariableVO toVariableVO(VariableExcelVO excelRow) {
    VariableVO vo = new VariableVO();
    vo.setVariableKey(excelRow.getVariableKey());
    vo.setVariableValue(excelRow.getVariableValue());
    vo.setValueType(excelRow.getValueType());
    vo.setDescription(excelRow.getDescription());
    vo.setStatus(excelRow.getStatus());
    return vo;
  }

  /**
   * 变量 VO 转 Excel VO（私有）。
   *
   * @return Excel VO
   */
  private VariableExcelVO toExcelVO(VariableVO vo) {
    VariableExcelVO excelVO = new VariableExcelVO();
    excelVO.setVariableKey(vo.getVariableKey());
    excelVO.setVariableValue(vo.getVariableValue());
    excelVO.setValueType(vo.getValueType());
    excelVO.setDescription(vo.getDescription());
    excelVO.setStatus(vo.getStatus());
    return excelVO;
  }

  /**
   * 批量保存有效变量（私有）。
   *
   * @param validItems 校验通过的变量列表
   * @param errors 错误收集器（保存失败时追加）
   * @return 保存成功条数
   */
  private int saveValidItemsBatch(List<VariableVO> validItems, List<String> errors) {
    if (validItems.isEmpty()) {
      return 0;
    }
    try {
      for (VariableVO vo : validItems) {
        VariableDTO dto = toDto(vo);
        variableRepository.insert(dto);
      }
      // 精准失效缓存：按涉及 variableKey 逐一失效
      validItems.forEach(vo -> evictVariable(vo.getVariableKey()));
      // 发布变更事件
      validItems.forEach(vo -> publishVariableChangedEvent(vo.getVariableKey(), "导入变量"));
      return validItems.size();
    } catch (Exception e) {
      errors.add("批量导入失败: " + e.getMessage());
      return 0;
    }
  }

  /**
   * 将 VariableVO 转换为 VariableDTO（避免 server 层依赖 infra 的 SystemConverter）。
   *
   * @return 变量 DTO
   */
  private VariableDTO toDto(VariableVO vo) {
    VariableDTO dto = new VariableDTO();
    dto.setId(vo.getId());
    dto.setVariableKey(vo.getVariableKey());
    dto.setVariableValue(vo.getVariableValue());
    dto.setValueType(vo.getValueType());
    dto.setDescription(vo.getDescription());
    dto.setStatus(vo.getStatus());
    return dto;
  }
}
