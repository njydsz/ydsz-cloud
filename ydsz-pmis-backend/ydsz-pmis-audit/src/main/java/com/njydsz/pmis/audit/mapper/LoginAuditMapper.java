package com.njydsz.pmis.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.audit.entity.LoginAuditDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LoginAuditMapper extends BaseMapper<LoginAuditDO> {

    int insertLogin(LoginAuditDO log);

    java.util.List<LoginAuditDO> selectByUsername(@Param("username") String username,
                                                 @Param("limit") int limit);

    long countByIpSince(@Param("ip") String ip,
                        @Param("status") String status,
                        @Param("sinceMinutes") int sinceMinutes);
}
