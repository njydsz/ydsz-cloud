package com.njydsz.pmis.finance.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.finance.domain.entity.CustomerCreditDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客户信用 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface CustomerCreditMapper extends BaseMapper<CustomerCreditDO> {

    /**
     * 按客户 ID 查询客户信用
     *
     * @param customerId 客户 ID
     * @return 客户信用对象，未找到返回 null
     */
    CustomerCreditDO selectByCustomerId(@Param("customerId") String customerId);

    /**
     * 更新客户信用等级
     *
     * @param id    信用 ID
     * @param level 信用等级
     * @param score 信用分值
     * @return 受影响行数
     */
    int updateLevel(@Param("id") String id,
                    @Param("level") String level,
                    @Param("score") Integer score);

    /**
     * 按信用等级查询客户信用列表
     *
     * @param level 信用等级
     * @return 客户信用列表
     */
    List<CustomerCreditDO> selectByLevel(@Param("level") String level);
}
