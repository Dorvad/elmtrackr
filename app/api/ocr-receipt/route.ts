import Anthropic from "@anthropic-ai/sdk";
import { NextRequest } from "next/server";

const client = new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY });

export async function POST(request: NextRequest) {
  const apiKey = process.env.ANTHROPIC_API_KEY;
  if (!apiKey) {
    return Response.json(
      { error: "ANTHROPIC_API_KEY is not configured" },
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

  const allowedTypes = ["image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf"];
  if (!allowedTypes.includes(file.type)) {
    return Response.json(
      { error: "Unsupported file type. Use JPEG, PNG, WebP, GIF, or PDF." },
      { status: 415 }
    );
  }

  const buffer = Buffer.from(await file.arrayBuffer());
  const base64 = buffer.toString("base64");

  // PDFs need to be handled as documents, images as image blocks
  const isPdf = file.type === "application/pdf";

  type ImageMediaType = "image/jpeg" | "image/png" | "image/gif" | "image/webp";
  const contentBlock = isPdf
    ? ({
        type: "document" as const,
        source: {
          type: "base64" as const,
          media_type: "application/pdf" as const,
          data: base64,
        },
      })
    : ({
        type: "image" as const,
        source: {
          type: "base64" as const,
          media_type: file.type as ImageMediaType,
          data: base64,
        },
      });

  const prompt = `You are a receipt OCR assistant. Extract the following fields from the receipt image or document:
1. The total amount paid (numeric value only, no currency symbol)
2. The date and time of the ride/transaction

Return ONLY a JSON object with this exact shape:
{
  "amount": <number or null>,
  "ride_date": "<YYYY-MM-DD or null>",
  "ride_time": "<HH:MM in 24h format or null>"
}

Rules:
- If a field cannot be determined, use null
- For amount: extract the final total charged (not subtotals). If multiple currencies appear, prefer ILS (₪)
- For date/time: use the ride/trip time, not the receipt printing time if both are present
- Return ONLY the JSON object, no extra text`;

  try {
    const response = await client.messages.create({
      model: "claude-opus-4-7",
      max_tokens: 256,
      messages: [
        {
          role: "user",
          content: [contentBlock, { type: "text", text: prompt }],
        },
      ],
    });

    const raw = response.content[0];
    if (raw.type !== "text") {
      return Response.json({ error: "Unexpected response from OCR" }, { status: 502 });
    }

    // Strip markdown code fences if present
    const jsonText = raw.text.replace(/```(?:json)?\s*/gi, "").replace(/```/g, "").trim();

    let parsed: { amount: number | null; ride_date: string | null; ride_time: string | null };
    try {
      parsed = JSON.parse(jsonText);
    } catch {
      return Response.json({ error: "Could not parse OCR result" }, { status: 502 });
    }

    return Response.json(parsed);
  } catch (err) {
    const msg = err instanceof Error ? err.message : "OCR failed";
    return Response.json({ error: msg }, { status: 502 });
  }
}
