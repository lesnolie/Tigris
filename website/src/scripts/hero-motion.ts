/* Hero-only motion: pointer tilt on the TV, and desktop liquid-glass badges.
   Scroll reveal and the headline entrance are CSS. Everything here no-ops
   under prefers-reduced-motion, and the glass path no-ops without WebGL,
   on narrow layouts, or when the hero video cannot be sampled. */

const REDUCE = "(prefers-reduced-motion: reduce)";
const WIDE = "(min-width: 1101px)";

export function initHeroMotion(): void {
  const hero = document.querySelector<HTMLElement>(".hero");
  if (!hero) return;
  const tilt = hero.querySelector<HTMLElement>("[data-tv-tilt]");
  const video = hero.querySelector<HTMLVideoElement>("video[data-clip]");
  if (tilt) bindTilt(tilt);
  if (video) bindLiquidGlass(hero, video);
}

function bindTilt(tilt: HTMLElement): void {
  const reduce = window.matchMedia(REDUCE);
  const coarse = window.matchMedia("(pointer: coarse)");
  /* Touch and other coarse pointers skip the tilt. A mouse still counts even
     when the browser reports hover: none (common in automation and some Linux setups). */
  const allow = (event: PointerEvent) => {
    if (reduce.matches) return false;
    if (event.pointerType === "touch") return false;
    if (coarse.matches && event.pointerType !== "mouse" && event.pointerType !== "pen") return false;
    return true;
  };

  const reset = () => {
    tilt.classList.remove("is-hot");
    tilt.style.removeProperty("--tilt-x");
    tilt.style.removeProperty("--tilt-y");
    tilt.style.removeProperty("--glare-x");
    tilt.style.removeProperty("--glare-y");
  };

  tilt.addEventListener("pointermove", (event) => {
    if (!allow(event)) {
      reset();
      return;
    }
    const rect = tilt.getBoundingClientRect();
    if (rect.width < 1 || rect.height < 1) return;
    const px = (event.clientX - rect.left) / rect.width;
    const py = (event.clientY - rect.top) / rect.height;
    const dx = Math.min(1, Math.max(-1, (px - 0.5) * 2));
    const dy = Math.min(1, Math.max(-1, (py - 0.5) * 2));
    tilt.style.setProperty("--tilt-y", `${(dx * 11).toFixed(2)}deg`);
    tilt.style.setProperty("--tilt-x", `${(-dy * 7).toFixed(2)}deg`);
    tilt.style.setProperty("--glare-x", `${(px * 100).toFixed(1)}%`);
    tilt.style.setProperty("--glare-y", `${(py * 100).toFixed(1)}%`);
    tilt.classList.add("is-hot");
  });

  tilt.addEventListener("pointerleave", reset);
  tilt.addEventListener("pointercancel", reset);
  reduce.addEventListener("change", () => { if (reduce.matches) reset(); });
}

interface GlassChip {
  chip: HTMLElement;
  canvas: HTMLCanvasElement;
  gl: WebGLRenderingContext;
  program: WebGLProgram;
  buffer: WebGLBuffer;
  texture: WebGLTexture;
  scratch: HTMLCanvasElement;
  scratchCtx: CanvasRenderingContext2D;
  pad: number;
}

function bindLiquidGlass(hero: HTMLElement, video: HTMLVideoElement): void {
  const reduce = window.matchMedia(REDUCE);
  const wide = window.matchMedia(WIDE);
  const chips = Array.from(hero.querySelectorAll<HTMLElement>(".float-chip"));
  if (!chips.length) return;

  let glasses: GlassChip[] = [];
  let raf = 0;
  let running = false;
  let seen = false;
  let lastDraw = 0;

  const allow = () => wide.matches && !reduce.matches && !window.matchMedia(REDUCE).matches;

  const stop = () => {
    running = false;
    if (raf) cancelAnimationFrame(raf);
    raf = 0;
  };

  const destroy = () => {
    stop();
    for (const glass of glasses) releaseGlass(glass);
    glasses = [];
    for (const chip of chips) chip.classList.remove("is-liquid");
  };

  const start = () => {
    if (running || !seen || !allow()) return;
    if (!glasses.length) {
      glasses = chips.flatMap((chip) => {
        const canvas = chip.querySelector<HTMLCanvasElement>(".chip-glass");
        if (!canvas) return [];
        const glass = createGlass(canvas, chip);
        return glass ? [glass] : [];
      });
      if (!glasses.length) return;
    }
    running = true;
    const tick = (now: number) => {
      if (!running) return;
      raf = requestAnimationFrame(tick);
      if (document.hidden || now - lastDraw < 32) return;
      lastDraw = now;
      paint(glasses, video);
    };
    raf = requestAnimationFrame(tick);
    paint(glasses, video);
  };

  const sync = () => {
    if (!allow()) {
      destroy();
      return;
    }
    if (seen) start();
  };

  if ("IntersectionObserver" in window) {
    const watch = new IntersectionObserver((entries) => {
      seen = entries.some((entry) => entry.isIntersecting);
      if (seen) start();
      else stop();
    }, { threshold: 0.08 });
    watch.observe(hero);
  } else {
    seen = true;
  }

  wide.addEventListener("change", sync);
  reduce.addEventListener("change", sync);
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden && seen) paint(glasses, video);
  });
  sync();
}

