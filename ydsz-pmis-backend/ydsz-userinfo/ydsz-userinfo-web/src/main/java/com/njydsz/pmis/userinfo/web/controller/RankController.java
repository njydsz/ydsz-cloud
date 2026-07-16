package com.njydsz.userinfo.web.controller.rate;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.userinfo.domain.entity.rate.RankDO;
import com.njydsz.userinfo.domain.entity.rate.RankRateDO;
import com.njydsz.userinfo.server.service.rate.RankService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 职级/职级费率接口
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "基础数据-职级费率")
@RestController
@RequestMapping("/ranks")
@RequiredArgsConstructor
@Validated
public class RankController {

    /** 职级服务 */
    private final RankService rankService;

    /**
     * 查询所有职级 (L1-L18)
     *
     * @return 统一响应结果，包含职级列表
     */
    @Operation(summary = "所有职级 (L1-L18)")
    @GetMapping
    public BaseResponse<List<RankDO>> list() {
        return BaseResponse.ok(rankService.listAllLevels());
    }

    /**
     * 查询指定日期生效的职级费率
     *
     * @param levelCode 职级编码
     * @param date      生效日期（为空时取当前日期）
     * @return 统一响应结果，包含职级费率
     */
    @Operation(summary = "查询生效的职级费率")
    @GetMapping("/rate")
    public BaseResponse<RankRateDO> getRate(@RequestParam String levelCode,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return BaseResponse.ok(rankService.getEffectiveRate(levelCode, date));
    }

    /**
     * 查询某职级的所有费率版本
     *
     * @param levelCode 职级编码
     * @return 统一响应结果，包含费率版本列表
     */
    @Operation(summary = "查询某职级所有版本")
    @GetMapping("/rate/versions")
    public BaseResponse<List<RankRateDO>> listVersions(@RequestParam String levelCode) {
        return BaseResponse.ok(rankService.listAllVersions(levelCode));
    }
}
