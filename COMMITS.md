# Commit style

```text
type(scope): imperative summary

- bullet per notable change, ordered roughly by importance
- keep each bullet to one line where possible; wrap near 72 characters
- state what changed and, if not obvious, why
```

- `type` is one of `feat`, `fix`, `test`, `ci`, `chore`, `docs`, `refactor`.
- `scope` is the feature/module touched (e.g. `chat`, `edgar`, `frontend`,
  `make`), not the file name.
- Always include a bulleted body — a bare subject line is not enough
  unless the change is genuinely one line.
- One commit per logical change. Split unrelated changes (e.g. a new
  feature vs. its CI wiring vs. docs) into separate commits even if
  they landed in the same session.
- Do not commit local/machine-specific tool config (editor or agent
  settings, MCP configs pointing at localhost, etc.) unless asked.
