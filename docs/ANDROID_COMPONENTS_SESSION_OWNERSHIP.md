# Android Components session ownership cutover

This document defines the live-session ownership boundary while MiniBrowser migrates from direct
`GeckoSession` management to Mozilla Android Components (A-C). The core rule is unchanged: one live
Gecko session has exactly one lifecycle owner at a time. Metadata may be mirrored during migration;
open/close/replace authority may not.

The A-C behavior referenced by the migration code was reviewed against Firefox / Android Components
`154.0.1`.

## Current ownership model

The browser is no longer in the old shadow-only phase. Ordinary fresh tabs are created as A-C-owned
tabs, while legacy/raw tabs can still exist temporarily until their one-way transfer completes.

### Fresh ordinary tabs: A-C-owned from creation

`TabManager.newTab(...)` creates the BrowserStore row first. The corresponding structural `Tab` is
created without a raw `GeckoSession` and records `RawSessionOwnership.Relinquished`.

The selected BrowserStore tab is rendered through the A-C engine-view binding. For a sessionless tab,
A-C dispatches `CreateEngineSessionAction`; `CreateEngineSessionMiddleware` creates the
`EngineSession`, `LinkingMiddleware` links it, and the stock A-C path loads `tab.content.url` after the
link. MiniBrowser must not issue a second eager raw-session load for that tab.

For these tabs BrowserStore/EngineMiddleware owns the live engine session. `TabManager` keeps the
structural tab record and migration-era policy state, but it must not close or replace the underlying
Gecko session.

### Existing raw tabs: explicit one-way ownership transfer

Tabs that already own a raw `GeckoSession` use an explicit transfer boundary rather than dual
ownership. `AndroidComponentsExistingSessionTransferCoordinator`:

1. flushes state that still belongs to the raw path;
2. captures the raw UI/media handoff state needed by BrowserStore;
3. wraps the exact existing Gecko session for A-C ownership rather than creating a replacement;
4. links the A-C engine session and replays the captured BrowserStore state;
5. irreversibly marks raw ownership relinquished before terminal cleanup.

After transfer, rollback to raw lifecycle ownership is not supported. The linked A-C session is the
close/suspend owner.

### Remaining raw compatibility path

Raw-owned tabs can still exist during the migration window. For those tabs the BrowserStore bridge is
metadata/state synchronization, not a second lifecycle owner. The bridge tracks the raw rows that it
actually mirrored and may remove only those missing raw mirrors. It must never delete a BrowserStore
row merely because an older structural snapshot does not contain an A-C-owned tab.

This distinction is required because BrowserStore and the structural tab flow are asynchronous. A
fresh A-C row can legitimately appear in BrowserStore before the latest structural snapshot reaches
the bridge.

### Gecko compatibility delegates

Stock A-C delegates remain authoritative after A-C takes ownership. MiniBrowser installs selective
compatibility wrappers only for callbacks where the A-C adapter loses information required by the
browser policy. The current compatibility installer covers prompt, permission, navigation and content
callbacks; it does not replace the stock media-session delegate.

The app-scoped compatibility registry is leased by the current Activity host so configuration changes
cannot leave a destroyed Activity as the callback target and an older lease cannot clear a newer host.

## A-C lifecycle behavior that constrains the cutover

A linked `EngineSession` is a BrowserStore lifecycle concern:

- `CreateEngineSessionMiddleware` creates the engine session from BrowserStore tab state and dispatches
  `LinkEngineSessionAction`.
- `LinkingMiddleware` links the engine observer and, when restore did not already load content, loads
  the tab URL.
- tab-removal middleware unlinks and closes the linked `EngineSession`.
- suspend middleware unlinks/closes the live session and retains restorable engine state for a later
  create/link cycle.
- unlinking removes the observer relationship; close authority remains with the A-C lifecycle path.
- `GeckoEngineSession` can wrap an existing public `GeckoSession`, which is used only at the explicit
  transfer boundary because its delegates and close semantics are ownership-changing operations.

Therefore a linked session must never remain subject to `TabManager` raw close/replace logic.

Relevant A-C source areas:

- `browser/state/engine/middleware/CreateEngineSessionMiddleware.kt`
- `browser/state/engine/middleware/LinkingMiddleware.kt`
- `browser/state/engine/middleware/TabsRemovedMiddleware.kt`
- `browser/state/engine/middleware/SuspendMiddleware.kt`
- `browser/state/reducer/EngineStateReducer.kt`
- `browser/engine-gecko/GeckoEngineSession.kt`

## State and restore ownership

BrowserStore creation-critical state is populated before A-C creates a session:

- tab id and URL;
- private mode;
- desktop mode;
- selection;
- compatible restorable `EngineSessionState` when available.

MiniBrowser persists raw `GeckoSession.SessionState` and A-C `EngineSessionState` separately during the
migration. The A-C state is stored in a versioned, engine-qualified, URL-bound envelope and is
serialized/deserialized only through the public engine APIs. MiniBrowser does not depend on internal
`GeckoEngineSessionState` representation details.

Process restore now has an A-C-owned path. When BrowserStore is empty and there is no in-process
recreation handoff, the persisted browser state is planned into BrowserStore before `TabManager` is
constructed. A compatible A-C state envelope is bound to the restored tab without decoding the same
opaque state twice.

An in-process Activity recreation handoff remains authoritative when present. In particular, a fresh
sessionless A-C structural tab may be handed off while its queued BrowserStore action has not reduced
yet; process restore must not race that handoff and create a competing owner.

