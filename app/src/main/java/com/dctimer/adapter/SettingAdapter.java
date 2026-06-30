package com.dctimer.adapter;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.dctimer.activity.MainActivity;
import com.dctimer.R;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.dctimer.APP.*;

public class SettingAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int ST_WCA = 1;
    public static final int ST_INSPECTION_ALERT = 2;
    public static final int ST_TIME_FORMAT = 3;
    public static final int ST_DECIMAL_MARK = 4;
    public static final int ST_ENTER_TIME = 5;
    public static final int ST_TIMER_UPDATE = 6;
    public static final int ST_TIMER_ACCURACY = 7;
    public static final int ST_START_DELAY = 8;
    public static final int ST_MULTI_PHASE = 9;
    public static final int ST_SIMULATE_SS = 10;
    public static final int ST_SHOW_STATS = 11;
    public static final int ST_DROP_TO_STOP = 12;
    public static final int ST_SENSITIVITY = 13;
    public static final int ST_SMART_GYRO_FOLLOW = 14;
    public static final int ST_SMART_ORIENTATION = 15;
    public static final int ST_SMART_SCRAMBLE_PROGRESS = 16;
    public static final int ST_SMART_LAYOUT = 17;
    public static final int ST_SMART_SOLVE_METHOD = 18;
    public static final int ST_SMART_APPEARANCE = 31;
    public static final int ST_SMART_CUBE_SIZE = 24;
    public static final int ST_SCR_FONT = 19;
    public static final int ST_MONO_SCRAMBLE = 20;
    public static final int ST_SHOW_SCRAMBLE = 21;
    public static final int ST_IMAGE_SIZE = 22;
    public static final int ST_EG_SCRAMBLE = 23;
    public static final int ST_PROMPT_TO_SAVE = 25;
    public static final int ST_AVG1_TYPE = 26;
    public static final int ST_AVG1_LEN = 27;
    public static final int ST_AVG2_TYPE = 28;
    public static final int ST_AVG2_LEN = 29;
    public static final int ST_SELECT_SESSION = 30;
    public static final int ST_SOLVE_333 = 32;
    public static final int ST_SOLVE_SQ1 = 33;
    public static final int ST_SOLVE_222 = 34;
    public static final int ST_SOLVE_PYR = 35;
    public static final int ST_SCHEME_NNN = 37;
    public static final int ST_SCHEME_PYR = 38;
    public static final int ST_SCHEME_SQ1 = 39;
    public static final int ST_SCHEME_SKEWB = 40;
    public static final int ST_MEGA_SCHEME = 41;
    public static final int ST_TIMER_FONT = 43;
    public static final int ST_TIMER_SIZE = 44;
    public static final int ST_BACKGROUND_COLOR = 45;
    public static final int ST_TEXT_COLOR = 46;
    public static final int ST_BACKGROUND_IMAGE = 47;
    public static final int ST_SHOW_BACKGROUND_IMAGE = 48;
    public static final int ST_OPACITY = 49;
    public static final int ST_BEST_TIME_COLOR = 50;
    public static final int ST_WORST_TIME_COLOR = 51;
    public static final int ST_BEST_AVERAGE_COLOR = 52;
    public static final int ST_GESTURE_LEFT = 54;
    public static final int ST_GESTURE_RIGHT = 55;
    public static final int ST_GESTURE_UP = 56;
    public static final int ST_GESTURE_DOWN = 57;
    public static final int ST_SCREEN_ON = 59;
    public static final int ST_VIBRATE = 60;
    public static final int ST_VIBRATE_TIME = 61;
    public static final int ST_SCREEN_ORIENTATION = 62;
    public static final int ST_APP_LANGUAGE = 63;
    private MainActivity dct;
    private Map<Integer, String> headers;
    private List<Map<String, Object>> cells;

    static class ViewHolder extends RecyclerView.ViewHolder {
        View item;
        RelativeLayout rlCell;
        LinearLayout layoutCell;
        TextView textView;
        TextView detailView;
        SeekBar seekAccessory;
        Switch checkAccessory;
        View divider;

        public ViewHolder(View view) {
            super(view);
            item = view;
            rlCell = view.findViewById(R.id.rl_item);
            layoutCell = view.findViewById(R.id.layout_cell);
            textView = view.findViewById(R.id.list_text);
            detailView = view.findViewById(R.id.list_detail);
            seekAccessory = view.findViewById(R.id.seek_accessory);
            checkAccessory = view.findViewById(R.id.check_accessory);
            divider = view.findViewById(R.id.divider);
        }
    }

    static class Header extends RecyclerView.ViewHolder {
        TextView tvHead;
        //View divider;
        View view;
        public Header(View v) {
            super(v);
            tvHead = v.findViewById(R.id.list_header_title);
            view = v.findViewById(R.id.view);
            //divider = v.findViewById(R.id.divider);
        }
    }

    static class Footer extends RecyclerView.ViewHolder {
        Button btnReset;
        public Footer(View v) {
            super(v);
            btnReset = v.findViewById(R.id.btn_reset);
        }
    }

    public SettingAdapter(MainActivity dct, Map<Integer, String> headers, List<Map<String, Object>> cells) {
        this.dct = dct;
        this.headers = headers;
        this.cells = cells;
    }

    public void reload() {
        for (int i = 0; i < cells.size(); i++) {
            Map<String, Object> map = cells.get(i);
            switch (getSettingId(map)) {
                case ST_WCA: //wca
                    map.put("detail", wca);
                    break;
                case ST_INSPECTION_ALERT: //观察语音
                    map.put("detail", inspectionAlert);
                    break;
                case ST_TIME_FORMAT: //时间格式
                    map.put("detail", itemStr[13][timeFormat]);
                    break;
                case ST_DECIMAL_MARK: //小数点格式
                    map.put("detail", itemStr[16][decimalMark]);
                    break;
                case ST_ENTER_TIME: //成绩输入方式
                    map.put("detail", itemStr[0][enterTime]);
                    break;
                case ST_TIMER_UPDATE: //更新方式
                    map.put("detail", itemStr[1][timerUpdate]);
                    break;
                case ST_TIMER_ACCURACY: //计时器精度
                    map.put("detail", itemStr[2][timerAccuracy]);
                    break;
                case ST_START_DELAY: //启动延时
                    map.put("detail", String.format(Locale.getDefault(), "%.02fs", freezeTime/20f));
                    map.put("value", freezeTime);
                    break;
                case ST_MULTI_PHASE: //分段计时
                    map.put("detail", itemStr[3][multiPhase]);
                    break;
                case ST_SIMULATE_SS: //模拟ss计时
                    map.put("detail", simulateSS);
                    break;
                case ST_SHOW_STATS:    //显示统计
                    map.put("detail", showStat);
                    break;
                case ST_DROP_TO_STOP:    //拍桌子停表
                    map.put("detail", dropToStop);
                    break;
                case ST_SENSITIVITY:    //灵敏度

                    break;
                case ST_SMART_ORIENTATION:
                    map.put("detail", dct.getSmartCubeOrientationLabel(smartCubeSolveOrientation));
                    break;
                case ST_SMART_SOLVE_METHOD:
                    map.put("detail", dct.getResources().getStringArray(R.array.opt_smart_solve_method)[smartCubeSolveMethod]);
                    break;
                case ST_SMART_SCRAMBLE_PROGRESS:
                    map.put("detail", dct.getResources().getStringArray(R.array.opt_smart_scramble_progress)[smartCubeScrambleProgressStyle]);
                    break;
                case ST_SMART_APPEARANCE:
                    map.put("detail", dct.getResources().getStringArray(R.array.opt_smart_cube_appearance)[smartCubeAppearance]);
                    break;
                case ST_SMART_CUBE_SIZE:
                    map.put("detail", String.valueOf(smartCubeSize));
                    map.put("value", smartCubeSize / 10 - 16);
                    break;
                case ST_SMART_GYRO_FOLLOW:
                    map.put("detail", smartCubeGyroFollow);
                    break;
                case ST_SMART_LAYOUT:
                    map.put("detail", dct.getResources().getStringArray(R.array.opt_smart_layout)[smartCubeLayoutMode]);
                    break;
                case ST_SCR_FONT:    //打乱字体大小
                    map.put("detail", String.valueOf(scrambleSize));
                    map.put("value", scrambleSize - 12);
                    break;
                case ST_MONO_SCRAMBLE:    //等宽打乱
                    map.put("detail", monoFont);
                    break;
                case ST_SHOW_SCRAMBLE:    //显示打乱
                    map.put("detail", showImage);
                    break;
                case ST_PROMPT_TO_SAVE:    //确认成绩
                    map.put("detail", promptToSave);
                    break;
                case ST_AVG1_TYPE:    //滚动平均1
                    map.put("detail", itemStr[14][avg1Type]);
                    break;
                case ST_AVG1_LEN:
                    map.put("detail", String.valueOf(avg1len));
                    break;
                case ST_AVG2_TYPE:    //滚动平均2
                    map.put("detail", itemStr[4][avg2Type]);
                    break;
                case ST_AVG2_LEN:
                    map.put("detail", String.valueOf(avg2len));
                    break;
                case ST_SELECT_SESSION:    //更改分组
                    map.put("detail", selectSession);
                    break;
                case ST_SOLVE_333:    //三阶求解
                    map.put("detail", itemStr[5][solve333]);
                    break;
                case ST_SOLVE_SQ1:    //SQ1求解
                    map.put("detail", itemStr[12][solveSq1]);
                    break;
                case ST_SOLVE_222:    //二阶求解
                    map.put("detail", itemStr[6][solve222]);
                    break;
                case ST_MEGA_SCHEME:    //五魔配色
                    map.put("detail", itemStr[7][megaColorScheme]);
                    break;
                case ST_TIMER_FONT:    //计时器字体
                    map.put("detail", itemStr[8][timerFont]);
                    break;
                case ST_APP_LANGUAGE:    //应用语言
                    map.put("detail", dct.getResources().getStringArray(R.array.opt_app_language)[appLanguage]);
                    break;
                case ST_TIMER_SIZE:    //计时器大小
                    map.put("detail", String.valueOf(timerSize));
                    map.put("value", timerSize - 50);
                    break;
                case ST_SHOW_BACKGROUND_IMAGE:    //显示背景图
                    map.put("detail", !useBgcolor);
                    break;
                case ST_GESTURE_LEFT:    //左
                    map.put("detail", itemStr[15][swipeType[0]]);
                    break;
                case ST_GESTURE_RIGHT:    //右
                    map.put("detail", itemStr[15][swipeType[1]]);
                    break;
                case ST_GESTURE_UP:    //上
                    map.put("detail", itemStr[15][swipeType[2]]);
                    break;
                case ST_GESTURE_DOWN:    //下
                    map.put("detail", itemStr[15][swipeType[3]]);
                    break;
                case ST_SCREEN_ON:    //屏幕常亮
                    map.put("detail", screenOn);
                    break;
                case ST_VIBRATE:    //触感反馈
                    map.put("detail", itemStr[10][vibrateType]);
                    break;
                case ST_VIBRATE_TIME:    //持续时间
                    map.put("detail", itemStr[11][vibrateTime]);
                    break;
                case ST_SCREEN_ORIENTATION:    //屏幕方向
                    map.put("detail", itemStr[9][screenOri]);
                    break;
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public @NonNull RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == 0) {
            view = LayoutInflater.from(dct).inflate(R.layout.setting_list_header, parent, false);
            return new Header(view);
        } else if (viewType == 1) {
            view = LayoutInflater.from(dct).inflate(R.layout.setting_list_item, parent, false);
            final ViewHolder holder = new ViewHolder(view);
            holder.item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int pos = holder.getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    dct.setPref(getSettingId(cells.get(pos)));
                }
            });
            holder.checkAccessory.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int pos = holder.getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    dct.setPref(getSettingId(cells.get(pos)));
                }
            });
            holder.seekAccessory.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    int pos = holder.getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    Map<String, Object> map = cells.get(pos);
                    int settingId = getSettingId(map);
                    //Log.w("seek", pos+"/"+i);
                    switch (settingId) {
                        case ST_START_DELAY: //启动延时
                            //map.put("value", i);
                            String detail = String.format(Locale.getDefault(), "%.02fs", i/20f);
                            map.put("detail", detail);
                            dct.updatePref(settingId, detail);
                            //notifyItemChanged(pos, 1);
                            break;
                        case ST_SENSITIVITY:    //拍桌子停表
                            //map.put("detail", detail);
                            break;
                        case ST_SCR_FONT:    //打乱字体
                            //map.put("value", i);
                            detail = String.valueOf(i + 12);
                            map.put("detail", detail);
                            dct.updatePref(settingId, detail);
                            break;
                        case ST_SMART_CUBE_SIZE:    //虚拟魔方大小
                            detail = String.valueOf(i * 10 + 160);
                            map.put("detail", detail);
                            dct.updatePref(settingId, detail);
                            break;
                        case ST_IMAGE_SIZE:    //打乱状态
                            //map.put("value", i);
                            break;
                        case ST_TIMER_SIZE:    //计时器大小
                            //map.put("value", i);
                            detail = String.valueOf(i + 50);
                            map.put("detail", detail);
                            dct.updatePref(settingId, detail);
                            break;
                        case ST_OPACITY:    //不透明度
                            //map.put("value", i);
                            break;
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) { }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int pos = holder.getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    int progress = seekBar.getProgress();
                    Map<String, Object> map = cells.get(pos);
                    map.put("value", progress);
                    dct.updatePref(getSettingId(map), progress);
                }
            });
            return holder;
        } else {
            view = LayoutInflater.from(dct).inflate(R.layout.setting_list_footer, parent, false);
            final Footer footer = new Footer(view);
            footer.btnReset.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dct.resetAll();
                }
            });
            return footer;
        }
    }

