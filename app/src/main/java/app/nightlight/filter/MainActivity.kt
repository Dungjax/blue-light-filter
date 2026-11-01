package app.nightlight.filter

import android.Manifest
import android.app.Activity
import android.app.StatusBarManager
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: FilterPrefs

    private lateinit var masterSwitch: Switch
    private lateinit var permissionCard: View
    private lateinit var grantButton: Button
    private lateinit var warmthLabel: TextView
    private lateinit var warmthSeek: SeekBar
    private lateinit var dimLabel: TextView
    private lateinit var dimSeek: SeekBar
    private lateinit var presetGentle: Button
    private lateinit var presetNight: Button
    private lateinit var presetDeep: Button
    private lateinit var scheduleSwitch: Switch
    private lateinit var startButton: Button
    private lateinit var endButton: Button
    private lateinit var tileCard: View
    private lateinit var addTileButton: Button

    /** true khi đang cập nhật giao diện bằng code, để bỏ qua các listener. */
    private var rendering = false

    /** Người dùng đã bấm bật nhưng phải sang màn hình cấp quyền trước. */
    private var pendingEnable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = FilterPrefs(this)
        bindViews()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()

        if (pendingEnable) {
            pendingEnable = false
            if (Settings.canDrawOverlays(this)) prefs.enabled = true
        }
        if (prefs.enabled && !Settings.canDrawOverlays(this)) {
            prefs.enabled = false
        }
        // Nếu bộ lọc đang bật mà dịch vụ bị hệ thống tắt, khởi động lại.
        if (prefs.enabled) startFilterService()

        render()
    }

    // ---------------------------------------------------------------- khởi tạo

    private fun bindViews() {
        masterSwitch = findViewById(R.id.masterSwitch)
        permissionCard = findViewById(R.id.permissionCard)
        grantButton = findViewById(R.id.grantButton)
        warmthLabel = findViewById(R.id.warmthLabel)
        warmthSeek = findViewById(R.id.warmthSeek)
        dimLabel = findViewById(R.id.dimLabel)
        dimSeek = findViewById(R.id.dimSeek)
        presetGentle = findViewById(R.id.presetGentle)
        presetNight = findViewById(R.id.presetNight)
        presetDeep = findViewById(R.id.presetDeep)
        scheduleSwitch = findViewById(R.id.scheduleSwitch)
        startButton = findViewById(R.id.startButton)
        endButton = findViewById(R.id.endButton)
        tileCard = findViewById(R.id.tileCard)
        addTileButton = findViewById(R.id.addTileButton)
    }

    private fun setupListeners() {
        masterSwitch.setOnCheckedChangeListener { _, checked ->
            if (rendering) return@setOnCheckedChangeListener
            if (checked) turnOn() else turnOff()
        }

        grantButton.setOnClickListener { requestOverlayPermission() }

        if (Build.VERSION.SDK_INT >= 33) {
            addTileButton.setOnClickListener { requestAddTile() }
        } else {
            // Android 12 trở xuống không có hộp thoại thêm ô nhanh.
            tileCard.visibility = View.GONE
        }

        warmthSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                warmthLabel.text = getString(R.string.label_warmth, progress)
                if (fromUser) prefs.warmth = progress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        dimSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                dimLabel.text = getString(R.string.label_dim, progress)
                if (fromUser) prefs.dim = progress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        presetGentle.setOnClickListener { applyPreset(30, 10) }
        presetNight.setOnClickListener { applyPreset(55, 25) }
        presetDeep.setOnClickListener { applyPreset(80, 45) }

        scheduleSwitch.setOnCheckedChangeListener { _, checked ->
            if (rendering) return@setOnCheckedChangeListener
            prefs.scheduleEnabled = checked
            render()
        }

        startButton.setOnClickListener {
            pickTime(prefs.startMinutes) { minutes ->
                prefs.startMinutes = minutes
                render()
            }
        }
        endButton.setOnClickListener {
            pickTime(prefs.endMinutes) { minutes ->
                prefs.endMinutes = minutes
                render()
            }
        }
    }

    // ---------------------------------------------------------------- hành động

    private fun turnOn() {
        if (!Settings.canDrawOverlays(this)) {
            pendingEnable = true
            render() // đưa công tắc về trạng thái tắt
            requestOverlayPermission()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        }
        prefs.enabled = true
        startFilterService()
    }

    private fun turnOff() {
        pendingEnable = false
        prefs.enabled = false
        stopService(Intent(this, OverlayService::class.java))
    }

    private fun requestAddTile() {
        if (Build.VERSION.SDK_INT < 33) return
        val statusBar = getSystemService(StatusBarManager::class.java)
        statusBar.requestAddTileService(
            ComponentName(this, NightLightTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_notification),
            mainExecutor
        ) { result ->
            val message = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.toast_tile_added
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.toast_tile_already
                else -> R.string.toast_tile_manual
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun startFilterService() {
        try {
            startForegroundService(Intent(this, OverlayService::class.java))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.toast_service_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun requestOverlayPermission() {
        Toast.makeText(this, R.string.toast_grant_overlay, Toast.LENGTH_LONG).show()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    private fun applyPreset(warmth: Int, dim: Int) {
        prefs.warmth = warmth
        prefs.dim = dim
        render()
    }

    private fun pickTime(currentMinutes: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            this,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            currentMinutes / 60,
            currentMinutes % 60,
            true
        ).show()
    }

    // ---------------------------------------------------------------- giao diện

    private fun render() {
        rendering = true

        permissionCard.visibility =
            if (Settings.canDrawOverlays(this)) View.GONE else View.VISIBLE

        masterSwitch.isChecked = prefs.enabled

        warmthSeek.progress = prefs.warmth
        warmthLabel.text = getString(R.string.label_warmth, prefs.warmth)
        dimSeek.progress = prefs.dim
        dimLabel.text = getString(R.string.label_dim, prefs.dim)

        val scheduled = prefs.scheduleEnabled
        scheduleSwitch.isChecked = scheduled
        startButton.isEnabled = scheduled
        endButton.isEnabled = scheduled
        startButton.text =
            getString(R.string.schedule_start, FilterPrefs.formatTime(prefs.startMinutes))
        endButton.text =
            getString(R.string.schedule_end, FilterPrefs.formatTime(prefs.endMinutes))

        rendering = false
    }

    companion object {
        private const val REQ_NOTIFICATIONS = 1
    }
}
