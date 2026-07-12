package com.njydsz.pmis.userinfo.web.controller.rate;

import com.njydsz.pmis.common.lock.annotation.Idempotent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.userinfo.domain.dto.rate.OutsourceRateCreateDTO;
import com.njydsz.pmis.userinfo.domain.dto.rate.OutsourceRatePageDTO;
import com.njydsz.pmis.userinfo.domain.dto.rate.OutsourceRateUpdateDTO;
import com.njydsz.pmis.userinfo.domain.entity.rate.OutsourceRateDO;
import com.njydsz.pmis.userinfo.server.service.rate.OutsourceRateService;
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
 * 外包职级费率接口（V1-V18）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "基础数据-外包职级费率")
@RestController
@RequestMapping("/outsourceRates")
@RequiredArgsConstructor
@Validated
public class OutsourceRateController {

    /** 外包职级费率服务 */
    private final OutsourceRateService outsourceRateService;

    /**
     * 创建外包职级费率
     *
     * @param dto 创建参数
     * @return 统一响应结果，包含新建记录 ID
     */
    @Operation(summary = "创建外包职级费率")
    @Idempotent(key = "outsourceRate:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody OutsourceRateCreateDTO dto) {
        return BaseResponse.ok(outsourceRateService.create(dto));
    }

    /**
     * 更新外包职级费率
     *
     * @param id  记录 ID
     * @param dto 更新参数
     * @return 统一响应结果
     */
    @Operation(summary = "更新外包职级费率")
    @Idempotent(key = "outsourceRate:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}")
    public BaseResponse<Void> update(@PathVariable String id, @Valid @RequestBody OutsourceRateUpdateDTO dto) {
        outsourceRateService.update(id, dto);
        return BaseResponse.ok();
    }

    /**
     * 删除外包职级费率
     *
     * @param id 记录 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除外包职级费率")
    @Idempotent(key = "outsourceRate:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        outsourceRateService.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询外包职级费率详情
     *
     * @param id 记录 ID
     * @return 统一响应结果，包含费率详情
     */
    @Operation(summary = "外包职级费率详情")
    @GetMapping("/{id}")
    public BaseResponse<OutsourceRateDO> get(@PathVariable String id) {
        return BaseResponse.ok(outsourceRateService.getById(id));
    }

    /**
     * 分页查询外包职级费率
     *
     * @param query 查询参数
     * @return 统一响应结果，包含分页数据
     */
    @Operation(summary = "外包职级费率分页")
    @GetMapping
    public BaseResponse<Page<OutsourceRateDO>> page(@Valid OutsourceRatePageDTO query) {
        return BaseResponse.ok(outsourceRateService.page(
                query.getPageNum(),
                Math.min(query.getPageSize(), 200),
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
    public BaseResponse<OutsourceRateDO> matchEffective(@RequestParam String rateCode,
                                                   @RequestParam(required = false)
                                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return BaseResponse.ok(outsourceRateService.matchEffective(rateCode, date));
    }

    /**
     * 查询某日期生效中的所有外包费率
     *
     * @param date 生效日期（为空时取当前日期）
     * @return 统一响应结果，包含生效费率列表
     */
    @Operation(summary = "查询某日期生效中的所有外包费率")
    @GetMapping("/effective")
    public BaseResponse<List<OutsourceRateDO>> listEffective(@RequestParam(required = false)
                                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return BaseResponse.ok(outsourceRateService.listEffective(date));
    }
}
