# Android Components session ownership cutover

This document defines the ownership boundary for the migration from MiniBrowser's raw `GeckoSession`
management to Mozilla Android Components (A-C). It is intentionally narrower than the general browser
migration: the goal is to prevent two owners from opening, closing, restoring, observing, or replacing
the same live Gecko session.

The A-C behavior referenced below is from Firefox / Android Components `154.0.1`.

## Current ownership

`TabManager` is still the live-session owner. It currently:

- constructs every raw `GeckoSession`;
- installs MiniBrowser navigation, progress, content, history, prompt, permission, download, context-menu and media-session delegates before opening/restoring a session;
- opens sessions against the application `GeckoRuntime`;
- closes sessions when tabs are removed, hibernated, browser data is cleared, the host is destroyed, or a crashed session is replaced;
- keeps raw `GeckoSession.SessionState` as the only active restore source;
- preserves a separately persisted, URL-bound A-C `EngineSessionState` envelope without using it to restore the raw session;
- applies the hot-tab budget and manually reopens cold tabs.

`BrowserStore` is shadow state only. The bridge mirrors tab metadata and any compatible persisted
`EngineSessionState`, but `EngineMiddleware` is never given `CreateEngineSessionAction` and the
render-only sidecar is never linked into `BrowserStore`. Therefore the shadow store still owns no live
`EngineSession`.

`GeckoEngineView` currently renders a temporary `GeckoEngineSession` facade around the existing raw
session. The facade is created with `openGeckoSession=false`, all delegates it temporarily installs are
restored, and MiniBrowser never calls `close()` on that borrowed facade.

## A-C lifecycle behavior that constrains the cutover

A-C 154.0.1 makes linked `EngineSession` lifetime an explicit BrowserStore concern:

- `CreateEngineSessionMiddleware` calls `engine.createSession(...)`, applies the tab's `private` and
  `desktopMode` state, restores `engineSessionState` when present, and dispatches
  `LinkEngineSessionAction`.
- `TabsRemovedMiddleware` dispatches `UnlinkEngineSessionAction` and then calls `close()` on the linked
  `EngineSession` whenever a tab is removed. Removing a shadow tab whose `engineSession` is null does
  not close the raw Gecko session.
- `SuspendMiddleware` also unlinks and closes the `EngineSession`; a later
  `CreateEngineSessionAction` creates a new session and restores the saved `engineSessionState`.
- `LinkingMiddleware` unregisters the engine observer on `UnlinkEngineSessionAction`; unlinking by
  itself is not a second close operation.
- `GeckoEngineSession` can be constructed around an existing raw `GeckoSession` through its public
  `geckoSessionProvider` with `openGeckoSession=false`. That is a possible future ownership-transfer
  mechanism without recreating the underlying Gecko session, but construction immediately installs
  A-C's Gecko delegates and `GeckoEngineSession.close()` closes the provided raw session. It therefore
  cannot be used as a long-lived bridge while `TabManager` still owns delegates or close authority.

Therefore a linked session must never remain owned by `TabManager.closeIfOpen()` at the same time.

Relevant A-C sources:

- `browser/state/engine/middleware/CreateEngineSessionMiddleware.kt`
- `browser/state/engine/middleware/TabsRemovedMiddleware.kt`
- `browser/state/engine/middleware/SuspendMiddleware.kt`
- `browser/state/engine/middleware/LinkingMiddleware.kt`
- `browser/state/reducer/EngineStateReducer.kt`
- `browser/engine-gecko/GeckoEngineSession.kt`

## Preconditions

The live cutover must not start until all of these are true.

### 1. BrowserStore has creation-critical state

Before A-C creates a session, BrowserStore must already contain the values used by
`CreateEngineSessionMiddleware`:

- tab id and URL;
- private mode;
- desktop mode;
- selected tab;
- restorable engine-session state when available.

The migration bridge mirrors these creation-critical values while remaining shadow-only. A valid,
URL-bound persisted engine-state envelope is decoded through `Engine.createSessionStateFrom(...)` and
stored on the shadow tab. Because A-C 154 has no nullable `UpdateEngineSessionStateAction`, replacing or
clearing that restore state recreates only the affected shadow tab while it has no linked
`EngineSession`. No `CreateEngineSessionAction` is dispatched by this bridge.

### 2. Raw delegate behavior has an A-C destination or an explicit retained policy

The cutover cannot simply replace `GeckoSession` with `GeckoEngineSession`, because A-C installs the
Gecko delegates that currently belong to MiniBrowser. Every current delegate responsibility needs one
of these outcomes first:

