package com.dctimer.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.dctimer.APP;
import com.dctimer.R;
import com.dctimer.activity.MainActivity;
import com.dctimer.activity.WebActivity;
import com.dctimer.util.StringUtils;
import com.dctimer.util.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import scrambler.Scrambler;

import static android.content.Context.CLIPBOARD_SERVICE;

public class ResultDialog extends DialogFragment {
    private EditText etComment;
    private TextView tvSolution;
    private EditText etSolution;
    private TableLayout tableSolveStats;
    private View scrollSolveStats;
    private ImageView imgArrow;
    private ImageView imgSolutionEdit;
    private int num;
    private String time;
    private String scramble;
    private String date;
    private int penalty;
    private String comment;
    private String solution;
    private String solveMeta;
    private int puzzle;
    private boolean expandSol;
    private boolean editingSolution;

    public void updateReconstruction(int session, int id, String moves, String meta) {
        Bundle args=getArguments();
        if (args==null || args.getInt("resultSession",-1)!=session
                || args.getInt("resultId",-1)!=id || editingSolution) return;
        solution=moves;
        solveMeta=meta;
        args.putString("solution",moves);
        args.putString("solveMeta",meta);
        if (tvSolution!=null) {
            tvSolution.setText(moves);
            etSolution.setText(moves);
            bindSolveStats(tableSolveStats,meta);
            setSolveStatsVisible(expandSol);
        }
    }

