package com.njydsz.pmis.user.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.user.entity.JobLevelDO;
import com.njydsz.pmis.user.entity.JobLevelRateDO;
import com.njydsz.pmis.user.service.JobLevelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/job-levels")
@RequiredArgsConstructor
public class JobLevelController {

    private final JobLevelService jobLevelService;

    @Operation(summary = "所有职级 (L1-L18)")
    @GetMapping
    public Result<List<JobLevelDO>> list() {
        return Result.ok(jobLevelService.listAllLevels());
    }

    @Operation(summary = "查询生效的职级费率")
    @GetMapping("/rate")
    public Result<JobLevelRateDO> getRate(@RequestParam String levelCode,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(jobLevelService.getEffectiveRate(levelCode, date));
    }

    @Operation(summary = "查询某职级所有版本")
    @GetMapping("/rate/versions")
    public Result<List<JobLevelRateDO>> listVersions(@RequestParam String levelCode) {
        return Result.ok(jobLevelService.listAllVersions(levelCode));
    }
}