- move to the corresponding A-C feature/delegate;
- keep a small MiniBrowser policy adapter behind that A-C feature;
- remain intentionally custom with a documented parity reason.

The history visit gate is now aligned before cutover: raw Gecko records only non-private, top-level,
non-`VISIT_UNRECOVERABLE_ERROR` visits, matching the filter used by `GeckoEngineSession` before
`HistoryTrackingDelegate.onVisited(...)`.

The download transport seam is also narrower than originally assumed. In A-C 154.0.1,
`GeckoEngineSession.onExternalResponse(...)` wraps the existing authenticated `WebResponse.body` in a
concept-fetch `Response.Body`; it does not issue a second request. `skipConfirmation`, `openInApp` and
private state are forwarded into `EngineSession.Observer.onExternalResource(...)`, then into
`DownloadState`. `AbstractFetchDownloadService` uses that supplied `DownloadState.response` for the
initial transfer and falls back to its HTTP client only for resume/retry/fallback or when no response
is available. MiniBrowser now exposes its filename policy as an A-C `DownloadDelegate` and applies the
same Mozilla `sanitizeFileName()` post-processing on the current raw path, so the final filename is
stable across the future `GeckoEngineSession` boundary. Live `DownloadsFeature`/download-manager
ownership is still deferred until BrowserStore receives real engine download state; its custom
first-party dialog hook can preserve the existing confirmation UI without requiring a FragmentManager.

PiP is now a validated raw baseline rather than an open parity gap. Stable API 36 instrumentation
serves a local HTML5 video through Gecko, observes real media playback/fullscreen state, enters Android
system picture-in-picture, and verifies the corresponding BrowserStore
`content.pictureInPictureEnabled` transition. Live `PictureInPictureFeature` ownership still waits for
the live EngineSession cutover, but there is no remaining synthetic-only PiP validation prerequisite.

Remaining known delegate blockers are the lossy `WEEK` prompt adapter boundary plus prompt host
integration, linked VIDEO/AUDIO context-menu parity, and the final ownership/lifetime boundary itself.
A-C 154 preserves image+link context through `HitResult.IMAGE_SRC`, but its VIDEO/AUDIO hit results keep
the media `src` and discard the wrapping `linkUri`; MiniBrowser currently exposes actions for both
resources.

Do not configure future delegates by blindly replacing the current `GeckoEngine(defaultSettings=null)`
with a generic `DefaultSettings`. In A-C 154, `engine.settings.historyTrackingDelegate` and
`engine.settings.downloadDelegate` setters write only through the nullable `defaultSettings` object,
so they are no-ops while it is null. Supplying `DefaultSettings` also opts future sessions into the
rest of that settings object's defaults. Delegate registration therefore belongs in the explicit
session/default-settings cutover, after the full future settings policy is reviewed.

### 3. Session-state persistence is versioned before ownership moves

This precondition is now implemented without activating A-C session ownership.

MiniBrowser persists raw `GeckoSession.SessionState` and A-C `EngineSessionState` in separate fields.
The A-C payload is stored in a versioned, engine-qualified envelope with an exact URL binding. It is
serialized only through `EngineSessionState.writeTo(...)` and decoded only through
`Engine.createSessionStateFrom(...)`; MiniBrowser does not reach into
`GeckoEngineSessionState.actualState` or depend on the private `GECKO_STATE` JSON key.

Raw-owned `TabManager` preserves a compatible envelope through materialization, normal persistence and
closed-tab restore, drops stale/unbound state when navigation invalidates its URL binding, and clears
both raw and A-C opaque snapshots when credential sanitization changes persisted metadata. The decoded
A-C state may now be mirrored into shadow BrowserStore state, but raw `GeckoSession.SessionState`
remains the only active restore path until the ownership cutover.

### 4. Render binding no longer borrows raw sessions

The render-only `GeckoEngineSession` sidecar is a transition tool, not the owned-session model. Before
A-C becomes session owner, rendering must consume the `EngineSession` linked to the selected
BrowserStore tab (for example through the A-C session/engine-view presenter path). Once that happens,
the borrowed sidecar and delegate snapshot/restore logic can be removed.

## Cutover sequence

The ownership change should be one directional boundary, not a long-lived mixed mode.

1. **Persist and hand off A-C-compatible state — complete pre-cutover.** Versioned
   `EngineSessionState` round-trips beside the legacy raw Gecko payload, survives the raw-owned tab
   lifecycle while URL-bound, and can populate shadow BrowserStore state without creating a session.
