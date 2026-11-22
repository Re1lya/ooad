package com.cdwy.permission.dto.user;

import com.cdwy.permission.service.port.data.PersonType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 修改人员资料的请求；外部与临时人员的有效期规则由服务层检查。
 *
 * @param displayName 姓名
 * @param departmentId 所属部门
 * @param personType 人员类型
 * @param expiresOn 有效期截止日
 */
public record UserUpdateDTO(
    @NotBlank @Size(max = 128) String displayName,
    Long departmentId,
    @NotNull PersonType personType,
    LocalDate expiresOn) {}
