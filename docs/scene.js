// Pathivu's world: a stack of register slabs inside a six-hour dial that follows the
// demo CERT-In clock, with the audit hash-chain behind it. In the tamper chapter one
// link is pulled out of the chain.
import { createWorld, THREE, smooth, textTexture } from "./world.js";

const ACC = new THREE.Color("#9e1b32");
const INK = new THREE.Color("#15120e");
const PAPER = new THREE.Color("#efe7d7");

// Keyframes per stage: [x, y, z, scale, fan, pull, dialTilt, camX, camY, camZ]
const K = [
  /* 0 hero    */ [2.7, 0.2, -0.6, 0.95, 0.0, 0, 0.05, 0, 0.6, 9.6],
  /* 1 problem */ [-3.4, -0.2, -1.2, 0.62, 0.35, 0, 0.4, 0, 0.7, 10.0],
  /* 2 layers  */ [3.2, 1.3, -2.6, 0.55, 1.0, 0, 0.9, 0, 0.9, 10.2],
  /* 3 try     */ [3.4, 1.6, -3.4, 0.5, 0.4, 0, 0.6, 0, 1.0, 10.6],
  /* 4 law     */ [0.4, 2.5, -5.5, 0.5, 0.15, 0, 0.15, 0, 0.8, 10.0],
  /* 5 refused */ [2.6, 0.3, -1.6, 0.8, 0.0, 0, 0.5, 0, 0.9, 10.0],
  /* 6 tamper  */ [3.3, 1.6, -2.8, 0.55, 0.2, 1, 0.7, 0, 1.0, 10.4],
  /* 7 proof   */ [3.6, 2.6, -5.0, 0.45, 0.0, 0, 0.8, 0, 1.1, 10.4],
  /* 8 run     */ [3.0, 1.5, -3.0, 0.55, 0.3, 0, 0.4, 0, 1.3, 11.0],
  /* end       */ [2.6, 0.3, -1.5, 0.95, 0.0, 0, 0.9, 0, 0.8, 10.0],
];
const MOBILE = (k, i) => i === 0 ? [1.1, 3.3, -1.5, 0.5, k[4], k[5], k[6], 0, 0.6, 12.5] : [1.3, 3.4, -2.5, 0.42, k[4], k[5], k[6], 0, 0.6, 12.5];

function frame(stage, mobile) {
  const i = Math.max(0, Math.min(K.length - 2, Math.floor(stage)));
  const f = smooth(0.15, 0.85, stage - i);
  const a = mobile ? MOBILE(K[i], i) : K[i], b = mobile ? MOBILE(K[i + 1], i + 1) : K[i + 1];
  return a.map((v, j) => v + (b[j] - v) * f);
}

