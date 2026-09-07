package com.elmtrackr.app.ui.dashboard

import androidx.compose.ui.graphics.Color

/**
 * The Evolution pack's creatures, as pixel art.
 *
 * Four cute originals — a leaf sprite, a flame sprite, a water sprite and a static
 * sprite — each with nine forms: the shell it starts the day in, then one evolution an
 * hour until the final form arrives at the eighth. Which is the whole idea of the pack:
 * a day's work drawn as something that grows, where every hour on the clock is a
 * visible, earned change rather than another percent of a bar.
 *
 * ### Why the art lives here as characters
 *
 * The rest of the catalogue draws with vectors, because a ring or a rail is a curve.
 * A pixel creature is not; it is a decision per pixel, and the decisions are the art.
 * Held as rows of characters they can be read, diffed and edited in place — a leaf moved
 * one pixel is a one-character change in review — and they cost the APK nothing beyond
 * the strings, where nine PNG variants per creature at four densities would be 144
 * assets to keep in step. [drawEvolutionFace] maps a character to a colour and a colour
 * to a rectangle, so the drawing scales with the face box the way every other face does.
 *
 * The legend, one character per pixel:
 *
 * ```
 * .  nothing        m  the body's mid tone   a  the species' flourish
 * o  outline        l  its lit side          b  the flourish's own shade
 * d  its shade      w  the gleam on it       c  a blush
 * p  the belly      e  eye white             k  pupil        y  mouth
 * ```
 *
 * Three conventions the renderer depends on, so a hand-edit must keep them:
 *
 * - **Each form is stored cropped and is drawn centred and standing on a ground line.**
 *   The forms are different sizes — hour one is eight rows and hour eight is twenty — and
 *   bottom-aligning them is what makes the creature grow upward from where it stands
 *   instead of scaling around its middle.
 * - **Paired features are authored as pairs, and centred features have even widths.**
 *   The grid is even-width, so its axis of symmetry runs between two columns rather than
 *   down one: an odd-width crown cannot sit on that axis, and the first draft of these
 *   sprites had one eye a pixel further out than the other for exactly that reason. A
 *   tail is the deliberate exception to the mirror — it is one-sided, which is what makes
 *   a creature look like it is facing you rather than like a pattern.
 * - **Eyes are authored as a two-pixel pair**, `ek` on the left of the face and `ke` on
 *   the right, so both pupils look inward. The renderer finds those pairs by shape and
 *   re-emits them to blink and to glance about; see [drawEvolutionFace].
 */

/** How many hours of work the final form takes. */
internal const val EVOLUTION_FINAL_STAGE = 8

/** One creature's colour ramp: the darkest ink to the gleam, plus its flourish. */
internal class EvolutionRamp(
    val outline: Color,
    val shade: Color,
    val base: Color,
    val light: Color,
    val gleam: Color,
    val belly: Color,
    /** The species' own colour: leaf tips, flame, fin, arc. */
    val accent: Color,
    val accentShade: Color,
    val blush: Color,
)

/** What rises off a creature while the shift runs. */
internal enum class EvolutionMote { SPORE, EMBER, BUBBLE, ARC }

/** A creature: how it is coloured, what it grows into, and what it gives off. */
internal class EvolutionSpecies(
    val ramp: EvolutionRamp,
    /** The nine forms, the shell first. Cropped; see this file's KDoc. */
    val forms: List<List<String>>,
    val mote: EvolutionMote,
)

// The eye and mouth inks, shared: four creatures with four different whites would read
// as four different drawings rather than one pack.
internal val EvolutionEyeWhite = Color(0xfff4f8ff)
internal val EvolutionEyeInk = Color(0xff161620)
internal val EvolutionMouthInk = Color(0xff361a20)

/** The colour [cell] paints, or null for a pixel the form leaves empty. */
internal fun EvolutionRamp.inkOf(cell: Char): Color? = when (cell) {
    'o' -> outline
    'd' -> shade
    'm' -> base
    'l' -> light
    'w' -> gleam
    'p' -> belly
    'a' -> accent
    'b' -> accentShade
    'c' -> blush
    'e' -> EvolutionEyeWhite
    'k' -> EvolutionEyeInk
    'y' -> EvolutionMouthInk
    else -> null
}

