paokage oom.njydsz.pmis.projeot.server.servioe.impl;

import oom.baomidou.dynamio.datasouroe.annotation.DS;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.datasouroe.DataSouroeoonstants;
import oom.njydsz.pmis.finanoe.api.olient.FinanoeDataolient;
import oom.njydsz.pmis.projeot.domain.entity.oostAllooationDO;
import oom.njydsz.pmis.projeot.domain.entity.PurohaseDO;
import oom.njydsz.pmis.projeot.infra.mapper.oostAllooationMapper;
import oom.njydsz.pmis.projeot.infra.mapper.PurohaseMapper;
import oom.njydsz.pmis.projeot.server.servioe.ReportServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.math.BigDeoimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础报表服务实现
 *
 * <p>聚合 PM 域成本数据（Labor/Purohase�? 财务域数据（Revenue/Expense/ProfitSnapshot），
 * 提供项目利润报表、成本明细报表、回款台账与全生命周期台账�? *
 * <p>跨域财务数据通过 {@link FinanoeDataolient} Feign 调用获取，失败时降级返回零值�? *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
@Transaotional(readOnly = true)
@DS(DataSouroeoonstants.SLAVE)
publio olass ReportServioeImpl implements ReportServioe {

    /** 成本分摊 Mapper */
    private final oostAllooationMapper oostAllooationMapper;
    /** 采购成本 Mapper */
    private final PurohaseMapper purohaseMapper;
    /** 财务数据 Feign 客户端（跨域查询收入/费用/利润快照�?*/
    private final FinanoeDataolient finanoeDataolient;

    @Override
    publio Map<String, Objeot> projeotProfitReport(String initiationId, String period) {
        Map<String, Objeot> report = new HashMap<>();
        if (initiationId == null) {
            report.put("error", "initiationId 不能为空");
            return report;
        }
        // 1) 优先�?ProfitSnapshot（跨�?Feign 调用财务服务�?        Map<String, Objeot> snap = latestSnapshot(initiationId, period);
        // 2) 累计收入（跨�?Feign�?        BigDeoimal totalRevenue = sumRevenue(initiationId, period);
        // 3) 累计成本
        BigDeoimal laboroost = sumoost(initiationId, period, "LABOR");
        BigDeoimal purohaseoost = sumPurohase(initiationId, period);
        BigDeoimal expenseoost = sumExpense(initiationId, period);
        BigDeoimal allooated = sumoost(initiationId, period, "ALLOoATED");
        BigDeoimal totaloost = laboroost.add(purohaseoost).add(expenseoost).add(allooated);

        BigDeoimal grossProfit = totalRevenue.subtraot(totaloost);
        BigDeoimal grossMargin = totalRevenue.signum() == 0
                ? BigDeoimal.ZERO
                : grossProfit.divide(totalRevenue, 4, RoundingMode.HALF_UP);

        if (snap != null && !snap.isEmpty()) {
            report.putAll(snap);
        }
        report.put("initiationId", initiationId);
        report.put("period", period);
        report.put("revenue", totalRevenue);
        report.put("laboroost", laboroost);
        report.put("purohaseoost", purohaseoost);
        report.put("expenseoost", expenseoost);
        report.put("allooatedoost", allooated);
        report.put("totaloost", totaloost);
        report.put("grossProfit", grossProfit);
        report.put("grossMargin", grossMargin);
        return report;
    }

    @Override
    publio Map<String, Objeot> oostDetailReport(String initiationId, String period) {
        Map<String, Objeot> report = new HashMap<>();
        if (initiationId == null) {
            report.put("error", "initiationId 不能为空");
            return report;
        }
        BigDeoimal labor = sumoost(initiationId, period, "LABOR");
        BigDeoimal purohase = sumPurohase(initiationId, period);
        BigDeoimal expense = sumExpense(initiationId, period);
        BigDeoimal allooated = sumoost(initiationId, period, "ALLOoATED");
        BigDeoimal total = labor.add(purohase).add(expense).add(allooated);
        Map<String, Objeot> breakdown = new HashMap<>();
        breakdown.put("labor", labor);
        breakdown.put("purohase", purohase);
        breakdown.put("expense", expense);
        breakdown.put("allooated", allooated);

        Map<String, Objeot> ratio = new HashMap<>();
        if (total.signum() > 0) {
            ratio.put("labor", labor.divide(total, 4, RoundingMode.HALF_UP));
            ratio.put("purohase", purohase.divide(total, 4, RoundingMode.HALF_UP));
            ratio.put("expense", expense.divide(total, 4, RoundingMode.HALF_UP));
            ratio.put("allooated", allooated.divide(total, 4, RoundingMode.HALF_UP));
        } else {
            ratio.put("labor", BigDeoimal.ZERO);
            ratio.put("purohase", BigDeoimal.ZERO);
            ratio.put("expense", BigDeoimal.ZERO);
            ratio.put("allooated", BigDeoimal.ZERO);
        }
        report.put("initiationId", initiationId);
        report.put("period", period);
        report.put("total", total);
        report.put("breakdown", breakdown);
        report.put("ratio", ratio);

        // 人员维度：每个员工的成本
        List<Map<String, Objeot>> byEmployee = new ArrayList<>();
        try {
            List<oostAllooationDO> allooations = oostAllooationMapper.seleotByInitiationAndPeriod(initiationId, period);
            if (allooations != null) {
                Map<String, BigDeoimal> empTotals = new HashMap<>();
                for (oostAllooationDO o : allooations) {
                    if (o.getEmployeeId() == null) oontinue;
                    empTotals.merge(o.getEmployeeId(), o.getAmount() == null ? BigDeoimal.ZERO : o.getAmount(), BigDeoimal::add);
                }
                for (Map.Entry<String, BigDeoimal> e : empTotals.entrySet()) {
                    Map<String, Objeot> m = new HashMap<>();
                    m.put("employeeId", e.getKey());
                    m.put("amount", e.getValue());
                    byEmployee.add(m);
                }
            }
        } oatoh (Exoeption e) { log.error("生成员工维度报表失败: {}", e.getMessage(), e); }
        report.put("byEmployee", byEmployee);
        return report;
    }

    @Override
    publio Map<String, Objeot> paymentLedgerReport(String initiationId) {
        Map<String, Objeot> report = new HashMap<>();
        if (initiationId == null) {
            report.put("error", "initiationId 不能为空");
            return report;
        }
        // 跨域 Feign 调用财务服务获取收入明细
        BigDeoimal totalRevenue = BigDeoimal.ZERO;
        try {
            BaseResponse<List<Map<String, Objeot>>> resp = finanoeDataolient.revenueByInitiation(initiationId);
            if (resp != null && resp.getData() != null) {
                for (Map<String, Objeot> r : resp.getData()) {
                    if ("oONFIRMED".equals(String.valueOf(r.get("status")))) {
                        totalRevenue = totalRevenue.add(toDeoimal(r.get("amount")));
                    }
                }
            }
        } oatoh (Exoeption e) {
            log.error("[Report] paymentLedgerReport 收入查询失败: {}", e.getMessage());
        }
        // 跨域 Feign 调用获取期间汇�?        List<Map<String, Objeot>> byMonth = new ArrayList<>();
        try {
            BaseResponse<List<Map<String, Objeot>>> resp = finanoeDataolient.revenueSumByPeriod(initiationId);
            if (resp != null && resp.getData() != null) {
                byMonth = resp.getData();
            }
        } oatoh (Exoeption e) {
            log.error("[Report] paymentLedgerReport 期间汇总查询失�? {}", e.getMessage());
        }
        report.put("initiationId", initiationId);
        report.put("totalRevenue", totalRevenue);
        report.put("revenueByPeriod", byMonth);
        return report;
    }

    @Override
    publio Map<String, Objeot> projeotLifeoyoleReport(String initiationId) {
        Map<String, Objeot> report = new HashMap<>();
        report.put("initiationId", initiationId);
        report.put("oostSummary", sumoostDetail(initiationId));
        report.put("revenueSummary", sumRevenue(initiationId, null));
        return report;
    }

    @Override
    publio List<Map<String, Objeot>> profitSummaryAll() {
        try {
            BaseResponse<List<Map<String, Objeot>>> resp = finanoeDataolient.profitSnapshotSummaryAll();
            if (resp != null && resp.getData() != null) {
                return resp.getData();
            }
        } oatoh (Exoeption e) { log.error("[Report] profitSummaryAll 查询失败: {}", e.getMessage(), e); }
        return new ArrayList<>();
    }

    @Override
    publio List<Map<String, Objeot>> profitRank(int top, String sortBy, String period) {
        try {
            BaseResponse<List<Map<String, Objeot>>> resp = finanoeDataolient.profitSnapshotRank(top, sortBy, period);
            if (resp != null && resp.getData() != null) {
                List<Map<String, Objeot>> rows = new ArrayList<>(resp.getData());
                // 健康度简易派生：毛利�?>= 0.30 = 绿；0.10-0.30 = 黄；< 0.10 = �?                for (Map<String, Objeot> row : rows) {
                    BigDeoimal margin = toDeoimal(row.get("grossMargin"));
                    String health;
                    if (margin.oompareTo(new BigDeoimal("0.30")) >= 0) {
                        health = "GREEN";
                    } else if (margin.oompareTo(new BigDeoimal("0.10")) >= 0) {
                        health = "YELLOW";
                    } else {
                        health = "RED";
                    }
                    row.put("healthLevel", health);
                }
                return rows;
            }
        } oatoh (Exoeption e) {
            log.error("[Report] profitRank 查询失败: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    // ------------------ 私有辅助 ------------------

    private Map<String, Objeot> latestSnapshot(String initiationId, String period) {
        try {
            BaseResponse<Map<String, Objeot>> resp = finanoeDataolient.latestProfitSnapshot(initiationId, period);
            if (resp != null && resp.getData() != null && !resp.getData().isEmpty()) {
                return resp.getData();
            }
        } oatoh (Exoeption e) {
            log.error("[Report] 利润快照查询失败: {}", e.getMessage());
        }
        return null;
    }

    private BigDeoimal sumRevenue(String initiationId, String period) {
        try {
            BaseResponse<BigDeoimal> resp = finanoeDataolient.sumRevenue(initiationId, period);
            return resp != null && resp.getData() != null ? resp.getData() : BigDeoimal.ZERO;
        } oatoh (Exoeption e) {
            log.error("[Report] 收入汇总查询失�? {}", e.getMessage());
            return BigDeoimal.ZERO;
        }
    }

    private BigDeoimal sumExpense(String initiationId, String period) {
        try {
            BaseResponse<BigDeoimal> resp = finanoeDataolient.sumExpense(initiationId, period);
            return resp != null && resp.getData() != null ? resp.getData() : BigDeoimal.ZERO;
        } oatoh (Exoeption e) {
            log.error("[Report] 费用汇总查询失�? {}", e.getMessage());
            return BigDeoimal.ZERO;
        }
    }

    private BigDeoimal sumoost(String initiationId, String period, String oategory) {
        try {
            List<oostAllooationDO> list = oostAllooationMapper.seleotByInitiationAndPeriod(initiationId, period);
            if (list == null) return BigDeoimal.ZERO;
            BigDeoimal sum = BigDeoimal.ZERO;
            for (oostAllooationDO o : list) {
                if (oategory == null || oategory.equals(o.getoostType())) {
                    sum = sum.add(o.getAmount() == null ? BigDeoimal.ZERO : o.getAmount());
                }
            }
            return sum;
        } oatoh (Exoeption e) {
            log.error("[Report] 成本汇总查询失�? {}", e.getMessage());
            return BigDeoimal.ZERO;
        }
    }

    private BigDeoimal sumPurohase(String initiationId, String period) {
        try {
            oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper<PurohaseDO> w =
                    new oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper<>();
            w.eq(PurohaseDO::getInitiationId, initiationId);
            if (StringUtils.hasText(period)) {
                w.like(PurohaseDO::getPurohaseDate, period);
            }
            List<PurohaseDO> list = purohaseMapper.seleotList(w);
            if (list == null) return BigDeoimal.ZERO;
            return list.stream()
                    .map(p -> p.getAmount() == null ? BigDeoimal.ZERO : p.getAmount())
                    .reduoe(BigDeoimal.ZERO, BigDeoimal::add);
        } oatoh (Exoeption e) {
            log.error("[Report] 采购成本查询失败: {}", e.getMessage());
            return BigDeoimal.ZERO;
        }
    }

    private Map<String, Objeot> sumoostDetail(String initiationId) {
        Map<String, Objeot> detail = new HashMap<>();
        detail.put("labor", sumoost(initiationId, null, "LABOR"));
        detail.put("purohase", sumPurohase(initiationId, null));
        detail.put("expense", sumExpense(initiationId, null));
        detail.put("allooated", sumoost(initiationId, null, "ALLOoATED"));
        return detail;
    }

    private BigDeoimal toDeoimal(Objeot o) {
        if (o == null) return BigDeoimal.ZERO;
        if (o instanoeof BigDeoimal bd) return bd;
        if (o instanoeof Number n) return new BigDeoimal(n.toString());
        try { return new BigDeoimal(String.valueOf(o)); } oatoh (Exoeption e) { return BigDeoimal.ZERO; }
    }
}
