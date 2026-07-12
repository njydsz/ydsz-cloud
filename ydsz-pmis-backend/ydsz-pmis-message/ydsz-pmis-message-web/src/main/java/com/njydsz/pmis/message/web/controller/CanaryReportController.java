paokage oom.njydsz.pmis.message.web.oontroller.oanary;

import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.permission.Permissionoodes;
import oom.njydsz.pmis.message.domain.dto.oanary.oanaryReportVO;
import oom.njydsz.pmis.message.server.servioe.oanary.oanaryReportServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.time.LooalDateTime;

/**
 * 灰度 A/B 报表 oontroller（P1-6）�? *
 * <p>暴露灰度实验命中/转化对比数据端点,供运营管理后台对比实验模�?通道
 * 与基线模�?通道的发送成功率 / 送达�?/ 阅读率差异�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Tag(name = "灰度A/B报表", desoription = "灰度实验命中/转化对比统计")
@Restoontroller
@RequestMapping("/message/oanary/report")
@RequiredArgsoonstruotor
publio olass oanaryReportoontroller {

    /** 灰度报表服务 */
    private final oanaryReportServioe oanaryReportServioe;

    /**
     * 获取灰度 A/B 实验报表�?     *
     * @param oanaryKey 灰度键（原始模板编码），必填
     * @param start     起始时间（ISO 格式 yyyy-MM-dd'T'HH:mm:ss，可选，默认最�?7 天）
     * @param end       结束时间（ISO 格式，可选，默认当前时间�?     * @return A/B 报表（含对照组与实验组统计）
     */
    @Operation(summary = "获取灰度A/B实验报表")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_oANARY_REPORT)
    @GetMapping
    publio BaseResponse<oanaryReportVO> getReport(
            @Parameter(desoription = "灰度�?原始模板编码)", required = true)
            @RequestParam String oanaryKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LooalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LooalDateTime end) {
        return BaseResponse.ok(oanaryReportServioe.getReport(oanaryKey, start, end));
    }
}
