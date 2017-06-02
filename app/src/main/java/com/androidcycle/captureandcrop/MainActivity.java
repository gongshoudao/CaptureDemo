package com.androidcycle.captureandcrop;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int PERMISSIONS_REQUEST = 0x01;
    private static final int PICK_IMAGE_REQUEST = 0x331;
    /**
     * capture image from device camera.
     */
    private static final int CAPTURE_IMAGE_REQUEST = 0x332;
    /**
     * crop image request code.
     */
    private static final int CROP_IMAGE_REQUEST = 0x333;
    public static final String IMAGE_HEAD_FILE_PATH = Environment.getExternalStorageDirectory() + "/Android/data/com.androidcycle.captureandcrop/my_images/";
    public static final String FILE_PROVIDER_AUTHORITY = "com.androidcycle.captureandcrop.capture.fileprovider";

    private Uri mUriForFile;
    private ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.capture).setOnClickListener(this);
        findViewById(R.id.pick_pic).setOnClickListener(this);
        imageView = (ImageView) findViewById(R.id.iv);
        judgePermission();
    }

    /**
     * 1.检查拍照和存储权限并授权
     */
    private void judgePermission() {
        try {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)
                        &&
                        ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        &&
                        ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    Snackbar.make(getWindow().getDecorView(), "需要相机和存储权限", Snackbar.LENGTH_INDEFINITE).setAction("确认", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                    PERMISSIONS_REQUEST);
                        }
                    }).show();
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            PERMISSIONS_REQUEST);
                }
            } else {
                //TODO
//                capturePicture();//拍照
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 2.拍照
     */
    private void capturePicture() {
        final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {//使用判断是否存在裁剪Activity，
            File file = null;
            try {
                file = createImageFile();//创建文件
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (file != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {//5.0以上用FileProvider比较好
                //高版本上使用FileProvider
                //为一个文件生成Content URI
                mUriForFile = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, file);
            } else {
                //5.0以下，不使用FileProvider，根据文件得到URI
                mUriForFile = Uri.fromFile(file);
            }
            intent.putExtra(MediaStore.EXTRA_OUTPUT, mUriForFile);//拍摄的照片输出路径uri
            startActivityForResult(intent, CAPTURE_IMAGE_REQUEST);
        }
    }

    /**
     * 3.本地选取
     */
    public void pickPicture() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent,"选择图片"), PICK_IMAGE_REQUEST);
    }

    /**
     * 4.裁剪
     *
     * @param fromUri 图片uri.
     */
    private void cropImage(Uri fromUri) {
        if (fromUri == null) {
            return;
        }
        final Intent cropIntent = new Intent("com.android.camera.action.CROP");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //5.0以下部分设备的没权限修改其它APP下的文件
            //如果不是程序自己的fileprovider，则把来源content uri的文件复制到自己fileprovider对应的目录下。
            // 因为部分应用不允许Intent.FLAG_GRANT_WRITE_URI_PERMISSION权限，强行申请该权限，会报安全异常。
            // 如果不拷贝文件，就需要自己实现一个文件裁剪器。自己的裁剪器裁剪的文件放在自己目录对应的包下，不会存在编辑时权限问题。
            if (!fromUri.getAuthority().equals(FILE_PROVIDER_AUTHORITY)) {
                try {
                    final File copyTargetFile = createImageFile();//
                    Uri targetUri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, copyTargetFile);
                    FileUtils.copyFileFromProviderToSelfProvider(getApplicationContext(), fromUri, targetUri);
                    fromUri = targetUri;//替换fromUri，之后的裁剪工作，裁剪的是我们程序FileProvider中的文件。
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }//如果是低版本则跳过上面这一步

        cropIntent.setDataAndType(fromUri, "image/*");//capture from camera

        cropIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        cropIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        cropIntent.putExtra("crop", "true");
        cropIntent.putExtra("aspectX", 1);
        cropIntent.putExtra("aspectY", 1);
        cropIntent.putExtra("outputX", 500);
        cropIntent.putExtra("outputY", 500);
        cropIntent.putExtra("scale", true);
        cropIntent.putExtra("return-data", false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            File cropImage = null;
            try {
                cropImage = createImageFile();//裁剪后的文件
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (cropImage != null) {
                mUriForFile = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, cropImage);
                //给所有具备裁剪功能的APP授权临时权限。
                List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(cropIntent, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo resolveInfo : resInfoList) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    //给每个APP授权
                    grantUriPermission(packageName, fromUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    grantUriPermission(packageName, mUriForFile, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                }
            }
        } else {
            try {
                createImageFile();//5.0以下直接创建文件，并赋值mUriForFile为新创建文件路径
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        cropIntent.putExtra(MediaStore.EXTRA_OUTPUT, mUriForFile);//裁剪后的图片被输出的路径的uri
        cropIntent.putExtra("noFaceDetection", true);
        cropIntent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());
        startActivityForResult(cropIntent, CROP_IMAGE_REQUEST);
    }

    /**
     * 创建图片存储文件
     * @return File对象
     * @throws IOException
     */
    private File createImageFile() throws IOException {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File dir = new File(IMAGE_HEAD_FILE_PATH);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            final File nomedia = new File(IMAGE_HEAD_FILE_PATH + ".nomedia");
            if (!nomedia.exists()) {
                nomedia.createNewFile();
            }
            final File file = new File(IMAGE_HEAD_FILE_PATH + System.currentTimeMillis() + ".jpg");
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                //如果是低版本，则根据文件生成uri
                mUriForFile = Uri.fromFile(file);
            }
            return file;
        }
        return null;
    }

    /**
     * 这个方法是对裁剪后的图片进行其它操作，如上传或显示等。
     * @param contentUri 图片uri
     */
    private void showImage(Uri contentUri) {
        byte[] imageBytes = new byte[0];
        Bitmap bm = null;
        try {
            if (FILE_PROVIDER_AUTHORITY.equals(contentUri.getAuthority())) {
                //如果是FileProvider方式，从ContentResolver中获取数据
                final ParcelFileDescriptor parcelFileDescriptor = getContentResolver().openFileDescriptor(contentUri, "r");
                final FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                bm = BitmapFactory.decodeFileDescriptor(fileDescriptor);
            } else {
                //直接file uri形式，直接从uri中获取数据
                bm = BitmapFactory.decodeFile(contentUri.getEncodedPath());
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, 75, baos); //bm is the bitmap object
            imageBytes = baos.toByteArray();//TODO a.将流上传
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bm != null && !bm.isRecycled()) {
                bm.recycle();
            }
        }
        //TODO  b.或者显示出来
        imageView.setImageURI(contentUri);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.capture:
//                judgePermission();
                capturePicture();
                break;
            case R.id.pick_pic:
                pickPicture();
                break;
            default:break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case CAPTURE_IMAGE_REQUEST://拍照的Activity响应
                if (resultCode == RESULT_OK && mUriForFile != null) {
                    cropImage(mUriForFile);
                }
                break;
            case PICK_IMAGE_REQUEST://本地选取的Activity响应
                if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                    Uri uri = data.getData();
                    cropImage(uri);
                }
                break;
            case CROP_IMAGE_REQUEST://处理裁剪后的图片，这里是显示出来
                showImage(mUriForFile);
                break;
            default:
                break;
        }
    }



    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST:
                try {
                    final boolean grantedCamera = verifyPermissions(grantResults);
                    if (grantedCamera) {
                        getWindow().getDecorView().postDelayed(new Runnable() {
                            @Override
                            public void run() {
//                                capturePicture();//TODO
                            }
                        },2000);
                    } else {
                        Toast.makeText(MainActivity.this, "未授予权限，无法完成操作", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            default:
                break;
        }

    }

    public boolean verifyPermissions(int[] grantResults) {
        if (grantResults.length < 1) {
            return false;
        }

        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }
}
