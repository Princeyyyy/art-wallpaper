"use client";

import React, { useEffect, useRef } from "react";
import { useTheme } from "./ThemeProvider";

interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
  alpha: number;
  maxAlpha: number;
  life: number;
  maxLife: number;
}

export function FluidAtmosphereCanvas({
  className = "",
  externalVelocityX = 0,
}: {
  className?: string;
  externalVelocityX?: number;
}) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const velocityRef = useRef(externalVelocityX);
  const { resolvedTheme } = useTheme();

  useEffect(() => {
    velocityRef.current = externalVelocityX;
  }, [externalVelocityX]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d", { alpha: true });
    if (!ctx) return;

    let animationFrameId: number;
    let width = (canvas.width = canvas.parentElement?.clientWidth || window.innerWidth);
    let height = (canvas.height = canvas.parentElement?.clientHeight || window.innerHeight);

    const handleResize = () => {
      if (!canvas || !canvas.parentElement) return;
      width = canvas.width = canvas.parentElement.clientWidth;
      height = canvas.height = canvas.parentElement.clientHeight;
    };
    window.addEventListener("resize", handleResize);

    // Particle pool: 50 atmospheric gallery light motes
    const particleCount = 50;
    const particles: Particle[] = [];

    const isLight = resolvedTheme === "light";
    const baseColor = isLight ? "158, 120, 62" : "212, 175, 55"; // Rich bronze or warm gold

    const createParticle = (): Particle => {
      const maxLife = 180 + Math.random() * 240;
      return {
        x: Math.random() * width,
        y: Math.random() * height,
        vx: (Math.random() - 0.5) * 0.4,
        vy: -0.15 - Math.random() * 0.35, // gentle upward drift
        radius: 0.8 + Math.random() * 1.8,
        alpha: 0,
        maxAlpha: isLight ? 0.15 + Math.random() * 0.2 : 0.25 + Math.random() * 0.35,
        life: 0,
        maxLife,
      };
    };

    for (let i = 0; i < particleCount; i++) {
      const p = createParticle();
      p.life = Math.random() * p.maxLife; // Stagger initial life
      particles.push(p);
    }

    let mouseX = width / 2;
    let mouseY = height / 2;
    let targetMouseX = mouseX;
    let targetMouseY = mouseY;

    const handleMouseMove = (e: MouseEvent) => {
      const rect = canvas.getBoundingClientRect();
      targetMouseX = e.clientX - rect.left;
      targetMouseY = e.clientY - rect.top;
    };

    window.addEventListener("mousemove", handleMouseMove);

    // Render loop
    const render = () => {
      // Fluid mouse lag
      mouseX += (targetMouseX - mouseX) * 0.05;
      mouseY += (targetMouseY - mouseY) * 0.05;

      ctx.clearRect(0, 0, width, height);

      // Current stroll push (read from ref so canvas loop stays unbroken)
      const strollDrift = velocityRef.current * 0.15;

      for (let i = 0; i < particles.length; i++) {
        const p = particles[i];
        p.life++;

        // Natural curl / gentle wave
        const angle = (p.y * 0.005) + (p.life * 0.01);
        p.vx = Math.sin(angle) * 0.3 - strollDrift;

        // Interactive mouse disturbance (fluid repulsion when close)
        const dx = p.x - mouseX;
        const dy = p.y - mouseY;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 100 && dist > 0) {
          const force = (100 - dist) / 100;
          p.x += (dx / dist) * force * 1.5;
          p.y += (dy / dist) * force * 1.5;
        }

        p.x += p.vx;
        p.y += p.vy;

        // Fade in and out over lifetime
        const halfLife = p.maxLife / 2;
        if (p.life < halfLife) {
          p.alpha = (p.life / halfLife) * p.maxAlpha;
        } else {
          p.alpha = (1 - (p.life - halfLife) / halfLife) * p.maxAlpha;
        }

        // Recycle particle
        if (p.life >= p.maxLife || p.y < -10 || p.x < -20 || p.x > width + 20) {
          particles[i] = createParticle();
          particles[i].y = height + 10;
        }

        // Draw soft glowing mote
        ctx.beginPath();
        const gradient = ctx.createRadialGradient(
          p.x,
          p.y,
          0,
          p.x,
          p.y,
          p.radius * 2.5
        );
        gradient.addColorStop(0, `rgba(${baseColor}, ${p.alpha})`);
        gradient.addColorStop(1, `rgba(${baseColor}, 0)`);
        ctx.fillStyle = gradient;
        ctx.arc(p.x, p.y, p.radius * 2.5, 0, Math.PI * 2);
        ctx.fill();
      }

      animationFrameId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animationFrameId);
      window.removeEventListener("resize", handleResize);
      window.removeEventListener("mousemove", handleMouseMove);
    };
  }, [resolvedTheme]);

  return (
    <canvas
      ref={canvasRef}
      className={`pointer-events-none absolute inset-0 z-10 w-full h-full ${className}`}
    />
  );
}
