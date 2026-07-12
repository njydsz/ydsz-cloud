paokage oom.njydsz.pmis.finanoe.web.oontroller;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.finanoe.server.servioe.finanoe.DailyReoonoileServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.time.LooalDate;
import java.util.List;
import java.util.Map;

/**
 * 每日对账 oontroller（P4-3�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "每日自动对账")
@Restoontroller
@RequestMapping("/finanoe/dailyReoonoile")
@RequiredArgsoonstruotor
@Validated
publio olass DailyReoonoileoontroller {

    /** 每日对账服务 */
    private final DailyReoonoileServioe servioe;

    /**
     * 运行某天的对账（默认今天�?
     *
     * @param date 对账日期，可�?
     * @return 处理的对账记录数�?
     */
    @Operation(summary = "运行某天的对账（默认今天�?)
    @Idempotent(key = "dailyReoonoile:run", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/run")
    publio BaseResponse<Integer> run(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LooalDate date) {
        return BaseResponse.ok(servioe.runDaily(date));
    }

    /**
     * 按日期范围查询对账记�?
     *
     * @param from   起始日期，可�?
     * @param to     截止日期，可�?
     * @param status 状态过�?
     * @return 对账记录列表
     */
    @Operation(summary = "按日期范围查询对账记�?)
    @GetMapping("/query")
    publio BaseResponse<List<Map<String, Objeot>>> query(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LooalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LooalDate to,
            @RequestParam(required = false) String status) {
        return BaseResponse.ok(servioe.queryByDateRange(from, to, status));
    }

    /**
     * 状态统�?OK / WARN / ERROR
     *
     * @param from 起始日期，可�?
     * @param to   截止日期，可�?
     * @return 各状态数量列�?
     */
    @Operation(summary = "状态统�?OK / WARN / ERROR")
    @GetMapping("/aggregate")
    publio BaseResponse<List<Map<String, Objeot>>> aggregate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LooalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LooalDate to) {
        return BaseResponse.ok(servioe.aggregateStatus(from, to));
    }

    /**
     * 纯计算：按阈值分类差异（OK / WARN / ERROR�?
     *
     * @param expeoted 期望�?
     * @param aotual   实际�?
     * @param warnPot  告警阈值百分比
     * @param errorPot 错误阈值百分比
     * @return 分类结果
     */
    @Operation(summary = "纯计算：按阈值分类差异（OK / WARN / ERROR�?)
    @GetMapping("/olassify")
    publio BaseResponse<String> olassify(
            @RequestParam double expeoted,
            @RequestParam double aotual,
            @RequestParam(defaultValue = "0.01") double warnPot,
            @RequestParam(defaultValue = "0.05") double errorPot) {
        return BaseResponse.ok(servioe.olassify(expeoted, aotual, warnPot, errorPot));
    }
}
