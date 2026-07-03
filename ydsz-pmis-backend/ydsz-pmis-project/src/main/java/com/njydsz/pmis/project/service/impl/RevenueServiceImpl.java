package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.RevenueCreateDTO;
import com.njydsz.pmis.project.entity.RevenueDO;
import com.njydsz.pmis.project.enums.RevenueRecognitionMethod;
import com.njydsz.pmis.project.mapper.RevenueMapper;
import com.njydsz.pmis.project.service.RevenueService;
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
 * 收入确认服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevenueServiceImpl implements RevenueService {

    private final RevenueMapper revenueMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RevenueCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        if (!StringUtils.hasText(dto.getRevenueCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_378203d4");
        }
        if (dto.getContractId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_af96cf73");
        }
        if (dto.getInitiationId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_576c2b5e");
        }
        if (dto.getAmount() == null || dto.getAmount().signum() <= 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_a853c0c6");
        }
        if (RevenueRecognitionMethod.fromCode(dto.getRecognitionMethod()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_9a58a1bc" + dto.getRecognitionMethod());
        }
        if (revenueMapper.selectByCode(dto.getRevenueCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "error.execution.msg_52c2d527" + dto.getRevenueCode());
        }
        RevenueDO r = new RevenueDO();
        BeanUtils.copyProperties(dto, r);
        if (!StringUtils.hasText(r.getStatus())) r.setStatus("DRAFT");
        if (r.getPercentComplete() == null) r.setPercentComplete(BigDecimal.ZERO);
        if (r.getTenantId() == null) r.setTenantId(1L);
        if (r.getProviderTraceId() == null) r.setProviderTraceId("");
        revenueMapper.insert(r);
        log.info("[Revenue] 创建收入确认: code={} amount={}", r.getRevenueCode(), r.getAmount());
        return r.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, Long confirmedBy) {
        RevenueDO r = getById(id);
        if (!"DRAFT".equals(r.getStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_0f0b1394");
        }
        revenueMapper.updateStatus(id, "CONFIRMED", confirmedBy);
        r.setConfirmedBy(confirmedBy);
        r.setConfirmedAt(LocalDateTime.now());
        revenueMapper.updateById(r);
        log.info("[Revenue] 确认收入: id={} amount={}", id, r.getAmount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverse(Long id) {
        RevenueDO r = getById(id);
        if (!"CONFIRMED".equals(r.getStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_1971a360");
        }
        revenueMapper.updateStatus(id, "REVERSED", null);
        log.info("[Revenue] 冲销收入: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        RevenueDO r = getById(id);
        if ("CONFIRMED".equals(r.getStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_6891a16a");
        }
        revenueMapper.deleteById(id);
    }

    @Override
    public RevenueDO getById(Long id) {
        RevenueDO r = revenueMapper.selectById(id);
        if (r == null) throw new BizException(BizErrorCode.NOT_FOUND, "error.execution.msg_4924d9b4");
        return r;
    }

    @Override
    public Page<RevenueDO> page(int page, int size, String keyword, String status,
                                 Long contractId, Long initiationId, String period) {
        Page<RevenueDO> p = new Page<>(page, size);
        LambdaQueryWrapper<RevenueDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(RevenueDO::getRevenueCode, keyword)
                    .or().like(RevenueDO::getMilestone, keyword));
        }
        if (StringUtils.hasText(status)) w.eq(RevenueDO::getStatus, status);
        if (contractId != null) w.eq(RevenueDO::getContractId, contractId);
        if (initiationId != null) w.eq(RevenueDO::getInitiationId, initiationId);
        if (StringUtils.hasText(period)) w.eq(RevenueDO::getPeriod, period);
        w.orderByDesc(RevenueDO::getRecognitionDate);
        return revenueMapper.selectPage(p, w);
    }

    @Override
    public List<RevenueDO> listByInitiation(Long initiationId) {
        return revenueMapper.selectByInitiation(initiationId);
    }

    @Override
    public List<Map<String, Object>> sumByContract(Long contractId) {
        if (contractId == null) return List.of();
        return revenueMapper.sumByContract(contractId);
    }

    @Override
    public List<Map<String, Object>> sumByPeriod(Long initiationId) {
        if (initiationId == null) return List.of();
        return revenueMapper.sumByPeriod(initiationId);
    }
}
