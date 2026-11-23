package br.com.matchstats.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.withSave
import br.com.matchstats.R
import java.io.OutputStream
import java.text.Normalizer
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val reportLocale = Locale.forLanguageTag("pt-BR")
private val reportDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", reportLocale)

internal fun matchReportFilename(team: String, opponent: String, date: LocalDate): String {
    fun component(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^A-Za-z0-9]+"), "_")
        .trim('_').take(48).trimEnd('_').ifEmpty { "Time" }
    return "MatchStats_${component(team)}_vs_${component(opponent)}_$date.pdf"
}

internal fun MatchWithTeam.reportFilename(): String = matchReportFilename(
    team.name, match.opponent, Instant.ofEpochMilli(match.startsAt).atZone(ZoneId.systemDefault()).toLocalDate(),
)

internal val Match.hasFinalReport: Boolean
    get() = status == MatchStatus.FINISHED && teamScore != null && opponentScore != null

internal data class MatchReportRow(val jersey: String, val player: String, val values: List<String>, val total: Boolean = false)
internal data class MatchReportTable(val heading: String, val rows: List<MatchReportRow>)
internal data class MatchReportData(
    val team: String,
    val opponent: String,
    val score: String,
    val details: String,
    val location: String?,
    val columns: List<String>,
    val tables: List<MatchReportTable>,
)

internal fun prepareMatchReport(context: Context, score: BoxScore): MatchReportData {
    val fixture = score.fixture
    val match = fixture.match
    require(match.hasFinalReport)
    val columns = score.config.visible
    val missing = context.getString(R.string.box_missing)
    val integer = NumberFormat.getIntegerInstance(reportLocale)
    val decimal = NumberFormat.getNumberInstance(reportLocale).apply { maximumFractionDigits = 1 }

    fun values(players: List<BoxPlayer>) = columns.map { metric ->
        val cell = metric.cell(players) ?: return@map missing
        val rate = cell.percentage?.let { context.getString(R.string.box_percent_value, decimal.format(it)) }
        val pair = cell.attempts?.let { context.getString(R.string.box_pair_value, integer.format(cell.value), integer.format(it)) }
        when (metric.kind) {
            BoxMetricKind.TOTAL -> integer.format(cell.value)
            BoxMetricKind.PERCENT -> if (metric.builtin == BoxBuiltin.FIELD_PERCENT && pair != null && rate != null) {
                context.getString(R.string.box_pair_with_rate, integer.format(cell.value), integer.format(cell.attempts), rate)
            } else rate ?: missing
            BoxMetricKind.PAIR -> if (metric.builtin == BoxBuiltin.PASSES && rate != null) {
                context.getString(R.string.box_pair_with_rate, integer.format(cell.value), integer.format(cell.attempts), rate)
            } else pair ?: missing
        }
    }

    fun table(heading: String, rows: List<BoxPlayer>, total: Int) = MatchReportTable(
        heading,
        if (rows.isEmpty()) emptyList() else rows.map { player ->
            MatchReportRow(player.jersey?.toString() ?: missing,
                player.player.name.ifBlank { context.getString(R.string.match_report_opponent_player) },
                values(listOf(player)),
            )
        } + MatchReportRow("", context.getString(total), values(rows), total = true),
    )

    val sport = context.getString(if (fixture.team.sport == Sport.BASKETBALL) R.string.basketball else R.string.soccer)
    val date = Instant.ofEpochMilli(match.startsAt).atZone(ZoneId.systemDefault()).format(reportDateFormat)
    return MatchReportData(
        fixture.team.name, match.opponent,
        context.getString(R.string.development_versus, match.teamScore.toString(), match.opponentScore.toString()),
        context.getString(R.string.practice_scope, sport, date),
        match.location?.takeIf { it.isNotBlank() }?.let { context.getString(R.string.match_report_location, it) },
        columns.map { metric -> metric.custom?.name ?: context.getString(requireNotNull(metric.builtin).nameRes) },
        buildList {
            add(table(context.getString(R.string.match_report_team, fixture.team.name), score.players, R.string.box_team_total))
            if (score.opponents.isNotEmpty()) {
                add(table(context.getString(R.string.match_report_opponents, match.opponent),
                    score.opponents, R.string.match_report_opponent_totals))
            }
        },
    )
}

