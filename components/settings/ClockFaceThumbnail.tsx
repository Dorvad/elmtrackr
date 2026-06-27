import type { ClockStyle } from "@/types";

const G0 = "#5B4DF2", G1 = "#7C5CF6", G2 = "#16C8D6";
const W = 160, H = 120;

function arc(cx: number, cy: number, r: number, from: number, to: number): string {
  const sx = cx + r * Math.cos(from), sy = cy + r * Math.sin(from);
  const ex = cx + r * Math.cos(to), ey = cy + r * Math.sin(to);
  return `M${sx.toFixed(1)} ${sy.toFixed(1)} A${r} ${r} 0 ${to - from > Math.PI ? 1 : 0} 1 ${ex.toFixed(1)} ${ey.toFixed(1)}`;
}

export function ClockFaceThumbnail({ style }: { style: ClockStyle }) {
  switch (style) {
    case "classic": return <ClassicThumb />;
    case "minimal": return <MinimalThumb />;
    case "bold":    return <BoldThumb />;
    case "focus":   return <FocusThumb />;
    case "night":   return <NightThumb />;
    case "retro":   return <RetroThumb />;
    case "aurora":  return <AuroraThumb />;
    case "pulse":   return <PulseThumb />;
    case "dial":    return <DialThumb />;
    case "strand":  return <StrandThumb />;
    case "prism":   return <PrismThumb />;
    case "sand":    return <SandThumb />;
    case "blocks":  return <BlocksThumb />;
    case "orbit":   return <OrbitThumb />;
    case "fellowship": return <FellowshipThumb />;
  }
}

// ── Classic ──────────────────────────────────────────────────────────────────
// 75% progress ring, comet at 9 o'clock
function ClassicThumb() {
  const cx = 80, cy = 58, r = 36, sw = 8;
  // arc from 12 o'clock (-π/2) to 9 o'clock (π) = 75%
  const a = arc(cx, cy, r, -Math.PI / 2, Math.PI);
  const [ex, ey] = [cx + r * Math.cos(Math.PI), cy + r * Math.sin(Math.PI)];
  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" xmlns="http://www.w3.org/2000/svg" fill="none">
      <defs>
        <linearGradient id="cl-g" x1="0" y1="0" x2="1" y2="1">
          <stop stopColor={G0} /><stop offset="0.5" stopColor={G1} /><stop offset="1" stopColor={G2} />
        </linearGradient>
        <filter id="cl-glow"><feGaussianBlur stdDeviation="2.5" result="b" /><feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge></filter>
      </defs>
      <rect width={W} height={H} rx={14} fill="white" />
      <circle cx={cx} cy={cy} r={r} stroke="#ECEBFA" strokeWidth={sw} />
      <path d={a} stroke="url(#cl-g)" strokeWidth={sw} strokeLinecap="round" />
      <circle cx={ex} cy={ey} r={5.5} fill="white" filter="url(#cl-glow)" />
      <circle cx={ex} cy={ey} r={4} fill={G1} opacity={0.9} filter="url(#cl-glow)" />
      <text x={cx} y={cy - 12} textAnchor="middle" fontSize={7} fontWeight="700" fill="#9B9AC4" letterSpacing={1}>ELAPSED</text>
      <text x={cx} y={cy + 8} textAnchor="middle" fontSize={18} fontWeight="700" fill="#181530" letterSpacing={-1}>2:34</text>
    </svg>
  );
}

// ── Minimal ───────────────────────────────────────────────────────────────────
// Giant H·M split typography
function MinimalThumb() {
  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" xmlns="http://www.w3.org/2000/svg" fill="none">
      <defs>
        <linearGradient id="mn-g" x1="0" y1="0" x2="1" y2="0">
          <stop stopColor={G0} /><stop offset="1" stopColor={G2} />
        </linearGradient>
      </defs>
      <rect width={W} height={H} rx={14} fill="white" />
      <text x={38} y={76} textAnchor="middle" fontSize={44} fontWeight="300" fill="#181530" letterSpacing={-2}>02</text>
      <circle cx={80} cy={60} r={6} fill="url(#mn-g)" />
      <text x={122} y={76} textAnchor="middle" fontSize={44} fontWeight="300" fill="#181530" letterSpacing={-2}>34</text>
      <rect x={54} y={86} width={52} height={3} rx={1.5} fill="url(#mn-g)" opacity={0.65} />
    </svg>
  );
}

