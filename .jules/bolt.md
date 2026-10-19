## 2026-03-31 - Optimize Job Path Lookup in Dashboard UI State Flow

**Learning:** In reactive Kotlin `combine` pipelines that map collections against state collections (e.g. matching scanned files against active transcode jobs), using `collection.any { it.property == item.property }` inside `map` results in $O(N \times M)$ linear searches on every state emission. Constructing a `HashSet` before iteration reduces lookup to $O(1)$ and overall transformation time complexity to $O(N + M)$.
**Action:** When mapping or filtering collections based on key presence in another list in reactive flows, always precompute a `HashSet` of keys before entering the mapping loop.