private const val PAGE_WIDTH = 842
private const val PAGE_HEIGHT = 595
private const val MARGIN = 32f
private const val TABLE_WIDTH = PAGE_WIDTH - MARGIN * 2
private const val TABLE_TOP = 233f
private const val TABLE_BOTTOM = 548f
private const val COLUMNS_PER_PAGE = 8
private val navy = 0xFF10243F.toInt()
private val green = 0xFF078A30.toInt()
private val softGreen = 0xFFDDF8E5.toInt()
private val gray = 0xFFF7F8FA.toInt()
private val border = 0xFFDDE3E9.toInt()
private val white = 0xFFFFFFFF.toInt()
private val muted = 0xFF515C6B.toInt()

private data class ReportCell(val text: StaticLayout, val width: Float)
private data class ReportLine(val cells: List<ReportCell>) {
    val height = maxOf(23f, cells.maxOf { it.text.height }.toFloat() + 9f)
}
private data class ReportPage(
    val title: String,
    val subset: String?,
    val header: ReportLine,
    val rows: List<Pair<ReportLine, Boolean>>,
)

internal fun writeMatchReport(context: Context, report: MatchReportData, output: OutputStream) {
    val pages = paginateMatchReport(context, report)
    val document = PdfDocument()
    try {
        pages.forEachIndexed { index, content ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create())
            try {
                drawReportPage(context, page.canvas, report, content, index + 1, pages.size)
            } finally {
                document.finishPage(page)
            }
        }
        document.writeTo(output)
    } finally {
        document.close()
    }
}

private fun paginateMatchReport(context: Context, report: MatchReportData): List<ReportPage> = buildList {
    val groups = report.columns.indices.toList().chunked(COLUMNS_PER_PAGE).ifEmpty { listOf(emptyList()) }
    report.tables.forEach { table ->
        groups.forEach { indices ->
            val columnWidth = (TABLE_WIDTH - 192f) / maxOf(1, indices.size)
            val widths = listOf(38f, if (indices.isEmpty()) TABLE_WIDTH - 38f else 154f) + List(indices.size) { columnWidth }
            val header = reportLine(listOf(context.getString(R.string.match_report_jersey),
                context.getString(R.string.box_player)) + indices.map(report.columns::get), widths, heading = true)
            val subset = when {
                indices.isEmpty() -> context.getString(R.string.match_report_no_columns)
                groups.size > 1 -> context.getString(R.string.match_report_columns, indices.first() + 1, indices.last() + 1, report.columns.size)
                else -> null
            }
            var top = TABLE_TOP + header.height
            var rows = mutableListOf<Pair<ReportLine, Boolean>>()
            table.rows.forEach { row ->
                val line = reportLine(listOf(row.jersey, row.player) + indices.map(row.values::get), widths, total = row.total)
                if (top + line.height > TABLE_BOTTOM && rows.isNotEmpty()) {
                    add(ReportPage(table.heading, subset, header, rows))
                    rows = mutableListOf()
                    top = TABLE_TOP + header.height
                }
                rows.add(line to row.total)
                top += line.height
            }
            add(ReportPage(table.heading, subset, header, rows))
        }
    }
}

private fun reportLine(cells: List<String>, widths: List<Float>, heading: Boolean = false, total: Boolean = false) =
    ReportLine(cells.zip(widths).mapIndexed { index, (text, width) ->
        ReportCell(reportText(text, width - 10, if (heading) 9f else 10.5f,
            if (heading) white else navy, bold = heading || total,
            align = if (index == 1) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER,
            maxLines = if (heading) 4 else 5), width)
    })

private fun reportText(
    text: String,
    width: Float,
    size: Float,
    color: Int,
    bold: Boolean = false,
    align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
    maxLines: Int = 1,
): StaticLayout {
    val clean = text.replace(Regex("[\\p{Cntrl}\\s]+"), " ").trim()
    val ink = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        textLocale = reportLocale
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    return StaticLayout.Builder.obtain(clean, 0, clean.length, ink, width.toInt().coerceAtLeast(1))
        .setAlignment(align).setIncludePad(false).setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END).build()
}

