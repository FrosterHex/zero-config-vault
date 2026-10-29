import * as THREE from "three";
import { EffectComposer } from "three/examples/jsm/postprocessing/EffectComposer.js";
import { RenderPass } from "three/examples/jsm/postprocessing/RenderPass.js";
import { UnrealBloomPass } from "three/examples/jsm/postprocessing/UnrealBloomPass.js";
import { ShaderPass } from "three/examples/jsm/postprocessing/ShaderPass.js";
import { GammaCorrectionShader } from "three/examples/jsm/shaders/GammaCorrectionShader.js";
import { CopyShader } from "three/examples/jsm/shaders/CopyShader.js";
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js";
import { DRACOLoader } from "three/examples/jsm/loaders/DRACOLoader.js";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import {
  CONFIG, ENTRY_DUR, ENTRY_START_Y, LAYERS, STOPS_S, STOPS_X, STOPS_Y, clamp, hexToVec3, sample,
} from "./config";
import * as S from "./shaders";

const ASSETS = {
  planet: "/planet/planet.glb",
  lights: "/planet/planet-lights.glb",
  clouds: "/planet/planet-clouds.png",
  draco: "/draco/",
};

type Uniform<T> = { value: T };

/**
 * The fixed WebGL planet behind the landing page (Ascend design, ported from three r0.143 to r0.186).
 * Framework-free: the React wrapper only creates it, calls start() and dispose().
 */
export class PlanetScene {
  private readonly canvas: HTMLCanvasElement;
  private readonly reducedMotion: boolean;
  private renderer?: THREE.WebGLRenderer;
  private scene = new THREE.Scene();
  private camera = new THREE.PerspectiveCamera(40, 1, 0.1, 200);
  private controls?: OrbitControls;
  private torusComposer?: EffectComposer;
  private bloomComposer?: EffectComposer;
  private finalComposer?: EffectComposer;
  private finalPass?: ShaderPass;

  private worldGroup = new THREE.Group();
  private planetGroup = new THREE.Group();
  private cloudGroup = new THREE.Group();
  private glowMesh?: THREE.Mesh;
  private clouds: { mesh: THREE.Mesh; spin: number }[] = [];
  private resUniforms: Uniform<THREE.Vector2>[] = [];
  private disposables: { dispose: () => void }[] = [];

  private planetTime: Uniform<number> = { value: 0 };
  private cloudTime: Uniform<number> = { value: 0 };
  private starTime: Uniform<number> = { value: 0 };
  private markerTime: Uniform<number> = { value: 0 };

  private curP = 0;
  private curX = STOPS_X[0].v;
  private curY = STOPS_Y[0].v;
  private curS = STOPS_S[0].v;
  private spinPhase = 0;
  private entryStart = -1; // performance.now() seconds when the planet appeared; -1 = not yet
  private planetReady = false;

  private frame = 0;
  private last = 0;
  private disposed = false;
  private bufferSize = new THREE.Vector2(1, 1);
  private lastW = 0;
  private lastH = 0;
  // Phones resize the viewport height whenever the address bar shows or hides; reallocating every
  // render target for that is wasted work, so small height-only changes are ignored (the fixed
  // canvas stretches by a few percent instead).
  private readonly onResize = () => {
    if (window.innerWidth === this.lastW && Math.abs(window.innerHeight - this.lastH) < 160) return;
    this.resize();
  };

  constructor(canvas: HTMLCanvasElement, opts: { reducedMotion: boolean }) {
    this.canvas = canvas;
    this.reducedMotion = opts.reducedMotion;
  }

  start(): void {
    try {
      this.setup();
    } catch (err) {
      console.warn("Planet scene unavailable (WebGL failed); the page continues without it.", err);
      return;
    }
    window.addEventListener("resize", this.onResize);
    this.resize();
    this.loadPlanet();
    this.last = performance.now();
    this.frame = requestAnimationFrame(this.tick);
  }

  dispose(): void {
    this.disposed = true;
    cancelAnimationFrame(this.frame);
    window.removeEventListener("resize", this.onResize);
    this.controls?.dispose();
    this.scene.traverse((obj) => {
      const mesh = obj as THREE.Mesh;
      mesh.geometry?.dispose();
      const materials = Array.isArray(mesh.material) ? mesh.material : mesh.material ? [mesh.material] : [];
      for (const m of materials) {
        for (const value of Object.values(m)) {
          if (value instanceof THREE.Texture) value.dispose();
        }
        m.dispose();
      }
    });
    this.disposables.forEach((d) => d.dispose());
    this.torusComposer?.dispose();
    this.bloomComposer?.dispose();
    this.finalComposer?.dispose();
    this.renderer?.dispose();
    // dispose() keeps the WebGL context alive; browsers cap live contexts, so release it.
    this.renderer?.forceContextLoss();
  }

