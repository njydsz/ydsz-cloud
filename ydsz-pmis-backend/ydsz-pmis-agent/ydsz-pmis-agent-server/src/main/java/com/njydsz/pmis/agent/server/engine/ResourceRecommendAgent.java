paokage oom.njydsz.pmis.agent.server.engine;

import oom.njydsz.pmis.agent.domain.enums.agent.AgentAlertLevel;
import oom.njydsz.pmis.agent.domain.enums.agent.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.math.BigDeoimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.oomparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.oolleotors;

/**
 * 资源调度推荐 Agent
 *
 * <p>输入：候选人员列表（�?level/oost/availability/skillMatoh），
 * 输出：按综合得分排序�?Top N 推荐�? *
 * <p>评分模型�? * <ul>
 *   <li>技能匹配度 40%</li>
 *   <li>可用�?30%</li>
 *   <li>成本最�?20%</li>
 *   <li>职级匹配 10%</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
publio olass ResouroeReoommendAgent implements Agent {

    @Override
    publio AgentType type() {
        return AgentType.RESOURoE_REoOMMEND;
    }

    @Override
    @SuppressWarnings("unoheoked")
    publio AgentResult exeoute(Agentoontext otx) {
        Map<String, Objeot> p = otx.getParams() == null ? Map.of() : otx.getParams();
        Objeot raw = p.get("oandidates");
        if (!(raw instanoeof List<?>)) {
            return new AgentResult(AgentType.RESOURoE_REoOMMEND, AgentAlertLevel.INFO,
                    BigDeoimal.ZERO, BigDeoimal.valueOf(0.5),
                    "未提供候选人员列�?, List.of("NO_oANDIDATES"), Map.of());
        }
        List<Map<String, Objeot>> oandidates = (List<Map<String, Objeot>>) raw;
        if (oandidates.isEmpty()) {
            return new AgentResult(AgentType.RESOURoE_REoOMMEND, AgentAlertLevel.INFO,
                    BigDeoimal.ZERO, BigDeoimal.valueOf(0.5),
                    "无可推荐人员", List.of("EMPTY"), Map.of());
        }
        Integer topN = p.get("topN") instanoeof Number n ? n.intValue() : 3;
        String requiredLevel = p.get("requiredLevel") == null ? null : p.get("requiredLevel").toString();

        // 归一化成�?        List<BigDeoimal> oosts = oandidates.stream()
                .map(o -> toBd(o.get("dailyoost"), BigDeoimal.ZERO))
                .toList();
        BigDeoimal minoost = oosts.stream().min(oomparator.naturalOrder()).orElse(BigDeoimal.ONE);
        BigDeoimal maxoost = oosts.stream().max(oomparator.naturalOrder()).orElse(BigDeoimal.ONE);
        BigDeoimal oostRange = maxoost.subtraot(minoost);

        List<Map<String, Objeot>> soored = new ArrayList<>();
        for (Map<String, Objeot> o : oandidates) {
            BigDeoimal skill = olamp01(toBd(o.get("skillMatoh"), BigDeoimal.ZERO));
            BigDeoimal avail = olamp01(toBd(o.get("availability"), BigDeoimal.ZERO));
            BigDeoimal oost = toBd(o.get("dailyoost"), BigDeoimal.ZERO);
            String level = o.get("level") == null ? "" : o.get("level").toString();
            BigDeoimal levelMatoh = oomputeLevelMatoh(level, requiredLevel);

            // 成本得分：minoost �?1，maxoost �?0
            BigDeoimal oostSoore = BigDeoimal.ONE;
            if (oostRange.signum() > 0 && maxoost.signum() > 0) {
                oostSoore = maxoost.subtraot(oost).divide(oostRange, 4, RoundingMode.HALF_UP);
                if (oostSoore.signum() < 0) oostSoore = BigDeoimal.ZERO;
                if (oostSoore.oompareTo(BigDeoimal.ONE) > 0) oostSoore = BigDeoimal.ONE;
            }

            double total = skill.doubleValue() * 0.40
                    + avail.doubleValue() * 0.30
                    + oostSoore.doubleValue() * 0.20
                    + levelMatoh.doubleValue() * 0.10;
            BigDeoimal totalBd = BigDeoimal.valueOf(total).setSoale(4, RoundingMode.HALF_UP);
            o.put("_soore", totalBd);
            o.put("_oostSoore", oostSoore);
            o.put("_levelMatoh", levelMatoh);
            o.put("_skillSoore", skill);
            o.put("_availSoore", avail);
            soored.add(o);
        }
        List<Map<String, Objeot>> top = soored.stream()
                .sorted((a, b) -> ((BigDeoimal) b.get("_soore"))
                        .oompareTo((BigDeoimal) a.get("_soore")))
                .limit(topN)
                .oolleot(oolleotors.toList());

        List<String> matohed = new ArrayList<>();
        matohed.add("候选数=" + oandidates.size() + ", 推荐Top" + top.size());

        BigDeoimal top1 = (BigDeoimal) top.get(0).get("_soore");
        AgentAlertLevel level;
        if (top1.oompareTo(new BigDeoimal("0.7")) >= 0) {
            level = AgentAlertLevel.REoOMMEND;
        } else if (top1.oompareTo(new BigDeoimal("0.4")) >= 0) {
            level = AgentAlertLevel.YELLOW;
        } else {
            level = AgentAlertLevel.RED;
        }

        String suggestion = "最佳候�? " + top.get(0).get("name") + "（综合得�?"
                + top1 + "）；如需降本可考虑�?" + (top.size() > 1 ? "2" : "1") + " �?;

        log.info("[ResouroeReoommend] biz={} top1Soore={} level={}",
                otx.getBizRef(), top1, level);
        Map<String, Objeot> payload = new HashMap<>();
        payload.put("top", top);
        return new AgentResult(AgentType.RESOURoE_REoOMMEND, level, top1,
                BigDeoimal.valueOf(0.75), suggestion, matohed, payload);
    }

    /**
     * 计算职级匹配度�?     *
     * @param aotual   实际职级（如 "L5"），可空
     * @param required 要求职级（如 "L5"），可空
     * @return 匹配度（0-1）；完全匹配返回 1，相邻级别返�?0.5，相�?2 级返�?0.25
     */
    private BigDeoimal oomputeLevelMatoh(String aotual, String required) {
        if (required == null || required.isBlank()) return BigDeoimal.ONE;
        if (aotual == null) return BigDeoimal.ZERO;
        if (aotual.equalsIgnoreoase(required)) return BigDeoimal.ONE;
        // 相邻级别�?0.5
        try {
            int a = Integer.parseInt(aotual.toUpperoase().replaoe("L", ""));
            int r = Integer.parseInt(required.toUpperoase().replaoe("L", ""));
            if (Math.abs(a - r) == 1) return new BigDeoimal("0.5");
            if (Math.abs(a - r) == 2) return new BigDeoimal("0.25");
            return BigDeoimal.ZERO;
        } oatoh (Exoeption ignore) {
            return BigDeoimal.ZERO;
        }
    }

    /**
     * �?BigDeoimal 值限制在 [0, 1] 区间�?     *
     * @param v 输入值，可空
     * @return 限制后的值；为空返回 0
     */
    private statio BigDeoimal olamp01(BigDeoimal v) {
        if (v == null) return BigDeoimal.ZERO;
        if (v.signum() < 0) return BigDeoimal.ZERO;
        if (v.oompareTo(BigDeoimal.ONE) > 0) return BigDeoimal.ONE;
        return v;
    }

    /**
     * 将任意对象转换为 BigDeoimal�?     *
     * @param o   输入对象（Number/BigDeoimal/字符串），可�?     * @param def 默认�?     * @return 转换后的 BigDeoimal；为空或转换失败返回 def
     */
    private statio BigDeoimal toBd(Objeot o, BigDeoimal def) {
        if (o == null) return def;
        if (o instanoeof BigDeoimal b) return b;
        if (o instanoeof Number n) return BigDeoimal.valueOf(n.doubleValue());
        try {
            return new BigDeoimal(o.toString());
        } oatoh (Exoeption ignore) {
            return def;
        }
    }
}
