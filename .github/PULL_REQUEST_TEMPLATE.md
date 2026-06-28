## Summary

<!-- What does this PR do? If Android-first: link to issue or user story. -->

## Area

- [ ] Android (`android/`)
- [ ] Supabase (`supabase/`)
- [ ] Governance / docs only
- [ ] Web (`app/`, `components/`, `lib/`, `types/`) — requires **`android-approved`** label

> Web product changes are frozen. See [ANDROID_FIRST.md](../ANDROID_FIRST.md).

## Supabase / schema checklist

Fill this section if the PR touches migrations, RPCs, or persisted string formats:

- [ ] N/A — no schema or wire-format changes
- [ ] Migration added under `supabase/migrations/`
- [ ] [supabase-contract.md](../android/docs/supabase-contract.md) updated
- [ ] Android `fromPersisted()` / mappers updated (no bare `valueOf` on synced data)
- [ ] Unit tests added or updated (`./gradlew testDebugUnitTest`)

## Testing

- [ ] `./gradlew testDebugUnitTest` (Android changes)
- [ ] Manual test on device/emulator (UI changes)
- [ ] N/A

## Screenshots / recordings

<!-- For UI changes -->
