package com.example.xkfeng.photowall.ImageViewZoom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.preference.PreferenceActivity;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.security.acl.LastOwnerException;

/**
 * Created by initializing on 2018/7/8.
 */

public class ImageViewZoom extends View{
    /*
       初始化状态常量
     */
    public static final int STATUS_INIT = 1 ;

    /*
        图片放大状态常量
     */
    public static final int STATUS_ZOMM_OUT = 2 ;

    /*
        图片缩小状态常量
     */
    public static final int STATUS_ZOOM_IN = 3 ;

    /*
       图片移动状态常量
     */
    public static final int STATUS_MOVE = 4 ;

    /*
       图片拖动变换矩阵
     */
    private Matrix matrix = new Matrix() ;

    /*
      待展示的Bitmap
     */
    private Bitmap sourceBitmap  ;

    /*
        当前操作的状态值  STATUS_INIT STATUS_ZOMM_OUT STATUS_ZOMOM_IN STATUS_MOVE
     */
     private int currentStatus ;

     /*
        整个控件的宽度
      */
     private int width ;

     /*
        整个控件的高度
      */
     private int height ;

     /*
         两指放在屏幕上时，中心点的X值
      */
     private float centerPointX ;

     /*
         两指放在屏幕上时，中心点的Y值
      */
     private float centerPointY ;

     /*
        记录当前图片的宽度，随着图片的缩放而变动
      */
     private float currentBitmapWidth ;

     /*
        记录当前的图片高度，随着图片的缩放而变动
      */
     private float currentBitmapHeight  ;

     /*
        记录上次手指移动时的横坐标X
      */
     private float lastXMove = -1 ;

     /*
        记录上次手指移动时的纵坐标Y
      */
     private float lastYMove = -1 ;

     /*
        记录手指再横坐标上的移动距离
      */
     private float movedDistanceX ;

     /*
        记录手指在纵坐标上的移动距离
      */
     private float movedDistanceY ;

     /*
        记录图片在矩阵上的横向偏移值
      */
     private float totalTranslateX ;

     /*
        记录图片在矩阵上的纵向偏移值
      */
     private float totalTranslateY ;


     /*
        记录图片在矩阵上的缩放比例
      */
     private float totalRatio ;

     /*
        记录手指移动所造成的缩放比例
      */
     private float scaledRatio ;

     /*
        记录当前图片初始化的缩放比例
      */
     private float initRatio ;
     /*
       记录上次两手指间移动所产生的缩放比例
      */
     private double lastFingerDis ;

     /*
         输出日志
      */
    private static final String TAG = "ImageViewZoom" ;



     public ImageViewZoom(Context context) {
        this(context , null);
    }

    /*
       默认启动的构造函数，设置当权状态为初始状态
     */
    public ImageViewZoom (Context context , AttributeSet set)
    {
        super(context , set);
        currentStatus = STATUS_INIT ;

    }

    /*
       设置需要展示的图片
     */
    public void setImageBitmap(Bitmap bitmap)
    {
        sourceBitmap = bitmap ;
        invalidate();
    }


