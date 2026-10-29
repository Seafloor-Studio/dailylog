package com.app.dailylog.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.app.dailylog.R
import com.app.dailylog.databinding.LogFileSettingsViewBinding
import com.app.dailylog.repository.Constants
import com.app.dailylog.utils.FileDisplayName
import com.app.dailylog.utils.FileNameTemplate
import com.app.dailylog.utils.NewLogFile
import com.google.android.material.chip.Chip

/**
 * Which file the log is written to: one file, or a new file each day.
 *
 * All of this used to sit above the shortcut list on Settings (#86); on its own screen it can
 * grow without costing the list any height.
 */
class LogFileSettingsFragment(
    private val viewModel: SettingsViewModel
) : Fragment() {

    private lateinit var binding: LogFileSettingsViewBinding

    companion object {
        fun newInstance(viewModel: SettingsViewModel) = LogFileSettingsFragment(viewModel)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.log_file_settings_view, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = LogFileSettingsViewBinding.bind(view)
        applySettingsInsets(view, binding.logFileToolbar)
        useDarkStatusBarIcons(requireActivity().window)
        binding.logFileToolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.selectFileButton.setOnClickListener { selectFile() }
        binding.newFileButton.setOnClickListener { createFile() }
        renderFileModeSection()
    }

    override fun onResume() {
        super.onResume()
        useDarkStatusBarIcons(requireActivity().window)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        restoreThemeStatusBarIcons(requireActivity().window)
    }

    private fun renderFileModeSection() {
        // Date templates need java.time.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            binding.fileModeToggle.visibility = View.GONE
            renderOneFileRow()
            return
        }
        binding.fileNameTemplate.setText(viewModel.getFileNameTemplate())
        renderFileMode()
        binding.fileModeToggle.addOnButtonCheckedListener { _, buttonId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            if (buttonId == R.id.oneFileButton) {
                viewModel.useOneFile()
            } else if (!viewModel.useDatedFiles()) {
                // Rendered again when the picker returns; a cancelled pick switches back to one file.
                selectLogFolder()
                return@addOnButtonCheckedListener
            }
            renderFileMode()
        }
        binding.chooseFolderButton.setOnClickListener { selectLogFolder() }
        binding.fileNameTemplate.doAfterTextChanged { renderTemplate() }
        val orderNames = listOf(
            R.string.date_order_ymd,
            R.string.date_order_dmy,
            R.string.date_order_mdy,
            R.string.date_order_year_folder,
        )
        for ((datePart, orderName) in FileNameTemplate.PRESET_DATE_PARTS.zip(orderNames)) {
            val chip = Chip(requireContext())
            chip.text = FileNameTemplate.resolve(datePart)
            chip.contentDescription =
                getString(R.string.date_order_preset, getString(orderName), chip.text)
            chip.setOnClickListener {
                val template = binding.fileNameTemplate.text.toString()
                binding.fileNameTemplate.setText(FileNameTemplate.withDatePart(template, datePart))
            }
            binding.templatePresets.addView(chip)
        }
    }

    private fun renderFileMode() {
        val dated = viewModel.isDatedMode()
        binding.fileModeToggle.check(if (dated) R.id.datedFileButton else R.id.oneFileButton)
        binding.fileRow.visibility = if (dated) View.GONE else View.VISIBLE
        binding.datedFileGroup.visibility = if (dated) View.VISIBLE else View.GONE
        binding.folderName.text =
            viewModel.getLogFolderName() ?: getString(R.string.no_folder_chosen)
        renderOneFileRow()
        renderTemplate()
    }

    private fun renderOneFileRow() {
        binding.fileName.text = FileDisplayName.of(requireContext(), viewModel.getFilename())
    }

    private fun renderTemplate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val template = binding.fileNameTemplate.text.toString()
        val error = viewModel.setFileNameTemplate(template)
        binding.fileNameTemplateLayout.error = error
        binding.todaysFilePreview.text = if (error == null) {
            val path = FileNameTemplate.resolve(template)
            getString(
                R.string.todays_file,
                listOfNotNull(viewModel.getLogFolderName(), path).joinToString("/")
            )
        } else {
            ""
        }
    }

    private fun selectFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // Not text/*: many providers report .md as application/octet-stream, greying it out.
            type = "*/*"
        }
        selectFileLauncher.launch(Intent.createChooser(intent, "Select a file"))
    }

    private fun createFile() {
        createFileLauncher.launch(Intent.createChooser(NewLogFile.intent(), getString(R.string.new_file)))
    }

    /**
     * Makes [uri] the log file, once the app holds a grant that outlives this process. Returns
     * false, leaving the current file in place, when the provider won't give one.
     */
    private fun useFile(uri: Uri): Boolean {
        val takeFlags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val contentResolver = requireContext().contentResolver
        try {
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), R.string.file_permission_denied, Toast.LENGTH_LONG).show()
            return false
        }
        // The system caps how many grants an app keeps, so let go of the file this replaces.
        val previous = viewModel.getFilename()
        if (previous != Constants.NO_FILE_SELECTED && previous != uri.toString()) {
            try {
                contentResolver.releasePersistableUriPermission(Uri.parse(previous), takeFlags)
            } catch (e: SecurityException) {
                // Already gone.
            }
        }
        viewModel.saveFilename(uri.toString())
        renderOneFileRow()
        return true
    }

    private fun selectLogFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        selectLogFolderLauncher.launch(intent)
    }

    private val selectFileLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != AppCompatActivity.RESULT_OK) return@registerForActivityResult
            val selectedFileUri = result.data?.data ?: return@registerForActivityResult
            useFile(selectedFileUri)
        }

    // The log picks up the switch, and resets the cursor, through openCurrentFile when it reopens.
    private val createFileLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != AppCompatActivity.RESULT_OK) return@registerForActivityResult
            val createdFileUri = result.data?.data ?: return@registerForActivityResult
            if (useFile(createdFileUri)) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.now_writing_to, binding.fileName.text),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val selectLogFolderLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val folder = result.data?.data
            if (result.resultCode == AppCompatActivity.RESULT_OK && folder != null) {
                val flags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                val contentResolver = requireContext().contentResolver
                try {
                    contentResolver.takePersistableUriPermission(folder, flags)
                    // The system caps how many grants an app keeps, so let go of the folder this replaces.
                    viewModel.getLogFolder()?.takeIf { it != folder }?.let {
                        try {
                            contentResolver.releasePersistableUriPermission(it, flags)
                        } catch (e: SecurityException) {
                            // Already gone.
                        }
                    }
                    viewModel.chooseLogFolder(folder)
                } catch (e: SecurityException) {
                    Toast.makeText(
                        requireContext(), R.string.log_folder_missing, Toast.LENGTH_LONG
                    ).show()
                }
            }
            renderFileMode()
        }
}
