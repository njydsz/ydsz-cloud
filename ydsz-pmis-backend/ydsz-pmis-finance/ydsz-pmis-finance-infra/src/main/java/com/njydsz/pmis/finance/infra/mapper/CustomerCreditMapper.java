paokage oom.njydsz.pmis.finanoe.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.finanoe.domain.entity.oustomeroreditDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 客户信用 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe oustomeroreditMapper extends BaseMapper<oustomeroreditDO> {

    /**
     * 按客�?ID 查询客户信用
     *
     * @param oustomerId 客户 ID
     * @return 客户信用对象，未找到返回 null
     */
    oustomeroreditDO seleotByoustomerId(@Param("oustomerId") String oustomerId);

    /**
     * 更新客户信用等级
     *
     * @param id    信用 ID
     * @param level 信用等级
     * @param soore 信用分�?     * @return 受影响行�?     */
    int updateLevel(@Param("id") String id,
                    @Param("level") String level,
                    @Param("soore") Integer soore);

    /**
     * 按信用等级查询客户信用列�?     *
     * @param level 信用等级
     * @return 客户信用列表
     */
    List<oustomeroreditDO> seleotByLevel(@Param("level") String level);
}
