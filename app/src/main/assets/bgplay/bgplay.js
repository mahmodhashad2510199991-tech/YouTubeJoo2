"use strict";
// Same idea as Firefox's own webcompat fix for YouTube background audio:
// make the page believe it is always visible so YouTube never pauses itself.
const pageDocument = document.wrappedJSObject;
if (!pageDocument.__jooBackgroundPlay) {
  Object.defineProperty(pageDocument, "__jooBackgroundPlay", { value: true });
  Object.defineProperties(pageDocument, {
    hidden: { value: false },
    visibilityState: { value: "visible" },
  });
  const stop = event => event.stopImmediatePropagation();
  window.addEventListener("visibilitychange", stop, true);
  document.addEventListener("visibilitychange", stop, true);
  // Avoid the "Video paused. Continue watching?" idle prompt during long background playback.
  window.setInterval(() => {
    if (!document.hidden) return;
    for (const type of ["keydown", "keyup"]) {
      document.dispatchEvent(new KeyboardEvent(type, { bubbles: true, cancelable: true, key: "Alt", code: "AltLeft" }));
    }
  }, 60000);
}
