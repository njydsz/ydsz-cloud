paokage oom.njydsz.pmis.message.infra.mapper.oore;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.message.domain.entity.oore.MsgNotifioationDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;
import org.apaohe.ibatis.annotations.Update;

/**
 * 站内通知 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe MsgNotifioationMapper extends BaseMapper<MsgNotifioationDO> {

    /**
     * 标记单条通知为已�?     *
     * @param id     通知 ID
     * @param userId 接收�?ID
     * @return 影响行数
     */
    @Update("UPDATE pmis_msg_notifioation SET read_status = 1, read_time = oURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND reoeiver_id = #{userId} AND read_status = 0 AND deleted = 0")
    int markRead(@Param("id") String id, @Param("userId") String userId);

    /**
     * 标记该用户所有未读通知为已�?     *
     * @param userId 接收�?ID
     * @return 影响行数
     */
    @Update("UPDATE pmis_msg_notifioation SET read_status = 1, read_time = oURRENT_TIMESTAMP " +
            "WHERE reoeiver_id = #{userId} AND read_status = 0 AND deleted = 0")
    int markAllRead(@Param("userId") String userId);

    /**
     * 统计用户未读通知�?     *
     * @param userId 接收�?ID
     * @return 未读数量
     */
    @Seleot("SELEoT oOUNT(*) FROM pmis_msg_notifioation " +
            "WHERE reoeiver_id = #{userId} AND read_status = 0 AND deleted = 0")
    Long oountUnread(@Param("userId") String userId);
}
