package com.dctimer.util;

import static com.dctimer.APP.egIdx;
import static com.dctimer.APP.egolls;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import cs.min2phase.CubieCube;
import scrambler.Scrambler;

import com.dctimer.APP;
import com.dctimer.activity.MainActivity;
import com.dctimer.R;
import com.dctimer.model.Result;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;

import cs.min2phase.Util;

public class Utils {
    static String egOllAll = "PHUTLSA";
    private static final String SOLVED_FACELET = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB";
    public static final int[][] SMART_CUBE_ORIENTATION_FACES = createSmartCubeOrientationFaces();
    private static final Sticker[] STICKERS = createStickers();

    public static int grayScale(int color) {
        int red = (color >>> 16) & 0xff;
        int green = (color >>> 8) & 0xff;
        int blue = color & 0xff;
        int gray = red * 299 + green * 587 + blue * 114;
        return gray / 1000;
    }

    public static int getVersion(Context context) {
        try {
            PackageManager manager = context.getPackageManager();
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        } catch (Exception e) {
            return 43;
        }
    }

    public static String getVersionName(Context context) {
        try {
            PackageManager manager = context.getPackageManager();
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            return info.versionName;
        } catch (Exception e) {
            return "1.3";
        }
    }

    public static String getContent(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(20000);     //设置连接超时时间
            conn.setReadTimeout(30000);
            //conn.setRequestMethod("POST");     //设置以Post方式提交数据
            //conn.setDoOutput(true);
            //conn.setDoInput(true);
            //conn.setUseCaches(false);
            conn.connect();
            int res = conn.getResponseCode();
            if (res == HttpURLConnection.HTTP_OK) {
                InputStreamReader is = new InputStreamReader(conn.getInputStream(), "GB2312");
                BufferedReader reader = new BufferedReader(is);
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\t');
                }
                reader.close();
                return sb.toString();
            } else return "error code: " + res;
        } catch (Exception e) {
            return "error open url:" + urlStr;
        }
    }

    public static boolean isTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    public static int hslToRgb(int h, double s, double l) {
        double r, g, b;
        if (s == 0) r = g = b = l;
        else {
            double q, p, tr, tg, tb;
            if (l < 0.5) q = l * (1 + s);
            else q = l + s - l * s;
            p = 2 * l - q;
            double H = h / 360D;
            tr = H + 1 / 3D;
            tg = H;
            tb = H - 1 / 3D;
            r = toRGB(tr, q, p);
            g = toRGB(tg, q, p);
            b = toRGB(tb, q, p);
        }
        r = r * 255 + 0.5;
        g = g * 255 + 0.5;
        b = b * 255 + 0.5;
        return Color.rgb((int) r, (int) g, (int) b);
    }

    public static double[] rgbToHSL(int rgb) {
        double R = ((rgb >> 16) & 0xff) / 255D;
        double G = ((rgb >> 8) & 0xff) / 255D;
        double B = (rgb & 0xff) / 255D;
        double h = 0, s = 0, l;
        double max = Math.max(Math.max(R, G), B);
        double min = Math.min(Math.min(R, G), B);
        if (max == min) h = 0;
        else if (max == R && G >= B) h = 60 * ((G - B) / (max - min));
        else if (max == R && G < B) h = 60 * ((G - B) / (max - min)) + 360;
        else if (max == G) h = 60 * ((B - R) / (max - min)) + 120;
        else if (max == B) h = 60 * ((R - G) / (max - min)) + 240;
        l = (max + min) / 2;
        if (l == 0 || max == min) s = 0;
        else if (l > 0 && l <= 0.5)s = (max - min) / (max + min);
        else if (l > 0.5) s = (max - min) / (2 - (max + min));
        return new double[] {h, s, l};
    }

    private static double toRGB(double tc, double q, double p) {
        if (tc < 0) tc += 1;
        if (tc > 1) tc -= 1;
        if (tc < 1/6D)
            return p + (q - p) * 6 * tc;
        else if (tc < 0.5)
            return q;
        else if (tc < 2/3D)
            return p + (q - p) * 6 * (2/3D - tc);
        else return p;
    }

    public static Bitmap getBitmap(DisplayMetrics dm, String path) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, opts);
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null;
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        Log.w("dct", "wid"+width+","+height);
        Log.w("dct", "pic"+opts.outWidth+", "+opts.outHeight);
        float scaleWidth = (float) opts.outWidth / width;
        float scaleHeight = (float) opts.outHeight / height;
        float scale = Math.min(scaleWidth, scaleHeight);
        Log.w("dct", "sc"+scale);
        opts.inJustDecodeBounds = false;
        if (scale > 1) {
            opts.inSampleSize = (int) scale;
        } else opts.inSampleSize = 1;
        return BitmapFactory.decodeFile(path, opts);
    }

    public static Bitmap getBitmap(Context context, DisplayMetrics dm, String uriText, String path) throws IOException {
        if (!TextUtils.isEmpty(uriText)) {
            Bitmap bitmap = getBitmap(context, dm, Uri.parse(uriText));
            if (bitmap != null) return bitmap;
        }
        if (!TextUtils.isEmpty(path)) {
            Bitmap bitmap = getBitmap(dm, path);
            if (bitmap != null) return bitmap;
        }
        throw new FileNotFoundException("background image unavailable");
    }

    public static Bitmap getBitmap(Context context, DisplayMetrics dm, Uri uri) throws IOException {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new FileNotFoundException(uri.toString());
            BitmapFactory.decodeStream(in, null, opts);
        }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) throw new IOException("invalid image");
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        float scaleWidth = (float) opts.outWidth / width;
        float scaleHeight = (float) opts.outHeight / height;
        float scale = Math.min(scaleWidth, scaleHeight);
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = scale > 1 ? (int) scale : 1;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new FileNotFoundException(uri.toString());
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, opts);
            if (bitmap == null) throw new IOException("decode bitmap failed");
            return bitmap;
        }
    }

    public static Bitmap loadBackgroundSource(Context context, String uriText, String path) throws IOException {
        Uri uri = TextUtils.isEmpty(uriText) ? Uri.fromFile(new File(path)) : Uri.parse(uriText);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Image unavailable");
            BitmapFactory.decodeStream(input, null, options);
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Invalid image");
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 4096
                || (long) options.outWidth * options.outHeight / options.inSampleSize / options.inSampleSize > 8000000) {
            options.inSampleSize *= 2;
        }
        options.inJustDecodeBounds = false;
        Bitmap source;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            source = BitmapFactory.decodeStream(input, null, options);
        }
        if (source == null) throw new IOException("Invalid image");
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null) return source;
                android.media.ExifInterface exif = new android.media.ExifInterface(input);
                int orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
                Matrix matrix = new Matrix();
                switch (orientation) {
                    case 2: matrix.setScale(-1, 1); break;
                    case 3: matrix.setRotate(180); break;
                    case 4: matrix.setScale(1, -1); break;
                    case 5: matrix.setRotate(90); matrix.postScale(-1, 1); break;
                    case 6: matrix.setRotate(90); break;
                    case 7: matrix.setRotate(270); matrix.postScale(-1, 1); break;
                    case 8: matrix.setRotate(270); break;
                }
                if (!matrix.isIdentity()) {
                    Bitmap rotated = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
                    if (rotated != source) source.recycle();
                    source = rotated;
                }
            } catch (IOException ignored) { }
        }
        return source;
    }

    public static Bitmap renderBackground(Bitmap source, int width, int height, BackgroundCrop crop, int opacity) {
        float[] b = crop.bounds(source.getWidth(), source.getHeight(), width, height);
        Bitmap output = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Matrix matrix = new Matrix();
        matrix.setRectToRect(new RectF(b[0], b[1], b[2], b[3]), new RectF(0, 0, width, height), Matrix.ScaleToFit.FILL);
        canvas.drawBitmap(source, matrix, new Paint(Paint.FILTER_BITMAP_FLAG));
        canvas.drawColor(Color.argb(255 * (100 - opacity) / 100, 255, 255, 255));
        return output;
    }

    public static Bitmap getBackgroundBitmap(DisplayMetrics dm, Bitmap bitmap) {
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        float scaleWidth = (float) bitmap.getWidth() / width;
        float scaleHeight = (float) bitmap.getHeight() / height;
        float scale = Math.min(scaleWidth, scaleHeight);
        Matrix matrix = new Matrix();
        matrix.postScale(1 / scale, 1 / scale);
        return Bitmap.createBitmap(bitmap, (int)((bitmap.getWidth() - width * scale) / 2),
                (int) ((bitmap.getHeight() - height * scale) / 2), (int) (width * scale), (int) (height * scale), matrix, true);
    }

    public static Drawable getBackgroundDrawable(Context context, DisplayMetrics dm, Bitmap scaleBitmap, int opacity) {
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        Bitmap newBitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        Canvas canvas = new Canvas(newBitmap);
        canvas.drawColor(0);
        Paint paint = new Paint();
        canvas.drawBitmap(scaleBitmap, 0, 0, paint);
        paint.setColor(Color.WHITE);
        paint.setAlpha(255 * (100 - opacity) / 100);
        canvas.drawRect(0, 0, width, height, paint);
        return new BitmapDrawable(context.getResources(), newBitmap);
    }

    public static void addMaxQueue(int[] data, int x, int size) {
        if (size == 0) {
            data[0] = x;
        } else {
            int k = size;
            while (k > 0) {
                int parent = (k - 1) >>> 1;
                int e = data[parent];
                if (x >= e) break;
                data[k] = e;
                k = parent;
            }
            data[k] = x;
        }
    }

    static void pollMax(int[] data, int size) {
        int s = --size;
        int x = data[s];
        int half = s >>> 1;
        int k = 0;
        while (half > k) {
            int child = (k << 1) + 1;
            int c = data[child];
            int right = child + 1;
            if (right < s && c > data[right])
                c = data[child = right];
            if (x <= c) break;
            data[k] = c;
            k = child;
        }
        data[k] = x;
    }

    static void addMinQueue(int[] data, int x, int size) {
        if (size == 0) {
            data[0] = x;
        } else {
            int k = size;
            while (k > 0) {
                int parent = (k - 1) >>> 1;
                int e = data[parent];
                if (x <= e) break;
                data[k] = e;
                k = parent;
            }
            data[k] = x;
        }
    }

    static void pollMin(int[] data, int size) {
        int s = --size;
        int x = data[s];
        int half = s >>> 1;
        int k = 0;
        while (half > k) {
            int child = (k << 1) + 1;
            int c = data[child];
            int right = child + 1;
            if (right < s && c < data[right])
                c = data[child = right];
            if (x >= c) break;
            data[k] = c;
            k = child;
        }
        data[k] = x;
    }

    public static void setEgOll() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++)
            if (egIdx[i + 3])
                sb.append(egOllAll.charAt(i));
        egolls = sb.toString();
    }

    public static int getEgOll() {
        int egoll = 0;
        for (int i = 0; i < 7; i++)
            if (egIdx[i + 3])
                egoll |= (0x80 >> i);
        return egoll;
    }

    public static void addScramble(String text, List<String> list) {
        String[] arr = text.split("\n");
        for (int i = 0; i < arr.length; i++) {
            String scramble = arr[i].replaceFirst("^\\s*((\\(?\\d+\\))|(\\d+\\.))\\s*", "");
            if (!TextUtils.isEmpty(scramble)) list.add(scramble);
        }
    }

    public static void showKeyboard(final EditText et) {
        et.requestFocus();
        et.postDelayed(new Runnable() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager) et.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(et, 0);
            }
        }, 300);
    }

    public static void hideKeyboard(EditText et) {
        InputMethodManager imm = (InputMethodManager) et.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(et.getWindowToken(), 0);
    }

