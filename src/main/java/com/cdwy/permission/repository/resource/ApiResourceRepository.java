package com.cdwy.permission.repository.resource;

import com.cdwy.permission.entity.resource.ApiResource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiResourceRepository extends JpaRepository<ApiResource, Long> {
  boolean existsBySystem_IdAndMethodAndPath(Long systemId, String method, String path);

  boolean existsBySystem_Id(Long systemId);

  java.util.Optional<ApiResource> findBySystem_IdAndMethodAndPath(
      Long systemId, String method, String path);

  boolean existsBySystem_IdAndOperationId(Long systemId, String operationId);

  java.util.Optional<ApiResource> findBySystem_CodeAndOperationId(String code, String operationId);

  java.util.List<ApiResource> findAllBySystem_CodeAndMethod(String code, String method);
}
