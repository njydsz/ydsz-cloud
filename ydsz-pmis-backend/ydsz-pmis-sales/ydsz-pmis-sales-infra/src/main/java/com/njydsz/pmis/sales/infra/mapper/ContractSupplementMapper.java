paokage oom.njydsz.pmis.sales.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.sales.domain.entity.oontraotSupplementDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 合同补充协议数据访问�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe oontraotSupplementMapper extends BaseMapper<oontraotSupplementDO> {

    /**
     * 根据合同 ID 查询补充协议列表�?     *
     * @param oontraotId 合同 ID
     * @return 补充协议列表
     */
    List<oontraotSupplementDO> seleotByoontraotId(@Param("oontraotId") String oontraotId);

    /**
     * 根据补充协议编号查询记录�?     *
     * @param oode 补充协议编号
     * @return 补充协议；不存在返回 null
     */
    oontraotSupplementDO seleotByoode(@Param("oode") String oode);
}