//    public static int getScrambleArrayId(int s) {
//        switch (s) {
//            case -1: return R.array.item_wca;
//            case 0: return R.array.item_222;
//            case 1: return R.array.item_333;
//            case 2: return R.array.item_444;
//            case 3: return R.array.item_555;
//            case 4:
//            case 5: return R.array.item_666;
//            case 6: return R.array.item_mega;
//            case 7: return R.array.item_pyr;
//            case 8: return R.array.item_sq1;
//            case 9: return R.array.item_clk;
//            case 15: return R.array.item_15p;
//            case 11: return R.array.item_mnl;
//            case 12: return R.array.item_cmt;
//            case 13: return R.array.item_gear;
//            case 14: return R.array.item_smc;
//            case 10: return R.array.item_skewb;
//            case 16: return R.array.item_other;
//            case 17: return R.array.item_333_sub;
//            case 18: return R.array.item_bandage;
//            case 19: return R.array.item_minx_sub;
//            default: return R.array.item_relay;
//        }
//    }

    public static void saveScramble(Context context, final ProgressDialog progress, final Handler handler, final Scrambler scramble, final String path, final int num) {
        File fPath = new File(path);
        if (fPath.getParentFile().exists()) {
            progress.setTitle(context.getString(R.string.action_export_scramble));
            progress.setMax(num);
            progress.show();
            new Thread() {
                public void run() {
                    try {
                        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path), "utf-8"));
                        for (int i = 0; i < num; i++) {
                            handler.sendEmptyMessage(100 + i);
                            scramble.generateScramble(APP.scrambleIdx, false);
                            String s = (i + 1) + ". " + scramble.getScramble() + "\r\n";
                            writer.write(s);
                        }
                        writer.close();
                        handler.sendEmptyMessage(7);
                    } catch (IOException e) {
                        handler.sendEmptyMessage(5);
                    }
                    progress.dismiss();
                }
            }.start();
        } else Toast.makeText(context, context.getString(R.string.path_not_exist), Toast.LENGTH_SHORT).show();
    }

    public static void saveScramble(Context context, final ProgressDialog progress, final Handler handler, final Scrambler scramble, final Uri uri, final int num) {
        progress.setTitle(context.getString(R.string.action_export_scramble));
        progress.setMax(num);
        progress.show();
        new Thread() {
            public void run() {
                try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt");
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
                    if (out == null) throw new FileNotFoundException(uri.toString());
                    for (int i = 0; i < num; i++) {
                        handler.sendEmptyMessage(100 + i);
                        scramble.generateScramble(APP.scrambleIdx, false);
                        writer.write((i + 1) + ". " + scramble.getScramble() + "\r\n");
                    }
                    handler.sendEmptyMessage(7);
                } catch (IOException e) {
                    handler.sendEmptyMessage(5);
                }
                progress.dismiss();
            }
        }.start();
    }

    public static void saveStat(Context context, String path, String fileName, String stat) {
        File fPath = new File(path);
        if (fPath.exists() || fPath.mkdirs()) {
            try {
                OutputStream out = new BufferedOutputStream(new FileOutputStream(path+fileName));
                byte [] bytes = stat.getBytes();
                out.write(bytes);
                out.close();
                Toast.makeText(context, context.getString(R.string.save_success), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(context, context.getString(R.string.save_fail), Toast.LENGTH_SHORT).show();
            }
        }
        else Toast.makeText(context, context.getString(R.string.path_not_exist), Toast.LENGTH_SHORT).show();
    }

    public static void saveStat(Context context, Uri uri, String stat) {
        try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new FileNotFoundException(uri.toString());
            out.write(stat.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(context, context.getString(R.string.save_success), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, context.getString(R.string.save_fail), Toast.LENGTH_SHORT).show();
        }
    }

    public static void exportDB(final String path, final Handler handler) {
        //File f = new File(path);
        //pd.setTitle(context.getString(R.string.export_db));
        //pd.show();
        new Thread() {
            public void run() {
                try {
                    InputStream is = new FileInputStream(APP.dataPath + "spdcube.db");
                    OutputStream os = new FileOutputStream(path);
                    byte[] buf = new byte[1024 * 8];
                    int bytesRead;
                    while ((bytesRead = is.read(buf)) != -1) {
                        os.write(buf, 0, bytesRead);
                    }
                    os.close();
                    handler.sendEmptyMessage(7);
                } catch (Exception e) {
                    e.printStackTrace();
                    handler.sendEmptyMessage(5);
                }
                //pd.dismiss();
            }
        }.start();
    }

    public static void exportDB(final Context context, final Uri uri, final Handler handler) {
        new Thread() {
            public void run() {
                try (InputStream is = new FileInputStream(APP.dataPath + "spdcube.db");
                     OutputStream os = context.getContentResolver().openOutputStream(uri, "wt")) {
                    if (os == null) throw new FileNotFoundException(uri.toString());
                    byte[] buf = new byte[1024 * 8];
                    int bytesRead;
                    while ((bytesRead = is.read(buf)) != -1) {
                        os.write(buf, 0, bytesRead);
                    }
                    handler.sendEmptyMessage(7);
                } catch (Exception e) {
                    e.printStackTrace();
                    handler.sendEmptyMessage(5);
                }
            }
        }.start();
    }

    public static void importDB(final String path, final Handler handler) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream is = new FileInputStream(path);
                    byte[] buf = new byte[1024 * 8];
                    int bytesRead = is.read(buf, 0, 16);
                    if (bytesRead < 16) handler.sendEmptyMessage(11);
                    else if (checkHeader(buf)) {
                        OutputStream os = new FileOutputStream(APP.dataPath + "spdcube.db");
                        os.write(buf, 0, 16);
                        while ((bytesRead = is.read(buf)) != -1) {
                            os.write(buf, 0, bytesRead);
                        }
                        os.close();
                        handler.sendEmptyMessage(12);
                    } else handler.sendEmptyMessage(11);
                } catch (Exception e) {
                    e.printStackTrace();
                    handler.sendEmptyMessage(11);
                }
            }
        }).start();
    }

    public static void importDB(final Context context, final Uri uri, final Handler handler) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                    if (is == null) throw new FileNotFoundException(uri.toString());
                    byte[] buf = new byte[1024 * 8];
                    int bytesRead = is.read(buf, 0, 16);
                    if (bytesRead < 16) handler.sendEmptyMessage(11);
                    else if (checkHeader(buf)) {
                        try (OutputStream os = new FileOutputStream(APP.dataPath + "spdcube.db")) {
                            os.write(buf, 0, 16);
                            while ((bytesRead = is.read(buf)) != -1) {
                                os.write(buf, 0, bytesRead);
                            }
                        }
                        handler.sendEmptyMessage(12);
                    } else handler.sendEmptyMessage(11);
                } catch (Exception e) {
                    e.printStackTrace();
                    handler.sendEmptyMessage(11);
                }
            }
        }).start();
    }

    public static String readText(Context context, Uri uri) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new FileNotFoundException(uri.toString());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                boolean firstLine = true;
                while ((line = reader.readLine()) != null) {
                    if (!firstLine) builder.append('\n');
                    builder.append(line);
                    firstLine = false;
                }
            }
        }
        return builder.toString();
    }

    private static boolean checkHeader(byte[] buf) {
        if (buf.length < 16) return false;
        byte[] head = "SQLite format 3".getBytes();
        for (int i = 0; i < 15; i++) {
            if (buf[i] != head[i]) return false;
        }
        if (buf[15] != 0) return false;
        return true;
    }

    public static void addSection(Map<Integer, String> header, List<Map<String, Object>> cells, String head, String[] title, int[] type, Object[] detail, int[] progress) {
        int[] settingIds = new int[title.length];
        int baseId = cells.size() + 1;
        for (int i = 0; i < settingIds.length; i++) {
            settingIds[i] = baseId + i;
        }
        addSection(header, cells, head, title, type, detail, progress, settingIds);
    }

    public static void addSection(Map<Integer, String> header, List<Map<String, Object>> cells, String head, String[] title, int[] type, Object[] detail, int[] progress, int[] settingIds) {
        header.put(cells.size(), head);
        cells.add(new HashMap<String, Object>());
        for (int i = 0; i < title.length; i++) {
            //Log.w("set", cells.size()+"/"+(progress[i] >> 16)+"/"+(progress[i] & 0xffff));
            Map<String, Object> map = new HashMap<>();
            map.put("settingId", settingIds[i]);
            map.put("title", title[i]);
            map.put("type", type[i]);
            map.put("detail", detail[i]);
            map.put("max", progress[i] >> 16);
            map.put("value", progress[i] & 0xffff);
            cells.add(map);
        }
    }

    public static String getShareContent(MainActivity context) {
        StringBuilder sb = new StringBuilder();
        Result result = APP.getInstance().getResult();
        int len = result.length();
        if (len == 0)
            sb.append(context.getString(R.string.share_c0));
        else {
            sb.append(String.format(context.getString(R.string.share_c1), len, context.getScrambleText(),
                    result.getTimeAt(result.getMinIdx(), false), StringUtils.timeToString(result.sessionMean())));
            if (len > APP.avg1len) sb.append(String.format(context.getString(R.string.share_c2), APP.avg1len, result.getBestAvg1()));
            if (len > APP.avg2len) sb.append(String.format(context.getString(R.string.share_c2), APP.avg2len, result.getBestAvg2()));
        }
        sb.append(context.getString(R.string.share_c3));
        return sb.toString();
    }

    public static void averageDetail(List<String[]> detailList, int n, int i, List<Integer> trim) {
        Result result = APP.getInstance().getResult();
        if (result == null) return;
        int num = 1;
        for (int j = i - n + 1; j <= i; j++) {
            String[] details = new String[4];
            if (trim.contains(j))
                details[0] = num + ". (" + result.getTimeAt(j, true) + ")";
            else details[0] = num + ". " + result.getTimeAt(j, true);
            details[1] = result.getString(j, 4);
            details[2] = result.getString(j, 5);
            details[3] = result.getString(j, 6);
            detailList.add(details);
            num++;
        }
    }

    public static void meanDetail(List<String[]> detailList, int n, int i) {
        Result result = APP.getInstance().getResult();
        if (result == null) return;
        int num = 1;
        for (int j = i - n + 1; j <= i; j++) {
            String[] details = new String[4];
            details[0] = num + ". " + result.getTimeAt(j, true);
            details[1] = result.getString(j, 4);
            details[2] = result.getString(j, 5);
            details[3] = result.getString(j, 6);
            detailList.add(details);
            num++;
        }
    }

    public static void sessionMeanDetail(List<String[]> detailList) {
        Result result = APP.getInstance().getResult();
        if (result == null) return;
        for (int i = 0; i < result.length(); i++) {
            String[] details = new String[4];
            details[0] = (i + 1) + ". " + result.getTimeAt(i, true);
            details[1] = result.getString(i, 4);
            details[2] = result.getString(i, 5);
            details[3] = result.getString(i, 6);
            detailList.add(details);
        }
    }

    public static void setCheck(int ch, CheckBox[] chks) {
        for (int i = 0; i < chks.length; i++) {
            chks[i].setChecked(((ch >> i) & 1) != 0);
        }
    }

    public static int getCheck(CheckBox[] chks) {
        int ch = 0;
        for (int i = 0; i < chks.length; i++) {
            if (chks[i].isChecked())
                ch |= (1 << i);
        }
        return ch;
    }

    public static String getCubeState(byte[] value) {
        //byte[] state = new byte[54];
        int f = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length - 2; i += 3) {
            int face = (value[i ^ 1] & 0xff) << 16 | (value[i + 1 ^ 1] & 0xff) << 8 | (value[i + 2 ^ 1] & 0xff);
            for (int j = 21; j >= 0; j -= 3) {
                int x = face >> j & 0x7;
                sb.append("URFDLB".charAt(x));
                //state[f++] = (byte) x;
                if (j == 12) {
                    x = i / 3;
                    sb.append("URFDLB".charAt(x));
                    //state[f++] = (byte) x;
                }
            }
        }
        //Log.w("dct", "face "+sb.toString());
        return sb.toString();
    }

    private static byte[][] cornerFacelet = {
            {26, 15, 29},
            {20, 8, 9},
            {18, 38, 6},
            {24, 27, 44},
            {51, 35, 17},
            {45, 11, 2},
            {47, 0, 36},
            {53, 42, 33}
    };

    private static byte[][] edgeFacelet = {
            {25, 28},
            {23, 12},
            {19, 7},
            {21, 41},
            {32, 16},
            {5, 10},
            {3, 37},
            {30, 43},
            {52, 34},
            {48, 14},
            {46, 1},
            {50, 39}
    };

    public static String parseGiikerState(byte[] value) {
        //byte[] valhex = toHexValue(value);
        int[] eo = new int[12];
        int count = 0;
        for (int i = 0; i < 3; i++) {
            for (int mask = 8; mask != 0; mask >>= 1) {
                eo[count++] = (value[i + 28] & mask) == 0 ? 0 : 1;
            }
        }
        int[] coMask = {-1, 1, -1, 1, 1, -1, 1, -1};
        int[] cp = new int[8], co = new int[8];
        for (int i = 0; i < 8; i++) {
            cp[i] = value[i] - 1;
            co[i] = (3 + value[i + 8] * coMask[i]) % 3;
        }
        int[] ep = new int[12];
        for (int i = 0; i < 12; i++) {
            ep[i] = value[i + 16] - 1;
        }
        CubieCube cc = new CubieCube(cp, co, ep, eo);
        return cc.toFaceCube(cornerFacelet, edgeFacelet);
    }

    public static Bitmap drawCubeState(String facelets) {
        if (facelets == null || facelets.length() < 54) return null;
        String center = new String(
                new char[] {
                        facelets.charAt(4),
                        facelets.charAt(13),
                        facelets.charAt(22),
                        facelets.charAt(31),
                        facelets.charAt(40),
                        facelets.charAt(49)
                }
        );
        byte[] f = new byte[54];
        for (int i = 0; i < 54; i++) {
            f[i] = (byte) center.indexOf(facelets.charAt(i));
            if (f[i] == -1) {
                f[i] = 6;
            }
        }
        int[] color = {Color.WHITE, Color.RED, 0xff009900, Color.YELLOW, 0xffff9900, Color.BLUE, Color.GRAY};
        Bitmap bitmap = Bitmap.createBitmap(APP.getPixel(245), APP.getPixel(183), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        c.drawColor(0);
        Paint p = new Paint();
        p.setAntiAlias(true);
        float a = APP.dpi * 19;
        int[] dx = {1, 2, 1, 1, 0, 3}, dy = {0, 1, 1, 2, 1, 1};
        for (int i = 0; i < 6; i++) {
            p.setStrokeWidth(1);
            float offx = APP.dpi * (1 + dx[i] * 5);
            float offy = APP.dpi * (1 + dy[i] * 5);
            for (int j = 0; j < 9; j++) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(color[f[i * 9 + j] & 0xff]);
                c.drawRect(a * (3 * dx[i] + j % 3) + offx, a * (3 * dy[i] + j / 3) + offy, a * (3 * dx[i] + j % 3 + 1) + offx, a * (3 * dy[i] + j / 3 + 1) + offy, p);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0xff000000);
                c.drawRect(a * (3 * dx[i] + j % 3) + offx, a * (3 * dy[i] + j / 3) + offy, a * (3 * dx[i] + j % 3 + 1) + offx, a * (3 * dy[i] + j / 3 + 1) + offy, p);
            }
            p.setStrokeWidth(APP.dpi);
            p.setColor(0xff000000);
            c.drawRect(a * 3 * dx[i] + offx, a * 3 * dy[i] + offy, a * 3 * (dx[i] + 1) + offx, a * 3 * (dy[i] + 1) + offy, p);
        }
        return bitmap;
    }

    public static Bitmap drawCubeState3D(String facelets) {
        if (facelets == null || facelets.length() < 27) return null;
        final float hsq3 = (float) (Math.sqrt(3) / 2);
        final float width = 20f;
        final float gap = 1f;
        final float[][] transforms = {
                {width * hsq3, -width * hsq3, (width * 3 + gap) * hsq3, width / 2f, width / 2f, 0f},
                {width * hsq3, 0f, (width * 3 + gap * 2) * hsq3, -width / 2f, width, width * 3 + gap * 1.5f},
                {width * hsq3, 0f, 0f, width / 2f, width, width * 1.5f + gap * 1.5f},
        };
        final float svgWidth = (6 * width + gap * 2) * hsq3;
        final float svgHeight = 6 * width + gap * 1.5f;
        final float scale = APP.dpi * 1.25f;

        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(svgWidth * scale)),
                Math.max(1, Math.round(svgHeight * scale)), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0);

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(0xff000000);
        strokePaint.setStrokeWidth(Math.max(1f, APP.dpi));
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        String pieces = facelets.substring(0, 27);
        for (int i = 0; i < 27; i++) {
            int x = i % 3;
            int y = (i / 3) % 3;
            float[][] polygon = {
                    {x, x + 1f, x + 1f, x},
                    {y, y, y + 1f, y + 1f}
            };
            float[] transformed = transformPolygon(polygon, transforms[i / 9], scale);
            Path path = new Path();
            path.moveTo(transformed[0], transformed[1]);
            path.lineTo(transformed[2], transformed[3]);
            path.lineTo(transformed[4], transformed[5]);
            path.lineTo(transformed[6], transformed[7]);
            path.close();
            fillPaint.setColor(face3Color(pieces.charAt(i)));
            canvas.drawPath(path, fillPaint);
            canvas.drawPath(path, strokePaint);
        }
        return bitmap;
    }

    private static float[] transformPolygon(float[][] polygon, float[] trans, float scale) {
        float[] result = new float[polygon[0].length * 2];
        for (int i = 0; i < polygon[0].length; i++) {
            result[i * 2] = (polygon[0][i] * trans[0] + polygon[1][i] * trans[1] + trans[2]) * scale;
            result[i * 2 + 1] = (polygon[0][i] * trans[3] + polygon[1][i] * trans[4] + trans[5]) * scale;
        }
        return result;
    }

    private static int face3Color(char face) {
        switch (face) {
            case 'U':
                return Color.WHITE;
            case 'R':
                return 0xfff00000;
            case 'F':
                return 0xff00aa00;
            case 'D':
                return Color.YELLOW;
            case 'L':
                return 0xffff8800;
            case 'B':
                return 0xff0033cc;
            default:
                return 0xff888888;
        }
    }

    public static List<String> getOrientationVariants(String facelets) {
        List<String> result = new ArrayList<>();
        if (facelets == null || facelets.length() < 54) {
            return result;
        }
        Set<String> seen = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(facelets);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!seen.add(current)) {
                continue;
            }
            queue.add(rotateFaceletX(current));
            queue.add(rotateFaceletY(current));
            queue.add(rotateFaceletZ(current));
        }
        result.addAll(seen);
        return result;
    }

    public static List<String> getAxisOrientationVariants(String facelets) {
        List<String> result = new ArrayList<>();
        if (facelets == null || facelets.length() < 54) {
            return result;
        }
        Set<Character> seenDownCenters = new LinkedHashSet<>();
        for (String variant : getOrientationVariants(facelets)) {
            char downCenter = variant.charAt(31);
            if (seenDownCenters.add(downCenter)) {
                result.add(variant);
            }
            if (result.size() == 6) {
                break;
            }
        }
        return result;
    }

    public static boolean isSameStateIgnoringRotation(String state, String target) {
        if (TextUtils.isEmpty(state) || TextUtils.isEmpty(target)) {
            return false;
        }
        if (state.equals(target)) {
            return true;
        }
        List<String> variants = getOrientationVariants(target);
        return variants.contains(state);
    }

    public static boolean isSolvedIgnoringRotation(String state) {
        return isSameStateIgnoringRotation(state, SOLVED_FACELET);
    }

    public static String orientFacelets(String facelets, int orientationIndex) {
        if (facelets == null || facelets.length() < 54) {
            return facelets;
        }
        int[] stickerMap = getSmartCubeStickerMap(orientationIndex);
        if (stickerMap == null) {
            return facelets;
        }
        char[] oriented = new char[54];
        for (int sourceIndex = 0; sourceIndex < stickerMap.length; sourceIndex++) {
            oriented[stickerMap[sourceIndex]] = facelets.charAt(sourceIndex);
        }
        return new String(oriented);
    }

    public static String unorientFacelets(String facelets, int orientationIndex) {
        if (facelets == null || facelets.length() < 54) {
            return facelets;
        }
        int[] stickerMap = getSmartCubeStickerMap(orientationIndex);
        if (stickerMap == null) {
            return facelets;
        }
        char[] unoriented = new char[54];
        for (int sourceIndex = 0; sourceIndex < stickerMap.length; sourceIndex++) {
            unoriented[sourceIndex] = facelets.charAt(stickerMap[sourceIndex]);
        }
        return new String(unoriented);
    }

    public static int orientSmartCubeMove(int move, int orientationIndex) {
        if (move < 0 || move >= 18) {
            return move;
        }
        if (getSmartCubeFaceMap(orientationIndex) == null) {
            return move;
        }
        return mapSmartCubeMove(move, orientationIndex, true);
    }

    public static int unorientSmartCubeMove(int move, int orientationIndex) {
        if (move < 0 || move >= 18) {
            return move;
        }
        if (getSmartCubeFaceMap(orientationIndex) == null) {
            return move;
        }
        return mapSmartCubeMove(move, orientationIndex, false);
    }

    public static String applySmartCubeMove(String facelets, int move) {
        if (facelets == null || facelets.length() < 54 || move < 0 || move >= 18) {
            return facelets;
        }
        CubieCube cube = new CubieCube();
        if (Util.toCubieCube(facelets, cube) != 0) {
            return facelets;
        }
        String moved = Util.toFaceCube(cube.move(move));
        char[] centers = new char[] {
                facelets.charAt(4), facelets.charAt(13), facelets.charAt(22),
                facelets.charAt(31), facelets.charAt(40), facelets.charAt(49)
        };
        char[] result = moved.toCharArray();
        String faces = "URFDLB";
        for (int i = 0; i < result.length; i++) {
            int faceIndex = faces.indexOf(result[i]);
            if (faceIndex >= 0) {
                result[i] = centers[faceIndex];
            }
        }
        return new String(result);
    }

    private static int mapSmartCubeMove(int move, int orientationIndex, boolean orient) {
        String base = SOLVED_FACELET;
        String moved = applySmartCubeMove(base, move);
        String transformed = orient ? orientFacelets(moved, orientationIndex) : unorientFacelets(moved, orientationIndex);
        String transformedBase = orient ? orientFacelets(base, orientationIndex) : unorientFacelets(base, orientationIndex);
        for (int candidate = 0; candidate < 18; candidate++) {
            if (Objects.equals(applySmartCubeMove(transformedBase, candidate), transformed)) {
                return candidate;
            }
        }
        return move;
    }

    public static int[] getSmartCubeOrientationPair(int orientationIndex) {
        if (orientationIndex < 0 || orientationIndex >= SMART_CUBE_ORIENTATION_FACES.length) {
            orientationIndex = 0;
        }
        return SMART_CUBE_ORIENTATION_FACES[orientationIndex];
    }

    private static int[] getSmartCubeFaceMap(int orientationIndex) {
        int[] pair = getSmartCubeOrientationPair(orientationIndex);
        int top = pair[0];
        int front = pair[1];
        if (top == front || getOppositeFace(top) == front) {
            return null;
        }
        int[] topVector = faceVector(top);
        int[] frontVector = faceVector(front);
        int[] rightVector = cross(topVector, frontVector);
        int[] faceMap = new int[6];
        for (int face = 0; face < 6; face++) {
            int[] vector = faceVector(face);
            if (dot(vector, topVector) == 1) faceMap[face] = 0;
            else if (dot(vector, rightVector) == 1) faceMap[face] = 1;
            else if (dot(vector, frontVector) == 1) faceMap[face] = 2;
            else if (dot(vector, topVector) == -1) faceMap[face] = 3;
            else if (dot(vector, rightVector) == -1) faceMap[face] = 4;
            else faceMap[face] = 5;
        }
        return faceMap;
    }

    private static int[] getSmartCubeStickerMap(int orientationIndex) {
        int[] pair = getSmartCubeOrientationPair(orientationIndex);
        int top = pair[0];
        int front = pair[1];
        if (top == front || getOppositeFace(top) == front) {
            return null;
        }
        int[] topVector = faceVector(top);
        int[] frontVector = faceVector(front);
        int[] rightVector = cross(topVector, frontVector);
        int[] stickerMap = new int[54];
        boolean[] used = new boolean[54];
        for (Sticker sticker : STICKERS) {
            int x = dot(sticker.x, sticker.y, sticker.z, rightVector[0], rightVector[1], rightVector[2]);
            int y = dot(sticker.x, sticker.y, sticker.z, topVector[0], topVector[1], topVector[2]);
            int z = dot(sticker.x, sticker.y, sticker.z, frontVector[0], frontVector[1], frontVector[2]);
            int nx = dot(sticker.nx, sticker.ny, sticker.nz, rightVector[0], rightVector[1], rightVector[2]);
            int ny = dot(sticker.nx, sticker.ny, sticker.nz, topVector[0], topVector[1], topVector[2]);
            int nz = dot(sticker.nx, sticker.ny, sticker.nz, frontVector[0], frontVector[1], frontVector[2]);
            int face = getFaceIndex(nx, ny, nz);
            int row = getRow(face, x, y, z);
            int col = getCol(face, x, y, z);
            int targetIndex = face * 9 + row * 3 + col;
            if (targetIndex < 0 || targetIndex >= 54 || used[targetIndex]) {
                return null;
            }
            used[targetIndex] = true;
            stickerMap[sticker.sourceIndex] = targetIndex;
        }
        return stickerMap;
    }

    private static int[][] createSmartCubeOrientationFaces() {
        List<int[]> pairs = new ArrayList<>();
        pairs.add(new int[] {0, 2});
        for (int top = 0; top < 6; top++) {
            for (int front = 0; front < 6; front++) {
                if (top == front || getOppositeFace(top) == front || (top == 0 && front == 2)) {
                    continue;
                }
                pairs.add(new int[] {top, front});
            }
        }
        return pairs.toArray(new int[pairs.size()][]);
    }

    private static int getOppositeFace(int face) {
        switch (face) {
            case 0: return 3;
            case 1: return 4;
            case 2: return 5;
            case 3: return 0;
            case 4: return 1;
            default: return 2;
        }
    }

    private static int[] faceVector(int face) {
        switch (face) {
            case 0: return new int[] {0, 1, 0};
            case 1: return new int[] {1, 0, 0};
            case 2: return new int[] {0, 0, 1};
            case 3: return new int[] {0, -1, 0};
            case 4: return new int[] {-1, 0, 0};
            default: return new int[] {0, 0, -1};
        }
    }

    private static int[] faceRightVector(int face) {
        switch (face) {
            case 0: return new int[] {1, 0, 0};
            case 1: return new int[] {0, 0, -1};
            case 2: return new int[] {1, 0, 0};
            case 3: return new int[] {1, 0, 0};
            case 4: return new int[] {0, 0, 1};
            default: return new int[] {-1, 0, 0};
        }
    }

    private static int[] faceUpVector(int face) {
        switch (face) {
            case 0: return new int[] {0, 0, -1};
            case 1:
            case 2:
            case 4:
            case 5:
                return new int[] {0, 1, 0};
            default: return new int[] {0, 0, 1};
        }
    }

    private static int dot(int[] a, int[] b) {
        return dot(a[0], a[1], a[2], b[0], b[1], b[2]);
    }

    private static int dot(int x1, int y1, int z1, int x2, int y2, int z2) {
        return x1 * x2 + y1 * y2 + z1 * z2;
    }

    private static int[] cross(int[] a, int[] b) {
        return new int[] {
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    private static String rotateFaceletX(String facelets) {
        return rotateFacelets(facelets, 0);
    }

    private static String rotateFaceletY(String facelets) {
        return rotateFacelets(facelets, 1);
    }

    private static String rotateFaceletZ(String facelets) {
        return rotateFacelets(facelets, 2);
    }

    private static String rotateFacelets(String facelets, int axis) {
        char[] rotated = new char[54];
        for (int i = 0; i < STICKERS.length; i++) {
            Sticker sticker = STICKERS[i];
            Sticker transformed = sticker.rotate(axis);
            rotated[transformed.index()] = facelets.charAt(i);
        }
        return new String(rotated);
    }

    private static Sticker[] createStickers() {
        Sticker[] stickers = new Sticker[54];
        int index = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                stickers[index++] = new Sticker(index - 1, -1 + col, 1, -1 + row, 0, 1, 0);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                stickers[index++] = new Sticker(index - 1, 1, 1 - row, 1 - col, 1, 0, 0);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                stickers[index++] = new Sticker(index - 1, -1 + col, 1 - row, 1, 0, 0, 1);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                stickers[index++] = new Sticker(index - 1, -1 + col, -1, 1 - row, 0, -1, 0);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                stickers[index++] = new Sticker(index - 1, -1, 1 - row, -1 + col, -1, 0, 0);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                stickers[index++] = new Sticker(index - 1, 1 - col, 1 - row, -1, 0, 0, -1);
            }
        }
        return stickers;
    }

    private static int getFaceIndex(int nx, int ny, int nz) {
        if (ny == 1) return 0;
        if (nx == 1) return 1;
        if (nz == 1) return 2;
        if (ny == -1) return 3;
        if (nx == -1) return 4;
        return 5;
    }

    private static int getRow(int face, int x, int y, int z) {
        switch (face) {
            case 0:
                return z + 1;
            case 1:
            case 2:
            case 4:
            case 5:
                return 1 - y;
            case 3:
                return 1 - z;
            default:
                return 0;
        }
    }

    private static int getCol(int face, int x, int y, int z) {
        switch (face) {
            case 0:
            case 2:
            case 3:
                return x + 1;
            case 1:
                return 1 - z;
            case 4:
                return z + 1;
            case 5:
                return 1 - x;
            default:
                return 0;
        }
    }

    private static class Sticker {
        final int sourceIndex;
        final int x;
        final int y;
        final int z;
        final int nx;
        final int ny;
        final int nz;

        Sticker(int sourceIndex, int x, int y, int z, int nx, int ny, int nz) {
            this.sourceIndex = sourceIndex;
            this.x = x;
            this.y = y;
            this.z = z;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }

        Sticker rotate(int axis) {
            switch (axis) {
                case 0:
                    return new Sticker(sourceIndex, x, -z, y, nx, -nz, ny);
                case 1:
                    return new Sticker(sourceIndex, z, y, -x, nz, ny, -nx);
                default:
                    return new Sticker(sourceIndex, -y, x, z, -ny, nx, nz);
            }
        }

        int index() {
            int face = getFaceIndex(nx, ny, nz);
            int row = getRow(face, x, y, z);
            int col = getCol(face, x, y, z);
            return face * 9 + row * 3 + col;
        }
    }
}
