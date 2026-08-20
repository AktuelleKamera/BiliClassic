package tv.biliclassic;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AbsListView;

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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import tv.biliclassic.util.GlobalImageCache;
import tv.biliclassic.util.MsgUtil;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class NewAnimeFragment extends Fragment {

    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler delayHandler = new Handler();

    private Map<String, Boolean> loadingMap = new HashMap<String, Boolean>();

    private View headerContainer;
    private ScrollView contentContainer;
    private LinearLayout gridContainer;
    private ListView animeList;
    private TextView emptyView;
    private AnimeListAdapter animeListAdapter;

    private int screenWidth = 0;
    private int screenHeight = 0;
    private boolean dataLoaded = false;
    private boolean isDestroyed = false;
    private List<AnimeItem> animeItems;

    private File cacheDir;

    private static final int MAX_RETRY = 1;
    private int retryCount = 0;

    // Android 2.x 上 setImageResource 每次可能重新解码资源图，缓存默认 Drawable 实例复用
    private static android.graphics.drawable.Drawable sDefaultCoverDrawable;

    // 滚动中暂缓应用新图，避免每张图到达都触发整屏软件重绘（仅主线程访问）
    private volatile boolean mListScrolling = false;
    private final ArrayList<Runnable> pendingBitmapSets = new ArrayList<Runnable>();

    // 分帧构建状态
    private int buildIndex = 0;
    private int buildLargeCardIndex = 0;

    private boolean isLowMemoryDevice() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        return maxMemory < 24576;
    }

    private void initExecutor() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        int threadCount = tv.biliclassic.util.SdkHelper.getImageLoadThreads();
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
        emptyView = (TextView) view.findViewById(R.id.empty_view);

        if (headerContainer != null) {
            headerContainer.setVisibility(View.GONE);
        }
        contentContainer.setVisibility(View.GONE);

        if (!isTablet()) {
            // 手机：用虚拟化 ListView，避免整页构建冻结
            animeList = (ListView) view.findViewById(R.id.anime_list);
            if (animeList != null) {
                animeList.setDivider(new android.graphics.drawable.ColorDrawable(0x00000000));
                animeList.setDividerHeight(dpToPx(4));
                animeList.setVerticalFadingEdgeEnabled(false);
                animeList.setHorizontalFadingEdgeEnabled(false);
                if (tv.biliclassic.util.SdkHelper.getSdkInt() >= 9) {
                    tv.biliclassic.util.SdkHelper.setOverScrollNever(animeList);
                }
                animeList.setCacheColorHint(0x00000000);
                animeList.setClipToPadding(false);
                animeList.setFocusable(true);
                animeList.setFocusableInTouchMode(true);
                // 绘制缓存（仅 32MB+ 堆设备）：滑页转场命中缓存，避免每帧重绘全部行
                if (tv.biliclassic.util.SdkHelper.isHighMemoryDevice()) {
                    animeList.setDrawingCacheEnabled(true);
                    animeList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_AUTO);
                }
                animeList.setVisibility(View.VISIBLE);
                animeListAdapter = new AnimeListAdapter(getActivity(), this);
                animeList.setAdapter(animeListAdapter);
                animeList.setOnScrollListener(new AbsListView.OnScrollListener() {
                    @Override
                    public void onScroll(AbsListView view, int firstVisibleItem,
                                         int visibleItemCount, int totalItemCount) {
                    }

                    @Override
                    public void onScrollStateChanged(AbsListView view, int scrollState) {
                        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                            mListScrolling = false;
                            flushPendingBitmapSets();
                        } else {
                            mListScrolling = true;
                        }
                    }
                });
            }
        }

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

    private boolean isTablet() {
        return getResources().getBoolean(R.bool.is_tablet);
    }

    private boolean isOrientationLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
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
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (getActivity() == null || isDestroyed) return;
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
        if (isLandscapeDevice() && screenWidth < screenHeight) {
            int temp = screenWidth;
            screenWidth = screenHeight;
            screenHeight = temp;
        }
        if (animeItems != null && animeItems.size() > 0) {
            displayAnimeList(animeItems);
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
        if (animeList != null) {
            animeList.setVisibility(View.GONE);
        }
    }

    private void hideAllLoading() {
        if (headerContainer != null) {
            headerContainer.setVisibility(View.GONE);
        }
        if (isTablet()) {
            if (contentContainer != null) {
                contentContainer.setVisibility(View.VISIBLE);
            }
        } else {
            if (animeList != null) {
                animeList.setVisibility(View.VISIBLE);
            }
        }
    }

    private void showNoNetworkButCache() {
        // 有缓存时，不显示错误，静默使用缓存
        // 但可以显示一个轻提示，在 header 中显示"网络不可用，显示缓存"
        if (headerContainer != null) {
            headerContainer.setVisibility(View.VISIBLE);
            TextView textView = (TextView) headerContainer.findViewById(R.id.header_text);
            if (textView != null) {
                textView.setText(getString(R.string.newanimefragment_settext_7f51));
            }
        }
        if (isTablet()) {
            if (contentContainer != null) {
                contentContainer.setVisibility(View.VISIBLE);
            }
        } else {
            if (animeList != null) {
                animeList.setVisibility(View.VISIBLE);
            }
        }
    }

    private void showNoNetwork() {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                hideAllLoading();
                if (!hasContent()) {
                    showErrorText(getString(R.string.emoticon__no_network));
                } else {
                    // 有内容，只显示 header 提示
                    if (headerContainer != null) {
                        headerContainer.setVisibility(View.VISIBLE);
                        TextView textView = (TextView) headerContainer.findViewById(R.id.header_text);
                        if (textView != null) {
                            textView.setText(getString(R.string.newanimefragment_settext_7f51));
                        }
                    }
                }
            }
        });
    }

    private boolean hasContent() {
        if (isTablet()) {
            return gridContainer != null && gridContainer.getChildCount() > 0;
        }
        return animeListAdapter != null && animeListAdapter.getCount() > 0;
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
        if (isTablet()) {
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
            return;
        }
        if (animeList != null) {
            animeList.setVisibility(View.GONE);
        }
        if (emptyView != null) {
            emptyView.setText(msg);
            emptyView.setVisibility(View.VISIBLE);
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

    private Bitmap getBitmapFromCache(String url, boolean isLarge) {
        if (cacheDir == null) return null;
        try {
            File coverDir = new File(cacheDir, "covers");
            String fileName = getCacheFileName(url);
            File cacheFile = new File(coverDir, fileName);
            if (cacheFile.exists()) {
                // 与 API 图片尺寸一致：大卡 480x240，小卡 240x120，避免降采样糊图
                return GlobalImageCache.decodeFileSafely(cacheFile,
                        isLarge ? 480 : 240, isLarge ? 240 : 120, 2);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // 网络加载

    private void preloadCoverCache(List<AnimeItem> items) {
        if (items == null || isDestroyed) return;
        int loaded = 0;
        for (int i = 0; i < items.size() && loaded < 8; i++) {
            AnimeItem item = items.get(i);
            if (item == null || item.coverUrl == null || item.coverUrl.length() == 0) continue;
            try {
                if (GlobalImageCache.getInstance().get(item.coverUrl) != null) continue;
                Bitmap bmp = getBitmapFromCache(item.coverUrl, item.isLarge);
                if (bmp != null && !bmp.isRecycled()) {
                    GlobalImageCache.getInstance().put(item.coverUrl, bmp);
                    loaded++;
                }
            } catch (Throwable t) {
            }
        }
    }

    private void loadAnimeData() {
        retryCount = 0;
        doLoadAnimeData();
    }

    private void doLoadAnimeData() {
        if (isDestroyed) return;

        showLoading();

        // 缓存读取 + JSON 解析挪到后台线程（之前在主线程同步执行，N900 上造成 2s+ 冻结）
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (isDestroyed) return;
                final List<AnimeItem> cachedItems = loadLocalCache();
                // 预解码可见封面进内存缓存：fill 时 1:1 命中，
                // 避免默认小图(220x165)放大绘制 + 逐张异步回填造成的重渲染冻结
                preloadCoverCache(cachedItems);
                if (getActivity() == null || isDestroyed) return;
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isDestroyed) return;

                        // 先尝试加载缓存
                        if (cachedItems != null && cachedItems.size() > 0) {
                            hideAllLoading();
                            displayAnimeList(cachedItems);

                            if (isNetworkAvailable()) {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (isDestroyed) return;
                                        fetchAnimeDataFromNetwork();
                                    }
                                }).start();
                            }
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
                });
            }
        }).start();
    }

    private void fetchAnimeDataFromNetwork() {
        try {
            String url = SettingsActivity.getNewAnimeApiUrl();

            // 走 NetWorkUtil：带 1.6 兼容（重定向/响应读取）
            final String jsonStr = NetWorkUtil.get(url);
            if (jsonStr == null || jsonStr.length() == 0) {
                if (getActivity() == null || isDestroyed) return;
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showLoadError();
                    }
                });
                return;
            }

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

    private void setDefaultCover(ImageView iv) {
        if (sDefaultCoverDrawable == null) {
            try {
                sDefaultCoverDrawable = getResources().getDrawable(R.drawable.bili_default_image_tv_with_bg);
            } catch (Throwable t) {
                sDefaultCoverDrawable = null;
            }
        }
        if (sDefaultCoverDrawable != null && iv.getDrawable() != sDefaultCoverDrawable) {
            iv.setImageDrawable(sDefaultCoverDrawable);
        }
    }

    private void displayAnimeList(List<AnimeItem> items) {
        if (isDestroyed || items == null || items.size() == 0 || getActivity() == null) {
            return;
        }

        animeItems = items;

        if (isTablet()) {
            if (gridContainer == null) return;
            gridContainer.removeAllViews();
            buildIndex = 0;
            buildLargeCardIndex = 0;

            // 分帧构建：每帧只建一行，避免整页一次性 inflate+布局卡死（3606ms 单帧冻结的来源）
            final Runnable step = new Runnable() {
                @Override
                public void run() {
                    if (isDestroyed) return;
                    if (!buildNextAnimeRow()) return;
                    delayHandler.postDelayed(this, 40);
                }
            };
            step.run();
        } else {
            if (emptyView != null) {
                emptyView.setVisibility(View.GONE);
            }
            if (animeListAdapter != null) {
                // 延迟一帧再填充：列表刚可见那帧先画出，填充+首帧绘制不挤在同一帧（重渲染冻结来源）
                final List<AnimeItem> finalItems = items;
                animeList.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isDestroyed) return;
                        animeListAdapter.setData(finalItems, screenWidth, screenHeight, false);
                    }
                });
            }
        }
    }

    /** 构建一行，返回 true 表示还有更多行 */
    private boolean buildNextAnimeRow() {
        List<AnimeItem> items = animeItems;
        if (items == null || getActivity() == null || isDestroyed) return false;
        boolean tabletMode = isTablet();
        int index = buildIndex;
        if (index >= items.size()) return false;

        if (index % 3 == 0) {
            if (tabletMode && index + 2 < items.size()) {
                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                int displayWidth = dm.widthPixels;
                boolean isLandscape = displayWidth > dm.heightPixels;
                // 横屏大卡占 2/3，竖屏大卡占 3/5
                int largeWidth = isLandscape ? displayWidth * 2 / 3 : displayWidth * 3 / 5;
                int smallWidth = displayWidth - largeWidth - dpToPx(4);
                int smallHeight = smallWidth / 3;
                int rowHeight = smallHeight * 2 + dpToPx(4);

                // 偶数行：大卡左、小卡右竖排；奇数行：小卡左竖排、大卡右
                boolean isEven = (buildLargeCardIndex % 2 == 0);

                // 两张小卡竖排容器
                LinearLayout smallColumn = new LinearLayout(getActivity());
                smallColumn.setOrientation(LinearLayout.VERTICAL);
                smallColumn.setLayoutParams(new LinearLayout.LayoutParams(smallWidth, rowHeight));

                View sm1 = createSmallCard(items.get(index + 1), smallWidth, false);
                sm1.setLayoutParams(new LinearLayout.LayoutParams(smallWidth, smallHeight));
                smallColumn.addView(sm1);

                View gap = new View(getActivity());
                gap.setLayoutParams(new LinearLayout.LayoutParams(smallWidth, dpToPx(4)));
                smallColumn.addView(gap);

                View sm2 = createSmallCard(items.get(index + 2), smallWidth, false);
                sm2.setLayoutParams(new LinearLayout.LayoutParams(smallWidth, smallHeight));
                smallColumn.addView(sm2);

                // 大卡
                View largeView = createLargeCardForRow(items.get(index), buildLargeCardIndex == 0, largeWidth);
                largeView.setLayoutParams(new LinearLayout.LayoutParams(largeWidth, rowHeight));

                // 组装行
                LinearLayout row = new LinearLayout(getActivity());
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, rowHeight);
                if (buildLargeCardIndex == 0) {
                    rowLp.setMargins(0, 0, 0, dpToPx(2));
                } else {
                    rowLp.setMargins(0, dpToPx(2), 0, dpToPx(2));
                }
                row.setLayoutParams(rowLp);

                if (isEven) {
                    row.addView(largeView);
                    addRowDivider(row);
                    row.addView(smallColumn);
                } else {
                    row.addView(smallColumn);
                    addRowDivider(row);
                    row.addView(largeView);
                }
                gridContainer.addView(row);
                buildIndex = index + 3;
            } else {
                boolean isFirstLarge = (buildLargeCardIndex == 0);
                View largeView = createLargeCard(items.get(index), isFirstLarge);
                gridContainer.addView(largeView);
                buildIndex = index + 1;
            }
            buildLargeCardIndex++;
        } else {
            LinearLayout row = new LinearLayout(getActivity());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            View leftView = createSmallCard(items.get(index));
            row.addView(leftView);
            int next = index + 1;

            View divider = new View(getActivity());
            divider.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(4), LinearLayout.LayoutParams.MATCH_PARENT));
            row.addView(divider);

            if (next < items.size()) {
                View rightView = createSmallCard(items.get(next));
                row.addView(rightView);
                next++;
            } else {
                int itemWidth = screenWidth / 2;
                View emptyView = new View(getActivity());
                emptyView.setLayoutParams(new LinearLayout.LayoutParams(itemWidth, 1));
                row.addView(emptyView);
            }

            gridContainer.addView(row);
            buildIndex = next;
        }
        return buildIndex < items.size();
    }

    private View createLargeCard(final AnimeItem item, boolean isFirst) {
        return createLargeCard(item, isFirst, screenWidth, true);
    }

    private View createLargeCard(final AnimeItem item, boolean isFirst, int width, boolean addMargins) {
        if (isDestroyed || getActivity() == null) {
            return new View(getActivity());
        }

        // Android 2.x 外部堆小，inflate 卡片可能 OOM，失败时返回空白占位不崩溃
        View card = null;
        for (int attempt = 0; attempt < 2 && card == null; attempt++) {
            try {
                card = LayoutInflater.from(getActivity()).inflate(R.layout.item_anime_large, null);
            } catch (OutOfMemoryError e) {
                tv.biliclassic.util.GlobalImageCache.getInstance().releaseMemory();
                System.gc();
            } catch (android.view.InflateException e) {
                tv.biliclassic.util.GlobalImageCache.getInstance().releaseMemory();
                System.gc();
            } catch (Throwable e) {
                tv.biliclassic.util.GlobalImageCache.getInstance().releaseMemory();
                System.gc();
            }
        }
        if (card == null) {
            return new View(getActivity());
        }

        int cardHeight = width / 2;
        int maxHeight = (int) (screenHeight * 0.45f);
        if (cardHeight > maxHeight) {
            cardHeight = maxHeight;
        }
        if (cardHeight < 80) {
            cardHeight = 80;
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, cardHeight);
        if (addMargins) {
            if (isFirst) {
                params.setMargins(0, 0, 0, dpToPx(2));
            } else {
                params.setMargins(0, dpToPx(2), 0, dpToPx(2));
            }
        }
        card.setLayoutParams(params);

        TextView tvTitle = (TextView) card.findViewById(R.id.anime_title);
        ImageView ivCover = (ImageView) card.findViewById(R.id.anime_cover);

        tvTitle.setText(item.title);
        try {
            setDefaultCover(ivCover);
        } catch (OutOfMemoryError e) {
            tv.biliclassic.util.GlobalImageCache.getInstance().releaseMemory();
            System.gc();
        } catch (Throwable e) {
        }
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

    /**
     * 横屏平板专用：大卡与小卡混合行，不单独设置边距（由行统一控制）
     */
    private View createLargeCardForRow(final AnimeItem item, boolean isFirst, int width) {
        return createLargeCard(item, isFirst, width, false);
    }

    private void addRowDivider(LinearLayout row) {
        View divider = new View(getActivity());
        divider.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(4), LinearLayout.LayoutParams.MATCH_PARENT));
        row.addView(divider);
    }

    private View createSmallCard(final AnimeItem item) {
        int dividerWidth = dpToPx(4);
        int itemWidth = (screenWidth - dividerWidth) / 2;
        return createSmallCard(item, itemWidth, true);
    }

    private View createSmallCard(final AnimeItem item, int width, boolean addMargins) {
        if (isDestroyed || getActivity() == null) {
            return new View(getActivity());
        }

        // Android 2.x 外部堆小，inflate 卡片可能 OOM，失败时返回空白占位不崩溃
        View card = null;
        for (int attempt = 0; attempt < 2 && card == null; attempt++) {
            try {
                card = LayoutInflater.from(getActivity()).inflate(R.layout.item_anime_small, null);
            } catch (OutOfMemoryError e) {
                tv.biliclassic.util.GlobalImageCache.getInstance().releaseMemory();
                System.gc();
            } catch (android.view.InflateException e) {
                tv.biliclassic.util.GlobalImageCache.getInstance().releaseMemory();
                System.gc();
            } catch (Throwable e) {
                tv.biliclassic.util.GlobalImageCache.getInstance().releaseMemory();
                System.gc();
            }
        }
        if (card == null) {
            return new View(getActivity());
        }

        int cardHeight = width / 2;
        int maxHeight = (int) (screenHeight * 0.4f);
        if (cardHeight > maxHeight) {
            cardHeight = maxHeight;
        }
        if (cardHeight < 60) {
            cardHeight = 60;
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, cardHeight);
        if (addMargins) {
            params.setMargins(0, dpToPx(2), 0, dpToPx(2));
        }
        card.setLayoutParams(params);

        TextView tvTitle = (TextView) card.findViewById(R.id.anime_title);
        ImageView ivCover = (ImageView) card.findViewById(R.id.anime_cover);

        tvTitle.setText(item.title);
        try {
            setDefaultCover(ivCover);
        } catch (OutOfMemoryError e) {
            tv.biliclassic.util.GlobalImageCache.getInstance().releaseMemory();
            System.gc();
        } catch (Throwable e) {
        }
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
            Toast.makeText(getActivity(), getActivity().getString(R.string.newanimefragment_toast_65e0), Toast.LENGTH_SHORT).show();
            return;
        }

        startActivity(intent);
    }

    private void loadImageLazy(final ImageView imageView, final String urlStr, final boolean isLarge) {
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false)) return;
        if (isDestroyed || imageView == null || getActivity() == null) return;

        Bitmap cached = GlobalImageCache.getInstance().get(urlStr);
        if (cached != null && !cached.isRecycled()) {
            imageView.setImageBitmap(cached);
            return;
        }

        Boolean isLoading = loadingMap.get(urlStr);
        if (isLoading != null && isLoading) {
            return;
        }

        setDefaultCover(imageView);
        loadingMap.put(urlStr, true);

        if (executor == null || executor.isShutdown()) {
            loadingMap.remove(urlStr);
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (isDestroyed) return;

                // 磁盘缓存解码放在后台线程，避免主线程文件解码卡顿（1.6 无 JIT 时更明显）
                Bitmap bitmap = getBitmapFromCache(urlStr, isLarge);
                boolean fromCache = (bitmap != null && !bitmap.isRecycled());
                if (!fromCache) {
                    bitmap = downloadImage(urlStr, isLarge);
                    if (bitmap != null && !bitmap.isRecycled()) {
                        saveCoverToCache(urlStr, bitmap);
                    }
                }
                loadingMap.remove(urlStr);

                if (bitmap != null && !bitmap.isRecycled()) {
                    GlobalImageCache.getInstance().put(urlStr, bitmap);
                    final Bitmap fbmp = bitmap;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed) return;
                            if (mListScrolling) {
                                // 滚动中不立即应用：每张图到达都会触发整屏软件重绘
                                pendingBitmapSets.add(this);
                                return;
                            }
                            applyAnimeBitmap(imageView, urlStr, fbmp);
                        }
                    });
                }
            }
        });
    }

    private void applyAnimeBitmap(ImageView imageView, String urlStr, Bitmap fbmp) {
        if (isDestroyed) return;
        Object tag = imageView.getTag();
        if (tag != null && tag.equals(urlStr)) {
            if (fbmp != null && !fbmp.isRecycled()) {
                imageView.setImageBitmap(fbmp);
            } else {
                setDefaultCover(imageView);
            }
        }
    }

    private void flushPendingBitmapSets() {
        if (pendingBitmapSets.isEmpty()) return;
        final ArrayList<Runnable> pending = new ArrayList<Runnable>(pendingBitmapSets);
        pendingBitmapSets.clear();
        // 分批应用（每帧最多 2 张），避免停下瞬间一次性 setImageBitmap 全部封面造成整帧卡顿
        final int[] idx = {0};
        final Runnable drain = new Runnable() {
            @Override
            public void run() {
                if (executor == null || executor.isShutdown()) {
                    return;
                }
                int applied = 0;
                while (idx[0] < pending.size() && applied < 2) {
                    try {
                        pending.get(idx[0]).run();
                    } catch (Throwable t) {
                    }
                    idx[0]++;
                    applied++;
                }
                if (idx[0] < pending.size()) {
                    mainHandler.postDelayed(this, 16);
                }
            }
        };
        drain.run();
    }

    private Bitmap downloadImage(String urlStr, boolean isLarge) {
        HttpURLConnection conn = null;
        java.io.File tempFile = null;
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
            conn.setRequestProperty("Accept-Encoding", "identity");
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return null;
            }

            tempFile = new java.io.File(getActivity().getCacheDir(), "anime_" + finalUrl.hashCode() + ".tmp");
            InputStream is = conn.getInputStream();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            is.close();
            fos.close();

            if (!tempFile.exists() || tempFile.length() == 0) return null;

            int targetWidth = isLarge ? 480 : 240;
            int targetHeight = isLarge ? 240 : 120;
            return GlobalImageCache.decodeFileSafely(tempFile, targetWidth, targetHeight, 2);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception e) {}
            }
            if (tempFile != null && tempFile.exists()) {
                try { tempFile.delete(); } catch (Exception e) {}
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

    /**
     * 虚拟化行适配器（手机用）：ListView 只构建可见行。
     * 行型：大卡行 / 双小卡行 / 平板混合行（大卡+双小卡竖排）。
     */
    static class AnimeListAdapter extends BaseAdapter {

        static final int TYPE_LARGE = 0;
        static final int TYPE_SMALL = 1;
        static final int TYPE_TABLET_MIXED = 2;

        static class RowInfo {
            int type;
            int[] items;
            boolean isEven;
        }

        private Context context;
        private NewAnimeFragment fragment;
        private List<AnimeItem> items;
        private java.util.ArrayList<RowInfo> rows = new java.util.ArrayList<RowInfo>();
        private int screenWidth;
        private int screenHeight;
        private boolean tabletMode;

        AnimeListAdapter(Context context, NewAnimeFragment fragment) {
            this.context = context;
            this.fragment = fragment;
        }

        void setData(List<AnimeItem> items, int screenWidth, int screenHeight, boolean tabletMode) {
            this.items = items;
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
            this.tabletMode = tabletMode;
            rebuildRows();
            notifyDataSetChanged();
        }

        private void rebuildRows() {
            rows.clear();
            if (items == null || items.size() == 0) return;
            int index = 0;
            int largeCardIndex = 0;
            while (index < items.size()) {
                if (index % 3 == 0) {
                    if (tabletMode && index + 2 < items.size()) {
                        RowInfo r = new RowInfo();
                        r.type = TYPE_TABLET_MIXED;
                        r.items = new int[]{index, index + 1, index + 2};
                        r.isEven = (largeCardIndex % 2 == 0);
                        rows.add(r);
                        index += 3;
                    } else {
                        RowInfo r = new RowInfo();
                        r.type = TYPE_LARGE;
                        r.items = new int[]{index};
                        rows.add(r);
                        index++;
                    }
                    largeCardIndex++;
                } else {
                    RowInfo r = new RowInfo();
                    r.type = TYPE_SMALL;
                    r.items = new int[]{index, (index + 1 < items.size()) ? index + 1 : -1};
                    rows.add(r);
                    index = (r.items[1] >= 0) ? index + 2 : index + 1;
                }
            }
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return position < rows.size() ? rows.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public int getItemViewType(int position) {
            if (position < rows.size()) return rows.get(position).type;
            return TYPE_LARGE;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (position >= rows.size()) {
                return convertView != null ? convertView : new View(context);
            }
            RowInfo row = rows.get(position);
            if (row.type == TYPE_LARGE) {
                return buildLargeCard(convertView, items.get(row.items[0]));
            } else if (row.type == TYPE_SMALL) {
                return buildSmallRow(convertView, row);
            } else {
                return buildTabletMixedRow(convertView, row);
            }
        }

        private View buildLargeCard(View convertView, AnimeItem item) {
            View card = convertView;
            if (card == null) {
                card = LayoutInflater.from(context).inflate(R.layout.item_anime_large, null);
                int cardHeight = screenWidth / 2;
                int maxHeight = (int) (screenHeight * 0.45f);
                if (cardHeight > maxHeight) cardHeight = maxHeight;
                if (cardHeight < 80) cardHeight = 80;
                card.setLayoutParams(new AbsListView.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, cardHeight));
            }
            bindCard(card, item, true);
            return card;
        }

        private View buildSmallRow(View convertView, RowInfo row) {
            LinearLayout container = (LinearLayout) convertView;
            if (container == null) {
                container = new LinearLayout(context);
                container.setOrientation(LinearLayout.HORIZONTAL);
                int smallHeight = computeSmallHeight();

                View left = LayoutInflater.from(context).inflate(R.layout.item_anime_small, null);
                left.setLayoutParams(new LinearLayout.LayoutParams(0, smallHeight, 1f));
                container.addView(left);

                View divider = new View(context);
                divider.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(4),
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT));
                container.addView(divider);

                View right = LayoutInflater.from(context).inflate(R.layout.item_anime_small, null);
                right.setLayoutParams(new LinearLayout.LayoutParams(0, smallHeight, 1f));
                container.addView(right);

                container.setLayoutParams(new AbsListView.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, smallHeight));
            }
            bindCard(container.getChildAt(0), items.get(row.items[0]), false);
            View right = container.getChildAt(2);
            if (row.items.length > 1 && row.items[1] >= 0) {
                right.setVisibility(View.VISIBLE);
                bindCard(right, items.get(row.items[1]), false);
            } else {
                right.setVisibility(View.INVISIBLE);
            }
            return container;
        }

        private int computeSmallHeight() {
            int smallWidth = (screenWidth - dpToPx(4)) / 2;
            int smallHeight = smallWidth / 2;
            int maxHeight = (int) (screenHeight * 0.4f);
            if (smallHeight > maxHeight) smallHeight = maxHeight;
            if (smallHeight < 60) smallHeight = 60;
            return smallHeight;
        }

        private View buildTabletMixedRow(View convertView, RowInfo row) {
            LinearLayout container = (LinearLayout) convertView;
            if (container == null) {
                container = new LinearLayout(context);
                container.setOrientation(LinearLayout.HORIZONTAL);

                int displayWidth = screenWidth;
                boolean isLandscape = screenWidth > screenHeight;
                int largeWidth = isLandscape ? displayWidth * 2 / 3 : displayWidth * 3 / 5;
                int smallWidth = displayWidth - largeWidth - dpToPx(4);
                int smallHeight = smallWidth / 3;
                int rowHeight = smallHeight * 2 + dpToPx(4);

                LinearLayout smallColumn = new LinearLayout(context);
                smallColumn.setOrientation(LinearLayout.VERTICAL);
                smallColumn.setLayoutParams(new LinearLayout.LayoutParams(smallWidth, rowHeight));

                View sm1 = LayoutInflater.from(context).inflate(R.layout.item_anime_small, null);
                sm1.setLayoutParams(new LinearLayout.LayoutParams(smallWidth, smallHeight));
                smallColumn.addView(sm1);

                View gap = new View(context);
                gap.setLayoutParams(new LinearLayout.LayoutParams(smallWidth, dpToPx(4)));
                smallColumn.addView(gap);

                View sm2 = LayoutInflater.from(context).inflate(R.layout.item_anime_small, null);
                sm2.setLayoutParams(new LinearLayout.LayoutParams(smallWidth, smallHeight));
                smallColumn.addView(sm2);

                View large = LayoutInflater.from(context).inflate(R.layout.item_anime_large, null);
                large.setLayoutParams(new LinearLayout.LayoutParams(largeWidth, rowHeight));

                if (row.isEven) {
                    container.addView(large);
                    container.addView(makeDivider(rowHeight));
                    container.addView(smallColumn);
                } else {
                    container.addView(smallColumn);
                    container.addView(makeDivider(rowHeight));
                    container.addView(large);
                }
                container.setLayoutParams(new AbsListView.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, rowHeight));
            }

            View large = row.isEven ? container.getChildAt(0) : container.getChildAt(2);
            LinearLayout smallColumn = row.isEven
                    ? (LinearLayout) container.getChildAt(2)
                    : (LinearLayout) container.getChildAt(0);
            bindCard(large, items.get(row.items[0]), true);
            bindCard(smallColumn.getChildAt(0), items.get(row.items[1]), false);
            bindCard(smallColumn.getChildAt(2), items.get(row.items[2]), false);
            return container;
        }

        private View makeDivider(int height) {
            View d = new View(context);
            d.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(4), height));
            return d;
        }

        private void bindCard(View card, final AnimeItem item, boolean isLarge) {
            if (card == null || item == null) return;
            TextView title = (TextView) card.findViewById(R.id.anime_title);
            ImageView cover = (ImageView) card.findViewById(R.id.anime_cover);
            if (title != null) {
                title.setText(item.title != null ? item.title : "");
            }
            if (cover != null) {
                cover.setTag(item.coverUrl);
                if (item.coverUrl != null && item.coverUrl.length() > 0 && !fragment.isDestroyed) {
                    fragment.loadImageLazy(cover, item.coverUrl, isLarge);
                }
            }
            // 手机 ListView 路径的点击（平板 createLargeCard/createSmallCard 已有点击）
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (fragment != null) {
                        fragment.openAnimeDetail(item);
                    }
                }
            });
        }

        private int dpToPx(int dp) {
            float density = context.getResources().getDisplayMetrics().density;
            return (int) (dp * density + 0.5f);
        }
    }
}