// ── Bold ──────────────────────────────────────────────────────────────────────
// Oversized gradient numbers
function BoldThumb() {
  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" xmlns="http://www.w3.org/2000/svg" fill="none">
      <defs>
        <linearGradient id="bd-g" x1="0" y1="0" x2="1" y2="1">
          <stop stopColor={G0} /><stop offset="0.5" stopColor={G1} /><stop offset="1" stopColor={G2} />
        </linearGradient>
      </defs>
      <rect width={W} height={H} rx={14} fill="white" />
      <text x={80} y={70} textAnchor="middle" fontSize={42} fontWeight="800" fill="url(#bd-g)" letterSpacing={-2}>02:34</text>
      <text x={80} y={88} textAnchor="middle" fontSize={7} fontWeight="700" fill="#9B9AC4" letterSpacing={1.5}>ON SHIFT</text>
      <rect x={36} y={98} width={88} height={4} rx={2} fill="#ECEBFA" />
      <rect x={36} y={98} width={52} height={4} rx={2} fill="url(#bd-g)" />
    </svg>
  );
}

// ── Focus ─────────────────────────────────────────────────────────────────────
// Concentric breathing rings
function FocusThumb() {
  const cx = 80, cy = 57;
  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" xmlns="http://www.w3.org/2000/svg" fill="none">
      <defs>
        <radialGradient id="fc-rg" cx="50%" cy="50%" r="50%">
          <stop stopColor={G0} stopOpacity={0.15} /><stop offset="1" stopColor={G0} stopOpacity={0} />
        </radialGradient>
      </defs>
      <rect width={W} height={H} rx={14} fill="white" />
      <circle cx={cx} cy={cy} r={46} fill="url(#fc-rg)" />
      <circle cx={cx} cy={cy} r={44} stroke={G1} strokeWidth={1.5} opacity={0.18} />
      <circle cx={cx} cy={cy} r={32} stroke={G1} strokeWidth={1.5} opacity={0.32} />
      <circle cx={cx} cy={cy} r={20} stroke={G1} strokeWidth={2} opacity={0.55} />
      <circle cx={cx} cy={cy} r={14} fill={G0} opacity={0.1} />
      <text x={cx} y={cy - 5} textAnchor="middle" fontSize={7} fontWeight="700" fill="#9B9AC4" letterSpacing={1}>FOCUS</text>
      <text x={cx} y={cy + 9} textAnchor="middle" fontSize={14} fontWeight="500" fill="#181530" letterSpacing={-0.5}>2:34</text>
    </svg>
  );
}

// ── Night ─────────────────────────────────────────────────────────────────────
// Dark background + stars + neon ring
function NightThumb() {
  const cx = 80, cy = 56, r = 36, sw = 8;
  // 65% arc: to = -π/2 + 0.65 * 2π
  const to = -Math.PI / 2 + 0.65 * 2 * Math.PI;
  const a = arc(cx, cy, r, -Math.PI / 2, to);
  const [ex, ey] = [cx + r * Math.cos(to), cy + r * Math.sin(to)];
  const stars: [number, number, number][] = [[22, 16, 1.5], [72, 10, 1], [132, 20, 1.5], [148, 42, 1], [14, 58, 1], [142, 92, 1.5]];
  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" xmlns="http://www.w3.org/2000/svg" fill="none">
      <defs>
        <linearGradient id="ng-g" x1="0" y1="0" x2="1" y2="1">
          <stop stopColor="#7C7CFF" /><stop offset="0.5" stopColor="#9A6BFF" /><stop offset="1" stopColor="#3CE8E0" />
        </linearGradient>
        <filter id="ng-glow"><feGaussianBlur stdDeviation="3" result="b" /><feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge></filter>
      </defs>
      <rect width={W} height={H} rx={14} fill="#14112E" />
      {stars.map(([x, y, r2], i) => (
        <circle key={i} cx={x} cy={y} r={r2} fill="white" opacity={0.55 + (i % 3) * 0.15} />
      ))}
      <circle cx={cx} cy={cy} r={r} stroke="rgba(255,255,255,0.07)" strokeWidth={sw} />
      <path d={a} stroke="url(#ng-g)" strokeWidth={sw} strokeLinecap="round" filter="url(#ng-glow)" />
      <circle cx={ex} cy={ey} r={5} fill="white" opacity={0.9} filter="url(#ng-glow)" />
      <text x={cx} y={cy - 11} textAnchor="middle" fontSize={7} fontWeight="700" fill="rgba(255,255,255,0.45)" letterSpacing={1}>NIGHT SHIFT</text>
      <text x={cx} y={cy + 7} textAnchor="middle" fontSize={15} fontWeight="500" fill="white" letterSpacing={-0.5}>02:34</text>
    </svg>
  );
}

