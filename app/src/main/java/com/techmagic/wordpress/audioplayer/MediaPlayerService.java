package com.techmagic.wordpress.audioplayer;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.app.NotificationCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;

public class MediaPlayerService extends Service implements MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener, AudioManager.OnAudioFocusChangeListener {

    private MediaPlayer mediaPlayer;
    /*Path to Audio file*/
    private String mediaFile;
    private int resumePosition;
    private AudioManager audioManager;
    private boolean onGoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;
    /*List of available audio files*/
    private ArrayList<Audio> audioList;
    private int audioIndex = -1;
    /*An object of the current playing audio*/
    private Audio activeAudio;

    public static final String ACTION_PLAY = "com.techmagic.wordpress.audioplayer.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.techmagic.wordpress.audioplayer.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "com.techmagic.wordpress.audioplayer.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.techmagic.wordpress.audioplayer.ACTION_NEXT";
    public static final String ACTION_STOP = "com.techmagic.wordpress.audioplayer.ACTION_STOP";

    /*Media Session*/
    private MediaSessionManager mediaSessionManager;
    private MediaSessionCompat mediaSession;
    private MediaControllerCompat.TransportControls transportControls;

    /*Audio Player Notification ID*/
    private static final int NOTIFICATION_ID = 101;

    public MediaPlayerService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        /*Perform one time setup procedures
        * Manage incoming phone calls during playback
        * Pause MediaPlayer on Incoming call,
        * Resume on Hangup*/
        callStateListener();
        /*ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs -- BroadcastReceiver*/
        registerReceiver();
        /*Listen for new audio to play -- BroadcastReceiver*/
        registerNewAudio();
    }

    /*The System calls this method when an activity, requests the service to be started*/
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            /*Load data from sharedPreference*/
            StorageUtil storage = new StorageUtil(getApplicationContext());
            audioList = storage.loadAudio();
            audioIndex = storage.loadAudioIndex();

            if (audioIndex != -1 && audioIndex < audioList.size()){
                /*Index is in valid range*/
                activeAudio = audioList.get(audioIndex);
            }else {
                stopSelf();
            }
        }catch (NullPointerException e){
            stopSelf();
        }
        if (!requestAudioFocus()){
            /*couldn't gain focus*/
            stopSelf();
        }

        if (mediaSessionManager == null){
            try {
                initMediaSession();
                initMediaPlayer();
            }catch (RemoteException e){
                e.printStackTrace();
                stopSelf();
            }
            buildNotification(PlaybackStatus.PLAYING);
        }

        /*Handle intent action from MediaSession.TransportControls*/
        handleIncomingActions(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    /*Initialize Media Player*/
    private void initMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        /*SetUp MediaPlayer event listeners*/
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnInfoListener(this);
        /*Reset so that the media player is not pointing to another data source*/
        mediaPlayer.reset();

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mediaPlayer.setDataSource(activeAudio.getData());
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }
        mediaPlayer.prepareAsync();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null){
            stopMedia();
            mediaPlayer.release();
        }
        removeAudioFocus();
        /*Disable the PhoneStateListener*/
        if (phoneStateListener != null){
            telephonyManager.listen(phoneStateListener,PhoneStateListener.LISTEN_NONE);
        }
        removeNotification();
        /*Unregister BroadcastReceivers*/
        unregisterReceiver(receiver);
        unregisterReceiver(playNewAudio);

        /*Clear cached playlist*/
        new StorageUtil(getApplicationContext()).clearCachedAudioPlaylist();
    }

    private void playMedia() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    private void stopMedia() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }

    private void pauseMedia() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            resumePosition = mediaPlayer.getCurrentPosition();
        }
    }

    private void resumeMedia() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.seekTo(resumePosition);
            mediaPlayer.start();
        }
    }

    /*Binder given to clients*/
    private final IBinder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onAudioFocusChange(int focusState) {
        /*Invoked when the audio focus of the system is updated*/
        switch (focusState){
            case AudioManager.AUDIOFOCUS_GAIN:
                /*Resume Playback*/
                if (mediaPlayer == null){
                    initMediaPlayer();
                }else {
                    if (!mediaPlayer.isPlaying()){
                        mediaPlayer.start();
                    }
                    mediaPlayer.setVolume(1.0f,1.0f);
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                /*Lost focus for an unbounded amount of time: stop playback and release media player*/
                if (mediaPlayer.isPlaying()){
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                /*Lost focus for a short time, but we have to stop playback. we don't release media player
                * because playback is likely to resume*/
                if (mediaPlayer.isPlaying()){
                    mediaPlayer.pause();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                /*Lost focus for short time, but its ok to keep playing at an attenuated level*/
                if (mediaPlayer.isPlaying()){
                    mediaPlayer.setVolume(1.0f,1.0f);
                }
                break;
        }
    }

    private boolean requestAudioFocus(){
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
            /*Focus Gained*/
            return true;
        }
        /*Could not gain focus*/
        return false;
    }

    private boolean removeAudioFocus(){
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(this);
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mediaPlayer, int i) {

    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        /*Invoked when playback of a media source has completed*/
        stopMedia();
        /*Stop the service*/
        stopSelf();
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
        /*Invoked when there has been an error during an asynchronous operation*/
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.d("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN " + extra);
                break;
        }
        return false;
    }

    @Override
    public boolean onInfo(MediaPlayer mediaPlayer, int i, int i1) {
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        playMedia();
    }

    @Override
    public void onSeekComplete(MediaPlayer mediaPlayer) {

    }

    public class LocalBinder extends Binder {
        public MediaPlayerService getService() {
            return MediaPlayerService.this;
        }
    }

    /*Becoming noisy*/
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            /*Pause audio on ACTION_AUDIO_BECOMING_NOISY*/
            pauseMedia();
            buildNotification(PlaybackStatus.PAUSED);
        }
    };

    private void registerReceiver(){
        /*Register after getting audio focus*/
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(receiver,intentFilter);
    }

    /*Handle Incoming Phone Calls*/
    private void callStateListener(){
        /*Get the telephony manager*/
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        /*Starting listening to PhoneState changes*/
        phoneStateListener = new PhoneStateListener(){
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                /*If at least one call exists or the phone is ringing pause the media player*/
                switch (state){
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mediaPlayer != null){
                            pauseMedia();
                            onGoingCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        /*Phone idle start playing*/
                        if (mediaPlayer != null){
                            if (onGoingCall){
                                onGoingCall = false;
                                resumeMedia();
                            }
                        }
                        break;
                }
            }
        };
        /*Register the listener with the telephony manager listen for changes to the device call state*/
        telephonyManager.listen(phoneStateListener,PhoneStateListener.LISTEN_CALL_STATE);
    }

    private BroadcastReceiver playNewAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            /*Get the new media index from the share preference*/
            audioIndex = new StorageUtil(getApplicationContext()).loadAudioIndex();
            if (audioIndex != -1 && audioIndex < audioList.size()){
                /*Index is in valid range*/
                activeAudio = audioList.get(audioIndex);
            }else {
                stopSelf();
            }

            /*PLAY_NEW _AUDIO action received
            * reset media player to play the audio*/
            stopMedia();
            mediaPlayer.reset();
            initMediaPlayer();
            updateMetaData();
            buildNotification(PlaybackStatus.PLAYING);
        }
    };

    private void registerNewAudio(){
        IntentFilter intentFilter = new IntentFilter(MainActivity.Broadcast_PLAY_NEW_AUDIO);
        registerReceiver(playNewAudio,intentFilter);
    }

    private void initMediaSession() throws RemoteException{
        if (mediaSessionManager == null){
            mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            /*Create new Media Session*/
            mediaSession = new MediaSessionCompat(getApplicationContext(),"AudioPlayer");
            /*Get MediaSessions transport controls*/
            transportControls = mediaSession.getController().getTransportControls();
            /*Set MediaSession -> ready to receive media commands*/
            mediaSession.setActive(true);
            /*Indicate that the MediaSession handles transport control commands
            * through its MediaSessionCompact.Callback*/
            mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

            /*Set mediaSessions MetaData*/
            updateMetaData();

            /*Attach CallBack to receive MediaSession updates*/
            mediaSession.setCallback(new MediaSessionCompat.Callback(){
                @Override
                public void onPlay() {
                    super.onPlay();
                    resumeMedia();
                    buildNotification(PlaybackStatus.PLAYING);
                }

                @Override
                public void onPause() {
                    super.onPause();
                    pauseMedia();
                    buildNotification(PlaybackStatus.PAUSED);
                }

                @Override
                public void onSkipToNext() {
                    super.onSkipToNext();
                    skipToNext();
                    updateMetaData();
                    buildNotification(PlaybackStatus.PLAYING);
                }

                @Override
                public void onSkipToPrevious() {
                    super.onSkipToPrevious();
                    skipToPrevious();
                    updateMetaData();
                    buildNotification(PlaybackStatus.PLAYING);
                }

                @Override
                public void onStop() {
                    super.onStop();
                    removeNotification();
                    /*Stop the Service*/
                    stopSelf();
                }

                @Override
                public void onSeekTo(long pos) {
                    super.onSeekTo(pos);
                }
            });
        }
    }

    private void updateMetaData(){
        Bitmap albumArt = BitmapFactory.decodeResource(getResources(),R.drawable.image2);/*replace with media albumArt*/
        /*Update current metaData*/
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,albumArt)
        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,activeAudio.getArtist())
        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM,activeAudio.getAlbum())
        .putString(MediaMetadataCompat.METADATA_KEY_TITLE,activeAudio.getTitle()).build());
    }

    private void skipToNext(){
        if (audioIndex == audioList.size() -1){
            /*if last in playlist*/
            audioIndex = 0;
            activeAudio = audioList.get(audioIndex);
        }else {
            /*Get next in playlist*/
            activeAudio = audioList.get(++audioIndex);
        }

        /*Update Stored Index*/
        new StorageUtil(getApplicationContext()).storeAudioIndex(audioIndex);

        stopMedia();
        /*reset MediaPlayer*/
        mediaPlayer.reset();
        initMediaPlayer();
    }

    private void skipToPrevious(){
        if (audioIndex == 0){
            /*if first in playlist set index to the last of audioList*/
            audioIndex = audioList.size() - 1;
            activeAudio = audioList.get(audioIndex);
        }else {
            /*get previous in playlist*/
            activeAudio = audioList.get(--audioIndex);
        }

        /*Update stored index*/
        new StorageUtil(getApplicationContext()).storeAudioIndex(audioIndex);

        stopMedia();
        /*reset mediaPlayer*/
        mediaPlayer.reset();
        initMediaPlayer();
    }

    private void buildNotification(PlaybackStatus playbackStatus){
        int notificationAction = android.R.drawable.ic_media_pause;/*Needs to be initialised*/
        PendingIntent play_pauseAction = null;

        /*Build a new notification according to current state of the mediaPlayer*/
        if (playbackStatus == PlaybackStatus.PLAYING){
            notificationAction = android.R.drawable.ic_media_pause;
            /*Create pause action*/
            play_pauseAction = playBackAction(1);
        }else if (playbackStatus == PlaybackStatus.PAUSED){
            notificationAction = android.R.drawable.ic_media_play;
            /*Create play action*/
            play_pauseAction = playBackAction(0);
        }

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(),R.drawable.image2);/*replace with your own image*/
        /*Create a new notification*/
        NotificationCompat.Builder builder = (NotificationCompat.Builder) new NotificationCompat.Builder(this).setShowWhen(false)
                .setStyle(new NotificationCompat.MediaStyle()
                /*Attach our media session token*/
                .setMediaSession(mediaSession.getSessionToken())
                /*Show our playback controls in the compact notification view*/
                .setShowActionsInCompactView(0,1,2))
                /*set the notification color*/
                .setColor(getResources().getColor(R.color.colorPrimary))
                /*set the large and small icons*/
                .setLargeIcon(largeIcon)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                /*set notofication content information*/
                .setContentText(activeAudio.getArtist())
                .setContentTitle(activeAudio.getAlbum())
                .setContentInfo(activeAudio.getTitle())
                /*Add playback actions*/
                .addAction(android.R.drawable.ic_media_previous,"previous",playBackAction(3))
                .addAction(notificationAction,"pause",play_pauseAction)
                .addAction(android.R.drawable.ic_media_next,"next",playBackAction(2));

        ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID,builder.build());
    }

    private void removeNotification(){
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private PendingIntent playBackAction(int actionNumber){
        Intent playBackAction = new Intent(this,MediaPlayerService.class);
        switch (actionNumber){
            case 0:
                /*Play*/
                playBackAction.setAction(ACTION_PLAY);
                return PendingIntent.getService(this,actionNumber,playBackAction,0);
            case 1:
                /*Pause*/
                playBackAction.setAction(ACTION_PAUSE);
                return PendingIntent.getService(this,actionNumber,playBackAction,0);
            case 2:
                /*Next Track*/
                playBackAction.setAction(ACTION_NEXT);
                return PendingIntent.getService(this,actionNumber,playBackAction,0);
            case 3:
                /*Previous Track*/
                playBackAction.setAction(ACTION_PREVIOUS);
                return PendingIntent.getService(this,actionNumber,playBackAction,0);
            default:
                break;
        }
        return null;
    }

    private void handleIncomingActions(Intent playBackAction){
        if (playBackAction != null && playBackAction.getAction() != null){
            String actionString = playBackAction.getAction();
            switch (actionString){
                case ACTION_PLAY:
                    transportControls.play();
                    break;
                case ACTION_PAUSE:
                    transportControls.pause();
                    break;
                case ACTION_NEXT:
                    transportControls.skipToNext();
                    break;
                case ACTION_PREVIOUS:
                    transportControls.skipToPrevious();
                    break;
                case ACTION_STOP:
                    transportControls.stop();
                    break;
                default:
                    break;
            }
        }
    }
}
