package com.njydsz.pmis.userinfo.controller.rate;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.userinfo.dto.rate.PartTimeRateCreateDTO;
import com.njydsz.pmis.userinfo.dto.rate.PartTimeRatePageDTO;
import com.njydsz.pmis.userinfo.dto.rate.PartTimeRateUpdateDTO;
import com.njydsz.pmis.userinfo.entity.rate.PartTimeRateDO;
import com.njydsz.pmis.userinfo.service.rate.PartTimeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 兼职职级费率接口（P1-P18）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "基础数据-兼职职级费率")
@RestController
@RequestMapping("/partTimeRates")
@RequiredArgsConstructor
@Validated
public class PartTimeRateController {

    /** 兼职职级费率服务 */
    private final PartTimeRateService partTimeRateService;

    /**
     * 创建兼职职级费率
     *
     * @param dto 创建参数
     * @return 统一响应结果，包含新建记录 ID
     */
    @Operation(summary = "创建兼职职级费率")
    @Idempotent(key = "partTimeRate:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<String> create(@Valid @RequestBody PartTimeRateCreateDTO dto) {
        return Result.ok(partTimeRateService.create(dto));
    }

    /**
     * 更新兼职职级费率
     *
     * @param id  记录 ID
     * @param dto 更新参数
     * @return 统一响应结果
     */
    @Operation(summary = "更新兼职职级费率")
    @Idempotent(key = "partTimeRate:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody PartTimeRateUpdateDTO dto) {
        partTimeRateService.update(id, dto);
        return Result.ok();
    }

    /**
     * 删除兼职职级费率
     *
     * @param id 记录 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除兼职职级费率")
    @Idempotent(key = "partTimeRate:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        partTimeRateService.delete(id);
        return Result.ok();
    }

    /**
     * 查询兼职职级费率详情
     *
     * @param id 记录 ID
     * @return 统一响应结果，包含费率详情
     */
    @Operation(summary = "兼职职级费率详情")
    @GetMapping("/{id}")
    public Result<PartTimeRateDO> get(@PathVariable String id) {
        return Result.ok(partTimeRateService.getById(id));
    }

    /**
     * 分页查询兼职职级费率
     *
     * @param query 查询参数
     * @return 统一响应结果，包含分页数据
     */
    @Operation(summary = "兼职职级费率分页")
    @GetMapping
    public Result<Page<PartTimeRateDO>> page(@Valid PartTimeRatePageDTO query) {
        return Result.ok(partTimeRateService.page(
                (int) query.getPage(),
                (int) Math.min(query.getSize(), 200),
                query.getKeyword(),
                query.getLevelSegment(),
                query.getStatus()));
    }

    /**
     * 按级别编码 + 日期匹配生效中的费率
     *
     * @param rateCode 级别编码
     * @param date     生效日期（为空时取当前日期）
     * @return 统一响应结果，包含生效费率
     */
    @Operation(summary = "按级别编码 + 日期匹配生效费率")
    @GetMapping("/match")
    public Result<PartTimeRateDO> matchEffective(@RequestParam String rateCode,
                                                 @RequestParam(required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(partTimeRateService.matchEffective(rateCode, date));
    }

    /**
     * 查询某日期生效中的所有兼职费率
     *
     * @param date 生效日期（为空时取当前日期）
     * @return 统一响应结果，包含生效费率列表
     */
    @Operation(summary = "查询某日期生效中的所有兼职费率")
    @GetMapping("/effective")
    public Result<List<PartTimeRateDO>> listEffective(@RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(partTimeRateService.listEffective(date));
    }
}
