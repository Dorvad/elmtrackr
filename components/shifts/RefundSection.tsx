"use client";

import React, { useState, FormEvent, useRef, useEffect } from "react";
import type { Shift, RefundClaim, RefundProvider } from "@/types";
import {
  checkRefundEligibility,
  checkToWorkEligibility,
  getRefundStatus,
} from "@/lib/shifts/refund";
import { useRefundClaim, type RefundDirection, type SaveClaimData } from "@/hooks/useRefundClaim";
import { useToast } from "@/components/ui/Toast";

function toLocalDateTimeInput(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

// ── Scooter illustration ──────────────────────────────────────────────────────
const SCOOTER_THEME = {
  lime: {
    frame: "#F4F6F4", outline: "#C7D0C7", strip: "#2C2E33",
    box: "#3FC81E", boxMark: "rgba(255,255,255,.9)",
    tire: "#26262B", rim: "#3C3F47", spoke: "#5A5E68", hub: "#C7CBD4",
    grip: "#2C2E33", light: "#3FC81E",
  },
  dott: {
    frame: "#2E6FD6", outline: "#1B4FA8", strip: "#173B73",
    box: "#E0162B", boxMark: "rgba(255,255,255,.9)",
    tire: "#1F1F24", rim: "#E0162B", spoke: "#9A0E1E", hub: "#F3CACF",
    grip: "#222227", light: "#FFC83D",
  },
} as const;

type ScooterBrand = "lime" | "dott";

function ScooterWheel({ cx, cy, brand, dur }: { cx: number; cy: number; brand: ScooterBrand; dur: string }) {
  const o = SCOOTER_THEME[brand];
  const spokes = [0, 1, 2, 3].map(i => {
    const a = i * 45 * Math.PI / 180;
    return { x1: cx - 12 * Math.cos(a), y1: cy - 12 * Math.sin(a), x2: cx + 12 * Math.cos(a), y2: cy + 12 * Math.sin(a) };
  });
  return (
    <>
      <circle cx={cx} cy={cy} r={22} fill={o.tire} />
      <g style={{ transformOrigin: `${cx}px ${cy}px`, animation: `rideWheelSpin ${dur} linear infinite` }}>
        <circle cx={cx} cy={cy} r={13} fill={o.rim} />
        {spokes.map((s, i) => (
          <line key={i} x1={s.x1} y1={s.y1} x2={s.x2} y2={s.y2}
            stroke={o.spoke} strokeWidth={2.4} strokeLinecap="round" />
        ))}
        <circle cx={cx} cy={cy} r={4} fill={o.hub} />
      </g>
    </>
  );
}

function ScooterSVG({ brand, wheelDur }: { brand: ScooterBrand; wheelDur: string }) {
  const o = SCOOTER_THEME[brand];
  return (
    <svg viewBox="0 0 200 165" aria-hidden
      style={{ display: "block", height: 118, width: "auto", overflow: "visible",
        filter: "drop-shadow(0 7px 7px rgba(20,16,48,.20))" }}>
      <ScooterWheel cx={44}  cy={130} brand={brand} dur={wheelDur} />
      <ScooterWheel cx={156} cy={130} brand={brand} dur={wheelDur} />
      {/* front fork */}
      <line x1={146} y1={114} x2={156} y2={130} stroke={o.outline} strokeWidth={11} strokeLinecap="round" />
      <line x1={146} y1={114} x2={156} y2={130} stroke={o.frame}   strokeWidth={8}  strokeLinecap="round" />
      {/* deck */}
      <rect x={40} y={119} width={110} height={13} rx={6.5} fill={o.frame} stroke={o.outline} strokeWidth={1.5} />
      <rect x={50} y={118} width={92}  height={4}  rx={2}   fill={o.strip} />
      {/* stem */}
      <line x1={148} y1={114} x2={118} y2={32} stroke={o.outline} strokeWidth={14} strokeLinecap="round" />
      <line x1={148} y1={114} x2={118} y2={32} stroke={o.frame}   strokeWidth={11} strokeLinecap="round" />
      {/* battery box */}
      <rect x={117} y={48} width={30} height={48} rx={10} fill={o.box} transform="rotate(-20 132 72)" />
      <circle cx={132} cy={72} r={5} fill={o.boxMark} />
      {/* handlebar */}
      <line x1={96} y1={29} x2={126} y2={33} stroke={o.grip} strokeWidth={8} strokeLinecap="round" />
      <circle cx={96}  cy={29}  r={3.4} fill={o.light} />
      <circle cx={150} cy={110} r={3.6} fill={o.light} />
    </svg>
  );
}

// ── Animated riding scene ─────────────────────────────────────────────────────
const SCENE = {
  lime: {
    skyTop: "#EAFBE8", skyBot: "#CFF3CB",
    sun: "radial-gradient(circle, rgba(120,230,90,.9), rgba(120,230,90,0) 70%)",
    hillFar: "#A9E59E", hillNear: "#8DD680",
    road: "linear-gradient(180deg,#5BB845,#3E9A30)",
  },
  dott: {
    skyTop: "#E9F1FE", skyBot: "#CFE0FA",
    sun: "radial-gradient(circle, rgba(120,170,250,.9), rgba(120,170,250,0) 70%)",
    hillFar: "#A8C4F0", hillNear: "#86A9E6",
    road: "linear-gradient(180deg,#8AA6CE,#6F8CB8)",
  },
} as const;

function RidingScene({ brand, isSelected }: { brand: ScooterBrand; isSelected: boolean }) {
  const c = SCENE[brand];
  const farDur    = isSelected ? "14s"   : "22s";
  const nearDur   = isSelected ? "8s"    : "13s";
  const dashDur   = isSelected ? "0.7s"  : "1.1s";
  const bobDur    = isSelected ? "2s"    : "2.6s";
  const shadowDur = isSelected ? "2s"    : "2.6s";
  const wheelDur  = isSelected ? "0.5s"  : "1.05s";
  const streaks   = isSelected
    ? ["0.9s", "0.7s", "1.1s"]
    : ["1.4s", "1.1s", "1.7s"];

  const hill: React.CSSProperties = { position: "absolute", bottom: 0, borderRadius: "50% 50% 0 0" };
  const hillN: React.CSSProperties = { position: "absolute", bottom: 0, borderRadius: "46% 46% 0 0" };

  return (
    <div className="ride-scene" style={{ position: "relative", height: 160, overflow: "hidden",
      filter: isSelected ? undefined : "saturate(.92)" }}>

      {/* sky */}
      <div style={{ position: "absolute", inset: 0,
        background: `linear-gradient(180deg, ${c.skyTop} 0%, ${c.skyBot} 100%)` }} />

      {/* sun */}
      <div style={{ position: "absolute", top: 18, right: 34, width: 46, height: 46,
        borderRadius: "50%", background: c.sun, filter: "blur(1px)",
        animation: "rideSunPulse 5s ease-in-out infinite" }} />

      {/* clouds */}
      {[
        { w: 46, h: 15, top: 26, dur: "13s", delay: "0s",  op: 1,   bumps: [{ w: 24, h: 24, t: -11, l: 8 }, { w: 18, h: 18, t: -7, l: 26 }] },
        { w: 34, h: 12, top: 52, dur: "18s", delay: "-7s", op: 0.8, bumps: [{ w: 18, h: 18, t: -8,  l: 6 }, { w: 14, h: 14, t: -5, l: 20 }] },
      ].map((cl, i) => (
        <div key={i} style={{ position: "absolute", top: cl.top, width: cl.w, height: cl.h,
          borderRadius: 999, background: "rgba(255,255,255,.85)", filter: "blur(.4px)",
          opacity: cl.op, zIndex: 2, animation: `rideDrift ${cl.dur} linear infinite`, animationDelay: cl.delay }}>
          {cl.bumps.map((b, j) => (
            <div key={j} style={{ position: "absolute", width: b.w, height: b.h,
              top: b.t, left: b.l, borderRadius: 999, background: "rgba(255,255,255,.85)" }} />
          ))}
        </div>
      ))}

      {/* far hills */}
      <div style={{ position: "absolute", left: 0, bottom: 0, display: "flex",
        width: "200%", height: 108, animation: `rideScrollX ${farDur} linear infinite`, willChange: "transform" }}>
        {[0, 1].map(t => (
          <div key={t} style={{ width: "50%", flexShrink: 0, position: "relative", height: "100%" }}>
            <div style={{ ...hill, left: "6%",  width: 120, height: 70,  background: c.hillFar }} />
            <div style={{ ...hill, left: "48%", width: 170, height: 95,  background: c.hillFar }} />
            <div style={{ ...hill, left: "84%", width: 110, height: 60,  background: c.hillFar }} />
          </div>
        ))}
      </div>

      {/* near hills */}
      <div style={{ position: "absolute", left: 0, bottom: 0, display: "flex",
        width: "200%", height: 78, animation: `rideScrollX ${nearDur} linear infinite`, willChange: "transform" }}>
        {[0, 1].map(t => (
          <div key={t} style={{ width: "50%", flexShrink: 0, position: "relative", height: "100%" }}>
            <div style={{ ...hillN, left: "14%", width: 150, height: 60, background: c.hillNear }} />
            <div style={{ ...hillN, left: "64%", width: 130, height: 50, background: c.hillNear }} />
          </div>
        ))}
      </div>

      {/* speed streaks */}
      <div style={{ position: "absolute", bottom: 50, left: 0, width: "100%", height: 74,
        pointerEvents: "none", opacity: isSelected ? 1 : 0.5 }}>
        {[
          { top:  5, width: 46, dur: streaks[0], delay: "0s"   },
          { top: 25, width: 64, dur: streaks[1], delay: "0.5s" },
          { top: 46, width: 38, dur: streaks[2], delay: "0.9s" },
        ].map((s, i) => (
          <div key={i} style={{ position: "absolute", top: s.top, width: s.width, height: 4,
            borderRadius: 3, right: -60, opacity: 0,
            background: "linear-gradient(90deg, transparent, rgba(255,255,255,.85))",
            animation: `rideStreak ${s.dur} linear infinite`, animationDelay: s.delay }} />
        ))}
      </div>

      {/* scooter shadow */}
      <div style={{ position: "absolute", bottom: 17, left: "50%", width: 116, height: 15,
        borderRadius: "50%", zIndex: 3, willChange: "transform, opacity",
        background: "radial-gradient(50% 50%, rgba(20,16,48,.28), rgba(20,16,48,0) 72%)",
        animation: `rideShadow ${shadowDur} ease-in-out infinite` }} />

      {/* rider */}
      <div style={{ position: "absolute", bottom: 20, left: "50%", zIndex: 4, willChange: "transform",
        animation: `rideBob ${bobDur} ease-in-out infinite` }}>
        <ScooterSVG brand={brand} wheelDur={wheelDur} />
      </div>

      {/* road */}
      <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: 44, background: c.road }}>
        <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 3,
          background: "rgba(255,255,255,.55)" }} />
        <div style={{ position: "absolute", top: 18, left: 0, height: 5, width: "200%",
          borderRadius: 3, willChange: "transform",
          background: "repeating-linear-gradient(90deg, rgba(255,255,255,.92) 0 26px, transparent 26px 52px)",
          animation: `rideScrollDash ${dashDur} linear infinite` }} />
      </div>

      {/* brand badge */}
      <div style={{ position: "absolute", top: 12, left: 14, zIndex: 6,
        display: "flex", alignItems: "center", gap: 6,
        padding: "5px 10px 5px 7px", borderRadius: 999,
        background: "rgba(255,255,255,.82)", backdropFilter: "blur(8px)",
        boxShadow: "0 6px 18px -8px rgba(20,16,48,.3)" }}>
        <span style={{ fontFamily: "var(--au-display)", fontWeight: 700, fontSize: 13.5,
          letterSpacing: "-.01em", color: brand === "lime" ? "#1E8A0E" : "#1B4FA8" }}>
          {brand === "lime" ? "Lime" : "dott"}
        </span>
        <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: ".04em",
          color: "var(--au-ink-2)", whiteSpace: "nowrap" }}>e-scooter</span>
      </div>
    </div>
  );
}

