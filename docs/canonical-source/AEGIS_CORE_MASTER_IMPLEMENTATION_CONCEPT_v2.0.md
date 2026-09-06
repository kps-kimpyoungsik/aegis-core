# AEGIS-CORE MASTER IMPLEMENTATION CONCEPT
## Autonomous Adaptive Architecture Constitution — Implementation Baseline v2.0

> **Status:** Canonical Implementation Entry Baseline  
> **Purpose:** 지금까지 축적된 AEGIS-CORE 설계 헌법·자율 설계·기억·지식·자산·학습·검증·오케스트레이션·UI/UX 개념을 실제 구현 설계로 전환하기 위한 단일 기준 문서  
> **Rule:** 구현 상세 기술은 교체 가능하지만, 이 문서의 Canonical Meaning과 Trust Boundary는 유지한다.

---

# 0. 설계 선언

AEGIS-CORE는 특정 LLM, 특정 프레임워크, 특정 데이터베이스, 특정 클라우드, 특정 UI, 특정 Agent SDK에 의존하는 시스템이 아니다.

AEGIS-CORE의 핵심은 다음 순환 구조다.

```text
OBSERVE
  ↓
UNDERSTAND
  ↓
MEASURE / RETRIEVE
  ↓
PLAN
  ↓
EXECUTE
  ↓
VERIFY
  ↓
COMPARE
  ↓
REMEMBER
  ↓
PROMOTE / REJECT / ROLLBACK
  ↓
ADAPT
  ↓
NEXT ITERATION
```

목표는 “항상 같은 답을 내는 시스템”이 아니라 **상황을 실측하고, 필요한 질문을 하고, 기존 경험과 외부 지식을 최소한으로 검색하고, 검증 가능한 방법으로 실행하며, 결과와 실패를 자산화하여 다음 작업을 더 적은 추론과 더 높은 품질로 수행하는 시스템**이다.

---

# 1. 최상위 헌법

## 1.1 Independence
구현체는 교체 가능해야 한다.

```text
Model
Runtime
Database
Vector Engine
Graph Engine
Message Broker
UI Framework
Cloud
Tool Provider
```

어느 하나도 AEGIS의 정체성이 되어서는 안 된다.

## 1.2 Context Before Design
설계 전에 상황을 이해한다.

```text
Intent
Goal
Environment
Constraints
Existing Assets
Risk
Authority
Quality Target
Time / Cost Budget
Unknowns
```

정보가 부족하면 추측으로 고정하지 않고 필요한 질문·실측·탐색을 수행한다.

## 1.3 Evidence Before Belief
주장, 판단, 변경에는 가능한 범위에서 근거를 연결한다.

```text
Claim → Evidence → Source → Version → Confidence
```

## 1.4 Compare Before Promote
새 설계가 “새롭다”는 이유로 채택하지 않는다.

```text
Baseline
vs
Candidate
```

를 동일 조건에서 비교한다.

## 1.5 Fail Closed
개선이 입증되지 않으면 Canonical 상태를 변경하지 않는다.

## 1.6 Reversible by Default
중요 변경은 Version, Diff, Audit, Recovery, Rollback 경로를 가진다.

## 1.7 Learn, Then Automate
LLM이 반복적으로 수행하는 결정적 패턴은 Skill → Policy → Library → Service/System으로 승격하여 LLM 추론량을 줄인다.

## 1.8 Memory Is Selective
모든 기억을 Context에 넣지 않는다. 필요한 순간에 필요한 포인트만 검색한다.

## 1.9 Trust Boundary Is Not Self-Modifiable
자율 개선 대상과 헌법·권한·검증·감사 영역을 분리한다.

## 1.10 Human Authority Remains Explicit
고위험·비가역·정책 충돌·불확실성이 높은 작업은 승인 또는 Escalation 경로를 가진다.

---

# 2. Canonical System Model

