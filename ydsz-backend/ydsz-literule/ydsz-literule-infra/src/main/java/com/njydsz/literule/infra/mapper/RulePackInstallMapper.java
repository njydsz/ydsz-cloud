package com.njydsz.literule.infra.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.literule.domain.entity.RulePackInstall;

/**
 * 规则集安装历史 Mapper（P2-14）。
 *
 * <p>对应 {@code ydsz_rule_pack_install} 表，记录规则集在租户环境下的安装/卸载历史。
 * 继承 {@link BaseMapper} 获得 CRUD 能力，扩展方法定义在 XML 中。
 *
 * @author ydsz-team
 * @since 1.0.0 (P2-14)
 */
@Mapper
public interface RulePackInstallMapper extends BaseMapper<RulePackInstall> {
}
