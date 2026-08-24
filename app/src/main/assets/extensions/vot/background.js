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
}, t = globalThis.browser, n = globalThis.chrome, r = t ?? n ?? null, i = !!t && r === t, a = typeof t?.runtime?.getBrowserInfo == "function", o = a;
function s() {
	let e = n?.runtime?.lastError ?? r?.runtime?.lastError;
	return e?.message ? String(e.message) : null;
}
async function c(e, t = [], n = {}) {
	if (typeof e != "function") throw TypeError("WebExtension API is not available");
	let r = n.mapCbArgs, a = n.rejectOnLastError !== !1;
	return i ? await e(...t) : await new Promise((n, i) => {
		try {
			e(...t, (...e) => {
				let t = a ? s() : null;
				t ? i(Error(t)) : n(r ? r(...e) : e[0]);
			});
		} catch (e) {
			i(e);
		}
	});
}
function l(e, t, n) {
	return new Promise((r, i) => {
		try {
			e(...t, (...e) => {
				let t = s();
				if (t) {
					i(Error(t));
					return;
				}
				r(n ? n(...e) : e[0]);
			});
		} catch (e) {
			i(e);
		}
	});
}
async function u(e) {
	let i = a ? void 0 : n?.storage?.local;
	if (i && typeof i.get == "function") return await l(i.get.bind(i), [e]);
	let o = t?.storage?.local ?? r?.storage?.local;
	return await c(o?.get?.bind(o), [e]);
}
async function d(e) {
	let i = a ? void 0 : n?.storage?.local;
	if (i && typeof i.set == "function") {
		await l(i.set.bind(i), [e], () => void 0);
		return;
	}
	let o = t?.storage?.local ?? r?.storage?.local;
	await c(o?.set?.bind(o), [e], { mapCbArgs: () => void 0 });
}
async function f(e) {
	let i = a ? void 0 : n?.storage?.local;
	if (i && typeof i.remove == "function") {
		await l(i.remove.bind(i), [e], () => void 0);
		return;
	}
	let o = t?.storage?.local ?? r?.storage?.local;
	await c(o?.remove?.bind(o), [e], { mapCbArgs: () => void 0 });
}
async function p(e) {
	let t = r?.declarativeNetRequest, n = t?.updateSessionRules;
	!t || typeof n != "function" || await c(n.bind(t), [{
		addRules: e.addRules ?? [],
		removeRuleIds: e.removeRuleIds ?? []
	}], { mapCbArgs: () => void 0 });
}
async function m(e, t) {
	let n = r?.notifications, i = n?.create;
	!n || typeof i != "function" || await c(i.bind(n), [e, t], { mapCbArgs: () => void 0 });
}
async function ee(e) {
	let t = r?.notifications, n = t?.clear;
	!t || typeof n != "function" || await c(n.bind(t), [e], {
		mapCbArgs: () => void 0,
		rejectOnLastError: !1
	});
}
async function te(e, t) {
	let n = r?.windows, i = n?.update;
	!n || typeof i != "function" || await c(i.bind(n), [e, t], {
		mapCbArgs: () => void 0,
		rejectOnLastError: !1
	});
}
async function h(e, t) {
	let n = r?.tabs, i = n?.update;
	!n || typeof i != "function" || await c(i.bind(n), [e, t], {
		mapCbArgs: () => void 0,
		rejectOnLastError: !1
	});
}
//#endregion
//#region src/extension/shared/yandexHeaders.ts
var g = "api.browser.yandex.ru", _ = /* @__PURE__ */ new Set([
	"sec-ch-ua",
	"sec-ch-ua-mobile",
	"sec-ch-ua-platform",
	"sec-ch-ua-full-version-list"
]), v = [
	"sec-ch-ua-full-version",
	"sec-ch-ua-platform-version",
	"sec-ch-ua-arch",
	"sec-ch-ua-bitness",
	"sec-ch-ua-model",
	"sec-ch-ua-wow64"
], y = new Set(v);
function b(e) {
	return String(e || "") === g;
}
function x(e) {
	return String(e || "").trim();
}
function S(e) {
	let t = x(e).toLowerCase();
	return t ? !!(t === "origin" || t === "referer" || y.has(t) || t.startsWith("sec-ch-ua") && !_.has(t)) : !1;
}
function C(e) {
	let t = {};
	for (let [n, r] of Object.entries(e || {})) {
		let e = x(n);
		e && (S(e) || (t[e] = String(r)));
	}
	return t;
}
//#endregion
//#region src/extension/background/dnr-rules.ts
var w = 9001, T = 9002, E = 9003, D = /* @__PURE__ */ new Map(), ne = Promise.resolve(), O = [
	{
		header: "Origin",
		operation: "remove"
	},
	{
		header: "x-client-data",
		operation: "remove"
	},
	{
		header: "x-goog-visitor-id",
		operation: "remove"
	}
], k = [{
	header: "x-client-data",
	operation: "remove"
}];
function A() {
	return !!r?.declarativeNetRequest?.updateSessionRules;
}
async function j(e) {
	await p(e);
}
var re = /* @__PURE__ */ new Set([
	"user-agent",
	"origin",
	"referer"
]);
function ie(e) {
	let t = e.trim().toLowerCase();
	return t.startsWith("sec-") || t.startsWith("proxy-") || re.has(t);
}
function M(e) {
	let t = e.map((e) => [`${x(String(e.header)).toLowerCase()}:${String(e.operation)}`, String("value" in e ? e.value : "")]).sort((e, t) => e[0].localeCompare(t[0]));
	return JSON.stringify(t);
}
async function N(e, t, n) {
	if (t === D.get(e)) return !1;
	let r = ne, i;
	ne = new Promise((e) => {
		i = e;
	});
	try {
		await r;
	} catch {}
	try {
		return t !== D.get(e) && (await j({
			removeRuleIds: [e],
			addRules: [n]
		}), D.set(e, t)), !0;
	} finally {
		i();
	}
}
async function ae(e, t) {
	if (!A()) return;
	let n = "";
	try {
		n = new URL(e).hostname;
	} catch {
		return;
	}
	if (!b(n)) return;
	let r = [
		{
			header: "Origin",
			operation: "remove"
		},
		{
			header: "Referer",
			operation: "remove"
		},
		...v.map((e) => ({
			header: e,
			operation: "remove"
		}))
	];
	for (let [e, n] of Object.entries(C(t))) r.push({
		header: x(e),
		operation: "set",
		value: String(n)
	});
	await N(w, M(r), {
		id: w,
		priority: 1,
		action: {
			type: "modifyHeaders",
			requestHeaders: r
		},
		condition: {
			urlFilter: "|https://api.browser.yandex.ru/",
			resourceTypes: ["xmlhttprequest"]
		}
	});
}
function P(e) {
	try {
		let { protocol: t, hostname: n } = new URL(e);
		return t === "https:" && n.toLowerCase() === "m.youtube.com";
	} catch {
		return !1;
	}
}
function F(e) {
	try {
		let { protocol: t, hostname: n } = new URL(e), r = n.toLowerCase();
		return t === "https:" && (r === "googlevideo.com" || r.endsWith(".googlevideo.com"));
	} catch {
		return !1;
	}
}
async function I(t, n, r, i, a) {
	if (!A() || !n(t)) return !1;
	let o = M(i), s = {
		id: r,
		priority: 1,
		action: {
			type: "modifyHeaders",
			requestHeaders: i
		},
		condition: {
			urlFilter: a,
			resourceTypes: ["xmlhttprequest"]
		}
	};
	e.log("[VOT EXT][background][dnr] applying rule", {
		ruleId: r,
		urlFilter: a,
		headerOps: i.map((e) => `${e.operation}:${e.header}`),
		isDuplicate: o === D.get(r)
	});
	let c = await N(r, o, s);
	return e.log("[VOT EXT][background][dnr] rule applied", {
		ruleId: r,
		isNew: c
	}), c;
}
async function oe(e, t = {}) {
	if (!P(e)) return !1;
	let n = [...O];
	for (let [e, r] of Object.entries(t)) {
		let t = x(e);
		!t || t.toLowerCase() === "origin" || n.push({
			header: t,
			operation: "set",
			value: String(r)
		});
	}
	return I(e, P, T, n, "||m.youtube.com/");
}
async function se(e, t = {}) {
	if (!F(e)) return !1;
	let n = [...k];
	for (let [e, r] of Object.entries(t)) {
		let t = x(e);
		!t || t.toLowerCase() === "origin" || n.push({
			header: t,
			operation: "set",
			value: String(r)
		});
	}
	return I(e, F, E, n, "||googlevideo.com/");
}
async function ce() {
	if (A()) try {
		await j({
			removeRuleIds: [T, E],
			addRules: [{
				id: T,
				priority: 1,
				action: {
					type: "modifyHeaders",
					requestHeaders: O
				},
				condition: {
					urlFilter: "||m.youtube.com/",
					resourceTypes: ["xmlhttprequest"]
				}
			}, {
				id: E,
				priority: 1,
				action: {
					type: "modifyHeaders",
					requestHeaders: k
				},
				condition: {
					urlFilter: "||googlevideo.com/",
					resourceTypes: ["xmlhttprequest"]
				}
			}]
		}), D.set(T, M(O)), D.set(E, M(k)), e.log("[VOT EXT][background] DNR rules pre-seeded");
	} catch (t) {
		e.warn("[VOT EXT][background] Failed to pre-seed DNR rules:", t);
	}
}
var le = "gm_storage", ue = "gm_notification";
//#endregion
//#region src/utils/errors.ts
function de(e) {
	let t = /* @__PURE__ */ new WeakSet();
	try {
		return JSON.stringify(e, (e, n) => typeof n != "object" || !n ? n : t.has(n) ? "[Circular]" : (t.add(n), n)) ?? null;
	} catch {
		return null;
	}
}
function fe(e) {
	let t = [
		e?.data?.message,
		e?.error?.message,
		e?.message
	];
	for (let e of t) if (typeof e == "string" && e) return e;
	return null;
}
function pe(e, t) {
	let n = fe(e);
	if (n) return n;
	let r = de(e);
	if (r && r !== "{}") return r;
	let i = e.constructor?.name;
	return i ? `[${i}]` : t;
}
function me(e, t) {
	return typeof e == "number" || typeof e == "boolean" || typeof e == "bigint" ? `${e}` : typeof e == "symbol" ? e.description ? `Symbol(${e.description})` : "Symbol" : typeof e == "function" ? e.name ? `[Function ${e.name}]` : "[Function]" : t;
}
function he(e, t = "Unknown error") {
	return e instanceof Error ? e.message || t : typeof e == "string" ? e || t : e == null ? t : typeof e == "object" ? pe(e, t) : me(e, t);
}
//#endregion
//#region src/extension/shared/utils.ts
function L(e) {
	return he(e);
}
function R(e, t) {
	if (typeof e == "function") try {
		e(t);
	} catch {}
}
//#endregion
//#region src/extension/background/notifications.ts
var ge = "icons/icon-128.png", _e = "src/extension/icons/icon-128.png";
function ve(e) {
	return !e || typeof e != "object" ? !1 : e.type === ue;
}
function ye(e) {
	let t = e && typeof e == "object" ? e : {}, n = Number(t.timeout ?? 0);
	return {
		title: typeof t.title == "string" ? t.title : "",
		text: typeof t.text == "string" ? t.text : "",
		silent: !!t.silent,
		timeout: Number.isFinite(n) && n > 0 ? n : 0
	};
}
function be(e) {
	let t = e.tab?.id, n = e.tab?.windowId;
	return `vot:${typeof t == "number" ? t : -1}:${typeof n == "number" ? n : -1}:${typeof crypto < "u" && "randomUUID" in crypto ? crypto.randomUUID() : `${Date.now()}:${Math.random().toString(36).slice(2)}`}`;
}
function xe() {
	return typeof r?.runtime?.getBrowserInfo == "function";
}
function Se() {
	let e = xe(), t = e ? ge : _e;
	return e && r?.runtime?.getURL ? r.runtime.getURL(t) : t;
}
function Ce(e) {
	let t = xe(), n = {
		type: "basic",
		iconUrl: Se(),
		title: e.title || "VOT",
		message: e.text
	};
	return t || (n.silent = e.silent), n;
}
function we() {
	r?.runtime?.onMessage?.addListener?.((t, n, r) => {
		if (!ve(t)) return;
		let i = ye(t.details), a = be(n), o = Ce(i);
		return (async () => {
			try {
				await m(a, o), i.timeout > 0 && setTimeout(() => {
					ee(a);
				}, i.timeout), R(r, { ok: !0 });
			} catch (t) {
				e.error("[VOT EXT][background] Failed to create notification", t), R(r, {
					ok: !1,
					error: L(t)
				});
			}
		})(), !0;
	}), r?.notifications?.onClicked?.addListener?.((e) => {
		if (!e.startsWith("vot:")) return;
		let t = e.split(":");
		if (t.length < 3) return;
		let n = Number(t[1]), r = Number(t[2]);
		Number.isFinite(r) && r >= 0 && te(r, { focused: !0 }), Number.isFinite(n) && n >= 0 && h(n, { active: !0 });
	});
}
//#endregion
//#region src/extension/background/storage-bridge.ts
function Te(e) {
	return !e || typeof e != "object" ? !1 : e.type === le;
}
function z(e) {
	switch (typeof e) {
		case "string": return e;
		case "number":
		case "boolean":
		case "bigint": return String(e);
		default: return "";
	}
}
async function Ee(e, t) {
	switch (e) {
		case "gm_getValue": {
			let e = z(t?.key), n = t?.def, r = await u(e);
			return Object.hasOwn(r, e) ? r[e] : n;
		}
		case "gm_setValue": return await d({ [z(t?.key)]: t?.value }), !0;
		case "gm_deleteValue": return await f(z(t?.key)), !0;
		case "gm_listValues": {
			let e = await u(null);
			return Object.keys(e ?? {});
		}
		case "gm_getValues": return await u(t?.defaults ?? {});
		default: throw Error(`Unknown storage action: ${e}`);
	}
}
function De() {
	r?.runtime?.onMessage?.addListener?.((e, t, n) => {
		if (Te(e)) return (async () => {
			try {
				R(n, {
					ok: !0,
					result: await Ee(String(e.action ?? ""), e.payload)
				});
			} catch (e) {
				R(n, {
					ok: !1,
					error: L(e)
				});
			}
		})(), !0;
	});
}
//#endregion
//#region src/extension/shared/bodySerialization.ts
var B = typeof Uint8Array.prototype.toBase64 == "function" ? Uint8Array.prototype.toBase64 : null, V = Uint8Array.fromBase64, Oe = /[-_]/;
function ke(e) {
	let t = String(e).replaceAll(/\s+/g, ""), n = t.length % 4;
	if (n === 1) throw TypeError("Invalid base64 input.");
	return n === 0 ? t : t + "=".repeat(4 - n);
}
function Ae(e) {
	let t = "";
	for (let n of e) t += String.fromCharCode(n);
	return t;
}
function H(e) {
	if (typeof B == "function") return B.call(e);
	let t = globalThis.btoa;
	if (typeof t != "function") throw TypeError("Base64 encoder is not available in this environment.");
	return t(Ae(e));
}
function je(e) {
	return H(new Uint8Array(e));
}
function U(e) {
	let t = ke(e), n = t.replaceAll("-", "+").replaceAll("_", "/");
	if (typeof V == "function") {
		let e = Oe.test(t);
		return V(e ? n : t, e ? { alphabet: "base64" } : void 0);
	}
	let r = globalThis.atob;
	if (typeof r != "function") throw TypeError("Base64 decoder is not available in this environment.");
	let i = r(n), a = new Uint8Array(i.length);
	for (let e = 0; e < i.length; e += 1) a[e] = i.charCodeAt(e);
	return a;
}
var W = 1e7, Me = 12, Ne = 2;
function G(e) {
	return typeof e == "object" && !!e;
}
function Pe(e) {
	return K(e) === "[object Object]";
}
function K(e) {
	try {
		return Object.prototype.toString.call(e);
	} catch {
		return null;
	}
}
function q(e) {
	return K(e) === "[object ArrayBuffer]";
}
function Fe(e) {
	try {
		let t = e?.constructor?.name;
		return typeof t == "string" ? t : null;
	} catch {
		return null;
	}
}
function J(e, t) {
	try {
		let n = e?.[t];
		return typeof n == "string" ? n : null;
	} catch {
		return null;
	}
}
function Y(e) {
	if (K(e) === "[object Blob]") return !0;
	if (!e || typeof e != "object") return !1;
	try {
		let t = e, n = typeof t.arrayBuffer == "function", r = typeof t.slice == "function", i = typeof t.size == "number", a = typeof t.type == "string";
		return (n || r) && i && a;
	} catch {
		return !1;
	}
}
function Ie(e) {
	try {
		return JSON.stringify(e);
	} catch {
		return null;
	}
}
function X(e) {
	if (typeof e != "number" || !Number.isFinite(e)) return null;
	let t = Math.trunc(e);
	return t < 0 || t > W ? null : t;
}
function Z(e) {
	return typeof e != "number" || !Number.isFinite(e) ? null : Math.trunc(e) & 255;
}
function Le(e) {
	let t = e.length;
	if (t > W) return null;
	let n = new Uint8Array(t);
	for (let r = 0; r < t; r += 1) {
		let t = Z(e[r]);
		if (t === null) return null;
		n[r] = t;
	}
	return n;
}
function Re(e) {
	return new Uint8Array(e.buffer, e.byteOffset, e.byteLength);
}
function ze(e) {
	return Re(e).slice();
}
function Be(e) {
	if (e === "") return null;
	let t = Number(e);
	return !Number.isFinite(t) || !Number.isInteger(t) || t < 0 ? null : t;
}
function Ve(e) {
	if (!G(e)) return !1;
	let t = e;
	return t.__votExtBody === !0 && typeof t.b64 == "string";
}
function Q(e, t = 0) {
	if (t > Ne || !G(e)) return null;
	let n = e;
	if (q(e)) return new Uint8Array(e);
	try {
		if (ArrayBuffer.isView(e)) return ze(e);
	} catch {}
	return Array.isArray(e) ? Le(e) : He(n) || Ue(n) || We(n, e, t) || Ge(n) || (Pe(e) ? Ke(n) : null);
}
function He(e) {
	let t = [
		e.type === "Buffer" && Array.isArray(e.data) ? e.data : null,
		Array.isArray(e.data) ? e.data : null,
		Array.isArray(e.bytes) ? e.bytes : null
	];
	for (let e of t) {
		if (!e) continue;
		let t = Le(e);
		if (t) return t;
	}
	return null;
}
function Ue(e) {
	let t = [e.b64, e.base64];
	for (let e of t) if (typeof e == "string") try {
		return U(e);
	} catch {}
	return null;
}
function We(e, t, n) {
	let r = X(e.byteLength), i = X(e.byteOffset ?? 0);
	if (r === null || i === null) return null;
	let a = e.buffer;
	if (q(a)) try {
		return new Uint8Array(a, i, r).slice();
	} catch {}
	if (!a || a === t) return null;
	let o = Q(a, n + 1);
	if (!o || i > o.byteLength) return null;
	let s = Math.min(o.byteLength, i + r);
	return o.slice(i, s);
}
function Ge(e) {
	let t = X(e.length);
	if (t === null) return null;
	let n = e, r = new Uint8Array(t);
	for (let e = 0; e < t; e += 1) {
		let t = Z(n[e]);
		if (t === null) return null;
		r[e] = t;
	}
	return r;
}
function Ke(e) {
	let t = Object.keys(e);
	if (!t.length) return null;
	let n = Array(t.length), r = -1;
	for (let e = 0; e < t.length; e += 1) {
		let i = Be(t[e]);
		if (i === null || i > W) return null;
		n[e] = i, i > r && (r = i);
	}
	let i = new Uint8Array(r + 1);
	for (let r = 0; r < t.length; r += 1) {
		let a = Z(e[t[r]]);
		if (a === null) return null;
		i[n[r]] = a;
	}
	return i;
}
function $(e) {
	let t = e === null ? "null" : typeof e, n = K(e), r = Fe(e), i = qe(e, t, n, r);
	if (i) return i;
	let a = Je(e, t, n, r);
	if (a) return a;
	let o = Ye(e, t, n, r);
	if (o) return o;
	let s = Q(e);
	if (s) return {
		kind: "coerced-bytes",
		jsType: t,
		tag: n,
		ctor: r,
		byteLength: s.byteLength
	};
	if (G(e)) {
		let i = Object.keys(e);
		return {
			kind: "object",
			jsType: t,
			tag: n,
			ctor: r,
			keyCount: i.length,
			keys: i.slice(0, Me)
		};
	}
	return {
		kind: "primitive",
		jsType: t,
		tag: n,
		ctor: r
	};
}
function qe(e, t, n, r) {
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
function Je(e, t, n, r) {
	return Ve(e) ? {
		kind: `serialized:${typeof e.kind == "string" && e.kind ? e.kind : "bytes"}`,
		jsType: t,
		tag: n,
		ctor: r,
		base64Length: e.b64.length,
		mime: J(e, "mime")
	} : null;
}
function Ye(e, t, n, r) {
	if (q(e)) return {
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
	return Y(e) ? {
		kind: "BlobLike",
		jsType: t,
		tag: n,
		ctor: r,
		byteLength: typeof e.size == "number" ? Number(e.size) : -1,
		mime: J(e, "type")
	} : null;
}
function Xe(e) {
	if (e == null) return;
	if (typeof e == "string" || q(e)) return e;
	try {
		if (ArrayBuffer.isView(e)) return Re(e);
	} catch {}
	if (Y(e)) return e;
	if (Ve(e)) return Ze(e);
	let t = Q(e);
	if (t) return t;
	if (G(e)) {
		let t = Ie(e);
		if (typeof t == "string") return t;
	}
	return String(e);
}
function Ze(e) {
	let t = U(e.b64);
	if ((typeof e.kind == "string" && e.kind ? e.kind : "bytes") !== "blob") return t;
	let n = e.mime, r = t.buffer;
	return typeof n == "string" && n ? new Blob([r], { type: n }) : new Blob([r]);
}
//#endregion
//#region src/extension/background/xhr-handler.ts
var Qe = 524288, $e = /application\/(?:x-)?protobuf|application\/octet-stream/, et = /^(?=.*[+/=_-])[A-Za-z0-9+/=_-]+$/;
function tt(e) {
	return Array.from(e.entries()).map(([e, t]) => `${e}: ${t}`).join("\r\n");
}
function nt(e) {
	let t = {};
	if (!e) return t;
	for (let [n, r] of Object.entries(e)) r !== void 0 && (typeof r == "string" || typeof r == "number" || typeof r == "boolean") && (t[String(n)] = String(r));
	return t;
}
function rt(e, t) {
	return {
		finalUrl: e,
		readyState: 4,
		status: 0,
		statusText: "",
		responseHeaders: "",
		response: null,
		responseText: "",
		error: t
	};
}
function it(e) {
	let t = new Uint8Array(e.byteLength);
	return t.set(e), t.buffer;
}
function at(e) {
	return o ? { chunk: it(e) } : { chunkB64: H(e) };
}
function ot(e) {
	return o ? { response: e } : e.byteLength <= 0 ? {} : { responseB64: je(e) };
}
function st(e, t) {
	let n = t.toLowerCase();
	for (let [t, r] of Object.entries(e)) if (String(t).toLowerCase() === n) return String(r);
}
function ct(e) {
	return $e.test(String(e ?? "").toLowerCase());
}
function lt(e) {
	let t = String(e ?? "");
	if (!et.test(t)) return null;
	let n = t.replaceAll("-", "+").replaceAll("_", "/"), r = n.length % 4;
	if (r === 1) return null;
	let i = r === 0 ? n : `${n}${"=".repeat(4 - r)}`;
	try {
		return U(i);
	} catch {
		return null;
	}
}
function ut(e) {
	let t = String(e || ""), n = new Uint8Array(t.length);
	for (let e = 0; e < t.length; e += 1) n[e] = (t.codePointAt(e) ?? 0) & 255;
	return n;
}
function dt(e) {
	return /^\[object [^\]]+\]$/.test(String(e || "").trim());
}
function ft(t, n, r, i, a) {
	let o = Q(t.data);
	if (n == null && o) return e.warn("[VOT EXT][background][xhr] protobuf body recovered from raw payload", {
		xhrSessionId: r,
		url: i,
		method: a,
		recoveredBody: $(o)
	}), o;
	if (typeof n == "string") {
		if (dt(n) && o) return e.warn("[VOT EXT][background][xhr] recovered protobuf body from object-like string fallback", {
			xhrSessionId: r,
			url: i,
			method: a,
			sourceBody: $(t.data),
			recoveredBody: $(o)
		}), o;
		let s = lt(n), c = s ?? ut(n);
		return e.log("[VOT EXT][background][xhr] protobuf string converted to bytes", {
			xhrSessionId: r,
			url: i,
			method: a,
			strategy: s ? "base64" : "latin1",
			convertedBody: $(c)
		}), c;
	}
	return n;
}
async function pt(e, t, n, r) {
	if (t === "arraybuffer" || t === "blob" || t === "stream") return await mt(e, n, r);
	if (t === "json") {
		let t = await e.text(), n;
		try {
			n = JSON.parse(t);
		} catch {
			n = null;
		}
		return {
			response: n,
			responseText: t
		};
	}
	let i = await e.text();
	return {
		response: i,
		responseText: i
	};
}
async function mt(e, t, n) {
	let r = Number(e.headers.get("content-length") || 0);
	if (e.body && (!Number.isFinite(r) || r > Qe)) {
		let i = 0, a = Number.isFinite(r) ? r : 0, o = e.body?.getReader();
		for (;;) {
			let { done: e, value: r } = await o.read();
			if (e) break;
			r && (i += r.byteLength, n({
				type: "progress",
				state: "in_flight",
				progress: {
					...t(3),
					loaded: i,
					total: a,
					lengthComputable: a > 0,
					...at(r)
				}
			}));
		}
		return { response: void 0 };
	}
	let i = ot(await e.arrayBuffer());
	return {
		response: i.response,
		responseB64: i.responseB64
	};
}
function ht() {
	let t = 0;
	r?.runtime?.onConnect?.addListener?.((n) => {
		if (!n || typeof n != "object") return;
		let r = n;
		if (r.name !== "vot_gm_xhr" || typeof r.onDisconnect?.addListener != "function" || typeof r.onMessage?.addListener != "function" || typeof r.postMessage != "function") return;
		let i = (e) => {
			r.postMessage?.(e);
		};
		t += 1;
		let a = String(t), o = null, s = null, c = !1, l = !1, u = () => {
			s !== null && (clearTimeout(s), s = null);
		};
		r.onDisconnect.addListener(() => {
			u(), e.warn("[VOT EXT][background][xhr] port disconnected", { xhrSessionId: a }), d();
		});
		let d = () => {
			try {
				o?.abort();
			} catch {}
		}, f = () => {
			c = !0, e.warn("[VOT EXT][background][xhr] abort requested", { xhrSessionId: a }), d();
		}, p = (e) => {
			let t = nt(e.headers), n = {}, r = {};
			for (let [e, i] of Object.entries(t)) ie(e) ? r[e] = i : n[e] = i;
			return {
				allHeaders: t,
				headers: n,
				forbiddenHeaders: r
			};
		}, m = (e) => {
			let t = {
				finalUrl: e,
				readyState: 4,
				status: 0,
				statusText: "",
				responseHeaders: "",
				response: null,
				responseText: "",
				error: "Aborted"
			};
			try {
				i({
					type: "abort",
					state: "terminal",
					error: t
				});
			} catch {}
		}, ee = (e) => e.anonymous || e.withCredentials === !1 ? "omit" : "include", te = (e) => {
			if (e.nocache) return "no-store";
			if (e.revalidate) return "no-cache";
		}, h = (t, n, r, i) => {
			if (n === "GET") return;
			let o = st(r, "content-type"), s = ct(o), c = Xe(t.data);
			return e.log("[VOT EXT][background][xhr] body decoded", {
				xhrSessionId: a,
				url: i,
				method: n,
				contentType: o ?? null,
				isProtobufRequest: s,
				sourceBody: $(t.data),
				decodedBody: $(c)
			}), s && (c = ft(t, c, a, i, n)), c;
		}, g = (e) => {
			let t = {
				method: e.method,
				headers: e.headers,
				redirect: e.redirect,
				credentials: e.credentials,
				signal: o?.signal
			};
			return e.body !== void 0 && (t.body = e.body), e.cache !== void 0 && (t.cache = e.cache), t;
		}, _ = async (t) => {
			let { url: n, method: r, responseType: o, res: s } = t;
			e.log("[VOT EXT][background][xhr] fetch response received", {
				xhrSessionId: a,
				url: s.url || n,
				method: r,
				status: s.status,
				statusText: s.statusText,
				responseType: o,
				contentType: s.headers.get("content-type") || null,
				contentLength: s.headers.get("content-length") || null
			});
			let c = tt(s.headers), l = s.url || n, d = s.headers.get("content-type") || "", f = (e) => ({
				finalUrl: l,
				readyState: e,
				status: s.status,
				statusText: s.statusText,
				responseHeaders: c
			}), p = await pt(s, o, f, i);
			u(), e.log("[VOT EXT][background][xhr] terminal", {
				xhrSessionId: a,
				state: "terminal",
				kind: "load",
				url: l,
				status: s.status,
				responseType: o,
				responseBody: $(p.response),
				responseTextLength: p.responseText?.length ?? 0,
				responseB64Length: p.responseB64?.length ?? 0
			}), i({
				type: "load",
				state: "terminal",
				response: {
					...f(4),
					responseType: o,
					...d ? { contentType: d } : {},
					...p.responseB64 ? { responseB64: p.responseB64 } : {},
					response: p.response,
					...typeof p.responseText == "string" ? { responseText: p.responseText } : {}
				}
			});
		}, v = (t, n, r, o) => {
			if (u(), c || l || o instanceof DOMException && o.name === "AbortError") {
				let o = l ? "timeout" : "abort", s = rt(t, o === "timeout" ? "Timeout" : "Aborted");
				try {
					i({
						type: o,
						state: "terminal",
						error: s
					});
				} catch {}
				e.warn("[VOT EXT][background][xhr] terminal", {
					xhrSessionId: a,
					state: "terminal",
					kind: o,
					url: t,
					method: n,
					responseType: r
				});
				return;
			}
			let s = rt(t, L(o));
			i({
				type: "error",
				state: "terminal",
				error: s
			}), e.error("[VOT EXT][background][xhr] terminal", {
				xhrSessionId: a,
				state: "terminal",
				kind: "error",
				url: t,
				method: n,
				responseType: r,
				error: s.error
			});
		}, y = async (t) => {
			let n = c && o === null;
			c = !1;
			let { details: r } = t, i = r.url, u = (r.method || "GET").toUpperCase(), { allHeaders: f, headers: y, forbiddenHeaders: b } = p(r), x = Number(r.timeout || 0), S = String(r.responseType || "text").toLowerCase();
			if (e.log("[VOT EXT][background][xhr] start", {
				xhrSessionId: a,
				state: "in_flight",
				url: i,
				method: u,
				responseType: S,
				timeoutMs: x,
				headerCount: Object.keys(f).length,
				headerNames: Object.keys(f),
				forbiddenHeaderNames: Object.keys(b),
				body: $(r.data)
			}), n) {
				m(i);
				return;
			}
			l = !1, o = new AbortController(), x > 0 && (s = setTimeout(() => {
				l = !0, d();
			}, x));
			let C = ee(r), w = te(r), T = r.redirect === "error" || r.redirect === "manual" ? r.redirect : "follow";
			try {
				try {
					await se(i, b), await oe(i, b), await ae(i, b);
				} catch (e) {
					console.warn("[VOT Extension] Failed to apply DNR header rules; requests may break:", e);
				}
				let t = h(r, u, f, i);
				e.log("[VOT EXT][background][xhr] fetch dispatch", {
					xhrSessionId: a,
					url: i,
					method: u,
					credentials: C,
					redirect: T,
					cache: w ?? "default",
					body: $(t)
				});
				let n = await fetch(i, g({
					method: u,
					headers: y,
					redirect: T,
					credentials: C,
					body: t,
					cache: w
				}));
				await _({
					url: i,
					method: u,
					responseType: S,
					res: n
				});
			} catch (e) {
				v(i, u, S, e);
			}
		};
		r.onMessage.addListener(async (e) => {
			if (!(!e || typeof e != "object")) {
				if (e.type === "abort") {
					f();
					return;
				}
				e.type === "start" && await y(e);
			}
		});
	});
}
De(), we(), ht(), ce();
//#endregion
