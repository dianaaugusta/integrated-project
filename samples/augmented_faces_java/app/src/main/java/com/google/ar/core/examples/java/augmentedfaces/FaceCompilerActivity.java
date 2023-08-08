package com.google.ar.core.examples.java.augmentedfaces;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;

import org.w3c.dom.Text;

public class FaceCompilerActivity extends AppCompatActivity {
    TextView txtNoseX;
    TextView txtNoseY;
    TextView txtNoseZ;
    TextView txtEyeDistance;
    TextView txtAdjustmentHatch;
    TextView txtDistancesForehead;

    TextView txtDistanceLeftEar;
    TextView txtDistanceRightEar;

    TextView comparisonNose1;
    TextView comparisonNose2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_compiler);

        txtNoseX = findViewById(R.id.txtNoseX);
        txtNoseY = findViewById(R.id.txtNoseY);
        txtNoseZ = findViewById(R.id.txtNoseZ);
        txtEyeDistance = findViewById(R.id.txtEyeDistance);
        txtAdjustmentHatch = findViewById(R.id.txtAdjustmentHatch);
        txtDistancesForehead = findViewById(R.id.txtDistanceForehead);
        txtDistanceLeftEar = findViewById(R.id.txtDistanceLeftEar);
        txtDistanceRightEar = findViewById(R.id.txtDistanceRightEar);
        comparisonNose1 = findViewById(R.id.txtComparisonNose1);
        comparisonNose2 = findViewById(R.id.txtComparisonNose2);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String between_eyebrows = extras.getString("between_eyebrows");
            String adjust_eyebrow = extras.getString("adjustment_hatch");
            String nose_coordinates_x = extras.getString("nose_coordinates_x");
            String nose_coordinates_y = extras.getString("nose_coordinates_y");
            String nose_coordinates_z = extras.getString("nose_coordinates_z");
            String distance_forehead = extras.getString("forehead_measurement");
            String distance_leftear = extras.getString("leftear_measurement");
            String distance_rightear = extras.getString("rightear_measurement");
            String comparison_nose_1 = extras.getString("first_nose_z_comparison");
            String comparison_nose_2 = extras.getString("second_nose_z_comparison");

            txtDistancesForehead.setText(distance_forehead);
            txtEyeDistance.setText(between_eyebrows);
            txtAdjustmentHatch.setText("+ " + adjust_eyebrow + "%");
            txtNoseX.setText(nose_coordinates_x);
            txtNoseY.setText(nose_coordinates_y);
            txtNoseZ.setText(nose_coordinates_z);
            txtDistanceRightEar.setText(distance_rightear);
            txtDistanceLeftEar.setText(distance_leftear);
            comparisonNose1.setText(comparison_nose_1);
            comparisonNose2.setText(comparison_nose_2);


        }
    }


}