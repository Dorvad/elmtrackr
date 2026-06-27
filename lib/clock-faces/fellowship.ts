/** Journey backgrounds — one scene per hour, looping in order. */
export const FELLOWSHIP_BACKGROUNDS = [
  { id: "shire", label: "The Shire", file: "shire.svg" },
  { id: "bree", label: "Bree", file: "bree.svg" },
  { id: "rivendell", label: "Rivendell", file: "rivendell.svg" },
  { id: "moria", label: "Moria", file: "moria.svg" },
  { id: "anduin", label: "Anduin", file: "anduin.svg" },
  { id: "lothlorien", label: "Lothlórien", file: "lothlorien.svg" },
  { id: "mordor", label: "Mordor", file: "mordor.svg" },
] as const;

export const FELLOWSHIP_ASSET_BASE = "/clock-faces/fellowship";

export function fellowshipBackgroundPath(file: string): string {
  return `${FELLOWSHIP_ASSET_BASE}/backgrounds/${file}`;
}

export function fellowshipPartyPath(): string {
  return `${FELLOWSHIP_ASSET_BASE}/fellowship.svg`;
}

/** Which background index to show for the current clock hour (0–23). */
export function fellowshipBackgroundIndex(hour: number): number {
  const count = FELLOWSHIP_BACKGROUNDS.length;
  return ((hour % count) + count) % count;
}
