package com.cdwy.permission.controller.identity;

import com.cdwy.permission.dto.identity.IdentityDtos;
import com.cdwy.permission.service.port.data.AuditData;
import com.cdwy.permission.service.port.data.IdentityData;

/** 接入应用接口的显式边界映射：用例结果 → HTTP 响应，HTTP 请求 → 用例输入。 */
public final class IdentityMapping {
  private IdentityMapping() {}

  public static IdentityDtos.Current view(IdentityData.Current v) {
    return new IdentityDtos.Current(v.subject(), v.roles(), v.permissions(), v.name(), v.no());
  }

  public static IdentityDtos.Page<IdentityDtos.Department> departments(
      IdentityData.Page<IdentityData.Department> v) {
    return new IdentityDtos.Page<>(
        v.page(),
        v.pageSize(),
        v.total(),
        v.items().stream()
            .map(
                d ->
                    new IdentityDtos.Department(
                        d.id(), d.code(), d.name(), d.parentId(), d.hasChildren()))
            .toList());
  }

  public static IdentityDtos.Page<IdentityDtos.DepartmentUser> departmentUsers(
      IdentityData.Page<IdentityData.DepartmentUser> v) {
    return new IdentityDtos.Page<>(
        v.page(),
        v.pageSize(),
        v.total(),
        v.items().stream()
            .map(
                u ->
                    new IdentityDtos.DepartmentUser(
                        u.userId(), u.no(), u.name(), u.departmentId(), u.departmentName()))
            .toList());
  }

  public static IdentityDtos.User view(IdentityData.User v) {
    return new IdentityDtos.User(v.userId(), v.no(), v.name());
  }

  public static AuditData.Write command(IdentityDtos.AuditWrite v) {
    return new AuditData.Write(
        v.requestId(),
        v.type(),
        v.objectType(),
        v.objectId(),
        v.target(),
        v.before(),
        v.after(),
        v.termId(),
        v.courseId(),
        v.classId());
  }

  public static IdentityDtos.AuditEntry view(AuditData.Entry v) {
    IdentityDtos.AuditActor actor =
        v.actor() == null
            ? null
            : new IdentityDtos.AuditActor(
                v.actor().id(), v.actor().name(), v.actor().roles(), v.actor().deleted());
    return new IdentityDtos.AuditEntry(
        v.id(),
        v.systemCode(),
        v.at(),
        actor,
        v.type(),
        v.objectType(),
        v.objectId(),
        v.target(),
        v.before(),
        v.after(),
        v.termId(),
        v.courseId(),
        v.classId());
  }

  public static IdentityDtos.AuditPage view(AuditData.Page v) {
    return new IdentityDtos.AuditPage(
        v.page(), v.pageSize(), v.total(), v.items().stream().map(IdentityMapping::view).toList());
  }
}
