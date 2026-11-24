package com.cdwy.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.persistence.Column;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.metamodel.EntityType;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.hibernate.annotations.Check;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 部署用建表脚本与实体映射的一致性，在真实 MySQL 上核对。
 *
 * <p>测试库由 {@code docs/first-review/database-mysql.sql} 建立，Hibernate 以 validate 模式启动时已核对表、列和类型；
 * 这里再核对 validate 不检查的主键、唯一约束、外键和检查约束，保证数据库兜底约束与实体声明一一对应。
 */
@ActiveProfiles("test")
@SpringBootTest
class MySqlSchemaTests {
  private static final String PRIMARY_KEYS =
      "select k.table_name, k.column_name from information_schema.table_constraints c"
          + " join information_schema.key_column_usage k on k.constraint_schema = c.constraint_schema"
          + " and k.table_name = c.table_name and k.constraint_name = c.constraint_name"
          + " where c.table_schema = database() and c.constraint_type = 'PRIMARY KEY'";
  private static final String UNIQUE_KEYS =
      "select k.table_name, group_concat(k.column_name order by k.column_name) as columns"
          + " from information_schema.table_constraints c"
          + " join information_schema.key_column_usage k on k.constraint_schema = c.constraint_schema"
          + " and k.table_name = c.table_name and k.constraint_name = c.constraint_name"
          + " where c.table_schema = database() and c.constraint_type = 'UNIQUE'"
          + " group by k.table_name, k.constraint_name";
  private static final String FOREIGN_KEYS =
      "select table_name, column_name, referenced_table_name from information_schema.key_column_usage"
          + " where table_schema = database() and referenced_table_name is not null";
  private static final String CHECKS =
      "select c.table_name, k.check_clause from information_schema.table_constraints c"
          + " join information_schema.check_constraints k on k.constraint_schema = c.constraint_schema"
          + " and k.constraint_name = c.constraint_name"
          + " where c.table_schema = database() and c.constraint_type = 'CHECK'";

  @Autowired JdbcTemplate db;
  @Autowired EntityManagerFactory entityManagerFactory;

  /** NFR-03：数据库层的唯一键、外键与检查约束是业务不变量的兜底，必须与实体声明完全一致。 */
  @Test
  @Tag("等价类")
  @DisplayName("NFR-03 NFR-09 在真实 MySQL 上，部署建表脚本的主键、唯一约束、外键与检查约束与实体声明一致")
  void NFR03_deploymentDdlMatchesEntityConstraints() {
    Set<String> primary = new TreeSet<>();
    Set<String> unique = new TreeSet<>();
    Set<String> foreign = new TreeSet<>();
    Set<String> checks = new TreeSet<>();
    for (EntityType<?> entity : entityManagerFactory.getMetamodel().getEntities()) {
      declared(entity.getJavaType(), primary, unique, foreign, checks);
    }

    assertEquals(primary, rows(PRIMARY_KEYS, row -> row.get("TABLE_NAME") + "." + row.get("COLUMN_NAME")));
    assertEquals(unique, rows(UNIQUE_KEYS, row -> row.get("TABLE_NAME") + "(" + row.get("columns") + ")"));
    assertEquals(
        foreign,
        rows(
            FOREIGN_KEYS,
            row ->
                row.get("TABLE_NAME") + "." + row.get("COLUMN_NAME") + "->" + row.get("REFERENCED_TABLE_NAME")));
    assertEquals(checks, rows(CHECKS, row -> row.get("TABLE_NAME") + ":" + clause(row.get("CHECK_CLAUSE"))));
    assertEquals(14, foreign.size());
  }

  /** 从实体注解收集声明的主键、唯一约束、外键与检查约束。 */
  private static void declared(
      Class<?> type, Set<String> primary, Set<String> unique, Set<String> foreign, Set<String> checks) {
    String table = type.getAnnotation(Table.class).name();
    for (UniqueConstraint constraint : type.getAnnotation(Table.class).uniqueConstraints()) {
      unique.add(table + "(" + sorted(constraint.columnNames()) + ")");
    }
    SecondaryTable secondary = type.getAnnotation(SecondaryTable.class);
    if (secondary != null) {
      String join = secondary.pkJoinColumns()[0].name();
      primary.add(secondary.name() + "." + join);
      foreign.add(secondary.name() + "." + join + "->" + table);
      for (UniqueConstraint constraint : secondary.uniqueConstraints()) {
        unique.add(secondary.name() + "(" + sorted(constraint.columnNames()) + ")");
      }
    }
    Check check = type.getAnnotation(Check.class);
    if (check != null) checks.add(table + ":" + clause(check.constraints()));
    for (Field field : type.getDeclaredFields()) {
      Column column = field.getAnnotation(Column.class);
      String owner = column != null && !column.table().isEmpty() ? column.table() : table;
      String name = column != null && !column.name().isEmpty() ? column.name() : snake(field.getName());
      if (field.isAnnotationPresent(Id.class)) primary.add(table + "." + name);
      if (column != null && column.unique()) unique.add(owner + "(" + name + ")");
      JoinColumn join = field.getAnnotation(JoinColumn.class);
      if (field.isAnnotationPresent(ManyToOne.class) && join != null) {
        foreign.add(table + "." + join.name() + "->" + field.getType().getAnnotation(Table.class).name());
      }
    }
  }

  private Set<String> rows(String sql, java.util.function.Function<Map<String, Object>, String> key) {
    Set<String> result = new TreeSet<>();
    for (Map<String, Object> row : db.queryForList(sql)) result.add(key.apply(upper(row)));
    return result;
  }

  /** MySQL 返回的列名大小写随版本不同，统一按大写取值（自定义别名 columns 保留原样）。 */
  private static Map<String, Object> upper(Map<String, Object> row) {
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    row.forEach(
        (name, value) -> result.put(name.equals("columns") ? name : name.toUpperCase(), value));
    return result;
  }

  private static String sorted(String[] columns) {
    List<String> names = Arrays.stream(columns).sorted().toList();
    return String.join(",", names);
  }

  /** 去掉反引号、括号和空白后比较检查约束表达式（MySQL 会改写表达式的书写形式）。 */
  private static String clause(Object text) {
    return String.valueOf(text).replaceAll("[`()\\s]", "");
  }

  private static String snake(String name) {
    return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
  }
}
