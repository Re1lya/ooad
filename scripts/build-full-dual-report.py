"""Build a shareable dual-design report from current documentation and verified evidence."""
from pathlib import Path
import argparse,html,json,re,shutil,os
import markdown
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--output',required=True);args=parser.parse_args()
out=Path(args.output).resolve();out.mkdir(parents=True,exist_ok=True)
images=out/'full-dual-diagrams';images.mkdir(exist_ok=True)
for p in (ROOT/'docs/design/images').iterdir():
 if p.suffix in ['.png','.svg']:shutil.copy2(p,images/p.name)
evidence=out/'full-dual-evidence';evidence.mkdir(exist_ok=True)
for source in ['docs/first-review/verification.json','docs/first-review/openapi.json','docs/design/diagram-validation.json','docs/design/phase-two/full-dual-company-readonly.json']:
 p=ROOT/source
 if p.exists():shutil.copy2(p,evidence/p.name)
for mode in ['structured','ddd']:
 folder=evidence/mode;folder.mkdir(exist_ok=True)
 for name in ['browser-verification.json','navigation-verification.json','import-verification.json']:
  shutil.copy2(ROOT/'docs/design/phase-two'/('full-dual-'+mode)/name,folder/name)
verification=json.loads((ROOT/'docs/first-review/verification.json').read_text(encoding='utf-8'))
s=(ROOT/'docs/phase-two/full-dual-design.md').read_text(encoding='utf-8')
s=s.replace('../design/images/','full-dual-diagrams/')
# 导出文档使用相对路径；克隆或下载后可在不同电脑打开。
def relative_url(path):
 return Path(os.path.relpath(path,out)).as_posix()
def local_link(m):
 label,target=m[1],m[2]
 if target.startswith(('http:','https:','#')) or target.startswith('full-dual-diagrams/'):
  return m.group(0)
 resolved=(ROOT/'docs/phase-two'/target.split('#')[0]).resolve()
 return '['+label+']('+relative_url(resolved)+')'
s=re.sub(r'\[([^\]]+)\]\(([^)]+)\)',local_link,s)
(out/'全业务双设计说明.md').write_text(s,encoding='utf-8')
body=markdown.markdown(s,extensions=['tables','fenced_code','toc'])
# 源码阅读页复制普通生产源码，仅包含固定所选类，不读取运行目录或凭据。
selections=[
 ('HTTP 用户入口','controller/user/UserController.java'),('DTO/VO 映射','controller/user/UserVoMapper.java'),
 ('用户用例端口','service/port/user/UserUseCase.java'),('结构化用户实现','service/user/UserService.java'),('面向对象用户实现','service/ddd/application/user/DddUserService.java'),
 ('账号领域对象','service/ddd/domain/model/Account.java'),('期限值对象','service/ddd/domain/model/AccessWindow.java'),
 ('领域用户仓储','service/ddd/domain/repository/AccountRepository.java'),('JPA 用户适配器','infrastructure/persistence/JpaAccountRepositoryAdapter.java'),
 ('结构化权限实现','service/permission/PermissionService.java'),('面向对象权限实现','service/ddd/application/permission/DddPermissionService.java'),('权限领域对象','service/ddd/domain/model/FrontendPermission.java'),
 ('角色继承应用服务','service/ddd/application/rbac/DddHierarchyService.java'),('角色图领域对象','service/ddd/domain/model/RoleGraph.java'),('授权领域快照','service/ddd/domain/model/AuthorizationSnapshot.java'),
 ('面向对象 API 鉴权','service/ddd/application/rbac/DddAuthorizationService.java'),
 ('结构化员工信息同步','service/directory/DirectorySyncService.java'),('面向对象员工信息同步','service/ddd/application/directory/DddDirectorySyncService.java'),('员工目录领域对象','service/ddd/domain/model/EmployeeDirectory.java'),
 ('实际模式清单','controller/rbac/SystemInfoController.java')]
