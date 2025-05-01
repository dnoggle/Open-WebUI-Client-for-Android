package com.maticcm.openwebuiclient

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.maticcm.openwebuiclient.databinding.ActivityWelcomeBinding

class WelcomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWelcomeBinding
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("OpenWebUIClient", MODE_PRIVATE)
        
        // Check if setup is complete (both welcome seen AND URL saved)
        val savedUrl = getSharedPreferences("AppPrefs", MODE_PRIVATE)
            .getString("SAVED_URL", null)
        val setupComplete = sharedPreferences.getBoolean("setup_complete", false)

        if (setupComplete && savedUrl != null) {
            startIpInputActivity()
            return
        }

        // Set up animations
        setupAnimations()

        binding.getStartedButton.setOnClickListener {
            startIpInputActivity()
        }

        binding.donateTextView.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/maticcukimikeln"))
            startActivity(intent)
        }
    }

    private fun setupAnimations() {
        // Fade in the logo
        binding.logoImageView.visibility = View.INVISIBLE
        binding.logoImageView.post {
            binding.logoImageView.startAnimation(
                AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
            )
            binding.logoImageView.visibility = View.VISIBLE
        }

        // Slide up the title and description
        binding.titleTextView.startAnimation(
            AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
        )
        binding.descriptionTextView.startAnimation(
            AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
        )

        // Fade in the button with a delay
        binding.getStartedButton.alpha = 0f
        binding.getStartedButton.animate()
            .alpha(1f)
            .setStartDelay(1000)
            .setDuration(500)
            .start()
    }

    private fun startIpInputActivity() {
        val intent = Intent(this, IpInputActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
} 