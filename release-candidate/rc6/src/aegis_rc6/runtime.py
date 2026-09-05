from hashlib import sha256
import json
from aegis_rc5.runtime import RuntimeEngine,DeterministicToolAdapter,TaskState,now_iso
from aegis_rc6.persistence import SQLiteDataPlane
class PersistentTaskStore:
    def __init__(self,db):self.db=db
    def save(self,t):self.db.save_task(t)
    def get(self,i):return self.db.get_task(i)
    def list(self):return self.db.list_tasks()
class PersistentEventStore:
    def __init__(self,db):self.db=db
    def append(self,e):self.db.append_event(e)
    def for_task(self,i):return self.db.events_for_task(i)
class PersistentMemory:
    def __init__(self,db):
        self.db=db; self.knowledge=[{'id':'K-FAIL-CLOSED','text':'Unverified or unauthorized side effects must not execute.','quality':1.0},{'id':'K-ROLLBACK','text':'Important changes require a rollback point.','quality':1.0}]
    @property
    def episodes(self):return self.db.episodes()
    def retrieve(self,intent):
        q=intent.lower(); return [x for x in self.knowledge if any(t in x['text'].lower() for t in q.split())][:3]
    def remember_episode(self,task):self.db.save_episode(task,now_iso())
class PersistentRuntime(RuntimeEngine):
    def __init__(self,db_path):
        self.db=SQLiteDataPlane(db_path)
        super().__init__(tasks=PersistentTaskStore(self.db),events=PersistentEventStore(self.db),memory=PersistentMemory(self.db),tools=DeterministicToolAdapter())
    def execute_idempotent(self,task_id,tool_id,payload,idempotency_key,approved=False):
        t=self._require(task_id); h=sha256(json.dumps(payload,sort_keys=True,separators=(',',':')).encode()).hexdigest(); s=self.db.claim_idempotency(idempotency_key,h,task_id); self._event(t,'IDEMPOTENCY_DECISION',{'decision':s,'key':idempotency_key})
        if s in ('DENY_MISSING_KEY','DENY_PAYLOAD_CONFLICT','IN_PROGRESS'):
            t.error=s; self.tasks.save(t); return t
        if s=='REUSE':t.error=None; return t
        out=super().execute(task_id,tool_id,payload,approved)
        if out.state==TaskState.COMPLETED:self.db.complete_idempotency(idempotency_key)
        return out
    def recover_after_restart(self):
        resumed=[]; failed=[]
        for t in self.tasks.list():
            if t.state in (TaskState.RUNNING,TaskState.VALIDATING):
                t.state=TaskState.FAILED; t.error='RECOVERY_REQUIRES_RECONCILIATION'; t.updated_at=now_iso(); self.tasks.save(t); self._event(t,'FAILURE',{'reason':t.error}); failed.append(t.task_id)
            elif t.state==TaskState.PLANNED:resumed.append(t.task_id)
        return {'resumable_planned':resumed,'failed_closed':failed}
