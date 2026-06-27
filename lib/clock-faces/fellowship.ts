/** Journey backgrounds — one scene per hour, looping in order. */
export const FELLOWSHIP_BACKGROUNDS = [
  { id: "shire", label: "The Shire", file: "shire.png" },
  { id: "bree", label: "Bree", file: "bree.png" },
  { id: "rivendell", label: "Rivendell", file: "rivendell.png" },
  { id: "moria", label: "Moria", file: "moria.png" },
  { id: "anduin", label: "Anduin", file: "anduin.png" },
  { id: "lothlorien", label: "Lothlórien", file: "lothlorien.png" },
  { id: "mordor", label: "Mordor", file: "mordor.png" },
] as const;

export const FELLOWSHIP_ASSET_BASE = "/clock-faces/fellowship";

export function fellowshipBackgroundPath(file: string): string {
  return `${FELLOWSHIP_ASSET_BASE}/backgrounds/${file}`;
}

export function fellowshipPartyPath(): string {
  return `${FELLOWSHIP_ASSET_BASE}/fellowship.png`;
}

/** Which background index to show for the current clock hour (0–23). */
export function fellowshipBackgroundIndex(hour: number): number {
  const count = FELLOWSHIP_BACKGROUNDS.length;
  return ((hour % count) + count) % count;
}
