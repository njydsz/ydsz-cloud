package com.njydsz.pmis.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.SatisfactionCreateDTO;
import com.njydsz.pmis.project.entity.SatisfactionDO;

import java.util.List;
import java.util.Map;

/**
 * 服务满意度评价
 *
 * <p>工单关闭 / 质保期结束可触发；4 维度（专业度/及时性/质量/态度）+ 总体评分。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface SatisfactionService {

    /** 提交评价 */
    Long submit(SatisfactionCreateDTO dto);

    /** 标记需跟进（满意度 ≤ 2 星） */
    void markFollowUp(Long id, String note);

    /** 关闭跟进 */
    void closeFollowUp(Long id);

    /** 整体满意度均值 */
    Map<String, Object> overall();

    /** 等级分布 */
    List<Map<String, Object>> levelDistribution();

    /** 分页 */
    Page<SatisfactionDO> page(int page, int size, String level, Long initiationId, String keyword);
}
