package com.njydsz.pmis.userinfo.controller;

import com.njydsz.pmis.common.annotation.RateLimit;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.userinfo.entity.DictItemDO;
import com.njydsz.pmis.userinfo.entity.DictTypeDO;
import com.njydsz.pmis.userinfo.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "基础数据-字典")
@RestController
@RequestMapping("/dict")
@RequiredArgsConstructor
@Validated
public class DictController {

    /** 字典服务 */
    private final DictService dictService;

    /**
     * 查询所有字典类型
     *
     * @return 统一响应结果，包含字典类型列表
     */
    @Operation(summary = "查询所有字典类型")
    @RateLimit(key = "dict", qps = 50, windowSeconds = 60)
    @GetMapping("/types")
    public Result<List<DictTypeDO>> listTypes() {
        return Result.ok(dictService.listAllTypes());
    }

    /**
     * 按 typeCode 查询字典项
     *
     * @param typeCode 字典类型编码
     * @return 统一响应结果，包含字典项列表
     */
    @Operation(summary = "按 typeCode 查询字典项")
    @RateLimit(key = "dict", qps = 50, windowSeconds = 60)
    @GetMapping("/items")
    public Result<List<DictItemDO>> listItems(@RequestParam String typeCode) {
        return Result.ok(dictService.listItems(typeCode));
    }

    /**
     * 刷新指定字典类型的缓存
     *
     * @param typeCode 字典类型编码
     * @return 统一响应结果
     */
    @Operation(summary = "刷新字典缓存")
    @PostMapping("/refresh")
    public Result<Void> refresh(@RequestParam String typeCode) {
        dictService.refreshCache(typeCode);
        return Result.ok();
    }
}