function paint(glasses: GlassChip[], video: HTMLVideoElement): void {
  if (video.readyState < 2 || !video.videoWidth) return;
  const videoRect = video.getBoundingClientRect();
  if (videoRect.width < 2 || videoRect.height < 2) return;
  for (const glass of glasses) drawGlass(glass, video, videoRect);
}

function releaseGlass(glass: GlassChip): void {
  const lose = glass.gl.getExtension("WEBGL_lose_context");
  lose?.loseContext();
}

function createGlass(canvas: HTMLCanvasElement, chip: HTMLElement): GlassChip | null {
  const gl = canvas.getContext("webgl", {
    alpha: true,
    premultipliedAlpha: false,
    antialias: false,
    depth: false,
    stencil: false,
    powerPreference: "low-power",
  });
  if (!gl) return null;
  const program = buildProgram(gl, VERT, FRAG);
  if (!program) return null;
  const buffer = gl.createBuffer();
  const texture = gl.createTexture();
  if (!buffer || !texture) return null;
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
  gl.bindTexture(gl.TEXTURE_2D, texture);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  const scratch = document.createElement("canvas");
  const scratchCtx = scratch.getContext("2d", { alpha: true });
  if (!scratchCtx) return null;
  gl.useProgram(program);
  const pos = gl.getAttribLocation(program, "aPos");
  gl.enableVertexAttribArray(pos);
  gl.vertexAttribPointer(pos, 2, gl.FLOAT, false, 0, 0);
  gl.uniform1i(gl.getUniformLocation(program, "uTex"), 0);
  return { chip, canvas, gl, program, buffer, texture, scratch, scratchCtx, pad: 28 };
}

function drawGlass(glass: GlassChip, video: HTMLVideoElement, videoRect: DOMRect): void {
  const rect = glass.canvas.getBoundingClientRect();
  if (rect.width < 8 || rect.height < 8) {
    glass.chip.classList.remove("is-liquid");
    return;
  }
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  const bw = Math.max(1, Math.round(rect.width * dpr));
  const bh = Math.max(1, Math.round(rect.height * dpr));
  if (glass.canvas.width !== bw || glass.canvas.height !== bh) {
    glass.canvas.width = bw;
    glass.canvas.height = bh;
  }

  const pad = glass.pad;
  const scratchCssW = rect.width + pad * 2;
  const scratchCssH = rect.height + pad * 2;
  const sw = Math.max(1, Math.round(scratchCssW * dpr));
  const sh = Math.max(1, Math.round(scratchCssH * dpr));
  if (glass.scratch.width !== sw || glass.scratch.height !== sh) {
    glass.scratch.width = sw;
    glass.scratch.height = sh;
  }

  const mapped = mapViewportToVideo(
    video,
    videoRect,
    rect.left - pad,
    rect.top - pad,
    rect.right + pad,
    rect.bottom + pad,
  );
  const ctx = glass.scratchCtx;
  ctx.clearRect(0, 0, sw, sh);
  if (!mapped) {
    glass.chip.classList.remove("is-liquid");
    return;
  }
  ctx.drawImage(
    video,
    mapped.sx, mapped.sy, mapped.sw, mapped.sh,
    mapped.dx * dpr, mapped.dy * dpr, mapped.dw * dpr, mapped.dh * dpr,
  );
  const gl = glass.gl;
  gl.viewport(0, 0, bw, bh);
  gl.useProgram(glass.program);
  gl.bindBuffer(gl.ARRAY_BUFFER, glass.buffer);
  const pos = gl.getAttribLocation(glass.program, "aPos");
  gl.enableVertexAttribArray(pos);
  gl.vertexAttribPointer(pos, 2, gl.FLOAT, false, 0, 0);
  gl.activeTexture(gl.TEXTURE0);
  gl.bindTexture(gl.TEXTURE_2D, glass.texture);
  gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, glass.scratch);
  gl.uniform2f(gl.getUniformLocation(glass.program, "uSize"), rect.width, rect.height);
  gl.uniform2f(gl.getUniformLocation(glass.program, "uScratch"), scratchCssW, scratchCssH);
  gl.uniform1f(gl.getUniformLocation(glass.program, "uPad"), pad);
  gl.uniform1f(gl.getUniformLocation(glass.program, "uRadius"), 14);
  gl.enable(gl.BLEND);
  gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
  gl.clearColor(0, 0, 0, 0);
  gl.clear(gl.COLOR_BUFFER_BIT);
  gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  glass.chip.classList.add("is-liquid");
}

