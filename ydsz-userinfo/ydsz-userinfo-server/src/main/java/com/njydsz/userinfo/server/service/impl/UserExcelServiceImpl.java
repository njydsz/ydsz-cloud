package com.njydsz.userinfo.server.service.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.context.AnalysisContext;
import com.njydsz.common.excel.core.listener.ReadListener;
import com.njydsz.common.excel.helper.ExcelExportHelper;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.dto.UserAccountCreateDTO;
import com.njydsz.userinfo.domain.dto.UserImportDTO;
import com.njydsz.userinfo.domain.dto.UserImportResultDTO;
import com.njydsz.userinfo.infra.entity.DepartmentDO;
import com.njydsz.userinfo.infra.entity.UserAccountDO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.infra.converter.UserInfoConverter;
import com.njydsz.userinfo.infra.repository.DepartmentRepository;
import com.njydsz.userinfo.infra.repository.UserAccountRepository;
import com.njydsz.userinfo.server.auth.PasswordPolicyValidator;
import com.njydsz.userinfo.server.config.UserInfoProperties;
import com.njydsz.userinfo.server.service.UserAccountService;
import com.njydsz.userinfo.server.service.UserExcelService;

/**
 * 用户 Excel 导入导出服务实现
 *
 * <p>提供用户数据的 Excel 批量导入和导出能力。
 *
 * <p><b>导入限制：</b>
 *
 * <ul>
 *   <li>单次导入上限 1000 行（由配置控制）
 *   <li>文件大小上限 10MB
 *   <li>仅支持 .xlsx 格式
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserExcelServiceImpl implements UserExcelService {

  /** 默认导入上限 */
  private static final int DEFAULT_IMPORT_LIMIT = 1000;

  private final UserAccountService userAccountService;
  private final UserAccountRepository userAccountRepository;
  private final DepartmentRepository departmentRepository;
  private final PasswordPolicyValidator passwordPolicyValidator;
  private final UserInfoProperties properties;
  private final ExcelExportHelper excelExportHelper;

  @Override
  public UserImportResultDTO importUsers(InputStream inputStream, String originalFilename) {
    // 1. 读取 Excel 数据
    List<UserImportDTO> importList = readExcel(inputStream, originalFilename);

    if (importList == null || importList.isEmpty()) {
      throw new BusinessException(UserInfoExceptionCode.IMPORT_DATA_EMPTY);
    }

    // 2. 检查导入数量限制
    int limit = DEFAULT_IMPORT_LIMIT;
    if (importList.size() > limit) {
      throw BusinessException.builder()
          .resultCode(UserInfoExceptionCode.IMPORT_EXCEEDS_LIMIT)
          .params(limit)
          .build();
    }

    // 3. 预加载批量查询数据，避免 N+1（一次性查询所有用户名、部门编码、上级用户名）
    Set<String> importUsernames =
        importList.stream()
            .map(UserImportDTO::getUsername)
            .filter(s -> s != null && !s.isBlank())
            .collect(Collectors.toSet());
    Set<String> deptCodes =
        importList.stream()
            .map(UserImportDTO::getDeptCode)
            .filter(s -> s != null && !s.isBlank())
            .collect(Collectors.toSet());
    Set<String> leaderUsernames =
        importList.stream()
            .map(UserImportDTO::getLeaderUsername)
            .filter(s -> s != null && !s.isBlank())
            .collect(Collectors.toSet());

    // 批量查询已有用户名
    Set<String> existingUsernames = queryExistingUsernames(importUsernames);
    // 批量查询部门编码 → ID 映射
    Map<String, String> deptCodeToIdMap = queryDeptCodeToIdMap(deptCodes);
    // 批量查询上级用户名 → ID 映射
    Map<String, String> leaderUsernameToIdMap = queryUsernameToIdMap(leaderUsernames);

    // 4. 逐行处理
    int successCount = 0;
    int failCount = 0;
    List<String> failDetails = new ArrayList<>();

    for (int i = 0; i < importList.size(); i++) {
      int rowNum = i + 2; // Excel 行号（第1行是表头）
      UserImportDTO importDTO = importList.get(i);

      try {
        importSingleUser(importDTO, existingUsernames, deptCodeToIdMap, leaderUsernameToIdMap);
        successCount++;
      } catch (Exception e) {
        failCount++;
        String errorMsg = e instanceof BusinessException be ? be.getMessage() : e.getMessage();
        failDetails.add("第" + rowNum + "行[" + importDTO.getUsername() + "]: " + errorMsg);
        log.warn(
            "导入用户失败: row={}, username={}, error={}",
            rowNum,
            importDTO.getUsername(),
            errorMsg);
      }
    }

    String failDetailStr = failDetails.isEmpty() ? "" : String.join("; ", failDetails);
    return UserImportResultDTO.of(importList.size(), successCount, failCount, failDetailStr);
  }

  @Override
  public byte[] exportUsers() {
    // 查询全部用户
    LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByDesc(UserAccountDO::getCreatedAt);
    List<UserAccountDO> userList = userAccountRepository.list(wrapper);

    // 转换为 VO（脱敏后的数据）
    List<UserAccountVO> voList = new ArrayList<>();
    for (UserAccountDO user : userList) {
      voList.add(UserInfoConverter.INSTANT.entityToVO(user));
    }

    // 导出 Excel
    return excelExportHelper.export("用户列表", UserAccountVO.class, voList);
  }

  @Override
  public byte[] getImportTemplate() {
    // 创建模板数据（含一行示例）
    List<UserImportDTO> templateData = new ArrayList<>(1);
    UserImportDTO example = new UserImportDTO();
    example.setUsername("示例：zhangsan");
    example.setRealName("示例：张三");
    example.setPassword("示例：Abc@1234");
    example.setPhone("示例：1*********0");
    example.setEmail("示例：z*************m");
    example.setDeptCode("示例：DEV");
    example.setPositionCode("示例：DEV");
    example.setLeaderUsername("示例：lisi");
    templateData.add(example);

    return excelExportHelper.export("用户导入模板", UserImportDTO.class, templateData);
  }

  /**
   * 读取 Excel 文件并解析为 UserImportDTO 列表。
   *
   * @param inputStream Excel 文件输入流
   * @param originalFilename 原始文件名（用于错误日志）
   * @return 解析后的用户导入 DTO 列表
   */
  private List<UserImportDTO> readExcel(InputStream inputStream, String originalFilename) {
    try {
      List<UserImportDTO> result = new ArrayList<>();
      // 使用 common-excel 的 ExcelFacade 读取
      ExcelFacade.read(inputStream, UserImportDTO.class)
          .sheet()
          .doRead(
              new ReadListener<UserImportDTO>() {
                @Override
                public void onStart(AnalysisContext context) {}

                @Override
                public void onData(AnalysisContext context, UserImportDTO data) {
                  result.add(data);
                }

                @Override
                public void onEnd(AnalysisContext context) {}
              });
      return result;
    } catch (Exception e) {
      log.error("读取 Excel 失败: filename={}, error={}", originalFilename, e.getMessage(), e);
      throw BusinessException.builder()
          .resultCode(UserInfoExceptionCode.IMPORT_READ_FAILED)
          .params(e.getMessage())
          .build();
    }
  }

  /**
   * 导入单个用户。
   *
   * @param importDTO 用户导入 DTO
   * @param existingUsernames 已存在的用户名集合（用于唯一性校验）
   * @param deptCodeToIdMap 部门编码 → ID 映射
   * @param leaderUsernameToIdMap 上级用户名 → ID 映射
   */
  private void importSingleUser(
      UserImportDTO importDTO,
      Set<String> existingUsernames,
      Map<String, String> deptCodeToIdMap,
      Map<String, String> leaderUsernameToIdMap) {
    validateRequiredFields(importDTO);
    validateUsernameUnique(importDTO.getUsername(), existingUsernames);
    String deptId = resolveDepartmentId(importDTO.getDeptCode(), deptCodeToIdMap);
    String leaderId = resolveLeaderId(importDTO.getLeaderUsername(), leaderUsernameToIdMap);
    passwordPolicyValidator.validate(importDTO.getPassword(), importDTO.getUsername());
    createUser(importDTO, deptId, leaderId);
  }

  /**
   * 校验必填字段。
   *
   * @param importDTO 用户导入 DTO
   * @throws BusinessException 必填字段为空时抛出
   */
  private void validateRequiredFields(UserImportDTO importDTO) {
    if (importDTO.getUsername() == null || importDTO.getUsername().isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.IMPORT_USERNAME_EMPTY);
    }
    if (importDTO.getRealName() == null || importDTO.getRealName().isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.IMPORT_REALNAME_EMPTY);
    }
    if (importDTO.getPassword() == null || importDTO.getPassword().isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.IMPORT_PASSWORD_EMPTY);
    }
  }

  /**
   * 校验用户名唯一性。
   *
   * @param username 用户名
   * @param existingUsernames 已存在的用户名集合
   * @throws BusinessException 用户名已存在时抛出
   */
  private void validateUsernameUnique(String username, Set<String> existingUsernames) {
    if (existingUsernames.contains(username)) {
      throw new BusinessException(UserInfoExceptionCode.IMPORT_USERNAME_DUPLICATE);
    }
  }

  /**
   * 根据部门编码解析部门 ID。
   *
   * @param deptCode 部门编码（可为空）
   * @param deptCodeToIdMap 部门编码 → ID 映射
   * @return 部门 ID，部门编码为空时返回 null
   * @throws BusinessException 部门编码不存在时抛出
   */
  private String resolveDepartmentId(String deptCode, Map<String, String> deptCodeToIdMap) {
    if (deptCode == null || deptCode.isBlank()) {
      return null;
    }
    String deptId = deptCodeToIdMap.get(deptCode);
    if (deptId == null) {
      throw BusinessException.builder()
          .resultCode(UserInfoExceptionCode.IMPORT_DEPT_NOT_FOUND)
          .params(deptCode)
          .build();
    }
    return deptId;
  }

  /**
   * 根据上级用户名解析上级用户 ID。
   *
   * @param leaderUsername 上级用户名（可为空）
   * @param leaderUsernameToIdMap 上级用户名 → ID 映射
   * @return 上级用户 ID，用户名为空时返回 null
   * @throws BusinessException 上级用户名不存在时抛出
   */
  private String resolveLeaderId(String leaderUsername, Map<String, String> leaderUsernameToIdMap) {
    if (leaderUsername == null || leaderUsername.isBlank()) {
      return null;
    }
    String leaderId = leaderUsernameToIdMap.get(leaderUsername);
    if (leaderId == null) {
      throw BusinessException.builder()
          .resultCode(UserInfoExceptionCode.IMPORT_LEADER_NOT_FOUND)
          .params(leaderUsername)
          .build();
    }
    return leaderId;
  }

  /**
   * 批量查询已存在的用户名。
   *
   * @param usernames 待检查的用户名集合
   * @return 已存在的用户名集合
   */
  private Set<String> queryExistingUsernames(Set<String> usernames) {
    if (usernames.isEmpty()) {
      return new HashSet<>();
    }
    LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.in(UserAccountDO::getUsername, usernames).select(UserAccountDO::getUsername);
    return userAccountRepository.list(wrapper).stream()
        .map(UserAccountDO::getUsername)
        .collect(Collectors.toSet());
  }

  /**
   * 批量查询部门编码 → ID 映射。
   *
   * @param deptCodes 部门编码集合
   * @return 部门编码 → ID 映射
   */
  private Map<String, String> queryDeptCodeToIdMap(Set<String> deptCodes) {
    if (deptCodes.isEmpty()) {
      return new HashMap<>();
    }
    LambdaQueryWrapper<DepartmentDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.in(DepartmentDO::getDeptCode, deptCodes).select(DepartmentDO::getDeptCode, DepartmentDO::getId);
    return departmentRepository.list(wrapper).stream()
        .collect(Collectors.toMap(DepartmentDO::getDeptCode, DepartmentDO::getId));
  }

  /**
   * 批量查询用户名 → ID 映射。
   *
   * @param usernames 用户名集合
   * @return 用户名 → ID 映射
   */
  private Map<String, String> queryUsernameToIdMap(Set<String> usernames) {
    if (usernames.isEmpty()) {
      return new HashMap<>();
    }
    LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.in(UserAccountDO::getUsername, usernames).select(UserAccountDO::getUsername, UserAccountDO::getId);
    return userAccountRepository.list(wrapper).stream()
        .collect(Collectors.toMap(UserAccountDO::getUsername, UserAccountDO::getId));
  }

  /**
   * 创建用户。
   *
   * @param importDTO 用户导入 DTO
   * @param deptId 部门 ID
   * @param leaderId 上级用户 ID
   */
  private void createUser(UserImportDTO importDTO, String deptId, String leaderId) {
    UserAccountCreateDTO createDTO = new UserAccountCreateDTO();
    createDTO.setUsername(importDTO.getUsername());
    createDTO.setRealName(importDTO.getRealName());
    createDTO.setPassword(importDTO.getPassword());
    createDTO.setPhone(importDTO.getPhone());
    createDTO.setEmail(importDTO.getEmail());
    createDTO.setDeptId(deptId);
    createDTO.setPositionCode(importDTO.getPositionCode());
    createDTO.setLeaderId(leaderId);
    userAccountService.create(createDTO);
  }
}
