package com.cdwy.permission.repository.system;

import com.cdwy.permission.entity.system.BusinessSystem;
import org.springframework.data.jpa.repository.JpaRepository;

/** 接入应用仓储：应用编码、名称、地址与启用状态，供应用管理使用；登录签发、统一身份接口和按应用取菜单都按编码查找。 */
public interface BusinessSystemRepository extends JpaRepository<BusinessSystem, Long> {
  /** 应用编码是否已被占用：精确匹配（区分大小写），含已停用应用。 */
  boolean existsByCode(String code);

  /** 按编码精确查找应用（区分大小写），不过滤已停用应用，启用状态由调用方判断；空表示应用未登记。 */
  java.util.Optional<BusinessSystem> findByCode(String code);
}
