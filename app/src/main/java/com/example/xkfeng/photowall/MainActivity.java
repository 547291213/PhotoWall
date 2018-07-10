package com.example.xkfeng.photowall;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.RequiresPermission;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.Pools;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.xkfeng.photowall.DiskLruCache.DiskLruCacheHelper;
import com.example.xkfeng.photowall.ImageViewZoom.ImageViewZoom;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.InvalidMarkException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private GridView gridView  ;

    private PhoteArrayAdapter arrayAdapter ;

    private final static int ReqCode = 1 ;

    DiskLruCacheHelper helper;

    private final static String TAG = "MainActivity" ;

    /*
       判断是否处于滑动状态   false表示否  true表示是
     */
    private static Boolean SCROLL_STATUS = false ;

    /*
        判断滑动方向  0表示静止  1表示向下  2表示向上
     */
    private static int SCROLL_DIRECTION = 0 ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        int check = ContextCompat.checkSelfPermission(MainActivity.this , Manifest.permission.WRITE_EXTERNAL_STORAGE ) ;
        if (check != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this , new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE} , ReqCode);
        }else {
            Toast.makeText(this , "已有存储权限" ,Toast.LENGTH_SHORT).show();

        }

        try {
            helper = new DiskLruCacheHelper(this , "kefeng") ;
            Log.i(TAG , "HELPER DIRECTORY : " + helper.getDirectory()) ;
        } catch (IOException e) {
            e.printStackTrace();
        }
        gridView = (GridView)findViewById(R.id.gv_photoGrid) ;
        arrayAdapter = new PhoteArrayAdapter(this , 0 , Images.imageThumbUrls ,gridView) ;
        gridView.setAdapter(arrayAdapter);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode)
        {
            case ReqCode :
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    Toast.makeText(this , "获取权限成功" ,Toast.LENGTH_SHORT).show();

                }
                else {
                    Toast.makeText(this , "权限获取失败，可能导致部分功能无法完整使用" ,Toast.LENGTH_SHORT).show();

                }
                break ;
            default:
                Toast.makeText(this , "未知错误" ,Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        arrayAdapter.cancelAllTask();
        try {
            if (helper != null )
            helper.delete();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public class PhoteArrayAdapter extends ArrayAdapter<String> implements AbsListView.OnScrollListener {
        /*
             记录所有正在下载或者等待的任务
        */
        private Set<PhoteArrayAdapter.AsynvPicTask> taskCollection;

        /*
        TAG  日志输出
         */
        private final static String TAG = "PhoteArrayAdapter" ;
        /*
            图片缓存的核心类
         */
        private LruCache<String, Bitmap> lruCache;



        /*
            GridView 实例 用于绑定OnScorllListener
         */
        private GridView gridView;

        /*
            第一张可见图片的下标
         */
        private int mFirstIndex;

        /*
            一屏可见的图片数目
         */
        private int mPicCount;

        /*
            是否是第一次打开程序，用于处理进入程序不滚动 不会进行下载的问题

         */
        private boolean mFirst;



        public PhoteArrayAdapter(@NonNull Context context, int resource, @NonNull String[] objects, GridView photoView) {
            super(context, resource, objects);
            /*
            初始化部分参数
             */
            gridView = photoView;
            taskCollection = new HashSet<>();
            mFirst = true;
            //获取程序的最大可用内存  单位为kb
            final int maxMemoty = (int) (Runtime.getRuntime().maxMemory() / 1024);
            //缓存 为可用内存的1/6
            final int cacheSize = maxMemoty / 6;
            lruCache = new LruCache<String, Bitmap>(cacheSize) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };

            //绑定监听器
            gridView.setOnScrollListener(this);
        }


        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            final String url = getItem(position);
            View view;
            if (convertView == null) {
                view = LayoutInflater.from(getContext()).inflate(R.layout.grid_item, null);

            } else {
                view = convertView;
            }

            /*
                 让列表在滑动的时候显示默认的图片，可以完全避免List卡顿的现象
             */
            if (SCROLL_STATUS)
            {
                final ImageView imageView = (ImageView) view.findViewById(R.id.iv_photo);
                imageView.setTag(url);
                imageView.setImageResource(R.drawable.loading);
                return  view ;
            }
            final ImageView imageView = (ImageView) view.findViewById(R.id.iv_photo);
            imageView.setTag(url);
            setIamgeView(url, imageView);


            return view;
        }


        /*
           给列表项设置图片，首先从缓存中去获取，如果没有则先设置一张默认的图片
         */
        private void setIamgeView(final String url, ImageView imageView) {
            /*
               首先从LruCache缓存中获取
             */
            Bitmap bitmap = getBitmapFromMemory(url);
            if (bitmap == null)
            {
                //LruCache缓存中没有，则再此从DiskLruCache中获取
                bitmap =  helper.getAsBitmap(url) ;
            }
            /*
               根据是否获取到Bitmap，来设置图片
             */
            if (bitmap != null) {
                Log.i(TAG, "getBitmapFromMemory " + bitmap) ;
                imageView.setImageBitmap(bitmap);
            } else {
                //从本地文件去获取图片
                if (getImagePath(url) != null)
                {
                    bitmap = BitmapFactory.decodeFile(getImagePath(url))  ;
                    if (url != null && bitmap != null)
                    imageView.setImageBitmap(bitmap);
                    else {
                        imageView.setImageResource(R.drawable.loading);
                    }
                }
                else
                {
                    imageView.setImageResource(R.drawable.loading);
                }

            }

        }

        /*
            将图片加载到LruCache
         */
        private void putBitmaoToMemory(String key, Bitmap bitmap) {
            if (getBitmapFromMemory(key) == null) {
                lruCache.put(key, bitmap);
            }
        }

        /*
            从LruCache中获取图片。
            如果获取失败则返回null
         */
        private Bitmap getBitmapFromMemory(String key) {
            return lruCache.get(key);
        }

        /*
           加载当前页面的图片
         */
        private void loadBitmaps(int firstVisibleItem, int visibleCount) {
            try {

                for (int i = firstVisibleItem; i < firstVisibleItem + visibleCount; i++) {

                    final String imageUrl = Images.imageThumbUrls[i];
                    /*
                          获取当前URL对应的图片
                          设置对应图片的点击事件
                     */
                    ImageView images = (ImageView) gridView.findViewWithTag(imageUrl);
                    images.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(getContext(), ImageActivity.class);
                            intent.putExtra("image_path", getImagePath(imageUrl));
                            getContext().startActivity(intent);
                        }
                    });

                     /*
                        1  从缓存中获取
                        2  从本地缓存中获取
                        3  从SD卡获取
                        4  从网络获取
                     */
                    //1  从缓存中获取
                    Bitmap bitmap = getBitmapFromMemory(imageUrl);
                    //缓存获取图片失败
                    if (bitmap == null) {
                        // 2  从本地缓存中获取
                        bitmap = helper.getAsBitmap(imageUrl) ;
                        if (bitmap != null)
                        {

                            ImageView imageView = (ImageView) gridView.findViewWithTag(imageUrl);
                            if (imageUrl != null && bitmap != null) {
                                imageView.setImageBitmap(bitmap);
                            }
                        }else {

                            /*
                              经过测试：从本地文件中去获取图片会出现OOM的错误
                             */
                            //3  从SD卡获取
                            //从本地文件去获取图片
//                            if (getImagePath(imageUrl) != null)
//                            {
//                                try {
//                                    ImageView imageView = (ImageView) gridView.findViewWithTag(imageUrl);
//                                    bitmap = BitmapFactory.decodeFile(getImagePath(imageUrl))  ;
//                                    if (imageUrl != null && bitmap != null) {
//                                        imageView.setImageBitmap(bitmap);
//                                    }
//                                }catch (Exception e)
//                                {
//                                    e.printStackTrace();
//                                }
//
//
//                            }
                            //4  从网络获取
                            AsynvPicTask task = new AsynvPicTask();
                            taskCollection.add(task);
                            task.execute(imageUrl);
                        }

                    } else {
                        //将缓存中获取的图片设置为ImageView的资源图片
                        ImageView imageView = (ImageView) gridView.findViewWithTag(imageUrl);
                        if (imageUrl != null && bitmap != null) {
                            imageView.setImageBitmap(bitmap);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /*
        建立 Htpp链接 并且下载Bitmap
         */
        private Bitmap downloadBitmaps(String imageUrl) {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;
            try {

                URL url = new URL(imageUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5 * 1000);
                connection.setReadTimeout(5 * 1000);
                InputStream is = connection.getInputStream();
                bitmap = BitmapFactory.decodeStream(is);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }

            }
            if(bitmap !=  null)
            {
                saveBitmap(bitmap , getImagePath(imageUrl));

            }

            return bitmap;
        }

        //将Bitmap保存到本地
        private void saveBitmap(Bitmap bitmap , String files) {
            File file = new File(files);


            try {
                FileOutputStream fos = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.flush();
                fos.close();
                //file.getAbsolutePath();//获取保存的图片的文件名
                //    onSaveSuccess(file);
            } catch (IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "保存失败", Toast.LENGTH_SHORT).show();
                    }
                });
                e.printStackTrace();
            }
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {

            //仅当GridView静止的时候才去下载图片，滑动的时候不会去下载
            if (scrollState == SCROLL_STATE_IDLE) {

                loadBitmaps(mFirstIndex, mPicCount);
                Log.i("PhotoActivity", "firstIndex is " + mFirstIndex + " count is " + mPicCount);
                SCROLL_STATUS = false ;   //  非滑动状态
            } else {
                cancelAllTask();
                SCROLL_STATUS = true ;    //滑动状态
            }

        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

//            if (mFirstIndex > firstVisibleItem)
//            {
//                SCROLL_DIRECTION = 1 ;
//            }else if (mFirstIndex == firstVisibleItem)
//            {
//                SCROLL_DIRECTION = 0 ;
//                return ;
//            }
//            else {
//                SCROLL_DIRECTION = 2 ;
//            }
            mFirstIndex = firstVisibleItem;
            mPicCount = visibleItemCount;
            if (mFirst && visibleItemCount > 0) {
                loadBitmaps(firstVisibleItem, visibleItemCount);
                mFirst = false;
            }
//            //如果处于滑动状态下，则直接加载默认的图爿
//            ImageView imageView  = new ImageView(getContext());
//            if (mPicCount > 0 && SCROLL_DIRECTION == 2)
//            {
//                imageView = (ImageView) gridView.findViewWithTag(Images.imageThumbUrls[mFirstIndex + mPicCount - 1]);;
//
//            }else if (mPicCount > 0 && SCROLL_DIRECTION == 1)
//            {
//                imageView = (ImageView) gridView.findViewWithTag(Images.imageThumbUrls[mFirstIndex]);;
//
//            }
//              else{
//                return;
//            }
//            if (SCROLL_STATUS &&  !imageView.getDrawable().getConstantState().equals(getResources().getDrawable(R.drawable.loading).getConstantState()) )
//            {
//                Log.i(TAG , "SCROLL_STATUS") ;
//
//                for (int i = mFirstIndex ; i < mFirstIndex + mPicCount ; i++)
//                {
//                    ImageView imageView1 = (ImageView) gridView.findViewWithTag(Images.imageThumbUrls[i]);
//
//                    imageView1.setImageResource(R.drawable.loading);
//
//
//                }
//            }


        }

        public class AsynvPicTask extends AsyncTask<String, Void, Bitmap> {

            private String imageUrl;

            @Override
            protected Bitmap doInBackground(String... strings) {

                imageUrl = strings[0];
                //在后台开始下载图片
                Bitmap bitmap = downloadBitmaps(imageUrl);
                if (bitmap != null) {
                    //将图片添加到缓存中   LruCache
                    putBitmaoToMemory(imageUrl, bitmap);
                    //将图片添加到缓存中  DiskLruCache
                    helper.put(imageUrl, bitmap);
                }
                return bitmap;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                super.onPostExecute(bitmap);
                //根据TAG 找到对应的ImageView控件，并且设置图片显示
                ImageView imageView = (ImageView) gridView.findViewWithTag(imageUrl);
                if (imageView != null && bitmap != null) {
                    imageView.setImageBitmap(bitmap);

                }

                //当前对象已经下载和显示完成，所以移除当前对象
                taskCollection.remove(this);
            }
        }

        /*
           取消当前所有的下载任务
           1  当前屏幕滑动会调用
           2  当前程序退出会调用
         */
        public void cancelAllTask() {

            if (taskCollection != null) {
                for (AsynvPicTask task : taskCollection) {
                    task.cancel(false);
                }
            }
        }

        /*
            图片在SD卡中的存储路径
         */
        private String getImagePath(String imageUrl) {
            int lastSlashIndex = imageUrl.lastIndexOf("/");
            String imageName = imageUrl.substring(lastSlashIndex + 1);
            String imageDir = getExternalCacheDir().getPath()   ;

            File file = new File(imageDir);
            if (!file.exists()) {
                file.mkdirs();
            }
            String imagePath = imageDir  + File.separator + imageName;
            return imagePath;
        }




    }
    /*
      图片资源
       */
    public static  class Images {

        public final static String[] imageThumbUrls = new String[]{

                "http://pic1.win4000.com/wallpaper/2017-11-22/5a153a264e593.jpg",
                "http://pic1.win4000.com/wallpaper/b/595ded297afac.jpg",
                "http://pic1.win4000.com/wallpaper/b/595ded2f64a14.jpg",
                "http://pic1.win4000.com/wallpaper/2018-07-05/5b3d8d152aa6a.jpg",
                "http://pic1.win4000.com/wallpaper/2018-07-05/5b3d8d18b498c.jpg",
                "http://pic1.win4000.com/wallpaper/2018-07-05/5b3d8d1a8874e.jpg",
                "http://pic1.win4000.com/wallpaper/2017-11-22/5a153a317da20.jpg",
                "http://pic1.win4000.com/wallpaper/2017-11-22/5a153a2bf3b4c.jpg",
                "http://pic1.win4000.com/wallpaper/0/59bf656191bfb.jpg",
                "http://pic1.win4000.com/wallpaper/0/59bf6575a6751.jpg",
                "http://pic1.win4000.com/wallpaper/a/58bcef80a82f4.jpg",
                "http://pic1.win4000.com/wallpaper/a/58bcefdbe08da.jpg",
                "http://pic1.win4000.com/wallpaper/a/58bcefe070937.jpg",
                "http://pic1.win4000.com/wallpaper/a/58bcefd623af9.jpg",
                "http://pic1.win4000.com/wallpaper/8/59c4725af26cc.jpg",
                "http://pic1.win4000.com/wallpaper/8/59c47267b5fa8.jpg",
                "http://pic1.win4000.com/wallpaper/8/59c4726bb9493.jpg",
                "http://pic1.win4000.com/wallpaper/8/59c47269decb3.jpg",
                "http://pic1.win4000.com/wallpaper/6/59533b9859189.jpg",
                "http://pic1.win4000.com/wallpaper/6/59533ba22836e.jpg",
                "http://pic1.win4000.com/wallpaper/6/59533b9dd0e8c.jpg",
                "http://pic1.win4000.com/wallpaper/6/59533b9fb86b8.jpg",
                "http://pic1.win4000.com/wallpaper/2017-11-17/5a0ea415b5efd.jpg",
                "http://pic1.win4000.com/wallpaper/2017-11-17/5a0ea41861ee1.jpg",
                "http://pic1.win4000.com/wallpaper/2017-11-17/5a0ea420a2235.jpg",
                "http://pic1.win4000.com/wallpaper/2017-11-17/5a0ea41aebd5b.jpg",
                "http://pic1.win4000.com/wallpaper/7/58e87fdc3431a.jpg",
                "http://pic1.win4000.com/wallpaper/7/58e87fe8eaf52.jpg",
                "http://pic1.win4000.com/wallpaper/7/58e87fde02c95.jpg",
                "http://pic1.win4000.com/wallpaper/7/58e87fe0dec2b.jpg",
                "http://pic1.win4000.com/wallpaper/1/59ad0867e4d0a.jpg",
                "http://pic1.win4000.com/wallpaper/1/59ad0871283a1.jpg",
                "http://pic1.win4000.com/wallpaper/1/59ad0875a83e2.jpg",
                "http://pic1.win4000.com/wallpaper/1/59ad086960fa1.jpg",
                "http://pic1.win4000.com/wallpaper/1/59ad086f850df.jpg",
                "http://pic1.win4000.com/wallpaper/1/59ad0874206ac.jpg",
                "http://pic1.win4000.com/wallpaper/1/59ad087737c69.jpg",
                "http://pic1.win4000.com/wallpaper/2017-11-17/5a0ea415b5efd.jpg",
                "http://pic1.win4000.com/wallpaper/2017-11-17/5a0ea41861ee1.jpg",
                "http://pic1.win4000.com/wallpaper/2017-11-17/5a0ea41d3b6e3.jpg",
                "http://pic1.win4000.com/wallpaper/2017-11-17/5a0ea41fa3451.jpg",
                "http://pic1.win4000.com/wallpaper/6/58cf827dd7184.jpg",


        };
    }

}