```text
┌──────────────────────────────────────────────┐
│              AEGIS CONSTITUTION              │
│ Policy / Authority / Trust / Quality         │
└──────────────────────┬───────────────────────┘
                       ↓
┌──────────────────────────────────────────────┐
│              PORTABLE BRAIN                  │
│ Memory / Knowledge / Skill / Experience      │
│ Provenance / Evaluation / Protocol           │
└──────────────────────┬───────────────────────┘
                       ↓
┌──────────────────────────────────────────────┐
│          ORCHESTRATION & DECISION            │
│ Router / Planner / Delegation / Budget       │
└──────────────────────┬───────────────────────┘
                       ↓
┌──────────────────────────────────────────────┐
│        RUNTIME-SPECIFIC HARNESS              │
│ Model / Tool / Context / Retry / Adapter     │
└──────────────────────┬───────────────────────┘
                       ↓
┌──────────────────────────────────────────────┐
│          TASK-SPECIFIC SCAFFOLD              │
│ Workflow / DAG / Loop / Validation Plan      │
└──────────────────────┬───────────────────────┘
                       ↓
┌──────────────────────────────────────────────┐
│                EXECUTION                     │
│ Agent / Program / Service / Tool / Human     │
└──────────────────────┬───────────────────────┘
                       ↓
┌──────────────────────────────────────────────┐
│      EVENT / EVIDENCE / QUALITY LAYER        │
│ Trace / Metrics / Diff / Test / Audit        │
└──────────────────────┬───────────────────────┘
                       ↓
               MEMORY & EVOLUTION
```

---

# 3. 핵심 Canonical Object

구현 기술과 무관하게 다음 객체는 공통 의미를 유지한다.

```yaml
canonical_objects:
  task:
  context_pack:
  plan:
  scaffold:
  harness:
  agent:
  capability:
  skill:
  tool_contract:
  delegation_contract:
  memory:
  knowledge:
  evidence:
  experience:
  asset:
  evaluation:
  decision:
  event:
  failure_signature:
  proposal:
  version:
  provenance:
  rollback_point:
```

모든 주요 객체는 가능하면 다음 공통 필드를 가진다.

```yaml
common:
  id:
  type:
  scope:
  version:
  status:
  owner:
  created_at:
  updated_at:
  provenance:
  quality:
  authority:
  tags:
  relations:
```

---

# 4. Task Lifecycle

```text
INTAKE
 ↓
CONTEXT DIAGNOSIS
 ↓
PRE-MEASUREMENT
 ↓
MEMORY / ASSET RETRIEVAL
 ↓
RESEARCH IF NEEDED
 ↓
PLAN
 ↓
RISK / AUTHORITY GATE
 ↓
EXECUTION
 ↓
VALIDATION
 ↓
BASELINE COMPARISON
 ↓
ACCEPT / CORRECT / RETRY / ROLLBACK
 ↓
MEMORY & ASSET UPDATE
 ↓
POST-MEASUREMENT
 ↓
CLOSE
```

상태 예:

```text
NEW
DIAGNOSING
WAITING_INPUT
PLANNED
READY
RUNNING
VALIDATING
CORRECTING
COMPLETED
FAILED
PAUSED
CANCELLED
ROLLING_BACK
ROLLED_BACK
```

---

# 5. Context Diagnosis & Questioning

Agent는 질문을 많이 하는 것이 아니라 **설계 결과를 바꿀 핵심 Unknown만 질문**한다.

```yaml
diagnosis:
  goal:
  current_state:
  desired_state:
  constraints:
  known_assets:
  unknowns:
  assumptions:
  risk:
  authority:
  acceptance_criteria:
```

질문 우선순위:

```text
Safety / Authority
Architecture-changing Unknown
Irreversible Decision
Quality Acceptance
Environment Fact
Preference
```

실측 가능한 것은 질문보다 실측을 우선할 수 있다.

---

# 6. Pre/Post Measurement

모든 중요한 변경은 전후 상태를 비교 가능하게 만든다.

```yaml
measurement:
  baseline:
  target:
  metrics:
  environment:
  timestamp:
  evidence:
```

평가 축:

```text
Correctness
Reliability
Security
Performance
Latency
Throughput
Resource
Cost
Maintainability
Reusability
UX
Recovery
Compliance
```

---

# 7. Portable Brain

```text
Harness = replaceable
Brain   = persistent
```

Portable Brain은 다음을 소유한다.

```text
Memory
Knowledge
Skills
Protocols
Policies
Experience
Provenance
Evaluation Assets
Reusable Patterns
```