private val FernForms = listOf(
    // hour 0 — the shell
    listOf(
        "....aa....",
        "...abba...",
        "....bb....",
        "....oo....",
        "...ommo...",
        "..ollmmo..",
        "..owlmdo..",
        ".olllmmdo.",
        ".ommommdo.",
        "ommmmommdo",
        "ommmommmdo",
        "odmmmommdo",
        ".odmommdo.",
        ".oddddddo.",
        "..oooooo..",
    ),
    // hour 1 — sprig
    listOf(
        ".b.oooo.b.",
        "baollmmoab",
        "ablwllmdba",
        "omllllmmdo",
        "omeklmkedo",
        ".ommyymdo.",
        ".oddddddo.",
        "..oooooo..",
    ),
    // hour 2 — seedling
    listOf(
        "....a..a....",
        ".b.oboobo.b.",
        "baomlmmmdoab",
        "abmlwlmmmdba",
        ".bmllllmmdb.",
        ".omlllmmmdo.",
        ".omekmmkedo.",
        ".ommmyymmdo.",
        "..oddddddo..",
        "...oooooo...",
    ),
    // hour 3 — shootling
    listOf(
        "....a..a....",
        ".b..boob..b.",
        "ba.ommmmo.ab",
        "abolllmmdoba",
        ".bmlwllmmdb.",
        ".omeklmkedo.",
        ".ommlyymmdo.",
        ".ommmmmmmdo.",
        "..odmmmmdo..",
        ".odmmmmmmdo.",
        ".mdmmmmmmdm.",
        ".odmmmmmmdo.",
        ".odmmmmmmdo.",
        "..oddddddo..",
        "...oooooo...",
    ),
    // hour 4 — leafling
    listOf(
        ".......a..a.......",
        "....b.oboobo.b....",
        "...baommmmmmoab...",
        "...abmlllmmmdba...",
        "...ommwllmmmmdo...",
        "...ommeklmkemdo...",
        "...ommekmmkemdo...",
        "...ommmmyymmmdo...",
        "....ommmmmmmdo....",
        "....odmmmmmmdo....",
        "...odmmppppmmdo...",
        "...mdmppppppmdm...",
        "...omdppppppdmo.ba",
        "....odmppppmdo.ba.",
        ".....oddppddo.bb..",
        "......oooooo......",
    ),
    // hour 5 — crownling
    listOf(
        ".......a....a.......",
        "......a.a..a.a......",
        ".......abaaba.......",
        "....b..obmmbo..b....",
        "...ba.ommmmmdo.ab...",
        "...abomwllmmmdoba...",
        "....bmllllmmmmdb....",
        "....omekllmmkedo....",
        "....omekmmmmkedo....",
        "....ommmmyymmmdo....",
        ".....odmyyyymdo.....",
        "....odmmmmmmmmdo....",
        "...omdmmppppmmdmo...",
        "...mmdmppppppmdmm...",
        "...omdmppppppmdmo.ba",
        "....odmppppppmdo.ba.",
        ".....odmppppmdo.bb..",
        "......oddddddo......",
        ".......oooooo.......",
    ),
    // hour 6 — collared
    listOf(
        ".......a....a.......",
        "......a.a..a.a......",
        ".......abaaba.......",
        "....b.oommmmoo.b....",
        "...baomllmmmddoab...",
        "...abmlwllmmmmdba...",
        "....bmllllmmmmdb....",
        "....omeklmmmkedo....",
        "....ocekmmmmkeco....",
        "....ommmmyymmmdo....",
        ".....odmyyyymdo.....",
        "....odmmmppmmmdo....",
        "...omdmppppppmdmo...",
        "...mdmmppppppmmdm...",
        "...omdmppppppmdmo.ba",
        "....odmppppppmdo.ba.",
        ".....odmppppmdo.bb..",
        "......oddddddo......",
        ".......oooooo.......",
    ),
    // hour 7 — budded
    listOf(
        "..........aa..........",
        "........aabbaa........",
        ".......a.abba.a.......",
        "........abaaba........",
        ".....b.oommmmoo.b.....",
        "....baommlmmmddoab....",
        "....abmlwllmmmmdba....",
        ".....bmllllmmmmdb.....",
        ".....omekllmmkedo.....",
        ".....ocekmmmmkeco.....",
        ".....ommmmyymmmdo.....",
        ".....ommmyyyymmdo.....",
        "....ommmppppppmmdo....",
        "...omdmmppppppmmdmo...",
        "...mmdmppppppppmdmm...",
        "...omdmppppppppmdmo.ba",
        "....odmmppppppmmdo.ba.",
        ".....odmmppppmmdo.bb..",
        "......oddddddddo......",
        ".......oooooooo.......",
    ),
    // hour 8 — in bloom
    listOf(
        ".........aaaa.........",
        "........aawwaa........",
        ".......a.abba.a.......",
        ".....b..abaaba..b.....",
        "....ba.ommmmmmo.ab....",
        "....abomllmmmmdoba....",
        ".....bmlwllmmmmdb.....",
        ".....omllllmmmmdo.....",
        "....ommeklmmmkemdo....",
        "....omcekmmmmkecdo....",
        ".....ommmmyymmmdo.....",
        "....ommmmyyyymmmdo....",
        "...ommmmppppppmmdmo...",
        "...mmmmppppppppmmdm...",
        "...odmmppppppppmmdo.ba",
        "....odmppppppppmdo.ba.",
        "....odmmppppppmmdobb..",
        ".....odmmppppmmdo.....",
        "......oddddddddo......",
        ".......oooooooo.......",
    ),
)

