const STORAGE_KEY = "elmtrackr-first-clock-in-celebrated";

export function hasCelebratedFirstClockIn(): boolean {
  if (typeof window === "undefined") return false;
  try {
    return localStorage.getItem(STORAGE_KEY) === "true";
  } catch {
    return false;
  }
}

export function markFirstClockInCelebrated(): void {
  if (typeof window === "undefined") return;
  try {
    localStorage.setItem(STORAGE_KEY, "true");
  } catch {
    // Ignore storage failures — celebration may show again.
  }
}