// ── Retro ─────────────────────────────────────────────────────────────────────
// Dark background + flip-card digit display
function RetroThumb() {
  const boxes: [number, string][] = [[12, "0"], [44, "2"], [84, "3"], [116, "4"]];
  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" xmlns="http://www.w3.org/2000/svg" fill="none">
      <defs>
        <linearGradient id="rt-g" x1="0" y1="0" x2="1" y2="0">
          <stop stopColor={G0} /><stop offset="1" stopColor={G1} />
        </linearGradient>
      </defs>
      <rect width={W} height={H} rx={14} fill="#0D0B1E" />
      <text x={80} y={17} textAnchor="middle" fontSize={7} fontWeight="700" fill="rgba(255,255,255,0.35)" letterSpacing={2}>ELAPSED</text>
      {boxes.map(([x, d]) => (
        <g key={x}>
          <rect x={x} y={24} width={28} height={40} rx={6} fill="rgba(255,255,255,0.05)" />
          <rect x={x} y={24} width={28} height={40} rx={6} stroke="rgba(255,255,255,0.1)" strokeWidth={1} />
          <line x1={x} y1={44} x2={x + 28} y2={44} stroke="rgba(0,0,0,0.5)" strokeWidth={1.5} />
          <text x={x + 14} y={48} textAnchor="middle" fontSize={22} fontWeight={600} fill="#F4F3FF">{d}</text>
        </g>
      ))}
      {/* Colon dots */}
      <circle cx={78} cy={37} r={3} fill="url(#rt-g)" />
      <circle cx={78} cy={53} r={3} fill="url(#rt-g)" />
      <text x={80} y={84} textAnchor="middle" fontSize={9} fontWeight="700" fill={G1} letterSpacing={2.5}>00 SEC</text>
    </svg>
  );
}

// ── Aurora ────────────────────────────────────────────────────────────────────
// Thick rainbow gradient ring
function AuroraThumb() {
  const cx = 80, cy = 58, r = 34, sw = 13;
  // 85% arc
  const to = -Math.PI / 2 + 0.85 * 2 * Math.PI;
  const a = arc(cx, cy, r, -Math.PI / 2, to);
  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" xmlns="http://www.w3.org/2000/svg" fill="none">
      <defs>
        <linearGradient id="au-g" x1="0" y1="0" x2="1" y2="1">
          <stop stopColor="#FF6B9D" />
          <stop offset="0.25" stopColor={G0} />
          <stop offset="0.5" stopColor={G1} />
          <stop offset="0.75" stopColor={G2} />
          <stop offset="1" stopColor="#16D9C8" />
        </linearGradient>
        <linearGradient id="au-txt" x1="0" y1="0" x2="1" y2="0">
          <stop stopColor={G0} /><stop offset="1" stopColor={G2} />
        </linearGradient>
      </defs>
      <rect width={W} height={H} rx={14} fill="white" />
      <circle cx={cx} cy={cy} r={r} stroke="#ECEBFA" strokeWidth={sw} />
      <path d={a} stroke="url(#au-g)" strokeWidth={sw} strokeLinecap="round" />
      <text x={cx} y={cy + 7} textAnchor="middle" fontSize={17} fontWeight="700" fill="url(#au-txt)" letterSpacing={-0.5}>2:34</text>
    </svg>
  );
}

