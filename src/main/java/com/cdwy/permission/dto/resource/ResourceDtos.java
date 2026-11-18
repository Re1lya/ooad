package com.cdwy.permission.dto.resource;

import jakarta.validation.constraints.*;

public final class ResourceDtos {
  private ResourceDtos() {}

  public record Create(
      @NotNull @Positive Long systemId,
      @NotBlank @Size(max = 128) String name,
      @NotBlank @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE") String method,
      @NotBlank @Size(max = 255) String path,
      @Pattern(regexp = "[A-Za-z][A-Za-z0-9_:.-]{0,63}", message = "须以字母开头，只含字母、数字和 _:.-")
          String code) {}

  public record Update(@NotBlank @Size(max = 128) String name) {}

  public record View(
      Long id,
      Long systemId,
      String name,
      String method,
      String path,
      boolean active,
      String code) {}
}
