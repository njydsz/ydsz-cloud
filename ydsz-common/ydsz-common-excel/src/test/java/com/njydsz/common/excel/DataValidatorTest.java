package com.njydsz.common.excel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.junit.jupiter.api.Test;

import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.api.validator.DataValidator;
import com.njydsz.common.excel.api.validator.DataValidator.ValidationMode;
import com.njydsz.common.excel.exception.ExcelReadException;

/**
 * P1-3 回归测试集 — DataValidator 委托标准 Jakarta Bean Validation。
 *
 * <p>修复前：手搓五规则（NotNull/Size/Min/Max/Pattern）对 {@code @NotBlank}/{@code @Email}
 * 等标准约束与自定义约束<b>静默放行</b>。 修复后：classpath 存在校验实现（本模块测试域引入
 * spring-boot-starter-validation）时委托标准 Validator，全约束覆盖。
 *
 * <p>断言设计：核心证据是 <b>@NotBlank 与 @Email 两类"旧路径必然漏放"的约束被实际拦截</b>，
 * 防止"委托代码存在但未走到"。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DataValidatorTest {

  // ==================== 标准委托路径生效（核心证据） ====================

  @Test
  void notBlankViolationCaught() {
    // @NotBlank 对空串/纯空格拦截；旧手搓路径仅识别 @NotNull（null 拦截），空串静默放行
    ValidatedRow row = new ValidatedRow("   ", 30, "a@b.com");
    ExcelReadException ex =
        assertThrows(
            ExcelReadException.class,
            () -> DataValidator.validate(row, 5, ValidationMode.FAIL_FAST));
    assertTrue(ex.getMessage().contains("姓名") || ex.getMessage().contains("name"));
  }

  @Test
  void emailViolationCaught() {
    // @Email 格式校验；旧手搓路径完全不识别该约束
    ValidatedRow row = new ValidatedRow("张三", 30, "not-an-email");
    ExcelReadException ex =
        assertThrows(
            ExcelReadException.class,
            () -> DataValidator.validate(row, 5, ValidationMode.FAIL_FAST));
    assertTrue(ex.getMessage().contains("邮箱") || ex.getMessage().contains("email"));
  }

  @Test
  void validObjectPasses() {
    ValidatedRow row = new ValidatedRow("张三", 30, "zhangsan@example.com");
    assertDoesNotThrow(() -> DataValidator.validate(row, 5, ValidationMode.FAIL_FAST));
    assertDoesNotThrow(() -> DataValidator.validate(row, 5, ValidationMode.COLLECT_ALL));
  }

  // ==================== 双模式行为 ====================

  @Test
  void collectAllAggregatesAllViolations() {
    // 同时违反 @NotNull（年龄）与 @Email（邮箱）→ 汇总消息应包含两处错误
    ValidatedRow row = new ValidatedRow("张三", null, "bad-email");
    ExcelReadException ex =
        assertThrows(
            ExcelReadException.class,
            () -> DataValidator.validate(row, 9, ValidationMode.COLLECT_ALL));
    String message = ex.getMessage();
    assertTrue(message.contains("2 处错误"), "应收集到 2 处错误，实际: " + message);
    assertTrue(message.contains("邮箱") || message.contains("email"));
  }

  @Test
  void failFastThrowsWithRowContext() {
    ValidatedRow row = new ValidatedRow("张三", 200, "zhangsan@example.com");
    ExcelReadException ex =
        assertThrows(
            ExcelReadException.class,
            () -> DataValidator.validate(row, 12, ValidationMode.FAIL_FAST));
    assertNotNull(ex.getMessage());
    assertTrue(ex.getMessage().contains("12"), "异常应携带行号上下文: " + ex.getMessage());
  }

  // ==================== 字段名友好映射与空对象 ====================

  @Test
  void nullObjectAlwaysFails() {
    ExcelReadException ex =
        assertThrows(
            ExcelReadException.class, () -> DataValidator.validate(null, 3, ValidationMode.FAIL_FAST));
    assertTrue(ex.getMessage().contains("对象为null"));
  }

  @Test
  void noAnnotationDtoPassesBothModes() {
    // 无约束注解的对象（如 ExcelRoundTripTest.EmployeeRow 场景）不受委托路径影响
    PlainRow row = new PlainRow();
    assertDoesNotThrow(() -> DataValidator.validate(row, 1, ValidationMode.FAIL_FAST));
    assertDoesNotThrow(() -> DataValidator.validate(row, 1, ValidationMode.COLLECT_ALL));
  }

  /** 带标准约束的 DTO：@NotBlank 与 @Email 为旧路径漏放的关键约束 */
  public static class ValidatedRow {

    @NotBlank(message = "姓名不能为空白")
    @ExcelProperty("姓名")
    private String name;

    @NotNull(message = "年龄不能为空")
    @Min(value = 0, message = "年龄不能小于0")
    @Max(value = 150, message = "年龄不能大于150")
    @ExcelProperty("年龄")
    private Integer age;

    @Email(message = "邮箱格式不正确")
    @ExcelProperty("邮箱")
    private String email;

    public ValidatedRow() {}

    public ValidatedRow(String name, Integer age, String email) {
      this.name = name;
      this.age = age;
      this.email = email;
    }

    public String getName() {
      return name;
    }

    public Integer getAge() {
      return age;
    }

    public String getEmail() {
      return email;
    }
  }

  /** 无任何校验注解的 DTO */
  public static class PlainRow {

    @ExcelProperty("备注")
    private String remark = "ok";

    private List<String> tags = new ArrayList<>();

    public String getRemark() {
      return remark;
    }
  }
}
