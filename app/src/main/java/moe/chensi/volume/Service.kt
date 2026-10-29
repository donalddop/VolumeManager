package moe.chensi.volume

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import moe.chensi.volume.compose.CompactVolumePanel
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.merge
import kotlin.math.roundToInt
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import moe.chensi.volume.compose.AppVolumeList
import moe.chensi.volume.compose.SystemVolumePanel
import moe.chensi.volume.compose.VolumeChangeObserver
import moe.chensi.volume.system.ActivityTaskManagerProxy
import moe.chensi.volume.ui.theme.VolumeManagerTheme
import org.joor.Reflect
import java.util.Objects

@SuppressLint("AccessibilityPolicy")
class Service : AccessibilityService() {
    companion object {
        const val ACTION_SHOW_VIEW = "moe.chensi.volume.ACTION_SHOW_VIEW"
        /** Boolean extra: show the panel as a non-interactive live preview (settings screen). */
        const val EXTRA_PREVIEW = "preview"

        private const val TAG = "VolumeManager.Service"

        private const val NOTIFICATION_CHANNEL_ID = "service"
        private const val NOTIFICATION_ID = 1

    }

    private val windowManager: WindowManager by lazy {
        Objects.requireNonNull(
            getSystemService(
                WindowManager::class.java
            )!!
        )
    }
    private lateinit var manager: Manager
    private lateinit var app: MyApplication
    private var preferenceScope: CoroutineScope? = null

    private val handler = object : Handler(Looper.getMainLooper()) {
        fun hideView() {
            if (viewVisible) {
                Log.i(TAG, "animate out")
                animateAlpha(layoutParams.alpha, 0f, animationMs) {
                    if (!viewVisible) {
                        Log.i(TAG, "remove view")
                        view!!.background = null
                        lifecycle?.currentState = Lifecycle.State.DESTROYED
                        windowManager.removeView(view)
                        view = null
                    }
                }
                viewVisible = false
                setPreviewMode(false)
            }
        }

        private val hideViewRunnable = Runnable(::hideView)

        fun startIdleTimer() {
            removeCallbacks(hideViewRunnable)
            postDelayed(hideViewRunnable, app.settings.panelTimeoutMs.value.toLong())
        }

        private var repeatAdjustVolumeDirection = 0
        private val repeatAdjustVolumeRunnable: Runnable = Runnable {
            adjustVolume()
            postDelayed(repeatAdjustVolumeRunnable, app.settings.keysRepeatIntervalMs.value.toLong())
        }

        private fun adjustVolume() {
            if (app.settings.keysAlwaysMedia.value) {
                manager.audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC, repeatAdjustVolumeDirection, 0
                )
            } else {
                manager.audioManager.adjustSuggestedStreamVolume(
                    repeatAdjustVolumeDirection, AudioManager.USE_DEFAULT_STREAM_TYPE, 0
                )
            }
            VolumeChangeObserver.notifyVolumeChanged()
            startIdleTimer()
        }

        fun startRepeatAdjustVolume(direction: Int) {
            repeatAdjustVolumeDirection = direction
            if (view != null || app.settings.keysFirstPressAdjusts.value) {
                adjustVolume()
            }
            postDelayed(
                repeatAdjustVolumeRunnable,
                app.settings.keysRepeatInitialDelayMs.value.toLong()
            )
        }

