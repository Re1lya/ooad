package com.cdwy.permission.vo.identity;

/**
 * 能否调用指定 operationId 接口的判断结果，不返回权限码。
 *
 * @param operationId 接口的 operationId
 * @param allowed 能否调用
 */
public record ApiAuthorityCheckVO(String operationId, boolean allowed) {}