// ── Provider card (scooter selection) ─────────────────────────────────────────
function ScooterCard({
  brand, isSelected, onClick,
}: {
  brand: ScooterBrand; isSelected: boolean; onClick: () => void;
}) {
  const name        = brand === "lime" ? "Lime" : "Dott";
  const borderColor = isSelected ? (brand === "lime" ? "#3FC81E" : "#2E6FD6") : "transparent";
  const checkBg     = isSelected ? (brand === "lime" ? "#3FC81E" : "#2E6FD6") : "white";
  const checkBorder = isSelected ? checkBg : "var(--au-hair)";

  return (
    <button type="button" onClick={onClick}
      className="w-full text-left active:scale-[0.993] transition-transform"
      style={{
        borderRadius: 20, overflow: "hidden", cursor: "pointer",
        border: `2px solid ${borderColor}`,
        background: "var(--au-surface)",
        boxShadow: isSelected
          ? "0 22px 52px -22px rgba(80,64,210,.44), 0 2px 8px rgba(80,64,210,.06)"
          : "0 10px 28px -18px rgba(80,64,210,.28), 0 1px 3px rgba(80,64,210,.05)",
        transition: "box-shadow .28s, border-color .28s",
      }}>
      <RidingScene brand={brand} isSelected={isSelected} />

      {/* meta row */}
      <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 16px" }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 15, fontWeight: 800, color: "var(--au-ink)" }}>{name}</div>
          <div style={{ fontSize: 12, color: "var(--au-faint)", marginTop: 2 }}>Shared electric scooter</div>
        </div>
        <span style={{ width: 26, height: 26, borderRadius: "50%", flexShrink: 0,
          display: "flex", alignItems: "center", justifyContent: "center",
          background: checkBg, border: `2px solid ${checkBorder}`, transition: "all .22s" }}>
          <svg width={13} height={13} viewBox="0 0 24 24" fill="none" stroke="white"
            strokeWidth={3.4} strokeLinecap="round" strokeLinejoin="round"
            style={{ opacity: isSelected ? 1 : 0, transform: isSelected ? "scale(1)" : "scale(.5)", transition: "all .22s" }}>
            <path d="M5 12l5 5L20 6" />
          </svg>
        </span>
      </div>
    </button>
  );
}

