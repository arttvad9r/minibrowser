(() => {
  const DIRECTIVE = "interactive-widget=resizes-content";
  const DIRECTIVE_PATTERN = /^interactive-widget\s*=/i;
  const SYNTHETIC_ATTRIBUTE = "data-minibrowser-viewport";

  let observedHead = null;
  let headObserver = null;
  let applying = false;

  function withDirective(content) {
    const parts = (content || "")
      .split(",")
      .map((part) => part.trim())
      .filter(Boolean)
      .filter((part) => !DIRECTIVE_PATTERN.test(part));

    parts.push(DIRECTIVE);
    return parts.join(", ");
  }

  function enforceViewportPolicy() {
    if (applying) return;
    const head = document.head;
    if (!head) return;

    applying = true;
    try {
      let viewports = Array.from(head.querySelectorAll('meta[name="viewport" i]'));
      const authored = viewports.filter(
        (viewport) => !viewport.hasAttribute(SYNTHETIC_ATTRIBUTE),
      );

      // Authenticated ChatGPT can rebuild its document head during SPA/auth transitions. Gecko may
      // decide keyboard viewport behavior before ChatGPT appends its own viewport tag, so keep a
      // temporary canonical viewport present from the first available <head>.
      if (authored.length === 0) {
        let synthetic = viewports.find((viewport) =>
          viewport.hasAttribute(SYNTHETIC_ATTRIBUTE),
        );
        if (!synthetic) {
          synthetic = document.createElement("meta");
          synthetic.name = "viewport";
          synthetic.setAttribute(SYNTHETIC_ATTRIBUTE, "true");
          synthetic.content = `width=device-width, initial-scale=1, ${DIRECTIVE}`;
          head.prepend(synthetic);
        } else {
          const next = withDirective(synthetic.getAttribute("content"));
          if (synthetic.getAttribute("content") !== next) {
            synthetic.setAttribute("content", next);
          }
        }
        return;
      }

      // Once the site's real viewport exists, remove our bootstrap tag and modify every authored
      // viewport in place. Handling all tags avoids Gecko falling back to a later replacement tag.
      viewports
        .filter((viewport) => viewport.hasAttribute(SYNTHETIC_ATTRIBUTE))
        .forEach((viewport) => viewport.remove());

      authored.forEach((viewport) => {
        const next = withDirective(viewport.getAttribute("content"));
        if (viewport.getAttribute("content") !== next) {
          viewport.setAttribute("content", next);
        }
      });
    } finally {
      applying = false;
    }
  }

  function bindCurrentHead() {
    const head = document.head;
    if (!head) return;

    if (head !== observedHead) {
      headObserver?.disconnect();
      observedHead = head;
      headObserver = new MutationObserver(enforceViewportPolicy);
      headObserver.observe(head, {
        subtree: true,
        childList: true,
        attributes: true,
        attributeFilter: ["content", "name"],
      });
    }

    enforceViewportPolicy();
  }

  // Keep watching the document for the lifetime of the SPA. A one-time observer bound to the first
  // <head> silently stops helping if the authenticated app replaces that node after sign-in.
  const documentObserver = new MutationObserver(bindCurrentHead);
  documentObserver.observe(document.documentElement || document, {
    subtree: true,
    childList: true,
  });

  bindCurrentHead();
})();
