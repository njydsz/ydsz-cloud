package com.njydsz.pmis.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.entity.MsgNotificationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 站内通知 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface MsgNotificationMapper extends BaseMapper<MsgNotificationDO> {

    /**
     * 标记单条通知为已读
     *
     * @param id     通知 ID
     * @param userId 接收人 ID
     * @return 影响行数
     */
    @Update("UPDATE pmis_msg_notification SET read_status = 1, read_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND receiver_id = #{userId} AND read_status = 0 AND deleted = 0")
    int markRead(@Param("id") String id, @Param("userId") String userId);

    /**
     * 标记该用户所有未读通知为已读
     *
     * @param userId 接收人 ID
     * @return 影响行数
     */
    @Update("UPDATE pmis_msg_notification SET read_status = 1, read_time = CURRENT_TIMESTAMP " +
            "WHERE receiver_id = #{userId} AND read_status = 0 AND deleted = 0")
    int markAllRead(@Param("userId") String userId);

    /**
     * 统计用户未读通知数
     *
     * @param userId 接收人 ID
     * @return 未读数量
     */
    @Select("SELECT COUNT(*) FROM pmis_msg_notification " +
            "WHERE receiver_id = #{userId} AND read_status = 0 AND deleted = 0")
    Long countUnread(@Param("userId") String userId);
}
