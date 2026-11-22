package com.cdwy.permission.vo.system;

/**
 * 接入应用。
 *
 * @param id 标识
 * @param code 应用编码
 * @param name 名称
 * @param baseUrl 访问地址
 * @param enabled 是否启用
 */
public record SystemVO(Long id, String code, String name, String baseUrl, boolean enabled) {}
