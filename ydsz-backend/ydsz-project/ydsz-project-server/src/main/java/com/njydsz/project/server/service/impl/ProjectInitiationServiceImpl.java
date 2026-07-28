package com.njydsz.project.server.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.event.model.StandardEventTypes;
import com.njydsz.common.event.service.OutboxService;
import com.njydsz.common.feign.assembler.NameAssembler;
import com.njydsz.common.feign.assembler.NameType;
import com.njydsz.common.json.YdszJson;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.entity.project.ProjectInitiation;
import com.njydsz.project.domain.dto.ProjectInitiationPageQuery;
import com.njydsz.project.domain.entity.project.ProjectInitiation;
import com.njydsz.project.domain.repository.project.IProjectInitiationRepository;
import com.njydsz.project.domain.vo.ProjectInitiationVO;
import com.njydsz.common.search.sync.SearchIndexEventBridge;
import com.njydsz.project.server.metrics.ProjectMetrics;
import com.njydsz.project.server.service.ProjectInitiationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 项目立项 Service 实现
 *
 * <p>对 {@link ProjectInitiationService} 接口的完整实现，是「项目管理」业务域的<b>起点</b>。
 * 维护 {@code ydsz_project_initiation} 立项单表，对标大厂 PMIS / 经营管理系统中的「项目立项 / 项目立项申请」流程，
 * 是合同 / 预算 / 计划 / 执行 / 收尾所有后续环节的「种子」。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #getByCode} / {@link #page} /
 *       {@link #save} / {@link #updateById} / {@link #removeById}</li>
 *   <li><b>阶段推进</b>：{@link #advanceStage} — 推进立项阶段（{@code PRE_INITIATION} →
 *       {@code INITIATION} → {@code CONTRACT} → {@code EXECUTION} → {@code CLOSURE}），
 *       触发 {@code PROJECT_STAGE_CHANGED} 领域事件</li>
 *   <li><b>PM 维度查询</b>：{@link #listByPmId} — 项目经理「我负责的所有项目」入口</li>
 *   <li><b>外键名称自动装配</b>：通过 {@link NameAssembler} 自动装配 PM 姓名、客户名称等外键字段，
 *       <b>避免</b>业务方 N+1 远程调用</li>
 *   <li><b>领域事件</b>：立项创建 / 阶段变更时通过 {@link OutboxService} 发布事件</li>
 *   <li><b>搜索同步</b>：通过 {@link SearchIndexEventBridge} 同步到 ES 索引</li>
 *   <li><b>指标埋点</b>：通过 {@link ProjectMetrics} 暴露 Prometheus 指标</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>读方法不开启事务，依赖 MyBatis 自动提交</li>
 *   <li>领域事件（Outbox）和搜索同步（ES）走<b>事务后</b>发布，失败仅记录告警不影响主流程</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>项目状态机（{@code stage}）：</b>
 * <pre>
 *  PRE_INITIATION → INITIATION → CONTRACT → EXECUTION → CLOSURE
 *       (预立项)       (立项)     (合同)     (执行)     (收尾)
 * </pre>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 创建立项（默认 DRAFT / PRE_INITIATION / 项目级别 C）
 * ProjectInitiation entity = new ProjectInitiation();
 * entity.setProjectCode("PRJ-2026-001");
 * entity.setProjectName("某大型 ERP 实施项目");
 * entity.setPmId("user_123");
 * entity.setCustomerId("cust_456");
 * entity.setEstimatedAmount(new BigDecimal("5000000"));
 * String id = projectInitiationService.save(entity);
 *
 * // 2. 推进到立项阶段
 * projectInitiationService.advanceStage(id, "INITIATION", "G1");
 *
 * // 3. PM 查看「我的项目」
 * List<ProjectInitiationVO> myProjects = projectInitiationService.listByPmId("user_123");
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectInitiationService 立项 Service 接口
 * @see com.njydsz.project.domain.entity.project.ProjectInitiation 立项实体
 * @see com.njydsz.project.domain.vo.ProjectInitiationVO 立项 VO
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectInitiationServiceImpl implements ProjectInitiationService {

    /** 立项仓储（聚合 Mapper + 缓存 + 事件） */
    private final IProjectInitiationRepository repository;
    /** 外键名称自动装配器（PM 姓名 / 客户名称等） */
    private final NameAssembler nameAssembler;
    /** 项目监控指标采集器 */
    private final ProjectMetrics projectMetrics;
    /** Outbox 服务（可选依赖，用于发布领域事件） */
    private final ObjectProvider<OutboxService> outboxServiceProvider;
    /** 搜索索引事件桥接（可选依赖，用于同步 ES 索引） */
    private final ObjectProvider<SearchIndexEventBridge> searchIndexBridgeProvider;

    /**
     * 根据主键查询立项
     *
     * <p>适用场景：管理后台「立项详情」页。
     * 高频查询（如合同关联项目）建议走 {@link #getByCode}，索引效率更高。
     *
     * @param id 立项主键
     * @return 立项 VO（已装配外键名称），不存在返回 null
     */
    @Override
    public ProjectInitiationVO getById(String id) {
        long start = System.currentTimeMillis();
        try {
            ProjectInitiation entity = repository.getById(id);
            if (entity == null) {
                return null;
            }
            return convertToVO(entity);
        } finally {
            projectMetrics.recordQueryDuration(System.currentTimeMillis() - start);
        }
    }

    /**
     * 根据项目编码查询立项
     *
     * <p>业务系统常用 {@code projectCode}（业务主键）而非 {@code id}（雪花算法）查询，
     * 例如从合同 / 预算 / 工时系统反查立项。
     *
     * @param projectCode 项目编码（如 {@code "PRJ-2026-001"}）
     * @return 立项 VO，不存在返回 null
     */
    @Override
    public ProjectInitiationVO getByCode(String projectCode) {
        long start = System.currentTimeMillis();
        try {
            ProjectInitiation entity = repository.getOne(
                    new LambdaQueryWrapper<ProjectInitiation>()
                            .eq(ProjectInitiation::getProjectCode, projectCode));
            if (entity == null) {
                return null;
            }
            return convertToVO(entity);
        } finally {
            projectMetrics.recordQueryDuration(System.currentTimeMillis() - start);
        }
    }

    /**
     * 分页查询立项（管理后台列表页）
     *
     * <p>支持按 {@code projectCode} 模糊匹配、{@code projectName} 模糊匹配、
     * {@code stage / status / pmId / customerId} 精确匹配进行过滤，按 {@code created_at} 倒序返回。
     *
     * <p><b>外键名称自动装配：</b>通过 {@link NameAssembler} 批量装配 PM 姓名（{@code pmName}），
     * 避免业务方 N+1 远程调用。
     *
     * @param query 分页查询条件
     * @return 分页结果（已装配外键名称）
     */
    @Override
    public IPage<ProjectInitiationVO> page(ProjectInitiationPageQuery query) {
        long start = System.currentTimeMillis();
        try {
            LambdaQueryWrapper<ProjectInitiation> wrapper = new LambdaQueryWrapper<>();
            wrapper.like(StringUtils.hasText(query.getProjectCode()),
                            ProjectInitiation::getProjectCode, query.getProjectCode())
                   .like(StringUtils.hasText(query.getProjectName()),
                            ProjectInitiation::getProjectName, query.getProjectName())
                   .eq(StringUtils.hasText(query.getStage()),
                            ProjectInitiation::getStage, query.getStage())
                   .eq(StringUtils.hasText(query.getStatus()),
                            ProjectInitiation::getStatus, query.getStatus())
                   .eq(StringUtils.hasText(query.getPmId()),
                            ProjectInitiation::getPmId, query.getPmId())
                   .eq(StringUtils.hasText(query.getCustomerId()),
                            ProjectInitiation::getCustomerId, query.getCustomerId())
                   .orderByDesc(ProjectInitiation::getCreatedAt);

            Page<ProjectInitiation> page = new Page<>(query.getPageNum(), query.getPageSize());
            IPage<ProjectInitiation> result = repository.page(page, wrapper);

            List<ProjectInitiationVO> voList = result.getRecords().stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());

            nameAssembler.enrich(voList,
                    ProjectInitiationVO::getPmId,
                    ProjectInitiationVO::setPmName, NameType.USER);

            Page<ProjectInitiationVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
            voPage.setRecords(voList);
            return voPage;
        } finally {
            projectMetrics.recordQueryDuration(System.currentTimeMillis() - start);
        }
    }

    /**
     * 新建立项
     *
     * <p>执行链路：
     * <ol>
     *   <li>默认 {@code status=DRAFT}（草稿）、{@code stage=PRE_INITIATION}（预立项）</li>
     *   <li>{@code projectLevel} 缺省 {@code "C"}（C 级项目）</li>
     *   <li>插入 {@code ydsz_project_initiation} 表</li>
     *   <li>触发 {@code PROJECT_INITIATION_CREATED} 领域事件（Outbox）</li>
     *   <li>同步到 ES 搜索索引</li>
     *   <li>埋点 Prometheus 指标（{@code incInitiationCreated}）</li>
     * </ol>
     *
     * <p><b>项目级别：</b>{@code A}（战略级）/ {@code B}（部门级）/ {@code C}（常规级），
     * 影响审批流和资源分配策略。
     *
     * @param entity 立项实体（不需携带 ID、status、stage，由本方法设置默认值）
     * @return 新创建的立项 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(ProjectInitiation entity) {
        entity.setStatus("DRAFT");
        entity.setStage("PRE_INITIATION");
        if (entity.getProjectLevel() == null) {
            entity.setProjectLevel("C");
        }
        repository.save(entity);
        projectMetrics.incInitiationCreated();
        publishEvent(StandardEventTypes.PROJECT_INITIATION_CREATED, entity.getId(), entity);
        indexUpsert(entity);
        return entity.getId();
    }

    /**
     * 更新立项
     *
     * <p>执行链路：
     * <ol>
     *   <li>更新 {@code ydsz_project_initiation} 表</li>
     *   <li>更新成功同步到 ES 搜索索引</li>
     *   <li>埋点 Prometheus 指标（{@code incInitiationUpdated}）</li>
     * </ol>
     *
     * <p><b>注意：</b>本方法<b>不</b>发布领域事件，事件由专门的阶段推进接口（{@link #advanceStage}）触发，
     * 避免普通字段更新触发「阶段变更」类误报。
     *
     * @param entity 立项实体（需携带 ID）
     * @return true=更新成功，false=记录不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectInitiation entity) {
        boolean result = repository.updateById(entity);
        if (result) {
            projectMetrics.incInitiationUpdated();
            indexUpsert(entity);
        }
        return result;
    }

    /**
     * 逻辑删除立项
     *
     * <p>采用<b>逻辑删除</b>，不真正从 DB 删除，便于审计回溯。
     *
     * <p><b>注意：</b>删除立项前应检查是否有关联的合同 / 预算 / 计划，调用方需自行校验。
     *
     * @param id 立项主键
     * @return true=删除成功，false=记录不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        boolean result = repository.removeById(id);
        if (result) {
            projectMetrics.incInitiationDeleted();
            indexDelete(id);
        }
        return result;
    }

    /**
     * 推进立项阶段
     *
     * <p>状态机：
     * <pre>
     *  PRE_INITIATION → INITIATION → CONTRACT → EXECUTION → CLOSURE
     * </pre>
     *
     * <p>执行链路：
     * <ol>
     *   <li>查询原实体</li>
     *   <li>更新 {@code stage}（必须）和 {@code currentGate}（可选）</li>
     *   <li>更新成功触发 {@code PROJECT_STAGE_CHANGED} 领域事件（Outbox）</li>
     *   <li>埋点 Prometheus 指标</li>
     * </ol>
     *
     * <p><b>阶段门（gate）含义：</b>每个阶段可能包含多个门（如 G1、G2、G3），
     * 阶段推进时同步更新当前所在门。
     *
     * @param id    立项主键
     * @param stage 新阶段（如 {@code "INITIATION"}）
     * @param gate  当前阶段门（如 {@code "G1"}），可选
     * @return true=推进成功，false=记录不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean advanceStage(String id, String stage, String gate) {
        ProjectInitiation entity = repository.getById(id);
        if (entity == null) {
            return false;
        }
        entity.setStage(stage);
        if (StringUtils.hasText(gate)) {
            entity.setCurrentGate(gate);
        }
        boolean result = repository.updateById(entity);
        if (result) {
            projectMetrics.incInitiationUpdated();
            publishEvent(StandardEventTypes.PROJECT_STAGE_CHANGED, entity.getId(), entity);
        }
        return result;
    }

    /**
     * 按项目经理查询所有立项
     *
     * <p>典型调用方：项目经理工作台「我负责的所有项目」列表。
     *
     * @param pmId 项目经理 ID（{@code ydsz_user.id}）
     * @return 该 PM 名下所有立项（按 createdAt 倒序），无数据返回空列表
     */
    @Override
    public List<ProjectInitiationVO> listByPmId(String pmId) {
        long start = System.currentTimeMillis();
        try {
            List<ProjectInitiation> list = repository.list(
                    new LambdaQueryWrapper<ProjectInitiation>()
                            .eq(ProjectInitiation::getPmId, pmId)
                            .orderByDesc(ProjectInitiation::getCreatedAt));
            return list.stream().map(this::convertToVO).collect(Collectors.toList());
        } finally {
            projectMetrics.recordQueryDuration(System.currentTimeMillis() - start);
        }
    }

    /**
     * 实体 → VO 转换（私有）
     *
     * @param entity 数据库实体
     * @return 视图对象
     */
    private ProjectInitiationVO convertToVO(ProjectInitiation entity) {
        return ProjectConverter.INSTANT.entityToVO(entity);
    }

    /**
     * 同步到 ES 搜索索引（私有，upsert 语义）
     *
     * @param entity 立项实体
     */
    private void indexUpsert(ProjectInitiation entity) {
        SearchIndexEventBridge bridge = searchIndexBridgeProvider.getIfAvailable();
        if (bridge != null) {
            bridge.indexUpsert("project", entity);
        }
    }

    /**
     * 从 ES 搜索索引删除（私有）
     *
     * @param id 立项主键
     */
    private void indexDelete(String id) {
        SearchIndexEventBridge bridge = searchIndexBridgeProvider.getIfAvailable();
        if (bridge != null) {
            bridge.indexDelete("project", id);
        }
    }

    /**
     * 发布领域事件到 Outbox（私有，可选依赖）
     *
     * <p>{@link OutboxService} 不存在时安全降级（仅记录 DEBUG 日志），不抛异常影响主流程。
     * Outbox 模式保证「业务事务与事件发布」的最终一致性，避免消息丢失。
     *
     * @param eventType   事件类型（{@link StandardEventTypes#PROJECT_INITIATION_CREATED} 等）
     * @param aggregateId 聚合根 ID
     * @param payload     事件负载对象
     */
    private void publishEvent(String eventType, String aggregateId, Object payload) {
        OutboxService outboxService = outboxServiceProvider.getIfAvailable();
        if (outboxService == null) {
            log.debug("OutboxService not available, skipping event: type={}, id={}", eventType, aggregateId);
            return;
        }
        try {
            outboxService.appendToOutbox(
                    "ProjectInitiation", aggregateId, eventType,
                    YdszJson.toJson(payload));
        } catch (Exception e) {
            log.warn("Failed to publish outbox event: type={}, id={}, error={}",
                    eventType, aggregateId, e.getMessage());
        }
    }
}
