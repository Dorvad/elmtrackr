# Fellowship clock face assets

Pixel-art backgrounds and party sprite for the Fellowship clock face. The scene advances once per hour (7 locations, looping).

## Files

| File | Scene (hour slot) |
|------|-------------------|
| `backgrounds/shire.svg` + `.png` | 12am, 7am, 2pm, 9pm… |
| `backgrounds/bree.svg` + `.png` | Bree / Prancing Pony |
| `backgrounds/rivendell.svg` + `.png` | Rivendell |
| `backgrounds/moria.svg` + `.png` | Mines of Moria |
| `backgrounds/anduin.svg` + `.png` | Anduin / river journey |
| `backgrounds/lothlorien.svg` + `.png` | Lothlórien |
| `backgrounds/mordor.svg` + `.png` | Mordor |

`fellowship.svg` + `fellowship.png` — horizontal party sprite strip.

Web loads the `.svg` wrappers (which reference the `.png` raster). Android uses the PNG drawables in `android/app/src/main/res/drawable-nodpi/`.

## Canvas size

- **Backgrounds:** 1200×800 (3:2). Ground/path near the bottom ~15%.
- **Fellowship:** ~1200×360 wide strip; characters on the bottom edge.

To replace art, overwrite both the `.png` and matching `.svg` pair (or update the SVG if you have true vector exports).
