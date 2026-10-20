import { useEffect, useState } from 'react';
import { api,message,pretty } from './api';
import { StatusBadge } from './RequestDiagnostics';
interface Delivery {id:string;requestId:string;traceId:string;interfaceId:string;status:string;state:string;attempts:number;maxAttempts:number;nextAt:string;errorCode:string|null;canRetry:boolean}
export function RetryCenter(){
 const [rows,setRows]=useState<Delivery[]>([]),[error,setError]=useState(''),[busy,setBusy]=useState(false),[detail,setDetail]=useState<unknown>(null);
 async function load(){try{setRows(await api<Delivery[]>('/api/retries'));setError('');}catch(e){setError(message(e));}}
 useEffect(()=>{void load();const timer=setInterval(()=>void load(),5000);return()=>clearInterval(timer);},[]);
 async function retry(id:string){setBusy(true);try{await api(`/api/requests/${id}/retry`,{method:'POST'});await load();}catch(e){setError(message(e));}finally{setBusy(false);}}
 async function show(id:string){try{setDetail(await api(`/api/requests/${id}`));}catch(e){setError(message(e));}}
 return <><section className="intro"><h1>Retry Center</h1><p>기존 요청·Trace·설정 버전으로 재처리하고 Attempt를 추가합니다. UNKNOWN은 확정 실패가 아닙니다. 자동 정책 소진 후 수동으로 한 번 더 예약할 수 있으며 총 10회까지 허용합니다.</p></section><section className="panel"><button disabled={busy} onClick={()=>void load()}>재처리 목록 새로고침</button><p>최근 100개 · 5초마다 갱신 · 요청 상태는 마지막 처리 결과,큐 상태는 실행/재시도 대기·소진을 나타냅니다. 과거 기록과 수동 장애 실험은 이 목록에 없습니다.</p>{error&&<p role="alert" className="error">{error}</p>}<div className="table-scroll" style={{maxHeight:600,overflow:"auto"}} role="region" aria-label="재처리 목록 표" tabIndex={0}><table><thead><tr><th>요청 / Trace</th><th>결과 / 큐 상태</th><th>Attempt / 한도</th><th>오류 / 다음 시각</th><th>재처리</th></tr></thead><tbody>{rows.map(r=><tr key={r.id}><td><button onClick={()=>show(r.id)}>{r.requestId}</button><small>{r.traceId}</small></td><td><StatusBadge value={r.status}/><small>{r.state}</small></td><td>{r.attempts} / {r.maxAttempts}</td><td>{r.errorCode}<small>{r.state==='WAIT'?new Date(r.nextAt).toLocaleString('ko-KR'):'—'}</small></td><td><button disabled={busy||!r.canRetry} onClick={()=>void retry(r.id)}>한 번 더 예약</button></td></tr>)}</tbody></table></div></section>{detail!==null&&<section className="panel spaced"><h2>요청 상세 · 마스킹된 진단 정보</h2><pre>{pretty(detail)}</pre></section>}</>;
}