function mapViewportToVideo(
  video: HTMLVideoElement,
  vRect: DOMRect,
  left: number,
  top: number,
  right: number,
  bottom: number,
): { sx: number; sy: number; sw: number; sh: number; dx: number; dy: number; dw: number; dh: number } | null {
  const vw = video.videoWidth;
  const vh = video.videoHeight;
  if (!vw || !vh) return null;
  const scale = Math.max(vRect.width / vw, vRect.height / vh);
  const offX = (vw * scale - vRect.width) / 2;
  const offY = (vh * scale - vRect.height) / 2;
  const toSrcX = (x: number) => (x - vRect.left + offX) / scale;
  const toSrcY = (y: number) => (y - vRect.top + offY) / scale;
  const sx0 = toSrcX(left);
  const sy0 = toSrcY(top);
  const sx1 = toSrcX(right);
  const sy1 = toSrcY(bottom);
  const csx = Math.max(0, sx0);
  const csy = Math.max(0, sy0);
  const csr = Math.min(vw, sx1);
  const csb = Math.min(vh, sy1);
  if (csr - csx < 1 || csb - csy < 1) return null;
  const destW = right - left;
  const destH = bottom - top;
  const srcW = sx1 - sx0;
  const srcH = sy1 - sy0;
  return {
    sx: csx,
    sy: csy,
    sw: csr - csx,
    sh: csb - csy,
    dx: ((csx - sx0) / srcW) * destW,
    dy: ((csy - sy0) / srcH) * destH,
    dw: ((csr - csx) / srcW) * destW,
    dh: ((csb - csy) / srcH) * destH,
  };
}

function buildProgram(gl: WebGLRenderingContext, vert: string, frag: string): WebGLProgram | null {
  const program = gl.createProgram();
  const vs = compile(gl, gl.VERTEX_SHADER, vert);
  const fs = compile(gl, gl.FRAGMENT_SHADER, frag);
  if (!program || !vs || !fs) return null;
  gl.attachShader(program, vs);
  gl.attachShader(program, fs);
  gl.linkProgram(program);
  gl.deleteShader(vs);
  gl.deleteShader(fs);
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) return null;
  return program;
}

function compile(gl: WebGLRenderingContext, type: number, source: string): WebGLShader | null {
  const shader = gl.createShader(type);
  if (!shader) return null;
  gl.shaderSource(shader, source);
  gl.compileShader(shader);
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    gl.deleteShader(shader);
    return null;
  }
  return shader;
}

const VERT = `
attribute vec2 aPos;
varying vec2 vUv;
void main() {
  vUv = vec2(aPos.x * 0.5 + 0.5, 0.5 - aPos.y * 0.5);
  gl_Position = vec4(aPos, 0.0, 1.0);
}
`;

const FRAG = `
precision mediump float;
varying vec2 vUv;
uniform sampler2D uTex;
uniform vec2 uSize;
uniform vec2 uScratch;
uniform float uPad;
uniform float uRadius;

float sdRoundBox(vec2 p, vec2 halfSize, float r) {
  vec2 q = abs(p) - halfSize + vec2(r);
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
  vec2 center = uSize * 0.5;
  vec2 p = vUv * uSize;
  float sd = sdRoundBox(p - center, uSize * 0.5, uRadius);
  /* Odd field so the bend is continuous through the middle (an SDF gradient
     flips along the medial axis and leaves a hard seam). */
  vec2 axis = (p - center) / max(uSize.x, 1.0);
  float ax = abs(axis.x);
  float ay = abs(axis.y) * (uSize.x / max(uSize.y, 1.0));
  vec2 field = vec2(sign(axis.x) * pow(ax, 1.35), sign(axis.y) * pow(ay, 1.35));
  float mag = length(field);
  vec2 n = field / max(mag, 0.0001);
  float edge = smoothstep(0.04, 0.62, mag);
  vec2 refr = n * (2.5 + edge * 13.0) + axis * 3.5;
  vec2 base = vec2(uPad, uPad) + p + refr;
  vec2 ca = n * (1.5 + edge * 4.8);
  vec2 uv = base / uScratch;
  vec2 uvR = (base + ca) / uScratch;
  vec2 uvB = (base - ca) / uScratch;
  uv.y = 1.0 - uv.y;
  uvR.y = 1.0 - uvR.y;
  uvB.y = 1.0 - uvB.y;
  vec4 src = texture2D(uTex, uv);
  if (src.a < 0.08) discard;
  float r = texture2D(uTex, uvR).r;
  float g = src.g;
  float b = texture2D(uTex, uvB).b;
  vec3 col = vec3(r, g, b);
  float rim = smoothstep(-12.0, -0.6, sd) * smoothstep(1.1, -1.6, sd);
  float glint = pow(max(dot(normalize(vec2(-0.35, -0.94)), n), 0.0), 8.0) * edge;
  col += vec3(1.0) * rim * 0.42;
  col += vec3(0.72, 0.84, 1.0) * glint * 0.7;
  col = mix(col, vec3(1.0), rim * 0.08);
  float alpha = smoothstep(1.15, -1.4, sd) * src.a;
  gl_FragColor = vec4(col, alpha);
}
`;
