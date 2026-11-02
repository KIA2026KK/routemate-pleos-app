# RouteMate Android

- Use feature-oriented DDD packages: destination, voice and room are separate contexts.
- Within each context use domain, application, infrastructure and presentation.
- Domain is pure Kotlin; it must not import Android, Compose, HTTP or vendor SDK types.
- Application orchestrates use cases via domain ports. Infrastructure implements those ports.
- Presentation uses ViewModel/StateFlow and application use cases. Wire adapters at the app entry point.
- Do not introduce empty aggregates, repositories or extra modules without domain needs.
- Create each API/feature branch from an up-to-date develop, never directly from main.
- Submit feature PRs to develop. Promote develop to main by a separate PR only after checks pass.
- Never push features directly to develop/main or merge failed checks. Do not bypass branch rules.
- Verify official SDK APIs; never fabricate Pleos calls. Do not embed LiveKit API secrets.
- Clearly distinguish mocks from real navigation and real voice calls.
- Report actual build/device test results. No chat, friends or AI features in this MVP.
