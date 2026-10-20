// Pathivu site: hero clock, incident simulator, time machine, real refusals,
// tamper lab on the real audit chain, and the Java-vs-JavaScript proof.
(() => {
  const $ = (id) => document.getElementById(id);
  const P = Pathivu, MIN = 60000, HOUR = 60 * MIN;
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  const load = (f) => fetch(f).then((r) => { if (!r.ok) throw new Error(f + " " + r.status); return r; });
  let RULES = null;

  /* ---------------- hero: a demo CERT-In clock that started 2 hours ago ---------------- */
  const demoAware = Date.now() - (2 * HOUR + 3 * MIN);
  const demoDue = demoAware + 6 * HOUR;
  $("heroDue").textContent = "due " + P.ist(demoDue);
  window.PathivuDemo = { aware: demoAware, due: demoDue };   // the 3D dial follows the same clock
  function heroTick() {
    const left = demoDue - Date.now();
    $("heroLeft").textContent = P.left(left).replace(" left", "");
    $("heroArc").style.strokeDashoffset = String(106.8 * (1 - Math.max(0, left) / (6 * HOUR)));
  }
  heroTick(); setInterval(heroTick, 1000);

  /* ---------------- simulator ---------------- */
  const sim = {
    kind: "AGENT", tags: new Set(),
    harms: [{ category: "DATA_LOSS", personalData: "YES" }, { category: "SERVICE_OUTAGE", personalData: "NO" }],
  };
  const TITLES = { AGENT: "Coding agent dropped the production bookings database",
                   MODEL_HARM: "Triage model ranked samples from rural clinics lower",
                   DATA: "Support bot revealed other riders' phone numbers" };
  const toLocalIST = (ms) => new Date(ms + 330 * MIN).toISOString().slice(0, 16);
  const fromLocalIST = (v) => Date.parse(v + ":00Z") - 330 * MIN;
  $("aware").value = toLocalIST(Date.now() - 2 * HOUR);

  function incident() {
    const awareMs = fromLocalIST($("aware").value) || Date.now();
    const inc = {
      kind: sim.kind, title: $("title").value || "Untitled", description: "Filed from the website demo",
      aware: new Date(awareMs).toISOString(), adversary: $("adversary").value, cyber: $("cyber").value,
      tags: [...sim.tags], harms: sim.harms.slice(),
    };
    if (sim.kind === "AGENT") inc.agent = { name: "Agent", instruction: "fix the failing deploy",
      action: $("cred").checked ? "deleted production data" : "changed a configuration file",
      credentialBeyondTask: $("cred").checked, confirmationStep: $("confirm").checked };
    if (sim.kind === "MODEL_HARM") { inc.outputType = $("output").value; inc.affectedGroup = "users"; }
    if (sim.kind === "DATA") inc.data = { categories: "phone numbers", records: Number($("records").value) || 0, exfiltrated: "UNSURE" };
    return inc;
  }

  const WHO = { CERTIN_6H: "Report to CERT-In", DPDP_R7_PRINCIPALS: "Tell affected people",
                DPDP_R7_BOARD_INITIAL: "First intimation to the Board", DPDP_R7_BOARD_72H: "Detailed report to the Board" };

  function clockRows(rules, inc, now) {
    const aware = Date.parse(inc.aware);
    return rules.map((r) => {
      const ex = P.explain(r, inc, aware);
      if (!ex.applies) return `<li class="off"><span class="who">${WHO[r.code]}</span><span class="st off">no clock</span>
        <span class="rule">${r.code} · ${P.windowText(r)}</span><span class="why">${esc(ex.why)}</span></li>`;
      const due = P.dueAt(r, aware), left = due - now;
      if (aware > now) return `<li><span class="who">${WHO[r.code]}</span><span class="st run">would start</span>
        <span class="rule">${r.code} · ${P.windowText(r)} · due ${P.ist(due)}</span>
        <span class="why">${esc(ex.why)}. A preview: the real app refuses an awareness time in the future.</span></li>`;
      const over = left < 0;
      return `<li><span class="who">${WHO[r.code]}</span><span class="st ${over ? "over" : "run"}">${over ? "overdue" : "running"}</span>
        <span class="rule">${r.code} · ${P.windowText(r)} · due ${P.ist(due)}</span>
        <span class="why">${esc(P.left(left))} · ${esc(ex.why)}</span></li>`;
    }).join("");
  }

  function render() {
    if (!RULES) return;
    const inc = incident(), now = Date.now();
    $("clocks").innerHTML = clockRows(RULES, inc, now);
    // mappings grouped by framework, in registry order
    const sug = P.suggestAll(inc), groups = {};
    sug.forEach((s) => (groups[s.framework] ||= []).push(s));
    $("maps").innerHTML = Object.keys(P.FRAMEWORK_LABEL).map((f) => `<div class="fw"><span class="label bare">${P.FRAMEWORK_LABEL[f]}</span>${
      (groups[f] || []).map((s) => `<div class="map"><code>${esc(s.code)}</code><span class="nm">${esc(P.NODE_NAMES[s.code] || s.code)}</span><span class="cf">${s.confidence}</span><p class="rs">${esc(s.rationale)}</p></div>`).join("")
      || '<p class="fine">Nothing suggested. A reviewer can still propose one by hand.</p>'}</div>`).join("");
    // workflow: TRIAGED -> ASSESSED by a reviewer who did not report it
    const unsure = inc.harms.filter((h) => h.personalData === "UNSURE").length;
    const facts = [inc.harms.length, $("fEvidence").checked ? 1 : 0, $("fConfirmed").checked ? 1 : 0, sug.length ? 1 : 0, unsure, 1, 1];
    const b = P.blockers({ status: "TRIAGED", cyber: inc.cyber }, "ASSESSED", { id: 2, role: "REVIEWER" }, facts, 1);
    $("blockers").innerHTML = b.length ? b.map((x) => `<li>${esc(x)}</li>`).join("") : '<li class="yes">Allowed. The reviewer can move it to Assessed.</li>';
    // harms list
    $("harms").innerHTML = sim.harms.length ? sim.harms.map((h, i) => `<li><b>${h.category}</b><span>personal data: ${h.personalData.toLowerCase()}</span><button type="button" data-rm="${i}" aria-label="Remove harm">×</button></li>`).join("")
      : '<li class="none">No harms yet. For a data incident, that alone keeps DPDP clocks possible.</li>';
  }

  $("kind").addEventListener("click", (e) => {
    const b = e.target.closest("[data-v]"); if (!b) return;
    sim.kind = b.dataset.v;
    $("kind").querySelectorAll(".chip").forEach((c) => { c.classList.toggle("on", c === b); c.setAttribute("aria-checked", c === b); });
    document.querySelectorAll(".kindbox").forEach((k) => (k.hidden = k.dataset.kind !== sim.kind));
    $("title").value = TITLES[sim.kind];
    if (sim.kind === "DATA") sim.harms = [{ category: "DATA_EXPOSURE", personalData: "YES" }];
    if (sim.kind === "MODEL_HARM") sim.harms = [{ category: "DISCRIMINATION", personalData: "NO" }];
    if (sim.kind === "AGENT") sim.harms = [{ category: "DATA_LOSS", personalData: "YES" }, { category: "SERVICE_OUTAGE", personalData: "NO" }];
    render();
  });
  $("tags").addEventListener("click", (e) => {
    const b = e.target.closest("[data-v]"); if (!b) return;
    const on = !sim.tags.has(b.dataset.v);
    on ? sim.tags.add(b.dataset.v) : sim.tags.delete(b.dataset.v);
    b.setAttribute("aria-pressed", on); render();
  });
  document.querySelector(".presets").addEventListener("click", (e) => {
    const b = e.target.closest("button"); if (!b) return;
    $("aware").value = b.dataset.ago ? toLocalIST(Date.now() - Number(b.dataset.ago) * MIN) : b.dataset.at;
    render();
  });
  $("addHarm").addEventListener("click", () => { sim.harms.push({ category: $("hcat").value, personalData: $("hpd").value }); render(); });
  $("harms").addEventListener("click", (e) => { const b = e.target.closest("[data-rm]"); if (b) { sim.harms.splice(Number(b.dataset.rm), 1); render(); } });
  $("simForm").addEventListener("input", render);
  $("simForm").addEventListener("change", render);
  $("simForm").addEventListener("submit", (e) => e.preventDefault());
  ["fEvidence", "fConfirmed"].forEach((id) => $(id).addEventListener("change", render));
  setInterval(render, 30000);

  /* ---------------- time machine ---------------- */
  const T0 = Date.UTC(2022, 0, 1), T1 = Date.UTC(2028, 0, 1);
  const tmInc = { kind: "DATA", title: "Support bot revealed phone numbers", description: "x", adversary: "SUSPECTED",
                  cyber: "UNSURE", tags: [], harms: [{ category: "DATA_EXPOSURE", personalData: "YES" }],
                  data: { categories: "phone numbers", records: 37, exfiltrated: "UNSURE" } };
  function renderTM() {
    if (!RULES) return;
    const t = T0 + (T1 - T0) * ($("tm").value / 1000);
    const at = Math.round(t / (30 * MIN)) * 30 * MIN;
    $("tmWhen").textContent = P.ist(at);
    $("tmRules").innerHTML = RULES.map((r) => {
      const inForce = P.inForceAt(r, at);
      const due = P.dueAt(r, at);
      return `<li><span class="who">${WHO[r.code]}</span><span class="st ${inForce ? "run" : "off"}">${inForce ? "clock starts" : "not law yet"}</span>
        <span class="rule">${inForce ? "due " + (r.windowMinutes == null ? "immediately, " + P.ist(due) : P.ist(due)) : "in force from " + P.ist(Date.parse(r.effectiveFrom))}</span></li>`;
    }).join("");
  }
  $("tm").value = String(Math.round(((Date.now() - T0) / (T1 - T0)) * 1000));   // start at today
  $("tm").addEventListener("input", renderTM);

  /* ---------------- refusals (real output of the Java test suite) ---------------- */
  load("refusals.jsonl").then((r) => r.text()).then((txt) => {
    const rows = txt.trim().split("\n").map((l) => JSON.parse(l));
    $("refusals").innerHTML = rows.map((x) => {
      const sql = x.sql.length > 170 ? x.sql.slice(0, 167) + "…" : x.sql;
      return `<article class="refusal"><h3>${esc(x.test.charAt(0).toUpperCase() + x.test.slice(1))}</h3><pre>${esc(sql)}</pre>
        <p class="reply"><small>MySQL ${x.code} · ${esc(x.state)}</small>${esc(x.message)}</p></article>`;
    }).join("");
  }).catch(() => { $("refusals").innerHTML = '<p class="body">Could not load the refusals file.</p>'; });

  /* ---------------- tamper lab ---------------- */
  let original = [], chain = [], anchor = null, editedId = null;
  const target = 25;
  async function verify(events) {
    let prev = "0".repeat(64);
    const res = [];
    for (const e of events) {
      const h = await P.hashEvent(e.prev, e.incident, e.actorId, e.action, e.details, Date.parse(e.at));
      res.push({ link: e.prev === prev, self: h === e.hash });
      prev = e.hash;
    }
    return res;
  }
  async function drawChain(note) {
    const res = await verify(chain);
    const firstBad = res.findIndex((r) => !r.link || !r.self);
    $("chainRows").innerHTML = chain.map((e, i) => {
      const r = res[i], ok = r.link && r.self;
      const cls = (e.id === editedId ? "bad " : "") + (e.rehashed ? "rehashed" : "");
      return `<tr class="${cls}"><td>${e.id}</td><td>${esc(e.actor)}</td><td>${esc(e.action)}</td><td>${esc(e.details)}</td><td>${e.hash.slice(0, 10)}…</td><td class="${ok ? "p" : "f"}">${ok ? "✓" : "✗"}</td></tr>`;
    }).join("");
    const m = $("tMsg");
    if (firstBad < 0) { m.className = "msg ok"; m.textContent = note || `All ${chain.length} events verified. The chain is intact.`; }
    else { m.className = "msg bad"; m.textContent = `Event ${chain[firstBad].id} ${res[firstBad].self ? "no longer links to the event before it" : "was changed after it was written"}. verify() points at it exactly.`; }
    return firstBad < 0;
  }
  load("audit-sample.json").then((r) => r.json()).then((d) => {
    original = d.events.map((e) => ({ ...e }));
    chain = original.map((e) => ({ ...e }));
    const last = chain[chain.length - 1];
    anchor = { id: last.id, hash: last.hash };   // a copy, kept "outside the database"
    $("evCount").textContent = chain.length;
    $("tAnchorText").textContent = `anchor saved before the attack: event=${anchor.id} hash=${anchor.hash.slice(0, 24)}…`;
    drawChain();
  });
  $("tEdit").addEventListener("click", () => {
    const e = chain.find((x) => x.id === target); if (!e) return;
    e.details = "CERTIN_6H: never mind"; editedId = target;
    drawChain().then(() => {
      const row = [...$("chainRows").rows].find((r) => r.cells[0].textContent === String(target));
      if (row) row.parentElement.parentElement.parentElement.scrollTop = row.offsetTop - 80;
    });
  });
  $("tRehash").addEventListener("click", async () => {
    if (editedId == null) { $("tMsg").className = "msg"; $("tMsg").textContent = "Edit an event first."; return; }
    let prev = null;
    for (const e of chain) {
      if (e.id < editedId) { prev = e.hash; continue; }
      e.prev = prev;
      e.hash = await P.hashEvent(e.prev, e.incident, e.actorId, e.action, e.details, Date.parse(e.at));
      e.rehashed = true; prev = e.hash;
    }
    drawChain("Every hash checks out again. verify() alone is fooled: this is the honest limit of a hash chain.");
  });
  $("tAnchor").addEventListener("click", async () => {
    const ok = await drawChain();
    const now = chain.find((x) => x.id === anchor.id);
    const m = $("tMsg");
    if (now && now.hash === anchor.hash) { m.className = ok ? "msg ok" : "msg bad"; m.textContent = ok ? `Anchor matches event ${anchor.id}: history is exactly as it was.` : m.textContent; }
    else { m.className = "msg bad"; m.textContent = `Anchor does NOT match event ${anchor.id}. History before it was rewritten and re-hashed. Caught.`; }
  });
  $("tReset").addEventListener("click", () => { chain = original.map((e) => ({ ...e })); editedId = null; drawChain(); });

  /* ---------------- proof: JavaScript port vs the Java engine ---------------- */
  load("vectors.json").then((r) => r.json()).then((V) => {
    RULES = V.rules;
    render(); renderTM();
    let ok = 0;
    const rows = V.cases.map((c, i) => {
      const inc = c.input, e = c.expect, w = e.workflow;
      const clocks = P.applicable(V.rules, inc).map((x) => ({ code: x.rule.code, due: new Date(x.due).toISOString().replace(".000Z", "Z") }));
      const sug = P.suggestAll(inc).map((s) => [s.framework, s.code, s.confidence]);
      const bl = P.blockers({ status: w.from, cyber: w.cyber }, w.to, w.user, w.facts, 1);
      const a = JSON.stringify(clocks) === JSON.stringify(e.clocks), b = JSON.stringify(sug) === JSON.stringify(e.suggestions),
            d = JSON.stringify(bl) === JSON.stringify(w.blockers);
      if (a && b && d) ok++;
      return `<tr><td>${esc(inc.kind)} · ${inc.aware.slice(0, 10)} · ${esc(inc.title)}</td><td>${e.clocks.length}</td><td>${e.suggestions.length}</td><td>${w.blockers.length ? w.blockers.length + " block" : "allowed"}</td><td class="${a && b && d ? "p" : "f"}">${a && b && d ? "match" : "DIFF"}</td></tr>`;
    });
    $("rows").innerHTML = rows.join("");
    $("caseCount").textContent = V.cases.length;
    $("passed").textContent = ok + "/" + V.cases.length;
    window.__check = { vectors: ok + "/" + V.cases.length };
    $("tallytext").textContent = ok === V.cases.length ? "cases match the Java engine, in this browser" : "cases match: something differs";
  }).catch(() => { $("tallytext").textContent = "could not load the test vectors"; });
})();
