import { expect, test } from '@playwright/test';
import pg from 'pg';

test.beforeEach(async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: '전문 작성' })).toBeVisible();
});

test('real HTTP transmission returns correlated identifiers', async ({ page }, testInfo) => {
  const requestId = `E2E-${crypto.randomUUID()}`;
  await page.getByLabel('Request ID').fill(requestId);
  const responsePromise = page.waitForResponse(response =>
    response.url().endsWith('/api/requests') && response.request().method() === 'POST');
  await page.getByRole('button', { name: '전문 전송 →' }).click();
  const response = await responsePromise;
  expect(response.status()).toBe(200);
  const result = await response.json();
  expect(result.status).toBe('SUCCESS');
  expect(result.requestId).toBe(requestId);
  expect(result.traceId).toMatch(/^[a-f0-9]{32}$/);
  expect(result.attemptId).toMatch(/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/);
  expect(result.response.traceId).toBe(result.traceId);
  expect(result.response.requestId).toBe(requestId);
  expect(response.headers()['x-trace-id']).toBe(result.traceId);
  expect(response.request().postDataJSON().payload.userName).toBe('홍길동');
  expect(response.request().postDataJSON()).not.toHaveProperty('payloadObject');
  if (!process.env.E2E_DB_URL) throw new Error('E2E_DB_URL must point to the disposable stack database.');
  const databaseURL = new URL(process.env.E2E_DB_URL);
  databaseURL.username = process.env.DB_USER || databaseURL.username || 'interfaceflow';
  if (process.env.DB_PASSWORD) databaseURL.password = process.env.DB_PASSWORD;
  const database = new pg.Client({ connectionString: databaseURL.toString() });
  try {
    await database.connect();
    const source = await database.query(
      'SELECT request_id, trace_id, attempt_id, status FROM integration.if_request WHERE id=$1', [result.id]);
    expect(source.rows).toEqual([{
      request_id: requestId, trace_id: result.traceId, attempt_id: result.attemptId, status: 'SUCCESS',
    }]);
    const target = await database.query(
      'SELECT request_id, trace_id, attempt_id FROM target.if_target_receipt WHERE attempt_id=$1', [result.attemptId]);
    expect(target.rows).toEqual([{
      request_id: requestId, trace_id: result.traceId, attempt_id: result.attemptId,
    }]);
    const snapshots = await database.query('SELECT original_payload,mapped_payload,interface_version FROM integration.if_request WHERE id=$1',[result.id]);
    expect(snapshots.rows[0].original_payload.email).toBe('***');
    expect(snapshots.rows[0].mapped_payload.employee_id).toBe('USER001');
    expect(snapshots.rows[0].mapped_payload).not.toHaveProperty('userId');
    expect(snapshots.rows[0].interface_version).toBe(result.interfaceVersion);
  } finally { await database.end(); }
  await expect(page.getByRole('status')).toContainText(result.attemptId);
  await page.screenshot({ path: testInfo.outputPath('console-success.png'), fullPage: true });
});

test('preview validates and maps without dispatching a request', async ({ page }) => {
  let dispatches = 0;
  page.on('request', request => { if(request.url().endsWith('/api/requests'))dispatches++; });
  const responsePromise=page.waitForResponse(response=>response.url().endsWith('/preview'));
  await page.getByRole('button',{name:'변환 미리보기'}).click();
  const preview=await (await responsePromise).json();
  expect(preview.valid).toBe(true);
  expect(preview.mappedPayload.employee_id).toBe('USER001');
  expect(preview.mappedPayload).not.toHaveProperty('userId');
  expect(preview.originalPayload.email).toBe('***');
  await expect(page.getByTestId('mapping-preview')).toContainText('employee_id');
  expect(dispatches).toBe(0);
});

