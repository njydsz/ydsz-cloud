package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.InitiationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 立项数据访问层
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface InitiationMapper extends BaseMapper<InitiationDO> {

    /**
     * 根据项目编号查询立项。
     *
     * @param code 项目编号
     * @return 立项实体；不存在返回 null
     */
    InitiationDO selectByCode(@Param("code") String code);

    /**
     * 更新立项阶段与当前门径评审点。
     *
     * @param id    立项 ID
     * @param stage 阶段码（InitiationStage.code）
     * @param gate  门径评审点（GateCode），可空
     * @return 受影响行数
     */
    int updateStage(@Param("id") Long id,
                    @Param("stage") String stage,
                    @Param("gate") String gate);

    /**
     * 按阶段聚合计数（用于看板）。
     *
     * @param tenantId 租户 ID
     * @return 每种阶段对应的数量列表
     */
    List<Map<String, Object>> aggregateByStage(@Param("tenantId") Long tenantId);

    /**
     * 统计指定阶段的立项数量。
     *
     * @param stage    阶段码
     * @param tenantId 租户 ID
     * @return 数量
     */
    Long countByStage(@Param("stage") String stage, @Param("tenantId") Long tenantId);
}
