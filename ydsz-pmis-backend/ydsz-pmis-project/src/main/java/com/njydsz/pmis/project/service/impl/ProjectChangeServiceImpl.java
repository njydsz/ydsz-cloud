package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.event.ProjectChangeExecutedEvent;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.ProjectChangeCreateDTO;
import com.njydsz.pmis.project.dto.ProjectChangeStatusDTO;
import com.njydsz.pmis.project.engine.ChangeImpactEvaluator;
import com.njydsz.pmis.project.entity.ProjectChangeDO;
import com.njydsz.pmis.project.enums.ChangeStatus;
import com.njydsz.pmis.project.enums.ChangeType;
import com.njydsz.pmis.project.enums.RiskLevel;
import com.njydsz.pmis.project.mapper.ProjectChangeMapper;
import com.njydsz.pmis.project.service.ProjectChangeService;
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

    private final ProjectChangeMapper changeMapper;
    /**
     * Spring 事件发布器, 用于变更执行后发布 ProjectChangeExecutedEvent
     * 通知 EVM 基线重算 / 资源重调度 / 通知中心等监听器
     */
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ProjectChangeCreateDTO dto) {
        validate(dto);
        if (changeMapper.selectByCode(dto.getChangeCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY,
                    "变更编号已存在: " + dto.getChangeCode());
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
        if (c.getTenantId() == null) c.setTenantId(1L);
        if (c.getProviderTraceId() == null) c.setProviderTraceId("");
        changeMapper.insert(c);
        log.info("[ProjectChange] 创建变更: code={} type={} major={} level={}",
                c.getChangeCode(), c.getChangeType(), c.getMajorFlag(), c.getRiskLevelAfter());
        return c.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(ProjectChangeStatusDTO dto) {
        ProjectChangeDO c = getById(dto.getId());
        ChangeStatus from = ChangeStatus.fromCode(c.getStatus());
        ChangeStatus to = ChangeStatus.fromCode(dto.getTargetStatus());
        if (to == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "未知状态: " + dto.getTargetStatus());
        }
        if (from == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "当前状态非法: " + c.getStatus());
        }
        if (!from.canTransitTo(to)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "变更状态不允许迁移: " + from.getDesc() + " → " + to.getDesc());
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

    @Override
    public void delete(Long id) {
        ProjectChangeDO c = getById(id);
        ChangeStatus st = ChangeStatus.fromCode(c.getStatus());
        if (st != ChangeStatus.DRAFT && st != ChangeStatus.REJECTED && st != ChangeStatus.CANCELLED) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "当前状态不允许删除: " + st.getDesc());
        }
        changeMapper.deleteById(id);
        log.info("[ProjectChange] 删除变更: id={}", id);
    }

    @Override
    public ProjectChangeDO getById(Long id) {
        ProjectChangeDO c = changeMapper.selectById(id);
        if (c == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "项目变更不存在");
        }
        return c;
    }

    @Override
    public Page<ProjectChangeDO> page(int page, int size, String keyword,
                                      String changeType, String status, Long initiationId) {
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

    @Override
    public List<ProjectChangeDO> listByInitiation(Long initiationId) {
        if (initiationId == null) return List.of();
        return changeMapper.selectByInitiation(initiationId);
    }

    @Override
    public List<Map<String, Object>> aggregateByType(Long tenantId) {
        if (tenantId == null) tenantId = 1L;
        return changeMapper.aggregateByType(tenantId);
    }

    @Override
    public List<Map<String, Object>> aggregateByStatus(Long tenantId) {
        if (tenantId == null) tenantId = 1L;
        return changeMapper.aggregateByStatus(tenantId);
    }

    @Override
    public long countMajorByInitiation(Long initiationId) {
        if (initiationId == null) return 0L;
        return changeMapper.countMajorByInitiation(initiationId);
    }

    private void validate(ProjectChangeCreateDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        if (ChangeType.fromCode(dto.getChangeType()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "变更类型不合法: " + dto.getChangeType());
        }
        if (dto.getApplicantId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "申请人 ID 不能为空");
        }
        if (dto.getScheduleImpactDays() != null && dto.getScheduleImpactDays() < -3650) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "进度影响天数超出合理范围");
        }
    }

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
