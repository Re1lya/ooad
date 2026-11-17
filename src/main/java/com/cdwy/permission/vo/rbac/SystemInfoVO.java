package com.cdwy.permission.vo.rbac;

/** 公开运行模式与各业务实际实现，不包含部署凭据。 */
public record SystemInfoVO(
    String backendStrategy, String authorizationModel, java.util.Map<String, String> modules) {
  /** 模块清单防御性复制。 */
  public SystemInfoVO {
    modules = java.util.Map.copyOf(modules);
  }
}
