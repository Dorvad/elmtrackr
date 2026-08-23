#!/usr/bin/env python3
"""Check candidate Wear OS store screenshots against Play's asset rules.

Google rejected the August 2026 ElmTrackr listing with:

    "Your app Wear screenshots must not be positioned within the device frames,
     or include additional text, graphics, or backgrounds that are not part of
     the interface of the app."

The hard checks below (format, square aspect, size bounds, count) are the parts
of the spec that can be verified mechanically. The frame check is a heuristic
and is reported as a warning, not a failure: it looks for the uniform border a
mockup template leaves around a pasted-in screen. Whether a screenshot shows
only the app's own interface is ultimately a judgement call, so read the
warnings rather than trusting a clean run.

Play's published numbers change. Confirm the current spec at
https://support.google.com/googleplay/android-developer/answer/9866151 before
relying on the limits encoded here.

    Usage: tools/check-wear-screenshots.py <dir-or-file> [...]
"""

from __future__ import annotations

import pathlib
import struct
import sys
import zlib

# Play's Wear screenshot spec as published at the time of writing.
MIN_SIDE = 384
MAX_SIDE = 3840
MAX_BYTES = 8 * 1024 * 1024
MIN_COUNT = 1
MAX_COUNT = 8

# Known Wear OS screen sizes, for a sanity note when a capture does not match
# any of them (a resized or re-exported screenshot usually will not).
KNOWN_SIZES = {384, 408, 416, 450, 454, 466, 480, 486, 502}


class Png:
    def __init__(self, path: pathlib.Path) -> None:
        self.path = path
        self.data = path.read_bytes()
        self.width = 0
        self.height = 0
        self.bit_depth = 0
        self.colour_type = 0
        self.pixels: list[list[tuple[int, int, int]]] | None = None
        self._parse()

    @property
    def is_png(self) -> bool:
        return self.data[:8] == b"\x89PNG\r\n\x1a\n"

    @property
    def is_jpeg(self) -> bool:
        return self.data[:2] == b"\xff\xd8"

    def _parse(self) -> None:
        if not self.is_png:
            return
        offset, idat = 8, bytearray()
        while offset < len(self.data):
            (length,) = struct.unpack(">I", self.data[offset:offset + 4])
            kind = self.data[offset + 4:offset + 8]
            body = self.data[offset + 8:offset + 8 + length]
            if kind == b"IHDR":
                self.width, self.height, self.bit_depth, self.colour_type = struct.unpack(
                    ">IIBB", body[:10]
                )
            elif kind == b"IDAT":
                idat += body
            elif kind == b"IEND":
                break
            offset += length + 12
        if self.bit_depth == 8 and self.colour_type in (2, 6):
            self.pixels = self._decode(bytes(idat))

    def _decode(self, idat: bytes) -> list[list[tuple[int, int, int]]] | None:
        """Un-filter the scanlines. 8-bit RGB / RGBA only, which is what
        `adb exec-out screencap -p` writes."""
        try:
            raw = zlib.decompress(idat)
        except zlib.error:
            return None
        channels = 3 if self.colour_type == 2 else 4
        stride = self.width * channels
        rows: list[list[tuple[int, int, int]]] = []
        previous = bytearray(stride)
        pos = 0
        for _ in range(self.height):
            if pos >= len(raw):
                return None
            filter_type = raw[pos]
            pos += 1
            line = bytearray(raw[pos:pos + stride])
            pos += stride
            if len(line) < stride:
                return None
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                up = previous[i]
                up_left = previous[i - channels] if i >= channels else 0
                if filter_type == 1:
                    line[i] = (line[i] + left) & 0xFF
                elif filter_type == 2:
                    line[i] = (line[i] + up) & 0xFF
                elif filter_type == 3:
                    line[i] = (line[i] + ((left + up) >> 1)) & 0xFF
                elif filter_type == 4:
                    p = left + up - up_left
                    pa, pb, pc = abs(p - left), abs(p - up), abs(p - up_left)
                    nearest = left if (pa <= pb and pa <= pc) else (up if pb <= pc else up_left)
                    line[i] = (line[i] + nearest) & 0xFF
                elif filter_type != 0:
                    return None
            rows.append(
                [
                    (line[x * channels], line[x * channels + 1], line[x * channels + 2])
                    for x in range(self.width)
                ]
            )
            previous = line
        return rows


