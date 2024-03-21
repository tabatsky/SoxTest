package jatx.soxtest

import android.Manifest
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import jatx.soxtest.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val openAudioFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { theUri ->
           loadAudioFileFromUri(theUri)
        }
    }

    private val saveAudioFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { theUri ->
            saveAudioFileToUri(theUri)
        }
    }

    private val tmpFiles = arrayListOf<File>()
    private val appliedEffects = arrayListOf<AudioEffect>()

    private var mediaPlayer: MediaPlayer? = null

    private var currentProjectFile: File? = null
        set(value) {
            field = value
            binding.tvFileName.text = value?.name ?: ""
        }
    private var outFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cleanProject()

        // Example of a call to a native method
        binding.btnLoadFile.setOnClickListener {
            tryOpenAudioFile()
        }

        binding.btnSaveMp3.setOnClickListener {
            trySaveAudioFile("mp3")
        }

        binding.btnSaveFlac.setOnClickListener {
            trySaveAudioFile("flac")
        }

        binding.btnApplyTempo.setOnClickListener {
            val tempo = binding.etTempo.text.toString()
                .takeIf { it.isNotEmpty() }
                ?.toFloat()
                ?.takeIf { it > 0 } ?: 1.0f
            applyTempo(tempo)
        }

        binding.btnApplyPitch.setOnClickListener {
            val pitch = binding.etPitch.text.toString()
                .takeIf { it.isNotEmpty() }
                ?.toInt() ?: 0
            applyPitch(pitch)
        }

        binding.btnApplyReverse.setOnClickListener {
            applyReverse()
        }

        binding.btnUndo.setOnClickListener {
            undoEffect()
        }

        binding.btnPlay.setOnClickListener {
            playResult()
        }

        binding.btnPause.setOnClickListener {
            pausePlayer()
        }

        val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        binding.seekBar.setOnSeekBarChangeListener(seekBarListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAndReleasePlayer()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun cleanProject() {
        tmpFiles.clear()
        appliedEffects.clear()
        currentProjectFile = null
        outFile = null
        FileUtils.cleanDirectory(getProjectDir())
    }

    private fun cleanOutFile() {
        outFile?.let {
            FileUtils.delete(it)
        }
        outFile = null
    }

    private fun getProjectDir() = getExternalFilesDir(null)

    private fun setButtonsEnables(enabled: Boolean) {
        binding.btnLoadFile.isEnabled = enabled
        binding.btnSaveMp3.isEnabled = enabled
        binding.btnSaveFlac.isEnabled = enabled
        binding.btnApplyTempo.isEnabled = enabled
        binding.btnApplyPitch.isEnabled = enabled
        binding.btnApplyReverse.isEnabled = enabled
        binding.btnUndo.isEnabled = enabled
    }

    private fun tryOpenAudioFile() {
        openAudioFileLauncher.launch(arrayOf("*/*"))
    }

    private fun trySaveAudioFile(extension: String) {
        tmpFiles.lastOrNull()?.let { lastFile ->
            outFile = generateTmpFileFromCurrentDate(extension)
        }
        currentProjectFile?.let {theCurrentProjectFile ->
            val fileNameParts = listOf(theCurrentProjectFile.nameWithoutExtension) +
                    appliedEffects.drop(1).map { it.fileNameModifier }
            val fileNameWithoutExtension = fileNameParts.joinToString("_")
            val fileName = "${fileNameWithoutExtension}.$extension"
            saveAudioFileLauncher.launch(fileName)
        }
    }

    private fun generateTmpFileFromCurrentDate(extension: String): File {
        val sdf = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault())
        val dateStr = sdf.format(Date())
        val fileName = "${dateStr}.${extension}"
        return File(getProjectDir(), fileName)
    }

    private fun copyFileAndGetPath(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null) ?: return null
        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        cursor.moveToFirst()
        val displayName = cursor.getString(columnIndex)
        cursor.close()

        val inputStream = contentResolver.openInputStream(uri)
        val newFile = File(getProjectDir(), displayName)
        val fileOutputStream = FileOutputStream(newFile)

        inputStream?.copyTo(fileOutputStream)

        fileOutputStream.flush()
        fileOutputStream.close()
        inputStream?.close()

        tmpFiles.add(newFile)
        currentProjectFile = newFile

        return newFile.absolutePath
    }

    private suspend fun convertLastFileToOutFile(): Boolean {
        tmpFiles.lastOrNull()?.let { lastFile ->
            outFile?.let { theOutFile ->
                val result = convertAudioFileJNI(lastFile.absolutePath, theOutFile.absolutePath)
                if (result == 0) {
                    withContext(Dispatchers.Main) {
                        showToast("success")
                    }
                    return true
                } else {
                    withContext(Dispatchers.Main) {
                        showToast("an error occured")
                    }
                    return false
                }
            }
        }
        return false
    }

    private fun copyOutFileToUri(uri: Uri) {
        val outputStream = contentResolver.openOutputStream(uri)
        outFile?.let { theOutFile ->
            val fileInputStream = FileInputStream(theOutFile)

            outputStream?.let {
                fileInputStream.copyTo(it)
                it.flush()
                it.close()
            }

            fileInputStream.close()
        }
    }

    private fun tryLoadAudioFileFromUri(uri: Uri) {
        val permissionListener = object: PermissionListener {
            override fun onPermissionGranted() {
                loadAudioFileFromUri(uri)
            }

            override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
                Log.e("access", "no sdcard access")
            }
        }

        checkMediaPermissions(permissionListener)
    }

    private fun loadAudioFileFromUri(uri: Uri) {
        performAsync {
            cleanProject()
            copyFileAndGetPath(uri)?.let { origPath ->
                val newFile = generateTmpFileFromCurrentDate("wav")
                val result = convertAudioFileJNI(origPath, newFile.absolutePath)
                if (result == 0) {
                    applyEffect(newFile, LoadFile(File(origPath)))
                    withContext(Dispatchers.Main) {
                        showToast("success")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showToast("an error occured")
                    }
                }
            }
        }
    }

    private fun trySaveAudioFileToUri(uri: Uri) {
        val permissionListener = object: PermissionListener {
            override fun onPermissionGranted() {
                saveAudioFileToUri(uri)
            }

            override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
                Log.e("access", "no sdcard access")
            }
        }

        checkMediaPermissions(permissionListener)
    }

    private fun saveAudioFileToUri(uri: Uri) {
        performAsync {
            if (convertLastFileToOutFile()) {
                copyOutFileToUri(uri)
                cleanOutFile()
            }
        }
    }

    private fun checkMediaPermissions(permissionListener: PermissionListener) {
        if (Build.VERSION.SDK_INT >= 33) {
            TedPermission.create()
                .setPermissionListener(permissionListener)
                .setPermissions(
                    Manifest.permission.READ_MEDIA_AUDIO
                )
                .check()
        } else if (Build.VERSION.SDK_INT >= 30) {
            TedPermission.create()
                .setPermissionListener(permissionListener)
                .setPermissions(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
                .check()
        } else {
            TedPermission.create()
                .setPermissionListener(permissionListener)
                .setPermissions(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                .check()
        }
    }

    private fun applyTempo(tempo: Float) {
        performAsync {
            tmpFiles.lastOrNull()?.let { inFile ->
                val newFile = generateTmpFileFromCurrentDate("wav")
                val result = applyTempoJNI(inFile.absolutePath, newFile.absolutePath, tempo.toString())
                if (result == 0) {
                    applyEffect(newFile, Tempo(tempo))
                    withContext(Dispatchers.Main) {
                        showToast("success")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showToast("an error occured")
                    }
                }
            }
        }
    }

    private fun applyPitch(pitch: Int) {
        performAsync {
            tmpFiles.lastOrNull()?.let { inFile ->
                val newFile = generateTmpFileFromCurrentDate("wav")
                val result = applyPitchJNI(inFile.absolutePath, newFile.absolutePath, pitch.toString())
                if (result == 0) {
                    applyEffect(newFile, Pitch(pitch))
                    withContext(Dispatchers.Main) {
                        showToast("success")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showToast("an error occured")
                    }
                }
            }
        }
    }

    private fun applyReverse() {
        performAsync {
            tmpFiles.lastOrNull()?.let { inFile ->
                val newFile = generateTmpFileFromCurrentDate("wav")
                val result = applyReverseJNI(inFile.absolutePath, newFile.absolutePath)
                if (result == 0) {
                    applyEffect(newFile, Reverse)
                    withContext(Dispatchers.Main) {
                        showToast("success")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showToast("an error occured")
                    }
                }
            }
        }
    }

    private suspend fun applyEffect(newFile: File, audioEffect: AudioEffect) = withContext(Dispatchers.Main) {
        tmpFiles.add(newFile)
        appliedEffects.add(audioEffect)
        val text = appliedEffects.reversed().joinToString(separator="\n") { it.description }
        binding.etAppliedEffects.setText(text)

        stopAndReleasePlayer()
    }

    private fun undoEffect() {
        val lastEffect = appliedEffects.lastOrNull() ?: return
        if (lastEffect is LoadFile) return
        val lastFile = tmpFiles.lastOrNull() ?: return

        stopAndReleasePlayer()

        FileUtils.delete(lastFile)
        tmpFiles.removeLast()
        appliedEffects.removeLast()
        val text = appliedEffects.reversed().joinToString(separator="\n") { it.description }
        binding.etAppliedEffects.setText(text)
    }

    private fun performAsync(block: suspend () -> Unit) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                setButtonsEnables(false)
            }
            withContext(Dispatchers.IO) {
                block.invoke()
            }
            withContext(Dispatchers.Main) {
                setButtonsEnables(true)
            }
        }
    }

    private fun playResult() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
            }

            tmpFiles.lastOrNull()?.let { lastFile ->
                mediaPlayer?.setDataSource(applicationContext, lastFile.toUri())
                mediaPlayer?.prepare()
                mediaPlayer?.setOnCompletionListener {
                    stopAndReleasePlayer()
                }
                binding.seekBar.max = mediaPlayer?.duration ?: 0
                mediaPlayer?.start()
            }
        } else {
            mediaPlayer?.start()
        }

        lifecycleScope.launch {
            while (mediaPlayer?.isPlaying == true) {
                delay(50L)
                withContext(Dispatchers.Main) {
                    binding.seekBar.progress = mediaPlayer?.currentPosition ?: 0
                }
            }
        }

        binding.btnPlay.visibility = View.GONE
        binding.btnPause.visibility = View.VISIBLE
    }

    private fun stopAndReleasePlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        binding.btnPlay.visibility = View.VISIBLE
        binding.btnPause.visibility = View.GONE
    }

    private fun pausePlayer() {
        mediaPlayer?.pause()

        binding.btnPlay.visibility = View.VISIBLE
        binding.btnPause.visibility = View.GONE
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    /**
     * A native method that is implemented by the 'soxtest' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    external fun convertAudioFileJNI(inPath: String, outPath: String): Int
    external fun applyTempoJNI(inPath: String, outPath: String, tempo: String): Int
    external fun applyPitchJNI(inPath: String, outPath: String, pitch: String): Int
    external fun applyReverseJNI(inPath: String, outPath: String): Int

    companion object {
        // Used to load the 'soxtest' library on application startup.
        init {
            System.loadLibrary("sox")
            System.loadLibrary("soxtest")
        }
    }
}