// ── Pulse ─────────────────────────────────────────────────────────────────────
// Concentric glowing rings
function PulseThumb() {
  const cx = 80, cy = 57;
  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" xmlns="http://www.w3.org/2000/svg" fill="none">
      <defs>
        <radialGradient id="pu-rg" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor={G1} stopOpacity={0.35} />
          <stop offset="100%" stopColor={G1} stopOpacity={0} />
        </radialGradient>
        <filter id="pu-glow"><feGaussianBlur stdDeviation="3.5" result="b" /><feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge></filter>
      </defs>
      <rect width={W} height={H} rx={14} fill="white" />
      <circle cx={cx} cy={cy} r={46} fill="url(#pu-rg)" />
      <circle cx={cx} cy={cy} r={44} stroke={G0} strokeWidth={1.5} opacity={0.22} />
      <circle cx={cx} cy={cy} r={32} stroke={G1} strokeWidth={2} opacity={0.45} />
      <circle cx={cx} cy={cy} r={20} stroke={G1} strokeWidth={3} opacity={0.75} filter="url(#pu-glow)" />
      <circle cx={cx} cy={cy} r={11} fill={G1} opacity={0.15} />
      <text x={cx} y={cy + 5} textAnchor="middle" fontSize={7} fontWeight="700" fill={G1} letterSpacing={1.5} opacity={0.8}>PULSE</text>
    </svg>
  );
}

// ── Dial ──────────────────────────────────────────────────────────────────────
// Analog clock face with ticks and sweep hand
function DialThumb() {
  const cx = 80, cy = 54, r = 36;
  // 25% sector: 12 o'clock to 3 o'clock
  const sectorPath = `M${cx} ${cy} L${cx} ${cy - r} A${r} ${r} 0 0 1 ${cx + r} ${cy} Z`;
  const ticks = Array.from({ length: 12 }, (_, i) => {
    const a = (i * 30 - 90) * (Math.PI / 180);
    const isQ = i % 3 === 0;
    return {
      x1: (cx + Math.cos(a) * (isQ ? 28 : 30)).toFixed(1),
      y1: (cy + Math.sin(a) * (isQ ? 28 : 30)).toFixed(1),
      x2: (cx + Math.cos(a) * r).toFixed(1),
      y2: (cy + Math.sin(a) * r).toFixed(1),
      isQ,
    };
  });
  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" xmlns="http://www.w3.org/2000/svg" fill="none">
      <defs>
        <linearGradient id="dl-g" x1="0" y1="0" x2="1" y2="0">
          <stop stopColor={G0} /><stop offset="1" stopColor={G1} />
        </linearGradient>
      </defs>
      <rect width={W} height={H} rx={14} fill="white" />
      <circle cx={cx} cy={cy} r={r + 4} fill="white" />
      <path d={sectorPath} fill={G0} opacity={0.1} />
      {ticks.map((t, i) => (
        <line key={i} x1={t.x1} y1={t.y1} x2={t.x2} y2={t.y2}
          stroke={t.isQ ? "#9B9AC4" : "#C8C6E0"} strokeWidth={t.isQ ? 2 : 1} strokeLinecap="round" />
      ))}
      {/* Sweep hand at 3 o'clock */}
      <line x1={cx} y1={cy} x2={cx + 28} y2={cy} stroke={G0} strokeWidth={2.5} strokeLinecap="round" />
      <circle cx={cx} cy={cy} r={4.5} fill={G0} />
      <text x={cx} y={cy + r + 18} textAnchor="middle" fontSize={11} fontWeight="700" fill="#181530" letterSpacing={-0.5}>2:34</text>
    </svg>
  );
}

