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
  } finally { await database.end(); }
  await expect(page.getByRole('status')).toContainText(result.attemptId);
  await page.screenshot({ path: testInfo.outputPath('console-success.png'), fullPage: true });
});

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
