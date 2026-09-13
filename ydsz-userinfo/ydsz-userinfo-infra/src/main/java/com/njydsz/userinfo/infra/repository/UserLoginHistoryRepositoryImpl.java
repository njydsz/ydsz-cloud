package com.njydsz.userinfo.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.converter.UserInfoUserConverter;
import com.njydsz.userinfo.domain.dto.UserLoginHistoryDTO;
import com.njydsz.userinfo.domain.entity.UserLoginHistory;
import com.njydsz.userinfo.domain.query.LoginLogPageQuery;
import com.njydsz.userinfo.domain.repository.UserLoginHistoryRepository;
import com.njydsz.userinfo.domain.vo.UserLoginHistoryVO;
import com.njydsz.userinfo.infra.mapper.UserLoginHistoryMapper;

/**
 * 用户登录历史 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link UserLoginHistoryMapper} 实现登录历史的数据访问。
 * 所有返回值通过 {@link UserInfoUserConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class UserLoginHistoryRepositoryImpl implements UserLoginHistoryRepository {

  /** 浏览器名称提取正则 */
  private static final Pattern BROWSER_PATTERN =
      Pattern.compile("(Chrome|Firefox|Safari|Edge|Edg|MSIE|Trident|Opera)/?\\s*([\\d.]*)");

  /** 操作系统名称提取正则 */
  private static final Pattern OS_PATTERN =
      Pattern.compile(
          "(Windows NT [\\d.]+|Mac OS X [\\d_]+|Linux|Android [\\d.]+|iOS [\\d.]+|iPhone OS [\\d_]+)");

  private final UserLoginHistoryMapper userLoginHistoryMapper;
  private final UserInfoUserConverter converter;

  @Override
  public UserLoginHistoryVO create(UserLoginHistoryDTO dto) {
    UserLoginHistory entity = converter.dtoToEntity(dto);
    userLoginHistoryMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public int countRecentFailures(String userId, int windowMinutes) {
    LambdaQueryWrapper<UserLoginHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserLoginHistory::getUserId, userId);
    wrapper.eq(UserLoginHistory::getLoginResult, "FAILED");
    wrapper.ge(e -> e.getCreatedAt(), LocalDateTime.now().minusMinutes(windowMinutes));
    return Math.toIntExact(userLoginHistoryMapper.selectCount(wrapper));
  }

  @Override
  public List<UserLoginHistoryVO> findRecentByUserId(String userId, int limit) {
    LambdaQueryWrapper<UserLoginHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserLoginHistory::getUserId, userId);
    wrapper.orderByDesc(e -> e.getCreatedAt());
    wrapper.last("LIMIT " + Math.min(limit, 100));
    List<UserLoginHistory> entities = userLoginHistoryMapper.selectList(wrapper);
    return converter.userLoginHistoryListToVO(entities);
  }

  @Override
  public List<UserLoginHistoryVO> findByUserId(String userId) {
    LambdaQueryWrapper<UserLoginHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserLoginHistory::getUserId, userId);
    List<UserLoginHistory> entities = userLoginHistoryMapper.selectList(wrapper);
    return converter.userLoginHistoryListToVO(entities);
  }

  @Override
  public long countByResultAndTimeRange(
      LocalDateTime startTime, LocalDateTime endTime, String result) {
    LambdaQueryWrapper<UserLoginHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.ge(e -> e.getCreatedAt(), startTime);
    wrapper.lt(e -> e.getCreatedAt(), endTime);
    if (result != null) {
      wrapper.eq(UserLoginHistory::getLoginResult, result);
    }
    return userLoginHistoryMapper.selectCount(wrapper);
  }

  @Override
  public int countByFailReasonAndTimeRange(
      LocalDateTime startTime, LocalDateTime endTime, String failReason) {
    LambdaQueryWrapper<UserLoginHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.ge(e -> e.getCreatedAt(), startTime);
    wrapper.lt(e -> e.getCreatedAt(), endTime);
    wrapper.eq(UserLoginHistory::getLoginResult, "FAILED");
    if (failReason != null) {
      wrapper.eq(UserLoginHistory::getFailReason, failReason);
    }
    return Math.toIntExact(userLoginHistoryMapper.selectCount(wrapper));
  }

  @Override
  public List<UserLoginHistoryVO> findRecentFailedLogins(LocalDateTime since, int limit) {
    LambdaQueryWrapper<UserLoginHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.ge(e -> e.getCreatedAt(), since);
    wrapper.eq(UserLoginHistory::getLoginResult, "FAILED");
    wrapper.orderByDesc(e -> e.getCreatedAt());
    wrapper.last("LIMIT " + Math.min(limit, 100));
    List<UserLoginHistory> entities = userLoginHistoryMapper.selectList(wrapper);
    return converter.userLoginHistoryListToVO(entities);
  }

  @Override
  public long countDistinctUsersWithFailures(LocalDateTime startTime, LocalDateTime endTime) {
    return userLoginHistoryMapper.countDistinctUsersWithFailures(startTime, endTime);
  }

  @Override
  public PageResponse<List<UserLoginHistoryVO>> page(LoginLogPageQuery query) {
    // 构建筛选条件
    LambdaQueryWrapper<UserLoginHistory> wrapper = new LambdaQueryWrapper<>();
    if (query.getUsername() != null && !query.getUsername().isBlank()) {
      wrapper.like(UserLoginHistory::getUsername, query.getUsername().trim());
    }
    if (query.getLoginIp() != null && !query.getLoginIp().isBlank()) {
      wrapper.eq(UserLoginHistory::getLoginIp, query.getLoginIp().trim());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank() && !"ALL".equalsIgnoreCase(query.getStatus())) {
      wrapper.eq(UserLoginHistory::getLoginResult, query.getStatus());
    }
    if (query.getStartTime() != null) {
      wrapper.ge(e -> e.getCreatedAt(), query.getStartTime());
    }
    if (query.getEndTime() != null) {
      wrapper.le(e -> e.getCreatedAt(), query.getEndTime());
    }
    wrapper.orderByDesc(e -> e.getCreatedAt());

    // 查询总数
    long total = userLoginHistoryMapper.selectCount(wrapper);

    // 分页查询（追加 LIMIT）
    wrapper.last("LIMIT " + query.getOffset() + ", " + query.getLimit());
    List<UserLoginHistory> entities = userLoginHistoryMapper.selectList(wrapper);

    // 转换并填充派生字段
    List<UserLoginHistoryVO> voList = converter.userLoginHistoryListToVO(entities);
    voList.forEach(this::fillDerivedFields);

    return PageResponse.success(total, (long) query.getPageNum(), (long) query.getPageSize(), voList);
  }

  /**
   * 填充派生字段（browser / os / location）。
   *
   * <p>这些字段在数据库中不单独存储，而是从 {@code userAgent} 直接解析。 使用简单的正则匹配，能覆盖主流浏览器和操作系统识别。
   *
   * @param vo 登录历史 VO
   */
  private void fillDerivedFields(UserLoginHistoryVO vo) {
    String userAgent = vo.getUserAgent();
    if (userAgent == null || userAgent.isBlank()) {
      vo.setBrowser("Unknown");
      vo.setOs("Unknown");
      vo.setLocation("Unknown");
      return;
    }
    vo.setBrowser(extractBrowser(userAgent));
    vo.setOs(extractOs(userAgent));
    vo.setLocation("Unknown");
  }

  /**
   * 从 User-Agent 字符串中提取浏览器名称。
   *
   * @param userAgent 原始 UA 字符串
   * @return 浏览器名称（含版本号），无法识别返回 "Unknown"
   */
  private String extractBrowser(String userAgent) {
    Matcher matcher = BROWSER_PATTERN.matcher(userAgent);
    if (matcher.find()) {
      String name = matcher.group(1);
      String version = matcher.group(2);
      if ("Trident".equals(name)) {
        return "IE 11";
      }
      return (version != null && !version.isBlank()) ? name + " " + version : name;
    }
    return "Unknown";
  }

  /**
   * 从 User-Agent 字符串中提取操作系统名称。
   *
   * @param userAgent 原始 UA 字符串
   * @return 操作系统名称，无法识别返回 "Unknown"
   */
  private String extractOs(String userAgent) {
    Matcher matcher = OS_PATTERN.matcher(userAgent);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return "Unknown";
  }
}
