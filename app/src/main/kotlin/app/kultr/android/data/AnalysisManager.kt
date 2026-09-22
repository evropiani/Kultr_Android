package app.kultr.android.data

import app.kultr.android.AppGraph
import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.kultr.android.KultrApp
import app.kultr.android.data.db.toAnalysis
import app.kultr.android.data.db.toEntity
import app.kultr.android.data.db.toSong
import app.kultr.core.api.Song
import app.kultr.core.dsp.ANALYSIS_BITRATE
import app.kultr.core.dsp.ANALYSIS_VERSION
import app.kultr.core.dsp.MonoAccumulator
import app.kultr.core.dsp.TrackAnalysis
import app.kultr.core.dsp.analysePcm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.ByteOrder

/** Longest track worth analysing; DJ mixes and audiobooks do not need InjeKt. */
private const val MAX_ANALYSIS_SECONDS = 20 * 60

data class AnalysisProgress(val done: Int, val total: Int, val current: String)

/**
 * InjeKt's analysis: decode a track once (at a low bitrate, or from the offline
 * copy), measure tempo, key, energy and structure, and cache the numbers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisManager(private val graph: AppGraph) {
    private val inFlight = HashMap<String, CompletableDeferred<TrackAnalysis?>>()
    private val lock = Mutex()

    /** Decoding is heavy; never more than two at once. */
    private val permits = Semaphore(2)

    val analysedCount: Flow<Int> = graph.database.flatMapLatest { db ->
        db?.analysis()?.countFlow(ANALYSIS_VERSION) ?: flowOf(0)
    }

    val missingCount: Flow<Int> = graph.database.flatMapLatest { db ->
        db?.analysis()?.missingCountFlow(ANALYSIS_VERSION, MAX_ANALYSIS_SECONDS) ?: flowOf(0)
    }

    private val _bulk = MutableStateFlow<AnalysisProgress?>(null)
    val bulkProgress: StateFlow<AnalysisProgress?> = _bulk.asStateFlow()

    internal fun reportBulk(progress: AnalysisProgress?) {
        _bulk.value = progress
    }

    fun observe(songId: String): Flow<TrackAnalysis?> = graph.database.flatMapLatest { db ->
        db?.analysis()?.observe(songId)?.map { it?.toAnalysis()?.takeIf { a -> a.isCurrent } } ?: flowOf(null)
    }

    suspend fun cached(songId: String): TrackAnalysis? {
        val db = graph.database.value ?: return null
        return withContext(Dispatchers.IO) { db.analysis().get(songId)?.toAnalysis()?.takeIf { it.isCurrent } }
    }

    suspend fun cachedMany(ids: List<String>): Map<String, TrackAnalysis> {
        val db = graph.database.value ?: return emptyMap()
        return withContext(Dispatchers.IO) {
            ids.chunked(500).flatMap { db.analysis().getMany(it) }
                .map { it.toAnalysis() }
                .filter { it.isCurrent }
                .associateBy { it.songId }
        }
    }

    /** Cached analysis, or analyse now. Concurrent requests for one track share the work. */
    suspend fun getOrAnalyse(song: Song, force: Boolean = false): TrackAnalysis? {
        if (song.isRadio) return null
        if (!force) cached(song.id)?.let { return it }
        val (deferred, owner) = lock.withLock {
            val existing = inFlight[song.id]
            if (existing != null) {
                existing to false
            } else {
                CompletableDeferred<TrackAnalysis?>().also { inFlight[song.id] = it } to true
            }
        }
        if (!owner) return deferred.await()
        val result = try {
            permits.withPermit { analyse(song) }
        } catch (err: CancellationException) {
            deferred.complete(null)
            lock.withLock { inFlight.remove(song.id) }
            throw err
        } catch (_: Exception) {
            null
        }
        deferred.complete(result)
        lock.withLock { inFlight.remove(song.id) }
        return result
    }

    /** Warm the cache for a track that is about to play. Fire and forget. */
    fun analyseAhead(song: Song) {
        val s = graph.settings.current
        if (!s.injektEnabled || !s.injektAnalyseAhead || song.isRadio) return
        graph.scope.launch { runCatching { getOrAnalyse(song) } }
    }

    private suspend fun analyse(song: Song): TrackAnalysis? {
        val db = graph.database.value ?: return null
        val duration = song.duration ?: 0
        if (duration > MAX_ANALYSIS_SECONDS) return null
        val local = graph.offline.fileFor(song.id)
        if (local == null && graph.settings.current.injektAnalyseOnWifiOnly && graph.network.isMetered) return null
        val client = graph.auth.client.value ?: return null

        return withContext(Dispatchers.Default) {
            val temp = if (local == null) File.createTempFile("analysis", ".audio", graph.app.cacheDir) else null
            try {
                val source = local ?: temp!!.also { file ->
                    val url = client.streamUrl(song.id, ANALYSIS_BITRATE, "mp3")
                    withContext(Dispatchers.IO) {
                        graph.http.newCall(Request.Builder().url(url).build()).execute().use { res ->
                            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                            file.outputStream().use { out -> res.body.byteStream().use { it.copyTo(out, 64 * 1024) } }
                        }
                    }
                }
                val (pcm, rate) = AudioDecoder.decodeToMono(source, MAX_ANALYSIS_SECONDS)
                if (pcm.size < rate * 5) return@withContext null
                val analysis = TrackAnalysis.from(song, analysePcm(pcm, rate))
                db.analysis().put(analysis.toEntity())
                analysis
            } finally {
                temp?.delete()
            }
        }
    }

    fun startBulk() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (graph.settings.current.injektAnalyseOnWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<AnalysisWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(graph.app).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        graph.messages.show("Analysing tracks in the background.")
    }

    fun cancelBulk() {
        WorkManager.getInstance(graph.app).cancelUniqueWork(WORK_NAME)
        _bulk.value = null
    }

    suspend fun clear() {
        val db = graph.database.value ?: return
        withContext(Dispatchers.IO) { db.analysis().clear() }
    }

    companion object {
        const val WORK_NAME = "kultr.analysis"
    }
}

