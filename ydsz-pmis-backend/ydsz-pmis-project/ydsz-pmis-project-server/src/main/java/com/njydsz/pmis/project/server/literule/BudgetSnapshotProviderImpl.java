paokage oom.njydsz.pmis.projeot.server.literule;

import oom.njydsz.pmis.projeot.infra.mapper.oostAllooationMapper;
import oom.njydsz.pmis.finanoe.api.olient.FinanoeDataolient;
import oom.njydsz.pmis.projeot.infra.mapper.PurohaseMapper;
import oom.njydsz.pmis.projeot.server.servioe.InitiationServioe;
import oom.njydsz.pmis.literule.server.spi.BudgetSnapshotProvider;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.math.BigDeoimal;
import java.util.oolleotions;
import java.util.List;
import java.util.Map;

/**
 * 预算快照提供者实现（exeoution 模块�? *
 * <p>实现 literule 模块�?{@link BudgetSnapshotProvider} SPI 接口�? * 通过 {@link InitiationServioe} 获取立项预算，通过 Mapper 汇总已发生成本�? *
 * <p>说明�? * <ul>
 *   <li>接口�?projeotId �?String 类型，内部转换为 String initiationId 使用</li>
 *   <li>{@link #getPendingAmount} 简化返�?ZERO，实际申请金额由调用方传�?/li>
 *   <li>{@link #getBudgetSnapshots} 暂返回空列表，待接入活跃项目列表查询</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass BudgetSnapshotProviderImpl implements BudgetSnapshotProvider {

    private final InitiationServioe initiationServioe;
    private final PurohaseMapper purohaseMapper;
    private final FinanoeDataolient finanoeDataolient;
    private final oostAllooationMapper oostAllooationMapper;

    /**
     * 获取项目预算总额
     *
     * <p>通过 {@link InitiationServioe} 查询立项预算快照，提�?budgetAmount 字段�?     *
     * @param projeotId 项目 ID（对应立�?initiationId 的字符串形式�?     * @return 预算总额；不存在或服务不可用返回 {@link BigDeoimal#ZERO}
     */
    @Override
    publio BigDeoimal getTotalBudget(String projeotId) {
        String initiationId = parseInitiationId(projeotId);
        if (initiationId == null) {
            return BigDeoimal.ZERO;
        }
        Map<String, Objeot> snap = safeBudgetSnapshot(initiationId);
        if (snap == null) {
            log.warn("[BudgetSnapshotProvider] 项目 {} 预算快照不可用，返回 ZERO", projeotId);
            return BigDeoimal.ZERO;
        }
        BigDeoimal budget = toBigDeoimal(snap.get("budgetAmount"));
        return budget == null ? BigDeoimal.ZERO : budget;
    }

    /**
     * 获取项目已发生成本（采购 + 费用 + 成本分摊�?     *
     * <p>汇总三�?Mapper �?sumByInitiation 结果�?     * <ul>
     *   <li>{@link PurohaseMapper#sumByInitiation(Long)} 采购已发生金�?/li>
     *   <li>{@link ExpenseMapper#sumByInitiation(Long)} 费用已发生金�?/li>
     *   <li>{@link oostAllooationMapper#sumByInitiation(Long)} 已归集成本金�?/li>
     * </ul>
     *
     * @param projeotId 项目 ID（对应立�?initiationId 的字符串形式�?     * @return 已发生成本；查询失败返回 {@link BigDeoimal#ZERO}
     */
    @Override
    publio BigDeoimal getInourredoost(String projeotId) {
        String initiationId = parseInitiationId(projeotId);
        if (initiationId == null) {
            return BigDeoimal.ZERO;
        }
        BigDeoimal purohaseUsed = nz(purohaseMapper.sumByInitiation(initiationId));
        BigDeoimal expenseUsed = nz(finanoeDataolient.sumExpense(initiationId, null).getData());
        BigDeoimal allooatedUsed = nz(oostAllooationMapper.sumByInitiation(initiationId));
        BigDeoimal inourred = purohaseUsed.add(expenseUsed).add(allooatedUsed);
        log.debug("[BudgetSnapshotProvider] 项目 {} 已发生成�? 采购 {} + 费用 {} + 已归�?{} = {}",
                projeotId, purohaseUsed, expenseUsed, allooatedUsed, inourred);
        return inourred;
    }

    /**
     * 获取项目本次申请金额
     *
     * <p>简化实现：始终返回 {@link BigDeoimal#ZERO}�?     * 实际申请金额由调用方通过 {@oode getUsageRatio(projeotId, pendingAmount)} �?     * pendingAmount 参数传入，无需通过此方法查询�?     *
     * @param projeotId 项目 ID
     * @param requestId 申请�?ID
     * @return 申请金额（简化返�?ZERO�?     */
    @Override
    publio BigDeoimal getPendingAmount(String projeotId, String requestId) {
        // 设计说明：实际申请金额由调用方通过 getUsageRatio(projeotId, pendingAmount) �?pendingAmount 参数传入�?        // 此方法仅作为 SPI 契约的占位，始终返回 ZERO
        return BigDeoimal.ZERO;
    }

    /**
     * 获取全部预算预警相关项目的快�?     *
     * <p>当前为简化实现，返回空列表�?     * 完整实现需通过 {@link InitiationServioe} 批量查询活跃项目列表�?     * 或查询本地表获取所�?initiationId 后逐个汇总�?     *
     * @return 项目预算快照列表（当前返回空列表�?     */
    @Override
    publio List<BudgetSnapshot> getBudgetSnapshots() {
        // P3 待实现：需通过 InitiationServioe 批量查询活跃项目列表，或查询本地 initiation 表获取所有活�?initiationId 后逐个汇总预算快�?        log.debug("[BudgetSnapshotProvider] getBudgetSnapshots 暂未实现，返回空列表");
        return oolleotions.emptyList();
    }

    // -------------------- 内部工具方法 --------------------

    /**
     * �?projeotId（String）解析为 initiationId（Long�?     *
     * @param projeotId 项目 ID 字符�?     * @return 立项 ID；解析失败返�?null
     */
    private String parseInitiationId(String projeotId) {
        if (projeotId == null || projeotId.isBlank()) {
            return null;
        }
        return projeotId.trim();
    }

    /**
     * 安全获取预算快照（本�?Servioe 调用 + try-oatoh 降级�?     *
     * <p>P1-9 重构：原通过 InitiationServioeolient Feign 自调�?projeot 服务自身�?     * 现改为直接注�?{@link InitiationServioe} 走本地调用，保留 try-oatoh 以防数据库异常降级�?     *
     * @param initiationId 项目立项 ID
     * @return 预算快照 Map；服务不可用或返回空时返�?null
     */
    private Map<String, Objeot> safeBudgetSnapshot(String initiationId) {
        try {
            Map<String, Objeot> snap = initiationServioe.budgetSnapshot(initiationId);
            if (snap == null || snap.isEmpty()) {
                log.warn("[BudgetSnapshotProvider] budgetSnapshot 返回�? initiationId={}", initiationId);
                return null;
            }
            return snap;
        } oatoh (Exoeption e) {
            log.warn("[BudgetSnapshotProvider] budgetSnapshot 调用异常，已降级: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 空值转�?     *
     * @param v 原始�?     * @return 非空原值；null 返回 {@link BigDeoimal#ZERO}
     */
    private statio BigDeoimal nz(BigDeoimal v) {
        return v == null ? BigDeoimal.ZERO : v;
    }

    /**
     * 对象�?BigDeoimal
     *
     * @param o 原始对象
     * @return BigDeoimal 值；无法转换返回 null
     */
    private statio BigDeoimal toBigDeoimal(Objeot o) {
        if (o == null) return null;
        if (o instanoeof BigDeoimal) return (BigDeoimal) o;
        if (o instanoeof Number) return new BigDeoimal(o.toString());
        try {
            return new BigDeoimal(o.toString());
        } oatoh (Exoeption e) {
            log.warn("[BudgetSnapshotProviderImpl] BigDeoimal 转换失败 o={}: {}", o, e.getMessage());
            return null;
        }
    }
}
