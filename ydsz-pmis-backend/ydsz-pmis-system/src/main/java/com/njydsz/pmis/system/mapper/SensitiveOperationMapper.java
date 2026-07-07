package com.njydsz.pmis.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.system.entity.SensitiveOperationDO;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 敏感操作审计 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface SensitiveOperationMapper extends BaseMapper<SensitiveOperationDO> {

    /**
     * 插入敏感操作记录
     *
     * @param e 敏感操作实体
     * @return 影响行数
     */
    int insertOp(SensitiveOperationDO e);

    /**
     * 按用户查询敏感操作历史
     *
     * @param userId 用户 ID
     * @param limit  最大条数
     * @return 敏感操作列表
     */
    List<SensitiveOperationDO> selectByUser(@Param("userId") String userId,
                                                      @Param("limit") int limit);
}
