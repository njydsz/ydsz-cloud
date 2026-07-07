package com.njydsz.pmis.project.service.impl;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.RateCardCreateDTO;
import com.njydsz.pmis.project.entity.RateCardDO;
import com.njydsz.pmis.project.mapper.RateCardMapper;
import com.njydsz.pmis.project.service.RateCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 报价费率卡服务实现
 *
 * <p>负责对外报价费率的创建、更新、匹配与分页查询。
 * matchEffective 采用三级回退：(level+project+customer) > (level+project) > (level)。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateCardServiceImpl implements RateCardService {

    private final RateCardMapper rateCardMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(RateCardCreateDTO dto) {
        validate(dto);
        if (rateCardMapper.selectByCode(dto.getRateCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "error.execution.msg_f713b711", dto.getRateCode());
        }
        RateCardDO r = new RateCardDO();
        BeanUtils.copyProperties(dto, r);
        if (!StringUtils.hasText(r.getStatus())) r.setStatus("ACTIVE");
        if (!StringUtils.hasText(r.getCurrency())) r.setCurrency("CNY");
        if (r.getRateAmount() == null) r.setRateAmount(BigDecimal.ZERO);
        if (r.getTenantId() == null) r.setTenantId(TenantContext.getTenantId());
        if (r.getProviderTraceId() == null) r.setProviderTraceId("");
        rateCardMapper.insert(r);
        log.info("[RateCard] 创建报价费率: code={} level={} amount={}",
                r.getRateCode(), r.getLevelCode(), r.getRateAmount());
        return r.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, RateCardCreateDTO dto) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_411b6827");
        RateCardDO r = rateCardMapper.selectById(id);
        if (r == null) throw new BizException(BizErrorCode.NOT_FOUND, "error.execution.msg_c90e3407");
        if (dto.getRateAmount() != null) r.setRateAmount(dto.getRateAmount());
        if (dto.getBillingUnit() != null) r.setBillingUnit(dto.getBillingUnit());
        if (dto.getCurrency() != null) r.setCurrency(dto.getCurrency());
        if (dto.getEffectiveDate() != null) r.setEffectiveDate(dto.getEffectiveDate());
        if (dto.getExpiryDate() != null) r.setExpiryDate(dto.getExpiryDate());
        if (dto.getStatus() != null) r.setStatus(dto.getStatus());
        if (dto.getRemark() != null) r.setRemark(dto.getRemark());
        if (dto.getProjectType() != null) r.setProjectType(dto.getProjectType());
        if (dto.getCustomerLevel() != null) r.setCustomerLevel(dto.getCustomerLevel());
        rateCardMapper.updateById(r);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_411b6827");
        rateCardMapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public RateCardDO getById(String id) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_411b6827");
        RateCardDO r = rateCardMapper.selectById(id);
        if (r == null) throw new BizException(BizErrorCode.NOT_FOUND, "error.execution.msg_c90e3407");
        return r;
    }

    @Override
    @Transactional(readOnly = true)
    public RateCardDO matchEffective(String levelCode, String projectType, String customerLevel, LocalDate date) {
        if (!StringUtils.hasText(levelCode)) return null;
        if (date == null) date = LocalDate.now();
        return rateCardMapper.matchEffective(levelCode, projectType, customerLevel, date);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RateCardDO> listByLevel(String levelCode) {
        if (!StringUtils.hasText(levelCode)) return List.of();
        return rateCardMapper.selectByLevel(levelCode);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RateCardDO> page(int page, int size, String levelCode, String status) {
        Page<RateCardDO> p = new Page<>(page, size);
        LambdaQueryWrapper<RateCardDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(levelCode)) w.eq(RateCardDO::getLevelCode, levelCode);
        if (StringUtils.hasText(status)) w.eq(RateCardDO::getStatus, status);
        w.orderByAsc(RateCardDO::getLevelCode);
        return rateCardMapper.selectPage(p, w);
    }

    private void validate(RateCardCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        if (!StringUtils.hasText(dto.getRateCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_3fbd3c07");
        }
        if (!StringUtils.hasText(dto.getLevelCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_11653d4c");
        }
        if (!StringUtils.hasText(dto.getBillingUnit())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_8e68458a");
        }
        if (dto.getRateAmount() == null || dto.getRateAmount().signum() < 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_4c1c6ba9");
        }
        if (dto.getEffectiveDate() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_c10e0b62");
        }
    }
}
