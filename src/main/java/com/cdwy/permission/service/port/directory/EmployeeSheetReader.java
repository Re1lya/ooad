package com.cdwy.permission.service.port.directory;

import com.cdwy.permission.service.port.data.DirectoryData;

/** 员工信息表的文件格式边界：把上传的文件解析并校验为数据行；格式错误抛出带行号的 IllegalArgumentException。 */
public interface EmployeeSheetReader {
  DirectoryData.Sheet read(byte[] workbook);
}
