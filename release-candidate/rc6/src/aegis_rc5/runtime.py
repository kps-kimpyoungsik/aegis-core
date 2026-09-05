from __future__ import annotations
from dataclasses import dataclass, field, asdict
from enum import Enum
from typing import Dict, List, Optional, Protocol
from uuid import uuid4
from datetime import datetime, timezone
import hashlib, json

def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()

class TaskState(str, Enum):
    NEW="NEW"; PLANNED="PLANNED"; READY="READY"; RUNNING="RUNNING"
    VALIDATING="VALIDATING"; COMPLETED="COMPLETED"; FAILED="FAILED"
    ROLLING_BACK="ROLLING_BACK"; ROLLED_BACK="ROLLED_BACK"

@dataclass
class CanonicalEvent:
    event_id:str
    task_id:str
    event_type:str
    at:str
    payload:dict

@dataclass
class Task:
    task_id:str
    intent:str
    state:TaskState=TaskState.NEW
    plan:list[str]=field(default_factory=list)
    output:Optional[dict]=None
    error:Optional[str]=None
    rollback_ref:Optional[str]=None
    created_at:str=field(default_factory=now_iso)
    updated_at:str=field(default_factory=now_iso)

@dataclass(frozen=True)
class ToolContract:
    tool_id:str
    side_effect:bool
    approval_required:bool

class EventStorePort(Protocol):
    def append(self, event:CanonicalEvent)->None: ...
    def for_task(self, task_id:str)->List[CanonicalEvent]: ...

class TaskStorePort(Protocol):
    def save(self, task:Task)->None: ...
    def get(self, task_id:str)->Optional[Task]: ...
    def list(self)->List[Task]: ...

class MemoryPort(Protocol):
    def retrieve(self, intent:str)->List[dict]: ...
    def remember_episode(self, task:Task)->None: ...

class ToolPort(Protocol):
    def execute(self, tool_id:str, payload:dict)->dict: ...

class InMemoryTaskStore:
    def __init__(self): self.data:Dict[str,Task]={}
    def save(self,task): self.data[task.task_id]=task
    def get(self,task_id): return self.data.get(task_id)
    def list(self): return list(self.data.values())

class InMemoryEventStore:
    def __init__(self): self.events:List[CanonicalEvent]=[]
    def append(self,event): self.events.append(event)
    def for_task(self,task_id): return [e for e in self.events if e.task_id==task_id]

class InMemoryMemory:
    def __init__(self):
        self.episodes:List[dict]=[]
        self.knowledge=[
            {"id":"K-FAIL-CLOSED","text":"Unverified or unauthorized side effects must not execute.","quality":1.0},
            {"id":"K-ROLLBACK","text":"Important changes require a rollback point.","quality":1.0},
        ]
    def retrieve(self,intent):
        q=intent.lower()
        return [x for x in self.knowledge if any(t in x["text"].lower() for t in q.split())][:3]
    def remember_episode(self,task):
        self.episodes.append({"task_id":task.task_id,"intent":task.intent,"state":task.state.value,
                              "output":task.output,"error":task.error,"at":now_iso()})

class DeterministicToolAdapter:
    CONTRACTS={
        "echo":ToolContract("echo",False,False),
        "sha256":ToolContract("sha256",False,False),
        "mutate-demo":ToolContract("mutate-demo",True,True),
    }
    def contract(self,tool_id): return self.CONTRACTS.get(tool_id)
    def execute(self,tool_id,payload):
        if tool_id=="echo": return {"text":str(payload.get("text",""))}
        if tool_id=="sha256":
            raw=str(payload.get("text","")).encode()
            return {"sha256":hashlib.sha256(raw).hexdigest()}
        if tool_id=="mutate-demo": return {"changed":True,"value":payload.get("value")}
        raise KeyError(tool_id)

class HarnessGate:
    def authorize(self, contract:Optional[ToolContract], approved:bool)->str:
        if contract is None: return "DENY_UNKNOWN_TOOL"
        if contract.approval_required and not approved: return "REQUIRE_APPROVAL"
        return "ALLOW"

class Validator:
    def validate(self, tool_id:str, output:dict)->tuple[bool,str]:
        if tool_id=="sha256":
            v=output.get("sha256","")
            return (len(v)==64 and all(c in "0123456789abcdef" for c in v),"sha256-format")
        if tool_id=="echo":
            return ("text" in output,"echo-shape")
        if tool_id=="mutate-demo":
            return (output.get("changed") is True,"mutation-result")
        return (False,"unknown-tool")

