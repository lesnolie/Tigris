package com.leslie.tvhome
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Public Settings actions only; never silently modify OEM system settings. */
class TvOSDeviceSettingsActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   setPadding(dp(64),dp(42),dp(64),dp(25))
   setBackgroundColor(0xFF10151F.toInt())
  }
  root.addView(TextView(this).apply {
   text=getString(R.string.tvos_device_settings_title)
   setTextColor(Color.WHITE);textSize=30f
  })
  root.addView(TextView(this).apply {
   text=getString(R.string.tvos_device_settings_info)
   setTextColor(0xFFA9B7C7.toInt());textSize=15f
  })
  val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val items=listOf(
   R.string.tvos_setting_display to Settings.ACTION_DISPLAY_SETTINGS,
   R.string.tvos_setting_audio to Settings.ACTION_SOUND_SETTINGS,
   R.string.tvos_setting_network to Settings.ACTION_WIFI_SETTINGS,
   R.string.tvos_setting_bluetooth to Settings.ACTION_BLUETOOTH_SETTINGS,
   R.string.tvos_setting_apps to Settings.ACTION_APPLICATION_SETTINGS,
   R.string.tvos_setting_accessibility to Settings.ACTION_ACCESSIBILITY_SETTINGS,
   R.string.tvos_setting_keyboard to Settings.ACTION_INPUT_METHOD_SETTINGS,
   R.string.tvos_setting_date to Settings.ACTION_DATE_SETTINGS)
  for ((name,action) in items) {
   val row=TextView(this).apply {
    text=getString(name);textSize=20f;setTextColor(Color.WHITE)
    isFocusable=true;isClickable=true;gravity=Gravity.CENTER_VERTICAL
    setPadding(dp(22),0,dp(22),0)
    background=rounded(0xFF263449.toInt(),dp(12).toFloat())
    setOnFocusChangeListener { v,on -> v.background=rounded(if(on)0xFF486081.toInt() else 0xFF263449.toInt(),dp(12).toFloat()) }
    setOnClickListener {
     try {startActivity(Intent(action))}
     catch (_:Exception) {
      try {startActivity(Intent(Settings.ACTION_SETTINGS))}
      catch (_:Exception) {android.widget.Toast.makeText(this@TvOSDeviceSettingsActivity,R.string.tvos_device_settings_unavailable,android.widget.Toast.LENGTH_SHORT).show()}
     }
    }
   }
   list.addView(row,LinearLayout.LayoutParams(-1,dp(57)).apply{bottomMargin=dp(9)})
  }
  root.addView(ScrollView(this).apply{addView(list)},LinearLayout.LayoutParams(-1,0,1f))
  setContentView(root)
  list.getChildAt(0)?.requestFocus()
 }
}
