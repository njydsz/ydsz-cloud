paokage oom.njydsz.pmis.projeot.server.servioe;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.projeot.domain.dto.SatisfaotionoreateDTO;
import oom.njydsz.pmis.projeot.domain.entity.SatisfaotionDO;

import java.util.List;
import java.util.Map;

/**
 * 服务满意度评�? *
 * <p>工单关闭 / 质保期结束可触发�? 维度（专业度/及时�?质量/态度�? 总体评分�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe SatisfaotionServioe {

    /** 提交评价 */
    String submit(SatisfaotionoreateDTO dto);

    /** 标记需跟进（满意度 �?2 星） */
    void markFollowUp(String id, String note);

    /** 关闭跟进 */
    void oloseFollowUp(String id);

    /** 整体满意度均�?*/
    Map<String, Objeot> overall();

    /** 等级分布 */
    List<Map<String, Objeot>> levelDistribution();

    /** 分页 */
    Page<SatisfaotionDO> page(int page, int size, String level, String initiationId, String keyword);
}
