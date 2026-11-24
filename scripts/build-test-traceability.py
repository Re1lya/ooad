"""根据测试源码生成需求—测试追踪表与错误码—测试表，写入 docs/second-review/04-test-traceability.adoc。

需求编号取自每个测试方法的 @DisplayName（形如 "FR-18 ..." 或 "NFR-07 ..."），测试类型（等价类、边界值、异常）取自
@Tag，浏览器测试取自 test(...) 上一行的“// 测试类型：”注释；每条功能需求三类都要有，缺一类时脚本报错。需求名称取自
docs/second-review/01-requirements.adoc 的需求表。错误码取自主代码中业务异常的第一个参数，再在测试源码中查找断言它的测试方法。
测试数量取自 target/surefire-reports（先运行 mvn clean verify）。

用法：python3 scripts/build-test-traceability.py
"""
from collections import defaultdict
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
TESTS = ROOT / "src/test/java/com/cdwy/permission"
MAIN = ROOT / "src/main/java/com/cdwy/permission"
DOC = ROOT / "docs/second-review/04-test-traceability.adoc"
REQUIREMENTS = ROOT / "docs/second-review/01-requirements.adoc"
BEGIN, END = "// tag::generated[]", "// end::generated[]"
STATUS = {"ResourceNotFoundException": 404, "ConflictException": 409, "InvalidDataException": 400}
GENERIC = {"BAD_REQUEST": 400, "UNAUTHORIZED": 401, "FORBIDDEN": 403, "DATA_CONFLICT": 409}


def requirement_names():
    names = {}
    for line in REQUIREMENTS.read_text(encoding="utf-8").splitlines():
        match = re.match(r"\|((?:N?FR)-\d+) \|([^|]+)\|", line)
        if match:
            names[match.group(1)] = match.group(2).strip()
    return names


