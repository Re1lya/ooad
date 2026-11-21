package com.cdwy.permission.controller.importing;

import com.cdwy.permission.controller.common.HttpMapping;
import com.cdwy.permission.service.port.importing.*;
import com.cdwy.permission.vo.importing.CompanyImportSummaryVO;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 三张原始 Excel 的预览与确认入口；统一 API 拦截器要求管理员。 */
@RestController
@com.cdwy.permission.config.BusinessAccess(com.cdwy.permission.service.auth.ConsoleCatalog.SYSTEM)
@RequiredArgsConstructor
@RequestMapping("/api/imports/company")
public class CompanyImportController {
  private final CompanyImportUseCase service;

  /** 预览只解析校验，不改变数据库；不返回员工敏感明细。 */
  @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public CompanyImportSummaryVO preview(
      @RequestPart MultipartFile employees,
      @RequestPart MultipartFile departments,
      @RequestPart MultipartFile permissions)
      throws IOException {
    return HttpMapping.view(plan(employees, departments, permissions).summary());
  }

  /** 再次解析同一组文件并核对摘要后，在单一事务提交全部数据。 */
  @PostMapping(value = "/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public CompanyImportSummaryVO commit(
      @RequestPart MultipartFile employees,
      @RequestPart MultipartFile departments,
      @RequestPart MultipartFile permissions,
      @RequestParam String digest)
      throws IOException {
    return HttpMapping.view(service.commit(plan(employees, departments, permissions), digest));
  }

  /** 文件只在请求内存中处理，格式错误返回400。 */
  private CompanyImportPlan plan(
      MultipartFile users, MultipartFile departments, MultipartFile permissions)
      throws IOException {
    if (users.isEmpty() || departments.isEmpty() || permissions.isEmpty())
      throw new IllegalArgumentException("必须选择三张非空Excel文件");
    return com.cdwy.permission.service.importing.CompanyWorkbookParser.parse(
        users.getBytes(), departments.getBytes(), permissions.getBytes());
  }
}
