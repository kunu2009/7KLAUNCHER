package com.sevenk.studio

import android.content.ContentValues
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sevenk.launcher.R
import java.io.File

class VideoEditorActivity : AppCompatActivity() {
    private lateinit var infoText: TextView
    private var pickedVideo: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_editor)
        infoText = findViewById(R.id.info)

        findViewById<android.view.View>(R.id.btnPickVideo).setOnClickListener { pickVideo() }
        findViewById<android.view.View>(R.id.btnTrim10s).setOnClickListener { trimFirst10s() }
    }

    private fun pickVideo() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQ_PICK_VIDEO)
    }

    private fun trimFirst10s() {
        val src = pickedVideo ?: run { Toast.makeText(this, "Pick a video first", Toast.LENGTH_SHORT).show(); return }
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, src)
            val durMs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L)
            val endUs = minOf(10_000_000L, durMs * 1000)
            retriever.release()

            val resolver = contentResolver
            val name = "7kstudio_trim_${System.currentTimeMillis()}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/7KSTUDIO")
            }
            val outUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Cannot create output")

            contentResolver.openFileDescriptor(outUri, "w")?.use { pfd ->
                val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val extractor = MediaExtractor()
                extractor.setDataSource(contentResolver.openFileDescriptor(src, "r")!!.fileDescriptor)

                var videoTrackIndex = -1
                var audioTrackIndex = -1
                var muxVideoTrack = -1
                var muxAudioTrack = -1

                // Select tracks
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/")) {
                        videoTrackIndex = i
                    } else if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                    }
                }

                // Start muxing
                var muxerStartedLocal = false
                fun ensureStart() { if (!muxerStartedLocal) { muxer.start(); muxerStartedLocal = true } }

                // Video first
                if (videoTrackIndex >= 0) {
                    extractor.selectTrack(videoTrackIndex)
                    extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    val vFormat = extractor.getTrackFormat(videoTrackIndex)
                    muxVideoTrack = muxer.addTrack(vFormat)
                }
                if (audioTrackIndex >= 0) {
                    extractor.selectTrack(audioTrackIndex)
                    extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    val aFormat = extractor.getTrackFormat(audioTrackIndex)
                    muxAudioTrack = muxer.addTrack(aFormat)
                }
                ensureStart()

                // Write samples
                fun writeSamples(trackIndex: Int, outIndex: Int) {
                    if (trackIndex < 0 || outIndex < 0) return
                    extractor.unselectTrack(trackIndex)
                    extractor.selectTrack(trackIndex)
                    extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    val buffer = java.nio.ByteBuffer.allocate(1 shl 20)
                    val info = MediaCodec.BufferInfo()
                    while (true) {
                        info.offset = 0
                        info.size = extractor.readSampleData(buffer, 0)
                        if (info.size < 0) break
                        info.presentationTimeUs = extractor.sampleTime
                        info.flags = extractor.sampleFlags
                        if (info.presentationTimeUs > endUs) break
                        muxer.writeSampleData(outIndex, buffer, info)
                        extractor.advance()
                    }
                }
                writeSamples(videoTrackIndex, muxVideoTrack)
                writeSamples(audioTrackIndex, muxAudioTrack)

                muxer.stop()
                muxer.release()
                extractor.release()
            }

            Toast.makeText(this, "Exported to Movies/7KSTUDIO", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "Trim failed: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_VIDEO && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
                pickedVideo = uri
                infoText.text = "Picked: $uri"
            }
        }
    }

    companion object { private const val REQ_PICK_VIDEO = 7001 }
}
