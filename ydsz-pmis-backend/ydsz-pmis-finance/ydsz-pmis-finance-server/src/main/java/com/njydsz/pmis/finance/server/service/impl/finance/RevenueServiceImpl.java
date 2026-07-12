package com.njydsz.pmis.finance.server.service.impl.finance;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.finance.domain.dto.RevenueCreateDTO;
import com.njydsz.pmis.finance.domain.entity.RevenueDO;
import com.njydsz.pmis.finance.domain.enums.RevenueRecognitionMethod;
import com.njydsz.pmis.finance.infra.mapper.RevenueMapper;
import com.njydsz.pmis.finance.server.service.finance.RevenueService;
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

    /** 收入确认 Mapper */
    private final RevenueMapper revenueMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(RevenueCreateDTO dto) {
        if (dto == null) throw new BizException(StandardResultCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        if (!StringUtils.hasText(dto.getRevenueCode())) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.execution.msg_378203d4");
        }
        if (dto.getContractId() == null) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.execution.msg_af96cf73");
        }
        if (dto.getInitiationId() == null) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.execution.msg_576c2b5e");
        }
        if (dto.getAmount() == null || dto.getAmount().signum() <= 0) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.execution.msg_a853c0c6");
        }
        if (RevenueRecognitionMethod.fromCode(dto.getRecognitionMethod()) == null) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.execution.msg_9a58a1bc", dto.getRecognitionMethod());
        }
        if (revenueMapper.selectByCode(dto.getRevenueCode()) != null) {
            throw new BizException(StandardResultCode.DUPLICATE_KEY, "error.execution.msg_52c2d527", dto.getRevenueCode());
        }
        RevenueDO r = new RevenueDO();
        BeanUtils.copyProperties(dto, r);
        if (!StringUtils.hasText(r.getStatus())) r.setStatus("DRAFT");
        if (r.getPercentComplete() == null) r.setPercentComplete(BigDecimal.ZERO);
        if (r.getTenantId() == null) r.setTenantId(TenantContext.getTenantId());
        if (r.getProviderTraceId() == null) r.setProviderTraceId("");
        revenueMapper.insert(r);
        log.info("[Revenue] 创建收入确认: code={} amount={}", r.getRevenueCode(), r.getAmount());
        return r.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(String id, String confirmedBy) {
        RevenueDO r = getById(id);
        if (!"DRAFT".equals(r.getStatus())) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.execution.msg_0f0b1394");
        }
        revenueMapper.updateStatus(id, "CONFIRMED", confirmedBy);
        r.setConfirmedBy(confirmedBy);
        r.setConfirmedAt(LocalDateTime.now());
        revenueMapper.updateById(r);
        log.info("[Revenue] 确认收入: id={} amount={}", id, r.getAmount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverse(String id) {
        RevenueDO r = getById(id);
        if (!"CONFIRMED".equals(r.getStatus())) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.execution.msg_1971a360");
        }
        revenueMapper.updateStatus(id, "REVERSED", null);
        log.info("[Revenue] 冲销收入: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        RevenueDO r = getById(id);
        if ("CONFIRMED".equals(r.getStatus())) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.execution.msg_6891a16a");
        }
        revenueMapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public RevenueDO getById(String id) {
        RevenueDO r = revenueMapper.selectById(id);
        if (r == null) throw new BizException(StandardResultCode.NOT_FOUND, "error.execution.msg_4924d9b4");
        return r;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RevenueDO> page(int page, int size, String keyword, String status,
                                 String contractId, String initiationId, String period) {
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
    @Transactional(readOnly = true)
    public List<RevenueDO> listByInitiation(String initiationId) {
        return revenueMapper.selectByInitiation(initiationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> sumByContract(String contractId) {
        if (contractId == null) return List.of();
        return revenueMapper.sumByContract(contractId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> sumByPeriod(String initiationId) {
        if (initiationId == null) return List.of();
        return revenueMapper.sumByPeriod(initiationId);
    }
}