class RuntimeEngine:
    def __init__(self, tasks=None, events=None, memory=None, tools=None):
        self.tasks=tasks or InMemoryTaskStore()
        self.events=events or InMemoryEventStore()
        self.memory=memory or InMemoryMemory()
        self.tools=tools or DeterministicToolAdapter()
        self.harness=HarnessGate()
        self.validator=Validator()

    def _event(self,task,event_type,payload=None):
        self.events.append(CanonicalEvent(str(uuid4()),task.task_id,event_type,now_iso(),payload or {}))

    def create_task(self,intent:str)->Task:
        if not intent.strip(): raise ValueError("intent required")
        t=Task(task_id=str(uuid4()),intent=intent.strip())
        self.tasks.save(t); self._event(t,"TASK_STARTED",{"intent":t.intent})
        return t

    def plan(self,task_id:str,tool_id:str)->Task:
        t=self._require(task_id)
        if t.state != TaskState.NEW: raise ValueError("invalid state for plan")
        memories=self.memory.retrieve(t.intent)
        t.plan=["RETRIEVE_MEMORY","HARNESS_GATE",f"TOOL:{tool_id}","VALIDATE","REMEMBER"]
        t.state=TaskState.PLANNED; t.updated_at=now_iso(); self.tasks.save(t)
        self._event(t,"MEMORY_RETRIEVED",{"count":len(memories),"items":memories})
        self._event(t,"PLAN_CREATED",{"plan":t.plan,"tool_id":tool_id})
        return t

    def execute(self,task_id:str,tool_id:str,payload:dict,approved:bool=False)->Task:
        t=self._require(task_id)
        if t.state != TaskState.PLANNED: raise ValueError("task must be PLANNED")
        decision=self.harness.authorize(self.tools.contract(tool_id),approved)
        self._event(t,"AUTHORITY_DECISION",{"decision":decision,"tool_id":tool_id})
        if decision=="DENY_UNKNOWN_TOOL":
            t.state=TaskState.FAILED; t.error=decision; self.tasks.save(t); self._event(t,"FAILURE",{"reason":decision}); return t
        if decision=="REQUIRE_APPROVAL":
            t.error=decision; self.tasks.save(t); return t

        contract=self.tools.contract(tool_id)
        t.rollback_ref=f"rollback:{t.task_id}:before:{tool_id}" if contract and contract.side_effect else None
        t.state=TaskState.RUNNING; self.tasks.save(t); self._event(t,"TOOL_CALLED",{"tool_id":tool_id})
        try:
            out=self.tools.execute(tool_id,payload)
            self._event(t,"TOOL_RESULT",{"tool_id":tool_id,"output":out})
            t.state=TaskState.VALIDATING; self.tasks.save(t)
            ok,rule=self.validator.validate(tool_id,out)
            self._event(t,"VALIDATION",{"passed":ok,"rule":rule})
            if not ok:
                if t.rollback_ref:
                    t.state=TaskState.ROLLING_BACK; self._event(t,"ROLLBACK",{"rollback_ref":t.rollback_ref})
                    t.state=TaskState.ROLLED_BACK
                else:
                    t.state=TaskState.FAILED
                t.error="VALIDATION_FAILED"
            else:
                t.output=out; t.state=TaskState.COMPLETED; t.error=None
                self._event(t,"TASK_COMPLETED",{"output":out})
            t.updated_at=now_iso(); self.tasks.save(t); self.memory.remember_episode(t)
            return t
        except Exception as exc:
            t.state=TaskState.FAILED; t.error=type(exc).__name__; self.tasks.save(t)
            self._event(t,"FAILURE",{"reason":t.error}); self.memory.remember_episode(t)
            return t

    def timeline(self,task_id:str)->list[dict]:
        self._require(task_id)
        return [asdict(e) for e in self.events.for_task(task_id)]

    def status(self)->dict:
        tasks=self.tasks.list()
        return {"tasks":len(tasks),
                "completed":sum(1 for t in tasks if t.state==TaskState.COMPLETED),
                "failed":sum(1 for t in tasks if t.state in (TaskState.FAILED,TaskState.ROLLED_BACK))}

    def _require(self,task_id):
        t=self.tasks.get(task_id)
        if not t: raise KeyError(task_id)
        return t
