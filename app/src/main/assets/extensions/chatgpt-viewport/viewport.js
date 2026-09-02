(() => {
  // GeckoView 154 already contains the Android viewport fixes needed by current ChatGPT.
  // Do not force interactive-widget=resizes-content here: doing so shrinks ChatGPT's layout
  // viewport and makes the middle of the page reflow when the software keyboard opens.
  // Keep this built-in shim intentionally inert for one release so existing Gecko profiles can
  // update away from version 1.0.1 without leaving the old compatibility policy installed.
})();