  private setup(): void {
    const renderer = new THREE.WebGLRenderer({ canvas: this.canvas, antialias: true });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.VSMShadowMap;
    this.renderer = renderer;

    this.scene.background = new THREE.Color(0x000000);

    const camera = this.camera;
    camera.position.set(0, 0, 8);
    camera.layers.enable(LAYERS.TORUS_SCENE);
    camera.layers.enable(LAYERS.BLOOM_SCENE);
    camera.layers.enable(LAYERS.ENTIRE_SCENE);
    this.scene.add(camera);

    // three r155+ uses physically based light units; r0.143 multiplied intensities by PI internally,
    // so the design's 1.8 / 0.8 are scaled by PI to keep the same brightness. Lights are enabled on
    // every layer because three culls lights by camera layer and the planet renders on TORUS_SCENE.
    const ambient = new THREE.AmbientLight(0xffffff, 1.8 * Math.PI);
    const sun = new THREE.DirectionalLight(0xffffff, 0.8 * Math.PI);
    sun.position.set(0, 10, 2);
    for (const light of [ambient, sun]) {
      light.layers.set(LAYERS.ENTIRE_SCENE);
      light.layers.enable(LAYERS.TORUS_SCENE);
      light.layers.enable(LAYERS.BLOOM_SCENE);
      this.scene.add(light);
    }

    const w = window.innerWidth;
    const h = window.innerHeight;
    const dpr = Math.min(window.devicePixelRatio, 2);

    // 8-bit render targets, as in three r0.143. r0.186 defaults to half-float targets, which keep the
    // planet shader's slightly negative ocean noise; the gamma pass turns those into NaN and the bloom
    // blur spreads the NaN over the whole frame (a black screen). 8-bit targets clamp them to 0.
    const makeComposer = () =>
      new EffectComposer(
        renderer,
        new THREE.WebGLRenderTarget(w * dpr, h * dpr, { type: THREE.UnsignedByteType })
      );

    const torusComposer = makeComposer();
    torusComposer.renderToScreen = false;
    torusComposer.addPass(new RenderPass(this.scene, camera));
    torusComposer.addPass(new ShaderPass(GammaCorrectionShader));
    torusComposer.addPass(new UnrealBloomPass(new THREE.Vector2(w, h), 0.22, 0.2, 0));
    torusComposer.addPass(new ShaderPass(CopyShader));
    this.torusComposer = torusComposer;

    const bloomComposer = makeComposer();
    bloomComposer.renderToScreen = false;
    bloomComposer.addPass(new RenderPass(this.scene, camera));
    bloomComposer.addPass(new UnrealBloomPass(new THREE.Vector2(w, h), 0.5, 0.6, 0));
    bloomComposer.addPass(new ShaderPass(GammaCorrectionShader));
    this.bloomComposer = bloomComposer;

    const finalPass = new ShaderPass(
      {
        uniforms: {
          iTime: { value: 0 }, tDiffuse: { value: null }, torusTexture: { value: null },
          bloomTexture: { value: null }, haloTexture: { value: null },
          uBg: { value: hexToVec3(CONFIG.bgColor) }, uFlameA: { value: hexToVec3(CONFIG.flameColor) },
          uFlameB: { value: hexToVec3(CONFIG.flameColor2) }, uFlameAmt: { value: CONFIG.flameAmt },
        },
        vertexShader: S.FINAL_VERTEX,
        fragmentShader: S.FINAL_FRAGMENT,
      },
      "tDiffuse"
    );
    // ShaderPass clones its uniforms, so the bloom/torus textures are wired on the pass. They are set
    // every frame in tick() from each composer's readBuffer (its finished output): a fixed
    // renderTarget1 would alternate between finished and half-finished frames in the bloom composer,
    // which swaps buffers an odd number of times per frame.
    const finalComposer = makeComposer();
    finalComposer.addPass(new RenderPass(this.scene, camera));
    finalComposer.addPass(finalPass);
    this.finalComposer = finalComposer;
    this.finalPass = finalPass;

    const controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.06;
    controls.enablePan = false;
    controls.enabled = false; // never capture pointer or scroll
    controls.autoRotate = true;
    controls.autoRotateSpeed = CONFIG.autoRotate;
    controls.minDistance = 3.5;
    controls.maxDistance = 16;
    controls.target.set(0, 0, 0);
    this.controls = controls;

    // worldGroup only ever gets position + uniform scale from the scroll story, never rotation.
    this.planetGroup.rotation.z = CONFIG.tilt;
    this.cloudGroup.rotation.z = CONFIG.tilt;
    this.cloudGroup.visible = false;
    this.worldGroup.add(this.planetGroup, this.cloudGroup);
    this.worldGroup.position.set(STOPS_X[0].v, STOPS_Y[0].v, 0);
    this.worldGroup.scale.setScalar(STOPS_S[0].v);
    this.scene.add(this.worldGroup);

    this.addGlow();
    this.addClouds();
    this.addMotes();
    this.addStars();
  }

