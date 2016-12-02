package com.simplemobiletools.musicplayer.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import com.simplemobiletools.filepicker.extensions.toast
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.SongAdapter
import com.simplemobiletools.musicplayer.extensions.getColoredIcon
import com.simplemobiletools.musicplayer.extensions.getTimeString
import com.simplemobiletools.musicplayer.extensions.sendIntent
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Song
import com.simplemobiletools.musicplayer.services.MusicService
import com.squareup.otto.Bus
import com.squareup.otto.Subscribe
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.*

class MainActivity : SimpleActivity(), View.OnTouchListener, MediaScannerConnection.OnScanCompletedListener, SeekBar.OnSeekBarChangeListener {
    companion object {
        private val STORAGE_PERMISSION = 1

        lateinit var mBus: Bus
        private var mSongs: List<Song> = ArrayList()
        private var mSnackbar: Snackbar? = null
        private var mToBeDeleted: MutableList<String>? = null
        private var mPlayBitmap: Bitmap? = null
        private var mPauseBitmap: Bitmap? = null

        private var mIsNumericProgressShown = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mBus = BusProvider.instance
        mBus.register(this)
        progressbar.setOnSeekBarChangeListener(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            initializePlayer()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), STORAGE_PERMISSION)
        }

        previous_btn.setOnClickListener { sendIntent(PREVIOUS) }
        play_pause_btn.setOnClickListener { sendIntent(PLAYPAUSE) }
        next_btn.setOnClickListener { sendIntent(NEXT) }
    }

    override fun onResume() {
        super.onResume()
        mIsNumericProgressShown = mConfig.isNumericProgressEnabled
        setupIconColors()
        song_progress.visibility = if (mIsNumericProgressShown) View.VISIBLE else View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        menu.findItem(R.id.enable_song_repetition).isVisible = !mConfig.repeatSong
        menu.findItem(R.id.disable_song_repetition).isVisible = mConfig.repeatSong
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                startActivity(Intent(applicationContext, SettingsActivity::class.java))
                true
            }
            R.id.about -> {
                startActivity(Intent(applicationContext, AboutActivity::class.java))
                true
            }
            R.id.enable_song_repetition -> {
                toggleSongRepetition(true)
                true
            }
            R.id.disable_song_repetition -> {
                toggleSongRepetition(false)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializePlayer()
            } else {
                toast(R.string.no_permissions)
            }
        }
    }

    private fun toggleSongRepetition(enable: Boolean) {
        mConfig.repeatSong = enable
        invalidateOptionsMenu()
    }

    private fun initializePlayer() {
        mToBeDeleted = ArrayList<String>()
        songs_list.setOnTouchListener(this)
        sendIntent(INIT)
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    private fun setupIconColors() {
        val res = resources
        val color = song_title.currentTextColor
        previous_btn.setImageBitmap(res.getColoredIcon(color, R.mipmap.previous))
        next_btn.setImageBitmap(res.getColoredIcon(color, R.mipmap.next))
        mPlayBitmap = res.getColoredIcon(color, R.mipmap.play)
        mPauseBitmap = res.getColoredIcon(color, R.mipmap.pause)
    }

    private fun songPicked(pos: Int) {
        Intent(this, MusicService::class.java).apply {
            putExtra(SONG_POS, pos)
            action = PLAYPOS
            startService(this)
        }
    }

    private fun updateSongInfo(song: Song?) {
        if (song != null) {
            song_title.text = song.title
            song_artist.text = song.artist
            progressbar.max = song.duration
            progressbar.progress = 0
        } else {
            song_title.text = ""
            song_artist.text = ""
        }
    }

    private fun fillSongsListView(songs: ArrayList<Song>) {
        mSongs = songs
        val adapter = SongAdapter(this, songs) {
            songPicked(it)
        }
        songs_list.adapter = adapter
    }

    override fun onPause() {
        super.onPause()
        deleteSongs()
    }

    override fun onDestroy() {
        super.onDestroy()
        mConfig.isFirstRun = false
        mBus.unregister(this)
    }

    @Subscribe
    fun songChangedEvent(event: Events.SongChanged) {
        updateSongInfo(event.song)
    }

    @Subscribe
    fun songStateChanged(event: Events.SongStateChanged) {
        play_pause_btn.setImageBitmap(if (event.isPlaying) mPauseBitmap else mPlayBitmap)
    }

    @Subscribe
    fun playlistUpdated(event: Events.PlaylistUpdated) {
        fillSongsListView(event.songs)
    }

    /*override fun onItemCheckedStateChanged(mode: ActionMode, position: Int, id: Long, checked: Boolean) {
        if (checked) {
            mSelectedItemsCnt++
        } else {
            mSelectedItemsCnt--
        }

        if (mSelectedItemsCnt > 0) {
            mode.title = mSelectedItemsCnt.toString()
        }

        mode.invalidate()
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.cab, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val menuItem = menu.findItem(R.id.cab_edit)
        menuItem.isVisible = mSelectedItemsCnt == 1
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.cab_edit -> {
                displayEditDialog()
                mode.finish()
                true
            }
            R.id.cab_delete -> {
                prepareForDeleting()
                mode.finish()
                true
            }
            R.id.cab_properties -> {
                showProperties()
                true
            }
            else -> false
        }
    }

    private fun displayEditDialog() {
        val songIndex = selectedSongIndex
        if (songIndex == -1)
            return

        val selectedSong = mSongs[songIndex]

        EditDialog(this, selectedSong)
    }*/

    private fun notifyDeletion(cnt: Int) {
        val coordinator = findViewById(R.id.coordinator_layout) as CoordinatorLayout
        val res = resources
        val msg = res.getQuantityString(R.plurals.songs_deleted, cnt, cnt)
        mSnackbar = Snackbar.make(coordinator, msg, Snackbar.LENGTH_INDEFINITE)
        mSnackbar!!.apply {
            setAction(res.getString(R.string.undo), undoDeletion)
            setActionTextColor(Color.WHITE)
            show()
        }
        updateSongsList()
    }

    private fun updateSongsList() {
        val deletedSongs = arrayOfNulls<String>(mToBeDeleted!!.size)
        mToBeDeleted!!.toTypedArray()
        Intent(this, MusicService::class.java).apply {
            putExtra(DELETED_SONGS, deletedSongs)
            putExtra(UPDATE_ACTIVITY, true)
            action = REFRESH_LIST
            startService(this)
        }
    }

    private fun deleteSongs() {
        if (mToBeDeleted == null || mToBeDeleted!!.isEmpty())
            return

        mSnackbar?.dismiss()

        val updatedFiles = ArrayList<String>()
        for (delPath in mToBeDeleted!!) {
            val file = File(delPath)
            if (file.exists()) {
                if (file.delete()) {
                    updatedFiles.add(delPath)
                }
            }
        }

        val deletedPaths = updatedFiles.toTypedArray()
        MediaScannerConnection.scanFile(this, deletedPaths, null, null)
        mToBeDeleted!!.clear()
    }

    private val undoDeletion = View.OnClickListener {
        mToBeDeleted!!.clear()
        mSnackbar!!.dismiss()
        updateSongsList()
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (mSnackbar?.isShown == true) {
            deleteSongs()
        }

        return false
    }

    override fun onScanCompleted(path: String, uri: Uri) {
        sendIntent(REFRESH_LIST)
    }

    @Subscribe
    fun songChangedEvent(event: Events.ProgressUpdated) {
        progressbar.progress = event.progress
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (mIsNumericProgressShown) {
            val duration = progressbar.max.getTimeString()
            val formattedProgress = progress.getTimeString()

            val progressText = String.format(resources.getString(R.string.progress), formattedProgress, duration)
            song_progress.text = progressText
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {

    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        Intent(this, MusicService::class.java).apply {
            putExtra(PROGRESS, seekBar.progress)
            action = SET_PROGRESS
            startService(this)
        }
    }
}