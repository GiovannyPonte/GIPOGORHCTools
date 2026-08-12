# Repository working agreements

- This is an Android application built with Kotlin and Jetpack Compose.
- Do not modify generated build output, local IDE state, or machine-specific configuration.
- Never add real patient data, credentials, signing keys, `local.properties`, logs, or diagnostic dumps to Git.
- Keep Room schema exports in `app/schemas/` versioned and update them when database migrations change.
- Run the relevant unit or instrumentation tests after changing behavior.
- Preserve existing user changes and keep unrelated work out of each commit.
