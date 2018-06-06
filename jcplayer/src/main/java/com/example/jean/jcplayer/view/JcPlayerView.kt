package com.example.jean.jcplayer.view

import android.content.Context
import android.content.res.TypedArray
import android.graphics.PorterDuff
import android.os.Build
import android.support.v4.content.res.ResourcesCompat
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import com.daimajia.androidanimations.library.Techniques
import com.daimajia.androidanimations.library.YoYo
import com.example.jean.jcplayer.JcPlayerManager
import com.example.jean.jcplayer.R
import com.example.jean.jcplayer.general.JcStatus
import com.example.jean.jcplayer.general.PlayerUtil.toTimeSongString
import com.example.jean.jcplayer.general.errors.AudioListNullPointerException
import com.example.jean.jcplayer.general.errors.OnInvalidPathListener
import com.example.jean.jcplayer.model.JcAudio
import com.example.jean.jcplayer.service.JcPlayerManagerListener
import kotlinx.android.synthetic.main.view_jcplayer.view.*


/**
 * This class is the JcAudio View. Handles user interactions and communicate with [JcPlayerManager].
 * @author Jean Carlos (Github: @jeancsanchez)
 * @date 12/07/16.
 * Jesus loves you.
 */
class JcPlayerView : LinearLayout, View.OnClickListener, SeekBar.OnSeekBarChangeListener, JcPlayerManagerListener {

    private val jcPlayerManager: JcPlayerManager by lazy {
        JcPlayerManager.getInstance(context).get()!!
    }

    val myPlaylist: List<JcAudio>?
        get() = jcPlayerManager.playlist

    val isPlaying: Boolean
        get() = jcPlayerManager.isPlaying

    val isPaused: Boolean
        get() = jcPlayerManager.isPaused

    val currentAudio: JcAudio?
        get() = jcPlayerManager.currentAudio

    companion object {
        private const val PULSE_ANIMATION_DURATION = 200
        private const val TITLE_ANIMATION_DURATION = 600
    }

    private val onInvalidPathListener = object : OnInvalidPathListener {
        override fun onPathError(jcAudio: JcAudio) {
            dismissProgressBar()
        }
    }

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()

        context.theme
                .obtainStyledAttributes(attrs, R.styleable.JcPlayerView, 0, 0)
                .also { setAttributes(it) }
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init()

