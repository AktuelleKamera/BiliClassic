package tv.biliclassic;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import tv.biliclassic.util.GlobalImageCache;
import tv.biliclassic.util.MsgUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class NewAnimeFragment extends Fragment {

    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler delayHandler = new Handler();

    private Map<String, Boolean> loadingMap = new HashMap<String, Boolean>();

    private View headerContainer;
    private ScrollView contentContainer;
    private LinearLayout gridContainer;

    private int screenWidth = 0;
    private int screenHeight = 0;
    private boolean dataLoaded = false;
    private boolean isDestroyed = false;

    private File cacheDir;

    private static final int MAX_RETRY = 1;
    private int retryCount = 0;

    private boolean isLowMemoryDevice() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        return maxMemory < 16384;
    }

    private void initExecutor() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        int threadCount = isLowMemoryDevice() ? 1 : 2;
        executor = new ThreadPoolExecutor(threadCount, threadCount, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_new_anime, container, false);

        isDestroyed = false;
        initExecutor();

        if (getActivity() != null) {
            cacheDir = new File(getActivity().getCacheDir(), "anime_cache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
        }

        headerContainer = view.findViewById(R.id.header_container);
        contentContainer = (ScrollView) view.findViewById(R.id.content_container);
        gridContainer = (LinearLayout) view.findViewById(R.id.grid_container);

        if (headerContainer != null) {
            headerContainer.setVisibility(View.GONE);
        }
        contentContainer.setVisibility(View.GONE);

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        delayHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isDestroyed) {
                    getScreenSizeAndLoad();
                }
            }
        }, 500);
    }

    private void getScreenSizeAndLoad() {
        if (isDestroyed) return;

        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;

        if (isLandscapeDevice() && screenWidth < screenHeight) {
            int temp = screenWidth;
            screenWidth = screenHeight;
            screenHeight = temp;
        }

        if (screenWidth == 0) {
            screenWidth = 800;
        }
        if (screenHeight == 0) {
            screenHeight = 480;
        }

        if (!dataLoaded && !isDestroyed) {
            dataLoaded = true;
            loadAnimeData();
        }
    }

    private boolean isLandscapeDevice() {
        boolean landscapeEnabled = SharedPreferencesUtil.getBoolean(
                BaseActivity.KEY_LANDSCAPE_ENABLED, true);
        if (!landscapeEnabled) {
            return false;
        }

        String model = android.os.Build.MODEL;
        if (model == null) {
            return false;
        }

        String[] landscapeModels = {"HTC ChaCha", "Galaxy Y Pro", "Galaxy Pro", "A5100"};
        for (String m : landscapeModels) {
            if (model.contains(m)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNetworkAvailable() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    getActivity().getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public void onDestroyView() {
        isDestroyed = true;
        if (gridContainer != null) {
            for (int i = 0; i < gridContainer.getChildCount(); i++) {
                View item = gridContainer.getChildAt(i);
                if (item != null) {
                    ImageView iv = (ImageView) item.findViewById(R.id.anime_cover);
                    if (iv != null) {
                        iv.setImageResource(R.drawable.bili_default_image_tv_with_bg);
                        iv.setImageBitmap(null);
                    }
                }
            }
        }
        loadingMap.clear();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            executor = null;
        }
        delayHandler.removeCallbacksAndMessages(null);
        loadingMap.clear();
    }

    private void showLoading() {
        if (headerContainer != null) {
            headerContainer.setVisibility(View.VISIBLE);
        }
        if (contentContainer != null) {
            contentContainer.setVisibility(View.GONE);
        }
    }

    private void hideAllLoading() {
        if (headerContainer != null) {
            headerContainer.setVisibility(View.GONE);
        }
        if (contentContainer != null) {
            contentContainer.setVisibility(View.VISIBLE);
        }
    }

    private void showNoNetworkButCache() {
        // 有缓存时，不显示错误，静默使用缓存
        // 但可以显示一个轻提示，在 header 中显示"网络不可用，显示缓存"
        if (headerContainer != null) {
            headerContainer.setVisibility(View.VISIBLE);
            TextView textView = (TextView) headerContainer.findViewById(R.id.header_text);
            if (textView != null) {
                textView.setText("网络不可用，显示缓存");
            }
        }
        if (contentContainer != null) {
            contentContainer.setVisibility(View.VISIBLE);
        }
    }

    private void showNoNetwork() {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                hideAllLoading();
                if (gridContainer != null && gridContainer.getChildCount() == 0) {
                    showErrorText(getString(R.string.emoticon__no_network));
                } else {
                    // 有内容，只显示 header 提示
                    if (headerContainer != null) {
                        headerContainer.setVisibility(View.VISIBLE);
                        TextView textView = (TextView) headerContainer.findViewById(R.id.header_text);
                        if (textView != null) {
                            textView.setText("网络不可用，显示缓存");
                        }
                    }
                }
            }
        });
    }

    private void showLoadError() {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                hideAllLoading();
                showErrorText(getString(R.string.emoticon__failed_need_retry));
            }
        });
    }

    private void showErrorText(String msg) {
        if (getActivity() == null) return;
        if (gridContainer != null) {
            gridContainer.removeAllViews();
            TextView tv = new TextView(getActivity());
            tv.setText(msg);
            tv.setTextSize(16);
            tv.setTextColor(0xFF999999);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setPadding(0, dpToPx(100), 0, 0);
            gridContainer.addView(tv);
        }
    }

    // 缓存方法

    private List<AnimeItem> loadLocalCache() {
        if (cacheDir == null) return null;
        try {
            File jsonFile = new File(cacheDir, "data.json");
            if (!jsonFile.exists()) {
                return null;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(jsonFile), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            try {
                JSONObject root = new JSONObject(sb.toString());
                String version = root.optString("version");
                if (version == null || version.length() == 0) {
                    jsonFile.delete();
                    return null;
                }
            } catch (Exception e) {
                jsonFile.delete();
                return null;
            }

            long lastModified = jsonFile.lastModified();
            long now = System.currentTimeMillis();
            if (now - lastModified > 60 * 60 * 1000) {
                jsonFile.delete();
                return null;
            }

            String jsonStr = sb.toString();
            List<AnimeItem> items = parseAnimeJson(jsonStr);

            File coverDir = new File(cacheDir, "covers");
            if (!coverDir.exists() || !coverDir.isDirectory()) {
                jsonFile.delete();
                return null;
            }

            boolean hasCover = false;
            for (AnimeItem item : items) {
                if (item.coverUrl != null && item.coverUrl.length() > 0) {
                    String fileName = getCacheFileName(item.coverUrl);
                    File coverFile = new File(coverDir, fileName);
                    if (coverFile.exists()) {
                        hasCover = true;
                        break;
                    }
                }
            }

            if (!hasCover) {
                jsonFile.delete();
                return null;
            }

            return items;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void saveToCache(String jsonStr) {
        if (cacheDir == null || jsonStr == null) return;
        try {
            File jsonFile = new File(cacheDir, "data.json");
            FileOutputStream fos = new FileOutputStream(jsonFile);
            fos.write(jsonStr.getBytes("UTF-8"));
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveCoverToCache(String url, Bitmap bitmap) {
        if (cacheDir == null || bitmap == null || bitmap.isRecycled()) return;
        try {
            String fileName = getCacheFileName(url);
            File coverDir = new File(cacheDir, "covers");
            if (!coverDir.exists()) {
                coverDir.mkdirs();
            }
            File coverFile = new File(coverDir, fileName);
            FileOutputStream fos = new FileOutputStream(coverFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, fos);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getCacheFileName(String url) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(url.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString() + ".jpg";
        } catch (Exception e) {
            return String.valueOf(url.hashCode()) + ".jpg";
        }
    }

    private Bitmap getBitmapFromCache(String url) {
        if (cacheDir == null) return null;
        try {
            File coverDir = new File(cacheDir, "covers");
            String fileName = getCacheFileName(url);
            File cacheFile = new File(coverDir, fileName);
            if (cacheFile.exists()) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                return BitmapFactory.decodeFile(cacheFile.getAbsolutePath(), options);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // 网络加载

    private void loadAnimeData() {
        retryCount = 0;
        doLoadAnimeData();
    }

    private void doLoadAnimeData() {
        if (isDestroyed) return;

        showLoading();

        // 先尝试加载缓存
        List<AnimeItem> cachedItems = loadLocalCache();
        if (cachedItems != null && cachedItems.size() > 0) {
            // 有缓存，先显示
            hideAllLoading();
            displayAnimeList(cachedItems);

            // 检查网络，如果无网络则显示提示
            if (!isNetworkAvailable()) {
                showNoNetworkButCache();
                return;
            }

            // 有网络则后台更新
            new Thread(new Runnable() {
                @Override
                public void run() {
                    fetchAnimeDataFromNetwork();
                }
            }).start();
            return;
        }

        // 无缓存，检查网络
        if (!isNetworkAvailable()) {
            showNoNetwork();
            return;
        }

        // 无缓存有网络，请求数据
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (isDestroyed) return;
                fetchAnimeDataFromNetwork();
            }
        }).start();
    }

    private void fetchAnimeDataFromNetwork() {
        try {
            String url = SettingsActivity.getNewAnimeApiUrl();

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();

            InputStream is = conn.getInputStream();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            final String jsonStr = baos.toString("UTF-8");
            is.close();
            conn.disconnect();

            try {
                JSONObject root = new JSONObject(jsonStr);
                String version = root.optString("version");
                if (version == null || version.length() == 0) {
                    clearCache();
                }
            } catch (Exception e) {
                clearCache();
            }

            final List<AnimeItem> items = parseAnimeJson(jsonStr);

            if (getActivity() != null && !isDestroyed) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isDestroyed) return;
                        if (items == null || items.size() == 0) {
                            showLoadError();
                            return;
                        }
                        saveToCache(jsonStr);
                        hideAllLoading();
                        displayAnimeList(items);
                        retryCount = 0;
                    }
                });
            }
        } catch (final Exception e) {
            e.printStackTrace();
            if (getActivity() == null || isDestroyed) return;
            if (!isNetworkAvailable()) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 如果有缓存，显示缓存提示
                        List<AnimeItem> cached = loadLocalCache();
                        if (cached != null && cached.size() > 0) {
                            showNoNetworkButCache();
                        } else {
                            showNoNetwork();
                        }
                    }
                });
            } else if (retryCount < MAX_RETRY) {
                retryCount++;
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        doLoadAnimeData();
                    }
                });
            } else {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showLoadError();
                    }
                });
            }
        }
    }

    private void clearCache() {
        if (cacheDir == null) return;
        try {
            File jsonFile = new File(cacheDir, "data.json");
            if (jsonFile.exists()) {
                jsonFile.delete();
            }
            File coverDir = new File(cacheDir, "covers");
            if (coverDir.exists() && coverDir.isDirectory()) {
                File[] files = coverDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f != null && f.exists()) {
                            f.delete();
                        }
                    }
                }
            }
            loadingMap.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<AnimeItem> parseAnimeJson(String jsonStr) {
        List<AnimeItem> items = new ArrayList<AnimeItem>();
        try {
            JSONObject json = new JSONObject(jsonStr);
            JSONArray array = json.getJSONArray("anime_list");
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String title = obj.optString("title");
                String image = obj.optString("image");
                boolean isBig = obj.optBoolean("is_big");
                String aidStr = obj.optString("aid");
                String epidStr = obj.optString("epid");
                if (title != null && title.length() > 0 && image != null && image.length() > 0) {
                    AnimeItem item = new AnimeItem(title, image, isBig);
                    if (aidStr != null && aidStr.length() > 0) {
                        try { item.aid = Long.parseLong(aidStr); } catch (Exception e) {}
                    }
                    if (epidStr != null && epidStr.length() > 0) {
                        try { item.epid = Long.parseLong(epidStr); } catch (Exception e) {}
                    }
                    items.add(item);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return items;
    }

    // 显示方法

    private void displayAnimeList(List<AnimeItem> items) {
        if (isDestroyed || items == null || items.size() == 0 || gridContainer == null || getActivity() == null) {
            return;
        }

        gridContainer.removeAllViews();

        int index = 0;
        int largeCardIndex = 0;
        while (index < items.size()) {
            if (index % 3 == 0) {
                boolean isFirstLarge = (largeCardIndex == 0);
                View largeView = createLargeCard(items.get(index), isFirstLarge);
                gridContainer.addView(largeView);
                largeCardIndex++;
                index++;
            } else {
                LinearLayout row = new LinearLayout(getActivity());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

                View leftView = createSmallCard(items.get(index));
                row.addView(leftView);
                index++;

                View divider = new View(getActivity());
                divider.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(4), LinearLayout.LayoutParams.MATCH_PARENT));
                row.addView(divider);

                if (index < items.size()) {
                    View rightView = createSmallCard(items.get(index));
                    row.addView(rightView);
                    index++;
                } else {
                    int itemWidth = screenWidth / 2;
                    View emptyView = new View(getActivity());
                    emptyView.setLayoutParams(new LinearLayout.LayoutParams(itemWidth, 1));
                    row.addView(emptyView);
                }

                gridContainer.addView(row);
            }
        }
    }

    private View createLargeCard(final AnimeItem item, boolean isFirst) {
        if (isDestroyed || getActivity() == null) {
            return new View(getActivity());
        }

        int cardHeight = screenWidth / 2;
        int maxHeight = (int) (screenHeight * 0.45f);
        if (cardHeight > maxHeight) {
            cardHeight = maxHeight;
        }
        if (cardHeight < 80) {
            cardHeight = 80;
        }

        View card = LayoutInflater.from(getActivity()).inflate(R.layout.item_anime_large, null);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                cardHeight);
        if (isFirst) {
            params.setMargins(0, 0, 0, dpToPx(2));
        } else {
            params.setMargins(0, dpToPx(2), 0, dpToPx(2));
        }
        card.setLayoutParams(params);

        TextView tvTitle = (TextView) card.findViewById(R.id.anime_title);
        ImageView ivCover = (ImageView) card.findViewById(R.id.anime_cover);

        tvTitle.setText(item.title);
        ivCover.setImageResource(R.drawable.bili_default_image_tv_with_bg);
        ivCover.setTag(item.coverUrl);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAnimeDetail(item);
            }
        });

        if (item.coverUrl != null && item.coverUrl.length() > 0 && !isDestroyed) {
            loadImageLazy(ivCover, item.coverUrl, true);
        }

        return card;
    }

    private View createSmallCard(final AnimeItem item) {
        if (isDestroyed || getActivity() == null) {
            return new View(getActivity());
        }

        int dividerWidth = dpToPx(4);
        int itemWidth = (screenWidth - dividerWidth) / 2;
        int cardHeight = itemWidth / 2;
        int maxHeight = (int) (screenHeight * 0.4f);
        if (cardHeight > maxHeight) {
            cardHeight = maxHeight;
        }
        if (cardHeight < 60) {
            cardHeight = 60;
        }

        View card = LayoutInflater.from(getActivity()).inflate(R.layout.item_anime_small, null);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                itemWidth,
                cardHeight);
        params.setMargins(0, dpToPx(2), 0, dpToPx(2));
        card.setLayoutParams(params);

        TextView tvTitle = (TextView) card.findViewById(R.id.anime_title);
        ImageView ivCover = (ImageView) card.findViewById(R.id.anime_cover);

        tvTitle.setText(item.title);
        ivCover.setImageResource(R.drawable.bili_default_image_tv_with_bg);
        ivCover.setTag(item.coverUrl);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAnimeDetail(item);
            }
        });

        if (item.coverUrl != null && item.coverUrl.length() > 0 && !isDestroyed) {
            loadImageLazy(ivCover, item.coverUrl, false);
        }

        return card;
    }

    private void openAnimeDetail(AnimeItem item) {
        if (getActivity() == null) return;

        Intent intent;

        if (item.epid > 0) {
            intent = new Intent(getActivity(), VideoDetailActivity.class);
            intent.putExtra("from_bangumi", true);
            intent.putExtra("bangumi_title", item.title);
            if (item.aid > 0) {
                intent.putExtra("aid", item.aid);
            } else {
                intent.putExtra("epid", item.epid);
            }
        } else if (item.aid > 0) {
            intent = new Intent(getActivity(), VideoDetailActivity.class);
            intent.putExtra("aid", item.aid);
        } else {
            Toast.makeText(getActivity(), "无法获取视频信息", Toast.LENGTH_SHORT).show();
            return;
        }

        startActivity(intent);
    }

    private void loadImageLazy(final ImageView imageView, final String urlStr, final boolean isLarge) {
        if (isDestroyed || imageView == null || getActivity() == null) return;

        Bitmap cached = GlobalImageCache.getInstance().get(urlStr);
        if (cached != null && !cached.isRecycled()) {
            imageView.setImageBitmap(cached);
            return;
        }

        Bitmap localCached = getBitmapFromCache(urlStr);
        if (localCached != null && !localCached.isRecycled()) {
            GlobalImageCache.getInstance().put(urlStr, localCached);
            imageView.setImageBitmap(localCached);
            return;
        }

        Boolean isLoading = loadingMap.get(urlStr);
        if (isLoading != null && isLoading) {
            return;
        }

        imageView.setImageResource(R.drawable.bili_default_image_tv_with_bg);
        loadingMap.put(urlStr, true);

        if (executor == null || executor.isShutdown()) {
            loadingMap.remove(urlStr);
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (isDestroyed) return;

                final Bitmap bitmap = downloadImage(urlStr, isLarge);
                loadingMap.remove(urlStr);

                if (bitmap != null && !bitmap.isRecycled()) {
                    saveCoverToCache(urlStr, bitmap);
                    GlobalImageCache.getInstance().put(urlStr, bitmap);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed) return;
                            Object tag = imageView.getTag();
                            if (tag != null && tag.equals(urlStr)) {
                                if (bitmap != null && !bitmap.isRecycled()) {
                                    imageView.setImageBitmap(bitmap);
                                } else {
                                    imageView.setImageResource(R.drawable.bili_default_image_tv_with_bg);
                                }
                            }
                        }
                    });
                }
            }
        });
    }

    private Bitmap downloadImage(String urlStr, boolean isLarge) {
        HttpURLConnection conn = null;
        try {
            String finalUrl = urlStr;
            if (finalUrl.startsWith("https://")) {
                finalUrl = "http://" + finalUrl.substring(8);
            }

            URL url = new URL(finalUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return null;
            }

            InputStream is = conn.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            is.close();
            byte[] imageData = baos.toByteArray();

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(imageData, 0, imageData.length, options);

            int targetWidth = isLarge ? 320 : 160;
            int targetHeight = isLarge ? 160 : 80;
            int scale = 1;

            if (options.outWidth > 0 && options.outHeight > 0) {
                if (options.outWidth > targetWidth || options.outHeight > targetHeight) {
                    int widthRatio = options.outWidth / targetWidth;
                    int heightRatio = options.outHeight / targetHeight;
                    scale = Math.max(widthRatio, heightRatio);
                    if (scale < 1) scale = 1;
                    if (scale > 8) scale = 8;
                }
            }

            options = new BitmapFactory.Options();
            options.inSampleSize = scale;
            options.inPreferredConfig = Bitmap.Config.RGB_565;

            return BitmapFactory.decodeByteArray(imageData, 0, imageData.length, options);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception e) {}
            }
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private static class AnimeItem {
        String title;
        String coverUrl;
        boolean isLarge;
        long aid;
        long epid;

        AnimeItem(String title, String coverUrl, boolean isLarge) {
            this.title = title;
            this.coverUrl = coverUrl;
            this.isLarge = isLarge;
        }
    }
}