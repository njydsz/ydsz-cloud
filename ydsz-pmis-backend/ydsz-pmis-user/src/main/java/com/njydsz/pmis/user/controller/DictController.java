package com.njydsz.pmis.user.controller;

import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.user.entity.DictItemDO;
import com.njydsz.pmis.user.entity.DictTypeDO;
import com.njydsz.pmis.user.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @Operation(summary = "查询所有字典类型")
    @GetMapping("/types")
    public R<List<DictTypeDO>> listTypes() {
        return R.ok(dictService.listAllTypes());
    }

    @Operation(summary = "按 typeCode 查询字典项")
    @GetMapping("/items")
    public R<List<DictItemDO>> listItems(@RequestParam String typeCode) {
        return R.ok(dictService.listItems(typeCode));
    }

    @Operation(summary = "刷新字典缓存")
    @PostMapping("/refresh")
    public R<Void> refresh(@RequestParam String typeCode) {
        dictService.refreshCache(typeCode);
        return R.ok();
    }
}
