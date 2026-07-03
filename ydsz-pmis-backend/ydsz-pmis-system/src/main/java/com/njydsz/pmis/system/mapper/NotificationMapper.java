package com.njydsz.pmis.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.notification.entity.NotificationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 通知 Mapper
 *
 * <p>对应 pmis_notification 表，提供已读标记、未读计数等站内通知持久化能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface NotificationMapper extends BaseMapper<NotificationDO> {

    /**
     * 标记单条已读
     *
     * @param id     通知 ID
     * @param userId 接收人 ID
     * @return 实际更新条数（0 表示通知不存在或不属于该用户）
     */
    @Update("UPDATE pmis_notification SET read_status = 1, read_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND receiver_id = #{userId} AND read_status = 0 AND deleted = 0")
    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 全部标记已读
     *
     * @param userId 接收人 ID
     * @return 实际标记条数
     */
    @Update("UPDATE pmis_notification SET read_status = 1, read_time = CURRENT_TIMESTAMP " +
            "WHERE receiver_id = #{userId} AND read_status = 0 AND deleted = 0")
    int markAllRead(@Param("userId") Long userId);

    /**
     * 未读数量
     *
     * @param userId 接收人 ID
     * @return 未读通知数
     */
    @Select("SELECT COUNT(*) FROM pmis_notification " +
            "WHERE receiver_id = #{userId} AND read_status = 0 AND deleted = 0")
    Long countUnread(@Param("userId") Long userId);
}
