# Contributing to Flavors

Thanks for working on Flavors. This page is the short version of how we ship changes. The detail lives in
[docs/README.md](docs/README.md) (setup, branches, tests) and [docs/Architecture.md](docs/Architecture.md)
(the agreed design). Read both before your first PR.

## Before you start

- Work on one Jira story at a time. Create your branch from `develop` and name it `EEC-NN-short-name`
  (lowercase words joined by hyphens, e.g. `EEC-31-creator-registration`).
- Follow [docs/Architecture.md](docs/Architecture.md); don't re-decide what it settles. If you think it is
  wrong, raise it with the team first.

## Workflow

```
main <-- release/sprint-N <-- develop <-- EEC-NN-short-name
```

- **Never push to `main` or `develop`.** Both are protected: every change goes through a pull request.
- **Commits:** `EEC-NN: <imperative summary>`, with a short body that explains *why*. One logical step per
  commit, each one building and passing its own tests.
- **PR title:** `EEC-NN: short summary`. Target `develop` (release PRs target `main`).
- **Keep your branch current:** merge or rebase onto the latest `develop` before opening the PR.
- Keep PRs small and focused on the story. Ask before adding anything beyond it.

## Before you open a PR

Run what CI runs, so the PR is green the first time:

```bash
cd backend  && ./mvnw verify                      # needs Docker (Testcontainers)
cd frontend && npm run lint && npm run format:check && npm test && npm run build
```

Also:

- **Migrations:** never edit a merged migration; add a new `V<n>__<what>.sql`, and claim the number in the
  team chat first (two branches with the same version break Flyway for whoever merges second).
- **Comments:** add short comments that explain *why* on the non-obvious parts (security rules, triggers,
  idempotency, config), not what the code obviously does.
- **No secrets** (tokens, passwords, `.env` files) in code, config or screenshots. If one leaks, tell the team
  straight away so it can be rotated.

## Opening a pull request

Fill in the template; GitHub pre-fills it from
[.github/pull_request_template.md](.github/pull_request_template.md). Use it as written:

````markdown
## Jira

EEC-<!-- number --> · <!-- link to the issue -->

## What and why

<!-- What does this PR change, and why? One short paragraph or a few bullets. -->

## Files to read first

<!-- Guide the reviewer: list the 2–5 files that explain the change, in reading order. -->

1.
2.

## How to test

<!-- Steps a reviewer can follow locally, and which automated tests cover it. -->

## Checklist

- [ ] Branch is `EEC-NN-short-name` and targets `develop` (or `main` for a release PR)
- [ ] Tests added or updated for the change; CI is green
- [ ] Database migrations (if any) reviewed; no edits to migrations that are already merged
- [ ] No secrets, passwords or tokens in the code or config
- [ ] README / docs updated if setup or behaviour changed
````

For UI changes, add a screenshot or short recording to the PR.

## Reviewers

- **@Michouli is a required reviewer on every PR.** Add them under *Reviewers* when you open the PR; it
  won't be merged without their review.
- **@kaaek is an optional reviewer.** Add them if you want another pair of eyes, or when the change touches
  architecture, security, the database schema or CI/CD.
- A PR needs **1 approval** and green CI (`backend`, `frontend`, `docker`). New pushes dismiss earlier
  approvals, so re-request review after you push changes.
- Don't merge your own PR before it has been approved.

## Reviewing a PR

- Start from the "Files to read first" list in the description, then read the tests.
- Check the template checklist, that the change stays within the story, that tests cover it, and that the
  rules in [docs/Architecture.md](docs/Architecture.md) hold (security, audit log, idempotency, money in cents).
- Be specific and kind: say what to change and why. Reply to or resolve every comment before merging.
- Aim to respond within a working day; if you can't, say so on the PR so the author isn't left waiting.

## Definition of done

- [ ] Acceptance criteria of the Jira story are met
- [ ] Tests added or updated; CI is green
- [ ] Docs updated if setup or behaviour changed
- [ ] Approved by @Michouli, all comments resolved
- [ ] Merged into `develop`, branch deleted, Jira issue moved on
