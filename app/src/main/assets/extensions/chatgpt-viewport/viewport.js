(() => {
  const DIRECTIVE = "interactive-widget=resizes-content";
  const DIRECTIVE_PATTERN = /^interactive-widget\s*=/i;

  function enforceViewportPolicy() {
    // Gecko currently behaves most reliably when the page's original viewport meta
    // is modified in place. Do not append a second viewport meta tag.
    const viewport = document.head?.querySelector('meta[name="viewport" i]');
    if (!viewport) return;

    const parts = (viewport.getAttribute("content") || "")
      .split(",")
      .map((part) => part.trim())
      .filter(Boolean)
      .filter((part) => !DIRECTIVE_PATTERN.test(part));

    parts.push(DIRECTIVE);
    const next = parts.join(", ");
    if (viewport.getAttribute("content") !== next) {
      viewport.setAttribute("content", next);
    }
  }

  function observeHead() {
    const head = document.head;
    if (!head) return false;

    enforceViewportPolicy();
    const observer = new MutationObserver(enforceViewportPolicy);
    observer.observe(head, {
      subtree: true,
      childList: true,
      attributes: true,
      attributeFilter: ["content", "name"],
    });
    return true;
  }

  if (!observeHead()) {
    // document_start can run before <head> exists. Watch only until it appears,
    // then move observation into <head> so normal ChatGPT body updates/streaming
    // do not trigger this compatibility hook.
    const bootstrapObserver = new MutationObserver(() => {
      if (observeHead()) bootstrapObserver.disconnect();
    });
    bootstrapObserver.observe(document, { subtree: true, childList: true });
  }
})();
