package com.example.tapthetarget

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Random
import android.media.AudioManager
import android.media.ToneGenerator
import android.content.SharedPreferences

class MainActivity : AppCompatActivity() {

    // ==== data permainan ====
    private var skor = 0
    private var jumlahKlik = 0
    private var ukuranTargetDp = 72
    private val durasiPermainan = 30_000L
    private var sisaWaktuDetik = 30
    private var timer: CountDownTimer? = null
    private var sedangDijeda = false
    private val acak = Random()
    private var toneGen: ToneGenerator? = null
    private lateinit var prefs: SharedPreferences
    private var skorTertinggi = 0

    private val daftarLatar = intArrayOf(
        R.drawable.bg_game, R.drawable.bg_game_2,
        R.drawable.bg_game_3, R.drawable.bg_game_4
    )
    private var indeksLatar = 0
    private val targetMenghilangRunnable = Runnable {
        if (!sedangDijeda && overlayGameOver.visibility != View.VISIBLE) {
            imgTarget.visibility = View.GONE

            imgTarget.postDelayed({
                if (!sedangDijeda && overlayGameOver.visibility != View.VISIBLE) {
                    pindahkanTarget()
                    imgTarget.visibility = View.VISIBLE
                }
            }, 1_000L)
        }
    }

    // ==== view ====
    private lateinit var imgBackground: ImageView
    private lateinit var frameGame: FrameLayout
    private lateinit var imgTarget: ImageView
    private lateinit var tvSkor: TextView
    private lateinit var tvWaktu: TextView
    private lateinit var btnPause: Button
    private lateinit var overlayPause: LinearLayout
    private lateinit var overlayGameOver: LinearLayout
    private lateinit var tvSkorJeda: TextView
    private lateinit var tvSkorAkhir: TextView
    private lateinit var tvRekor: TextView
    private lateinit var tvRekorBaru: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        frameGame = findViewById(R.id.frameGame)
        imgBackground = findViewById(R.id.imgBackground)
        imgTarget = findViewById(R.id.imgTarget)
        tvSkor = findViewById(R.id.tvSkor)
        tvRekor = findViewById(R.id.tvRekor)
        tvRekorBaru = findViewById(R.id.tvRekorBaru)
        tvWaktu = findViewById(R.id.tvWaktu)
        btnPause = findViewById(R.id.btnPause)
        overlayPause = findViewById(R.id.overlayPause)
        overlayGameOver = findViewById(R.id.overlayGameOver)
        tvSkorJeda = findViewById(R.id.tvSkorJeda)
        tvSkorAkhir = findViewById(R.id.tvSkorAkhir)

        imgTarget.setOnClickListener { targetDitekan() }
        btnPause.setOnClickListener { jedaPermainan() }
        findViewById<Button>(R.id.btnLanjutkan).setOnClickListener { lanjutkanPermainan() }
        findViewById<Button>(R.id.btnUlangi).setOnClickListener { mulaiPermainan() }
        findViewById<Button>(R.id.btnMainLagi).setOnClickListener { mulaiPermainan() }

        prefs = getSharedPreferences(PREF_NAMA, MODE_PRIVATE)
        skorTertinggi = prefs.getInt(KEY_REKOR, 0)

