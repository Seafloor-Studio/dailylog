package com.app.dailylog.ui.settings

import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.core.widget.doAfterTextChanged
import com.app.dailylog.R
import com.app.dailylog.databinding.BulkAddRowBinding
import com.app.dailylog.databinding.BulkAddShortcutsBinding
import com.app.dailylog.repository.ShortcutRow
import com.google.android.material.textfield.TextInputLayout

class BulkAddShortcutsDialogFragment(viewModel: ShortcutDialogViewModel) : ShortcutDialogFragment(viewModel)  {
    private val listener get() = parentFragment as BulkAddListener

    lateinit var binding: BulkAddShortcutsBinding
    interface BulkAddListener {
        fun onBulkAddShortcuts(rows: List<ShortcutRow>)
    }

    companion object {
        fun newInstance(viewModel: ShortcutDialogViewModel) = BulkAddShortcutsDialogFragment(viewModel)
        private const val NAMES = "names"
        private const val TEXTS = "texts"
        private const val CURSORS = "cursors"
        private const val CURSORS_SET = "cursorsSet"
        private const val SHOW_MISSING = "showMissing"
    }

    private val rows = mutableListOf<BulkAddRowBinding>()

    // Rows that were on screen when Add was tapped. Only their empty fields count as mistakes,
    // so a row still being typed into isn't flagged before it's finished.
    private val flagged = mutableSetOf<BulkAddRowBinding>()

    // Rows whose cursor position the user has typed. The others keep it at the end of the text.
    private val cursorSet = mutableSetOf<BulkAddRowBinding>()

