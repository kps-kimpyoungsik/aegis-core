from pathlib import Path
from typing import Optional
import json, sqlite3
from aegis_rc5.runtime import Task, TaskState, CanonicalEvent
SCHEMA_VERSION=1
DDL="""
CREATE TABLE IF NOT EXISTS schema_version(version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS task(task_id TEXT PRIMARY KEY,intent TEXT NOT NULL,state TEXT NOT NULL,plan_json TEXT NOT NULL,output_json TEXT,error TEXT,rollback_ref TEXT,created_at TEXT NOT NULL,updated_at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS event(event_id TEXT PRIMARY KEY,task_id TEXT NOT NULL,event_type TEXT NOT NULL,at TEXT NOT NULL,payload_json TEXT NOT NULL);
CREATE INDEX IF NOT EXISTS ix_event_task_at ON event(task_id,at);
CREATE TABLE IF NOT EXISTS episode(episode_id INTEGER PRIMARY KEY AUTOINCREMENT,task_id TEXT NOT NULL,intent TEXT NOT NULL,state TEXT NOT NULL,output_json TEXT,error TEXT,at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS idempotency(idempotency_key TEXT PRIMARY KEY,payload_hash TEXT NOT NULL,task_id TEXT NOT NULL,status TEXT NOT NULL);
"""
class SQLiteDataPlane:
    def __init__(self,path:str): self.path=path; self._init()
    def _conn(self):
        c=sqlite3.connect(self.path); c.row_factory=sqlite3.Row; return c
    def _init(self):
        Path(self.path).parent.mkdir(parents=True,exist_ok=True)
        with self._conn() as c:
            c.executescript(DDL); c.execute('INSERT OR IGNORE INTO schema_version(version) VALUES (?)',(SCHEMA_VERSION,))
    def save_task(self,t:Task):
        with self._conn() as c:
            c.execute("INSERT INTO task(task_id,intent,state,plan_json,output_json,error,rollback_ref,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(task_id) DO UPDATE SET intent=excluded.intent,state=excluded.state,plan_json=excluded.plan_json,output_json=excluded.output_json,error=excluded.error,rollback_ref=excluded.rollback_ref,updated_at=excluded.updated_at",(t.task_id,t.intent,t.state.value,json.dumps(t.plan),json.dumps(t.output) if t.output is not None else None,t.error,t.rollback_ref,t.created_at,t.updated_at))
    def get_task(self,task_id:str)->Optional[Task]:
        with self._conn() as c: r=c.execute('SELECT * FROM task WHERE task_id=?',(task_id,)).fetchone()
        if not r:return None
        return Task(task_id=r['task_id'],intent=r['intent'],state=TaskState(r['state']),plan=json.loads(r['plan_json']),output=json.loads(r['output_json']) if r['output_json'] else None,error=r['error'],rollback_ref=r['rollback_ref'],created_at=r['created_at'],updated_at=r['updated_at'])
    def list_tasks(self):
        with self._conn() as c: ids=[r[0] for r in c.execute('SELECT task_id FROM task ORDER BY created_at').fetchall()]
        return [self.get_task(x) for x in ids]
    def append_event(self,e:CanonicalEvent):
        with self._conn() as c:c.execute('INSERT OR IGNORE INTO event(event_id,task_id,event_type,at,payload_json) VALUES(?,?,?,?,?)',(e.event_id,e.task_id,e.event_type,e.at,json.dumps(e.payload)))
    def events_for_task(self,task_id):
        with self._conn() as c: rs=c.execute('SELECT * FROM event WHERE task_id=? ORDER BY at,event_id',(task_id,)).fetchall()
        return [CanonicalEvent(r['event_id'],r['task_id'],r['event_type'],r['at'],json.loads(r['payload_json'])) for r in rs]
    def save_episode(self,task,at):
        with self._conn() as c:c.execute('INSERT INTO episode(task_id,intent,state,output_json,error,at) VALUES(?,?,?,?,?,?)',(task.task_id,task.intent,task.state.value,json.dumps(task.output) if task.output is not None else None,task.error,at))
    def episodes(self):
        with self._conn() as c:rs=c.execute('SELECT task_id,intent,state,output_json,error,at FROM episode ORDER BY episode_id').fetchall()
        return [{'task_id':r['task_id'],'intent':r['intent'],'state':r['state'],'output':json.loads(r['output_json']) if r['output_json'] else None,'error':r['error'],'at':r['at']} for r in rs]
    def claim_idempotency(self,key,payload_hash,task_id):
        if not key:return 'DENY_MISSING_KEY'
        with self._conn() as c:
            r=c.execute('SELECT payload_hash,status FROM idempotency WHERE idempotency_key=?',(key,)).fetchone()
            if not r:
                c.execute('INSERT INTO idempotency(idempotency_key,payload_hash,task_id,status) VALUES(?,?,?,?)',(key,payload_hash,task_id,'IN_PROGRESS')); return 'START'
            if r['payload_hash']!=payload_hash:return 'DENY_PAYLOAD_CONFLICT'
            return 'REUSE' if r['status']=='COMPLETED' else 'IN_PROGRESS'
    def complete_idempotency(self,key):
        with self._conn() as c:c.execute("UPDATE idempotency SET status='COMPLETED' WHERE idempotency_key=?",(key,))
    def schema_version(self):
        with self._conn() as c:r=c.execute('SELECT MAX(version) FROM schema_version').fetchone()
        return int(r[0] or 0)
