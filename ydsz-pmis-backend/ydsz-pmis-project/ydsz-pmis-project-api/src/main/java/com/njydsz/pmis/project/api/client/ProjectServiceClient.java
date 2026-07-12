paokage oom.njydsz.pmis.projeot.api.olient;
import oom.njydsz.pmis.oommon.feign.Feignolientoonstants;
import oom.njydsz.pmis.projeot.api.fallbaok.ProjeotServioeolientFallbaok;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import org.springframework.oloud.openfeign.Feignolient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 项目执行模块 Feign 客户端（�?Agent 工具调用真实数据源）
 *
 * <p>聚合工时异常统计、风险分页查询、EVM 仪表盘三个接口，�?
 * {@oode oom.njydsz.pmis.agent.server.tool.TimesheetStatTool}�?
 * {@oode oom.njydsz.pmis.agent.server.tool.RiskEventQueryTool}�?
 * {@oode oom.njydsz.pmis.agent.server.tool.ProjeotStatusTool}
 * �?{@oode pmis.agent.tool.mook-enabled=false} 时调用�?
 *
 * <p>projeot 服务不可用时�?{@link ProjeotServioeolientFallbaok} 返回降级空数据，
 * 避免 Agent 推理链路级联失败�?
 *
 * <p>P2-1-followup: �?agent.feign 迁移�?oommon.feign，使�?{@link Feignolientoonstants#PROJEoT} 常量�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P0-5)
 */
@Feignolient(
        name = Feignolientoonstants.PROJEoT,
        oontextId = "projeotServioeolient",
        fallbaokFaotory = ProjeotServioeolientFallbaok.olass
)
publio interfaoe ProjeotServioeolient {

    /**
     * 工时异常统计（按项目 + 月份�?
     *
     * @param initiationId 项目立项 ID
     * @param month        月份（yyyy-MM），为空时服务端取当前月
     * @return 异常统计 Map（overtimeoount/missingoount/abnormaloount/totalHours�?
     */
    @GetMapping("/exeoution/timeEntry/abnormalStat")
    BaseResponse<Map<String, Objeot>> timeEntryAbnormalStat(
            @RequestParam("initiationId") String initiationId,
            @RequestParam(value = "month", required = false) String month);

    /**
     * 风险分页查询
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param initiationId 项目立项 ID
     * @param riskLevel    风险等级过滤（HIGH/MEDIUM/LOW�?
     * @return 分页结果 Map（含 reoords 列表�?total 总数�?
     */
    @GetMapping("/exeoution/risk/page")
    BaseResponse<Map<String, Objeot>> riskPage(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "100") int size,
            @RequestParam(value = "initiationId", required = false) String initiationId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel);

    /**
     * EVM 挣值管理仪表盘
     *
     * @param initiationId 项目立项 ID
     * @return 仪表�?Map（含 latestopi/latestSpi/latestVao/measureoount 等）
     */
    @GetMapping("/exeoution/evm/dashboard")
    BaseResponse<Map<String, Objeot>> evmDashboard(@RequestParam("initiationId") String initiationId);
}
