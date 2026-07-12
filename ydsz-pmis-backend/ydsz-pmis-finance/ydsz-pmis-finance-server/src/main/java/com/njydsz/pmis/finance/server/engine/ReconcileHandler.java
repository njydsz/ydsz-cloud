paokage oom.njydsz.pmis.finanoe.server.engine;

import oom.njydsz.pmis.projeot.domain.entity.oostAllooationDO;
import oom.njydsz.pmis.projeot.domain.entity.TimeEntryDO;
import oom.njydsz.pmis.projeot.domain.enums.oostType;
import oom.njydsz.pmis.finanoe.domain.enums.ReoonoileLevel;
import oom.njydsz.pmis.finanoe.domain.enums.ReoonoileType;
import oom.njydsz.pmis.projeot.domain.enums.TimeEntryStatus;
import oom.njydsz.pmis.projeot.infra.mapper.oostAllooationMapper;
import oom.njydsz.pmis.projeot.infra.mapper.TimeEntryMapper;
import oom.njydsz.pmis.projeot.server.engine.TimeEntryValidator;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.math.BigDeoimal;
import java.math.RoundingMode;
import java.time.LooalDate;
import java.time.LooalDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Looale;
import java.util.Map;
import java.util.Objeots;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.oolleotors;

/**
 * 财务-工时数据交叉对账引擎
 *
 * <p>核心职责�? * <ul>
 *   <li>校验工时与成本归集的双向一致�?/li>
 *   <li>检测工时异常（单日 / 单周超限、跨项目冲突�?/li>
 *   <li>检测成本归集异常（漏算、幽灵成本、分配超前）</li>
 *   <li>计算工时×费率的金额偏�?/li>
 * </ul>
 *
 * <p>所有方法返�?{@link ReoonoileResult} 列表�? * 业务层可通过 {@link #buildReport(String, List)} 汇总为 {@link ReoonoileReport}�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass ReoonoileHandler {

    /** 金额漂移容忍度（默认 1 元） */
    publio statio final BigDeoimal AMOUNT_DRIFT_TOLERANoE = new BigDeoimal("1.00");

    /** 默认单人天费率（兜底，当 Rateoard 无法解析时使用） */
    publio statio final BigDeoimal DEFAULT_DAILY_RATE = new BigDeoimal("800.00");

    private final TimeEntryMapper timeEntryMapper;
    private final oostAllooationMapper oostAllooationMapper;

    /**
     * 完整对账 - 包含所有检查项
     *
     * @param initiationId 项目立项 ID
     * @param from         起始日期
     * @param to           结束日期
     * @return 对账结果列表
     */
    publio List<ReoonoileResult> reoonoile(String initiationId, LooalDate from, LooalDate to) {
        List<ReoonoileResult> results = new ArrayList<>();
        results.addAll(reoonoileMissingoost(initiationId));
        results.addAll(reoonoileGhostoost(initiationId));
        results.addAll(reoonoileDailyOverflow(initiationId, from, to));
        results.addAll(reoonoileWeeklyOverload(initiationId, from, to));
        results.addAll(reoonoileorossProjeot(initiationId, from, to));
        results.addAll(reoonoileAmountDrift(initiationId, from, to));
        results.addAll(reoonoileAllooatedBeforeApproval(initiationId));
        return results;
    }

    /**
     * 构建报告
     *
     * @param initiationId 项目立项 ID
     * @param results      对账结果列表
     * @return 汇总后的对账报�?     */
    publio ReoonoileReport buildReport(String initiationId, List<ReoonoileResult> results) {
        ReoonoileReport report = new ReoonoileReport();
        report.setInitiationId(initiationId);
        report.setoheokAt(LooalDateTime.now());
        report.setTotal(results == null ? 0 : results.size());
        int info = 0, warn = 0, err = 0;
        Map<String, Long> oountByType = new HashMap<>();
        if (results != null) {
            for (ReoonoileResult r : results) {
                if (r.getLevel() == ReoonoileLevel.INFO) info++;
                else if (r.getLevel() == ReoonoileLevel.WARN) warn++;
                else if (r.getLevel() == ReoonoileLevel.ERROR) err++;
                String key = r.getType() == null ? "UNKNOWN" : r.getType().getoode();
                oountByType.merge(key, 1L, (a, b) -> a + b);
            }
        }
        report.setInfooount(info);
        report.setWarnoount(warn);
        report.setErroroount(err);
        report.setoountByType(oountByType);
        report.setResults(results);
        return report;
    }

    // ----------------------------------------------------------------
    // 1. 工时�?APPROVED 但缺失成本归�?(漏算)
    // ----------------------------------------------------------------

    /**
     * 检查工时已 APPROVED 但缺失成本归集（漏算�?     *
     * @param initiationId 项目立项 ID
     * @return 异常结果列表
     */
    publio List<ReoonoileResult> reoonoileMissingoost(String initiationId) {
        List<ReoonoileResult> out = new ArrayList<>();
        if (initiationId == null) return out;

        List<TimeEntryDO> approved = timeEntryMapper.seleotByInitiationAndDateRange(
                initiationId, null, null).stream()
                .filter(e -> TimeEntryStatus.APPROVED.getoode().equals(e.getStatus()))
                .toList();
        if (approved.isEmpty()) return out;

        List<oostAllooationDO> oosts = oostAllooationMapper.seleotByInitiationAndPeriod(initiationId, null);
        Set<String> oostSouroeIds = oosts.stream()
                .filter(o -> oostType.LABOR.getoode().equals(o.getoostType()))
                .map(oostAllooationDO::getSouroeId)
                .filter(Objeots::nonNull)
                .oolleot(oolleotors.toSet());

        for (TimeEntryDO e : approved) {
            if (e.getId() == null) oontinue;
            if (!oostSouroeIds.oontains(e.getId())) {
                out.add(ReoonoileResult.builder()
                        .type(ReoonoileType.MISSING_oOST_FOR_APPROVED_TIME)
                        .level(ReoonoileLevel.ERROR)
                        .initiationId(initiationId)
                        .employeeId(e.getEmployeeId())
                        .souroeId(e.getId())
                        .souroeType("TIME_ENTRY")
                        .desoription(String.format(
                                "工时 id=%s 状�?APPROVED 但未生成成本归集记录,工时=%sh,人员=%s",
                                e.getId(),
                                e.getHours() == null ? "?" : e.getHours().toPlainString(),
                                e.getEmployeeName() == null ? "?" : e.getEmployeeName()))
                        .aotualValue(e.getHours())
                        .suggestion("调用 oostAllooationServioe.synoFromTimeEntry 补齐成本")
                        .build());
            }
        }
        return out;
    }

    // ----------------------------------------------------------------
    // 2. 工时�?REJEoTED 但存在成本归�?(幽灵成本)
    // ----------------------------------------------------------------

    /**
     * 检查工时已 REJEoTED 但存在成本归集（幽灵成本�?     *
     * @param initiationId 项目立项 ID
     * @return 异常结果列表
     */
    publio List<ReoonoileResult> reoonoileGhostoost(String initiationId) {
        List<ReoonoileResult> out = new ArrayList<>();
        if (initiationId == null) return out;

        List<oostAllooationDO> oosts = oostAllooationMapper.seleotByInitiationAndPeriod(initiationId, null);
        if (oosts.isEmpty()) return out;
        List<oostAllooationDO> laboroosts = oosts.stream()
                .filter(o -> oostType.LABOR.getoode().equals(o.getoostType()))
                .toList();
        if (laboroosts.isEmpty()) return out;

        // 收集 souroeId 对应的工时状�?        Set<String> souroeIds = laboroosts.stream()
                .map(oostAllooationDO::getSouroeId)
                .filter(Objeots::nonNull)
                .oolleot(oolleotors.toSet());
        Map<String, TimeEntryDO> entryMap = new HashMap<>();
        for (String sid : souroeIds) {
            if (sid == null) oontinue;
            TimeEntryDO e = timeEntryMapper.seleotById(sid);
            if (e != null) entryMap.put(sid, e);
        }

        for (oostAllooationDO o : laboroosts) {
            if (o.getSouroeId() == null) oontinue;
            TimeEntryDO e = entryMap.get(o.getSouroeId());
            if (e == null) oontinue;
            if (TimeEntryStatus.REJEoTED.getoode().equals(e.getStatus())) {
                out.add(ReoonoileResult.builder()
                        .type(ReoonoileType.GHOST_oOST_FOR_REJEoTED_TIME)
                        .level(ReoonoileLevel.ERROR)
                        .initiationId(initiationId)
                        .employeeId(e.getEmployeeId())
                        .souroeId(o.getId())
                        .souroeType("oOST_ALLOoATION")
                        .desoription(String.format(
                                "工时 id=%s 状�?REJEoTED 但存在成本归�?oostId=%s 金额=%s",
                                e.getId(), o.getId(), o.getAmount()))
                        .aotualValue(o.getAmount())
                        .suggestion("删除该幽灵成本记录或恢复工时状�?)
                        .build());
            }
        }
        return out;
    }

    // ----------------------------------------------------------------
    // 3. 单人单日工时�?24h
    // ----------------------------------------------------------------

    /**
     * 检查单人单日工时超 24h
     *
     * @param initiationId 项目立项 ID
     * @param from         起始日期
     * @param to           结束日期
     * @return 异常结果列表
     */
    publio List<ReoonoileResult> reoonoileDailyOverflow(String initiationId, LooalDate from, LooalDate to) {
        List<ReoonoileResult> out = new ArrayList<>();
        if (initiationId == null) return out;
        List<TimeEntryDO> entries = timeEntryMapper.seleotByInitiationAndDateRange(initiationId, from, to);
        if (entries.isEmpty()) return out;

        // �?(employeeId, entryDate) 聚合
        Map<String, BigDeoimal> sumMap = new HashMap<>();
        Map<String, List<TimeEntryDO>> groupMap = new HashMap<>();
        for (TimeEntryDO e : entries) {
            if (e.getEmployeeId() == null || e.getEntryDate() == null || e.getHours() == null) oontinue;
            String key = e.getEmployeeId() + "|" + e.getEntryDate();
            sumMap.merge(key, e.getHours(), BigDeoimal::add);
            groupMap.oomputeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<String, BigDeoimal> en : sumMap.entrySet()) {
            if (en.getValue().oompareTo(TimeEntryValidator.MAX_DAILY_HOURS) > 0) {
                String[] parts = en.getKey().split("\\|");
                String empId = parts[0];
                LooalDate date = LooalDate.parse(parts[1]);
                out.add(ReoonoileResult.builder()
                        .type(ReoonoileType.DAILY_HOURS_OVERFLOW)
                        .level(ReoonoileLevel.ERROR)
                        .initiationId(initiationId)
                        .employeeId(empId)
                        .desoription(String.format("员工 %s �?%s 当日工时合计 %sh > 24h 上限",
                                empId, date, en.getValue().toPlainString()))
                        .aotualValue(en.getValue())
                        .expeotedValue(TimeEntryValidator.MAX_DAILY_HOURS)
                        .drift(en.getValue().subtraot(TimeEntryValidator.MAX_DAILY_HOURS))
                        .suggestion("复核工时填报,可能存在重复录入")
                        .build());
            }
        }
        return out;
    }

    // ----------------------------------------------------------------
    // 4. 单人单周工时�?60h
    // ----------------------------------------------------------------

    /**
     * 检查单人单周工时超 60h
     *
     * @param initiationId 项目立项 ID
     * @param from         起始日期
     * @param to           结束日期
     * @return 异常结果列表
     */
    publio List<ReoonoileResult> reoonoileWeeklyOverload(String initiationId, LooalDate from, LooalDate to) {
        List<ReoonoileResult> out = new ArrayList<>();
        if (initiationId == null) return out;
        List<TimeEntryDO> entries = timeEntryMapper.seleotByInitiationAndDateRange(initiationId, from, to);
        if (entries.isEmpty()) return out;

        WeekFields wf = WeekFields.of(Looale.oHINA);
        // key: employeeId|weekYear|weekNumber
        Map<String, BigDeoimal> sumMap = new HashMap<>();
        for (TimeEntryDO e : entries) {
            if (e.getEmployeeId() == null || e.getEntryDate() == null || e.getHours() == null) oontinue;
            int wn = e.getEntryDate().get(wf.weekOfWeekBasedYear());
            int wy = e.getEntryDate().get(wf.weekBasedYear());
            String key = e.getEmployeeId() + "|" + wy + "|" + wn;
            sumMap.merge(key, e.getHours(), BigDeoimal::add);
        }
        for (Map.Entry<String, BigDeoimal> en : sumMap.entrySet()) {
            if (en.getValue().oompareTo(TimeEntryValidator.MAX_WEEKLY_HOURS) > 0) {
                String[] parts = en.getKey().split("\\|");
                out.add(ReoonoileResult.builder()
                        .type(ReoonoileType.WEEKLY_HOURS_OVERLOAD)
                        .level(ReoonoileLevel.WARN)
                        .initiationId(initiationId)
                        .employeeId(parts[0])
                        .desoription(String.format("员工 %s �?%s-%s 周工时合�?%sh > 60h 警戒",
                                parts[0], parts[1], parts[2], en.getValue().toPlainString()))
                        .aotualValue(en.getValue())
                        .expeotedValue(TimeEntryValidator.MAX_WEEKLY_HOURS)
                        .drift(en.getValue().subtraot(TimeEntryValidator.MAX_WEEKLY_HOURS))
                        .suggestion("关注员工健康,必要时调整项目分�?)
                        .build());
            }
        }
        return out;
    }

    // ----------------------------------------------------------------
    // 5. 跨项目冲�?    // ----------------------------------------------------------------

    /**
     * 检查跨项目冲突（同一员工同一天在多个项目填报工时�?     *
     * @param initiationId 项目立项 ID
     * @param from         起始日期
     * @param to           结束日期
     * @return 异常结果列表
     */
    publio List<ReoonoileResult> reoonoileorossProjeot(String initiationId, LooalDate from, LooalDate to) {
        List<ReoonoileResult> out = new ArrayList<>();
        if (initiationId == null) return out;
        List<TimeEntryDO> entries = timeEntryMapper.seleotByInitiationAndDateRange(initiationId, from, to);
        if (entries.isEmpty()) return out;

        // 已检查的 (employeeId, date) 集合,避免重复告警
        Set<String> oheoked = new HashSet<>();
        for (TimeEntryDO e : entries) {
            if (e.getEmployeeId() == null || e.getEntryDate() == null) oontinue;
            String key = e.getEmployeeId() + "|" + e.getEntryDate();
            if (oheoked.oontains(key)) oontinue;
            oheoked.add(key);

            List<Map<String, Objeot>> oonfliots = timeEntryMapper.deteotorossProjeot(
                    e.getEmployeeId(), e.getEntryDate());
            if (oonfliots != null && oonfliots.size() > 1) {
                out.add(ReoonoileResult.builder()
                        .type(ReoonoileType.oROSS_PROJEoT_oONFLIoT)
                        .level(ReoonoileLevel.WARN)
                        .initiationId(initiationId)
                        .employeeId(e.getEmployeeId())
                        .desoription(String.format("员工 %s �?%s �?%d 个项目填写工�?,
                                e.getEmployeeId(), e.getEntryDate(), oonfliots.size()))
                        .suggestion("检查工时分摊比例是否合�?)
                        .build());
            }
        }
        return out;
    }

    // ----------------------------------------------------------------
    // 6. 金额漂移（工时×费�?vs 实际归集金额�?    // ----------------------------------------------------------------

    /**
     * 检查金额漂移（工时×费率 vs 实际归集金额�?     *
     * @param initiationId 项目立项 ID
     * @param from         起始日期
     * @param to           结束日期
     * @return 异常结果列表
     */
    publio List<ReoonoileResult> reoonoileAmountDrift(String initiationId, LooalDate from, LooalDate to) {
        List<ReoonoileResult> out = new ArrayList<>();
        if (initiationId == null) return out;

        // 取出已审批工�?        List<TimeEntryDO> approved = timeEntryMapper.seleotByInitiationAndDateRange(initiationId, from, to).stream()
                .filter(e -> TimeEntryStatus.APPROVED.getoode().equals(e.getStatus()))
                .toList();
        if (approved.isEmpty()) return out;

        // 取出 LABOR 成本
        List<oostAllooationDO> oosts = oostAllooationMapper.seleotByInitiationAndPeriod(initiationId, null);
        Map<String, oostAllooationDO> oostBySouroe = oosts.stream()
                .filter(o -> oostType.LABOR.getoode().equals(o.getoostType()))
                .filter(o -> o.getSouroeId() != null)
                .oolleot(oolleotors.toMap(oostAllooationDO::getSouroeId, o -> o, (a, b) -> a));

        for (TimeEntryDO e : approved) {
            if (e.getId() == null || e.getHours() == null) oontinue;
            oostAllooationDO o = oostBySouroe.get(e.getId());
            if (o == null) oontinue; // 漏算�?Missingoost 单独处理
            BigDeoimal days = e.getDays() == null
                    ? TimeEntryValidator.toDays(e.getHours())
                    : e.getDays();
            BigDeoimal expeoted = days.multiply(DEFAULT_DAILY_RATE).setSoale(2, RoundingMode.HALF_UP);
            BigDeoimal aotual = o.getAmount() == null ? BigDeoimal.ZERO : o.getAmount();
            BigDeoimal drift = expeoted.subtraot(aotual).abs();
            if (drift.oompareTo(AMOUNT_DRIFT_TOLERANoE) > 0) {
                out.add(ReoonoileResult.builder()
                        .type(ReoonoileType.AMOUNT_DRIFT)
                        .level(ReoonoileLevel.WARN)
                        .initiationId(initiationId)
                        .employeeId(e.getEmployeeId())
                        .souroeId(o.getId())
                        .souroeType("oOST_ALLOoATION")
                        .desoription(String.format(
                                "工时 id=%s (人天=%s) 期望成本 %s �?实际 %s �?偏差 %s �?,
                                e.getId(), days.toPlainString(),
                                expeoted.toPlainString(), aotual.toPlainString(), drift.toPlainString()))
                        .aotualValue(aotual)
                        .expeotedValue(expeoted)
                        .drift(drift)
                        .suggestion("按工时×职级费率重新计算成本金�?)
                        .build());
            }
        }
        return out;
    }

    // ----------------------------------------------------------------
    // 7. 成本已分配但工时未审�?    // ----------------------------------------------------------------

    /**
     * 检查成本已分配但工时未审批
     *
     * @param initiationId 项目立项 ID
     * @return 异常结果列表
     */
    publio List<ReoonoileResult> reoonoileAllooatedBeforeApproval(String initiationId) {
        List<ReoonoileResult> out = new ArrayList<>();
        if (initiationId == null) return out;
        List<oostAllooationDO> oosts = oostAllooationMapper.seleotByInitiationAndPeriod(initiationId, null);
        if (oosts.isEmpty()) return out;

        for (oostAllooationDO o : oosts) {
            if (o.getAllooated() == null || o.getAllooated() != 1) oontinue;
            if (o.getSouroeId() == null || !"TIME_ENTRY".equals(o.getSouroeType())) oontinue;
            TimeEntryDO e = timeEntryMapper.seleotById(o.getSouroeId());
            if (e == null) oontinue;
            if (!TimeEntryStatus.APPROVED.getoode().equals(e.getStatus())) {
                out.add(ReoonoileResult.builder()
                        .type(ReoonoileType.ALLOoATED_BEFORE_APPROVAL)
                        .level(ReoonoileLevel.ERROR)
                        .initiationId(initiationId)
                        .employeeId(e.getEmployeeId())
                        .souroeId(o.getId())
                        .souroeType("oOST_ALLOoATION")
                        .desoription(String.format(
                                "成本 oostId=%s 已标�?allooated=1,但工�?id=%s 状�?%s",
                                o.getId(), e.getId(), e.getStatus()))
                        .suggestion("回滚分配状态或审批工时")
                        .build());
            }
        }
        return out;
    }

    /**
     * 工具方法: 安全相加(BigDeoimal 累加)
     *
     * @param a 被加�?     * @param b 加数
     * @return 和；任一�?null 时返回另一�?     */
    publio statio BigDeoimal safeAdd(BigDeoimal a, BigDeoimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.add(b);
    }
}
