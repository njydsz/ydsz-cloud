package com.njydsz.pmis.message.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.entity.PageQuery;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.message.dto.CanaryUpsertDTO;
import com.njydsz.pmis.message.entity.MsgCanaryDO;
import com.njydsz.pmis.message.service.CanaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 灰度桶 Controller。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "灰度桶", description = "消息灰度发布配置与命中判定")
@RestController
@RequestMapping("/message/canary")
@RequiredArgsConstructor
public class CanaryController {

    private final CanaryService canaryService;

    @Operation(summary = "新增/更新灰度桶")
    @PrePermission(PermissionCodes.MESSAGE_CANARY_UPDATE)
    @PostMapping
    public Result<MsgCanaryDO> upsert(@RequestBody CanaryUpsertDTO dto) {
        return Result.ok(canaryService.upsert(dto));
    }

    @Operation(summary = "按灰度键查询灰度桶")
    @PrePermission(PermissionCodes.MESSAGE_CANARY_VIEW)
    @GetMapping("/{canaryKey}")
    public Result<MsgCanaryDO> getByKey(@PathVariable String canaryKey) {
        return Result.ok(canaryService.getByKey(canaryKey));
    }

    @Operation(summary = "灰度桶分页")
    @PrePermission(PermissionCodes.MESSAGE_CANARY_VIEW)
    @GetMapping("/page")
    public Result<Page<MsgCanaryDO>> page(PageQuery query) {
        return Result.ok(canaryService.page(query));
    }

    @Operation(summary = "判定桶值是否命中灰度")
    @PrePermission(PermissionCodes.MESSAGE_CANARY_VIEW)
    @GetMapping("/hit")
    public Result<Boolean> hit(@RequestParam String canaryKey, @RequestParam String bucketValue) {
        return Result.ok(canaryService.hit(canaryKey, bucketValue));
    }
}