code_nav=[];code_sections=[]
for i,(label,relative) in enumerate(selections):
 p=ROOT/'src/main/java/com/cdwy/permission'/relative
 assert p.is_file(),p
 anchor='code-'+str(i)
 code_nav.append(f'<a href="#{anchor}">{html.escape(label)}</a>')
 lines='\n'.join(f'<span class="line" id="{anchor}-L{n}"><a href="#{anchor}-L{n}">{n:3}</a> {html.escape(line)}</span>' for n,line in enumerate(p.read_text(encoding='utf-8').splitlines(),1))
 code_sections.append(f'<section id="{anchor}"><h2>{html.escape(label)}</h2><p>{html.escape(relative)} · <a href="{relative_url(p)}">打开原文件</a></p><details><summary>展开源码（带行号）</summary><pre class="source">{lines}</pre></details></section>')
style='''*{box-sizing:border-box}body{margin:0;background:#f5f4ef;color:#223a33;font:16px/1.8 "Microsoft YaHei",sans-serif}header{background:#173c32;color:#fff;padding:45px max(24px,calc((100vw - 1180px)/2))}header p{opacity:.8}main{max-width:1240px;margin:auto;padding:30px}h1{font-size:32px}h2{margin-top:45px;border-bottom:1px solid #cdd9d2;padding-bottom:12px}h3{margin-top:30px}a{color:#1e6852;word-break:break-word}header a{color:#fff}table{border-collapse:collapse;width:100%;display:block;overflow:auto;background:white;font-size:14px}th,td{border:1px solid #d4ddd6;padding:12px;min-width:100px}th{background:#e4ede5}pre{padding:18px;overflow:auto;background:#e8eee9;border-radius:10px;font:14px/1.7 Consolas,monospace}code{font-family:Consolas,monospace}img{max-width:100%;background:white;margin:15px auto;display:block;border:1px solid #d4ddd6}section{margin-top:24px}.facts{display:flex;gap:18px;flex-wrap:wrap}.fact{border:1px solid #d4ddd6;padding:18px;background:white;border-radius:12px;flex:1;min-width:150px}.fact b{font-size:28px;display:block}.jump{display:flex;gap:12px;flex-wrap:wrap}.jump a{background:#e5eee6;padding:5px 12px;border-radius:8px}.line{display:block;white-space:pre}.line a{color:#6e8277;text-decoration:none}details{background:white;border:1px solid #d4ddd6;padding:12px;border-radius:8px}summary{cursor:pointer}.source{max-height:700px}@media print{body{background:#fff}header{padding:20px}pre{white-space:pre-wrap}main{padding:10px}h2{break-after:avoid}img{break-inside:avoid}}'''
page=f'''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Cdwy · 全业务双设计说明</title><style>{style}</style><header><h1>统一权限管理系统 · 全业务双设计</h1><p>Cdwy · 2026-11-18 · 教师检查版本</p><a href="双设计源码阅读.html">打开源码对照阅读页 →</a></header><main><div class="facts"><div class="fact"><b>10</b>业务模块双实现</div><div class="fact"><b>{verification['tests']['tests']}</b>后端测试通过</div><div class="fact"><b>{verification['browserCheckCount']}</b>两模式浏览器检查</div><div class="fact"><b>{verification['diagramCount']}</b>可重复生成设计图</div></div>{body}</main></html>'''
(out/'全业务双设计说明.html').write_text(page,encoding='utf-8')
code=f'''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Cdwy · 双设计源码阅读</title><style>{style}</style><header><h1>双设计源码对照</h1><p>展开每个类查看当前源码和行号；本页只收录固定的生产类，不包含运行凭据或员工资料。</p><a href="全业务双设计说明.html">返回设计说明 →</a></header><main><nav class="jump">{''.join(code_nav)}</nav>{''.join(code_sections)}</main></html>'''
(out/'双设计源码阅读.html').write_text(code,encoding='utf-8')
print('Report and code-reading page:',out)
