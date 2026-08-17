package com.nbys.training

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.TextureView
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.UUID
import java.util.concurrent.Executors
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class DisplayHit(val x: Float, val y: Float, val number: Int)
data class PendingHitUpload(
    val sessionId: Long,
    val hit: LaserHit,
    val targetHit: CalibrationPoint,
    val shotNo: Int,
    val hitAt: Long
)

class MainActivity : AppCompatActivity() {
  private val http = OkHttpClient()
  private val executor = Executors.newSingleThreadExecutor()
  private val sans by lazy { Typeface.create("sans-serif", Typeface.NORMAL) }
  private val sansBold by lazy { Typeface.create("sans-serif", Typeface.BOLD) }
  private val loginPrefs by lazy { getSharedPreferences("login_preferences", MODE_PRIVATE) }
  private val heartbeatHandler = Handler(Looper.getMainLooper())
  private lateinit var cameraTexture: TextureView
  private lateinit var status: TextView
  private var camera2: Camera2Controller? = null
  private var zoomRatio = 1f
  private var zoomLabel: TextView? = null
  private var actualFps = 0f
  private var endTrainingButton: Button? = null
  private var loginButton: Button? = null
  private var loginSpinner: ProgressBar? = null
  private lateinit var calibrationOverlay: CalibrationOverlay
  private var targetName = "NBYS Laser Precision A4"
  private var latestPoints = emptyList<CalibrationPoint>()
  private var previousCalibrationPoints = emptyList<CalibrationPoint>()
  @Volatile private var manualCalibration = false
  @Volatile private var manualPoints = emptyList<CalibrationPoint>()
  private var manualButton: Button? = null
  private var autoLocateButton: Button? = null
  private var zoomControls: View? = null
  private var actionLogPanel: View? = null
  private var actionLogView: TextView? = null
  private val actionLogs = ArrayDeque<String>()
  private var lastActionLog = ""
  private var lastLocatedPointCount = -1
  private var draggingManualPoint = -1
  @Volatile private var autoLocateRequested = false
  private var calibrationStableFrames = 0
  private var sceneCheckFrames = 0
  private var calibrated = false
  private var lastCalibrationText = ""
  @Volatile private var activeSessionId: Long? = null
  @Volatile private var activeTargetNo = 1
  @Volatile private var boundRoomId = 0L
  private var shotNo = 0
  private var lastHitAt = 0L
  @Volatile private var detectionEnabledAt = Long.MAX_VALUE
  private val hitUploadQueue = ArrayDeque<PendingHitUpload>()
  @Volatile private var hitUploadRunning = false
  @Volatile private var targetHitLimit = 5
  @Volatile private var localTrainingComplete = false
  private var lastCandidateX = -1f
  private var lastCandidateY = -1f
  private var candidateFrames = 0
  private var apiBase = "http://10.139.56.99:8080"
  private var token = ""
  private val deviceCode by lazy {
    loginPrefs.getString("device_code", null)
        ?: "NODE-${UUID.randomUUID().toString().take(8).uppercase()}"
            .also { loginPrefs.edit().putString("device_code", it).apply() }
  }
  private val heartbeatTask =
      object : Runnable {
        override fun run() {
          if (token.isNotEmpty() && ::cameraTexture.isInitialized) heartbeat()
          heartbeatHandler.postDelayed(this, 30000)
        }
      }
  private val sessionTask =
      object : Runnable {
        override fun run() {
          if (token.isNotEmpty() && ::cameraTexture.isInitialized) refreshActiveSession()
          heartbeatHandler.postDelayed(this, 2000)
        }
      }
  private val permission =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startCamera() else status.text = "需要相机权限"
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    showLogin()
  }

  private fun showLogin() {
    val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(5, 8, 9)) }
    root.addView(
        ImageView(this).apply {
          setImageResource(R.drawable.member_auth_bg)
          scaleType = ImageView.ScaleType.CENTER_CROP
          alpha = .48f
        },
        FrameLayout.LayoutParams(-1, -1))
    root.addView(
        View(this).apply { setBackgroundColor(Color.argb(92, 3, 6, 7)) },
        FrameLayout.LayoutParams(-1, -1))
    val scroll = ScrollView(this).apply { isFillViewport = true }
    val stage = FrameLayout(this).apply { setPadding(dp(22), dp(28), dp(22), dp(28)) }
    val card =
        LinearLayout(this).apply {
          orientation = LinearLayout.VERTICAL
          setPadding(dp(20), dp(20), dp(20), dp(20))
          background = rounded(Color.argb(218, 8, 13, 14), "#364245", 7f)
        }
    fun label(value: String) =
        TextView(this).apply {
          text = value
          typeface = sans
          setTextColor(Color.rgb(174, 184, 181))
          textSize = 12f
          setPadding(0, dp(10), 0, dp(6))
        }
    val eyebrow =
        TextView(this).apply {
          text = "NBYS MEMBER ACCESS"
          setTextColor(Color.rgb(183, 221, 67))
          textSize = 11f
          typeface = sansBold
          letterSpacing = .12f
        }
    val title =
        TextView(this).apply {
          text = "队员登录"
          setTextColor(Color.rgb(237, 242, 237))
          textSize = 30f
          typeface = sansBold
          setPadding(0, dp(12), 0, dp(8))
        }
    val intro =
        TextView(this).apply {
          text = "活动、出勤、租赁与个人资料统一入口。"
          typeface = sans
          setTextColor(Color.rgb(142, 153, 153))
          textSize = 13f
          setPadding(0, 0, 0, dp(8))
        }
    val account = loginInput("名字 / 呼号", InputType.TYPE_CLASS_TEXT)
    val password =
        loginInput("密码", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
    val rememberPassword =
        CheckBox(this).apply {
          text = "记住密码"
          typeface = sans
          textSize = 13f
          setTextColor(Color.rgb(174, 184, 181))
          buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(183, 221, 67))
          isChecked = loginPrefs.getBoolean("remember_password", false)
        }
    val showPassword =
        CheckBox(this).apply {
          text = "显示密码"
          typeface = sans
          textSize = 13f
          setTextColor(Color.rgb(174, 184, 181))
          buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(183, 221, 67))
        }
    val passwordOptions =
        LinearLayout(this).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
          addView(rememberPassword, LinearLayout.LayoutParams(0, dp(44), 1f))
          addView(showPassword, LinearLayout.LayoutParams(-2, dp(44)))
        }
    if (rememberPassword.isChecked) {
      account.setText(loginPrefs.getString("account", "") ?: "")
      password.setText(loginPrefs.getString("password", "") ?: "")
      password.setSelection(password.text.length)
    }
    showPassword.setOnCheckedChangeListener { _, checked ->
      password.transformationMethod =
          if (checked) HideReturnsTransformationMethod.getInstance()
          else PasswordTransformationMethod.getInstance()
      password.setSelection(password.text.length)
    }
    val base =
        loginInput("本地服务地址", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI).apply {
          setText(apiBase)
        }
    val button =
        Button(this).apply {
          tag = "login_button"
          text = "登录"
          textSize = 15f
          typeface = sansBold
          setTextColor(Color.rgb(23, 32, 9))
          isAllCaps = false
          background = rounded(Color.rgb(183, 221, 67), null, 6f)
        }
    val loginSpinner =
        ProgressBar(this).apply {
          tag = "login_spinner"
          isIndeterminate = true
          visibility = View.GONE
          indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.rgb(23, 32, 9))
        }
    this.loginButton = button
    this.loginSpinner = loginSpinner
    val loginAction =
        FrameLayout(this).apply {
          addView(button, FrameLayout.LayoutParams(-1, dp(52)))
          addView(loginSpinner, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        }
    status =
        TextView(this).apply {
          typeface = sans
          setTextColor(Color.rgb(226, 104, 109))
          textSize = 12f
          gravity = Gravity.CENTER
          setPadding(0, dp(10), 0, 0)
        }
    card.addView(eyebrow)
    card.addView(title)
    card.addView(intro)
    card.addView(label("名字 / 呼号"))
    card.addView(account, fieldParams())
    card.addView(label("密码"))
    card.addView(password, fieldParams())
    card.addView(passwordOptions)
    card.addView(label("服务地址"))
    card.addView(base, fieldParams())
    card.addView(loginAction, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(18) })
    card.addView(status)
    stage.addView(card, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
    scroll.addView(stage, FrameLayout.LayoutParams(-1, -1))
    root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    setContentView(root)
    fun setLoggingIn(active: Boolean) {
      button.isEnabled = !active
      button.text = if (active) "正在登录..." else "登录"
      loginSpinner.visibility = if (active) View.VISIBLE else View.GONE
    }
    button.setOnClickListener {
      apiBase = base.text.toString().trimEnd('/')
      setLoggingIn(true)
      post(
          "/api/h5/auth/login",
          JSONObject().put("account", account.text).put("password", password.text)) { reply ->
            token = reply.optJSONObject("data")?.optString("token") ?: ""
            runOnUiThread {
              if (token.isEmpty()) {
                status.text = "登录失败"
                setLoggingIn(false)
              } else {
                loginPrefs
                    .edit()
                    .apply {
                      putBoolean("remember_password", rememberPassword.isChecked)
                      if (rememberPassword.isChecked) {
                        putString("account", account.text.toString())
                        putString("password", password.text.toString())
                      } else {
                        remove("account")
                        remove("password")
                      }
                    }
                    .apply()
                chooseTarget()
              }
            }
          }
    }
  }

  private fun chooseTarget() {
    val targetOptions =
        arrayOf(
            "NBYS Laser Precision A4",
            "NBYS Laser Precision A3",
            "NBYS Human Silhouette A4",
            "NBYS Human Silhouette A3")
    var selected = targetOptions.indexOf(targetName).coerceAtLeast(0)
    AlertDialog.Builder(this)
        .setTitle("选择靶纸")
        .setSingleChoiceItems(targetOptions, selected) { _, which -> selected = which }
        .setNegativeButton("取消", null)
        .setPositiveButton("确认") { dialog, _ ->
          targetName = targetOptions[selected]
          dialog.dismiss()
          showCamera()
        }
        .show()
  }

  private fun loginInput(hintText: String, type: Int) =
      EditText(this).apply {
        hint = hintText
        inputType = type
        typeface = sans
        setTextColor(Color.rgb(237, 242, 237))
        setHintTextColor(Color.rgb(105, 119, 120))
        textSize = 15f
        setPadding(dp(14), 0, dp(14), 0)
        background = rounded(Color.rgb(17, 25, 27), "#3b494c", 6f)
      }

  private fun fieldParams() = LinearLayout.LayoutParams(-1, dp(52))

  private fun rounded(fill: Int, stroke: String?, radius: Float) =
      GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radius.toInt()).toFloat()
        if (stroke != null) setStroke(dp(1), Color.parseColor(stroke))
      }

  private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

  private fun attachCamera2Surface(root: FrameLayout) {
    cameraTexture = TextureView(this)
    root.addView(cameraTexture, FrameLayout.LayoutParams(-1, -1))
  }

  private fun showCamera() {
    val root = FrameLayout(this)
    calibrationOverlay = CalibrationOverlay()
    status =
        TextView(this).apply {
          setTextColor(0xffffffff.toInt())
          text = "正在注册靶机 · $targetName\n请将靶纸中心对准绿色准星"
          setPadding(24, 48, 24, 24)
        }
    attachCamera2Surface(root)
    root.addView(calibrationOverlay)
    root.addView(status)
    root.addView(
        createCalibrationControls(),
        FrameLayout.LayoutParams(-2, dp(44), Gravity.TOP or Gravity.END).apply {
          topMargin = dp(88)
          marginEnd = dp(12)
        })
    endTrainingButton =
        Button(this).apply {
          text = "结束训练"
          textSize = 13f
          setTextColor(Color.WHITE)
          isAllCaps = false
          visibility = View.GONE
          background = rounded(Color.argb(225, 151, 48, 48), "#ef8888", 6f)
          setOnClickListener { confirmEndTraining() }
        }
    root.addView(
        endTrainingButton,
        FrameLayout.LayoutParams(dp(100), dp(44), Gravity.TOP or Gravity.END).apply {
          topMargin = dp(142)
          marginEnd = dp(12)
        })
    zoomControls = createZoomControls()
    root.addView(
        zoomControls,
        FrameLayout.LayoutParams(-2, dp(48), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
          bottomMargin = dp(170)
        })
    actionLogPanel = createActionLog()
    root.addView(
        actionLogPanel,
        FrameLayout.LayoutParams(-1, dp(145), Gravity.BOTTOM).apply {
          marginStart = dp(12)
          marginEnd = dp(12)
          bottomMargin = dp(12)
        })
    val scaleDetector =
        ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
              override fun onScale(detector: ScaleGestureDetector): Boolean {
                setCameraZoom(zoomRatio * detector.scaleFactor)
                return true
              }
            })
    var moved = false
    var downX = 0f
    var downY = 0f
    cameraTexture.setOnTouchListener { _, event ->
      scaleDetector.onTouchEvent(event)
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          downX = event.x
          downY = event.y
          moved = false
          draggingManualPoint = if (manualCalibration) findManualPoint(event.x, event.y) else -1
        }
        MotionEvent.ACTION_MOVE -> {
          if (kotlin.math.abs(event.x - downX) > dp(8) || kotlin.math.abs(event.y - downY) > dp(8))
              moved = true
          if (manualCalibration && draggingManualPoint >= 0 && !scaleDetector.isInProgress)
              dragManualPoint(draggingManualPoint, event.x, event.y)
        }
        MotionEvent.ACTION_UP -> {
          if (manualCalibration && !scaleDetector.isInProgress) {
            if (draggingManualPoint >= 0) {
              dragManualPoint(draggingManualPoint, event.x, event.y)
              updateCameraStatus("已调整第 ${draggingManualPoint + 1} 个定位点，可继续拖动或确认")
              actionLog("手动调整定位点 ${draggingManualPoint + 1}：${manualPoints[draggingManualPoint].let { "(%.3f, %.3f)".format(it.x, it.y) }}")
            } else if (!moved) addManualPoint(event.x, event.y)
          }
          draggingManualPoint = -1
        }
        MotionEvent.ACTION_CANCEL -> draggingManualPoint = -1
      }
      true
    }
    setContentView(root)
    actionLog("相机页面已打开，调整缩放后点击自动定位")
    heartbeatHandler.removeCallbacks(heartbeatTask)
    heartbeatHandler.removeCallbacks(sessionTask)
    heartbeatTask.run()
    sessionTask.run()
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED)
        startCamera()
    else permission.launch(Manifest.permission.CAMERA)
  }

  private fun confirmEndTraining() {
    AlertDialog.Builder(this)
        .setTitle("结束训练")
        .setMessage("结束后网页端也会同步完成本轮训练，确认结束？")
        .setNegativeButton("取消", null)
        .setPositiveButton("确认结束") { _, _ -> endTrainingFromDevice() }
        .show()
  }

  private fun endTrainingFromDevice() {
    val button = endTrainingButton ?: return
    button.isEnabled = false
    button.text = "结束中..."
    post("/api/training/h5/devices/$deviceCode/sessions/current/complete", JSONObject()) { reply ->
      val ok = reply.optInt("code", -1) == 0 || reply.optBoolean("success", false)
      runOnUiThread {
        button.isEnabled = true
        button.text = "结束训练"
        if (ok) {
          activeSessionId = null
          button.visibility = View.GONE
          updateCameraStatus("训练已结束，等待网页同步")
        } else updateCameraStatus("结束训练失败：${reply.optString("message","未知错误")}")
      }
    }
  }

  private fun createCalibrationControls() =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        autoLocateButton =
            Button(this@MainActivity).apply {
              text = "自动定位"
              textSize = 13f
              setTextColor(Color.BLACK)
              isAllCaps = false
              background = rounded(Color.rgb(183, 221, 67), "#e3ff73", 6f)
              setOnClickListener { requestAutoLocate() }
            }
        addView(autoLocateButton, LinearLayout.LayoutParams(dp(92), dp(44)))
        manualButton =
            Button(this@MainActivity).apply {
              text = "手动定位"
              textSize = 13f
              setTextColor(Color.WHITE)
              isAllCaps = false
              background = rounded(Color.argb(220, 35, 43, 44), "#667174", 6f)
              setOnClickListener { toggleManualCalibration() }
            }
        addView(manualButton, LinearLayout.LayoutParams(dp(92), dp(44)))
      }

  private fun createActionLog() =
      ScrollView(this).apply {
        background = rounded(Color.argb(225, 4, 8, 9), "#526064", 6f)
        actionLogView =
            TextView(this@MainActivity).apply {
              setPadding(dp(10), dp(8), dp(10), dp(8))
              setTextColor(Color.rgb(194, 225, 113))
              textSize = 11f
              typeface = Typeface.MONOSPACE
            }
        addView(actionLogView, FrameLayout.LayoutParams(-1, -2))
      }

  private fun actionLog(message: String) {
    if (message == lastActionLog) return
    lastActionLog = message
    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
    runOnUiThread {
      actionLogs.addLast("$time  $message")
      while (actionLogs.size > 8) actionLogs.removeFirst()
      actionLogView?.text = actionLogs.joinToString("\n")
      (actionLogView?.parent as? ScrollView)?.post { (actionLogView?.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN) }
    }
  }

  private fun updateCameraStatus(message: String) {
    if (::status.isInitialized) runOnUiThread { status.text = message }
    actionLog("顶部状态：$message")
  }

  private fun requestAutoLocate() {
    manualCalibration = false
    setManualFocusMode(false)
    manualPoints = emptyList()
    manualButton?.text = "手动定位"
    autoLocateRequested = true
    calibrated = false
    calibrationStableFrames = 0
    previousCalibrationPoints = emptyList()
    latestPoints = emptyList()
    SceneMonitor.reset()
    CalibrationDetector.reset()
    LaserDetector.resetPulse()
    calibrationOverlay.points = emptyList()
    calibrationOverlay.invalidate()
    updateCameraStatus("自动定位：请保持当前缩放画面静止")
    actionLog("自动定位启动，等待画面连续静止 3 帧")
  }

  private fun toggleManualCalibration() {
    manualCalibration = !manualCalibration
    setManualFocusMode(manualCalibration)
    manualPoints = emptyList()
    calibrated = false
    autoLocateRequested = false
    calibrationStableFrames = 0
    manualButton?.text = if (manualCalibration) "恢复自动" else "手动定位"
    latestPoints = emptyList()
    calibrationOverlay.points = emptyList()
    calibrationOverlay.invalidate()
    updateCameraStatus(if (manualCalibration) "手动定位：依次点击左上、右上、右下、左下" else "正在恢复自动识别")
    actionLog(if (manualCalibration) "进入手动定位，请依次点击左上、右上、右下、左下" else "退出手动定位")
  }

  private fun addManualPoint(screenX: Float, screenY: Float) {
    if (manualPoints.size >= 4) return
    val point = calibrationOverlay.screenToSource(screenX, screenY) ?: return
    manualPoints = manualPoints + point
    latestPoints = manualPoints
    calibrationOverlay.points = manualPoints
    calibrationOverlay.invalidate()
    if (manualPoints.size == 4) {
      updateCameraStatus("已选择 4 个定位点，请确认")
      confirmManualCalibration()
    } else
        updateCameraStatus(
            "手动定位 ${manualPoints.size}/4：请点击${listOf("左上","右上","右下","左下")[manualPoints.size]}定位点")
  }

  private fun findManualPoint(screenX: Float, screenY: Float): Int {
    var nearest = -1
    var nearestDistance = dp(42).toFloat()
    manualPoints.forEachIndexed { index, point ->
      val mapped = calibrationOverlay.screenPoint(point)
      val distance = kotlin.math.hypot((mapped.first - screenX).toDouble(), (mapped.second - screenY).toDouble()).toFloat()
      if (distance <= nearestDistance) {
        nearest = index
        nearestDistance = distance
      }
    }
    return nearest
  }

  private fun dragManualPoint(index: Int, screenX: Float, screenY: Float) {
    val point = calibrationOverlay.screenToSource(screenX, screenY) ?: return
    manualPoints = manualPoints.toMutableList().apply { this[index] = point }
    latestPoints = manualPoints
    calibrationOverlay.points = manualPoints
    calibrationOverlay.invalidate()
  }

  private fun setManualFocusMode(enabled:Boolean) {
    zoomControls?.visibility=if(enabled)View.GONE else View.VISIBLE
    actionLogPanel?.visibility=if(enabled)View.GONE else View.VISIBLE
  }

  private fun confirmManualCalibration() {
    AlertDialog.Builder(this).setTitle("确认定位").setMessage("确认使用当前四个定位点？").setCancelable(false)
        .setNegativeButton("重新选择") { _,_ ->
          manualPoints=emptyList();latestPoints=emptyList();calibrated=false
          calibrationOverlay.points=emptyList();calibrationOverlay.invalidate();updateCameraStatus("手动定位：请点击左上定位点")
        }
        .setPositiveButton("确认") { _,_ ->
          calibrated=true;calibrationStableFrames=8;manualCalibration=false;manualButton?.text="手动定位";setManualFocusMode(false)
          updateCameraStatus("手动四点定位完成，等待网页开始训练")
          actionLog("手动定位完成：${manualPoints.joinToString { "(%.3f, %.3f)".format(it.x,it.y) }}");heartbeat()
        }.show()
  }

  private fun zoomButton(label: String, action: () -> Unit) =
      Button(this).apply {
        text = label
        textSize = 22f
        setTextColor(Color.WHITE)
        isAllCaps = false
        setPadding(0, 0, 0, 0)
        background = rounded(Color.rgb(35, 43, 44), null, 6f)
        setOnClickListener { action() }
      }

  private fun createZoomControls() =
      TextView(this).apply {
        text = "1.0x"
        gravity = Gravity.CENTER
        typeface = sansBold
        textSize = 14f
        setTextColor(Color.WHITE)
        setPadding(dp(8), dp(4), dp(8), dp(4))
        zoomLabel = this
      }

  private fun setCameraZoom(requested: Float) {
    zoomRatio = camera2?.setZoom(requested) ?: zoomRatio
    zoomLabel?.text = String.format(java.util.Locale.US, "%.1fx", zoomRatio)
    actionLog("画面缩放调整为 ${String.format(java.util.Locale.US, "%.1f", zoomRatio)}x")
  }

  private fun startCamera() {
    camera2?.close()
    camera2 =
        Camera2Controller(
            this,
            cameraTexture,
            onFrame = { frame, crop, rotation, measuredFps ->
            try {
              actualFps = measuredFps
              val sourceWidth = crop.width()
              val sourceHeight = crop.height()
              if (manualCalibration) {
                latestPoints = manualPoints
                runOnUiThread {
                  calibrationOverlay.updateFrame(latestPoints, sourceWidth, sourceHeight, rotation)
                }
              } else if (activeSessionId == null || !calibrated || latestPoints.size != 4) {
                val sceneStill = SceneMonitor.observe(frame, crop)
                val calibration =
                    if (sceneStill) CalibrationDetector.detect(frame, crop)
                    else CalibrationResult(emptyList())
                if (sceneStill && calibration.points.size != lastLocatedPointCount) {
                  lastLocatedPointCount = calibration.points.size
                  actionLog(CalibrationDetector.diagnostics)
                }
                latestPoints = calibration.points
                runOnUiThread {
                  calibrationOverlay.updateFrame(latestPoints, sourceWidth, sourceHeight, rotation)
                }
                val stable =
                    calibration.complete &&
                        CalibrationDetector.locked &&
                        previousCalibrationPoints.size == 4 &&
                        calibration.points.indices.all { i ->
                          kotlin.math.hypot(
                              (calibration.points[i].x - previousCalibrationPoints[i].x).toDouble(),
                              (calibration.points[i].y - previousCalibrationPoints[i].y)
                                  .toDouble()) < .012
                        }
                if (stable) calibrationStableFrames++ else calibrationStableFrames = 0
                previousCalibrationPoints =
                    if (calibration.complete) calibration.points else emptyList()
                val justCalibrated = !calibrated && calibrationStableFrames >= 8
                calibrated = calibrationStableFrames >= 8
                val text =
                    if (calibrated)
                        if (activeSessionId != null)
                            "训练中 · 已记录 $shotNo 发 · ${actualFps.toInt()}fps"
                        else "四点定位完成，等待网页开始训练"
                    else if (!sceneStill) "请保持手机和靶纸静止，正在保存定位画面"
                    else "正在识别黑白定位标记 ${calibration.points.size}/4，请让四个方形标记完整入镜"
                if (text != lastCalibrationText) {
                  lastCalibrationText = text
                  updateCameraStatus(text)
                }
                if (justCalibrated) {
                  SceneMonitor.saveAnchor(frame, crop)
                  LaserDetector.updateBackground(frame, crop)
                  autoLocateRequested = false
                  autoLocateButton?.post { autoLocateButton?.text = "重新定位" }
                  actionLog("自动定位完成：${latestPoints.joinToString { "(%.3f, %.3f)".format(it.x,it.y) }}")
                  heartbeat()
                }
              } else {
                sceneCheckFrames++
                if (sceneCheckFrames >= 12) {
                  sceneCheckFrames = 0
                  if (SceneMonitor.moved(frame, crop)) {
                    calibrated = false
                    calibrationStableFrames = 0
                    previousCalibrationPoints = emptyList()
                    latestPoints = emptyList()
                    SceneMonitor.reset()
                    CalibrationDetector.reset()
                    LaserDetector.resetPulse()
                    autoLocateRequested = true
                    actionLog("检测到画面明显变化，暂停命中并重新寻找 4 个黑白定位标记")
                    updateCameraStatus("检测到画面移动，已暂停命中并重新定位")
                  }
                }
                runOnUiThread {
                  calibrationOverlay.updateFrame(latestPoints, sourceWidth, sourceHeight, rotation)
                }
              }
              val hit =
                  if (calibrated && activeSessionId != null && !localTrainingComplete && System.currentTimeMillis() >= detectionEnabledAt)
                      LaserDetector.detect(frame, crop)
                  else {
                    LaserDetector.updateBackground(frame, crop)
                    null
                  }
              if (hit != null && pointInTarget(hit, latestPoints)) confirmAndUploadHit(hit)
              else candidateFrames = 0
            } finally {
              frame.close()
            }
          },
            onStatus = { message ->
              Log.i("NbysCamera2", message)
              runOnUiThread {
                if (!message.startsWith("Camera2 ") || activeSessionId == null) updateCameraStatus(message)
              }
            })
    camera2?.start()
    setCameraZoom(1f)
  }

  private fun heartbeat() {
    post(
        "/api/training/h5/devices/heartbeat",
        JSONObject()
            .put("device_code", deviceCode)
            .put("name", "Android 靶机")
            .put("target_name", targetName)
            .put("frame_rate", actualFps.toInt())
            .put("calibration_status", if (calibrated) "calibrated" else "pending")) { device ->
          val data = device.optJSONObject("data")
          activeTargetNo = data?.optInt("target_no", 1) ?: 1
          targetHitLimit = data?.optInt("target_hit_limit", 5)?.coerceAtLeast(1) ?: 5
          boundRoomId = data?.optLong("room_id", 0) ?: 0
          refreshActiveSession()
          if (!calibrated) updateCameraStatus("靶机在线，请将完整靶纸置于画面中")
        }
  }

  private fun refreshActiveSession() {
    get(
        "/api/training/h5/devices/$deviceCode/state",
        onError = { message -> updateCameraStatus("靶机状态同步失败：$message") }) { reply ->
          val data = reply.optJSONObject("data")
          boundRoomId = data?.optLong("room_id", 0) ?: 0
          activeTargetNo = data?.optInt("target_no", 1) ?: 1
          val session = data?.optJSONObject("running_session")
          val next = session?.optLong("id", 0)?.takeIf { it > 0 }
          runOnUiThread {
            endTrainingButton?.visibility = if (next != null) View.VISIBLE else View.GONE
          }
          if (next != activeSessionId) {
            activeSessionId = next
            camera2?.setTrainingExposure(next != null)
            LaserDetector.resetPulse()
            if (next == null) SceneMonitor.reset()
            detectionEnabledAt =
                if (next != null) System.currentTimeMillis() + 1200 else Long.MAX_VALUE
            actionLog(if(next != null) "收到训练开始指令，降低曝光并建立无激光基准" else "训练已结束，停止命中检测")
            shotNo = 0
            localTrainingComplete = false
            lastHitAt = 0
            synchronized(hitUploadQueue) { hitUploadQueue.clear() }
            hitUploadRunning = false
            runOnUiThread { calibrationOverlay.hits = emptyList() }
            updateCameraStatus(
                if (next != null) "训练中 · 已记录 0 发 · ${actualFps.toInt()}fps"
                else if (calibrated) "四点定位完成，等待网页开始训练" else "等待完成四点定位")
          }
        }
  }

  private fun confirmAndUploadHit(hit: LaserHit) {
    Log.i(
        "NbysLaser",
        "detected x=${hit.x} y=${hit.y} confidence=${hit.confidence} session=$activeSessionId")
    lastCandidateX = hit.x
    lastCandidateY = hit.y
    candidateFrames = 0
    uploadHit(hit)
  }

  private fun pointInTarget(hit: LaserHit, points: List<CalibrationPoint>): Boolean {
    if (points.size != 4) return false
    var inside = false
    var j = points.size - 1
    for (i in points.indices) {
      val a = points[i]
      val b = points[j]
      if ((a.y > hit.y) != (b.y > hit.y) && hit.x < (b.x - a.x) * (hit.y - a.y) / (b.y - a.y) + a.x)
          inside = !inside
      j = i
    }
    return inside
  }

  /** Inverts the bilinear camera quadrilateral (TL, TR, BR, BL) into target coordinates. */
  private fun targetCoordinates(hit: LaserHit): CalibrationPoint? {
    if (latestPoints.size != 4) return null
    val p = latestPoints
    var u = .5f
    var v = .5f
    repeat(10) {
      val x =
          (1 - u) * (1 - v) * p[0].x + u * (1 - v) * p[1].x + u * v * p[2].x + (1 - u) * v * p[3].x
      val y =
          (1 - u) * (1 - v) * p[0].y + u * (1 - v) * p[1].y + u * v * p[2].y + (1 - u) * v * p[3].y
      val dxdu = (1 - v) * (p[1].x - p[0].x) + v * (p[2].x - p[3].x)
      val dxdv = (1 - u) * (p[3].x - p[0].x) + u * (p[2].x - p[1].x)
      val dydu = (1 - v) * (p[1].y - p[0].y) + v * (p[2].y - p[3].y)
      val dydv = (1 - u) * (p[3].y - p[0].y) + u * (p[2].y - p[1].y)
      val det = dxdu * dydv - dxdv * dydu
      if (kotlin.math.abs(det) > .000001f) {
        val ex = hit.x - x
        val ey = hit.y - y
        u += (ex * dydv - ey * dxdv) / det
        v += (ey * dxdu - ex * dydu) / det
      }
    }
    return CalibrationPoint(u.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
  }

  private fun uploadHit(hit: LaserHit) {
    val sessionId = activeSessionId ?: return
    val targetHit = targetCoordinates(hit) ?: return
    val now = System.currentTimeMillis()
    lastHitAt = now
    val nextShot = ++shotNo
    Log.i(
        "NbysLaser",
        "queued session=$sessionId shot=$nextShot camera=${hit.x},${hit.y} target=${targetHit.x},${targetHit.y}")
    actionLog(
        "命中 #$nextShot 相机(%.3f, %.3f) 靶纸(%.3f, %.3f) 置信度%d%%".format(
            hit.x, hit.y, targetHit.x, targetHit.y, (hit.confidence * 100).toInt()))
    runOnUiThread {
      calibrationOverlay.hits = calibrationOverlay.hits + DisplayHit(hit.x, hit.y, nextShot)
      updateCameraStatus("训练中 · 已识别 $nextShot 发 · ${actualFps.toInt()}fps")
    }
    synchronized(hitUploadQueue) {
      hitUploadQueue.addLast(PendingHitUpload(sessionId, hit, targetHit, nextShot, now))
    }
    if (nextShot >= targetHitLimit) {
      localTrainingComplete = true
      updateCameraStatus("已完成 $nextShot 发，正在上传成绩")
      actionLog("已识别 $nextShot/$targetHitLimit 发，立即停止检测并批量上传")
      uploadHitBatch(sessionId)
    }
  }

  private fun uploadHitBatch(sessionId:Long) {
    val pending =
        synchronized(hitUploadQueue) {
          if (hitUploadRunning || hitUploadQueue.isEmpty()) return
          hitUploadRunning = true
          hitUploadQueue.toList()
        }
    val hits=JSONArray()
    pending.forEach { item ->
      hits.put(JSONObject()
          .put("event_id", "$deviceCode-${item.sessionId}-${item.hitAt}")
          .put("target_no", activeTargetNo)
          .put("shot_no", item.shotNo)
          .put("hit_at_ms", item.hitAt)
          .put("x_ratio", item.targetHit.x)
          .put("y_ratio", item.targetHit.y)
          .put("accuracy", (item.hit.confidence * 100).toInt()))
    }
    Log.i("NbysLaser", "batch uploading session=$sessionId count=${pending.size}")
    post(
        "/api/training/h5/sessions/$sessionId/hits/batch",
        JSONObject().put("hits",hits)) { reply ->
          val ok = reply.optInt("code", -1) == 0 || reply.optBoolean("success", false)
          Log.i("NbysLaser", "batch upload response ok=$ok body=$reply")
          actionLog(if(ok) "批量上传成功，共 ${pending.size} 发" else "批量上传失败：${reply.optString("message","服务异常")}")
          synchronized(hitUploadQueue) {
            if(ok) hitUploadQueue.clear()
            hitUploadRunning = false
          }
          updateCameraStatus(if(ok) "训练完成，成绩已上传" else "成绩上传失败，请点击结束训练重试")
        }
  }

  private inner class CalibrationOverlay : View(this@MainActivity) {
    var points = emptyList<CalibrationPoint>()
    private var sourceWidth = 1
    private var sourceHeight = 1
    private var rotation = 0
    var hits = emptyList<DisplayHit>()
      set(value) {
        field = value
        invalidate()
      }

    fun updateFrame(value: List<CalibrationPoint>, w: Int, h: Int, r: Int) {
      points = value
      sourceWidth = w
      sourceHeight = h
      rotation = r
      invalidate()
    }

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.rgb(183, 221, 67)
          style = Paint.Style.STROKE
          strokeWidth = dp(3).toFloat()
        }
    private val hitPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.rgb(255, 55, 55)
          style = Paint.Style.FILL
          textAlign = Paint.Align.CENTER
          textSize = dp(12).toFloat()
          typeface = sansBold
        }
    private val centerGuidePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.rgb(183, 221, 67)
          style = Paint.Style.STROKE
          strokeWidth = dp(2).toFloat()
        }
    private val centerGuideShadowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.argb(170, 0, 0, 0)
          style = Paint.Style.FILL
        }

    private fun geometry(): FloatArray {
      val rw = if (rotation == 90 || rotation == 270) sourceHeight else sourceWidth
      val rh = if (rotation == 90 || rotation == 270) sourceWidth else sourceHeight
      val scale = maxOf(width.toFloat() / rw, height.toFloat() / rh)
      return floatArrayOf(
          rw.toFloat(), rh.toFloat(), scale, (width - rw * scale) / 2, (height - rh * scale) / 2)
    }

    private fun map(x: Float, y: Float): Pair<Float, Float> {
      val rotated =
          when (rotation) {
            90 -> Pair(1 - y, x)
            180 -> Pair(1 - x, 1 - y)
            270 -> Pair(y, 1 - x)
            else -> Pair(x, y)
          }
      val g = geometry()
      return Pair(g[3] + rotated.first * g[0] * g[2], g[4] + rotated.second * g[1] * g[2])
    }

    fun screenPoint(point: CalibrationPoint): Pair<Float, Float> = map(point.x, point.y)

    fun screenToSource(x: Float, y: Float): CalibrationPoint? {
      if (width == 0 || height == 0) return null
      val g = geometry()
      val rx = ((x - g[3]) / (g[0] * g[2])).coerceIn(0f, 1f)
      val ry = ((y - g[4]) / (g[1] * g[2])).coerceIn(0f, 1f)
      val source =
          when (rotation) {
            90 -> CalibrationPoint(ry, 1 - rx)
            180 -> CalibrationPoint(1 - rx, 1 - ry)
            270 -> CalibrationPoint(1 - ry, rx)
            else -> CalibrationPoint(rx, ry)
          }
      return source
    }

    override fun onDraw(canvas: Canvas) {
      super.onDraw(canvas)
      if (!calibrated) {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = dp(18).toFloat()
        canvas.drawCircle(centerX, centerY, radius, centerGuideShadowPaint)
        canvas.drawCircle(centerX, centerY, radius, centerGuidePaint)
        canvas.drawLine(centerX - dp(30), centerY, centerX - dp(7), centerY, centerGuidePaint)
        canvas.drawLine(centerX + dp(7), centerY, centerX + dp(30), centerY, centerGuidePaint)
        canvas.drawLine(centerX, centerY - dp(30), centerX, centerY - dp(7), centerGuidePaint)
        canvas.drawLine(centerX, centerY + dp(7), centerX, centerY + dp(30), centerGuidePaint)
        canvas.drawCircle(centerX, centerY, dp(3).toFloat(), centerGuidePaint)
      }
      if (points.isNotEmpty()) {
        val path = Path()
        points.forEachIndexed { i, p ->
          val mapped = map(p.x, p.y)
          if (i == 0) path.moveTo(mapped.first, mapped.second)
          else path.lineTo(mapped.first, mapped.second)
          canvas.drawCircle(mapped.first, mapped.second, dp(12).toFloat(), paint)
          if (manualCalibration) {
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dp(12).toFloat()
            canvas.drawText((i + 1).toString(), mapped.first, mapped.second + dp(4), paint)
            paint.style = Paint.Style.STROKE
          }
        }
        if (points.size == 4) {
          path.close()
          canvas.drawPath(path, paint)
        }
      }
      hits.forEach { hit ->
        val mapped = map(hit.x, hit.y)
        canvas.drawCircle(mapped.first, mapped.second, dp(11).toFloat(), hitPaint)
        hitPaint.color = Color.WHITE
        canvas.drawText(hit.number.toString(), mapped.first, mapped.second + dp(4), hitPaint)
        hitPaint.color = Color.rgb(255, 55, 55)
      }
    }
  }

  private fun post(path: String, body: JSONObject, done: (JSONObject) -> Unit) {
    val request =
        Request.Builder()
            .url(apiBase + path)
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    http
        .newCall(request)
        .enqueue(
            object : Callback {
              override fun onFailure(call: Call, e: java.io.IOException) {
                runOnUiThread {
                  status.text = "网络失败: ${e.message}"
                  loginButton?.let {
                    it.isEnabled = true
                    it.text = "登录"
                  }
                  loginSpinner?.visibility = View.GONE
                }
                done(JSONObject().put("code", -1).put("message", e.message ?: "网络连接失败"))
              }

              override fun onResponse(call: Call, response: Response) {
                done(JSONObject(response.body?.string() ?: "{}"))
              }
            })
  }

  private fun get(path: String, onError: (String) -> Unit = {}, done: (JSONObject) -> Unit) {
    val request =
        Request.Builder().url(apiBase + path).header("Authorization", "Bearer $token").get().build()
    http
        .newCall(request)
        .enqueue(
            object : Callback {
              override fun onFailure(call: Call, e: java.io.IOException) {
                onError(e.message ?: "网络连接失败")
              }

              override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string() ?: "{}"
                if (!response.isSuccessful) {
                  onError("HTTP ${response.code}")
                  return
                }
                try {
                  val json = JSONObject(raw)
                  val ok = json.optInt("code", -1) == 0 || json.optBoolean("success", false)
                  if (!ok) onError(json.optString("message", "服务返回失败")) else done(json)
                } catch (e: Exception) {
                  onError("响应解析失败")
                }
              }
            })
  }

  override fun onDestroy() {
    camera2?.close()
    heartbeatHandler.removeCallbacks(heartbeatTask)
    heartbeatHandler.removeCallbacks(sessionTask)
    executor.shutdown()
    super.onDestroy()
  }
}
