package com.njydsz.pmis.user.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.user.dto.EmployeeTagCreateDTO;
import com.njydsz.pmis.user.entity.EmployeeTagDO;
import com.njydsz.pmis.user.mapper.EmployeeTagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * EmployeeTagServiceImpl 测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("EmployeeTagServiceImpl 人员标签")
class EmployeeTagServiceImplTest {

    private EmployeeTagMapper mapper;
    private EmployeeTagServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(EmployeeTagMapper.class);
        service = new EmployeeTagServiceImpl(mapper);
    }

    @Test
    @DisplayName("add 标签类型无效")
    void add_invalidType() {
        EmployeeTagCreateDTO dto = baseDto();
        dto.setTagType("WRONG");
        assertThatThrownBy(() -> service.add(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("add 熟练度越界")
    void add_invalidProficiency() {
        EmployeeTagCreateDTO dto = baseDto();
        dto.setProficiency(7);
        assertThatThrownBy(() -> service.add(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("add 缺标签名")
    void add_missingName() {
        EmployeeTagCreateDTO dto = baseDto();
        dto.setTagName(null);
        assertThatThrownBy(() -> service.add(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("add 成功补齐默认值")
    void add_success() {
        EmployeeTagCreateDTO dto = baseDto();
        when(mapper.insert(any(EmployeeTagDO.class))).thenAnswer(inv -> {
            EmployeeTagDO t = inv.getArgument(0);
            t.setId(11L);
            return 1;
        });
        Long id = service.add(dto);
        assertThat(id).isEqualTo(11L);
    }

    @Test
    @DisplayName("remove 缺 id")
    void remove_nullId() {
        assertThatThrownBy(() -> service.remove(null))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("replaceByEmployee 空列表等价清空")
    void replace_empty() {
        service.replaceByEmployee(7L, List.of());
    }

    @Test
    @DisplayName("findCandidates 缺 tagType 返回空")
    void find_empty() {
        assertThat(service.findCandidates("", "JAVA")).isEmpty();
    }

    @Test
    @DisplayName("listByEmployee 委托 mapper")
    void listByEmployee() {
        when(mapper.selectByEmployee(1L)).thenReturn(List.of(new EmployeeTagDO()));
        assertThat(service.listByEmployee(1L)).hasSize(1);
    }

    private EmployeeTagCreateDTO baseDto() {
        EmployeeTagCreateDTO d = new EmployeeTagCreateDTO();
        d.setEmployeeId(1L);
        d.setTagType("SKILL");
        d.setTagCode("JAVA");
        d.setTagName("Java 开发");
        d.setProficiency(4);
        d.setYearsExp(5);
        return d;
    }
}
