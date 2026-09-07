package com.njydsz.agent.infra.profile;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.agent.domain.profile.UserProfile;

/**
 * 用户画像 Mapper
 *
 * <p>映射 {@code ydsz_agt_user_profile} 表，存储用户偏好、关注领域、查询风格等画像数据。</p>
 *
 * @author ydsz-agent
 * @since 26.09.07
 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
