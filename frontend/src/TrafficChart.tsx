import { useId } from 'react';
import { hours, type Bucket } from './diagnostics';
const series=[{key:'SUCCESS',label:'성공',color:'#229e7b'},{key:'FAILED',label:'명확한 실패',color:'#dc5268'},{key:'UNKNOWN',label:'처리 불확실',color:'#d99b30'},{key:'PROGRESS',label:'진행 중',color:'#598bdc'}] as const;
export function TrafficChart({buckets,from,to}:{buckets:Bucket[];from:string;to:string}){
 const rows=hours(buckets,from,to),id=useId(),width=960,height=260,left=44,top=18,plot=190;
 const max=Math.max(1,...rows.map(r=>series.reduce((n,s)=>n+r[s.key],0))),step=(width-left-18)/Math.max(1,rows.length);
 return <section className="traffic-chart" aria-label="시간별 처리량 차트"><div className="legend">{series.map(s=><span key={s.key}><i style={{background:s.color}}/>{s.label}</span>)}</div>
 <p className="table-hint">차트를 좌우로 스크롤하여 시간별 값을 확인하세요.</p><div className="chart-scroll" role="region" aria-label="시간별 차트 스크롤" tabIndex={0}><svg viewBox={`0 0 ${width} ${height}`} role="img" aria-labelledby={`${id}-title`} aria-describedby={`${id}-desc`}><title id={`${id}-title`}>시간별 처리량</title><desc id={`${id}-desc`}>UTC 시간별 요청 수를 성공·명확한 실패·처리 불확실·진행 중으로 구분합니다. 값이 없는 시간은 0입니다. 아래 통계 표에서도 같은 데이터를 확인할 수 있습니다.</desc>
 {[0,.5,1].map(f=><g key={f}><line x1={left} x2={width-18} y1={top+plot*(1-f)} y2={top+plot*(1-f)} stroke="currentColor" opacity=".15"/><text x={left-7} y={top+plot*(1-f)+4} textAnchor="end">{Number((max*f).toFixed(1))}</text></g>)}
 {rows.map((row,index)=>{let total=0;return <g key={row.hour}>{series.map(s=>{const count=row[s.key],barHeight=count/max*plot,y=top+plot-(total+count)/max*plot;total+=count;return count>0?<rect key={s.key} x={left+index*step+step*.1} y={y} width={Math.max(.2,step*.8)} height={barHeight} fill={s.color}><title>{row.hour} · {s.label} {count}건</title></rect>:null;})}{index%Math.max(1,Math.ceil(rows.length/5))===0&&<text x={left+index*step} y={top+plot+24}>{row.hour.slice(5,10)} {row.hour.slice(11,16)}</text>}</g>;})}
 <text x={width-18} y={height-7} textAnchor="end">UTC · 요청 수</text></svg></div>
 </section>;
}
