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
}, t = "__VOT_EXT_BRIDGE__", n = "VOT_EXT_REQ", r = "VOT_EXT_RES", i = "VOT_EXT_XHR_START", a = "VOT_EXT_XHR_ABORT", o = "VOT_EXT_XHR_ACK", s = "VOT_EXT_XHR_EVENT", c = "VOT_EXT_NOTIFY", ee = "vot_gm_xhr", l = "gm_notification", u = /* @__PURE__ */ new Set([
	n,
	r,
	c,
	i,
	a,
	o,
	s
]);
function te(e) {
	return !!(e && typeof e == "object" && e.__VOT_EXT_BRIDGE__ === !0 && typeof e.type == "string" && u.has(e.type));
}
//#endregion
//#region src/extension/shared/transport.ts
function d(e) {
	return typeof ArrayBuffer < "u" && e instanceof ArrayBuffer;
}
function ne(e) {
	if (e.type !== "VOT_EXT_XHR_EVENT") return [];
	let t = e.payload, n = t?.progress?.chunk, r = t?.response?.response;
	return d(n) ? !d(r) || r === n ? [n] : [n, r] : d(r) ? [r] : [];
}
function re(e) {
	return {
		...e,
		[t]: !0
	};
}
function f() {
	let e = globalThis.location?.origin;
	return !e || e === "null" ? "*" : e;
}
function ie(e) {
	if (e.source !== globalThis.window) return !1;
	let t = globalThis.location?.origin;
	return (!t || t === "null") && e.origin === "null" || e.origin === t;
}
function p(e) {
	return {
		message: re(e),
		transfer: ne(e)
	};
}
//#endregion
//#region src/utils/errors.ts
function ae(e) {
	let t = /* @__PURE__ */ new WeakSet();
	try {
		return JSON.stringify(e, (e, n) => typeof n != "object" || !n ? n : t.has(n) ? "[Circular]" : (t.add(n), n)) ?? null;
	} catch {
		return null;
	}
}
function oe(e) {
	let t = [
		e?.data?.message,
		e?.error?.message,
		e?.message
	];
	for (let e of t) if (typeof e == "string" && e) return e;
	return null;
}
function se(e, t) {
	let n = oe(e);
	if (n) return n;
	let r = ae(e);
	if (r && r !== "{}") return r;
	let i = e.constructor?.name;
	return i ? `[${i}]` : t;
}
function ce(e, t) {
	return typeof e == "number" || typeof e == "boolean" || typeof e == "bigint" ? `${e}` : typeof e == "symbol" ? e.description ? `Symbol(${e.description})` : "Symbol" : typeof e == "function" ? e.name ? `[Function ${e.name}]` : "[Function]" : t;
}
function m(e, t = "Unknown error") {
	return e instanceof Error ? e.message || t : typeof e == "string" ? e || t : e == null ? t : typeof e == "object" ? se(e, t) : ce(e, t);
}
//#endregion
//#region src/extension/shared/utils.ts
function le(e) {
	return m(e);
}
//#endregion
//#region src/extension/shared/webext.ts
var h = globalThis.browser, g = globalThis.chrome, _ = h ?? g ?? null, ue = !!h && _ === h, de = typeof h?.runtime?.getBrowserInfo == "function";
function fe() {
	let e = g?.runtime?.lastError ?? _?.runtime?.lastError;
	return e?.message ? String(e.message) : null;
}
async function pe(e, t = [], n = {}) {
	if (typeof e != "function") throw TypeError("WebExtension API is not available");
	let r = n.mapCbArgs, i = n.rejectOnLastError !== !1;
	return ue ? await e(...t) : await new Promise((n, a) => {
		try {
			e(...t, (...e) => {
				let t = i ? fe() : null;
				t ? a(Error(t)) : n(r ? r(...e) : e[0]);
			});
		} catch (e) {
			a(e);
		}
	});
}
async function v(e) {
	let t = _?.runtime, n = t?.sendMessage;
	return await pe(n?.bind(t), [e]);
}
//#endregion
//#region src/extension/bridge/request-handler.ts
var me = "gm_storage";
async function he(e) {
	return await v(e);
}
async function ge(e, t) {
	let n = await he({
		type: me,
		action: e,
		payload: t
	});
	if (!n?.ok) throw Error(n?.error || `Storage request failed: ${e}`);
	return n.result;
}
async function _e(e, t) {
	switch (e) {
		case "handshake": return {
			manifest: _?.runtime?.getManifest?.() ?? {},
			id: _?.runtime?.id ?? null
		};
		case "gm_getValue":
		case "gm_setValue":
		case "gm_deleteValue":
		case "gm_listValues":
		case "gm_getValues": return await ge(e, t);
		default: throw Error(`Unknown bridge action: ${e}`);
	}
}
//#endregion
//#region src/extension/shared/bodySerialization.ts
var y = typeof Uint8Array.prototype.toBase64 == "function" ? Uint8Array.prototype.toBase64 : null, b = Uint8Array.fromBase64, ve = /[-_]/;
function ye(e) {
	let t = String(e).replaceAll(/\s+/g, ""), n = t.length % 4;
	if (n === 1) throw TypeError("Invalid base64 input.");
	return n === 0 ? t : t + "=".repeat(4 - n);
}
function be(e) {
	let t = "";
	for (let n of e) t += String.fromCharCode(n);
	return t;
}
function xe(e) {
	let { buffer: t, byteOffset: n, byteLength: r } = e;
	if (t instanceof ArrayBuffer) return n === 0 && r === t.byteLength ? t : t.slice(n, n + r);
	let i = new ArrayBuffer(r);
	return new Uint8Array(i).set(e), i;
}
function x(e) {
	if (typeof y == "function") return y.call(e);
	let t = globalThis.btoa;
	if (typeof t != "function") throw TypeError("Base64 encoder is not available in this environment.");
	return t(be(e));
}
function S(e) {
	let t = ye(e), n = t.replaceAll("-", "+").replaceAll("_", "/");
	if (typeof b == "function") {
		let e = ve.test(t);
		return b(e ? n : t, e ? { alphabet: "base64" } : void 0);
	}
	let r = globalThis.atob;
	if (typeof r != "function") throw TypeError("Base64 decoder is not available in this environment.");
	let i = r(n), a = new Uint8Array(i.length);
	for (let e = 0; e < i.length; e += 1) a[e] = i.charCodeAt(e);
	return a;
}
function C(e) {
	return xe(S(e));
}
var w = 1e7, Se = 12, Ce = 2;
function T(e) {
	return typeof e == "object" && !!e;
}
function we(e) {
	return E(e) === "[object Object]";
}
function E(e) {
	try {
		return Object.prototype.toString.call(e);
	} catch {
		return null;
	}
}
function D(e) {
	return E(e) === "[object ArrayBuffer]";
}
function Te(e) {
	try {
		let t = e?.constructor?.name;
		return typeof t == "string" ? t : null;
	} catch {
		return null;
	}
}
function O(e, t) {
	try {
		let n = e?.[t];
		return typeof n == "string" ? n : null;
	} catch {
		return null;
	}
}
function k(e) {
	if (E(e) === "[object Blob]") return !0;
	if (!e || typeof e != "object") return !1;
	try {
		let t = e, n = typeof t.arrayBuffer == "function", r = typeof t.slice == "function", i = typeof t.size == "number", a = typeof t.type == "string";
		return (n || r) && i && a;
	} catch {
		return !1;
	}
}
function Ee(e) {
	try {
		return JSON.stringify(e);
	} catch {
		return null;
	}
}
function A(e) {
	if (typeof e != "number" || !Number.isFinite(e)) return null;
	let t = Math.trunc(e);
	return t < 0 || t > w ? null : t;
}
function j(e) {
	return typeof e != "number" || !Number.isFinite(e) ? null : Math.trunc(e) & 255;
}
function M(e) {
	let t = e.length;
	if (t > w) return null;
	let n = new Uint8Array(t);
	for (let r = 0; r < t; r += 1) {
		let t = j(e[r]);
		if (t === null) return null;
		n[r] = t;
	}
	return n;
}
function N(e) {
	return new Uint8Array(e.buffer, e.byteOffset, e.byteLength);
}
function De(e) {
	return N(e).slice();
}
function Oe(e) {
	if (e === "") return null;
	let t = Number(e);
	return !Number.isFinite(t) || !Number.isInteger(t) || t < 0 ? null : t;
}
function P(e) {
	if (!T(e)) return !1;
	let t = e;
	return t.__votExtBody === !0 && typeof t.b64 == "string";
}
function ke(e) {
	return e == null || typeof e == "string" || P(e);
}
function F(e) {
	return {
		__votExtBody: !0,
		kind: "bytes",
		b64: x(e)
	};
}
function Ae(e, t) {
	return {
		__votExtBody: !0,
		kind: "blob",
		b64: x(e),
		mime: t
	};
}
async function je(e) {
	if (!T(e)) return null;
	try {
		let t = e;
		if (typeof t.arrayBuffer == "function") return {
			ab: await t.arrayBuffer(),
			mime: O(t, "type")
		};
	} catch {}
	if (typeof Response < "u") try {
		return {
			ab: await new Response(e).arrayBuffer(),
			mime: O(e, "type")
		};
	} catch {}
	if (typeof Blob < "u" && k(e)) try {
		return {
			ab: await e.arrayBuffer(),
			mime: O(e, "type")
		};
	} catch {}
	return null;
}
function I(e, t = 0) {
	if (t > Ce || !T(e)) return null;
	let n = e;
	if (D(e)) return new Uint8Array(e);
	try {
		if (ArrayBuffer.isView(e)) return De(e);
	} catch {}
	return Array.isArray(e) ? M(e) : Me(n) || Ne(n) || Pe(n, e, t) || Fe(n) || (we(e) ? Ie(n) : null);
}
function Me(e) {
	let t = [
		e.type === "Buffer" && Array.isArray(e.data) ? e.data : null,
		Array.isArray(e.data) ? e.data : null,
		Array.isArray(e.bytes) ? e.bytes : null
	];
	for (let e of t) {
		if (!e) continue;
		let t = M(e);
		if (t) return t;
	}
	return null;
}
function Ne(e) {
	let t = [e.b64, e.base64];
	for (let e of t) if (typeof e == "string") try {
		return S(e);
	} catch {}
	return null;
}
function Pe(e, t, n) {
	let r = A(e.byteLength), i = A(e.byteOffset ?? 0);
	if (r === null || i === null) return null;
	let a = e.buffer;
	if (D(a)) try {
		return new Uint8Array(a, i, r).slice();
	} catch {}
	if (!a || a === t) return null;
	let o = I(a, n + 1);
	if (!o || i > o.byteLength) return null;
	let s = Math.min(o.byteLength, i + r);
	return o.slice(i, s);
}
function Fe(e) {
	let t = A(e.length);
	if (t === null) return null;
	let n = e, r = new Uint8Array(t);
	for (let e = 0; e < t; e += 1) {
		let t = j(n[e]);
		if (t === null) return null;
		r[e] = t;
	}
	return r;
}
function Ie(e) {
	let t = Object.keys(e);
	if (!t.length) return null;
	let n = Array(t.length), r = -1;
	for (let e = 0; e < t.length; e += 1) {
		let i = Oe(t[e]);
		if (i === null || i > w) return null;
		n[e] = i, i > r && (r = i);
	}
	let i = new Uint8Array(r + 1);
	for (let r = 0; r < t.length; r += 1) {
		let a = j(e[t[r]]);
		if (a === null) return null;
		i[n[r]] = a;
	}
	return i;
}
function L(e) {
	let t = e === null ? "null" : typeof e, n = E(e), r = Te(e), i = Le(e, t, n, r);
	if (i) return i;
	let a = Re(e, t, n, r);
	if (a) return a;
	let o = ze(e, t, n, r);
	if (o) return o;
	let s = I(e);
	if (s) return {
		kind: "coerced-bytes",
		jsType: t,
		tag: n,
		ctor: r,
		byteLength: s.byteLength
	};
	if (T(e)) {
		let i = Object.keys(e);
		return {
			kind: "object",
			jsType: t,
			tag: n,
			ctor: r,
			keyCount: i.length,
			keys: i.slice(0, Se)
		};
	}
	return {
		kind: "primitive",
		jsType: t,
		tag: n,
		ctor: r
	};
}
function Le(e, t, n, r) {
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
function Re(e, t, n, r) {
	return P(e) ? {
		kind: `serialized:${typeof e.kind == "string" && e.kind ? e.kind : "bytes"}`,
		jsType: t,
		tag: n,
		ctor: r,
		base64Length: e.b64.length,
		mime: O(e, "mime")
	} : null;
}
function ze(e, t, n, r) {
	if (D(e)) return {
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
	return k(e) ? {
		kind: "BlobLike",
		jsType: t,
		tag: n,
		ctor: r,
		byteLength: typeof e.size == "number" ? Number(e.size) : -1,
		mime: O(e, "type")
	} : null;
}
async function Be(e) {
	if (e == null || typeof e == "string") return e;
	if (P(e)) return e.kind === "blob" ? {
		__votExtBody: !0,
		kind: "blob",
		b64: e.b64,
		mime: O(e, "mime")
	} : {
		__votExtBody: !0,
		kind: "bytes",
		b64: e.b64
	};
	if (D(e)) return F(new Uint8Array(e));
	try {
		if (ArrayBuffer.isView(e)) return F(N(e));
	} catch {}
	let t = await je(e);
	if (t) return Ae(new Uint8Array(t.ab), t.mime);
	let n = I(e);
	if (n) return F(n);
	let r = L(e);
	try {
		console.warn("[VOT Extension] Unsupported GM_xmlhttpRequest body type; coercing fallback.", r);
	} catch {}
	if (T(e)) {
		let t = Ee(e);
		if (typeof t == "string") return t;
	}
	return String(e);
}
//#endregion
//#region src/extension/shared/yandexHeaders.ts
var Ve = "api.browser.yandex.ru", He = /* @__PURE__ */ new Set([
	"sec-ch-ua",
	"sec-ch-ua-mobile",
	"sec-ch-ua-platform",
	"sec-ch-ua-full-version-list"
]), Ue = /* @__PURE__ */ new Set([
	"sec-ch-ua-full-version",
	"sec-ch-ua-platform-version",
	"sec-ch-ua-arch",
	"sec-ch-ua-bitness",
	"sec-ch-ua-model",
	"sec-ch-ua-wow64"
]);
function We(e) {
	return String(e || "") === Ve;
}
function Ge(e) {
	return String(e || "").trim();
}
function Ke(e) {
	let t = Ge(e).toLowerCase();
	return t ? !!(t === "origin" || t === "referer" || Ue.has(t) || t.startsWith("sec-ch-ua") && !He.has(t)) : !1;
}
//#endregion
//#region src/extension/bridge/xhr-bridge.ts
var qe = 6e5, Je = ["fullVersionList"], Ye = /* @__PURE__ */ new Set([
	"load",
	"error",
	"timeout",
	"abort"
]), Xe = Object.freeze({}), Ze = String.raw`\"`, R = Xe, z = 0, B = null, V = /* @__PURE__ */ new Map();
function H(e) {
	return e.replaceAll("\"", Ze);
}
function U(e) {
	return e.filter((e) => e && typeof e.brand == "string" && typeof e.version == "string").map((e) => `"${H(e.brand)}";v="${H(e.version)}"`).join(", ");
}
async function Qe() {
	let e = navigator?.userAgentData;
	if (!e) return {};
	let t = {}, n = Array.isArray(e.brands) && e.brands || Array.isArray(e.uaList) && e.uaList || [];
	n.length && (t["sec-ch-ua"] = U(n)), typeof e.mobile == "boolean" && (t["sec-ch-ua-mobile"] = e.mobile ? "?1" : "?0"), typeof e.platform == "string" && e.platform && (t["sec-ch-ua-platform"] = `"${H(e.platform)}"`);
	try {
		let n = await e.getHighEntropyValues?.(Je);
		Array.isArray(n?.fullVersionList) && n.fullVersionList.length && (t["sec-ch-ua-full-version-list"] = U(n.fullVersionList));
	} catch {}
	return t;
}
async function $e() {
	if (Date.now() < z) return R;
	if (B !== null) return await B;
	B = (async () => {
		let e = Object.freeze({ ...await Qe() });
		return R = e, z = Date.now() + qe, e;
	})();
	try {
		return await B;
	} finally {
		B = null;
	}
}
function et(e) {
	let t = e?.headers;
	if (!t || typeof t != "object") {
		let t = {};
		return e.headers = t, t;
	}
	let n = t;
	for (let [e, t] of Object.entries(n)) if (typeof t != "string") {
		if (typeof t == "number" || typeof t == "boolean") {
			n[e] = String(t);
			continue;
		}
		delete n[e];
	}
	return e.headers = n, n;
}
function tt(e) {
	for (let t of Object.keys(e)) Ke(t) && delete e[t];
}
function nt(e, t) {
	let n = /* @__PURE__ */ new Set();
	for (let t of Object.keys(e)) n.add(t.toLowerCase());
	for (let [r, i] of Object.entries(t)) {
		if (!i) continue;
		let t = r.toLowerCase();
		n.has(t) || (e[r] = i, n.add(t));
	}
}
function W(e) {
	let { message: t, transfer: n } = p(e), r = f();
	if (n.length) {
		globalThis.postMessage(t, r, n);
		return;
	}
	globalThis.postMessage(t, r);
}
function G(e, t) {
	t.settled = !0;
	try {
		t.port.disconnect();
	} catch {}
	V.delete(e);
}
function K(e, t) {
	let n = V.get(e);
	return n === t && !n.settled;
}
function q(e, t) {
	let n = String(t?.type ?? "");
	W({
		type: s,
		requestId: e,
		payload: {
			...t,
			state: n === "progress" ? "in_flight" : "terminal"
		}
	});
}
function J(e, t) {
	return {
		finalUrl: String(e?.url || ""),
		readyState: 4,
		status: 0,
		statusText: "",
		responseHeaders: "",
		response: null,
		responseText: "",
		error: t
	};
}
function rt(e, t, n, r) {
	if (e instanceof ArrayBuffer) return e;
	try {
		if (ArrayBuffer.isView(e)) {
			let t = e, n = new Uint8Array(t.byteLength);
			return n.set(new Uint8Array(t.buffer, t.byteOffset, t.byteLength)), n.buffer;
		}
	} catch {}
	if (n > 0) {
		let e = new Uint8Array(n), r = 0;
		for (let n of t) {
			let t = new Uint8Array(n);
			e.set(t, r), r += t.byteLength;
		}
		return e.buffer;
	}
	return typeof r == "string" && r.length > 0 ? C(r) : /* @__PURE__ */ new ArrayBuffer(0);
}
function it(t, n) {
	e.log("[VOT EXT][bridge] startXhr", {
		requestId: t,
		url: n?.url,
		method: n?.method,
		responseType: n?.responseType,
		timeoutMs: Number(n?.timeout ?? 0),
		headerCount: n?.headers && typeof n.headers == "object" ? Object.keys(n.headers).length : 0,
		body: L(n?.data)
	});
}
function at(t, n) {
	e.log("[VOT EXT][bridge] port message", {
		requestId: t,
		kind: n.type ?? "unknown",
		state: n.state ?? null,
		status: n.response?.status ?? n.error?.status ?? n.progress?.status ?? null,
		loaded: n.progress?.loaded ?? null,
		total: n.progress?.total ?? null
	});
}
function ot(e, t) {
	if (t.type !== "progress" || !t.progress) return;
	if (t.progress.chunk instanceof ArrayBuffer) {
		let n = t.progress.chunk.slice(0);
		e.chunks.push(n), e.totalBytes += n.byteLength;
		return;
	}
	let n = t.progress.chunkB64;
	if (typeof n != "string" || !n.length) return;
	let r = C(n), i = r.slice(0);
	e.chunks.push(i), e.totalBytes += i.byteLength, t.progress.chunk = r, delete t.progress.chunkB64;
}
function st(e, t) {
	if (t.type !== "load" || !t.response) return;
	let n = String(t.response.responseType || e.responseType || "text").toLowerCase();
	if (n !== "arraybuffer" && n !== "blob") return;
	let r = rt(t.response.response, e.chunks, e.totalBytes, t.response.responseB64);
	if (delete t.response.responseB64, e.chunks.length = 0, e.totalBytes = 0, n === "blob") {
		let e = t.response.contentType || t.response.mime || void 0;
		t.response.response = e ? new Blob([r], { type: String(e) }) : new Blob([r]);
		return;
	}
	t.response.response = r;
}
function ct(t, n, r) {
	Ye.has(String(r.type ?? "")) && (e.log("[VOT EXT][bridge] terminal event", {
		requestId: t,
		kind: r.type,
		status: r.response?.status ?? r.error?.status ?? null
	}), G(t, n));
}
function Y(e, t) {
	let n = V.get(e);
	!n || n.settled || !t || typeof t != "object" || (at(e, t), ot(n, t), st(n, t), q(e, t), ct(e, n, t));
}
function lt(t, n) {
	let r = V.get(t);
	!r || r.settled || (e.warn("[VOT EXT][bridge] port disconnected before terminal event", {
		requestId: t,
		url: n?.url ?? null
	}), G(t, r), q(t, {
		type: "error",
		error: J(n, "Bridge port disconnected before response")
	}));
}
function ut(e, t, n) {
	t.onMessage.addListener((t) => {
		Y(e, t);
	}), t.onDisconnect.addListener(() => {
		lt(e, n);
	});
}
async function dt(t, n, r) {
	let i = String(n?.url ?? ""), a = "";
	if (i) try {
		a = new URL(i).hostname;
	} catch {}
	if (!We(a)) return;
	let o = et(n);
	tt(o);
	let s = await $e();
	K(t, r) && (nt(o, s), e.log("[VOT EXT][bridge] yandex header normalization", {
		requestId: t,
		url: i,
		headerCount: Object.keys(o).length,
		headerNames: Object.keys(o)
	}));
}
async function ft(e) {
	return ke(e?.data) ? e.data : de ? e?.data : await Be(e?.data);
}
function pt(t, n, r, i) {
	let a = n || t, o = V.get(a);
	o && !o.settled && G(a, o);
	let s = m(i);
	e.log("[VOT EXT][bridge] startXhr error", {
		requestId: a,
		error: s,
		lastError: _?.runtime?.lastError ?? null
	}), a && q(a, {
		type: "error",
		error: J(r, s)
	});
}
async function mt(t, n) {
	let r = String(t || ""), i = n ?? {};
	try {
		if (!r) throw Error("Missing requestId for bridge XHR");
		t = r, V.has(t) && (e.warn("[VOT EXT][bridge] replacing active XHR request", { requestId: t }), X(t)), it(t, i);
		let n = _?.runtime?.connect?.({ name: ee });
		if (!n || typeof n != "object") throw Error("Bridge port is not available");
		let a = n, s = String(i?.responseType || "text").toLowerCase(), c = {
			port: a,
			responseType: s,
			chunks: [],
			totalBytes: 0,
			settled: !1
		};
		if (V.set(t, c), W({
			type: o,
			requestId: t,
			payload: {
				state: "acknowledged",
				timeoutMs: Number(i?.timeout ?? 0),
				responseType: s,
				ts: Date.now()
			}
		}), ut(t, a, i), await dt(t, i, c), !K(t, c)) return;
		let l = await ft(i);
		if (e.log("[VOT EXT][bridge] serialized body", {
			requestId: t,
			url: i?.url ?? null,
			from: L(i?.data),
			to: L(l)
		}), !K(t, c)) return;
		let u = {
			...i,
			data: l,
			responseType: i?.responseType
		};
		e.log("[VOT EXT][bridge] post start to background", {
			requestId: t,
			url: u.url,
			method: u.method,
			responseType: u.responseType,
			body: L(u.data)
		}), c.port.postMessage({
			type: "start",
			details: u
		});
	} catch (e) {
		pt(t, r, i, e);
	}
}
function X(t) {
	let n = V.get(t);
	if (!(!n || n.settled)) {
		n.settled = !0, e.warn("[VOT EXT][bridge] abortXhr", { requestId: t });
		try {
			n.port.postMessage({ type: "abort" });
		} catch {}
		try {
			n.port.disconnect();
		} catch {}
		V.delete(t);
	}
}
//#endregion
//#region src/extension/bridge/index.ts
var Z = "__VOT_EXT_BRIDGE_BOOTED__";
function Q(e) {
	let t = document.head ?? document.documentElement;
	if (!t) {
		console.error("[VOT Extension] bridge: missing document root");
		return;
	}
	let n = document.createElement("script");
	n.type = "module", n.async = !1, n.src = _.runtime?.getURL(e) ?? "", n.dataset.votExtensionModule = e, n.addEventListener("error", () => {
		console.error(`[VOT Extension] bridge: failed to inject ${e}`);
	}, { once: !0 }), t.appendChild(n);
}
function ht(e) {
	let { message: t, transfer: n } = p(e), r = f();
	if (n.length) {
		globalThis.postMessage(t, r, n);
		return;
	}
	globalThis.postMessage(t, r);
}
function $(e, t, n, i) {
	ht({
		type: r,
		id: e,
		ok: t,
		result: n,
		error: i
	});
}
function gt() {
	let t = globalThis;
	if (t[Z]) {
		e.log("[VOT EXT][bridge] already initialized");
		return;
	}
	if (t[Z] = !0, !_?.runtime || !_?.storage?.local) {
		console.warn("[VOT Extension] bridge: missing WebExtension APIs");
		return;
	}
	Q("prelude.module.js"), Q("content.module.js"), globalThis.addEventListener("message", async (t) => {
		if (!ie(t)) return;
		let n = t.data;
		if (te(n)) try {
			if (n.type === "VOT_EXT_REQ") {
				$(String(n.id ?? ""), !0, await _e(String(n.action ?? ""), n.payload ?? {}));
				return;
			}
			if (n.type === "VOT_EXT_NOTIFY") {
				v({
					type: l,
					details: n.details
				}).catch((t) => {
					e.warn("[VOT EXT][bridge] notification dispatch failed", t);
				});
				return;
			}
			if (n.type === "VOT_EXT_XHR_START") {
				await mt(String(n.requestId ?? ""), n.details ?? {});
				return;
			}
			if (n.type === "VOT_EXT_XHR_ABORT") {
				X(String(n.requestId ?? ""));
				return;
			}
		} catch (e) {
			n?.type === "VOT_EXT_REQ" ? $(String(n.id ?? ""), !1, void 0, le(e)) : console.error("[VOT Extension] bridge error", e);
		}
	});
}
gt();
//#endregion
