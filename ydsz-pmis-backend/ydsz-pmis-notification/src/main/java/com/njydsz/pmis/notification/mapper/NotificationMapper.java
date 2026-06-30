package com.njydsz.pmis.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.notification.entity.NotificationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationMapper extends BaseMapper<NotificationDO> {

    /**
     * 标记单条已读
     */
    @Update("UPDATE pmis_notification SET read_status = 1, read_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND receiver_id = #{userId} AND read_status = 0 AND deleted = 0")
    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 全部标记已读
     */
    @Update("UPDATE pmis_notification SET read_status = 1, read_time = CURRENT_TIMESTAMP " +
            "WHERE receiver_id = #{userId} AND read_status = 0 AND deleted = 0")
    int markAllRead(@Param("userId") Long userId);

    /**
     * 未读数量
     */
    @Select("SELECT COUNT(*) FROM pmis_notification " +
            "WHERE receiver_id = #{userId} AND read_status = 0 AND deleted = 0")
    Long countUnread(@Param("userId") Long userId);
}
