# Changelog

## 0.8.3

- Increased the page bitmap cache budget to 25% of the Java heap limit.
- Reduced adjacent page preloading from 3 pages to 1 page while zoomed.
- Reduced zoom-time cache churn and unnecessary render/evict cycles.

## 0.8.0

- Added public viewer configuration and state APIs.
- Added the Jetpack Compose adapter module.
- Added Maven publication metadata for core and Compose artifacts.
- Added optional in-memory PGP signing for CI publishing.
- Added CI build/test workflow.
- Added tag-driven publishing workflow.
- Added Apache License 2.0.
