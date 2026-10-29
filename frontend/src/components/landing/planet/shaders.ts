/** GLSL from the Ascend design (getlayers.ai), unchanged. */

export const SNOISE = /* glsl */ `
vec4 permute(vec4 x){return mod(((x*34.0)+1.0)*x, 289.0);}
vec4 taylorInvSqrt(vec4 r){return 1.79284291400159 - 0.85373472095314 * r;}
float snoise(vec3 v){
  const vec2 C = vec2(1.0/6.0, 1.0/3.0); const vec4 D = vec4(0.0, 0.5, 1.0, 2.0);
  vec3 i = floor(v + dot(v, C.yyy)); vec3 x0 = v - i + dot(i, C.xxx);
  vec3 g = step(x0.yzx, x0.xyz); vec3 l = 1.0 - g;
  vec3 i1 = min(g.xyz, l.zxy); vec3 i2 = max(g.xyz, l.zxy);
  vec3 x1 = x0 - i1 + 1.0 * C.xxx; vec3 x2 = x0 - i2 + 2.0 * C.xxx; vec3 x3 = x0 - 1.0 + 3.0 * C.xxx;
  i = mod(i, 289.0);
  vec4 p = permute(permute(permute(i.z + vec4(0.0, i1.z, i2.z, 1.0)) + i.y + vec4(0.0, i1.y, i2.y, 1.0)) + i.x + vec4(0.0, i1.x, i2.x, 1.0));
  float n_ = 1.0/7.0; vec3 ns = n_ * D.wyz - D.xzx;
  vec4 j = p - 49.0 * floor(p * ns.z *ns.z);
  vec4 x_ = floor(j * ns.z); vec4 y_ = floor(j - 7.0 * x_);
  vec4 x = x_ *ns.x + ns.yyyy; vec4 y = y_ *ns.x + ns.yyyy; vec4 h = 1.0 - abs(x) - abs(y);
  vec4 b0 = vec4(x.xy, y.xy); vec4 b1 = vec4(x.zw, y.zw);
  vec4 s0 = floor(b0)*2.0 + 1.0; vec4 s1 = floor(b1)*2.0 + 1.0; vec4 sh = -step(h, vec4(0.0));
  vec4 a0 = b0.xzyw + s0.xzyw*sh.xxyy; vec4 a1 = b1.xzyw + s1.xzyw*sh.zzww;
  vec3 p0 = vec3(a0.xy,h.x); vec3 p1 = vec3(a0.zw,h.y); vec3 p2 = vec3(a1.xy,h.z); vec3 p3 = vec3(a1.zw,h.w);
  vec4 norm = taylorInvSqrt(vec4(dot(p0,p0), dot(p1,p1), dot(p2, p2), dot(p3,p3)));
  p0 *= norm.x; p1 *= norm.y; p2 *= norm.z; p3 *= norm.w;
  vec4 m = max(0.5 - vec4(dot(x0,x0), dot(x1,x1), dot(x2,x2), dot(x3,x3)), 0.0); m = m * m;
  return 42.0 * dot(m*m, vec4(dot(p0,x0), dot(p1,x1), dot(p2,x2), dot(p3,x3)));
}
`;

export const FINAL_VERTEX = /* glsl */ `varying vec2 vUv; void main(){ vUv = uv; gl_Position = vec4(position, 1.0); }`;

export const FINAL_FRAGMENT = /* glsl */ `
    uniform float iTime; uniform sampler2D tDiffuse; uniform sampler2D bloomTexture; uniform sampler2D torusTexture; uniform sampler2D haloTexture;
    uniform vec3 uBg; uniform vec3 uFlameA; uniform vec3 uFlameB; uniform float uFlameAmt;
    varying vec2 vUv;
    vec3 warp3d(vec3 pos, float t){ float curv=.8,a=1.9,b=0.7; pos*=2.;
      pos.x+=curv*sin(t+a*pos.y)+t*b; pos.y+=curv*cos(t+a*pos.x);
      pos.y+=curv*sin(t+a*pos.z)+t*b; pos.z+=curv*cos(t+a*pos.y);
      pos.z+=curv*sin(t+a*pos.x)+t*b; pos.x+=curv*cos(t+a*pos.z);
      return 0.5+0.5*cos(pos.xyz+vec3(1,2,4)); }
    void main(){
      vec2 uv = 2.*vUv - 1.;
      vec3 w = pow(warp3d(vec3(uv.x, sin(uv.y), uv.y), iTime*1.5), vec3(1.5));
      vec3 flame = 1.5*uFlameA*w.x; flame*=w.y; flame += uFlameB*w.z;
      flame *= smoothstep(0.25, 1., abs(uv.y));
      float md = smoothstep(-0.7, 1., -uv.y*uv.x); flame *= md*md;
      vec3 bg = uBg * (1.0 - 0.4 * length(uv));
      vec3 halo = texture2D(haloTexture, vUv).xyz;
      gl_FragColor = vec4(bg + flame*uFlameAmt + texture2D(bloomTexture, vUv).xyz + texture2D(torusTexture, vUv).xyz + texture2D(tDiffuse, vUv).xyz + halo, 1.);
    }`;

