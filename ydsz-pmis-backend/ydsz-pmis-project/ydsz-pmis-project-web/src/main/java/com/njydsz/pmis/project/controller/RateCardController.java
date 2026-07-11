package com.njydsz.pmis.project.web.controller.resource;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.domain.dto.RateCardCreateDTO;
import com.njydsz.pmis.project.domain.entity.RateCardDO;
import com.njydsz.pmis.project.server.service.RateCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * 对外报价费率 Rate Card Controller
 *
 * <p>负责对外报价费率的创建、匹配（职级+项目类型+客户等级+日期）、分页查询。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "对外报价费率 Rate Card")
@RestController
@RequestMapping("/resource/rateCard")
@RequiredArgsConstructor
@Validated
public class RateCardController {

    /** 标准费率卡服务 */
    private final RateCardService service;

    /**
     * 创建对外报价费率
     *
     * @param dto 费率创建参数
     * @return 新建费率 ID
     */
    @Operation(summary = "创建对外报价费率")
    @PrePermission("execution:rateCard:create")
    @Idempotent(key = "rateCard:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<String> create(@Valid @RequestBody RateCardCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    /**
     * 更新对外报价费率
     *
     * @param id  费率 ID
     * @param dto 费率更新参数
     * @return 空结果
     */
    @Operation(summary = "更新")
    @PrePermission("execution:rateCard:update")
    @Idempotent(key = "rateCard:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody RateCardCreateDTO dto) {
        service.update(id, dto);
        return Result.ok();
    }

    /**
     * 删除对外报价费率
     *
     * @param id 费率 ID
     * @return 空结果
     */
    @Operation(summary = "删除")
    @PrePermission("execution:rateCard:delete")
    @Idempotent(key = "rateCard:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok();
    }

    /**
     * 查询对外报价费率详情
     *
     * @param id 费率 ID
     * @return 费率实体
     */
    @Operation(summary = "详情")
    @PrePermission("execution:rate:list")
    @GetMapping("/{id}")
    public Result<RateCardDO> get(@PathVariable String id) {
        return Result.ok(service.getById(id));
    }

    /**
     * 命中有效费率（职级+项目类型+客户等级+日期）
     *
     * @param levelCode     职级编码
     * @param projectType   项目类型，可选
     * @param customerLevel 客户等级，可选
     * @param date          生效日期，可选
     * @return 命中的费率实体
     */
    @Operation(summary = "命中有效费率（职级+项目类型+客户等级+日期）")
    @PrePermission("execution:rate:list")
    @GetMapping("/match")
    public Result<RateCardDO> match(
            @RequestParam String levelCode,
            @RequestParam(required = false) String projectType,
            @RequestParam(required = false) String customerLevel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(service.matchEffective(levelCode, projectType, customerLevel, date));
    }

    /**
     * 按职级查询费率
     *
     * @param levelCode 职级编码
     * @return 费率列表
     */
    @Operation(summary = "按职级查询")
    @PrePermission("execution:rate:list")
    @GetMapping("/byLevel")
    public Result<List<RateCardDO>> listByLevel(@RequestParam String levelCode) {
        return Result.ok(service.listByLevel(levelCode));
    }

    /**
     * 分页查询对外报价费率
     *
     * @param page      页码（从 1 开始）
     * @param size      每页大小
     * @param levelCode 职级编码
     * @param status    状态过滤
     * @return 分页结果
     */
    @Operation(summary = "分页")
    @PrePermission("execution:rate:list")
    @GetMapping("/page")
    public Result<Page<RateCardDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String levelCode,
            @RequestParam(required = false) String status) {
        return Result.ok(service.page(page, size, levelCode, status));
    }
}
