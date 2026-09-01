package com.njydsz.literule.server.config;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.event.service.OutboxService;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.search.sync.SearchIndexEventBridge;
import com.njydsz.common.util.id.IdGenerator;
import com.njydsz.literule.domain.RuleEngine;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.dto.RuleVersionDTO;
import com.njydsz.literule.domain.enums.RuleSeverity;
import com.njydsz.literule.domain.enums.RuleStatus;
import com.njydsz.literule.domain.event.RuleConfigRefreshEvent;
import com.njydsz.literule.domain.expression.ExpressionEngine;
import com.njydsz.literule.domain.expression.ExpressionTraceNode;
import com.njydsz.literule.domain.repository.RuleDefinitionRepository;
import com.njydsz.literule.domain.repository.RuleVersionRepository;
import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.domain.vo.RuleResultVO;
import com.njydsz.literule.domain.vo.RuleVersionVO;
import com.njydsz.literule.server.impl.ExpressionRule;
import com.njydsz.literule.server.spi.RuleConfigBroadcaster;
import com.njydsz.literule.server.spi.RuleConfigProvider;

/**
 * 规则管理服务
 *
 * <p>提供规则 CRUD、启停、版本管理、dry-run 仿真等管理操作。 变更操作完成后发布 {@link RuleConfigRefreshEvent} 触发热刷新。
 *
 * <p>若配置了 {@link RuleConfigBroadcaster}，变更事件将通过广播器同步到所有节点， 实现分布式热加载一致性。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class RuleAdminService {

    /** 节点 ID 取前缀长度 */
  private static final int NODE_ID_PREFIX_LENGTH = 8;

  /** 规则路径最大长度 */
  private static final int MAX_PATH_LENGTH = 512;

  /** 规则路径最大段数 */
  private static final int MAX_PATH_SEGMENTS = 5;

  /** 规则引擎实例，用于规则注册/注销和 dry-run 仿真 */
  private final RuleEngine ruleEngine;

  /** 表达式求值器，用于编译条件/严重度表达式并构建 ExpressionRule */
  private final ExpressionEngine evaluator;

  /** 规则配置提供者（SPI），从数据库/配置中心加载规则定义 */
  private final RuleConfigProvider configProvider;

  /** 规则版本仓库（SPI），保存规则变更版本以支持回滚；为 null 时不支持版本管理 */
  private final RuleVersionRepository versionRepository;

  /** Spring 事件发布器，变更后发布 RuleConfigRefreshEvent 触发热加载 */
  private final ApplicationEventPublisher eventPublisher;

  /** 分布式广播器（可选，配置后支持多实例热加载一致性） */
  private RuleConfigBroadcaster broadcaster;

  /** 事务性 Outbox 服务（可选，配置后规则变更事件同事务落 Outbox 表，广播失败可重试） */
  private OutboxService outboxService;

  /** 搜索索引事件桥接器（可选，用于将规则变更同步到统一搜索索引） */
  private ObjectProvider<SearchIndexEventBridge> searchIndexEventBridgeProvider;

  /** 规则定义仓库（用于分页查询和搜索） */
  private final RuleDefinitionRepository ruleDefinitionRepository;

  /** 规则搜索服务（数据库级搜索，替代内存过滤） */
  private final RuleSearchService searchService;

  /** 当前节点标识（用于广播防循环） */
  private String nodeId;

  /** 是否启用 dry-run 仿真（对应 ydsz.literule.dryRunEnabled 配置） */
  private boolean dryRunEnabled = true;

  /** 规则冲突检测器（可选，1.4.0 起支持） */
  private RuleConflictDetector conflictDetector;

  /** 是否启用冲突检测（对应 ydsz.literule.conflictDetectionEnabled） */
  private boolean conflictDetectionEnabled = true;

  /** ERROR 级别冲突是否阻塞保存（对应 ydsz.literule.conflictDetectionBlockOnError） */
  private boolean conflictDetectionBlockOnError = true;

  /**
   * 构造规则管理服务
   *
   * @param ruleEngine 规则引擎
   * @param evaluator 表达式求值器
   * @param configProvider 规则配置提供者
   * @param versionRepository 版本仓库（可为 null）
   * @param eventPublisher 事件发布器
   * @param ruleDefinitionRepository 规则定义仓库（用于分页查询和搜索）
   */
  public RuleAdminService(
      RuleEngine ruleEngine,
      ExpressionEngine evaluator,
      RuleConfigProvider configProvider,
      RuleVersionRepository versionRepository,
      ApplicationEventPublisher eventPublisher,
      RuleDefinitionRepository ruleDefinitionRepository) {
    this.ruleEngine = ruleEngine;
    this.evaluator = evaluator;
    this.configProvider = configProvider;
    this.versionRepository = versionRepository;
    this.eventPublisher = eventPublisher;
    this.ruleDefinitionRepository = ruleDefinitionRepository;
    this.searchService = new RuleSearchService(ruleDefinitionRepository);
    this.nodeId = IdGenerator.nextIdStr().substring(0, NODE_ID_PREFIX_LENGTH);
  }

  /**
   * 设置分布式广播器
   *
   * @param broadcaster 广播器实例
   * @since 1.0.0
   */
  public void setBroadcaster(RuleConfigBroadcaster broadcaster) {
    this.broadcaster = broadcaster;
  }

  /**
   * 设置事务性 Outbox 服务（P0-A1 热更新一致性）
   *
   * <p>配置后，规则变更事件的分布式广播改为"同事务写 Outbox 表"， 由 {@code RuleConfigOutboxRelay} 低延迟广播 +
   * {@code OutboxProcessor} 失败重试， 消除"DB 已提交但广播失败导致其他节点缓存陈旧"的双写不一致。 未配置时降级为直接广播（向后兼容）。
   *
   * @param outboxService Outbox 服务实例（可为 null）
   * @since 1.0.0
   */
  public void setOutboxService(OutboxService outboxService) {
    this.outboxService = outboxService;
  }

  /**
   * 设置搜索索引事件桥接器（可选）。
   *
   * <p>用于将规则创建/更新操作异步同步到 ydsz-common-search 统一搜索索引。 未引入 {@code ydsz-common-search} 时可不设置，同步自动跳过。
   *
   * @param searchIndexEventBridgeProvider 桥接器的惰性提供者
   * @since 1.0.0
   */
  public void setSearchIndexEventBridgeProvider(
      ObjectProvider<SearchIndexEventBridge> searchIndexEventBridgeProvider) {
    this.searchIndexEventBridgeProvider = searchIndexEventBridgeProvider;
  }

  /**
   * 设置节点标识
   *
   * @param nodeId 节点标识
   * @since 1.0.0
   */
  public void setNodeId(String nodeId) {
    this.nodeId = nodeId;
  }

  /**
   * 设置是否启用 dry-run 仿真
   *
   * @param dryRunEnabled 是否启用
   * @since 1.0.0
   */
  public void setDryRunEnabled(boolean dryRunEnabled) {
    this.dryRunEnabled = dryRunEnabled;
  }

  /**
   * 设置规则冲突检测器
   *
   * @param conflictDetector 冲突检测器实例
   * @since 1.0.0
   */
  public void setConflictDetector(RuleConflictDetector conflictDetector) {
    this.conflictDetector = conflictDetector;
  }

  /**
   * 设置是否启用冲突检测
   *
   * @param conflictDetectionEnabled 是否启用
   * @since 1.0.0
   */
  public void setConflictDetectionEnabled(boolean conflictDetectionEnabled) {
    this.conflictDetectionEnabled = conflictDetectionEnabled;
  }

  /**
   * 设置 ERROR 级别冲突是否阻塞保存
   *
   * @param conflictDetectionBlockOnError 是否阻塞
   * @since 1.0.0
   */
  public void setConflictDetectionBlockOnError(boolean conflictDetectionBlockOnError) {
    this.conflictDetectionBlockOnError = conflictDetectionBlockOnError;
  }

  /**
   * 查询全部规则定义
   *
   * @return 全部规则定义
   */
  public List<RuleDefinitionDTO> listAll() {
    return configProvider.loadAllRules();
  }

  /**
   * 分页查询规则定义（P1-2 分页标准化）
   *
   * <p>通过 Repository 分页查询，返回框架无关的 {@link
   * com.njydsz.common.core.response.PageResponse}。 分页参数通过 {@link com.njydsz.common.domain.query.PageQuery}
   * 传入， 支持 pageNum/pageSize/orderItems/cursor 等分页能力。
   *
   * @param pageQuery 分页查询参数
   * @return 分页结果（PageResponse 封装的 RuleDefinitionDTO 列表）
   * @since 1.0.0
   */
  public PageResponse<List<RuleDefinitionDTO>> pageRuleDefinitions(
      PageQuery pageQuery) {
    PageResponse<List<RuleDefinitionVO>> voPage = ruleDefinitionRepository.pageRuleDefinitions(pageQuery);
    // VO → RuleDefinitionDTO 转换
    List<RuleDefinitionDTO> records =
        voPage.getData().stream().map(this::voToRuleDefinition).toList();
    return PageResponse.success(
        voPage.getTotal(), voPage.getPageNum(), voPage.getPageSize(), records);
  }

  /**
   * RuleDefinitionVO → RuleDefinitionDTO 转换
   *
   * @param vo 规则定义 VO
   * @return RuleDefinitionDTO
   */
  private RuleDefinitionDTO voToRuleDefinition(RuleDefinitionVO vo) {
    RuleDefinitionDTO def = new RuleDefinitionDTO();
    def.setCode(vo.getRuleCode());
    def.setName(vo.getRuleName());
    def.setCategory(vo.getCategory());
    def.setCategoryPath(vo.getCategoryPath());
    def.setOwner(vo.getOwner());
    def.setDescription(vo.getDescription());
    def.setConditionExpression(vo.getConditionExpression());
    def.setSeverityExpression(vo.getSeverityExpression());
    def.setDefaultSeverity(
        vo.getDefaultSeverity() != null
            ? RuleSeverity.fromCode(vo.getDefaultSeverity())
            : null);
    def.setTitleTemplate(vo.getTitleTemplate());
    def.setDescriptionTemplate(vo.getDescriptionTemplate());
    def.setPriority(vo.getPriority());
    def.setEnabled(vo.getEnabled() != null && vo.getEnabled());
    def.setScope(vo.getScope());
    def.setMutexGroup(vo.getMutexGroup());
    def.setVersion(vo.getVersion() != null ? vo.getVersion() : 1);
    def.setStatus(vo.getStatus());
    def.setEffectiveFrom(vo.getEffectiveFrom());
    def.setEffectiveTo(vo.getEffectiveTo());
    def.setReviewedBy(vo.getReviewedBy());
    def.setReviewedAt(vo.getReviewedAt());
    def.setReviewComment(vo.getReviewComment());
    def.setCanaryRatio(vo.getCanaryRatio() != null ? vo.getCanaryRatio() : 0.0);
    def.setCanaryConditionExpression(vo.getCanaryConditionExpression());
    def.setCanarySeverityExpression(vo.getCanarySeverityExpression());
    return def;
  }

  /**
   * 查询单条规则定义
   *
   * @param ruleCode 规则编码
   * @return 规则定义
   */
  public RuleDefinitionDTO getByCode(String ruleCode) {
    return configProvider.findByCode(ruleCode);
  }

  /**
   * 全文搜索规则（数据库级 LIKE 查询）
   *
   * <p>委托给 {@link RuleSearchService}，使用数据库级 LIKE 查询替代内存过滤，提升大规则量场景下的搜索性能。
   *
   * @param query 搜索关键词（空格分隔为 AND 条件，null/空返回全部）
   * @param status 状态过滤（null=不过滤）
   * @param category 分类过滤（null=不过滤）
   * @param enabled 启停过滤（null=不过滤）
   * @param offset 分页偏移
   * @param limit 分页大小
   * @return 搜索结果列表
   * @since 1.0.0
   */
  public List<RuleDefinitionDTO> search(
      String query, String status, String category, Boolean enabled, int offset, int limit) {
    return searchService.search(query, status, category, enabled, offset, limit);
  }

  /**
   * 统计搜索结果总数（不分页）
   *
   * @param query 搜索关键词
   * @param status 状态过滤
   * @param category 分类过滤
   * @param enabled 启停过滤
   * @return 匹配的规则总数
   * @since 1.0.0
   */
  public int searchCount(String query, String status, String category, Boolean enabled) {
    return searchService.searchCount(query, status, category, enabled);
  }

  /**
   * 新增/更新规则（自动保存版本快照）
   *
   * <p>整个操作在单个事务内完成：规则定义持久化 + 版本快照保存原子提交。 版本快照保存失败将触发整体回滚（避免主表已提交但版本记录缺失的数据不一致）。 热刷新事件由 {@link
   * RuleHotReloader} 通过 {@code @TransactionalEventListener(AFTER_COMMIT)} 在事务提交后异步触发，回滚时不触发热加载。
   *
   * @param definition 规则定义
   * @param operator 操作人
   * @param changeDesc 变更描述
   * @return 保存后的规则定义
   */
  @Transactional(rollbackFor = Exception.class)
  public RuleDefinitionDTO save(RuleDefinitionDTO definition, String operator, String changeDesc) {
    // 校验表达式语法
    if (!evaluator.validate(definition.getConditionExpression())) {
      throw new IllegalArgumentException("条件表达式语法错误: " + definition.getConditionExpression());
    }
    if (definition.getSeverityExpression() != null
        && !definition.getSeverityExpression().isBlank()) {
      if (!evaluator.validate(definition.getSeverityExpression())) {
        throw new IllegalArgumentException("严重度表达式语法错误: " + definition.getSeverityExpression());
      }
    }

    // 校验生命周期状态合法性 + 状态转换合法性
    validateStatusTransition(definition);

    // 冲突检测（可选，1.4.0 起支持）
    detectConflicts(definition);

    RuleDefinitionDTO saved = configProvider.save(definition, operator);

    // 保存版本快照（同一事务内，失败则整体回滚）
    if (versionRepository != null) {
      RuleVersionDTO saveDTO = new RuleVersionDTO();
      saveDTO.setRuleCode(saved.getCode());
      saveDTO.setRuleName(saved.getName());
      saveDTO.setVersion(saved.getVersion());
      saveDTO.setDefinitionJson(YdszJson.toJson(saved));
      saveDTO.setChangeDesc(changeDesc);
      saveDTO.setOperator(operator);
      versionRepository.saveVersion(saveDTO);
    }

    // 发布热刷新事件（基于持久化后的 version 判断 CREATE/UPDATE）
    RuleConfigRefreshEvent.ChangeType changeType =
        saved.getVersion() > 1
            ? RuleConfigRefreshEvent.ChangeType.UPDATE
            : RuleConfigRefreshEvent.ChangeType.CREATE;
    publishRefreshEvent(RuleConfigRefreshEvent.of(saved.getCode(), changeType, operator));

    // 同步到统一搜索索引（ydsz_wiki_search_index）
    syncSearchIndex(saved);

    log.info(
        "[LiteRule] 规则已保存: code={}, version={}, operator={}, broadcast={}",
        saved.getCode(),
        saved.getVersion(),
        operator,
        broadcaster != null);
    return saved;
  }

  /**
   * 切换规则启停
   *
   * @param ruleCode 规则编码
   * @param enabled 是否启用
   * @param operator 操作人
   */
  @Transactional(rollbackFor = Exception.class)
  public void toggle(String ruleCode, boolean enabled, String operator) {
    configProvider.toggleEnabled(ruleCode, enabled, operator);
    publishRefreshEvent(
        RuleConfigRefreshEvent.of(ruleCode, RuleConfigRefreshEvent.ChangeType.TOGGLE, operator));
    log.info("[LiteRule] 规则启停切换: code={}, enabled={}, operator={}", ruleCode, enabled, operator);
  }

  /**
   * 更新规则责任人（P1-9 规则目录树）
   *
   * <p>Owner 主要用于异常告警通知、AB Test 自动回滚通知、巡检派单。 操作不触发热刷新事件（仅元数据变更），但会写审计日志。
   *
   * @param ruleCode 规则编码
   * @param owner 责任人（工号/用户名）
   * @param operator 操作人
   * @since 1.0.0
   */
  @Transactional(rollbackFor = Exception.class)
  public void updateOwner(String ruleCode, String owner, String operator) {
    if (ruleCode == null || ruleCode.isBlank()) {
      throw new IllegalArgumentException("ruleCode 不能为空");
    }
    RuleDefinitionDTO existing = configProvider.findByCode(ruleCode);
    if (existing == null) {
      throw new IllegalArgumentException("规则不存在: " + ruleCode);
    }
    existing.setOwner(owner);
    configProvider.save(existing, operator);
    log.info("[LiteRule] 规则责任人更新: code={}, owner={}, operator={}", ruleCode, owner, operator);
  }

  /**
   * 更新规则分类路径（P1-9 规则目录树）
   *
   * <p>categoryPath 用 {@code /} 分隔的多级分类。校验规则：
   *
   * <ul>
   *   <li>不能为空字符串
   *   <li>段之间用 {@code /} 分隔，每段不能包含特殊字符
   *   <li>深度不超过 5 级
   * </ul>
   *
   * 操作不触发热刷新事件（仅元数据变更）。
   *
   * @param ruleCode 规则编码
   * @param path 分类路径
   * @param operator 操作人
   * @since 1.0.0
   */
  @Transactional(rollbackFor = Exception.class)
  public void updateCategoryPath(String ruleCode, String path, String operator) {
    if (ruleCode == null || ruleCode.isBlank()) {
      throw new IllegalArgumentException("ruleCode 不能为空");
    }
    validateCategoryPath(path);
    RuleDefinitionDTO existing = configProvider.findByCode(ruleCode);
    if (existing == null) {
      throw new IllegalArgumentException("规则不存在: " + ruleCode);
    }
    existing.setCategoryPath(path);
    // 一级分类同步到 category
    if (path != null && !path.isBlank()) {
      int slashIdx = path.indexOf('/');
      existing.setCategory(slashIdx > 0 ? path.substring(0, slashIdx) : path);
    }
    configProvider.save(existing, operator);
    log.info("[LiteRule] 规则分类路径更新: code={}, path={}, operator={}", ruleCode, path, operator);
  }

  /** 校验分类路径合法性 */
  private void validateCategoryPath(String path) {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("分类路径不能为空");
    }
    if (path.length() > MAX_PATH_LENGTH) {
      throw new IllegalArgumentException("分类路径长度不能超过 512");
    }
    if (path.startsWith("/") || path.endsWith("/")) {
      throw new IllegalArgumentException("分类路径不能以 / 开头或结尾: " + path);
    }
    if (path.contains("//")) {
      throw new IllegalArgumentException("分类路径不能包含连续 / : " + path);
    }
    String[] segs = path.split("/");
    if (segs.length > MAX_PATH_SEGMENTS) {
      throw new IllegalArgumentException("分类路径深度不能超过 5 级: " + path);
    }
    for (String s : segs) {
      if (!s.matches("[\\w\\u4e00-\\u9fa5-]+")) {
        throw new IllegalArgumentException("分类路径段包含非法字符: " + s);
      }
    }
  }

  /**
   * 分页查询规则版本历史
   *
   * @param ruleCode 规则编码
   * @param pageQuery 分页查询参数
   * @return 分页结果
   */
  public PageResponse<List<RuleVersionVO>> pageVersions(String ruleCode, PageQuery pageQuery) {
    if (versionRepository == null) {
      return PageResponse.empty(
          (long) pageQuery.getEffectivePageNum(),
          (long) pageQuery.getEffectivePageSize());
    }
    return versionRepository.pageVersions(
        ruleCode, pageQuery.getEffectivePageNum(), pageQuery.getEffectivePageSize());
  }

  /**
   * 查询规则版本历史（全量）
   *
   * <p>内部使用（如 versionDiff 需要全量列表对比），API 层请使用分页版本。
   *
   * @param ruleCode 规则编码
   * @return 版本历史
   */
  public List<RuleVersionVO> listVersions(String ruleCode) {
    if (versionRepository == null) {
      return List.of();
    }
    return versionRepository.listVersions(ruleCode);
  }

  /**
   * 回滚到指定版本
   *
   * @param ruleCode 规则编码
   * @param version 目标版本号
   * @param operator 操作人
   * @return 回滚后的规则定义
   */
  @Transactional(rollbackFor = Exception.class)
  public Optional<RuleDefinitionVO> rollback(String ruleCode, int version, String operator) {
    if (versionRepository == null) {
      throw new IllegalStateException("版本仓库未配置，不支持回滚");
    }
    Optional<RuleDefinitionVO> restored = versionRepository.rollback(ruleCode, version, operator);
    publishRefreshEvent(
        RuleConfigRefreshEvent.of(ruleCode, RuleConfigRefreshEvent.ChangeType.UPDATE, operator));
    log.info("[LiteRule] 规则已回滚: code={}, version={}, operator={}", ruleCode, version, operator);
    return restored;
  }

  /**
   * Dry-run 仿真（不发布事件、不记录统计）
   *
   * <p>当 {@code dryRunEnabled=false} 时抛出 {@link IllegalStateException}， 消费
   * ydsz.literule.dryRunEnabled 配置开关。
   *
   * <p>支持短路返回优化（P2-5）：
   *
   * <ul>
   *   <li>当 {@code limit != null} 且 {@code minSeverity != null} 时，在按优先级遍历过程中， 已命中（triggered=true）且严重度不低于
   *       {@code minSeverity} 的结果数量达到 {@code limit} 时立即停止评估—— 后续低优先级规则无需评估，节省计算资源
   *   <li>仅对全规则仿真（{@code ruleCode == null}）生效，单条规则仿真忽略此参数
   * </ul>
   *
   * @param ruleCode 规则编码（null 表示仿真全部规则）
   * @param facts 事实数据
   * @param limit 返回结果数量上限（可为 null，表示不限制）。需配合 {@code minSeverity} 使用
   * @param minSeverity 最低严重度阈值（可为 null，表示不限制）。需配合 {@code limit} 使用
   * @return 仿真结果列表
   * @throws IllegalStateException dry-run 功能被禁用
   */
  public List<RuleResultVO> dryRun(
      String ruleCode, Map<String, Object> facts, Integer limit, RuleSeverity minSeverity) {
    if (!dryRunEnabled) {
      throw new IllegalStateException("Dry-run 功能已被禁用（ydsz.literule.dryRunEnabled=false）");
    }
    RuleContextVO context = RuleContextVO.of(facts, "DRY_RUN", "MANUAL");

    if (ruleCode != null) {
      // 单条规则仿真
      RuleDefinitionDTO def = configProvider.findByCode(ruleCode);
      if (def == null) {
        return List.of();
      }
      ExpressionRule rule = new ExpressionRule(def, evaluator);
      RuleResultVO result = rule.evaluate(context);
      return List.of(result);
    }

    // 全部规则仿真（支持短路返回优化）
    return ruleEngine.dryRun(context, limit, minSeverity);
  }

  /**
   * Dry-run 仿真（简化版本，不支持短路参数）
   *
   * @param ruleCode 规则编码（null 表示仿真全部规则）
   * @param facts 事实数据
   * @return 仿真结果列表
   */
  public List<RuleResultVO> dryRun(String ruleCode, Map<String, Object> facts) {
    return dryRun(ruleCode, facts, null, null);
  }

  /**
   * 用指定表达式评估事实数据（P2-2 规则变更影响分析）
   *
   * <p>构造临时规则定义，用新的条件表达式 / 严重度表达式对历史 facts 重新评估， 用于预览规则变更后的影响范围。不发布事件、不记录统计、不持久化。
   *
   * @param ruleCode 规则编码（用于结果标识）
   * @param conditionExpression 新条件表达式
   * @param severityExpression 新严重度表达式（可为 null）
   * @param defaultSeverity 默认严重度（severityExpression 为空时使用）
   * @param facts 事实数据
   * @return 评估结果；表达式非法或评估异常时返回未触发结果
   * @since 1.0.0
   */
  public RuleResultVO evaluateWithExpression(
      String ruleCode,
      String conditionExpression,
      String severityExpression,
      RuleSeverity defaultSeverity,
      Map<String, Object> facts) {
    // 表达式语法校验
    if (!evaluator.validate(conditionExpression)) {
      return RuleResultVO.notTriggered(ruleCode);
    }
    if (severityExpression != null
        && !severityExpression.isBlank()
        && !evaluator.validate(severityExpression)) {
      return RuleResultVO.notTriggered(ruleCode);
    }

    // 构造临时规则定义
    RuleDefinitionDTO tempDef =
        RuleDefinitionDTO.builder()
            .code(ruleCode)
            .name("影响分析-" + ruleCode)
            .conditionExpression(conditionExpression)
            .severityExpression(severityExpression)
            .defaultSeverity(defaultSeverity != null ? defaultSeverity : RuleSeverity.YELLOW)
            .build();

    ExpressionRule rule = new ExpressionRule(tempDef, evaluator);
    RuleContextVO context =
        RuleContextVO.of(facts != null ? facts : Collections.emptyMap(), "IMPACT_PREVIEW", "MANUAL");
    try {
      return rule.evaluate(context);
    } catch (Exception e) {
      log.warn(
          "[LiteRule] 影响分析评估异常: ruleCode={}, expr={}, error={}",
          ruleCode,
          conditionExpression,
          e.getMessage());
      return RuleResultVO.notTriggered(ruleCode);
    }
  }

  /**
   * 校验表达式语法
   *
   * @param expression 表达式
   * @return true=合法
   */
  public boolean validateExpression(String expression) {
    return evaluator.validate(expression);
  }

  /**
   * 表达式追踪求值（P0-2 表达式级追踪/归因）
   *
   * <p>将表达式执行过程转换为计算树，用于规则归因分析、短路排查和中间结果可视化。
   *
   * <p>LiteExpr 引擎提供完整的追踪树（逻辑/比较/变量节点 + 短路分析）。
   *
   * @param expression 表达式字符串
   * @param facts 事实数据
   * @return 追踪结果（含求值结果和追踪树）
   * @since 1.0.0
   */
  public ExpressionEngine.TraceResult traceExpression(
      String expression, Map<String, Object> facts) {
    if (expression == null || expression.isBlank()) {
      ExpressionTraceNode root =
          ExpressionTraceNode.builder()
              .nodeType(ExpressionTraceNode.NodeType.ROOT)
              .expression(expression)
              .result(false)
              .error("表达式为空")
              .build();
      return new ExpressionEngine.TraceResult(false, root);
    }
    RuleContextVO context =
        RuleContextVO.of(facts != null ? facts : Collections.emptyMap(), "EXPR_TRACE", "MANUAL");
    return evaluator.evalBooleanWithTrace(expression, context);
  }

  /**
   * 校验规则状态值合法性 + 状态转换合法性
   *
   * <p>规则：
   *
   * <ul>
   *   <li>status 为空：跳过校验（由数据库默认值生效，向后兼容）
   *   <li>status 非法值（无法 fromCode 解析）：抛 IllegalArgumentException
   *   <li>新建（数据库中不存在该 code）：限制初始状态只能为 DRAFT 或 PUBLISHED
   *   <li>更新（数据库中已存在）：校验 {@code current.canTransitionTo(target)}， 状态未变化时放行
   * </ul>
   *
   * @param definition 待保存的规则定义
   * @since 1.0.0
   */
  private void validateStatusTransition(RuleDefinitionDTO definition) {
    String statusStr = definition.getStatus();
    if (statusStr == null || statusStr.isBlank()) {
      return;
    }
    RuleStatus target = RuleStatus.fromCode(statusStr);
    if (target == null) {
      throw new IllegalArgumentException(
          "非法的规则状态: "
              + statusStr
              + "，合法值: DRAFT/REVIEW/REVIEW_L1/REVIEW_L2/REVIEW_FINAL/PUBLISHED/DISABLED/ARCHIVED");
    }

    RuleDefinitionDTO existing = configProvider.findByCode(definition.getCode());
    if (existing == null) {
      // 新建：限制初始状态白名单（禁止 REVIEW/DISABLED/ARCHIVED 作为初始状态）
      if (target != RuleStatus.DRAFT && target != RuleStatus.PUBLISHED) {
        throw new IllegalStateException("新建规则的初始状态只能为 DRAFT 或 PUBLISHED，禁止: " + target.getDesc());
      }
      return;
    }

    // 更新：校验状态转换合法性（状态未变化时直接放行）
    RuleStatus current = parseStatusSafely(existing.getStatus());
    if (target != current && !current.canTransitionTo(target)) {
      throw new IllegalStateException(
          "不允许的状态转换: "
              + current.getDesc()
              + " -> "
              + target.getDesc()
              + "（合法转换路径见 RuleStatus#canTransitionTo）");
    }
  }

  /**
   * 安全解析状态字符串，异常时回退到 PUBLISHED（数据库默认值）
   *
   * @param status 状态字符串
   * @return RuleStatus；无法解析时返回 PUBLISHED
   */
  private RuleStatus parseStatusSafely(String status) {
    RuleStatus parsed = RuleStatus.fromCode(status);
    return parsed != null ? parsed : RuleStatus.PUBLISHED;
  }

  /**
   * 执行规则冲突检测
   *
   * <p>根据配置决定是否启用、ERROR 级别冲突是否阻塞保存。 WARN 级别冲突仅记录日志。
   *
   * @param definition 待保存的规则定义
   * @since 1.0.0
   */
  private void detectConflicts(RuleDefinitionDTO definition) {
    if (!conflictDetectionEnabled || conflictDetector == null) {
      return;
    }
    List<RuleConflict> conflicts;
    try {
      conflicts = conflictDetector.detect(definition);
    } catch (Exception e) {
      log.warn("[LiteRule-Conflict] 冲突检测执行异常，跳过: {}", e.getMessage());
      return;
    }
    if (conflicts == null || conflicts.isEmpty()) {
      return;
    }

    boolean hasError = false;
    for (RuleConflict c : conflicts) {
      if (c.getLevel() == RuleConflict.Level.ERROR) {
        hasError = true;
        log.error(
            "[LiteRule-Conflict] {} 冲突: {} vs {} - {}",
            c.getType(),
            c.getNewRuleCode(),
            c.getConflictingRuleCode(),
            c.getDescription());
      } else {
        log.warn(
            "[LiteRule-Conflict] {} 提示: {} vs {} - {}",
            c.getType(),
            c.getNewRuleCode(),
            c.getConflictingRuleCode(),
            c.getDescription());
      }
    }

    if (hasError && conflictDetectionBlockOnError) {
      RuleConflict firstError =
          conflicts.stream()
              .filter(c -> c.getLevel() == RuleConflict.Level.ERROR)
              .findFirst()
              .orElse(null);
      throw new IllegalStateException(
          "规则冲突检测未通过（"
              + conflicts.size()
              + " 项冲突，其中 "
              + conflicts.stream().filter(c -> c.getLevel() == RuleConflict.Level.ERROR).count()
              + " 项 ERROR）: "
              + (firstError != null ? firstError.getDescription() : ""));
    }
  }

  /**
   * 发布规则刷新事件（本地 + 分布式广播，P0-A1 引入事务性 Outbox）
   *
   * <p><b>分布式广播路径（P0-A1 增强）：</b>
   *
   * <ol>
   *   <li>本地 Spring 事件（当前节点热加载，同步执行）
   *   <li>若配置了 Outbox 服务：同一事务内将事件写入 Outbox 表， 由 {@code RuleConfigOutboxRelay}
   *       在事务提交后低延迟广播，失败由 {@code OutboxProcessor} 指数退避重试 —— 消除"DB 已提交但广播失败"
   *       的双写不一致风险
   *   <li>Outbox 不可用时降级为直接广播（向后兼容）
   * </ol>
   *
   * @param event 规则变更事件
   * @since 1.0.0
   */
  private void publishRefreshEvent(RuleConfigRefreshEvent event) {
    // 1. 本地事件（当前节点热加载）
    eventPublisher.publishEvent(event);

    // 2. 分布式广播：优先走事务性 Outbox（同事务落表 + 异步可靠投递）
    if (outboxService != null) {
      try {
        outboxService.appendToOutbox(event);
        log.debug(
            "[LiteRule] 规则变更事件已写入 Outbox: ruleCode={}, changeType={}",
            event.getRuleCode(),
            event.getChangeType());
        return;
      } catch (Exception e) {
        log.warn("[LiteRule] Outbox 写入失败，降级为直接广播: {}", e.getMessage());
      }
    }

    // 3. 降级：Outbox 不可用时直接广播（向后兼容）
    if (broadcaster != null && broadcaster.isAvailable()) {
      try {
        broadcaster.broadcast(event, nodeId);
      } catch (Exception e) {
        log.warn("[LiteRule] 分布式广播失败，仅当前节点已刷新: {}", e.getMessage());
      }
    }
  }

  /**
   * 将规则数据同步到统一搜索索引（ydsz_wiki_search_index）。
   *
   * <p>通过 {@link SearchIndexEventBridge} 异步写入，不阻塞主业务流程。 未引入 {@code ydsz-common-search} 时桥接器为空，跳过同步。
   *
   * @param definition 规则定义
   */
  private void syncSearchIndex(RuleDefinitionDTO definition) {
    if (searchIndexEventBridgeProvider == null) {
      return;
    }
    SearchIndexEventBridge bridge = searchIndexEventBridgeProvider.getIfAvailable();
    if (bridge != null) {
      bridge.indexUpsert("rule", definition);
    }
  }
}


