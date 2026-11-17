package com.cdwy.permission.service.port.data;

public class UserResult {

  private Long id;

  private String username;

  private String displayName;

  private boolean enabled;

  private boolean administrator;

  private Long departmentId;

  private String kind;

  private java.time.Instant expiresAt;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String value) {
    username = value;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String value) {
    displayName = value;
  }

  public boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(boolean value) {
    enabled = value;
  }

  public boolean getAdministrator() {
    return administrator;
  }

  public void setAdministrator(boolean value) {
    administrator = value;
  }

  public Long getDepartmentId() {
    return departmentId;
  }

  public void setDepartmentId(Long value) {
    departmentId = value;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String value) {
    kind = value;
  }

  public java.time.Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(java.time.Instant value) {
    expiresAt = value;
  }

  @lombok.Getter @lombok.Setter private String companyName;

  @lombok.Getter @lombok.Setter private String jobTitle;

  @lombok.Getter @lombok.Setter private String phone;

  @lombok.Getter @lombok.Setter private String sourceDepartment;
}
