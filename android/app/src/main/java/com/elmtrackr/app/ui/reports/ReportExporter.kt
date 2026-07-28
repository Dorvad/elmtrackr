package com.elmtrackr.app.ui.reports

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.MonthlyReportBuilder
import com.elmtrackr.app.domain.time.WorkTimezone
import com.elmtrackr.app.domain.model.RefundClaim
import com.elmtrackr.app.domain.MoneyFormatter
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.RefundDirection
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.TaskMonthlyBreakdown
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.language.withAppLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

data class RefundPdfRow(
    val shift: Shift,
    val claim: RefundClaim,
    val receipt: Bitmap? = null,
)

object ReportExporter {
    fun shareCsv(context: Context, content: String, filename: String) {
        // UTF-8 BOM: without it Excel decodes the file with the system ANSI
        // codepage and Hebrew notes/merchant names render as mojibake.
        val file = exportFile(context, filename).apply { writeText("\uFEFF" + content) }
        shareFile(context, file, "text/csv", context.withAppLocale().getString(R.string.export_share_csv))
    }

    fun shareShiftPdf(
        context: Context,
        state: ReportsUiState.Ready,
    ) {
        val res = context.withAppLocale()
        val locale = res.resources.configuration.locales[0]
        val filename = "elmtrackr-shifts-${state.year}-${state.month.toString().padStart(2, '0')}.pdf"
        val file = exportFile(context, filename)
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 21f; isFakeBoldText = true; color = AuroraPdfInk }
        val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = AuroraPdfMuted }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; isFakeBoldText = true; color = AuroraPdfInk }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = AuroraPdfInk }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = AuroraPdfMuted }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AuroraPdfHair; strokeWidth = 1f }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AuroraPdfSubtle }
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AuroraPdfIndigo; strokeWidth = 7f }
        val plum = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AuroraPdfPlum; strokeWidth = 7f }
        val peach = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AuroraPdfPeach; strokeWidth = 7f }
        val currency = state.paySummary?.currencyCode ?: state.settings?.displayCurrencyCode() ?: CurrencyCode.ILS.name
        val monthLabel = "${Month.of(state.month).getDisplayName(TextStyle.FULL, locale)} ${state.year}"
        // Work zone, not the device zone: the CSV, the Hours breakdown and the pay
        // engine all bucket shifts by the configured work timezone, so a PDF printed in
        // the device zone dated shifts differently from every other surface.
        val zone = state.settings?.let { WorkTimezone.zoneFor(it) } ?: ZoneId.systemDefault()
        val dateFormat = DateTimeFormatter.ofPattern("EEE, dd MMM", locale).withZone(zone)
        val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin

        fun newPage() {
            canvas.drawText(res.getString(R.string.export_generated_by), margin, pageHeight - margin, muted)
            document.finishPage(page)
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin
        }

        fun ensureSpace(required: Float) {
            if (y + required > pageHeight - margin - 18f) newPage()
        }

        canvas.drawText(res.getString(R.string.export_monthly_title), margin, y, title)
        canvas.drawText(monthLabel, pageWidth - margin, y, heading.apply { textAlign = Paint.Align.RIGHT })
        heading.textAlign = Paint.Align.LEFT
        y += 18f
        canvas.drawText(res.getString(R.string.export_completed_shifts, state.report.shiftCount), margin, y, subtitle)
        y += 22f

        canvas.drawRoundRect(margin, y, pageWidth - margin, y + 84f, 18f, 18f, fill)
        y += 20f
        canvas.drawText(res.getString(R.string.reports_hours_distribution), margin + 16f, y, heading)
        canvas.drawText(ShiftDurationCalculator.formatMinutes(state.report.totalMinutes), pageWidth - margin - 16f, y, heading.apply { textAlign = Paint.Align.RIGHT })
        heading.textAlign = Paint.Align.LEFT
        y += 18f
        drawMetric(canvas, res.getString(R.string.dashboard_stat_regular), ShiftDurationCalculator.formatMinutes(state.report.regularMinutes), margin + 16f, y, body, muted)
        drawMetric(canvas, res.getString(R.string.dashboard_stat_overtime), ShiftDurationCalculator.formatMinutes(state.report.overtimeMinutes), margin + 155f, y, body, muted)
        drawMetric(canvas, res.getString(R.string.dashboard_stat_weekend), ShiftDurationCalculator.formatMinutes(state.report.weekendMinutes), margin + 294f, y, body, muted)
        state.paySummary?.let { pay ->
            drawMetric(canvas, res.getString(R.string.export_gross_pay), MoneyFormatter.format(pay.totalGross, currency), pageWidth - margin - 116f, y, body, muted)
        }
        y += 28f
        drawDistributionBar(canvas, margin + 16f, y, pageWidth - margin * 2 - 32f, state.report.totalMinutes, state.report.regularMinutes, state.report.overtimeMinutes, state.report.weekendMinutes, accent, peach, plum)
        y += 38f

        state.paySummary?.takeIf { it.totalGross > 0.0 }?.let { pay ->
            ensureSpace(70f)
            canvas.drawText(res.getString(R.string.reports_gross_pay_before_tax), margin, y, heading)
            y += 17f
            drawMetric(canvas, res.getString(R.string.dashboard_stat_regular), MoneyFormatter.format(pay.regularGross, currency), margin, y, body, muted)
            drawMetric(canvas, res.getString(R.string.dashboard_stat_overtime), MoneyFormatter.format(pay.overtimeGross, currency), margin + 150f, y, body, muted)
            drawMetric(canvas, res.getString(R.string.dashboard_pay_holiday), MoneyFormatter.format(pay.specialGross, currency), margin + 300f, y, body, muted)
            drawMetric(canvas, res.getString(R.string.export_col_total), MoneyFormatter.format(pay.totalGross, currency), pageWidth - margin - 95f, y, body, muted)
            y += 30f
        }

        if (state.weeklyTotals.isNotEmpty()) {
            ensureSpace(70f)
            canvas.drawText(res.getString(R.string.reports_weekly_breakdown), margin, y, heading)
            y += 14f
            state.weeklyTotals.forEach { week ->
                ensureSpace(18f)
                canvas.drawLine(margin, y, pageWidth - margin, y, line)
                y += 12f
                canvas.drawText("${res.getString(R.string.reports_week_label, week.label.filter { it.isDigit() })} (${res.getString(R.string.reports_week_days, week.dayRange)})", margin, y, body)
                canvas.drawText(
                    ShiftDurationCalculator.formatMinutes(week.totalMinutes),
                    pageWidth - margin,
                    y,
                    body.apply { textAlign = Paint.Align.RIGHT },
                )
                body.textAlign = Paint.Align.LEFT
                y += 12f
            }
            y += 10f
        }

        state.settings?.let { settings ->
            ensureSpace(42f)
            canvas.drawText(res.getString(R.string.export_thresholds), margin, y, heading)
            y += 14f
            canvas.drawText(
                "${res.getString(R.string.reports_daily_ot_after)} ${ShiftDurationCalculator.formatMinutes(settings.dailyOvertimeThresholdMinutes)}   ${res.getString(R.string.reports_weekly_ot_after)} ${ShiftDurationCalculator.formatMinutes(settings.weeklyOvertimeThresholdMinutes)}",
                margin,
                y,
                muted,
            )
            y += 24f
        }

        ensureSpace(56f)
        canvas.drawText(res.getString(R.string.reports_shift_breakdown), margin, y, heading)
        y += 16f
        drawShiftHeader(canvas, margin, y, heading, fill, res)
        y += 18f

        state.rawShifts.sortedByDescending { it.startTime }.forEach { shift ->
            ensureSpace(38f)
            val breakdown = state.settings?.let { MonthlyReportBuilder.buildShiftBreakdown(shift, it) }
            // Gate on either rate source, matching ReportsViewModel and
            // WeeklyBreakdownBuilder. Checking only the legacy settings rate printed "-"
            // in every Gross cell for users who configure rates on compensation profiles,
            // even though the same PDF's header showed a monthly gross.
            val pay = state.settings?.let { settings ->
                val hasRate = (settings.hourlyRate ?: 0.0) > 0.0 ||
                    state.profiles.any { (it.baseHourlyRate ?: 0.0) > 0.0 }
                if (!hasRate) null else {
                    com.elmtrackr.app.domain.PayrollCalculator.calculateShiftPayInContext(
                        shift, state.rawShifts, settings, state.profiles, state.premiumProfiles,
                    )
                }
            }
            val timeText = buildString {
                append(timeFormat.format(shift.startTime))
                append(" - ")
                append(shift.endTime?.let(timeFormat::format) ?: res.getString(R.string.shifts_active))
                if (shift.breakMinutes > 0) append("  ${res.getString(R.string.shifts_break_line, shift.breakMinutes)}")
            }
            val tags = buildList {
                if (shift.isSpecialDay) add(res.getString(R.string.dashboard_pay_holiday))
                if ((breakdown?.weekendMinutes ?: 0) > 0 && !shift.isSpecialDay) add(res.getString(R.string.dashboard_stat_weekend))
                if (com.elmtrackr.app.domain.OvernightShiftDetector.isOvernight(shift, zone)) add(res.getString(R.string.reports_chip_overnight))
            }.joinToString(" / ")
            canvas.drawText(dateFormat.format(shift.startTime), margin, y, body)
            canvas.drawText(timeText, margin + 94f, y, body)
            canvas.drawText(breakdown?.totalMinutes?.let(ShiftDurationCalculator::formatMinutes) ?: "-", margin + 230f, y, body)
            canvas.drawText(tags.ifBlank { "-" }, margin + 300f, y, muted)
            canvas.drawText(
                pay?.let { MoneyFormatter.format(it.totalGross, currency) } ?: "-",
                pageWidth - margin,
                y,
                body.apply { textAlign = Paint.Align.RIGHT },
            )
            body.textAlign = Paint.Align.LEFT
            y += 9f
            shift.notes?.takeIf { it.isNotBlank() }?.let {
                canvas.drawText(res.getString(R.string.export_note, it.take(92)), margin + 94f, y, muted)
                y += 9f
            }
            canvas.drawLine(margin, y, pageWidth - margin, y, line)
            y += 13f
        }

        if (state.taskBreakdown.isNotEmpty()) {
            ensureSpace(56f)
            y += 10f
            canvas.drawText(res.getString(R.string.reports_by_task), margin, y, heading)
            y += 16f
            drawTaskHeader(canvas, margin, y, heading, fill, res)
            y += 18f

            state.taskBreakdown.forEach { task ->
                ensureSpace(32f)
                drawTaskRow(
                    canvas = canvas,
                    task = task,
                    currency = currency,
                    x = margin,
                    y = y,
                    pageWidth = pageWidth,
                    margin = margin,
                    body = body,
                    muted = muted,
                    res = res,
                )
                canvas.drawLine(margin, y + 20f, pageWidth - margin, y + 20f, line)
                y += 33f
            }
        }

        canvas.drawText(res.getString(R.string.export_generated_by), margin, pageHeight - margin, muted)
        document.finishPage(page)
        file.outputStream().use(document::writeTo)
        document.close()
        shareFile(context, file, "application/pdf", res.getString(R.string.export_share_pdf))
    }

    suspend fun loadReceipt(url: String): Bitmap? = withContext(Dispatchers.IO) {
        // Bounded timeouts: a stalled signed-URL endpoint must not hang the
        // refund-PDF export coroutine indefinitely with no user feedback.
        runCatching {
            val connection = URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            try {
                connection.inputStream.use(BitmapFactory::decodeStream)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    fun shareRefundPdf(
        context: Context,
        rows: List<RefundPdfRow>,
        year: Int,
        month: Int,
        currency: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        shareRefundPdf(
            context = context,
            rows = rows,
            filenameSuffix = "$year-${month.toString().padStart(2, '0')}",
            periodLabel = "$year-${month.toString().padStart(2, '0')}",
            currency = currency,
            zone = zone,
        )
    }

    fun shareRefundPdf(
        context: Context,
        rows: List<RefundPdfRow>,
        filenameSuffix: String,
        periodLabel: String,
        currency: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val res = context.withAppLocale()
        val locale = res.resources.configuration.locales[0]
        val filename = "elmtrackr-rides-$filenameSuffix.pdf"
        val file = exportFile(context, filename)
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; isFakeBoldText = true }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; isFakeBoldText = true }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = 0xff777785.toInt() }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffddddE5.toInt(); strokeWidth = 1f }
        val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", locale).withZone(zone)

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin
        canvas.drawText(res.getString(R.string.export_reimb_title), margin, y, title)
        y += 24f
        canvas.drawText(periodLabel, margin, y, body)
        val total = rows.sumOf { it.claim.amount }
        canvas.drawText(res.getString(R.string.export_claims_count, rows.size), margin, y + 20f, heading)
        canvas.drawText(res.getString(R.string.export_total, MoneyFormatter.format(total, currency)), pageWidth - margin - 130f, y + 20f, heading)
        y += 50f
        canvas.drawText(res.getString(R.string.export_col_direction), margin, y, heading)
        canvas.drawText(res.getString(R.string.export_col_ride_date), 105f, y, heading)
        canvas.drawText(res.getString(R.string.export_col_provider), 280f, y, heading)
        canvas.drawText(res.getString(R.string.export_col_amount), 490f, y, heading)
        y += 8f
        canvas.drawLine(margin, y, pageWidth - margin, y, line)
        y += 16f

        rows.forEach { row ->
            if (y > pageHeight - 70f) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = margin
            }
            val direction = res.getString(if (row.claim.direction == RefundDirection.TO_WORK) R.string.reports_dir_to_work else R.string.reports_dir_from_work)
            canvas.drawText(direction, margin, y, body)
            canvas.drawText(dateFormat.format(row.claim.rideAt), 105f, y, body)
            canvas.drawText(row.claim.provider.name.lowercase().replaceFirstChar(Char::uppercase), 280f, y, body)
            canvas.drawText(MoneyFormatter.format(row.claim.amount, currency), 475f, y, body)
            y += 14f
            val detail = buildString {
                append(res.getString(R.string.export_shift_prefix, dateFormat.format(row.shift.startTime)))
                row.claim.notes?.let { append("  ").append(res.getString(R.string.export_note, it.take(70))) }
                if (row.claim.receiptPath != null) append("  ").append(res.getString(R.string.refunds_receipt_attached))
            }
            canvas.drawText(detail, 105f, y, muted)
            y += 12f
            canvas.drawLine(margin, y, pageWidth - margin, y, line)
            y += 16f
        }
        canvas.drawText(res.getString(R.string.export_generated_by), margin, pageHeight - margin, muted)
        document.finishPage(page)

        rows.filter { it.receipt != null }.forEachIndexed { index, row ->
            pageNumber++
            val receiptPage = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            val receiptCanvas = receiptPage.canvas
            receiptCanvas.drawText(
                res.getString(R.string.export_receipt_n, (index + 1).toString(), row.claim.provider.name, MoneyFormatter.format(row.claim.amount, currency)),
                margin,
                margin,
                heading,
            )
            val bitmap = requireNotNull(row.receipt)
            val maxWidth = (pageWidth - margin * 2).toInt()
            val maxHeight = (pageHeight - margin * 2 - 24).toInt()
            val scale = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height, 1f)
            val width = (bitmap.width * scale).toInt()
            val height = (bitmap.height * scale).toInt()
            val left = ((pageWidth - width) / 2f)
            val top = margin + 24f
            receiptCanvas.drawBitmap(bitmap, null, android.graphics.RectF(left, top, left + width, top + height), null)
            document.finishPage(receiptPage)
        }

        file.outputStream().use(document::writeTo)
        document.close()
        shareFile(context, file, "application/pdf", res.getString(R.string.export_share_reimb))
    }

    private fun exportFile(context: Context, filename: String): File =
        File(context.cacheDir, "exports").apply { mkdirs() }.resolve(filename)

    private fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    private fun drawMetric(
        canvas: android.graphics.Canvas,
        label: String,
        value: String,
        x: Float,
        y: Float,
        body: Paint,
        muted: Paint,
    ) {
        canvas.drawText(label.uppercase(), x, y, muted)
        canvas.drawText(value, x, y + 13f, body.apply { isFakeBoldText = true })
        body.isFakeBoldText = false
    }

    private fun drawDistributionBar(
        canvas: android.graphics.Canvas,
        x: Float,
        y: Float,
        width: Float,
        total: Int,
        regular: Int,
        overtime: Int,
        weekend: Int,
        regularPaint: Paint,
        overtimePaint: Paint,
        weekendPaint: Paint,
    ) {
        val safeTotal = total.coerceAtLeast(1).toFloat()
        var cursor = x
        listOf(regular to regularPaint, overtime to overtimePaint, weekend to weekendPaint).forEach { (minutes, paint) ->
            if (minutes <= 0) return@forEach
            val segmentWidth = width * (minutes / safeTotal)
            canvas.drawLine(cursor, y, cursor + segmentWidth, y, paint)
            cursor += segmentWidth
        }
    }

    private fun drawShiftHeader(
        canvas: android.graphics.Canvas,
        x: Float,
        y: Float,
        heading: Paint,
        fill: Paint,
        res: Context,
    ) {
        canvas.drawRoundRect(x, y - 12f, x + 515f, y + 6f, 8f, 8f, fill)
        canvas.drawText(res.getString(R.string.export_col_date), x + 4f, y, heading)
        canvas.drawText(res.getString(R.string.export_col_time), x + 94f, y, heading)
        canvas.drawText(res.getString(R.string.export_col_total), x + 230f, y, heading)
        canvas.drawText(res.getString(R.string.export_col_tags), x + 300f, y, heading)
        canvas.drawText(res.getString(R.string.export_col_gross), x + 515f, y, heading.apply { textAlign = Paint.Align.RIGHT })
        heading.textAlign = Paint.Align.LEFT
    }

    private fun drawTaskHeader(
        canvas: android.graphics.Canvas,
        x: Float,
        y: Float,
        heading: Paint,
        fill: Paint,
        res: Context,
    ) {
        canvas.drawRoundRect(x, y - 12f, x + 515f, y + 6f, 8f, 8f, fill)
        canvas.drawText(res.getString(R.string.export_col_task), x + 4f, y, heading)
        canvas.drawText(res.getString(R.string.export_col_shifts), x + 210f, y, heading)
        canvas.drawText(res.getString(R.string.export_col_total), x + 280f, y, heading)
        canvas.drawText(res.getString(R.string.export_col_ot), x + 350f, y, heading)
        canvas.drawText(res.getString(R.string.export_col_gross), x + 515f, y, heading.apply { textAlign = Paint.Align.RIGHT })
        heading.textAlign = Paint.Align.LEFT
    }

    private fun drawTaskRow(
        canvas: android.graphics.Canvas,
        task: TaskMonthlyBreakdown,
        currency: String,
        x: Float,
        y: Float,
        pageWidth: Int,
        margin: Float,
        body: Paint,
        muted: Paint,
        res: Context,
    ) {
        val taskLabel = buildString {
            task.icon?.takeIf { it.isNotBlank() }?.let { append(it).append(' ') }
            append(task.name)
        }.take(28)
        canvas.drawText(taskLabel, x, y, body.apply { isFakeBoldText = true })
        body.isFakeBoldText = false
        canvas.drawText("${task.shiftCount}", x + 210f, y, body)
        canvas.drawText(ShiftDurationCalculator.formatMinutes(task.totalMinutes), x + 280f, y, body)
        canvas.drawText(
            if (task.overtimeMinutes > 0) ShiftDurationCalculator.formatMinutes(task.overtimeMinutes) else "-",
            x + 350f,
            y,
            muted,
        )
        canvas.drawText(
            task.totalPay?.let { MoneyFormatter.format(it, currency) } ?: "-",
            pageWidth - margin,
            y,
            body.apply { textAlign = Paint.Align.RIGHT },
        )
        body.textAlign = Paint.Align.LEFT
        canvas.drawText(
            res.getString(R.string.export_avg, ShiftDurationCalculator.formatMinutes(task.averageShiftMinutes)),
            x + 210f,
            y + 10f,
            muted,
        )
    }

    private const val AuroraPdfInk = 0xff181530.toInt()
    private const val AuroraPdfMuted = 0xff615C8A.toInt()
    private const val AuroraPdfHair = 0x335B4DF2
    private const val AuroraPdfSubtle = 0xffF6F6FD.toInt()
    private const val AuroraPdfIndigo = 0xff5B4DF2.toInt()
    private const val AuroraPdfPlum = 0xff8B5CF6.toInt()
    private const val AuroraPdfPeach = 0xffFF9E7D.toInt()
}
