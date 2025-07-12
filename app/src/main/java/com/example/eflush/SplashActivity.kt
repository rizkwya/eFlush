
// Stack Universal

package com.example.eflush

import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.viewpager2.widget.ViewPager2

class SplashActivity : AppCompatActivity() {
    private lateinit var videoView: VideoView
    private lateinit var textPager: ViewPager2

    private val messages = listOf(
        "Welcome to eFlush",
        "Smart Hydroponic",
        "Simple & Powerful"
    )
    private val slogans = listOf(
        "Grow Smart, Live Green.",
        "Teknologi untuk Hidroponik yang Lebih Mudah.",
        "Tanaman Bahagia, Hidup Lebih Ceria."
    )

    private var currentPage = 0
    private val autoScrollHandler = Handler(Looper.getMainLooper())
    private val resetHandler = Handler(Looper.getMainLooper())
    private var barAnimators = mutableListOf<ValueAnimator?>()
    private var isAutoScrolling = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Video background
        videoView = findViewById(R.id.videoView)
        val videoUri = Uri.parse("android.resource://${packageName}/raw/opening")
        videoView.setVideoURI(videoUri)
        videoView.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVolume(0f, 0f)
        }
        videoView.start()

        // ViewPager2 untuk teks swipe/otomatis
        textPager = findViewById(R.id.textPager)
        val pagerAdapter = TextPagerAdapter(messages)
        textPager.adapter = pagerAdapter

        // Slogan dinamis di bawah ViewPager2
        val sloganText = findViewById<TextView>(R.id.sloganText)
        sloganText.text = slogans[0] // Slogan awal

        // Tombol dan animasi fade-in
        val nextButton = findViewById<Button>(R.id.nextButton)
        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.duration = 1500
        nextButton.startAnimation(fadeIn)
        nextButton.alpha = 1f

        nextButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Bar progress
        val bar1 = findViewById<View>(R.id.bar1)
        val bar2 = findViewById<View>(R.id.bar2)
        val bar3 = findViewById<View>(R.id.bar3)
        val bar1Container = findViewById<View>(R.id.bar1Container)
        val bar2Container = findViewById<View>(R.id.bar2Container)
        val bar3Container = findViewById<View>(R.id.bar3Container)
        val barList = listOf(
            Pair(bar1, bar1Container),
            Pair(bar2, bar2Container),
            Pair(bar3, bar3Container)
        )

        // Fungsi animasi bar
        fun animateBarToFull(bar: View, container: View, duration: Long, onEnd: () -> Unit = {}) {
            barAnimators.forEach { it?.cancel() }
            barAnimators.clear()

            bar.layoutParams.width = 0
            bar.requestLayout()
            val fullWidth = container.width
            val animator = ValueAnimator.ofInt(0, fullWidth)
            animator.duration = duration
            animator.addUpdateListener {
                val value = it.animatedValue as Int
                bar.layoutParams.width = value
                bar.requestLayout()
            }
            animator.doOnEnd { onEnd() }
            animator.start()
            barAnimators.add(animator)
        }

        // Fungsi set bar penuh untuk slide yang sudah lewat
        fun setBarFullUpTo(position: Int) {
            for (i in barList.indices) {
                val (bar, container) = barList[i]
                if (i < position) {
                    bar.layoutParams.width = container.width
                } else {
                    bar.layoutParams.width = 0
                }
                bar.requestLayout()
            }
        }

        // Fungsi untuk sinkronisasi bar dan animasi sesuai slide aktif
        fun syncBarWithPage(position: Int) {
            setBarFullUpTo(position)
            if (position < barList.size) {
                val (bar, container) = barList[position]
                animateBarToFull(bar, container, 5000)
            }
        }

        // Auto-scroll function
        val autoScrollRunnable = object : Runnable {
            override fun run() {
                if (currentPage < messages.size - 1) {
                    currentPage++
                    textPager.setCurrentItem(currentPage, true)
                }
            }
        }

        // Reset ke bar 1 jika diam di bar terakhir selama 7 detik
        val resetRunnable = Runnable {
            currentPage = 0
            textPager.setCurrentItem(0, true)
        }

        // Pastikan animasi bar dan auto-scroll dimulai setelah layout siap
        val rootView = findViewById<View>(android.R.id.content)
        rootView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                syncBarWithPage(0)
                autoScrollHandler.postDelayed(autoScrollRunnable, 5000)
            }
        })

        // Sinkronkan bar progress, animasi, dan slogan saat page berubah (manual/otomatis)
        textPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                syncBarWithPage(position)
                // Update slogan sesuai slide aktif
                sloganText.text = slogans.getOrNull(position) ?: ""
                // (Opsional) animasi fade-in pada slogan
                val fadeInSlogan = AlphaAnimation(0f, 1f)
                fadeInSlogan.duration = 500
                sloganText.startAnimation(fadeInSlogan)
                sloganText.alpha = 1f
                // Reset auto-scroll timer agar tetap lanjut dari posisi terakhir
                autoScrollHandler.removeCallbacks(autoScrollRunnable)
                resetHandler.removeCallbacks(resetRunnable)
                if (position < messages.size - 1) {
                    autoScrollHandler.postDelayed(autoScrollRunnable, 5000)
                } else {
                    // Jika di bar terakhir, tunggu 7 detik lalu reset ke bar 1
                    resetHandler.postDelayed(resetRunnable, 8000)
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        autoScrollHandler.removeCallbacksAndMessages(null)
        resetHandler.removeCallbacksAndMessages(null)
        barAnimators.forEach { it?.cancel() }
        barAnimators.clear()
    }

    override fun onResume() {
        super.onResume()
        videoView.start()
    }

    override fun onPause() {
        super.onPause()
        videoView.pause()
    }
}
