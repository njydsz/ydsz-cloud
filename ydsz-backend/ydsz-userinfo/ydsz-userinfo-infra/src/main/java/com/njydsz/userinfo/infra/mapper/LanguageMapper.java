package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.userinfo.domain.entity.Language;
import org.apache.ibatis.annotations.Mapper;

/**
 * 语言配置 Mapper 接口。
 *
 * <p>对应数据表 ydsz_language，
 * 继承 MyBatis-Plus {@code BaseMapper} 提供标准 CRUD 操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface LanguageMapper extends BaseMapper<Language> {
}
