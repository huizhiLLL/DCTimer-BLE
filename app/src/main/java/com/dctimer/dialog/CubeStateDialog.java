package com.dctimer.dialog;

import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.dctimer.R;
import com.dctimer.activity.MainActivity;
import com.dctimer.model.SmartCube;
import com.dctimer.view.SmartCube3DView;
import com.dctimer.view.SmartCubeImageView;

public class CubeStateDialog extends DialogFragment {
    private SmartCube cube;
    private TextView tvBattery;
    private ImageView ivBattery;
    private SmartCubeImageView imageView;
    private SmartCube3DView cube3DView;

    public static CubeStateDialog newInstance(SmartCube cube) {
        CubeStateDialog dialog = new CubeStateDialog();
        Bundle bundle = new Bundle();
        bundle.putSerializable("cube", cube);
        dialog.setArguments(bundle);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        cube = getArguments() == null ? null : (SmartCube) getArguments().getSerializable("cube");
        AlertDialog.Builder buidler = new AlertDialog.Builder(getActivity());
        View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_cube_state, null);
        tvBattery = view.findViewById(R.id.tv_battery);
        SmartCube currentCube = resolveCube();
        int batteryValue = currentCube == null ? 0 : currentCube.getBatteryValue();
        tvBattery.setText(batteryValue + "%");
        ivBattery = view.findViewById(R.id.iv_battery);
        setBatteryImage(batteryValue);
        cube3DView = view.findViewById(R.id.gl_cube_state);
        if (cube3DView != null && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).applySmartCubeAppearance(cube3DView);
        }
        imageView = view.findViewById(R.id.image_view);
        setImage();
        Button btRefresh = view.findViewById(R.id.btn_refresh);
        btRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SmartCube currentCube = resolveCube();
                int batteryValue = currentCube == null ? 0 : currentCube.getBatteryValue();
                tvBattery.setText(batteryValue + "%");
                setBatteryImage(batteryValue);
                setImage();
            }
        });
        Button btMarkSolve = view.findViewById(R.id.bt_solved);
        btMarkSolve.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).resetSmartCubeToSolved();
                } else if (cube != null) {
                    cube.markSolved();
                }
                setImage();
            }
        });
        Button btResetPosture = view.findViewById(R.id.bt_reset_posture);
        btResetPosture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).resetSmartCubeGyroPosture();
                } else {
                    resetGyroPosture();
                }
            }
        });
        Button btDisconnect = view.findViewById(R.id.bt_scrambled);
        btDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).disconnectSmartCube();
                }
                dismissAllowingStateLoss();
            }
        });
        String title = currentCube == null ? null : currentCube.getDeviceName();
        if (title == null || title.trim().isEmpty()) {
            title = "SmartCube";
        }
        buidler.setTitle(title).setView(view).setNegativeButton(R.string.btn_close, null);
        return buidler.create();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (cube3DView != null) {
            cube3DView.onResume();
            setImage();
            applyLatestGyro();
        }
    }

    @Override
    public void onPause() {
        if (cube3DView != null) {
            cube3DView.onPause();
        }
        super.onPause();
    }

    private void setBatteryImage(int batteryValue) {
        if (batteryValue >= 95) ivBattery.setImageResource(R.drawable.ic_battery_100);
        else if (batteryValue >= 85) ivBattery.setImageResource(R.drawable.ic_battery_90);
        else if (batteryValue >= 75) ivBattery.setImageResource(R.drawable.ic_battery_80);
        else if (batteryValue >= 65) ivBattery.setImageResource(R.drawable.ic_battery_70);
        else if (batteryValue >= 55) ivBattery.setImageResource(R.drawable.ic_battery_60);
        else if (batteryValue >= 45) ivBattery.setImageResource(R.drawable.ic_battery_50);
        else if (batteryValue >= 35) ivBattery.setImageResource(R.drawable.ic_battery_40);
        else if (batteryValue >= 25) ivBattery.setImageResource(R.drawable.ic_battery_30);
        else if (batteryValue >= 15) ivBattery.setImageResource(R.drawable.ic_battery_20);
        else if (batteryValue >= 5) ivBattery.setImageResource(R.drawable.ic_battery_10);
        else ivBattery.setImageResource(R.drawable.ic_battery_10);
    }

    private void setImage() {
        SmartCube currentCube = resolveCube();
        if (currentCube != null) {
            if (cube3DView != null) {
                cube3DView.setVisibility(View.VISIBLE);
                if (imageView != null) {
                    imageView.setVisibility(View.GONE);
                }
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).applySmartCubeAppearance(cube3DView);
                }
                cube3DView.showCubeState(currentCube.getCubeState());
                applyLatestGyro();
            } else if (imageView != null) {
                imageView.setVisibility(View.VISIBLE);
                imageView.showCubeState(currentCube.getCubeState());
            }
        }
    }

    public void refreshState() {
        SmartCube currentCube = resolveCube();
        int batteryValue = currentCube == null ? 0 : currentCube.getBatteryValue();
        if (tvBattery != null) {
            tvBattery.setText(batteryValue + "%");
        }
        if (ivBattery != null) {
            setBatteryImage(batteryValue);
        }
        setImage();
    }

    public void playMove(String fromState, String toState, int move) {
        if (cube != null) {
            cube.setCubeState(toState);
        }
        if (cube3DView != null) {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).applySmartCubeAppearance(cube3DView);
            }
            cube3DView.animateMove(fromState, toState, move);
            applyLatestGyro();
        } else if (imageView != null) {
            imageView.animateMove(fromState, toState, move);
        }
    }

    public void setAppearanceMode(int appearanceMode) {
        if (cube3DView != null) {
            cube3DView.setAppearanceMode(appearanceMode);
        }
    }

    public void setGyroQuaternion(float x, float y, float z, float w) {
        if (cube3DView != null) {
            cube3DView.setGyroQuaternion(x, y, z, w);
        }
    }

    public void resetGyroPosture() {
        if (cube3DView != null) {
            cube3DView.resetGyroPosture();
        }
    }

    public void resetGyroPosture(float[] gyro, boolean hasGyro) {
        if (cube3DView == null) {
            return;
        }
        if (hasGyro && gyro != null && gyro.length >= 4) {
            cube3DView.resetGyroPosture(gyro[0], gyro[1], gyro[2], gyro[3]);
        } else {
            cube3DView.resetGyroPosture();
        }
    }

    public void disableGyroView() {
        if (cube3DView != null) {
            cube3DView.disableGyroView();
        }
    }

    public void applyLatestGyro() {
        if (cube3DView != null && getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            activity.applyLatestSmartCubeGyroCalibration(cube3DView);
            activity.applyLatestSmartCubeGyro(cube3DView);
        }
    }

    private SmartCube resolveCube() {
        if (getActivity() instanceof MainActivity) {
            SmartCube activeCube = ((MainActivity) getActivity()).getSmartCubeForUi();
            if (activeCube != null) {
                cube = activeCube;
                return activeCube;
            }
        }
        return cube;
    }
}
