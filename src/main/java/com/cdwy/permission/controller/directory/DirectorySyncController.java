package com.cdwy.permission.controller.directory;

import com.cdwy.permission.config.BusinessAccess;
import com.cdwy.permission.controller.common.BadRequestException;
import com.cdwy.permission.service.port.directory.DirectorySyncUseCase;
import com.cdwy.permission.vo.directory.SyncSummaryVO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
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
  private final DirectoryVoMapper mapper;

  /** 只校验并计算变化，不写数据库。 */
  @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public SyncSummaryVO preview(
      @RequestPart MultipartFile employees,
      @RequestParam(defaultValue = "总公司")
          @NotBlank(message = "请填写总公司名称")
          @Size(max = 128, message = "最长 128 个字符")
          String headOffice,
      @RequestParam(required = false) @Size(max = 64, message = "最长 64 个字符") String headOfficeRole,
      @RequestParam(required = false) @Size(max = 64, message = "最长 64 个字符") String branchRole)
      throws IOException {
    return mapper.toVO(
        service.preview(bytes(employees), mapper.toCommand(headOffice, headOfficeRole, branchRole)));
  }

  /** 文件与规则须与预览时一致（digest 相同），在一个事务内写入。 */
  @PostMapping(value = "/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public SyncSummaryVO commit(
      @RequestPart MultipartFile employees,
      @RequestParam(defaultValue = "总公司")
          @NotBlank(message = "请填写总公司名称")
          @Size(max = 128, message = "最长 128 个字符")
          String headOffice,
      @RequestParam(required = false) @Size(max = 64, message = "最长 64 个字符") String headOfficeRole,
      @RequestParam(required = false) @Size(max = 64, message = "最长 64 个字符") String branchRole,
      @RequestParam @NotBlank(message = "请先预览") @Size(max = 64, message = "最长 64 个字符")
          String digest)
      throws IOException {
    return mapper.toVO(
        service.commit(
            bytes(employees), mapper.toCommand(headOffice, headOfficeRole, branchRole), digest));
  }

  private static byte[] bytes(MultipartFile file) throws IOException {
    if (file.isEmpty()) throw new BadRequestException("请选择员工信息表");
    return file.getBytes();
  }
}
