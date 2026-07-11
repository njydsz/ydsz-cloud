package com.njydsz.pmis.system.server.service.impl.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.system.domain.entity.audit.LoginAuditDO;
import com.njydsz.pmis.system.infra.mapper.audit.LoginAuditMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 登录审计服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAuditServiceImpl {

    private final LoginAuditMapper loginAuditMapper;

    /**
     * 记录登录审计
     *
     * @param entity 登录审计实体
     */
    public void record(LoginAuditDO entity) {
        loginAuditMapper.insertLogin(entity);
        log.info("[LoginAudit] 记录登录审计: username={} status={}", entity.getUsername(), entity.getStatus());
    }

    /**
     * 分页查询登录审计
     *
     * @param page     页码
     * @param size     每页大小
     * @param username 用户名（可选）
     * @param status   状态（可选）
     * @param loginIp  登录IP（可选）
     * @return 分页结果
     */
    public Page<LoginAuditDO> page(int page, int size, String username, String status, String loginIp) {
        Page<LoginAuditDO> p = new Page<>(page, size);
        LambdaQueryWrapper<LoginAuditDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) w.like(LoginAuditDO::getUsername, username);
        if (StringUtils.hasText(status)) w.eq(LoginAuditDO::getStatus, status);
        if (StringUtils.hasText(loginIp)) w.like(LoginAuditDO::getLoginIp, loginIp);
        w.orderByDesc(LoginAuditDO::getLoginAt);
        return loginAuditMapper.selectPage(p, w);
    }

    /**
     * 按用户名查询登录历史
     *
     * @param username 用户名
     * @param limit    最大条数
     * @return 登录审计列表
     */
    public List<LoginAuditDO> listByUsername(String username, int limit) {
        return loginAuditMapper.selectByUsername(username, Math.max(1, Math.min(limit, 500)));
    }

    /**
     * 统计某 IP 短期登录失败次数
     *
     * @param ip           登录 IP
     * @param status       登录状态
     * @param sinceMinutes 统计时间窗口(分钟)
     * @return 登录次数
     */
    public long countByIp(String ip, String status, int sinceMinutes) {
        return loginAuditMapper.countByIpSince(ip, status, sinceMinutes);
    }
}