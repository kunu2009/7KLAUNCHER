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
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.sevenk.launcher.R
import java.io.File

class VideoEditorActivity : AppCompatActivity() {
    private lateinit var infoText: TextView
    private lateinit var videoView: VideoView
    private var pickedVideo: Uri? = null
    private var pickedMusic: Uri? = null
    private var videoDurationMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_editor)
        infoText = findViewById(R.id.info)
        videoView = findViewById(R.id.videoView)
        try {
            val controller = MediaController(this)
            controller.setAnchorView(videoView)
            videoView.setMediaController(controller)
        } catch (_: Throwable) {}

        findViewById<android.view.View>(R.id.btnPickVideo).setOnClickListener { pickVideo() }
        findViewById<android.view.View>(R.id.btnTrim10s).setOnClickListener { trimFirst10s() }
        findViewById<android.view.View>(R.id.btnPlayPause).setOnClickListener { togglePlayPause() }
        findViewById<android.view.View>(R.id.btnMuteExport).setOnClickListener { exportMute() }
        findViewById<android.view.View>(R.id.btnExtractFrame).setOnClickListener { extractCurrentFrame() }
        findViewById<android.view.View>(R.id.btnRotate90).setOnClickListener { exportRotate90() }
        findViewById<android.view.View>(R.id.btnSpeed05).setOnClickListener { exportSpeed(0.5f) }
        findViewById<android.view.View>(R.id.btnSpeed15).setOnClickListener { exportSpeed(1.5f) }
        findViewById<android.view.View>(R.id.btnSpeed2).setOnClickListener { exportSpeed(2.0f) }
        findViewById<android.view.View>(R.id.btnPickMusic).setOnClickListener { pickMusic() }
        findViewById<android.view.View>(R.id.btnExportWithMusic).setOnClickListener { exportWithMusicReplace() }
        findViewById<android.view.View>(R.id.btnExportTrimRange).setOnClickListener { exportTrimRange() }
    }

    private fun exportSpeed(factor: Float) {
        val src = pickedVideo ?: run { Toast.makeText(this, "Pick a video first", Toast.LENGTH_SHORT).show(); return }
        if (factor <= 0f) { Toast.makeText(this, "Invalid speed", Toast.LENGTH_SHORT).show(); return }
        try {
            val name = "7kstudio_speed_${factor}x_${System.currentTimeMillis()}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/7KSTUDIO")
            }
            val outUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Cannot create output")
            contentResolver.openFileDescriptor(outUri, "w")?.use { pfd ->
                val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val extractor = MediaExtractor()
                extractor.setDataSource(contentResolver.openFileDescriptor(src, "r")!!.fileDescriptor)

                var videoTrackIndex = -1
                var muxVideoTrack = -1

                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/")) { videoTrackIndex = i; break }
                }

                if (videoTrackIndex >= 0) {
                    val vFormat = extractor.getTrackFormat(videoTrackIndex)
                    muxVideoTrack = muxer.addTrack(vFormat)
                }
                muxer.start()

                // Write only video with scaled timestamps, enforce monotonic PTS
                if (videoTrackIndex >= 0 && muxVideoTrack >= 0) {
                    extractor.unselectTrack(videoTrackIndex)
                    extractor.selectTrack(videoTrackIndex)
                    extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    val buffer = java.nio.ByteBuffer.allocate(1 shl 20)
                    val info = MediaCodec.BufferInfo()
                    var basePtsUs: Long = -1
                    var lastPtsUs: Long = -1
                    while (true) {
                        info.offset = 0
                        info.size = extractor.readSampleData(buffer, 0)
                        if (info.size < 0) break
                        val inPts = extractor.sampleTime
                        if (basePtsUs < 0) basePtsUs = inPts
                        var outPtsUs = ((inPts - basePtsUs) / factor).toLong()
                        if (lastPtsUs >= 0 && outPtsUs <= lastPtsUs) outPtsUs = lastPtsUs + 1
                        info.presentationTimeUs = outPtsUs
                        info.flags = extractor.sampleFlags
                        muxer.writeSampleData(muxVideoTrack, buffer, info)
                        lastPtsUs = outPtsUs
                        extractor.advance()
                    }
                }

                muxer.stop(); muxer.release(); extractor.release()
            }
            Toast.makeText(this, "Exported ${factor}x to Movies/7KSTUDIO", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "Speed export failed: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportWithMusicReplace() {
        val videoUri = pickedVideo ?: run { Toast.makeText(this, "Pick a video first", Toast.LENGTH_SHORT).show(); return }
        val musicUri = pickedMusic ?: run { Toast.makeText(this, "Pick music first", Toast.LENGTH_SHORT).show(); return }
        try {
            val retrieverV = MediaMetadataRetriever(); retrieverV.setDataSource(this, videoUri)
            val vDurMs = (retrieverV.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L)
            retrieverV.release()
            val retrieverM = MediaMetadataRetriever(); retrieverM.setDataSource(this, musicUri)
            val mDurMs = (retrieverM.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L)
            retrieverM.release()
            val outDurUs = minOf(vDurMs, mDurMs) * 1000

            val name = "7kstudio_music_${System.currentTimeMillis()}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/7KSTUDIO")
            }
            val outUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Cannot create output")

            contentResolver.openFileDescriptor(outUri, "w")?.use { pfd ->
                val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val exVideo = MediaExtractor(); exVideo.setDataSource(contentResolver.openFileDescriptor(videoUri, "r")!!.fileDescriptor)
                val exMusic = MediaExtractor(); exMusic.setDataSource(contentResolver.openFileDescriptor(musicUri, "r")!!.fileDescriptor)

                var videoTrackIndex = -1
                var musicTrackIndex = -1
                var muxVideoTrack = -1
                var muxAudioTrack = -1

                for (i in 0 until exVideo.trackCount) {
                    val f = exVideo.getTrackFormat(i)
                    if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) { videoTrackIndex = i; break }
                }
                for (i in 0 until exMusic.trackCount) {
                    val f = exMusic.getTrackFormat(i)
                    if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) { musicTrackIndex = i; break }
                }
                if (videoTrackIndex < 0 || musicTrackIndex < 0) throw IllegalStateException("Missing tracks")

                val vFormat = exVideo.getTrackFormat(videoTrackIndex)
                val aFormat = exMusic.getTrackFormat(musicTrackIndex)
                muxVideoTrack = muxer.addTrack(vFormat)
                muxAudioTrack = muxer.addTrack(aFormat)
                muxer.start()

                // Copy video track fully
                exVideo.selectTrack(videoTrackIndex)
                exVideo.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                run {
                    val buffer = java.nio.ByteBuffer.allocate(1 shl 20)
                    val info = MediaCodec.BufferInfo()
                    while (true) {
                        info.offset = 0
                        info.size = exVideo.readSampleData(buffer, 0)
                        if (info.size < 0) break
                        info.presentationTimeUs = exVideo.sampleTime
                        info.flags = exVideo.sampleFlags
                        muxer.writeSampleData(muxVideoTrack, buffer, info)
                        exVideo.advance()
                    }
                }

                // Copy audio from music up to outDurUs
                exMusic.selectTrack(musicTrackIndex)
                exMusic.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                run {
                    val buffer = java.nio.ByteBuffer.allocate(1 shl 20)
                    val info = MediaCodec.BufferInfo()
                    while (true) {
                        info.offset = 0
                        info.size = exMusic.readSampleData(buffer, 0)
                        if (info.size < 0) break
                        info.presentationTimeUs = exMusic.sampleTime
                        if (info.presentationTimeUs > outDurUs) break
                        info.flags = exMusic.sampleFlags
                        muxer.writeSampleData(muxAudioTrack, buffer, info)
                        exMusic.advance()
                    }
                }

                muxer.stop(); muxer.release(); exVideo.release(); exMusic.release()
            }
            Toast.makeText(this, "Exported with music (replaced audio).", Toast.LENGTH_LONG).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "Music export failed: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportTrimRange() {
        val src = pickedVideo ?: run { Toast.makeText(this, "Pick a video first", Toast.LENGTH_SHORT).show(); return }
        val startSeek = findViewById<android.widget.SeekBar>(R.id.seekStart)
        val endSeek = findViewById<android.widget.SeekBar>(R.id.seekEnd)
        val durMs = videoDurationMs.takeIf { it > 0 } ?: run {
            Toast.makeText(this, "Load a video first", Toast.LENGTH_SHORT).show(); return
        }
        val startUs = (startSeek.progress.toLong() * durMs * 1000L) / 1000L
        val endUs = (endSeek.progress.toLong() * durMs * 1000L) / 1000L
        if (endUs <= startUs + 500_000L) { Toast.makeText(this, "Range too small", Toast.LENGTH_SHORT).show(); return }

        try {
            val name = "7kstudio_trim_${System.currentTimeMillis()}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/7KSTUDIO")
            }
            val outUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Cannot create output")
            contentResolver.openFileDescriptor(outUri, "w")?.use { pfd ->
                val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val extractor = MediaExtractor()
                extractor.setDataSource(contentResolver.openFileDescriptor(src, "r")!!.fileDescriptor)

                var videoTrackIndex = -1
                var audioTrackIndex = -1
                var muxVideoTrack = -1
                var muxAudioTrack = -1
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/")) videoTrackIndex = i else if (mime.startsWith("audio/")) audioTrackIndex = i
                }
                if (videoTrackIndex >= 0) muxVideoTrack = muxer.addTrack(extractor.getTrackFormat(videoTrackIndex))
                if (audioTrackIndex >= 0) muxAudioTrack = muxer.addTrack(extractor.getTrackFormat(audioTrackIndex))
                muxer.start()

                fun writeRange(trackIndex: Int, outIndex: Int) {
                    if (trackIndex < 0 || outIndex < 0) return
                    extractor.unselectTrack(trackIndex)
                    extractor.selectTrack(trackIndex)
                    extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    val buffer = java.nio.ByteBuffer.allocate(1 shl 20)
                    val info = MediaCodec.BufferInfo()
                    while (true) {
                        info.offset = 0
                        info.size = extractor.readSampleData(buffer, 0)
                        if (info.size < 0) break
                        val pts = extractor.sampleTime
                        if (pts < 0) break
                        if (pts > endUs) break
                        info.presentationTimeUs = pts - startUs
                        info.flags = extractor.sampleFlags
                        muxer.writeSampleData(outIndex, buffer, info)
                        extractor.advance()
                    }
                }

                writeRange(videoTrackIndex, muxVideoTrack)
                writeRange(audioTrackIndex, muxAudioTrack)

                muxer.stop(); muxer.release(); extractor.release()
            }
            Toast.makeText(this, "Trimmed range exported", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "Range trim failed: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pickVideo() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQ_PICK_VIDEO)
    }

    private fun pickMusic() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQ_PICK_MUSIC)
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

    private fun togglePlayPause() {
        if (pickedVideo == null) { Toast.makeText(this, "Pick a video first", Toast.LENGTH_SHORT).show(); return }
        if (videoView.isPlaying) {
            videoView.pause()
            (findViewById<android.view.View>(R.id.btnPlayPause) as? android.widget.Button)?.text = "Play"
        } else {
            videoView.start()
            (findViewById<android.view.View>(R.id.btnPlayPause) as? android.widget.Button)?.text = "Pause"
        }
    }

    private fun exportMute() {
        val src = pickedVideo ?: run { Toast.makeText(this, "Pick a video first", Toast.LENGTH_SHORT).show(); return }
        try {
            val name = "7kstudio_mute_${System.currentTimeMillis()}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/7KSTUDIO")
            }
            val outUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Cannot create output")
            contentResolver.openFileDescriptor(outUri, "w")?.use { pfd ->
                val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val extractor = MediaExtractor()
                extractor.setDataSource(contentResolver.openFileDescriptor(src, "r")!!.fileDescriptor)
                var videoTrackIndex = -1
                var muxVideoTrack = -1
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/")) {
                        videoTrackIndex = i
                    }
                }
                if (videoTrackIndex < 0) throw IllegalStateException("No video track")
                extractor.selectTrack(videoTrackIndex)
                extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val vFormat = extractor.getTrackFormat(videoTrackIndex)
                muxVideoTrack = muxer.addTrack(vFormat)
                muxer.start()
                val buffer = java.nio.ByteBuffer.allocate(1 shl 20)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    info.offset = 0
                    info.size = extractor.readSampleData(buffer, 0)
                    if (info.size < 0) break
                    info.presentationTimeUs = extractor.sampleTime
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxVideoTrack, buffer, info)
                    extractor.advance()
                }
                muxer.stop(); muxer.release(); extractor.release()
            }
            Toast.makeText(this, "Exported mute to Movies/7KSTUDIO", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "Mute export failed: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractCurrentFrame() {
        val src = pickedVideo ?: run { Toast.makeText(this, "Pick a video first", Toast.LENGTH_SHORT).show(); return }
        try {
            val posMs = try { videoView.currentPosition } catch (_: Throwable) { 0 }
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, src)
            val frame = retriever.getFrameAtTime(posMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
            retriever.release()
            if (frame == null) { Toast.makeText(this, "Failed to get frame", Toast.LENGTH_SHORT).show(); return }
            val name = "7kstudio_frame_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/7KSTUDIO")
            }
            val out = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (out != null) {
                contentResolver.openOutputStream(out)?.use { os ->
                    frame.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os)
                }
                Toast.makeText(this, "Frame saved to Pictures/7KSTUDIO", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            }
        } catch (t: Throwable) {
            Toast.makeText(this, "Extract failed: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportRotate90() {
        val src = pickedVideo ?: run { Toast.makeText(this, "Pick a video first", Toast.LENGTH_SHORT).show(); return }
        try {
            val name = "7kstudio_rot90_${System.currentTimeMillis()}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/7KSTUDIO")
            }
            val outUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Cannot create output")
            contentResolver.openFileDescriptor(outUri, "w")?.use { pfd ->
                val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                // Orientation hint must be set before start()
                try { muxer.setOrientationHint(90) } catch (_: Throwable) {}
                val extractor = MediaExtractor()
                extractor.setDataSource(contentResolver.openFileDescriptor(src, "r")!!.fileDescriptor)
                var videoTrackIndex = -1
                var audioTrackIndex = -1
                var muxVideoTrack = -1
                var muxAudioTrack = -1
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/")) videoTrackIndex = i else if (mime.startsWith("audio/")) audioTrackIndex = i
                }
                if (videoTrackIndex >= 0) {
                    extractor.selectTrack(videoTrackIndex)
                    extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    val vFormat = extractor.getTrackFormat(videoTrackIndex)
                    muxVideoTrack = muxer.addTrack(vFormat)
                }
                if (audioTrackIndex >= 0) {
                    val aFormat = extractor.getTrackFormat(audioTrackIndex)
                    muxAudioTrack = muxer.addTrack(aFormat)
                }
                muxer.start()

                fun writeAll(trackIndex: Int, outIndex: Int) {
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
                        muxer.writeSampleData(outIndex, buffer, info)
                        extractor.advance()
                    }
                }
                writeAll(videoTrackIndex, muxVideoTrack)
                writeAll(audioTrackIndex, muxAudioTrack)
                muxer.stop(); muxer.release(); extractor.release()
            }
            Toast.makeText(this, "Rotated export saved to Movies/7KSTUDIO", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "Rotate export failed: ${t.message}", Toast.LENGTH_SHORT).show()
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
                try {
                    videoView.setVideoURI(uri)
                    videoView.visibility = android.view.View.VISIBLE
                    videoView.setOnPreparedListener { mp ->
                        // Display first frame
                        try { videoView.seekTo(1) } catch (_: Throwable) {}
                        try { videoView.start() } catch (_: Throwable) {}
                        try {
                            // Initialize trim range UI
                            val retriever = MediaMetadataRetriever(); retriever.setDataSource(this, uri)
                            videoDurationMs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L)
                            retriever.release()
                            val startSeek = findViewById<android.widget.SeekBar>(R.id.seekStart)
                            val endSeek = findViewById<android.widget.SeekBar>(R.id.seekEnd)
                            val labelStart = findViewById<TextView>(R.id.labelStart)
                            val labelEnd = findViewById<TextView>(R.id.labelEnd)
                            fun updateLabels() {
                                val sMs = (startSeek.progress.toLong() * videoDurationMs) / 1000L
                                val eMs = (endSeek.progress.toLong() * videoDurationMs) / 1000L
                                labelStart.text = "Start: ${sMs/1000}s"
                                labelEnd.text = "End: ${eMs/1000}s"
                            }
                            startSeek.setOnSeekBarChangeListener(object: android.widget.SeekBar.OnSeekBarChangeListener {
                                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                                    if (fromUser && p >= endSeek.progress) endSeek.progress = (p + 1).coerceAtMost(1000)
                                    updateLabels()
                                }
                                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
                            })
                            endSeek.setOnSeekBarChangeListener(object: android.widget.SeekBar.OnSeekBarChangeListener {
                                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                                    if (fromUser && p <= startSeek.progress) startSeek.progress = (p - 1).coerceAtLeast(0)
                                    updateLabels()
                                }
                                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
                            })
                            updateLabels()
                        } catch (_: Throwable) {}
                    }
                } catch (_: Throwable) {}
            }
        }
        if (requestCode == REQ_PICK_MUSIC && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
                pickedMusic = uri
                infoText.text = "Music: $uri"
            }
        }
    }

    companion object {
        private const val REQ_PICK_VIDEO = 7001
        private const val REQ_PICK_MUSIC = 7002
    }
}
