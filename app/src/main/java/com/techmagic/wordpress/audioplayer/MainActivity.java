package com.techmagic.wordpress.audioplayer;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private MediaPlayerService player;
    private boolean serviceBound = false;
    private ArrayList<Audio> audioList;
    public static final String Broadcast_PLAY_NEW_AUDIO = "com.techmagic.wordpress.audioplayer.PlayNewAudio";
    private ImageView collapsingImageView;
    private int imageIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        collapsingImageView = (ImageView) findViewById(R.id.collapsingImageView);

        loadCollapsingImage(imageIndex);
        //playAudio("https://upload.wikimedia.org/wikipedia/commons/6/6c/Grieg_Lyric_Pieces_Kobold.ogg");
        loadAudio();
        initRecyclerView();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (imageIndex == 4) {
                    imageIndex = 0;
                    loadCollapsingImage(imageIndex);
                } else {
                    loadCollapsingImage(++imageIndex);
                }
            }
        });
        /*Play the first audio in the arrayList*//*
        playAudio(audioList.get(0).getData());*/
    }

    private void initRecyclerView(){
        if (audioList.size() > 0){
            RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recyclerview);
            RecyclerView_Adapter adapter = new RecyclerView_Adapter(audioList,getApplication());
            recyclerView.setAdapter(adapter);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.addOnItemTouchListener(new CustomTouchListener(this, new onItemClickListener() {
                @Override
                public void onClick(View view, int index) {
                    playAudio(index);
                }
            }));
        }
    }

    private void loadCollapsingImage(int i){
        TypedArray array = getResources().obtainTypedArray(R.array.images);
        collapsingImageView.setImageDrawable(array.getDrawable(i));
        array.recycle();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings){
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*Binding this client to the AudioPlayer Service*/
    ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            /*We have bound to LocalService, cast the IBinder and get LocalService Instance*/
            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) iBinder;
            player = binder.getService();
            serviceBound = true;
            Toast.makeText(MainActivity.this, "Service Bound", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            serviceBound = false;
        }
    };

    private void playAudio(int audioIndex){
        /*Check is service is active*/
        if (!serviceBound){
            /*Store Serializable audioList to SharedPreference*/
            StorageUtil storage = new StorageUtil(getApplicationContext());
            storage.storeAudio(audioList);
            storage.storeAudioIndex(audioIndex);

            Intent intent = new Intent(MainActivity.this,MediaPlayerService.class);
            startService(intent);
            bindService(intent,connection, Context.BIND_AUTO_CREATE);
        }else {
            /*Store the new audioIndex to SharedPreference*/
            StorageUtil storage = new StorageUtil(getApplicationContext());
            storage.storeAudioIndex(audioIndex);

            /*Service is active*/
            /*Send a broadcast to the service PLAY_NEW_AUDIO*/
            Intent intent = new Intent(Broadcast_PLAY_NEW_AUDIO);
            sendBroadcast(intent);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("ServiceState",serviceBound);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serviceBound = savedInstanceState.getBoolean("ServiceState");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound){
            unbindService(connection);
            /*Service is active*/
            player.stopSelf();
        }
    }

    private void loadAudio(){
        ContentResolver contentResolver = getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";
        Cursor cursor = contentResolver.query(uri,null,selection,null,sortOrder);

        if (cursor != null && cursor.getCount() > 0){
            audioList = new ArrayList<>();
            while (cursor.moveToNext()){
                String data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
                String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                String album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
                String artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
                Bitmap album_art = getAlbumArt(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)));

                /*Save to audioList*/
                audioList.add(new Audio(data,title,album,artist,album_art));
            }
            cursor.close();
        }
    }

    private Bitmap getAlbumArt(String albumId){
        Bitmap bitmap = null;
        ContentResolver contentResolver = getContentResolver();
        Uri uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Albums._ID + "=" + albumId;
        Cursor cursor = contentResolver.query(uri,null,selection,null,null);
        if (cursor != null && cursor.getCount() > 0){
            while (cursor.moveToNext()){
                String album_art = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART));
                bitmap = stringToBitMap(album_art);
            }
            cursor.close();
        }
        return bitmap;
    }

    public Bitmap stringToBitMap(String encodedString) {
        try {
            File imgFile = new File(encodedString);
            if (imgFile.exists()){
                return BitmapFactory.decodeFile(imgFile.getAbsolutePath());
            }
        } catch (Exception e) {
            e.getMessage();
            return null;
        }
        return null;
    }
}
