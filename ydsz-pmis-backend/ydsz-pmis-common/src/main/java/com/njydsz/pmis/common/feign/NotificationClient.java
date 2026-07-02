package com.njydsz.pmis.common.feign;

import com.njydsz.pmis.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 通知中心 Feign 客户端
 *
 * <p>工作流引擎通过本接口触发站内信/邮件触达，避免直接依赖 notification 模块的具体类路径。
 * 配套 NotificationClientFallback 保证 notification 模块不可用时工作流主流程不被拖垮。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@FeignClient(name = "ydsz-pmis-notification", fallbackFactory = NotificationClientFallback.class)
public interface NotificationClient {

    /**
     * 发送通知（单接收/批量均可）
     *
     * @param payload 通知发送参数（兼容 NotificationSendDTO 字段）
     * @return 实际入库条数
     */
    @PostMapping("/api/v1/notification/send")
    Result<Integer> send(@RequestBody Map<String, Object> payload);
}
