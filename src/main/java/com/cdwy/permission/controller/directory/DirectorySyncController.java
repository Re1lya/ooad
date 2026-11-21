package com.cdwy.permission.controller.directory;

import com.cdwy.permission.config.BusinessAccess;
import com.cdwy.permission.dto.directory.DirectoryDtos;
import com.cdwy.permission.service.port.data.DirectoryData;
import com.cdwy.permission.service.port.directory.DirectorySyncUseCase;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 员工信息同步：上传员工信息表，先预览变化，再凭摘要确认写入；可以重复执行。
 *
 * <p>headOffice 默认“总公司”；headOfficeRole、branchRole 为总公司员工、其他分公司员工补授的角色编码，不填则不授予。
 */
@RestController
@BusinessAccess(BusinessAccess.CONSOLE)
@RequiredArgsConstructor
@RequestMapping("/api/directory/sync")
public class DirectorySyncController {
  private final DirectorySyncUseCase service;

  /** 只校验并计算变化，不写数据库。 */
  @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public DirectoryDtos.Summary preview(
      @RequestPart MultipartFile employees,
      @RequestParam(defaultValue = "总公司") String headOffice,
      @RequestParam(required = false) String headOfficeRole,
      @RequestParam(required = false) String branchRole)
      throws IOException {
    return view(
        service.preview(
            bytes(employees), new DirectoryData.Rule(headOffice, headOfficeRole, branchRole)));
  }

  /** 文件与规则须与预览时一致（digest 相同），在一个事务内写入。 */
  @PostMapping(value = "/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public DirectoryDtos.Summary commit(
      @RequestPart MultipartFile employees,
      @RequestParam(defaultValue = "总公司") String headOffice,
      @RequestParam(required = false) String headOfficeRole,
      @RequestParam(required = false) String branchRole,
      @RequestParam String digest)
      throws IOException {
    return view(
        service.commit(
            bytes(employees),
            new DirectoryData.Rule(headOffice, headOfficeRole, branchRole),
            digest));
  }

  private static byte[] bytes(MultipartFile file) throws IOException {
    if (file.isEmpty()) throw new IllegalArgumentException("请选择员工信息表");
    return file.getBytes();
  }

  private static DirectoryDtos.Summary view(DirectoryData.Summary v) {
    return new DirectoryDtos.Summary(
        v.rows(),
        v.companies(),
        v.departmentsCreated(),
        v.departmentsKept(),
        v.usersCreated(),
        v.usersUpdated(),
        v.usersUnchanged(),
        v.roleGrants(),
        v.digest(),
        v.applied());
  }
}
