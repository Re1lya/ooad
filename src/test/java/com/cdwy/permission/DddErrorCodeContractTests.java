package com.cdwy.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cdwy.permission.service.ddd.domain.model.AccessRole;
import com.cdwy.permission.service.ddd.domain.model.DepartmentNode;
import com.cdwy.permission.service.ddd.domain.model.ProtectedResource;
import com.cdwy.permission.service.ddd.domain.repository.RoleCatalog;
import com.cdwy.permission.service.port.error.ConflictException;
import com.cdwy.permission.service.port.error.InvalidDataException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 错误码契约的 DDD 路线：复用 {@link ErrorCodeContractTests} 的全部断言，并补充只在领域层与持久化适配器出现的错误码。
 */
@ActiveProfiles("test")
@SpringBootTest(
    properties = {
      "permission.jwt.secret=first-review-integration-signing-secret-32-bytes",
      "permission.policy.external-max-days=7",
      "permission.policy.temporary-max-days=3",
      "app.backend-strategy=ddd"
    })
class DddErrorCodeContractTests extends ErrorCodeContractTests {
  @Autowired RoleCatalog roleCatalog;

  /** NFR-03：DDD 适配器在保存处就地把唯一约束冲突翻译为 DATA_CONFLICT。 */
  @Test
  @DisplayName("NFR-03 DDD 仓储适配器把数据库唯一约束冲突就地翻译为 DATA_CONFLICT")
  void NFR03_adapterTranslatesUniqueViolation() {
    AccessRole duplicate = new AccessRole();
    duplicate.setCode("teacher");
    duplicate.setName("并发写入的同编码角色");
    ConflictException error = assertThrows(ConflictException.class, () -> roleCatalog.save(duplicate));
    assertEquals("DATA_CONFLICT", error.getCode());
  }

  /**
   * FR-26：领域对象自行守住格式不变量。HTTP 请求在 API 层已按同样规则校验（返回 BAD_REQUEST），这些错误码只在领域对象被绕过
   * API 层直接使用时出现。
   */
  @Test
  @DisplayName("FR-26 领域对象拒绝不合法的资源路径、权限码、operationId 与部门编码")
  void FR26_domainObjectsGuardFormats() {
    ProtectedResource resource = new ProtectedResource();
    resource.setPath("/api/**");
    assertEquals("API_RESOURCE_PATH_INVALID", code(resource::assertPath));
    resource.setCode("1bad");
    assertEquals("API_RESOURCE_CODE_INVALID", code(resource::assertCode));
    resource.setOperationId("bad id");
    assertEquals("API_OPERATION_ID_INVALID", code(resource::assertOperationId));
    DepartmentNode department = new DepartmentNode();
    assertEquals(
        "DEPARTMENT_CODE_INVALID",
        code(() -> department.assertUnique("-bad", List.of(), null, "部门")));
  }

  private static String code(org.junit.jupiter.api.function.Executable action) {
    return assertThrows(InvalidDataException.class, action).getCode();
  }
}
