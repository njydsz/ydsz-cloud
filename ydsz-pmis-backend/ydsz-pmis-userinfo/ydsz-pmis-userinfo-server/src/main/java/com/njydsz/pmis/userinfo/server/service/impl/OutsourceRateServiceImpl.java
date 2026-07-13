package com.njydsz.pmis.userinfo.server.service.impl.rate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.userinfo.domain.dto.rate.OutsourceRateCreateDTO;
import com.njydsz.pmis.userinfo.domain.dto.rate.OutsourceRateUpdateDTO;
import com.njydsz.pmis.userinfo.domain.entity.rate.OutsourceRateDO;
import com.njydsz.pmis.userinfo.infra.mapper.rate.OutsourceRateMapper;
import com.njydsz.pmis.userinfo.server.service.rate.OutsourceRateService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 外包职级费率服务实现（V1-V18，人天核算月薪+差旅报销+差旅补贴）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutsourceRateServiceImpl implements OutsourceRateService {

    /** 合法的级别段位 */
    private static final Set<String> VALID_SEGMENTS =
            Set.of("PRIMARY", "MIDDLE", "SENIOR", "EXPERT", "STRATEGIC");

    /** 默认状态 */
    private static final String DEFAULT_STATUS = "ACTIVE";

    /** 默认版本号 */
    private static final int DEFAULT_VERSION = 1;

    /** 默认月工作天数 */
    private static final BigDecimal DEFAULT_MONTHLY_DAYS = new BigDecimal("22");

    /** 外包职级费率 Mapper */
    private final OutsourceRateMapper outsourceRateMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(OutsourceRateCreateDTO dto) {
        validateCreate(dto);
        OutsourceRateDO entity = new OutsourceRateDO();
        BeanUtils.copyProperties(dto, entity);
        // 外包核心：月薪 = 人天单价 × 月工作天数
        BigDecimal dailyRate = dto.getDailyRate();
        BigDecimal monthlyDays = dto.getMonthlyDays() != null ? dto.getMonthlyDays() : DEFAULT_MONTHLY_DAYS;
        BigDecimal monthlySalary = dailyRate.multiply(monthlyDays).setScale(2, RoundingMode.HALF_UP);
        entity.setDailyRate(dailyRate);
        entity.setMonthlyDays(monthlyDays);
        entity.setMonthlySalary(monthlySalary);
        // 自动计算 totalCost = monthlySalary + travelReimbursement + travelAllowance
        entity.setTotalCost(calculateTotalCost(monthlySalary, dto.getTravelReimbursement(), dto.getTravelAllowance()));
        if (entity.getStatus() == null) {
            entity.setStatus(DEFAULT_STATUS);
        }
        if (entity.getVersion() == null) {
            entity.setVersion(DEFAULT_VERSION);
        }
        outsourceRateMapper.insert(entity);
        log.info("[OutsourceRate] 创建外包费率: code={} version={} dailyRate={} monthlySalary={}",
                entity.getRateCode(), entity.getVersion(), dailyRate, monthlySalary);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, OutsourceRateUpdateDTO dto) {
        if (id == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "外包职级费率 ID 不能为空");
        }
        OutsourceRateDO exists = outsourceRateMapper.selectById(id);
        if (exists == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "外包职级费率不存在: " + id);
        }
        if (dto == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "外包职级费率参数不能为空");
        }
        if (dto.getMonthlySalary() != null && dto.getMonthlySalary().signum() <= 0) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "月度薪资必须大于 0");
        }
        if (dto.getDailyRate() != null && dto.getDailyRate().signum() <= 0) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "人天单价必须大于 0");
        }
        if (StringUtils.hasText(dto.getLevelSegment()) && !VALID_SEGMENTS.contains(dto.getLevelSegment())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "级别段位非法: " + dto.getLevelSegment());
        }
        // 日期校验
        LocalDate effective = dto.getEffectiveDate() != null ? dto.getEffectiveDate() : exists.getEffectiveDate();
        LocalDate expire = dto.getExpireDate() != null ? dto.getExpireDate() : exists.getExpireDate();
        if (expire != null && expire.isBefore(effective)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "失效日期不能早于生效日期");
        }
        // rate_code + version 唯一性校验（排除自身）
        String code = dto.getRateCode() != null ? dto.getRateCode() : exists.getRateCode();
        Integer ver = dto.getVersion() != null ? dto.getVersion() : exists.getVersion();
        OutsourceRateDO dup = outsourceRateMapper.selectOne(new LambdaQueryWrapper<OutsourceRateDO>()
                .eq(OutsourceRateDO::getRateCode, code)
                .eq(OutsourceRateDO::getVersion, ver)
                .ne(OutsourceRateDO::getId, id));
        if (dup != null) {
            throw new SysException(StandardResultCode.DUPLICATE_KEY, "外包级别编码已存在: " + code);
        }
        OutsourceRateDO entity = new OutsourceRateDO();
        BeanUtils.copyProperties(dto, entity);
        entity.setId(id);
        // 外包核心：如果人天单价或月工作天数有变更，重新推导月薪
        BigDecimal dailyRate = dto.getDailyRate() != null ? dto.getDailyRate() : exists.getDailyRate();
        BigDecimal monthlyDays = dto.getMonthlyDays() != null ? dto.getMonthlyDays() : exists.getMonthlyDays();
        if (monthlyDays == null) {
            monthlyDays = DEFAULT_MONTHLY_DAYS;
        }
        boolean rateChanged = dto.getDailyRate() != null || dto.getMonthlyDays() != null;
        BigDecimal salary;
        if (rateChanged) {
            salary = dailyRate.multiply(monthlyDays).setScale(2, RoundingMode.HALF_UP);
            entity.setDailyRate(dailyRate);
            entity.setMonthlyDays(monthlyDays);
            entity.setMonthlySalary(salary);
        } else {
            salary = dto.getMonthlySalary() != null ? dto.getMonthlySalary() : exists.getMonthlySalary();
        }
        // 重新计算 totalCost
        BigDecimal reimbursement = dto.getTravelReimbursement() != null ? dto.getTravelReimbursement() : exists.getTravelReimbursement();
        BigDecimal allowance = dto.getTravelAllowance() != null ? dto.getTravelAllowance() : exists.getTravelAllowance();
        entity.setTotalCost(calculateTotalCost(salary, reimbursement, allowance));
        outsourceRateMapper.updateById(entity);
        log.info("[OutsourceRate] 更新外包费率: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        if (id == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "外包职级费率 ID 不能为空");
        }
        if (outsourceRateMapper.selectById(id) == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "外包职级费率不存在: " + id);
        }
        outsourceRateMapper.deleteById(id);
        log.info("[OutsourceRate] 删除外包费率: id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public OutsourceRateDO getById(String id) {
        OutsourceRateDO rate = outsourceRateMapper.selectById(id);
        if (rate == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "外包职级费率不存在: " + id);
        }
        return rate;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OutsourceRateDO> page(int page, int size, String keyword, String segment, String status) {
        Page<OutsourceRateDO> p = new Page<>(page, size);
        LambdaQueryWrapper<OutsourceRateDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(OutsourceRateDO::getRateCode, keyword)
                    .or().like(OutsourceRateDO::getRateName, keyword));
        }
        if (StringUtils.hasText(segment)) {
            w.eq(OutsourceRateDO::getLevelSegment, segment);
        }
        if (StringUtils.hasText(status)) {
            w.eq(OutsourceRateDO::getStatus, status);
        }
        w.orderByAsc(OutsourceRateDO::getSortOrder).orderByDesc(OutsourceRateDO::getId);
        return outsourceRateMapper.selectPage(p, w);
    }

    @Override
    @Transactional(readOnly = true)
    public OutsourceRateDO matchEffective(String rateCode, LocalDate date) {
        if (!StringUtils.hasText(rateCode)) {
            return null;
        }
        if (date == null) {
            date = LocalDate.now();
        }
        return outsourceRateMapper.selectEffective(rateCode, date);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutsourceRateDO> listEffective(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        return outsourceRateMapper.listEffective(date);
    }

    // ==================== private ====================

    /**
     * 计算公司总人力成本 = 月薪 + 差旅报销 + 差旅补贴
     *
     * @param monthlySalary      月度薪资
     * @param travelReimbursement 差旅报销（为 null 时按 0 处理）
     * @param travelAllowance    差旅补贴（为 null 时按 0 处理）
     * @return 总成本（保留 2 位小数）
     */
    private BigDecimal calculateTotalCost(BigDecimal monthlySalary, BigDecimal travelReimbursement, BigDecimal travelAllowance) {
        if (monthlySalary == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "月度薪资不能为空");
        }
        BigDecimal reimbursement = travelReimbursement != null ? travelReimbursement : BigDecimal.ZERO;
        BigDecimal allowance = travelAllowance != null ? travelAllowance : BigDecimal.ZERO;
        return monthlySalary.add(reimbursement).add(allowance).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 创建参数校验
     *
     * @param dto 创建参数
     */
    private void validateCreate(OutsourceRateCreateDTO dto) {
        if (dto == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "外包职级费率参数不能为空");
        }
        if (!StringUtils.hasText(dto.getRateCode())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "外包级别编码不能为空");
        }
        if (!StringUtils.hasText(dto.getRateName())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "外包级别名称不能为空");
        }
        // monthlySalary 由 dailyRate × monthlyDays 服务端自动计算（见 create 方法），不在 create 入参校验
        if (dto.getDailyRate() == null || dto.getDailyRate().signum() <= 0) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "人天单价必须大于 0");
        }
        validateSegment(dto.getLevelSegment());
        if (dto.getEffectiveDate() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "生效日期不能为空");
        }
        if (dto.getExpireDate() != null && dto.getExpireDate().isBefore(dto.getEffectiveDate())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "失效日期不能早于生效日期");
        }
        // rate_code + version 唯一性校验
        Integer version = dto.getVersion() != null ? dto.getVersion() : DEFAULT_VERSION;
        OutsourceRateDO dup = outsourceRateMapper.selectOne(new LambdaQueryWrapper<OutsourceRateDO>()
                .eq(OutsourceRateDO::getRateCode, dto.getRateCode())
                .eq(OutsourceRateDO::getVersion, version));
        if (dup != null) {
            throw new SysException(StandardResultCode.DUPLICATE_KEY, "外包级别编码已存在: " + dto.getRateCode());
        }
    }

    /**
     * 校验级别段位合法性
     *
     * @param segment 级别段位
     */
    private void validateSegment(String segment) {
        if (!StringUtils.hasText(segment) || !VALID_SEGMENTS.contains(segment)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "级别段位非法: " + segment);
        }
    }
}
