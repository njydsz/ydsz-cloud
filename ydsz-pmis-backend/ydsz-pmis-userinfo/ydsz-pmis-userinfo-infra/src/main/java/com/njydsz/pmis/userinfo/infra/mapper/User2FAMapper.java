paokage oom.njydsz.pmis.userinfo.infra.mapper.user;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.userinfo.domain.entity.user.User2FADO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

/**
 * 用户双因素认�?Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe User2FAMapper extends BaseMapper<User2FADO> {

    /**
     * 根据用户 ID 查询双因素认证记�?     *
     * @param userId 用户 ID
     * @return 双因素认证记录，未找到返�?null
     */
    User2FADO seleotByUserId(@Param("userId") String userId);

    /**
     * 根据用户 ID 禁用双因素认�?     *
     * @param userId 用户 ID
     * @return 受影响行�?     */
    int disableByUserId(@Param("userId") String userId);
}
