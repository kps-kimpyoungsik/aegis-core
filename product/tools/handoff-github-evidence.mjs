#!/usr/bin/env node

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function hasExecutedSteps(jobs) {
  return asArray(jobs).some((job) => Array.isArray(job?.steps) && job.steps.length > 0);
}

function hasExecutedFailure(jobs) {
  return asArray(jobs).some((job) => job?.conclusion === 'failure' && Array.isArray(job?.steps) && job.steps.length > 0);
}

function allRequiredValidationPassed(jobs) {
  const executed = asArray(jobs).filter((job) => Array.isArray(job?.steps) && job.steps.length > 0);
  return executed.length > 0 && executed.every((job) => job.conclusion === 'success');
}

function inferAcceptanceComplete(issue) {
  const body = String(issue?.body ?? '');
  const checkboxes = [...body.matchAll(/- \[( |x|X)\]/g)];
  if (checkboxes.length === 0) return false;
  return checkboxes.every((match) => match[1].toLowerCase() === 'x');
}

export function normalizeGitHubEvidence({
  issue,
  jobs = [],
  blockers = [],
  ownerMatches = true,
  acknowledged,
  relevantChange = false,
  transientFailure = false,
  highRisk = false,
  externalStateChanged = false,
  retryCount = 0,
  maxRetry = 2
}) {
  if (!issue || typeof issue.state !== 'string') {
    throw new Error('AEGIS-GITHUB-EVIDENCE-001 INVALID_ISSUE');
  }
  if (!Array.isArray(jobs)) {
    throw new Error('AEGIS-GITHUB-EVIDENCE-002 JOBS_NOT_ARRAY');
  }

  const executedSteps = hasExecutedSteps(jobs);
  const validationFailed = hasExecutedFailure(jobs);
  const validationEvidence = allRequiredValidationPassed(jobs);
  const preRunAdmissionFailure = jobs.some((job) => job?.conclusion === 'failure' && (!Array.isArray(job?.steps) || job.steps.length === 0));

  return {
    issueOpen: issue.state === 'open',
    acknowledged: acknowledged ?? Number(issue.comments ?? 0) > 0,
    ownerMatches: Boolean(ownerMatches),
    acceptanceComplete: inferAcceptanceComplete(issue),
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
      source: 'github',
      issueNumber: issue.issue_number ?? issue.number ?? null,
      issueState: issue.state,
      jobCount: jobs.length,
      executedSteps,
      preRunAdmissionFailure,
      verificationStatus: preRunAdmissionFailure && !executedSteps ? 'NOT_EXECUTED' : validationEvidence ? 'PASS' : validationFailed ? 'FAIL' : 'UNKNOWN'
    }
  };
}