// ── Strand ────────────────────────────────────────────────────────────────────
// Fiber-optic vertical strands, left group lit at ~55%, right group dim
function StrandThumb() {
  const litXs  = [10, 19, 28, 37, 46];
  const frontX = 55;
  const dimXs  = [64, 73, 99, 108, 117, 126, 135, 144];
  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" xmlns="http://www.w3.org/2000/svg" fill="none">
      <defs>
        <linearGradient id="str-g" x1="0" y1="0" x2="0" y2="1">
          <stop stopColor={G0} /><stop offset="0.5" stopColor={G1} /><stop offset="1" stopColor={G2} />
        </linearGradient>
        <filter id="str-glow"><feGaussianBlur stdDeviation="1.8" result="b" /><feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge></filter>
      </defs>
      <rect width={W} height={H} rx={14} fill="white" />
      {litXs.map((x) => (
        <line key={x} x1={x} y1={8} x2={x} y2={112} stroke="url(#str-g)" strokeWidth={2} strokeLinecap="round" opacity={0.8} />
      ))}
      <line x1={frontX} y1={8} x2={frontX} y2={112} stroke={G1} strokeWidth={3} strokeLinecap="round" filter="url(#str-glow)" />
      {dimXs.map((x) => (
        <line key={x} x1={x} y1={8} x2={x} y2={112} stroke="#9B9AC4" strokeWidth={1.5} strokeLinecap="round" opacity={0.22} />
      ))}
      <text x={80} y={55} textAnchor="middle" fontSize={7} fontWeight="700" fill="#9B9AC4" letterSpacing={1}>ELAPSED</text>
      <text x={80} y={70} textAnchor="middle" fontSize={16} fontWeight="700" fill="#181530" letterSpacing={-0.5}>2:34</text>
    </svg>
  );
}

// ── Prism ─────────────────────────────────────────────────────────────────────
// Triangle with ~55% liquid fill rising from base
function PrismThumb() {
  const [ax, ay] = [80, 8];
  const [bx, by] = [10, 112];
  const [cx2, cy2] = [150, 112];
  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" xmlns="http://www.w3.org/2000/svg" fill="none">
      <defs>
        <linearGradient id="pr-g" x1="0" y1="0" x2="0" y2="1">
          <stop stopColor={G0} stopOpacity={0.5} />
          <stop offset="1" stopColor={G2} stopOpacity={0.55} />
        </linearGradient>
        <clipPath id="pr-th-clip">
          <polygon points={`${ax},${ay} ${bx},${by} ${cx2},${cy2}`} />
        </clipPath>
        <filter id="pr-glow"><feGaussianBlur stdDeviation="1.5" result="b" /><feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge></filter>
      </defs>
      <rect width={W} height={H} rx={14} fill="white" />
      {/* Fill: 55% = clip from y=112-(55%*104)≈55 upward */}
      <rect x={0} y={55} width={W} height={60} clipPath="url(#pr-th-clip)" fill="url(#pr-g)" />
      {/* Triangle outline */}
      <polygon points={`${ax},${ay} ${bx},${by} ${cx2},${cy2}`} stroke={G0} strokeWidth={1.5} strokeOpacity={0.5} />
      {/* Orbiting segment hint */}
      <polygon points={`${ax},${ay} ${bx},${by} ${cx2},${cy2}`} stroke={G1} strokeWidth={2.5} strokeLinecap="round"
        strokeDasharray="30 270" strokeDashoffset={-40} filter="url(#pr-glow)" />
      {/* Vertex dots */}
      <circle cx={ax} cy={ay} r={3} fill={G0} filter="url(#pr-glow)" />
      <circle cx={bx} cy={by} r={3} fill={G1} filter="url(#pr-glow)" />
      <circle cx={cx2} cy={cy2} r={3} fill={G2} filter="url(#pr-glow)" />
      {/* Time at centroid */}
      <text x={80} y={86} textAnchor="middle" fontSize={12} fontWeight="700" fill="white" filter="url(#pr-glow)">2:34</text>
    </svg>
  );
}

// ── Sand ──────────────────────────────────────────────────────────────────────
function SandThumb() {
  const cx = 80, top = 14, bottom = 106, mid = 60;
  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" xmlns="http://www.w3.org/2000/svg" fill="none">
      <defs>
        <linearGradient id="sd-g" x1="0" y1="0" x2="0" y2="1">
          <stop stopColor={G0} /><stop offset="1" stopColor={G2} />
        </linearGradient>
      </defs>
      <rect width={W} height={H} rx={14} fill="white" />
      <path
        d={`M${cx - 28} ${top} Q${cx} ${top - 6} ${cx + 28} ${top} L${cx + 8} ${mid} L${cx + 28} ${bottom} Q${cx} ${bottom + 6} ${cx - 28} ${bottom} L${cx - 8} ${mid} Z`}
        stroke="#ECEBFA" strokeWidth={2}
      />
      <path
        d={`M${cx - 8} ${bottom - 34} L${cx + 8} ${bottom - 34} L${cx + 22} ${bottom - 8} L${cx - 22} ${bottom - 8} Z`}
        fill="url(#sd-g)" opacity={0.75}
      />
      <circle cx={cx} cy={mid} r={2} fill={G1} opacity={0.7} />
      <text x={cx} y={72} textAnchor="middle" fontSize={16} fontWeight="700" fill="#181530" letterSpacing={-0.5}>2:34</text>
    </svg>
  );
}