def border_ring(png: Png, inset: int = 2) -> list[tuple[int, int, int]]:
    """The pixels just inside each edge, where a mockup frame would sit."""
    assert png.pixels is not None
    rows = png.pixels
    top, bottom = rows[inset], rows[-1 - inset]
    ring = list(top) + list(bottom)
    for row in rows[inset:-inset or None]:
        ring.append(row[inset])
        ring.append(row[-1 - inset])
    return ring


def check(path: pathlib.Path) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []

    size = path.stat().st_size
    if size > MAX_BYTES:
        errors.append(f"{size / 1024 / 1024:.1f} MB exceeds the {MAX_BYTES // 1024 // 1024} MB limit")

    png = Png(path)
    if not png.is_png:
        if png.is_jpeg:
            warnings.append("JPEG is accepted, but PNG is lossless — prefer a PNG straight from screencap")
        else:
            errors.append("not a PNG or JPEG")
        return errors, warnings

    if png.width == 0 or png.height == 0:
        errors.append("could not read the image dimensions")
        return errors, warnings

    if png.width != png.height:
        errors.append(
            f"{png.width}x{png.height} is not square — Play requires a 1:1 Wear screenshot, "
            "and a non-square one is usually a screen pasted onto a background"
        )
    for side, label in ((png.width, "width"), (png.height, "height")):
        if side < MIN_SIDE:
            errors.append(f"{label} {side}px is below the {MIN_SIDE}px minimum")
        if side > MAX_SIDE:
            errors.append(f"{label} {side}px is above the {MAX_SIDE}px maximum")

    if png.width == png.height and png.width not in KNOWN_SIZES:
        warnings.append(
            f"{png.width}px matches no common Wear OS screen size — check this is an "
            "untouched capture rather than a resized export"
        )

    if png.pixels is None:
        warnings.append("could not decode the pixels, so the device-frame check was skipped")
        return errors, warnings

    ring = border_ring(png)
    unique = set(ring)
    if len(unique) == 1:
        colour = next(iter(unique))
        # A round Wear app on the app's own black face legitimately has black
        # corners. Any other flat colour around the whole edge is a frame, a
        # mat, or a background the app never drew.
        if colour != (0, 0, 0):
            errors.append(
                f"every edge pixel is the same non-black colour rgb{colour} — this looks like a "
                "device frame or an added background, which is what Play rejected"
            )
    else:
        darkest = min(sum(c) for c in ring)
        if darkest > 90:
            warnings.append(
                "no dark pixels anywhere on the edge — worth confirming the screenshot is not "
                "sitting on a light mockup background"
            )
    return errors, warnings


def main(argv: list[str]) -> int:
    targets: list[pathlib.Path] = []
    for arg in argv or ["."]:
        path = pathlib.Path(arg)
        if path.is_dir():
            targets += sorted(
                p for p in path.iterdir()
                if p.suffix.lower() in (".png", ".jpg", ".jpeg")
            )
        elif path.is_file():
            targets.append(path)
        else:
            print(f"no such path: {path}", file=sys.stderr)
            return 2

    if not targets:
        print("No screenshots found.", file=sys.stderr)
        return 2

    failed = 0
    for path in targets:
        errors, warnings = check(path)
        status = "FAIL" if errors else ("WARN" if warnings else "OK  ")
        print(f"{status} {path}")
        for message in errors:
            print(f"       error:   {message}")
        for message in warnings:
            print(f"       warning: {message}")
        failed += bool(errors)

    print()
    print(f"{len(targets)} screenshot(s), {failed} with blocking problems.")
    if not MIN_COUNT <= len(targets) <= MAX_COUNT:
        print(f"Play accepts {MIN_COUNT}-{MAX_COUNT} Wear screenshots per listing.")
        failed += 1
    if not failed:
        print(
            "Mechanical checks passed. Still confirm by eye that each image shows only the "
            "app's own interface — no frame, no caption, no added graphic."
        )
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
