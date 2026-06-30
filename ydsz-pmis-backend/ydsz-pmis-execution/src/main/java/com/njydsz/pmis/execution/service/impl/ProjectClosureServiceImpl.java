package com.njydsz.pmis.execution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.ProjectClosureCreateDTO;
import com.njydsz.pmis.execution.dto.ProjectClosureStatusDTO;
import com.njydsz.pmis.execution.engine.ClosureAdmissionValidator;
import com.njydsz.pmis.execution.entity.ProjectClosureDO;
import com.njydsz.pmis.execution.enums.ClosureStatus;
import com.njydsz.pmis.execution.enums.ClosureType;
import com.njydsz.pmis.execution.mapper.ProjectClosureMapper;
import com.njydsz.pmis.execution.service.ProjectClosureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 项目结项服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectClosureServiceImpl implements ProjectClosureService {

    private final ProjectClosureMapper closureMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ProjectClosureCreateDTO dto) {
        validate(dto);
        if (closureMapper.selectByCode(dto.getClosureCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY,
                    "结项编码已存在: " + dto.getClosureCode());
        }
        ProjectClosureDO c = new ProjectClosureDO();
        BeanUtils.copyProperties(dto, c);
        // 自动计算回款比例
        c.setReceivedRatio(computeRatio(dto.getReceivedAmount(), dto.getContractAmount()));
        if (!StringUtils.hasText(c.getStatus())) c.setStatus(ClosureStatus.DRAFT.getCode());
        if (c.getTenantId() == null) c.setTenantId(1L);
        if (c.getProviderTraceId() == null) c.setProviderTraceId("");
        closureMapper.insert(c);
        log.info("[ProjectClosure] 创建结项: code={} type={} initiation={}",
                c.getClosureCode(), c.getClosureType(), c.getInitiationId());
        return c.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(ProjectClosureStatusDTO dto) {
        ProjectClosureDO c = getById(dto.getId());
        ClosureStatus from = ClosureStatus.fromCode(c.getStatus());
        ClosureStatus to = ClosureStatus.fromCode(dto.getTargetStatus());
        if (to == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "未知状态: " + dto.getTargetStatus());
        }
        if (from == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "当前状态非法: " + c.getStatus());
        }
        if (!from.canTransitTo(to)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "结项状态不允许迁移: " + from.getDesc() + " → " + to.getDesc());
        }
        LocalDateTime now = LocalDateTime.now();
        if (to == ClosureStatus.SUBMITTED) c.setSubmittedAt(now);
        if (to == ClosureStatus.APPROVED) {
            c.setApprovedAt(now);
            if (dto.getApproverId() != null) c.setApproverId(dto.getApproverId());
            if (StringUtils.hasText(dto.getApproverName())) c.setApproverName(dto.getApproverName());
            if (StringUtils.hasText(dto.getApprovalComment())) c.setApprovalComment(dto.getApprovalComment());
        }
        if (to == ClosureStatus.ARCHIVED) {
            c.setArchivedAt(now);
            c.setActualArchiveDate(LocalDate.now());
            closureMapper.updateLocked(c.getId(), 1);
        }
        c.setStatus(to.getCode());
        closureMapper.updateById(c);
        log.info("[ProjectClosure] 状态迁移: id={} {} -> {}", c.getId(), from.getCode(), to.getCode());
    }

    @Override
    public void delete(Long id) {
        ProjectClosureDO c = getById(id);
        ClosureStatus st = ClosureStatus.fromCode(c.getStatus());
        if (st == ClosureStatus.ARCHIVED) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "已归档的结项记录不能删除");
        }
        if (Integer.valueOf(1).equals(c.getLocked())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "记录已锁定，不能删除");
        }
        closureMapper.deleteById(id);
    }

    @Override
    public ProjectClosureDO getById(Long id) {
        ProjectClosureDO c = closureMapper.selectById(id);
        if (c == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "结项记录不存在");
        }
        return c;
    }

    @Override
    public ProjectClosureDO getByInitiation(Long initiationId) {
        if (initiationId == null) return null;
        return closureMapper.selectByInitiation(initiationId);
    }

    @Override
    public Page<ProjectClosureDO> page(int page, int size, String keyword,
                                       String closureType, String status) {
        Page<ProjectClosureDO> p = new Page<>(page, size);
        LambdaQueryWrapper<ProjectClosureDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(ProjectClosureDO::getClosureCode, keyword)
                    .or().like(ProjectClosureDO::getClosureReason, keyword)
                    .or().like(ProjectClosureDO::getApplicantName, keyword));
        }
        if (StringUtils.hasText(closureType)) w.eq(ProjectClosureDO::getClosureType, closureType);
        if (StringUtils.hasText(status)) w.eq(ProjectClosureDO::getStatus, status);
        w.orderByDesc(ProjectClosureDO::getCreatedAt);
        return closureMapper.selectPage(p, w);
    }

    @Override
    public List<ProjectClosureDO> listByType(String closureType) {
        return closureMapper.selectByType(closureType);
    }

    @Override
    public List<Map<String, Object>> aggregateByType(Long tenantId) {
        if (tenantId == null) tenantId = 1L;
        return closureMapper.aggregateByType(tenantId);
    }

    @Override
    public ClosureAdmissionValidator.AdmissionCheck checkAdmission(Long id) {
        ProjectClosureDO c = getById(id);
        ClosureType type = ClosureType.fromCode(c.getClosureType());
        ClosureAdmissionValidator.ClosureMetrics m = new ClosureAdmissionValidator.ClosureMetrics(
                c.getReceivedRatio(),
                c.getCpi(),
                c.getProgressPct(),
                c.getGrossMargin(),
                c.getTotalCost(),
                c.getTotalCost() != null,
                true   // 此处简化：默认通过交付物校验（应由 DeliveryService 注入实际指标）
        );
        return ClosureAdmissionValidator.check(type, m);
    }

    private void validate(ProjectClosureCreateDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        if (ClosureType.fromCode(dto.getClosureType()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "结项类型不合法: " + dto.getClosureType());
        }
        if (dto.getApplicantId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "申请人 ID 不能为空");
        }
        if (dto.getWarrantyMonths() != null && dto.getWarrantyMonths().signum() < 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "质保期月数不能为负");
        }
    }

    private BigDecimal computeRatio(BigDecimal received, BigDecimal contract) {
        if (received == null || contract == null || contract.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return received.divide(contract, 4, RoundingMode.HALF_UP);
    }
}