def test_methods():
    """返回 [(类名, 方法名, 显示名, 方法体, 测试类型)]：方法体包含它调用的本类辅助方法，便于找到经辅助方法断言的错误码。"""
    methods = []
    declaration = re.compile(r"^  (?=[^ \n])(?!(?:if|for|while|switch|try|catch|return|new)\b)(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|protected|private|static|final)\s+)*"
                             r"[\w<>\[\], ?.]+\s+(\w+)\s*\([^;{]*\)\s*(?:throws [\w., ]+)?\{", re.M)
    for path in sorted(TESTS.rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        spans = [(m.group(1), m.start(), m.group(0)) for m in declaration.finditer(text)]
        bodies = {}
        tests = []
        for index, (name, start, signature) in enumerate(spans):
            end = spans[index + 1][1] if index + 1 < len(spans) else len(text)
            head = text[max(0, start - 400):start]
            annotations = (head[head.rfind("\n\n") + 1:] if "\n\n" in head else head) + signature
            bodies[name] = bodies.get(name, "") + text[start:end]
            if "@Test" in annotations:
                display = re.search(r'@DisplayName\("([^"]*)"\)', annotations)
                kinds = tuple(re.findall(r'@Tag\("([^"]+)"\)', annotations))
                tests.append((name, display.group(1) if display else "", kinds))
        for name, display, kinds in tests:
            body = bodies[name]
            for helper, helper_body in bodies.items():
                if helper != name and re.search(r"\b" + helper + r"\(", body):
                    body += helper_body
            methods.append((path.stem, name, display, body, kinds))
    return methods


def browser_tests():
    """两个前端的浏览器测试（权限台账、原管理控制台）：返回 [(相对路径, 测试名, 测试类型)]。"""
    found = []
    for folder in ("frontend-access-registry/tests", "frontend/tests"):
        for path in sorted((ROOT / folder).glob("*.test.cjs")):
            text = path.read_text(encoding="utf-8")
            for match in re.finditer(r"(?:// 测试类型：([^\n]*)\n)?test\('([^']+)'", text):
                kinds = tuple(k for k in (match.group(1) or "").split("、") if k)
                found.append((f"{folder}/{path.name}", match.group(2), kinds))
    return found


def ddd_variants():
    """结构化测试类 -> 继承它、以 DDD 路线重跑的测试类。"""
    variants = {}
    for path in TESTS.rglob("*.java"):
        match = re.search(r"class (\w+) extends (\w+)", path.read_text(encoding="utf-8"))
        if match and match.group(1).startswith("Ddd"):
            variants[match.group(2)] = match.group(1)
    return variants


def error_codes():
    codes = dict(GENERIC)
    for path in MAIN.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        for match in re.finditer(r'(ResourceNotFoundException|ConflictException|InvalidDataException)\(\s*"([A-Z_]+)"', text):
            codes.setdefault(match.group(2), STATUS[match.group(1)])
    return codes


def surefire_counts():
    counts = {}
    for report in (ROOT / "target/surefire-reports").glob("TEST-*.xml"):
        suite = ET.parse(report).getroot()
        counts[suite.attrib["name"].rsplit(".", 1)[-1]] = int(suite.attrib["tests"])
    return counts


def build():
    names = requirement_names()
    methods = test_methods()
    variants = ddd_variants()
    counts = surefire_counts()

    by_requirement = defaultdict(list)
    by_kind = defaultdict(lambda: defaultdict(list))
    for owner, method, display, _, kinds in methods:
        for requirement in re.findall(r"\b(N?FR-\d+)\b", display):
            by_requirement[requirement].append((owner, method, display))
            for kind in kinds:
                by_kind[requirement][kind].append(f"`{owner}.{method}`")
    for owner, display, kinds in browser_tests():
        for requirement in re.findall(r"\b(N?FR-\d+)\b", display):
            by_requirement[requirement].append((owner, "", display))
            for kind in kinds:
                by_kind[requirement][kind].append(f"`{owner}`")

    def routes(owner):
        if owner.endswith(".test.cjs"):
            return "浏览器（两条路线各跑一遍）"
        return "结构化 + DDD" if owner in variants else ("DDD" if owner.startswith("Ddd") else "通用")

    lines = [BEGIN, "", "=== 测试类", "",
             "`Ddd*` 类继承对应的结构化测试类，只把 `app.backend-strategy` 改为 `ddd`，同一组测试方法在两条设计路线上各跑一遍。", "",
             '[cols="3,1,1",options="header"]', "|===", "|测试类 |设计路线 |测试数"]
    for owner in sorted({m[0] for m in methods}):
        if owner.startswith("Ddd") and any(v == owner for v in variants.values()):
            continue
        variant = variants.get(owner)
        label = f"`{owner}` / `{variant}`" if variant else f"`{owner}`"
        total = counts.get(owner, 0) + (counts.get(variant, 0) if variant else 0)
        lines.append(f"|{label} |{routes(owner)} |{total}")
    def order(requirement):
        kind, number = requirement.split("-")
        return (kind == "NFR", int(number))

    lines += ["|===", "", "=== 等价类、边界值与异常", "",
              "每条功能需求都有三类测试：等价类取一组合法输入看正常结果（含停用、撤回等合法的状态变化）；"
              "边界值在长度、数量、分页、层级等界限的两侧各取一个值；异常覆盖非法输入、缺少授权、引用冲突与不存在的对象。"
              "后端测试用 `@Tag(\"等价类\")`、`@Tag(\"边界值\")`、`@Tag(\"异常\")` 标注，浏览器测试在 `test(...)` 上一行注明。"
              "非功能需求按各自的验收条件测试，见下一节。", "",
              '[cols="1,2,4,4,4",options="header"]', "|===", "|编号 |需求 |等价类 |边界值 |异常"]
    for requirement in sorted((r for r in names if r.startswith("FR-")), key=order):
        cells = []
        for kind in ("等价类", "边界值", "异常"):
            tests = sorted(set(by_kind[requirement][kind]))
            if not tests:
                raise SystemExit(f"{requirement} 缺少{kind}测试")
            cells.append(" +\n".join(tests))
        lines.append(f"|{requirement} |{names[requirement]} |" + " |".join(cells))
    lines += ["|===", "", "=== 需求—测试追踪", "",
              "每个测试方法的名称以需求编号开头，`@DisplayName` 写明需求编号与验收条件；下表由测试源码生成。", "",
              '[cols="1,2,6,1",options="header"]', "|===", "|编号 |需求 |测试方法（显示名） |设计路线"]

    for requirement in sorted(set(names) | set(by_requirement), key=order):
        tests = by_requirement.get(requirement)
        if not tests:
            lines.append(f"|{requirement} |{names.get(requirement, '')} |部署后核对，见第 4 节与 link:05-deployment.adoc[部署说明] |—")
            continue
        cell = " +\n".join(
            f"`{owner}.{method}`：{display}" if method else f"`{owner}`：{display}"
            for owner, method, display in tests)
        modes = sorted({routes(owner) for owner, _, _ in tests})
        lines.append(f"|{requirement} |{names.get(requirement, '')} |{cell} |{'、'.join(modes)}")
    lines += ["|===", "", "=== 错误码—测试", "",
              "每个业务错误码至少有一个测试断言响应体的 `code`；按 id 操作的接口在 `ErrorCodeContractTests.NFR07_everyByIdEndpointRejectsMissingIds` 中逐个覆盖不存在的 id。", "",
              '[cols="2,1,5",options="header"]', "|===", "|错误码 |HTTP |断言它的测试方法"]
    for code, status in sorted(error_codes().items()):
        hits = sorted({f"`{owner}.{method}`" for owner, method, _, body, _ in methods if f'"{code}"' in body})
        if not hits:
            raise SystemExit(f"错误码 {code} 没有测试断言")
        lines.append(f"|`{code}` |{status} |{'、'.join(hits)}")
    lines += ["|===", "", END]

    document = DOC.read_text(encoding="utf-8")
    start, end = document.index(BEGIN), document.index(END) + len(END)
    DOC.write_text(document[:start] + "\n".join(lines) + document[end:], encoding="utf-8")
    print(f"{len(methods)} 个测试方法，{len(by_requirement)} 个需求编号，{len(error_codes())} 个错误码")


if __name__ == "__main__":
    build()