// ── Single-direction claim card ───────────────────────────────────────────────
interface ClaimCardProps {
  direction: RefundDirection;
  label: string;
  defaultTime: string;
  eligibilityReasons: string[];
  claim: RefundClaim | null;
  loading: boolean;
  saving: boolean;
  onSave: (data: SaveClaimData) => Promise<void>;
  onDelete: () => Promise<void>;
  shiftAction?: Shift["refund_action"];
  onActionChange?: ((action: Shift["refund_action"]) => Promise<void>) | null;
}

function ClaimCard({
  direction, label, defaultTime, eligibilityReasons,
  claim, loading, saving, onSave, onDelete,
  shiftAction, onActionChange,
}: ClaimCardProps) {
  const { toast } = useToast();
  const [showForm, setShowForm]       = useState(false);
  const [provider, setProvider]       = useState<RefundProvider>("Lime");
  const [amount, setAmount]           = useState("");
  const [rideAt, setRideAt]           = useState(() => toLocalDateTimeInput(defaultTime));
  const [notes, setNotes]             = useState("");
  const [receiptFile, setReceiptFile] = useState<File | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!claim) return;
    setProvider(claim.provider as RefundProvider);
    setRideAt(toLocalDateTimeInput(claim.ride_at));
    setNotes(claim.notes ?? "");
    setAmount(String(claim.amount));
  }, [claim]);

  async function handleActionClick(action: Shift["refund_action"]) {
    if (!onActionChange) return;
    try {
      await onActionChange(action);
      toast(
        action === "no_ride_taken" ? "Marked: no ride taken"
          : action === "remind_later" ? "We'll remind you at month end"
          : "Action saved",
        "success",
      );
    } catch {
      toast("Failed to save", "error");
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const finalAmount = parseFloat(amount);
    if (isNaN(finalAmount) || finalAmount <= 0) {
      toast("Enter a valid amount", "error");
      return;
    }
    if (!rideAt) { toast("Enter the ride date & time", "error"); return; }
    try {
      await onSave({ provider, amount: finalAmount, ride_at: new Date(rideAt).toISOString(), notes: notes.trim() || null, receiptFile });
    } catch (err) {
      toast(err instanceof Error ? err.message : "Failed to save claim", "error");
      return;
    }
    if (onActionChange) {
      try { await onActionChange("submitted"); } catch { /* badge syncs on reload */ }
    }
    setShowForm(false);
    setReceiptFile(null);
    toast("Ride claim saved", "success");
  }

  async function handleDelete() {
    try {
      await onDelete();
      if (onActionChange) { try { await onActionChange(null); } catch { /* ignore */ } }
      setAmount("");
      setNotes("");
      setReceiptFile(null);
      toast("Claim removed", "success");
    } catch {
      toast("Failed to remove claim", "error");
    }
  }

  const isFromWork = direction === "from_work";

  // Dynamic CTA label + style
  const ctaLabel = (() => {
    const amt = parseFloat(amount);
    if (isNaN(amt) || amt <= 0) return "Save claim";
    if (provider === "Lime") return `Add ₪${amt.toFixed(2)} Lime refund`;
    if (provider === "Dott") return `Add ₪${amt.toFixed(2)} Dott refund`;
    return `Save ₪${amt.toFixed(2)} claim`;
  })();
  const ctaGradient = provider === "Lime"
    ? "linear-gradient(118deg,#1E8A0E,#3FC81E)"
    : provider === "Dott"
    ? "linear-gradient(118deg,#1B4FA8,#2E6FD6)"
    : "var(--au-grad)";
  const ctaShadow = provider === "Lime"
    ? "0 14px 28px -12px rgba(30,138,14,.52)"
    : provider === "Dott"
    ? "0 14px 28px -12px rgba(27,79,168,.52)"
    : "0 14px 28px -12px rgba(91,77,242,.48)";

  const fieldStyle: React.CSSProperties = {
    width: "100%", borderRadius: 12, border: "1px solid var(--au-hair)",
    padding: "10px 14px", fontSize: 14, color: "var(--au-ink)",
    background: "var(--au-surface)", outline: "none",
  };
  const labelStyle: React.CSSProperties = {
    fontSize: 13, fontWeight: 600, color: "var(--au-ink-2)",
  };

  return (
    <div style={{ borderRadius: 16, border: "1px solid var(--au-hair)",
      background: "var(--au-surface-sub)", padding: 12 }}>

      {/* direction header */}
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
        <span style={{ fontSize: 11, fontWeight: 700, color: "var(--au-faint)",
          textTransform: "uppercase", letterSpacing: ".12em" }}>
          {direction === "to_work" ? "→" : "←"} {label}
        </span>
        {eligibilityReasons.map(r => (
          <span key={r} style={{ padding: "2px 8px", borderRadius: 999,
            background: "var(--au-overtime-bg)", color: "var(--au-overtime-ink)",
            fontSize: 10, fontWeight: 600 }}>
            {r}
          </span>
        ))}
      </div>

      {/* ── no claim, no form ── */}
      {!claim && !showForm && (
        <>
          {isFromWork && shiftAction == null && (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              <button type="button" onClick={() => setShowForm(true)}
                style={{ width: "100%", borderRadius: 13, background: "var(--au-grad)", color: "white",
                  fontSize: 14, fontWeight: 700, padding: "11px 0", border: "none", cursor: "pointer",
                  boxShadow: "0 12px 24px -10px rgba(91,77,242,.42)" }}>
                Add receipt claim
              </button>
              <div style={{ display: "flex", gap: 8 }}>
                <button type="button" onClick={() => handleActionClick("no_ride_taken")}
                  style={{ flex: 1, borderRadius: 13, border: "1px solid var(--au-hair)",
                    color: "var(--au-faint)", fontSize: 13, fontWeight: 600, padding: "9px 0",
                    background: "var(--au-surface)", cursor: "pointer" }}>
                  No ride taken
                </button>
                <button type="button" onClick={() => handleActionClick("remind_later")}
                  style={{ flex: 1, borderRadius: 13, border: "1px solid var(--au-overtime-bg)",
                    color: "var(--au-peach-deep)", fontSize: 13, fontWeight: 600, padding: "9px 0",
                    background: "var(--au-overtime-bg)", cursor: "pointer" }}>
                  Remind me later
                </button>
              </div>
            </div>
          )}

          {isFromWork && shiftAction === "no_ride_taken" && (
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <span style={{ fontSize: 12, color: "var(--au-faint)" }}>No ride taken</span>
              <button type="button" onClick={() => handleActionClick(null)}
                style={{ fontSize: 12, color: "var(--au-faint)", textDecoration: "underline",
                  background: "none", border: "none", cursor: "pointer" }}>
                Undo
              </button>
            </div>
          )}

          {isFromWork && shiftAction === "remind_later" && (
            <div style={{ display: "flex", gap: 8 }}>
              <button type="button" onClick={() => setShowForm(true)}
                style={{ flex: 1, borderRadius: 13, background: "var(--au-grad)", color: "white",
                  fontSize: 14, fontWeight: 700, padding: "11px 0", border: "none", cursor: "pointer" }}>
                Add receipt now
              </button>
              <button type="button" onClick={() => handleActionClick("no_ride_taken")}
                style={{ borderRadius: 13, border: "1px solid var(--au-hair)",
                  color: "var(--au-faint)", fontSize: 13, fontWeight: 600, padding: "9px 14px",
                  background: "var(--au-surface)", cursor: "pointer" }}>
                No ride
              </button>
            </div>
          )}

          {(!isFromWork || shiftAction === "submitted") && (
            <button type="button" onClick={() => setShowForm(true)}
              style={{ width: "100%", borderRadius: 13, background: "var(--au-grad)", color: "white",
                fontSize: 14, fontWeight: 700, padding: "11px 0", border: "none", cursor: "pointer",
                boxShadow: "0 12px 24px -10px rgba(91,77,242,.42)" }}>
              Add receipt claim
            </button>
          )}
        </>
      )}

      {/* ── claim exists ── */}
      {claim && !showForm && (
        <div>
          {loading ? (
            <div style={{ height: 16, width: 128, background: "var(--au-hair)", borderRadius: 8 }} />
          ) : (
            <>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                <p style={{ fontSize: 14, fontWeight: 600, color: "var(--au-ink)" }}>
                  {claim.provider} — ₪{claim.amount.toFixed(2)}
                </p>
                <span style={{ borderRadius: 999, background: "rgba(16,185,129,.12)",
                  color: "var(--color-active)", fontSize: 10, fontWeight: 700, padding: "2px 8px" }}>
                  Submitted
                </span>
              </div>
              <p style={{ fontSize: 11, color: "var(--au-faint)", marginTop: 3 }}>
                {new Date(claim.ride_at).toLocaleString()}
              </p>
              {claim.receipt_path && (
                <p style={{ fontSize: 11, color: "var(--au-indigo)", fontWeight: 600, marginTop: 2 }}>
                  Receipt attached
                </p>
              )}
              {claim.notes && (
                <p style={{ fontSize: 11, color: "var(--au-faint)", marginTop: 2 }}>{claim.notes}</p>
              )}
              <div style={{ display: "flex", gap: 12, marginTop: 8 }}>
                <button type="button" onClick={() => setShowForm(true)}
                  style={{ fontSize: 12, color: "var(--au-indigo)", fontWeight: 700,
                    textDecoration: "underline", background: "none", border: "none", cursor: "pointer", padding: 0 }}>
                  Edit
                </button>
                <button type="button" onClick={handleDelete} disabled={saving}
                  style={{ fontSize: 12, color: "#f87171", fontWeight: 700,
                    textDecoration: "underline", background: "none", border: "none",
                    cursor: saving ? "not-allowed" : "pointer", padding: 0, opacity: saving ? 0.5 : 1 }}>
                  Delete
                </button>
              </div>
            </>
          )}
        </div>
      )}

      {/* ── claim form ── */}
      {showForm && (
        <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: 10 }}>

          {/* scooter cards */}
          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            <ScooterCard brand="lime" isSelected={provider === "Lime"}
              onClick={() => setProvider("Lime")} />
            <ScooterCard brand="dott" isSelected={provider === "Dott"}
              onClick={() => setProvider("Dott")} />
            <button type="button" onClick={() => setProvider("Other")}
              style={{
                borderRadius: 13, padding: "10px 16px", fontSize: 14, fontWeight: 600,
                cursor: "pointer", textAlign: "left", transition: "all .2s",
                border: `2px solid ${provider === "Other" ? "var(--au-indigo)" : "var(--au-hair)"}`,
                background: provider === "Other" ? "var(--au-surface-sub)" : "var(--au-surface)",
                color: provider === "Other" ? "var(--au-indigo)" : "var(--au-faint)",
              }}>
              Other provider
            </button>
          </div>

          {/* amount — same field for all providers */}
          <div style={{ display: "flex", flexDirection: "column", gap: 5 }}>
            <label style={labelStyle}>Amount (₪)</label>
            <input type="number" min={0.01} step={0.01} placeholder="e.g. 12.50"
              value={amount} onChange={e => setAmount(e.target.value)} style={fieldStyle} />
          </div>

          {/* ride date/time */}
          <div style={{ display: "flex", flexDirection: "column", gap: 5 }}>
            <label style={labelStyle}>Ride date &amp; time</label>
            <input type="datetime-local" value={rideAt}
              onChange={e => setRideAt(e.target.value)} style={fieldStyle} />
          </div>

          {/* receipt upload */}
          <div style={{ display: "flex", flexDirection: "column", gap: 5 }}>
            <label style={labelStyle}>
              Receipt{" "}
              <span style={{ fontWeight: 400, color: "var(--au-faint)" }}>(optional)</span>
            </label>
            <input ref={fileRef} type="file" accept="image/*" style={{ display: "none" }}
              onChange={e => setReceiptFile(e.target.files?.[0] ?? null)} />
            <button type="button" onClick={() => fileRef.current?.click()}
              style={{
                width: "100%", borderRadius: 12, padding: "10px 0", fontSize: 13, cursor: "pointer",
                border: `2px dashed ${receiptFile ? "var(--au-indigo)" : "var(--au-hair)"}`,
                background: receiptFile ? "var(--au-surface-sub)" : "transparent",
                color: receiptFile ? "var(--au-indigo)" : "var(--au-faint)",
                fontWeight: receiptFile ? 600 : 400,
              }}>
              {receiptFile ? `📎 ${receiptFile.name}` : "Tap to attach a photo"}
            </button>
          </div>

          {/* notes */}
          <div style={{ display: "flex", flexDirection: "column", gap: 5 }}>
            <label style={labelStyle}>
              Notes{" "}
              <span style={{ fontWeight: 400, color: "var(--au-faint)" }}>(optional)</span>
            </label>
            <textarea rows={2} placeholder="Route, trip ID, reason…" value={notes}
              onChange={e => setNotes(e.target.value)}
              style={{ ...fieldStyle, resize: "none" }} />
          </div>

          {/* submit + cancel */}
          <div style={{ display: "flex", gap: 8 }}>
            <button type="submit" disabled={saving}
              style={{
                flex: 1, borderRadius: 15, padding: "15px 0", color: "white",
                fontSize: 14.5, fontWeight: 700, border: "none",
                cursor: saving ? "not-allowed" : "pointer",
                background: ctaGradient, boxShadow: ctaShadow,
                opacity: saving ? 0.7 : 1, display: "flex", alignItems: "center",
                justifyContent: "center", gap: 8, transition: "opacity .22s, transform .14s",
                letterSpacing: ".01em",
              }}>
              {saving ? "Saving…" : ctaLabel}
            </button>
            <button type="button"
              onClick={() => { setShowForm(false); setReceiptFile(null); }}
              style={{ borderRadius: 15, padding: "15px 16px", fontSize: 14, fontWeight: 600,
                border: "1px solid var(--au-hair)", background: "var(--au-surface)",
                color: "var(--au-faint)", cursor: "pointer" }}>
              Cancel
            </button>
          </div>
        </form>
      )}
    </div>
  );
}