저장 구현은 교체 가능하다.

```text
Files
RDBMS
Graph
Vector
Object Store
Event Store
Hybrid
```

---

# 8. Memory Architecture

## 8.1 Memory Types

```text
WORKING
현재 Task의 임시 상태

EPISODIC
실제 수행 이력과 사건

SEMANTIC
반복 경험에서 검증된 일반 지식

LOCAL / PERSONAL
특정 사용자·조직·프로젝트 규칙

PROCEDURAL
Skill / Scaffold / 실행 방법

FAILURE
오류·실패·잘못된 가설·복구 경험
```

## 8.2 Promotion

```text
Working
 ↓
Episode
 ↓
Pattern
 ↓
Candidate Lesson
 ↓
Evidence / Review
 ↓
Semantic / Procedural Asset
```

## 8.3 Retraction

```text
ACTIVE
→ CHALLENGED
→ SUPERSEDED / RETRACTED
```

삭제 대신 계보와 이유를 보존한다.

## 8.4 Point Retrieval

```text
Task Signature
 ↓
Need Memory?
 ↓
Scope / Domain / Relation / Role
 ↓
Keyword + Semantic + Graph + Metadata
 ↓
Quality / Freshness / Context Fit
 ↓
Minimum Relevant Context
```

---

# 9. Knowledge & Evidence Fabric

지식은 단순 문서가 아니라 관계형 Evidence Object로 관리한다.

```text
Source
 ↓ supports
Claim
 ↓ used_by
Decision
 ↓ affects
Design / Code / Policy
```

Evidence는 다음을 가진다.

```yaml
evidence:
  source:
  source_type:
  captured_at:
  version:
  claim:
  confidence:
  freshness:
  license:
  applicability:
  contradiction:
```

최신성이 중요한 지식과 안정적인 기초 지식을 구분한다.

---

# 10. Freshness & Staleness

Staleness는 시간만으로 판단하지 않는다.

```text
Elapsed Time
Source Version Drift
Environment Drift
Policy Drift
Model Drift
Dependency Drift
Schema Drift
Contradiction
```

```text
Fresh → Full Weight
Aging → Reduced Weight
Invalid → Revalidate / Exclude
```

---

# 11. Skill Architecture

Skill은 반복 가능한 작업 능력이다.

```yaml
skill_manifest:
  skill_id:
  purpose:
  triggers:
  exclusions:
  inputs:
  outputs:
  required_tools:
  authority:
  context_cost:
  quality_tier:
  version:
  provenance:
```

전체 Skill을 항상 Context에 넣지 않는다.

```text
Manifest Search
→ Trigger Match
→ Minimum Skill Load
```

---

# 12. Pattern → Software Asset Promotion

핵심 자동화 원칙:

```text
Repeated LLM Work
 ↓
Pattern Detection
 ↓
Quality / Stability
 ↓
Deterministic?
 ├─ NO → Skill / Scaffold
 └─ YES
      ↓
   Policy / Validator
      ↓
   Library / Component
      ↓
   Service / System
```

승격 근거:

```text
Repeated Frequency
Same Input/Output Shape
Stable Decision Rule
Low Ambiguity
High Validation Confidence
Economic Benefit
Security Benefit
```

Software Asset에는 반드시 원 작업 경험과 Provenance를 연결한다.

---

# 13. Learnable Scaffold

Scaffold는 Task를 해결하는 절차 자체다.

```text
Tool Order
Memory Retrieval
Delegation
Retry
Validation
Stop
Escalation
Budget
```

```text
Current Scaffold
+
Past Experience
+
Failure Memory
      ↓
Candidate Scaffold
      ↓
Representative Execution
      ↓
Compare
      ↓
Promote / Reject
```

Scaffold의 성공은 한 번의 결과가 아니라 반복 재현성으로 평가한다.

---

# 14. Evidence-Driven Self-Harness

Harness는 Model/Agent가 환경과 상호작용하는 Runtime-specific 운영 계층이다.

```text
Current Harness
 ↓
Execute
 ↓
Verifier-grounded Trace
 ↓
Weakness Mining
 ↓
Failure Clustering
 ↓
Minimal Causal Proposal
 ↓
Target + Held-out Regression
 ↓
Accept / Reject
```