    /*
       设置控件的宽度和高度
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (changed)
        {
            width = getWidth() ;
            height = getHeight() ;
        }

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch (event.getActionMasked())
        {
            case MotionEvent.ACTION_POINTER_DOWN :
                if (event.getPointerCount() == 2)
                {
                    //当屏幕上有两个手指值，计算两点之间的距离
                    lastFingerDis =  distanceBetweenFingers(event) ;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                Log.i(TAG , "ACTION MOVE") ;
                if (event.getPointerCount() == 1)
                {
                    //如果时单指操作，当前处于Move状态
                    float xMove = event.getX() ;
                    float yMove = event.getY() ;
                    if (lastXMove == -1 && lastYMove == -1)
                    {
                        lastXMove = xMove ;
                        lastYMove = yMove ;
                    }
                    //设置当前状态
                    currentStatus = STATUS_MOVE ;
                    //手指在坐标轴上的滑动距离
                    movedDistanceX = xMove - lastXMove ;
                    movedDistanceY = yMove - lastYMove ;
//                    //进行边界检查 , 不允许吧图片拖出边界
//                    if (totalTranslateX + movedDistanceX  > 0 )
//                    {
//                        Log.i(TAG , "ACTION MOVE 000") ;
//                        movedDistanceX = 0 ;
//                    }else if (width - ( totalTranslateX + movedDistanceX ) > currentBitmapWidth)
//                    {
//                        movedDistanceX = 0 ;
//                    }
//
//
//                    if (totalTranslateY + movedDistanceY > 0)
//                    {
//                        movedDistanceY = 0 ;
//                    }else if (height - (totalTranslateY + movedDistanceY) > currentBitmapHeight)
//                    {
//                        movedDistanceY = 0 ;
//                    }
                    //通知重绘图片
                    invalidate();
                    lastXMove = xMove ;
                    lastYMove = yMove ;

                }else if (event.getPointerCount() == 2)
                {
                    //当有两个手指在屏幕时，当前为缩放状态

                    //更新当前两个手指中心点的值
                    centerPointBetweenFingers(event);
                    double fingerDis = distanceBetweenFingers(event) ;
                    if ( fingerDis > lastFingerDis)
                    {
                        currentStatus = STATUS_ZOMM_OUT ;
                    }else {
                        currentStatus = STATUS_ZOOM_IN ;
                    }

                    //进行缩放倍数检查  最大可以把当前图片放大四倍，最小为初始化图片大小
                    if ((currentStatus == STATUS_ZOMM_OUT && totalRatio < 4 * initRatio ) ||
                            ( currentStatus == STATUS_ZOOM_IN && totalRatio > initRatio)){
                        //基于现在图片缩放的比例，计算还应该缩放的比例
                        scaledRatio = (float) (fingerDis / lastFingerDis) ;
                        totalRatio = totalRatio * scaledRatio ;
                        if (totalRatio > 4 * initRatio)
                        {
                            totalRatio = 4 * initRatio ;
                        }else if (totalRatio < initRatio)
                        {
                            totalRatio = initRatio ;
                        }

                        lastFingerDis = fingerDis ;
                        //通知重绘
                        invalidate();
                    }

                }
                break ;
            case MotionEvent.ACTION_POINTER_UP :
                //手指离开时  将值还原
                if (event.getPointerCount() == 2)
                {
                    lastXMove = -1 ;
                    lastYMove = -1 ;
                }
                break ;

            case MotionEvent.ACTION_UP :
                //手指离开时将临时值还原
                lastYMove = -1 ;
                lastXMove = -1 ;
                break ;

            default:

                break ;
        }

        return true ;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        switch (currentStatus)
        {
            case STATUS_ZOMM_OUT :

            case STATUS_ZOOM_IN :
                zoom(canvas);
                break ;

            case STATUS_MOVE :
                move(canvas);
                break ;

            case STATUS_INIT :
                initBitmap(canvas);
                break ;

            default:
                canvas.drawBitmap(sourceBitmap , matrix , null);
                break ;

        }
    }

    /*
      对图片进行缩放处理
     */
    private void zoom(Canvas canvas)
    {
        matrix.reset();
        //将图片按总缩放比例进行缩放
        matrix.postScale(totalRatio , totalRatio) ;
        float scaledWidth = sourceBitmap.getWidth() * totalRatio ;
        float scaledHeight = sourceBitmap.getHeight() * totalRatio ;
        float translateX ;
        float translateY ;
        //如果当图片的宽度小于屏幕的宽度，按屏幕的中心进行缩放，否则按两指的中心进行缩放
        if(currentBitmapWidth  < width)
        {
            translateX = (width - scaledWidth) / 2 ;
        }else {
            translateX = totalTranslateX * scaledRatio + centerPointX * (1 - scaledRatio) ;
            //进行边界检查，放置画出边界
            if (translateX > 0)
            {
                translateX = 0 ;
            }else if (width - scaledWidth > translateX)
            {
                translateX = width - scaledWidth ;
            }
        }

        //如果当前图片的高度小于屏幕的高度，按上面的方法进行缩放
        if (currentBitmapHeight < height)
        {
            translateY = (height - scaledHeight ) / 2 ;
        }else {
            translateY = totalTranslateY * scaledRatio + centerPointY * (1 - scaledRatio) ;
            if (translateY > 0 )
            {
                translateY = 0 ;
            }else if (height - scaledHeight > translateY)
            {
                translateY = height - scaledHeight ;
            }
        }

        //缩放比例计算完成后，进行偏移
        matrix.postTranslate(translateX , translateY) ;
        totalTranslateY =  translateY ;
        totalTranslateX = translateX ;
        currentBitmapHeight = scaledHeight ;
        currentBitmapWidth = scaledWidth ;
        canvas.drawBitmap(sourceBitmap , matrix , null);


    }

