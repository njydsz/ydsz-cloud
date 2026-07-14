package com.njydsz.pmis.message.infra.mapper.core;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.domain.entity.core.MsgNotificationDO;

/**
 * 站内通知 Mapper
 *
 * <p>P2-3: markRead/markAllRead/countUnread 的 SQL 统一由 XML 定义,
 * 移除注解冗余 SQL 避免与 XML 冲突。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface MsgNotificationMapper extends BaseMapper<MsgNotificationDO> {

    /**
     * P3-6: 批量插入站内通知（XML foreach 单条 INSERT VALUES (...), (...)）。
     *
     * <p>调用方需在传入前用 {@code IdWorker.getIdStr()} 预生成 ID 赋给每个 entity，
     * 以保证批量 insert 后能拿到主键。
     *
     * @param list 通知实体列表
     * @return 影响行数
     */
    int insertBatch(@Param("list") List<MsgNotificationDO> list);

    /**
     * 标记单条通知为已读(XML 定义)
     *
     * @param id     通知 ID
     * @param userId 接收人 ID
     * @return 影响行数
     */
    int markRead(@Param("id") String id, @Param("userId") String userId);

    /**
     * 标记该用户所有未读通知为已读(XML 定义)。
     *
     * <p>P2-6: 增加 {@code batchSize} 参数实现分批 UPDATE，避免单次 UPDATE
     * 万级未读通知导致的长事务与行锁堆积。调用方需循环调用直到返回值 &lt; batchSize。
     *
     * @param userId    接收人 ID
     * @param batchSize 单批最大处理条数（&lt;= 0 时不限制，兼容旧逻辑）
     * @return 本批影响行数
     */
    int markAllRead(@Param("userId") String userId, @Param("batchSize") int batchSize);

    /**
     * 统计用户未读通知数(XML 定义)
     *
     * @param userId 接收人 ID
     * @return 未读数量
     */
    Long countUnread(@Param("userId") String userId);
}
