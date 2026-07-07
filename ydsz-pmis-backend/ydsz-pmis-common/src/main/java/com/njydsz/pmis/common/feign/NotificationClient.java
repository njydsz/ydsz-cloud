package com.njydsz.pmis.common.feign;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.dto.NotificationFeignDTO;
import com.njydsz.pmis.common.feign.dto.RealtimePushDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

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
@FeignClient(name = "ydsz-pmis-message", fallbackFactory = NotificationClientFallback.class)
public interface NotificationClient {

    /**
     * 发送通知（单接收/批量均可）
     *
     * @param payload 通知发送参数（与 NotificationSendDTO 字段对齐）
     * @return 实际入库条数
     */
    @PostMapping("/notifications/send")
    Result<Integer> send(@RequestBody NotificationFeignDTO payload);

    /**
     * P1-7: 实时推送消息到指定用户（WebSocket）
     *
     * <p>工作流引擎通过本接口在任务创建/完成时向办理人推送实时消息，
     * 包括待办数更新 / 通知消息等。前端订阅 /user/{userId}/queue/notifications 即可接收。
     *
     * @param userId  接收用户 ID
     * @param type    消息类型 (TASK_ASSIGNED/TASK_COMPLETED/TODO_COUNT/NOTIFICATION)
     * @param payload 消息内容
     * @return 推送结果
     */
    @PostMapping("/notifications/push")
    Result<Map<String, Object>> pushRealtime(@RequestParam("userId") String userId,
                                             @RequestParam("type") String type,
                                             @RequestBody RealtimePushDTO payload);
}