## 14.1 Failure Signature

```yaml
failure_signature:
  verifier_cause:
  causal_behavior:
  reusable_mechanism:
  affected_surface:
  recurrence:
  evidence_refs:
```

## 14.2 Promotion Rule

```text
Target problem improved
AND
No unacceptable held-out regression
AND
Evidence sufficient
```

아니면 변경하지 않는다.

## 14.3 Bloat Control

```text
Duplicate
Contradictory
Dead
Never-triggered
Over-specific
Obsolete
High-cost / Low-value
```

Rule을 탐지하고 제거 후보로 관리한다.

---

# 15. Trust Boundary & Security

```text
PROTECTED
Constitution
Root Authority
Audit Ledger
Independent Verifier
Security Policy
Secret Boundary
Promotion Gate
Production Permission Root
```

```text
EVOLVABLE
Prompt
Context Policy
Memory Strategy
Tool Ordering
Retry
Workflow
Scaffold
Harness Projection
```

Self-improvement가 Protected Surface를 수정하지 못한다.

---

# 16. Tool Contract

```yaml
tool_contract:
  tool_id:
  input_schema:
  output_schema:
  side_effect:
  permissions:
  approval_required:
  idempotency:
  retry_policy:
  timeout:
  audit:
```

실행 전:

```text
Intent
 ↓
Policy Gate
 ├─ ALLOW
 ├─ APPROVAL
 └─ DENY
```

---

# 17. Multi-Agent Orchestration

모든 작업에 Multi-Agent를 사용하지 않는다.

```text
Simple / bounded / reversible
→ Single Agent or Program

Complex / cross-domain / long-running / parallelizable
→ Orchestration Candidate
```

Delegation:

```yaml
delegation:
  task_scope:
  context_pack:
  authority:
  tools:
  expected_output:
  evidence:
  budget:
  deadline:
```

위임은 권한 상속이 아니다.

---

# 18. Async Collaboration

병렬 Agent가 모든 정보를 Broadcast하지 않는다.

```text
Agent Event
 ↓
Evidence
 ↓
Relevance / Urgency / Confidence
 ↓
Who Needs It?
 ↓
Selective Delivery
```

저신뢰 가설은 다른 Agent에게 사실처럼 전달되지 않도록 태깅한다.

---

# 19. Canonical Event Stream

모든 Runtime의 이벤트를 공통 Schema로 정규화한다.

```text
TASK_STARTED
PLAN_CREATED
MEMORY_RETRIEVED
SKILL_USED
TOOL_CALLED
TOOL_RESULT
MESSAGE_SENT
VALIDATION
FAILURE
RETRY
ROLLBACK
TASK_COMPLETED
```

이를 기반으로:

```text
Audit
Observability
Replay
Recovery
Evaluation
Learning
```

을 수행한다.

---

# 20. Quality & Verification

검증은 위험과 기억 품질에 따라 동적으로 조절한다.

```text
New / High Risk / Low Confidence
→ Strong Verification

Trusted Pattern / High-quality History / Reversible
→ Fast Path

Deterministic Software Asset
→ Automated Verification
```

검증 생략은 “기록 생략”을 의미하지 않는다.

모든 Fast Path도 실행 상태와 결과 이력은 남긴다.

---

# 21. Quality Tier

예시:

```text
Q0 UNKNOWN
Q1 OBSERVED
Q2 REPRODUCED
Q3 VALIDATED
Q4 REPEATEDLY VALIDATED
Q5 TRUSTED
Q6 CANONICAL
```

품질이 높을수록 검증 비용을 줄일 수 있으나 Context Drift가 있으면 재검증한다.

---

# 22. Rollback & Recovery

모든 중요 변경:

```text
Before State
Change Set
After State
Validation
Rollback Point
```

실패도 다음 재시도 자산으로 남긴다.

```text
Failure
→ Cause
→ Attempt
→ Result
→ Recovery
→ Retry Condition
```

---

# 23. UI/UX Constitution

UI/UX는 장식이 아니라 시스템 이해·통제·복구 인터페이스다.

설계 입력:

```text
Domain
Core Task
Target User
Expertise
Device
Information Density
Risk
Accessibility
Existing Design System
Trend
```

