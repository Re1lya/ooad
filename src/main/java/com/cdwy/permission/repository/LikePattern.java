package com.cdwy.permission.repository;

/** 模糊查询的 LIKE 模式：转义通配符，转小写，前后加 %；配合 JPQL {@code like :p escape '!'} 使用。 */
public final class LikePattern {
  private LikePattern() {}

  /** 空白关键字返回 null，表示不过滤。 */
  public static String of(String keyword) {
    if (keyword == null || keyword.isBlank()) return null;
    String escaped =
        keyword.trim().toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_");
    return "%" + escaped + "%";
  }
}
