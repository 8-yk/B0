package com.blockpuzzle

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.blockpuzzle.game.GameManager
import com.blockpuzzle.ui.DragCoordinator
import com.blockpuzzle.ui.GameBoardView
import com.blockpuzzle.ui.ParticleView
import com.blockpuzzle.ui.TrayView

class MainActivity : AppCompatActivity() {

    private lateinit var gameManager:     GameManager
    private lateinit var boardView:       GameBoardView
    private lateinit var trayView:        TrayView
    private lateinit var particleView:    ParticleView
    private lateinit var tvScore:         TextView
    private lateinit var tvHighScore:     TextView
    private lateinit var tvCombo:         TextView
    private lateinit var btnRestart:      View
    private lateinit var gameOverLayout:  View
    private lateinit var tvFinalScore:    TextView
    private lateinit var btnPlayAgain:    View
    private lateinit var dragCoordinator: DragCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        setContentView(R.layout.activity_main)
        bindViews()
        setupGame()
        setupDrag()
        setupButtons()
    }

    override fun onPause() {
        super.onPause()
        gameManager.saveState()
    }

    // ── Bind ──────────────────────────────────────────────────────────────

    private fun bindViews() {
        boardView      = findViewById(R.id.boardView)
        trayView       = findViewById(R.id.trayView)
        particleView   = findViewById(R.id.particleView)
        tvScore        = findViewById(R.id.tvScore)
        tvHighScore    = findViewById(R.id.tvHighScore)
        tvCombo        = findViewById(R.id.tvCombo)
        btnRestart     = findViewById(R.id.btnRestart)
        gameOverLayout = findViewById(R.id.gameOverLayout)
        tvFinalScore   = findViewById(R.id.tvFinalScore)
        btnPlayAgain   = findViewById(R.id.btnPlayAgain)
    }

    // ── Game setup ────────────────────────────────────────────────────────

    private fun setupGame() {
        gameManager = GameManager(this)
        boardView.gameManager = gameManager
        trayView.gameManager  = gameManager

        gameManager.onScoreChanged = { score, high ->
            runOnUiThread {
                tvScore.text     = score.toString()
                tvHighScore.text = high.toString()
            }
        }

        gameManager.onLinesCleared = { rows, cols, combo ->
            runOnUiThread {
                boardView.triggerClearAnimation(rows, cols)
                // جزيئات ← بعد تأخير خفيف حتى تُرى الـ flash أولاً
                particleView.postDelayed({
                    particleView.burst(
                        rows       = rows,
                        cols       = cols,
                        cellPx     = boardView.exposedCellPx,
                        boardOX    = boardView.exposedBoardOX,
                        boardOY    = boardView.exposedBoardOY,
                        gridSize   = gameManager.board.gridSize,
                        boardColors = gameManager.board.board
                    )
                }, 80)
                if (combo.isNotEmpty()) showCombo(combo)
            }
        }

        gameManager.onGameOver = {
            runOnUiThread { showGameOver() }
        }

        gameManager.onTrayChanged = {
            runOnUiThread {
                trayView.refresh()
                for (i in 0..2) if (gameManager.tray[i] != null) trayView.animateSlotAppear(i)
            }
        }

        tvScore.text     = "0"
        tvHighScore.text = gameManager.score.highScore.toString()
    }

    // ── Drag ──────────────────────────────────────────────────────────────

    private fun setupDrag() {
        dragCoordinator = DragCoordinator(
            root  = findViewById(R.id.rootLayout),
            tray  = trayView,
            board = boardView
        )
        trayView.onDragStarted = { idx, block, rawX, rawY ->
            dragCoordinator.beginDrag(idx, block, rawX, rawY)
        }
        boardView.onBlockDropped = { trayView.refresh() }
    }

    private fun setupButtons() {
        btnRestart.setOnClickListener { restartGame() }
        btnPlayAgain.setOnClickListener { restartGame() }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (dragCoordinator.isDragging) {
            val consumed = dragCoordinator.onDispatchTouchEvent(ev)
            if (consumed) return true
        }
        return super.dispatchTouchEvent(ev)
    }

    // ── Actions ───────────────────────────────────────────────────────────

    private fun restartGame() {
        gameOverLayout.visibility = View.GONE
        gameManager.restart()
        boardView.invalidate()
        trayView.refresh()
        tvScore.text     = "0"
        tvHighScore.text = gameManager.score.highScore.toString()
    }

    // ── UI helpers ────────────────────────────────────────────────────────

    private fun showCombo(label: String) {
        tvCombo.text       = label
        tvCombo.visibility = View.VISIBLE
        tvCombo.alpha      = 1f
        tvCombo.scaleX     = 0.5f
        tvCombo.scaleY     = 0.5f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(tvCombo, "scaleX", 0.5f, 1.25f, 1f),
                ObjectAnimator.ofFloat(tvCombo, "scaleY", 0.5f, 1.25f, 1f),
                ObjectAnimator.ofFloat(tvCombo, "alpha",  1f, 1f, 0f).apply { startDelay = 700 }
            )
            duration = 900; start()
        }
        tvCombo.postDelayed({ tvCombo.visibility = View.INVISIBLE }, 1000)
    }

    private fun showGameOver() {
        tvFinalScore.text = "Score: ${gameManager.score.score}"
        gameOverLayout.alpha = 0f
        gameOverLayout.visibility = View.VISIBLE
        gameOverLayout.animate().alpha(1f).setDuration(400).start()
    }
}