private fun drawReportPage(
    context: Context, canvas: Canvas, report: MatchReportData, page: ReportPage, number: Int, count: Int,
) = with(canvas) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun block(left: Float, top: Float, end: Float, bottom: Float, color: Int, radius: Float = 0f) {
        paint.color = color
        drawRoundRect(left, top, end, bottom, radius, radius, paint)
    }
    fun text(value: String, x: Float, y: Float, width: Float, size: Float, color: Int, bold: Boolean = false,
             align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, maxLines: Int = 1) {
        drawLayout(reportText(value, width, size, color, bold, align, maxLines), x, y)
    }
    fun centered(value: String, x: Float, middle: Float, width: Float, size: Float, color: Int, maxLines: Int) {
        val layout = reportText(value, width, size, color, true, Layout.Alignment.ALIGN_CENTER, maxLines)
        drawLayout(layout, x, middle - layout.height / 2f)
    }

    drawColor(white)
    block(0f, 0f, PAGE_WIDTH.toFloat(), 57f, navy)
    for (index in 0..2) {
        val x = MARGIN + index * 7
        block(x, 34f - index * 5, x + 4, 42f, 0xFF44D763.toInt(), 2f)
    }
    text(context.getString(R.string.app_name), MARGIN + 29, 19f, 370f, 22f, white, true)
    text(context.getString(R.string.match_report_title), PAGE_WIDTH - MARGIN - 215, 25f, 215f, 12f, softGreen,
        align = Layout.Alignment.ALIGN_OPPOSITE)

    block(MARGIN, 72f, PAGE_WIDTH - MARGIN, 152f, gray, 9f)
    val center = PAGE_WIDTH / 2f
    block(center - 87, 84f, center + 87, 140f, softGreen, 8f)
    text(context.getString(R.string.match_report_result), center - 86, 90f, 172f, 9.5f, green, true, Layout.Alignment.ALIGN_CENTER)
    centered(report.score, center - 86, 119f, 172f, 24f, navy, 1)
    centered(report.team, MARGIN + 12, 112f, center - MARGIN - 109, 15f, navy, 3)
    centered(report.opponent, center + 97, 112f, center - MARGIN - 109, 15f, navy, 3)
    text(report.details, MARGIN, 162f, TABLE_WIDTH, 11f, muted)
    report.location?.let { text(it, MARGIN, 178f, TABLE_WIDTH, 10f, muted) }
    text(page.title, MARGIN, 201f, TABLE_WIDTH - 210, 16f, navy, true)
    page.subset?.let { text(it, PAGE_WIDTH - MARGIN - 235, 205f, 235f, 10f, muted, align = Layout.Alignment.ALIGN_OPPOSITE) }

    var top = TABLE_TOP
    fun line(item: ReportLine, background: Int) {
        block(MARGIN, top, PAGE_WIDTH - MARGIN, top + item.height, background)
        var left = MARGIN
        item.cells.forEach { cell ->
            drawLayout(cell.text, left + 5, top + (item.height - cell.text.height) / 2f)
            left += cell.width
        }
        block(MARGIN, top + item.height - .45f, PAGE_WIDTH - MARGIN, top + item.height, border)
        top += item.height
    }
    line(page.header, navy)
    if (page.rows.isEmpty()) text(context.getString(R.string.match_report_no_players), MARGIN + 10, top + 12, TABLE_WIDTH - 20, 10f, muted)
    page.rows.forEachIndexed { index, (row, total) -> line(row, if (total) softGreen else if (index % 2 == 0) white else gray) }
    block(MARGIN, 559f, PAGE_WIDTH - MARGIN, 560f, border)
    text(context.getString(R.string.match_report_no_measurements), MARGIN, 571f, TABLE_WIDTH - 150, 9f, muted)
    text(context.getString(R.string.match_report_page, number, count), PAGE_WIDTH - MARGIN - 146, 570f, 146f, 9.5f,
        navy, true, Layout.Alignment.ALIGN_OPPOSITE)
}

private fun Canvas.drawLayout(layout: StaticLayout, left: Float, top: Float) = withSave {
    translate(left, top)
    layout.draw(this)
}