test('source validation records a failed request without a target attempt', async ({ page }) => {
  const requestId=`E2E-INVALID-${crypto.randomUUID()}`;
  await page.getByLabel('Request ID').fill(requestId);
  await page.getByLabel('요청 JSON').fill('{"userId":"U1"}');
  const responsePromise=page.waitForResponse(response=>response.url().endsWith('/api/requests') && response.request().method()==='POST');
  await page.getByRole('button',{name:'전문 전송 →'}).click();
  const result=await (await responsePromise).json();
  expect(result.status).toBe('VALIDATION_FAILED');expect(result.processingStage).toBe('SOURCE_VALIDATION');
  expect(result.attemptId).toBeNull();expect(result.errors[0].field).toBe('userName');
  await expect(page.getByRole('status')).toContainText('userName');
  const database=databaseClient();
  try {
    await database.connect();
    const records=await database.query('SELECT status,attempt_id FROM integration.if_request WHERE id=$1',[result.id]);
    expect(records.rows).toEqual([{status:'VALIDATION_FAILED',attempt_id:null}]);
    const target=await database.query('SELECT count(*)::integer AS count FROM target.if_target_receipt WHERE trace_id=$1',[result.traceId]);
    expect(target.rows[0].count).toBe(0);
  } finally {await database.end();}
});

test('interface editor versions real mapping and preserves transmission history', async ({ page },testInfo) => {
  const id=`IF-E2E-${crypto.randomUUID()}`,requestId=`E2E-${crypto.randomUUID()}`;
  await page.getByRole('button',{name:'인터페이스 관리',exact:true}).click();
  await page.getByRole('button',{name:'신규 등록',exact:true}).click();
  await page.getByLabel('Interface ID',{exact:true}).fill(id);
  await page.getByLabel('인터페이스 이름',{exact:true}).fill('브라우저 매핑 검증');
  await page.getByLabel('필드 매핑 규칙 (JSON)',{exact:true}).fill(JSON.stringify([
    {source:'userId',target:'employee_id',transform:'COPY',required:true,nullPolicy:'ERROR'},
  ]));
  await page.getByLabel('Source / Target 검증 규칙 (JSON)',{exact:true}).fill(JSON.stringify([
    {field:'userId',stage:'SOURCE',type:'STRING',required:true,nullable:false,minLength:1},
    {field:'employee_id',stage:'TARGET',type:'STRING',required:true,nullable:false},
  ]));
  await page.getByRole('button',{name:'설정 저장',exact:true}).click();
  await expect(page.getByRole('status')).toContainText('v1 저장 완료');
  await page.getByLabel('인터페이스 이름',{exact:true}).fill('브라우저 매핑 검증 v2');
  await page.getByRole('button',{name:'설정 저장',exact:true}).click();
  await expect(page.getByRole('status')).toContainText('v2 저장 완료');
  await page.screenshot({path:testInfo.outputPath('interface-editor.png'),fullPage:true});
  await page.getByRole('button',{name:'전문 전송',exact:true}).click();
  await page.getByLabel('인터페이스',{exact:true}).selectOption(id);
  await page.getByLabel('Request ID').fill(requestId);
  await page.getByLabel('요청 JSON').fill('{"userId":"E2E-USER","ignored":true}');
  const responsePromise=page.waitForResponse(response=>response.url().endsWith('/api/requests') && response.request().method()==='POST');
  await page.getByRole('button',{name:'전문 전송 →'}).click();
  const result=await (await responsePromise).json();
  expect(result.status).toBe('SUCCESS');expect(result.interfaceVersion).toBe(2);
  expect(result.mappedPayload).toEqual({employee_id:'E2E-USER'});
  await page.getByRole('button',{name:'전송 이력',exact:true}).click();
  await page.getByLabel('이력 검색').fill(requestId);
  await page.getByRole('button',{name:'검색 / 새로고침'}).click();
  await page.getByRole('button',{name:requestId,exact:true}).click();
  await expect(page.getByRole('heading',{name:'요청 상세 · 마스킹된 전문과 설정 스냅샷'})).toBeVisible();
  await expect(page.locator('pre')).toContainText('브라우저 매핑 검증 v2');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({path:testInfo.outputPath('transmission-history.png'),fullPage:true});
  await page.getByRole('button',{name:'인터페이스 관리',exact:true}).click();
  await page.getByRole('button',{name:new RegExp(id)}).click();
  await page.getByRole('button',{name:'삭제 / 이력 있으면 비활성화',exact:true}).click();
  await expect(page.getByRole('status')).toContainText('이력을 보존하기 위해 비활성화');
  await page.getByRole('button',{name:'전문 전송',exact:true}).click();
  await page.getByLabel('인터페이스',{exact:true}).selectOption(id);
  await expect(page.getByRole('button',{name:'전문 전송 →'})).toBeDisabled();
});

