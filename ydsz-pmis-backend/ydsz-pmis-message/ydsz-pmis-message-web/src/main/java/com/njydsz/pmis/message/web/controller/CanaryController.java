package com.njydsz.pmis.message.web.controller.canary;

import com.njydsz.pmis.common.lock.annotation.Idempotent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.entity.PageQuery;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.message.domain.dto.canary.CanaryUpsertDTO;
import com.njydsz.pmis.message.domain.entity.canary.MsgCanaryDO;
import com.njydsz.pmis.message.server.service.canary.CanaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    /** 灰度桶服务 */
    private final CanaryService canaryService;

    /**
     * 新增或更新灰度桶配置。
     *
     * @param dto 灰度桶保存请求体
     * @return 统一响应结果，包含灰度桶详情
     */
    @Operation(summary = "新增/更新灰度桶")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_CANARY_UPDATE)
    @Idempotent(key = "canary:upsert", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<MsgCanaryDO> upsert(@Valid @RequestBody CanaryUpsertDTO dto) {
        return BaseResponse.ok(canaryService.upsert(dto));
    }

    /**
     * 按灰度键查询灰度桶配置。
     *
     * @param canaryKey 灰度键
     * @return 统一响应结果，包含灰度桶详情
     */
    @Operation(summary = "按灰度键查询灰度桶")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_CANARY_VIEW)
    @GetMapping("/{canaryKey}")
    public BaseResponse<MsgCanaryDO> getByKey(@PathVariable String canaryKey) {
        return BaseResponse.ok(canaryService.getByKey(canaryKey));
    }

    /**
     * 分页查询灰度桶列表。
     *
     * @param query 分页查询参数
     * @return 统一响应结果，包含灰度桶分页数据
     */
    @Operation(summary = "灰度桶分页")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_CANARY_VIEW)
    @GetMapping("/page")
    public BaseResponse<Page<MsgCanaryDO>> page(PageQuery query) {
        return BaseResponse.ok(canaryService.page(query));
    }

    /**
     * 判定桶值是否命中灰度。
     *
     * @param canaryKey  灰度键
     * @param bucketValue 桶值
     * @return 统一响应结果，true 表示命中灰度
     */
    @Operation(summary = "判定桶值是否命中灰度")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_CANARY_VIEW)
    @GetMapping("/hit")
    public BaseResponse<Boolean> hit(@RequestParam String canaryKey, @RequestParam String bucketValue) {
        return BaseResponse.ok(canaryService.hit(canaryKey, bucketValue));
    }
}
