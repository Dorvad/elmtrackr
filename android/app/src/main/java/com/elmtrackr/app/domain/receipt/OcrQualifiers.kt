package com.elmtrackr.app.domain.receipt

import javax.inject.Qualifier

/** ML Kit's Latin-script recognizer — best for digits and Latin brand names. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LatinOcr

/** Tesseract's Hebrew recognizer — the only engine that can read סה"כ / לתשלום labels. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HebrewOcr