        if (savedInstanceState != null) {
            pulihkanPermainan(savedInstanceState)
        } else {
            mulaiPermainan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        imgTarget.removeCallbacks(targetMenghilangRunnable)
        toneGen?.release()
        toneGen = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SKOR, skor)
        outState.putInt(STATE_WAKTU, sisaWaktuDetik)
        outState.putInt(STATE_KLIK, jumlahKlik)
        outState.putInt(STATE_UKURAN, ukuranTargetDp)
        outState.putBoolean(STATE_JEDA, sedangDijeda)
        outState.putBoolean(STATE_GAME_OVER, overlayGameOver.visibility == View.VISIBLE)
        outState.putBoolean(STATE_REKOR_BARU, tvRekorBaru.visibility == View.VISIBLE)
        outState.putInt(STATE_LATAR, indeksLatar)
    }

    private fun pulihkanPermainan(state: Bundle) {
        skor = state.getInt(STATE_SKOR)
        sisaWaktuDetik = state.getInt(STATE_WAKTU)
        jumlahKlik = state.getInt(STATE_KLIK)
        ukuranTargetDp = state.getInt(STATE_UKURAN, 72)
        indeksLatar = state.getInt(STATE_LATAR)

        imgBackground.setImageResource(daftarLatar[indeksLatar])
        tvSkor.text = getString(R.string.label_skor, skor)
        tvWaktu.text = getString(R.string.label_waktu, sisaWaktuDetik)
        ubahUkuranTarget()

        when {
            // sedang di layar Game Over
            state.getBoolean(STATE_GAME_OVER) -> {
                sedangDijeda = false
                imgTarget.visibility = View.GONE
                imgTarget.isEnabled = false
                btnPause.isEnabled = false
                tvSkorAkhir.text = skor.toString()
                tvRekor.text = getString(R.string.label_rekor, skorTertinggi)
                tvRekorBaru.visibility =
                    if (state.getBoolean(STATE_REKOR_BARU)) View.VISIBLE else View.GONE
                overlayGameOver.visibility = View.VISIBLE
            }
            // sedang dijeda
            state.getBoolean(STATE_JEDA) -> {
                sedangDijeda = true
                imgTarget.isEnabled = false
                tvSkorJeda.text = skor.toString()
                overlayPause.visibility = View.VISIBLE
            }
            // sedang bermain: lanjut dari sisa waktu
            else -> {
                sedangDijeda = false
                frameGame.post {
                    pindahkanTarget()
                    jadwalkanTargetMenghilang()
                }
                jalankanTimer(sisaWaktuDetik * 1000L)
            }
        }
    }

    // ================= memulai / mengulang permainan =================
    private fun mulaiPermainan() {
        pilihLatarAcak()
        skor = 0
        jumlahKlik = 0
        ukuranTargetDp = 72
        sisaWaktuDetik = 30
        sedangDijeda = false

        tvSkor.text = getString(R.string.label_skor, skor)
        tvWaktu.text = getString(R.string.label_waktu, sisaWaktuDetik)

        overlayPause.visibility = View.GONE
        overlayGameOver.visibility = View.GONE
        overlayGameOver.alpha = 1f

        imgTarget.visibility = View.VISIBLE
        imgTarget.isEnabled = true
        imgTarget.scaleX = 1f
        imgTarget.scaleY = 1f
        ubahUkuranTarget()
        btnPause.isEnabled = true

        frameGame.post {
            pindahkanTarget()
            jadwalkanTargetMenghilang()
        }
        jalankanTimer(durasiPermainan)
    }

    private fun pilihLatarAcak() {
        var baru: Int
        do {
            baru = acak.nextInt(daftarLatar.size)
        } while (baru == indeksLatar)   // supaya tidak sama dengan latar sebelumnya
        indeksLatar = baru
        imgBackground.setImageResource(daftarLatar[indeksLatar])
    }

    // ================= posisi acak target =================
    private fun pindahkanTarget() {
        val lebarArea = frameGame.width
        val tinggiArea = frameGame.height
        if (lebarArea == 0 || tinggiArea == 0) return

        val jarakAman = 24
        val maxX = lebarArea - imgTarget.width - (jarakAman * 2)
        val maxY = tinggiArea - imgTarget.height - (jarakAman * 2)
        if (maxX <= 0 || maxY <= 0) return

        imgTarget.x = (jarakAman + acak.nextInt(maxX)).toFloat()
        imgTarget.y = (jarakAman + acak.nextInt(maxY)).toFloat()
    }
    private fun ubahUkuranTarget() {
        val ukuranPx = (ukuranTargetDp * resources.displayMetrics.density).toInt()

         val params = imgTarget.layoutParams
        params.width = ukuranPx
        params.height = ukuranPx
        imgTarget.layoutParams = params
    }
    private fun jadwalkanTargetMenghilang() {
        imgTarget.removeCallbacks(targetMenghilangRunnable)
        imgTarget.postDelayed(targetMenghilangRunnable, 1_000L)
    }
    // ================= klik target =================
    private fun targetDitekan() {
        if (sedangDijeda) return
        toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)

        skor += 20
        jumlahKlik++
        if (jumlahKlik % 5 == 0) {
            ukuranTargetDp = maxOf(40, ukuranTargetDp - 8)
            ubahUkuranTarget()
        }
        tvSkor.text = getString(R.string.label_skor, skor)

        imgTarget.animate()
            .scaleX(0.6f).scaleY(0.6f).setDuration(70)
            .withEndAction {
                imgTarget.animate().scaleX(1f).scaleY(1f).setDuration(70).start()
            }
            .start()

        pindahkanTarget()
        jadwalkanTargetMenghilang()
    }

    // ================= timer =================
    private fun jalankanTimer(durasi: Long) {
        timer?.cancel()
        timer = object : CountDownTimer(durasi, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                sisaWaktuDetik = (millisUntilFinished / 1000).toInt()
                tvWaktu.text = getString(R.string.label_waktu, sisaWaktuDetik)
            }

            override fun onFinish() {
                sisaWaktuDetik = 0
                tvWaktu.text = getString(R.string.label_waktu, 0)
                gameOver()
            }
        }.start()
    }

    // ================= jeda & lanjut =================
    private fun jedaPermainan() {
        if (sedangDijeda || overlayGameOver.visibility == View.VISIBLE) return

        sedangDijeda = true
        timer?.cancel()
        imgTarget.removeCallbacks(targetMenghilangRunnable)
        imgTarget.isEnabled = false

        tvSkorJeda.text = skor.toString()
        overlayPause.alpha = 0f
        overlayPause.visibility = View.VISIBLE
        overlayPause.animate().alpha(1f).setDuration(200).start()
    }

    private fun lanjutkanPermainan() {
        if (!sedangDijeda) return

        sedangDijeda = false
        overlayPause.visibility = View.GONE
        imgTarget.isEnabled = true
        jadwalkanTargetMenghilang()
        jalankanTimer(sisaWaktuDetik * 1000L)
    }

    // ================= game over =================
    private fun gameOver() {
        timer?.cancel()
        sedangDijeda = false

        imgTarget.removeCallbacks(targetMenghilangRunnable)
        imgTarget.isEnabled = false
        imgTarget.visibility = View.GONE
        btnPause.isEnabled = false
        overlayPause.visibility = View.GONE

        tvSkorAkhir.text = skor.toString()
        val rekorBaru = skor > skorTertinggi
        if (rekorBaru) {
            skorTertinggi = skor
            prefs.edit().putInt(KEY_REKOR, skorTertinggi).apply()
        }
        tvRekor.text = getString(R.string.label_rekor, skorTertinggi)
        tvRekorBaru.visibility = if (rekorBaru) View.VISIBLE else View.GONE

        overlayGameOver.alpha = 0f
        overlayGameOver.visibility = View.VISIBLE
        overlayGameOver.animate().alpha(1f).setDuration(350).start()
    }

    companion object {
        private const val PREF_NAMA = "tap_the_target_prefs"
        private const val KEY_REKOR = "skor_tertinggi"
        private const val STATE_SKOR       = "state_skor"
        private const val STATE_WAKTU      = "state_waktu"
        private const val STATE_KLIK       = "state_klik"
        private const val STATE_UKURAN     = "state_ukuran"
        private const val STATE_JEDA       = "state_jeda"
        private const val STATE_GAME_OVER  = "state_game_over"
        private const val STATE_REKOR_BARU = "state_rekor_baru"
        private const val STATE_LATAR      = "state_latar"
    }
}