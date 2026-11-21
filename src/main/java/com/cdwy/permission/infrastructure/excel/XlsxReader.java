package com.cdwy.permission.infrastructure.excel;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** 读取 OOXML 工作簿首张工作表；不信任 dimension，不执行公式或外部链接。 */
public final class XlsxReader {
  private XlsxReader() {}

  /** 有界展开 ZIP 并读取第一张工作表和共享字符串，保留空列位置。 */
  public static List<List<String>> rows(byte[] bytes) {
    if (bytes.length > 8_000_000) throw new IllegalArgumentException("单个Excel不能超过8MB");
    Map<String, byte[]> entries = new HashMap<>();
    int expanded = 0;
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory()) continue;
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zip.read(buffer)) != -1) {
          expanded += read;
          if (expanded > 40_000_000) throw new IllegalArgumentException("Excel展开内容过大");
          data.write(buffer, 0, read);
        }
        entries.put(entry.getName(), data.toByteArray());
      }
      List<String> shared = new ArrayList<>();
      if (entries.containsKey("xl/sharedStrings.xml")) {
        NodeList values =
            parse(entries.get("xl/sharedStrings.xml")).getElementsByTagNameNS("*", "si");
        for (int i = 0; i < values.getLength(); i++) shared.add(text((Element) values.item(i)));
      }
      byte[] sheet = entries.get(firstWorksheet(entries));
      if (sheet == null) throw new IllegalArgumentException("Excel缺少第一张工作表");
      NodeList rows = parse(sheet).getElementsByTagNameNS("*", "row");
      if (rows.getLength() > 20002) throw new IllegalArgumentException("Excel最多20000条数据");
      List<List<String>> result = new ArrayList<>();
      for (int i = 0; i < rows.getLength(); i++) {
        Element row = (Element) rows.item(i);
        NodeList cells = row.getElementsByTagNameNS("*", "c");
        List<String> values = new ArrayList<>();
        for (int j = 0; j < cells.getLength(); j++) {
          Element cell = (Element) cells.item(j);
          String address = cell.getAttribute("r");
          int column = 0;
          for (char letter : address.toCharArray()) {
            if (letter < 'A' || letter > 'Z') break;
            column = column * 26 + (letter - 'A' + 1);
          }
          if (column < 1 || column > 64) throw new IllegalArgumentException("Excel列位置不合法");
          while (values.size() < column) values.add("");
          String type = cell.getAttribute("t");
          NodeList v = cell.getElementsByTagNameNS("*", "v");
          String value = v.getLength() == 0 ? "" : v.item(0).getTextContent();
          if ("s".equals(type)) value = shared.get(Integer.parseInt(value));
          else if ("inlineStr".equals(type)) value = text(cell);
          values.set(column - 1, value.strip());
        }
        if (values.stream().anyMatch(v -> !v.isBlank())) result.add(List.copyOf(values));
      }
      return List.copyOf(result);
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("无法读取Excel，请检查文件格式与内容");
    }
  }

  /** 依据工作簿首个 sheet 的 relationship 定位 ZIP 项，拒绝外链与越界目标。 */
  private static String firstWorksheet(Map<String, byte[]> entries) throws Exception {
    if (!entries.containsKey("xl/workbook.xml")) return "xl/worksheets/sheet1.xml";
    NodeList sheets = parse(entries.get("xl/workbook.xml")).getElementsByTagNameNS("*", "sheet");
    if (sheets.getLength() == 0 || !entries.containsKey("xl/_rels/workbook.xml.rels"))
      throw new IllegalArgumentException("Excel工作簿缺少工作表关系");
    Element first = (Element) sheets.item(0);
    String id =
        first.getAttributeNS(
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
    NodeList relationships =
        parse(entries.get("xl/_rels/workbook.xml.rels"))
            .getElementsByTagNameNS("*", "Relationship");
    for (int i = 0; i < relationships.getLength(); i++) {
      Element relation = (Element) relationships.item(i);
      if (!relation.getAttribute("Id").equals(id)) continue;
      if (relation.getAttribute("TargetMode").equals("External")
          || !relation.getAttribute("Type").endsWith("/worksheet"))
        throw new IllegalArgumentException("Excel首张表关系不合法");
      String target = relation.getAttribute("Target");
      java.nio.file.Path resolved =
          java.nio.file.Path.of(target.startsWith("/") ? target.substring(1) : "xl/" + target)
              .normalize();
      String name = resolved.toString().replace('\\', '/');
      if (!name.startsWith("xl/worksheets/") || name.contains(":"))
        throw new IllegalArgumentException("Excel工作表位置不合法");
      return name;
    }
    throw new IllegalArgumentException("Excel未找到首张工作表");
  }

  /** 禁止 DTD、实体与网络解析。 */
  private static Document parse(byte[] bytes) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
  }

  /** 富文本与普通文本均按原顺序组合。 */
  private static String text(Element element) {
    NodeList nodes = element.getElementsByTagNameNS("*", "t");
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < nodes.getLength(); i++) result.append(nodes.item(i).getTextContent());
    return result.toString();
  }
}
