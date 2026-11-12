package com.greengauge

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            delay(1200)
            val currentUser = FirebaseAuth.getInstance().currentUser
            val destination = if (currentUser != null) {
                MainActivity::class.java
            } else {
                AuthActivity::class.java
            }
            startActivity(Intent(this@SplashActivity, destination))
            finish()
        }
    }
}
