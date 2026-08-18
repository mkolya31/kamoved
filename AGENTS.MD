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

## Validation

Before considering an implementation complete:

1. Build the affected application.
2. Run tests relevant to the change.
3. For user-interface behavior that requires browser validation, use Playwright.
4. If you believe any additional validation beyond the checks above is necessary, ask the user for permission before running it.

Playwright is the standard tool for browser-level validation in this project.

Prefer the smallest relevant set of tests instead of running unrelated test suites.

If Playwright or another expected validation tool is not yet configured correctly, report that limitation instead of spending significant time on ad-hoc installation attempts or complicated workarounds.

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
