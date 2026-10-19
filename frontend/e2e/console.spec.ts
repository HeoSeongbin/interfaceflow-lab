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

test('trace explorer and dashboard show actual persisted processing', async ({ page }) => {
  const requestId=`TRACE-${crypto.randomUUID()}`;
  await page.getByLabel('Request ID').fill(requestId);
  const pending=page.waitForResponse(r=>r.url().endsWith('/api/requests')&&r.request().method()==='POST');
  await page.getByRole('button',{name:'전문 전송 →'}).click();
  const result=await (await pending).json();
  expect(result.status).toBe('SUCCESS');
  await page.getByRole('button',{name:'Trace Explorer',exact:true}).filter({visible:true}).click();
  await page.getByLabel('Trace ID 검색').fill(result.traceId);
  await page.getByRole('button',{name:'흐름 조회'}).click();
  await expect(page.getByText('REQUEST_RECEIVED · VALIDATING')).toBeVisible();
  await expect(page.getByText('RESPONSE_PROCESSED · SUCCESS')).toBeVisible();
  await expect(page.getByText(result.attemptId,{exact:false})).toBeVisible();
  await page.getByRole('button',{name:'대시보드',exact:true}).filter({visible:true}).click();
  await page.getByRole('button',{name:'현황 조회'}).click();
  await expect(page.getByRole('heading',{name:'시간별 상태 건수 (UTC)'})).toBeVisible();
  await expect(page.getByRole('cell',{name:'SUCCESS',exact:true}).first()).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});

test('simulation lab creates an isolated scenario and shows a real HTTP error', async ({page},testInfo)=>{
 await page.getByRole('button',{name:'Simulation Lab',exact:true}).filter({visible:true}).click();
 await page.getByLabel('장애 유형').selectOption('HTTP_500');
 await page.getByRole('button',{name:'시나리오 등록',exact:true}).click();
 await expect(page.getByRole('button',{name:'시나리오 실행',exact:true})).toBeEnabled();
 await page.getByRole('button',{name:'시나리오 실행',exact:true}).click();
 await expect(page.getByRole('cell',{name:'UNKNOWN',exact:false})).toBeVisible();
 await expect(page.getByRole('cell',{name:'UNKNOWN',exact:false})).toContainText('500');
 await page.getByRole('button',{name:'시나리오 목록 조회'}).click();
 await expect(page.locator('pre')).toContainText('HTTP_500');
 await page.screenshot({path:testInfo.outputPath('simulation.png'),fullPage:true});
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});

test('real simulator scenarios preserve counters, isolation and uncertain outcomes',async({page})=>{
 const base=(await (await page.request.get('/api/interfaces/IF-USER-001')).json());
 const definition={...base,interfaceId:`IF-SIM-${crypto.randomUUID()}`,interfaceName:'E2E Simulation',version:0,timeoutMs:300};
 expect((await page.request.post('/api/interfaces',{data:definition})).status()).toBe(201);
 const payload={userId:'SIM001',userName:'가상 테스트'};let lastRequestId='';
 for(const type of ['SUCCESS','HTTP_400','HTTP_500','DELAY','TIMEOUT','INVALID_RESPONSE','FAIL_THEN_SUCCESS','DUPLICATE','CONNECTION_FAILURE']){
  const requestId=`SIM-${crypto.randomUUID()}`;lastRequestId=requestId;
  const created=await page.request.post('/api/simulations',{data:{type,interfaceId:definition.interfaceId,requestId,delayMs:type==='DELAY'?50:type==='TIMEOUT'?900:0,failureCount:type==='FAIL_THEN_SUCCESS'?1:0,expiresAt:new Date(Date.now()+600000).toISOString()}});
  expect(created.status(),await created.text()).toBe(201);const scenario=await created.json();
  const execute=async()=>{const r=await page.request.post(`/api/simulations/${scenario.id}/execute`,{data:{interfaceId:definition.interfaceId,requestId,payload}});expect(r.status(),await r.text()).toBe(200);return r.json();};
  const first=await execute();
  const expected=['SUCCESS','DELAY','DUPLICATE'].includes(type)?'SUCCESS':type==='HTTP_400'?'FAILED':'UNKNOWN';
  expect(first.status,type).toBe(expected);expect(first.attemptId).toMatch(/^[a-f0-9-]{36}$/);
  if(type==='TIMEOUT'||type==='CONNECTION_FAILURE'){expect(first.httpStatus).toBeNull();expect(first.errorCode).toBe('TARGET_IO_ERROR');}
  if(type==='INVALID_RESPONSE')expect(first.errorCode).toBe('INVALID_RESPONSE');
  if(type==='FAIL_THEN_SUCCESS'||type==='DUPLICATE'){
   const second=await execute();expect(second.status).toBe(type==='FAIL_THEN_SUCCESS'?'SUCCESS':'FAILED');expect(second.attemptId).not.toBe(first.attemptId);expect(second.id).not.toBe(first.id);expect(second.requestId).toBe(first.requestId);
   if(type==='DUPLICATE')expect(second.response.code).toBe('SIMULATED_DUPLICATE');
  }
  const mismatch=await page.request.post(`/api/simulations/${scenario.id}/execute`,{data:{interfaceId:definition.interfaceId,requestId:'WRONG',payload}});expect(mismatch.status()).toBe(409);
  const trace=await (await page.request.get(`/api/traces/${first.traceId}`)).json();expect(trace[0].request.simulationId).toBe(scenario.id);
  if(!process.env.E2E_DB_URL)throw new Error('E2E_DB_URL required');
  const url=new URL(process.env.E2E_DB_URL);url.username=process.env.DB_USER||'interfaceflow';if(process.env.DB_PASSWORD)url.password=process.env.DB_PASSWORD;
  const db=new pg.Client({connectionString:url.toString()});try{await db.connect();const receipt=await db.query('SELECT request_id,attempt_id,simulation_id FROM target.if_target_receipt WHERE attempt_id=$1',[first.attemptId]);if(type==='CONNECTION_FAILURE')expect(receipt.rowCount).toBe(0);else{expect(receipt.rowCount).toBe(1);expect(receipt.rows[0].simulation_id).toBe(scenario.id);}}finally{await db.end();}
 }
 // An ordinary send with identical logical IDs must not inherit any scenario.
 const normal=await page.request.post('/api/requests',{data:{interfaceId:definition.interfaceId,requestId:lastRequestId,payload}});expect((await normal.json()).status).toBe('SUCCESS');
});

