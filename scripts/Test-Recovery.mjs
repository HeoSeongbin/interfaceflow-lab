// Destructive only to the two disposable JVMs created by this script. Existing services are untouched.
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import fs from 'node:fs';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const require=createRequire(path.join(root,'frontend/package.json'));
const {Client}=require('pg');
if(!process.env.DB_URL||!process.env.REPLAY_KEY)throw new Error('Set disposable DB_URL, DB_USER, DB_PASSWORD and stable REPLAY_KEY.');
const java=process.env.RECOVERY_JAVA||'java';
const integrationPort=Number(process.env.RECOVERY_INTEGRATION_PORT||18080),targetPort=Number(process.env.RECOVERY_TARGET_PORT||18081);
const api=`http://127.0.0.1:${integrationPort}`,target=`http://127.0.0.1:${targetPort}`;
const databaseURL=new URL(process.env.DB_URL.replace(/^jdbc:/,''));
databaseURL.username=process.env.DB_USER||'interfaceflow';databaseURL.password=process.env.DB_PASSWORD||'';
const db=new Client({connectionString:databaseURL.toString()});
const children=[];
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function until(action,timeout=30000){const end=Date.now()+timeout;while(Date.now()<end){if(await action())return;await pause(250);}throw new Error('Recovery verification deadline exceeded');}
async function free(port){const net=await import('node:net');await new Promise((resolve,reject)=>{const server=net.createServer();server.once('error',reject);server.listen(port,'127.0.0.1',()=>server.close(resolve));});}
function start(module,port){
 const log=fs.openSync(path.join(root,`.tools/recovery-${module}.log`),'a');
 const child=spawn(java,['-jar',path.join(root,`backend/${module}/target/${module}-0.1.0-SNAPSHOT.jar`),`--server.port=${port}`,'--server.address=127.0.0.1',`--lab.target-url=${target}`,'--lab.retry-worker=true'],{cwd:root,env:process.env,windowsHide:true,stdio:['ignore',log,log]});
 fs.closeSync(log);children.push(child);return child;
}
async function healthy(url,child){await until(async()=>{if(child.exitCode!==null)throw new Error('Test JVM exited early');try{return (await (await fetch(url+'/actuator/health')).json()).status==='UP';}catch{return false;}},45000);}
async function kill(child){if(child.exitCode!==null||child.signalCode!==null)return;const exit=once(child,'exit');child.kill('SIGKILL');await exit;}
async function json(url,body){const response=await fetch(api+url,{method:body?'POST':'GET',headers:{'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined});assert.ok(response.ok,`HTTP ${response.status} at ${url}`);return response.json();}
try{
 await free(integrationPort);await free(targetPort);await db.connect();
 const receiver=start('target-simulator',targetPort);await healthy(target,receiver);
 let sender=start('integration-server',integrationPort);await healthy(api,sender);
 const sample=await json('/api/interfaces/IF-USER-001');
 const definition=await json('/api/interfaces',{...sample,interfaceId:'IF-RECOVERY-'+randomUUID(),interfaceName:'Recovery verification',version:0,timeoutMs:3000,retryMaxAttempts:3});
 const requestId='RECOVERY-'+randomUUID();
 const scenario=await json('/api/simulations',{type:'TIMEOUT',interfaceId:definition.interfaceId,requestId,delayMs:5000,failureCount:0,expiresAt:new Date(Date.now()+600000).toISOString()});
 const sending=json(`/api/simulations/${scenario.id}/reliable-execute`,{interfaceId:definition.interfaceId,requestId,payload:{userId:'REC001',userName:'Recovery demo'}}).catch(()=>null);
 await until(async()=>Number((await db.query('SELECT count(*) FROM target.if_business_result WHERE scope=$1',[scenario.id])).rows[0].count)===1,10000);
 const original=(await db.query('SELECT r.id,r.trace_id,r.attempt_id,r.status FROM integration.if_request r JOIN integration.if_delivery d ON d.request_key=r.id WHERE d.scope=$1',[scenario.id])).rows[0];
 assert.equal(original.status,'SENDING');
 await kill(sender);await sending;
 sender=start('integration-server',integrationPort);await healthy(api,sender);
 console.log('Restarted the sender after Target committed; awaiting the real 60-second lease expiry.');
 await until(async()=>{const result=(await db.query('SELECT status FROM integration.if_request WHERE id=$1',[original.id])).rows[0];return result?.status==='SUCCESS';},85000);
 const final=(await db.query('SELECT trace_id,attempt_id FROM integration.if_request WHERE id=$1',[original.id])).rows[0];
 assert.equal(final.trace_id,original.trace_id);assert.notEqual(final.attempt_id,original.attempt_id);
 const attempts=(await db.query('SELECT status FROM integration.if_attempt WHERE request_key=$1 ORDER BY attempt_number',[original.id])).rows;
 assert.deepEqual(attempts.map(a=>a.status),['UNKNOWN','SUCCESS']);
 assert.equal(Number((await db.query('SELECT count(*) FROM target.if_business_result WHERE scope=$1',[scenario.id])).rows[0].count),1);
 assert.equal(Number((await db.query('SELECT count(*) FROM target.if_target_receipt WHERE simulation_id=$1 AND replayed',[scenario.id])).rows[0].count),1);
 console.log('PASS: killed/restarted JVM, persistent lease recovery, same Request/Trace, new Attempt, one Target business result.');
}finally{
 for(const child of children)await kill(child);
 await db.end();
}
