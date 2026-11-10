package com.notuygulamam.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.ZipInputStream

/** Ses kaydını cihazda, internetsiz yazıya çevirir (Vosk, küçük Türkçe model). Model yalnız bir kez indirilir. */
object Transcribe {
    const val MODEL_NAME = "vosk-model-small-tr-0.3"
    const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip"

    private fun root(ctx: Context) = File(ctx.filesDir, "vosk").also { it.mkdirs() }
    fun modelDir(ctx: Context) = File(root(ctx), MODEL_NAME)
    fun hasModel(ctx: Context) = File(modelDir(ctx), "am").isDirectory && File(modelDir(ctx), "conf").isDirectory
    fun textFileOf(rec: File) = File(rec.parentFile, rec.name.removeSuffix(".m4a") + ".txt")

    /** Modeli indirip açar. Başarılıysa null, değilse hata iletisi döner. İş parçacığında çağrılmalıdır. */
    fun download(ctx: Context, progress: (Int) -> Unit): String? {
        val zip = File(root(ctx), "model.zip")
        try {
            val con = URL(MODEL_URL).openConnection() as HttpURLConnection
            con.connectTimeout = 20000
            con.readTimeout = 30000
            con.instanceFollowRedirects = true
            if (con.responseCode != 200) return "Sunucu yanıtı: " + con.responseCode
            val total = con.contentLengthLong
            con.inputStream.use { ins ->
                FileOutputStream(zip).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var got = 0L
                    var last = -1
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        got += n
                        if (total > 0) {
                            val p = (got * 100 / total).toInt()
                            if (p != last) { last = p; progress(p) }
                        }
                    }
                }
            }
            val base = root(ctx).canonicalFile
            ZipInputStream(zip.inputStream().buffered()).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    val target = File(base, e.name).canonicalFile
                    if (!target.path.startsWith(base.path + File.separator)) continue
                    if (e.isDirectory) { target.mkdirs(); continue }
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { o -> zin.copyTo(o) }
                }
            }
            zip.delete()
            return if (hasModel(ctx)) null else "Model dosyaları eksik çıktı"
        } catch (e: Exception) {
            zip.delete()
            return e.message ?: e.javaClass.simpleName
        }
    }

    /** Kaydı yazıya çevirir ve kayıt yanına .txt olarak saklar. İş parçacığında çağrılmalıdır. */
    fun transcribe(ctx: Context, rec: File, progress: (Int) -> Unit): Result<String> {
        return try {
            val model = Model(modelDir(ctx).absolutePath)
            val parts = ArrayList<String>()
            Recognizer(model, 16000f).use { r ->
                fun take(json: String) {
                    val t = JSONObject(json).optString("text", "").trim()
                    if (t.isNotEmpty()) parts.add(t)
                }
                decodeTo16k(rec, { pcm, n -> if (r.acceptWaveForm(pcm, n)) take(r.result) }, progress)
                take(r.finalResult)
            }
            model.close()
            val tr = Locale("tr")
            val text = parts.joinToString(" ") { s -> s.substring(0, 1).uppercase(tr) + s.substring(1) + "." }
            if (text.isBlank()) return Result.failure(Exception("Kayıtta anlaşılır konuşma bulunamadı"))
            textFileOf(rec).writeText(text)
            Result.success(text)
        } catch (e: Throwable) {
            Result.failure(Exception(e.message ?: e.javaClass.simpleName))
        }
    }

    /** m4a dosyasını çözüp 16 kHz, tek kanallı 16 bit örnekler olarak sink'e verir. */
    private fun decodeTo16k(file: File, sink: (ShortArray, Int) -> Unit, progress: (Int) -> Unit) {
        val ex = MediaExtractor()
        ex.setDataSource(file.absolutePath)
        var track = -1
        for (i in 0 until ex.trackCount) {
            val m = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
            if (m.startsWith("audio/")) { track = i; break }
        }
        if (track < 0) { ex.release(); throw Exception("Ses kanalı bulunamadı") }
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        var inRate = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        var channels = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()
        val info = MediaCodec.BufferInfo()
        val out = ShortArray(16000)
        var outN = 0
        var inIdx = 0L
        var prevS = 0f
        var outPos = 0.0
        var inputDone = false
        var lastPct = -1
        try {
            while (true) {
                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(10000)
                    if (ii >= 0) {
                        val b = codec.getInputBuffer(ii)!!
                        val n = ex.readSampleData(b, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = codec.outputFormat
                    inRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else if (oi >= 0) {
                    val ob = codec.getOutputBuffer(oi)!!
                    ob.position(info.offset)
                    ob.limit(info.offset + info.size)
                    val sb = ob.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val frames = sb.remaining() / maxOf(1, channels)
                    val ratio = inRate / 16000.0
                    for (f in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until channels) sum += sb.get().toFloat()
                        val s = sum / channels
                        while (outPos <= inIdx) {
                            val frac = (outPos - (inIdx - 1)).toFloat()
                            out[outN++] = (prevS + (s - prevS) * frac).toInt().coerceIn(-32768, 32767).toShort()
                            if (outN == out.size) { sink(out, outN); outN = 0 }
                            outPos += ratio
                        }
                        prevS = s
                        inIdx++
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (durUs > 0) {
                        val p = (info.presentationTimeUs * 100 / durUs).toInt().coerceIn(0, 100)
                        if (p != lastPct) { lastPct = p; progress(p) }
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            if (outN > 0) sink(out, outN)
        } finally {
            try { codec.stop() } catch (e: Exception) {}
            codec.release()
            ex.release()
        }
    }
}
