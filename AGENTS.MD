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
* Do not assume that tools are installed globally.
* Prefer tools, dependencies, and scripts already declared in the repository.
* Do not install additional tools or dependencies only to work around a validation problem.
* If the expected validation tool is unavailable or incorrectly configured, report the problem instead of repeatedly trying unrelated workarounds.

## Running the project

### Backend and database

From the repository root:

```powershell
docker compose -f compose.yaml -f compose.dev.yaml up --build database backend
```

### Frontend

The frontend uses `pnpm`.

From `frontend/`:

```powershell
pnpm dev
```

The development server runs through:

```text
vite --host 127.0.0.1
```

Available frontend scripts:

```text
pnpm dev
pnpm build
pnpm preview
pnpm test
```

`pnpm build` runs:

```text
tsc -b && vite build
```

`pnpm test` runs:

```text
vitest run
```

### Backend build and tests

Use the build and test tooling already configured in `backend/`.

Inspect the existing Maven or Gradle configuration before choosing commands. Do not introduce or replace backend build tooling merely to validate a task.

# Validation

Before considering an implementation complete:

1. Build the affected application.
2. Run tests relevant to the change.
3. Validate user-visible behavior when the change affects the user interface.
4. If you believe any additional validation is necessary, ask the user for permission before running it.

Use the smallest reasonable validation scope that provides confidence in the current change.

## Available browser validation

The project may be validated interactively using the browser capabilities available in Codex.

Playwright is also configured in `frontend/` and may be used when an automated browser test is useful, especially when the scenario should be reproducible or protected against future regressions.

Choose the validation method appropriate for the task. Do not use Playwright merely because the change affects the UI.

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

Do not repeatedly retry stalled browser tests, switch to debug modes, restart processes, elevate permissions, or try alternative workarounds without user approval.

## Git branches

Do not create branches with a `codex/` prefix.

Use one of these prefixes according to the type of change:

* `feat/`
* `fix/`
* `hotfix/`
* `build/`
* `chore/`
* `ci/`
* `cd/`
* `docs/`
* `style/`
* `refactor/`
* `perf/`
* `test/`

After the prefix, include the Linear issue identifier and a short description of the change.

Format:

```text
<prefix>/<LINEAR-ISSUE>-<short-description>
```

Examples:

```text
feat/KAM-36-sale-author
fix/KAM-31-remove-app-info-from-login-page
```

When creating a branch:

1. Start from `develop`.
2. Make sure local `develop` is up to date.
3. Create the new branch from the updated `develop`.

The agent may create branches, switch between branches, and update local branches without additional permission.

Do not commit, push, merge, rebase shared history, or create pull requests unless the user explicitly asks.

## Commits

Use Conventional Commits.

Commit messages must be written in Russian.

Include the Linear issue identifier at the end of the commit subject.

Format:

```text
<type>: <описание изменения> (<LINEAR-ISSUE>)
```

Examples:

```text
feat: добавлен автор продажи (KAM-36)
fix: скрыта информация о приложении со страницы входа (KAM-31)
```

Create a commit only when the user explicitly asks for it.

## Linear

When beginning substantive discussion or implementation of a Linear issue, change that issue's status to:

```text
In Progress
```

Do not make any other issue status changes.

Other status transitions are handled automatically from Git history or manually by the user.

Do not modify an issue's title, description, acceptance criteria, comments, priority, labels, assignee, or other fields unless the user explicitly asks.

Reading Linear issues and their existing context is allowed when relevant to the current task.

## API and architecture changes

Ask the user before:

* making a backward-incompatible API contract change;
* performing a large-scale refactoring.

Compatible API changes and ordinary implementation decisions may be made as part of the current task without additional confirmation.

## Documentation

When a change makes existing documentation inaccurate or incomplete, update the relevant documentation as part of the same task.

Keep documentation consistent with the actual behavior of the application.