  private resUniform(): Uniform<THREE.Vector2> {
    const u = { value: this.bufferSize.clone() };
    this.resUniforms.push(u);
    return u;
  }

  private addGlow(): void {
    const material = new THREE.ShaderMaterial({
      uniforms: { uGlow: { value: hexToVec3(CONFIG.glowColor) }, uIntensity: { value: CONFIG.glowIntensity } },
      vertexShader: S.GLOW_VERTEX,
      fragmentShader: S.GLOW_FRAGMENT,
      transparent: true,
      side: THREE.DoubleSide,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
    });
    const glow = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), material);
    glow.scale.setScalar(CONFIG.planetRadius * 2.3);
    glow.layers.set(LAYERS.TORUS_SCENE);
    this.worldGroup.add(glow);
    this.glowMesh = glow;
  }

  private addClouds(): void {
    const tex = new THREE.TextureLoader().load(ASSETS.clouds);
    tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
    tex.repeat.set(5, 5);
    const layers = [
      { h: CONFIG.cloud1Height, o: CONFIG.cloud1Opacity, s: CONFIG.cloud1Spin, ry: 0.0, phase: 0.0 },
      { h: CONFIG.cloud2Height, o: CONFIG.cloud2Opacity, s: CONFIG.cloud2Spin, ry: 2.2, phase: 13.0 },
      { h: CONFIG.cloud3Height, o: CONFIG.cloud3Opacity, s: CONFIG.cloud3Spin, ry: 4.3, phase: 27.0 },
    ];
    for (const layer of layers) {
      const material = new THREE.MeshStandardMaterial({ map: tex, transparent: true, depthWrite: false });
      material.onBeforeCompile = (shader) => {
        Object.assign(shader.uniforms, {
          uTime: this.cloudTime, noiseScale: { value: 20 }, uSpeedX: { value: 1 }, uSpeedY: { value: 2 },
          uSpeedZ: { value: 2 }, uOpacity: { value: layer.o }, uPhase: { value: layer.phase },
        });
        shader.vertexShader = "varying vec2 vCloudUv;\n" + shader.vertexShader.replace("void main() {", "void main() {\n  vCloudUv = uv;");
        shader.fragmentShader = S.CLOUD_FRAGMENT_UNIFORMS + S.SNOISE + shader.fragmentShader.replace("#include <dithering_fragment>", S.CLOUD_FRAGMENT_TAIL);
      };
      const mesh = new THREE.Mesh(new THREE.SphereGeometry(CONFIG.planetRadius * layer.h, 64, 64), material);
      mesh.rotation.y = layer.ry;
      mesh.renderOrder = 2;
      mesh.layers.set(LAYERS.TORUS_SCENE);
      this.cloudGroup.add(mesh);
      this.clouds.push({ mesh, spin: layer.s });
    }
  }

  private addMotes(): void {
    const n = CONFIG.atmoCount;
    const positions = new Float32Array(n * 3);
    const sizes = new Float32Array(n);
    const seeds = new Float32Array(n);
    for (let i = 0; i < n; i++) {
      positions[i * 3] = Math.random() * 2 - 1;
      positions[i * 3 + 1] = Math.random() * 2 - 1;
      positions[i * 3 + 2] = Math.random() * 2 - 1;
      sizes[i] = CONFIG.atmoSize * (0.4 + Math.random());
      seeds[i] = Math.random();
    }
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute("size", new THREE.BufferAttribute(sizes, 1));
    geometry.setAttribute("seed", new THREE.BufferAttribute(seeds, 1));
    const uniforms = { uTime: { value: 0 }, uRes: this.resUniform(), uColor: { value: hexToVec3(CONFIG.atmoColor) } };
    const material = new THREE.ShaderMaterial({
      uniforms, vertexShader: S.MOTES_VERTEX, fragmentShader: S.MOTES_FRAGMENT,
      transparent: true, depthWrite: false, depthTest: false, blending: THREE.AdditiveBlending,
    });
    const motes = new THREE.Points(geometry, material);
    motes.frustumCulled = false;
    motes.layers.set(LAYERS.BLOOM_SCENE);
    motes.onBeforeRender = () => {
      const now = performance.now() / 1000;
      uniforms.uTime.value = now * CONFIG.atmoSpeed * 8;
      motes.position.copy(this.camera.position);
      if (this.finalPass) this.finalPass.uniforms.iTime.value = now;
    };
    this.scene.add(motes);
  }

  private addStars(): void {
    const n = CONFIG.starCount;
    const positions = new Float32Array(n * 3);
    const seeds = new Float32Array(n);
    const bright = new Float32Array(n);
    for (let i = 0; i < n; i++) {
      // Uniform on a sphere of radius 90
      const u = Math.random() * 2 - 1;
      const theta = Math.random() * Math.PI * 2;
      const r = Math.sqrt(1 - u * u);
      positions[i * 3] = 90 * r * Math.cos(theta);
      positions[i * 3 + 1] = 90 * u;
      positions[i * 3 + 2] = 90 * r * Math.sin(theta);
      seeds[i] = Math.random() * 100;
      bright[i] = 0.35 + Math.random() * 0.65;
    }
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute("seed", new THREE.BufferAttribute(seeds, 1));
    geometry.setAttribute("bright", new THREE.BufferAttribute(bright, 1));
    const material = new THREE.ShaderMaterial({
      uniforms: {
        uTime: this.starTime, uSize: { value: CONFIG.starSize }, uFlicker: { value: CONFIG.starFlicker },
        uColor: { value: hexToVec3(CONFIG.starColor) }, uRes: this.resUniform(),
      },
      vertexShader: S.STARS_VERTEX, fragmentShader: S.STARS_FRAGMENT,
      transparent: true, depthWrite: false, depthTest: true, blending: THREE.AdditiveBlending,
    });
    const stars = new THREE.Points(geometry, material);
    stars.frustumCulled = false;
    stars.layers.set(LAYERS.BLOOM_SCENE);
    this.scene.add(stars);
  }

  private loadPlanet(): void {
    const draco = new DRACOLoader();
    draco.setDecoderPath(ASSETS.draco);
    this.disposables.push(draco);
    const loader = new GLTFLoader();
    loader.setDRACOLoader(draco);

    const fail = (err: unknown) => {
      if (this.disposed) return;
      // The stars and background keep rendering; the page itself never depends on the planet.
      console.warn("Planet model could not be loaded; the page continues without it.", err);
    };

    loader.load(ASSETS.lights, (lightsGltf) => {
      // Left the page meanwhile: skip downloading and decoding the much larger planet model.
      if (this.disposed) return;
      let nightTex: THREE.Texture | null = null;
      lightsGltf.scene.traverse((o) => {
        const m = o as THREE.Mesh;
        if (!nightTex && m.isMesh) nightTex = (m.material as THREE.MeshStandardMaterial).map;
      });
      loader.load(ASSETS.planet, (gltf) => {
        if (this.disposed) return;
        let planet: THREE.Mesh | null = null;
        gltf.scene.traverse((o) => {
          const m = o as THREE.Mesh;
          if (!planet && m.isMesh) planet = m;
        });
        if (!planet) return fail(new Error("planet.glb has no mesh"));
        this.buildPlanet(planet, nightTex);
      }, undefined, fail);
    }, undefined, fail);
  }

  private buildPlanet(mesh: THREE.Mesh, nightTex: THREE.Texture | null): void {
    mesh.removeFromParent();
    mesh.position.set(0, 0, 0);
    mesh.rotation.set(0, 0, 0);
    mesh.geometry.computeBoundingSphere();
    const radius = mesh.geometry.boundingSphere?.radius || 1;
    mesh.scale.setScalar(CONFIG.planetRadius / radius);

    const material = (mesh.material as THREE.MeshStandardMaterial).clone();
    material.metalness = 0;
    material.roughness = 1;
    material.envMapIntensity = 0;
    material.onBeforeCompile = (shader) => {
      Object.assign(shader.uniforms, {
        time: this.planetTime, noiseScale: { value: 30 }, speedX: { value: 1.5 }, speedY: { value: 2.0 },
        speedZ: { value: 2.5 }, rimColor: { value: hexToVec3(CONFIG.rimColor) }, rimPower: { value: CONFIG.rimPower },
        nightBlendTexture: { value: nightTex }, nightLights: { value: CONFIG.nightLights },
        terrainDepth: { value: CONFIG.terrainDepth }, terrainShade: { value: CONFIG.terrainShade },
        oceanGlint: { value: CONFIG.oceanGlint }, oceanDeep: { value: CONFIG.oceanDeep },
        oceanFlow: { value: CONFIG.oceanFlow }, oceanFlowSpeed: { value: CONFIG.oceanFlowSpeed },
        oceanFlowScale: { value: CONFIG.oceanFlowScale },
      });
      shader.vertexShader = "varying vec2 vCustomUv;\n" + shader.vertexShader.replace("void main() {", "void main() {\n  vCustomUv = uv;");
      shader.fragmentShader = S.PLANET_FRAGMENT_UNIFORMS + S.SNOISE + shader.fragmentShader.replace("#include <dithering_fragment>", S.PLANET_FRAGMENT_TAIL);
    };
    mesh.material = material;
    mesh.layers.set(LAYERS.TORUS_SCENE);
    this.planetGroup.add(mesh);

    this.addMarkers(mesh, material.map);

    this.cloudGroup.visible = true;
    this.planetReady = true;
    this.entryStart = performance.now() / 1000;
  }

  /** Golden radar pings on land: area-weighted surface samples whose texel is not ocean blue. */
  private addMarkers(mesh: THREE.Mesh, map: THREE.Texture | null): void {
    const image = map?.image as CanvasImageSource & { width: number; height: number } | undefined;
    const geometry = mesh.geometry;
    const pos = geometry.getAttribute("position");
    const uv = geometry.getAttribute("uv");
    if (!image || !pos || !uv) return;

    const canvas = document.createElement("canvas");
    canvas.width = image.width;
    canvas.height = image.height;
    const ctx = canvas.getContext("2d", { willReadFrequently: true });
    if (!ctx) return;
    ctx.drawImage(image, 0, 0);
    const pixels = ctx.getImageData(0, 0, canvas.width, canvas.height).data;

    const index = geometry.getIndex();
    const triCount = index ? index.count / 3 : pos.count / 3;
    const vi = (t: number, k: number) => (index ? index.getX(t * 3 + k) : t * 3 + k);
    const a = new THREE.Vector3(), b = new THREE.Vector3(), c = new THREE.Vector3();
    const cumulative = new Float32Array(triCount);
    let total = 0;
    for (let t = 0; t < triCount; t++) {
      a.fromBufferAttribute(pos, vi(t, 0));
      b.fromBufferAttribute(pos, vi(t, 1));
      c.fromBufferAttribute(pos, vi(t, 2));
      total += new THREE.Triangle(a, b, c).getArea();
      cumulative[t] = total;
    }

    const points: number[] = [];
    const seeds: number[] = [];
    const flipY = map?.flipY ?? false;
    for (let attempt = 0; attempt < CONFIG.markerCount * 60 && seeds.length < CONFIG.markerCount; attempt++) {
      const r = Math.random() * total;
      let lo = 0, hi = triCount - 1;
      while (lo < hi) {
        const mid = (lo + hi) >> 1;
        if (cumulative[mid] < r) lo = mid + 1;
        else hi = mid;
      }
      let u = Math.random(), v = Math.random();
      if (u + v > 1) { u = 1 - u; v = 1 - v; }
      const w = 1 - u - v;
      const i0 = vi(lo, 0), i1 = vi(lo, 1), i2 = vi(lo, 2);
      const tu = uv.getX(i0) * w + uv.getX(i1) * u + uv.getX(i2) * v;
      const tv = uv.getY(i0) * w + uv.getY(i1) * u + uv.getY(i2) * v;
      const px = Math.min(canvas.width - 1, Math.max(0, Math.floor((tu - Math.floor(tu)) * canvas.width)));
      const fy = tv - Math.floor(tv);
      const py = Math.min(canvas.height - 1, Math.max(0, Math.floor((flipY ? 1 - fy : fy) * canvas.height)));
      const o = (py * canvas.width + px) * 4;
      const cr = pixels[o], cg = pixels[o + 1], cb = pixels[o + 2];
      if (cb > cr + 6 && cb > cg + 6) continue; // ocean
      a.fromBufferAttribute(pos, i0).multiplyScalar(w)
        .add(b.fromBufferAttribute(pos, i1).multiplyScalar(u))
        .add(c.fromBufferAttribute(pos, i2).multiplyScalar(v))
        .multiplyScalar(1.012);
      points.push(a.x, a.y, a.z);
      seeds.push(Math.random());
    }

    const markerGeometry = new THREE.BufferGeometry();
    markerGeometry.setAttribute("position", new THREE.Float32BufferAttribute(points, 3));
    markerGeometry.setAttribute("seed", new THREE.Float32BufferAttribute(seeds, 1));
    const material = new THREE.ShaderMaterial({
      uniforms: {
        uTime: this.markerTime, uColor: { value: hexToVec3(CONFIG.markerColor) }, uSize: { value: CONFIG.markerSize },
        uSpeed: { value: CONFIG.markerSpeed }, uRes: this.resUniform(),
      },
      vertexShader: S.MARKERS_VERTEX, fragmentShader: S.MARKERS_FRAGMENT,
      transparent: true, depthTest: true, depthWrite: false, blending: THREE.AdditiveBlending,
    });
    const markers = new THREE.Points(markerGeometry, material);
    markers.layers.set(LAYERS.TORUS_SCENE);
    mesh.add(markers);
  }

  private resize(): void {
    const renderer = this.renderer;
    if (!renderer) return;
    const w = window.innerWidth;
    const h = window.innerHeight;
    const dpr = Math.min(window.devicePixelRatio, 2);
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
    renderer.setPixelRatio(dpr);
    renderer.setSize(w, h, false);
    for (const composer of [this.torusComposer, this.bloomComposer, this.finalComposer]) {
      composer?.setPixelRatio(dpr);
      composer?.setSize(w, h);
    }
    this.lastW = w;
    this.lastH = h;
    this.bufferSize.set(w * dpr, h * dpr);
    for (const u of this.resUniforms) u.value.copy(this.bufferSize);
  }

  private tick = (nowMs: number) => {
    if (this.disposed) return;
    this.frame = requestAnimationFrame(this.tick);
    const dt = Math.min((nowMs - this.last) / 1000, 0.05);
    this.last = nowMs;

    this.planetTime.value += dt / 12;
    this.cloudTime.value += dt / 20;
    this.starTime.value += dt;
    this.markerTime.value += dt;
    this.spinPhase += dt * CONFIG.spin;
    for (const c of this.clouds) c.mesh.rotation.y += dt * c.spin;

    // Scroll story: progress 0..1 drives the keyframes, damped per frame.
    const doc = document.documentElement;
    const max = Math.max(1, doc.scrollHeight - window.innerHeight);
    const pTarget = clamp(window.scrollY / max, 0, 1);
    this.curP += (pTarget - this.curP) * Math.min(1, dt * 4.5);
    const sideScale = clamp(window.innerWidth / 1200, 0.5, 1);
    const k = Math.min(1, dt * 3.2);
    this.curX += (sample(STOPS_X, this.curP) * sideScale - this.curX) * k;
    this.curY += (sample(STOPS_Y, this.curP) - this.curY) * k;
    this.curS += (sample(STOPS_S, this.curP) - this.curS) * k;

    let entryY = 0;
    if (this.planetReady && !this.reducedMotion && this.entryStart >= 0) {
      const t = clamp((nowMs / 1000 - this.entryStart) / ENTRY_DUR, 0, 1);
      entryY = ENTRY_START_Y * (1 - (1 - Math.pow(1 - t, 3)));
    }
    this.worldGroup.position.set(this.curX, this.curY + entryY, 0);
    this.worldGroup.scale.setScalar(this.curS);
    this.planetGroup.rotation.y = CONFIG.initRotation + this.spinPhase + this.curP * Math.PI * 1.6;
    this.glowMesh?.quaternion.copy(this.camera.quaternion);
    this.controls?.update();

    const camera = this.camera;
    camera.layers.set(LAYERS.TORUS_SCENE);
    this.torusComposer?.render();
    camera.layers.set(LAYERS.BLOOM_SCENE);
    this.bloomComposer?.render();
    if (this.finalPass && this.torusComposer && this.bloomComposer) {
      this.finalPass.uniforms.torusTexture.value = this.torusComposer.readBuffer.texture;
      this.finalPass.uniforms.bloomTexture.value = this.bloomComposer.readBuffer.texture;
    }
    camera.layers.set(LAYERS.ENTIRE_SCENE);
    this.finalComposer?.render();
  };
}
