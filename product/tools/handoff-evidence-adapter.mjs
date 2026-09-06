export function normalizeGitHubEvidence({ issue, pullRequest = null, workflowRuns = [], jobs = [], ownerMatches = true, acceptanceComplete = false, relevantChange = false, transientFailure = false, highRisk = false, externalStateChanged = false, retryCount = 0, maxRetry = 2, blockers = [] }) {
  if (!issue || typeof issue.state !== 'string') throw new Error('AEGIS-HANDOFF-EVIDENCE-001 INVALID_ISSUE');
  if (!Array.isArray(workflowRuns) || !Array.isArray(jobs) || !Array.isArray(blockers)) throw new Error('AEGIS-HANDOFF-EVIDENCE-002 INVALID_COLLECTION');

  const executedJobs = jobs.filter((job) => Array.isArray(job.steps) && job.steps.length > 0);
  const failedExecutedJobs = executedJobs.filter((job) => job.conclusion === 'failure');
  const successfulExecutedJobs = executedJobs.filter((job) => job.conclusion === 'success');
  const preRunFailures = jobs.filter((job) => job.conclusion === 'failure' && (!Array.isArray(job.steps) || job.steps.length === 0));
  const allRequiredRunsCompleted = workflowRuns.length > 0 && workflowRuns.every((run) => run.status === 'completed');
  const validationEvidence = allRequiredRunsCompleted && successfulExecutedJobs.length > 0 && failedExecutedJobs.length === 0 && preRunFailures.length === 0;
  const validationFailed = failedExecutedJobs.length > 0;

  return {
    issueOpen: issue.state === 'open',
    acknowledged: Boolean(pullRequest || issue.comments > 0 || relevantChange),
    ownerMatches: Boolean(ownerMatches),
    acceptanceComplete: Boolean(acceptanceComplete),
    validationEvidence,
    validationFailed,
    relevantChange: Boolean(relevantChange),
    transientFailure: Boolean(transientFailure),
    highRisk: Boolean(highRisk),
    externalStateChanged: Boolean(externalStateChanged),
    retryCount,
    maxRetry,
    blockers,
    provenance: {
      issueNumber: issue.issue_number ?? issue.number ?? null,
      pullRequestNumber: pullRequest?.number ?? null,
      workflowRunIds: workflowRuns.map((run) => run.id),
      executedJobIds: executedJobs.map((job) => job.id),
      preRunFailureJobIds: preRunFailures.map((job) => job.id)
    },
    executionStatus: preRunFailures.length > 0 && executedJobs.length === 0 ? 'NOT_EXECUTED' : executedJobs.length > 0 ? 'EXECUTED' : 'UNKNOWN'
  };
}
