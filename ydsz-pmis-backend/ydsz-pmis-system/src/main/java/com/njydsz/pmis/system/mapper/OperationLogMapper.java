package com.njydsz.pmis.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.system.entity.OperationLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 操作日志 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogDO> {

    /**
     * 插入操作日志
     *
     * @param log 操作日志实体
     * @return 影响行数
     */
    int insertLog(OperationLogDO log);

    /**
     * 按用户查询操作日志
     *
     * @param userId 用户 ID
     * @param limit  最大条数
     * @return 操作日志列表
     */
    java.util.List<OperationLogDO> selectByUser(@Param("userId") Long userId,
                                                @Param("limit") int limit);

    /**
     * 按业务查询操作日志
     *
     * @param bizType 业务类型
     * @param bizId   业务单据 ID
     * @param limit   最大条数
     * @return 操作日志列表
     */
    java.util.List<OperationLogDO> selectByBiz(@Param("bizType") String bizType,
                                              @Param("bizId") String bizId,
                                              @Param("limit") int limit);

    /**
     * 清理指定天数之前的日志
     *
     * @param days 保留天数
     * @return 删除条数
     */
    int deleteBefore(@Param("days") int days);
}
