package com.app.dailylog.ui.welcome

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import com.app.dailylog.R
import com.app.dailylog.databinding.WelcomeFragmentBinding
import com.app.dailylog.utils.NewLogFile

class WelcomeFragment(private val viewModel: WelcomeViewModel) : Fragment() {
    private lateinit var binding: WelcomeFragmentBinding

    companion object {
        fun newInstance(viewModel: WelcomeViewModel) = WelcomeFragment(viewModel)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.welcome_fragment, container, false)
        
        // Apply window insets to handle notch and navigation areas
        ViewCompat.setOnApplyWindowInsetsListener(view) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemBars.left,
                systemBars.top,    // avoids notch/status bar
                systemBars.right,
                systemBars.bottom  // avoids nav buttons
            )
            insets
        }
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = WelcomeFragmentBinding.bind(view)
        binding.createFileButton.setOnClickListener {
            val filePickerIntent = Intent.createChooser(NewLogFile.intent(), "Create file")
            filePickerActivityResult.launch(filePickerIntent)
        }
        binding.selectFileButton.setOnClickListener {
            val intent =
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                }
            val filePickerIntent = Intent.createChooser(intent, "Select a file")
            filePickerActivityResult.launch(filePickerIntent)
        }
    }

    private val filePickerActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val selectedFileUri = data.data
                if (selectedFileUri != null) {
                    viewModel.saveFilename(selectedFileUri.toString())
                    val contentResolver = requireContext().contentResolver
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(selectedFileUri, takeFlags)
                    // TODO("if we didn't get the permissions we needed, ask for permission or have the user select a different file")
                    viewModel.openLogView()
                }
            }
        }
    }

}