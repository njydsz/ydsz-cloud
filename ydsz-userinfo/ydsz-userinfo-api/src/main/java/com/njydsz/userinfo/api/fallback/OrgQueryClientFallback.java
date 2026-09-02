package com.njydsz.userinfo.api.fallback;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.userinfo.api.client.OrgQueryClient;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;

/**
 * {@link OrgQueryClient} 的降级工厂。
 *
 * <p>所有方法在 Feign 调用失败（连接超时 / 服务端异常 / 熔断打开）时统一返回 {@link
 * FeignClientConstants#FEIGN_SERVICE_UNAVAILABLE} 错误码，不抛异常，避免阻断调用方主流程（如工作流引擎的办理人展开）。
 *
 * <p>使用 {@link FallbackFactory} 模式可在 {@link #create(Throwable)} 中获取异常原因，区分超时 / 熔断 / 服务端异常，
 * 便于在日志中区分降级来源。
 *
 * <p>注意：必须返回 error 而非 success(null/emptyList/emptyMap)，否则调用方通过 {@code isSuccess()} 检查会误判为查询成功（结果为空）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class OrgQueryClientFallback implements FallbackFactory<OrgQueryClient> {

  @Override
  public OrgQueryClient create(Throwable cause) {
    log.warn("[OrgQueryClient] 降级触发: {}", cause == null ? "?" : cause.getMessage());
    return new OrgQueryClient() {

      @Override
      public YdszResponse<UserAccountVO> queryUserById(String userId) {
        log.warn("[OrgQueryClient] queryUserById 降级: userId={}, reason=用户中心服务不可用", userId);
        return YdszResponse.error(
            FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
      }

      @Override
      public YdszResponse<List<DepartmentTreeVO>> getDeptTree() {
        log.warn("[OrgQueryClient] getDeptTree 降级: reason=用户中心服务不可用");
        return YdszResponse.error(
            FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
      }

      @Override
      public YdszResponse<List<DepartmentVO>> getDeptList() {
        log.warn("[OrgQueryClient] getDeptList 降级: reason=用户中心服务不可用");
        return YdszResponse.error(
            FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
      }

      @Override
      public YdszResponse<List<String>> listUserIdsByRoleCode(String roleCode) {
        log.warn(
            "[OrgQueryClient] listUserIdsByRoleCode 降级: roleCode={}, reason=用户中心服务不可用",
            roleCode);
        return YdszResponse.error(
            FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
      }

      @Override
      public YdszResponse<List<String>> listRoleCodesByUserId(String userId) {
        log.warn(
            "[OrgQueryClient] listRoleCodesByUserId 降级: userId={}, reason=用户中心服务不可用",
            userId);
        return YdszResponse.error(
            FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
      }

      @Override
      public YdszResponse<List<String>> listDeptIdsByUserId(String userId) {
        log.warn(
            "[OrgQueryClient] listDeptIdsByUserId 降级: userId={}, reason=用户中心服务不可用",
            userId);
        return YdszResponse.error(
            FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
      }

      @Override
      public YdszResponse<String> getLeaderByUserId(String userId) {
        log.warn(
            "[OrgQueryClient] getLeaderByUserId 降级: userId={}, reason=用户中心服务不可用",
            userId);
        return YdszResponse.error(
            FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
      }

      @Override
      public YdszResponse<List<String>> listUserIdsByPositionCode(String positionCode) {
        log.warn(
            "[OrgQueryClient] listUserIdsByPositionCode 降级: positionCode={}, reason=用户中心服务不可用",
            positionCode);
        return YdszResponse.error(
            FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
      }

      @Override
      public YdszResponse<String> getDeptLeaderByDeptId(String deptId) {
        log.warn(
            "[OrgQueryClient] getDeptLeaderByDeptId 降级: deptId={}, reason=用户中心服务不可用",
            deptId);
        return YdszResponse.error(
            FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
      }

      @Override
      public YdszResponse<String> getDeptLeaderByDeptCode(String deptCode) {
        log.warn(
            "[OrgQueryClient] getDeptLeaderByDeptCode 降级: deptCode={}, reason=用户中心服务不可用",
            deptCode);
        return YdszResponse.error(
            FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
      }

      @Override
      public YdszResponse<Map<String, String>> batchUserNames(List<String> userIds) {
        log.warn(
            "[OrgQueryClient] batchUserNames 降级: size={}, reason=用户中心服务不可用",
            userIds == null ? 0 : userIds.size());
        return YdszResponse.error(
            FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
      }

      @Override
      public YdszResponse<Map<String, String>> batchDeptNames(List<String> deptIds) {
        log.warn(
            "[OrgQueryClient] batchDeptNames 降级: size={}, reason=用户中心服务不可用",
            deptIds == null ? 0 : deptIds.size());
        return YdszResponse.error(
            FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
      }

      @Override
      public YdszResponse<Map<String, String>> batchRoleNames(List<String> roleIds) {
        log.warn(
            "[OrgQueryClient] batchRoleNames 降级: size={}, reason=用户中心服务不可用",
            roleIds == null ? 0 : roleIds.size());
        return YdszResponse.error(
            FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
      }

      @Override
      public YdszResponse<Map<String, String>> batchPostNames(List<String> postIds) {
        log.warn(
            "[OrgQueryClient] batchPostNames 降级: size={}, reason=用户中心服务不可用",
            postIds == null ? 0 : postIds.size());
        return YdszResponse.error(
            FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
      }

      @Override
      public YdszResponse<Map<String, String>> batchCompanyNames(List<String> companyIds) {
        log.warn(
            "[OrgQueryClient] batchCompanyNames 降级: size={}, reason=用户中心服务不可用",
            companyIds == null ? 0 : companyIds.size());
        return YdszResponse.error(
            FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
      }
    };
  }
}
