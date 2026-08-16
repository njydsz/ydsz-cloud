package com.njydsz.workflow.server.service.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.ObjectNode;
import com.njydsz.common.util.collection.MapUtils;
import com.njydsz.workflow.domain.dto.FlowDeployProcessDTO;
import com.njydsz.workflow.domain.dto.InstanceMigrationDTO;
import com.njydsz.workflow.domain.dto.InstanceMigrationResultDTO;
import com.njydsz.workflow.domain.entity.FlowDefinition;
import com.njydsz.workflow.domain.entity.FlowNode;
import com.njydsz.workflow.domain.entity.FlowSkip;
import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.domain.enums.FlowSkipType;
import com.njydsz.workflow.infra.mapper.FlowDefinitionMapper;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.workflow.infra.mapper.FlowNodeMapper;
import com.njydsz.workflow.infra.mapper.FlowSkipMapper;
import com.njydsz.workflow.server.config.FlowProperties;
import com.njydsz.workflow.server.engine.BpmnModel;
import com.njydsz.workflow.server.engine.BpmnXmlParser;
import com.njydsz.workflow.server.engine.FlowDefinitionCacheService;
import com.njydsz.workflow.server.engine.FlowGraphValidator;
import com.njydsz.workflow.server.service.FlowDefinitionService;
import com.njydsz.workflow.server.service.FlowInstanceMigrationService;

/**
 * 流程定义 Service 实现
 *
 * <p>对 {@link FlowDefinitionService} 接口的完整实现，承担工作流引擎<b>「定义侧」</b>的全部职责：
 * 部署、发布、停用、查询、设计器协同编辑、版本对比、灰度发布、一键回滚等。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>双模式部署</b>：支持 BPMN 2.0 标准 XML（{@code bpmnXml}）与轻量 JSON（{@code nodes+skips}）两种模型，
 *       通过 {@link BpmnXmlParser} 解析后统一转写为 {@link FlowNode} / {@link FlowSkip} 实体</li>
 *   <li><b>拓扑校验</b>：部署前调用 {@link FlowGraphValidator} 校验<b>连通性</b>、<b>死节点</b>、<b>环路口</b>等结构规则，
 *       校验失败立即阻断写入</li>
 *   <li><b>版本管理</b>：同 {@code flowCode+version+tenantId} 唯一约束；多版本并存；灰度发布；
 *       一键回滚时联动 {@link FlowInstanceMigrationService} 迁移在途实例</li>
 *   <li><b>缓存治理</b>：发布/停用时通过 {@link CacheEvict} + {@link FlowDefinitionCacheService} 双层失效
 *       本地与 Redis 集群缓存，{@link Cacheable} 注解缓存已发布 / 最新版本元数据</li>
 *   <li><b>设计器集成</b>：提供节点坐标同步、协同编辑锁、表单字段权限、节点 SLA 配置等设计器侧能力</li>
 *   <li><b>变更分析</b>：{@link #analyzeMigrationImpact} 评估在途实例兼容性，{@link #diffVersions} 输出版本节点差异</li>
 *   <li><b>导入导出</b>：BPMN 2.0 zip 包批量部署（{@link #batchDeployFromZip}），单定义 JSON 导入导出</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写操作（{@code deploy / publish / deprecate / switchActiveVersion / rollbackDefinition}）开启
 *       {@code @Transactional(rollbackFor = Exception.class)}，确保「定义 + 节点 + 跳转」三方写入原子性</li>
 *   <li>{@code @CacheEvict} 标注在方法级别，事务提交后再清除缓存，避免脏读</li>
 *   <li>批量部署（{@link #batchDeployFromZip}）通过 {@code self} 代理引用调用 {@link #deploy}，触发 Spring 事务代理</li>
 * </ul>
 *
 * <p><b>并发控制：</b>
 * <ul>
 *   <li>协同编辑锁（{@link #lockDefinition}）：{@code Redisson} 分布式锁 {@code ydsz:flow:def:lock:{defId}}，
 *       默认 30 分钟自动释放，防设计器多 Tab 冲突</li>
 *   <li>乐观锁：{@link FlowDefinition} 继承 {@code revision} 字段，发布/停用版本切换自动重试</li>
 * </ul>
 *
 * <p><b>性能优化：</b>
 * <ul>
 *   <li>{@link #loadPublished} / {@link #loadLatestVersion} 命中 {@code ydsz_flow_definition} 主键索引</li>
 *   <li>批量加载节点（{@link #listNodesByDefinition}）走 {@code idx_definition} 索引</li>
 *   <li>缓存策略：{@code flow:def:published:{code}} TTL 10min（高频读取）；
 *       {@code flow:def:latest:{code}} TTL 5min（更新频率较高）</li>
 * </ul>
 *
 * <p><b>P1-2 God Class 拆分规划：</b>本类约 2215 行，承担「定义侧」全部职责，
 * 建议按以下子服务拆分（保留原接口，新服务通过组合模式接入）：
 * <ul>
 *   <li><b>FlowDefinitionDeployService</b> — 双模式部署（BPMN XML / JSON）+ 拓扑校验 + 三方写入</li>
 *   <li><b>FlowDefinitionVersionService</b> — 版本管理（发布 / 停用 / 灰度 / 回滚 / 版本对比）</li>
 *   <li><b>FlowDefinitionDesignService</b> — 设计器集成（坐标同步 / 协同编辑锁 / 表单字段权限 / SLA 配置）</li>
 *   <li><b>FlowDefinitionQueryService</b> — 查询能力（已发布 / 最新版本 / 节点列表 / 分页列表）</li>
 *   <li><b>FlowDefinitionImportExportService</b> — BPMN 2.0 zip 批量部署 + 单定义 JSON 导入导出</li>
 *   <li><b>FlowDefinitionAnalysisService</b> — 变更影响分析（analyzeMigrationImpact / diffVersions）</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有查询与写入均按 {@code tenantId} 隔离，DTO 显式传入优先，回退 {@code SecurityContext}，
 * 最后兜底 {@code "1"}（默认租户）。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowDefinitionService 接口定义
 * @see FlowDefinition 流程定义实体
 * @see FlowNode 流程节点实体
 * @see FlowSkip 流程跳转实体
 * @see BpmnXmlParser BPMN 2.0 解析器
 * @see FlowGraphValidator 流程图结构校验器
 * @see FlowInstanceMigrationService 实例迁移服务（回滚时联动）
 */
@Slf4j
@Service
public class FlowDefinitionServiceImpl implements FlowDefinitionService {

    /** 流程定义 Mapper，负责 ydsz_flow_definition 表的增删改查 */
    private final FlowDefinitionMapper definitionMapper;
    /** 流程节点 Mapper，负责 ydsz_flow_node 表的增删改查 */
    private final FlowNodeMapper nodeMapper;
    /** 流程跳转 Mapper，负责 ydsz_flow_skip 表的增删改查 */
    private final FlowSkipMapper skipMapper;
    /** BPMN 2.0 XML 解析器，将标准 BPMN XML 转换为内部节点/跳转模型 */
    private final BpmnXmlParser bpmnXmlParser;
    /** 流程图结构校验器，校验连通性/死节点/环路等拓扑规则 */
    private final FlowGraphValidator graphValidator;
    /** P1: 流程定义元数据缓存，部署/更新时主动失效 */
    private final FlowDefinitionCacheService flowDefinitionCacheService;
    /**
     * GAP-P1-6: 自注入代理引用，使 {@link #batchDeployFromZip} 内部调用 {@link #deploy}
     * 时能正确触发 Spring 事务代理（避免 self-invocation 导致事务失效）。
     * 使用 {@code @Lazy} 打破启动期循环依赖。
     */
    private final FlowDefinitionServiceImpl self;
    /** P2-5: 流程实例 Mapper，用于变更影响分析 */
    private final FlowInstanceMapper instanceMapper;
    /** P0-2: 流程实例迁移服务（一键回滚时迁移在途实例） */
    @Lazy
    private final FlowInstanceMigrationService migrationService;
    /** 统一配置属性 */
    private final FlowProperties flowProperties;

    public FlowDefinitionServiceImpl(
            FlowDefinitionMapper definitionMapper,
            FlowNodeMapper nodeMapper,
            FlowSkipMapper skipMapper,
            BpmnXmlParser bpmnXmlParser,
            FlowGraphValidator graphValidator,
            FlowDefinitionCacheService flowDefinitionCacheService,
            FlowInstanceMapper instanceMapper,
            @Lazy FlowInstanceMigrationService migrationService,
            FlowProperties flowProperties,
            @Lazy FlowDefinitionServiceImpl self) {
        this.definitionMapper = definitionMapper;
        this.nodeMapper = nodeMapper;
        this.skipMapper = skipMapper;
        this.bpmnXmlParser = bpmnXmlParser;
        this.graphValidator = graphValidator;
        this.flowDefinitionCacheService = flowDefinitionCacheService;
        this.instanceMapper = instanceMapper;
        this.migrationService = migrationService;
        this.flowProperties = flowProperties;
        this.self = self;
    }

