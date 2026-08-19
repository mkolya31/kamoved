# AGENTS.md

## Project structure

* `frontend/` — frontend application.
* `backend/` — backend application.
* `deploy/` — deployment scripts and deployment-related configuration.
* `docs/` — important project documentation, including product overview and operational instructions such as restoring the application from backups.

Before making significant changes, read the relevant existing documentation when it can affect the task.

## Development environment

* Primary development OS: Windows.
* Use PowerShell-compatible commands.
* Use PowerShell syntax and `&` to invoke executables by path; do not assume Bash utilities are available.
* When the sandbox blocks writes to `.git` or launching Docker or browser processes, request elevation instead of retrying the command or investigating whether the tool is installed.
* Text files use UTF-8 without BOM and LF line endings; do not check encoding or line endings unless a tool reports an actual error.
* Do not assume that tools are installed globally.
* Prefer tools, dependencies, and scripts already declared in the repository.
* Do not install additional tools or dependencies only to work around a validation problem.
* If the expected validation tool is unavailable or incorrectly configured, report the problem instead of repeatedly trying unrelated workarounds.

## Running the project

### Backend and database

For local frontend or browser validation, run the database and backend through Docker Compose and run the frontend separately through Vite.

If `.env` is missing, copy `.env.example` to `.env`.

From the repository root:

```powershell
docker compose -f compose.yaml -f compose.dev.yaml up -d database backend
```

Add `--build` only after backend source or Docker configuration changes.

The backend is ready when `http://127.0.0.1:8080/api/auth/csrf` responds successfully.

### Frontend

From `frontend/`, use:

```powershell
pnpm dev
pnpm build
pnpm test
pnpm test:e2e
```

The development server runs at `http://127.0.0.1:5173` and proxies API requests to the backend on port `8080`.

### Backend build and tests

The backend uses Maven Wrapper. Run commands from `backend/`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

# Validation

Before considering an implementation complete:

1. Build the affected application.
2. Run tests relevant to the change.
3. Validate user-visible behavior when the change affects the user interface.
4. If you believe any additional validation is necessary, ask the user for permission before running it.

Use the smallest reasonable validation scope that provides confidence in the current change.

## Available browser validation

Use the interactive browser for one-off exploratory, behavioral, or visual checks.

Local browser validation requires authentication. Use the test user `test-user` / `password-for-test-user`.

Use `pnpm test:e2e` from `frontend/` when the scenario should be repeatable or protected against regression.

Do not inspect or install Playwright browsers before running the selected test; do so only when Playwright reports that a browser is missing.

Use the smallest suitable validation method. Do not run both interactive browser validation and Playwright unless they cover different risks.

## Validation failures

A failed automated check does not by itself prove that the application contains a defect. The failure may be caused by the implementation, the test itself, timing, the local environment, sandbox restrictions, or infrastructure.

When a validation check fails:

1. Inspect the immediate failure information.
2. If the failure is clearly caused by the current implementation, fix it and rerun the relevant check.
3. If the cause is uncertain, or the failure appears unrelated to the current change, report the finding to the user before starting a broader investigation.
4. Do not automatically fix unrelated defects discovered during validation.

## Stalled validation

Do not wait indefinitely for a validation command that stops making progress.

If validation runs unusually long without useful output or progress:

1. Report what has already been successfully validated.
2. Explain where validation is stalled and the likely cause, if known.
3. Suggest the smallest reasonable next step.
4. Ask the user whether to continue the automated investigation.

When useful, offer manual validation as an alternative.

Do not repeatedly retry stalled browser tests, switch to debug modes, terminate or restart processes, elevate permissions, or try alternative workarounds without user approval.

## Git branches

Use `<prefix>/<LINEAR-ISSUE>-<short-description>`, where `<prefix>` is one of: `feat`, `fix`, `hotfix`, `build`, `chore`, `ci`, `cd`, `docs`, `style`, `refactor`, `perf`, `test`. Do not use `codex/`.

Create branches from an up-to-date `develop`. Creating, switching, and updating branches is allowed without additional permission.

Do not commit, push, merge, rebase shared history, or create pull requests unless the user explicitly asks.

## Commits

Create commits only when explicitly requested. Use Conventional Commits with a Russian description and append the Linear issue identifier: `<type>: <описание изменения> <LINEAR-ISSUE>`.

Examples: `feat: добавлен автор продажи KAM-36`, `fix: скрыта информация о приложении со страницы входа KAM-31` 

## Linear

When substantive work on a Linear issue begins, set only its status to `In Progress`. Do not make other status transitions or modify its title, description, acceptance criteria, comments, priority, labels, assignee, or other fields unless explicitly requested. Reading issue context is allowed.

## API and architecture changes

Ask the user before:

* making a backward-incompatible API contract change;
* performing a large-scale refactoring.

Compatible API changes and ordinary implementation decisions may be made as part of the current task without additional confirmation.

## Documentation

When a change makes existing documentation inaccurate or incomplete, update the relevant documentation as part of the same task.

Keep documentation consistent with the actual behavior of the application.
