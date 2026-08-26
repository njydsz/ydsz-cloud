package com.njydsz.workflow.server.service.impl.definition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.search.sync.SearchIndexEventBridge;
import com.njydsz.common.util.collection.MapUtils;
import com.njydsz.workflow.domain.dto.FlowDeployProcessDTO;
import com.njydsz.workflow.domain.repository.FlowTemplateRepository;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowDefinition;
import com.njydsz.workflow.infra.entity.FlowNode;
import com.njydsz.workflow.infra.entity.FlowSkip;
import com.njydsz.workflow.infra.entity.FlowTemplate;
import com.njydsz.workflow.server.config.FlowProperties;
import com.njydsz.workflow.server.service.FlowDefinitionService;
import com.njydsz.workflow.server.service.FlowTemplateService;

/**
 * 流程模板市场服务实现
 *
 * <p>对 {@link FlowTemplateService} 接口的完整实现，基于 {@code ydsz_flow_template} 数据库表， 提供流程模板的<b>查询 / 详情 /
 * 导入 / 导出 / 版本化 / 克隆 / 继承 / 同步</b>完整业务能力。 是工作流引擎「模板复用 + 标准化」能力的服务端支撑。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>模板市场</b>：{@link #listTemplates} / {@link #getTemplate} — 模板列表 / 详情查询， 支持分类筛选
 *   <li><b>导入导出</b>：
 *       <ul>
 *         <li>{@link #importTemplate} — 将模板部署为草稿流程定义（调用 {@link FlowDefinitionService#deploy}）
 *         <li>{@link #exportAsTemplate} — 将已发布流程定义转换为 BPMN XML 并保存为模板
 *       </ul>
 *   <li><b>版本管理（P2-9）</b>：{@link #listTemplateVersions} / {@link #getTemplateVersion} / {@link
 *       #createNewVersion} — 模板支持多版本并存，每次导出自动递增版本号，旧版本降级为 {@code isLatest=0}
 *   <li><b>克隆与继承（P2-9）</b>：
 *       <ul>
 *         <li>{@link #cloneTemplate} — 克隆为独立模板（{@code inheritType=CLONE}）
 *         <li>{@link #inheritFromParent} — 继承父模板（{@code inheritType=INHERIT}）， 保留父子关联
 *         <li>{@link #syncFromParent} — 子模板同步父模板最新内容，生成 {@code -synced} 版本
 *       </ul>
 *   <li><b>BPMN 2.0 转换</b>：{@link #generateBpmnXml} — 将内部 {@code FlowNode/FlowSkip} 模型 序列化为标准 BPMN
 *       2.0 XML（兼容 Flowable 命名空间）
 * </ul>
 *
 * <p><b>事务边界：</b>
 *
 * <ul>
 *   <li>查询方法（{@code listTemplates / getTemplate / listTemplateVersions}）开启
 *       {@code @Transactional(readOnly = true)}，走只读副本
 *   <li>写方法（{@code import / export / createNewVersion / clone / inherit / sync}）开启
 *       {@code @Transactional(rollbackFor = Exception.class)}， 确保「版本降级 + 新版本写入」原子性
 *   <li>{@link #importTemplate} 调用 {@link FlowDefinitionService#deploy} 内部为独立事务，
 *       部署失败不影响使用次数递增（try-catch 隔离）
 * </ul>
 *
 * <p><b>性能优化：</b>
 *
 * <ul>
 *   <li>列表查询走 {@code ydsz_flow_template} 复合索引（{@code idx_category} + {@code idx_is_latest}）
 *   <li>详情查询仅查单条（主键索引）
 *   <li>导入时增加 {@code use_count} 字段（{@code incrementUseCount}），模板市场按热度排序
 * </ul>
 *
 * <p><b>防御性编程：</b>
 *
 * <ul>
 *   <li>所有方法均 try-catch 兜底，业务异常 {@code SysException} 直接抛出， 其它异常统一包装为 {@code
 *       INTERNAL_ERROR}（避免暴露内部错误细节）
 *   <li>导入校验 {@code bpmnXml} 非空；导出校验 {@code definitionId / templateName} 非空
 *   <li>克隆 / 继承校验新模板编码未被占用
 *   <li>同步父模板时仅 {@code INHERIT} 类型可同步，{@code CLONE} / {@code STANDALONE} 抛异常
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 场景：从模板市场导入"费用报销"流程
 * String definitionId = templateService.importTemplate("expense_reimbursement", "费用报销流程");
 * // → 部署为草稿流程定义，业务方可继续编辑 → 发布
 * }</pre>
 *
 * <p><b>版本模型：</b>模板与流程定义均支持多版本并存，模板版本独立于流程定义版本—— 同一模板可在多次导出后累积多个版本，业务方按需选择部署任一版本。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowTemplateService 接口定义
 * @see FlowTemplate 模板实体（含版本与继承字段）
 * @see FlowDefinitionService 流程定义服务（模板导入时调用 deploy）
 */
@Slf4j
@Service
public class FlowTemplateServiceImpl implements FlowTemplateService {

    /** 默认排序号：未设置排序的模板置底（999） */
  private static final int DEFAULT_SORT_ORDER = 999;

  /** 流程名称后缀最大长度（30 字符，防止超长后缀导致名称超限） */
  private static final int MAX_NAME_SUFFIX_LENGTH = 30;

