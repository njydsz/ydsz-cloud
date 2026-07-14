package com.njydsz.pmis.message.infra.mapper.core;

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
     * 标记单条通知为已读(XML 定义)
     *
     * @param id     通知 ID
     * @param userId 接收人 ID
     * @return 影响行数
     */
    int markRead(@Param("id") String id, @Param("userId") String userId);

    /**
     * 标记该用户所有未读通知为已读(XML 定义)
     *
     * @param userId 接收人 ID
     * @return 影响行数
     */
    int markAllRead(@Param("userId") String userId);

    /**
     * 统计用户未读通知数(XML 定义)
     *
     * @param userId 接收人 ID
     * @return 未读数量
     */
    Long countUnread(@Param("userId") String userId);
}