test('reliable retries keep identifiers and commit one Target business result',async({page})=>{
 const sample=await (await page.request.get('/api/interfaces/IF-USER-001')).json();
 const definition={...sample,interfaceId:`IF-REL-${crypto.randomUUID()}`,interfaceName:'Reliable test',version:0,timeoutMs:300,retryMaxAttempts:3};
 expect((await page.request.post('/api/interfaces',{data:definition})).status()).toBe(201);
 for(const type of ['FAIL_THEN_SUCCESS','TIMEOUT','INVALID_RESPONSE']){
  const requestId=`REL-${crypto.randomUUID()}`;
  const scenario=await (await page.request.post('/api/simulations',{data:{type,interfaceId:definition.interfaceId,requestId,delayMs:type==='TIMEOUT'?900:0,failureCount:type==='FAIL_THEN_SUCCESS'?1:0,expiresAt:new Date(Date.now()+600000).toISOString()}})).json();
  const body={interfaceId:definition.interfaceId,requestId,payload:{userId:'REL001',userName:'Retry demo',email:'protected@example.com'}};
  const firstResponse=await page.request.post(`/api/simulations/${scenario.id}/reliable-execute`,{data:body});expect(firstResponse.status(),await firstResponse.text()).toBe(200);const first=await firstResponse.json();
  expect(first.status,type).toBe('UNKNOWN');
  await expect.poll(async()=> (await (await page.request.get(`/api/requests/${first.id}`)).json()).status,{timeout:15000}).toBe('SUCCESS');
  const replay=await (await page.request.post(`/api/simulations/${scenario.id}/reliable-execute`,{data:body})).json();
  expect(replay.id).toBe(first.id);expect(replay.traceId).toBe(first.traceId);expect(replay.attemptId).not.toBe(first.attemptId);
  const trace=await (await page.request.get(`/api/traces/${first.traceId}`)).json();expect(trace[0].attempts).toHaveLength(2);expect(trace[0].attempts[0].id).not.toBe(trace[0].attempts[1].id);
  const url=new URL(process.env.E2E_DB_URL!);url.username=process.env.DB_USER||'interfaceflow';if(process.env.DB_PASSWORD)url.password=process.env.DB_PASSWORD;
  const db=new pg.Client({connectionString:url.toString()});try{await db.connect();expect(Number((await db.query('SELECT count(*) FROM target.if_business_result WHERE scope=$1',[scenario.id])).rows[0].count)).toBe(1);const stored=(await db.query('SELECT replay_cipher FROM integration.if_delivery WHERE request_key=$1',[first.id])).rows[0].replay_cipher;expect(stored).not.toContain('protected@example.com');}finally{await db.end();}
  expect(JSON.stringify(trace)).not.toContain('protected@example.com');
 }
});

test('retry center manually queues one additional attempt after automatic budget',async({page},testInfo)=>{
 const sample=await (await page.request.get('/api/interfaces/IF-USER-001')).json();
 const definition={...sample,interfaceId:`IF-MAN-${crypto.randomUUID()}`,interfaceName:'Manual retry',version:0,retryMaxAttempts:1};
 expect((await page.request.post('/api/interfaces',{data:definition})).status()).toBe(201);
 const requestId=`MAN-${crypto.randomUUID()}`;
 const scenario=await (await page.request.post('/api/simulations',{data:{type:'FAIL_THEN_SUCCESS',interfaceId:definition.interfaceId,requestId,delayMs:0,failureCount:1,expiresAt:new Date(Date.now()+600000).toISOString()}})).json();
 const first=await (await page.request.post(`/api/simulations/${scenario.id}/reliable-execute`,{data:{interfaceId:definition.interfaceId,requestId,payload:{userId:'M001',userName:'Manual retry demo'}}})).json();expect(first.status).toBe('UNKNOWN');
 await page.getByRole('button',{name:'Retry Center',exact:true}).filter({visible:true}).click();
 const row=page.getByRole('row').filter({hasText:requestId});await expect(row).toContainText('DEAD_LETTER');
 await row.getByRole('button',{name:'한 번 더 예약'}).click();
 await expect.poll(async()=> (await (await page.request.get(`/api/requests/${first.id}`)).json()).status,{timeout:15000}).toBe('SUCCESS');
 await page.getByRole('button',{name:'재처리 목록 새로고침'}).click();await expect(row).toContainText('SUCCESS');await expect(row).toContainText('2 / 2');await expect(row.getByRole('button',{name:'한 번 더 예약'})).toBeDisabled();
 await expect(row).toContainText(first.traceId);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 await page.screenshot({path:testInfo.outputPath('retry-center.png'),fullPage:true});
});
