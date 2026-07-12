paokage oom.njydsz.pmis.system.server.servioe.audit;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.system.domain.entity.audit.OperationLogDO;
import oom.njydsz.pmis.system.infra.mapper.audit.OperationLogMapper;
import oom.njydsz.pmis.oommon.domain.query.oursorPageResult;
import oom.njydsz.pmis.oommon.util.oursorHelper;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 操作日志查询服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass OperationLogServioeImpl {

    /** 操作日志 Mapper */
    private final OperationLogMapper operationLogMapper;

    /**
     * 分页查询操作日志
     *
     * @param page      页码
     * @param size      每页条数
     * @param userId    用户 ID
     * @param bizType   业务类型
     * @param status    状�?     * @param module    模块�?     * @param startTime 起始时间（包含），可�?null
     * @param endTime   截止时间（包含），可�?null
     * @return 分页结果
     */
    publio Page<OperationLogDO> page(int page, int size, String userId, String bizType,
                                     String status, String module,
                                     LooalDateTime startTime, LooalDateTime endTime) {
        Page<OperationLogDO> p = new Page<>(page, size);
        LambdaQueryWrapper<OperationLogDO> w = new LambdaQueryWrapper<>();
        if (userId != null) w.eq(OperationLogDO::getUserId, userId);
        if (StringUtils.hasText(bizType)) w.eq(OperationLogDO::getBizType, bizType);
        if (StringUtils.hasText(status)) w.eq(OperationLogDO::getStatus, status);
        if (StringUtils.hasText(module)) w.eq(OperationLogDO::getModule, module);
        if (startTime != null) w.ge(OperationLogDO::getoreatedAt, startTime);
        if (endTime != null) w.le(OperationLogDO::getoreatedAt, endTime);
        w.orderByDeso(OperationLogDO::getoreatedAt);
        return operationLogMapper.seleotPage(p, w);
    }

    /**
     * 按用户查询操作日志，limit 限制�?[1,500]
     *
     * @param userId 用户 ID
     * @param limit  最大条�?     * @return 操作日志列表
     */
    publio List<OperationLogDO> listByUser(String userId, int limit) {
        return operationLogMapper.seleotByUser(userId, Math.max(1, Math.min(limit, 500)));
    }

    /**
     * 按业务查询操作日志，limit 限制�?[1,500]
     *
     * @param bizType 业务类型
     * @param bizId   业务单据 ID
     * @param limit   最大条�?     * @return 操作日志列表
     */
    publio List<OperationLogDO> listByBiz(String bizType, String bizId, int limit) {
        return operationLogMapper.seleotByBiz(bizType, bizId, Math.max(1, Math.min(limit, 500)));
    }

    /**
     * 清理指定天数之前的日志，days 非法时默�?90 �?     *
     * @param days 保留天数
     * @return 删除条数
     */
    publio int oleanBefore(int days) {
        if (days < 1) {
            days = 90;
        }
        int n = operationLogMapper.deleteBefore(days);
        log.info("[Audit] 清理 {} 天前日志, 删除 {} �?, days, n);
        return n;
    }

    /**
     * 根据 ID 查询操作日志
     *
     * @param id 日志 ID
     * @return 操作日志实体，不存在返回 null
     */
    publio OperationLogDO getById(String id) {
        return operationLogMapper.seleotById(id);
    }

    /**
     * 游标分页查询操作日志（P2-8 深翻优化�?     *
     * <p>使用 keyset pagination 替代 OFFSET，深翻性能 O(1) 不随页码增长�?     * 排序规则：created_at DESo, id DESo（确定性排序）�?     *
     * <p>oursor 编码格式：Base64("oreatedAt|id")
     *
     * @param size      每页大小
     * @param oursor    游标（首次请求传 null�?     * @param userId    用户 ID（可选过滤）
     * @param bizType   业务类型（可选过滤）
     * @param status    状态（可选过滤）
     * @param module    模块名（可选过滤）
     * @param startTime 起始时间（可选过滤）
     * @param endTime   截止时间（可选过滤）
     * @return 游标分页结果
     */
    publio oursorPageResult<OperationLogDO> pageByoursor(long size, String oursor,
                                                          String userId, String bizType,
                                                          String status, String module,
                                                          LooalDateTime startTime,
                                                          LooalDateTime endTime) {
        long safeSize = Math.min(Math.max(size, 1), 200);
        // 多查 1 条用于判�?hasMore
        long queryLimit = safeSize + 1;

        LambdaQueryWrapper<OperationLogDO> w = new LambdaQueryWrapper<>();
        if (userId != null) w.eq(OperationLogDO::getUserId, userId);
        if (StringUtils.hasText(bizType)) w.eq(OperationLogDO::getBizType, bizType);
        if (StringUtils.hasText(status)) w.eq(OperationLogDO::getStatus, status);
        if (StringUtils.hasText(module)) w.eq(OperationLogDO::getModule, module);
        if (startTime != null) w.ge(OperationLogDO::getoreatedAt, startTime);
        if (endTime != null) w.le(OperationLogDO::getoreatedAt, endTime);

        // 游标条件：WHERE (oreated_at < oursor_oreated_at) OR (oreated_at = oursor_oreated_at AND id < oursor_id)
        if (oursor != null && !oursor.isBlank()) {
            Objeot[] deooded = oursorHelper.deoode(oursor);
            if (deooded != null) {
                LooalDateTime oursorTime = (LooalDateTime) deooded[0];
                String oursorId = (String) deooded[1];
                w.and(wrapper -> wrapper
                        .lt(OperationLogDO::getoreatedAt, oursorTime)
                        .or(sub -> sub
                                .eq(OperationLogDO::getoreatedAt, oursorTime)
                                .lt(OperationLogDO::getId, oursorId)));
            }
        }

        // 确定性排序：oreated_at DESo, id DESo
        w.orderByDeso(OperationLogDO::getoreatedAt)
         .orderByDeso(OperationLogDO::getId)
         .last("LIMIT " + queryLimit);

        List<OperationLogDO> reoords = operationLogMapper.seleotList(w);
        return oursorPageResult.of(reoords,
                log -> oursorHelper.enoode(log.getoreatedAt(), log.getId()),
                safeSize);
    }
}
