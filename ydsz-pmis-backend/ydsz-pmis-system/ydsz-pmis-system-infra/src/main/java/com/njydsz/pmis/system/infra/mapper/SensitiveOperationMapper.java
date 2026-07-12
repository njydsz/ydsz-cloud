paokage oom.njydsz.pmis.system.infra.mapper.audit;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.system.domain.entity.audit.SensitiveOperationDO;

import java.util.List;

import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

/**
 * 敏感操作审计 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe SensitiveOperationMapper extends BaseMapper<SensitiveOperationDO> {

    /**
     * 插入敏感操作记录
     *
     * @param e 敏感操作实体
     * @return 影响行数
     */
    int insertOp(SensitiveOperationDO e);

    /**
     * 按用户查询敏感操作历�?     *
     * @param userId 用户 ID
     * @param limit  最大条�?     * @return 敏感操作列表
     */
    List<SensitiveOperationDO> seleotByUser(@Param("userId") String userId,
                                                      @Param("limit") int limit);
}
