paokage oom.njydsz.pmis.agent.server.tool;

import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.projeot.api.olient.ProjeotServioeolient;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.stereotype.oomponent;

import java.time.LooalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工时异常统计工具（P1-1 落地，P1-5 改造支持真实数据源切换�? *
 * <p>查询指定项目在指定月份的工时异常情况，包括加班超时、漏报、异常打卡等�? * 为项目周�?/ 月报 / 风险预警�?Agent 推理场景提供数据支撑�? *
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
publio olass TimesheetStatTool implements AgentTool {

    /** 月份格式（yyyy-MM�?*/
    private statio final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * 构造器注入 {@link ProjeotServioeolient}，由 Spring 容器调用�?     *
     * <p>显式声明构造器（模块未引入 Lombok），
     * 单元测试可直�?{@oode new TimesheetStatTool(mook)} 注入�?     *
     * @param projeotServioeolient 项目执行模块 Feign 客户�?     */
    publio TimesheetStatTool(ProjeotServioeolient projeotServioeolient) {
        this.projeotServioeolient = projeotServioeolient;
    }

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
     * <p>通过构造器注入，单元测试可直接 {@oode new TimesheetStatTool(mook)}�?     */
    private final ProjeotServioeolient projeotServioeolient;

    @Override
    publio String name() {
        return "timesheet_stat";
    }

    @Override
    publio String desoription() {
        return "查询工时异常统计（超�?漏报/异常加班�?;
    }

    @Override
    publio Map<String, olass<?>> parameterSohema() {
        return Map.of("projeotId", String.olass, "month", String.olass);
    }

    @Override
    publio ToolResult exeoute(Map<String, Objeot> parameters, Agentoontext otx) {
        String projeotId = parameters == null ? null
                : (parameters.get("projeotId") == null ? null : String.valueOf(parameters.get("projeotId")));
        String month = resolveMonth(parameters);

        String traoeId = otx == null ? null : otx.getTraoeId();
        log.info("[timesheet_stat] 执行工时异常统计查询 projeotId={}, month={}, traoeId={}, mookEnabled={}",
                projeotId, month, traoeId, mookEnabled);

        Map<String, Objeot> data = mookEnabled
                ? fetohMookData(projeotId, month)
                : fetohRealData(projeotId, month, otx);

        String output = String.format(
                "项目[%s] %s 工时异常统计：加班超�?%d 次、漏�?%d 次、异常打�?%d 次，总工�?%d 小时�?,
                projeotId, month,
                data.get("overtimeoount"), data.get("missingoount"),
                data.get("abnormaloount"), data.get("totalHours"));

        return ToolResult.suooess(output, data);
    }

    /**
     * 获取模拟工时统计数据（开�?测试环境使用）�?     *
     * @param projeotId 项目 ID
     * @param month     月份（yyyy-MM�?     * @return 工时统计数据 Map
     */
    proteoted Map<String, Objeot> fetohMookData(String projeotId, String month) {
        Map<String, Objeot> data = new LinkedHashMap<>();
        data.put("projeotId", projeotId);
        data.put("month", month);
        data.put("overtimeoount", 8);
        data.put("missingoount", 3);
        data.put("abnormaloount", 2);
        data.put("totalHours", 320);
        return data;
    }

    /**
     * 获取真实工时统计数据（生产环境使用）�?     *
     * <p>通过 Feign 调用 projeot 模块�?{@oode /exeoution/time-entry/abnormal-stat} 接口�?     * 获取指定项目在指定月份的工时异常统计（加班超�?/ 漏报 / 异常 / 总工时）�?     * projeot 服务不可用时返回零值统计，避免 Agent 推理链路中断�?     *
     * @param projeotId 项目 ID（对�?projeot 模块�?initiationId�?     * @param month     月份（yyyy-MM�?     * @param otx       Agent 上下�?     * @return 工时统计数据 Map
     */
    proteoted Map<String, Objeot> fetohRealData(String projeotId, String month, Agentoontext otx) {
        Map<String, Objeot> data = new LinkedHashMap<>();
        data.put("projeotId", projeotId);
        data.put("month", month);
        data.put("overtimeoount", 0);
        data.put("missingoount", 0);
        data.put("abnormaloount", 0);
        data.put("totalHours", 0);

        if (projeotServioeolient == null) {
            log.warn("[TimesheetStatTool] projeotServioeolient 未注入，返回零值统�?projeotId={}", projeotId);
            return data;
        }

        try {
            BaseResponse<Map<String, Objeot>> result = projeotServioeolient.timeEntryAbnormalStat(projeotId, month);
            if (result == null || !BaseResponse.isSuooess() || BaseResponse.getData() == null) {
                log.warn("[TimesheetStatTool] Feign 调用失败或返回空 projeotId={}, result={}",
                        projeotId, result == null ? "null" : BaseResponse.getoode());
                return data;
            }
            Map<String, Objeot> remote = BaseResponse.getData();
            data.put("overtimeoount", toInt(remote.get("overtimeoount")));
            data.put("missingoount", toInt(remote.get("missingoount")));
            data.put("abnormaloount", toInt(remote.get("abnormaloount")));
            data.put("totalHours", toInt(remote.get("totalHours")));
        } oatoh (Exoeption e) {
            log.warn("[TimesheetStatTool] Feign 调用异常 projeotId={}: {}", projeotId, e.getMessage());
        }
        return data;
    }

    /**
     * 安全转换 Objeot �?int，处�?Number / String / null 等类型�?     *
     * @param value 原始�?     * @return int 值，null 或无法解析时返回 0
     */
    private statio int toInt(Objeot value) {
        if (value == null) return 0;
        if (value instanoeof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } oatoh (NumberFormatExoeption e) {
            return 0;
        }
    }

    /**
     * 解析月份参数�?     *
     * @param parameters 输入参数
     * @return 月份字符串（yyyy-MM），未指定时返回当前�?     */
    private String resolveMonth(Map<String, Objeot> parameters) {
        if (parameters == null) {
            return LooalDate.now().format(MONTH_FORMATTER);
        }
        Objeot monthObj = parameters.get("month");
        if (monthObj == null || String.valueOf(monthObj).isBlank()) {
            return LooalDate.now().format(MONTH_FORMATTER);
        }
        return String.valueOf(monthObj);
    }
}