출력:

```text
1. Context / Cognitive Bottleneck
2. Dynamic UX Law Mapping
3. Trend Pros / Cons / Fallback
4. State / Feedback / Error Prevention
5. Actionable Guidelines
6. Reusable Pattern
7. Validation Metrics
8. Provenance
```

핵심 상태는 사용자에게 보인다.

```text
Planning
Running
Waiting
Validating
Correcting
Completed
Failed
Rolling Back
```

사용자 통제 후보:

```text
Pause
Cancel
Undo
Retry
Inspect
Approve
Override
Rollback
```

---

# 24. UX Learning

```text
UX Request
 ↓
Retrieve Case / Pattern / Anti-pattern
 ↓
Design
 ↓
Prototype
 ↓
Measure
 ↓
Compare
 ↓
Case
 ↓
Repeated Success
 ↓
Pattern / Component / Token
```

Reference는 복제 대상이 아니라 Pattern Evidence다.

---

# 25. Data Flywheel

모든 로그가 학습 데이터가 되는 것은 아니다.

```text
Raw Run
 ↓
Quality Gate
 ↓
Approval
 ↓
Security / License / Redaction
 ↓
Reusable Asset
```

자산:

```text
Context Card
Eval Case
Failure Case
Retrieval Example
Trace
Training-ready Example
```

우선 사용처:

```text
Retrieval
Evaluation
Skill
Scaffold
Harness
Context Compression
```

Model Training은 선택적 상위 단계다.

---

# 26. Research Integration

새 기술·논문·알고리즘은 다음 절차로 흡수한다.

```text
Problem / Gap
 ↓
Search
 ↓
Primary Source
 ↓
Cross-check
 ↓
Extract Principle
 ↓
Applicability
 ↓
Experiment
 ↓
Compare
 ↓
Adopt / Adapt / Reject
```

특정 제품의 기능 이름이나 Benchmark를 Canonical Constitution에 직접 고정하지 않는다.

---

# 27. Decision Model

단일 점수에 모든 품질을 숨기지 않는다.

```yaml
quality_vector:
  correctness:
  security:
  reliability:
  performance:
  cost:
  maintainability:
  reusability:
  ux:
  recovery:
  compliance:
```

상황별 가중치는 가능하지만 원 축은 보존한다.

---

# 28. Authority Model

```text
OBSERVE
READ
PROPOSE
EXECUTE_REVERSIBLE
EXECUTE_MUTATING
APPROVE
PROMOTE
DEPLOY
ROLLBACK
MODIFY_POLICY
MODIFY_VERIFIER
```

각 Agent/Tool/Skill은 필요한 최소 권한만 가진다.

---

# 29. Conflict Resolution

충돌 우선순위 예:

```text
Constitution / Safety
↓
Authority
↓
Explicit User Goal
↓
Verified Project Policy
↓
Canonical Asset
↓
Trusted Pattern
↓
Current Proposal
```

상충하는 Evidence는 숨기지 않고 Conflict Object로 관리한다.

---

# 30. Folder / Architecture Ownership Principle

구현 시 폴더는 기술 이름보다 책임과 경계를 우선한다.

권장 논리 영역:

```text
constitution/
core-domain/
application/
orchestration/
brain/
memory/
knowledge/
skills/
scaffolds/
harness/
adapters/
tools/
evaluation/
security/
observability/
ui/
infrastructure/
contracts/
schemas/
tests/
docs/
```

실제 언어/프레임워크에 따라 Projection하되 동일 책임의 중복 소유를 금지한다.

---

# 31. Implementation Boundary

## Core에 넣을 것

```text
Canonical Models
State Machines
Policy Interfaces
Quality Model
Authority Model
Event Contracts
Memory Interfaces
Provenance
Decision Contracts
```

## Adapter로 뺄 것

```text
LLM Provider
Database
Vector DB
Graph DB
Message Broker
Filesystem
Web Search
IDE / CLI
External API
```

## Application/Orchestration에 넣을 것

```text
Use Cases
Task Routing
Planning
Delegation
Workflow
Promotion
Recovery
```

---

# 32. 구현 시작 순서

