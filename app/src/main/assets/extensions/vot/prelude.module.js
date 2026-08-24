var e = {
	log: (...e) => {
		console.log("%c[VOT DEBUG]", "background: #3700ffff; color: #fff; padding: 5px;", ...e);
	},
	warn: (...e) => {
		console.warn("%c[VOT DEBUG]", "background: #e1ff00ff; color: #fff; padding: 5px;", ...e);
	},
	error: (...e) => {
		console.error("%c[VOT DEBUG]", "background: #F2452D; color: #fff; padding: 5px;", ...e);
	}
};
//#endregion
//#region src/utils/errors.ts
function t(e) {
	let t = /* @__PURE__ */ new WeakSet();
	try {
		return JSON.stringify(e, (e, n) => typeof n != "object" || !n ? n : t.has(n) ? "[Circular]" : (t.add(n), n)) ?? null;
	} catch {
		return null;
	}
}
function n(e) {
	let t = [
		e?.data?.message,
		e?.error?.message,
		e?.message
	];
	for (let e of t) if (typeof e == "string" && e) return e;
	return null;
}
function r(e, r) {
	let i = n(e);
	if (i) return i;
	let a = t(e);
	if (a && a !== "{}") return a;
	let o = e.constructor?.name;
	return o ? `[${o}]` : r;
}
function i(e, t) {
	return typeof e == "number" || typeof e == "boolean" || typeof e == "bigint" ? `${e}` : typeof e == "symbol" ? e.description ? `Symbol(${e.description})` : "Symbol" : typeof e == "function" ? e.name ? `[Function ${e.name}]` : "[Function]" : t;
}
function a(e, t = "Unknown error") {
	return e instanceof Error ? e.message || t : typeof e == "string" ? e || t : e == null ? t : typeof e == "object" ? r(e, t) : i(e, t);
}
typeof Uint8Array.prototype.toBase64 == "function" && Uint8Array.prototype.toBase64;
var o = Uint8Array.fromBase64, s = /[-_]/;
function c(e) {
	let t = String(e).replaceAll(/\s+/g, ""), n = t.length % 4;
	if (n === 1) throw TypeError("Invalid base64 input.");
	return n === 0 ? t : t + "=".repeat(4 - n);
}
function ee(e) {
	let t = c(e), n = t.replaceAll("-", "+").replaceAll("_", "/");
	if (typeof o == "function") {
		let e = s.test(t);
		return o(e ? n : t, e ? { alphabet: "base64" } : void 0);
	}
	let r = globalThis.atob;
	if (typeof r != "function") throw TypeError("Base64 decoder is not available in this environment.");
	let i = r(n), a = new Uint8Array(i.length);
	for (let e = 0; e < i.length; e += 1) a[e] = i.charCodeAt(e);
	return a;
}
var l = 1e7, u = 12, te = 2;
function d(e) {
	return typeof e == "object" && !!e;
}
function ne(e) {
	return f(e) === "[object Object]";
}
function f(e) {
	try {
		return Object.prototype.toString.call(e);
	} catch {
		return null;
	}
}
function p(e) {
	return f(e) === "[object ArrayBuffer]";
}
function m(e) {
	try {
		let t = e?.constructor?.name;
		return typeof t == "string" ? t : null;
	} catch {
		return null;
	}
}
function h(e, t) {
	try {
		let n = e?.[t];
		return typeof n == "string" ? n : null;
	} catch {
		return null;
	}
}
function re(e) {
	if (f(e) === "[object Blob]") return !0;
	if (!e || typeof e != "object") return !1;
	try {
		let t = e, n = typeof t.arrayBuffer == "function", r = typeof t.slice == "function", i = typeof t.size == "number", a = typeof t.type == "string";
		return (n || r) && i && a;
	} catch {
		return !1;
	}
}
function g(e) {
	if (typeof e != "number" || !Number.isFinite(e)) return null;
	let t = Math.trunc(e);
	return t < 0 || t > l ? null : t;
}
function _(e) {
	return typeof e != "number" || !Number.isFinite(e) ? null : Math.trunc(e) & 255;
}
function v(e) {
	let t = e.length;
	if (t > l) return null;
	let n = new Uint8Array(t);
	for (let r = 0; r < t; r += 1) {
		let t = _(e[r]);
		if (t === null) return null;
		n[r] = t;
	}
	return n;
}
function ie(e) {
	return new Uint8Array(e.buffer, e.byteOffset, e.byteLength);
}
function ae(e) {
	return ie(e).slice();
}
function oe(e) {
	if (e === "") return null;
	let t = Number(e);
	return !Number.isFinite(t) || !Number.isInteger(t) || t < 0 ? null : t;
}
function se(e) {
	if (!d(e)) return !1;
	let t = e;
	return t.__votExtBody === !0 && typeof t.b64 == "string";
}
function y(e, t = 0) {
	if (t > te || !d(e)) return null;
	let n = e;
	if (p(e)) return new Uint8Array(e);
	try {
		if (ArrayBuffer.isView(e)) return ae(e);
	} catch {}
	return Array.isArray(e) ? v(e) : ce(n) || le(n) || b(n, e, t) || ue(n) || (ne(e) ? de(n) : null);
}
function ce(e) {
	let t = [
		e.type === "Buffer" && Array.isArray(e.data) ? e.data : null,
		Array.isArray(e.data) ? e.data : null,
		Array.isArray(e.bytes) ? e.bytes : null
	];
	for (let e of t) {
		if (!e) continue;
		let t = v(e);
		if (t) return t;
	}
	return null;
}
function le(e) {
	let t = [e.b64, e.base64];
	for (let e of t) if (typeof e == "string") try {
		return ee(e);
	} catch {}
	return null;
}
function b(e, t, n) {
	let r = g(e.byteLength), i = g(e.byteOffset ?? 0);
	if (r === null || i === null) return null;
	let a = e.buffer;
	if (p(a)) try {
		return new Uint8Array(a, i, r).slice();
	} catch {}
	if (!a || a === t) return null;
	let o = y(a, n + 1);
	if (!o || i > o.byteLength) return null;
	let s = Math.min(o.byteLength, i + r);
	return o.slice(i, s);
}
function ue(e) {
	let t = g(e.length);
	if (t === null) return null;
	let n = e, r = new Uint8Array(t);
	for (let e = 0; e < t; e += 1) {
		let t = _(n[e]);
		if (t === null) return null;
		r[e] = t;
	}
	return r;
}
function de(e) {
	let t = Object.keys(e);
	if (!t.length) return null;
	let n = Array(t.length), r = -1;
	for (let e = 0; e < t.length; e += 1) {
		let i = oe(t[e]);
		if (i === null || i > l) return null;
		n[e] = i, i > r && (r = i);
	}
	let i = new Uint8Array(r + 1);
	for (let r = 0; r < t.length; r += 1) {
		let a = _(e[t[r]]);
		if (a === null) return null;
		i[n[r]] = a;
	}
	return i;
}
function x(e) {
	let t = e === null ? "null" : typeof e, n = f(e), r = m(e), i = fe(e, t, n, r);
	if (i) return i;
	let a = pe(e, t, n, r);
	if (a) return a;
	let o = me(e, t, n, r);
	if (o) return o;
	let s = y(e);
	if (s) return {
		kind: "coerced-bytes",
		jsType: t,
		tag: n,
		ctor: r,
		byteLength: s.byteLength
	};
	if (d(e)) {
		let i = Object.keys(e);
		return {
			kind: "object",
			jsType: t,
			tag: n,
			ctor: r,
			keyCount: i.length,
			keys: i.slice(0, u)
		};
	}
	return {
		kind: "primitive",
		jsType: t,
		tag: n,
		ctor: r
	};
}
function fe(e, t, n, r) {
	return e == null ? {
		kind: "empty",
		jsType: t,
		tag: n,
		ctor: r
	} : typeof e == "string" ? {
		kind: "string",
		jsType: t,
		tag: n,
		ctor: r,
		textLength: e.length
	} : null;
}
function pe(e, t, n, r) {
	return se(e) ? {
		kind: `serialized:${typeof e.kind == "string" && e.kind ? e.kind : "bytes"}`,
		jsType: t,
		tag: n,
		ctor: r,
		base64Length: e.b64.length,
		mime: h(e, "mime")
	} : null;
}
function me(e, t, n, r) {
	if (p(e)) return {
		kind: "ArrayBuffer",
		jsType: t,
		tag: n,
		ctor: r,
		byteLength: e.byteLength
	};
	try {
		if (ArrayBuffer.isView(e)) return {
			kind: r ? `TypedArray:${r}` : "TypedArray",
			jsType: t,
			tag: n,
			ctor: r,
			byteLength: e.byteLength
		};
	} catch {}
	return re(e) ? {
		kind: "BlobLike",
		jsType: t,
		tag: n,
		ctor: r,
		byteLength: typeof e.size == "number" ? Number(e.size) : -1,
		mime: h(e, "type")
	} : null;
}
//#endregion
//#region src/extension/shared/constants.ts
var he = "__VOT_EXT_BRIDGE__", S = "VOT_EXT_REQ", ge = "VOT_EXT_RES", C = "VOT_EXT_XHR_START", w = "VOT_EXT_XHR_ABORT", T = "VOT_EXT_XHR_ACK", E = "VOT_EXT_XHR_EVENT", D = "VOT_EXT_NOTIFY", O = /* @__PURE__ */ new Set([
	S,
	ge,
	D,
	C,
	w,
	T,
	E
]);
function k(e) {
	return !!(e && typeof e == "object" && e.__VOT_EXT_BRIDGE__ === !0 && typeof e.type == "string" && O.has(e.type));
}
//#endregion
//#region src/extension/shared/transport.ts
function A(e) {
	return {
		...e,
		[he]: !0
	};
}
function j() {
	let e = globalThis.location?.origin;
	return !e || e === "null" ? "*" : e;
}
function M(e) {
	if (e.source !== globalThis.window) return !1;
	let t = globalThis.location?.origin;
	return (!t || t === "null") && e.origin === "null" || e.origin === t;
}
//#endregion
//#region src/extension/shared/utils.ts
function N(e, t) {
	try {
		typeof e == "function" && e(t);
	} catch (e) {
		console.error("[VOT Extension] GM_xmlhttpRequest callback error", e);
	}
}
//#endregion
//#region src/extension/prelude/gm-polyfills.ts
var P = 1e3, F = 15e3, I = typeof crypto < "u" && "randomUUID" in crypto ? crypto.randomUUID() : `${Date.now().toString(36)}_${Math.random().toString(36).slice(2)}`;
function L(e) {
	return e && typeof e == "object" ? e : {};
}
function _e(e) {
	if (e == null) return;
	let t = Number(e);
	return Number.isFinite(t) ? t : void 0;
}
function R(e) {
	globalThis.postMessage(A(e), j());
}
var ve = [
	"title",
	"text",
	"image",
	"tag"
];
function z(e) {
	let t = L(e), n = {
		silent: !!t.silent,
		timeout: _e(t.timeout)
	};
	for (let e of ve) {
		let r = t[e];
		typeof r == "string" && (n[e] = r);
	}
	return n;
}
var B = 0, V = /* @__PURE__ */ new Map();
function H() {
	return B += 1, `${I}_${B.toString(36)}`;
}
function U(t, n = {}) {
	let r = H();
	return new Promise((i, a) => {
		let o = globalThis.setTimeout(() => {
			V.delete(r), e.warn("[VOT EXT][prelude] GM API timeout", {
				requestId: r,
				action: t
			}), a(/* @__PURE__ */ Error(`VOT bridge timeout for ${t}`));
		}, F);
		e.log("[VOT EXT][prelude] GM API request", {
			requestId: r,
			action: t,
			payload: n
		}), V.set(r, {
			action: t,
			resolve: (e) => i(e),
			reject: a,
			timeoutId: o
		}), R({
			type: S,
			id: r,
			action: t,
			payload: n
		});
	});
}
var W = /* @__PURE__ */ new Map();
function G(e, t, n, r, i) {
	return (a) => {
		if (N(t[e], a), !n.isSettled) {
			if (n.isSettled = !0, e === "onload") {
				r(a);
				return;
			}
			i(a);
		}
	};
}
function K(e) {
	let t = W.get(e);
	return t ? (t.settled = !0, W.delete(e), t.timeoutId !== null && clearTimeout(t.timeoutId), t.callbacks) : null;
}
function q(t, n) {
	n.timeoutId !== null && (clearTimeout(n.timeoutId), n.timeoutId = null), !(n.settled || !n.acknowledged || !Number.isFinite(n.timeoutMs) || n.timeoutMs <= 0) && (n.timeoutId = globalThis.setTimeout(() => {
		let n = W.get(t);
		if (!n || n.settled) return;
		e.warn("[VOT EXT][prelude] GM_xmlhttpRequest timeout fallback fired", {
			requestId: t,
			timeoutMs: n.timeoutMs,
			state: "terminal",
			lastBridgeEventAt: n.lastBridgeEventAt || null
		});
		let r = K(t);
		if (r) {
			try {
				R({
					type: w,
					requestId: t
				});
			} catch {}
			N(r.ontimeout, {
				finalUrl: String(n.callbacks.url || ""),
				readyState: 4,
				status: 0,
				statusText: "",
				responseHeaders: "",
				response: null,
				responseText: "",
				error: "Timeout"
			});
		}
	}, n.timeoutMs + P));
}
function J(e) {
	return {
		method: e.method,
		url: e.url,
		headers: e.headers,
		data: x(e.data),
		timeout: e.timeout,
		responseType: e.responseType,
		anonymous: e.anonymous,
		withCredentials: e.withCredentials
	};
}
function ye(t) {
	return e.log("[VOT EXT][prelude] GM_xmlhttpRequest body passthrough", {
		url: t.url,
		method: t.method,
		body: x(t.data)
	}), {
		method: t.method,
		url: t.url,
		headers: t.headers,
		data: t.data,
		timeout: t.timeout,
		responseType: t.responseType,
		anonymous: t.anonymous,
		withCredentials: t.withCredentials
	};
}
function Y(e) {
	let t = L(e);
	return {
		status: t.status ?? null,
		statusText: t.statusText ?? null,
		readyState: t.readyState ?? null,
		finalUrl: t.finalUrl ?? null
	};
}
function be(e, t) {
	return {
		finalUrl: String(e.url ?? ""),
		readyState: 4,
		status: 0,
		statusText: "",
		responseHeaders: "",
		response: null,
		responseText: "",
		error: t
	};
}
function xe() {
	globalThis.GM_notification = (e) => {
		try {
			R({
				type: D,
				details: z(e)
			});
		} catch {}
	}, globalThis.GM_addStyle = (e) => {
		let t = document.createElement("style");
		return t.textContent = String(e ?? ""), (document.head || document.documentElement).appendChild(t), t;
	}, globalThis.GM_xmlhttpRequest = (t) => {
		let n = H(), r = L(t), i = Number(r.timeout ?? 0), o = {
			callbacks: r,
			timeoutId: null,
			timeoutMs: Number.isFinite(i) ? i : 0,
			acknowledged: !1,
			settled: !1,
			lastBridgeEventAt: 0
		};
		W.set(n, o);
		let s = !0;
		return e.log("[VOT EXT][prelude] GM_xmlhttpRequest", {
			requestId: n,
			state: "created",
			timeoutMs: o.timeoutMs,
			details: J(r)
		}), (() => {
			try {
				if (!s || !W.has(n)) return;
				let t = ye(r);
				e.log("[VOT EXT][prelude] GM_xmlhttpRequest post TYPE_XHR_START", {
					requestId: n,
					details: J(r)
				}), R({
					type: C,
					requestId: n,
					details: t
				});
			} catch (t) {
				if (!s || !W.has(n)) return;
				s = !1;
				let i = a(t);
				e.log("[VOT EXT][prelude] GM_xmlhttpRequest start error", {
					requestId: n,
					error: i
				}), N(K(n)?.onerror, be(r, i));
			}
		})(), { abort: () => {
			s && (s = !1, e.warn("[VOT EXT][prelude] GM_xmlhttpRequest abort called", { requestId: n }), K(n), R({
				type: w,
				requestId: n
			}));
		} };
	};
	let t = (e) => {
		let t = L(e), n = null, r = new Promise((e, r) => {
			let i = { ...t }, a = { isSettled: !1 };
			i.onload = G("onload", t, a, e, r), i.onerror = G("onerror", t, a, e, r), i.ontimeout = G("ontimeout", t, a, e, r), i.onabort = G("onabort", t, a, e, r);
			let o = globalThis.GM_xmlhttpRequest(i);
			n = o && typeof o.abort == "function" ? o.abort.bind(o) : null;
		});
		return r.abort = () => {
			try {
				n?.();
			} catch {}
		}, r;
	};
	globalThis.GM = {
		getValue: (e, t) => U("gm_getValue", {
			key: e,
			def: t
		}),
		setValue: (e, t) => U("gm_setValue", {
			key: e,
			value: t
		}),
		deleteValue: (e) => U("gm_deleteValue", { key: e }),
		listValues: () => U("gm_listValues"),
		getValues: (e) => U("gm_getValues", { defaults: e }),
		notification: (e) => {
			R({
				type: D,
				details: z(e)
			});
		},
		xmlHttpRequest: (e) => t(e)
	};
	let n = "1.11.8";
	globalThis.GM_info = {
		script: {
			name: "VOT Extension",
			version: n
		},
		scriptHandler: "VOT Extension",
		version: n
	};
}
//#endregion
//#region src/extension/prelude/message-handlers.ts
function Se() {
	globalThis.addEventListener("message", (e) => {
		if (!M(e)) return;
		let t = e.data;
		k(t) && (Ce(t) || we(t) || De(t));
	});
}
function Ce(t) {
	if (t.type !== "VOT_EXT_RES") return !1;
	let n = String(t.id ?? ""), r = V.get(n);
	if (!r) return !0;
	if (V.delete(n), clearTimeout(r.timeoutId), t.ok) e.log("[VOT EXT][prelude] GM API response", {
		requestId: n,
		action: r.action,
		ok: !0,
		resultType: Array.isArray(t.result) ? "array" : typeof t.result
	}), r.resolve(t.result);
	else {
		let i = a(t.error ?? "Bridge error");
		e.warn("[VOT EXT][prelude] GM API response", {
			requestId: n,
			action: r.action,
			ok: !1,
			error: i
		}), r.reject(Error(i));
	}
	return !0;
}
function we(t) {
	if (t.type !== "VOT_EXT_XHR_ACK") return !1;
	let n = String(t.requestId ?? ""), r = W.get(n);
	return !r || (r.acknowledged = !0, r.lastBridgeEventAt = Date.now(), q(n, r), e.log("[VOT EXT][prelude] XHR acknowledged", {
		requestId: n,
		state: "acknowledged",
		timeoutMs: r.timeoutMs,
		payload: t.payload ?? null
	}), !0);
}
function X(t, n, r) {
	e.log("[VOT EXT][prelude] XHR event", {
		requestId: t,
		state: n === "progress" ? "in_flight" : "terminal",
		kind: n,
		...Y(r.response ?? r.error ?? r.progress)
	});
}
function Te(t, n, r) {
	e.log("[VOT EXT][prelude] XHR terminal load", {
		requestId: t,
		state: "terminal",
		...Y(r.response)
	}), N(n.onload, r.response), K(t);
}
function Ee(t, n, r, i) {
	let a = i === "error" ? e.error : e.warn, o = {
		error: "onerror",
		timeout: "ontimeout",
		abort: "onabort"
	}[i];
	a(`[VOT EXT][prelude] XHR terminal ${i}`, {
		requestId: t,
		state: "terminal",
		...Y(r.error),
		error: r?.error?.error ?? null
	}), N(n[o], r.error), K(t);
}
function De(t) {
	if (t.type !== "VOT_EXT_XHR_EVENT") return;
	let n = String(t.requestId ?? ""), r = W.get(n);
	if (!r) return;
	let i = r.callbacks, a = t.payload, o = String(a.type ?? "");
	if (X(n, o, a), o === "progress") {
		r.lastBridgeEventAt = Date.now(), q(n, r), N(i.onprogress, a.progress);
		return;
	}
	if (o === "load") {
		Te(n, i, a);
		return;
	}
	if (o === "error" || o === "timeout" || o === "abort") {
		Ee(n, i, a, o);
		return;
	}
	e.warn("[VOT EXT][prelude] unexpected XHR bridge event", {
		requestId: n,
		kind: o,
		payload: a
	});
}
//#endregion
//#region src/extension/prelude/index.ts
var Z = "__VOT_EXT_PRELUDE_BOOTED__";
function Oe() {
	try {
		let e = globalThis.location?.href;
		if (e === "about:blank" || e?.startsWith("about:srcdoc") || globalThis.self !== globalThis.top && globalThis.location?.origin === "null") return !0;
	} catch {}
	return !1;
}
function Q() {
	let t = globalThis;
	if (t[Z]) {
		e.log("[VOT EXT][prelude] already initialized");
		return;
	}
	t[Z] = !0, xe(), Se();
}
async function ke() {
	try {
		let { manifest: e } = await U("handshake"), t = globalThis.GM_info;
		e?.name && (t.script.name = e.name), e?.version && (t.script.version = e.version, t.version = e.version);
	} catch {}
}
function $() {
	if (Q(), Oe()) {
		e.log("[VOT EXT][prelude] skipping handshake in transient frame");
		return;
	}
	ke();
}
$();
//#endregion
export { $ as bootstrapExtensionPrelude, Q as installPreludeSynchronous };