//    @Override
//    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, List payloads) {
//        if (payloads.isEmpty()) {
//            Log.w("dct", "payload null");
//            onBindViewHolder(holder, position);
//        } else {
//            Log.w("dct", "payload "+payloads.get(0).toString());
//        }
//    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof Header) {
            Header hh = (Header) holder;
            hh.tvHead.setText(headers.get(position));
            if (position == 0) {
                if (hh.view == null) Log.e("dct", "view为Null0");
                else hh.view.setVisibility(View.GONE);
                //hh.divider.setVisibility(View.GONE);
            } else {
                if (hh.view == null) Log.e("dct", "view为Null1");
                else hh.view.setVisibility(View.VISIBLE);
                //hh.divider.setVisibility(View.VISIBLE);
            }
        } else if (holder instanceof ViewHolder) {
            ViewHolder vh = (ViewHolder) holder;
            Map<String, Object> map = cells.get(position);
            String title = (String) map.get("title");
            //vh.layoutCell.setOnClickListener(null);
            vh.textView.setText(title);
            int type = (int) map.get("type");
            if (type == 0) {
                vh.layoutCell.setBackgroundResource(R.drawable.item_background);
                vh.checkAccessory.setVisibility(View.GONE);
                vh.seekAccessory.setVisibility(View.GONE);
                String detail = (String) map.get("detail");
                if (TextUtils.isEmpty(detail)) vh.detailView.setVisibility(View.GONE);
                else {
                    vh.detailView.setVisibility(View.VISIBLE);
                    vh.detailView.setText(detail);
                }
            } else if (type == 1) {
                vh.layoutCell.setBackgroundResource(R.drawable.item_background);
                vh.checkAccessory.setVisibility(View.VISIBLE);
                vh.checkAccessory.setChecked((boolean) map.get("detail"));
                vh.seekAccessory.setVisibility(View.GONE);
                String description = (String) map.get("desc");
                if (TextUtils.isEmpty(description)) {
                    vh.detailView.setVisibility(View.GONE);
                } else {
                    vh.detailView.setVisibility(View.VISIBLE);
                    vh.detailView.setText(description);
                }
            } else {
                //Log.w("setting", "seek");
                vh.layoutCell.setBackgroundResource(R.color.colorBackground);
                vh.checkAccessory.setVisibility(View.GONE);
                vh.seekAccessory.setVisibility(View.VISIBLE);
                int max = (int) map.get("max");
                vh.seekAccessory.setMax(max);
                int progress = (int) map.get("value");
                //Log.w("seek", position+"/"+max+"/"+progress);
                vh.seekAccessory.setProgress(progress);
                String detail = (String) map.get("detail");
                if (detail == null || detail.length() == 0) vh.detailView.setVisibility(View.GONE);
                else {
                    vh.detailView.setVisibility(View.VISIBLE);
                    vh.detailView.setText(detail);
                }
            }
            if (headers.containsKey(position - 1)) {
                vh.divider.setVisibility(View.GONE);
            } else vh.divider.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return cells.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == cells.size()) return 2;
        if (headers.containsKey(position))
            return 0;
        return 1;
    }

    private int getSettingId(Map<String, Object> map) {
        Object settingId = map.get("settingId");
        return settingId instanceof Integer ? (int) settingId : -1;
    }

    public int getPositionBySettingId(int settingId) {
        for (int i = 0; i < cells.size(); i++) {
            if (getSettingId(cells.get(i)) == settingId) return i;
        }
        return -1;
    }

    public void setCheck(int settingId, boolean chk) {
        int pos = getPositionBySettingId(settingId);
        if (pos < 0) return;
        Map<String, Object> map = cells.get(pos);
        map.put("detail", chk);
        notifyItemChanged(pos);
    }

    public void setText(int settingId, String text) {
        int pos = getPositionBySettingId(settingId);
        if (pos < 0) return;
        Map<String, Object> map = cells.get(pos);
        map.put("detail", text);
        notifyItemChanged(pos);
    }
}
