package com.njydsz.userinfo.server.service;

import com.njydsz.userinfo.domain.entity.UserLoginHistory;

/**
 * 登录历史服务接口
 *
 * <p>提供登录历史记录和 IP 封禁策略功能。
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li>记录登录尝试（成功/失败）</li>
 *   <li>检查 IP 是否被封禁</li>
 *   <li>查询用户最近登录记录</li>
 *   <li>统计 IP 登录失败次数</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface LoginHistoryService {

    /**
     * 记录登录尝试
     *
     * @param userId 用户 ID（可能为 null，如用户名不存在时）
     * @param username 用户名
     * @param loginIp 登录 IP
     * @param result 登录结果（SUCCESS / FAILED）
     * @param failReason 失败原因
     * @param userAgent 用户代理
     */
    void recordLoginAttempt(String userId, String username, String loginIp,
                            String result, String failReason, String userAgent);

    /**
     * 检查 IP 是否被封禁
     *
     * <p>如果 IP 在过去 N 分钟内失败次数超过阈值，则视为被封禁。
     *
     * @param ip IP 地址
     * @return true 表示被封禁；false 表示正常
     */
    boolean isIpBlocked(String ip);

    /**
     * 查询用户最近登录记录
     *
     * @param userId 用户 ID
     * @param limit 返回记录数上限
     * @return 登录历史列表
     */
    java.util.List<UserLoginHistory> getRecentLogins(String userId, int limit);
}
