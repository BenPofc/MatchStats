package br.com.matchstats.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import br.com.matchstats.R
import br.com.matchstats.data.BoxScore
import br.com.matchstats.data.prepareMatchReport
import br.com.matchstats.data.reportFilename
import br.com.matchstats.data.writeMatchReport
import br.com.matchstats.ui.components.MatchStatsSecondaryButton
import br.com.matchstats.ui.theme.MatchStatsColors
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SHARED_REPORT_DIRECTORY = "shared-match-reports"
private const val EXPORT_REPORT_DIRECTORY = "pending-match-exports"
private const val PDF_MIME = "application/pdf"
private const val REPORT_RETENTION_MS = 24 * 60 * 60 * 1000L

private data class StagedMatchReport(val file: File, val suggestedName: String)
private class FinalReportUnavailable : IOException()

internal fun matchReportShareIntent(context: Context, report: File): Intent {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".reports", report)
    return Intent(Intent.ACTION_SEND).apply {
        type = PDF_MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.match_report_title))
        clipData = ClipData.newUri(context.contentResolver, report.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun Context.reportDirectory(name: String): File = File(cacheDir, name).also { root ->
    if (!root.isDirectory && !root.mkdirs() && !root.isDirectory) throw IOException()
    val cutoff = System.currentTimeMillis() - REPORT_RETENTION_MS
    root.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { old ->
        if (old.isDirectory) {
            old.listFiles()?.forEach { it.delete() }
        }
        old.delete()
    }
}

private fun Context.stageReport(score: BoxScore, share: Boolean): StagedMatchReport {
    val name = score.fixture.reportFilename()
    val root = reportDirectory(if (share) SHARED_REPORT_DIRECTORY else EXPORT_REPORT_DIRECTORY)
    val file = if (share) File(Files.createTempDirectory(root.toPath(), "report-").toFile(), name)
        else File.createTempFile("report-", ".pdf", root)
    try {
        file.outputStream().buffered().use { writeMatchReport(this, prepareMatchReport(this, score), it) }
        return StagedMatchReport(file, name)
    } catch (error: Exception) {
        discardStaged(file, share)
        throw error
    }
}

private fun discardStaged(file: File, share: Boolean) {
    file.delete()
    if (share) file.parentFile?.delete()
}

@Composable
internal fun MatchReportActions(enabled: Boolean, readBoxScore: suspend () -> BoxScore?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareLabel = stringResource(R.string.match_report_share)
    var pendingExport by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf<Int?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME)) { target ->
        val name = pendingExport ?: return@rememberLauncherForActivityResult
        pendingExport = null
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val file = File(File(context.cacheDir, EXPORT_REPORT_DIRECTORY), name)
                    try {
                        if (target != null) {
                            val output = context.contentResolver.openOutputStream(target, "wt") ?: throw IOException()
                            output.use { destination -> file.inputStream().use { it.copyTo(destination) } }
                        }
                    } finally {
                        file.delete()
                    }
                }
                if (target != null) feedback = R.string.match_report_saved
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                feedback = R.string.match_report_error
            } finally {
                busy = false
            }
        }
    }

    fun start(share: Boolean) {
        if (busy || pendingExport != null) return
        busy = true
        feedback = null
        scope.launch {
            var staged: StagedMatchReport? = null
            try {
                val readyReport = withContext(Dispatchers.IO) {
                    context.stageReport(readBoxScore() ?: throw FinalReportUnavailable(), share)
                }
                staged = readyReport
                if (share) {
                    context.startActivity(Intent.createChooser(matchReportShareIntent(context, readyReport.file), shareLabel))
                } else {
                    pendingExport = readyReport.file.name
                    picker.launch(readyReport.suggestedName)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                staged?.let { report -> withContext(Dispatchers.IO) { discardStaged(report.file, share) } }
                if (!share) pendingExport = null
                feedback = if (error is FinalReportUnavailable) R.string.match_report_unavailable else R.string.match_report_error
            } finally {
                busy = false
            }
        }
    }

    val ready = enabled && !busy && pendingExport == null
    MatchStatsSecondaryButton(stringResource(R.string.match_report_export), { start(false) }, Modifier.fillMaxWidth(), enabled = ready)
    MatchStatsSecondaryButton(shareLabel, { start(true) }, Modifier.fillMaxWidth(), enabled = ready)
    feedback?.let {
        Text(stringResource(it), color = if (it == R.string.match_report_saved) MatchStatsColors.GreenInk else MatchStatsColors.Error)
    }
}
