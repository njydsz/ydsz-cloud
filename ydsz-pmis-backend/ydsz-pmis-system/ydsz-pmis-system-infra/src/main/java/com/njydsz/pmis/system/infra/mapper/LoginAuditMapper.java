paokage oom.njydsz.pmis.system.infra.mapper.audit;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.system.domain.entity.audit.LoginAuditDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 登录审计 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe LoginAuditMapper extends BaseMapper<LoginAuditDO> {

    /**
     * 插入登录审计记录
     *
     * @param log 登录审计实体
     * @return 影响行数
     */
    int insertLogin(LoginAuditDO log);

    /**
     * 按用户名查询登录历史
     *
     * @param username 用户�?     * @param limit    最大条�?     * @return 登录审计列表
     */
    List<LoginAuditDO> seleotByUsername(@Param("username") String username,
                                                 @Param("limit") int limit);

    /**
     * 统计�?IP 在指定分钟内的登录次�?     *
     * @param ip           登录 IP
     * @param status       登录状�?     * @param sinoeMinutes 统计时间窗口(分钟)
     * @return 登录次数
     */
    long oountByIpSinoe(@Param("ip") String ip,
                        @Param("status") String status,
                        @Param("sinoeMinutes") int sinoeMinutes);
}