## Phase 0 — Constitution & Contracts
산출물:

```text
CONSTITUTION.md
ARCHITECTURE_INDEX.md
Canonical Object Schemas
Authority Matrix
Quality Vector
Event Schema
State Machines
```

## Phase 1 — Runtime Kernel
구현:

```text
Task
Context
State
Event
Policy Gate
Tool Contract
Execution Trace
Rollback Point
```

## Phase 2 — Portable Brain
구현:

```text
Working Memory
Episode Store
Semantic Store
Provenance
Point Retrieval
Context Pack
```

## Phase 3 — Skill / Asset Engine
구현:

```text
Skill Manifest
Skill Router
Pattern Detector
Asset Registry
Promotion Pipeline
```

## Phase 4 — Evaluation & Recovery
구현:

```text
Verifier
Baseline Comparator
Quality Scoring
Regression Suite
Failure Memory
Rollback
```

## Phase 5 — Orchestration
구현:

```text
Planner
Delegator
Agent Registry
Async Messaging
Selective Awareness
Budget / Priority
```

## Phase 6 — Learnable Scaffold / Self-Harness
구현:

```text
Scaffold Registry
Failure Mining
Proposal
Sandbox
Regression Gate
Version Lineage
```

## Phase 7 — UI/UX Operational View
구현:

```text
Task Timeline
Agent State
Evidence Viewer
Memory Viewer
Before/After Diff
Quality
Approval
Rollback
```

## Phase 8 — Research & Evolution
구현:

```text
Research Intake
Evidence Register
Experiment
Technology Radar
Canonical Promotion
```

---

# 33. Minimum Viable AEGIS-CORE

본격 구현의 첫 MVP는 모든 기능을 구현하지 않는다.

최소 Vertical Slice:

```text
User Task
 ↓
Task Object
 ↓
Context Diagnosis
 ↓
Memory Retrieval
 ↓
Plan
 ↓
Single Agent / Tool Execution
 ↓
Canonical Events
 ↓
Validation
 ↓
Before/After
 ↓
Episode Memory
 ↓
Failure / Success Record
 ↓
UI Timeline
```

이 Vertical Slice가 완성된 후 Multi-Agent와 Self-Improvement를 확장한다.

---

# 34. 초기 구현에서 피해야 할 것

```text
처음부터 모든 LLM 연결
처음부터 거대한 Multi-Agent 조직
모든 Memory를 Vector DB에 저장
모든 관계를 Graph로 강제
모든 이벤트를 Kafka로 시작
모든 반복을 LLM Prompt로 유지
검증기와 개선 Agent를 동일 권한으로 운영
기술 이름을 Domain Model에 박아 넣기
Benchmark 점수를 내부 품질 보장으로 오해
UI에서 내부 복잡성을 그대로 노출
```

---

# 35. Implementation Definition of Done

각 기능은 다음을 만족해야 완료다.

```text
Purpose defined
Contract defined
Owner defined
Input / Output defined
State transition defined
Authority defined
Failure defined
Evidence emitted
Observability available
Test available
Rollback/recovery considered
Provenance recorded
Documentation updated
```

---

# 36. 구현 설계 문서 세트

이 Master Concept 다음 단계부터는 아래 구현 설계 문서를 생성한다.

```text
01_SYSTEM_CONTEXT.md
02_DOMAIN_MODEL.md
03_RUNTIME_KERNEL.md
04_CANONICAL_OBJECT_MODEL.md
05_EVENT_PROTOCOL.md
06_MEMORY_KNOWLEDGE_ARCHITECTURE.md
07_SKILL_ASSET_ENGINE.md
08_ORCHESTRATION_ENGINE.md
09_SECURITY_AUTHORITY.md
10_EVALUATION_QUALITY.md
11_RECOVERY_ROLLBACK.md
12_SELF_IMPROVEMENT_ENGINE.md
13_ADAPTER_ARCHITECTURE.md
14_DATA_STORAGE_ARCHITECTURE.md
15_API_CONTRACTS.md
16_UI_UX_OPERATIONAL_VIEW.md
17_OBSERVABILITY.md
18_DEPLOYMENT_TOPOLOGY.md
19_TEST_STRATEGY.md
20_IMPLEMENTATION_ROADMAP.md
```

