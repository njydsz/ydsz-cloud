package com.njydsz.userinfo.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.context.AnalysisContext;
import com.njydsz.common.excel.core.listener.ReadListener;
import com.njydsz.common.excel.helper.ExcelExportHelper;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.dto.UserAccountCreateDTO;
import com.njydsz.userinfo.domain.dto.UserImportDTO;
import com.njydsz.userinfo.domain.dto.UserImportResultDTO;
import com.njydsz.userinfo.domain.entity.Department;
import com.njydsz.userinfo.domain.entity.UserAccount;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.infra.mapper.DepartmentMapper;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;
import com.njydsz.userinfo.server.auth.PasswordPolicyValidator;
import com.njydsz.userinfo.server.config.UserInfoProperties;
import com.njydsz.userinfo.server.service.UserAccountService;
import com.njydsz.userinfo.server.service.UserExcelService;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
  private final UserAccountMapper userAccountMapper;
  private final DepartmentMapper departmentMapper;
  private final PasswordPolicyValidator passwordPolicyValidator;
  private final UserInfoProperties properties;
  private final ExcelExportHelper excelExportHelper;

  @Override
  public UserImportResultDTO importUsers(InputStream inputStream, String originalFilename) {
    // 1. 读取 Excel 数据
    List<UserImportDTO> importList = readExcel(inputStream, originalFilename);

    if (importList == null || importList.isEmpty()) {
      return UserImportResultDTO.of(0, 0, 0, "导入文件无数据");
    }

    // 2. 检查导入数量限制
    int limit = DEFAULT_IMPORT_LIMIT;
    if (importList.size() > limit) {
      return UserImportResultDTO.of(
          importList.size(), 0, importList.size(), "导入数量超过上限 " + limit + " 行");
    }

    // 3. 逐行处理
    int successCount = 0;
    int failCount = 0;
    List<String> failDetails = new ArrayList<>();

    for (int i = 0; i < importList.size(); i++) {
      int rowNum = i + 2; // Excel 行号（第1行是表头）
      UserImportDTO importDTO = importList.get(i);

      try {
        importSingleUser(importDTO);
        successCount++;
      } catch (Exception e) {
        failCount++;
        failDetails.add("第" + rowNum + "行[" + importDTO.getUsername() + "]: " + e.getMessage());
        log.warn(
            "导入用户失败: row={}, username={}, error={}",
            rowNum,
            importDTO.getUsername(),
            e.getMessage());
      }
    }

    String failDetailStr = failDetails.isEmpty() ? "" : String.join("; ", failDetails);
    return UserImportResultDTO.of(importList.size(), successCount, failCount, failDetailStr);
  }

  @Override
  public byte[] exportUsers() {
    // 查询全部用户
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByDesc(UserAccount::getCreatedAt);
    List<UserAccount> userList = userAccountMapper.selectList(wrapper);

    // 转换为 VO（脱敏后的数据）
    List<UserAccountVO> voList = new ArrayList<>();
    for (UserAccount user : userList) {
      voList.add(UserAccountVO.fromEntity(user));
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
   * @param inputStream      Excel 文件输入流
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
      throw BusinessException.builder().message("读取 Excel 文件失败: " + e.getMessage()).build();
    }
  }

  /**
   * 导入单个用户。
   *
   * @param importDTO 用户导入 DTO
   */
  private void importSingleUser(UserImportDTO importDTO) {
    // 1. 必填校验
    if (importDTO.getUsername() == null || importDTO.getUsername().isBlank()) {
      throw BusinessException.builder().message("用户名不能为空").build();
    }
    if (importDTO.getRealName() == null || importDTO.getRealName().isBlank()) {
      throw BusinessException.builder().message("真实姓名不能为空").build();
    }
    if (importDTO.getPassword() == null || importDTO.getPassword().isBlank()) {
      throw BusinessException.builder().message("初始密码不能为空").build();
    }

    // 2. 用户名唯一性校验
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccount::getUsername, importDTO.getUsername());
    if (userAccountMapper.selectCount(wrapper) > 0) {
      throw BusinessException.builder().message("用户名已存在").build();
    }

    // 3. 部门编码校验
    String deptId = null;
    if (importDTO.getDeptCode() != null && !importDTO.getDeptCode().isBlank()) {
      LambdaQueryWrapper<Department> deptWrapper = new LambdaQueryWrapper<>();
      deptWrapper.eq(Department::getDeptCode, importDTO.getDeptCode());
      Department dept = departmentMapper.selectOne(deptWrapper);
      if (dept == null) {
        throw BusinessException.builder().message("部门编码不存在: " + importDTO.getDeptCode()).build();
      }
      deptId = dept.getId();
    }

    // 4. 上级用户校验
    String leaderId = null;
    if (importDTO.getLeaderUsername() != null && !importDTO.getLeaderUsername().isBlank()) {
      LambdaQueryWrapper<UserAccount> leaderWrapper = new LambdaQueryWrapper<>();
      leaderWrapper.eq(UserAccount::getUsername, importDTO.getLeaderUsername());
      UserAccount leader = userAccountMapper.selectOne(leaderWrapper);
      if (leader == null) {
        throw BusinessException.builder()
            .message("上级用户名不存在: " + importDTO.getLeaderUsername())
            .build();
      }
      leaderId = leader.getId();
    }

    // 5. 密码策略校验
    passwordPolicyValidator.validate(importDTO.getPassword(), importDTO.getUsername());

    // 6. 创建用户
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
