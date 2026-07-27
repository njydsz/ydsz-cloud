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
import com.njydsz.project.domain.dto.ProjectInitiationDTO;
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
 * 项目立项 Service 实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectInitiationServiceImpl implements ProjectInitiationService {

    private final IProjectInitiationRepository repository;
    private final NameAssembler nameAssembler;
    private final ProjectMetrics projectMetrics;
    private final ObjectProvider<OutboxService> outboxServiceProvider;
    private final ObjectProvider<SearchIndexEventBridge> searchIndexBridgeProvider;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(ProjectInitiationDTO dto) {
        ProjectInitiation entity = ProjectConverter.INSTANT.dtoToEntity(dto);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectInitiationDTO dto) {
        ProjectInitiation entity = ProjectConverter.INSTANT.dtoToEntity(dto);
        boolean result = repository.updateById(entity);
        if (result) {
            projectMetrics.incInitiationUpdated();
            indexUpsert(entity);
        }
        return result;
    }

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

    private ProjectInitiationVO convertToVO(ProjectInitiation entity) {
        return ProjectConverter.INSTANT.entityToVO(entity);
    }

    /**
     * 发布领域事件到 Outbox（可选依赖，OutboxService 不存在时安全降级）。
     *
     * @param eventType   事件类型
     * @param aggregateId 聚合根 ID
     * @param payload     事件负载对象
     */
    private void indexUpsert(ProjectInitiation entity) {
        SearchIndexEventBridge bridge = searchIndexBridgeProvider.getIfAvailable();
        if (bridge != null) {
            bridge.indexUpsert("project", entity);
        }
    }

    private void indexDelete(String id) {
        SearchIndexEventBridge bridge = searchIndexBridgeProvider.getIfAvailable();
        if (bridge != null) {
            bridge.indexDelete("project", id);
        }
    }

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
