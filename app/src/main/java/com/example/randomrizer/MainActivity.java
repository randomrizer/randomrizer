package com.example.randomrizer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Toast;
import android.content.res.Configuration;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {


    private static int RESULT_LOAD_IMAGE = 1;
    private static int MAX_DISPLAY_SIZE = 3000;
    private static int MAX_IMAGE_SIZE_FOR_PROCESS = 3000000;
    private static int NUM_COLUMN_PER_IMAGE = 3;
    private static int MAX_WIDTH_LONG_ARTICLE = 500;
    private static int OVERLAPPING_PIXELS = 20;
    private String imagePath = "";
    private Random rnd = new Random();
    private static int[] backgroundNames = { R.drawable.background1, R.drawable.background2,
        R.drawable.background3 };
    private boolean noText = false;
    private boolean longArticle = false;
    private Bitmap bmp_display = null;
    private Bitmap bmp_process = null;
    private Bitmap bmp_original = null;
    private ArrayList<Bitmap> bmp_process_list = new ArrayList<Bitmap>();

    SharedPreferences prefFile = null;
    String pref_file_name = "PREF_FILE";
    String language_pref = null;
    String[] languages = {"English", "简体中文","繁體中文"};
    final static String[] language_keys = {"ENGLISH", "CHINESE_SIMP", "CHINESE_TRAD"};



    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i("OpenCV", "OpenCV loaded successfully");
                    Mat originImage = Imgcodecs.imread(imagePath);
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        rnd.setSeed(System.currentTimeMillis());
        prefFile = getApplication().getSharedPreferences(
                pref_file_name, Context.MODE_PRIVATE);
        language_pref = prefFile.getString(pref_file_name, language_keys[0]);
        changeLanguage(language_pref);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check for write image permission
        if (ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    Constants.MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        }

        // Button Load
        Button buttonLoadImage = (Button) findViewById(R.id.uploadImage);
        buttonLoadImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Intent i = new Intent(
                        Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(i, RESULT_LOAD_IMAGE);
            }
        });

        // Button Randomrizer

        Button buttonRandomrizer = (Button) findViewById(R.id.randomrizer);
        buttonRandomrizer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                ImageView imageView = (ImageView) findViewById(R.id.imageView2);
                if (imageView.getDrawable() == null) {
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.toast1),
                            Toast.LENGTH_SHORT).show();
                } else {
                    // Read the original image
                    Mat image_in_process = new Mat();
                    Utils.bitmapToMat(bmp_original,image_in_process);
                    ArrayList<Mat> image_in_process_list = new ArrayList<Mat>();
                    bmp_process_list.clear();

                    boolean invalid = false;

                    if (!longArticle){
                        if (image_in_process.height()/image_in_process.width()>10 || image_in_process.width()/image_in_process.height()>10){
                            Toast.makeText(getApplicationContext(),
                                    getResources().getString(R.string.toast2),
                                    Toast.LENGTH_LONG).show();
                            invalid = true;
                        }
                    }
                    else{
                        // Rotate image if it's not slim; aka width>height
                        if (image_in_process.width() > image_in_process.height()){
                            Point center = new Point(image_in_process.width()/2, image_in_process.height()/2);
                            Mat rotMatrix = Imgproc.getRotationMatrix2D(center, 90, 1.0);
                            Size size = new Size(image_in_process.height(), image_in_process.height());
                            Imgproc.warpAffine(image_in_process, image_in_process, rotMatrix, size);
                        }

                        if (image_in_process.width()>MAX_WIDTH_LONG_ARTICLE){
                            Size size = new Size(MAX_WIDTH_LONG_ARTICLE,
                                    (int) 1.0 * MAX_WIDTH_LONG_ARTICLE * image_in_process.height() / image_in_process.width());
                            Imgproc.resize(image_in_process, image_in_process, size);
                        }

                        // Divide long image into several smaller ones for processing
                        divideLongImage(image_in_process, image_in_process_list);

                        // Rearrange each smaller image into 'fatter' image
                        for (int i = 0; i < image_in_process_list.size(); i++){
                            image_in_process_list.set(i,
                                    rearrangeLongImage(image_in_process_list.get(i)));
                        }
                    }


                    if (!invalid){
                        if (!longArticle){
                            image_in_process = randomrizeSingleImage(image_in_process);
                        }
                        else{
                            for (int i = 0; i < image_in_process_list.size(); i++){
                                image_in_process_list.set(i,
                                        randomrizeSingleImage(image_in_process_list.get(i)));
                            }
                        }
                    }



                    // Convert to bitmap
                    if (!longArticle){
                        bmp_process = convertSingleImageToBitmap(image_in_process);
                    }
                    else{
                        for (int i = 0; i < image_in_process_list.size(); i++){
                            bmp_process_list.add(convertSingleImageToBitmap(image_in_process_list.get(i)));
                        }
                    }

                    if (!longArticle){
                        convertSingleBmpToDisplay(bmp_process);
                    }
                    else{
                        convertSingleBmpToDisplay(bmp_process_list.get(0));
                        Toast.makeText(getApplicationContext(),
                                getResources().getString(R.string.toast3)+String.valueOf(image_in_process_list.size())+
                                        " " +
                                        getResources().getString(R.string.toast4), Toast.LENGTH_LONG).show();
                    }

                    // Display on imageview
                    imageView.setImageBitmap(bmp_display);
                }
            }
        });

        // Button Save
        Button buttonSave = (Button) findViewById(R.id.saveImage);
        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ImageView imageView = (ImageView) findViewById(R.id.imageView2);
                if (imageView.getDrawable() == null) {
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.toast1),
                            Toast.LENGTH_SHORT).show();
                } else {

                    String currentDateandTime =
                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                    String albumName = "Randomrizer";
                    File root = getPublicAlbumStorageDir(albumName);
                    Log.d("root activity_main", root.getAbsolutePath());

                    if (!longArticle){
                        String imageName = "randomrizer".concat(currentDateandTime.concat(".jpg"));
                        File sdImageMainDirectory = new File(root, imageName);
                        Log.d("image activity_main", sdImageMainDirectory.getAbsolutePath());

                        saveSingleBitmap(bmp_process, sdImageMainDirectory);
                    }
                    else{
                        for (int i = bmp_process_list.size(); i > 0 ; i--){
                            String imageName =
                                    "randomrizer".concat(currentDateandTime.concat("-"+String.valueOf(i)+
                                    ".jpg"));
                            File sdImageMainDirectory = new File(root, imageName);
                            Log.d("image activity_main", sdImageMainDirectory.getAbsolutePath());

                            saveSingleBitmap(bmp_process_list.get(i-1), sdImageMainDirectory);
                        }
                    }
                }
            }
        });

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && null != data) {
            Uri selectedImage = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};

            Cursor cursor = getContentResolver().query(selectedImage,
                    filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            imagePath = picturePath;
            cursor.close();

            bmp_original = BitmapFactory.decodeFile(picturePath);
//            bmp_process = bmp_original;
            convertSingleBmpToDisplay(bmp_original);

            ImageView imageView = (ImageView) findViewById(R.id.imageView2);
            imageView.setImageBitmap(bmp_display);
        }


    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case Constants.MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for " +
                    "initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public File getPublicAlbumStorageDir(String albumName) {
        // Get the directory for the user's public pictures directory.
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), albumName);
        if (!file.mkdirs()) {
            Log.e("Log", "Directory not created");
        }
        return file;
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    public void rotate_image_without_cropping(Mat image, double angle){
        //Calculate size of new matrix
        double radians = Math.toRadians(angle);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));

        int newWidth = (int) (image.width() * cos + image.height() * sin);
        int newHeight = (int) (image.width() * sin + image.height() * cos);

        // rotating image
        Point center = new Point(image.width()/2, image.height()/2);
        Mat rotMatrix = Imgproc.getRotationMatrix2D(center, angle, 1.0); //1.0 means 100 % scale

        rotMatrix.put(0,2,rotMatrix.get(0,2)[0] +newWidth/2.0-image.width()/2.0);
        rotMatrix.put(1,2,rotMatrix.get(1,2)[0] +newHeight/2.0-image.height()/2.0);


        Size size = new Size(newWidth, newHeight);
        Log.d("Image size in rotate",String.valueOf(newWidth) + " " + String.valueOf(newWidth));
        Imgproc.warpAffine(image, image, rotMatrix, size);
    }

    public Mat rotate_image_without_cropping_with_background(Mat image, double angle,
                                                              Mat background){
        //Calculate size of new matrix
        double radians = Math.toRadians(angle);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));

        int newWidth = (int) (image.width() * cos + image.height() * sin);
        int newHeight = (int) (image.width() * sin + image.height() * cos);

        // rotating image
        Point center = new Point(image.width()/2, image.height()/2);
        Mat rotMatrix = Imgproc.getRotationMatrix2D(center, angle, 1.0); //1.0 means 100 % scale

        rotMatrix.put(0,2,rotMatrix.get(0,2)[0] +newWidth/2.0-image.width()/2.0);
        rotMatrix.put(1,2,rotMatrix.get(1,2)[0] +newHeight/2.0-image.height()/2.0);


        Size size = new Size(newWidth, newHeight);

        Mat dst = background;
        while (dst.width() < newWidth){
            List<Mat> src = Arrays.asList(dst, dst);
            Core.hconcat(src, dst);
        }
        while (dst.height() < newHeight){
            List<Mat> src = Arrays.asList(dst, dst);
            Core.vconcat(src, dst);
        }

        int w_offset = rnd.nextInt(dst.width() - newWidth);
        int h_offset = rnd.nextInt(dst.height() - newHeight);

        Rect rectCrop = new Rect(w_offset , h_offset, newWidth, newHeight);
        Mat final_image = new Mat(background, rectCrop);
        Imgproc.warpAffine(image, final_image, rotMatrix, size,
                Imgproc.INTER_LINEAR, Core.BORDER_TRANSPARENT);
        return final_image;
    }
    public Mat change_aspect_ratio(Mat image, double ratio_width, double ratio_height){
        Size size = new Size(image.width()*ratio_width,image.height()*ratio_height);
        Imgproc.resize(image,image,size);

        return image;
    }

    public void onOptionButtonClicked(View view) {
        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();

        // Check which radio button was clicked
        switch (view.getId()) {
            case R.id.noTextOption:
                if (checked){
                    noText = true;
                    longArticle = false;
                }
                break;
            case R.id.longArticleOption:
                if (checked){
                    noText = false;
                    longArticle = true;
                }
                break;
            case R.id.noneOption:
                if (checked){
                    noText = false;
                    longArticle = false;
                }
                break;
        }
    }

    public void loadDisplayBmpFromMat(Mat mat){
        if (mat.height()>MAX_DISPLAY_SIZE || mat.width()>MAX_DISPLAY_SIZE){
            Mat finalImageRGB_scaled = new Mat();
            Size size = null;
            if (mat.height()>mat.width()){
                size =
                        new Size(2000.0*mat.width()/mat.height(),
                                2000);
            }
            else{
                size =
                        new Size(2000,
                                2000.0*mat.width()/mat.height());
            }
            Imgproc.resize(mat, finalImageRGB_scaled, size);
            bmp_display = Bitmap.createBitmap(finalImageRGB_scaled.cols(),
                    finalImageRGB_scaled.rows(),
                    Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(finalImageRGB_scaled, bmp_display);
        }
        else{
            bmp_display = Bitmap.createBitmap(mat.cols(), mat.rows(),
                    Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat, bmp_display);
        }
    }

    public void convertSingleBmpToDisplay(Bitmap bmp){
        if (bmp.getHeight()>MAX_DISPLAY_SIZE || bmp.getWidth() > MAX_DISPLAY_SIZE){
            int height = MAX_DISPLAY_SIZE;
            int width = MAX_DISPLAY_SIZE;

            if (bmp.getHeight() > bmp.getWidth()){
                width =
                        (int) (1.0 * MAX_DISPLAY_SIZE * bmp.getWidth()/bmp.getHeight());
            }
            else{
                height =
                        (int) (1.0 * MAX_DISPLAY_SIZE * bmp.getHeight()/bmp.getWidth());
            }
            bmp_display = Bitmap.createScaledBitmap(bmp, width,height,true);
        }
        else{
            bmp_display = bmp;
        }
    }

    /*Divide long image into smaller ones accoring to size;
    * Max image size to process 5,000,000 pixels; set in MAX_IMAGE_SIZE_FOR_PROCESS*/
    public void divideLongImage(Mat image, ArrayList<Mat> imageList){
        // Calculate number of images need to be divided into
        int num_divided_images =
                image.height()*image.width()/MAX_IMAGE_SIZE_FOR_PROCESS;
        if (num_divided_images * MAX_IMAGE_SIZE_FOR_PROCESS < image.height()*image.width()){
            num_divided_images += 1;
        }

        int pixel_height_per_image = image.height()/num_divided_images;


        Rect rect = new Rect();
        for (int i = 0; i < num_divided_images; i++){
            if (i==0){
                rect = new Rect(0,0,image.width(),pixel_height_per_image);
            }
            else if (i==num_divided_images - 1){
                rect = new Rect(0,pixel_height_per_image*i - OVERLAPPING_PIXELS, image.width(),
                        image.height() - pixel_height_per_image*i+OVERLAPPING_PIXELS);
            }
            else{
                rect = new Rect(0,pixel_height_per_image*i - OVERLAPPING_PIXELS, image.width(),
                        pixel_height_per_image+OVERLAPPING_PIXELS);
            }

            imageList.add(new Mat(image, rect));
        }
    }

    public Mat rearrangeLongImage(Mat image){
        Mat rearrangedImage = new Mat();

        /* s*3 = image.size + 20 + 20 + b */
        int pixel_per_coloum;
        int offset;
        if ((image.height() + (NUM_COLUMN_PER_IMAGE -1)*OVERLAPPING_PIXELS)%3 == 0){
            pixel_per_coloum =
                    (image.height() + (NUM_COLUMN_PER_IMAGE -1)*OVERLAPPING_PIXELS)/NUM_COLUMN_PER_IMAGE;
            offset = 0;
        }
        else{
            pixel_per_coloum =
                    (image.height() + (NUM_COLUMN_PER_IMAGE -1)*OVERLAPPING_PIXELS)/NUM_COLUMN_PER_IMAGE + 1;
            offset =
                    pixel_per_coloum * NUM_COLUMN_PER_IMAGE - (image.height() + (NUM_COLUMN_PER_IMAGE -1)*OVERLAPPING_PIXELS);
        }


        Rect rect = new Rect();
        for (int i = 0; i < NUM_COLUMN_PER_IMAGE; i++){
            if (i == 0){
                rect = new Rect(0,0,image.width(),pixel_per_coloum);
                rearrangedImage = new Mat(image, rect);
            }
            else{
                if(i == NUM_COLUMN_PER_IMAGE - 1){
                    rect = new Rect(0,i*pixel_per_coloum - OVERLAPPING_PIXELS*i - offset,
                            image.width(),
                            pixel_per_coloum);
                }
                else{
                    rect = new Rect(0,i*pixel_per_coloum - OVERLAPPING_PIXELS*i, image.width(),
                            pixel_per_coloum);
                }
                Log.d("rect",
                        String.valueOf(i)+" "+String.valueOf(rect.height)+" "+String.valueOf(rect.y)+" "+String.valueOf(rect.width)+" "+String.valueOf(rect.x));
                Log.d("rect", String.valueOf(image.height()));
                List<Mat> src = Arrays.asList(rearrangedImage, new Mat(image,rect));
                Core.hconcat(src, rearrangedImage);
            }
        }
        return rearrangedImage;
    }

    public Mat randomrizeSingleImage(Mat image){

        /*Algorithm
         * Random rotate without cropping
         * Random color change
         * Random background; translational invariant
         * Remove meta deta
         * Mirroring (if no texts)
         * Blur
         * Stroke
         * Aspect ratio change*/


        // Mirror
        if (noText){
            Core.flip(image,image,1);
            Imgproc.GaussianBlur(image,image,new Size (9,9),0);
        }

        // Randomly change aspect ratio of the original image. Range: [0.9,1.1]
        change_aspect_ratio(image,1+(rnd.nextDouble()-0.5)/5,
                1+(rnd.nextDouble()-0.5)/5);


        // Random rotate with background. Range: [-60,-60]
        Mat background = new Mat();
        Utils.bitmapToMat(BitmapFactory.decodeResource(getResources(),
                backgroundNames[rnd.nextInt(backgroundNames.length)]), background);

        image = rotate_image_without_cropping_with_background(image,
                (rnd.nextDouble()-0.5)*120, background);

        return image;
    }

    public Bitmap convertSingleImageToBitmap(Mat image){
        Bitmap bmp = Bitmap.createBitmap(image.cols(), image.rows(),
                Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(image, bmp);
        return bmp;
    }

    public void saveSingleBitmap(Bitmap bmp, File file){
        OutputStream fOut = null;
        Uri outputFileUri;

        try {
//                        File root = new File(Environment.getExternalStorageDirectory()
//                                + File.separator + "folder_name" + File.separator);
//                        root.mkdirs();
            outputFileUri = Uri.fromFile(file);
            fOut = new FileOutputStream(file);

            Toast.makeText(getApplicationContext(),
                    getResources().getString(R.string.toast5)+file.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.toast6),
                    Toast.LENGTH_SHORT).show();
        }

        int saveQuality = 100;
        if (longArticle){
            saveQuality = 50;
        }
        if (isExternalStorageWritable()) {
            try {
                bmp.compress(Bitmap.CompressFormat.JPEG, saveQuality, fOut);
                fOut.flush();
                fOut.close();
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.toast7),
                        Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.toast8),
                    Toast.LENGTH_LONG).show();
        }

        MediaScannerConnection.scanFile(getApplicationContext(),
                new String[]{file.toString()}, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                        Log.i("ExternalStorage", "Scanned " + path + ":");
                        Log.i("ExternalStorage", "-> uri=" + uri);
                    }
                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_about:
                showAbout();
                return true;
            case R.id.action_language:
                showChangeLanguage();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void showAbout(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(R.string.about));
        builder.setMessage(R.string.about_message);
        builder.show();
    }

    public void showChangeLanguage(){


        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(R.string.language));
        builder.setItems(languages, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // the user clicked on colors[which]
                if (!language_keys[which].equals(language_pref)){
                    SharedPreferences.Editor editor = prefFile.edit();
                    editor.putString(pref_file_name, language_keys[which]);
                    editor.apply();

                    language_pref =  language_keys[which];

                    changeLanguage(language_pref);
                    recreate();
                }


            }
        });
        builder.show();
    }


    private static Context updateResources(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);
        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        if (Build.VERSION.SDK_INT >= 17) {
            config.setLocale(locale);
            context = context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            res.updateConfiguration(config, res.getDisplayMetrics());
        }
        return context;
    }

    public void changeLanguage(String lang) {
        if (lang.equalsIgnoreCase(""))
            return;

        android.content.res.Configuration config = new android.content.res.Configuration();

        switch (lang){
            case "ENGLISH":
                config.locale = Locale.ENGLISH;
                break;
            case "CHINESE_SIMP":
                config.locale = Locale.SIMPLIFIED_CHINESE;
                break;
            case "CHINESE_TRAD":
                config.locale = Locale.TRADITIONAL_CHINESE;
                break;
            default:
                Toast.makeText(getApplicationContext(),
                        getResources().getString(R.string.toast7),Toast.LENGTH_LONG).show();

        }

        getBaseContext().getResources().updateConfiguration(config,
                getBaseContext().getResources().getDisplayMetrics());

    }
}
