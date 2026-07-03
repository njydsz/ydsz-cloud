package com.njydsz.pmis.project.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.ProfitSnapshotDTO;
import com.njydsz.pmis.project.engine.ProfitCalculator;
import com.njydsz.pmis.project.entity.ProfitSnapshotDO;
import com.njydsz.pmis.project.mapper.ProfitSnapshotMapper;
import com.njydsz.pmis.project.service.ProfitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 利润核算服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfitServiceImpl implements ProfitService {

    private final ProfitSnapshotMapper snapshotMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateSnapshot(ProfitSnapshotDTO dto) {
        if (dto == null || dto.getInitiationId() == null
                || !StringUtils.hasText(dto.getPeriod())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_99e1d335");
        }
        ProfitSnapshotDO snap = snapshotMapper.selectByInitiationAndPeriod(
                dto.getInitiationId(), dto.getPeriod());
        if (snap == null) {
            snap = new ProfitSnapshotDO();
            snap.setInitiationId(dto.getInitiationId());
            snap.setPeriod(dto.getPeriod());
        }
        BeanUtils.copyProperties(dto, snap, "id", "initiationId", "period");
        snap.setSnapshotAt(LocalDateTime.now());
        if (snap.getTenantId() == null) snap.setTenantId(1L);
        if (snap.getProviderTraceId() == null) snap.setProviderTraceId("");

        // 派生计算
        ProfitCalculator.fillDerived(snap);

        if (snap.getId() == null) {
            snapshotMapper.insert(snap);
        } else {
            snapshotMapper.updateById(snap);
        }
        log.info("[Profit] 利润快照: initiation={} period={} totalCost={} grossMargin={}",
                snap.getInitiationId(), snap.getPeriod(), snap.getTotalCost(), snap.getGrossMargin());
        return snap.getId();
    }

    @Override
    public ProfitSnapshotDO getByInitiationAndPeriod(Long initiationId, String period) {
        return snapshotMapper.selectByInitiationAndPeriod(initiationId, period);
    }

    @Override
    public List<ProfitSnapshotDO> listByInitiation(Long initiationId) {
        if (initiationId == null) return List.of();
        return snapshotMapper.selectByInitiation(initiationId);
    }

    @Override
    public List<Map<String, Object>> trendByPeriod(Long initiationId) {
        if (initiationId == null) return List.of();
        return snapshotMapper.trendByPeriod(initiationId);
    }

    @Override
    public int healthScore(Long initiationId, String period) {
        ProfitSnapshotDO s = snapshotMapper.selectByInitiationAndPeriod(initiationId, period);
        if (s == null) return -1;
        return ProfitCalculator.healthScore(
                s.getGrossMargin(), new BigDecimal("100"), s.getProgressPct(),
                s.getRecognizedRevenue(), s.getTotalCost());
    }
}
