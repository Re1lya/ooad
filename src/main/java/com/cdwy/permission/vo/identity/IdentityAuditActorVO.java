package com.cdwy.permission.vo.identity;

import java.util.List;

/**
 * 审计记录中的操作人快照。
 *
 * @param id 操作人标识
 * @param name 姓名
 * @param roles 操作时的角色编码
 * @param deleted 账号是否已删除
 */
public record IdentityAuditActorVO(Long id, String name, List<String> roles, boolean deleted) {}
