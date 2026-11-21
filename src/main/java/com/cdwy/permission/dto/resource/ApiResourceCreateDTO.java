package com.cdwy.permission.dto.resource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 登记 API 资源的请求；同一应用内“方法 + 路径”唯一、operationId 唯一由服务层检查。
 *
 * @param systemId 所属应用
 * @param name 名称
 * @param method HTTP 方法
 * @param path 路径模式，以 / 开头，可含 {变量}
 * @param code 下发给接入应用的后端权限码，可空
 * @param operationId 接口的 operationId，可空
 */
public record ApiResourceCreateDTO(
    @NotNull @Positive Long systemId,
    @NotBlank @Size(max = 128) String name,
    @NotBlank @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE") String method,
    @NotBlank
        @Size(max = 255)
        @Pattern(regexp = "/(?!.*\\*\\*)[^?#]*", message = "资源路径须为不含查询参数的绝对路径，不支持 ** 通配符")
        String path,
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_:.-]{0,63}", message = "须以字母开头，只含字母、数字和 _:.-")
        String code,
    @Pattern(
            regexp = "[A-Za-z_][A-Za-z0-9_.-]{0,127}",
            message = "须以字母或下划线开头，只含字母、数字和 _.-")
        String operationId) {}