createWorld(document.getElementById("world"), async ({ scene, camera, mobile }) => {
  await document.fonts?.load("500 64px 'Geist Mono'").catch(() => {});

  /* ---------- the register: six record slabs with a crimson spine ---------- */
  const reg = new THREE.Group();
  scene.add(reg);
  const paper = new THREE.MeshPhysicalMaterial({ color: PAPER, roughness: 0.85, sheen: 0.3 });
  const spineMat = new THREE.MeshPhysicalMaterial({ color: ACC, roughness: 0.3, clearcoat: 1, clearcoatRoughness: 0.15 });
  const slabs = [];
  const refs = ["PTV-2026-0001", "PTV-2026-0002", "PTV-2026-0003", "PTV-2026-0004", "PTV-2026-0005", "PTV-2026-0006"];
  for (let i = 0; i < 6; i++) {
    const g = new THREE.Group();
    const body = new THREE.Mesh(new THREE.BoxGeometry(2.1, 0.13, 1.45), paper);
    const spine = new THREE.Mesh(new THREE.BoxGeometry(0.16, 0.15, 1.47), spineMat);
    spine.position.x = -1.06;
    [body, spine].forEach((m) => { m.castShadow = true; m.receiveShadow = true; g.add(m); });
    const tex = textTexture(refs[i], { font: "500 44px 'Geist Mono'", color: "#15120e", pad: 16 });
    const lbl = new THREE.Mesh(new THREE.PlaneGeometry(0.34 * tex.userData.aspect * 0.42, 0.14),
      new THREE.MeshBasicMaterial({ map: tex, transparent: true, depthWrite: false }));
    lbl.rotation.x = -Math.PI / 2; lbl.position.set(0.05, 0.068, 0.42);
    g.add(lbl);
    g.position.y = i * 0.15;
    reg.add(g); slabs.push(g);
  }

  /* ---------- the dial: 72 ticks over six hours, 6 major ---------- */
  const dial = new THREE.Group();
  scene.add(dial);
  const NT = 72, RD = 1.95;
  const ticks = new THREE.InstancedMesh(new THREE.BoxGeometry(0.03, 0.22, 0.03), new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 0.6 }), NT);
  ticks.castShadow = true; dial.add(ticks);
  const ringMat = new THREE.MeshStandardMaterial({ color: INK, roughness: 0.5 });
  dial.add(new THREE.Mesh(new THREE.TorusGeometry(RD + 0.26, 0.006, 8, 240), ringMat));
  dial.add(new THREE.Mesh(new THREE.TorusGeometry(RD - 0.24, 0.004, 8, 240), ringMat));
  const hand = new THREE.Mesh(new THREE.BoxGeometry(0.022, 0.55, 0.022), new THREE.MeshStandardMaterial({ color: ACC, roughness: 0.3 }));
  dial.add(hand);
  const hours = [];
  for (let h = 0; h < 6; h++) {
    const t = textTexture(h === 0 ? "6h" : String(h) + "h", { font: "500 48px 'Geist Mono'", color: "#15120e", pad: 8 });
    const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: t, transparent: true, depthWrite: false, opacity: 0.8 }));
    sp.scale.set(0.16 * t.userData.aspect, 0.16, 1);
    const a = Math.PI / 2 - (h / 6) * Math.PI * 2;
    sp.position.set(Math.cos(a) * (RD + 0.55), Math.sin(a) * (RD + 0.55), 0);
    dial.add(sp); hours.push(sp);
  }
  const m4 = new THREE.Matrix4(), q = new THREE.Quaternion(), s3 = new THREE.Vector3(), v3 = new THREE.Vector3(), zAxis = new THREE.Vector3(0, 0, 1), tc = new THREE.Color();

  /* ---------- the audit chain: links behind the register ---------- */
  const chain = new THREE.Group();
  scene.add(chain);
  const linkMat = new THREE.MeshStandardMaterial({ color: "#3a332b", roughness: 0.35, metalness: 0.6 });
  const badMat = new THREE.MeshStandardMaterial({ color: ACC, roughness: 0.3, metalness: 0.3, emissive: ACC, emissiveIntensity: 0.15 });
  const NL = 16, links = [];
  for (let i = 0; i < NL; i++) {
    const l = new THREE.Mesh(new THREE.TorusGeometry(0.22, 0.045, 12, 40), i === 9 ? badMat : linkMat);
    l.scale.set(1.5, 1, 1);
    l.castShadow = true;
    chain.add(l); links.push(l);
  }

  /* ---------- hex characters drifting, like hashes in the air ---------- */
  const HEX = "0123456789abcdef", mats = [];
  for (const c of HEX) {
    const a = textTexture(c, { font: "400 96px 'Geist Mono'", color: "#15120e", pad: 10 });
    const b = textTexture(c, { font: "500 96px 'Geist Mono'", color: "#9e1b32", pad: 10 });
    mats.push([new THREE.SpriteMaterial({ map: a, transparent: true, depthWrite: false, opacity: 0.45 }),
               new THREE.SpriteMaterial({ map: b, transparent: true, depthWrite: false, opacity: 0.85 }), a.userData.aspect]);
  }
  const rand = (() => { let s = 17; return () => ((s = (s * 16807) % 2147483647) / 2147483647); })();
  const glyphs = [];
  for (let i = 0; i < (mobile ? 40 : 90); i++) {
    const [mi, ma, asp] = mats[Math.floor(rand() * 16)];
    const sp = new THREE.Sprite(rand() < 0.15 ? ma : mi);
    const sz = 0.11 + rand() * 0.2;
    sp.scale.set(sz * asp, sz, 1);
    sp.position.set((rand() - 0.5) * 22, -1.4 + rand() * 7, -9 + rand() * 12);
    sp.userData = { v: 0.07 + rand() * 0.16, w: rand() * 6.28, base: sp.position.x };
    scene.add(sp); glyphs.push(sp);
  }

  const look = new THREE.Vector3();
  return (st) => {
    const [x, y, z, sc, fan, pull, tilt, cx, cy, cz] = frame(st.stage, st.mobile);
    const t = st.t;

    // register: slabs fan open in the "four layers" chapter
    reg.position.set(x, y - 0.35 + Math.sin(t * 0.7) * 0.05, z);
    reg.scale.setScalar(sc);
    reg.rotation.y = -0.55 + t * 0.12 + st.px * 0.3;
    reg.rotation.x = 0.12 + st.py * 0.1;
    slabs.forEach((g, i) => {
      g.position.y = i * (0.15 + fan * 0.16);
      g.rotation.y = fan * (i - 2.5) * 0.12;
      g.position.x = fan * Math.sin(i * 1.3) * 0.12;
    });

    // dial follows the demo CERT-In clock on the page
    const D = window.PathivuDemo;
    const frac = D ? Math.min(1, Math.max(0, (Date.now() - D.aware) / (D.due - D.aware))) : (t / 60) % 1;
    dial.position.set(x, y + 0.1, z);
    dial.scale.setScalar(sc);
    dial.rotation.set(-Math.PI / 2 + 0.38 + tilt, 0, 0);
    for (let i = 0; i < NT; i++) {
      const a = Math.PI / 2 - (i / NT) * Math.PI * 2;
      v3.set(Math.cos(a) * RD, Math.sin(a) * RD, 0);
      q.setFromAxisAngle(zAxis, a - Math.PI / 2);
      const major = i % 12 === 0, past = i / NT < frac, isNow = Math.floor(frac * NT) === i;
      s3.set(major ? 1.4 : 1, isNow ? 2.1 : major ? 1.5 : past ? 1.05 : 0.75, 1);
      m4.compose(v3, q, s3); ticks.setMatrixAt(i, m4);
      ticks.setColorAt(i, tc.copy(isNow ? ACC : past ? INK : PAPER));
    }
    ticks.instanceMatrix.needsUpdate = true; ticks.instanceColor.needsUpdate = true;
    const ha = Math.PI / 2 - frac * Math.PI * 2;
    hand.position.set(Math.cos(ha) * (RD - 0.6), Math.sin(ha) * (RD - 0.6), 0);
    hand.rotation.z = ha - Math.PI / 2;

    // chain: an S-curve behind; in the tamper chapter link 9 is pulled out
    // only shown around the tamper chapter, so it never crosses a headline elsewhere
    const vis = smooth(5.35, 5.9, st.stage) * (1 - smooth(6.6, 7.15, st.stage));
    chain.visible = vis > 0.01;
    chain.scale.setScalar(Math.max(0.001, vis) * (st.mobile ? 0.55 : 0.8));
    chain.position.set(st.mobile ? 0.6 : 2.4, y + 0.9, z - 1.4);
    links.forEach((l, i) => {
      const u = i - NL / 2;
      l.position.set(u * 0.52, Math.sin(u * 0.45 + t * 0.4) * 0.18, Math.cos(u * 0.3) * 0.3);
      l.rotation.set(i % 2 ? Math.PI / 2 : 0, 0, Math.sin(t * 0.3 + i) * 0.05);
      if (i === 9) { l.position.y += pull * 1.1; l.position.z += pull * 0.9; l.rotation.z += pull * 0.9; }
    });

    glyphs.forEach((g) => {
      g.position.y += g.userData.v * st.dt;
      g.position.x = g.userData.base + Math.sin(t * 0.3 + g.userData.w) * 0.25;
      if (g.position.y > 6) g.position.y = -1.4;
    });

    camera.position.set(cx + st.px * 0.35, cy - st.py * 0.2, cz);
    look.set(cx * 0.6, 0.2 + (cy - 0.5) * 0.15, -2);
    camera.lookAt(look);
  };
}, { bg: "#e8e1d3", fog: [10, 40] });
