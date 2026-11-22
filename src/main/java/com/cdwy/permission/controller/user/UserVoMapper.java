package com.cdwy.permission.controller.user;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.cdwy.permission.dto.user.UserCreateDTO;
import com.cdwy.permission.dto.user.UserUpdateDTO;
import com.cdwy.permission.service.port.data.CreateUserCommand;
import com.cdwy.permission.service.port.data.PersonType;
import com.cdwy.permission.service.port.data.UserData;
import com.cdwy.permission.service.port.data.UserResult;
import com.cdwy.permission.vo.common.PageVO;
import com.cdwy.permission.vo.user.UserVO;
import org.springframework.stereotype.Component;

/** 人员管理的 DTO → 命令、结果 → VO 映射。同名字段用 CloneFactory 复制，人员类型在枚举与编码之间显式转换。 */
@Component
public class UserVoMapper {
  /** 新建人员请求转为创建命令。 */
  public CreateUserCommand toCommand(UserCreateDTO dto) {
    CreateUserCommand command = CloneFactory.copy(new CreateUserCommand(), dto);
    command.setKind(dto.getPersonType().name());
    return command;
  }

  /** 修改人员请求转为修改命令。 */
  public UserData.Update toCommand(UserUpdateDTO dto) {
    return new UserData.Update(
        dto.displayName(), dto.departmentId(), dto.personType().name(), dto.expiresOn());
  }

  /** 人员结果转为 VO，不含密码散列和凭证版本。 */
  public UserVO toVO(UserResult result) {
    UserVO vo = CloneFactory.copy(new UserVO(), result);
    vo.setPersonType(PersonType.valueOf(result.getKind()));
    return vo;
  }

  /** 人员分页结果转为分页 VO。 */
  public PageVO<UserVO> toPageVO(UserData.Page<UserResult> page) {
    return new PageVO<>(
        page.page(), page.pageSize(), page.total(), page.items().stream().map(this::toVO).toList());
  }
}
