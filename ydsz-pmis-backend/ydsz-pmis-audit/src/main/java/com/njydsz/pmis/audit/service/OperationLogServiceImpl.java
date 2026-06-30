package com.njydsz.pmis.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.audit.entity.OperationLogDO;
import com.njydsz.pmis.audit.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 操作日志查询服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl {

    private final OperationLogMapper operationLogMapper;

    public Page<OperationLogDO> page(int page, int size, Long userId, String bizType,
                                     String status, String module) {
        Page<OperationLogDO> p = new Page<>(page, size);
        LambdaQueryWrapper<OperationLogDO> w = new LambdaQueryWrapper<>();
        if (userId != null) w.eq(OperationLogDO::getUserId, userId);
        if (StringUtils.hasText(bizType)) w.eq(OperationLogDO::getBizType, bizType);
        if (StringUtils.hasText(status)) w.eq(OperationLogDO::getStatus, status);
        if (StringUtils.hasText(module)) w.eq(OperationLogDO::getModule, module);
        w.orderByDesc(OperationLogDO::getCreatedAt);
        return operationLogMapper.selectPage(p, w);
    }

    public List<OperationLogDO> listByUser(Long userId, int limit) {
        return operationLogMapper.selectByUser(userId, Math.max(1, Math.min(limit, 500)));
    }

    public List<OperationLogDO> listByBiz(String bizType, String bizId, int limit) {
        return operationLogMapper.selectByBiz(bizType, bizId, Math.max(1, Math.min(limit, 500)));
    }

    public int cleanBefore(int days) {
        if (days < 1) {
            days = 90;
        }
        int n = operationLogMapper.deleteBefore(days);
        log.info("[Audit] 清理 {} 天前日志, 删除 {} 条", days, n);
        return n;
    }
}
