# LPPL PMS Native Android

Native Kotlin + Jetpack Compose rebuild of LPPL PMS using the AI Studio mobile UI direction.

## Architecture
Android app -> `Mobile_API.gs` in the existing LPPL Apps Script project -> existing PMS functions -> existing `PMS_DATABASE`.

No WebView. No mock database. No Firebase.

## One-time backend step
1. Open the existing LPPL PMS Apps Script project.
2. Add `apps-script/Mobile_API.gs` as a new file named `Mobile_API`.
3. Deploy a new version of the existing Web App.
4. Keep the deployment URL in `app/build.gradle.kts` as `BASE_API_URL`.

## Connected in this first native build
- Existing Employee ID/password auth
- Existing session validation
- Dashboard
- My Tasks / direct-report Team Tasks
- Compact mobile task rows
- Today / Upcoming / Overdue / Not Done / On Leave / Completed
- Mark Done through existing `api_completeTask`
- After Done, user remains on Today
- My / Team Help Tickets
- Compact ticket rows
- Filters collapsed by default
- New Ticket FAB shell
- Notifications
- Role-aware navigation
- LPPL PMS launcher artwork
- Android status bar does not overlap content

The backend bridge reuses the current LPPL business rules and same Google Sheet database.