private val EmberForms = listOf(
    // hour 0 — the shell
    listOf(
        "....aa....",
        "...aaaa...",
        "....bb....",
        "....oo....",
        "...ommo...",
        "..ollmmo..",
        "..owlmdo..",
        ".olllmmdo.",
        ".ommommdo.",
        "ommmmommdo",
        "ommmommmdo",
        "odmmmommdo",
        ".odmommdo.",
        ".oddddddo.",
        "..oooooo..",
    ),
    // hour 1 — spark
    listOf(
        "...aa...",
        "..aaaa..",
        ".aoaaoa.",
        ".ollmmo.",
        ".owlldo.",
        "ollllmdo",
        "oeklmkeo",
        "ommyymdo",
        "oddddddo",
        ".oooooo.",
    ),
    // hour 2 — hornlet
    listOf(
        "....aa....",
        "...aaaa...",
        "..a.aa.a..",
        "...obbo...",
        ".aolmmmoa.",
        ".blwlmmdb.",
        ".ollllmdo.",
        "omlllmmmdo",
        "omekmmkedo",
        "ommmyymmdo",
        ".oddddddo.",
        "..oooooo..",
    ),
    // hour 3 — kindling
    listOf(
        "....aa....",
        "...aaaa...",
        "..a.aa.a..",
        "...ommo...",
        ".aollmmoa.",
        ".blwllmdb.",
        ".oeklmkeo.",
        "ommlyymmdo",
        "ommmmmmmdo",
        ".odmmmmdo.",
        "omdmmmmdmo",
        "mmdmmmmdmm",
        "omdmmmmdmo",
        ".odmmmmdo.",
        ".oddddddo.",
        "..oooooo..",
    ),
    // hour 4 — flarelet
    listOf(
        ".......aa.......",
        "......aaaa......",
        ".....aoaaoa.....",
        ".....ommmmo.....",
        "....alllmmda....",
        "....bwllmmdb....",
        "...omeklmkedo...",
        "...omekmmkedo...",
        "...ommmyymmdo...",
        "...ommmmmmmdo...",
        "....odmmmmdo....",
        "..omodppppdomo.a",
        "..mmdppppppdmmaa",
        "..omdppppppdma..",
        "...odmppppmdo.b.",
        "....oddppddo....",
        ".....oooooo.....",
    ),
    // hour 5 — maned
    listOf(
        "........aa........",
        ".......aaaa.......",
        "......a.aa.a......",
        ".......ommo.......",
        ".....aommmmoa.....",
        ".....bwllmmdb.....",
        ".....olllmmdo.....",
        "....oekllmmkeo....",
        "....oekmmmmkeo....",
        "....ommmyymmdo....",
        "....odmyyyymdo....",
        ".....odmmmmdo.....",
        "..omodmppppmdomo.a",
        "..mmodppppppdommaa",
        "..omodppppppdoma..",
        "....odppppppdo..b.",
        "....odmppppmdo....",
        ".....oddddddo.....",
        "......oooooo......",
    ),
    // hour 6 — spiked
    listOf(
        "........aa........",
        ".......aaaa.......",
        "......a.aa.a......",
        "......oommoo......",
        ".....allmmmda.....",
        ".....bwllmmdb.....",
        "....ollllmmmdo....",
        "....oeklmmmkeo....",
        "....oekmmmmkeo....",
        "....ommmyymmdo....",
        "....odmyyyymdo....",
        ".a..odmmppmmdo..a.",
        ".aamodppppppdomaaa",
        "..mmdmppppppmdmmaa",
        "..omdmppppppmdma..",
        "...odmppppppmdo.b.",
        "....odmppppmdo....",
        ".....oddddddo.....",
        "......oooooo......",
    ),
    // hour 7 — greathorn
    listOf(
        ".........aa.........",
        "........aaaa........",
        ".....a.a.aa.a.a.....",
        ".....a..ommo..a.....",
        ".....aaolmmmoaa.....",
        "......bwllmmdb......",
        ".....ollllmmmdo.....",
        ".....oekllmmkeo.....",
        "....ocekmmmmkeco....",
        ".....ommmyymmdo.....",
        ".a...odmyyyymdo...a.",
        ".aa..odppppppdo..aa.",
        "..bmodmppppppmdomb.a",
        "..mmodppppppppdommaa",
        "..omodppppppppdoma..",
        "....odmppppppmdo..b.",
        "....odmmppppmmdo....",
        ".....oddddddddo.....",
        "......oooooooo......",
    ),
    // hour 8 — crowned
    listOf(
        ".......a.aa.a.......",
        "......a.aaaa.a......",
        ".......baaaab.......",
        ".....a.aoaaoa.a.....",
        ".....a.ommmmo.a.....",
        ".....aallmmmdaa.....",
        ".....olwllmmmdo.....",
        ".....ollllmmmdo.....",
        "....omeklmmmkedo....",
        "....ocekmmmmkeco....",
        "....ommmmyymmmdo....",
        ".a...odmyyyymdo...a.",
        ".aamodmppppppmdomaaa",
        "..mmodppppppppdommaa",
        "..omodppppppppdoma..",
        "...odmppppppppmdo.b.",
        "....odmppppppmdo....",
        "....odmmppppmmdo....",
        ".....oddddddddo.....",
        "......oooooooo......",
    ),
)

