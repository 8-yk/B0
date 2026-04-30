package com.blockpuzzle

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class MenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        setContentView(R.layout.activity_menu)

        val prefs = getSharedPreferences("block_puzzle_prefs", Context.MODE_PRIVATE)
        val best  = prefs.getInt("high_score", 0)

        val tvBest = findViewById<android.widget.TextView>(R.id.tvMenuBest)
        tvBest.text = if (best > 0) "BEST  $best" else ""

        // Pulse animation on play button
        val btnPlay = findViewById<View>(R.id.btnPlay)
        ValueAnimator.ofFloat(1f, 1.06f, 1f).apply {
            duration      = 1100L
            repeatCount   = ValueAnimator.INFINITE
            addUpdateListener {
                val s = it.animatedValue as Float
                btnPlay.scaleX = s; btnPlay.scaleY = s
            }
            start()
        }

        btnPlay.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}
