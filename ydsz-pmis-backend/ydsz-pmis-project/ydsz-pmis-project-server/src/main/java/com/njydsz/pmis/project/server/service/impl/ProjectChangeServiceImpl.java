package com.njydsz.pmis.project.server.service.impl;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.event.ProjectChangeExecutedEvent;
import com.njydsz.pmis.common.exception.SysException;
import com.njydsz.pmis.project.domain.dto.ProjectChangeCreateDTO;
import com.njydsz.pmis.project.domain.dto.ProjectChangeStatusDTO;
import com.njydsz.pmis.project.server.engine.ChangeImpactEvaluator;
import com.njydsz.pmis.project.domain.entity.ProjectChangeDO;
import com.njydsz.pmis.project.domain.enums.ChangeStatus;
import com.njydsz.pmis.project.domain.enums.ChangeType;
import com.njydsz.pmis.project.infra.mapper.ProjectChangeMapper;
import com.njydsz.pmis.project.server.service.ProjectChangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 项目变更服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectChangeServiceImpl implements ProjectChangeService {

    /** 项目变更 Mapper */
    private final ProjectChangeMapper changeMapper;
    /**
     * Spring 事件发布器, 用于变更执行后发布 ProjectChangeExecutedEvent
     * 通知 EVM 基线重算 / 资源重调度 / 通知中心等监听器
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 创建项目变更申请。
     * <p>处理流程：参数校验 → 变更编号唯一性预检 → 属性拷贝 →
     * 自动调用 {@link ChangeImpactEvaluator} 评估影响（风险等级/是否重大/利润影响） →
     * 按重大/非重大自动装配审批角色 → 默认状态 DRAFT → 持久化。</p>
     *
     * @param dto 变更创建参数
     * @return 变更记录 ID
     * @throws SysException 编号重复或参数非法时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(ProjectChangeCreateDTO dto) {
        validate(dto);
        if (changeMapper.selectByCode(dto.getChangeCode()) != null) {
            throw new SysException(StandardResultCode.DUPLICATE_KEY,
                    "error.project.msg_f3637e40", dto.getChangeCode());
        }
        ProjectChangeDO c = new ProjectChangeDO();
        BeanUtils.copyProperties(dto, c);
        // 自动影响评估
        ChangeImpactEvaluator.ImpactResult impact = ChangeImpactEvaluator.evaluate(dto);
        c.setRiskLevelAfter(impact.level().getCode());
        c.setMajorFlag(impact.major() ? 1 : 0);
        c.setProfitImpactPct(impact.profitImpactPct());
        if (impact.major()) {
            c.setApproverRoles("[\"GM\",\"CFO\"]");
        } else {
            c.setApproverRoles("[\"PMO\"]");
        }
        if (!StringUtils.hasText(c.getStatus())) c.setStatus(ChangeStatus.DRAFT.getCode());
        if (c.getTenantId() == null) c.setTenantId(TenantContext.getTenantId());
        if (c.getProviderTraceId() == null) c.setProviderTraceId("");
        changeMapper.insert(c);
        log.info("[ProjectChange] 创建变更: code={} type={} major={} level={}",
                c.getChangeCode(), c.getChangeType(), c.getMajorFlag(), c.getRiskLevelAfter());
        return c.getId();
    }

    /**
     * 项目变更状态迁移。
     * <p>校验 {@link ChangeStatus#canTransitTo}，按目标状态自动填充提交/审批/执行时间戳；
     * 迁移至 EXECUTING 或 EXECUTED 时发布 {@link ProjectChangeExecutedEvent}
     * 触发 EVM 基线重算等下游联动（事件发布失败不影响主流程）。</p>
     *
     * @param dto 状态迁移参数
     * @throws SysException 状态非法或迁移路径不允许时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(ProjectChangeStatusDTO dto) {
        ProjectChangeDO c = getById(dto.getId());
        ChangeStatus from = ChangeStatus.fromCode(c.getStatus());
        ChangeStatus to = ChangeStatus.fromCode(dto.getTargetStatus());
        if (to == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_7bc741c6", dto.getTargetStatus());
        }
        if (from == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_2e33226a", c.getStatus());
        }
        if (!from.canTransitTo(to)) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.project.msg_0c941160", from.getDesc(), to.getDesc());
        }
        LocalDateTime now = LocalDateTime.now();
        if (to == ChangeStatus.SUBMITTED) c.setSubmittedAt(now);
        if (to == ChangeStatus.APPROVED) c.setApprovedAt(now);
        if (to == ChangeStatus.EXECUTED) c.setExecutedAt(now);
        changeMapper.updateStatus(c.getId(), to.getCode());
        changeMapper.updateById(c);
        log.info("[ProjectChange] 状态迁移: id={} {} -> {}", c.getId(), from.getCode(), to.getCode());

        // 变更执行/闭环: 触发 EVM 基线重算 等下游联动
        // EXECUTING 触发表明变更已落地, 旧基线需要刷新
        // EXECUTED 为终态闭环, 进一步触发收尾
        if (to == ChangeStatus.EXECUTING || to == ChangeStatus.EXECUTED) {
            publishExecutedEvent(c, to);
        }
    }

    /**
     * 删除变更申请，仅 DRAFT/REJECTED/CANCELLED 状态允许删除。
     *
     * @param id 变更 ID
     * @throws SysException 变更不存在或当前状态不允许删除时抛出
     */
    @Override
    public void delete(String id) {
        ProjectChangeDO c = getById(id);
        ChangeStatus st = ChangeStatus.fromCode(c.getStatus());
        if (st != ChangeStatus.DRAFT && st != ChangeStatus.REJECTED && st != ChangeStatus.CANCELLED) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_3a1a0d4b", st.getDesc());
        }
        changeMapper.deleteById(id);
        log.info("[ProjectChange] 删除变更: id={}", id);
    }

    /**
     * 根据主键查询变更详情。
     *
     * @param id 变更 ID
     * @return 变更实体
     * @throws SysException 变更不存在时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public ProjectChangeDO getById(String id) {
        ProjectChangeDO c = changeMapper.selectById(id);
        if (c == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.project.msg_2cfba1ec");
        }
        return c;
    }

    /**
     * 分页查询项目变更，支持关键词、变更类型、状态、立项 ID 过滤。
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词（编号/标题/原因），可空
     * @param changeType   变更类型，可空
     * @param status       状态码，可空
     * @param initiationId 立项 ID，可空
     * @return 分页结果
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ProjectChangeDO> page(int page, int size, String keyword,
                                      String changeType, String status, String initiationId) {
        Page<ProjectChangeDO> p = new Page<>(page, size);
        LambdaQueryWrapper<ProjectChangeDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(ProjectChangeDO::getChangeCode, keyword)
                    .or().like(ProjectChangeDO::getChangeTitle, keyword)
                    .or().like(ProjectChangeDO::getChangeReason, keyword));
        }
        if (StringUtils.hasText(changeType)) w.eq(ProjectChangeDO::getChangeType, changeType);
        if (StringUtils.hasText(status)) w.eq(ProjectChangeDO::getStatus, status);
        if (initiationId != null) w.eq(ProjectChangeDO::getInitiationId, initiationId);
        w.orderByDesc(ProjectChangeDO::getCreatedAt);
        return changeMapper.selectPage(p, w);
    }

    /**
     * 按立项查询变更记录列表。
     *
     * @param initiationId 立项 ID
     * @return 变更记录列表，立项 ID 为空时返回空列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProjectChangeDO> listByInitiation(String initiationId) {
        if (initiationId == null) return List.of();
        return changeMapper.selectByInitiation(initiationId);
    }

    /**
     * 按变更类型聚合计数（租户维度）。
     *
     * @param tenantId 租户 ID，可空（默认 "1"）
     * @return 每种变更类型对应的数量列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aggregateByType(String tenantId) {
        if (tenantId == null) tenantId = "1";
        return changeMapper.aggregateByType(tenantId);
    }

    /**
     * 按状态聚合计数（租户维度）。
     *
     * @param tenantId 租户 ID，可空（默认 "1"）
     * @return 每种状态对应的数量列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aggregateByStatus(String tenantId) {
        if (tenantId == null) tenantId = "1";
        return changeMapper.aggregateByStatus(tenantId);
    }

    /**
     * 统计某立项下的重大变更数量。
     *
     * @param initiationId 立项 ID
     * @return 重大变更数量，立项 ID 为空时返回 0
     */
    @Override
    @Transactional(readOnly = true)
    public Integer countMajorByInitiation(String initiationId) {
        if (initiationId == null) return 0;
        return changeMapper.countMajorByInitiation(initiationId);
    }

    /**
     * 校验变更创建参数：变更类型合法、申请人必填、进度影响天数合理。
     *
     * @param dto 变更创建参数
     * @throws SysException 参数非法时抛出
     */
    private void validate(ProjectChangeCreateDTO dto) {
        if (dto == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_d9712a58");
        }
        if (ChangeType.fromCode(dto.getChangeType()) == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_7d505699", dto.getChangeType());
        }
        if (dto.getApplicantId() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_98bc5a1a");
        }
        if (dto.getScheduleImpactDays() != null && dto.getScheduleImpactDays() < -3650) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_40763f49");
        }
    }

    /**
     * 发布变更执行事件。
     * <p>事件发布失败会被捕获并降级为 warn 日志，不影响主业务流；
     * eventPublisher 为 null 时跳过（单测场景）。</p>
     *
     * @param c           变更实体
     * @param finalStatus 最终状态码
     */
    private void publishExecutedEvent(ProjectChangeDO c, ChangeStatus finalStatus) {
        if (eventPublisher == null) {
            return; // 单测场景
        }
        try {
            ProjectChangeExecutedEvent event = ProjectChangeExecutedEvent.builder()
                    .changeId(c.getId())
                    .changeCode(c.getChangeCode())
                    .changeTitle(c.getChangeTitle())
                    .initiationId(c.getInitiationId())
                    .changeType(c.getChangeType())
                    .majorFlag(c.getMajorFlag() != null && c.getMajorFlag() == 1)
                    .finalStatusCode(finalStatus == null ? null : finalStatus.getCode())
                    .profitImpactPct(c.getProfitImpactPct())
                    .scheduleImpactDays(c.getScheduleImpactDays())
                    .timestamp(System.currentTimeMillis())
                    .build();
            eventPublisher.publishEvent(event);
            log.info("[ProjectChange] 发布执行事件: change={} status={} initiation={}",
                    c.getChangeCode(), finalStatus, c.getInitiationId());
        } catch (Exception e) {
            // 事件发布失败不影响主业务流
            log.warn("[ProjectChange] 事件发布失败: {}", e.getMessage());
        }
    }
}
