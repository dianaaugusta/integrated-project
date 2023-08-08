package com.google.ar.core.examples.java.augmentedfaces;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.content.ContentValues;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class CameraPreviewActivity extends AppCompatActivity implements View.OnClickListener {
    Button btnPicture;
    private static final String TAG = CameraPreviewActivity.class.getSimpleName();
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    PreviewView previewView;
    private ImageCapture imageCapture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_preview);
        btnPicture = findViewById(R.id.btnPicture);
        btnPicture.setOnClickListener(this);
        previewView = findViewById(R.id.previewView);
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            ProcessCameraProvider cameraProvider = null;
            try {
                cameraProvider = cameraProviderFuture.get();
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            startCameraX(cameraProvider);
        }, getExecutor());


    }

    private Executor getExecutor(){
        return ContextCompat.getMainExecutor(this);
    }


    private void startCameraX(ProcessCameraProvider cameraProvider){
        cameraProvider.unbindAll();
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(
                CameraSelector.LENS_FACING_FRONT
        ).build();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder().setCaptureMode(
                ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        ).build();
        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.btnPicture:
                capturePhoto();
        }
    }

    private void capturePhoto() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

            long timestamp = System.currentTimeMillis();
            ContentValues contentValues = new ContentValues();

            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME , timestamp);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

            imageCapture.takePicture(
                    new ImageCapture.OutputFileOptions.Builder(
                            getContentResolver(),
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            contentValues
                    ).build(),
                    getExecutor(),
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                            Toast.makeText(CameraPreviewActivity.this, "Photo saved.", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            Log.e(TAG, String.valueOf(exception));
                            Toast.makeText(CameraPreviewActivity.this, "error " + exception, Toast.LENGTH_SHORT).show();
                        }
                    }
            );

        }


    }
}