private val DropletForms = listOf(
    // hour 0 — the shell
    listOf(
        "....aa....",
        "...awwa...",
        "....aa....",
        "....oo....",
        "...ommo...",
        "..ollmmo..",
        "..owlmdo..",
        ".olllmmdo.",
        ".ommommdo.",
        "ommmmommdo",
        "ommmommmdo",
        "odmmmommdo",
        ".odmommdo.",
        ".oddddddo.",
        "..oooooo..",
    ),
    // hour 1 — bead
    listOf(
        "....oooo....",
        "...ollmmo...",
        "..olwllmdo..",
        ".bmllllmmdb.",
        "abmeklmkedba",
        "aabmmyymdbaa",
        ".boddddddob.",
        "...oooooo...",
    ),
    // hour 2 — finling
    listOf(
        ".....oooooo.....",
        "....omlmmmdo....",
        "...omlwlmmmdo...",
        "...bmllllmmdb..a",
        "..abmlllmmmdbaaa",
        "..aamekmmkedaaab",
        "...bmmmyymmdb.ab",
        "....oddddddo....",
        ".....oooooo.....",
    ),
    // hour 3 — crested
    listOf(
        ".......aa.......",
        "......aaaa......",
        ".....bommob.....",
        ".....ollmmo.....",
        "....olwllmdo....",
        "...boeklmkeob...",
        "..abomlyymdoba..",
        "..aammmmmmmdaa..",
        "...bodmmmmdob...",
        ".....odmmdo....a",
        "....odmmmmdo..aa",
        "....odmmmmdo.aab",
        "....odmmmmdo..ab",
        "....oddddddo....",
        ".....oooooo.....",
    ),
    // hour 4 — paddler
    listOf(
        ".......aa.......",
        "......aaaa......",
        ".....bommob.....",
        ".....ollmmo.....",
        "....owllmmdo....",
        "..b.oeklmkeo.b..",
        ".abomekmmkedoba.",
        ".aabmmmyymmdbaa.",
        "..bommmmmmmdob..",
        "....odmmmmdo....",
        "..omodppppdomo.a",
        "..mmdppppppdmmaa",
        "..omdppppppdmaab",
        "...odmppppmdo.ab",
        "....oddppddo....",
        ".....oooooo.....",
    ),
    // hour 5 — winged
    listOf(
        "........aa........",
        ".......aaaa.......",
        "......bobbob......",
        "......ommmmo......",
        "......ollmdo......",
        ".....olllmmdo.....",
        "...boekllmmkeob...",
        "..aboekmmmmkeoba..",
        "..aabmmmyymmdbaa..",
        "...bodmyyyymdob...",
        "a....odmmmmdo....a",
        "aaom.odppppdo.moaa",
        ".ammodppppppdommaa",
        "..omodppppppdomaab",
        "....odppppppdo..ab",
        "....odmppppmdo....",
        ".....oddddddo.....",
        "......oooooo......",
    ),
    // hour 6 — four-fin
    listOf(
        "........aa........",
        ".......aaaa.......",
        "......bommob......",
        "......olmmmo......",
        ".....owllmmdo.....",
        ".....olllmmdo.....",
        "...boeklmmmkeob...",
        "..aboekmmmmkeoba..",
        "..aabmmmyymmdbaa..",
        "...bodmyyyymdob...",
        "a....odmppmdo....a",
        "aaomodppppppdomoaa",
        "aammodppppppdommaa",
        "aaomodppppppdomaab",
        ".ab.odppppppdo.bab",
        "....odmppppmdo....",
        ".....oddddddo.....",
        "......oooooo......",
    ),
    // hour 7 — pearled
    listOf(
        ".........aa.........",
        "........aaaa........",
        ".......bommob.......",
        ".......olbbmo.......",
        "......owbwwbdo......",
        "......ollbbmdo......",
        "....boekllmmkeob....",
        "...aboekmmmmkeoba...",
        "...aabmmmyymmdbaa...",
        "a...bodmyyyymdob...a",
        "aa...odppppppdo...aa",
        ".abm.odppppppdo.mbaa",
        "a.mmodppppppppdommaa",
        "aaomodppppppppdomaab",
        ".ab.odmppppppmdo.bab",
        "....odmmppppmmdo....",
        ".....oddddddddo.....",
        "......oooooooo......",
    ),
    // hour 8 — ringed
    listOf(
        "........a..a........",
        ".......a.ww.a.......",
        "........aaaa........",
        "........aaaa........",
        ".......bommob.......",
        ".......olbbmo.......",
        "......owbwwbdo......",
        ".....olllbbmmdo.....",
        "...b.oeklmmmkeo.b...",
        "..ab.oekmmmmkeo.ba..",
        "..aabommmyymmdobaa..",
        "a..b.odmyyyymdo.b..a",
        "aaom.odppppppdo.moaa",
        "aammodppppppppdommaa",
        "aaomodppppppppdomaab",
        ".ab.odppppppppdo.bab",
        "....odmppppppmdo....",
        "....odmmppppmmdo....",
        ".....oddddddddo.....",
        "......oooooooo......",
    ),
)

