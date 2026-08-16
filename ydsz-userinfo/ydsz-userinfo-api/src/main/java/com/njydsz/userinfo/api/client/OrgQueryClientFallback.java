package com.njydsz.userinfo.api.client;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;

/**
 * OrgQueryClient 降级处理。
 *
 * <p>所有方法在 Feign 调用失败时统一返回 {@link FeignClientConstants#FEIGN_SERVICE_UNAVAILABLE} 错误码，
 * 不抛异常，避免阻断调用方主流程（如工作流引擎的办理人展开）。
 *
 * <p>注意：必须返回 error 而非 success(null/emptyList/emptyMap)， 否则调用方通过 {@code isSuccess()}
 * 检查会误判为查询成功（结果为空）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class OrgQueryClientFallback implements OrgQueryClient {

  @Override
  public BaseResponse<UserAccountVO> queryUserById(String userId) {
    log.warn("OrgQueryClient fallback: queryUserById={}", userId);
    return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
  }

  @Override
  public BaseResponse<List<DepartmentTreeVO>> getDeptTree() {
    log.warn("OrgQueryClient fallback: getDeptTree");
    return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
  }

  @Override
  public BaseResponse<List<DepartmentVO>> getDeptList() {
    log.warn("OrgQueryClient fallback: getDeptList");
    return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
  }

  @Override
  public BaseResponse<List<String>> listUserIdsByRoleCode(String roleCode) {
    log.warn("OrgQueryClient fallback: listUserIdsByRoleCode roleCode={}", roleCode);
    return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
  }

  @Override
  public BaseResponse<List<String>> listRoleCodesByUserId(String userId) {
    log.warn("OrgQueryClient fallback: listRoleCodesByUserId userId={}", userId);
    return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
  }

  @Override
  public BaseResponse<List<String>> listDeptIdsByUserId(String userId) {
    log.warn("OrgQueryClient fallback: listDeptIdsByUserId userId={}", userId);
    return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
  }

  @Override
  public BaseResponse<String> getLeaderByUserId(String userId) {
    log.warn("OrgQueryClient fallback: getLeaderByUserId userId={}", userId);
    return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
  }

  @Override
  public BaseResponse<List<String>> listUserIdsByPositionCode(String positionCode) {
    log.warn("OrgQueryClient fallback: listUserIdsByPositionCode positionCode={}", positionCode);
    return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
  }

  @Override
  public BaseResponse<String> getDeptLeaderByDeptId(String deptId) {
    log.warn("OrgQueryClient fallback: getDeptLeaderByDeptId deptId={}", deptId);
    return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
  }

  @Override
  public BaseResponse<String> getDeptLeaderByDeptCode(String deptCode) {
    log.warn("OrgQueryClient fallback: getDeptLeaderByDeptCode deptCode={}", deptCode);
    return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
  }

  @Override
  public BaseResponse<Map<String, String>> batchUserNames(List<String> userIds) {
    log.warn(
        "OrgQueryClient fallback: batchUserNames size={}", userIds == null ? 0 : userIds.size());
    return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
  }

  @Override
  public BaseResponse<Map<String, String>> batchDeptNames(List<String> deptIds) {
    log.warn(
        "OrgQueryClient fallback: batchDeptNames size={}", deptIds == null ? 0 : deptIds.size());
    return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
  }

  @Override
  public BaseResponse<Map<String, String>> batchRoleNames(List<String> roleIds) {
    log.warn(
        "OrgQueryClient fallback: batchRoleNames size={}", roleIds == null ? 0 : roleIds.size());
    return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
  }

  @Override
  public BaseResponse<Map<String, String>> batchPostNames(List<String> postIds) {
    log.warn(
        "OrgQueryClient fallback: batchPostNames size={}", postIds == null ? 0 : postIds.size());
    return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
  }

  @Override
  public BaseResponse<Map<String, String>> batchCompanyNames(List<String> companyIds) {
    log.warn(
        "OrgQueryClient fallback: batchCompanyNames size={}",
        companyIds == null ? 0 : companyIds.size());
    return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用");
  }
}