export const PLANET_FRAGMENT_UNIFORMS = /* glsl */ `
uniform float time; uniform float noiseScale; uniform float speedX; uniform float speedY; uniform float speedZ;
uniform vec3 rimColor; uniform float rimPower; uniform sampler2D nightBlendTexture; uniform float nightLights;
uniform float terrainDepth; uniform float terrainShade; uniform float oceanGlint; uniform float oceanDeep;
uniform float oceanFlow; uniform float oceanFlowSpeed; uniform float oceanFlowScale;
varying vec2 vCustomUv;
`;

export const PLANET_FRAGMENT_TAIL = /* glsl */ `
#include <dithering_fragment>
vec3 normalizedNormal = normalize(vNormal);
vec3 viewDir = normalize(vViewPosition);
float rim = 1.0 - max(dot(viewDir, normalizedNormal), 0.0);
rim = pow(rim, rimPower); rim = pow(rim, 1.5); rim *= 0.7;
vec3 currentColor = gl_FragColor.rgb;
float blueDom = currentColor.b - max(currentColor.r, currentColor.g);
float waterMask = clamp(smoothstep(-0.005, 0.03, blueDom), 0.0, 1.0);
float shimmer = snoise(vec3(vCustomUv.x * noiseScale + time * speedX, vCustomUv.y * noiseScale - time * speedY, time * speedZ));
gl_FragColor.rgb += waterMask * shimmer * 0.025;
float fT = time * oceanFlowSpeed * 4.0;
float fS = 4.0 * oceanFlowScale;
float warp = snoise(vec3(vCustomUv.x * fS - fT * 0.5, vCustomUv.y * fS + fT * 0.4, fT * 0.5));
float flow = snoise(vec3(vCustomUv.x * fS * 2.0 + fT * 0.6 + warp, vCustomUv.y * fS * 2.0 - fT * 0.5, fT * 0.7));
flow = warp * 0.6 + flow * 0.4;
gl_FragColor.rgb += waterMask * flow * 0.12 * oceanFlow;
gl_FragColor.rgb = mix(gl_FragColor.rgb, vec3(0.01, 0.06, 0.16), waterMask * oceanDeep);
vec3 finalColor = mix(gl_FragColor.rgb, rimColor, rim);
gl_FragColor = vec4(finalColor, 1.0);
vec3 surfPos = -vViewPosition;
float terrH = dot(texture2D(map, vCustomUv).rgb, vec3(0.299, 0.587, 0.114));
vec3 sigX = dFdx(surfPos), sigY = dFdy(surfPos);
vec3 vR1 = cross(sigY, normalizedNormal), vR2 = cross(normalizedNormal, sigX);
float fDet = dot(sigX, vR1);
vec3 vGrad = sign(fDet) * (dFdx(terrH) * vR1 + dFdy(terrH) * vR2);
vec3 bumpedNormal = normalize(abs(fDet) * normalizedNormal - terrainDepth * vGrad);
vec3 shadeNormal = mix(bumpedNormal, normalizedNormal, waterMask);
vec3 cityLights = texture2D(nightBlendTexture, vCustomUv).rgb * gl_FragColor.rgb * nightLights;
vec3 viewSunDir = normalize(vec3(-0.9, 0.18, 0.4));
float ndl = dot(normalizedNormal, viewSunDir);
float dayAmt = smoothstep(-0.05, 0.35, ndl);
float relief = dot(shadeNormal, viewSunDir) - ndl;
gl_FragColor.rgb *= clamp(1.0 + relief * terrainShade * dayAmt, 0.55, 1.6);
float nightFactor  = smoothstep(0.18, -0.30, ndl);
float lightsFactor = smoothstep(0.30, -0.35, ndl);
gl_FragColor.rgb = mix(gl_FragColor.rgb, gl_FragColor.rgb * 0.08, nightFactor);
gl_FragColor.rgb += cityLights * lightsFactor;
vec3 halfDir = normalize(viewSunDir + viewDir);
float ripple = snoise(vec3(vCustomUv * 240.0, time * 4.0));
float ndh = max(dot(normalizedNormal, halfDir) + ripple * 0.02, 0.0);
float glint = pow(ndh, 140.0);
gl_FragColor.rgb += glint * waterMask * dayAmt * oceanGlint * vec3(1.0, 0.97, 0.88);
`;

export const GLOW_VERTEX = /* glsl */ `varying vec2 vUv; void main(){ vUv=uv; gl_Position = projectionMatrix*modelViewMatrix*vec4(position,1.0); }`;

export const GLOW_FRAGMENT = /* glsl */ `
uniform vec3 uGlow; uniform float uIntensity; varying vec2 vUv;
void main(){
  float d = length(vUv - 0.5) * 2.0;
  float a = pow(clamp(1.0 - d, 0.0, 1.0), 2.2);
  gl_FragColor = vec4(uGlow * a * uIntensity, a);
}`;

