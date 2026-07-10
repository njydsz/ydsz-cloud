package com.njydsz.pmis.system.mapper.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.system.entity.audit.LoginAuditDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 登录审计 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface LoginAuditMapper extends BaseMapper<LoginAuditDO> {

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
     * @param username 用户名
     * @param limit    最大条数
     * @return 登录审计列表
     */
    List<LoginAuditDO> selectByUsername(@Param("username") String username,
                                                 @Param("limit") int limit);

    /**
     * 统计某 IP 在指定分钟内的登录次数
     *
     * @param ip           登录 IP
     * @param status       登录状态
     * @param sinceMinutes 统计时间窗口(分钟)
     * @return 登录次数
     */
    long countByIpSince(@Param("ip") String ip,
                        @Param("status") String status,
                        @Param("sinceMinutes") int sinceMinutes);
}