    /**
     * 部署流程定义（双模式：BPMN XML / 轻量 JSON）
     *
     * <p>完整执行链路：
     * <ol>
     *   <li><b>参数校验</b>：必填 {@code flowCode / flowName}，至少二选一传 {@code bpmnXml / nodes}</li>
     *   <li><b>租户解析</b>：{@code dto.tenantId} → {@code SecurityContext} → 默认 {@code "1"}</li>
     *   <li><b>重名校验</b>：同 {@code flowCode+version+tenantId} 已存在时抛 {@code DUPLICATE_KEY}</li>
     *   <li><b>模型解析</b>：
     *     <ul>
     *       <li>XML 模式：{@link BpmnXmlParser#parse} 解析为节点/跳转模型，自动注入 BPMNDI 坐标</li>
     *       <li>JSON 模式：直接构造节点/跳转，要求必须含开始节点（{@code nodeType=0}）</li>
     *     </ul>
     *   </li>
     *   <li><b>结构校验</b>：{@link FlowGraphValidator#validate} 校验连通性、死节点、环路口</li>
     *   <li><b>三方写入</b>：{@code ydsz_flow_definition + ydsz_flow_node + ydsz_flow_skip} 事务原子性</li>
     *   <li><b>缓存清理</b>：{@code @CacheEvict} + {@link FlowDefinitionCacheService#evict} 主动清除</li>
     * </ol>
     *
     * <p>新部署的 {@link FlowDefinition} 状态为 {@code isPublish=0 / activityStatus=1}，
     * 需调用 {@link #publish} 后才能被流程实例引用。
     *
     * @param dto 部署 DTO（含 {@code flowCode/flowName/version/bpmnXml/nodes/skips/tenantId}）
     * @return 新流程定义的 ID
     * @throws SysException {@code BAD_REQUEST} — 参数缺失或结构校验失败；{@code DUPLICATE_KEY} — 版本冲突
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CacheConstants.FLOW_DEF_PUBLISHED_CACHE,
            CacheConstants.FLOW_DEF_LATEST_CACHE}, allEntries = true)
    public String deploy(FlowDeployProcessDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getFlowCode())
                || !StringUtils.hasText(dto.getFlowName())) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("flowCode/flowName 不能为空")
                .build();
        }

        String version = StringUtils.hasText(dto.getVersion()) ? dto.getVersion() : "1.0";
        // P2-16: 多租户上下文 - DTO 显式传入优先，否则从 SecurityContext 获取，最后兜底 1L
        String tenantId = dto.getTenantId() != null
                ? dto.getTenantId()
                : AuthContextUtils.getTenantIdOrDefault();

        // 1. 检查重名：同 flowCode + version + tenant 只能有一条
        FlowDefinition existing = definitionMapper.selectPublished(
                dto.getFlowCode(), version, tenantId);
        if (existing != null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("流程定义已存在: code=" + dto.getFlowCode() + " version=" + version)
                .build();
        }

        // 2. 解析 BPMN / JSON 模型
        boolean hasBpmn = StringUtils.hasText(dto.getBpmnXml());
        boolean hasJson = dto.getNodes() != null && !dto.getNodes().isEmpty();
        if (!hasBpmn && !hasJson) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("bpmnXml / nodes 至少二选一")
                .build();
        }

        List<FlowNode> nodes = new ArrayList<>();
        List<FlowSkip> skips = new ArrayList<>();

        if (hasBpmn) {
            // 模式 A：标准 BPMN 2.0 XML
            BpmnModel bpmnModel = bpmnXmlParser.parse(dto.getBpmnXml());
            // 校验：flowCode 必须与 BPMN process id 一致（或缺失时不强制）
            if (StringUtils.hasText(bpmnModel.getProcessId())
                    && !bpmnModel.getProcessId().equals(dto.getFlowCode())) {
                throw SysException.builder()
                    .resultCode(BaseResultCode.BAD_REQUEST)
                    .message("BPMN process id 与 flowCode 不一致: bpmn=" + bpmnModel.getProcessId()
                                + " dto=" + dto.getFlowCode())
                    .build();
            }
            // 若 dto.flowName 为空，用 BPMN process name
            if (!StringUtils.hasText(dto.getFlowName()) || dto.getFlowName().equals(dto.getFlowCode())) {
                dto.setFlowName(bpmnModel.getProcessName());
            }
            nodes.addAll(bpmnModel.getNodes());
            skips.addAll(bpmnModel.getSkips());
            // P3-1: 自动注入 BPMNDI 坐标到节点 coordinate 字段（覆盖现有值）
            Map<String, BpmnModel.NodeCoordinate> nodeCoords = bpmnModel.getNodeCoordinates();
            if (nodeCoords != null && !nodeCoords.isEmpty()) {
                for (FlowNode n : nodes) {
                    BpmnModel.NodeCoordinate coord = nodeCoords.get(n.getNodeCode());
                    if (coord != null) {
                        n.setCoordinate(YdszJson.toJson(Map.of(
                                "x", coord.getX(),
                                "y", coord.getY(),
                                "width", coord.getWidth(),
                                "height", coord.getHeight()
                        )));
                    }
                }
                log.info("[Flow] 从 BPMNDI 注入节点坐标: defId-pending count={}", nodeCoords.size());
            }
        } else {
            // 模式 B：轻量 JSON
            for (FlowDeployProcessDTO.FlowNodeDTO n : dto.getNodes()) {
                FlowNode node = new FlowNode();
                node.setNodeCode(n.getNodeCode());
                node.setNodeName(n.getNodeName() == null ? n.getNodeCode() : n.getNodeName());
                node.setNodeType(n.getNodeType() == null
                        ? FlowNodeType.APPROVAL.getCode() : n.getNodeType());
                node.setPermissionFlag(n.getPermissionFlag());
                node.setSkipAnyNode(n.getSkipAnyNode());
                nodes.add(node);
            }
            // 必须含开始节点
            boolean hasStart = nodes.stream()
                    .anyMatch(n -> FlowNodeType.START.getCode() == n.getNodeType());
            if (!hasStart) {
                throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("流程定义必须包含开始节点（nodeType=0）")
                .build();
            }
            // 节点编码唯一
            long uniqueCount = nodes.stream()
                    .map(FlowNode::getNodeCode)
                    .distinct()
                    .count();
            if (uniqueCount != nodes.size()) {
                throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("节点编码 nodeCode 必须唯一")
                .build();
            }
            if (dto.getSkips() != null) {
                for (FlowDeployProcessDTO.FlowSkipDTO s : dto.getSkips()) {
                    FlowSkip skip = new FlowSkip();
                    skip.setSkipName(s.getSkipName());
                    skip.setSkipType(StringUtils.hasText(s.getSkipType())
                            ? s.getSkipType() : FlowSkipType.PASS.name());
                    skip.setSkipCondition(s.getSkipCondition());
                    skip.setNextNodeCode(s.getToNodeCode());
                    skip.setExt(YdszJson.toJson(Map.of("sourceRef", s.getFromNodeCode())));
                    skips.add(skip);
                }
            }
        }

        // P2-1: 流程图结构校验（连通性/死节点/环路）
        graphValidator.validate(nodes, skips);

        // 3. 写入定义
        FlowDefinition def = new FlowDefinition();
        def.setFlowCode(dto.getFlowCode());
        def.setFlowName(dto.getFlowName());
        def.setCategory(dto.getCategory());
        def.setFlowVersion(version);
        def.setModelValue("CLASSICS");
        def.setFormCustom("N");
        def.setFormPath(dto.getFormPath());
        def.setActivityStatus(1);
        def.setIsPublish(0);
        def.setDescription(dto.getDescription());
        def.setTenantId(tenantId);
        def.setProviderTraceId(dto.getProviderTraceId());
        definitionMapper.insert(def);
        String definitionId = def.getId();

        // 4. 写入节点
        for (FlowNode node : nodes) {
            node.setDefinitionId(definitionId);
            node.setFlowCode(dto.getFlowCode());
            node.setTenantId(tenantId);
            node.setProviderTraceId(dto.getProviderTraceId());
            nodeMapper.insert(node);
        }

        // 5. 写入跳转
        for (FlowSkip skip : skips) {
            skip.setDefinitionId(definitionId);
            skip.setFlowCode(dto.getFlowCode());
            skip.setTenantId(tenantId);
            skip.setProviderTraceId(dto.getProviderTraceId());
            skipMapper.insert(skip);
        }

        log.info("[Flow] 部署流程成功: code={} version={} defId={} mode={} nodes={} skips={}",
                dto.getFlowCode(), version, definitionId,
                hasBpmn ? "BPMN" : "JSON",
                nodes.size(), skips.size());
        // P1: 部署新版本后主动清除该定义的缓存（防御性，避免遗留脏数据）
        flowDefinitionCacheService.evict(definitionId);
        return definitionId;
    }

    /**
     * 发布流程定义（不带强制标志）
     *
     * <p>默认调用 {@link #publish(String, boolean)} 并传 {@code force=false}，
     * 当变更影响分析为 HIGH 风险时会被 {@link #checkPublishCompatibility} 阻断。
     *
     * @param definitionId 流程定义 ID
     * @see #publish(String, boolean)
     */
    @Override
    @CacheEvict(value = {CacheConstants.FLOW_DEF_PUBLISHED_CACHE,
            CacheConstants.FLOW_DEF_LATEST_CACHE}, allEntries = true)
    public void publish(String definitionId) {
        publish(definitionId, false);
    }

    /**
     * 发布流程定义（带强制标志）
     *
     * <p>将 {@code ydsz_flow_definition.isPublish=0 → 1}，并清理本地 + Redis 集群缓存。
     * 发布前通过 {@link #checkPublishCompatibility} 评估与同 {@code flowCode} 激活版本的差异，
     * 当存在「在途实例卡在已删除节点」的 HIGH 风险时，仅当 {@code force=true} 才放行。
     *
     * <p>注意：本方法<b>不开启事务</b>（单条 UPDATE），但 {@code @CacheEvict} 标注确保
     * 缓存清理晚于 DB 写入生效，避免发布后读到旧缓存。
     *
     * @param definitionId 流程定义 ID
     * @param force        是否强制发布（跳过 HIGH 风险阻断）
     * @throws SysException {@code NOT_FOUND} — 流程定义不存在；{@code BAD_REQUEST} — HIGH 风险未强制发布
     */
    @Override
    @CacheEvict(value = {CacheConstants.FLOW_DEF_PUBLISHED_CACHE,
            CacheConstants.FLOW_DEF_LATEST_CACHE}, allEntries = true)
    public void publish(String definitionId, boolean force) {
        FlowDefinition def = definitionMapper.selectById(definitionId);
        if (def == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("流程定义不存在: " + definitionId)
                .build();
        }
        // P1-4: 版本兼容性校验 — 检测在途实例是否会因节点删除而卡死
        checkPublishCompatibility(def, force);
        definitionMapper.publish(definitionId, 1);
        // P0-3: 失效本地 + 集群缓存
        flowDefinitionCacheService.evict(definitionId);
        log.info("[Flow] 发布流程: defId={} flowCode={} version={} force={}",
                definitionId, def.getFlowCode(), def.getFlowVersion(), force);
    }

    /**
     * P1-4: 发布前版本兼容性校验。
     *
     * <p>检测当前同 flowCode 的激活版本是否有在途实例，并比对新旧版本节点编码差异：
     * <ul>
     *   <li>无激活版本（首次发布）或无在途实例 → 直接放行（NONE 风险）</li>
     *   <li>有在途实例但节点未删除 → 记录警告日志后放行（LOW/MEDIUM 风险）</li>
     *   <li>有在途实例卡在已删除节点（HIGH 风险）：
     *     <ul>
     *       <li>{@code force=false} 且 {@link #blockOnHighRisk}=true → 抛 SysException 阻断</li>
     *       <li>{@code force=true} 或 {@link #blockOnHighRisk}=false → 记录警告日志后放行</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @param def  待发布的流程定义
     * @param force 是否强制发布（跳过 HIGH 风险阻断）
     */
    private void checkPublishCompatibility(FlowDefinition def, boolean force) {
        String flowCode = def.getFlowCode();
        String tenantId = def.getTenantId() != null ? def.getTenantId() : "1";

        // 1. 查询同 flowCode 的当前激活版本（已发布且非当前定义）
        FlowDefinition activeDef = definitionMapper.selectPublished(flowCode, null, tenantId);
        if (activeDef == null || activeDef.getId().equals(def.getId())) {
            // 无激活版本或当前定义已是激活版本 → 首次发布或重复发布，无需校验
            log.debug("[Flow][P1-4] 无前序激活版本，跳过兼容性校验: flowCode={} defId={}",
                    flowCode, def.getId());
            return;
        }

        // 2. 调用变更影响分析评估风险
        Map<String, Object> impact;
        try {
            impact = analyzeMigrationImpact(activeDef.getId(), def.getId());
        } catch (Exception e) {
            // 影响分析失败时不阻断发布（避免分析工具故障导致业务无法发布），仅记录警告
            log.warn("[Flow][P1-4] 变更影响分析失败，跳过兼容性校验: oldDef={} newDef={} err={}",
                    activeDef.getId(), def.getId(), e.getMessage());
            return;
        }

        String riskLevel = (String) impact.get("riskLevel");
        long runningTotal = extractLong(impact, "runningInstances", "total");
        List<String> recommendations = extractStringList(impact, "recommendations");

        // 3. 根据风险等级处理
        if ("HIGH".equals(riskLevel)) {
            if (force) {
                log.warn("[Flow][P1-4] 强制发布 HIGH 风险流程: flowCode={} newDef={} oldDef={} "
                                + "runningInstances={} recommendations={}",
                        flowCode, def.getId(), activeDef.getId(), runningTotal, recommendations);
            } else if (flowProperties.isPublishBlockOnHighRisk()) {
                log.warn("[Flow][P1-4] 阻断 HIGH 风险发布: flowCode={} newDef={} oldDef={} "
                                + "runningInstances={} recommendations={}",
                        flowCode, def.getId(), activeDef.getId(), runningTotal, recommendations);
                throw SysException.builder()
                    .resultCode(BaseResultCode.BAD_REQUEST)
                    .message("发布阻断：存在 " + runningTotal + " 个在途实例将因节点删除而卡死。"
                                + "请先处理在途实例（强制流转/通知撤回/等待完成），"
                                + "或使用 force=true 参数强制发布（需管理员权限）。"
                                + "建议：" + String.join("；", recommendations))
                    .build();
            } else {
                log.warn("[Flow][P1-4] block-on-high-risk=false，放行 HIGH 风险发布: flowCode={} "
                                + "newDef={} oldDef={} runningInstances={} recommendations={}",
                        flowCode, def.getId(), activeDef.getId(), runningTotal, recommendations);
            }
        } else if ("MEDIUM".equals(riskLevel) || "LOW".equals(riskLevel)) {
            log.warn("[Flow][P1-4] 发布 {} 风险流程: flowCode={} newDef={} oldDef={} "
                            + "runningInstances={} recommendations={}",
                    riskLevel, flowCode, def.getId(), activeDef.getId(),
                    runningTotal, recommendations);
        } else {
            // NONE 风险 — 无在途实例，直接放行
            log.info("[Flow][P1-4] 发布无风险: flowCode={} newDef={} oldDef={} runningInstances=0",
                    flowCode, def.getId(), activeDef.getId());
        }
    }

