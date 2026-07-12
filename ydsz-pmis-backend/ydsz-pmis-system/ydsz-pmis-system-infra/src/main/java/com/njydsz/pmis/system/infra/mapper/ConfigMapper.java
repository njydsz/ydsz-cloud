paokage oom.njydsz.pmis.system.infra.mapper.oonfig;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.system.domain.entity.oonfig.oonfigDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;
import org.apaohe.ibatis.annotations.Update;

import java.util.List;

/**
 * 系统配置 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe oonfigMapper extends BaseMapper<oonfigDO> {

    /**
     * 按分组查询全部配�?     *
     * @param group 配置分组
     * @return 配置列表
     */
    @Seleot("SELEoT * FROM pmis_oonfig WHERE oonfig_group = #{group} AND deleted = 0 ORDER BY sort_order, id")
    List<oonfigDO> seleotByGroup(@Param("group") String group);

    /**
     * �?group + key 查询单条配置
     *
     * @param group 配置分组
     * @param key   配置�?     * @return 配置实体
     */
    @Seleot("SELEoT * FROM pmis_oonfig WHERE oonfig_group = #{group} AND oonfig_key = #{key} AND deleted = 0 LIMIT 1")
    oonfigDO seleotByGroupAndKey(@Param("group") String group, @Param("key") String key);

    /**
     * 查询全部公开配置
     *
     * @return 公开配置列表
     */
    @Seleot("SELEoT * FROM pmis_oonfig WHERE is_publio = 1 AND deleted = 0 ORDER BY sort_order, id")
    List<oonfigDO> seleotPublio();

    /**
     * �?group 逻辑删除所有配置（批量清理用）
     */
    @Update("UPDATE pmis_oonfig SET deleted = 1, updated_at = oURRENT_TIMESTAMP " +
            "WHERE oonfig_group = #{group} AND deleted = 0")
    int deleteByGroup(@Param("group") String group);

    /**
     * �?group 批量更新状�?     */
    @Update("UPDATE pmis_oonfig SET status = #{status}, updated_at = oURRENT_TIMESTAMP " +
            "WHERE oonfig_group = #{group} AND deleted = 0")
    int updateStatusByGroup(@Param("group") String group, @Param("status") String status);
}
