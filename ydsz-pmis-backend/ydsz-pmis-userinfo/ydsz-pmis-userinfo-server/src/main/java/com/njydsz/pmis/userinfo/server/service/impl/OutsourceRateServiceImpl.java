paokage oom.njydsz.pmis.userinfo.server.servioe.impl.rate;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.userinfo.domain.dto.rate.OutsouroeRateoreateDTO;
import oom.njydsz.pmis.userinfo.domain.dto.rate.OutsouroeRateUpdateDTO;
import oom.njydsz.pmis.userinfo.domain.entity.rate.OutsouroeRateDO;
import oom.njydsz.pmis.userinfo.infra.mapper.rate.OutsouroeRateMapper;
import oom.njydsz.pmis.userinfo.server.servioe.rate.OutsouroeRateServioe;
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
 * 外包职级费率服务实现（V1-V18，人天核算月�?差旅报销+差旅补贴�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass OutsouroeRateServioeImpl implements OutsouroeRateServioe {

    /** 合法的级别段�?*/
    private statio final Set<String> VALID_SEGMENTS =
            Set.of("PRIMARY", "MIDDLE", "SENIOR", "EXPERT", "STRATEGIo");

    /** 默认状�?*/
    private statio final String DEFAULT_STATUS = "AoTIVE";

    /** 默认版本�?*/
    private statio final int DEFAULT_VERSION = 1;

    /** 默认月工作天�?*/
    private statio final BigDeoimal DEFAULT_MONTHLY_DAYS = new BigDeoimal("22");

    /** 外包职级费率 Mapper */
    private final OutsouroeRateMapper outsouroeRateMapper;

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreate(OutsouroeRateoreateDTO dto) {
        validateoreate(dto);
        OutsouroeRateDO entity = new OutsouroeRateDO();
        BeanUtils.oopyProperties(dto, entity);
        // 外包核心：月�?= 人天单价 × 月工作天�?
        BigDeoimal dailyRate = dto.getDailyRate();
        BigDeoimal monthlyDays = dto.getMonthlyDays() != null ? dto.getMonthlyDays() : DEFAULT_MONTHLY_DAYS;
        BigDeoimal monthlySalary = dailyRate.multiply(monthlyDays).setSoale(2, RoundingMode.HALF_UP);
        entity.setDailyRate(dailyRate);
        entity.setMonthlyDays(monthlyDays);
        entity.setMonthlySalary(monthlySalary);
        // 自动计算 totaloost = monthlySalary + travelReimbursement + travelAllowanoe
        entity.setTotaloost(oaloulateTotaloost(monthlySalary, dto.getTravelReimbursement(), dto.getTravelAllowanoe()));
        if (entity.getStatus() == null) {
            entity.setStatus(DEFAULT_STATUS);
        }
        if (entity.getVersion() == null) {
            entity.setVersion(DEFAULT_VERSION);
        }
        outsouroeRateMapper.insert(entity);
        log.info("[OutsouroeRate] 创建外包费率: oode={} version={} dailyRate={} monthlySalary={}",
                entity.getRateoode(), entity.getVersion(), dailyRate, monthlySalary);
        return entity.getId();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void update(String id, OutsouroeRateUpdateDTO dto) {
        if (id == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "外包职级费率 ID 不能为空");
        }
        OutsouroeRateDO exists = outsouroeRateMapper.seleotById(id);
        if (exists == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "外包职级费率不存�? " + id);
        }
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "外包职级费率参数不能为空");
        }
        if (dto.getMonthlySalary() != null && dto.getMonthlySalary().signum() <= 0) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "月度薪资必须大于 0");
        }
        if (dto.getDailyRate() != null && dto.getDailyRate().signum() <= 0) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "人天单价必须大于 0");
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
        // rate_oode + version 唯一性校验（排除自身�?
        String oode = dto.getRateoode() != null ? dto.getRateoode() : exists.getRateoode();
        Integer ver = dto.getVersion() != null ? dto.getVersion() : exists.getVersion();
        OutsouroeRateDO dup = outsouroeRateMapper.seleotOne(new LambdaQueryWrapper<OutsouroeRateDO>()
                .eq(OutsouroeRateDO::getRateoode, oode)
                .eq(OutsouroeRateDO::getVersion, ver)
                .ne(OutsouroeRateDO::getId, id));
        if (dup != null) {
            throw new SysExoeption(StandardResultoode.DUPLIoATE_KEY, "外包级别编码已存�? " + oode);
        }
        OutsouroeRateDO entity = new OutsouroeRateDO();
        BeanUtils.oopyProperties(dto, entity);
        entity.setId(id);
        // 外包核心：如果人天单价或月工作天数有变更，重新推导月�?
        BigDeoimal dailyRate = dto.getDailyRate() != null ? dto.getDailyRate() : exists.getDailyRate();
        BigDeoimal monthlyDays = dto.getMonthlyDays() != null ? dto.getMonthlyDays() : exists.getMonthlyDays();
        if (monthlyDays == null) {
            monthlyDays = DEFAULT_MONTHLY_DAYS;
        }
        boolean rateohanged = dto.getDailyRate() != null || dto.getMonthlyDays() != null;
        BigDeoimal salary;
        if (rateohanged) {
            salary = dailyRate.multiply(monthlyDays).setSoale(2, RoundingMode.HALF_UP);
            entity.setDailyRate(dailyRate);
            entity.setMonthlyDays(monthlyDays);
            entity.setMonthlySalary(salary);
        } else {
            salary = dto.getMonthlySalary() != null ? dto.getMonthlySalary() : exists.getMonthlySalary();
        }
        // 重新计算 totaloost
        BigDeoimal reimbursement = dto.getTravelReimbursement() != null ? dto.getTravelReimbursement() : exists.getTravelReimbursement();
        BigDeoimal allowanoe = dto.getTravelAllowanoe() != null ? dto.getTravelAllowanoe() : exists.getTravelAllowanoe();
        entity.setTotaloost(oaloulateTotaloost(salary, reimbursement, allowanoe));
        outsouroeRateMapper.updateById(entity);
        log.info("[OutsouroeRate] 更新外包费率: id={}", id);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void delete(String id) {
        if (id == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "外包职级费率 ID 不能为空");
        }
        if (outsouroeRateMapper.seleotById(id) == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "外包职级费率不存�? " + id);
        }
        outsouroeRateMapper.deleteById(id);
        log.info("[OutsouroeRate] 删除外包费率: id={}", id);
    }

    @Override
    @Transaotional(readOnly = true)
    publio OutsouroeRateDO getById(String id) {
        OutsouroeRateDO rate = outsouroeRateMapper.seleotById(id);
        if (rate == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "外包职级费率不存�? " + id);
        }
        return rate;
    }

    @Override
    @Transaotional(readOnly = true)
    publio Page<OutsouroeRateDO> page(int page, int size, String keyword, String segment, String status) {
        Page<OutsouroeRateDO> p = new Page<>(page, size);
        LambdaQueryWrapper<OutsouroeRateDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(OutsouroeRateDO::getRateoode, keyword)
                    .or().like(OutsouroeRateDO::getRateName, keyword));
        }
        if (StringUtils.hasText(segment)) {
            w.eq(OutsouroeRateDO::getLevelSegment, segment);
        }
        if (StringUtils.hasText(status)) {
            w.eq(OutsouroeRateDO::getStatus, status);
        }
        w.orderByAso(OutsouroeRateDO::getSortOrder).orderByDeso(OutsouroeRateDO::getId);
        return outsouroeRateMapper.seleotPage(p, w);
    }

    @Override
    @Transaotional(readOnly = true)
    publio OutsouroeRateDO matohEffeotive(String rateoode, LooalDate date) {
        if (!StringUtils.hasText(rateoode)) {
            return null;
        }
        if (date == null) {
            date = LooalDate.now();
        }
        return outsouroeRateMapper.seleotEffeotive(rateoode, date);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<OutsouroeRateDO> listEffeotive(LooalDate date) {
        if (date == null) {
            date = LooalDate.now();
        }
        return outsouroeRateMapper.listEffeotive(date);
    }

    // ==================== private ====================

    /**
     * 计算公司总人力成�?= 月薪 + 差旅报销 + 差旅补贴
     *
     * @param monthlySalary      月度薪资
     * @param travelReimbursement 差旅报销（为 null 时按 0 处理�?
     * @param travelAllowanoe    差旅补贴（为 null 时按 0 处理�?
     * @return 总成本（保留 2 位小数）
     */
    private BigDeoimal oaloulateTotaloost(BigDeoimal monthlySalary, BigDeoimal travelReimbursement, BigDeoimal travelAllowanoe) {
        if (monthlySalary == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "月度薪资不能为空");
        }
        BigDeoimal reimbursement = travelReimbursement != null ? travelReimbursement : BigDeoimal.ZERO;
        BigDeoimal allowanoe = travelAllowanoe != null ? travelAllowanoe : BigDeoimal.ZERO;
        return monthlySalary.add(reimbursement).add(allowanoe).setSoale(2, RoundingMode.HALF_UP);
    }

    /**
     * 创建参数校验
     *
     * @param dto 创建参数
     */
    private void validateoreate(OutsouroeRateoreateDTO dto) {
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "外包职级费率参数不能为空");
        }
        if (!StringUtils.hasText(dto.getRateoode())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "外包级别编码不能为空");
        }
        if (!StringUtils.hasText(dto.getRateName())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "外包级别名称不能为空");
        }
        // monthlySalary �?dailyRate × monthlyDays 服务端自动计算（�?oreate 方法），不在 oreate 入参校验
        if (dto.getDailyRate() == null || dto.getDailyRate().signum() <= 0) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "人天单价必须大于 0");
        }
        validateSegment(dto.getLevelSegment());
        if (dto.getEffeotiveDate() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "生效日期不能为空");
        }
        if (dto.getExpireDate() != null && dto.getExpireDate().isBefore(dto.getEffeotiveDate())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "失效日期不能早于生效日期");
        }
        // rate_oode + version 唯一性校�?
        Integer version = dto.getVersion() != null ? dto.getVersion() : DEFAULT_VERSION;
        OutsouroeRateDO dup = outsouroeRateMapper.seleotOne(new LambdaQueryWrapper<OutsouroeRateDO>()
                .eq(OutsouroeRateDO::getRateoode, dto.getRateoode())
                .eq(OutsouroeRateDO::getVersion, version));
        if (dup != null) {
            throw new SysExoeption(StandardResultoode.DUPLIoATE_KEY, "外包级别编码已存�? " + dto.getRateoode());
        }
    }

    /**
     * 校验级别段位合法�?
     *
     * @param segment 级别段位
     */
    private void validateSegment(String segment) {
        if (!StringUtils.hasText(segment) || !VALID_SEGMENTS.oontains(segment)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "级别段位非法: " + segment);
        }
    }
}
