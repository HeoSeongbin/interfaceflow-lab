// Owns only a unique disposable Compose project; never operates the developer stack.
import {spawn} from 'node:child_process';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import {randomUUID,randomBytes} from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import net from 'node:net';
import assert from 'node:assert/strict';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const require=createRequire(path.join(root,'frontend/package.json'));
const {Client}=require('pg');
const runtime=path.join(root,'.tools');fs.mkdirSync(runtime,{recursive:true});
const project='iflab-test-'+randomUUID().slice(0,8);
const envFile=path.join(runtime,project+'.env');fs.writeFileSync(envFile,'# Disposable test stack. Secrets passed only through process environment.\n');
const logFile=path.join(runtime,'phase7-compose.log');fs.writeFileSync(logFile,'');
const resultFile=path.join(runtime,'phase7-compose-result.json');fs.rmSync(resultFile,{force:true});
const env={...process.env,DB_USER:'interfaceflow',DB_PASSWORD:randomBytes(24).toString('hex'),REPLAY_KEY:randomBytes(32).toString('base64'),SIMULATIONS_ENABLED:'true',RETRY_CONCURRENCY:'2',CONNECTION_FAILURE_URL:'http://127.0.0.1:1'};
const compose=['compose','--env-file',envFile,'-p',project,'-f',path.join(root,'docker-compose.yml')];
let owned=false,db,report;
function command(executable,args,{capture=false,cwd=root}={}){
 return new Promise((resolve,reject)=>{
  const output=fs.openSync(logFile,'a');
  const child=spawn(executable,args,{cwd,env,windowsHide:true,stdio:['ignore',capture?'pipe':output,output]});fs.closeSync(output);
  let result='';if(capture)child.stdout.on('data',chunk=>{result+=chunk;});
  child.once('error',reject);child.once('exit',code=>code===0?resolve(result):reject(new Error(`${executable} ${args[0]} failed (${code}); inspect .tools/phase7-compose.log`)));
 });
}
async function port(){return new Promise((resolve,reject)=>{const server=net.createServer();server.once('error',reject);server.listen(0,'127.0.0.1',()=>{const value=server.address().port;server.close(error=>error?reject(error):resolve(String(value)));});});}
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function until(action,timeout=120000){const end=Date.now()+timeout;while(Date.now()<end){if(await action())return;await pause(300);}throw new Error('Compose verification deadline exceeded');}
async function health(url){await until(async()=>{try{return (await (await fetch(url+'/actuator/health',{signal:AbortSignal.timeout(2000)})).json()).status==='UP';}catch{return false;}});}
try{
 await command('docker',['info']);
 // Preflight ensures cleanup can never adopt an already-existing project.
 assert.equal((await command('docker',['ps','-a','--filter',`label=com.docker.compose.project=${project}`,'-q'],{capture:true})).trim(),'');
 const assigned=new Set();for(const key of ['DB_PORT','TARGET_PORT','INTEGRATION_PORT','FRONTEND_PORT']){do{env[key]=await port();}while(assigned.has(env[key]));assigned.add(env[key]);}
 const api=`http://127.0.0.1:${env.INTEGRATION_PORT}`,target=`http://127.0.0.1:${env.TARGET_PORT}`;
 console.log(`Compose ${project}: building isolated stack on dynamically allocated loopback ports.`);
 await command('docker',[...compose,'config','--quiet']);
 owned=true;await command('docker',[...compose,'up','--build','-d']);
 await health(api);await health(target);
 const web=`http://127.0.0.1:${env.FRONTEND_PORT}`;
 await until(async()=>{try{return (await fetch(web,{signal:AbortSignal.timeout(2000)})).ok;}catch{return false;}});
 env.E2E_BASE_URL=web;env.E2E_DB_URL=`postgresql://127.0.0.1:${env.DB_PORT}/interfaceflow`;
 db=new Client({host:'127.0.0.1',port:Number(env.DB_PORT),database:'interfaceflow',user:env.DB_USER,password:env.DB_PASSWORD});await db.connect();
 // Verify independent migration histories on the fresh volume, including redundant-index removal.
 for(const [schema,table,count] of [['integration','flyway_integration_history',7],['target','flyway_target_history',3]]){
  const applied=await db.query(`SELECT count(*)::int AS count,bool_and(success) AS success FROM ${schema}.${table} WHERE version IS NOT NULL`);
  assert.equal(applied.rows[0].count,count);assert.equal(applied.rows[0].success,true);
 }
 const indexes=(await db.query("SELECT to_regclass('integration.ix_request_created') AS old,to_regclass('integration.ix_request_created_key') AS current")).rows[0];
 assert.equal(indexes.old,null);assert.ok(indexes.current);
 console.log('Fresh migrations and both HTTP health endpoints passed; running desktop/mobile browser regression.');
 const frontend=path.join(root,'frontend');
 if(process.platform==='win32')await command(process.env.ComSpec||'cmd.exe',['/d','/s','/c','npm run test:e2e'],{cwd:frontend});
 else await command('npm',['run','test:e2e'],{cwd:frontend});
 async function json(url,body){const response=await fetch(api+url,{method:body?'POST':'GET',headers:{'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(15000)});assert.ok(response.ok,`HTTP ${response.status} at ${url}`);return response.json();}
 const sample=await json('/api/interfaces/IF-USER-001');
 const definition=await json('/api/interfaces',{...sample,interfaceId:'IF-COMPOSE-'+randomUUID(),interfaceName:'Container recovery test',version:0,timeoutMs:3000,retryMaxAttempts:3});
 const requestId='COMPOSE-'+randomUUID();
 const scenario=await json('/api/simulations',{type:'TIMEOUT',interfaceId:definition.interfaceId,requestId,delayMs:5000,failureCount:0,expiresAt:new Date(Date.now()+600000).toISOString()});
 const sending=json(`/api/simulations/${scenario.id}/reliable-execute`,{interfaceId:definition.interfaceId,requestId,payload:{userId:'COM001',userName:'Virtual recovery'}}).catch(()=>null);
 await until(async()=>Number((await db.query('SELECT count(*) FROM target.if_business_result WHERE scope=$1',[scenario.id])).rows[0].count)===1,10000);
 const original=(await db.query('SELECT r.id,r.trace_id,r.attempt_id,r.status FROM integration.if_request r JOIN integration.if_delivery d ON d.request_key=r.id WHERE d.scope=$1',[scenario.id])).rows[0];assert.equal(original.status,'SENDING');
 await command('docker',[...compose,'kill','-s','SIGKILL','integration']);await sending;
 await command('docker',[...compose,'start','integration']);await health(api);
 console.log('Integration container restarted after Target commit; waiting for the real 60-second lease expiry.');
 await until(async()=>(await db.query('SELECT status FROM integration.if_request WHERE id=$1',[original.id])).rows[0]?.status==='SUCCESS',85000);
 const final=(await db.query('SELECT trace_id,attempt_id,request_id FROM integration.if_request WHERE id=$1',[original.id])).rows[0];
 assert.equal(final.trace_id,original.trace_id);assert.equal(final.request_id,requestId);assert.notEqual(final.attempt_id,original.attempt_id);
 assert.deepEqual((await db.query('SELECT status FROM integration.if_attempt WHERE request_key=$1 ORDER BY attempt_number',[original.id])).rows.map(a=>a.status),['UNKNOWN','SUCCESS']);
 assert.equal(Number((await db.query('SELECT count(*) FROM target.if_business_result WHERE scope=$1',[scenario.id])).rows[0].count),1);
 assert.equal(Number((await db.query('SELECT count(*) FROM target.if_target_receipt WHERE simulation_id=$1 AND replayed',[scenario.id])).rows[0].count),1);
 report={verifiedAt:new Date().toISOString(),project,ports:Object.fromEntries([...assigned].map((p,i)=>[['db','target','integration','frontend'][i],Number(p)])),databaseVersion:(await db.query('SHOW server_version')).rows[0].server_version,freshMigrations:true,browserPassed:true,recoveryPassed:true};
}finally{
 try{if(db)await db.end();}finally{
  try{if(owned){try{await command('docker',[...compose,'logs','--no-color']);}finally{await command('docker',[...compose,'down','--volumes','--remove-orphans']);}}}
  finally{fs.rmSync(envFile,{force:true});}
 }
}
fs.writeFileSync(resultFile,JSON.stringify({...report,cleanupPassed:true},null,2));
console.log('PASS: fresh Compose build/migrations, browser regression, container crash recovery, same Request/Trace, new Attempt, one Target business result; disposable stack removed.');
