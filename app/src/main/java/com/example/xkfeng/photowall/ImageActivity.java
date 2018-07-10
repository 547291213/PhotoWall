package com.example.xkfeng.photowall;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;
import android.widget.Toast;

import com.example.xkfeng.photowall.ImageViewZoom.ImageViewZoom;

/**
 * Created by initializing on 2018/7/9.
 */

public class ImageActivity extends AppCompatActivity {

    private ImageViewZoom imageViewZoom ;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE) ;
        setContentView(R.layout.image_zoom);
        imageViewZoom  = (ImageViewZoom)findViewById(R.id.v_zoomView) ;
        String imagePath = getIntent().getStringExtra("image_path") ;
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath) ;
        if (bitmap == null)
        {
            Toast.makeText(this , "获取图片失败", Toast.LENGTH_SHORT).show();
            Bitmap bitmap1 = BitmapFactory.decodeResource(getResources() , R.drawable.cat) ;
            imageViewZoom.setImageBitmap(bitmap1);
            return;
        }
        imageViewZoom.setImageBitmap(bitmap);


    }
}