    /*
      对图片进行平移处理
     */
    private void move(Canvas canvas){
        matrix.reset();
        //根据手指移动的距离计算出总偏移值
        float translateX = totalTranslateX + movedDistanceX ;
        float translateY = totalTranslateY + movedDistanceY ;
        //先按照已有的缩放比例对图片进行缩放
        matrix.postScale(totalRatio , totalRatio) ;
        //再根据移动距离进行偏移
        matrix.postTranslate(translateX , translateY) ;
        totalTranslateY = translateY ;
        totalTranslateX = translateX ;
        canvas.drawBitmap(sourceBitmap , matrix , null);
        Log.i(TAG , "MOVE FUNCTION  X : " + translateX + " Y:" + translateY ) ;
    }

    /*
       对图片进行初始化操作，包括让图片居中，以及当图片大于屏幕高宽时对图片进行压缩
     */
     private void initBitmap(Canvas canvas)
     {
         if (sourceBitmap != null){

             matrix.reset();
             int bitmapWidth  = sourceBitmap.getWidth() ;
             int bitmapHeight = sourceBitmap.getHeight() ;

             //当图片宽度大于屏幕宽度时，将图片进行等比例压缩，使他可以完全显示出来
             if (bitmapWidth > width || bitmapHeight > height){
                 if (bitmapWidth / width > bitmapHeight / height){
                     float ratio = width / (bitmapWidth * 1.0f) ;
                     matrix.postScale(ratio ,ratio) ;
                     //在纵坐标轴上进行偏移，以保证图片居中显示
                     float translateY = (height - (bitmapHeight * ratio)) / 2.0f ;
                     matrix.postTranslate(0 , translateY) ;
                     totalTranslateY = translateY ;
                     totalRatio = initRatio = ratio ;
                 }else {
                     //当图片的高度大于屏幕高度时，将图片进行等比例压缩
                     float ratio = height / (bitmapHeight * 1.0f) ;
                     matrix.postScale(ratio ,ratio) ;
                     float translateX = (width - ( bitmapWidth * ratio)) / 2.0f ;
                     matrix.postTranslate(translateX , 0) ;
                     totalTranslateX = translateX ;
                     totalRatio = initRatio = ratio ;
                 }

                 currentBitmapHeight = bitmapHeight * initRatio ;
                 currentBitmapWidth = bitmapWidth * initRatio ;
             }
             else {
                 //当屏幕宽和高都小于屏幕的宽和高时，直接让图片居中显示
                 float translateX = (width - bitmapWidth) / 2f ;
                 float tramslateY = (height - bitmapHeight) / 2f ;
                 matrix.postTranslate(translateX , tramslateY) ;
                 totalTranslateX = translateX ;
                 totalTranslateY = tramslateY ;
                 totalRatio = initRatio = 1f ;
                 currentBitmapWidth = bitmapWidth ;
                 currentBitmapHeight = bitmapHeight ;

             }

             canvas.drawBitmap(sourceBitmap , matrix , null);
         }

     }

    /*
        计算两个手指之间的距离
     */
    private double distanceBetweenFingers(MotionEvent event)
    {
         float disX = Math.abs(event.getX(0)  - event.getX(1)) ;
         float disY = Math.abs(event.getY(0) - event.getY(1)) ;
         return Math.sqrt(disX * disX + disY * disY) ;
    }

    /*
       计算两个手指中心点的距离
     */
    private void centerPointBetweenFingers(MotionEvent event)
    {
        float xPoint0 = event.getX(0) ;
        float xPoint1 = event.getX(1) ;
        float yPoint0 = event.getY(0) ;
        float yPoint1 = event.getY(1) ;

        centerPointX = ( xPoint0 + xPoint1) / 2 ;
        centerPointY = ( yPoint0 + yPoint1) / 2 ;

    }
}
