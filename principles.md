# Coding principles

- Make the code say what the system is doing. A reader should not need a comment to discover the important behavior.
- Keep domain behavior visible. Do not hide meaningful actions behind vague, generalized operations.
- Name state transitions after the exact domain change they perform. Prefer a specific transition over a generic update.
- Introduce abstractions only when the shared idea is genuinely independent of the domain. Similar-looking code is not enough reason to generalize it.
- Scope each interface to one kind of consumer. A type may implement several interfaces, but callers should only see the operations relevant to them.
- Keep repository operations small, explicit, and easy to verify. Query names and inputs should make their intent clear without a separate explanation.
- Use comments to record constraints that cannot be expressed in code. Do not use comments to compensate for unclear names, mixed responsibilities, or complex control flow.
- Keep familiar terminology when it is already precise. Rename something only when the new name adds material clarity.
- Prefer direct composition of simple operations over a single method that combines unrelated responsibilities.
- Keep design proportional to the problem. Add structure when it clarifies behavior, not merely to make the code look more abstract or uniform.
- Use configuration only for secrets or values that genuinely vary between environments. Keep stable values in code, preferably as constants.
- Never assume an internal infrastructure artifact exists, including container image tags, deployment resources, topics, subscriptions, service accounts, or permissions. Verify it against the owning system before referencing or changing it; if it cannot be verified, stop and state the dependency explicitly.
