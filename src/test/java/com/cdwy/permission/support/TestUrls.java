package com.cdwy.permission.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 测试用 URL 模板展开：把模板中的 {@code {name}} 占位符依次替换为参数，不做 URL 编码（由 MockMvc 统一编码）。 */
public final class TestUrls {
  private static final Pattern VARIABLE = Pattern.compile("\\{[^}]+}");

  private TestUrls() {}

  /** 按出现顺序展开占位符；参数个数必须与占位符个数一致。 */
  public static String url(String template, Object... values) {
    Matcher matcher = VARIABLE.matcher(template);
    StringBuilder result = new StringBuilder();
    int index = 0;
    while (matcher.find()) {
      if (index >= values.length) throw new IllegalArgumentException("缺少 URL 参数：" + template);
      matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(values[index++])));
    }
    if (index != values.length) throw new IllegalArgumentException("URL 参数过多：" + template);
    matcher.appendTail(result);
    return result.toString();
  }
}
