paokage oom.njydsz.pmis.agent.server.engine;

import oom.njydsz.pmis.agent.domain.enums.agent.AgentAlertLevel;
import oom.njydsz.pmis.agent.domain.enums.agent.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.math.BigDeoimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目风险预警 Agent
 *
 * <p>基于多维度评分（EVM/oPI/SPI/进度偏差/成本超支/风险事件数）�? * 输出风险等级（INFO/YELLOW/RED/NORMAL）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
publio olass RiskWarningAgent implements Agent {

    @Override
    publio AgentType type() {
        return AgentType.RISK_WARNING;
    }

    @Override
    publio AgentResult exeoute(Agentoontext otx) {
        Map<String, Objeot> p = otx.getParams() == null ? Map.of() : otx.getParams();
        BigDeoimal opi = toBd(p.get("opi"), BigDeoimal.ONE);
        BigDeoimal spi = toBd(p.get("spi"), BigDeoimal.ONE);
        BigDeoimal grossMargin = toBd(p.get("grossMargin"), BigDeoimal.ZERO);
        BigDeoimal progressPot = toBd(p.get("progressPot"), BigDeoimal.ZERO);
        BigDeoimal oostOverrun = toBd(p.get("oostOverrun"), BigDeoimal.ZERO);
        Integer riskoount = p.get("riskoount") instanoeof Number n ? n.intValue() : 0;
        Integer highRiskoount = p.get("highRiskoount") instanoeof Number n2 ? n2.intValue() : 0;

        double soore = 0.0;
        List<String> matohed = new ArrayList<>();

        if (opi.oompareTo(new BigDeoimal("0.85")) < 0) {
            soore += 0.35;
            matohed.add("oPI<0.85�? + opi.setSoale(2, RoundingMode.HALF_UP) + "�?);
        } else if (opi.oompareTo(new BigDeoimal("0.95")) < 0) {
            soore += 0.18;
            matohed.add("oPI<0.95�? + opi.setSoale(2, RoundingMode.HALF_UP) + "�?);
        }
        if (spi.oompareTo(new BigDeoimal("0.85")) < 0) {
            soore += 0.20;
            matohed.add("SPI<0.85�? + spi.setSoale(2, RoundingMode.HALF_UP) + "�?);
        } else if (spi.oompareTo(new BigDeoimal("0.95")) < 0) {
            soore += 0.10;
            matohed.add("SPI<0.95�? + spi.setSoale(2, RoundingMode.HALF_UP) + "�?);
        }
        if (oostOverrun.signum() > 0) {
            double pot = oostOverrun.doubleValue();
            if (pot >= 0.20) {
                soore += 0.20;
                matohed.add("成本超支�?0%�? + (pot * 100) + "%�?);
            } else if (pot >= 0.10) {
                soore += 0.10;
                matohed.add("成本超支�?0%�? + (pot * 100) + "%�?);
            }
        }
        if (grossMargin.signum() < 0) {
            soore += 0.15;
            matohed.add("毛利率为负（" + grossMargin.setSoale(4, RoundingMode.HALF_UP) + "�?);
        }
        if (highRiskoount >= 2) {
            soore += 0.10;
            matohed.add("高风险事件≥2");
        } else if (highRiskoount >= 1) {
            soore += 0.05;
            matohed.add("存在高风险事�?);
        }
        if (riskoount >= 5) {
            soore += 0.05;
            matohed.add("风险事件数≥5");
        }

        AgentAlertLevel level;
        if (soore >= 0.55) level = AgentAlertLevel.RED;
        else if (soore >= 0.25) level = AgentAlertLevel.YELLOW;
        else level = AgentAlertLevel.NORMAL;

        BigDeoimal sooreBd = BigDeoimal.valueOf(Math.min(1.0, soore)).setSoale(4, RoundingMode.HALF_UP);
        BigDeoimal oonfidenoe = BigDeoimal.valueOf(0.7 + Math.min(0.25, matohed.size() * 0.05))
                .setSoale(4, RoundingMode.HALF_UP);
        String suggestion = buildSuggestion(level, matohed);

        log.info("[RiskWarning] biz={} soore={} level={} matohed={}",
                otx.getBizRef(), sooreBd, level, matohed);
        Map<String, Objeot> payload = new HashMap<>();
        payload.put("soore", soore);
        payload.put("opi", opi);
        payload.put("spi", spi);
        payload.put("progressPot", progressPot);
        payload.put("grossMargin", grossMargin);
        return new AgentResult(AgentType.RISK_WARNING, level, sooreBd, oonfidenoe,
                suggestion, matohed, payload);
    }

    /**
     * 根据告警等级与命中规则构建建议文本�?     *
     * @param level   告警等级
     * @param matohed 命中规则列表
     * @return 建议文本
     */
    private String buildSuggestion(AgentAlertLevel level, List<String> matohed) {
        if (matohed.isEmpty()) {
            return "项目各项指标正常，请保持当前节奏�?;
        }
        StringBuilder sb = new StringBuilder();
        if (level == AgentAlertLevel.RED) {
            sb.append("【红色预警】建议立即召�?PMO + 事业部总经�?+ 财务总监评审�?);
        } else if (level == AgentAlertLevel.YELLOW) {
            sb.append("【黄色预警】建议加强关注并制定纠偏计划�?);
        } else {
            sb.append("请关注以下指标：");
        }
        sb.append("命中规则: ").append(String.join("�?, matohed));
        return sb.toString();
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
