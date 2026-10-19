import { useEffect, useState } from 'react';
import { api, message, pretty, type Definition, type MappingRule, type ValidationRule, type SystemInfo } from './api';

interface Props { interfaces: Definition[]; systems: SystemInfo[]; refresh: () => Promise<void> }
export function InterfaceManagement({ interfaces, systems, refresh }: Props) {
  const [draft, setDraft] = useState<Definition|null>(null);
  const [isNew, setNew] = useState(false);
  const [mapping, setMapping] = useState('[]'), [validation, setValidation] = useState('[]');
  const [query, setQuery] = useState(''), [notice, setNotice] = useState(''), [error, setError] = useState('');
  const [busy, setBusy] = useState(false), [versions, setVersions] = useState<Definition[]>([]);
  const [sourceId, setSourceId] = useState(''), [sourceName, setSourceName] = useState('');
  function choose(value: Definition, fresh=false) {
    setDraft(value);setNew(fresh);setMapping(pretty(value.mappings));setValidation(pretty(value.validations));setVersions([]);setError('');setNotice('');
  }
  useEffect(() => { if (!draft && interfaces.length) choose(interfaces[0]); }, [interfaces, draft]);
  function create() {
    choose({ interfaceId:'',interfaceName:'',description:'',sourceSystem:'SYSTEM_A',targetSystem:'SYSTEM_B',method:'POST',path:'/api/receive',enabled:true,timeoutMs:3000,retryMaxAttempts:1,version:0,mappings:[],validations:[] }, true);
  }
  function field<K extends keyof Definition>(key: K, value: Definition[K]) { if (draft) setDraft({...draft,[key]:value}); }
  async function action(work: () => Promise<void>) {
    setBusy(true);setError('');setNotice('');
    try { await work(); } catch(e) {setError(message(e));} finally {setBusy(false);}
  }
  async function save() {
    if (!draft) return;
    await action(async () => {
      const mappings = JSON.parse(mapping) as MappingRule[], validations = JSON.parse(validation) as ValidationRule[];
      if (!Array.isArray(mappings) || !Array.isArray(validations)) throw new Error('규칙은 JSON 배열로 입력하세요.');
      const saved = await api<Definition>(isNew?'/api/interfaces':`/api/interfaces/${encodeURIComponent(draft.interfaceId)}`, {
        method:isNew?'POST':'PUT',body:pretty({...draft,mappings,validations}),
      });
      choose(saved);await refresh();setNotice(`설정 v${saved.version} 저장 완료`);
    });
  }
  return <>
    <section className="intro"><div className="eyebrow">INTERFACE REGISTRY</div><h1>인터페이스와 전문 규칙을 관리하세요.</h1><p>변경할 때마다 새 설정 버전을 저장합니다. 진행 중인 요청은 접수 당시 버전을 사용합니다.</p></section>
    <div className="management-grid"><section className="panel"><div className="row"><h2>인터페이스 목록</h2><button onClick={create} disabled={busy}>신규 등록</button></div>
      <label htmlFor="interface-search">Interface ID / 이름 검색</label><input id="interface-search" value={query} onChange={e=>setQuery(e.target.value)}/>
      <div className="interface-list">{interfaces.filter(i=>(i.interfaceId+' '+i.interfaceName).toLowerCase().includes(query.toLowerCase())).map(i=><button key={i.interfaceId} className={draft?.interfaceId===i.interfaceId?'selected':''} onClick={()=>choose(i)} disabled={busy}><b>{i.interfaceId}</b><span>{i.interfaceName}</span><small>v{i.version} · {i.enabled?'활성':'비활성'}</small></button>)}</div>
      <h2 className="spaced">출발 시스템 등록</h2><label htmlFor="source-id">시스템 ID</label><input id="source-id" value={sourceId} onChange={e=>setSourceId(e.target.value)}/><label htmlFor="source-name">시스템 이름</label><input id="source-name" value={sourceName} onChange={e=>setSourceName(e.target.value)}/>
      <button className="spaced" disabled={busy || !sourceId || !sourceName} onClick={()=>action(async()=>{await api('/api/systems',{method:'POST',body:pretty({systemId:sourceId,systemName:sourceName,role:'SOURCE'})});await refresh();setSourceId('');setSourceName('');setNotice('출발 시스템 등록 완료');})}>출발 시스템 저장</button>
      <p>대상은 사전 등록된 SYSTEM_B로 제한합니다. 외부 URL 입력은 제공하지 않습니다.</p>
    </section><section className="panel">
      {error && <p className="error" role="alert">{error}</p>}{notice && <p className="notice" role="status">{notice}</p>}
      {draft?<fieldset disabled={busy}><h2>{isNew?'신규 인터페이스':'인터페이스 설정'} <span className="badge">v{draft.version}</span></h2>
        <div className="form-grid"><div><label htmlFor="definition-id">Interface ID</label><input id="definition-id" value={draft.interfaceId} disabled={!isNew} onChange={e=>field('interfaceId',e.target.value)} maxLength={80}/></div><div><label htmlFor="definition-name">인터페이스 이름</label><input id="definition-name" value={draft.interfaceName} onChange={e=>field('interfaceName',e.target.value)} maxLength={120}/></div>
          <div><label htmlFor="definition-source">출발 시스템</label><select id="definition-source" value={draft.sourceSystem} onChange={e=>field('sourceSystem',e.target.value)}>{systems.filter(s=>s.role==='SOURCE').map(s=><option key={s.systemId}>{s.systemId}</option>)}</select></div>
          <div><label htmlFor="definition-target">대상 시스템</label><select id="definition-target" value={draft.targetSystem} onChange={e=>field('targetSystem',e.target.value)}>{systems.filter(s=>s.role==='TARGET').map(s=><option key={s.systemId}>{s.systemId}</option>)}</select></div>
          <div><label htmlFor="definition-timeout">읽기 Timeout (ms)</label><input id="definition-timeout" type="number" min={100} max={10000} value={draft.timeoutMs} onChange={e=>field('timeoutMs',Number(e.target.value))}/></div><div><label htmlFor="definition-retry">최대 시도 수 (정책 저장만)</label><input id="definition-retry" type="number" min={1} max={5} value={draft.retryMaxAttempts} onChange={e=>field('retryMaxAttempts',Number(e.target.value))}/></div></div>
        <label htmlFor="definition-description">설명</label><input id="definition-description" value={draft.description??''} onChange={e=>field('description',e.target.value)} maxLength={1000}/>
        <label className="checkbox"><input type="checkbox" checked={draft.enabled} onChange={e=>field('enabled',e.target.checked)}/> 활성화 · POST /api/receive</label>
        <label htmlFor="mapping-rules">필드 매핑 규칙 (JSON)</label><textarea id="mapping-rules" value={mapping} spellCheck={false} onChange={e=>setMapping(e.target.value)}/>
        <p>점 경로 · COPY / STRING / NUMBER / BOOLEAN / DATE · 기본값 · KEEP / OMIT / ERROR / DEFAULT. 빈 배열은 원본 복사, 규칙이 있으면 지정한 대상 필드만 생성합니다.</p>
        <label htmlFor="validation-rules">Source / Target 검증 규칙 (JSON)</label><textarea id="validation-rules" value={validation} spellCheck={false} onChange={e=>setValidation(e.target.value)}/>
        <p>stage: SOURCE / TARGET. 날짜 연도 패턴은 uuuu를 사용합니다. 비밀번호·토큰을 규칙 기본값에 저장하지 마세요.</p>
        <button className="primary" onClick={save}>설정 저장</button>
        {!isNew && <div className="toolbar spaced"><button onClick={()=>action(async()=>{const saved=await api<Definition>(`/api/interfaces/${encodeURIComponent(draft.interfaceId)}/status`,{method:'PATCH',body:pretty({enabled:!draft.enabled,version:draft.version})});choose(saved);await refresh();setNotice('활성 상태 변경 완료');})}>{draft.enabled?'비활성화':'활성화'}</button>
          <button onClick={()=>action(async()=>{setVersions(await api<Definition[]>(`/api/interfaces/${encodeURIComponent(draft.interfaceId)}/versions`));})}>설정 버전 조회</button>
          <button onClick={()=>action(async()=>{const result=await api<{deleted:boolean}>(`/api/interfaces/${encodeURIComponent(draft.interfaceId)}`,{method:'DELETE'});if(!result.deleted) choose(await api<Definition>(`/api/interfaces/${encodeURIComponent(draft.interfaceId)}`));await refresh();if(result.deleted){setDraft(null);setNew(false);}setNotice(result.deleted?'삭제 완료':'이력을 보존하기 위해 비활성화했습니다.');})}>삭제 / 이력 있으면 비활성화</button></div>}
        {versions.length>0 && <details open><summary>저장된 설정 버전 (최근 50개)</summary>{versions.map(v=><details key={v.version}><summary>v{v.version} · {v.interfaceName} · {v.enabled?'활성':'비활성'}</summary><pre>{pretty(v)}</pre></details>)}</details>}
      </fieldset>:<p>신규 등록을 눌러 인터페이스를 만드세요.</p>}
    </section></div>
  </>;
}
