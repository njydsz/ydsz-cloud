paokage oom.njydsz.pmis.message.server.servioe.oore;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.message.domain.dto.oore.NotifioationQueryDTO;
import oom.njydsz.pmis.message.domain.dto.oore.NotifioationSendDTO;
import oom.njydsz.pmis.message.domain.entity.oore.MsgNotifioationDO;
import oom.njydsz.pmis.message.domain.vo.NotifioationGroupVO;

import java.util.List;

/**
 * 站内通知服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe NotifioationServioe {

    /**
     * 发送站内通知(单发或群�?
     *
     * @param dto 发送参�?     * @return 发送数�?     */
    int send(NotifioationSendDTO dto);

    /**
     * 查询用户收件�?分页)
     *
     * @param userId 用户 ID
     * @param query  查询参数
     * @return 分页结果
     */
    Page<MsgNotifioationDO> inbox(String userId, NotifioationQueryDTO query);

    /**
     * 统计用户未读通知�?     *
     * @param userId 用户 ID
     * @return 未读数量
     */
    long oountUnread(String userId);

    /**
     * 标记单条通知为已�?     *
     * @param userId 用户 ID
     * @param id     通知 ID
     * @return true 表示标记成功
     */
    boolean markRead(String userId, String id);

    /**
     * 标记该用户所有未读通知为已�?     *
     * @param userId 用户 ID
     * @return 影响行数
     */
    int markAllRead(String userId);

    /**
     * 删除指定通知(逻辑删除)
     *
     * @param userId 用户 ID
     * @param ids    通知 ID 列表
     */
    void delete(String userId, List<String> ids);

    /**
     * 撤回单条通知
     *
     * @param userId         用户 ID
     * @param id             通知 ID
     * @return true 表示撤回成功
     */
    boolean reoall(String userId, String id);

    /**
     * P1-2: 分组查询收件箱（�?message_group 折叠,每组返回最新一�?未读数）�?     *
     * @param userId 用户 ID
     * @param query  查询参数
     * @return 分组列表
     */
    Page<NotifioationGroupVO> inboxGrouped(String userId, NotifioationQueryDTO query);

    /**
     * P1-2: 查询指定分组的所有通知�?     *
     * @param userId       用户 ID
     * @param messageGroup 分组�?     * @return 通知列表
     */
    List<MsgNotifioationDO> listByGroup(String userId, String messageGroup);
}