// ── RefundSection (shell) ─────────────────────────────────────────────────────
interface Props {
  shift: Shift;
  onActionChange: (action: Shift["refund_action"]) => Promise<void>;
}

export function RefundSection({ shift, onActionChange }: Props) {
  const fromEligibility = checkRefundEligibility(shift);
  const toEligibility   = checkToWorkEligibility(shift);
  const status          = getRefundStatus(shift);
  const { claims, loading, saving, saveClaim, deleteClaim } = useRefundClaim(shift.id);

  if (!fromEligibility.eligible && !toEligibility.eligible) return null;

  const STATUS_STYLE: Record<string, { bg: string; color: string }> = {
    emerald: { bg: "rgba(16,185,129,.1)",     color: "var(--color-active)" },
    amber:   { bg: "var(--au-overtime-bg)",   color: "var(--au-peach-deep)" },
    orange:  { bg: "var(--au-overtime-bg)",   color: "var(--au-peach-deep)" },
    gray:    { bg: "var(--au-surface-sub)",   color: "var(--au-faint)" },
  };
  const ss = STATUS_STYLE[status.color] ?? STATUS_STYLE.orange;

  return (
    <div className="rounded-3xl border border-white/80 au-card bg-white p-4">
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 12 }}>
        <h2 style={{ fontSize: 11, fontWeight: 700, color: "var(--au-faint)",
          textTransform: "uppercase", letterSpacing: ".16em" }}>
          Travel Refund
        </h2>
        {status.label && (
          <span style={{ padding: "3px 10px", borderRadius: 999, fontSize: 11, fontWeight: 700,
            background: ss.bg, color: ss.color }}>
            {status.label}
          </span>
        )}
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        {toEligibility.eligible && (
          <ClaimCard
            direction="to_work" label="Ride to work"
            defaultTime={shift.start_time}
            eligibilityReasons={toEligibility.reasons}
            claim={claims.to_work} loading={loading} saving={saving === "to_work"}
            onSave={data => saveClaim("to_work", data)}
            onDelete={() => deleteClaim("to_work")}
            onActionChange={onActionChange}
          />
        )}
        {fromEligibility.eligible && (
          <ClaimCard
            direction="from_work" label="Ride from work"
            defaultTime={shift.end_time ?? shift.start_time}
            eligibilityReasons={fromEligibility.reasons}
            claim={claims.from_work} loading={loading} saving={saving === "from_work"}
            onSave={data => saveClaim("from_work", data)}
            onDelete={() => deleteClaim("from_work")}
            shiftAction={shift.refund_action}
            onActionChange={onActionChange}
          />
        )}
      </div>
    </div>
  );
}