    // True while the dialog itself writes a cursor position, so that isn't taken as the user's.
    private var followingText = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bulk_add_shortcuts, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // The dialog grows with each row, so it has to resize for the keyboard to keep the
        // focused row in view.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            dialog?.window?.setDecorFitsSystemWindows(true)
        } else {
            // You'll get a build warning that this is deprecated. Ignore it, we're only calling this
            // if it's not deprecated (Version < 30)
            dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        super.onViewCreated(view, savedInstanceState)
        binding = BulkAddShortcutsBinding.bind(view)
        saveButton = binding.btnSaveBulkShortcut

        val names = savedInstanceState?.getStringArrayList(NAMES)
        val texts = savedInstanceState?.getStringArrayList(TEXTS)
        val cursors = savedInstanceState?.getStringArrayList(CURSORS)
        val cursorsSet = savedInstanceState?.getBooleanArray(CURSORS_SET)
        if (names != null && texts != null && cursors != null && cursorsSet != null &&
            setOf(names.size, texts.size, cursors.size, cursorsSet.size).size == 1 && names.isNotEmpty()) {
            names.indices.forEach { addRow(names[it], texts[it], cursors[it], cursorsSet[it]) }
            if (savedInstanceState?.getBoolean(SHOW_MISSING) == true) flagged += rows
        } else {
            addRow()
        }

        binding.btnAddRow.setOnClickListener { addRow(focus = true) }
        binding.btnCancelBulkShortcut.setOnClickListener { dismiss() }
        binding.btnSaveBulkShortcut.setOnClickListener {
            flagged += rows
            val filled = rows
                .filter { it.name().isNotEmpty() && it.text().isNotEmpty() }
                .map { ShortcutRow(it.name(), it.text(), it.cursorIndex()) }
            if (refresh() && filled.isNotEmpty()) {
                listener.onBulkAddShortcuts(filled)
                dismiss()
            }
        }
        refresh()
    }

    override fun onStart() {
        super.onStart()
        // The rows share their width by weight, which a wrap-content dialog would squeeze.
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(NAMES, ArrayList(rows.map { it.name() }))
        outState.putStringArrayList(TEXTS, ArrayList(rows.map { it.text() }))
        outState.putStringArrayList(CURSORS, ArrayList(rows.map { it.rowCursor.text.toString() }))
        outState.putBooleanArray(CURSORS_SET, rows.map { it in cursorSet }.toBooleanArray())
        outState.putBoolean(SHOW_MISSING, flagged.isNotEmpty())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rows.clear()
        flagged.clear()
        cursorSet.clear()
    }

    // A keyboard's suggestion often leaves a space after a word, which would let "mood " pass
    // as a new name beside "mood".
    private fun BulkAddRowBinding.name() = rowLabel.text.toString().trim()

    private fun BulkAddRowBinding.text() = rowText.text.toString()

    // An empty position means the end of the text. A number too long for an Int is past any
    // text's end, so it's refused like one.
    private fun BulkAddRowBinding.cursorIndex(): Int {
        val typed = rowCursor.text.toString()
        return if (typed.isEmpty()) text().length else typed.toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun BulkAddRowBinding.followText() {
        followingText = true
        rowCursor.setText(if (text().isEmpty()) "" else text().length.toString())
        followingText = false
    }

    private fun addRow(
        name: String = "", text: String = "", cursor: String? = null, cursorTyped: Boolean = false,
        focus: Boolean = false
    ) {
        val row = BulkAddRowBinding.inflate(layoutInflater, binding.bulkRows, true)
        // Every row's fields share IDs, so Android can't restore them; onSaveInstanceState does.
        row.rowLabel.isSaveEnabled = false
        row.rowText.isSaveEnabled = false
        row.rowCursor.isSaveEnabled = false
        // The text is monospace, but its label and errors should match the name field's.
        row.rowTextLayout.typeface = Typeface.DEFAULT
        row.rowLabel.setText(name)
        row.rowText.setText(text)
        if (cursor != null) row.rowCursor.setText(cursor) else row.followText()
        if (cursorTyped) cursorSet += row
        row.rowLabel.doAfterTextChanged { refresh() }
        row.rowText.doAfterTextChanged {
            if (row !in cursorSet) row.followText()
            refresh()
        }
        row.rowCursor.doAfterTextChanged {
            if (!followingText) {
                // Clearing the position hands it back to the end of the text.
                if (it.isNullOrEmpty()) cursorSet -= row else cursorSet += row
            }
            refresh()
        }
        row.removeRowButton.setOnClickListener { removeRow(row) }
        rows += row
        if (focus) row.rowLabel.requestFocus()
        refresh()
    }

    private fun removeRow(row: BulkAddRowBinding) {
        binding.bulkRows.removeView(row.root)
        rows -= row
        flagged -= row
        cursorSet -= row
        if (rows.isEmpty()) addRow(focus = true)
        refresh()
    }

    /**
     * Shows each row's mistakes and sets up the Add button. Add stays enabled for a half-filled
     * row so that tapping it can say what's missing; returns false if anything is wrong.
     */
    private fun refresh(): Boolean {
        val seen = mutableSetOf<String>()
        var complete = 0
        var started = false
        var mistakes = false
        rows.forEachIndexed { index, row ->
            val name = row.name()
            val text = row.text()
            val blank = name.isEmpty() && text.isEmpty()
            val showMissing = row in flagged && !blank
            val nameError = when {
                name.isEmpty() -> if (showMissing) R.string.emptyNameError else null
                !viewModel.isLabelValid(name) -> R.string.duplicateNameError
                !seen.add(name) -> R.string.duplicateRowError
                else -> null
            }
            val textError = if (showMissing && !viewModel.isTextValid(text)) R.string.emptyTextError else null
            val cursorError = if (!blank && row.cursorIndex() > text.length) getString(R.string.cursorPastEndError, text.length) else null
            row.rowLabelLayout.setErrorIfChanged(nameError?.let { getString(it) })
            row.rowTextLayout.setErrorIfChanged(textError?.let { getString(it) })
            row.rowCursorLayout.setErrorIfChanged(cursorError)
            row.removeRowButton.contentDescription = getString(R.string.bulk_add_remove_row, index + 1)
            if (nameError != null || textError != null || cursorError != null) mistakes = true
            if (name.isNotEmpty() && text.isNotEmpty()) complete++
            if (!blank) started = true
        }
        binding.btnSaveBulkShortcut.text =
            if (complete > 0) getString(R.string.bulk_add_count, complete) else getString(R.string.bulk_add_none)
        val enabled = started && !mistakes
        binding.btnSaveBulkShortcut.isEnabled = enabled
        // SecondaryButton's tint has no disabled colour, so fade it to show it can't be tapped.
        binding.btnSaveBulkShortcut.alpha = if (enabled) 1f else 0.38f
        return !mistakes
    }

    // Setting the same error again would make TalkBack announce it on every keystroke.
    private fun TextInputLayout.setErrorIfChanged(message: String?) {
        if (error?.toString() != message) error = message
    }
}
