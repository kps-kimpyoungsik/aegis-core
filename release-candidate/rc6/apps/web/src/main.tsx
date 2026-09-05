import React from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

type Check={id:string;state:'PASS'|'FAIL'|'NOT_EXECUTED'|'BLOCKED_BY_ENVIRONMENT'};
type Task={task_id:string;intent:string;state:string;output?:Record<string,unknown>|null;error?:string|null};
type Readiness={version:string;release:string;decision:string;production_open_allowed:boolean;blockers:string[];checks:Check[];runtime:{tasks:number;completed:number;failed:number}};

function App(){
  const [r,setR]=React.useState<Readiness|null>(null);
  const [tasks,setTasks]=React.useState<Task[]>([]);
  const [intent,setIntent]=React.useState('echo customer-open vertical slice');
  const [error,setError]=React.useState('');

  const refresh=React.useCallback(async()=>{
    try{
      setError('');
      const [a,b]=await Promise.all([fetch('/api/v1/open-readiness',{cache:'no-store'}),fetch('/api/v1/tasks',{cache:'no-store'})]);
      if(!a.ok||!b.ok) throw new Error('API unavailable');
      setR(await a.json() as Readiness); setTasks((await b.json() as {items:Task[]}).items);
    }catch(e){setError(e instanceof Error?e.message:'unknown error')}
  },[]);
  React.useEffect(()=>{void refresh()},[refresh]);

  async function demo(){
    try{
      const c=await fetch('/api/v1/tasks',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({intent})});
      const t=await c.json() as Task;
      await fetch(`/api/v1/tasks/${t.task_id}/plan`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({tool_id:'echo'})});
      await fetch(`/api/v1/tasks/${t.task_id}/execute`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({tool_id:'echo',payload:{text:intent}})});
      await refresh();
    }catch(e){setError(e instanceof Error?e.message:'run failed')}
  }

  return <main className="shell">
    <header className="hero"><div><p className="eyebrow">AEGIS-CLI RC5</p><h1>Runtime Operations</h1></div><button onClick={()=>void refresh()}>Refresh</button></header>
    {error&&<section className="card danger">{error}</section>}
    <section className="summary">
      <article className="card"><span>Release</span><strong>{r?.release??'Loading'}</strong><small>{r?.version}</small></article>
      <article className="card warn"><span>Customer open</span><strong>{r?.decision??'Loading'}</strong><small>{r?.production_open_allowed?'Allowed':'Blocked until GA evidence passes'}</small></article>
      <article className="card"><span>Runtime</span><strong>{r?.runtime.completed??0}/{r?.runtime.tasks??0}</strong><small>completed tasks</small></article>
    </section>
    <section className="card">
      <h2>Vertical-slice task</h2>
      <div className="runner"><input value={intent} onChange={e=>setIntent(e.target.value)} aria-label="Task intent"/><button onClick={()=>void demo()}>Run verified demo</button></div>
    </section>
    <section className="card"><h2>Tasks</h2>
      <div className="checks">{tasks.map(t=><div className="check" key={t.task_id}><div><strong>{t.intent}</strong><small className="taskid">{t.task_id}</small></div><span className="pill">{t.state}</span></div>)}</div>
    </section>
    <section className="card"><h2>GA blockers</h2>{r?.blockers.length?<ul>{r.blockers.map(x=><li key={x}>{x}</li>)}</ul>:<p>No blockers.</p>}</section>
  </main>
}
createRoot(document.getElementById('root')!).render(<React.StrictMode><App/></React.StrictMode>);
