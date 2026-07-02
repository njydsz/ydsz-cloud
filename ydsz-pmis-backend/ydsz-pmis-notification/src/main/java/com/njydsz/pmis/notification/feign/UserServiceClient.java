package com.njydsz.pmis.notification.feign;

import com.njydsz.pmis.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 用户服务 Feign 客户端（通知模块专用）
 *
 * <p>用于通知发送时获取接收人邮箱（邮件通道）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-pmis-user", fallbackFactory = UserServiceClientFallback.class)
public interface UserServiceClient {

    /**
     * 获取员工基本信息（含 email/phone）
     */
    @GetMapping("/api/v1/user/employee/{id}")
    Result<Map<String, Object>> getEmployee(@PathVariable("id") Long id);
}
