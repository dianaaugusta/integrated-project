/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.rawdepth;

import android.Manifest;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Shader;
import android.media.Image;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Mesh;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.opencsv.CSVWriter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * This is a simple example that shows how to use ARCore Raw Depth API. The application will display
 * a 3D point cloud and allow the user control the number of points based on depth confidence.
 */
public class RawDepthActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
  private static final String TAG = RawDepthActivity.class.getSimpleName();

  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private GLSurfaceView surfaceView;

  private boolean installRequested;


  private Session session;
  private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
  private DisplayRotationHelper displayRotationHelper;
  private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
  private boolean depthReceived;
  private final Renderer renderer = new Renderer();

  // This lock prevents accessing the frame images while Session is paused.
  private final Object frameInUseLock = new Object();

  private Button btnSnapshot;

  /** The current raw depth image timestamp. */
  private long depthTimestamp = -1;

  // Point Cloud
  private VertexBuffer pointCloudVertexBuffer;
  private Mesh pointCloudMesh;
  private Shader pointCloudShader;
  // Keep track of the last point cloud rendered to avoid updating the VBO if point cloud
  // was not changed.  Do this using the timestamp since we can't compare PointCloud objects.
  private long lastPointCloudTimestamp = 0;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    surfaceView = findViewById(R.id.surfaceview);
    displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

    // Set up rendering.
    surfaceView.setPreserveEGLContextOnPause(true);
    surfaceView.setEGLContextClientVersion(2);
    surfaceView.setEGLConfigChooser(8, 8, 8, 0, 16, 0);
    surfaceView.setRenderer(this);
    surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    surfaceView.setWillNotDraw(false);

    // Set up confidence threshold slider.
    SeekBar seekBar = findViewById(R.id.slider);
    seekBar.setProgress((int) (renderer.getPointAmount() * seekBar.getMax()));
    seekBar.setOnSeekBarChangeListener(seekBarChangeListener);

    installRequested = false;
    depthReceived = false;
  }

  private SeekBar.OnSeekBarChangeListener seekBarChangeListener =
      new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
          float progressNormalized = (float) progress / seekBar.getMax();
          renderer.setPointAmount(progressNormalized);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
      };

  @Override
  protected void onDestroy() {
    if (session != null) {
      // Explicitly close ARCore Session to release native resources.
      // Review the API reference for important considerations before calling close() in apps with
      // more complicated lifecycle requirements:
      // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
      session.close();
      session = null;
    }

    super.onDestroy();
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (session == null) {
      Exception exception = null;
      String message = null;
      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }

        // Create the session.
        session = new Session(/* context= */ this);
      } catch (UnavailableArcoreNotInstalledException
          | UnavailableUserDeclinedInstallationException e) {
        message = "Please install ARCore";
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        message = "Please update ARCore";
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        message = "Please update this app";
        exception = e;
      } catch (UnavailableDeviceNotCompatibleException e) {
        message = "This device does not support AR";
        exception = e;
      } catch (RuntimeException e) {
        message = "Failed to create AR session";
        exception = e;
      }

      if (!session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)) {
        message = "This device does not support the ARCore Raw Depth API.";
        session = null;
      }

      if (message != null) {
        messageSnackbarHelper.showError(this, message);
        Log.e(TAG, "Exception creating session", exception);
        return;
      }
    }

    // Note that order matters - see the note in onPause(), the reverse applies here.
    try {
      // Wait until the frame is no longer being processed.
      synchronized (frameInUseLock) {
        // Enable raw depth estimation and auto focus mode while ARCore is running.
        CameraConfigFilter cameraConfigFilter = new CameraConfigFilter(session);
        cameraConfigFilter.setFacingDirection(CameraConfig.FacingDirection.FRONT);
        List<CameraConfig> cameraConfigs = session.getSupportedCameraConfigs(cameraConfigFilter);
        if (!cameraConfigs.isEmpty()) {
          // Element 0 contains the camera config that best matches the session feature
          // and filter settings.
          session.setCameraConfig(cameraConfigs.get(0));
        }

        configureSession();

        session.resume();

      }
    } catch (CameraNotAvailableException e) {
      throw new RuntimeException(e);
    }
    surfaceView.onResume();
    displayRotationHelper.onResume();

    messageSnackbarHelper.showMessage(this, "No depth yet. Try moving the device.");
  }

  @Override
  public void onPause() {
    super.onPause();
    if (session != null) {
      // Note that the order matters - see note in onResume().
      // GLSurfaceView is paused before pausing the ARCore session, to prevent onDrawFrame() from
      // calling session.update() on a paused session.
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(requestCode, permissions, results);
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
          .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

    // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
    try {
      renderer.createOnGlThread(/*context=*/ this);
    } catch (IOException e) {
      Log.e(TAG, "Failed to read an asset file", e);
    }
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    GLES20.glViewport(0, 0, width, height);
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    // Clear screen to notify driver it should not load any pixels from previous frame.
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    if (session == null) {
      return;
    }

    // Synchronize prevents session.update() call while paused, see note in onPause().
    synchronized (frameInUseLock) {
      // Notify ARCore that the view size changed so that the perspective matrix can be adjusted.
      displayRotationHelper.updateSessionIfNeeded(session);

      try {
        session.setCameraTextureNames(new int[] {0});

        Frame frame = session.update();
        Camera camera = frame.getCamera();

        // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
        trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

        if (camera.getTrackingState() != TrackingState.TRACKING) {
          // If motion tracking is not available but previous depth is available, notify the user
          // that the app will resume with tracking.
          if (depthReceived) {
            messageSnackbarHelper.showMessage(
                this, TrackingStateHelper.getTrackingFailureReasonString(camera));
          }

          // If not tracking, do not render the point cloud.
          return;
        }

        // Check if the frame contains new depth data or a 3D reprojection of the previous data. See
        // documentation of acquireRawDepthImage16Bits for more details.
        boolean containsNewDepthData;
        try (Image depthImage = frame.acquireRawDepthImage16Bits()) {
          containsNewDepthData = depthTimestamp == depthImage.getTimestamp();
          depthTimestamp = depthImage.getTimestamp();
        } catch (NotYetAvailableException e) {
          // This is normal at the beginning of session, where depth hasn't been estimated yet.
          containsNewDepthData = false;
        }

        btnSnapshot = findViewById(R.id.btnSnapshot);
        List<String[]> csvData = new ArrayList<>();

        csvData.add(new String[]{"Name", "Age", "Country"});
        csvData.add(new String[]{"John Doe", "30", "United States"});
        csvData.add(new String[]{"Jane Doe", "25", "Canada"});



        if (containsNewDepthData) {

          // Get Raw Depth data of the current frame.
          final DepthData depth = DepthData.create(session, frame);

          StringBuilder result = new StringBuilder();

          for (int i = 0; i <depth.getPoints().capacity(); i++) {
            String dataPoints = String.valueOf(depth.getPoints().get(i));         // confidence
            result.append(dataPoints).append("");

          }
          String csv = (Environment.getExternalStorageDirectory() + "/MyCsvFile.csv"); // Here csv file name is MyCsvFile.csv
          btnSnapshot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

              String filename = "pointcloud_teste_perto.txt";

              if (isExternalStorageWritable()) {
                // Obter o diretório de documentos do armazenamento externo
                File documentsDirectory = getDocumentsDirectory();

                // Criar o arquivo de texto no diretório de documentos
                File file = new File(documentsDirectory, filename);

                try {
                  // Abrir um fluxo de saída para o arquivo
                  FileOutputStream outputStream = new FileOutputStream(file);

                  // Escrever o conteúdo no arquivo
                  String content = String.valueOf(result);
                  outputStream.write(content.getBytes());

                  // Fechar o fluxo de saída
                  outputStream.close();

                  // Notificar que o arquivo foi criado com sucesso
                  Toast.makeText(RawDepthActivity.this, "Arquivo criado com sucesso: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
                } catch (IOException e) {
                  e.printStackTrace();
                  // Lidar com o erro de criação do arquivo
                  Toast.makeText(RawDepthActivity.this, "Erro ao criar o arquivo", Toast.LENGTH_SHORT).show();
                }
              } else {
                // Lidar com o caso em que o armazenamento externo não está disponível
                Toast.makeText(RawDepthActivity.this, "O armazenamento externo não está disponível", Toast.LENGTH_SHORT).show();
              }
            }
          });

          Log.d(TAG, String.valueOf(depth.getPoints().capacity()));

          // Skip rendering the current frame if an exception arises during depth data processing.
          // For example, before depth estimation finishes initializing.
          if (depth != null) {
            depthReceived = true;
            renderer.update(depth);
          }
        }

        float[] projectionMatrix = new float[16];
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);
        float[] viewMatrix = new float[16];
        camera.getViewMatrix(viewMatrix, 0);

        // Visualize depth points.
        renderer.draw(viewMatrix, projectionMatrix);

        // Hide all user notifications when the frame has been rendered successfully.
        messageSnackbarHelper.hide(this);

        StringBuilder result = new StringBuilder();


      } catch (Throwable t) {
        // Avoid crashing the application due to unhandled exceptions.
        Log.e(TAG, "Exception on the OpenGL thread", t);
      }


    }
  }

  private boolean isExternalStorageWritable() {
    String state = Environment.getExternalStorageState();
    if (Environment.MEDIA_MOUNTED.equals(state)) {
      return true;
    } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
      Toast.makeText(RawDepthActivity.this, "Permissão de escrita no armazenamento externo negada. Garanta a permissão para salvar o arquivo CSV.", Toast.LENGTH_LONG).show();
    } else {
      Toast.makeText(RawDepthActivity.this, "O armazenamento externo não está disponível.", Toast.LENGTH_SHORT).show();
    }
    return false;
  }

  private File getDocumentsDirectory() {
    return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
  }

  public static void writeDataLineByLine(String filePath)
  {
    // first create file object for file placed at location
    // specified by filepath
    File file = new File(filePath);
    try {
      // create FileWriter object with file as parameter
      FileWriter outputfile = new FileWriter(file);

      // create CSVWriter object filewriter object as parameter
      CSVWriter writer = new CSVWriter(outputfile);

      // adding header to csv
      String[] header = { "Name", "Class", "Marks" };
      writer.writeNext(header);

      // add data to csv
      String[] data1 = { "Aman", "10", "620" };
      writer.writeNext(data1);
      String[] data2 = { "Suraj", "10", "630" };
      writer.writeNext(data2);
      Log.e(TAG, "CASCASCASCA");
      // closing writer connection
      writer.close();
    }
    catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  private void configureSession() {
    Config config = new Config(session);
    config.setDepthMode(Config.DepthMode.RAW_DEPTH_ONLY);
    session.configure(config);
  }

}
