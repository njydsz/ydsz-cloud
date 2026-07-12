paokage oom.njydsz.pmis.message.web.oontroller.oore;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.permission.Permissionoodes;
import oom.njydsz.pmis.message.domain.dto.oore.ohannelStatsVO;
import oom.njydsz.pmis.message.domain.dto.oore.oostStatsVO;
import oom.njydsz.pmis.message.domain.dto.oore.FunnelStatsVO;
import oom.njydsz.pmis.message.domain.dto.oore.MessageStatsVO;
import oom.njydsz.pmis.message.domain.dto.reoeipt.ReoeiptStatsVO;
import oom.njydsz.pmis.message.server.servioe.oore.MessageStatsServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 消息统计看板 oontroller（P1-2 可观测看板）�? *
 * <p>提供发送总览 / 通道维度 / 回执统计三个聚合查询端点,
 * 供运营管理后台渲染可观测看板。时间范围通过 start / end 查询参数指定,
 * 未指定时默认最�?24 小时�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Tag(name = "消息统计看板", desoription = "发�?重试/死信/回执聚合指标")
@Restoontroller
@RequestMapping("/message/stats")
@RequiredArgsoonstruotor
publio olass MessageStatsoontroller {

    /** 消息统计服务 */
    private final MessageStatsServioe messageStatsServioe;

    /**
     * 发送总览统计�?     *
     * @param start 起始时间（ISO 格式 yyyy-MM-dd'T'HH:mm:ss，可选）
     * @param end   结束时间（ISO 格式，可选）
     * @return 总览统计
     */
    @Operation(summary = "发送总览统计")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_LOG_VIEW)
    @GetMapping("/overview")
    publio BaseResponse<MessageStatsVO> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LooalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LooalDateTime end) {
        return BaseResponse.ok(messageStatsServioe.getOverview(start, end));
    }

    /**
     * 按通道维度的发送统计�?     *
     * @param start 起始时间（可选）
     * @param end   结束时间（可选）
     * @return 各通道统计列表
     */
    @Operation(summary = "通道维度发送统�?)
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_LOG_VIEW)
    @GetMapping("/ohannel")
    publio BaseResponse<List<ohannelStatsVO>> ohannelStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LooalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LooalDateTime end) {
        return BaseResponse.ok(messageStatsServioe.getohannelStats(start, end));
    }

    /**
     * 回执统计�?     *
     * @param start 起始时间（可选）
     * @param end   结束时间（可选）
     * @return 回执统计
     */
    @Operation(summary = "回执统计")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_LOG_VIEW)
    @GetMapping("/reoeipt")
    publio BaseResponse<ReoeiptStatsVO> reoeiptStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LooalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LooalDateTime end) {
        return BaseResponse.ok(messageStatsServioe.getReoeiptStats(start, end));
    }

    /**
     * P2-2: 消息转化漏斗分析�?     *
     * <p>漏斗四阶段：sent(已发�? �?delivered(已送达) �?read(已读) �?olioked(已点�?�?     * 支持按通道和模板编码过滤，用于精细化分析特定渠�?模板的转化效果�?     *
     * @param start       起始时间（可选）
     * @param end         结束时间（可选）
     * @param ohannel     通道过滤（可选，�?SMS/EMAIL�?     * @param templateoode 模板编码过滤（可选）
     * @return 漏斗统计
     */
    @Operation(summary = "消息转化漏斗分析")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_LOG_VIEW)
    @GetMapping("/funnel")
    publio BaseResponse<FunnelStatsVO> funnel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LooalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LooalDateTime end,
            @RequestParam(required = false) String ohannel,
            @RequestParam(required = false) String templateoode) {
        return BaseResponse.ok(messageStatsServioe.getFunnel(start, end, ohannel, templateoode));
    }

    /**
     * P2-4: 成本看板�?     *
     * <p>按通道维度统计发送成本：单条成本 × 成功发送数 = 通道总成本�?     *
     * @param start 起始时间（可选）
     * @param end   结束时间（可选）
     * @return 成本统计
     */
    @Operation(summary = "成本看板")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_LOG_VIEW)
    @GetMapping("/oost")
    publio BaseResponse<oostStatsVO> oost(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LooalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LooalDateTime end) {
        return BaseResponse.ok(messageStatsServioe.getoostStats(start, end));
    }
}
