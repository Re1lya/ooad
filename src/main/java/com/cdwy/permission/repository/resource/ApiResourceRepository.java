package com.cdwy.permission.repository.resource;

import com.cdwy.permission.entity.resource.ApiResource;
import org.springframework.data.jpa.repository.JpaRepository;

/** API 资源仓储：接入应用登记的受保护接口（HTTP 方法、路径模板、operationId、后端权限码），供资源管理、接口鉴权和统一身份判权使用。 */
public interface ApiResourceRepository extends JpaRepository<ApiResource, Long> {
  /** 同一应用内是否已登记相同方法与路径模板：按字符串精确比较（区分大小写），不做模板匹配，含已停用资源。 */
  boolean existsBySystem_IdAndMethodAndPath(Long systemId, String method, String path);

  /** 应用下是否还有 API 资源（含已停用）；删除应用前检查。 */
  boolean existsBySystem_Id(Long systemId);

  /** 按应用、方法和路径模板精确查找（区分大小写，不做模板匹配，含已停用）；空表示尚未登记。 */
  java.util.Optional<ApiResource> findBySystem_IdAndMethodAndPath(
      Long systemId, String method, String path);

  /** 同一应用内 operationId 是否已被占用（区分大小写，含已停用资源）；operationId 为空时不应调用，空值会按 {@code is null} 匹配。 */
  boolean existsBySystem_IdAndOperationId(Long systemId, String operationId);

  /** 按应用编码和 operationId 精确查找（区分大小写），不过滤资源和应用的启用状态，由调用方判断；空表示未登记。 */
  java.util.Optional<ApiResource> findBySystem_CodeAndOperationId(String code, String operationId);

  /**
   * 某应用某个 HTTP 方法下的全部资源，作为路径模板匹配的候选；方法名精确比较（区分大小写），顺序不保证。
   * 含已停用资源：调用方先选出最具体的模板再判断是否停用，宽泛模板因此不能绕过停用的具体资源。
   */
  java.util.List<ApiResource> findAllBySystem_CodeAndMethod(String code, String method);
}
