package com.remisoft.system.web.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.remisoft.common.lock.annotation.Idempotent;
import com.remisoft.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.remisoft.system.domain.vo.DictItemVO;
import com.remisoft.system.server.service.AppInfoService;
import com.remisoft.system.server.service.ConfigService;
import com.remisoft.system.server.service.DictItemService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 内部 API Controller（服务间 Feign 调用）
 *
 * <p>为 <b>跨服务 Feign 调用</b> 提供统一 HTTP 入口，是 {@code remi-gateway} 之外的
 * 服务间点对点调用通道。这些端点<b>仅用于服务间通信</b>，不应直接对外暴露。
 *
 * <p><b>接口路径：</b>{@code /api/internal/**}
 *
 * <p><b>安全要求：</b>
 * <ul>
 *   <li>Gateway 应限制 {@code /api/internal/**} 仅允许<b>内部服务 IP</b>调用（白名单），
 *       对公网不可访问</li>
 *   <li>敏感参数（{@code appSecret} 等）通过 <b>POST body</b> 传输，<b>严禁</b>出现在 URL 中，
 *       避免被网关日志 / 浏览器历史等记录</li>
 *   <li>所有接口启用 {@link RateLimit} 接口级限流（50 QPS），防止被恶意刷接口</li>
 *   <li>所有接口启用 {@link Idempotent} 幂等保护（5 秒），避免重试风暴</li>
 *   <li>后续可启用 mTLS 双向认证或 JWT 内部令牌，进一步提升安全性</li>
 * </ul>
 *
 * <p><b>典型使用：</b>本服务通过 {@code @FeignClient} 调用 {@code remi-system} 的内部 API，
 * 如工作流模块查询字典项、用户模块校验应用密钥等。
 *
 * @author remi-team
 * @since 1.0.0
 * @see AppInfoService 应用注册业务逻辑
 * @see ConfigService 配置业务逻辑
 * @see DictItemService 字典项业务逻辑
 */
@Slf4j
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalApiController {

    private final ConfigService configService;
    private final DictItemService dictItemService;
    private final AppInfoService appInfoService;

    /**
     * 按配置键查询配置值（走缓存）
     *
     * <p>走 Redis 二级缓存（{@code remi:config:value:{configKey}}），未命中时回源 DB 并回写缓存。
     * <p>为防止重试风暴，本接口启用 5 秒幂等保护 + 50 QPS 限流。
     *
     * @param request 请求体（必须包含 {@code key} 字段，如 {@code {"key": "remi.workflow.sla-default-hours"}}）
     * @return 配置值字符串；不存在时返回 null
     */
    @RateLimit(resource = "system.internalapi.getConfig", threshold = 50)
    @Idempotent(key = "remi:system:InternalApiController:getConfig:lock", ttlSeconds = 5)
    @PostMapping("/config/get")
    public String getConfig(@RequestBody Map<String, String> request) {
        return configService.getConfigValue(request.get("key"));
    }

    /**
     * 按类型编码和字典项编码查询字典项（走缓存）
     *
     * <p>走 Redis 缓存（{@code remi:dict:item:{typeCode}:{itemCode}}），高频调用安全。
     * <p>典型场景：工作流模块解析「审批状态」「审批类型」等字典项。
     *
     * @param request 请求体（必须包含 {@code typeCode} 和 {@code itemCode} 字段）
     * @return 字典项 VO；不存在时返回 null
     */
    @RateLimit(resource = "system.internalapi.getDictItem", threshold = 50)
    @Idempotent(key = "remi:system:InternalApiController:getDictItem:lock", ttlSeconds = 5)
    @PostMapping("/dict/item")
    public DictItemVO getDictItem(@RequestBody Map<String, String> request) {
        return dictItemService.getByTypeAndCode(request.get("typeCode"), request.get("itemCode"));
    }

    /**
     * 按字典类型编码查询全部启用字典项的值列表（走缓存）
     *
     * <p>走 Redis 缓存，高频调用安全。
     * <p>典型场景：跨服务 Feign 调用获取字典项值列表（如工作流模块获取所有审批状态）。
     *
     * @param typeCode 字典类型编码
     * @return 字典项值列表（itemValue 字段）；类型不存在时返回空列表
     */
    @RateLimit(resource = "system.internalapi.listDictItems", threshold = 50)
    @Idempotent(key = "remi:system:InternalApiController:listDictItems:lock", ttlSeconds = 5)
    @PostMapping("/dict/list")
    public List<String> listDictItems(@RequestParam("typeCode") String typeCode) {
        List<DictItemVO> items = dictItemService.listEnabledByTypeCode(typeCode);
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(DictItemVO::getItemValue)
                .collect(Collectors.toList());
    }

    /**
     * 校验应用密钥（BCrypt）
     *
     * <p>通过 BCrypt 校验 {@code appSecret} 与数据库存储的密钥哈希是否匹配。
     * <p>密钥通过 <b>POST body</b> 传输，<b>严禁</b>出现在 URL 中，避免被网关日志记录。
     * <p>校验结果同时上报 Micrometer 指标（成功/失败计数），便于监控。
     *
     * @param request 请求体（必须包含 {@code appKey} 和 {@code appSecret} 字段）
     * @return 校验通过返回 {@code true}；应用不存在 / 未启用 / 密钥不匹配返回 {@code false}
     */
    @RateLimit(resource = "system.internalapi.validateClient", threshold = 50)
    @Idempotent(key = "remi:system:InternalApiController:validateClient:lock", ttlSeconds = 5)
    @PostMapping("/app/validate")
    public boolean validateClient(@RequestBody Map<String, String> request) {
        return appInfoService.validateClient(request.get("appKey"), request.get("appSecret"));
    }
}
