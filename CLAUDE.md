# DnDPartyFinder

## Project context
- In-person D&D 5e party finder. GMs post tables, players apply, GMs accept or reject applicants up to the table's seat limit.
- Stack: Java + Spring Boot, PostgreSQL, Flyway, Testcontainers, GitHub Actions CI, deploying to Render.
- This is a portfolio project. I own the codebase and must be able to explain every line.
- Environment: Windows. Use `.\mvnw.cmd`, not `./mvnw`.

## Commands
- Unit tests only: `.\mvnw.cmd test`
- Full build incl. integration tests: `.\mvnw.cmd verify`

## Working rules
- Always propose a plan before editing any files. Wait for my approval.
- Make one small change at a time.
- After each change, explain what it does and why, then ask me one question to check my understanding before continuing.
- If there are several reasonable approaches, briefly compare them and let me choose.
- I'm new to CI/CD: explain new concepts in plain terms when they first come up.

## Boundaries
- Never commit, push or open PRs. When a change is ready, tell me and suggest a commit message.
- Never modify branch protection, secrets or deployment settings. Explain what I need to change instead.
- Don't add dependencies without explaining why and getting my approval.

## Conventions
- TDD: write the failing test before the implementation.
- Unit tests end in `Test`, use no Spring context and run fast.
- Integration tests end in `IT` and use Testcontainers PostgreSQL.
- Schema changes go through Flyway migrations only; never edit an applied migration.
- Leave `TODO(human)` for key business logic (e.g. seat-limit rules) so I implement it myself.

## Scope
- Keep it simple. In scope: seat limits (optimistic locking), distance search, pagination, Actuator health checks, rate limiting (Bucket4j, in-memory).
- Out of scope unless I ask: Redis, caching, nginx/load balancing, idempotency keys.