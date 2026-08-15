package com.njydsz.message.web.controller.canary;

import java.util.List;

import jakarta.validation.Valid;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.domain.converter.MessageConverter;
import com.njydsz.message.domain.dto.canary.CanaryUpsertDTO;
import com.njydsz.message.domain.entity.canary.MsgCanary;
import com.njydsz.message.domain.vo.MsgCanaryVO;
import com.njydsz.message.server.service.canary.CanaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;

/**
 * 灰度桶（Canary Bucket）Controller。
 *
 * <p>提供<b>消息灰度发布配置与命中判定</b>的 HTTP API。
 * 灰度桶按 (canaryKey, bucketValue) 二元组对消息进行分流，
 * 用于新模板 / 新渠道的灰度发布：将一定比例或特定属性的接收人路由到实验组，
 * 其余路由到对照组，对比转化效果后决定全量发布。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/canary/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>灰度配置</b>：{@code POST /} — 新增或更新灰度桶配置（含 canaryKey / bucketStrategy / ratio / whitelist / blacklist）</li>
 *   <li><b>查询配置</b>：{@code GET /{canaryKey}} — 按灰度键查询配置</li>
 *   <li><b>分页查询</b>：{@code GET /page} — 配置列表</li>
 *   <li><b>命中判定</b>：{@code GET /hit} — 给定 (canaryKey, bucketValue) 判定是否命中灰度</li>
 * </ul>
 *
 * <p><b>命中判定流程：</b>{@code CanaryService.hit(canaryKey, bucketValue)} 的判定逻辑：
 * <ol>
 *   <li>加载 {@code canaryKey} 对应的灰度桶配置</li>
 *   <li>检查 {@code bucketValue}（如 userId）是否在白名单（直接命中）/ 黑名单（直接不命中）</li>
 *   <li>按 {@code bucketStrategy}（HASH / MOD / RANDOM）计算桶值，决定是否落入实验组</li>
 *   <li>返回 {@code true}（命中实验组）/ {@code false}（命中对照组）</li>
 * </ol>
 *
 * <p><b>典型场景：</b>
 * <ul>
 *   <li>新短信模板灰度：10% 用户使用新文案，对比送达率与点击率</li>
 *   <li>新渠道接入灰度：内部员工 (employeeId 前缀 EMP) 先体验新通道</li>
 *   <li>地域灰度：仅向杭州 / 上海用户推送新模板</li>
 * </ul>
 *
 * <p><b>与 CanaryReportController 的关系：</b>本 Controller 管理配置（CRUD），
 * {@code CanaryReportController} 提供 A/B 实验效果对比报表。
 *
 * <p><b>多租户隔离：</b>灰度配置按 {@code tenantId} 隔离，跨租户配置不可见。
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>写接口（upsert）启用 {@link Idempotent} 5s 防重</li>
 *   <li>写接口（upsert）启用 {@link RateLimit} 50 QPS 限流</li>
 *   <li>写接口（upsert）启用 {@link Audit} 审计日志（异步持久化）</li>
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#MESSAGE_CANARY_UPDATE} 权限码</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.canary.CanaryService 灰度桶服务
 * @see com.njydsz.message.domain.entity.canary.MsgCanary 灰度桶实体
 */
@Tag(name = "灰度桶", description = "消息灰度发布配置与命中判定")
@RestController
@RequestMapping("/api/v1/message/canary")
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
    @Idempotent(key = "ydsz:message:CanaryController:upsert:lock", ttlSeconds = 5)
    @Audit(module = "灰度管理", type = AuditType.CONFIG, action = AuditAction.CREATE, content = "'upsert'")
    @RateLimit(resource = "message.canary.upsert", threshold = 50)
    @PostMapping
    public BaseResponse<MsgCanaryVO> upsert(@Valid @RequestBody CanaryUpsertDTO dto) {
        return BaseResponse.success(MessageConverter.INSTANT.entityToVO(canaryService.upsert(dto)));
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
    public BaseResponse<MsgCanaryVO> getByKey(@PathVariable String canaryKey) {
        return BaseResponse.success(MessageConverter.INSTANT.entityToVO(canaryService.getByKey(canaryKey)));
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
    public PageResponse<List<MsgCanaryVO>> page(PageQuery query) {
        Page<MsgCanary> page = canaryService.page(query);
        return PageResponse.success(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                MessageConverter.INSTANT.canaryListToVO(page.getRecords()));
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
        return BaseResponse.success(canaryService.hit(canaryKey, bucketValue));
    }
}