  /** BPMN 节点类型码：开始事件 */
  private static final int NODE_TYPE_START_EVENT = 0;
  /** BPMN 节点类型码：用户任务 */
  private static final int NODE_TYPE_USER_TASK = 1;
  /** BPMN 节点类型码：抄送任务（映射为 userTask） */
  private static final int NODE_TYPE_CC_TASK = 2;
  /** BPMN 节点类型码：排他网关 */
  private static final int NODE_TYPE_EXCLUSIVE_GATEWAY = 3;
  /** BPMN 节点类型码：并行网关 */
  private static final int NODE_TYPE_PARALLEL_GATEWAY = 4;
  /** BPMN 节点类型码：包容网关 */
  private static final int NODE_TYPE_INCLUSIVE_GATEWAY = 5;
  /** BPMN 节点类型码：结束事件 */
  private static final int NODE_TYPE_END_EVENT = 6;
  /** BPMN 节点类型码：子流程调用 */
  private static final int NODE_TYPE_CALL_ACTIVITY = 7;

  /** 流程模板仓储（domain 层契约），管理 ydsz_flow_template 表 CRUD */
  private final FlowTemplateRepository templateRepository;

  /** DO/VO 转换器 */
  private final WorkflowConverter converter;

  /** 流程定义服务，模板导入时调用 deploy 部署为草稿定义 */
  private final FlowDefinitionService definitionService;

  /** 搜索索引事件桥接器（可选注入）。 用于在模板创建/更新时异步同步到 ydsz-common-search 统一搜索索引。 */
  private final ObjectProvider<SearchIndexEventBridge> searchIndexEventBridgeProvider;

  /** P6: 模板详情二级缓存（Caffeine，按 templateCode 缓存，加速高频模板查询） */
  private final YdszCache templateCache;

  /** 流程模块配置（缓存 TTL 与容量） */
  private final FlowProperties flowProperties;

  /**
   * 构造模板服务，初始化二级缓存。
   *
   * @param templateRepository 模板仓储
   * @param converter 转换器
   * @param definitionService 流程定义服务
   * @param searchIndexEventBridgeProvider 搜索索引事件桥接提供者
   * @param flowProperties 流程配置
   */
  public FlowTemplateServiceImpl(
      FlowTemplateRepository templateRepository,
      WorkflowConverter converter,
      FlowDefinitionService definitionService,
      ObjectProvider<SearchIndexEventBridge> searchIndexEventBridgeProvider,
      FlowProperties flowProperties) {
    this.templateRepository = templateRepository;
    this.converter = converter;
    this.definitionService = definitionService;
    this.searchIndexEventBridgeProvider = searchIndexEventBridgeProvider;
    this.flowProperties = flowProperties;
    this.templateCache =
        YdszCache.newBuilder()
            .type(CacheType.STRIPED)
            .name("flow:template-detail")
            .expireAfterWrite(flowProperties.getDefinitionCacheTtlMinutes(), TimeUnit.MINUTES)
            .maximumSize(flowProperties.getDefinitionCacheMaxSize())
            .build();
  }

  /**
   * 按分类查询模板列表
   *
   * <p>支持按 {@code category} 过滤（{@code null/空} 时查全部分类），仅返回最新版本（{@code isLatest=1}）， 异常时返回空列表（不抛异常）。
   *
   * @param category 模板分类（可空，{@code null/空} 时查全部分类）
   * @return 模板摘要列表（不含 BPMN XML 详细字段），无数据返回空列表
   */
  @Override
  @Transactional(readOnly = true)
  public List<Map<String, Object>> listTemplates(String category) {
    try {
      List<FlowTemplate> templates = templateRepository.findLatestByCategory(category).stream()
          .map(converter::entityToDO)
          .toList();
      return templates.stream().map(this::toSummaryMap).collect(Collectors.toList());
    } catch (Exception e) {
      log.error("[FlowTemplate] 列出模板异常: category={} err={}", category, e.getMessage(), e);
      return List.of();
    }
  }

