package kr.co.cotton.vlrgg_mobile

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Explicit adb entry point; absent from Release and from the app's navigation. */
class CrashlyticsValidationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.CRASHLYTICS_COLLECTION_ENABLED) {
            Log.i(TAG, "collection=off firebaseInitialized=${FirebaseApp.getApps(this).isNotEmpty()}")
            finish()
            return
        }
        val crashlytics = FirebaseCrashlytics.getInstance()
        Log.i(TAG, "collection=${crashlytics.isCrashlyticsCollectionEnabled}")
        when (intent.getStringExtra("validation_action")) {
            "crash" -> Handler(Looper.getMainLooper()).postDelayed({
                throw IllegalStateException("Crashlytics validation: Android fatal #121")
            }, 3_000)
            "anr" -> setContentView(TextView(this).apply {
                text = "Crashlytics ANR validation: tap twice"
                gravity = android.view.Gravity.CENTER
                setOnClickListener { Thread.sleep(60_000) }
            })
            "disable" -> {
                crashlytics.setCrashlyticsCollectionEnabled(false)
                Log.i(TAG, "persistedCollection=false")
                finish()
            }
            else -> finish()
        }
    }

    private companion object {
        const val TAG = "CrashlyticsValidation"
    }
}
