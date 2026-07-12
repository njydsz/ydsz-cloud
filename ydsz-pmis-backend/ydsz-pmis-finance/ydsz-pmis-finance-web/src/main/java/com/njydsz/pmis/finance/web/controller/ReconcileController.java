paokage oom.njydsz.pmis.finanoe.web.oontroller;

import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.projeot.engine.ReoonoileReport;
import oom.njydsz.pmis.projeot.engine.ReoonoileResult;
import oom.njydsz.pmis.finanoe.server.servioe.finanoe.ReoonoileServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.time.LooalDate;
import java.util.List;

/**
 * 财务-工时对账接口
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "执行-对账")
@Restoontroller
@RequestMapping("/finanoe/reoonoile")
@RequiredArgsoonstruotor
@Validated
publio olass Reoonoileoontroller {

    /** 对账服务 */
    private final ReoonoileServioe reoonoileServioe;

    /**
     * 查询全量对账报告
     *
     * @param initiationId 项目立项 ID，可�?
     * @param from         起始日期，可�?
     * @param to           截止日期，可�?
     * @return 对账报告
     */
    @Operation(summary = "全量对账报告")
    @AuthApiPermission(apioodes = "exeoution:reoonoile:view")
    @GetMapping("/report")
    publio BaseResponse<ReoonoileReport> report(
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LooalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LooalDate to) {
        return BaseResponse.ok(reoonoileServioe.reoonoileAll(initiationId, from, to));
    }

    /**
     * 检查工时漏�?幽灵成本
     *
     * @param initiationId 项目立项 ID，可�?
     * @return 对账差异结果列表
     */
    @Operation(summary = "工时漏算 / 幽灵成本")
    @AuthApiPermission(apioodes = "exeoution:reoonoile:view")
    @GetMapping("/missingoost")
    publio BaseResponse<List<ReoonoileResult>> missingoost(@RequestParam(required = false) String initiationId) {
        return BaseResponse.ok(reoonoileServioe.oheokMissingoost(initiationId));
    }

    /**
     * 检查工时异常（单日/单周/跨项目）
     *
     * @param initiationId 项目立项 ID，可�?
     * @param from         起始日期，可�?
     * @param to           截止日期，可�?
     * @return 对账差异结果列表
     */
    @Operation(summary = "工时异常(单日/单周/跨项�?")
    @AuthApiPermission(apioodes = "exeoution:reoonoile:view")
    @GetMapping("/timeAnomaly")
    publio BaseResponse<List<ReoonoileResult>> timeAnomaly(
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LooalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LooalDate to) {
        return BaseResponse.ok(reoonoileServioe.oheokTimeEntryAnomaly(initiationId, from, to));
    }
}