// ── Blocks ───────────────────────────────────────────────────────────────────
function BlocksThumb() {
  const blocks = 8;
  const gap = 4;
  const bw = (W - 24 - gap * (blocks - 1)) / blocks;
  const baseX = 12;
  const baseY = 88;
  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" xmlns="http://www.w3.org/2000/svg" fill="none">
      <defs>
        <linearGradient id="bk-g" x1="0" y1="0" x2="1" y2="0">
          <stop stopColor={G0} /><stop offset="1" stopColor={G2} />
        </linearGradient>
      </defs>
      <rect width={W} height={H} rx={14} fill="white" />
      <text x={80} y={52} textAnchor="middle" fontSize={7} fontWeight="700" fill="#9B9AC4" letterSpacing={1}>WORKDAY</text>
      <text x={80} y={72} textAnchor="middle" fontSize={18} fontWeight="700" fill="#181530" letterSpacing={-1}>2:34</text>
      {Array.from({ length: blocks }, (_, i) => (
        <rect
          key={i}
          x={baseX + i * (bw + gap)}
          y={baseY}
          width={bw}
          height={14}
          rx={3}
          fill={i < 5 ? "url(#bk-g)" : "#ECEBFA"}
          opacity={i === 5 ? 0.45 : 1}
        />
      ))}
    </svg>
  );
}

// ── Orbit ─────────────────────────────────────────────────────────────────────
function OrbitThumb() {
  const cx = 80, cy = 58, r = 34;
  const angle = (-90 + 0.62 * 360) * (Math.PI / 180);
  const sx = cx + r * Math.cos(angle);
  const sy = cy + r * Math.sin(angle);
  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" xmlns="http://www.w3.org/2000/svg" fill="none">
      <rect width={W} height={H} rx={14} fill="white" />
      <circle cx={cx} cy={cy} r={r} stroke="#ECEBFA" strokeWidth={1.5} strokeDasharray="6 7" />
      <path
        d={arc(cx, cy, r, -Math.PI / 2, angle)}
        stroke={G1}
        strokeWidth={3}
        strokeLinecap="round"
        opacity={0.35}
      />
      <circle cx={sx} cy={sy} r={5} fill={G0} />
      <circle cx={sx} cy={sy} r={2} fill="white" />
      <text x={cx} y={cy + 6} textAnchor="middle" fontSize={16} fontWeight="700" fill="#181530" letterSpacing={-0.5}>2:34</text>
    </svg>
  );
}

// ── Fellowship ────────────────────────────────────────────────────────────────
function FellowshipThumb() {
  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" xmlns="http://www.w3.org/2000/svg" fill="none">
      <rect width={W} height={H} rx={14} fill="#5B8F3E"/>
      <rect y="0" width={W} height="52" fill="#7EC8E3"/>
      <ellipse cx="80" cy="78" rx="70" ry="18" fill="#4A7A34"/>
      <g transform="translate(24, 88)">
        {[0, 18, 36, 54, 72, 90, 108].map((x, i) => (
          <rect key={i} x={x} y={i % 2 === 0 ? 4 : 0} width="12" height="18" rx="1" fill={["#3D5C3A","#9CA3AF","#2D6B3A","#8B2E2E","#8B5A2B","#4A7A34","#2E7A6A"][i]} />
        ))}
      </g>
      <text x={80} y={24} textAnchor="middle" fontSize={8} fontWeight="700" fill="#fff" opacity={0.85}>FELLOWSHIP</text>
    </svg>
  );
}
