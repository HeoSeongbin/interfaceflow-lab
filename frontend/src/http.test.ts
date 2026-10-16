import { expect, test } from 'vitest';
import { readResponse } from './http';

test('JSON response is preserved for result and trace display', async () => {
  await expect(readResponse(Response.json({ status: 'SUCCESS', traceId: 'trace' })))
    .resolves.toEqual({ status: 'SUCCESS', traceId: 'trace' });
});

test('JSON API error remains available to the caller', async () => {
  await expect(readResponse(Response.json({ code: 'VALIDATION_ERROR' }, { status: 400 })))
    .resolves.toEqual({ code: 'VALIDATION_ERROR' });
});

test('HTML gateway failure reports status without rendering response body', async () => {
  await expect(readResponse(new Response('<html>private upstream details</html>', { status: 502 })))
    .rejects.toThrow('요청 오류 · HTTP 502');
});

test('malformed JSON does not expose the upstream body', async () => {
  await expect(readResponse(new Response('private upstream details', {
    headers: { 'content-type': 'application/json' },
  }))).rejects.toThrow('서버 응답 JSON을 해석할 수 없습니다. HTTP 200');
});
