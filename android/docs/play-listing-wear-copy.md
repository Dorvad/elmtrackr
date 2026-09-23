# Play listing copy — Wear OS (WO-G2)

Paste this into Play Console **before** sending a Wear artifact for review.
This is a listing change, not a code change. Play has rejected the watch app
for this exact sentence four times (July 2026, then every Wear resubmission
that left the listing untouched).

**Requirement WO-G2:** the Google Play store listing must mention **tile** and
**complication** if the app includes those surfaces. ElmTrackr includes both.
The reviewer greps for those English words. They must appear **literally**, in
**every language the listing is offered in**.

Do not mention "Android Wear". "Wear OS" is allowed.

The app ships `en`, `iw`, `ar`, and `ru`. If the listing has a translation in
a language you skip, that language fails on its own.

---

## English — add this paragraph to the full description

> On Wear OS: clock in and out straight from your wrist. ElmTrackr includes a watch app with a live shift timer, a tile for one-tap punch in/out with your daily progress ring, and a watch-face complication showing your current shift status and elapsed time at a glance.

## Hebrew

> ב-Wear OS: כניסה ויציאה ממשמרת ישירות מהשעון. ElmTrackr כוללת אפליקציית שעון עם טיימר משמרת חי, אריח (tile) לכניסה/יציאה בהקשה אחת עם טבעת התקדמות יומית, וסיבוכיית (complication) לפני השעון שמציגה את סטטוס המשמרת והזמן שחלף במבט אחד.

## Arabic

> على Wear OS: سجّل الدخول والخروج من معصمك. تتضمن ElmTrackr تطبيق ساعة مع مؤقت مناوبة مباشر، وtile للتسجيل بنقرة واحدة مع حلقة التقدم اليومية، وcomplication على وجه الساعة تعرض حالة المناوبة والوقت المنقضي.

## Russian

> На Wear OS: отмечайтесь прямо с запястья. ElmTrackr включает приложение для часов с живым таймером смены, tile для отметки одним касанием с кольцом дневного прогресса и complication на циферблате со статусом смены и прошедшим временем.

---

## How to confirm it took

In Play Console → Grow → Store presence → Main store listing (and every
translation):

1. Search the full description for `tile`.
2. Search it for `complication`.
3. Repeat for Hebrew, Arabic, and Russian if those locales are listed.

If either word is missing in any listed language, do not submit. The 17
September 2026 rejection of version code **10055** was this finding, word for
word, plus a separate crash finding.
