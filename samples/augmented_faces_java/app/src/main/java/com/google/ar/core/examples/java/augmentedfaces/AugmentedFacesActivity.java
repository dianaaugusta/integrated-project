/*
 * Copyright 2020 Google LLC
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

package com.google.ar.core.examples.java.augmentedfaces;

import android.Manifest;
import android.content.Intent;
import android.media.FaceDetector;
import android.media.Image;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedFace;
import com.google.ar.core.AugmentedFace.RegionType;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Config;
import com.google.ar.core.Config.AugmentedFaceMode;
import com.google.ar.core.Frame;
import com.google.ar.core.Point;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import com.google.ar.core.examples.java.common.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer;
import com.google.ar.core.examples.java.common.rendering.PointCloudRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3d model of the Android robot.
 */
public class AugmentedFacesActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
  private static final String TAG = AugmentedFacesActivity.class.getSimpleName();

  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private GLSurfaceView surfaceView;
  private boolean installRequested;
  private Session session;
  private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
  private DisplayRotationHelper displayRotationHelper;
  private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
  private Switch switchToggleDebug;

  private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
  private final AugmentedFaceRenderer augmentedFaceRenderer = new AugmentedFaceRenderer();
  private final ObjectRenderer noseObject = new ObjectRenderer();
  private final ObjectRenderer rightEarObject = new ObjectRenderer();
  private final ObjectRenderer leftEarObject = new ObjectRenderer();
  private float variableValue;
  private SeekBar slider;
  private Button btnPassDataActivies;
  private Button btnCameraActivity;
  private TextView txtAjuste;
  private TextView txtApproach;
  Integer activateGlassesView = 1;

  private final ObjectRenderer leftEyeObject = new ObjectRenderer();
  // Temporary matrix allocated here to reduce number of allocations for each frame.
  private final float[] noseMatrix = new float[16];
  private final float[] rightEarMatrix = new float[16];
  private final float[] leftEarMatrix = new float[16];
  float scaleFactor = 0.5f; // Valor de escala desejado (0.5f reduzirá o tamanho pela metade)
  private static final float[] DEFAULT_COLOR = new float[] {0f, 0f, 0f, 0f};

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    surfaceView = findViewById(R.id.surfaceview);
    displayRotationHelper = new DisplayRotationHelper(this);

    // Set up renderer.
    surfaceView.setPreserveEGLContextOnPause(true);
    surfaceView.setEGLContextClientVersion(2);
    surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
    surfaceView.setRenderer(this);
    surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    surfaceView.setWillNotDraw(false);


    installRequested = false;
  }

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

        // Create the session and configure it to use a front-facing (selfie) camera.
        session = new Session(/* context= */ this, EnumSet.noneOf(Session.Feature.class));
        CameraConfigFilter cameraConfigFilter = new CameraConfigFilter(session);
        cameraConfigFilter.setFacingDirection(CameraConfig.FacingDirection.FRONT);
        List<CameraConfig> cameraConfigs = session.getSupportedCameraConfigs(cameraConfigFilter);
        if (!cameraConfigs.isEmpty()) {
          // Element 0 contains the camera config that best matches the session feature
          // and filter settings.
          session.setCameraConfig(cameraConfigs.get(0));
        } else {
          message = "This device does not have a front-facing (selfie) camera";
          exception = new UnavailableDeviceNotCompatibleException(message);
        }
        configureSession();

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
      } catch (Exception e) {
        message = "Failed to create AR session";
        exception = e;
      }

      if (message != null) {
        messageSnackbarHelper.showError(this, message);
        Log.e(TAG, "Exception creating session", exception);
        return;
      }
    }

    // Note that order matters - see the note in onPause(), the reverse applies here.
    try {
      session.resume();
    } catch (CameraNotAvailableException e) {
      messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
      session = null;
      return;
    }

    surfaceView.onResume();
    displayRotationHelper.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (session != null) {
      // Note that the order matters - GLSurfaceView is paused first so that it does not try
      // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
      // still call session.update() and get a SessionPausedException.
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
      // Create the texture and pass it to ARCore session to be filled during update().
      backgroundRenderer.createOnGlThread(this);
      augmentedFaceRenderer.createOnGlThread(this, "models/freckles.png");
      augmentedFaceRenderer.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f);
      noseObject.createOnGlThread( this, "models/glasses.obj", "models/glasses.png");
      noseObject.setMaterialProperties(1.0f, 1.0f, 0.1f, 6.0f);

      noseObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending);
      // Defina a escala do objeto


      leftEyeObject.createOnGlThread( this, "models/nose.obj", "models/nose_fur.png");
      leftEyeObject.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f);
      leftEyeObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending);
      rightEarObject.createOnGlThread(this, "models/forehead_right.obj", "models/ear_fur.png");
      rightEarObject.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f);
      rightEarObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending);
      leftEarObject.createOnGlThread(this, "models/forehead_left.obj", "models/ear_fur.png");
      leftEarObject.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f);
      leftEarObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending);

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
    Switch swDebugGlasses = findViewById(R.id.swDebugGlasses);


    if (session == null) {
      return;
    }
    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    displayRotationHelper.updateSessionIfNeeded(session);

    // Instanciando o toggle

    try {
      session.setCameraTextureName(backgroundRenderer.getTextureId());

      // Obtain the current frame from ARSession. When the configuration is set to
      // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
      // camera framerate.
      Frame frame = session.update();
      Camera camera = frame.getCamera();


      // Get projection matrix.
      float[] projectionMatrix = new float[16];
      camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);
      camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);
      CameraIntrinsics intrinsics = frame.getCamera().getTextureIntrinsics();

      // Get camera matrix and draw.
      float[] viewMatrix = new float[16];
      camera.getViewMatrix(viewMatrix, 0);


      // Compute lighting from average intensity of the image.
      // The first three components are color scaling factors.
      // The last one is the average pixel intensity in gamma space.
      final float[] colorCorrectionRgba = new float[4];
      frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

      // If frame is ready, render camera preview image to the GL surface.
      backgroundRenderer.draw(frame);

      // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
      trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());


      // ARCore's face detection works best on upright faces, relative to gravity.
      // If the device cannot determine a screen side aligned with gravity, face
      // detection may not work optimally.
      Collection<AugmentedFace> faces = session.getAllTrackables(AugmentedFace.class);
      for (AugmentedFace face : faces) {
        if (face.getTrackingState() != TrackingState.TRACKING) {
          break;
        }

        float[] defaultColor   = {1.0f, 1.0f, 1.0f, 1.0f};
        float scaleFactor = 1.0f;

        slider = findViewById(R.id.slider);
        btnPassDataActivies = findViewById(R.id.buttonTest);
        btnCameraActivity = findViewById(R.id.btnCameraAct);
        txtAjuste =  findViewById(R.id.txtAjuste);
        variableValue = slider.getProgress() / 50.0f;
        txtApproach = findViewById(R.id.txtApproachDistance);


        // Face objects use transparency so they must be rendered back to front without depth write.
        GLES20.glDepthMask(false);

        // Each face's region poses, mesh vertices, and mesh normals are updated every frame.
        // 1. Render the face mesh first, behind any 3D objects attached to the face regions.
        float[] modelMatrix = new float[16];
        face.getCenterPose().toMatrix(modelMatrix, 0);

        //log das infos da face detectada
        /*FloatBuffer uvs = face.getMeshTextureCoordinates();
        Log.d(TAG, "UVs: " + uvs.toString());
        ShortBuffer indices = face.getMeshTriangleIndices();
        Log.d(TAG, "Indices: " + indices.toString());
        Pose facePose = face.getCenterPose();
        Log.d(TAG, "Face Pose: " + facePose.toString());*/
        FloatBuffer faceVertices = face.getMeshVertices();
       //Log.d(TAG, "Face Vertices: " + faceVertices.toString());
        float[] tempArray = new float[faceVertices.remaining()];
        faceVertices.get(tempArray);
        faceVertices.rewind();

        StringBuilder stringBuilder3 = new StringBuilder();
        for (int i = 0; i < 4; i++) {
          for (int j = 0; j < 4; j++) {
            float value = tempArray[i * 4 + j];
            stringBuilder3.append(value).append(", ");
          }
          stringBuilder3.append("\n");
        }

        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < 4; i++) {
          for (int j = 0; j < 4; j++) {
            float value = modelMatrix[i * 4 + j];
            stringBuilder.append(value).append(", ");
          }
          stringBuilder.append("\n");
        }

        //Log.d(TAG, "Model Matrix:\n" + stringBuilder.toString());

        //fim dos logs

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            variableValue = (float) progress / 50.0f;
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {

          }

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {
            int delta = seekBar.getProgress() - 50;
            float percentage = (float) delta;

            variableValue = percentage;

            String displayText;
            if (percentage >= 0) {
              displayText = "+" + String.format("%.1f%%", percentage);
            } else {
              displayText = String.format("%.1f%%", percentage);
            }

            txtAjuste.setText(displayText);
          }
        });

        augmentedFaceRenderer.drawFaceEspecificPoints(projectionMatrix, viewMatrix,
                modelMatrix, colorCorrectionRgba, face,
                251,
                21,
                defaultColor);

        augmentedFaceRenderer.drawFaceEspecificPoints(projectionMatrix, viewMatrix,
                modelMatrix, colorCorrectionRgba, face,
                127 ,
                25,
                defaultColor);


        augmentedFaceRenderer.drawFaceEspecificPoints(projectionMatrix, viewMatrix,
                modelMatrix, colorCorrectionRgba, face,
                356,
                359,
                defaultColor);


        augmentedFaceRenderer.drawScalableFaceEspecificPoints(projectionMatrix, viewMatrix,
                modelMatrix, colorCorrectionRgba, face,
                FacePoints.Point.UTMOST_LEFT_EYEBROW.getIndex(),
                FacePoints.Point.UTMOST_RIGHT_EYEBROW.getIndex(),
                FacePoints.Point.UTMOST_RIGHT_APPLE.getIndex(),
                FacePoints.Point.UTMOST_LEFT_APPLE.getIndex(),
                defaultColor, variableValue, 1.0F);


        augmentedFaceRenderer.colorSingularPoint(projectionMatrix, viewMatrix,
                modelMatrix, colorCorrectionRgba, face, FacePoints.Point.NOSE_GLASSES_SUPPORT.getIndex() ,defaultColor);

        float distances = augmentedFaceRenderer.calculateDistances(face,
                FacePoints.Point.UTMOST_LEFT_EYEBROW.getIndex(), FacePoints.Point.UTMOST_RIGHT_EYEBROW.getIndex()
        );

        float distancesForehead = augmentedFaceRenderer.calculateDistances(face,
                FacePoints.Point.UTMOST_LEFT_FOREHEAD.getIndex(),   FacePoints.Point.UTMOST_RIGHT_FOREHEAD.getIndex()
        );

        float comparisonNose1;
        float comparisonNose2;

        float[] coordinatesNose = augmentedFaceRenderer.getCoordinatesCenterFace(face, FacePoints.Point.NOSE_GLASSES_SUPPORT.getIndex());
        float[] coordinatesNose1 = augmentedFaceRenderer.getCoordinatesCenterFace(face, 188);
        float[] coordinatesNose2 = augmentedFaceRenderer.getCoordinatesCenterFace(face, 412);

        comparisonNose1 = coordinatesNose[2] - coordinatesNose1[2];
        comparisonNose2 = coordinatesNose[2] - coordinatesNose2[2];


        float distancesOfLeftEarToEye = augmentedFaceRenderer.calculateDistances(face,
                127 , 25
        );

        float distancesOfRightEarToEye = augmentedFaceRenderer.calculateDistances(face,
                127 , 25
        );
        DecimalFormat decimalFormat = new DecimalFormat("#.#"); // Define o formato com 1 casa decimal
        decimalFormat.setGroupingUsed(false);

        StringBuilder distancesStringBuilder = new StringBuilder();
        distancesStringBuilder.append("between_eyebrows: ").append(distances * 100);
        distancesStringBuilder.append("adjustment_hatch: ").append(variableValue * 10).append("\n");
        distancesStringBuilder.append("forehead_measurement: ").append(distancesForehead * 100);
        distancesStringBuilder.append("nose_coordinates_x: ").append(coordinatesNose[0]).append("\n");
        distancesStringBuilder.append("nose_coordinates_y: ").append(coordinatesNose[1]).append("\n");
        distancesStringBuilder.append("nose_coordinates_z: ").append(coordinatesNose[2]).append("\n");
        distancesStringBuilder.append("leftear_measurement: ").append(distancesOfLeftEarToEye * 100);
        distancesStringBuilder.append("rightear_measurement: ").append(distancesOfRightEarToEye * 100);
        distancesStringBuilder.append("first_nose_z_comparison: ").append(comparisonNose1 * 100);
        distancesStringBuilder.append("second_nose_z_comparison: ").append(comparisonNose2 * 100);

        btnCameraActivity.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            Log.e(TAG, "CLICKADO");
            long currentTimeMillis = System.currentTimeMillis();
            String titleWithTimestamp = "mesh_" + currentTimeMillis;
            String titleDistancesWithTimestamp = "distances_" + currentTimeMillis;
            saveFile(arrayToString(tempArray), titleWithTimestamp);
            saveFile(String.valueOf(distancesStringBuilder), titleDistancesWithTimestamp);
            Intent intent = new Intent(AugmentedFacesActivity.this, CameraPreviewActivity.class);
            startActivity(intent);
          }
        });

        btnPassDataActivies.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            Intent intent = new Intent(AugmentedFacesActivity.this, FaceCompilerActivity.class);
            intent.putExtra("between_eyebrows", String.valueOf(distances* 100));
            intent.putExtra("adjustment_hatch", String.valueOf(variableValue * 10 + "%"));
            intent.putExtra("forehead_measurement", String.valueOf(distancesForehead * 100) + " cm");
            intent.putExtra("nose_coordinates_x",String.valueOf(coordinatesNose[0]));
            intent.putExtra("nose_coordinates_y",String.valueOf(coordinatesNose[1]));
            intent.putExtra("nose_coordinates_z",String.valueOf(coordinatesNose[2]));
            intent.putExtra("leftear_measurement",String.valueOf(distancesOfLeftEarToEye * 100) + " cm");
            intent.putExtra("rightear_measurement",String.valueOf(distancesOfRightEarToEye * 100) + " cm");
            intent.putExtra("first_nose_z_comparison",String.valueOf(comparisonNose1 * 100) + " cm");
            intent.putExtra("second_nose_z_comparison",String.valueOf(comparisonNose2 * 100) + " cm");
            startActivity(intent);
          }
        });

        //switch para ativar ou desativar a visualização dos óculos sobrepondo a mesh
        swDebugGlasses.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
              activateGlassesView = 0;
            } else {
              activateGlassesView = 1;
            }
          }
        });

        if(coordinatesNose2[2] * 100 > 5){
          txtApproach.setText("Mantenha a posição");

            Pose centerPose = face.getCenterPose();

            float[] noseMatrix = new float[16];
            centerPose.toMatrix(noseMatrix, 0);

            float x = 0.0f;
            float glassesOffsetScale = -0.0310f;
            float y = coordinatesNose[1] + glassesOffsetScale;
            float z = coordinatesNose[2];

            Matrix.translateM(noseMatrix, 0, x, y, z);
          if(activateGlassesView == 1) {
            float scaleFactor2 = 1.05f;

            Matrix.scaleM(noseMatrix, 0, scaleFactor, scaleFactor, scaleFactor);

            noseObject.updateModelMatrix(noseMatrix, scaleFactor2);
          }else{
            float scaleFactor2 = 1.0f; // Adjust this scale factor as needed

            Matrix.scaleM(noseMatrix, 0, scaleFactor, scaleFactor, scaleFactor);

            noseObject.updateModelMatrix(noseMatrix, scaleFactor2);
          }

            noseObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, DEFAULT_COLOR);

        }else{
          txtApproach.setText("Afaste-se");
        }




        /*face.getCenterPose().toMatrix(distances, 0);
        noseObject.updateModelMatrix(distances, scaleFactor);
        noseObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, DEFAULT_COLOR);*/
      }
    } catch (Throwable t) {
      // Avoid crashing the application due to unhandled exceptions.
      Log.e(TAG, "Exception on the OpenGL thread", t);
    } finally {
      GLES20.glDepthMask(true);
    }
  }

  private void configureSession() {
    Config config = new Config(session);
    config.setAugmentedFaceMode(AugmentedFaceMode.MESH3D);
    session.configure(config);
  }

  private void saveFile(String meshData, String fileName){
    String filename = fileName + ".txt";

    if (isExternalStorageWritable()) {
      // Obter o diretório de documentos do armazenamento externo
      File documentsDirectory = getDocumentsDirectory();

      // Criar o arquivo de texto no diretório de documentos
      File file = new File(documentsDirectory, filename);

      try {
        // Abrir um fluxo de saída para o arquivo
        FileOutputStream outputStream = new FileOutputStream(file);

        // Escrever o conteúdo no arquivo
        String content = String.valueOf(meshData);
        outputStream.write(content.getBytes());

        // Fechar o fluxo de saída
        outputStream.close();

        // Notificar que o arquivo foi criado com sucesso
        Toast.makeText(AugmentedFacesActivity.this, "Arquivo criado com sucesso: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
      } catch (IOException e) {
        e.printStackTrace();
        // Lidar com o erro de criação do arquivo
        Toast.makeText(AugmentedFacesActivity.this, String.valueOf(e), Toast.LENGTH_SHORT).show();
      }
    } else {
      // Lidar com o caso em que o armazenamento externo não está disponível
      Toast.makeText(AugmentedFacesActivity.this, "O armazenamento externo não está disponível", Toast.LENGTH_SHORT).show();
    }
  }

  private boolean isExternalStorageWritable() {
    String state = Environment.getExternalStorageState();
    if (Environment.MEDIA_MOUNTED.equals(state)) {
      return true;
    } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
      Toast.makeText(AugmentedFacesActivity.this, "Permissão de escrita no armazenamento externo negada. Garanta a permissão para salvar o arquivo CSV.", Toast.LENGTH_LONG).show();
    } else {
      Toast.makeText(AugmentedFacesActivity.this, "O armazenamento externo não está disponível.", Toast.LENGTH_SHORT).show();
    }
    return false;
  }

  private File getDocumentsDirectory() {
    return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
  }
  public static String arrayToString(float[] array) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < array.length; i++) {
      sb.append(array[i]);
      if (i < array.length - 1) {
        sb.append(",");
      }
    }
    return sb.toString();
  }


}