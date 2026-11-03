## 2026-03-31 - Optimize Job Path Lookup in Dashboard UI State Flow

**Learning:** In reactive Kotlin `combine` pipelines that map collections against state collections (e.g. matching scanned files against active transcode jobs), using `collection.any { it.property == item.property }` inside `map` results in $O(N \times M)$ linear searches on every state emission. Constructing a `HashSet` before iteration reduces lookup to $O(1)$ and overall transformation time complexity to $O(N + M)$.
**Action:** When mapping or filtering collections based on key presence in another list in reactive flows, always precompute a `HashSet` of keys before entering the mapping loop.

## 2026-04-01 - Avoid Blocking Network Interface Enumeration in Reactive UI Pipelines

**Learning:** Invoking OS network interface enumeration (`NetworkInterface.getNetworkInterfaces()`) inside a high-frequency `combine` StateFlow pipeline or within Compose UI rendering blocks blocks execution with kernel socket queries on every transcode statistics update or search keystroke. Reusing pre-enumerated available interfaces (`availableInterfaces.firstOrNull()`) in Composables and caching the interface list with a TTL and status invalidation in ViewModels prevents main-thread jank and eliminates redundant syscalls.
**Action:** Never invoke `NetworkUtils.getActiveNetworkInterface()` or `NetworkUtils.getAvailableNetworkInterfaces()` directly within Compose layout scopes or high-frequency reactive transformation blocks; rely on cached state or upstream flows.
