package com.njydsz.pmis.userinfo.controller.rate;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.userinfo.entity.rate.JobLevelDO;
import com.njydsz.pmis.userinfo.entity.rate.JobLevelRateDO;
import com.njydsz.pmis.userinfo.service.rate.JobLevelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 职级/职级费率接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "基础数据-职级费率")
@RestController
@RequestMapping("/job-levels")
@RequiredArgsConstructor
@Validated
public class JobLevelController {

    /** 职级服务 */
    private final JobLevelService jobLevelService;

    /**
     * 查询所有职级 (L1-L18)
     *
     * @return 统一响应结果，包含职级列表
     */
    @Operation(summary = "所有职级 (L1-L18)")
    @GetMapping
    public Result<List<JobLevelDO>> list() {
        return Result.ok(jobLevelService.listAllLevels());
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
    public Result<JobLevelRateDO> getRate(@RequestParam String levelCode,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(jobLevelService.getEffectiveRate(levelCode, date));
    }

    /**
     * 查询某职级的所有费率版本
     *
     * @param levelCode 职级编码
     * @return 统一响应结果，包含费率版本列表
     */
    @Operation(summary = "查询某职级所有版本")
    @GetMapping("/rate/versions")
    public Result<List<JobLevelRateDO>> listVersions(@RequestParam String levelCode) {
        return Result.ok(jobLevelService.listAllVersions(levelCode));
    }
}
