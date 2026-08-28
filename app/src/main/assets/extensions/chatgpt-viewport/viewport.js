(() => {
  const DIRECTIVE = "interactive-widget=resizes-content";
  const DIRECTIVE_PATTERN = /^interactive-widget\s*=/i;

  function enforceViewportPolicy() {
    // Gecko currently behaves most reliably when the page's original viewport meta
    // is modified in place. Do not append a second viewport meta tag.
    const viewport = document.querySelector('meta[name="viewport" i]');
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

  enforceViewportPolicy();

  // ChatGPT is an SPA and may update document metadata after startup. Re-apply only
  // when its existing viewport meta appears or changes; all other sites are excluded
  // by the extension manifest.
  const observer = new MutationObserver(enforceViewportPolicy);
  observer.observe(document, {
    subtree: true,
    childList: true,
    attributes: true,
    attributeFilter: ["content", "name"],
  });
})();
