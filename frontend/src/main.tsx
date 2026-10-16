import {useState} from 'react';
import {createRoot} from 'react-dom/client';
import {parsePayload} from './json';
import {readResponse} from './http';
import './style.css';
const sample=JSON.stringify({userId:'USER001',userName:'홍길동',departmentCode:'DEV01',email:'demo@example.com'},null,2);
function App(){
 const [payload,setPayload]=useState(sample),[requestId,setRequestId]=useState('REQ-'+crypto.randomUUID()),[result,setResult]=useState<unknown>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false),[dark,setDark]=useState(true);
 async function send(){setError('');setResult(null);setBusy(true);try{
  const body=JSON.stringify({interfaceId:'IF-USER-001',requestId,payload:parsePayload(payload)});
  if(new TextEncoder().encode(body).length>65536) throw new Error('전문 크기는 64KB 이하로 입력하세요.');
  const response=await fetch('/api/requests',{method:'POST',headers:{'Content-Type':'application/json'},body,signal:AbortSignal.timeout(15000)});
  const data:unknown=await readResponse(response);setResult(data);if(!response.ok) setError(`요청 오류 · HTTP ${response.status}`);
 }catch(e){setError(e instanceof Error?e.message:'전송 오류');}finally{setBusy(false);}}
 return <div className={dark?'app dark':'app'}><aside><div className="brand">⇄ InterfaceFlow<span>LAB / DEVELOPMENT</span></div><nav><strong>01　전문 전송</strong><p>Phase 1 · 기본 통신 검증</p></nav><small>LOCAL ENVIRONMENT<br/>가상 데이터 전용</small></aside><main><header><span>워크스페이스 / 전문 전송</span><button onClick={()=>setDark(!dark)}>{dark?'라이트 모드':'다크 모드'}</button></header><section className="intro"><div className="eyebrow">INTEGRATION PLAYGROUND</div><h1>시스템 사이의 흐름을 확인하세요.</h1><p>JSON 전문을 전송하고 두 서버의 실제 HTTP 응답과 Trace ID를 확인합니다.</p></section><div className="flow"><span>시스템 A · 콘솔</span><b>→</b><span>Integration :8080</span><b>→</b><span>Target :8081</span></div><div className="grid"><section className="panel"><h2>전문 작성 <label>POST</label></h2><label htmlFor="interface">인터페이스</label><select id="interface"><option>IF-USER-001 · 사용자 정보 연동</option></select><label htmlFor="request">Request ID</label><input id="request" value={requestId} onChange={e=>setRequestId(e.target.value)} maxLength={80}/><div className="row"><label htmlFor="payload">요청 JSON</label><button onClick={()=>setPayload(sample)}>샘플 불러오기</button></div><textarea id="payload" spellCheck={false} value={payload} onChange={e=>setPayload(e.target.value)}/><button className="primary" disabled={busy || !/^[A-Za-z0-9_-]{1,80}$/.test(requestId)} onClick={send}>{busy?'전송 중…':'전문 전송 →'}</button></section><section className="panel"><h2>처리 결과</h2><div role="status" aria-live="polite">{error&&<p className="error">{error}</p>}{result?<pre>{JSON.stringify(result,null,2)}</pre>:<div className="empty"><b>⇄</b><p>{busy?'대상 시스템 응답을 기다리는 중입니다.':'전문을 전송하면 결과가 표시됩니다.'}</p><small>Trace ID · Attempt ID · HTTP 상태</small></div>}</div></section></div><footer>현재 제공: 고정 인터페이스 전송, 수신 기록, Trace 헤더 전파. 매핑·장애 시뮬레이션·재시도는 후속 Phase에서 구현합니다.</footer></main></div>
}createRoot(document.getElementById('root')!).render(<App/>);
