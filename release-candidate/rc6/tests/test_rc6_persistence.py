import tempfile,unittest
from pathlib import Path
from aegis_rc6.runtime import PersistentRuntime
from aegis_rc5.runtime import TaskState
class T(unittest.TestCase):
 def mk(self):
  d=tempfile.TemporaryDirectory(); return d,str(Path(d.name)/'a.db')
 def test_task_survives_restart(self):
  d,p=self.mk(); r=PersistentRuntime(p); t=r.create_task('persist'); r.plan(t.task_id,'echo'); self.assertEqual(PersistentRuntime(p).tasks.get(t.task_id).state,TaskState.PLANNED); d.cleanup()
 def test_event_survives_restart(self):
  d,p=self.mk(); r=PersistentRuntime(p); t=r.create_task('e'); r.plan(t.task_id,'echo'); r.execute_idempotent(t.task_id,'echo',{'text':'ok'},'k1'); self.assertIn('TASK_COMPLETED',[x['event_type'] for x in PersistentRuntime(p).timeline(t.task_id)]); d.cleanup()
 def test_episode_survives_restart(self):
  d,p=self.mk(); r=PersistentRuntime(p); t=r.create_task('e'); r.plan(t.task_id,'echo'); r.execute_idempotent(t.task_id,'echo',{'text':'ok'},'k1'); self.assertEqual(len(PersistentRuntime(p).memory.episodes),1); d.cleanup()
 def test_reuse_no_new_tool_call(self):
  d,p=self.mk(); r=PersistentRuntime(p); t=r.create_task('e'); r.plan(t.task_id,'echo'); r.execute_idempotent(t.task_id,'echo',{'text':'ok'},'k1'); b=sum(x['event_type']=='TOOL_CALLED' for x in r.timeline(t.task_id)); r2=PersistentRuntime(p); r2.execute_idempotent(t.task_id,'echo',{'text':'ok'},'k1'); a=sum(x['event_type']=='TOOL_CALLED' for x in r2.timeline(t.task_id)); self.assertEqual(a,b); d.cleanup()
 def test_payload_conflict(self):
  d,p=self.mk(); r=PersistentRuntime(p); t=r.create_task('e'); r.plan(t.task_id,'echo'); r.execute_idempotent(t.task_id,'echo',{'text':'a'},'k1'); self.assertEqual(PersistentRuntime(p).execute_idempotent(t.task_id,'echo',{'text':'b'},'k1').error,'DENY_PAYLOAD_CONFLICT'); d.cleanup()
 def test_missing_key(self):
  d,p=self.mk(); r=PersistentRuntime(p); t=r.create_task('e'); r.plan(t.task_id,'echo'); self.assertEqual(r.execute_idempotent(t.task_id,'echo',{},'').error,'DENY_MISSING_KEY'); d.cleanup()
 def test_schema(self):
  d,p=self.mk(); self.assertEqual(PersistentRuntime(p).db.schema_version(),1); d.cleanup()
 def test_planned_resumable(self):
  d,p=self.mk(); r=PersistentRuntime(p); t=r.create_task('e'); r.plan(t.task_id,'echo'); self.assertIn(t.task_id,PersistentRuntime(p).recover_after_restart()['resumable_planned']); d.cleanup()
 def test_running_fails_closed(self):
  d,p=self.mk(); r=PersistentRuntime(p); t=r.create_task('e'); r.plan(t.task_id,'echo'); t.state=TaskState.RUNNING; r.tasks.save(t); r2=PersistentRuntime(p); self.assertIn(t.task_id,r2.recover_after_restart()['failed_closed']); d.cleanup()
if __name__=='__main__':unittest.main()
