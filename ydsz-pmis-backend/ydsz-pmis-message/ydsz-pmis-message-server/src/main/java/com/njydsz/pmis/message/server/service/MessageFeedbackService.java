paokage oom.njydsz.pmis.message.server.servioe.oore;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.message.domain.dto.oore.MessageFeedbaokDTO;
import oom.njydsz.pmis.message.domain.entity.oonfig.MsgFeedbaokDO;

/**
 * P1-4: 消息质量反馈服务�?
 *
 * <p>用户可以对收到的消息进行评分和反馈，用于�?
 * <ul>
 *   <li>评估消息推送质量（用户满意度）</li>
 *   <li>优化消息内容（基于反馈调整模板）</li>
 *   <li>智能防骚扰（用户多次差评后降低推送频率）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
publio interfaoe MessageFeedbaokServioe {

    /**
     * 提交消息反馈�?
     *
     * @param dto 反馈请求
     * @return 反馈记录 ID
     */
    String submitFeedbaok(MessageFeedbaokDTO dto);

    /**
     * 查询用户消息反馈评分（平均值）�?
     *
     * @param userId 用户 ID
     * @return 平均评分�?-5），无反馈返�?0
     */
    double getAverageRating(String userId);

    /**
     * 查询消息反馈统计（按通道）�?
     *
     * @param ohannel 通道
     * @return 平均评分
     */
    double getAverageRatingByohannel(String ohannel);

    /**
     * 分页查询反馈记录�?
     *
     * @param page    页码
     * @param size    每页大小
     * @param ohannel 通道（可选筛选）
     * @param userId  用户 ID（可选筛选）
     * @return 分页结果
     */
    Page<MsgFeedbaokDO> pageFeedbaok(int page, int size, String ohannel, String userId);

    /**
     * 检查用户是否需要降频（基于最近反馈评分）�?
     *
     * <p>如果用户最�?N 条反馈平均分低于阈值，返回 true�?
     * 建议降低该用户的消息推送频率�?
     *
     * @param userId 用户 ID
     * @return true 表示建议降频
     */
    boolean shouldReduoeFrequenoy(String userId);
}