function databaseClient() {
  if(!process.env.E2E_DB_URL)throw new Error('E2E_DB_URL is required');
  const url=new URL(process.env.E2E_DB_URL);
  url.username=process.env.DB_USER||url.username||'interfaceflow';
  if(process.env.DB_PASSWORD)url.password=process.env.DB_PASSWORD;
  return new pg.Client({connectionString:url.toString()});
}

test('invalid JSON stays local and sample restores a valid payload', async ({ page }) => {
  const outgoing: string[] = [];
  page.on('request', request => {
    if (request.url().endsWith('/api/requests')) outgoing.push(request.url());
  });
  await page.getByLabel('요청 JSON').fill('{');
  await page.getByRole('button', { name: '전문 전송 →' }).click();
  await expect(page.locator('.error')).toBeVisible();
  expect(outgoing).toHaveLength(0);
  await page.getByRole('button', { name: '샘플 불러오기' }).click();
  expect(JSON.parse(await page.getByLabel('요청 JSON').inputValue()).userId).toBe('USER001');
});

test('one in-flight request disables submission until real response', async ({ page }) => {
  let release!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/requests', async route => { await gate; await route.continue(); });
  await page.getByRole('button', { name: '전문 전송 →' }).click();
  try {
    await expect(page.getByRole('button', { name: '전송 중…' })).toBeDisabled();
    await expect(page.getByRole('status')).toContainText('응답을 기다리는 중');
  } finally { release(); }
  await expect(page.getByRole('status')).toContainText('SUCCESS');
  await expect(page.getByRole('button', { name: '전문 전송 →' })).toBeEnabled();
});

test('failed network request displays error and enables another submission', async ({ page }) => {
  // Transport fault injection only; success tests always reach the real two-server stack.
  await page.route('**/api/requests', route => route.abort('connectionfailed'));
  await page.getByRole('button', { name: '전문 전송 →' }).click();
  await expect(page.locator('.error')).toBeVisible();
  await expect(page.getByRole('button', { name: '전문 전송 →' })).toBeEnabled();
  await expect(page.getByRole('status')).not.toContainText('SUCCESS');
});

test('non-JSON gateway error preserves HTTP status in feedback', async ({ page }) => {
  // This isolates gateway feedback; it does not simulate successful integration.
  await page.route('**/api/requests', route => route.fulfill({
    status: 502, contentType: 'text/html', body: '<html>Bad Gateway</html>',
  }));
  await page.getByRole('button', { name: '전문 전송 →' }).click();
  await expect(page.locator('.error')).toContainText('HTTP 502');
  await expect(page.getByRole('button', { name: '전문 전송 →' })).toBeEnabled();
});

test('theme, keyboard focus and narrow layout remain usable', async ({ page }, testInfo) => {
  await expect(page.locator('.app')).toHaveClass(/dark/);
  await page.getByRole('button', { name: '라이트 모드' }).click();
  await expect(page.locator('.app')).not.toHaveClass(/dark/);
  await page.getByLabel('Request ID').fill('invalid request');
  await expect(page.getByRole('button', { name: '전문 전송 →' })).toBeDisabled();
  await page.getByLabel('Request ID').fill('E2E-VALID');
  await page.getByLabel('Request ID').focus();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('button', { name: '샘플 불러오기' })).toBeFocused();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('console-light.png'), fullPage: true });
});
