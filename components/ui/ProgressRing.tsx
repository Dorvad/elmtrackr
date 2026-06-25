"use client";

import { useEffect, useRef } from "react";

interface ProgressRingProps {
  /** 0–1 progress value */
  progress: number;
  size?: number;
  strokeWidth?: number;
  /** Tailwind color class for the filled arc, e.g. "stroke-indigo-500" */
  color?: string;
  /** Track color class */
  trackColor?: string;
  children?: React.ReactNode;
}

export function ProgressRing({
  progress,
  size = 140,
  strokeWidth = 8,
  color = "stroke-indigo-500",
  trackColor = "stroke-indigo-100",
  children,
}: ProgressRingProps) {
  const arcRef = useRef<SVGCircleElement>(null);
  const clampedProgress = Math.min(1, Math.max(0, progress));

  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference * (1 - clampedProgress);

  // Animate from full offset → target offset on mount / progress change
  useEffect(() => {
    const arc = arcRef.current;
    if (!arc) return;
    // Set initial state (no arc) then transition to target
    arc.style.strokeDashoffset = String(circumference);
    // Force reflow so transition fires
    void arc.getBoundingClientRect();
    arc.style.strokeDashoffset = String(offset);
  }, [circumference, offset]);

  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
      <svg
        width={size}
        height={size}
        viewBox={`0 0 ${size} ${size}`}
        fill="none"
        className="absolute inset-0"
      >
        {/* Track */}
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          strokeWidth={strokeWidth}
          className={trackColor}
          strokeLinecap="round"
        />
        {/* Progress arc */}
        <circle
          ref={arcRef}
          cx={size / 2}
          cy={size / 2}
          r={radius}
          strokeWidth={strokeWidth}
          className={`ring-arc ${color}`}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference}
          style={{ transition: "stroke-dashoffset 1s cubic-bezier(0.16, 1, 0.3, 1)" }}
        />
      </svg>
      {/* Content in center */}
      <div className="relative z-10 flex flex-col items-center justify-center">
        {children}
      </div>
    </div>
  );
}
