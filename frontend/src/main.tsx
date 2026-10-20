import { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { parsePayload } from './json';
import { readResponse } from './http';
import { api, message, pretty, type Definition, type SystemInfo } from './api';
import { InterfaceManagement } from './InterfaceManagement';
import { History } from './History';
import './style.css';
import { RetryCenter } from './RetryCenter';
import { SimulationLab } from './SimulationLab';
import { Dashboard, TraceExplorer } from './Monitoring';

const sample=pretty({userId:'USER001',userName:'홍길동',departmentCode:'DEV01',email:'demo@example.com'});
type Tab='sender'|'interfaces'|'history'|'trace'|'dashboard'|'simulation'|'retry';
const tabs: {id:Tab;label:string}[]=[{id:'retry',label:'Retry Center'},{id:'simulation',label:'Simulation Lab'},{id:'dashboard',label:'대시보드'},{id:'trace',label:'Trace Explorer'},{id:'sender',label:'전문 전송'},{id:'interfaces',label:'인터페이스 관리'},{id:'history',label:'전송 이력'}];

function Sender({interfaces}:{interfaces:Definition[]}) {
  const [interfaceId,setInterfaceId]=useState('IF-USER-001');
  const [payload,setPayload]=useState(sample),[requestId,setRequestId]=useState(()=> 'REQ-'+crypto.randomUUID());
  const [result,setResult]=useState<unknown>(null),[preview,setPreview]=useState<unknown>(null);
  const [error,setError]=useState(''),[busy,setBusy]=useState(false),[previewing,setPreviewing]=useState(false);
  const definition=interfaces.find(i=>i.interfaceId===interfaceId);
  useEffect(()=>{if(interfaces.length && !interfaces.some(i=>i.interfaceId===interfaceId))setInterfaceId(interfaces[0].interfaceId);},[interfaces,interfaceId]);
  async function send() {
    setError('');setResult(null);setBusy(true);
    try {
      const body=pretty({interfaceId,requestId,payload:parsePayload(payload)});
      if(new TextEncoder().encode(body).length>65536) throw new Error('전문 크기는 64KB 이하로 입력하세요.');
      const response=await fetch('/api/requests',{method:'POST',headers:{'Content-Type':'application/json'},body,signal:AbortSignal.timeout(15000)});
      const data=await readResponse(response);setResult(data);
      if(!response.ok)setError(`요청 오류 · HTTP ${response.status}`);
    } catch(e){setError(message(e));} finally {setBusy(false);}
  }
  async function prepare() {
    setPreviewing(true);setError('');setPreview(null);
    try {setPreview(await api(`/api/interfaces/${encodeURIComponent(interfaceId)}/preview`,{method:'POST',body:pretty({payload:parsePayload(payload)})}));}
    catch(e){setError(message(e));}finally{setPreviewing(false);}
  }
  return <>
    <section className="intro"><div className="eyebrow">INTEGRATION PLAYGROUND</div><h1>시스템 사이의 흐름을 확인하세요.</h1><p>저장된 규칙으로 JSON 전문을 검증·변환하고 실제 HTTP 응답과 전송 이력을 확인합니다.</p></section>
    <div className="flow"><span>시스템 A · 콘솔</span><b>→</b><span>Integration :8080</span><b>→</b><span>Target :8081</span></div>
    <div className="grid"><section className="panel"><h2>전문 작성 <span className="badge">POST</span></h2>
      <label htmlFor="interface">인터페이스</label><select id="interface" value={interfaceId} disabled={busy} onChange={e=>{setInterfaceId(e.target.value);setPreview(null);setResult(null);}}>{interfaces.map(i=><option key={i.interfaceId} value={i.interfaceId}>{i.interfaceId} · {i.interfaceName} · v{i.version}{i.enabled?'':' (비활성)'}</option>)}</select>
      <label htmlFor="request">Request ID</label><input id="request" value={requestId} onChange={e=>setRequestId(e.target.value)} maxLength={80}/>
      <div className="row"><label htmlFor="payload">요청 JSON</label><button onClick={()=>{setPayload(sample);setPreview(null);}}>샘플 불러오기</button></div>
      <textarea id="payload" spellCheck={false} value={payload} onChange={e=>{setPayload(e.target.value);setPreview(null);}}/>
      <button className="spaced" disabled={busy||previewing||!definition} onClick={prepare}>{previewing?'검증 중…':'변환 미리보기'}</button>
      <button className="primary" disabled={busy||!definition?.enabled||!/^[A-Za-z0-9_-]{1,80}$/.test(requestId)} onClick={send}>{busy?'전송 중…':'전문 전송 →'}</button>
      {definition&&!definition.enabled&&<p className="error">비활성화된 인터페이스는 전송할 수 없습니다.</p>}
    </section><section className="panel"><h2>처리 결과</h2><div role="status" aria-live="polite">
      {error&&<p className="error">{error}</p>}{result?<pre>{pretty(result)}</pre>:<div className="empty"><b>⇄</b><p>{busy?'대상 시스템 응답을 기다리는 중입니다.':'전문을 전송하면 결과가 표시됩니다.'}</p><small>Trace ID · Attempt ID · HTTP 상태 · 검증 오류</small></div>}
    </div></section></div>
    {preview!==null&&<section className="panel spaced"><h2>변환 미리보기 · HTTP 전송 및 이력 저장 없음</h2><pre data-testid="mapping-preview">{pretty(preview)}</pre></section>}
  </>;
}
function App() {
  const [tab,setTab]=useState<Tab>('sender'),[dark,setDark]=useState(true);
  const [interfaces,setInterfaces]=useState<Definition[]>([]),[systems,setSystems]=useState<SystemInfo[]>([]),[error,setError]=useState('');
  async function refresh(){try{const [definitions,registered]=await Promise.all([api<Definition[]>('/api/interfaces'),api<SystemInfo[]>('/api/systems')]);setInterfaces(definitions);setSystems(registered);setError('');}catch(e){setError(message(e));throw e;}}
  useEffect(()=>{void refresh().catch(()=>{});},[]);
  function navigate(next:Tab){setTab(next);if(next==='sender')void refresh().catch(()=>{});}
  const navigation=tabs.map(t=><button key={t.id} aria-current={tab===t.id?'page':undefined} className={tab===t.id?'selected':''} onClick={()=>navigate(t.id)}>{t.label}</button>);
  return <div className={dark?'app dark':'app'}><a className="skip-link" href="#workspace">본문으로 이동</a><aside><div className="brand">⇄ InterfaceFlow<span>LAB / DEVELOPMENT</span></div><nav aria-label="주 메뉴">{navigation}<p>Phase 6 · 관측성과 사용성</p></nav><small>LOCAL ENVIRONMENT<br/>가상 데이터 전용</small></aside><main id="workspace" tabIndex={-1}>
    <header><span>워크스페이스 / {tabs.find(t=>t.id===tab)?.label}</span><button onClick={()=>setDark(!dark)}>{dark?'라이트 모드':'다크 모드'}</button></header>
    <nav className="mobile-nav" aria-label="모바일 메뉴">{navigation}</nav>
    {error&&<p className="error" role="alert">설정 조회 실패: {error} <button onClick={()=>void refresh().catch(()=>{})}>다시 조회</button></p>}
    {tab==='retry'?<RetryCenter/>:tab==='simulation'?<SimulationLab interfaces={interfaces}/>:tab==='sender'?<Sender interfaces={interfaces}/>:tab==='interfaces'?<InterfaceManagement interfaces={interfaces} systems={systems} refresh={refresh}/>:tab==='trace'?<TraceExplorer/>:tab==='dashboard'?<Dashboard/>:<History/>}
    <footer>Phase 6: 커서 이력·전문 비교·시간별 차트·접근성. 민감 필드는 마스킹됩니다. 일반 전송은 같은 요청 키의 중복 실행을 방지합니다.</footer>
  </main></div>;
}
createRoot(document.getElementById('root')!).render(<App/>);
