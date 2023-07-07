package com.example.ggmusic;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.IOException;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String ACTION_MUSIC_START =
            "com.example.ggmusic.ACTION_MUSIC_START";

    public static final String ACTION_MUSIC_STOP =
            "com.example.ggmusic.ACTION_MUSIC_STOP";

    private MusicReceiver musicReceiver;
    private MusicService mService;
    private boolean mBound = false;
    private ContentResolver mContentResolver;
    private ListView mPlaylist;
    private MediaCursorAdapter mCursorAdapter;
    private static final String TAG = MainActivity.class.getSimpleName();
    private Boolean mPlayStatus = true;
    private ImageView ivPlay;
    private BottomNavigationView navigation;
    private TextView tvBottomTitle;
    private TextView tvBottomArtist;
    private ImageView ivAlbumThumbnail;

    public static final int UPDATE_PROGRESS = 1;

    private ProgressBar pbProgress;
    private MediaPlayer mMediaPlayer = null;

    public static final String DATA_URI =
            "com.example.ggmusic.DATA_URI";
    public static final String TITLE =
            "com.example.ggmusic.TITLE";
    public static final String ARTIST =
            "com.example.ggmusic.ARTIST";


    private final String SELECTION =
            MediaStore.Audio.Media.IS_MUSIC + " = ? " + " AND " +
                    MediaStore.Audio.Media.MIME_TYPE + " LIKE ? ";
    private final String[] SELECTION_ARGS = {
            Integer.toString(1),
            "audio/mpeg"};

    private final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private ListView.OnItemClickListener itemClickListener = new ListView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            Cursor cursor = mCursorAdapter.getCursor();
            if (cursor != null && cursor.moveToPosition(i)) {

                int titleIndex = cursor.getColumnIndex(
                        MediaStore.Audio.Media.TITLE);
                int artistIndex = cursor.getColumnIndex(
                        MediaStore.Audio.Media.ARTIST);
                int albumIdIndex = cursor.getColumnIndex(
                        MediaStore.Audio.Media.ALBUM_ID);
                int dataIndex = cursor.getColumnIndex(
                        MediaStore.Audio.Media.DATA);


                String title = cursor.getString(titleIndex);
                String artist = cursor.getString(artistIndex);
                Long albumId = cursor.getLong(albumIdIndex);
                String data = cursor.getString(dataIndex);

                Uri dataUri = Uri.parse(data);

                Intent serviceIntent = new Intent(MainActivity.this, MusicService.class);
                serviceIntent.putExtra(MainActivity.DATA_URI, data);
                serviceIntent.putExtra(MainActivity.TITLE, title);
                serviceIntent.putExtra(MainActivity.ARTIST, artist);
                startService(serviceIntent);


                navigation.setVisibility(View.VISIBLE);

                if (tvBottomTitle != null) {
                    tvBottomTitle.setText(title);
                }

                if (tvBottomArtist != null) {
                    tvBottomArtist.setText(artist);
                }

                Uri albumUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                        albumId);

                cursor = mContentResolver.query(
                        albumUri,
                        null,
                        null,
                        null,
                        null);

                if (cursor != null && cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    int albumArtIndex = cursor.getColumnIndex(
                            MediaStore.Audio.Albums.ALBUM_ART);
                    String albumArt = cursor.getString(
                            albumArtIndex);

                    Glide.with(MainActivity.this)
                            .load(albumArt)
                            .into(ivAlbumThumbnail);
                    cursor.close();
                }


            }
        }

    };
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_PROGRESS:
                    int position = msg.arg1;
                    pbProgress.setProgress(position);
                    break;
                default:
                    break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPlaylist = findViewById(R.id.lv_playlist);

        mContentResolver = getContentResolver();
        mCursorAdapter = new MediaCursorAdapter(MainActivity.this);
        mPlaylist.setAdapter(mCursorAdapter);

        navigation = findViewById(R.id.navigation);
        LayoutInflater.from(MainActivity.this).inflate(R.layout.bottom_media_toolbar, navigation, true);

        ivPlay = navigation.findViewById(R.id.iv_play);
        tvBottomTitle = navigation.findViewById(R.id.tv_bottom_title);
        tvBottomArtist = navigation.findViewById(R.id.tv_bottom_artist);
        ivAlbumThumbnail = navigation.findViewById(R.id.iv_thumbnail);
        pbProgress = navigation.findViewById(R.id.progress);

        if (ivPlay != null) {
            ivPlay.setOnClickListener(MainActivity.this);
        }

        navigation.setVisibility(View.GONE);

        mPlaylist.setOnItemClickListener(itemClickListener);

        if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer();
            Log.d(TAG, "MediaPlayer insance create!");
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    MainActivity.this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {
            } else {
                requestPermissions(PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            }
        } else {
            initPlaylist();
        }

        musicReceiver = new MusicReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_MUSIC_START);
        intentFilter.addAction(ACTION_MUSIC_STOP);
        registerReceiver(musicReceiver, intentFilter);

    }

    private void initPlaylist() {
        scanSdCard();
        Cursor mCursor = mContentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                null,
                SELECTION,
                SELECTION_ARGS,
                MediaStore.Audio.Media.DEFAULT_SORT_ORDER
        );
        if (mCursor != null) {
            mCursorAdapter.swapCursor(mCursor);
            mCursorAdapter.notifyDataSetChanged();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_EXTERNAL_STORAGE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initPlaylist();
                }
                break;
            default:
                break;
        }
    }


    protected void onDestroy() {
        unregisterReceiver(musicReceiver);
        super.onDestroy();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.iv_play) {
            mPlayStatus = !mPlayStatus;
            if (mPlayStatus == true) {
                mService.play();
                ivPlay.setImageResource(
                        R.drawable.baseline_pause_circle_outline_24);
            } else {
                mService.pause();
                ivPlay.setImageResource(
                        R.drawable.baseline_play_circle_outline_24);
            }
        }
    }

    private ServiceConnection mConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(
                ComponentName componentName, IBinder iBinder) {
            MusicService.MusicServiceBinder binder =
                    (MusicService.MusicServiceBinder) iBinder;

            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(
                ComponentName componentName) {
            mService = null;
            mBound = false;
        }
    };



    @Override
    protected void onStart() {
        super.onStart();

        Intent intent = new Intent(MainActivity.this,
                MusicService.class);
        bindService(intent, mConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        unbindService(mConn);
        mBound = false;

        super.onStop();
    }

    private class MusicProgressRunnable implements Runnable {
        public MusicProgressRunnable() {
        }

        @Override
        public void run() {
            boolean mThreadWorking = true;
            while (mThreadWorking) {
                try {
                    if (mService != null) {
                        int position =
                                mService.getCurrentPosition();
                        Message message = new Message();
                        message.what = UPDATE_PROGRESS;
                        message.arg1 = position;
                        mHandler.sendMessage(message);
                    }
                    mThreadWorking = mService.isPlaying();
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }
    }

    public class MusicReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (mService != null) {
                pbProgress.setMax(mService.getDuration());

                new Thread(new MusicProgressRunnable()).start();
            }
        }
    }

    private void checkPermission() {
        //检查权限（NEED_PERMISSION）是否被授权 PackageManager.PERMISSION_GRANTED表示同意授权
        //这里是android11以上的读写权限申请
        //需要通过Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION跳转到权限设置打开权限
        AlertDialog dialog = null;
        boolean havePermission;
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                if (dialog != null) {
                    dialog.dismiss();
                    dialog = null;
                }
                dialog = new AlertDialog.Builder(this)
                        .setTitle("提示")//设置标题
                        .setMessage("请开启文件访问权限，否则无法正常使用本应用！")
                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int i) {
                                dialog.dismiss();
                            }
                        })
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                startActivity(intent);
                            }
                        }).create();
                dialog.show();
            } else {
                havePermission = true;
            }
        } else {
            //这里就是android7到android11的权限申请
            //直接通过ActivityCompat.requestPermissions就可以
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    //申请权限
                    if (dialog != null) {
                        dialog.dismiss();
                        dialog = null;
                    }
                    dialog = new AlertDialog.Builder(this)
                            .setTitle("提示")//设置标题
                            .setMessage("请开启文件访问权限，否则无法正常使用应用！")
                            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    ActivityCompat.requestPermissions(MainActivity.this, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
                                }
                            }).create();
                    dialog.show();
                } else {
                    havePermission = true;

                }
            } else {
                //android7以下的不需要动态申请权限
                havePermission = true;

            }
        }
    }

    private void scanSdCard(){
        IntentFilter intentfilter = new IntentFilter( Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentfilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentfilter.addDataScheme("file");
        ScanSdReceiver scanSdReceiver = new ScanSdReceiver();
        registerReceiver(scanSdReceiver, intentfilter);
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + Environment.getExternalStorageDirectory())));
    }
}