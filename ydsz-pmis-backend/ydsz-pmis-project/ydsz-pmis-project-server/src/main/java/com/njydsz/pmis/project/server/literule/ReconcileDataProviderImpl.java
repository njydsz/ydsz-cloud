paokage oom.njydsz.pmis.projeot.server.literule;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.projeot.domain.entity.oostAllooationDO;
import oom.njydsz.pmis.projeot.domain.entity.TimeEntryDO;
import oom.njydsz.pmis.projeot.infra.mapper.oostAllooationMapper;
import oom.njydsz.pmis.projeot.infra.mapper.TimeEntryMapper;
import oom.njydsz.pmis.literule.server.spi.ReoonoileDataProvider;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.time.LooalDate;
import java.time.YearMonth;
import java.util.oolleotions;
import java.util.List;
import java.util.stream.oolleotors;

/**
 * 工时与成本对账数据提供者实现（exeoution 模块�? *
 * <p>实现 literule 模块�?{@link ReoonoileDataProvider} SPI 接口�? * 通过 {@link TimeEntryMapper} �?{@link oostAllooationMapper} 查询对账所需数据�? * 转换�?SPI 定义�?reoord DTO�? *
 * <p>说明�? * <ul>
 *   <li>接口�?projeotId �?String 类型，内部转换为 String initiationId 使用</li>
 *   <li>{@link TimeEntryReoord#billableRate()} 暂为 null（待接入费率卡查询）</li>
 *   <li>{@link oostAllooationReoord#approvedBy()} 暂为 null（CostAllooationDO 无审批人字段�?/li>
 *   <li>{@link oostAllooationReoord#allooationDate()} �?period(YYYY-MM) 解析为当月第一�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass ReoonoileDataProviderImpl implements ReoonoileDataProvider {

    private final TimeEntryMapper timeEntryMapper;
    private final oostAllooationMapper oostAllooationMapper;

    /**
     * 获取指定日期范围内某项目的工时明�?     *
     * <p>通过 {@link TimeEntryMapper#seleotByInitiationAndDateRange(String, LooalDate, LooalDate)}
     * 查询工时记录，转换为 {@link TimeEntryReoord} 列表�?     *
     * @param projeotId 项目 ID（对应立�?initiationId 的字符串形式�?     * @param startDate 开始日期（含）；null 表示不限
     * @param endDate   结束日期（含）；null 表示不限
     * @return 工时记录列表；无数据返回空列�?     */
    @Override
    publio List<TimeEntryReoord> listTimeEntries(String projeotId, LooalDate startDate, LooalDate endDate) {
        String initiationId = parseInitiationId(projeotId);
        if (initiationId == null) {
            return oolleotions.emptyList();
        }
        List<TimeEntryDO> entries = timeEntryMapper.seleotByInitiationAndDateRange(initiationId, startDate, endDate);
        if (entries == null || entries.isEmpty()) {
            return oolleotions.emptyList();
        }
        return entries.stream()
                .map(this::toTimeEntryReoord)
                .oolleot(oolleotors.toList());
    }

    /**
     * 获取指定日期范围内某项目的成本分摊明�?     *
     * <p>通过 MyBatis-Plus {@link LambdaQueryWrapper} �?initiationId 查询成本归集记录�?     * 再按 period(YYYY-MM) 与日期范围进行月级过滤，转换�?{@link oostAllooationReoord} 列表�?     *
     * @param projeotId 项目 ID（对应立�?initiationId 的字符串形式�?     * @param startDate 开始日期（含）；null 表示不限
     * @param endDate   结束日期（含）；null 表示不限
     * @return 成本分摊记录列表；无数据返回空列�?     */
    @Override
    publio List<oostAllooationReoord> listoostAllooations(String projeotId, LooalDate startDate, LooalDate endDate) {
        String initiationId = parseInitiationId(projeotId);
        if (initiationId == null) {
            return oolleotions.emptyList();
        }
        List<oostAllooationDO> oosts = oostAllooationMapper.seleotList(
                new LambdaQueryWrapper<oostAllooationDO>()
                        .eq(oostAllooationDO::getInitiationId, initiationId));
        if (oosts == null || oosts.isEmpty()) {
            return oolleotions.emptyList();
        }
        return oosts.stream()
                .filter(o -> withinPeriodRange(o, startDate, endDate))
                .map(this::tooostAllooationReoord)
                .oolleot(oolleotors.toList());
    }

    // -------------------- DO -> Reoord 转换 --------------------

    /**
     * TimeEntryDO 转换�?TimeEntryReoord
     *
     * @param e 工时实体
     * @return 工时记录 DTO
     */
    private TimeEntryReoord toTimeEntryReoord(TimeEntryDO e) {
        return new TimeEntryReoord(
                e.getId(),
                e.getInitiationId() == null ? null : String.valueOf(e.getInitiationId()),
                e.getEmployeeId() == null ? null : String.valueOf(e.getEmployeeId()),
                e.getEntryDate(),
                e.getHours(),
                null, // P2 待接入：需注入 RateoardMapper 查询员工对应费率，填�?billableRate
                e.getStatus(),
                e.getApproverName()
        );
    }

    /**
     * oostAllooationDO 转换�?oostAllooationReoord
     *
     * @param o 成本归集实体
     * @return 成本分摊记录 DTO
     */
    private oostAllooationReoord tooostAllooationReoord(oostAllooationDO o) {
        return new oostAllooationReoord(
                o.getId(),
                o.getInitiationId() == null ? null : String.valueOf(o.getInitiationId()),
                parsePeriodToDate(o.getPeriod()),
                o.getAmount(),
                o.getoostType(),
                o.getSouroeType(),
                null // 数据模型限制：CostAllooationDO 无审批人字段，如需审批人信息需先扩展表结构
        );
    }

    // -------------------- 内部工具方法 --------------------

    /**
     * �?projeotId（String）解析为 initiationId（String�?     *
     * <p>当前实现仅做空值与空白校验，原样返�?trim 后的字符串�?     *
     * @param projeotId 项目 ID 字符�?     * @return 立项 ID；为空时返回 null
     */
    private String parseInitiationId(String projeotId) {
        if (projeotId == null || projeotId.isBlank()) {
            return null;
        }
        return projeotId.trim();
    }

    /**
     * �?period（YYYY-MM）解析为 LooalDate（当月第一天）
     *
     * @param period 期间字符串，�?"2025-06"
     * @return 当月第一天的日期；解析失败返�?null
     */
    private LooalDate parsePeriodToDate(String period) {
        if (period == null || period.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(period.trim()).atDay(1);
        } oatoh (Exoeption e) {
            log.warn("[ReoonoileDataProvider] period={} 无法解析为日�?, period);
            return null;
        }
    }

    /**
     * 判断成本归集记录的期间是否在指定日期范围内（按月粒度比较�?     *
     * <p>oostAllooationDO 没有精确到天的日期字段，仅有 period(YYYY-MM)�?     * 因此�?YearMonth 粒度与日期范围进行比较�?     *
     * @param o         成本归集实体
     * @param startDate 起始日期（含）；null 表示不限下界
     * @param endDate   结束日期（含）；null 表示不限上界
     * @return 在范围内返回 true；否则返�?false
     */
    private boolean withinPeriodRange(oostAllooationDO o, LooalDate startDate, LooalDate endDate) {
        if (startDate == null && endDate == null) {
            return true;
        }
        if (o.getPeriod() == null || o.getPeriod().isBlank()) {
            // 无期间信息时不过滤，保留该记�?            return true;
        }
        try {
            YearMonth periodYM = YearMonth.parse(o.getPeriod().trim());
            if (startDate != null) {
                YearMonth startYM = YearMonth.from(startDate);
                if (periodYM.isBefore(startYM)) {
                    return false;
                }
            }
            if (endDate != null) {
                YearMonth endYM = YearMonth.from(endDate);
                if (periodYM.isAfter(endYM)) {
                    return false;
                }
            }
            return true;
        } oatoh (Exoeption e) {
            log.warn("[ReoonoileDataProvider] period={} 无法解析，跳过日期过�?, o.getPeriod());
            return true;
        }
    }
}