/** "Analyse missing": work through every track without current analysis. */
class AnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = KultrApp.graph
        val db = graph.database.value ?: return Result.success()
        var done = 0
        val skipped = HashSet<String>()
        try {
            while (!isStopped) {
                val batch = db.analysis().missing(ANALYSIS_VERSION, MAX_ANALYSIS_SECONDS, 50 + skipped.size)
                    .filter { it.id !in skipped }
                if (batch.isEmpty()) break
                val total = done + batch.size
                for (entity in batch) {
                    if (isStopped) break
                    val song = entity.toSong()
                    graph.analysis.reportBulk(AnalysisProgress(done, total, song.title))
                    val result = runCatching { graph.analysis.getOrAnalyse(song) }.getOrNull()
                    if (result == null) skipped += song.id
                    done++
                }
            }
        } finally {
            graph.analysis.reportBulk(null)
        }
        if (done > 0) graph.messages.success("InjeKt analysis finished ($done tracks).")
        return Result.success()
    }
}

/** Decode any audio file Android understands into mono PCM at ~22 kHz. */
object AudioDecoder {
    private const val TIMEOUT_US = 10_000L

    fun decodeToMono(file: File, maxSeconds: Int): Pair<FloatArray, Int> {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        try {
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("No audio in this file.")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IOException("Unknown audio format.")
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var accumulator = MonoAccumulator(rate, channels)

            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                var shorts = ShortArray(0)
                var floats = FloatArray(0)
                while (!outputDone) {
                    if (!inputDone) {
                        val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inIndex >= 0) {
                            val buffer = codec.getInputBuffer(inIndex) ?: throw IOException("Decoder input unavailable.")
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        outIndex >= 0 -> {
                            val buffer = codec.getOutputBuffer(outIndex)
                            if (buffer != null && info.size > 0) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                buffer.order(ByteOrder.nativeOrder())
                                if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                    val count = info.size / 4
                                    if (floats.size < count) floats = FloatArray(count)
                                    buffer.asFloatBuffer().get(floats, 0, count)
                                    accumulator.addFloat(floats, count)
                                } else {
                                    val count = info.size / 2
                                    if (shorts.size < count) shorts = ShortArray(count)
                                    buffer.asShortBuffer().get(shorts, 0, count)
                                    accumulator.addPcm16(shorts, count)
                                }
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                            if (accumulator.length > maxSeconds.toLong() * accumulator.sampleRate) outputDone = true
                        }
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val out = codec.outputFormat
                            rate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = out.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            if (accumulator.length == 0) accumulator = MonoAccumulator(rate, channels)
                        }
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            return accumulator.toArray() to accumulator.sampleRate
        } finally {
            extractor.release()
        }
    }
}
