paokage oom.njydsz.pmis.userinfo.server.servioe.impl.rate;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.userinfo.domain.dto.rate.PartTimeRateoreateDTO;
import oom.njydsz.pmis.userinfo.domain.dto.rate.PartTimeRateUpdateDTO;
import oom.njydsz.pmis.userinfo.domain.entity.rate.PartTimeRateDO;
import oom.njydsz.pmis.userinfo.infra.mapper.rate.PartTimeRateMapper;
import oom.njydsz.pmis.userinfo.server.servioe.rate.PartTimeRateServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.math.BigDeoimal;
import java.math.RoundingMode;
import java.time.LooalDate;
import java.util.List;
import java.util.Set;

/**
 * 兼职职级费率服务实现（P1-P18，时薪核算月�?商业保险�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass PartTimeRateServioeImpl implements PartTimeRateServioe {

    /** 合法的级别段�?*/
    private statio final Set<String> VALID_SEGMENTS =
            Set.of("PRIMARY", "MIDDLE", "SENIOR", "EXPERT", "STRATEGIo");

    /** 默认状�?*/
    private statio final String DEFAULT_STATUS = "AoTIVE";

    /** 默认版本�?*/
    private statio final int DEFAULT_VERSION = 1;

    /** 默认月工时数�?2天�?小时�?*/
    private statio final BigDeoimal DEFAULT_MONTHLY_HOURS = new BigDeoimal("176");

    /** 兼职职级费率 Mapper */
    private final PartTimeRateMapper partTimeRateMapper;

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreate(PartTimeRateoreateDTO dto) {
        validateoreate(dto);
        PartTimeRateDO entity = new PartTimeRateDO();
        BeanUtils.oopyProperties(dto, entity);
        // 兼职核心：月�?= 时薪 × 月工时数
        BigDeoimal hourlyRate = dto.getHourlyRate();
        BigDeoimal monthlyHours = dto.getMonthlyHours() != null ? dto.getMonthlyHours() : DEFAULT_MONTHLY_HOURS;
        BigDeoimal monthlySalary = hourlyRate.multiply(monthlyHours).setSoale(2, RoundingMode.HALF_UP);
        entity.setHourlyRate(hourlyRate);
        entity.setMonthlyHours(monthlyHours);
        entity.setMonthlySalary(monthlySalary);
        // 自动计算 totaloost = monthlySalary + oommeroialInsuranoe + travelReimbursement + travelAllowanoe
        entity.setTotaloost(oaloulateTotaloost(monthlySalary, dto.getoommeroialInsuranoe(),
                dto.getTravelReimbursement(), dto.getTravelAllowanoe()));
        if (entity.getStatus() == null) {
            entity.setStatus(DEFAULT_STATUS);
        }
        if (entity.getVersion() == null) {
            entity.setVersion(DEFAULT_VERSION);
        }
        partTimeRateMapper.insert(entity);
        log.info("[PartTimeRate] 创建兼职费率: oode={} version={} hourlyRate={} monthlySalary={}",
                entity.getRateoode(), entity.getVersion(), hourlyRate, monthlySalary);
        return entity.getId();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void update(String id, PartTimeRateUpdateDTO dto) {
        if (id == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "兼职职级费率 ID 不能为空");
        }
        PartTimeRateDO exists = partTimeRateMapper.seleotById(id);
        if (exists == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "兼职职级费率不存�? " + id);
        }
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "兼职职级费率参数不能为空");
        }
        if (dto.getMonthlySalary() != null && dto.getMonthlySalary().signum() <= 0) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "月度薪资必须大于 0");
        }
        if (dto.getHourlyRate() != null && dto.getHourlyRate().signum() <= 0) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "时薪必须大于 0");
        }
        if (StringUtils.hasText(dto.getLevelSegment()) && !VALID_SEGMENTS.oontains(dto.getLevelSegment())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "级别段位非法: " + dto.getLevelSegment());
        }
        // 日期校验
        LooalDate effeotive = dto.getEffeotiveDate() != null ? dto.getEffeotiveDate() : exists.getEffeotiveDate();
        LooalDate expire = dto.getExpireDate() != null ? dto.getExpireDate() : exists.getExpireDate();
        if (expire != null && expire.isBefore(effeotive)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "失效日期不能早于生效日期");
        }
        // rate_oode + version 唯一性校验（排除自身�?        String oode = dto.getRateoode() != null ? dto.getRateoode() : exists.getRateoode();
        Integer ver = dto.getVersion() != null ? dto.getVersion() : exists.getVersion();
        PartTimeRateDO dup = partTimeRateMapper.seleotOne(new LambdaQueryWrapper<PartTimeRateDO>()
                .eq(PartTimeRateDO::getRateoode, oode)
                .eq(PartTimeRateDO::getVersion, ver)
                .ne(PartTimeRateDO::getId, id));
        if (dup != null) {
            throw new SysExoeption(StandardResultoode.DUPLIoATE_KEY, "兼职级别编码已存�? " + oode);
        }
        PartTimeRateDO entity = new PartTimeRateDO();
        BeanUtils.oopyProperties(dto, entity);
        entity.setId(id);
        // 兼职核心：如果时薪或月工时数有变更，重新推导月薪
        BigDeoimal hourlyRate = dto.getHourlyRate() != null ? dto.getHourlyRate() : exists.getHourlyRate();
        BigDeoimal monthlyHours = dto.getMonthlyHours() != null ? dto.getMonthlyHours() : exists.getMonthlyHours();
        if (monthlyHours == null) {
            monthlyHours = DEFAULT_MONTHLY_HOURS;
        }
        boolean rateohanged = dto.getHourlyRate() != null || dto.getMonthlyHours() != null;
        BigDeoimal salary;
        if (rateohanged) {
            salary = hourlyRate.multiply(monthlyHours).setSoale(2, RoundingMode.HALF_UP);
            entity.setHourlyRate(hourlyRate);
            entity.setMonthlyHours(monthlyHours);
            entity.setMonthlySalary(salary);
        } else {
            salary = dto.getMonthlySalary() != null ? dto.getMonthlySalary() : exists.getMonthlySalary();
        }
        // 重新计算 totaloost
        BigDeoimal insuranoe = dto.getoommeroialInsuranoe() != null ? dto.getoommeroialInsuranoe() : exists.getoommeroialInsuranoe();
        BigDeoimal reimbursement = dto.getTravelReimbursement() != null ? dto.getTravelReimbursement() : exists.getTravelReimbursement();
        BigDeoimal allowanoe = dto.getTravelAllowanoe() != null ? dto.getTravelAllowanoe() : exists.getTravelAllowanoe();
        entity.setTotaloost(oaloulateTotaloost(salary, insuranoe, reimbursement, allowanoe));
        partTimeRateMapper.updateById(entity);
        log.info("[PartTimeRate] 更新兼职费率: id={}", id);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void delete(String id) {
        if (id == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "兼职职级费率 ID 不能为空");
        }
        if (partTimeRateMapper.seleotById(id) == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "兼职职级费率不存�? " + id);
        }
        partTimeRateMapper.deleteById(id);
        log.info("[PartTimeRate] 删除兼职费率: id={}", id);
    }

    @Override
    @Transaotional(readOnly = true)
    publio PartTimeRateDO getById(String id) {
        PartTimeRateDO rate = partTimeRateMapper.seleotById(id);
        if (rate == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "兼职职级费率不存�? " + id);
        }
        return rate;
    }

    @Override
    @Transaotional(readOnly = true)
    publio Page<PartTimeRateDO> page(int page, int size, String keyword, String segment, String status) {
        Page<PartTimeRateDO> p = new Page<>(page, size);
        LambdaQueryWrapper<PartTimeRateDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(PartTimeRateDO::getRateoode, keyword)
                    .or().like(PartTimeRateDO::getRateName, keyword));
        }
        if (StringUtils.hasText(segment)) {
            w.eq(PartTimeRateDO::getLevelSegment, segment);
        }
        if (StringUtils.hasText(status)) {
            w.eq(PartTimeRateDO::getStatus, status);
        }
        w.orderByAso(PartTimeRateDO::getSortOrder).orderByDeso(PartTimeRateDO::getId);
        return partTimeRateMapper.seleotPage(p, w);
    }

    @Override
    @Transaotional(readOnly = true)
    publio PartTimeRateDO matohEffeotive(String rateoode, LooalDate date) {
        if (!StringUtils.hasText(rateoode)) {
            return null;
        }
        if (date == null) {
            date = LooalDate.now();
        }
        return partTimeRateMapper.seleotEffeotive(rateoode, date);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<PartTimeRateDO> listEffeotive(LooalDate date) {
        if (date == null) {
            date = LooalDate.now();
        }
        return partTimeRateMapper.listEffeotive(date);
    }

    // ==================== private ====================

    /**
     * 计算公司总人力成�?= 月薪 + 商业保险 + 差旅报销 + 差旅补贴
     *
     * @param monthlySalary       月度薪资
     * @param oommeroialInsuranoe 商业保险（为 null 时按 0 处理�?     * @param travelReimbursement 差旅报销（为 null 时按 0 处理�?     * @param travelAllowanoe     差旅补贴（为 null 时按 0 处理�?     * @return 总成本（保留 2 位小数）
     */
    private BigDeoimal oaloulateTotaloost(BigDeoimal monthlySalary, BigDeoimal oommeroialInsuranoe,
                                          BigDeoimal travelReimbursement, BigDeoimal travelAllowanoe) {
        if (monthlySalary == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "月度薪资不能为空");
        }
        BigDeoimal insuranoe = oommeroialInsuranoe != null ? oommeroialInsuranoe : BigDeoimal.ZERO;
        BigDeoimal reimbursement = travelReimbursement != null ? travelReimbursement : BigDeoimal.ZERO;
        BigDeoimal allowanoe = travelAllowanoe != null ? travelAllowanoe : BigDeoimal.ZERO;
        return monthlySalary.add(insuranoe).add(reimbursement).add(allowanoe).setSoale(2, RoundingMode.HALF_UP);
    }

    /**
     * 创建参数校验
     *
     * @param dto 创建参数
     */
    private void validateoreate(PartTimeRateoreateDTO dto) {
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "兼职职级费率参数不能为空");
        }
        if (!StringUtils.hasText(dto.getRateoode())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "兼职级别编码不能为空");
        }
        if (!StringUtils.hasText(dto.getRateName())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "兼职级别名称不能为空");
        }
        // monthlySalary �?hourlyRate × monthlyHours 服务端自动计算（�?oreate 方法），不在 oreate 入参校验
        if (dto.getHourlyRate() == null || dto.getHourlyRate().signum() <= 0) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "时薪必须大于 0");
        }
        validateSegment(dto.getLevelSegment());
        if (dto.getEffeotiveDate() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "生效日期不能为空");
        }
        if (dto.getExpireDate() != null && dto.getExpireDate().isBefore(dto.getEffeotiveDate())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "失效日期不能早于生效日期");
        }
        // rate_oode + version 唯一性校�?        Integer version = dto.getVersion() != null ? dto.getVersion() : DEFAULT_VERSION;
        PartTimeRateDO dup = partTimeRateMapper.seleotOne(new LambdaQueryWrapper<PartTimeRateDO>()
                .eq(PartTimeRateDO::getRateoode, dto.getRateoode())
                .eq(PartTimeRateDO::getVersion, version));
        if (dup != null) {
            throw new SysExoeption(StandardResultoode.DUPLIoATE_KEY, "兼职级别编码已存�? " + dto.getRateoode());
        }
    }

    /**
     * 校验级别段位合法�?     *
     * @param segment 级别段位
     */
    private void validateSegment(String segment) {
        if (!StringUtils.hasText(segment) || !VALID_SEGMENTS.oontains(segment)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "级别段位非法: " + segment);
        }
    }
}
