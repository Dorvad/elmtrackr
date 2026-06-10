"use client";

import { useRef, useState } from "react";
import Image from "next/image";
import { GripVertical } from "lucide-react";

interface ImageComparisonProps {
  beforeSrc: string;
  afterSrc: string;
  beforeAlt?: string;
  afterAlt?: string;
  /** Natural width of both images (used for correct srcset generation) */
  width: number;
  /** Natural height of both images */
  height: number;
  /** Visible container height in px. Defaults to 380. Images are object-cover from the top. */
  containerHeight?: number;
}

export function ImageComparison({
  beforeSrc,
  afterSrc,
  beforeAlt = "Before",
  afterAlt = "After",
  width,
  height,
  containerHeight = 380,
}: ImageComparisonProps) {
  const [inset, setInset] = useState(52);
  const [dragging, setDragging] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  function applyPosition(clientX: number) {
    const rect = containerRef.current?.getBoundingClientRect();
    if (!rect) return;
    const pct = Math.min(100, Math.max(0, ((clientX - rect.left) / rect.width) * 100));
    setInset(pct);
  }

  return (
    <div
      ref={containerRef}
      className="relative w-full overflow-hidden select-none"
      style={{ height: containerHeight, cursor: "ew-resize" }}
      onMouseMove={(e) => { if (dragging) applyPosition(e.clientX); }}
      onMouseUp={() => setDragging(false)}
      onMouseLeave={() => setDragging(false)}
      onTouchMove={(e) => { if (dragging && e.touches.length > 0) applyPosition(e.touches[0].clientX); }}
      onTouchEnd={() => setDragging(false)}
    >
      {/* ── Bottom layer: Before (old design) ── */}
      <div className="absolute inset-0">
        <Image
          src={beforeSrc}
          alt={beforeAlt}
          fill
          priority
          draggable={false}
          sizes="(max-width: 768px) 100vw, 448px"
          className="object-cover object-top select-none"
        />
      </div>

      {/* ── Top layer: After (Aurora redesign), reveals from the left ── */}
      <div
        className="absolute inset-0 z-10"
        style={{ clipPath: `inset(0 ${100 - inset}% 0 0)` }}
      >
        <Image
          src={afterSrc}
          alt={afterAlt}
          fill
          priority
          draggable={false}
          sizes="(max-width: 768px) 100vw, 448px"
          className="object-cover object-top select-none"
        />
      </div>

      {/* ── "Before" pill label ── */}
      <div
        className="absolute bottom-3 right-3 z-20 px-2.5 py-1 rounded-full text-[11px] font-bold tracking-wide pointer-events-none transition-opacity duration-200"
        style={{
          background: "rgba(0,0,0,0.38)",
          color: "rgba(255,255,255,0.9)",
          backdropFilter: "blur(8px)",
          WebkitBackdropFilter: "blur(8px)",
          opacity: inset < 88 ? 1 : 0,
        }}
      >
        Before
      </div>

      {/* ── "After" pill label ── */}
      <div
        className="absolute bottom-3 left-3 z-20 px-2.5 py-1 rounded-full text-[11px] font-bold tracking-wide pointer-events-none transition-opacity duration-200"
        style={{
          background: "var(--au-indigo)",
          color: "white",
          backdropFilter: "blur(8px)",
          WebkitBackdropFilter: "blur(8px)",
          boxShadow: "0 2px 8px -2px rgba(91,77,242,0.55)",
          opacity: inset > 12 ? 1 : 0,
        }}
      >
        After
      </div>

      {/* ── Divider line ── */}
      <div
        className="absolute top-0 bottom-0 z-20 pointer-events-none"
        style={{
          left: `${inset}%`,
          width: 2,
          transform: "translateX(-50%)",
          background: "rgba(255,255,255,0.85)",
          boxShadow: "0 0 0 1px rgba(91,77,242,0.22), 0 0 14px 0 rgba(91,77,242,0.18)",
        }}
      />

      {/* ── Drag handle ── */}
      <button
        aria-label="Drag to compare before and after"
        className="absolute z-30 top-1/2 -translate-y-1/2 -translate-x-1/2 h-10 w-7 rounded-xl flex items-center justify-center transition-transform duration-100 hover:scale-110 active:scale-95"
        style={{
          left: `${inset}%`,
          background: "white",
          boxShadow: "0 4px 18px -4px rgba(91,77,242,0.45), 0 0 0 1.5px rgba(91,77,242,0.18)",
          cursor: "ew-resize",
        }}
        onMouseDown={(e) => { e.preventDefault(); setDragging(true); applyPosition(e.clientX); }}
        onTouchStart={(e) => { setDragging(true); if (e.touches.length > 0) applyPosition(e.touches[0].clientX); }}
        onTouchEnd={() => setDragging(false)}
        onMouseUp={() => setDragging(false)}
      >
        <GripVertical className="h-4 w-4" style={{ color: "var(--au-indigo)" }} />
      </button>
    </div>
  );
}
