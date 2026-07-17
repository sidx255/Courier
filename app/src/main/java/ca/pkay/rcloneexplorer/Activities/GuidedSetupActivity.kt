package ca.pkay.rcloneexplorer.Activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import ca.pkay.rcloneexplorer.FilePicker
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.guided.GuidedBackupManager
import ca.pkay.rcloneexplorer.guided.NasDiscoveryManager
import ca.pkay.rcloneexplorer.util.ActivityHelper
import ca.pkay.rcloneexplorer.util.PermissionManager
import ca.pkay.rcloneexplorer.util.RcloneErrorMapper
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class GuidedSetupActivity : AppCompatActivity(), NasDiscoveryManager.Listener {

    private lateinit var manager: GuidedBackupManager
    private lateinit var pages: List<View>
    private lateinit var progress: LinearProgressIndicator
    private lateinit var backButton: MaterialButton
    private lateinit var nextButton: MaterialButton
    private lateinit var hostInput: TextInputEditText
    private lateinit var shareInput: TextInputEditText
    private lateinit var portInput: TextInputEditText
    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var domainInput: TextInputEditText
    private lateinit var connectProgress: CircularProgressIndicator
    private lateinit var connectStatus: TextView
    private lateinit var createProgress: CircularProgressIndicator
    private lateinit var createStatus: TextView
    private lateinit var scanProgress: LinearProgressIndicator
    private lateinit var scanStatus: TextView
    private lateinit var hostsContainer: RadioGroup
    private lateinit var categoryPhotos: MaterialCheckBox
    private lateinit var categoryWhatsApp: MaterialCheckBox
    private lateinit var categoryDocuments: MaterialCheckBox
    private lateinit var categoryDownloads: MaterialCheckBox
    private lateinit var customFolders: ChipGroup
    private lateinit var categoryError: TextView
    private lateinit var deviceNameInput: TextInputEditText
    private lateinit var connectAction: MaterialButton

    private val executor = Executors.newSingleThreadExecutor()
    private val customPaths = mutableListOf<String>()
    private var discovery: NasDiscoveryManager? = null
    private var pendingConnection: GuidedBackupManager.NasConnection? = null
    private var step = STEP_WELCOME
    private var mode = MODE_SETUP
    private var busy = false
    private val customFolderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val path = result.data?.getStringExtra(FilePicker.FILE_PICKER_RESULT)?.trim().orEmpty()
        if (path.isNotEmpty() && path !in customPaths) {
            customPaths += path
            renderCustomFolders()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityHelper.applyTheme(this)
        setContentView(R.layout.activity_guided_setup)
        manager = GuidedBackupManager(this)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_SETUP

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.guided_toolbar)
            .setNavigationOnClickListener { handleBack() }
        progress = findViewById(R.id.guided_progress)
        backButton = findViewById(R.id.guided_back_button)
        nextButton = findViewById(R.id.guided_next_button)
        hostInput = findViewById(R.id.guided_host)
        shareInput = findViewById(R.id.guided_share)
        portInput = findViewById(R.id.guided_port)
        usernameInput = findViewById(R.id.guided_username)
        passwordInput = findViewById(R.id.guided_password)
        domainInput = findViewById(R.id.guided_domain)
        connectProgress = findViewById(R.id.guided_connect_progress)
        connectStatus = findViewById(R.id.guided_connect_status)
        createProgress = findViewById(R.id.guided_create_progress)
        createStatus = findViewById(R.id.guided_create_status)
        scanProgress = findViewById(R.id.guided_scan_progress)
        scanStatus = findViewById(R.id.guided_scan_status)
        hostsContainer = findViewById(R.id.guided_hosts_container)
        categoryPhotos = findViewById(R.id.guided_category_photos)
        categoryWhatsApp = findViewById(R.id.guided_category_whatsapp)
        categoryDocuments = findViewById(R.id.guided_category_documents)
        categoryDownloads = findViewById(R.id.guided_category_downloads)
        customFolders = findViewById(R.id.guided_custom_folders)
        categoryError = findViewById(R.id.guided_category_error)
        deviceNameInput = findViewById(R.id.guided_device_name)
        connectAction = findViewById(R.id.guided_connect_action)
        pages = listOf(
            findViewById(R.id.guided_page_welcome),
            findViewById(R.id.guided_page_find),
            findViewById(R.id.guided_page_connect),
            findViewById(R.id.guided_page_categories),
            findViewById(R.id.guided_page_confirm),
            findViewById(R.id.guided_page_done)
        )

        backButton.setOnClickListener { goBack() }
        nextButton.setOnClickListener { goNext() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })
        findViewById<MaterialButton>(R.id.guided_scan_again).setOnClickListener { restartDiscovery() }
        findViewById<MaterialButton>(R.id.guided_add_custom).setOnClickListener { openFolderPicker() }

        initializeSelections(savedInstanceState)
        step = savedInstanceState?.getInt(STATE_STEP) ?: startStep()
        if (savedInstanceState != null && mode != MODE_CATEGORIES && step > STEP_CONNECT) {
            step = STEP_CONNECT
        }
        showStep(step)
    }

    private fun initializeSelections(savedInstanceState: Bundle?) {
        val existing = manager.getSelectedCategoryKeys()
        if (existing.isNotEmpty()) {
            categoryPhotos.isChecked = GuidedBackupManager.CATEGORY_PHOTOS in existing
            categoryWhatsApp.isChecked = GuidedBackupManager.CATEGORY_WHATSAPP in existing
            categoryDocuments.isChecked = GuidedBackupManager.CATEGORY_DOCUMENTS in existing
            categoryDownloads.isChecked = GuidedBackupManager.CATEGORY_DOWNLOADS in existing
        } else {
            configureAvailability(categoryPhotos, GuidedBackupManager.CATEGORY_PHOTOS)
            configureAvailability(categoryWhatsApp, GuidedBackupManager.CATEGORY_WHATSAPP)
            configureAvailability(categoryDocuments, GuidedBackupManager.CATEGORY_DOCUMENTS)
            configureAvailability(categoryDownloads, GuidedBackupManager.CATEGORY_DOWNLOADS)
        }
        customPaths.clear()
        customPaths += savedInstanceState?.getStringArrayList(STATE_CUSTOM_PATHS)
            ?: manager.getCustomPaths()
        renderCustomFolders()
        deviceNameInput.setText(manager.getDeviceName())
        manager.getConnectionSummary()?.let { summary ->
            hostInput.setText(summary.host)
            shareInput.setText(summary.share)
            portInput.setText(String.format(Locale.ROOT, "%d", summary.port))
        }
    }

    private fun configureAvailability(checkBox: MaterialCheckBox, category: String) {
        val available = manager.isCategoryAvailable(category)
        checkBox.isEnabled = available
        checkBox.isChecked = available
    }

    private fun startStep(): Int {
        return when (mode) {
            MODE_CATEGORIES -> STEP_CATEGORIES
            MODE_CONNECTION -> STEP_FIND
            else -> STEP_WELCOME
        }
    }

    private fun showStep(newStep: Int) {
        step = newStep.coerceIn(STEP_WELCOME, STEP_DONE)
        pages.forEachIndexed { index, page -> page.visibility = if (index == step) View.VISIBLE else View.GONE }
        progress.setProgressCompat(step + 1, true)
        backButton.visibility = if (step > startStep() && step < STEP_DONE) View.VISIBLE else View.INVISIBLE
        nextButton.text = when (step) {
            STEP_WELCOME -> getString(R.string.guided_welcome_action)
            STEP_CONFIRM -> getString(R.string.guided_create_backup)
            STEP_DONE -> getString(R.string.guided_done_action)
            else -> getString(R.string.guided_next)
        }
        if (step == STEP_FIND) startDiscovery()
        if (step == STEP_CONFIRM) updateConfirmation()
    }

    private fun goNext() {
        if (busy) return
        when (step) {
            STEP_WELCOME -> showStep(STEP_FIND)
            STEP_FIND -> {
                if (hostInput.text.isNullOrBlank()) {
                    hostInput.error = getString(R.string.guided_nas_address)
                    return
                }
                discovery?.stop()
                discovery = null
                showStep(STEP_CONNECT)
            }
            STEP_CONNECT -> validateConnection()
            STEP_CATEGORIES -> {
                if (deviceNameInput.text.isNullOrBlank()) {
                    deviceNameInput.error = getString(R.string.guided_device_required)
                    return
                }
                if (selectedCategories().isEmpty() && customPaths.isEmpty()) {
                    categoryError.text = getString(R.string.restore_choose_one)
                    categoryError.visibility = View.VISIBLE
                    return
                }
                categoryError.visibility = View.GONE
                showStep(STEP_CONFIRM)
            }
            STEP_CONFIRM -> provision()
            STEP_DONE -> openHome()
        }
    }

    private fun goBack() {
        if (busy) return
        if (step <= startStep()) {
            finish()
        } else {
            showStep(step - 1)
        }
    }

    private fun handleBack() = goBack()

    private fun validateConnection() {
        val connection = connectionFromFields()
        setBusy(true)
        connectProgress.visibility = View.VISIBLE
        connectAction.visibility = View.GONE
        connectStatus.setTextColor(MaterialColors.getColor(connectStatus, com.google.android.material.R.attr.colorOnSurfaceVariant))
        connectStatus.text = getString(R.string.guided_testing_connection)
        executor.execute {
            val result = runCatching { manager.validateConnection(connection) }
                .getOrElse {
                    GuidedBackupManager.OperationResult.Error(getString(R.string.guided_connection_failed))
                }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                connectProgress.visibility = View.GONE
                setBusy(false)
                when (result) {
                    is GuidedBackupManager.OperationResult.Success -> {
                        pendingConnection = connection
                        connectStatus.setTextColor(MaterialColors.getColor(connectStatus, com.google.android.material.R.attr.colorPrimary))
                        connectStatus.text = getString(R.string.guided_connection_ready)
                        connectAction.visibility = View.GONE
                        showStep(STEP_CATEGORIES)
                    }
                    is GuidedBackupManager.OperationResult.Error -> {
                        connectStatus.setTextColor(MaterialColors.getColor(connectStatus, com.google.android.material.R.attr.colorError))
                        connectStatus.text = result.message.ifBlank {
                            getString(R.string.guided_connection_failed)
                        }
                        if (result.action == RcloneErrorMapper.Action.NONE || result.actionLabel == 0) {
                            connectAction.visibility = View.GONE
                        } else {
                            connectAction.setText(result.actionLabel)
                            connectAction.visibility = View.VISIBLE
                            connectAction.setOnClickListener {
                                when (result.action) {
                                    RcloneErrorMapper.Action.RETRY -> validateConnection()
                                    RcloneErrorMapper.Action.EDIT_CONNECTION -> usernameInput.requestFocus()
                                    RcloneErrorMapper.Action.CHECK_NETWORK -> startActivity(
                                        Intent(Settings.ACTION_WIRELESS_SETTINGS)
                                    )
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun provision() {
        val permissions = PermissionManager(this)
        if (!permissions.grantedAlarms()) {
            permissions.requestAlarms()
            createStatus.setTextColor(MaterialColors.getColor(createStatus, com.google.android.material.R.attr.colorError))
            createStatus.text = getString(R.string.guided_alarm_required)
            return
        }
        val connection = if (mode == MODE_CATEGORIES) null else pendingConnection
        if (mode != MODE_CATEGORIES && connection == null) {
            showStep(STEP_CONNECT)
            return
        }
        setBusy(true)
        createProgress.visibility = View.VISIBLE
        createStatus.setTextColor(MaterialColors.getColor(createStatus, com.google.android.material.R.attr.colorOnSurfaceVariant))
        createStatus.text = getString(R.string.guided_creating_backup)
        executor.execute {
            val result = manager.provision(
                connection,
                selectedCategories(),
                customPaths,
                deviceNameInput.text?.toString().orEmpty()
            )
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                createProgress.visibility = View.GONE
                setBusy(false)
                when (result) {
                    is GuidedBackupManager.OperationResult.Success -> showStep(STEP_DONE)
                    is GuidedBackupManager.OperationResult.Error -> {
                        createStatus.setTextColor(MaterialColors.getColor(createStatus, com.google.android.material.R.attr.colorError))
                        createStatus.text = result.message
                    }
                }
            }
        }
    }

    private fun connectionFromFields(): GuidedBackupManager.NasConnection {
        var host = hostInput.text?.toString()?.trim().orEmpty()
        host = host.removePrefix("smb://").trimEnd('/')
        return GuidedBackupManager.NasConnection(
            host,
            portInput.text?.toString()?.toIntOrNull() ?: -1,
            shareInput.text?.toString()?.trim()?.trim('/').orEmpty(),
            usernameInput.text?.toString()?.trim().orEmpty(),
            passwordInput.text?.toString().orEmpty(),
            domainInput.text?.toString()?.trim().orEmpty()
        )
    }

    private fun selectedCategories(): Set<String> {
        return buildSet {
            if (categoryPhotos.isChecked) add(GuidedBackupManager.CATEGORY_PHOTOS)
            if (categoryWhatsApp.isChecked) add(GuidedBackupManager.CATEGORY_WHATSAPP)
            if (categoryDocuments.isChecked) add(GuidedBackupManager.CATEGORY_DOCUMENTS)
            if (categoryDownloads.isChecked) add(GuidedBackupManager.CATEGORY_DOWNLOADS)
        }
    }

    private fun updateConfirmation() {
        val summary = if (mode == MODE_CATEGORIES) manager.getConnectionSummary() else null
        val storage = pendingConnection?.let { "${it.host}:${it.port} / ${it.share}" }
            ?: summary?.let { "${it.host}:${it.port} / ${it.share}" }
            ?: "NAS"
        findViewById<TextView>(R.id.guided_confirm_storage_value).text = storage
        findViewById<TextView>(R.id.guided_confirm_device_value).text =
            deviceNameInput.text?.toString()?.trim().orEmpty()
        val names = mutableListOf<String>()
        if (categoryPhotos.isChecked) names += getString(R.string.guided_category_photos)
        if (categoryWhatsApp.isChecked) names += getString(R.string.guided_category_whatsapp)
        if (categoryDocuments.isChecked) names += getString(R.string.guided_category_documents)
        if (categoryDownloads.isChecked) names += getString(R.string.guided_category_downloads)
        names += customPaths.map { File(it).name.ifBlank { it } }
        findViewById<TextView>(R.id.guided_confirm_folders_value).text = names.joinToString("\n")
    }

    private fun setBusy(value: Boolean) {
        busy = value
        backButton.isEnabled = !value
        nextButton.isEnabled = !value
    }

    private fun startDiscovery() {
        if (discovery != null) return
        hostsContainer.removeAllViews()
        scanProgress.visibility = View.VISIBLE
        scanStatus.text = getString(R.string.guided_scanning)
        discovery = NasDiscoveryManager(this, this).also(NasDiscoveryManager::start)
    }

    private fun restartDiscovery() {
        discovery?.stop()
        discovery = null
        startDiscovery()
    }

    override fun onHostFound(host: NasDiscoveryManager.NasHost) {
        val radio = MaterialRadioButton(this).apply {
            id = View.generateViewId()
            text = if (host.name == host.address) {
                "${host.address}:${host.port}"
            } else {
                "${host.name}\n${host.address}:${host.port}"
            }
            minHeight = resources.getDimensionPixelSize(R.dimen.settingsIconSize)
            setOnClickListener {
                hostInput.setText(host.address)
                portInput.setText(String.format(Locale.ROOT, "%d", host.port))
            }
        }
        hostsContainer.addView(radio)
    }

    override fun onScanStateChanged(scanning: Boolean) {
        scanProgress.visibility = if (scanning) View.VISIBLE else View.GONE
        if (!scanning && hostsContainer.childCount == 0) {
            scanStatus.text = getString(R.string.guided_no_hosts)
        }
    }

    override fun onScanUnavailable(message: String) {
        scanStatus.text = message
    }

    private fun openFolderPicker() {
        val intent = Intent(this, FilePicker::class.java)
            .putExtra(FilePicker.FILE_PICKER_PICK_DESTINATION_TYPE, true)
        customFolderLauncher.launch(intent)
    }

    private fun renderCustomFolders() {
        customFolders.removeAllViews()
        customPaths.toList().forEach { path ->
            val chip = Chip(this).apply {
                text = File(path).name.ifBlank { path }
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    customPaths.remove(path)
                    renderCustomFolders()
                }
            }
            customFolders.addView(chip)
        }
    }

    private fun openHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_STEP, step)
        outState.putStringArrayList(STATE_CUSTOM_PATHS, ArrayList(customPaths))
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        discovery?.stop()
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MODE = "guided_setup_mode"
        const val MODE_SETUP = "setup"
        const val MODE_CATEGORIES = "categories"
        const val MODE_CONNECTION = "connection"
        private const val STEP_WELCOME = 0
        private const val STEP_FIND = 1
        private const val STEP_CONNECT = 2
        private const val STEP_CATEGORIES = 3
        private const val STEP_CONFIRM = 4
        private const val STEP_DONE = 5
        private const val STATE_STEP = "guided_step"
        private const val STATE_CUSTOM_PATHS = "guided_custom_paths"
    }
}