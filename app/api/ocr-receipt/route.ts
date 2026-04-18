import { NextRequest } from "next/server";

// OCR.space supported image types (PDF is also accepted)
const ALLOWED_TYPES = ["image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf"];

interface OcrSpaceResult {
  ParsedResults?: { ParsedText: string }[];
  IsErroredOnProcessing?: boolean;
  ErrorMessage?: string | string[];
}

/**
 * Parse plain text from a receipt to extract amount and ride date/time.
 *
 * Amount: looks for the largest currency-like number, preferring ₪/ILS context.
 * Date:   handles DD/MM/YYYY, DD.MM.YYYY, YYYY-MM-DD, and Hebrew/mixed formats.
 * Time:   looks for HH:MM patterns (24h or 12h with am/pm).
 */
function parseReceiptText(text: string): {
  amount: number | null;
  ride_date: string | null;
  ride_time: string | null;
} {
  // ── Amount ────────────────────────────────────────────────────────────────
  // Match numbers near a currency symbol or the word "total" (case-insensitive)
  // Pick the largest match to avoid picking up small subtotals / item prices
  const amountPatterns = [
    /(?:₪|ils|nis|total[:\s]*)[^\d]*([\d]+(?:[.,]\d{1,2})?)/gi,
    /([\d]+(?:[.,]\d{1,2})?)(?:\s*(?:₪|ils|nis))/gi,
  ];
  const candidates: number[] = [];
  for (const re of amountPatterns) {
    let m: RegExpExecArray | null;
    while ((m = re.exec(text)) !== null) {
      const n = parseFloat(m[1].replace(",", "."));
      if (!isNaN(n) && n > 0) candidates.push(n);
    }
  }
  // Fallback: grab all decimal numbers and take the largest ≤ 9999
  if (candidates.length === 0) {
    const all = [...text.matchAll(/([\d]+[.,]\d{2})/g)].map((m) =>
      parseFloat(m[1].replace(",", "."))
    );
    candidates.push(...all.filter((n) => n > 0 && n <= 9999));
  }
  const amount = candidates.length > 0 ? Math.max(...candidates) : null;

  // ── Date ──────────────────────────────────────────────────────────────────
  let ride_date: string | null = null;

  // YYYY-MM-DD
  const iso = text.match(/\b(\d{4})-(\d{2})-(\d{2})\b/);
  if (iso) {
    ride_date = `${iso[1]}-${iso[2].padStart(2, "0")}-${iso[3].padStart(2, "0")}`;
  }

  if (!ride_date) {
    // DD/MM/YYYY or DD.MM.YYYY
    const dmy = text.match(/\b(\d{1,2})[/.](\d{1,2})[/.](\d{4})\b/);
    if (dmy) {
      ride_date = `${dmy[3]}-${dmy[2].padStart(2, "0")}-${dmy[1].padStart(2, "0")}`;
    }
  }

  if (!ride_date) {
    // MM/DD/YYYY (US style — lower priority)
    const mdy = text.match(/\b(\d{1,2})\/(\d{1,2})\/(\d{4})\b/);
    if (mdy) {
      ride_date = `${mdy[3]}-${mdy[1].padStart(2, "0")}-${mdy[2].padStart(2, "0")}`;
    }
  }

  // ── Time ──────────────────────────────────────────────────────────────────
  let ride_time: string | null = null;

  // HH:MM:SS or HH:MM (24h)
  const time24 = text.match(/\b([01]?\d|2[0-3]):([0-5]\d)(?::[0-5]\d)?\b/);
  if (time24) {
    ride_time = `${time24[1].padStart(2, "0")}:${time24[2]}`;
  }

  if (!ride_time) {
    // 12h with am/pm
    const time12 = text.match(/\b(1[0-2]|0?[1-9]):([0-5]\d)\s*(am|pm)\b/i);
    if (time12) {
      let h = parseInt(time12[1], 10);
      const isPm = time12[3].toLowerCase() === "pm";
      if (isPm && h !== 12) h += 12;
      if (!isPm && h === 12) h = 0;
      ride_time = `${String(h).padStart(2, "0")}:${time12[2]}`;
    }
  }

  return { amount: amount ?? null, ride_date, ride_time };
}

export async function POST(request: NextRequest) {
  const apiKey = process.env.OCR_SPACE_API_KEY;
  if (!apiKey) {
    return Response.json(
      { error: "OCR_SPACE_API_KEY is not configured" },
      { status: 503 }
    );
  }

  let file: File;
  try {
    const form = await request.formData();
    const raw = form.get("file");
    if (!(raw instanceof File)) {
      return Response.json({ error: "No file provided" }, { status: 400 });
    }
    file = raw;
  } catch {
    return Response.json({ error: "Invalid form data" }, { status: 400 });
  }

  if (!ALLOWED_TYPES.includes(file.type)) {
    return Response.json(
      { error: "Unsupported file type. Use JPEG, PNG, WebP, GIF, or PDF." },
      { status: 415 }
    );
  }

  // Forward the file to OCR.space
  const ocrForm = new FormData();
  ocrForm.append("apikey", apiKey);
  ocrForm.append("language", "eng");   // also handles numbers in any language
  ocrForm.append("isOverlayRequired", "false");
  ocrForm.append("file", file);

  let ocrText: string;
  try {
    const res = await fetch("https://api.ocr.space/parse/image", {
      method: "POST",
      body: ocrForm,
    });
    if (!res.ok) {
      return Response.json({ error: `OCR service error: ${res.status}` }, { status: 502 });
    }
    const json: OcrSpaceResult = await res.json();
    if (json.IsErroredOnProcessing) {
      const msg = Array.isArray(json.ErrorMessage)
        ? json.ErrorMessage.join("; ")
        : (json.ErrorMessage ?? "OCR processing failed");
      return Response.json({ error: msg }, { status: 502 });
    }
    ocrText = json.ParsedResults?.[0]?.ParsedText ?? "";
  } catch (err) {
    const msg = err instanceof Error ? err.message : "Failed to reach OCR service";
    return Response.json({ error: msg }, { status: 502 });
  }

  if (!ocrText.trim()) {
    return Response.json({ amount: null, ride_date: null, ride_time: null });
  }

  return Response.json(parseReceiptText(ocrText));
}
