//#region src/shims/nodeCrypto.ts
var e = globalThis.crypto;
if (!e?.subtle) throw TypeError("Web Crypto API is not available in this environment.");
var t = e.subtle, n = e.getRandomValues.bind(e), r = typeof e.randomUUID == "function" ? e.randomUUID.bind(e) : void 0;
//#endregion
export { e as default, n as getRandomValues, r as randomUUID, t as subtle };