## Rendering

The selected A-C-owned tab renders the linked BrowserStore engine session. The old long-lived
"borrow a raw GeckoSession into a render-only facade" model is not the ownership model for fresh or
transferred A-C tabs.

Raw compatibility rendering may remain only for a tab that is still explicitly raw-owned. It must not
cause a linked BrowserStore session and a raw owner to coexist for the same Gecko session.

## Navigation and external intents

External navigation has one Activity owner. `MainActivity` installs the browser navigation target
before the first launch URI is accepted; Compose no longer installs a second handler.

Before `TabManager` initialization, `onNewIntent()` stores whole intents in the Activity's
`pendingIntents` queue. After initialization, intents are handled synchronously. The navigation
controller therefore does not need a second pending queue; URI validation remains its only policy
responsibility before dispatch to `TabManager.newTab(...)`.

For A-C-owned tabs, navigation/load/back/forward/reload helpers dispatch through BrowserStore/A-C
engine actions or use cases. Direct raw `GeckoSession` operations are valid only while a tab is still
raw-owned.

## Closing, suspend and final Activity teardown

For A-C-owned tabs, BrowserStore removal is the session-lifecycle source. The A-C middleware owns
unlink/close. MiniBrowser may remove the corresponding structural tab record, but must not perform a
second raw close.

For a raw-owned tab, `TabManager` still owns raw close/replace until the explicit transfer boundary.

Final Activity destruction preserves the same rule: persistence happens before terminal owner cleanup;
transferred/A-C-owned sessions are unlinked/removed through the A-C owner, while raw shadows are
cleared without creating a second close path.

## Media and picture-in-picture boundary

Production PiP follows the same state boundary as stock A-C: fullscreen comes from browser content
state and automatic PiP eligibility additionally requires media playback state `PLAYING`; private tabs
remain ineligible.

MiniBrowser does not install a custom replacement for the A-C Gecko media-session delegate. Real Gecko
media instrumentation verifies that a user gesture can start the HTML video, the video emits
`playing`, DOM `navigator.mediaSession.playbackState` can report `playing`, and Gecko enters content
fullscreen. A separate system test verifies eligible browser media state -> real Android system PiP ->
BrowserStore `pictureInPictureEnabled`.

The split is intentional. Current GeckoView has an upstream callback gap in which real DOM media can
be playing while the GeckoSession media-session playback callback is not emitted reliably; Mozilla's
corresponding GeckoView playback callback tests are disabled for that upstream issue. MiniBrowser must
not add a production fallback or duplicate delegate solely to make that external callback deterministic
in emulator CI.

## Selective delegate parity

Most callbacks use stock A-C behavior. The remaining compatibility cases are intentionally narrow:

- **WEEK prompts:** the A-C adapter collapses the raw WEEK input into a normal date-style prompt and
  loses MiniBrowser's ISO week-year semantics.
- **XR permission:** the adapter does not preserve the XR-specific distinction required by the current
  policy/UI.
- **Linked audio/video context menus:** the A-C hit-result conversion can retain media `src` while
  losing the wrapping link URI needed by MiniBrowser actions.

These cases are compatibility policy, not justification for restoring broad raw-session ownership.

Download handling follows the same principle: the A-C external-response path can preserve the existing
authenticated response body, while MiniBrowser keeps only the filename/confirmation policy that is not
provided by the stock flow. Do not introduce a second network request merely to preserve the old raw
implementation.

## Hard invariant

For each tab exactly one live-session mode is allowed:

- **Raw-owned:** `TabManager` may open/close/replace the raw `GeckoSession`; BrowserStore may mirror
  metadata/restorable state but must not own a linked engine session for that tab.
- **A-C-owned:** BrowserStore/EngineMiddleware may create/link/suspend/close its `EngineSession`;
  `TabManager` must not close or replace the underlying Gecko session.

A transfer can move a tab from the first mode to the second exactly once. There is no supported mode
where both retain close authority.

## Migration status and remaining cleanup

Completed ownership checkpoints:

1. versioned A-C engine-state persistence and URL binding;
2. BrowserStore process restore planning;
3. selective compatibility host/registry infrastructure;
4. fresh ordinary tabs created as A-C-owned BrowserStore tabs;
5. selected raw tabs transferable to A-C without replacing GeckoSession identity;
6. popup/new-window raw sessions transferred after Gecko opens the returned session;
7. selected A-C sessions rendered through the BrowserStore engine-view path;
8. BrowserStore-aware navigation and close paths for A-C-owned tabs;
9. bridge removal authority restricted to raw rows the bridge actually mirrors;
10. external-navigation handler ownership moved out of Compose and into `MainActivity`.

Remaining work should reduce migration infrastructure rather than add another owner:

- finish moving any still-raw-only browser operations to BrowserStore/A-C equivalents;
- remove raw lifecycle/delegate code when no production path depends on it;
- remove compatibility bridge pieces as their raw-owned inputs disappear;
- consolidate suspend/hot-tab policy on A-C `SuspendEngineSessionAction` once raw tabs no longer need a
  separate hibernation lifecycle;
- keep selective WEEK/XR/linked-media adapters only while upstream A-C loses the required information;
- update or delete transitional persistence fields after a release-safe migration window proves they
  are no longer required.

Every cleanup should preserve the same testable invariant: BrowserStore and `TabManager` may describe
the same tab during migration, but only one of them may own that tab's live Gecko session lifecycle.