private val SparkForms = listOf(
    // hour 0 — the shell
    listOf(
        "...a..a...",
        "....aa....",
        "...a..a...",
        "....oo....",
        "...ommo...",
        "..ollmmo..",
        "..owlmdo..",
        ".olllmmdo.",
        ".ommommdo.",
        "ommmmommdo",
        "ommmommmdo",
        "odmmmommdo",
        ".odmommdo.",
        ".oddddddo.",
        "..oooooo..",
    ),
    // hour 1 — mote
    listOf(
        "....aa....",
        ".ooobbooo.",
        "ommllmmddo",
        "omlwllmmdo",
        "omllllmmdo",
        "omeklmkedo",
        "ommmyymmdo",
        "ommmmmmmdo",
        ".oddddddo.",
        "..oooooo..",
    ),
    // hour 2 — twin-tail
    listOf(
        "..aoaaoa..",
        ".ommmmmmo.",
        "ommlmmmmdo",
        "omlwlmmmdo",
        "omllllmmdo",
        "omlllmmmdo",
        "omekmmkedo",
        "ommmyymmdo",
        "odmmmmmmdo",
        ".oddddddo.",
        "..oooooo..",
    ),
    // hour 3 — bolt-tail
    listOf(
        ".....a.aa.a.....",
        ".....obbbbo.....",
        "....ommmmmmo....",
        "...omlllmmmdo...",
        "...omlwllmmdo...",
        "...omeklmkedo...",
        "...ommlyymmdo...",
        "...ommmmmmmdo...",
        "...ommmmmmmdo...",
        "...odmmmmmmdo..a",
        "...odmmmmmmdo.a.",
        "...odmmmmmmdoaa.",
        "...odmmmmmmdo.a.",
        "...oddmmmmddo...",
        "....oddddddo....",
        ".....oooooo.....",
    ),
    // hour 4 — eared
    listOf(
        "..a..a.aa.a..a..",
        "..aaoobbbbooaa..",
        "..ommmmmmmmddo..",
        "..ammlllmmmmda..",
        "..ommwllmmmmdo..",
        "..ommeklmkemdo..",
        "..ommekmmkemdo..",
        "..ommmmyymmmdo..",
        "..ommmmmmmmmdo..",
        "..ommmmmmmmmdo..",
        "..odmmppppmmdo.a",
        "..odmppppppmdoa.",
        "..odmppppppmdaa.",
        "..odmmppppmmdoa.",
        "..odmdmppmdmdo..",
        "...oddddddddo...",
        "....oooooooo....",
    ),
    // hour 5 — handed
    listOf(
        "......a.aa.a......",
        "..a..oobbbboo..a..",
        "..aaommmmmmmdoaa..",
        "...ammmmmmmmmda...",
        "..abmmwllmmmmdba..",
        "...omllllmmmmdo...",
        "...omekllmmkedo...",
        "...omekmmmmkedo...",
        "...ommmmyymmmdo...",
        "...ommmyyyymmdo...",
        "...odmmmmmmmmdo...",
        "..omdmmppppmmdmo.a",
        "..mmdmppppppmdmma.",
        "..omdmppppppmdmaa.",
        "...odmppppppmdo.a.",
        "...odmmppppmmdo...",
        "...oddddddddddo...",
        "....oooooooooo....",
    ),
    // hour 6 — static
    listOf(
        "......a....a......",
        "..a...abaaba...a..",
        "..aa.oobbbboo.aa..",
        "...aommmmmmmdoa...",
        "..abmmllmmmmmdba..",
        "...omlwllmmmmdo...",
        "...omllllmmmmdo...",
        "...omeklmmmkedo...",
        "...ocekmmmmkeco...",
        "...ommmmyymmmdo...",
        "..ommmmyyyymmmdo..",
        "..ommmmmppmmmmdo..",
        "..odmmppppppmmdo.a",
        "..mdmmppppppmmdma.",
        "..odmmppppppmmdaa.",
        "..odmmppppppmmdoa.",
        "..odmmmppppmmmdo..",
        "..odmmdmmmmdmmdo..",
        "...oddddddddddo...",
        "....oooooooooo....",
    ),
    // hour 7 — crowned
    listOf(
        ".......a.aa.a.......",
        ".......ab..ba.......",
        ".......abaaba.......",
        "...a..oobbbboo..a...",
        "...aaommmmmmmdoaa...",
        "....ammmlmmmmmda....",
        "...abmlwllmmmmdba...",
        "....omllllmmmmdo....",
        "....omekllmmkedo....",
        "....ocekmmmmkeco....",
        "...ommmmmyymmmmdo...",
        "...ommmmyyyymmmdo...",
        "...ommmppppppmmdo...",
        "..omdmmppppppmmdmo.a",
        "..mmdmppppppppmdmma.",
        "..omdmppppppppmdmaa.",
        "...odmmppppppmmdo.a.",
        "...odmmmppppmmmdo...",
        "...odmmdmmmmdmmdo...",
        "....oddddddddddo....",
        ".....oooooooooo.....",
    ),
    // hour 8 — arced
    listOf(
        ".......a.aa.a.......",
        "......aab..baa......",
        "...a...abaaba...a...",
        "...aaooobbbboooaa...",
        "....ammmmmmmmdda....",
        "...ammmllmmmmmmda...",
        "...ommlwllmmmmmdo...",
        "...ommllllmmmmmdo...",
        "...ommeklmmmkemdo...",
        "..ommcekmmmmkecmdo..",
        "..ommmmmmyymmmmmdo..",
        "..ommmmmyyyymmmmdo..",
        "..ommmmppppppmmmdo.a",
        "..mmmmppppppppmmdma.",
        "..odmmppppppppmmdaa.",
        "..odmmppppppppmmdoa.",
        "..odmmmppppppmmmdo..",
        "..odmmmmppppmmmmdo..",
        "..odmmmdmmmmdmmmdo..",
        "...oddddddddddddo...",
        "....oooooooooooo....",
    ),
)

