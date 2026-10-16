export async function readResponse(response: Response): Promise<unknown> {
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('application/json') && !contentType.includes('+json')) {
    if (!response.ok) throw new Error(`요청 오류 · HTTP ${response.status}. 서버 연결 상태를 확인하세요.`);
    throw new Error('서버 응답이 JSON 형식이 아닙니다.');
  }
  try {
    return await response.json();
  } catch {
    throw new Error(`서버 응답 JSON을 해석할 수 없습니다. HTTP ${response.status}`);
  }
}
