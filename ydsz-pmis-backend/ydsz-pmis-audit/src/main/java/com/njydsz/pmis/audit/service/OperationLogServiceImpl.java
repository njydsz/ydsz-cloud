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

    /**
     * 分页查询操作日志
     *
     * @param page    页码
     * @param size    每页条数
     * @param userId  用户 ID
     * @param bizType 业务类型
     * @param status  状态
     * @param module  模块名
     * @return 分页结果
     */
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

    /**
     * 按用户查询操作日志，limit 限制在 [1,500]
     *
     * @param userId 用户 ID
     * @param limit  最大条数
     * @return 操作日志列表
     */
    public List<OperationLogDO> listByUser(Long userId, int limit) {
        return operationLogMapper.selectByUser(userId, Math.max(1, Math.min(limit, 500)));
    }

    /**
     * 按业务查询操作日志，limit 限制在 [1,500]
     *
     * @param bizType 业务类型
     * @param bizId   业务单据 ID
     * @param limit   最大条数
     * @return 操作日志列表
     */
    public List<OperationLogDO> listByBiz(String bizType, String bizId, int limit) {
        return operationLogMapper.selectByBiz(bizType, bizId, Math.max(1, Math.min(limit, 500)));
    }

    /**
     * 清理指定天数之前的日志，days 非法时默认 90 天
     *
     * @param days 保留天数
     * @return 删除条数
     */
    public int cleanBefore(int days) {
        if (days < 1) {
            days = 90;
        }
        int n = operationLogMapper.deleteBefore(days);
        log.info("[Audit] 清理 {} 天前日志, 删除 {} 条", days, n);
        return n;
    }
}