/**
 * The four species, in the order the pack sells them.
 *
 * The ramps are hand-mixed rather than derived from the theme: these are characters, and
 * a creature whose green shifted with the app's accent would be a different creature in
 * every theme. Each ramp is the same five steps of one hue so the four read as siblings,
 * and the flourish is the one place they diverge — leaf-lime, ember-gold, sea-foam and
 * lightning-white.
 */
internal val FernCreature = EvolutionSpecies(
    ramp = EvolutionRamp(
        outline = Color(0xff183822),
        shade = Color(0xff2d693a),
        base = Color(0xff4fa85c),
        light = Color(0xff7dcd78),
        gleam = Color(0xffd8f6c6),
        belly = Color(0xffdcf0b4),
        accent = Color(0xffa7e04e),
        accentShade = Color(0xff4a8422),
        blush = Color(0xfff29daa),
    ),
    forms = FernForms,
    mote = EvolutionMote.SPORE,
)

internal val EmberCreature = EvolutionSpecies(
    ramp = EvolutionRamp(
        outline = Color(0xff471317),
        shade = Color(0xff9c2a23),
        base = Color(0xffdd4b33),
        light = Color(0xfff6864d),
        gleam = Color(0xffffdaaa),
        belly = Color(0xffffcfa6),
        accent = Color(0xffffbf45),
        accentShade = Color(0xffb04c0d),
        blush = Color(0xffffa28c),
    ),
    forms = EmberForms,
    mote = EvolutionMote.EMBER,
)

