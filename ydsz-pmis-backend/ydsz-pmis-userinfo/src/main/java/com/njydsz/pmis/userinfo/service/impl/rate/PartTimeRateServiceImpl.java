package com.njydsz.pmis.userinfo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.dto.rate.PartTimeRateCreateDTO;
import com.njydsz.pmis.userinfo.dto.rate.PartTimeRateUpdateDTO;
import com.njydsz.pmis.userinfo.entity.rate.PartTimeRateDO;
import com.njydsz.pmis.userinfo.mapper.rate.PartTimeRateMapper;
import com.njydsz.pmis.userinfo.service.rate.PartTimeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 兼职职级费率服务实现（P1-P18，时薪核算月薪+商业保险）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartTimeRateServiceImpl implements PartTimeRateService {

    /** 合法的级别段位 */
    private static final Set<String> VALID_SEGMENTS =
            Set.of("PRIMARY", "MIDDLE", "SENIOR", "EXPERT", "STRATEGIC");

    /** 默认状态 */
    private static final String DEFAULT_STATUS = "ACTIVE";

    /** 默认版本号 */
    private static final int DEFAULT_VERSION = 1;

    /** 默认月工时数（22天×8小时） */
    private static final BigDecimal DEFAULT_MONTHLY_HOURS = new BigDecimal("176");

    /** 兼职职级费率 Mapper */
    private final PartTimeRateMapper partTimeRateMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(PartTimeRateCreateDTO dto) {
        validateCreate(dto);
        PartTimeRateDO entity = new PartTimeRateDO();
        BeanUtils.copyProperties(dto, entity);
        // 兼职核心：月薪 = 时薪 × 月工时数
        BigDecimal hourlyRate = dto.getHourlyRate();
        BigDecimal monthlyHours = dto.getMonthlyHours() != null ? dto.getMonthlyHours() : DEFAULT_MONTHLY_HOURS;
        BigDecimal monthlySalary = hourlyRate.multiply(monthlyHours).setScale(2, RoundingMode.HALF_UP);
        entity.setHourlyRate(hourlyRate);
        entity.setMonthlyHours(monthlyHours);
        entity.setMonthlySalary(monthlySalary);
        // 自动计算 totalCost = monthlySalary + commercialInsurance + travelReimbursement + travelAllowance
        entity.setTotalCost(calculateTotalCost(monthlySalary, dto.getCommercialInsurance(),
                dto.getTravelReimbursement(), dto.getTravelAllowance()));
        if (entity.getStatus() == null) {
            entity.setStatus(DEFAULT_STATUS);
        }
        if (entity.getVersion() == null) {
            entity.setVersion(DEFAULT_VERSION);
        }
        partTimeRateMapper.insert(entity);
        log.info("[PartTimeRate] 创建兼职费率: code={} version={} hourlyRate={} monthlySalary={}",
                entity.getRateCode(), entity.getVersion(), hourlyRate, monthlySalary);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, PartTimeRateUpdateDTO dto) {
        if (id == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "兼职职级费率 ID 不能为空");
        }
        PartTimeRateDO exists = partTimeRateMapper.selectById(id);
        if (exists == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "兼职职级费率不存在: " + id);
        }
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "兼职职级费率参数不能为空");
        }
        if (dto.getMonthlySalary() != null && dto.getMonthlySalary().signum() <= 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "月度薪资必须大于 0");
        }
        if (dto.getHourlyRate() != null && dto.getHourlyRate().signum() <= 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "时薪必须大于 0");
        }
        if (StringUtils.hasText(dto.getLevelSegment()) && !VALID_SEGMENTS.contains(dto.getLevelSegment())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "级别段位非法: " + dto.getLevelSegment());
        }
        // 日期校验
        LocalDate effective = dto.getEffectiveDate() != null ? dto.getEffectiveDate() : exists.getEffectiveDate();
        LocalDate expire = dto.getExpireDate() != null ? dto.getExpireDate() : exists.getExpireDate();
        if (expire != null && expire.isBefore(effective)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "失效日期不能早于生效日期");
        }
        // rate_code + version 唯一性校验（排除自身）
        String code = dto.getRateCode() != null ? dto.getRateCode() : exists.getRateCode();
        Integer ver = dto.getVersion() != null ? dto.getVersion() : exists.getVersion();
        PartTimeRateDO dup = partTimeRateMapper.selectOne(new LambdaQueryWrapper<PartTimeRateDO>()
                .eq(PartTimeRateDO::getRateCode, code)
                .eq(PartTimeRateDO::getVersion, ver)
                .ne(PartTimeRateDO::getId, id));
        if (dup != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "兼职级别编码已存在: " + code);
        }
        PartTimeRateDO entity = new PartTimeRateDO();
        BeanUtils.copyProperties(dto, entity);
        entity.setId(id);
        // 兼职核心：如果时薪或月工时数有变更，重新推导月薪
        BigDecimal hourlyRate = dto.getHourlyRate() != null ? dto.getHourlyRate() : exists.getHourlyRate();
        BigDecimal monthlyHours = dto.getMonthlyHours() != null ? dto.getMonthlyHours() : exists.getMonthlyHours();
        if (monthlyHours == null) {
            monthlyHours = DEFAULT_MONTHLY_HOURS;
        }
        boolean rateChanged = dto.getHourlyRate() != null || dto.getMonthlyHours() != null;
        BigDecimal salary;
        if (rateChanged) {
            salary = hourlyRate.multiply(monthlyHours).setScale(2, RoundingMode.HALF_UP);
            entity.setHourlyRate(hourlyRate);
            entity.setMonthlyHours(monthlyHours);
            entity.setMonthlySalary(salary);
        } else {
            salary = dto.getMonthlySalary() != null ? dto.getMonthlySalary() : exists.getMonthlySalary();
        }
        // 重新计算 totalCost
        BigDecimal insurance = dto.getCommercialInsurance() != null ? dto.getCommercialInsurance() : exists.getCommercialInsurance();
        BigDecimal reimbursement = dto.getTravelReimbursement() != null ? dto.getTravelReimbursement() : exists.getTravelReimbursement();
        BigDecimal allowance = dto.getTravelAllowance() != null ? dto.getTravelAllowance() : exists.getTravelAllowance();
        entity.setTotalCost(calculateTotalCost(salary, insurance, reimbursement, allowance));
        partTimeRateMapper.updateById(entity);
        log.info("[PartTimeRate] 更新兼职费率: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        if (id == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "兼职职级费率 ID 不能为空");
        }
        if (partTimeRateMapper.selectById(id) == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "兼职职级费率不存在: " + id);
        }
        partTimeRateMapper.deleteById(id);
        log.info("[PartTimeRate] 删除兼职费率: id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public PartTimeRateDO getById(String id) {
        PartTimeRateDO rate = partTimeRateMapper.selectById(id);
        if (rate == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "兼职职级费率不存在: " + id);
        }
        return rate;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PartTimeRateDO> page(int page, int size, String keyword, String segment, String status) {
        Page<PartTimeRateDO> p = new Page<>(page, size);
        LambdaQueryWrapper<PartTimeRateDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(PartTimeRateDO::getRateCode, keyword)
                    .or().like(PartTimeRateDO::getRateName, keyword));
        }
        if (StringUtils.hasText(segment)) {
            w.eq(PartTimeRateDO::getLevelSegment, segment);
        }
        if (StringUtils.hasText(status)) {
            w.eq(PartTimeRateDO::getStatus, status);
        }
        w.orderByAsc(PartTimeRateDO::getSortOrder).orderByDesc(PartTimeRateDO::getId);
        return partTimeRateMapper.selectPage(p, w);
    }

    @Override
    @Transactional(readOnly = true)
    public PartTimeRateDO matchEffective(String rateCode, LocalDate date) {
        if (!StringUtils.hasText(rateCode)) {
            return null;
        }
        if (date == null) {
            date = LocalDate.now();
        }
        return partTimeRateMapper.selectEffective(rateCode, date);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartTimeRateDO> listEffective(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        return partTimeRateMapper.listEffective(date);
    }

    // ==================== private ====================

    /**
     * 计算公司总人力成本 = 月薪 + 商业保险 + 差旅报销 + 差旅补贴
     *
     * @param monthlySalary       月度薪资
     * @param commercialInsurance 商业保险（为 null 时按 0 处理）
     * @param travelReimbursement 差旅报销（为 null 时按 0 处理）
     * @param travelAllowance     差旅补贴（为 null 时按 0 处理）
     * @return 总成本（保留 2 位小数）
     */
    private BigDecimal calculateTotalCost(BigDecimal monthlySalary, BigDecimal commercialInsurance,
                                          BigDecimal travelReimbursement, BigDecimal travelAllowance) {
        if (monthlySalary == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "月度薪资不能为空");
        }
        BigDecimal insurance = commercialInsurance != null ? commercialInsurance : BigDecimal.ZERO;
        BigDecimal reimbursement = travelReimbursement != null ? travelReimbursement : BigDecimal.ZERO;
        BigDecimal allowance = travelAllowance != null ? travelAllowance : BigDecimal.ZERO;
        return monthlySalary.add(insurance).add(reimbursement).add(allowance).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 创建参数校验
     *
     * @param dto 创建参数
     */
    private void validateCreate(PartTimeRateCreateDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "兼职职级费率参数不能为空");
        }
        if (!StringUtils.hasText(dto.getRateCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "兼职级别编码不能为空");
        }
        if (!StringUtils.hasText(dto.getRateName())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "兼职级别名称不能为空");
        }
        if (dto.getMonthlySalary() == null || dto.getMonthlySalary().signum() <= 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "月度薪资必须大于 0");
        }
        if (dto.getHourlyRate() == null || dto.getHourlyRate().signum() <= 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "时薪必须大于 0");
        }
        validateSegment(dto.getLevelSegment());
        if (dto.getEffectiveDate() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "生效日期不能为空");
        }
        if (dto.getExpireDate() != null && dto.getExpireDate().isBefore(dto.getEffectiveDate())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "失效日期不能早于生效日期");
        }
        // rate_code + version 唯一性校验
        Integer version = dto.getVersion() != null ? dto.getVersion() : DEFAULT_VERSION;
        PartTimeRateDO dup = partTimeRateMapper.selectOne(new LambdaQueryWrapper<PartTimeRateDO>()
                .eq(PartTimeRateDO::getRateCode, dto.getRateCode())
                .eq(PartTimeRateDO::getVersion, version));
        if (dup != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "兼职级别编码已存在: " + dto.getRateCode());
        }
    }

    /**
     * 校验级别段位合法性
     *
     * @param segment 级别段位
     */
    private void validateSegment(String segment) {
        if (!StringUtils.hasText(segment) || !VALID_SEGMENTS.contains(segment)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "级别段位非法: " + segment);
        }
    }
}
