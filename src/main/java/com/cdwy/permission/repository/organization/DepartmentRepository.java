package com.cdwy.permission.repository.organization;

import com.cdwy.permission.entity.organization.Department;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
  boolean existsByParent_Id(Long parentId);

  Optional<Department> findByCode(String code);

  /** 同一上级（根部门的上级为空）下是否已有同名部门，排除自身。 */
  @Query(
      "select count(d) > 0 from Department d where d.name = :name"
          + " and ((:parentId is null and d.parent is null) or d.parent.id = :parentId)"
          + " and (:selfId is null or d.id <> :selfId)")
  boolean siblingNamed(
      @Param("parentId") Long parentId, @Param("name") String name, @Param("selfId") Long selfId);

  /** 一层部门：parentId 为空时为根部门；pattern 来自 {@code LikePattern}，匹配名称或编码；数据库分页。 */
  @Query(
      "select d from Department d"
          + " where ((:parentId is null and d.parent is null) or d.parent.id = :parentId)"
          + " and (:pattern is null or lower(d.name) like :pattern escape '!'"
          + " or lower(d.code) like :pattern escape '!')")
  Page<Department> level(
      @Param("parentId") Long parentId, @Param("pattern") String pattern, Pageable pageable);

  /** ids 中有直接子部门的部门标识。 */
  @Query("select distinct d.parent.id from Department d where d.parent.id in :ids")
  List<Long> parentsAmong(@Param("ids") Collection<Long> ids);
}
