package com.njydsz.pmis.execution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.RiskCreateDTO;
import com.njydsz.pmis.execution.dto.RiskStatusDTO;
import com.njydsz.pmis.execution.engine.RiskScoreEvaluator;
import com.njydsz.pmis.execution.entity.RiskDO;
import com.njydsz.pmis.execution.enums.RiskLevel;
import com.njydsz.pmis.execution.enums.RiskStatus;
import com.njydsz.pmis.execution.mapper.RiskMapper;
import com.njydsz.pmis.execution.service.RiskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 项目风险服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskServiceImpl implements RiskService {

    private final RiskMapper riskMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RiskCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        if (!StringUtils.hasText(dto.getRiskCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "风险编号不能为空");
        }
        if (!StringUtils.hasText(dto.getRiskTitle())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "风险标题不能为空");
        }
        if (dto.getInitiationId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "项目 ID 不能为空");
        }
        if (dto.getOwnerId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "负责人 ID 不能为空");
        }
        if (riskMapper.selectByCode(dto.getRiskCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "风险编号已存在: " + dto.getRiskCode());
        }
        RiskDO r = new RiskDO();
        BeanUtils.copyProperties(dto, r);
        // 自动评估风险等级
        RiskLevel level = RiskScoreEvaluator.evaluate(dto.getProbability(), dto.getImpact());
        r.setRiskLevel(level.getCode());
        if (!StringUtils.hasText(r.getStatus())) r.setStatus(RiskStatus.OPEN.getCode());
        if (r.getTenantId() == null) r.setTenantId(1L);
        if (r.getProviderTraceId() == null) r.setProviderTraceId("");

        riskMapper.insert(r);
        log.info("[Risk] 登记风险: code={} title={} level={}",
                r.getRiskCode(), r.getRiskTitle(), r.getRiskLevel());
        return r.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(RiskStatusDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        RiskDO r = getById(dto.getId());
        RiskStatus from = RiskStatus.fromCode(r.getStatus());
        RiskStatus to = RiskStatus.fromCode(dto.getTargetStatus());
        if (to == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "未知状态: " + dto.getTargetStatus());
        }
        if (from == null || !from.canTransitTo(to)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "风险状态不允许迁移: " + (from == null ? "未知" : from.getDesc()) + " → " + to.getDesc());
        }
        riskMapper.updateStatus(dto.getId(), to.getCode());
        if (to == RiskStatus.OCCURRED) r.setOccurredAt(LocalDateTime.now());
        if (to == RiskStatus.CLOSED) r.setClosedAt(LocalDateTime.now());
        riskMapper.updateById(r);
        log.info("[Risk] 状态迁移: id={} {} -> {}", dto.getId(), from.getCode(), to.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        RiskDO r = getById(id);
        if (RiskStatus.fromCode(r.getStatus()) == RiskStatus.OCCURRED) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "已发生的风险不能删除");
        }
        riskMapper.deleteById(id);
    }

    @Override
    public RiskDO getById(Long id) {
        RiskDO r = riskMapper.selectById(id);
        if (r == null) throw new BizException(BizErrorCode.NOT_FOUND, "风险不存在");
        return r;
    }

    @Override
    public Page<RiskDO> page(int page, int size, String keyword, String status,
                             String riskLevel, Long initiationId) {
        Page<RiskDO> p = new Page<>(page, size);
        LambdaQueryWrapper<RiskDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(RiskDO::getRiskCode, keyword)
                    .or().like(RiskDO::getRiskTitle, keyword)
                    .or().like(RiskDO::getDescription, keyword));
        }
        if (StringUtils.hasText(status)) w.eq(RiskDO::getStatus, status);
        if (StringUtils.hasText(riskLevel)) w.eq(RiskDO::getRiskLevel, riskLevel);
        if (initiationId != null) w.eq(RiskDO::getInitiationId, initiationId);
        w.orderByDesc(RiskDO::getCreatedAt);
        return riskMapper.selectPage(p, w);
    }

    @Override
    public List<RiskDO> listByInitiation(Long initiationId) {
        if (initiationId == null) return List.of();
        return riskMapper.selectByInitiation(initiationId);
    }

    @Override
    public List<Map<String, Object>> aggregateByLevel(Long initiationId) {
        if (initiationId == null) return List.of();
        return riskMapper.aggregateByLevel(initiationId);
    }
}