export const CLOUD_FRAGMENT_UNIFORMS = /* glsl */ `
uniform float uTime; uniform float noiseScale; uniform float uSpeedX; uniform float uSpeedY; uniform float uSpeedZ;
uniform float uOpacity; uniform float uPhase;
varying vec2 vCloudUv;
`;

export const CLOUD_FRAGMENT_TAIL = /* glsl */ `
#include <dithering_fragment>
gl_FragColor.rgb = vec3(1.0);
float cloudNoise = snoise(vec3(vCloudUv.x * noiseScale + uTime * uSpeedX + uPhase, vCloudUv.y * noiseScale - uTime * uSpeedY + uPhase, uTime * uSpeedZ + uPhase));
float cloudNdv = max(dot(normalize(vNormal), normalize(vViewPosition)), 0.0);
float cloudEdge = pow(1.0 - cloudNdv, 3.0);
float cloudMod = mix(cloudNoise, 1.0, cloudEdge);
float cloudNdl = dot(normalize(vNormal), normalize(vec3(-0.9, 0.18, 0.4)));
float cloudDay = 1.0 - smoothstep(0.30, -0.30, cloudNdl) * 0.9;
gl_FragColor.a *= cloudMod * uOpacity * cloudDay;
`;

export const MOTES_VERTEX = /* glsl */ `
attribute float size; attribute float seed; uniform float uTime; uniform vec2 uRes;
varying float vA;
vec3 warp(vec3 p, float t){ float c=0.9,a=1.9,b=0.02,s=0.05; p*=2.;
  p.x+=c*sin(s*t+a*p.y)+t*b; p.y+=c*cos(s*t+a*p.x); p.y+=c*sin(s*t+a*p.z)+t*b;
  p.z+=c*cos(s*t+a*p.y); p.z+=c*sin(s*t+a*p.x)+t*b; p.x+=c*cos(s*t+a*p.z);
  return cos(p+vec3(1,2,4)); }
void main(){
  vec3 v = position*4.0 + warp(position, uTime)*1.2;
  vec4 mv = modelViewMatrix * vec4(v, 1.0);
  float r = length(v); float farF = 1.0 - smoothstep(5.0, 6.5, r); float nearF = smoothstep(0.0, 0.5, -mv.z);
  vA = farF * nearF;
  gl_PointSize = size * uRes.y / 900.0 / -mv.z; gl_PointSize = max(gl_PointSize, 1.0);
  gl_Position = projectionMatrix * mv;
}`;

export const MOTES_FRAGMENT = /* glsl */ `
uniform vec3 uColor; varying float vA;
void main(){ vec2 p = gl_PointCoord - 0.5; float l = length(p); if (l > 0.5) discard;
  float tex = smoothstep(0.5, 0.0, l); gl_FragColor = vec4(uColor * tex, tex * vA * 0.55); }`;

export const STARS_VERTEX = /* glsl */ `
attribute float seed; attribute float bright;
uniform float uTime; uniform float uSize; uniform float uFlicker; uniform vec2 uRes;
varying float vTw;
void main(){
  vec4 mv = modelViewMatrix * vec4(position, 1.0);
  float tw = 0.6 + 0.4 * sin(uTime * uFlicker + seed);
  vTw = bright * tw;
  gl_PointSize = max(uSize * uRes.y / 900.0 * (90.0 / max(-mv.z, 1.0)), 1.0);
  gl_Position = projectionMatrix * mv;
}`;

export const STARS_FRAGMENT = /* glsl */ `
uniform vec3 uColor; varying float vTw;
void main(){
  vec2 p = gl_PointCoord - 0.5; float l = length(p); if (l > 0.5) discard;
  float core = smoothstep(0.5, 0.0, l);
  gl_FragColor = vec4(uColor, core * vTw);
}`;

export const MARKERS_VERTEX = /* glsl */ `
attribute float seed; uniform float uSize; uniform vec2 uRes;
varying float vSeed; varying float vFade;
void main(){
  vSeed = seed;
  vec4 mv = modelViewMatrix * vec4(position, 1.0);
  vec3 vn = normalize(normalMatrix * normalize(position));
  vec3 vd = normalize(-mv.xyz);
  vFade = smoothstep(0.15, 0.5, dot(vn, vd));
  gl_PointSize = max(uSize * uRes.y / 900.0 * (7.0 / max(-mv.z, 1.0)), 2.0);
  gl_Position = projectionMatrix * mv;
}`;

export const MARKERS_FRAGMENT = /* glsl */ `
uniform vec3 uColor; uniform float uTime; uniform float uSpeed;
varying float vSeed; varying float vFade;
void main(){
  if (vFade <= 0.001) discard;
  vec2 p = gl_PointCoord - 0.5;
  float d = length(p) * 2.0;
  if (d > 1.0) discard;
  float core = smoothstep(0.30, 0.0, d) * 1.2;
  float ph = fract(uTime * uSpeed + vSeed);
  float ring = smoothstep(0.07, 0.0, abs(d - ph)) * (1.0 - ph);
  gl_FragColor = vec4(uColor, clamp(core + ring, 0.0, 1.0) * vFade);
}`;
