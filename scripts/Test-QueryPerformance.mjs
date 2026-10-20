import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import fs from 'node:fs';
import {randomUUID} from 'node:crypto';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const require=createRequire(path.join(root,'frontend/package.json'));const {Client}=require('pg');
if(process.env.ALLOW_PERF_TEST!=='true'||!process.env.DB_URL)throw new Error('Set ALLOW_PERF_TEST=true and a disposable DB_URL. Inserts 100,000 virtual records and rolls them back.');
const url=new URL(process.env.DB_URL.replace(/^jdbc:/,''));url.username=process.env.DB_USER||'interfaceflow';url.password=process.env.DB_PASSWORD||'';
const db=new Client({connectionString:url.toString()});const seed=randomUUID(),count=100000;
function summarize(plan){const indexes=new Set();function visit(node){if(node['Index Name'])indexes.add(node['Index Name']);for(const child of node.Plans||[])visit(child);}visit(plan.Plan);return {executionMs:plan['Execution Time'],sharedHitBlocks:plan.Plan['Shared Hit Blocks'],indexes:[...indexes],actualRows:plan.Plan['Actual Rows']};}
async function explain(sql,args=[]){return (await db.query('EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON) '+sql,args)).rows[0]['QUERY PLAN'][0];}
try{
 await db.connect();await db.query('BEGIN');await db.query("SET LOCAL statement_timeout='90s'");
 await db.query(`INSERT INTO integration.if_request(id,request_id,interface_id,trace_id,status,created_at)
 SELECT md5($1||g)::uuid,'PERF-'||g,'IF-PERF-'||$1,md5('trace-'||$1||g),'SUCCESS',TIMESTAMPTZ '2000-01-01T00:00:00Z'+((g-1)/10)*interval '1 second'
 FROM generate_series(1,$2::int) g`,[seed,count]);
 await db.query(`INSERT INTO integration.if_delivery(request_key,interface_id,source_system,request_id,scope,payload_hash,replay_cipher,state,max_attempts)
 SELECT id,interface_id,'PERF',request_id,'perf',repeat('0',64),'virtual-terminal-benchmark','DONE',1 FROM integration.if_request WHERE interface_id=$1`,['IF-PERF-'+seed]);
 await db.query(`UPDATE integration.if_delivery SET state='WAIT',next_at=now()-interval '1 second' WHERE request_key IN (SELECT id FROM integration.if_request WHERE interface_id=$1 ORDER BY id LIMIT 20)`,['IF-PERF-'+seed]);
 await db.query(`UPDATE integration.if_delivery SET state='LEASED',lease_until=now()+interval '1 hour' WHERE request_key IN (SELECT id FROM integration.if_request WHERE interface_id=$1 ORDER BY id OFFSET 20 LIMIT 20000)`,['IF-PERF-'+seed]);
 await db.query(`UPDATE integration.if_delivery SET lease_until=now()-interval '1 second' WHERE request_key IN (SELECT request_key FROM integration.if_delivery WHERE interface_id=$1 AND state='LEASED' ORDER BY request_key LIMIT 20)`,['IF-PERF-'+seed]);
 await db.query('ANALYZE integration.if_request');await db.query('ANALYZE integration.if_delivery');
 const range=`created_at>=TIMESTAMPTZ '2000-01-01T00:00:00Z' AND created_at<TIMESTAMPTZ '2001-01-01T00:00:00Z'`;
 const offsetSql=`SELECT id,created_at FROM integration.if_request WHERE ${range} ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET 90000`;
 const anchor=(await db.query(`SELECT created_at::text,id FROM integration.if_request WHERE ${range} ORDER BY created_at DESC,id DESC OFFSET 89999 LIMIT 1`)).rows[0];
 const cursorSql=`SELECT id,created_at FROM integration.if_request WHERE ${range} AND (created_at,id)<($1::timestamptz,$2::uuid) ORDER BY created_at DESC,id DESC LIMIT 20`;
 const oldDue=`SELECT request_key FROM integration.if_delivery WHERE (state='WAIT' AND next_at<=clock_timestamp()) OR (state='LEASED' AND lease_until<=clock_timestamp()) ORDER BY next_at LIMIT 20`;
 const newDue=`SELECT request_key FROM (SELECT request_key,next_at AS due_at FROM integration.if_delivery WHERE state='WAIT' AND next_at<=statement_timestamp() UNION ALL SELECT request_key,lease_until AS due_at FROM integration.if_delivery WHERE state='LEASED' AND lease_until<=statement_timestamp()) due ORDER BY due_at,request_key LIMIT 20`;
 await explain(offsetSql);await explain(cursorSql,[anchor.created_at,anchor.id]);
 const plans={offset:await explain(offsetSql),cursor:await explain(cursorSql,[anchor.created_at,anchor.id]),previousDue:await explain(oldDue),indexedDue:await explain(newDue)};
 const report={rows:count,offset:90000,distribution:'10 requests per timestamp; 20 WAIT, 20 expired leases, 19,980 live leases, remaining terminal.',note:'Warm cache, one local disposable PostgreSQL run; all queries use final V6/V7 indexes. Not a pre-migration benchmark, production throughput or latency SLA.',checks:Object.fromEntries(Object.entries(plans).map(([key,value])=>[key,summarize(value)]))};
 fs.writeFileSync(path.join(root,'.tools/phase6-performance.json'),JSON.stringify({report,plans},null,2));console.log(JSON.stringify(report,null,2));
}finally{
 try{await db.query('ROLLBACK');await db.query('ANALYZE integration.if_request');await db.query('ANALYZE integration.if_delivery');}finally{await db.end();}
}
