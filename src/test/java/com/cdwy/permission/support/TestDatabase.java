package com.cdwy.permission.support;

import java.sql.Statement;
import java.util.List;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** 测试数据准备：在当前测试事务内清空全部业务表，测试结束回滚后恢复原状。 */
public final class TestDatabase {
  private TestDatabase() {}

  /**
   * 删除当前库中所有业务表的数据。
   *
   * <p>用 DELETE 而不是 TRUNCATE：MySQL 的 TRUNCATE 会隐式提交事务，测试就无法回滚。外键检查是会话级开关，
   * 所以关闭、删除、重新打开必须在同一个连接上完成。
   */
  public static void clear(JdbcTemplate db) {
    List<String> tables =
        db.queryForList(
            "select table_name from information_schema.tables"
                + " where table_schema = database() and table_type = 'BASE TABLE'",
            String.class);
    db.execute(
        (ConnectionCallback<Void>)
            connection -> {
              try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String table : tables) statement.executeUpdate("DELETE FROM " + table);
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
              }
              return null;
            });
  }
}
