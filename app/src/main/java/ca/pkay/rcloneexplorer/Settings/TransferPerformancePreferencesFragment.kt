package ca.pkay.rcloneexplorer.Settings

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import ca.pkay.rcloneexplorer.R

class TransferPerformancePreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_transfer_performance, rootKey)
        requireActivity().title = getString(R.string.pref_header_transfer_performance)

        val numericKeys = listOf(
            R.string.pref_key_transfers,
            R.string.pref_key_checkers,
            R.string.pref_key_multithread_streams,
            R.string.pref_key_multithread_cutoff,
            R.string.pref_key_buffer_size
        )
        for (keyRes in numericKeys) {
            val preference = findPreference<EditTextPreference>(getString(keyRes))
            preference?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
                editText.setSelection(editText.text.length)
            }
        }
    }
}