        context.theme
                .obtainStyledAttributes(attrs, R.styleable.JcPlayerView, defStyle, 0)
                .also { setAttributes(it) }
    }

    private fun init() {
        View.inflate(context, R.layout.view_jcplayer, this)

        btnNext?.setOnClickListener(this)
        btnPrev?.setOnClickListener(this)
        btnPlay?.setOnClickListener(this)
        btnPause?.setOnClickListener(this)
        seekBar?.setOnSeekBarChangeListener(this)
    }

    private fun setAttributes(attrs: TypedArray) {
        val defaultColor = ResourcesCompat.getColor(resources, android.R.color.black, null)

        txtCurrentMusic?.setTextColor(attrs.getColor(R.styleable.JcPlayerView_text_audio_title_color, defaultColor))
        txtCurrentDuration?.setTextColor(attrs.getColor(R.styleable.JcPlayerView_text_audio_current_duration_color, defaultColor))
        txtDuration?.setTextColor(attrs.getColor(R.styleable.JcPlayerView_text_audio_duration_color, defaultColor))

        progressBarPlayer?.indeterminateDrawable?.setColorFilter(attrs.getColor(R.styleable.JcPlayerView_progress_color, defaultColor), PorterDuff.Mode.SRC_ATOP)
        seekBar?.progressDrawable?.setColorFilter(attrs.getColor(R.styleable.JcPlayerView_seek_bar_color, defaultColor), PorterDuff.Mode.SRC_ATOP)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            seekBar?.thumb?.setColorFilter(attrs.getColor(R.styleable.JcPlayerView_seek_bar_color, defaultColor), PorterDuff.Mode.SRC_ATOP)
            // TODO: change thumb in older versions (14 and 15).
        }

        btnPlay.setBackgroundResource(attrs.getResourceId(R.styleable.JcPlayerView_button_play_image, R.drawable.ic_play))
        btnPlay.setColorFilter(attrs.getColor(R.styleable.JcPlayerView_button_play_color, defaultColor))

        btnPause.setBackgroundResource(attrs.getResourceId(R.styleable.JcPlayerView_button_pause_image, R.drawable.ic_pause))
        btnPause.setColorFilter(attrs.getColor(R.styleable.JcPlayerView_button_pause_color, defaultColor))

        btnNext?.setColorFilter(attrs.getColor(R.styleable.JcPlayerView_button_next_color, defaultColor))
        btnNext?.setBackgroundResource(attrs.getResourceId(R.styleable.JcPlayerView_button_next_image, R.drawable.ic_next))

        btnPrev?.setColorFilter(attrs.getColor(R.styleable.JcPlayerView_button_previous_color, defaultColor))
        btnPrev?.setBackgroundResource(attrs.getResourceId(R.styleable.JcPlayerView_button_previous_image, R.drawable.ic_previous))
    }

    /**
     * Initialize the playlist and controls.
     *
     * @param playlist List of JcAudio objects that you want play
     * @param jcPlayerManagerListener The view status jcPlayerManagerListener (optional)
     */
    fun initPlaylist(playlist: List<JcAudio>, jcPlayerManagerListener: JcPlayerManagerListener? = null) {
        /*Don't sort if the playlist have position number.
        We need to do this because there is a possibility that the user reload previous playlist
        from persistence storage like sharedPreference or SQLite.*/
        if (isAlreadySorted(playlist).not()) {
            sortPlaylist(playlist)
        }

        jcPlayerManager.playlist = playlist as ArrayList<JcAudio>
        jcPlayerManager.jcPlayerManagerListener = jcPlayerManagerListener
        jcPlayerManager.jcPlayerManagerListener = this
    }

    /**
     * Initialize an anonymous playlist with a default JcPlayer title for all audios
     *
     * @param playlist List of urls strings
     */
    fun initAnonPlaylist(playlist: List<JcAudio>) {
        generateTitleAudio(playlist, context.getString(R.string.track_number))
        initPlaylist(playlist)
    }

    /**
     * Initialize an anonymous playlist, but with a custom title for all audios
     *
     * @param playlist List of JcAudio files.
     * @param title    Default title for all audios
     */
    fun initWithTitlePlaylist(playlist: List<JcAudio>, title: String) {
        generateTitleAudio(playlist, title)
        initPlaylist(playlist)
    }

    /**
     * Add an audio for the playlist. We can track the JcAudio by
     * its id. So here we returning its id after adding to list.
     *
     * @param jcAudio audio file generated from [JcAudio]
     * @return id of jcAudio.
     */
    fun addAudio(jcAudio: JcAudio): Long {
        jcPlayerManager.playlist.let {
            val lastPosition = it.size

            jcAudio.id = (lastPosition + 1).toLong()
            jcAudio.position = lastPosition + 1

            if (it.contains(jcAudio).not()) {
                it.add(lastPosition, jcAudio)
            }

            return jcAudio.id!!
        }
    }

    /**
     * Remove an audio for the playlist
     *
     * @param jcAudio JcAudio object
     */
    fun removeAudio(jcAudio: JcAudio) {
        jcPlayerManager.playlist.let {
            if (it.contains(jcAudio)) {
                if (it.size > 1) {
                    // play next audio when currently played audio is removed.
                    if (jcPlayerManager.isPlaying) {
                        if (jcPlayerManager.currentAudio == jcAudio) {
                            it.remove(jcAudio)
                            pause()
                            resetPlayerInfo()
                        } else {
                            it.remove(jcAudio)
                        }
                    } else {
                        it.remove(jcAudio)
                    }
                } else {
                    //TODO: Maybe we need jcPlayerManager.stopPlay() for stopping the player
                    it.remove(jcAudio)
                    pause()
                    resetPlayerInfo()
                }
            }
        }
    }

    /**
     * Plays the give audio.
     * @param jcAudio The audio to be played.
     */
    fun playAudio(jcAudio: JcAudio) {
        showProgressBar()

        jcPlayerManager.playlist.let {
            if (it.contains(jcAudio).not()) {
                it.add(jcAudio)
            }


            jcPlayerManager.playAudio(jcAudio)
        }
    }

    /**
     * Shows the play button on player.
     */
    private fun showPlayButton() {
        btnPlay?.visibility = View.VISIBLE
        btnPause?.visibility = View.GONE
    }

    /**
     * Shows the pause button on player.
     */
    private fun showPauseButton() {
        btnPlay?.visibility = View.GONE
        btnPause?.visibility = View.VISIBLE
    }

    /**
     * Goes to next audio.
     */
    fun next() {
        jcPlayerManager.let { player ->
            player.currentAudio?.let {
                resetPlayerInfo()
                showProgressBar()

                try {
                    player.nextAudio()
                } catch (e: AudioListNullPointerException) {
                    dismissProgressBar()
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Continues the current audio.
     */
    fun continueAudio() {
        showProgressBar()

        try {
            jcPlayerManager.continueAudio()
        } catch (e: AudioListNullPointerException) {
            dismissProgressBar()
            e.printStackTrace()
        }
    }

    /**
     * Pauses the current audio.
     */
    fun pause() {
        jcPlayerManager.pauseAudio()
    }


    /**
     * Goes to precious audio.
     */
    fun previous() {
        resetPlayerInfo()
        showProgressBar()

        try {
            jcPlayerManager.previousAudio()
        } catch (e: AudioListNullPointerException) {
            dismissProgressBar()
            e.printStackTrace()
        }

    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.btnPlay ->
                btnPlay?.let {
                    YoYo.with(Techniques.Pulse)
                            .duration(PULSE_ANIMATION_DURATION.toLong())
                            .playOn(it)

                    continueAudio()
                }

            R.id.btnPause -> {
                btnPause?.let {
                    YoYo.with(Techniques.Pulse)
                            .duration(PULSE_ANIMATION_DURATION.toLong())
                            .playOn(it)
                    pause()
                }
            }

            R.id.btnNext ->
                btnNext?.let {
                    YoYo.with(Techniques.Pulse)
                            .duration(PULSE_ANIMATION_DURATION.toLong())
                            .playOn(it)
                    next()
                }

            else -> /* Previous button case */
                btnPrev?.let {
                    YoYo.with(Techniques.Pulse)
                            .duration(PULSE_ANIMATION_DURATION.toLong())
                            .playOn(it)
                    previous()
                }
        }
    }

    /**
     * Create a notification player with same playlist with a custom icon.
     *
     * @param iconResource icon path.
     */
    fun createNotification(iconResource: Int) {
        jcPlayerManager.createNewNotification(iconResource)
    }

    /**
     * Create a notification player with same playlist with a default icon
     */
    fun createNotification() {
        jcPlayerManager.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // For light theme
                it.createNewNotification(R.drawable.ic_notification_default_black)
            } else {
                // For dark theme
                it.createNewNotification(R.drawable.ic_notification_default_white)
            }
        }
    }

    override fun onPreparedAudio(status: JcStatus) {
        dismissProgressBar()
        resetPlayerInfo()
        onUpdateTitle(status.jcAudio.title)

        val duration = status.duration.toInt()
        seekBar?.post { seekBar?.max = duration }
        txtDuration?.post { txtDuration?.text = toTimeSongString(duration) }
    }

    override fun onProgressChanged(seekBar: SeekBar, i: Int, fromUser: Boolean) {
        jcPlayerManager.let {
            if (fromUser) {
                it.seekTo(i)
            }
        }
    }

    override fun onCompletedAudio() {
        resetPlayerInfo()

        try {
            jcPlayerManager.nextAudio()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPaused(status: JcStatus) {
        showPlayButton()
    }

    override fun onContinueAudio(status: JcStatus) {
        dismissProgressBar()
    }

    override fun onPlaying(status: JcStatus) {
        dismissProgressBar()
        showPauseButton()
    }

    override fun onTimeChanged(status: JcStatus) {
        val currentPosition = status.currentPosition.toInt()
        seekBar?.post { seekBar?.progress = currentPosition }
        txtCurrentDuration?.post { txtCurrentDuration?.text = toTimeSongString(currentPosition) }
    }

    override fun onJcpError(throwable: Throwable) {
        throw  throwable
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        showProgressBar()
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        dismissProgressBar()
    }

    /**
     * Kills the player
     */
    fun kill() {
        jcPlayerManager.kill()
    }

    private fun showProgressBar() {
        progressBarPlayer?.visibility = ProgressBar.VISIBLE
        btnPlay?.visibility = Button.GONE
        btnNext?.isClickable = false
        btnPrev?.isClickable = false
    }

    private fun dismissProgressBar() {
        progressBarPlayer?.visibility = ProgressBar.GONE
        showPauseButton()
        btnNext?.isClickable = true
        btnPrev?.isClickable = true
    }

    private fun onUpdateTitle(title: String) {
        txtCurrentMusic?.let {
            it.visibility = View.VISIBLE
            YoYo.with(Techniques.FadeInLeft)
                    .duration(TITLE_ANIMATION_DURATION.toLong())
                    .playOn(it)

            it.post { it.text = title }
        }
    }

    private fun resetPlayerInfo() {
        txtCurrentMusic?.post { txtCurrentMusic.text = "" }
        seekBar?.post { seekBar?.progress = 0 }
        txtDuration?.post { txtDuration.text = context.getString(R.string.play_initial_time) }
        txtCurrentDuration?.post { txtCurrentDuration.text = context.getString(R.string.play_initial_time) }
    }

    /**
     * Sorts the playlist.
     */
    private fun sortPlaylist(playlist: List<JcAudio>) {
        for (i in playlist.indices) {
            val jcAudio = playlist[i]
            jcAudio.id = i.toLong()
            jcAudio.position = i
        }
    }

    /**
     * Check if playlist already sorted or not.
     * We need to check because there is a possibility that the user reload previous playlist
     * from persistence storage like sharedPreference or SQLite.
     *
     * @param playlist list of JcAudio
     * @return true if sorted, false if not.
     */
    private fun isAlreadySorted(playlist: List<JcAudio>?): Boolean {
        // If there is position in the first audio, then playlist is already sorted.
        return playlist?.let { it[0].position != -1 } == true
    }

    /**
     * Generates a default audio title for each audio on list.
     * @param playlist The audio list.
     * @param title The default title.
     */
    private fun generateTitleAudio(playlist: List<JcAudio>, title: String) {
        for (i in playlist.indices) {
            if (title == context.getString(R.string.track_number)) {
                playlist[i].title = context.getString(R.string.track_number) + " " + (i + 1).toString()
            } else {
                playlist[i].title = title
            }
        }
    }
}