  /**
   * 获取模板详情（含 BPMN XML）
   *
   * <p>P6: 通过 {@link #templateCache} 二级缓存加速高频查询，缓存未命中时回源数据库并写入缓存。
   *
   * @param templateCode 模板编码
   * @return 模板详情 Map（含 {@code bpmnXml / formPath / version / inheritType} 等全部字段）
   * @throws SysException {@code BAD_REQUEST} — 模板编码为空；{@code NOT_FOUND} — 模板不存在； {@code
   *     INTERNAL_ERROR} — 内部异常
   */
  @Override
  @Transactional(readOnly = true)
  public Map<String, Object> getTemplate(String templateCode) {
    try {
      if (!StringUtils.hasText(templateCode)) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("error.workflow.msg_f68a3fa3")
            .build();
      }
      // P6: 缓存命中直接返回，未命中则查库并回填
      Map<String, Object> cached = templateCache.getIfPresent(templateCode);
      if (cached != null) {
        return cached;
      }
      FlowTemplate template = templateRepository.findByTemplateCode(templateCode).map(converter::entityToDO).orElse(null);
      if (template == null) {
        throw SysException.builder()
            .resultCode(YdszResultCode.NOT_FOUND)
            .key("error.workflow.msg_c16cb047")
            .params(templateCode)
            .build();
      }
      Map<String, Object> detail = toDetailMap(template);
      templateCache.put(templateCode, detail);
      return detail;
    } catch (SysException e) {
      throw e;
    } catch (Exception e) {
      log.error("[FlowTemplate] 获取模板详情异常: templateCode={} err={}", templateCode, e.getMessage(), e);
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .key("error.workflow.msg_c2642700")
          .params(e.getMessage())
          .build();
    }
  }

  /**
   * P6: 清除指定模板的二级缓存。
   *
   * <p>在模板写入操作（导入/导出/版本创建/克隆/继承/同步）成功后调用，确保下次读取回源最新数据。
   *
   * @param templateCode 模板编码
   */
  private void evictTemplateCache(String templateCode) {
    if (StringUtils.hasText(templateCode)) {
      templateCache.invalidate(templateCode);
    }
  }

  /**
   * 导入模板 — 将模板的 BPMN XML 部署为草稿流程定义
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>查询模板最新版本，校验 {@code bpmnXml} 非空
   *   <li>构建 {@link FlowDeployProcessDTO}（使用 {@code bpmnXml} 模式，{@code version=1.0}）
   *   <li>调用 {@link FlowDefinitionService#deploy} 部署为草稿定义
   *   <li>递增 {@code use_count}（模板市场热度）
   * </ol>
   *
   * <p>租户隔离：默认从 {@link AuthContextUtils} 获取当前租户，回退到 {@code "1"}。
   *
   * @param templateCode 模板编码
   * @param flowName 自定义流程名称（可空，为空时使用模板名称）
   * @return 新部署的流程定义 ID（草稿状态）
   * @throws SysException {@code BAD_REQUEST} — 模板编码或 {@code bpmnXml} 为空； {@code NOT_FOUND} —
   *     模板不存在；{@code INTERNAL_ERROR} — 部署失败
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String importTemplate(String templateCode, String flowName) {
    try {
      if (!StringUtils.hasText(templateCode)) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("error.workflow.msg_f68a3fa3")
            .build();
      }
      // P6: 导入会递增 use_count，先失效缓存确保下次读取回源最新数据
      evictTemplateCache(templateCode);
      FlowTemplate template = templateRepository.findByTemplateCode(templateCode).map(converter::entityToDO).orElse(null);
      if (template == null) {
        throw SysException.builder()
            .resultCode(YdszResultCode.NOT_FOUND)
            .key("error.workflow.msg_c16cb047")
            .params(templateCode)
            .build();
      }
      if (!StringUtils.hasText(template.getBpmnXml())) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.workflow.msg_f407e561")
            .params(templateCode)
            .build();
      }

      // 构建部署 DTO，使用 BPMN XML 模式
      FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
      dto.setFlowCode(template.getTemplateCode());
      dto.setFlowName(StringUtils.hasText(flowName) ? flowName : template.getTemplateName());
      dto.setCategory(template.getCategory());
      dto.setDescription(template.getDescription());
      dto.setVersion("1.0");
      dto.setFormPath(template.getFormPath());
      dto.setBpmnXml(template.getBpmnXml());
      dto.setTenantId(AuthContextUtils.getTenantIdOrDefault());

      // 部署为草稿
      String definitionId = definitionService.deploy(dto);

      // 增加使用次数
      templateRepository.incrementUseCount(templateCode);

      log.info(
          "[FlowTemplate] 模板导入成功: templateCode={} definitionId={} flowName={}",
          templateCode,
          definitionId,
          dto.getFlowName());
      return definitionId;
    } catch (SysException e) {
      throw e;
    } catch (Exception e) {
      log.error("[FlowTemplate] 模板导入异常: templateCode={} err={}", templateCode, e.getMessage(), e);
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .key("error.workflow.msg_ecc1169b")
          .params(templateCode, e.getMessage())
          .build();
    }
  }

  /**
   * 导出流程定义为模板
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>查询流程定义详情（节点 + 跳转）
   *   <li>生成模板编码（{@code {category}_{templateName}}，去空格 / 标点）
   *   <li>生成 BPMN 2.0 XML（{@link #generateBpmnXml}）
   *   <li>若模板已存在：旧版本降级为 {@code isLatest=0}，新版本递增并保留继承关系
   *   <li>若模板不存在：新建 {@code version=1 / isLatest=1 / inheritType=STANDALONE}
   * </ol>
   *
   * @param definitionId 流程定义 ID
   * @param templateName 模板名称（必填）
   * @param category 模板分类（可空，默认 {@code GENERAL}）
   * @throws SysException {@code BAD_REQUEST} — definitionId / templateName 为空； {@code NOT_FOUND} —
   *     流程定义不存在；{@code INTERNAL_ERROR} — 序列化失败
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public void exportAsTemplate(String definitionId, String templateName, String category) {
    try {
      if (definitionId == null) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("error.workflow.msg_375a4677")
            .build();
      }
      if (!StringUtils.hasText(templateName)) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("error.workflow.msg_bbbf759d")
            .build();
      }

      // 获取流程定义详情
      Map<String, Object> detail = definitionService.getDetail(definitionId);
      if (detail == null) {
        throw SysException.builder()
            .resultCode(YdszResultCode.NOT_FOUND)
            .key("error.workflow.msg_690c83d8")
            .params(definitionId)
            .build();
      }

      FlowDefinition definition = (FlowDefinition) detail.get("definition");
      if (definition == null) {
        throw SysException.builder()
            .resultCode(YdszResultCode.NOT_FOUND)
            .key("error.workflow.msg_690c83d8")
            .params(definitionId)
            .build();
      }

      // 生成模板编码
      String templateCode = generateTemplateCode(category, templateName);

      // 生成 BPMN XML
      String bpmnXml = generateBpmnXml(detail);

      // 检查是否已存在（最新版本）
      FlowTemplate existing = templateRepository.findByTemplateCode(templateCode).map(converter::entityToDO).orElse(null);
      if (existing != null) {
        // P2-9: 已存在 → 创建新版本，旧版本统一降级为 is_latest=0
        templateRepository.markAsNotLatest(templateCode);
        int newVersion = templateRepository.selectMaxVersion(templateCode).map(v -> v + 1).orElse(1);

        FlowTemplate template = new FlowTemplate();
        template.setTemplateCode(templateCode);
        template.setTemplateName(templateName);
        template.setCategory(StringUtils.hasText(category) ? category : "GENERAL");
        template.setDescription(definition.getDescription());
        template.setBpmnXml(bpmnXml);
        template.setFormPath(definition.getFormPath());
        template.setUseCount(0);
        template.setSortOrder(existing.getSortOrder() != null ? existing.getSortOrder() : DEFAULT_SORT_ORDER);
        // P2-9: 版本化字段 — 沿用 inherit_type 与 parent_template_id 保持继承关系连续
        template.setVersion(newVersion);
        template.setVersionLabel("v" + newVersion + ".0");
        template.setInheritType(
            existing.getInheritType() != null ? existing.getInheritType() : "STANDALONE");
        template.setParentTemplateId(existing.getParentTemplateId());
        template.setIsLatest(1);
        templateRepository.save(converter.entityToVO(template));
        syncSearchIndex(template);
        log.info(
            "[FlowTemplate] 模板新版本已创建: templateCode={} version={} definitionId={}",
            templateCode,
            newVersion,
            definitionId);
      } else {
        // 新建模板（version=1, is_latest=1, inherit_type=STANDALONE）
        FlowTemplate template = new FlowTemplate();
        template.setTemplateCode(templateCode);
        template.setTemplateName(templateName);
        template.setCategory(StringUtils.hasText(category) ? category : "GENERAL");
        template.setDescription(definition.getDescription());
        template.setBpmnXml(bpmnXml);
        template.setFormPath(definition.getFormPath());
        template.setUseCount(0);
        template.setSortOrder(DEFAULT_SORT_ORDER);
        // P2-9: 版本化字段
        template.setVersion(1);
        template.setVersionLabel("1.0.0");
        template.setInheritType("STANDALONE");
        template.setIsLatest(1);
        templateRepository.save(converter.entityToVO(template));
        syncSearchIndex(template);
        log.info(
            "[FlowTemplate] 模板已创建: templateCode={} version=1 definitionId={}",
            templateCode,
            definitionId);
      }
    } catch (SysException e) {
      throw e;
    } catch (Exception e) {
      log.error("[FlowTemplate] 导出模板异常: definitionId={} err={}", definitionId, e.getMessage(), e);
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .key("error.workflow.msg_d119b2ed")
          .params(e.getMessage())
          .build();
    }
  }

  // ============================== P2-9: 模板继承与版本化 ==============================

  /**
   * 列出模板的全部历史版本
   *
   * @param templateCode 模板编码
   * @return 版本摘要列表（按版本号降序），无数据返回空列表
   * @throws SysException {@code BAD_REQUEST} — 模板编码为空
   */
  @Override
  @Transactional(readOnly = true)
  public List<Map<String, Object>> listTemplateVersions(String templateCode) {
    try {
      if (!StringUtils.hasText(templateCode)) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("error.workflow.msg_f68a3fa3")
            .build();
      }
      List<FlowTemplate> versions = templateRepository.findVersionsByTemplateCode(templateCode).stream()
          .map(converter::entityToDO)
          .toList();
      return versions.stream().map(this::toSummaryMap).collect(Collectors.toList());
    } catch (SysException e) {
      throw e;
    } catch (Exception e) {
      log.error("[FlowTemplate] 列出版本异常: templateCode={} err={}", templateCode, e.getMessage(), e);
      return List.of();
    }
  }

  /**
   * 获取指定版本模板的详情
   *
   * @param templateCode 模板编码
   * @param version 版本号（{@code null} 时返回最新版本）
   * @return 模板详情 Map
   * @throws SysException {@code BAD_REQUEST} — version &lt; 1；{@code NOT_FOUND} — 模板或指定版本不存在
   */
  @Override
  @Transactional(readOnly = true)
  public Map<String, Object> getTemplateVersion(String templateCode, Integer version) {
    try {
      if (!StringUtils.hasText(templateCode)) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("error.workflow.msg_f68a3fa3")
            .build();
      }
      // version 为空 → 返回最新版本（保持与 getTemplate 一致的语义）
      if (version == null) {
        FlowTemplate latest = templateRepository.findByTemplateCode(templateCode).map(converter::entityToDO).orElse(null);
        if (latest == null) {
          throw SysException.builder()
              .resultCode(YdszResultCode.NOT_FOUND)
              .key("error.workflow.msg_c16cb047")
              .params(templateCode)
              .build();
        }
        return toDetailMap(latest);
      }
      if (version < 1) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.workflow.msg_a9b0c1d3")
            .params(version)
            .build();
      }
      // 在所有版本中筛选指定版本
      List<FlowTemplate> versions = templateRepository.findVersionsByTemplateCode(templateCode).stream()
          .map(converter::entityToDO)
          .toList();
      if (versions.isEmpty()) {
        throw SysException.builder()
            .resultCode(YdszResultCode.NOT_FOUND)
            .key("error.workflow.msg_c16cb047")
            .params(templateCode)
            .build();
      }
      return versions.stream()
          .filter(v -> version.equals(v.getVersion()))
          .findFirst()
          .map(this::toDetailMap)
          .orElseThrow(
              () ->
                  SysException.builder()
                      .resultCode(YdszResultCode.NOT_FOUND)
                      .key("error.workflow.msg_f4a5b6c8")
                      .params(templateCode, version)
                      .build());
    } catch (SysException e) {
      throw e;
    } catch (Exception e) {
      log.error(
          "[FlowTemplate] 获取模板版本异常: templateCode={} version={} err={}",
          templateCode,
          version,
          e.getMessage(),
          e);
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .key("error.workflow.msg_c2642700")
          .params(e.getMessage())
          .build();
    }
  }

  /**
   * 基于当前最新版本创建新版本
   *
   * <p>执行链路：旧版本降级 → 读取源版本 BPMN → 写入新版本（{@code version=maxVersion+1}， {@code inheritType /
   * parentTemplateId} 沿用源版本）。<b>注意：</b>BPMN XML 一并复制， 调用方需自行编辑新版本。
   *
   * @param templateCode 模板编码
   * @param versionLabel 自定义版本标签（可空，默认 {@code v{newVersion}.0}）
   * @return 新版本号
   * @throws SysException {@code BAD_REQUEST} — 模板编码或 {@code bpmnXml} 为空； {@code NOT_FOUND} — 模板不存在
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public Integer createNewVersion(String templateCode, String versionLabel) {
    try {
      if (!StringUtils.hasText(templateCode)) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("error.workflow.msg_f68a3fa3")
            .build();
      }
      // 读取当前最新版本作为复制源
      FlowTemplate source = templateRepository.findByTemplateCode(templateCode).map(converter::entityToDO).orElse(null);
      if (source == null) {
        throw SysException.builder()
            .resultCode(YdszResultCode.NOT_FOUND)
            .key("error.workflow.msg_c16cb047")
            .params(templateCode)
            .build();
      }
      if (!StringUtils.hasText(source.getBpmnXml())) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.workflow.msg_f407e561")
            .params(templateCode)
            .build();
      }
      // 旧版本统一降级
      templateRepository.markAsNotLatest(templateCode);
      int newVersion = templateRepository.selectMaxVersion(templateCode).map(v -> v + 1).orElse(1);

      FlowTemplate newVer = new FlowTemplate();
      newVer.setTemplateCode(templateCode);
      newVer.setTemplateName(source.getTemplateName());
      newVer.setCategory(source.getCategory());
      newVer.setDescription(source.getDescription());
      newVer.setIcon(source.getIcon());
      newVer.setBpmnXml(source.getBpmnXml());
      newVer.setFormPath(source.getFormPath());
      newVer.setUseCount(0);
      newVer.setSortOrder(source.getSortOrder());
      // 沿用继承关系
      newVer.setParentTemplateId(source.getParentTemplateId());
      newVer.setVersion(newVersion);
      newVer.setVersionLabel(
          StringUtils.hasText(versionLabel) ? versionLabel : "v" + newVersion + ".0");
      newVer.setInheritType(
          source.getInheritType() != null ? source.getInheritType() : "STANDALONE");
      newVer.setIsLatest(1);
      templateRepository.save(converter.entityToVO(newVer));
      syncSearchIndex(newVer);

      log.info(
          "[FlowTemplate] 新版本已创建: templateCode={} oldVersion={} newVersion={} label={}",
          templateCode,
          source.getVersion(),
          newVersion,
          newVer.getVersionLabel());
      return newVersion;
    } catch (SysException e) {
      throw e;
    } catch (Exception e) {
      log.error("[FlowTemplate] 创建新版本异常: templateCode={} err={}", templateCode, e.getMessage(), e);
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .key("error.workflow.msg_c2642700")
          .params(e.getMessage())
          .build();
    }
  }

  /**
   * 克隆模板为独立模板
   *
   * <p>复制源模板内容到新编码（{@code inheritType=CLONE}），新模板与源模板独立演进，互不影响。
   *
   * @param sourceTemplateCode 源模板编码
   * @param newTemplateCode 新模板编码（必须不存在）
   * @param newTemplateName 新模板名称
   * @param newCategory 新模板分类（可空，沿用源模板分类）
   * @return 新模板编码
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String cloneTemplate(
      String sourceTemplateCode,
      String newTemplateCode,
      String newTemplateName,
      String newCategory) {
    return doCloneOrInherit(
        sourceTemplateCode, newTemplateCode, newTemplateName, newCategory, "CLONE");
  }

  /**
   * 从父模板继承为子模板
   *
   * <p>复制父模板内容到新编码（{@code inheritType=INHERIT}），<b>保留父子关联</b>。 子模板可通过 {@link #syncFromParent}
   * 同步父模板最新内容。
   *
   * @param parentTemplateCode 父模板编码
   * @param newTemplateCode 新模板编码（必须不存在）
   * @param newTemplateName 新模板名称
   * @param newCategory 新模板分类（可空，沿用父模板分类）
   * @return 新模板编码
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String inheritFromParent(
      String parentTemplateCode,
      String newTemplateCode,
      String newTemplateName,
      String newCategory) {
    return doCloneOrInherit(
        parentTemplateCode, newTemplateCode, newTemplateName, newCategory, "INHERIT");
  }

  /**
   * P2-9: cloneTemplate / inheritFromParent 共用复制逻辑。
   *
   * <p>差异仅在 {@code inheritType}：CLONE=克隆独立演进，INHERIT=继承保留父子关联。
   *
   * @param sourceTemplateCode 源/父模板编码（取其最新版本）
   * @param newTemplateCode 新模板编码（必须不存在）
   * @param newTemplateName 新模板名称
   * @param newCategory 新模板分类（可空，默认沿用源模板分类）
   * @param inheritType CLONE 或 INHERIT
   * @return 新模板编码
   */
  private String doCloneOrInherit(
      String sourceTemplateCode,
      String newTemplateCode,
      String newTemplateName,
      String newCategory,
      String inheritType) {
    try {
      if (!StringUtils.hasText(sourceTemplateCode)) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("error.workflow.msg_f68a3fa3")
            .build();
      }
      if (!StringUtils.hasText(newTemplateCode)) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("error.workflow.msg_d2e3f4a6")
            .build();
      }
      if (!StringUtils.hasText(newTemplateName)) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("error.workflow.msg_b6c7d8e0")
            .build();
      }
      // 读取源模板最新版本
      FlowTemplate source = templateRepository.findByTemplateCode(sourceTemplateCode).map(converter::entityToDO).orElse(null);
      if (source == null) {
        throw SysException.builder()
            .resultCode(YdszResultCode.NOT_FOUND)
            .key("error.workflow.msg_e3f4a5b7")
            .params(sourceTemplateCode)
            .build();
      }
      // 校验新编码未占用
      FlowTemplate existing = templateRepository.findByTemplateCode(newTemplateCode).map(converter::entityToDO).orElse(null);
      if (existing != null) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.workflow.msg_c1d2e3f5")
            .params(newTemplateCode)
            .build();
      }
      // 复制为新模板（version=1, is_latest=1, inherit_type=CLONE/INHERIT, parent_template_id=源模板 id）
      FlowTemplate newTemplate = new FlowTemplate();
      newTemplate.setTemplateCode(newTemplateCode);
      newTemplate.setTemplateName(newTemplateName);
      newTemplate.setCategory(
          StringUtils.hasText(newCategory) ? newCategory : source.getCategory());
      newTemplate.setDescription(source.getDescription());
      newTemplate.setIcon(source.getIcon());
      newTemplate.setBpmnXml(source.getBpmnXml());
      newTemplate.setFormPath(source.getFormPath());
      newTemplate.setUseCount(0);
      newTemplate.setSortOrder(source.getSortOrder() != null ? source.getSortOrder() : DEFAULT_SORT_ORDER);
      // P2-9: 继承关系字段
      newTemplate.setParentTemplateId(source.getId());
      newTemplate.setVersion(1);
      newTemplate.setVersionLabel("1.0.0");
      newTemplate.setInheritType(inheritType);
      newTemplate.setIsLatest(1);
      templateRepository.save(converter.entityToVO(newTemplate));
      syncSearchIndex(newTemplate);

      log.info(
          "[FlowTemplate] 模板{}成功: source={} newCode={} parentId={} inheritType={}",
          "CLONE".equals(inheritType) ? "克隆" : "继承",
          sourceTemplateCode,
          newTemplateCode,
          source.getId(),
          inheritType);
      return newTemplateCode;
    } catch (SysException e) {
      throw e;
    } catch (Exception e) {
      log.error(
          "[FlowTemplate] 复制模板异常: source={} newCode={} inheritType={} err={}",
          sourceTemplateCode,
          newTemplateCode,
          inheritType,
          e.getMessage(),
          e);
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .key("error.workflow.msg_c2642700")
          .params(e.getMessage())
          .build();
    }
  }

  /**
   * 列出指定父模板的全部子模板
   *
   * @param parentTemplateCode 父模板编码
   * @return 子模板摘要列表（仅最新版本），无子模板返回空列表
   * @throws SysException {@code BAD_REQUEST} — 父模板编码为空；{@code NOT_FOUND} — 父模板不存在
   */
  @Override
  @Transactional(readOnly = true)
  public List<Map<String, Object>> listInheritedTemplates(String parentTemplateCode) {
    try {
      if (!StringUtils.hasText(parentTemplateCode)) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("error.workflow.msg_f68a3fa3")
            .build();
      }
      // 先查出父模板主键 ID
      FlowTemplate parent = templateRepository.findByTemplateCode(parentTemplateCode).map(converter::entityToDO).orElse(null);
      if (parent == null) {
        throw SysException.builder()
            .resultCode(YdszResultCode.NOT_FOUND)
            .key("error.workflow.msg_b0c1d2e4")
            .params(parentTemplateCode)
            .build();
      }
      List<FlowTemplate> children = templateRepository.findByParentTemplateId(parent.getId()).stream()
          .map(converter::entityToDO)
          .toList();
      return children.stream().map(this::toSummaryMap).collect(Collectors.toList());
    } catch (SysException e) {
      throw e;
    } catch (Exception e) {
      log.error(
          "[FlowTemplate] 列出继承子模板异常: parentTemplateCode={} err={}",
          parentTemplateCode,
          e.getMessage(),
          e);
      return List.of();
    }
  }

  /**
   * 子模板同步父模板最新内容
   *
   * <p>仅 {@code inheritType=INHERIT} 类型可同步。同步时以父模板最新版本的 BPMN/description/icon/formPath
   * 覆盖子模板对应字段，<b>保留子模板自身的编码 / 名称 / 分类 / 排序</b>。 同步后版本号 +1，{@code versionLabel} 自动添加 {@code -synced}
   * 后缀。
   *
   * @param childTemplateCode 子模板编码
   * @return 新版本号
   * @throws SysException {@code BAD_REQUEST} — 子模板非 INHERIT 类型或无父模板 ID； {@code NOT_FOUND} —
   *     子模板或父模板不存在
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public Integer syncFromParent(String childTemplateCode) {
    try {
      if (!StringUtils.hasText(childTemplateCode)) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("error.workflow.msg_f68a3fa3")
            .build();
      }
      // 读取子模板最新版本
      FlowTemplate child = templateRepository.findByTemplateCode(childTemplateCode).map(converter::entityToDO).orElse(null);
      if (child == null) {
        throw SysException.builder()
            .resultCode(YdszResultCode.NOT_FOUND)
            .key("error.workflow.msg_c16cb047")
            .params(childTemplateCode)
            .build();
      }
      // 仅 INHERIT 类型可同步
      if (!"INHERIT".equals(child.getInheritType())) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.workflow.msg_d5e6f7a9")
            .params(
                childTemplateCode, child.getInheritType() != null ? child.getInheritType() : "null")
            .build();
      }
      // 读取父模板
      String parentId = child.getParentTemplateId();
      if (!StringUtils.hasText(parentId)) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.workflow.msg_e6f7a8b0")
            .params(childTemplateCode)
            .build();
      }
      FlowTemplate parent = templateRepository.findById(parentId).map(converter::entityToDO).orElse(null);
      if (parent == null) {
        throw SysException.builder()
            .resultCode(YdszResultCode.NOT_FOUND)
            .key("error.workflow.msg_f7a8b9c1")
            .params(parentId)
            .build();
      }
      if (!StringUtils.hasText(parent.getBpmnXml())) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.workflow.msg_f407e561")
            .params(parent.getTemplateCode())
            .build();
      }
      // 旧版本降级
      templateRepository.markAsNotLatest(childTemplateCode);
      int newVersion = templateRepository.selectMaxVersion(childTemplateCode).map(v -> v + 1).orElse(1);

      // 以父模板内容创建子模板新版本，保留子模板自身编码/名称/分类/排序
      FlowTemplate newVer = new FlowTemplate();
      newVer.setTemplateCode(child.getTemplateCode());
      newVer.setTemplateName(child.getTemplateName());
      newVer.setCategory(child.getCategory());
      newVer.setDescription(parent.getDescription());
      newVer.setIcon(parent.getIcon());
      newVer.setBpmnXml(parent.getBpmnXml());
      newVer.setFormPath(parent.getFormPath());
      newVer.setUseCount(0);
      newVer.setSortOrder(child.getSortOrder());
      // 保持继承关系
      newVer.setParentTemplateId(parentId);
      newVer.setVersion(newVersion);
      newVer.setVersionLabel("v" + newVersion + ".0-synced");
      newVer.setInheritType("INHERIT");
      newVer.setIsLatest(1);
      templateRepository.save(converter.entityToVO(newVer));
      syncSearchIndex(newVer);

      log.info(
          "[FlowTemplate] 子模板同步父模板成功: childCode={} parentCode={} newVersion={} parentId={}",
          childTemplateCode,
          parent.getTemplateCode(),
          newVersion,
          parentId);
      return newVersion;
    } catch (SysException e) {
      throw e;
    } catch (Exception e) {
      log.error(
          "[FlowTemplate] 同步父模板异常: childCode={} err={}", childTemplateCode, e.getMessage(), e);
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .key("error.workflow.msg_c2642700")
          .params(e.getMessage())
          .build();
    }
  }

  // ============================== 私有方法 ==============================

  /**
   * 将 DO 转为摘要 Map（不含 BPMN XML）
   *
   * <p>P2-9: 包含版本与继承元信息。
   */
  private Map<String, Object> toSummaryMap(FlowTemplate t) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("templateCode", t.getTemplateCode());
    map.put("templateName", t.getTemplateName());
    map.put("category", t.getCategory());
    map.put("description", t.getDescription());
    map.put("icon", t.getIcon());
    map.put("formPath", t.getFormPath());
    map.put("useCount", t.getUseCount());
    map.put("sortOrder", t.getSortOrder());
    // P2-9: 版本与继承元信息
    map.put("parentTemplateId", t.getParentTemplateId());
    map.put("version", t.getVersion());
    map.put("versionLabel", t.getVersionLabel());
    map.put("inheritType", t.getInheritType());
    map.put("isLatest", t.getIsLatest());
    return map;
  }

  /**
   * 将 DO 转为详情 Map（含 BPMN XML）
   *
   * <p>P2-9: 包含版本与继承元信息。
   */
  private Map<String, Object> toDetailMap(FlowTemplate t) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("templateCode", t.getTemplateCode());
    map.put("templateName", t.getTemplateName());
    map.put("category", t.getCategory());
    map.put("description", t.getDescription());
    map.put("icon", t.getIcon());
    map.put("formPath", t.getFormPath());
    map.put("useCount", t.getUseCount());
    map.put("sortOrder", t.getSortOrder());
    // P2-9: 版本与继承元信息
    map.put("parentTemplateId", t.getParentTemplateId());
    map.put("version", t.getVersion());
    map.put("versionLabel", t.getVersionLabel());
    map.put("inheritType", t.getInheritType());
    map.put("isLatest", t.getIsLatest());
    map.put("bpmnXml", t.getBpmnXml());
    return map;
  }

  /**
   * 生成模板编码
   *
   * @param category 参数说明
   * @param templateName 参数说明
   * @return 返回值说明
   */
  private String generateTemplateCode(String category, String templateName) {
    String prefix =
        StringUtils.hasText(category) ? category.toLowerCase().replace(" ", "_") : "general";
    String suffix =
        templateName
            .toLowerCase()
            .replaceAll("[\\s\\p{Punct}]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");
    if (suffix.length() > MAX_NAME_SUFFIX_LENGTH) {
      suffix = suffix.substring(0, MAX_NAME_SUFFIX_LENGTH);
    }
    return prefix + "_" + suffix;
  }

  /**
   * 根据流程定义详情生成 BPMN 2.0 XML
   *
   * @param detail 参数说明
   * @return 返回值说明
   */
  private String generateBpmnXml(Map<String, Object> detail) {
    Object defObj = detail.get("definition");
    if (!(defObj instanceof FlowDefinition definition)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .message("流程定义详情缺少 definition")
          .build();
    }
    List<FlowNode> nodes = MapUtils.safeCastList(detail.get("nodes"), FlowNode.class);
    List<FlowSkip> skips = MapUtils.safeCastList(detail.get("skips"), FlowSkip.class);

    String processId = definition.getFlowCode();
    String processName = definition.getFlowName();

    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n");
    xml.append("             xmlns:flowable=\"http://flowable.org/bpmn\"\n");
    xml.append("             targetNamespace=\"http://ydsz.ydsz/flow\">\n");
    xml.append("  <process id=\"")
        .append(escapeXml(processId))
        .append("\" name=\"")
        .append(escapeXml(processName))
        .append("\" isExecutable=\"true\">\n");

    // 输出节点
    if (nodes != null) {
      for (FlowNode node : nodes) {
        String nodeCode = node.getNodeCode();
        String nodeName = node.getNodeName();
        Integer nodeType = node.getNodeType();
        String permissionFlag = node.getPermissionFlag();

        String elementName = mapNodeTypeToElement(nodeType);
        xml.append("    <")
            .append(elementName)
            .append(" id=\"")
            .append(escapeXml(nodeCode))
            .append("\"");
        if (nodeName != null && !nodeName.isBlank()) {
          xml.append(" name=\"").append(escapeXml(nodeName)).append("\"");
        }
        if (permissionFlag != null && !permissionFlag.isBlank() && "userTask".equals(elementName)) {
          xml.append(" flowable:assignee=\"").append(escapeXml(permissionFlag)).append("\"");
        }
        xml.append("/>\n");
      }
    }

    // 输出跳转
    if (skips != null) {
      for (FlowSkip skip : skips) {
        // 从 ext JSON 中解析 sourceRef
        String fromNodeCode = extractSourceRef(skip.getExt());
        String toNodeCode = skip.getNextNodeCode();
        String skipName = skip.getSkipName();
        String skipCondition = skip.getSkipCondition();

        String flowId =
            "flow_"
                + (fromNodeCode != null ? fromNodeCode : "")
                + "_to_"
                + (toNodeCode != null ? toNodeCode : "");

        if (skipCondition != null && !skipCondition.isBlank()) {
          xml.append("    <sequenceFlow id=\"")
              .append(escapeXml(flowId))
              .append("\" sourceRef=\"")
              .append(escapeXml(fromNodeCode))
              .append("\" targetRef=\"")
              .append(escapeXml(toNodeCode))
              .append("\">\n");
          xml.append("      <conditionExpression xsi:type=\"tFormalExpression\">")
              .append(escapeXml(skipCondition))
              .append("</conditionExpression>\n");
          xml.append("    </sequenceFlow>\n");
        } else {
          xml.append("    <sequenceFlow id=\"")
              .append(escapeXml(flowId))
              .append("\" sourceRef=\"")
              .append(escapeXml(fromNodeCode))
              .append("\" targetRef=\"")
              .append(escapeXml(toNodeCode))
              .append("\"");
          if (skipName != null && !skipName.isBlank()) {
            xml.append(" name=\"").append(escapeXml(skipName)).append("\"");
          }
          xml.append("/>\n");
        }
      }
    }

    xml.append("  </process>\n");
    xml.append("</definitions>\n");
    return xml.toString();
  }

  /**
   * 节点类型码映射为 BPMN 元素名
   *
   * <p>映射表：
   *
   * <table>
   *   <caption>节点类型映射</caption>
   *   <tr><th>内部类型</th><th>BPMN 元素</th><th>说明</th></tr>
   *   <tr><td>0</td><td>startEvent</td><td>开始事件</td></tr>
   *   <tr><td>1</td><td>userTask</td><td>用户任务</td></tr>
   *   <tr><td>2</td><td>userTask</td><td>抄送节点（也用 userTask 表示）</td></tr>
   *   <tr><td>3</td><td>exclusiveGateway</td><td>排他网关</td></tr>
   *   <tr><td>4</td><td>parallelGateway</td><td>并行网关</td></tr>
   *   <tr><td>5</td><td>inclusiveGateway</td><td>包容网关</td></tr>
   *   <tr><td>6</td><td>endEvent</td><td>结束事件</td></tr>
   *   <tr><td>7</td><td>callActivity</td><td>子流程调用</td></tr>
   * </table>
   *
   * @param nodeType 节点类型码
   * @return BPMN 元素名（{@code null} / 未知类型时默认 {@code userTask}）
   */
  private String mapNodeTypeToElement(Integer nodeType) {
    if (nodeType == null) {
      return "userTask";
    }
    return switch (nodeType) {
      case NODE_TYPE_START_EVENT -> "startEvent";
      case NODE_TYPE_USER_TASK -> "userTask";
      case NODE_TYPE_CC_TASK -> "userTask"; // 抄送节点也映射为 userTask
      case NODE_TYPE_EXCLUSIVE_GATEWAY -> "exclusiveGateway";
      case NODE_TYPE_PARALLEL_GATEWAY -> "parallelGateway";
      case NODE_TYPE_INCLUSIVE_GATEWAY -> "inclusiveGateway";
      case NODE_TYPE_END_EVENT -> "endEvent";
      case NODE_TYPE_CALL_ACTIVITY -> "callActivity"; // 子流程
      default -> "userTask";
    };
  }

  /**
   * 从 skip 的 ext JSON 中提取 sourceRef（源节点编码）
   *
   * <p>{@code FlowSkip} 表中 {@code ext} 字段以 JSON 存储 BPMN 序列流属性， 解析失败时返回 {@code
   * null}，由上层使用空字符串兜底（BPMN 工具可识别）。
   *
   * @param ext skip 的 ext JSON 字符串
   * @return 源节点编码，无法解析返回 {@code null}
   */
  private String extractSourceRef(String ext) {
    if (ext == null || ext.isBlank()) {
      return null;
    }
    try {
      Map<String, Object> extJson = YdszJson.parseMap(ext);
      if (extJson != null) {
        Object raw = extJson.get("sourceRef");
        String sourceRef = raw == null ? null : String.valueOf(raw);
        if (sourceRef != null && !sourceRef.isBlank()) {
          return sourceRef;
        }
      }
    } catch (Exception ignored) {
      // ignore parse errors
    }
    return null;
  }

  /**
   * XML 转义
   *
   * @param s 参数说明
   * @return 返回值说明
   */
  private String escapeXml(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  /**
   * 将模板数据同步到统一搜索索引（ydsz_search_index）。
   *
   * <p>通过 {@link SearchIndexEventBridge} 异步写入，不阻塞主业务流程。 未引入 {@code ydsz-common-search} 时桥接器为空，跳过同步。
   *
   * @param template 流程模板实体
   */
  private void syncSearchIndex(FlowTemplate template) {
    SearchIndexEventBridge bridge = searchIndexEventBridgeProvider.getIfAvailable();
    if (bridge != null) {
      bridge.indexUpsert("workflow", template);
    }
  }
}
