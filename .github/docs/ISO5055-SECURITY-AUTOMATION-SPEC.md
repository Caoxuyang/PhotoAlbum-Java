# ISO 5055 Security Automation Specification

**Version:** 1.0
**Date:** 2026-03-05
**Status:** Active

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Prerequisites & Secrets](#prerequisites--secrets)
4. [Milestone 1: Security Assessment Report](#milestone-1-security-assessment-report)
5. [Milestone 2: Automated Security Fixes](#milestone-2-automated-security-fixes)
6. [Agent Definitions](#agent-definitions)
7. [Workflow Triggers](#workflow-triggers)
8. [Security Considerations](#security-considerations)
9. [Troubleshooting](#troubleshooting)

---

## Overview

This system automates ISO 5055 security compliance by:

1. **Milestone 1 (M1)**: Running security assessments and reporting findings to GitHub Issues
2. **Milestone 2 (M2)**: Automatically fixing vulnerabilities via Copilot Coding Agent with one PR per finding

### Key Features

- 🔄 **Fully Automated Pipeline**: From assessment to fix with no manual intervention
- 🎯 **Severity Filtering**: Focus on mandatory, optional, potential, or all findings
- 🤖 **Copilot Integration**: Leverages GitHub Copilot Coding Agent for intelligent fixes
- 📊 **ISO 5055 Compliance**: Based on industry-standard security assessment framework
- 🔐 **One Fix Per PR**: Atomic changes for easy review and rollback

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          ISO 5055 Security Automation                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    MILESTONE 1: Assessment                            │   │
│  │                                                                       │   │
│  │   ┌─────────────┐    ┌─────────────┐    ┌─────────────────────────┐  │   │
│  │   │  Trigger    │───▶│  Workflow   │───▶│  Create Issue           │  │   │
│  │   │  (Schedule/ │    │  Creates    │    │  + Assign Copilot       │  │   │
│  │   │   Manual)   │    │  Issue      │    │                         │  │   │
│  │   └─────────────┘    └─────────────┘    └───────────┬─────────────┘  │   │
│  │                                                      │                │   │
│  │                                                      ▼                │   │
│  │   ┌─────────────────────────────────────────────────────────────┐    │   │
│  │   │  Copilot (security-assessment-reporter agent)               │    │   │
│  │   │  1. Call appmod-iso5055-security-assessment tool            │    │   │
│  │   │  2. Format results as markdown                              │    │   │
│  │   │  3. Post comment to issue (using PAT_TOKEN)                 │    │   │
│  │   └─────────────────────────────────────────────────────────────┘    │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                          │                                   │
│                                          │ issue_comment event               │
│                                          ▼                                   │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    MILESTONE 2: Fixes                                 │   │
│  │                                                                       │   │
│  │   ┌─────────────┐    ┌─────────────┐    ┌─────────────────────────┐  │   │
│  │   │  Validate   │───▶│  Parse      │───▶│  Create Sub-Issues      │  │   │
│  │   │  Report     │    │  Findings   │    │  (1 per vulnerability)  │  │   │
│  │   └─────────────┘    └─────────────┘    └───────────┬─────────────┘  │   │
│  │                                                      │                │   │
│  │                                                      ▼                │   │
│  │   ┌─────────────────────────────────────────────────────────────┐    │   │
│  │   │  For each sub-issue:                                        │    │   │
│  │   │  Copilot (security-fixer agent)                             │    │   │
│  │   │  1. Call appmod-run-task tool                               │    │   │
│  │   │  2. Implement fix                                           │    │   │
│  │   │  3. Create PR (using PAT_TOKEN)                             │    │   │
│  │   └─────────────────────────────────────────────────────────────┘    │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Prerequisites & Secrets

### Required Secrets

You must configure **TWO** secrets in your GitHub repository:

#### 1. Repository Environment Secret: `GH_PAT_TOKEN`

**Purpose:** Used by GitHub Actions workflows to assign Copilot to issues via GraphQL API.

**Required Permissions:**
- `repo` (Full control of private repositories)
- `issues:write` (Read and write issues)
- `pull_requests:write` (Read and write pull requests)

**How to Create:**
1. Go to GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens
2. Create new token with the permissions above
3. Go to your repository → Settings → Secrets and variables → Actions
4. Click "New repository secret"
5. Name: `GH_PAT_TOKEN`
6. Value: Your personal access token

#### 2. Copilot Environment Secret: `PAT_TOKEN`

**Purpose:** Used by Copilot Coding Agent to post comments and create PRs (default GITHUB_TOKEN is read-only).

**Required Permissions:**
- Same as `GH_PAT_TOKEN` above (can use the same token value)

**How to Create:**
1. Go to your repository → Settings → Copilot → Coding agent
2. Under "Secrets", click "Add secret"
3. Name: `PAT_TOKEN`
4. Value: Your personal access token (same as GH_PAT_TOKEN)

> ⚠️ **IMPORTANT:** Both secrets can use the same Personal Access Token value, but they must be configured in **different locations**:
> - `GH_PAT_TOKEN` → Repository Actions Secrets
> - `PAT_TOKEN` → Copilot Coding Agent Secrets

### Why Two Secrets?

| Secret | Used By | Location | Purpose |
|--------|---------|----------|---------|
| `GH_PAT_TOKEN` | GitHub Actions Workflow | Repository → Settings → Secrets → Actions | Assign Copilot to issues via GraphQL API |
| `PAT_TOKEN` | Copilot Coding Agent | Repository → Settings → Copilot → Coding agent → Secrets | Post comments, create branches, create PRs |

The Copilot Coding Agent runs in an isolated environment and can only access secrets configured in its own secrets store, not repository Actions secrets.

### Copilot Configuration

Ensure Copilot Coding Agent is enabled:
1. Go to your repository → Settings → Copilot → Coding agent
2. Enable "Allow Copilot to open pull requests"
3. Configure allowed tools: `appmod-iso5055-security-assessment`, `appmod-run-task`

---

## Milestone 1: Security Assessment Report

### Workflow File

**Location:** `.github/workflows/iso5055-security-assessment.yml`

### Triggers

| Trigger | Description |
|---------|-------------|
| `schedule` | Daily at 2 AM UTC (`cron: '0 2 * * *'`) |
| `workflow_dispatch` | Manual trigger with severity filter input |

### Inputs (Manual Trigger)

| Input | Type | Default | Options |
|-------|------|---------|---------|
| `severity_filter` | choice | `mandatory` | `mandatory`, `optional`, `potential`, `all` |

### Process Flow

1. **Workflow creates GitHub Issue** with:
   - Title: `🔒 [ISO5055] Run Security Assessment - REPORT ONLY`
   - Labels: `security`, `iso5055`, `automated`, `copilot`
   - Body includes explicit issue number and URL for Copilot

2. **Workflow assigns Copilot** via GraphQL API:
   ```graphql
   mutation($issueId: ID!) {
     addAssigneesToAssignable(input: {
       assignableId: $issueId,
       assigneeIds: ["BOT_kgDOC9w8XQ"]
     }) {
       clientMutationId
     }
   }
   ```

3. **Copilot executes `security-assessment-reporter` agent:**
   - Reads issue body to get correct issue number
   - Calls `appmod-iso5055-security-assessment` MCP tool
   - Formats results as markdown with `<!-- ASSESSMENT_VERIFIED -->` marker
   - Posts comment using `add_issue_comment` MCP tool (uses `PAT_TOKEN` automatically)

### Output

A comment on the issue with format:
```markdown
<!-- ASSESSMENT_VERIFIED -->
## 📊 ISO 5055 Security Assessment Report

**Assessment Date:** 2026-03-05
**Repository:** Caoxuyang/PhotoAlbum-Java
**Severity Filter:** MANDATORY

### Summary

| Severity | Count |
|----------|-------|
| 🔴 Mandatory | 3 |
| 🟠 Optional | 5 |
| 🟡 Potential | 2 |
| 🔵 Information | 1 |
| **Total** | 11 |

---

### 🔴 Mandatory Findings

#### CWE-89: SQL Injection
- **Category:** Input Validation
- **Description:** Improper neutralization of special elements in SQL command
- **Evidence:** `src/main/java/UserDao.java:45` — String concatenation in SQL query

...
```

---

## Milestone 2: Automated Security Fixes

### Workflow File

**Location:** `.github/workflows/iso5055-security-fixer.yml`

### Trigger

| Event | Conditions |
|-------|------------|
| `issue_comment.created` | Multi-layer validation (see below) |

### Multi-Layer Validation

The workflow only triggers when ALL conditions are met:

```yaml
if: |
  github.event.comment.user.login == 'Copilot' &&
  github.event.comment.user.type == 'Bot' &&
  contains(github.event.issue.labels.*.name, 'iso5055') &&
  contains(github.event.issue.labels.*.name, 'automated') &&
  contains(github.event.comment.body, '## 📊 ISO 5055 Security Assessment Report') &&
  contains(github.event.comment.body, '<!-- ASSESSMENT_VERIFIED -->')
```

| Check | Purpose |
|-------|---------|
| `user.login == 'Copilot'` | Ensures comment is from Copilot |
| `user.type == 'Bot'` | Confirms bot account type |
| `labels contains 'iso5055'` | Issue is part of ISO 5055 workflow |
| `labels contains 'automated'` | Issue was auto-generated |
| `body contains report header` | Valid report format |
| `body contains verified marker` | Report was properly generated |

### Process Flow

1. **Workflow validates report structure** (Summary, Mandatory sections)

2. **Workflow extracts severity filter** from report

3. **Workflow parses findings** and creates sub-issues:
   - One issue per CWE finding
   - Labels: `security-fix`, `copilot-fix`, `iso5055`
   - Body includes CWE ID, evidence, and instructions

4. **Workflow assigns Copilot** to each sub-issue

5. **Copilot executes `security-fixer` agent** for each sub-issue:
   - Calls `appmod-run-task` MCP tool
   - Implements the fix
   - Creates PR with `PAT_TOKEN`

### Sub-Issue Format

```markdown
## 🔧 Fix Security Vulnerability: CWE-89

**Parent Issue:** #8
**CWE:** CWE-89 - SQL Injection
**Category:** Input Validation

---

### Evidence

`src/main/java/UserDao.java:45` — String concatenation in SQL query

---

### Instructions

**Agent:** `security-fixer`

Follow the instructions in `.github/agents/security-fixer.md`

**Use the `appmod-run-task` tool to fix this vulnerability.**

Task: Fix CWE-89 vulnerability as identified in the evidence above.

---

### Requirements

1. Fix ONLY this specific vulnerability (CWE-89)
2. Create a PR with the fix
3. Reference this issue in the PR description
4. Do NOT fix other issues in the same PR
```

---

## Agent Definitions

### security-assessment-reporter

**Location:** `.github/agents/security-assessment-reporter.md`

**Purpose:** READ-ONLY security assessment. Reports findings but NEVER fixes code.

**Allowed Actions:**
- ✅ Read issue body to get correct issue number
- ✅ Call `appmod-iso5055-security-assessment` tool
- ✅ Format results as markdown
- ✅ Post comment using `add_issue_comment` MCP tool

**Forbidden Actions:**
- ❌ Edit any source file
- ❌ Create pull requests
- ❌ Fix vulnerabilities
- ❌ Commit changes
- ❌ Close issues

### security-fixer

**Location:** `.github/agents/security-fixer.md`

**Purpose:** Fix ONE specific vulnerability and create a PR.

**Allowed Actions:**
- ✅ Read issue to understand vulnerability
- ✅ Call `appmod-run-task` tool
- ✅ Make code changes to fix vulnerability
- ✅ Create branch, commit, push (using `PAT_TOKEN`)
- ✅ Create PR (using `GH_TOKEN="${PAT_TOKEN}" gh pr create` - PR creation via gh CLI works, only issue comments need MCP)

**Requirements:**
- Fix ONLY the specified vulnerability
- One PR per vulnerability
- Reference parent issue in PR description

---

## Workflow Triggers

### Manual Trigger (Recommended for Testing)

```bash
# Via GitHub CLI
gh workflow run "ISO5055 Security Assessment" \
  --repo Caoxuyang/PhotoAlbum-Java \
  -f severity_filter=mandatory

# Via GitHub UI
# Go to Actions → ISO5055 Security Assessment → Run workflow
```

### Automatic Trigger

The assessment runs automatically at 2 AM UTC daily.

### Severity Filter Options

| Filter | Description |
|--------|-------------|
| `mandatory` | Critical vulnerabilities that must be fixed (default) |
| `optional` | Recommended fixes for better security |
| `potential` | Possible issues that may need investigation |
| `all` | All findings regardless of severity |

---

## Security Considerations

### Token Permissions

Both tokens should follow the principle of least privilege:

| Permission | Required For |
|------------|--------------|
| `repo` | Read repository content, create branches |
| `issues:write` | Create/comment on issues |
| `pull_requests:write` | Create pull requests |

### Why Not Use Default GITHUB_TOKEN?

The default `GITHUB_TOKEN` provided by GitHub Actions has read-only permissions for Copilot Coding Agent. This is by design to prevent unauthorized modifications. The `PAT_TOKEN` is required for:

1. Posting comments to issues
2. Creating branches
3. Pushing commits
4. Creating pull requests

### Audit Trail

All actions are logged:
- Workflow run logs in Actions tab
- Issue comments with assessment reports
- PR descriptions with fix details
- Git commit history

---

## Troubleshooting

### Common Issues

#### 1. HTTP 403 Error When Posting Comments

**Symptom:** Copilot fails to post comment with "HTTP 403" error.

**Cause:** Using default `GITHUB_TOKEN` instead of `PAT_TOKEN`, OR trying to access `api.github.com` directly.

**Solution:** Use the MCP JSON-RPC API with `Bearer ${PAT_TOKEN}`:

1. Endpoint: `https://api.enterprise.githubcopilot.com/mcp` (NOT `api.github.com`)
2. Authorization: `Bearer ${PAT_TOKEN}` (PAT_TOKEN configured in Copilot Coding Agent secrets)
3. Protocol: JSON-RPC 2.0 with session management
4. Tool: `add_issue_comment` via `tools/call` method

**What DOES NOT work:**
- `gh issue comment` → TIMEOUT (network blocked)
- `gh api` → TIMEOUT (network blocked)
- `curl api.github.com` → 403 FORBIDDEN (blocked by DNS proxy)
- `Authorization: token ${PAT_TOKEN}` to api.github.com → 403

**What WORKS (verified):**
```bash
# Initialize MCP session
curl -D "${TMPFILE}" --max-time 10 \
  -X POST \
  -H "Authorization: Bearer ${PAT_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  "https://api.enterprise.githubcopilot.com/mcp" \
  -d '{"jsonrpc":"2.0","id":0,"method":"initialize",...}'

# Extract session ID from response headers
SESSION_ID=$(grep -i "Mcp-Session-Id:" "${TMPFILE}" | sed 's/.*Mcp-Session-Id: //' | tr -d '\r\n ')

# Post comment via tools/call
curl -s -w "\nHTTP_STATUS:%{http_code}" --max-time 30 \
  -X POST \
  -H "Authorization: Bearer ${PAT_TOKEN}" \
  -H "Mcp-Session-Id: ${SESSION_ID}" \
  "https://api.enterprise.githubcopilot.com/mcp" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"add_issue_comment",...}}'
```

See `.github/agents/security-assessment-reporter.md` for complete implementation.

#### 2. Copilot Not Assigned to Issue

**Symptom:** Issue created but no Copilot activity.

**Cause:** `GH_PAT_TOKEN` missing or incorrect.

**Solution:**
1. Verify `GH_PAT_TOKEN` is set in repository Actions secrets
2. Check token has required permissions
3. Ensure Copilot Coding Agent is enabled for repository

#### 3. Milestone 2 Not Triggering

**Symptom:** Assessment report posted but no sub-issues created.

**Cause:** Report missing `<!-- ASSESSMENT_VERIFIED -->` marker or other validation checks failing.

**Solution:**
1. Check if marker is present in comment
2. Verify issue has correct labels (`iso5055`, `automated`)
3. Check comment author is `Copilot` bot

#### 4. Wrong Issue Number in Comment

**Symptom:** Copilot posts to issue #1 instead of correct issue.

**Cause:** Agent not reading issue number from issue body.

**Solution:** Workflow now includes explicit issue number in body. Agent instructions updated to extract from:
```
**Issue Number:** #XX
**Issue URL:** https://github.com/.../issues/XX
```

### Debug Commands

```bash
# Check workflow runs
gh run list --workflow "ISO5055 Security Assessment" --repo Caoxuyang/PhotoAlbum-Java

# View specific run logs
gh run view <RUN_ID> --log --repo Caoxuyang/PhotoAlbum-Java

# List issues
gh issue list --label iso5055 --repo Caoxuyang/PhotoAlbum-Java

# Check Copilot assignment
gh api repos/Caoxuyang/PhotoAlbum-Java/issues/<ISSUE_NUMBER> --jq '.assignees[].login'
```

---

## File Structure

```
.github/
├── agents/
│   ├── security-assessment-reporter.md    # M1: Read-only reporter agent
│   └── security-fixer.md                  # M2: Vulnerability fixer agent
├── workflows/
│   ├── iso5055-security-assessment.yml    # M1: Creates issue, assigns Copilot
│   └── iso5055-security-fixer.yml         # M2: Parses report, creates sub-issues
└── docs/
    └── ISO5055-SECURITY-AUTOMATION-SPEC.md  # This document
```

---

## Quick Setup Checklist

- [ ] **Create Personal Access Token** with `repo`, `issues:write`, `pull_requests:write` permissions
- [ ] **Add `GH_PAT_TOKEN`** to Repository → Settings → Secrets → Actions
- [ ] **Add `PAT_TOKEN`** to Repository → Settings → Copilot → Coding agent → Secrets
- [ ] **Enable Copilot Coding Agent** for repository
- [ ] **Configure allowed tools**: `appmod-iso5055-security-assessment`, `appmod-run-task`
- [ ] **Test manually**: Run workflow via `gh workflow run` or GitHub UI
- [ ] **Verify**: Check issue created, Copilot assigned, comment posted

---

## References

- [ISO/IEC 5055:2021](https://www.iso.org/standard/80623.html) - Software quality measurement
- [CWE - Common Weakness Enumeration](https://cwe.mitre.org/)
- [GitHub Copilot Coding Agent](https://docs.github.com/en/copilot/using-github-copilot/using-copilot-coding-agent)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
