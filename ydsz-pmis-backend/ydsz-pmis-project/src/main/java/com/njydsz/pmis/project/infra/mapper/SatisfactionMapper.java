package com.njydsz.pmis.project.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.domain.entity.SatisfactionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 满意度调查 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface SatisfactionMapper extends BaseMapper<SatisfactionDO> {

    /**
     * 按编码查询满意度评价
     *
     * @param code 满意度编码
     * @return 满意度对象，未找到返回 null
     */
    SatisfactionDO selectByCode(@Param("code") String code);

    /**
     * 按立项 ID 查询满意度评价列表
     *
     * @param initiationId 立项 ID
     * @return 满意度列表
     */
    List<SatisfactionDO> selectByInitiation(@Param("initiationId") String initiationId);

    /**
     * 按工单 ID 查询满意度评价列表
     *
     * @param ticketId 工单 ID
     * @return 满意度列表
     */
    List<SatisfactionDO> selectByTicket(@Param("ticketId") Long ticketId);

    /**
     * 整体满意度均值：score / professionalism / timeliness / quality / attitude
     *
     * @return 整体满意度均值数据
     */
    Map<String, Object> aggregateOverall();

    /**
     * 各等级分布
     *
     * @return 等级分布列表
     */
    List<Map<String, Object>> aggregateByLevel();
}
