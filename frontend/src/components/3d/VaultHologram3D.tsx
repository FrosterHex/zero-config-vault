"use client";

import React, { useEffect, useRef } from "react";
import * as THREE from "three";
import { useMotionAccessibility } from "@/components/motion/MotionProvider";

interface VaultHologram3DProps {
  className?: string;
  size?: number;
  interactive?: boolean;
}

export function VaultHologram3D({
  className = "",
  size = 460,
  interactive = true,
}: VaultHologram3DProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const { shouldReduceMotion } = useMotionAccessibility();
  // A ref, not state: the animation loop reads it each frame, and a state change would
  // re-run the effect below, tearing down and rebuilding the WebGL renderer on every hover.
  const isHoveredRef = useRef(false);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    // Dimensions
    const width = container.clientWidth || size;
    const height = container.clientHeight || size;

    // 1. Scene, Camera, Renderer
    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 1000);
    camera.position.z = 7.5;

    const renderer = new THREE.WebGLRenderer({
      alpha: true,
      antialias: true,
      powerPreference: "high-performance",
    });
    renderer.setSize(width, height);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    container.appendChild(renderer.domElement);

    // 2. Lighting Setup
    const ambientLight = new THREE.AmbientLight(0x0f172a, 2.5);
    scene.add(ambientLight);

    const cyanLight = new THREE.PointLight(0x06b6d4, 6, 20);
    cyanLight.position.set(4, 3, 5);
    scene.add(cyanLight);

    const indigoLight = new THREE.PointLight(0x6366f1, 8, 20);
    indigoLight.position.set(-4, -3, 4);
    scene.add(indigoLight);

    const emeraldLight = new THREE.PointLight(0x10b981, 4, 15);
    emeraldLight.position.set(0, 5, -2);
    scene.add(emeraldLight);

    // 3. Central Cryptographic Vault Core (Icosahedron)
    const coreGroup = new THREE.Group();
    scene.add(coreGroup);

    // Inner Glowing Core Sphere
    const innerGeo = new THREE.SphereGeometry(1.0, 32, 32);
    const innerMat = new THREE.MeshBasicMaterial({
      color: 0x6366f1,
      wireframe: true,
      transparent: true,
      opacity: 0.35,
    });
    const innerSphere = new THREE.Mesh(innerGeo, innerMat);
    coreGroup.add(innerSphere);

    // Outer Faceted Crystal Enclave
    const crystalGeo = new THREE.IcosahedronGeometry(1.6, 1);
    const crystalMat = new THREE.MeshPhysicalMaterial({
      color: 0x312e81,
      emissive: 0x1e1b4b,
      roughness: 0.1,
      metalness: 0.9,
      transmission: 0.7,
      transparent: true,
      opacity: 0.85,
      wireframe: true,
    });
    const crystal = new THREE.Mesh(crystalGeo, crystalMat);
    coreGroup.add(crystal);

    // Solid inner faceted octahedron
    const solidGeo = new THREE.OctahedronGeometry(1.1, 0);
    const solidMat = new THREE.MeshStandardMaterial({
      color: 0x4338ca,
      emissive: 0x312e81,
      roughness: 0.2,
      metalness: 0.8,
      flatShading: true,
    });
    const solidOcta = new THREE.Mesh(solidGeo, solidMat);
    coreGroup.add(solidOcta);

    // 4. Concentric Orbital Gimbal Rings (Security Shields)
    const ringMat1 = new THREE.MeshBasicMaterial({
      color: 0x06b6d4,
      wireframe: true,
      transparent: true,
      opacity: 0.5,
    });
    const ring1 = new THREE.Mesh(new THREE.TorusGeometry(2.3, 0.02, 16, 100), ringMat1);
    ring1.rotation.x = Math.PI / 3;
    coreGroup.add(ring1);

    const ringMat2 = new THREE.MeshBasicMaterial({
      color: 0x8b5cf6,
      wireframe: true,
      transparent: true,
      opacity: 0.45,
    });
    const ring2 = new THREE.Mesh(new THREE.TorusGeometry(2.7, 0.02, 16, 100), ringMat2);
    ring2.rotation.y = Math.PI / 4;
    coreGroup.add(ring2);

    const ringMat3 = new THREE.MeshBasicMaterial({
      color: 0x10b981,
      wireframe: true,
      transparent: true,
      opacity: 0.35,
    });
    const ring3 = new THREE.Mesh(new THREE.TorusGeometry(3.1, 0.015, 16, 100), ringMat3);
    ring3.rotation.z = Math.PI / 6;
    coreGroup.add(ring3);

    // 5. Swirling Quantum Data Particles (MongoDB Oplog Nodes)
    const particleCount = 140;
    const particlePositions = new Float32Array(particleCount * 3);
    const particleColors = new Float32Array(particleCount * 3);

    const colorPalette = [
      new THREE.Color(0x06b6d4), // Cyan
      new THREE.Color(0x6366f1), // Indigo
      new THREE.Color(0x10b981), // Emerald
      new THREE.Color(0xa855f7), // Purple
    ];

    for (let i = 0; i < particleCount; i++) {
      const radius = 2.0 + Math.random() * 2.2;
      const theta = Math.random() * Math.PI * 2;
      const phi = Math.acos(2 * Math.random() - 1);

      particlePositions[i * 3] = radius * Math.sin(phi) * Math.cos(theta);
      particlePositions[i * 3 + 1] = radius * Math.sin(phi) * Math.sin(theta);
      particlePositions[i * 3 + 2] = radius * Math.cos(phi);

      const color = colorPalette[Math.floor(Math.random() * colorPalette.length)];
      particleColors[i * 3] = color.r;
      particleColors[i * 3 + 1] = color.g;
      particleColors[i * 3 + 2] = color.b;
    }

    const particleGeo = new THREE.BufferGeometry();
    particleGeo.setAttribute("position", new THREE.BufferAttribute(particlePositions, 3));
    particleGeo.setAttribute("color", new THREE.BufferAttribute(particleColors, 3));

    const particleMat = new THREE.PointsMaterial({
      size: 0.065,
      vertexColors: true,
      transparent: true,
      opacity: 0.85,
      blending: THREE.AdditiveBlending,
    });
    const particleSystem = new THREE.Points(particleGeo, particleMat);
    coreGroup.add(particleSystem);

    // 6. Mouse Interaction & Parallax Tilt
    let targetRotationX = 0;
    let targetRotationY = 0;

    const handleMouseMove = (event: MouseEvent) => {
      if (!interactive) return;
      const rect = container.getBoundingClientRect();
      const x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
      const y = -(((event.clientY - rect.top) / rect.height) * 2 - 1);
      targetRotationY = x * 0.7;
      targetRotationX = -y * 0.7;
    };

    const handleResize = () => {
      if (!container) return;
      const w = container.clientWidth;
      const h = container.clientHeight;
      camera.aspect = w / h;
      camera.updateProjectionMatrix();
      renderer.setSize(w, h);
    };

    window.addEventListener("resize", handleResize);
    if (interactive) {
      container.addEventListener("mousemove", handleMouseMove);
    }

    // 7. Animation Loop
    let animationFrameId: number;
    const clock = new THREE.Clock();

    const animate = () => {
      animationFrameId = requestAnimationFrame(animate);
      const delta = clock.getDelta();
      const speedMultiplier = isHoveredRef.current ? 1.6 : 1.0;

      if (!shouldReduceMotion) {
        // Core auto-rotation
        crystal.rotation.x += 0.3 * delta * speedMultiplier;
        crystal.rotation.y += 0.45 * delta * speedMultiplier;

        solidOcta.rotation.x -= 0.5 * delta * speedMultiplier;
        solidOcta.rotation.z += 0.35 * delta * speedMultiplier;

        innerSphere.rotation.y += 0.2 * delta;

        // Differential Gimbal Ring Rotations
        ring1.rotation.z += 0.5 * delta * speedMultiplier;
        ring2.rotation.x += 0.4 * delta * speedMultiplier;
        ring3.rotation.y -= 0.35 * delta * speedMultiplier;

        // Swirling Particle Field
        particleSystem.rotation.y += 0.15 * delta * speedMultiplier;
        particleSystem.rotation.x += 0.08 * delta;

        // Smooth Parallax Lerp to Mouse Position
        coreGroup.rotation.y += (targetRotationY - coreGroup.rotation.y) * 0.05;
        coreGroup.rotation.x += (targetRotationX - coreGroup.rotation.x) * 0.05;
      }

      renderer.render(scene, camera);
    };

    animate();

    // Cleanup
    return () => {
      cancelAnimationFrame(animationFrameId);
      window.removeEventListener("resize", handleResize);
      if (interactive) {
        container.removeEventListener("mousemove", handleMouseMove);
      }
      renderer.dispose();
      // dispose() keeps the WebGL context; browsers cap live contexts, so release it explicitly.
      renderer.forceContextLoss();
      innerGeo.dispose();
      crystalGeo.dispose();
      solidGeo.dispose();
      particleGeo.dispose();
      if (container.contains(renderer.domElement)) {
        container.removeChild(renderer.domElement);
      }
    };
  }, [interactive, shouldReduceMotion, size]);

  return (
    <div
      ref={containerRef}
      onMouseEnter={() => (isHoveredRef.current = true)}
      onMouseLeave={() => (isHoveredRef.current = false)}
      className={`relative flex items-center justify-center cursor-grab active:cursor-grabbing ${className}`}
      style={{ width: "100%", height: "100%", minHeight: `${size}px` }}
    >
      {/* Ambient Radial Cyber Glow Behind 3D Canvas */}
      <div className="absolute inset-0 -z-10 flex items-center justify-center pointer-events-none">
        <div className="h-64 w-64 rounded-full bg-gradient-to-tr from-indigo-600/30 via-cyan-500/20 to-purple-600/30 blur-[90px] animate-pulse" />
      </div>
    </div>
  );
}
