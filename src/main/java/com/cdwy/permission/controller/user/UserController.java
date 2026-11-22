package com.cdwy.permission.controller.user;

import com.cdwy.permission.config.BusinessAccess;
import com.cdwy.permission.config.CurrentUser;
import com.cdwy.permission.dto.common.StatusDTO;
import com.cdwy.permission.dto.user.PasswordResetDTO;
import com.cdwy.permission.dto.user.UserCreateDTO;
import com.cdwy.permission.dto.user.UserUpdateDTO;
import com.cdwy.permission.service.port.user.UserUseCase;
import com.cdwy.permission.vo.common.PageVO;
import com.cdwy.permission.vo.user.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 人员管理：新建、检索、修改、启停、重置密码与删除。 */
@RestController
@BusinessAccess(BusinessAccess.CONSOLE)
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {
  private final UserUseCase service;
  private final UserVoMapper mapper;

  /** 新建人员。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserVO create(@Valid @RequestBody UserCreateDTO input) {
    return mapper.toVO(service.create(mapper.toCommand(input)));
  }

  /** 按工号、学号或姓名分页检索人员，页码从 1 开始。 */
  @GetMapping
  public PageVO<UserVO> list(
      @RequestParam(defaultValue = "1") @Min(value = 1, message = "须大于等于 1") int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "须在 1 到 100 之间")
          @Max(value = 100, message = "须在 1 到 100 之间")
          int pageSize,
      @RequestParam(required = false) @Size(max = 100, message = "最长 100 个字符")
          String keyword) {
    return mapper.toPageVO(service.list(page, pageSize, keyword));
  }

  /** 人员详情。 */
  @GetMapping("/{id}")
  public UserVO get(@PathVariable Long id) {
    return mapper.toVO(service.get(id));
  }

  /** 修改姓名、部门、人员类型与有效期。 */
  @PutMapping("/{id}")
  public UserVO update(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO input) {
    return mapper.toVO(service.update(id, mapper.toCommand(input)));
  }

  /** 启用或停用；不能停用自己。 */
  @PutMapping("/{id}/status")
  public UserVO status(
      HttpServletRequest request, @PathVariable Long id, @Valid @RequestBody StatusDTO input) {
    return mapper.toVO(service.status(CurrentUser.from(request).id(), id, input.enabled()));
  }

  /** 管理员重置密码。 */
  @PutMapping("/{id}/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void password(@PathVariable Long id, @Valid @RequestBody PasswordResetDTO input) {
    service.password(id, input.password());
  }

  /** 删除人员；不能删除自己。 */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(HttpServletRequest request, @PathVariable Long id) {
    service.delete(CurrentUser.from(request).id(), id);
  }
}
