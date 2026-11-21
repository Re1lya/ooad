package com.cdwy.permission.service.port.directory;

import com.cdwy.permission.service.port.data.DirectoryData;

/** 员工信息表的文件格式边界：把上传的文件解析并校验为数据行；格式错误抛出带行号的 IllegalArgumentException。 */
public interface EmployeeSheetReader {
  /**
   * 解析并校验整张员工信息表，返回数据行与文件内容摘要；任何一行不合法都拒绝整张表。
   *
   * <p>现有 Excel 实现抛 {@code InvalidDataException}（HTTP 400）：文件无法读取或超出大小、行数限制时为
   * {@code EXCEL_INVALID}；表头不符、必填为空、超长、用户名格式不合法或表内重复时为 {@code EMPLOYEE_SHEET_INVALID}，消息带 Excel 行号。
   */
  DirectoryData.Sheet read(byte[] workbook);
}
