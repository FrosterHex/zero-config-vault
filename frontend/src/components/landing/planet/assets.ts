/**
 * Planet scene asset URLs. Kept apart from the scene (and from three.js) so the landing page can
 * preload them from its HTML without pulling the 3D code into the page bundle.
 */
export const PLANET_ASSETS = {
  planet: "/planet/planet.glb",
  lights: "/planet/planet-lights.glb",
  clouds: "/planet/planet-clouds.webp",
  draco: "/draco/",
} as const;

/** Files the Draco decoder fetches on start (the WebAssembly build; every target browser supports it). */
export const DRACO_FILES = ["draco_wasm_wrapper.js", "draco_decoder.wasm"] as const;
