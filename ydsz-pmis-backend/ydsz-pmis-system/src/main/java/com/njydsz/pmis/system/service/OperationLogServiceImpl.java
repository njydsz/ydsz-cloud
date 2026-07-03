package com.njydsz.pmis.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.system.entity.OperationLogDO;
import com.njydsz.pmis.system.mapper.OperationLogMapper;
import com.njydsz.pmis.common.entity.CursorPageResult;
import com.njydsz.pmis.common.util.CursorHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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

    /** 操作日志 Mapper */
    private final OperationLogMapper operationLogMapper;

    /**
     * 分页查询操作日志
     *
     * @param page      页码
     * @param size      每页条数
     * @param userId    用户 ID
     * @param bizType   业务类型
     * @param status    状态
     * @param module    模块名
     * @param startTime 起始时间（包含），可为 null
     * @param endTime   截止时间（包含），可为 null
     * @return 分页结果
     */
    public Page<OperationLogDO> page(int page, int size, Long userId, String bizType,
                                     String status, String module,
                                     LocalDateTime startTime, LocalDateTime endTime) {
        Page<OperationLogDO> p = new Page<>(page, size);
        LambdaQueryWrapper<OperationLogDO> w = new LambdaQueryWrapper<>();
        if (userId != null) w.eq(OperationLogDO::getUserId, userId);
        if (StringUtils.hasText(bizType)) w.eq(OperationLogDO::getBizType, bizType);
        if (StringUtils.hasText(status)) w.eq(OperationLogDO::getStatus, status);
        if (StringUtils.hasText(module)) w.eq(OperationLogDO::getModule, module);
        if (startTime != null) w.ge(OperationLogDO::getCreatedAt, startTime);
        if (endTime != null) w.le(OperationLogDO::getCreatedAt, endTime);
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

    /**
     * 根据 ID 查询操作日志
     *
     * @param id 日志 ID
     * @return 操作日志实体，不存在返回 null
     */
    public OperationLogDO getById(Long id) {
        return operationLogMapper.selectById(id);
    }

    /**
     * 游标分页查询操作日志（P2-8 深翻优化）
     *
     * <p>使用 keyset pagination 替代 OFFSET，深翻性能 O(1) 不随页码增长。
     * 排序规则：created_at DESC, id DESC（确定性排序）。
     *
     * <p>cursor 编码格式：Base64("createdAt|id")
     *
     * @param size      每页大小
     * @param cursor    游标（首次请求传 null）
     * @param userId    用户 ID（可选过滤）
     * @param bizType   业务类型（可选过滤）
     * @param status    状态（可选过滤）
     * @param module    模块名（可选过滤）
     * @param startTime 起始时间（可选过滤）
     * @param endTime   截止时间（可选过滤）
     * @return 游标分页结果
     */
    public CursorPageResult<OperationLogDO> pageByCursor(long size, String cursor,
                                                          Long userId, String bizType,
                                                          String status, String module,
                                                          LocalDateTime startTime,
                                                          LocalDateTime endTime) {
        long safeSize = Math.min(Math.max(size, 1), 200);
        // 多查 1 条用于判断 hasMore
        long queryLimit = safeSize + 1;

        LambdaQueryWrapper<OperationLogDO> w = new LambdaQueryWrapper<>();
        if (userId != null) w.eq(OperationLogDO::getUserId, userId);
        if (StringUtils.hasText(bizType)) w.eq(OperationLogDO::getBizType, bizType);
        if (StringUtils.hasText(status)) w.eq(OperationLogDO::getStatus, status);
        if (StringUtils.hasText(module)) w.eq(OperationLogDO::getModule, module);
        if (startTime != null) w.ge(OperationLogDO::getCreatedAt, startTime);
        if (endTime != null) w.le(OperationLogDO::getCreatedAt, endTime);

        // 游标条件：WHERE (created_at < cursor_created_at) OR (created_at = cursor_created_at AND id < cursor_id)
        if (cursor != null && !cursor.isBlank()) {
            Object[] decoded = CursorHelper.decode(cursor);
            if (decoded != null) {
                LocalDateTime cursorTime = (LocalDateTime) decoded[0];
                Long cursorId = (Long) decoded[1];
                w.and(wrapper -> wrapper
                        .lt(OperationLogDO::getCreatedAt, cursorTime)
                        .or(sub -> sub
                                .eq(OperationLogDO::getCreatedAt, cursorTime)
                                .lt(OperationLogDO::getId, cursorId)));
            }
        }

        // 确定性排序：created_at DESC, id DESC
        w.orderByDesc(OperationLogDO::getCreatedAt)
         .orderByDesc(OperationLogDO::getId)
         .last("LIMIT " + queryLimit);

        List<OperationLogDO> records = operationLogMapper.selectList(w);
        return CursorPageResult.of(records,
                log -> CursorHelper.encode(log.getCreatedAt(), log.getId()),
                safeSize);
    }
}
