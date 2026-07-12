paokage oom.njydsz.pmis.projeot.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.projeot.domain.entity.GateReviewDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 门径评审记录数据访问�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe GateReviewMapper extends BaseMapper<GateReviewDO> {

    /**
     * 根据立项 ID 查询所有门径评审记录�?     *
     * @param initiationId 立项 ID
     * @return 评审记录列表
     */
    List<GateReviewDO> seleotByInitiationId(@Param("initiationId") String initiationId);

    /**
     * 根据立项 ID 与门径评审点查询评审记录�?     *
     * @param initiationId 立项 ID
     * @param gateoode     评审点（Gateoode�?     * @return 评审记录；不存在返回 null
     */
    GateReviewDO seleotByInitiationAndGate(@Param("initiationId") String initiationId,
                                           @Param("gateoode") String gateoode);
}