각 문서는 이 Master Concept의 하위 Projection이며 상위 의미를 변경할 수 없다.

---

# 37. 구현 설계 의사결정 원칙

구현 기술 선정 시:

```text
Requirement
 ↓
Constraint
 ↓
Candidate Technologies
 ↓
Evidence
 ↓
Prototype / Benchmark
 ↓
Security / Operations
 ↓
Cost
 ↓
Decision
 ↓
ADR
```

“최신 기술” 자체가 선택 근거가 되어서는 안 된다.

---

# 38. Canonical Invariants

다음은 구현 기술이 바뀌어도 유지해야 한다.

```text
No silent destructive change
No unverifiable promotion
No memory without provenance where provenance is required
No authority escalation by inheritance
No self-modification of root verifier
No global rule from a single anecdote
No trusted asset without quality history
No hidden rollback loss
No forced multi-agent where single execution is better
No repeated deterministic LLM work when software can replace it
```

---

# 39. Master Evolution Loop

```text
                  ┌─────────────────┐
                  │     INTENT      │
                  └────────┬────────┘
                           ↓
                  ┌─────────────────┐
                  │ CONTEXT / FACT  │
                  └────────┬────────┘
                           ↓
             ┌──────────────────────────┐
             │ MEMORY / KNOWLEDGE / R&D │
             └─────────────┬────────────┘
                           ↓
                  ┌─────────────────┐
                  │ PLAN / SCAFFOLD │
                  └────────┬────────┘
                           ↓
                  ┌─────────────────┐
                  │     EXECUTE     │
                  └────────┬────────┘
                           ↓
                  ┌─────────────────┐
                  │ VERIFY / MEASURE│
                  └────────┬────────┘
                           ↓
              ┌────────────────────────┐
              │ COMPARE WITH BASELINE  │
              └───────────┬────────────┘
                          ↓
        ┌─────────────────┼──────────────────┐
        ↓                 ↓                  ↓
      ACCEPT            RETRY             ROLLBACK
        ↓
   EXPERIENCE
        ↓
   PATTERN?
   ├─ NO → EPISODIC MEMORY
   └─ YES
        ↓
   GENERALIZABLE?
   ├─ NO → LOCAL ASSET
   └─ YES
        ↓
   DETERMINISTIC?
   ├─ NO → SKILL / SCAFFOLD
   └─ YES → SOFTWARE ASSET
        ↓
   QUALITY / REGRESSION
        ↓
   CANONICAL PROMOTION
        ↓
   NEXT TASK STARTS BETTER
```

---

# 40. 최종 구현 헌법

> **AEGIS-CORE는 특정 기술을 조합한 제품이 아니라 상황을 인지하고, 사실을 실측하고, 부족한 정보를 질문하고, 필요한 기억과 지식을 선택적으로 검색하고, 위험과 권한을 판단하여 실행하며, 결과를 독립적으로 검증하고 이전 상태와 비교하고, 성공과 실패를 모두 근거와 함께 기억하여 다음 작업을 더 안전하고 빠르고 정확하게 만드는 자율 적응형 설계·실행 Core다. 반복적으로 성공한 작업 방법은 Skill과 Scaffold로 구조화하고, 결정적으로 표현 가능한 반복 패턴은 Policy·Validator·Library·Service·System으로 승격하여 LLM의 반복 추론을 제거한다. 모델과 실행 Harness는 교체 가능해야 하며 Memory·Knowledge·Skill·Experience·Provenance는 Portable Brain에 유지한다. 자기개선은 실제 실패 Evidence에서 시작하여 최소 변경을 제안하고 독립된 회귀 검증을 통과한 변화만 누적한다. Constitution, Root Authority, Independent Verifier, Audit과 Security Boundary는 자기개선 대상에서 분리한다. 모든 중요한 작업은 상태·근거·변경·품질·복구 이력을 남기며 UI/UX는 이 복잡성을 사용자에게 떠넘기지 않고 현재 상태, 필요한 판단, 근거, 위험과 복구 수단을 명확하게 보여준다. 구현 기술은 언제든 바뀔 수 있지만 이러한 Canonical Meaning과 Invariant는 유지한다.**