    public static ResultDialog newInstance(int num, String time, String scramble, String date, int penalty, String comment, String solution, String solveMeta, int puzzle) {
        ResultDialog dialog = new ResultDialog();
        Bundle bundle = new Bundle();
        bundle.putInt("num", num);
        bundle.putString("time", time);
        bundle.putString("scramble", scramble);
        bundle.putString("date", date);
        bundle.putInt("penalty", penalty);
        bundle.putString("comment", comment);
        bundle.putString("solution", solution);
        bundle.putString("solveMeta", solveMeta);
        bundle.putInt("puzzle", puzzle);
        dialog.setArguments(bundle);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        num = getArguments().getInt("num", 0);
        time = getArguments().getString("time");
        scramble = getArguments().getString("scramble");
        date = getArguments().getString("date");
        penalty = getArguments().getInt("penalty", 0);
        comment = getArguments().getString("comment");
        solution = getArguments().getString("solution");
        solveMeta = getArguments().getString("solveMeta");
        puzzle = getArguments().getInt("puzzle", 0);
        AlertDialog.Builder buidler = new AlertDialog.Builder(getActivity());
        final View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_time, null);
        TextView tvNum = view.findViewById(R.id.tv_num);
        TextView tvTime = view.findViewById(R.id.tv_time);
        TextView tvDate = view.findViewById(R.id.tv_date);
        TextView tvScramble = view.findViewById(R.id.tv_scramble);
        etComment = view.findViewById(R.id.et_comment);
        Button btnCopy = view.findViewById(R.id.btn_copy);
        //Button btnSolution = view.findViewById(R.id.bt_solution);
        LinearLayout llSolution = view.findViewById(R.id.ll_sol);
        tvSolution = view.findViewById(R.id.tv_solution);
        etSolution = view.findViewById(R.id.et_solution);
        imgArrow = view.findViewById(R.id.iv_arrow);
        imgSolutionEdit = view.findViewById(R.id.iv_solution_edit);
        tableSolveStats = view.findViewById(R.id.table_solve_stats);
        scrollSolveStats = view.findViewById(R.id.scroll_solve_stats);
        tvNum.setText("#" + (num + 1));
        tvTime.setText(time);
        tvScramble.setText(scramble);
        ImageView ivScramble = view.findViewById(R.id.img_scramble);
        Scrambler scrambler = new Scrambler(getActivity().getSharedPreferences("dctimer", Activity.MODE_PRIVATE));
        scrambler.parseScramble(puzzle, scramble);
        if (scrambler.getImageType() == 0) ivScramble.setVisibility(View.GONE);
        else {
            int dip240 = APP.getPixel(240);
            Bitmap bitmap = Bitmap.createBitmap(dip240, dip240 * 3 / 4, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bitmap);
            c.drawColor(0);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setStrokeWidth(APP.dpi);
            scrambler.drawScramble(dip240, p, c);
            ivScramble.setImageBitmap(bitmap);
        }
        tvDate.setText(date);
        if (penalty == 2) {
            RadioButton rb = view.findViewById(R.id.rb_dnf);
            rb.setChecked(true);
        } else if (penalty == 1) {
            RadioButton rb = view.findViewById(R.id.rb_plus2);
            rb.setChecked(true);
        } else {
            RadioButton rb = view.findViewById(R.id.rb_no_penalty);
            rb.setChecked(true);
        }
        if (!TextUtils.isEmpty(comment)) {
            etComment.setText(comment);
            etComment.setSelection(comment.length());
        }
        tvSolution.setText(TextUtils.isEmpty(solution) ? "" : solution);
        etSolution.setText(TextUtils.isEmpty(solution) ? "" : solution);
        tvSolution.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                android.content.ClipboardManager clip = (android.content.ClipboardManager) getActivity().getSystemService(CLIPBOARD_SERVICE);
                clip.setPrimaryClip(ClipData.newPlainText("text", tvSolution.getText().toString()));
                Toast.makeText(getActivity(), R.string.copy_success, Toast.LENGTH_SHORT).show();
            }
        });
        setSolutionEditMode(false);
        tvSolution.setVisibility(View.GONE);
        etSolution.setVisibility(View.GONE);
        bindSolveStats(tableSolveStats, solveMeta);
        llSolution.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                expandSol = !expandSol;
                if (expandSol) {
                    getActiveSolutionView().setVisibility(View.VISIBLE);
                    setSolveStatsVisible(true);
                    imgArrow.setImageResource(R.drawable.ic_arrow_up);
                } else {
                    tvSolution.setVisibility(View.GONE);
                    etSolution.setVisibility(View.GONE);
                    setSolveStatsVisible(false);
                    imgArrow.setImageResource(R.drawable.ic_arrow_down);
                }
            }
        });
        imgSolutionEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (editingSolution) {
                    saveSolutionIfChanged();
                    setSolutionEditMode(false);
                } else {
                    if (!expandSol) {
                        expandSol = true;
                        imgArrow.setImageResource(R.drawable.ic_arrow_up);
                    }
                    setSolutionEditMode(true);
                }
            }
        });
        /*btnSolution.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getContext(), WebActivity.class);
                String web = "https://alg.cubing.net/?alg=" + solution.trim().replace('\'', '-').replace(' ', '_')
                        + "&setup=" + scramble.trim().replace('\'', '-').replace(' ', '_');
                intent.putExtra("web", web);
                startActivity(intent);
            }
        });*/
        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).copyScramble(scramble);
                }
            }
        });
        buidler.setView(view).setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                boolean mod = false;
                RadioGroup rg = view.findViewById(R.id.rg_penalty);
                int id = rg.getCheckedRadioButtonId();
                switch (id) {
                    case R.id.rb_no_penalty:
                        mod = penalty != 0;
                        penalty = 0;
                        break;
                    case R.id.rb_plus2:
                        mod = penalty != 1;
                        penalty = 1;
                        break;
                    case R.id.rb_dnf:
                        mod = penalty != 2;
                        penalty = 2;
                        break;
                }
                String text = etComment.getText().toString();
                if (!text.equals(comment)) {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).updateResult(num, text);
                    }
                    //result.update(num, text);
                }
                saveSolutionIfChanged();
                Utils.hideKeyboard(etComment);
                Utils.hideKeyboard(etSolution);
                if (mod) {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).updateResult(num, penalty);
                    }
                }
            }
        }).setNeutralButton(R.string.delete_time, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialogInterface, int i) {
                Utils.hideKeyboard(etComment);
                if (getActivity() instanceof MainActivity) {
                    //Log.w("dct", "num "+num+", "+getActivity());
                    ((MainActivity) getActivity()).delete(num, true);
                }
            }
        }).setNegativeButton(R.string.btn_close, null);
        return buidler.create();
    }

    private void setSolutionEditMode(boolean editing) {
        editingSolution = editing;
        imgSolutionEdit.setImageResource(editing ? R.drawable.ic_check : R.drawable.ic_edit);
        tvSolution.setVisibility(expandSol && !editing ? View.VISIBLE : View.GONE);
        etSolution.setVisibility(expandSol && editing ? View.VISIBLE : View.GONE);
        setSolveStatsVisible(expandSol);
        if (editing) {
            etSolution.setText(solution == null ? "" : solution);
            etSolution.setSelection(etSolution.getText().length());
            Utils.showKeyboard(etSolution);
        } else {
            etSolution.clearFocus();
            Utils.hideKeyboard(etSolution);
        }
    }

    private void saveSolutionIfChanged() {
        String text = etSolution.getText().toString();
        String oldSolution = solution == null ? "" : solution;
        if (!text.equals(oldSolution) && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateResultMoves(num, text);
            solution = text;
            tvSolution.setText(solution);
            etSolution.setText(solution);
        }
    }

    private View getActiveSolutionView() {
        return editingSolution ? etSolution : tvSolution;
    }

    private void setSolveStatsVisible(boolean visible) {
        if (tableSolveStats != null && tableSolveStats.getChildCount() > 0) {
            scrollSolveStats.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void bindSolveStats(TableLayout table, String meta) {
        table.removeAllViews();
        List<PhaseStat> stats = parsePhaseStats(meta);
        if (stats.isEmpty()) {
            scrollSolveStats.setVisibility(View.GONE);
            return;
        }
        addStatsRow(table, getString(R.string.solve_stats_phase), getString(R.string.solve_stats_observation),
                getString(R.string.solve_stats_execution), getString(R.string.solve_stats_moves),
                getString(R.string.solve_stats_tps), true, false);
        for (PhaseStat stat : stats) {
            addStatsRow(table, stat.name, formatSplitTime(stat, true), formatSplitTime(stat, false), String.valueOf(stat.moves),
                    formatTps(stat.moves, stat.timeMs), false, false);
            if (stat.children != null && !stat.children.isEmpty()) {
                for (PhaseStat child : stat.children) {
                    addStatsRow(table, child.name, formatSplitTime(child, true), formatSplitTime(child, false), String.valueOf(child.moves),
                            formatTps(child.moves, child.timeMs), false, true);
                }
            }
        }
        scrollSolveStats.setVisibility(expandSol ? View.VISIBLE : View.GONE);
    }

    private List<PhaseStat> parsePhaseStats(String meta) {
        List<PhaseStat> result = new ArrayList<>();
        if (TextUtils.isEmpty(meta)) {
            return result;
        }
        try {
            JSONObject root = new JSONObject(meta);
            JSONArray phases = root.optJSONArray("phases");
            if (phases == null || phases.length() == 0) {
                return result;
            }
            for (int i = 0; i < phases.length(); i++) {
                JSONObject phase = phases.optJSONObject(i);
                if (phase == null) {
                    continue;
                }
                String name = phase.optString("name");
                int moves = phase.optInt("moveCount", 0);
                int timeMs = Math.max(0, phase.optInt("endMs", 0) - phase.optInt("startMs", 0));
                if (TextUtils.isEmpty(name) || moves <= 0) {
                    continue;
                }
                int observationMs = phase.optInt("observationMs", -1);
                if (observationMs > timeMs) observationMs = -1;
                addPhaseStat(result, name, moves, timeMs, observationMs);
            }
        } catch (JSONException e) {
            Log.w("dct", "parse solve_meta failed", e);
            result.clear();
        }
        return result;
    }

    private void addPhaseStat(List<PhaseStat> stats, String name, int moves, int timeMs, int observationMs) {
        if (name != null && name.startsWith("F2L")) {
            PhaseStat f2l = findPhaseStat(stats, "F2L");
            if (f2l == null) {
                f2l = new PhaseStat("F2L", 0, 0, 0);
                stats.add(f2l);
            }
            f2l.moves += moves;
            f2l.timeMs += timeMs;
            f2l.observationMs = f2l.observationMs < 0 || observationMs < 0 ? -1 : f2l.observationMs + observationMs;
            f2l.children.add(new PhaseStat(name, moves, timeMs, observationMs));
            return;
        }
        for (PhaseStat stat : stats) {
            if (stat.name.equals(name)) {
                stat.moves += moves;
                stat.timeMs += timeMs;
                stat.observationMs = stat.observationMs < 0 || observationMs < 0 ? -1 : stat.observationMs + observationMs;
                return;
            }
        }
        stats.add(new PhaseStat(name, moves, timeMs, observationMs));
    }

    private PhaseStat findPhaseStat(List<PhaseStat> stats, String name) {
        for (PhaseStat stat : stats) {
            if (stat.name.equals(name)) {
                return stat;
            }
        }
        return null;
    }

    private void addStatsRow(TableLayout table, String phase, String observation, String execution,
                             String moves, String tps, boolean header, boolean detail) {
        TableRow row = new TableRow(getActivity());
        row.setLayoutParams(new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));
        row.addView(createStatsCell(detail ? "  " + phase : phase, header, detail, 52));
        row.addView(createStatsCell(observation, header, detail, 60));
        row.addView(createStatsCell(execution, header, detail, 60));
        row.addView(createStatsCell(moves, header, detail, 40));
        row.addView(createStatsCell(tps, header, detail, 44));
        table.addView(row);
    }

    private TextView createStatsCell(String text, boolean header, boolean detail, int minWidthDp) {
        TextView cell = new TextView(getActivity());
        cell.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));
        cell.setMinWidth(APP.getPixel(minWidthDp));
        cell.setText(text);
        TypedArray colors = cell.getContext().obtainStyledAttributes(new int[] {
                header || detail ? android.R.attr.textColorSecondary : android.R.attr.textColorPrimary
        });
        ColorStateList textColors = colors.getColorStateList(0);
        if (textColors != null) {
            cell.setTextColor(textColors);
        }
        colors.recycle();
        cell.setTextSize(header || detail ? 12 : 13);
        cell.setSingleLine(true);
        // Measure complete values; the surrounding scroll view handles overflow.
        int horizontal = APP.getPixel(2);
        int vertical = APP.getPixel(3);
        cell.setPadding(horizontal, vertical, horizontal, vertical);
        return cell;
    }

    private String formatSplitTime(PhaseStat stat, boolean observation) {
        if (stat.observationMs < 0) return "—";
        return formatSeconds(observation ? stat.observationMs : stat.timeMs - stat.observationMs);
    }

    private String formatSeconds(int ms) {
        return String.format(Locale.US, APP.timerAccuracy == 1 ? "%.3f" : "%.2f", ms / 1000.0)
                .replace('.', APP.decimalMark == 0 ? '.' : ',');
    }

    private String formatTps(int moves, int ms) {
        if (ms <= 0) {
            return "-";
        }
        return String.format(Locale.US, "%.2f", moves * 1000f / ms);
    }

    private static class PhaseStat {
        final String name;
        int moves;
        int timeMs;
        int observationMs;
        final List<PhaseStat> children = new ArrayList<>();

        PhaseStat(String name, int moves, int timeMs, int observationMs) {
            this.name = name;
            this.moves = moves;
            this.timeMs = timeMs;
            this.observationMs = observationMs;
        }
    }
}
