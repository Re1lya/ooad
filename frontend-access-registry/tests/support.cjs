/**
 * 权限台账浏览器测试公共部分：隔离开关、浏览器启动、HTTP 辅助、登录与导航、合成 xlsx。
 * 只在 PHASE_TWO_DEMO_ACK=isolated-h2 声明的隔离实例上运行（会写入合成数据）；所有数据都是合成的。
 *
 * PHASE_TWO_URL      后端地址（接口经它调用），默认 http://127.0.0.1:18081
 * REGISTRY_URL       权限台账页面地址，默认 <PHASE_TWO_URL>/access-registry/index.html；
 *                    用 Vite 开发服务器时填 http://127.0.0.1:5173/
 * PHASE_TWO_ADMIN_USERNAME / PHASE_TWO_ADMIN_PASSWORD  管理员账号
 * CHROME_PATH 或 EDGE_PATH                             浏览器可执行文件
 * PHASE_TWO_OUTPUT                                     截图目录，可空
 */
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const zlib = require('node:zlib');
const { chromium } = require('playwright-core');

const isolated = process.env.PHASE_TWO_DEMO_ACK === 'isolated-h2';
const base = (process.env.PHASE_TWO_URL || 'http://127.0.0.1:18081').replace(/\/$/, '');
const ui = process.env.REGISTRY_URL || base + '/access-registry/index.html';
const admin = { username: process.env.PHASE_TWO_ADMIN_USERNAME, password: process.env.PHASE_TWO_ADMIN_PASSWORD };
const out = process.env.PHASE_TWO_OUTPUT;
const skip = isolated ? false : '需要 PHASE_TWO_DEMO_ACK=isolated-h2 的隔离实例';

function launch() {
  return chromium.launch({
    executablePath:
      process.env.CHROME_PATH || process.env.EDGE_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  });
}

async function call(token, route, method = 'GET', body) {
  const response = await fetch(base + route, {
    method,
    headers: {
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const text = await response.text();
  return { status: response.status, data: text ? JSON.parse(text) : null };
}

async function api(token, route, method = 'GET', body) {
  const { status, data } = await call(token, route, method, body);
  assert.ok(status >= 200 && status < 300, `${method} ${route} → ${status} ${JSON.stringify(data)}`);
  return data;
}

async function token(username = admin.username, password = admin.password) {
  return (await api('', '/api/auth/login', 'POST', { username, password })).accessToken;
}

/** rbac 应用里登记的控制台菜单与按钮（后端启动时幂等登记），返回 编码 → 权限。 */
async function consolePermissions(adminToken) {
  const system = (await api(adminToken, '/api/systems')).find((s) => s.code === 'rbac');
  assert.ok(system, '后端启动时应已登记 rbac 应用');
  const list = await api(adminToken, `/api/permissions?systemId=${system.id}`);
  return Object.fromEntries(list.map((p) => [p.code, p]));
}

/** 记录页面上的请求，等到没有进行中的请求再继续。 */
function track(page) {
  const state = { inflight: 0, requests: [] };
  page.on('request', (r) => {
    state.inflight++;
    if (r.url().includes('/api/')) state.requests.push({ method: r.method(), url: r.url(), menu: r.headers()['x-client-menu'] });
  });
  page.on('requestfinished', () => state.inflight--);
  page.on('requestfailed', () => state.inflight--);
  page.settle = async () => {
    for (let i = 0; i < 80; i++) {
      await page.waitForTimeout(100);
      if (state.inflight <= 0) {
        await page.waitForTimeout(200);
        if (state.inflight <= 0) return;
      }
    }
  };
  return state;
}

async function login(page, username, password) {
  await page.goto(ui + '#/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: '盖章进入' }).click();
  await page.locator('.rail').waitFor();
  await page.settle();
}

/** 侧栏里当前看得到的菜单名。 */
async function navItems(page) {
  return (await page.locator('.rail__item').allInnerTexts()).map((t) => t.trim());
}

async function nav(page, label) {
  await page.locator('.rail__item', { hasText: new RegExp('^\\s*' + label + '\\s*$') }).click();
  await page.locator('.topbar__crumb', { hasText: label }).waitFor();
  await page.settle();
}

/** 台账表里含某段文字的一行。 */
function row(page, text) {
  return page.locator('tr', { hasText: text });
}

/** 回答二次确认框：ok=true 点确认按钮，否则点取消。 */
async function answer(page, ok, okText = '注销') {
  const dialog = page.getByRole('dialog');
  await dialog.waitFor();
  await dialog.getByRole('button', { name: ok ? okText : '取消', exact: true }).click();
  await dialog.waitFor({ state: 'detached' });
  await page.settle();
}

async function shot(page, name) {
  if (!out) return;
  await fs.mkdir(out, { recursive: true });
  await page.screenshot({ path: path.join(out, name + '.png'), fullPage: true });
}

/** 最小合法 OOXML（仅一个 inlineStr 工作表，不压缩），只用于合成测试数据。 */
function xlsx(rows) {
  const escape = (v) => String(v).replace(/&/g, '&amp;').replace(/</g, '&lt;');
  const col = (j) => String.fromCharCode(65 + j);
  const sheet =
    '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><dimension ref="A1"/><sheetData>' +
    rows
      .map(
        (r, i) =>
          `<row r="${i + 1}">` +
          r.map((v, j) => `<c r="${col(j)}${i + 1}" t="inlineStr"><is><t>${escape(v)}</t></is></c>`).join('') +
          '</row>',
      )
      .join('') +
    '</sheetData></worksheet>';
  const files = [
    [
      '[Content_Types].xml',
      '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/></Types>',
    ],
    ['xl/worksheets/sheet1.xml', sheet],
  ];
  const parts = [];
  const central = [];
  let offset = 0;
  for (const [name, text] of files) {
    const data = Buffer.from(text, 'utf8');
    const nameBytes = Buffer.from(name, 'utf8');
    const crc = zlib.crc32(data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(data.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(nameBytes.length, 26);
    parts.push(local, nameBytes, data);
    const entry = Buffer.alloc(46);
    entry.writeUInt32LE(0x02014b50, 0);
    entry.writeUInt16LE(20, 4);
    entry.writeUInt16LE(20, 6);
    entry.writeUInt16LE(0x0800, 8);
    entry.writeUInt32LE(crc, 16);
    entry.writeUInt32LE(data.length, 20);
    entry.writeUInt32LE(data.length, 24);
    entry.writeUInt16LE(nameBytes.length, 28);
    entry.writeUInt32LE(offset, 42);
    central.push(entry, nameBytes);
    offset += 30 + nameBytes.length + data.length;
  }
  const centralBuffer = Buffer.concat(central);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(files.length, 8);
  end.writeUInt16LE(files.length, 10);
  end.writeUInt32LE(centralBuffer.length, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...parts, centralBuffer, end]);
}

async function tempDir() {
  return fs.mkdtemp(path.join(os.tmpdir(), 'access-registry-'));
}

/** 合成数据的后缀，避免与已有数据重名。 */
const suffix = () => Date.now().toString(36);

/** 一个打开权限台账的浏览器上下文。 */
async function open(browser) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, reducedMotion: 'reduce' });
  const page = await context.newPage();
  page.setDefaultTimeout(10000);
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  const traffic = track(page);
  return { context, page, errors, traffic };
}

module.exports = {
  assert,
  base,
  ui,
  admin,
  out,
  skip,
  launch,
  call,
  api,
  token,
  consolePermissions,
  login,
  navItems,
  nav,
  row,
  answer,
  shot,
  xlsx,
  tempDir,
  suffix,
  open,
};
