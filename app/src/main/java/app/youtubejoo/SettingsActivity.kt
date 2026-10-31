package app.youtubejoo

import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.mozilla.geckoview.StorageController

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.settingsContainer, SettingsFragment()).commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            val ctx = requireContext()

            findPreference<EditTextPreference>("whitelist")?.setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                it.setSingleLine(false); it.minLines = 3
            }
            findPreference<Preference>("clear_history")?.setOnPreferenceClickListener {
                Store(ctx).clearHistory(); toast(R.string.pref_clear_history); true
            }
            findPreference<Preference>("clear_cache")?.setOnPreferenceClickListener {
                Gecko.runtime(ctx).storageController.clearData(StorageController.ClearFlags.ALL_CACHES)
                toast(R.string.pref_clear_cache); true
            }
            findPreference<Preference>("clear_cookies")?.setOnPreferenceClickListener {
                AlertDialog.Builder(ctx)
                    .setMessage(R.string.confirm_cookies)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        Gecko.runtime(ctx).storageController.clearData(
                            StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.SITE_DATA
                        )
                        toast(R.string.pref_clear_cookies)
                    }
                    .setNegativeButton(android.R.string.cancel, null).show()
                true
            }
            findPreference<Preference>("about")?.setOnPreferenceClickListener {
                AlertDialog.Builder(ctx).setTitle(R.string.app_name).setMessage(R.string.about_text)
                    .setPositiveButton(android.R.string.ok, null).show()
                true
            }
            findPreference<Preference>("version")?.summary = BuildConfig.VERSION_NAME
        }

        private fun toast(res: Int) = Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show()
    }
}