internal val DropletCreature = EvolutionSpecies(
    ramp = EvolutionRamp(
        outline = Color(0xff0f2c50),
        shade = Color(0xff1e5c9c),
        base = Color(0xff3696d8),
        light = Color(0xff78c9f1),
        gleam = Color(0xffe2f7ff),
        belly = Color(0xffcff1f4),
        accent = Color(0xff86f0dd),
        accentShade = Color(0xff1a7b84),
        blush = Color(0xffffb2c6),
    ),
    forms = DropletForms,
    mote = EvolutionMote.BUBBLE,
)

internal val SparkCreature = EvolutionSpecies(
    ramp = EvolutionRamp(
        outline = Color(0xff4a340a),
        shade = Color(0xffb68212),
        base = Color(0xfff3c32d),
        light = Color(0xffffe56d),
        gleam = Color(0xfffff9d2),
        belly = Color(0xfffff1bd),
        accent = Color(0xffffef86),
        accentShade = Color(0xff875b0a),
        blush = Color(0xffff9f9f),
    ),
    forms = SparkForms,
    mote = EvolutionMote.ARC,
)

/**
 * The creature [style] draws, or null for every face that is not one of the four.
 *
 * Null rather than a default: a face outside the pack has no creature, and quietly
 * handing back Fern would draw the wrong thing on someone else's clock.
 */
internal fun evolutionSpeciesOf(style: SupportedClockStyle): EvolutionSpecies? = when (style) {
    SupportedClockStyle.FERN -> FernCreature
    SupportedClockStyle.EMBER -> EmberCreature
    SupportedClockStyle.DROPLET -> DropletCreature
    SupportedClockStyle.SPARK -> SparkCreature
    else -> null
}
