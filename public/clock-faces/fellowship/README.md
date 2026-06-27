# Fellowship clock face assets

Pixel-art backgrounds and party sprite for the Fellowship clock face. The scene advances once per hour (7 locations, looping).

## Files

| File | Scene (hour slot) |
|------|-------------------|
| `backgrounds/shire.png` | 12am, 7am, 2pm, 9pm… |
| `backgrounds/bree.png` | Bree / Prancing Pony |
| `backgrounds/rivendell.png` | Rivendell |
| `backgrounds/moria.png` | Mines of Moria |
| `backgrounds/anduin.png` | Anduin / river journey |
| `backgrounds/lothlorien.png` | Lothlórien |
| `backgrounds/mordor.png` | Mordor |

`fellowship.png` — horizontal party sprite strip (transparent or white background).

## Canvas size

- **Backgrounds:** 1200×800 (3:2, @2x). Ground/path should sit near the bottom ~15% for character placement.
- **Fellowship:** wide strip (~1200×320); characters aligned to the bottom edge.

Android copies live in `android/app/src/main/res/drawable-nodpi/fellowship_bg_*.png` and `fellowship_party.png`.
