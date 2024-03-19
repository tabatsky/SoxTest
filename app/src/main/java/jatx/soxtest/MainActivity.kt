package jatx.soxtest

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import jatx.soxtest.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
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
           tryLoadAudioFileFromUri(theUri)
        }
    }

    private val saveAudioFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { theUri ->
            trySaveAudioFileToUri(theUri)
        }
    }

    private val tmpFiles = arrayListOf<File>()

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

        binding.btnApplyReverse.setOnClickListener {
            applyReverse()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun cleanProject() {
        tmpFiles.clear()
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
        binding.btnApplyReverse.isEnabled = enabled
    }

    private fun tryOpenAudioFile() {
        openAudioFileLauncher.launch(arrayOf("*/*"))
    }

    private fun trySaveAudioFile(extension: String) {
        tmpFiles.lastOrNull()?.let { lastFile ->
            outFile = generateTmpFileFromCurrentDate(extension)
        }
        currentProjectFile?.let {
            val fileName = "${it.nameWithoutExtension}.${extension}"
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

    private fun convertLastFileToOutFile() {
        tmpFiles.lastOrNull()?.let { lastFile ->
            outFile?.let { theOutFile ->
                convertAudioFileJNI(lastFile.absolutePath, theOutFile.absolutePath)
            }
        }
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
                val tmpFile = generateTmpFileFromCurrentDate("wav")
                convertAudioFileJNI(origPath, tmpFile.absolutePath)
                tmpFiles.add(tmpFile)
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
            convertLastFileToOutFile()
            copyOutFileToUri(uri)
            cleanOutFile()
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
                applyTempoJNI(inFile.absolutePath, newFile.absolutePath, tempo.toString())
                tmpFiles.add(newFile)
            }
        }
    }

    private fun applyReverse() {
        performAsync {
            tmpFiles.lastOrNull()?.let { inFile ->
                val newFile = generateTmpFileFromCurrentDate("wav")
                applyReverseJNI(inFile.absolutePath, newFile.absolutePath)
                tmpFiles.add(newFile)
            }
        }
    }

    private fun performAsync(block: () -> Unit) {
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

    /**
     * A native method that is implemented by the 'soxtest' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    external fun convertAudioFileJNI(inPath: String, outPath: String)

    external fun applyTempoJNI(inPath: String, outPath: String, tempo: String)

    external fun applyReverseJNI(inPath: String, outPath: String)

    companion object {
        // Used to load the 'soxtest' library on application startup.
        init {
            System.loadLibrary("sox")
            System.loadLibrary("soxtest")
        }
    }
}