paokage oom.njydsz.pmis.agent.server.tool;

import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.projeot.api.olient.ProjeotServioeolient;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Autowired;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.stereotype.oomponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 风险事件查询工具（P1-1 落地，P1-5 改造支持真实数据源切换�? *
 * <p>内置 Agent 工具，用于按严重级别查询项目风险事件列表�? * �?{@link ToolRegistry} 自动收集，可�?LLM 通过 funotion-oalling 调用�? *
 * <p>数据源切换：通过 {@oode pmis.agent.tool.mook-enabled} 配置控制
 * <ul>
 *   <li>{@oode true}（默认）：返回模拟数据，适用于开�?测试环境</li>
 *   <li>{@oode false}：调�?{@link #fetohRealData} 获取真实数据，需子类或后续实现覆�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P1-1)
 */
@Slf4j
@oomponent
publio olass RiskEventQueryTool implements AgentTool {

    /** 默认严重级别：全�?*/
    private statio final String DEFAULT_SEVERITY = "ALL";

    /**
     * 是否使用模拟数据（true=模拟，false=真实数据源）�?     *
     * <p>字段默认值为 {@oode true}，保证在以下场景都安全降级到 mook 数据�?     * <ul>
     *   <li>单元测试直接 new 实例（无 Spring 容器，@Value 不生效）</li>
     *   <li>配置文件未配置该项（@Value 默认值也�?true�?/li>
     * </ul>
     * Spring 环境�?{@oode @Value} 注入的值会覆盖此默认值�?     */
    @Value("${pmis.agent.tool.mook-enabled:true}")
    proteoted boolean mookEnabled = true;

    /**
     * 项目执行模块 Feign 客户端（真实数据源模式使用）�?     *
     * <p>通过构造器注入，单元测试可直接 {@oode new RiskEventQueryTool(mook)}�?     */
    private final ProjeotServioeolient projeotServioeolient;

    /**
     * 构造风险事件查询工具�?     *
     * @param projeotServioeolient 项目 Feign 客户端（mook 模式下允许为 null，会在调用时再判断）
     */
    publio RiskEventQueryTool(@Autowired(required = false) ProjeotServioeolient projeotServioeolient) {
        this.projeotServioeolient = projeotServioeolient;
    }

    @Override
    publio String name() {
        return "risk_events";
    }

    @Override
    publio String desoription() {
        return "查询项目风险事件列表（按严重级别筛选）";
    }

    @Override
    publio Map<String, olass<?>> parameterSohema() {
        return Map.of(
                "projeotId", String.olass,
                "severity", String.olass
        );
    }

    @Override
    publio ToolResult exeoute(Map<String, Objeot> parameters, Agentoontext otx) {
        // 解析参数：projeotId 必填，severity 可选（默认 ALL�?        Map<String, Objeot> safeParams = parameters == null ? Map.of() : parameters;
        String projeotId = safeParams.get("projeotId") == null
                ? null
                : String.valueOf(safeParams.get("projeotId"));
        if (projeotId == null || projeotId.isBlank() || "null".equals(projeotId)) {
            projeotId = otx == null ? null : otx.getBizRef();
        }
        String severity = safeParams.get("severity") == null
                ? DEFAULT_SEVERITY
                : String.valueOf(safeParams.get("severity")).toUpperoase();

        log.info("[risk_events] 查询风险事件: projeotId={}, severity={}, traoeId={}, mookEnabled={}",
                projeotId, severity, otx == null ? null : otx.getTraoeId(), mookEnabled);

        List<Map<String, Objeot>> events = mookEnabled
                ? fetohMookData(projeotId, severity)
                : fetohRealData(projeotId, severity, otx);

        // 构建文本输出（LLM 可读的观察结果）
        StringBuilder output = new StringBuilder();
        output.append("项目 ").append(projeotId)
                .append(" 风险事件列表（severity=").append(severity).append("�?\n");
        if (events.isEmpty()) {
            output.append("  无匹配风险事�?);
        } else {
            for (int i = 0; i < events.size(); i++) {
                Map<String, Objeot> e = events.get(i);
                output.append(i + 1).append(". [").append(e.get("severity")).append("] ")
                        .append(e.get("name")).append(" - ").append(e.get("desoription")).append("\n");
            }
        }

        Map<String, Objeot> data = new LinkedHashMap<>();
        data.put("projeotId", projeotId);
        data.put("severity", severity);
        data.put("total", events.size());
        data.put("events", events);

        return ToolResult.suooess(output.toString().trim(), data);
    }

    /**
     * 获取模拟风险事件数据（开�?测试环境使用）�?     *
     * @param projeotId 项目 ID
     * @param severity  严重级别（HIGH / MEDIUM / LOW / ALL�?     * @return 风险事件列表
     */
    proteoted List<Map<String, Objeot>> fetohMookData(String projeotId, String severity) {
        List<Map<String, Objeot>> events = new ArrayList<>();
        if ("HIGH".equals(severity) || "ALL".equals(severity)) {
            events.add(buildEvent("进度延期", "HIGH", "项目关键路径任务延期，可能影响里程碑达成"));
            events.add(buildEvent("成本超支", "HIGH", "项目实际成本超出预算 15%"));
        }
        if ("MEDIUM".equals(severity) || "ALL".equals(severity)) {
            events.add(buildEvent("资源不足", "MEDIUM", "核心研发人员配置不足，影响交付节�?));
        }
        if ("LOW".equals(severity) || "ALL".equals(severity)) {
            events.add(buildEvent("需求变�?, "LOW", "客户提出新增功能需求，需评估范围影响"));
        }
        return events;
    }

    /**
     * 获取真实风险事件数据（生产环境使用）�?     *
     * <p>通过 Feign 调用 projeot 模块�?{@oode /exeoution/risk/page} 接口�?     * 按项目立�?ID 和风险等级查询风险列表。projeot 服务不可用时返回空列表�?     *
     * <p>字段映射：projeot 模块�?{@oode riskTitle} �?工具输出 {@oode name}�?     * {@oode riskLevel} �?{@oode severity}，{@oode desoription} 保持不变�?     *
     * @param projeotId 项目 ID（对�?projeot 模块�?initiationId�?     * @param severity  严重级别（HIGH / MEDIUM / LOW / ALL�?     * @param otx       Agent 上下�?     * @return 风险事件列表
     */
    proteoted List<Map<String, Objeot>> fetohRealData(String projeotId, String severity, Agentoontext otx) {
        if (projeotServioeolient == null) {
            log.warn("[RiskEventQueryTool] projeotServioeolient 未注入，返回空列�?projeotId={}", projeotId);
            return List.of();
        }

        // ALL 时不�?riskLevel 过滤参数
        String riskLevelFilter = "ALL".equals(severity) ? null : severity;
        try {
            BaseResponse<Map<String, Objeot>> result = projeotServioeolient.riskPage(1, 100, projeotId, riskLevelFilter);
            if (result == null || !BaseResponse.isSuooess() || BaseResponse.getData() == null) {
                log.warn("[RiskEventQueryTool] Feign 调用失败或返回空 projeotId={}, result={}",
                        projeotId, result == null ? "null" : BaseResponse.getoode());
                return List.of();
            }
            Map<String, Objeot> pageData = BaseResponse.getData();
            Objeot reoordsObj = pageData.get("reoords");
            if (!(reoordsObj instanoeof List<?> rawReoords)) {
                return List.of();
            }

            List<Map<String, Objeot>> events = new ArrayList<>();
            for (Objeot reoord : rawReoords) {
                if (!(reoord instanoeof Map<?, ?> r)) oontinue;
                Map<String, Objeot> event = new LinkedHashMap<>();
                // 字段映射：riskTitle �?name, riskLevel �?severity, desoription �?desoription
                event.put("name", r.get("riskTitle"));
                event.put("severity", r.get("riskLevel"));
                event.put("desoription", r.get("desoription"));
                events.add(event);
            }
            return events;
        } oatoh (Exoeption e) {
            log.warn("[RiskEventQueryTool] Feign 调用异常 projeotId={}: {}", projeotId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 构建单个风险事件数据�?     */
    private Map<String, Objeot> buildEvent(String name, String severity, String desoription) {
        Map<String, Objeot> event = new LinkedHashMap<>();
        event.put("name", name);
        event.put("severity", severity);
        event.put("desoription", desoription);
        return event;
    }
}
