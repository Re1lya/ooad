package com.cdwy.permission.vo.resource;

/**
 * 已登记的 API 资源。
 *
 * @param id 标识
 * @param systemId 所属应用
 * @param name 名称
 * @param method HTTP 方法
 * @param path 路径模式
 * @param active 是否生效
 * @param code 后端权限码
 * @param operationId 接口的 operationId
 */
public record ApiResourceVO(
    Long id,
    Long systemId,
    String name,
    String method,
    String path,
    boolean active,
    String code,
    String operationId) {}