    /**
     * P1-4: 从影响分析结果中提取 long 值（兼容嵌套 Map 结构）。
     *
     * @param root     根 Map
     * @param keys     嵌套 key 路径（如 "runningInstances", "total"）
     * @return 提取的 long 值，无法提取返回 0
     */
    private long extractLong(Map<String, Object> root, String... keys) {
        Object current = root;
        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(key);
            } else {
                return 0L;
            }
        }
        if (current instanceof Number) {
            return ((Number) current).longValue();
        }
        return 0L;
    }

    /**
     * P1-4: 从影响分析结果中提取 String 列表。
     *
     * @param root 根 Map
     * @param key  列表对应的 key
     * @return String 列表，无法提取返回空列表
     */
    private List<String> extractStringList(Map<String, Object> root, String key) {
        Object value = root.get(key);
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    /**
     * 停用流程定义
     *
     * <p>将 {@code isPublish} 置为 {@code 9}（已废弃），并清理本地 + 集群缓存。
     * 停用后流程定义将无法被新实例引用，但已有实例不受影响。
     *
     * <p>与 {@link #disable}（{@code activityStatus=0}）的区别：
     * <ul>
     *   <li>{@code deprecate}：版本维度停用，{@code isPublish=9}，流程列表不可见</li>
     *   <li>{@code disable}：流程维度停用，{@code activityStatus=0}，仍可被查询</li>
     * </ul>
     *
     * @param definitionId 流程定义 ID
     */
    @Override
    @CacheEvict(value = {CacheConstants.FLOW_DEF_PUBLISHED_CACHE,
            CacheConstants.FLOW_DEF_LATEST_CACHE}, allEntries = true)
    public void deprecate(String definitionId) {
        definitionMapper.publish(definitionId, 9);
        // P0-3: 失效本地 + 集群缓存
        flowDefinitionCacheService.evict(definitionId);
        log.info("[Flow] 停用流程: defId={}", definitionId);
    }

    /**
     * 查询已发布的流程定义（带缓存）
     *
     * <p>按 {@code flowCode + version + tenantId} 三元组唯一定位，缓存至
     * {@code ydsz:flow:def:published:{code}:{version}:{tenantId}}，TTL 10min。
     * 当 {@code version} 为空时回退为 {@code "1.0"}。
     *
     * @param flowCode 流程编码
     * @param version  版本号（为空时取 {@code "1.0"}）
     * @param tenantId 租户 ID（为空时取 {@code SecurityContext}，默认 {@code "1"}）
     * @return 流程定义；不存在返回 {@code null}（不进缓存，由 {@code unless="#result == null"} 保证）
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConstants.FLOW_DEF_PUBLISHED_CACHE,
            key = "#flowCode + ':' + #version + ':' + #tenantId", unless = "#result == null")
    public FlowDefinition getPublished(String flowCode, String version, String tenantId) {
        if (!StringUtils.hasText(version)) {
            version = "1.0";
        }
        // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
        String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
        return definitionMapper.selectPublished(flowCode, version, tid);
    }

    /**
     * 查询指定流程编码的最新版本定义（带缓存）
     *
     * <p>按 {@code flowCode + tenantId} 唯一定位，缓存至
     * {@code ydsz:flow:def:latest:{code}:{tenantId}}，TTL 5min（更新频率较高）。
     * 用于设计器列表、流程发起页等需要「最新版」语义的地方。
     *
     * @param flowCode 流程编码
     * @param tenantId 租户 ID（为空时取 {@code SecurityContext}，默认 {@code "1"}）
     * @return 最新版本定义；不存在返回 {@code null}
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConstants.FLOW_DEF_LATEST_CACHE,
            key = "#flowCode + ':' + #tenantId", unless = "#result == null")
    public FlowDefinition getLatestByCode(String flowCode, String tenantId) {
        // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
        String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
        return definitionMapper.selectLatestByCode(flowCode, tid);
    }

    /**
     * 分页查询流程定义列表
     *
     * <p>仅返回 {@code activityStatus=1}（启用）且未逻辑删除的记录，
     * 按 {@code created_at} 倒序排列。支持按 {@code category}（精确）和 {@code flowCode}（模糊）过滤。
     *
     * <p>分页使用 MyBatis-Plus {@link Page}，启用 {@code @Transactional(readOnly = true)} 提升只读性能。
     *
     * @param pageNo   页码（从 1 开始）
     * @param pageSize 每页大小
     * @param category 分类编码过滤（可选）
     * @param flowCode 流程编码模糊过滤（可选）
     * @return 流程定义列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<FlowDefinition> page(int pageNo, int pageSize, String category, String flowCode) {
        Page<FlowDefinition> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<FlowDefinition> w = new LambdaQueryWrapper<>();
        w.eq(StringUtils.hasText(category), FlowDefinition::getCategory, category)
                .like(StringUtils.hasText(flowCode), FlowDefinition::getFlowCode, flowCode)
                .eq(FlowDefinition::getActivityStatus, 1)
                .eq(FlowDefinition::getDeleted, 0)
                .orderByDesc(FlowDefinition::getCreatedAt);
        return definitionMapper.selectPage(page, w).getRecords();
    }

    /**
     * 查询流程定义详情（含节点 + 跳转）
     *
     * <p>组装 {@code definition + nodes + skips} 三元组供设计器回显用。
     * 不存在时返回 {@code null}。
     *
     * @param definitionId 流程定义 ID
     * @return 详情 Map（{@code definition/nodes/skips}）；不存在返回 {@code null}
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getDetail(String definitionId) {
        // P2-21: 组装 definition + nodes + skips
        FlowDefinition definition = definitionMapper.selectById(definitionId);
        if (definition == null) {
            return null;
        }
        List<FlowNode> nodes = nodeMapper.selectByDefinitionId(definitionId);
        List<FlowSkip> skips = skipMapper.selectByDefinitionId(definitionId);
        Map<String, Object> result = new HashMap<>();
        result.put("definition", definition);
        result.put("nodes", nodes);
        result.put("skips", skips);
        return result;
    }

    // ============================== P2-27: 版本切换 ==============================

    /**
     * 切换激活版本（P2-27）
     *
     * <p>将同 {@code flowCode} 的其他已发布版本置为 {@code isPublish=0}，目标版本置为 {@code isPublish=1}。
     * 典型场景：灰度发布后全量切换、A/B 测试版本择优。
     *
     * <p>校验链：
     * <ul>
     *   <li>{@code flowCode} 必填</li>
     *   <li>目标定义存在</li>
     *   <li>目标定义的 {@code flowCode} 与入参一致</li>
     * </ul>
     *
     * <p>事务保证「失效旧版本 + 激活新版本」原子性，事务提交后通过
     * {@code @CacheEvict} + {@link FlowDefinitionCacheService#evict} 清理缓存。
     *
     * @param flowCode     流程编码
     * @param definitionId 目标定义 ID
     * @param tenantId     租户 ID
     * @throws SysException {@code BAD_REQUEST} / {@code NOT_FOUND} — 参数缺失或定义不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CacheConstants.FLOW_DEF_PUBLISHED_CACHE,
            CacheConstants.FLOW_DEF_LATEST_CACHE}, allEntries = true)
    public void switchActiveVersion(String flowCode, String definitionId, String tenantId) {
        if (!StringUtils.hasText(flowCode)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("flowCode 不能为空")
                .build();
        }
        FlowDefinition def = definitionMapper.selectById(definitionId);
        if (def == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("流程定义不存在: " + definitionId)
                .build();
        }
        if (!flowCode.equals(def.getFlowCode())) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("flowCode 不匹配: 期望=" + flowCode + " 实际=" + def.getFlowCode())
                .build();
        }
        // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
        String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
        // 失效同 flowCode 的其他已发布版本
        definitionMapper.deactivateByFlowCode(flowCode, definitionId, tid);
        // 激活目标版本
        definitionMapper.publish(definitionId, 1);
        // P0-3: 失效本地 + 集群缓存（目标版本 + 同 flowCode 旧版本）
        flowDefinitionCacheService.evict(definitionId);
        log.info("[Flow] 切换流程定义版本: flowCode={} → defId={} tenantId={}",
                flowCode, definitionId, tid);
    }

    // ============================== P2-28: 启用/停用 ==============================

    /**
     * 启用流程定义
     *
     * <p>将 {@code activityStatus=0 → 1}，恢复流程定义在设计器与发起页可见。
     * 启用后清理本地 + 集群缓存，避免旧缓存屏蔽新状态。
     *
     * @param definitionId 流程定义 ID
     */
    @Override
    public void enable(String definitionId) {
        definitionMapper.updateActivityStatus(definitionId, 1);
        // P0-3: 失效本地 + 集群缓存
        flowDefinitionCacheService.evict(definitionId);
        log.info("[Flow] 启用流程定义: defId={}", definitionId);
    }

    /**
     * 停用流程定义（activityStatus 维度）
     *
     * <p>将 {@code activityStatus=1 → 0}，流程定义仍在数据库但设计器与发起页不可见。
     * 与 {@link #deprecate}（{@code isPublish=9}，版本维度）配合使用可实现双重停用。
     *
     * @param definitionId 流程定义 ID
     */
    @Override
    public void disable(String definitionId) {
        definitionMapper.updateActivityStatus(definitionId, 0);
        // P0-3: 失效本地 + 集群缓存
        flowDefinitionCacheService.evict(definitionId);
        log.info("[Flow] 停用流程定义: defId={}", definitionId);
    }

    // ============================== P2-40: 节点坐标更新 ==============================

    /**
     * 更新节点坐标（P2-40）
     *
     * <p>设计器拖拽节点后调用，{@code coordinate} 字段为 JSON 字符串（{@code {x,y,width,height}}）。
     * 节点存在性校验：先通过 {@code (definitionId, nodeCode)} 复合索引查询，
     * 节点不存在抛 {@code NOT_FOUND}。
     *
     * @param definitionId 流程定义 ID
     * @param nodeCode     节点编码
     * @param coordinate   坐标 JSON 字符串
     * @throws SysException {@code BAD_REQUEST} / {@code NOT_FOUND}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNodeCoordinate(String definitionId, String nodeCode, String coordinate) {
        if (definitionId == null || !StringUtils.hasText(nodeCode)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("definitionId/nodeCode 不能为空")
                .build();
        }
        FlowNode node = nodeMapper.selectByCode(definitionId, nodeCode);
        if (node == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("节点不存在: definitionId=" + definitionId + " nodeCode=" + nodeCode)
                .build();
        }
        node.setCoordinate(coordinate);
        nodeMapper.updateById(node);
        // 节点数据变更，清除该定义的本地节点/跳转缓存避免脏读
        flowDefinitionCacheService.evict(definitionId);
        log.info("[Flow] 更新节点坐标: defId={} node={} coordinate={}",
                definitionId, nodeCode, coordinate);
    }

    // ============================== P2-41: 流程定义草稿编辑 ==============================

    /**
     * 更新流程定义（草稿编辑，P2-41）
     *
     * <p>仅允许编辑<b>未发布</b>（{@code isPublish=0}）的定义，已发布定义需走「创建新版本」流程。
     * 支持两种编辑模式：
     * <ul>
     *   <li><b>元数据更新</b>：{@code flowName / category / description / formPath}</li>
     *   <li><b>节点/跳转全量替换</b>：传 {@code bpmnXml} 或 {@code nodes/skips} 时，
     *       先删除旧节点/跳转，再插入新模型（事务保证原子性）</li>
     * </ul>
     *
     * <p>不可变字段：{@code flowCode / flowVersion}（流程核心标识）。
     *
     * @param definitionId 流程定义 ID（必须未发布）
     * @param dto          更新参数 DTO
     * @throws SysException {@code BAD_REQUEST} — 定义已发布；{@code NOT_FOUND} — 定义不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CacheConstants.FLOW_DEF_PUBLISHED_CACHE,
            CacheConstants.FLOW_DEF_LATEST_CACHE}, allEntries = true)
    public void updateDefinition(String definitionId, FlowDeployProcessDTO dto) {
        if (definitionId == null || dto == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("definitionId/dto 不能为空")
                .build();
        }
        // 1. 校验定义存在且未发布（只有未发布定义才能编辑）
        FlowDefinition def = definitionMapper.selectById(definitionId);
        if (def == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("流程定义不存在: " + definitionId)
                .build();
        }
        if (def.getIsPublish() != null && def.getIsPublish() == 1) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("已发布的流程定义不可编辑，请创建新版本: " + definitionId)
                .build();
        }

        // 2. 更新定义元数据（不修改 version 和 flowCode — 核心标识不可变）
        if (StringUtils.hasText(dto.getFlowName())) {
            def.setFlowName(dto.getFlowName());
        }
        if (StringUtils.hasText(dto.getCategory())) {
            def.setCategory(dto.getCategory());
        }
        if (dto.getDescription() != null) {
            def.setDescription(dto.getDescription());
        }
        if (StringUtils.hasText(dto.getFormPath())) {
            def.setFormPath(dto.getFormPath());
        }
        // ext 字段透传（FlowDeployProcessDTO 暂无 ext 字段，跳过）
        definitionMapper.updateById(def);

        // 3. 如果 dto 中包含 nodes/skips，先删除旧节点/跳转，再插入新的
        boolean hasNodes = dto.getNodes() != null && !dto.getNodes().isEmpty();
        boolean hasSkips = dto.getSkips() != null && !dto.getSkips().isEmpty();
        boolean hasBpmn = StringUtils.hasText(dto.getBpmnXml());
        if (hasBpmn || hasNodes || hasSkips) {
            skipMapper.deleteByDefinitionId(definitionId);
            nodeMapper.deleteByDefinitionId(definitionId);

            List<FlowNode> nodes = new ArrayList<>();
            List<FlowSkip> skips = new ArrayList<>();

            if (hasBpmn) {
                // BPMN 模式：解析 XML
                BpmnModel bpmnModel = bpmnXmlParser.parse(dto.getBpmnXml());
                nodes.addAll(bpmnModel.getNodes());
                skips.addAll(bpmnModel.getSkips());
            } else {
                // JSON 模式：从 DTO 构造节点
                for (FlowDeployProcessDTO.FlowNodeDTO n : dto.getNodes()) {
                    FlowNode node = new FlowNode();
                    node.setNodeCode(n.getNodeCode());
                    node.setNodeName(n.getNodeName() == null ? n.getNodeCode() : n.getNodeName());
                    node.setNodeType(n.getNodeType() == null
                            ? FlowNodeType.APPROVAL.getCode() : n.getNodeType());
                    node.setPermissionFlag(n.getPermissionFlag());
                    node.setSkipAnyNode(n.getSkipAnyNode());
                    nodes.add(node);
                }
                if (dto.getSkips() != null) {
                    for (FlowDeployProcessDTO.FlowSkipDTO s : dto.getSkips()) {
                        FlowSkip skip = new FlowSkip();
                        skip.setSkipName(s.getSkipName());
                        skip.setSkipType(StringUtils.hasText(s.getSkipType())
                                ? s.getSkipType() : FlowSkipType.PASS.name());
                        skip.setSkipCondition(s.getSkipCondition());
                        skip.setNextNodeCode(s.getToNodeCode());
                        skip.setExt(YdszJson.toJson(Map.of("sourceRef", s.getFromNodeCode())));
                        skips.add(skip);
                    }
                }
            }

            // 写入节点
            for (FlowNode node : nodes) {
                node.setDefinitionId(definitionId);
                node.setFlowCode(def.getFlowCode());
                node.setTenantId(def.getTenantId());
                node.setProviderTraceId(dto.getProviderTraceId());
                nodeMapper.insert(node);
            }
            // 写入跳转
            for (FlowSkip skip : skips) {
                skip.setDefinitionId(definitionId);
                skip.setFlowCode(def.getFlowCode());
                skip.setTenantId(def.getTenantId());
                skip.setProviderTraceId(dto.getProviderTraceId());
                skipMapper.insert(skip);
            }
        }

        // P1: 节点/跳转可能被重写，清除缓存避免脏读
        flowDefinitionCacheService.evict(definitionId);
        log.info("[Flow] 编辑流程定义草稿: defId={} flowCode={}", definitionId, def.getFlowCode());
    }

    // ============================== GAP-V2-06: 导入/导出 ==============================

    /**
     * 导出流程定义（GAP-V2-06）
     *
     * <p>将定义 + 节点 + 跳转序列化为 JSON 字符串。输出格式与 {@link #importDefinition} 输入完全对应。
     *
     * @param definitionId 流程定义 ID
     * @return 流程定义 JSON 字符串（含 {@code definition/nodes/skips} 三元组）
     * @throws SysException {@code NOT_FOUND} — 流程定义不存在
     */
    @Override
    @Transactional(readOnly = true)
    public String exportDefinition(String definitionId) {
        Map<String, Object> detail = getDetail(definitionId);
        if (detail == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("流程定义不存在: " + definitionId)
                .build();
        }
        return YdszJson.toJson(detail);
    }

    /**
     * 导入流程定义（GAP-V2-06）
     *
     * <p>解析 {@link #exportDefinition} 产出的 JSON 字符串，构造 {@link FlowDeployProcessDTO} 后
     * 调用 {@link #deploy} 创建为草稿（{@code isPublish=0}）。需手动调用 {@link #publish} 才能上线。
     *
     * <p>解析链：
     * <ol>
     *   <li>校验 JSON 合法性与 {@code definition/nodes/skips} 三元组</li>
     *   <li>从 {@code skip.ext} JSON 中还原 {@code fromNodeCode}（BPMN 序列流语义）</li>
     *   <li>委托 {@link #deploy} 走完整部署流程（含结构校验、租户注入、缓存清理）</li>
     * </ol>
     *
     * @param json     流程定义 JSON 字符串
     * @param tenantId 目标租户 ID
     * @return 新创建的草稿定义 ID
     * @throws SysException {@code BAD_REQUEST} — JSON 缺失/解析失败/必要字段为空
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importDefinition(String json, String tenantId) {
        if (!StringUtils.hasText(json)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("导入 JSON 不能为空")
                .build();
        }
        Map<String, Object> root;
        try {
            root = YdszJson.parseMap(json);
        } catch (Exception e) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("JSON 解析失败: " + e.getMessage())
                .build();
        }
        if (root == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("JSON 内容为空")
                .build();
        }

        // 1. 提取 definition 元数据
        Map<String, Object> defJson = MapUtils.safeCastMap(MapUtils.getMap(root, "definition"));
        if (defJson == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("JSON 缺少 definition 字段")
                .build();
        }
        String flowCode = MapUtils.getString(defJson, "flowCode");
        String flowName = MapUtils.getString(defJson, "flowName");
        if (!StringUtils.hasText(flowCode) || !StringUtils.hasText(flowName)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("definition 中 flowCode/flowName 不能为空")
                .build();
        }

        // 2. 构建 FlowDeployProcessDTO
        FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
        dto.setFlowCode(flowCode);
        dto.setFlowName(flowName);
        dto.setVersion(MapUtils.getString(defJson, "version"));
        dto.setCategory(MapUtils.getString(defJson, "category"));
        dto.setDescription(MapUtils.getString(defJson, "description"));
        dto.setFormPath(MapUtils.getString(defJson, "formPath"));
        dto.setTenantId(tenantId);
        dto.setProviderTraceId(MapUtils.getString(defJson, "providerTraceId"));

        // 3. 提取 nodes
        List<?> nodesJson = MapUtils.getList(root, "nodes");
        if (nodesJson != null && !nodesJson.isEmpty()) {
            List<FlowDeployProcessDTO.FlowNodeDTO> nodes = new ArrayList<>();
            for (int i = 0; i < nodesJson.size(); i++) {
                Map<String, Object> n = MapUtils.getMapFromList(nodesJson, i);
                FlowDeployProcessDTO.FlowNodeDTO node = new FlowDeployProcessDTO.FlowNodeDTO();
                node.setNodeCode(MapUtils.getString(n, "nodeCode"));
                node.setNodeName(MapUtils.getString(n, "nodeName"));
                node.setNodeType(MapUtils.getInteger(n, "nodeType"));
                node.setPermissionFlag(MapUtils.getString(n, "permissionFlag"));
                node.setSkipAnyNode(MapUtils.getString(n, "skipAnyNode"));
                nodes.add(node);
            }
            dto.setNodes(nodes);
        }

        // 4. 提取 skips（从 ext.sourceRef 还原 fromNodeCode）
        List<?> skipsJson = MapUtils.getList(root, "skips");
        if (skipsJson != null && !skipsJson.isEmpty()) {
            List<FlowDeployProcessDTO.FlowSkipDTO> skips = new ArrayList<>();
            for (int i = 0; i < skipsJson.size(); i++) {
                Map<String, Object> s = MapUtils.getMapFromList(skipsJson, i);
                FlowDeployProcessDTO.FlowSkipDTO skip = new FlowDeployProcessDTO.FlowSkipDTO();
                skip.setSkipName(MapUtils.getString(s, "skipName"));
                skip.setSkipType(MapUtils.getString(s, "skipType"));
                skip.setSkipCondition(MapUtils.getString(s, "skipCondition"));
                skip.setToNodeCode(MapUtils.getString(s, "nextNodeCode"));
                // 从 ext 字段还原 fromNodeCode
                String ext = MapUtils.getString(s, "ext");
                if (StringUtils.hasText(ext)) {
                    try {
                        ObjectNode extNode = YdszJson.parseObject(ext);
                        skip.setFromNodeCode(extNode != null ? extNode.getString("sourceRef") : null);
                    } catch (Exception e) {
                        log.warn("[Flow] 导入跳转 ext 解析失败: skipName={} err={}",
                                MapUtils.getString(s, "skipName"), e.getMessage());
                    }
                }
                skips.add(skip);
            }
            dto.setSkips(skips);
        }

        // 5. 调用 deploy 创建为草稿（isPublish=0）
        String newDefinitionId = deploy(dto);
        log.info("[Flow] 导入流程定义成功: flowCode={} version={} newDefId={}",
                dto.getFlowCode(), dto.getVersion(), newDefinitionId);
        return newDefinitionId;
    }

    // ============================== GAP-V2-01: 设计器数据 API ==============================

    /**
     * 获取设计器数据（GAP-V2-01）
     *
     * <p>在 {@link #getDetail} 基础上额外组装 {@code edges} 字段（{@code source/target/label/condition/skipType}），
     * 供前端 VueFlow / LogicFlow 直接消费。{@code source} 字段从 {@code skip.ext.sourceRef} JSON 中还原。
     *
     * @param definitionId 流程定义 ID
     * @return 设计器数据 Map（含 {@code definition/nodes/skips/edges}）；不存在返回 {@code null}
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getDesignerData(String definitionId) {
        Map<String, Object> detail = getDetail(definitionId);
        if (detail == null) {
            return null;
        }
        // 在 getDetail 基础上增加 edges 格式（供前端 VueFlow/LogicFlow 直接使用）
        Map<String, Object> result = new LinkedHashMap<>(detail);
        List<FlowSkip> skips = MapUtils.safeCastList(detail.get("skips"), FlowSkip.class);
        if (skips != null) {
            List<Map<String, Object>> edges = new ArrayList<>();
            for (FlowSkip skip : skips) {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("id", skip.getId());
                // sourceRef 存储在 ext JSON 中
                String source = null;
                if (StringUtils.hasText(skip.getExt())) {
                    try {
                        ObjectNode extNode = YdszJson.parseObject(skip.getExt());
                        source = extNode != null ? extNode.getString("sourceRef") : null;
                    } catch (Exception e) { log.warn("解析skip节点ext JSON失败: {}", e.getMessage(), e); }
                }
                edge.put("source", source);
                edge.put("target", skip.getNextNodeCode());
                edge.put("label", skip.getSkipName());
                edge.put("condition", skip.getSkipCondition());
                edge.put("skipType", skip.getSkipType());
                edges.add(edge);
            }
            result.put("edges", edges);
        }
        return result;
    }

    /**
     * 保存设计器数据（GAP-V2-01）
     *
     * <p>设计器拖拽 / 修改节点后保存。仅允许编辑<b>未发布</b>定义（{@code isPublish=0}），
     * 已发布定义需走「创建新版本」流程。
     *
     * <p>当前支持：
     * <ul>
     *   <li>批量更新节点坐标（{@code coordinate}）</li>
     *   <li>批量更新节点名称 / 权限标识 / 扩展字段</li>
     * </ul>
     *
     * <p>边（{@code skips}）的增删不在本方法处理范围，需通过 {@link #updateDefinition} 端点。
     *
     * @param definitionId 流程定义 ID
     * @param designerData 设计器数据 Map（含 {@code nodes} 数组）
     * @throws SysException {@code NOT_FOUND} — 流程定义不存在；{@code BAD_REQUEST} — 已发布
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDesignerData(String definitionId, Map<String, Object> designerData) {
        FlowDefinition def = definitionMapper.selectById(definitionId);
        if (def == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("流程定义不存在: " + definitionId)
                .build();
        }
        if (def.getIsPublish() != null && def.getIsPublish() == 1) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("已发布的流程定义不可编辑，请先创建新版本")
                .build();
        }

        // 1. 批量更新节点坐标 + 属性
        List<Map<String, Object>> nodes = MapUtils.getListOfMaps(designerData, "nodes");
        if (nodes != null) {
            for (Map<String, Object> nodeData : nodes) {
                String nodeCode = (String) nodeData.get("nodeCode");
                if (nodeCode == null) {
                    continue;
                }
                // 更新坐标
                Object coord = nodeData.get("coordinate");
                if (coord != null) {
                    String coordStr = coord instanceof String
                            ? (String) coord : YdszJson.toJson(coord);
                    FlowNode nodeForCoord = nodeMapper.selectByCode(definitionId, nodeCode);
                    if (nodeForCoord != null) {
                        nodeForCoord.setCoordinate(coordStr);
                        nodeMapper.updateById(nodeForCoord);
                    }
                }
                // 更新节点名称（如前端修改了）
                Object nodeName = nodeData.get("nodeName");
                if (nodeName != null) {
                    FlowNode node = nodeMapper.selectByCode(definitionId, nodeCode);
                    if (node != null) {
                        node.setNodeName((String) nodeName);
                        Object permFlag = nodeData.get("permissionFlag");
                        if (permFlag != null) {
                            node.setPermissionFlag((String) permFlag);
                        }
                        Object ext = nodeData.get("ext");
                        if (ext != null) {
                            node.setExt(ext instanceof String ? (String) ext : YdszJson.toJson(ext));
                        }
                        nodeMapper.updateById(node);
                    }
                }
            }
        }

        // 2. 批量更新边（skips）— 目前仅支持坐标和属性更新，不支持增删边
        // 边的增删需要通过 updateDefinition 端点处理
        // 节点数据批量变更，清除该定义的本地节点/跳转缓存避免脏读
        flowDefinitionCacheService.evict(definitionId);
        log.info("[Flow] 设计器数据已保存: definitionId={} nodes={}",
                definitionId, nodes != null ? nodes.size() : 0);
    }

    // ============================== GAP-V2-02: 表单字段配置 ==============================

    /**
     * 获取节点表单字段配置（GAP-V2-02）
     *
     * <p>读取 {@code ydsz_flow_node.form_fields_config} 字段，返回 JSON 字符串（含字段权限/必填/可见性配置）。
     *
     * @param definitionId 流程定义 ID
     * @param nodeCode     节点编码
     * @return 表单字段配置 JSON 字符串；节点未配置时返回 {@code null}
     * @throws SysException {@code NOT_FOUND} — 节点不存在
     */
    @Override
    @Transactional(readOnly = true)
    public String getFormConfig(String definitionId, String nodeCode) {
        FlowNode node = nodeMapper.selectByCode(definitionId, nodeCode);
        if (node == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("节点不存在: definitionId=" + definitionId + " nodeCode=" + nodeCode)
                .build();
        }
        return node.getFormFieldsConfig();
    }

    /**
     * 保存节点表单字段配置（GAP-V2-02）
     *
     * <p>更新 {@code ydsz_flow_node.form_fields_config} 字段并清理本地 + 集群缓存。
     * 节点不存在时抛 {@code NOT_FOUND}。
     *
     * @param definitionId     流程定义 ID
     * @param nodeCode         节点编码
     * @param formFieldsConfig 表单字段配置 JSON 字符串
     * @throws SysException {@code NOT_FOUND} — 节点不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveFormConfig(String definitionId, String nodeCode, String formFieldsConfig) {
        FlowNode node = nodeMapper.selectByCode(definitionId, nodeCode);
        if (node == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("节点不存在: definitionId=" + definitionId + " nodeCode=" + nodeCode)
                .build();
        }
        node.setFormFieldsConfig(formFieldsConfig);
        nodeMapper.updateById(node);
        // 节点数据变更，清除该定义的本地节点/跳转缓存避免脏读
        flowDefinitionCacheService.evict(definitionId);
        log.info("[Flow] 表单字段配置已保存: definitionId={} nodeCode={}",
                definitionId, nodeCode);
    }

    // ============================== P1-2: SLA 节点级配置 ==============================

    /**
     * 获取节点 SLA 配置
     *
     * <p>读取 {@code ydsz_flow_node.sla_config} 字段，返回 JSON 字符串（含超时时长、提醒策略、超时处理动作）。
     *
     * @param definitionId 流程定义 ID
     * @param nodeCode     节点编码
     * @return SLA 配置 JSON 字符串；未配置返回 {@code null}
     * @throws SysException {@code NOT_FOUND} — 节点不存在
     */
    @Override
    @Transactional(readOnly = true)
    public String getSlaConfig(String definitionId, String nodeCode) {
        FlowNode node = nodeMapper.selectByCode(definitionId, nodeCode);
        if (node == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("节点不存在: definitionId=" + definitionId + " nodeCode=" + nodeCode)
                .build();
        }
        return node.getSlaConfig();
    }

    /**
     * 保存节点 SLA 配置
     *
     * <p>更新 {@code ydsz_flow_node.sla_config} 字段并清理缓存。SLA 配置在 {@link FlowTaskTimeoutService}
     * 中读取并用于任务超时检测与处理。
     *
     * @param definitionId 流程定义 ID
     * @param nodeCode     节点编码
     * @param slaConfig    SLA 配置 JSON 字符串
     * @throws SysException {@code NOT_FOUND} — 节点不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSlaConfig(String definitionId, String nodeCode, String slaConfig) {
        FlowNode node = nodeMapper.selectByCode(definitionId, nodeCode);
        if (node == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("节点不存在: definitionId=" + definitionId + " nodeCode=" + nodeCode)
                .build();
        }
        node.setSlaConfig(slaConfig);
        nodeMapper.updateById(node);
        // 节点数据变更，清除该定义的本地节点/跳转缓存避免脏读
        flowDefinitionCacheService.evict(definitionId);
        log.info("[Flow] SLA 配置已保存: definitionId={} nodeCode={} slaConfig={}",
                definitionId, nodeCode, slaConfig);
    }

    // ============================== 版本历史与差异对比 ==============================

    /**
     * 查询流程版本历史
     *
     * <p>按 {@code flowCode+tenantId} 维度查询该流程的全部历史版本，组装为扁平 Map 列表供设计器版本时间线展示。
     *
     * @param definitionId 当前版本定义 ID（用于回溯 {@code flowCode/tenantId}）
     * @return 版本列表（按定义顺序）
     * @throws SysException {@code NOT_FOUND} — 流程定义不存在
     */
    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listVersions(String definitionId) {
        FlowDefinition def = definitionMapper.selectById(definitionId);
        if (def == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("流程定义不存在: " + definitionId)
                .build();
        }
        String tenantId = def.getTenantId() != null ? def.getTenantId() : "1";
        List<FlowDefinition> versions = definitionMapper.selectByFlowCode(def.getFlowCode(), tenantId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (FlowDefinition v : versions) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", v.getId());
            map.put("version", v.getFlowVersion());
            map.put("flowName", v.getFlowName());
            map.put("isPublish", v.getIsPublish());
            map.put("activityStatus", v.getActivityStatus());
            map.put("category", v.getCategory());
            map.put("description", v.getDescription());
            map.put("createdAt", v.getCreatedAt());
            map.put("updatedAt", v.getUpdatedAt());
            result.add(map);
        }
        log.info("[Flow] 查询版本历史: flowCode={} count={}",
                def.getFlowCode(), result.size());
        return result;
    }

    /**
     * 对比两个版本的差异
     *
     * <p>输出结构（{@code Map}）包含：
     * <ul>
     *   <li>{@code addedNodes} / {@code removedNodes} / {@code modifiedNodes}：节点级差异</li>
     *   <li>{@code addedSkips} / {@code removedSkips} / {@code modifiedSkips}：跳转级差异</li>
     *   <li>{@code addedNodeCodes} / {@code removedNodeCodes}：便于判断在途实例是否卡在已删除节点</li>
     * </ul>
     *
     * <p>典型用法：版本回滚前评估影响、灰度切换前预览变更。
     *
     * @param definitionId 流程定义 ID（用于回溯 {@code flowCode}）
     * @param version1     版本号 1（{@code Integer} 版本号，将转为 {@code String} 与 {@code flow_version} 比对）
     * @param version2     版本号 2
     * @return 差异 Map
     * @throws SysException {@code NOT_FOUND} — 定义或版本不存在
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> diffVersions(String definitionId, Integer version1, Integer version2) {
        // 1. 获取基础定义，找到 flowCode
        FlowDefinition baseDef = definitionMapper.selectById(definitionId);
        if (baseDef == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("流程定义不存在: " + definitionId)
                .build();
        }
        String tenantId = baseDef.getTenantId() != null ? baseDef.getTenantId() : "1";

        // 2. 查找两个版本的定义
        List<FlowDefinition> allVersions = definitionMapper.selectByFlowCode(
                baseDef.getFlowCode(), tenantId);
        String v1Str = String.valueOf(version1);
        String v2Str = String.valueOf(version2);
        FlowDefinition defV1 = allVersions.stream()
                .filter(d -> v1Str.equals(d.getFlowVersion()))
                .findFirst().orElse(null);
        FlowDefinition defV2 = allVersions.stream()
                .filter(d -> v2Str.equals(d.getFlowVersion()))
                .findFirst().orElse(null);

        if (defV1 == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("版本 " + version1 + " 不存在: flowCode=" + baseDef.getFlowCode())
                .build();
        }
        if (defV2 == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("版本 " + version2 + " 不存在: flowCode=" + baseDef.getFlowCode())
                .build();
        }

        // 3. 获取两个版本的节点和跳转
        List<FlowNode> nodesV1 = nodeMapper.selectByDefinitionId(defV1.getId());
        List<FlowNode> nodesV2 = nodeMapper.selectByDefinitionId(defV2.getId());
        List<FlowSkip> skipsV1 = skipMapper.selectByDefinitionId(defV1.getId());
        List<FlowSkip> skipsV2 = skipMapper.selectByDefinitionId(defV2.getId());

        // 4. 构建节点 nodeCode -> FlowNode 映射
        Map<String, FlowNode> nodeMapV1 = nodesV1.stream()
                .collect(Collectors.toMap(FlowNode::getNodeCode, n -> n, (a, b) -> a));
        Map<String, FlowNode> nodeMapV2 = nodesV2.stream()
                .collect(Collectors.toMap(FlowNode::getNodeCode, n -> n, (a, b) -> a));

        // 5. 对比节点差异
        List<Map<String, Object>> addedNodes = new ArrayList<>();
        List<Map<String, Object>> removedNodes = new ArrayList<>();
        List<Map<String, Object>> modifiedNodes = new ArrayList<>();

        // v2 有而 v1 没有 -> 新增
        for (Map.Entry<String, FlowNode> entry : nodeMapV2.entrySet()) {
            if (!nodeMapV1.containsKey(entry.getKey())) {
                FlowNode n = entry.getValue();
                addedNodes.add(Map.of("nodeCode", n.getNodeCode(),
                        "nodeName", n.getNodeName() != null ? n.getNodeName() : ""));
            }
        }
        // v1 有而 v2 没有 -> 删除
        for (Map.Entry<String, FlowNode> entry : nodeMapV1.entrySet()) {
            if (!nodeMapV2.containsKey(entry.getKey())) {
                FlowNode n = entry.getValue();
                removedNodes.add(Map.of("nodeCode", n.getNodeCode(),
                        "nodeName", n.getNodeName() != null ? n.getNodeName() : ""));
            }
        }
        // 两者都有 -> 检查修改
        for (Map.Entry<String, FlowNode> entry : nodeMapV1.entrySet()) {
            String code = entry.getKey();
            if (nodeMapV2.containsKey(code)) {
                FlowNode n1 = entry.getValue();
                FlowNode n2 = nodeMapV2.get(code);
                Map<String, Map<String, Object>> changes = new LinkedHashMap<>();
                if (!Objects.equals(n1.getNodeName(), n2.getNodeName())) {
                    changes.put("nodeName", Map.of("old",
                            n1.getNodeName() != null ? n1.getNodeName() : "",
                            "new", n2.getNodeName() != null ? n2.getNodeName() : ""));
                }
                if (!Objects.equals(n1.getNodeType(), n2.getNodeType())) {
                    changes.put("nodeType", Map.of("old",
                            n1.getNodeType() != null ? n1.getNodeType() : "",
                            "new", n2.getNodeType() != null ? n2.getNodeType() : ""));
                }
                if (!Objects.equals(n1.getPermissionFlag(), n2.getPermissionFlag())) {
                    changes.put("permissionFlag", Map.of("old",
                            n1.getPermissionFlag() != null ? n1.getPermissionFlag() : "",
                            "new", n2.getPermissionFlag() != null ? n2.getPermissionFlag() : ""));
                }
                // P1-3: 增强 diff — 对比 formFieldsConfig（表单字段权限）
                if (!Objects.equals(n1.getFormFieldsConfig(), n2.getFormFieldsConfig())) {
                    changes.put("formFieldsConfig", Map.of("old",
                            n1.getFormFieldsConfig() != null ? n1.getFormFieldsConfig() : "",
                            "new", n2.getFormFieldsConfig() != null ? n2.getFormFieldsConfig() : ""));
                }
                // P1-3: 增强 diff — 对比 ext（含 formSchema、selfSelect、SLA 等配置）
                if (!Objects.equals(n1.getExt(), n2.getExt())) {
                    changes.put("ext", Map.of("old",
                            n1.getExt() != null ? n1.getExt() : "",
                            "new", n2.getExt() != null ? n2.getExt() : ""));
                }
                // P1-3: 增强 diff — 对比节点描述（skipAnyNode）
                if (!Objects.equals(n1.getSkipAnyNode(), n2.getSkipAnyNode())) {
                    changes.put("skipAnyNode", Map.of("old",
                            n1.getSkipAnyNode() != null ? n1.getSkipAnyNode() : "",
                            "new", n2.getSkipAnyNode() != null ? n2.getSkipAnyNode() : ""));
                }
                // P1-3: 增强 diff — 对比 SLA 配置
                if (!Objects.equals(n1.getSlaConfig(), n2.getSlaConfig())) {
                    changes.put("slaConfig", Map.of("old",
                            n1.getSlaConfig() != null ? n1.getSlaConfig() : "",
                            "new", n2.getSlaConfig() != null ? n2.getSlaConfig() : ""));
                }
                if (!changes.isEmpty()) {
                    Map<String, Object> modEntry = new LinkedHashMap<>();
                    modEntry.put("nodeCode", code);
                    modEntry.put("changes", changes);
                    modifiedNodes.add(modEntry);
                }
            }
        }

        // 6. 构建连线 key 映射（sourceRef -> targetNodeCode）
        // sourceRef 存储在 ext JSON 的 sourceRef 字段中
        Map<String, FlowSkip> skipMapV1 = buildSkipKeyMap(skipsV1);
        Map<String, FlowSkip> skipMapV2 = buildSkipKeyMap(skipsV2);

        // 7. 对比连线差异
        List<Map<String, Object>> addedSkips = new ArrayList<>();
        List<Map<String, Object>> removedSkips = new ArrayList<>();

        for (Map.Entry<String, FlowSkip> entry : skipMapV2.entrySet()) {
            if (!skipMapV1.containsKey(entry.getKey())) {
                FlowSkip s = entry.getValue();
                addedSkips.add(skipToMap(s));
            }
        }
        for (Map.Entry<String, FlowSkip> entry : skipMapV1.entrySet()) {
            if (!skipMapV2.containsKey(entry.getKey())) {
                FlowSkip s = entry.getValue();
                removedSkips.add(skipToMap(s));
            }
        }

        // 8. 组装结果
        Map<String, Object> nodeChanges = new LinkedHashMap<>();
        nodeChanges.put("added", addedNodes);
        nodeChanges.put("removed", removedNodes);
        nodeChanges.put("modified", modifiedNodes);

        Map<String, Object> skipChanges = new LinkedHashMap<>();
        skipChanges.put("added", addedSkips);
        skipChanges.put("removed", removedSkips);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version1", version1);
        result.put("version2", version2);
        result.put("nodeChanges", nodeChanges);
        result.put("skipChanges", skipChanges);
        // P1-3: 增强 diff — 统计摘要
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalNodeChanges", addedNodes.size() + removedNodes.size() + modifiedNodes.size());
        summary.put("totalSkipChanges", addedSkips.size() + removedSkips.size());
        summary.put("hasBreakingChange", !removedNodes.isEmpty() || !removedSkips.isEmpty());
        result.put("summary", summary);

        log.info("[Flow] 版本差异对比: flowCode={} v1={} v2={} "
                        + "nodeAdded={} nodeRemoved={} nodeModified={} "
                        + "skipAdded={} skipRemoved={}",
                baseDef.getFlowCode(), version1, version2,
                addedNodes.size(), removedNodes.size(), modifiedNodes.size(),
                addedSkips.size(), removedSkips.size());

        return result;
    }

    /**
     * 构建连线 key 映射：sourceRef + "->" + nextNodeCode
     */
    private Map<String, FlowSkip> buildSkipKeyMap(List<FlowSkip> skips) {
        Map<String, FlowSkip> map = new LinkedHashMap<>();
        for (FlowSkip skip : skips) {
            String key = buildSkipKey(skip);
            if (key != null) {
                map.put(key, skip);
            }
        }
        return map;
    }

    /**
     * 从 ext JSON 中提取 sourceRef，拼接 key
     */
    private String buildSkipKey(FlowSkip skip) {
        String sourceRef = null;
        if (StringUtils.hasText(skip.getExt())) {
            try {
                Map<String, Object> extJson = YdszJson.parseMap(skip.getExt());
                sourceRef = MapUtils.getString(extJson, "sourceRef");
            } catch (Exception ignored) {
                // ignore parse error
            }
        }
        if (sourceRef != null && skip.getNextNodeCode() != null) {
            return sourceRef + "->" + skip.getNextNodeCode();
        }
        return skip.getId() != null ? String.valueOf(skip.getId()) : null;
    }

    /**
     * 将连线转为 Map 表示
     */
    private Map<String, Object> skipToMap(FlowSkip skip) {
        String sourceRef = null;
        if (StringUtils.hasText(skip.getExt())) {
            try {
                Map<String, Object> extJson = YdszJson.parseMap(skip.getExt());
                sourceRef = MapUtils.getString(extJson, "sourceRef");
            } catch (Exception ignored) {
                // ignore
            }
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sourceRef", sourceRef != null ? sourceRef : "");
        map.put("nextNodeCode", skip.getNextNodeCode() != null ? skip.getNextNodeCode() : "");
        map.put("skipName", skip.getSkipName() != null ? skip.getSkipName() : "");
        map.put("skipType", skip.getSkipType() != null ? skip.getSkipType() : "");
        map.put("skipCondition", skip.getSkipCondition() != null ? skip.getSkipCondition() : "");
        return map;
    }

    // ============================== GAP-P1-6: BPMN 部署包 .zip 批量导入 ==============================

    /**
     * GAP-P1-6: 从 BPMN 部署包 .zip 批量导入流程定义。
     *
     * <p>对标 Activiti/Flowable 的 {@code repositoryService.createDeployment().addZipInputStream()}。
     * 遍历 zip 内的 {@code .bpmn} / {@code .bpmn20.xml} 文件，逐个解析并委托 {@link #deploy} 入库。
     * 单个文件失败不影响其他文件（通过 self 代理调用 deploy，每个文件独立事务）。
     *
     * <p>flowCode 取自 BPMN process id，flowName 取自 BPMN process name（缺失时回退为文件名）。
     * 版本号默认 "1.0"，如已存在同 flowCode + version 的定义则该文件记为失败并跳过。
     *
     * @param zipBytes zip 文件字节数组
     * @param tenantId 租户 ID（可空，默认从 SecurityContext 获取）
     * @return Map 包含 successCount（成功数）和 failedItems（失败列表，每项含 fileName + reason）
     */
    @Override
    public Map<String, Object> batchDeployFromZip(byte[] zipBytes, String tenantId) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("zip 文件内容为空")
                .build();
        }
        String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();

        int successCount = 0;
        List<Map<String, String>> failedItems = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String fileName = entry.getName();
                // 仅处理 .bpmn / .bpmn20.xml 文件（大小写不敏感）
                String lowerName = fileName.toLowerCase();
                if (!lowerName.endsWith(".bpmn") && !lowerName.endsWith(".bpmn20.xml")) {
                    continue;
                }
                try {
                    String bpmnXml = new String(readAllBytes(zis), StandardCharsets.UTF_8);
                    // 先解析一次获取 processId（作为 flowCode）和 processName（作为 flowName）
                    BpmnModel model = bpmnXmlParser.parse(bpmnXml);
                    String flowCode = model.getProcessId();
                    String flowName = StringUtils.hasText(model.getProcessName())
                            ? model.getProcessName() : extractBaseName(fileName);

                    if (!StringUtils.hasText(flowCode)) {
                        throw SysException.builder()
                            .resultCode(BaseResultCode.BAD_REQUEST)
                            .message("BPMN 文件缺少 process id: " + fileName)
                            .build();
                    }

                    FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
                    dto.setFlowCode(flowCode);
                    dto.setFlowName(flowName);
                    dto.setVersion("1.0");
                    dto.setBpmnXml(bpmnXml);
                    dto.setTenantId(tid);
                    // 通过 self 代理调用，确保 deploy 的 @Transactional 生效（独立事务）
                    self.deploy(dto);
                    successCount++;
                    log.info("[Flow] zip 批量导入成功: fileName={} flowCode={}", fileName, flowCode);
                } catch (Exception e) {
                    Map<String, String> fail = new LinkedHashMap<>();
                    fail.put("fileName", fileName);
                    fail.put("reason", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                    failedItems.add(fail);
                    log.warn("[Flow] zip 批量导入失败: fileName={} reason={}", fileName, e.getMessage());
                } finally {
                    zis.closeEntry();
                }
            }
        } catch (Exception e) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("zip 文件解析失败: " + e.getMessage())
                .build();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("successCount", successCount);
        result.put("failedItems", failedItems);
        log.info("[Flow] zip 批量导入完成: success={} failed={}", successCount, failedItems.size());
        return result;
    }

    /** 读取 ZipInputStream 当前 entry 的全部字节（不关闭流） */
    private byte[] readAllBytes(ZipInputStream zis) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = zis.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    /** 从 zip entry 路径中提取文件名（去掉目录和扩展名） */
    private String extractBaseName(String fileName) {
        String name = fileName;
        int slashIdx = name.lastIndexOf('/');
        if (slashIdx >= 0) {
            name = name.substring(slashIdx + 1);
        }
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx > 0) {
            name = name.substring(0, dotIdx);
        }
        return name;
    }

    // ============================== P2-4: 设计器协同编辑锁定 ==============================

    /**
     * P2-4: 加锁流程定义。
     *
     * <p>采用 CAS（Compare-And-Swap）乐观锁实现，保证多用户并发加锁的强一致性：
     * <ol>
     *   <li>未锁定（lockedBy IS NULL）→ CAS 成功</li>
     *   <li>同一人持锁（lockedBy = userId）→ CAS 续约成功</li>
     *   <li>他人持锁但已超时（lockedAt &lt; timeoutExpired）→ CAS 抢占成功</li>
     *   <li>他人持锁且未超时 → CAS 失败，抛 SysException</li>
     * </ol>
     *
     * <p>使用 {@link FlowDefinitionMapper#casLock} 的单条 UPDATE SQL 完成判定 + 更新，
     * 避免"读-判-写"竞态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean lockDefinition(String definitionId, String userId) {
        if (!StringUtils.hasText(definitionId) || !StringUtils.hasText(userId)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_d6e7f8a9")
                .build();
        }
        FlowDefinition def = definitionMapper.selectById(definitionId);
        if (def == null || (def.getDeleted() != null && def.getDeleted() == 1)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .key("error.workflow.msg_e7f8a9b0").params(definitionId)
                .build();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeoutExpired = now.minusMinutes(flowProperties.getDesignerLockTimeoutMinutes());

        // CAS 加锁：expectedOldBy = userId 用于"同号续约"场景
        // SQL 条件：(locked_by IS NULL OR locked_by = userId OR locked_at < timeoutExpired)
        //          AND version = #{version}
        // 这里 expectedOldBy 传 userId，因为若是同一人持锁应允许续约
        int affected = definitionMapper.casLock(
                definitionId, userId, now, userId, timeoutExpired, def.getRevision());

        if (affected == 1) {
            log.info("[Flow] 设计器加锁成功: defId={} userId={} timeout={}min",
                    definitionId, userId, flowProperties.getDesignerLockTimeoutMinutes());
            return true;
        }

        // CAS 失败：要么 version 不匹配（并发更新），要么锁被他人持有且未超时
        FlowDefinition latest = definitionMapper.selectById(definitionId);
        if (latest == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .key("error.workflow.msg_e7f8a9b0").params(definitionId)
                .build();
        }
        String holder = latest.getLockedBy();
        if (StringUtils.hasText(holder) && !holder.equals(userId)) {
            // 检查是否其实已超时（理论上 SQL 应能命中，但 version 不匹配会阻塞）
            boolean expired = latest.getLockedAt() != null
                    && latest.getLockedAt().isBefore(timeoutExpired);
            if (expired) {
                // 已超时但 CAS 失败 → 因 version 变化导致，重试一次
                log.warn("[Flow] 设计器加锁重试（锁已超时但 version 变化）: defId={} holder={}",
                        definitionId, holder);
                int retry = definitionMapper.casLock(
                        definitionId, userId, now, userId, timeoutExpired, latest.getRevision());
                if (retry == 1) {
                    log.info("[Flow] 设计器加锁成功（重试）: defId={} userId={} 抢占自={}",
                            definitionId, userId, holder);
                    return true;
                }
            }
            // 锁被他人持有且未超时
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .key("error.workflow.msg_f8a9b0c1").params(holder)
                .build();
        }
        // 走到这里说明是并发 version 变化导致，按并发冲突处理
        throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_a9b0c1d2")
                .build();
    }

    /**
     * P2-4: 解锁流程定义。
     *
     * <p>仅持锁人本人可解锁；他人持锁或未锁定时抛 SysException。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unlockDefinition(String definitionId, String userId) {
        if (!StringUtils.hasText(definitionId) || !StringUtils.hasText(userId)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_d6e7f8a9")
                .build();
        }
        FlowDefinition def = definitionMapper.selectById(definitionId);
        if (def == null || (def.getDeleted() != null && def.getDeleted() == 1)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .key("error.workflow.msg_e7f8a9b0").params(definitionId)
                .build();
        }

        // 未锁定直接返回成功（幂等）
        if (!StringUtils.hasText(def.getLockedBy())) {
            log.debug("[Flow] 设计器解锁：当前未锁定，幂等返回 defId={}", definitionId);
            return true;
        }

        // CAS 解锁：仅持锁人可解锁
        int affected = definitionMapper.casUnlock(definitionId, userId, def.getRevision());
        if (affected == 1) {
            log.info("[Flow] 设计器解锁成功: defId={} userId={}", definitionId, userId);
            return true;
        }

        // CAS 失败：要么非持锁人，要么 version 变化
        FlowDefinition latest = definitionMapper.selectById(definitionId);
        if (latest == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .key("error.workflow.msg_e7f8a9b0").params(definitionId)
                .build();
        }
        String holder = latest.getLockedBy();
        if (StringUtils.hasText(holder) && !holder.equals(userId)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.FORBIDDEN)
                .key("error.workflow.msg_b1c2d3e4").params(holder)
                .build();
        }
        // 此时 holder = userId 或 holder 已被清空（并发已解锁）→ 视为成功
        log.info("[Flow] 设计器解锁：锁已被并发清空，视为成功 defId={} userId={}",
                definitionId, userId);
        return true;
    }

    /**
     * P2-4: 查询流程定义的锁定状态。
     */
    @Override
    public Map<String, Object> getLockStatus(String definitionId) {
        if (!StringUtils.hasText(definitionId)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_d6e7f8a9")
                .build();
        }
        FlowDefinition def = definitionMapper.selectById(definitionId);
        if (def == null || (def.getDeleted() != null && def.getDeleted() == 1)) {
            return null;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        boolean locked = StringUtils.hasText(def.getLockedBy());
        boolean expired = false;
        if (locked && def.getLockedAt() != null) {
            LocalDateTime timeoutExpired = LocalDateTime.now().minusMinutes(flowProperties.getDesignerLockTimeoutMinutes());
            expired = def.getLockedAt().isBefore(timeoutExpired);
        }
        result.put("locked", locked);
        result.put("lockedBy", def.getLockedBy());
        result.put("lockedAt", def.getLockedAt());
        result.put("expired", expired);
        return result;
    }

    // ============================== P2-5: 变更影响分析报告 ==============================

    /**
     * P2-5: 变更影响分析报告。
     *
     * <p>评估老版本定义升级到新版本对在途实例的影响，输出：
     * <ul>
     *   <li>版本差异（节点新增/删除/修改 + 跳转新增/删除）</li>
     *   <li>在途实例统计（总数 + 按节点分布）</li>
     *   <li>受影响实例识别（卡死节点 / 类型变更节点）</li>
     *   <li>风险等级（HIGH/MEDIUM/LOW/NONE）</li>
     *   <li>迁移建议（人工介入 / 等待自然完成 / 直接升级）</li>
     * </ul>
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> analyzeMigrationImpact(String oldDefinitionId, String newDefinitionId) {
        if (!StringUtils.hasText(oldDefinitionId) || !StringUtils.hasText(newDefinitionId)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_c2d3e4f5")
                .build();
        }

        // 1. 校验两个定义存在
        FlowDefinition oldDef = definitionMapper.selectById(oldDefinitionId);
        if (oldDef == null || (oldDef.getDeleted() != null && oldDef.getDeleted() == 1)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .key("error.workflow.msg_e7f8a9b0").params(oldDefinitionId)
                .build();
        }
        FlowDefinition newDef = definitionMapper.selectById(newDefinitionId);
        if (newDef == null || (newDef.getDeleted() != null && newDef.getDeleted() == 1)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .key("error.workflow.msg_e7f8a9b0").params(newDefinitionId)
                .build();
        }
        // 校验同 flowCode
        if (!Objects.equals(oldDef.getFlowCode(), newDef.getFlowCode())) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_d3e4f5a6")
                .build();
        }

        // 2. 复用 diffVersions 计算版本差异
        Integer v1 = parseVersionInt(oldDef.getFlowVersion());
        Integer v2 = parseVersionInt(newDef.getFlowVersion());
        Map<String, Object> diff = diffVersions(oldDefinitionId, v1, v2);

        // 3. 统计老版本在途实例
        long runningTotal = instanceMapper.countRunningByDefinition(oldDefinitionId);
        List<Map<String, Object>> runningByNode = instanceMapper
                .selectRunningGroupByNode(oldDefinitionId);

        // 4. 识别受影响实例
        // 4.1 卡死实例：当前节点在老版本存在但在新版本被删除
        Map<String, Object> nodeChanges = MapUtils.safeCastMap(diff.get("nodeChanges"));
        List<Map<String, Object>> removedNodes = MapUtils.getListOfMaps(nodeChanges, "removed");
        Set<String> removedNodeCodes = removedNodes != null ? removedNodes.stream()
                .map(n -> String.valueOf(n.get("nodeCode")))
                .collect(Collectors.toSet()) : Set.of();

        // 4.2 类型/审批人变更节点
        List<Map<String, Object>> modifiedNodes = MapUtils.getListOfMaps(nodeChanges, "modified");

        List<Map<String, Object>> stuckInstances = new ArrayList<>();
        List<Map<String, Object>> affectedInstances = new ArrayList<>();
        for (Map<String, Object> node : runningByNode) {
            String nodeCode = String.valueOf(node.get("currentNodeCode"));
            long cnt = ((Number) node.get("cnt")).longValue();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("nodeCode", nodeCode);
            entry.put("currentNodeName", node.get("currentNodeName"));
            entry.put("instanceCount", cnt);
            if (removedNodeCodes.contains(nodeCode)) {
                entry.put("reason", "NODE_REMOVED");
                stuckInstances.add(entry);
            } else if (modifiedNodes.stream()
                    .anyMatch(m -> nodeCode.equals(String.valueOf(m.get("nodeCode"))))) {
                entry.put("reason", "NODE_MODIFIED");
                affectedInstances.add(entry);
            }
        }

        // 5. 计算风险等级
        // HIGH：有在途实例卡在已删除节点（无法继续流转）
        // MEDIUM：有在途实例在已修改节点（类型/审批人变化）或大量在途实例（>100）
        // LOW：有少量在途实例但节点未变更
        // NONE：无在途实例
        String riskLevel;
        if (runningTotal == 0) {
            riskLevel = "NONE";
        } else if (!stuckInstances.isEmpty()) {
            riskLevel = "HIGH";
        } else if (!affectedInstances.isEmpty() || runningTotal > 100) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }

        // 6. 生成迁移建议
        List<String> recommendations = buildRecommendations(
                riskLevel, runningTotal, stuckInstances, affectedInstances,
                removedNodes, modifiedNodes);

        // 7. 组装结果
        Map<String, Object> oldDefInfo = new LinkedHashMap<>();
        oldDefInfo.put("id", oldDef.getId());
        oldDefInfo.put("flowCode", oldDef.getFlowCode());
        oldDefInfo.put("flowName", oldDef.getFlowName());
        oldDefInfo.put("flowVersion", oldDef.getFlowVersion());

        Map<String, Object> newDefInfo = new LinkedHashMap<>();
        newDefInfo.put("id", newDef.getId());
        newDefInfo.put("flowCode", newDef.getFlowCode());
        newDefInfo.put("flowName", newDef.getFlowName());
        newDefInfo.put("flowVersion", newDef.getFlowVersion());

        Map<String, Object> runningInstances = new LinkedHashMap<>();
        runningInstances.put("total", runningTotal);
        runningInstances.put("byNode", runningByNode);

        Map<String, Object> impactedInstances = new LinkedHashMap<>();
        impactedInstances.put("stuckInstances", stuckInstances);
        impactedInstances.put("affectedInstances", affectedInstances);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("oldDefinition", oldDefInfo);
        result.put("newDefinition", newDefInfo);
        result.put("diff", diff);
        result.put("runningInstances", runningInstances);
        result.put("impactedInstances", impactedInstances);
        result.put("riskLevel", riskLevel);
        result.put("recommendations", recommendations);

        log.info("[Flow] 变更影响分析: oldDef={} newDef={} running={} stuck={} affected={} risk={}",
                oldDefinitionId, newDefinitionId, runningTotal,
                stuckInstances.size(), affectedInstances.size(), riskLevel);
        return result;
    }

    /**
     * 解析版本号字符串为整数（用于 diffVersions 调用）。
     *
     * @param versionStr 版本字符串（如 "1.0" / "2"）
     * @return 主版本号整数（如 1 / 2），无法解析时返回 0
     */
    private Integer parseVersionInt(String versionStr) {
        if (!StringUtils.hasText(versionStr)) {
            return 0;
        }
        try {
            // "1.0" → 取 "." 之前的部分；"2" → 直接转
            String main = versionStr.contains(".")
                    ? versionStr.substring(0, versionStr.indexOf('.'))
                    : versionStr;
            return Integer.parseInt(main.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 根据风险等级和影响范围生成迁移建议。
     */
    private List<String> buildRecommendations(
            String riskLevel,
            long runningTotal,
            List<Map<String, Object>> stuckInstances,
            List<Map<String, Object>> affectedInstances,
            List<Map<String, Object>> removedNodes,
            List<Map<String, Object>> modifiedNodes) {
        List<String> recs = new ArrayList<>();

        switch (riskLevel) {
            case "NONE":
                recs.add("无在途实例，可直接发布新版本");
                recs.add("建议发布后停用老版本，避免新实例继续使用老版本");
                break;
            case "LOW":
                recs.add("存在 " + runningTotal + " 个在途实例，但节点未变更，风险较低");
                recs.add("建议：发布新版本 + 等待在途实例自然完成后停用老版本");
                recs.add("可选：通知发起人主动撤回后重新发起以使用新版本");
                break;
            case "MEDIUM":
                if (!affectedInstances.isEmpty()) {
                    recs.add("存在 " + affectedInstances.size() + " 个节点的在途实例受影响（类型/审批人变更）");
                    recs.add("建议：通知相关审批人确认变更影响，必要时手工干预");
                }
                if (runningTotal > 100) {
                    recs.add("在途实例数量较多（" + runningTotal + "），建议分批次迁移");
                }
                recs.add("建议：发布新版本但保留老版本激活，待在途实例自然消化后再切换");
                break;
            case "HIGH":
                recs.add("【高危】存在 " + stuckInstances.size() + " 个节点的在途实例将卡死");
                for (Map<String, Object> stuck : stuckInstances) {
                    recs.add("  - 节点 " + stuck.get("nodeCode")
                            + "（" + stuck.get("currentNodeName") + "）有 "
                            + stuck.get("instanceCount") + " 个实例无法继续流转");
                }
                recs.add("建议：发布新版本前必须先处理在途实例：");
                recs.add("  1) 对卡死节点的实例手工强制流转到新版本对应节点");
                recs.add("  2) 或通知发起人撤回后重新发起");
                recs.add("  3) 或保留老版本激活直到所有在途实例完成");
                recs.add("禁止：直接停用老版本会导致在途实例永久卡死");
                break;
            default:
                recs.add("未知风险等级: " + riskLevel);
        }
        return recs;
    }

    // ============================== P0-2: 一键回滚 ==============================

    /**
     * P0-2: 流程定义一键回滚
     *
     * <p>将指定 flowCode 的激活版本切换回上一个已发布版本，
     * 并自动迁移在途实例。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CacheConstants.FLOW_DEF_PUBLISHED_CACHE,
            CacheConstants.FLOW_DEF_LATEST_CACHE}, allEntries = true)
    public Map<String, Object> rollbackDefinition(String flowCode, String tenantId) {
        if (!StringUtils.hasText(flowCode)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("flowCode 不能为空")
                .build();
        }
        String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();

        // 1. 查询当前激活版本
        FlowDefinition currentDef = definitionMapper.selectPublished(flowCode, null, tid);
        if (currentDef == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("未找到当前激活的流程定义: flowCode=" + flowCode)
                .build();
        }

        // 2. 查询上一个已发布版本（排除当前版本，按版本号降序取第一条）
        LambdaQueryWrapper<FlowDefinition> qw = new LambdaQueryWrapper<>();
        qw.eq(FlowDefinition::getFlowCode, flowCode)
                .eq(FlowDefinition::getTenantId, tid)
                .ne(FlowDefinition::getId, currentDef.getId())
                .eq(FlowDefinition::getIsPublish, 1)
                .eq(FlowDefinition::getDeleted, 0)
                .orderByDesc(FlowDefinition::getFlowVersion)
                .last("LIMIT 1");
        FlowDefinition previousDef = definitionMapper.selectOne(qw);
        if (previousDef == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("无可回滚的历史版本: flowCode=" + flowCode)
                .build();
        }

        // 3. 评估迁移影响
        Map<String, Object> migrationImpact = analyzeMigrationImpact(
                currentDef.getId(), previousDef.getId());
        String riskLevel = (String) migrationImpact.get("riskLevel");

        // 4. HIGH 风险时阻止回滚
        if ("HIGH".equals(riskLevel)) {
            log.warn("[Flow] 一键回滚中止（HIGH 风险）: flowCode={} current={} target={} risk={}",
                    flowCode, currentDef.getId(), previousDef.getId(), riskLevel);
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("回滚风险等级为 HIGH，存在在途实例将卡死，请先处理在途实例后再回滚")
                .build();
        }

        // 5. 切换激活版本到上一个版本
        switchActiveVersion(flowCode, previousDef.getId(), tid);

        // 6. 迁移在途实例
        InstanceMigrationResultDTO migrationResult = null;
        try {
            InstanceMigrationDTO migrateDto = new InstanceMigrationDTO();
            migrateDto.setSourceDefinitionId(currentDef.getId());
            migrateDto.setTargetDefinitionId(previousDef.getId());
            migrateDto.setTenantId(tid);
            // 自动映射节点（编码相同的自动配对）
            Map<String, String> nodeMapping = new HashMap<>();
            List<FlowNode> oldNodes = nodeMapper.selectByDefinitionId(currentDef.getId());
            List<FlowNode> newNodes = nodeMapper.selectByDefinitionId(previousDef.getId());
            Set<String> newNodeCodes = newNodes.stream()
                    .map(FlowNode::getNodeCode)
                    .collect(Collectors.toSet());
            for (FlowNode oldNode : oldNodes) {
                if (newNodeCodes.contains(oldNode.getNodeCode())) {
                    nodeMapping.put(oldNode.getNodeCode(), oldNode.getNodeCode());
                }
            }
            migrateDto.setNodeMapping(nodeMapping);
            migrationResult = migrationService.migrate(migrateDto);
            log.info("[Flow] 一键回滚实例迁移完成: flowCode={} migrated={} skipped={}",
                    flowCode,
                    migrationResult != null ? migrationResult.getMigratedCount() : 0,
                    migrationResult != null ? migrationResult.getSkippedCount() : 0);
        } catch (Exception e) {
            log.error("[Flow] 一键回滚实例迁移异常: flowCode={} err={}",
                    flowCode, e.getMessage(), e);
        }

        // 7. 组装回滚报告
        Map<String, Object> fromInfo = new LinkedHashMap<>();
        fromInfo.put("id", currentDef.getId());
        fromInfo.put("flowVersion", currentDef.getFlowVersion());

        Map<String, Object> toInfo = new LinkedHashMap<>();
        toInfo.put("id", previousDef.getId());
        toInfo.put("flowVersion", previousDef.getFlowVersion());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fromDefinition", fromInfo);
        result.put("toDefinition", toInfo);
        result.put("migrationImpact", migrationImpact);
        result.put("migrationResult", migrationResult);
        result.put("rollbackTime", LocalDateTime.now().toString());

        log.info("[Flow] 一键回滚完成: flowCode={} from=v{} to=v{} risk={}",
                flowCode, currentDef.getFlowVersion(), previousDef.getFlowVersion(), riskLevel);
        return result;
    }
}
