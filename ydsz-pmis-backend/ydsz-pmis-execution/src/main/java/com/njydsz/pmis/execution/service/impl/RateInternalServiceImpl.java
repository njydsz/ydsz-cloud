package com.njydsz.pmis.execution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.RateInternalCreateDTO;
import com.njydsz.pmis.execution.entity.RateInternalDO;
import com.njydsz.pmis.execution.mapper.RateInternalMapper;
import com.njydsz.pmis.execution.service.RateInternalService;
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
 * 内部结算费率服务实现
 *
 * <p>负责内部成本费率的创建、更新、匹配与分页查询。
 * matchEffective 优先匹配 (level+department)，其次回退到 (level)。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateInternalServiceImpl implements RateInternalService {

    private final RateInternalMapper rateMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RateInternalCreateDTO dto) {
        validate(dto);
        if (rateMapper.selectByCode(dto.getRateCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "费率编号已存在: " + dto.getRateCode());
        }
        RateInternalDO r = new RateInternalDO();
        BeanUtils.copyProperties(dto, r);
        if (!StringUtils.hasText(r.getStatus())) r.setStatus("ACTIVE");
        if (!StringUtils.hasText(r.getCurrency())) r.setCurrency("CNY");
        if (r.getCostAmount() == null) r.setCostAmount(BigDecimal.ZERO);
        if (r.getTenantId() == null) r.setTenantId(1L);
        if (r.getProviderTraceId() == null) r.setProviderTraceId("");
        rateMapper.insert(r);
        log.info("[RateInternal] 创建对内费率: code={} level={} cost={}",
                r.getRateCode(), r.getLevelCode(), r.getCostAmount());
        return r.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RateInternalCreateDTO dto) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "ID 不能为空");
        RateInternalDO r = rateMapper.selectById(id);
        if (r == null) throw new BizException(BizErrorCode.NOT_FOUND, "费率不存在");
        if (dto.getCostAmount() != null) r.setCostAmount(dto.getCostAmount());
        if (dto.getBillingUnit() != null) r.setBillingUnit(dto.getBillingUnit());
        if (dto.getCurrency() != null) r.setCurrency(dto.getCurrency());
        if (dto.getEffectiveDate() != null) r.setEffectiveDate(dto.getEffectiveDate());
        if (dto.getExpiryDate() != null) r.setExpiryDate(dto.getExpiryDate());
        if (dto.getStatus() != null) r.setStatus(dto.getStatus());
        if (dto.getRemark() != null) r.setRemark(dto.getRemark());
        if (dto.getDepartmentId() != null) r.setDepartmentId(dto.getDepartmentId());
        if (dto.getDepartmentName() != null) r.setDepartmentName(dto.getDepartmentName());
        rateMapper.updateById(r);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "ID 不能为空");
        rateMapper.deleteById(id);
    }

    @Override
    public RateInternalDO getById(Long id) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "ID 不能为空");
        RateInternalDO r = rateMapper.selectById(id);
        if (r == null) throw new BizException(BizErrorCode.NOT_FOUND, "费率不存在");
        return r;
    }

    @Override
    public RateInternalDO matchEffective(String levelCode, Long departmentId, LocalDate date) {
        if (!StringUtils.hasText(levelCode)) return null;
        if (date == null) date = LocalDate.now();
        return rateMapper.matchEffective(levelCode, departmentId, date);
    }

    @Override
    public List<RateInternalDO> listByLevelAndDept(String levelCode, Long departmentId) {
        if (!StringUtils.hasText(levelCode)) return List.of();
        return rateMapper.selectByLevelAndDept(levelCode, departmentId);
    }

    @Override
    public Page<RateInternalDO> page(int page, int size, String levelCode, Long departmentId, String status) {
        Page<RateInternalDO> p = new Page<>(page, size);
        LambdaQueryWrapper<RateInternalDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(levelCode)) w.eq(RateInternalDO::getLevelCode, levelCode);
        if (departmentId != null) w.eq(RateInternalDO::getDepartmentId, departmentId);
        if (StringUtils.hasText(status)) w.eq(RateInternalDO::getStatus, status);
        w.orderByAsc(RateInternalDO::getLevelCode);
        return rateMapper.selectPage(p, w);
    }

    private void validate(RateInternalCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        if (!StringUtils.hasText(dto.getRateCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "费率编号不能为空");
        }
        if (!StringUtils.hasText(dto.getLevelCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "职级不能为空");
        }
        if (!StringUtils.hasText(dto.getBillingUnit())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "计费单位不能为空");
        }
        if (dto.getCostAmount() == null || dto.getCostAmount().signum() < 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "成本金额不能为负");
        }
        if (dto.getEffectiveDate() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "生效日期不能为空");
        }
    }
}
