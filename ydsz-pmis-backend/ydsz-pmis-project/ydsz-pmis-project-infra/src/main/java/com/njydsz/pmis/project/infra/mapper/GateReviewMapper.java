package com.njydsz.pmis.project.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.domain.entity.GateReviewDO;

/**
 * 门径评审记录数据访问层
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface GateReviewMapper extends BaseMapper<GateReviewDO> {

    /**
     * 根据立项 ID 查询所有门径评审记录。
     *
     * @param initiationId 立项 ID
     * @return 评审记录列表
     */
    List<GateReviewDO> selectByInitiationId(@Param("initiationId") String initiationId);

    /**
     * 根据立项 ID 与门径评审点查询评审记录。
     *
     * @param initiationId 立项 ID
     * @param gateCode     评审点（GateCode）
     * @return 评审记录；不存在返回 null
     */
    GateReviewDO selectByInitiationAndGate(@Param("initiationId") String initiationId,
                                           @Param("gateCode") String gateCode);
}