        fun stopRepeatAdjustVolume() {
            removeCallbacks(repeatAdjustVolumeRunnable)
            startIdleTimer()
        }
    }

    private var lifecycle: LifecycleRegistry? = null

    private fun createView(): View {
        return object : AbstractComposeView(this) {
            init {
                val owner = object : SavedStateRegistryOwner {
                    private val lifecycleRegistry = LifecycleRegistry(this)

                    private val savedStateRegistryController =
                        SavedStateRegistryController.create(this)

                    init {
                        savedStateRegistryController.performRestore(null)
                        lifecycleRegistry.currentState = Lifecycle.State.STARTED
                        this@Service.lifecycle = lifecycleRegistry
                    }

                    override val lifecycle: Lifecycle
                        get() = lifecycleRegistry

                    override val savedStateRegistry: SavedStateRegistry
                        get() = savedStateRegistryController.savedStateRegistry
                }

                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
            }

            override fun onAttachedToWindow() {
                super.onAttachedToWindow()

                Log.i(TAG, "onAttachedToWindow manufacturer: ${Build.MANUFACTURER}")

                @Suppress("SpellCheckingInspection") if (app.settings.panelBlur.value && windowManager.isCrossWindowBlurEnabled && isHardwareAccelerated && Build.MANUFACTURER != "realme") {
                    background =
                        Reflect.on(rootSurfaceControl).call("createBackgroundBlurDrawable").apply {
                            call("setBlurRadius", 200)
                            call("setCornerRadius", 40f)
                        }.get()
                }

                this@Service.handler.startIdleTimer()
            }

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouchEvent(event: MotionEvent): Boolean {
                Log.i(TAG, "onTouchEvent ${event.actionMasked}")

                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    if (!previewMode && app.settings.panelHideOnOutsideTouch.value) {
                        this@Service.handler.hideView()
                    }
                    return true
                }

                return super.onTouchEvent(event)
            }

            @Composable
            override fun Content() {
                val settings = app.settings
                val scalePct by settings.panelScalePct.flow.collectAsState()
                val panelLayout by settings.panelLayout.flow.collectAsState()
                // The view is recreated on every show, so this resets to compact each time
                var expanded by remember { mutableStateOf(false) }
                val backgroundPct by settings.panelBackgroundPct.flow.collectAsState()
                val widthPct by settings.panelWidthPct.flow.collectAsState()
                val maxHeightPct by settings.panelMaxHeightPct.flow.collectAsState()
                val screenHeightDp = LocalConfiguration.current.screenHeightDp

                return VolumeManagerTheme {
                    Surface(
                        modifier = Modifier
                            .then(if (widthPct > 0) Modifier.fillMaxWidth() else Modifier)
                            .then(
                                if (maxHeightPct > 0) {
                                    Modifier.heightIn(max = (screenHeightDp * maxHeightPct / 100).dp)
                                } else Modifier
                            ),
                        color = Color(1f, 1f, 1f, backgroundPct / 100f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(40f)
                    ) {
                      val baseDensity = LocalDensity.current
                      CompositionLocalProvider(
                          LocalDensity provides Density(
                              baseDensity.density * scalePct / 100f,
                              baseDensity.fontScale
                          )
                      ) {
                        Column(
                            modifier = Modifier.padding(
                                if (panelLayout == 0 && !expanded) 14.dp else 20.dp, 16.dp
                            )
                        ) {
                          if (panelLayout == 0 && !expanded) {
                            CompactVolumePanel(
                                apps = manager.apps.values,
                                audioManager = manager.audioManager,
                                onChange = this@Service.handler::startIdleTimer,
                                onExpand = {
                                    expanded = true
                                    this@Service.handler.startIdleTimer()
                                }
                            )
                          } else {
                            AppVolumeList(
                                apps = manager.apps.values,
                                showAll = false,
                                onChange = this@Service.handler::startIdleTimer
                            ) {
                                item("system_volume_panel") {
                                    SystemVolumePanel(
                                        audioManager = manager.audioManager,
                                        notificationManagerProxy = manager.notificationManagerProxy,
                                        showCallVolumeAlways = false,
                                        applyVisibilityFilter = true,
                                        allowVisibilityConfig = false,
                                        isSliderVisible = manager::isSystemSliderVisible,
                                        onSliderVisibilityChange = manager::setSystemSliderVisible,
                                        onChange = this@Service.handler::startIdleTimer
                                    )
                                }
                            }
                          }
                        }
                      }
                    }
                }
            }
        }
    }

    private val layoutParams by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, // Width
            WindowManager.LayoutParams.WRAP_CONTENT, // Height
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT // Make the background translucent
        ).apply {
            gravity = Gravity.CENTER // Center the view
        }
    }

    private val animationMs: Long get() = app.settings.panelAnimationMs.value.toLong()

    /** Position, anchor and width from settings. Safe to call any time; updates a live view. */
    private fun applyPanelLayout() {
        val s = app.settings
        val dm = resources.displayMetrics
        val hAnchor = s.panelHorizontalAnchor.value
        val vAnchor = s.panelVerticalAnchor.value
        layoutParams.gravity = when (hAnchor) {
            0 -> Gravity.LEFT
            2 -> Gravity.RIGHT
            else -> Gravity.CENTER_HORIZONTAL
        } or when (vAnchor) {
            0 -> Gravity.TOP
            2 -> Gravity.BOTTOM
            else -> Gravity.CENTER_VERTICAL
        }
        // For RIGHT/BOTTOM the window offset points inwards; flip so + is always right/down.
        val xPx = (s.panelOffsetXDp.value * dm.density).roundToInt()
        val yPx = (s.panelOffsetYDp.value * dm.density).roundToInt()
        layoutParams.x = if (hAnchor == 2) -xPx else xPx
        layoutParams.y = if (vAnchor == 2) -yPx else yPx
        val widthPct = s.panelWidthPct.value
        layoutParams.width = if (widthPct > 0) {
            dm.widthPixels * widthPct / 100
        } else WindowManager.LayoutParams.WRAP_CONTENT
        view?.let { windowManager.updateViewLayout(it, layoutParams) }
    }

    /** Preview = touches fall through to the settings screen underneath, no outside-dismiss. */
    private var previewMode = false
    private fun setPreviewMode(enabled: Boolean) {
        if (previewMode == enabled) return
        previewMode = enabled
        layoutParams.flags = if (enabled) {
            layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        view?.let { windowManager.updateViewLayout(it, layoutParams) }
    }

    private var view: View? = null
    private var viewVisible = false
    private var floatingButton: FloatingButton? = null

    private fun showView(preview: Boolean = false) {
        setPreviewMode(preview)
        applyPanelLayout()
        if (view == null) {
            Log.i(TAG, "add view")
            // The view doesn't respond to input events if reused
            view = createView()
            layoutParams.alpha = 0f
            windowManager.addView(view, layoutParams)
        }

        if (!viewVisible) {
            Log.i(TAG, "animate in")
            animateAlpha(layoutParams.alpha, 1f, animationMs)
            viewVisible = true
        }

        handler.startIdleTimer()
    }

    private fun setFloatingButtonPreferences(enabled: Boolean, sizeDp: Int) {
        if (enabled) {
            val button = floatingButton ?: FloatingButton(
                this,
                windowManager,
                ::showView,
                sizeDp
            ).also { floatingButton = it }
            button.setSize(sizeDp)
            button.show()
        } else {
            floatingButton?.setSize(sizeDp)
            floatingButton?.hide()
        }
    }

    private var currentAnimator: ValueAnimator? = null

    private fun animateAlpha(from: Float, to: Float, duration: Long, onEnd: (() -> Unit)? = null) {
        currentAnimator?.cancel()

        val animator = ValueAnimator.ofFloat(from, to)
        animator.duration = duration
        animator.interpolator = AccelerateDecelerateInterpolator()

        animator.addUpdateListener { animation ->
            if (view != null) {
                layoutParams.alpha = animation.animatedValue as Float
                windowManager.updateViewLayout(view, layoutParams)
            }
        }

        animator.addListener(object : Animator.AnimatorListener {
            var canceled = false

            override fun onAnimationStart(animation: Animator) {}

            override fun onAnimationEnd(animation: Animator) {
                if (canceled) {
                    return
                }

                layoutParams.alpha = to
                windowManager.updateViewLayout(view, layoutParams)

                onEnd?.invoke()
            }

            override fun onAnimationCancel(animation: Animator) {
                canceled = true
            }

            override fun onAnimationRepeat(animation: Animator) {}
        })

        animator.start()
        currentAnimator = animator
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "onReceive ${intent.action}")
            if (intent.action == ACTION_SHOW_VIEW) {
                showView(preview = intent.getBooleanExtra(EXTRA_PREVIEW, false))
            }
        }
    }

    override fun onServiceConnected() {
        Log.i(TAG, "onServiceConnected")

        startForegroundNotification()

        app = super.getApplication() as MyApplication
        manager = app.manager

        registerReceiver(broadcastReceiver, IntentFilter(ACTION_SHOW_VIEW), RECEIVER_NOT_EXPORTED)
        runCatching { cameraManager.registerAvailabilityCallback(cameraCallback, handler) }

        preferenceScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        preferenceScope = scope
        scope.launch {
            combine(app.floatingButtonEnabled, app.floatingButtonSize) { enabled, sizeDp ->
                enabled to sizeDp
            }.collect { (enabled, sizeDp) ->
                setFloatingButtonPreferences(enabled, sizeDp)
            }
        }
        scope.launch {
            val st = app.settings
            merge(
                st.panelHorizontalAnchor.flow, st.panelVerticalAnchor.flow,
                st.panelOffsetXDp.flow, st.panelOffsetYDp.flow, st.panelWidthPct.flow
            ).collect { applyPanelLayout() }
        }
        scope.launch {
            app.systemVolumeButtonHooksEnabled.collect { enabled ->
                if (!enabled) {
                    handler.stopRepeatAdjustVolume()
                }
            }
        }

        Log.i(TAG, "onServiceConnected done ${serviceInfo.capabilities.toString(2)}")
    }

    private fun startForegroundNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.foreground_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.foreground_notification_channel_description)
                setShowBadge(false)
            }
        )

        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.foreground_notification_title))
            .setContentText(getString(R.string.foreground_notification_text))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ---- Foreground app, tracked from window events (no binder call on the key path) ----
    @Volatile
    private var foregroundPackage: String? = null

    private val imePackages: Set<String> by lazy {
        getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            ?.enabledInputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui" || pkg in imePackages) return
        if (!::manager.isInitialized) return
        // Only real apps with UI; ignores toasts/overlays from background services
        if (manager.apps[pkg]?.hasAnyActivity != true) return
        foregroundPackage = pkg
    }

    // ---- Camera detection ----
    private val cameraManager by lazy {
        getSystemService(android.hardware.camera2.CameraManager::class.java)!!
    }
    private val camerasInUse = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val cameraCallback = object : android.hardware.camera2.CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            camerasInUse.remove(cameraId)
        }

        override fun onCameraUnavailable(cameraId: String) {
            camerasInUse.add(cameraId)
        }
    }

    /** Every app that can act as a camera (Pixel Camera, secure lock-screen camera, etc.). */
    private val cameraPackages: Set<String> by lazy {
        listOf(
            android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA,
            android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE,
            android.provider.MediaStore.ACTION_IMAGE_CAPTURE,
            android.provider.MediaStore.INTENT_ACTION_VIDEO_CAMERA,
        ).flatMap { action ->
            packageManager.queryIntentActivities(
                Intent(action), android.content.pm.PackageManager.MATCH_ALL
            ).map { it.activityInfo.packageName }
        }.toSet()
    }

    override fun onInterrupt() {
        Log.i(TAG, "onInterrupt")
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")

        preferenceScope?.cancel()
        floatingButton?.hide()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Toast.makeText(this, R.string.accessibility_service_died, Toast.LENGTH_SHORT).show()

        unregisterReceiver(broadcastReceiver)
        runCatching { cameraManager.unregisterAvailabilityCallback(cameraCallback) }
        super.onDestroy()
    }

    val activityTaskManager by lazy { ActivityTaskManagerProxy(this) }
    private val powerManager by lazy { getSystemService(android.os.PowerManager::class.java)!! }
    private val keyguardManager by lazy { getSystemService(android.app.KeyguardManager::class.java)!! }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.i(
            TAG,
            "onKeyEvent action = ${event.action}, key code = ${event.keyCode}, shizuku permission = ${manager.shizukuStatus}"
        )

        // Only handle `VOLUME_UP` and `VOLUME_DOWN`
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        if (!app.systemVolumeButtonHooksEnabled.value) {
            if (event.action == KeyEvent.ACTION_UP) {
                handler.stopRepeatAdjustVolume()
            }
            return false
        }

        // Ignore if Shizuku is not ready
        if (manager.shizukuStatus != Manager.ShizukuStatus.Connected) {
            return false
        }

        // Foreground app: cached from window events; only ask the system if we don't know yet
        val foreground = foregroundPackage
            ?: activityTaskManager.getForegroundTask()?.app?.also { foregroundPackage = it }

        // User-configurable "leave it to the system" situations
        val settings = app.settings
        val passToSystem =
            (settings.keysIgnoreCamera.value &&
                (camerasInUse.isNotEmpty() || (foreground != null && foreground in cameraPackages))) ||
                (settings.keysIgnoreScreenOff.value && !powerManager.isInteractive) ||
                (settings.keysIgnoreLockScreen.value && keyguardManager.isKeyguardLocked) ||
                (settings.keysIgnoreInCall.value && manager.audioManager.mode.let {
                    it == AudioManager.MODE_IN_CALL || it == AudioManager.MODE_IN_COMMUNICATION
                })
        if (passToSystem) {
            if (event.action == KeyEvent.ACTION_UP) {
                handler.stopRepeatAdjustVolume()
            }
            return false
        }

        // Per-app "don't hook volume buttons" list
        if (foreground != null && manager.apps[foreground]?.disableVolumeButtons == true) {
            if (event.action == KeyEvent.ACTION_UP) {
                handler.stopRepeatAdjustVolume()
            }
            return false
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                handler.startRepeatAdjustVolume(
                    if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        AudioManager.ADJUST_RAISE
                    } else {
                        AudioManager.ADJUST_LOWER
                    }
                )
                showView()
            }

            KeyEvent.ACTION_UP -> handler.stopRepeatAdjustVolume()
        }

        return true
    }
}