2. **Finish delegate/feature seams.** History filtering, PiP baseline validation, authenticated
   initial-download body handoff and final filename normalization are aligned. Resolve or explicitly
   retain the remaining prompt, linked VIDEO/AUDIO context-menu, download ownership/resume policy and
   permission/navigation seams before A-C installs live delegates.
3. **Make BrowserStore the tab/session creation source.** Restore tab metadata into BrowserStore and
   let `CreateEngineSessionAction` create only the sessions that need to be hot. If preserving a live
   raw Gecko session across this boundary is preferable, use an explicit one-time
   `GeckoEngineSession(geckoSessionProvider = ...)` ownership transfer instead of allowing both owners
   to coexist; delegate and close authority must move in the same boundary.
4. **Switch rendering to the linked EngineSession.** Remove the borrowed raw-session sidecar from the
   selected-tab render path.
5. **Switch navigation/reload/back/forward/desktop operations to A-C use cases/actions.** After this
   point raw `GeckoSession` navigation calls must not be used for BrowserStore-owned tabs.
6. **Switch hibernation to `SuspendEngineSessionAction`.** Keep MiniBrowser's hot-tab selection policy
   if desired, but let A-C perform unlink/close/state restore.
7. **Switch tab removal to BrowserStore actions.** `TabsRemovedMiddleware` becomes the only close owner;
   remove the matching raw `closeIfOpen()` path for BrowserStore-owned tabs.
8. **Switch crash recovery to A-C engine state.** Stop replacing `Tab.session` manually once A-C owns
   creation and restore.
9. **Delete raw session ownership infrastructure.** Remove the obsolete session field/lifecycle paths,
   sidecar helper, duplicate delegate wiring and shadow-state bridge pieces that are no longer needed.

## Hard invariant during implementation

For any tab, exactly one of these modes is allowed:

- **Raw-owned:** `TabManager` may open/close/replace its raw `GeckoSession`; BrowserStore may hold
  metadata and a restorable `EngineSessionState`, but must not hold an owned linked `EngineSession` for
  that tab.
- **A-C-owned:** BrowserStore/EngineMiddleware may create/link/suspend/close its `EngineSession`;
  `TabManager` must not close or replace the underlying Gecko session.

There is no supported third mode where a borrowed raw session is linked into BrowserStore while both
`TabManager` and A-C retain close authority.

## Prompt-specific parity before PromptFeature

The current prompt seam remains intentionally non-live:

- MiniBrowser's production and fallback file pickers now share the same engine-neutral MIME
  normalization policy that can be reused from `PromptRequest.File`.
- popup target filtering already lives outside raw prompt UI plumbing in navigation policy.
- current HTML date/time formatting, including the ISO week-based year for `WEEK`, is locked by unit
  tests.
- A-C's Gecko prompt adapter accepts raw Gecko `WEEK` prompts, but it parses them with
  `SimpleDateFormat("yyyy-'W'ww", Locale.ROOT)` and emits a normal
  `PromptRequest.TimeSelection.Type.DATE`. The original HTML `WEEK` type is therefore lost at the
  engine-adapter boundary before BrowserStore or `PromptFeature` sees the request.
- `PromptFeature` maps `TimeSelection.Type.DATE` directly to its date picker and has no public
  date/time renderer or prompt-filter hook that can distinguish the original `WEEK` request. A
  downstream MiniBrowser override cannot safely replace only WEEK while leaving ordinary DATE prompts
  under stock `PromptFeature`.
- the formatter semantics are also different. MiniBrowser uses `WeekFields.ISO`, while A-C's
  `SimpleDateFormat` pattern uses calendar year `yyyy` plus locale calendar week rules. For example,
  MiniBrowser intentionally formats 2021-01-01 as `2020-W53`, whereas that A-C formatter produces
  `2021-W01`. Default/min/max and confirmation behavior around ISO week-year boundaries must be
  bridged, retained on a custom prompt path, or explicitly accepted before `PromptFeature` owns these
  prompts.
- current upstream Firefox/Android Components still has this `WEEK` adapter shape, so a dependency
  bump alone is not a known resolution.
- `PromptFeature` uses a `FragmentManager`, while MiniBrowser currently hosts Compose in
  `ComponentActivity`; the host integration must be chosen deliberately rather than changing the
  Activity base class as an incidental side effect of the session cutover.
