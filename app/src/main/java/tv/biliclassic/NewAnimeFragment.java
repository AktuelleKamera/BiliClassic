package tv.biliclassic;

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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.SoftReference;
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

import tv.biliclassic.util.SharedPreferencesUtil;

public class NewAnimeFragment extends Fragment {

    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler delayHandler = new Handler();

    private Map<String, SoftReference<Bitmap>> imageCache = new HashMap<String, SoftReference<Bitmap>>();
    private Map<String, Boolean> loadingMap = new HashMap<String, Boolean>();

    private LinearLayout loadingContainer;
    private ScrollView contentContainer;
    private LinearLayout gridContainer;

    private int screenWidth = 0;
    private int screenHeight = 0;
    private boolean dataLoaded = false;
    private boolean sizeConfirmed = false;
    private boolean isDestroyed = false;

    private File cacheDir;

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

        loadingContainer = (LinearLayout) view.findViewById(R.id.loading_container);
        contentContainer = (ScrollView) view.findViewById(R.id.content_container);
        gridContainer = (LinearLayout) view.findViewById(R.id.grid_container);

        loadingContainer.setVisibility(View.VISIBLE);
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
        }, 1500);
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

            // 先尝试加载缓存
            List<AnimeItem> cachedItems = loadLocalCache();
            if (cachedItems != null && cachedItems.size() > 0) {
                loadingContainer.setVisibility(View.GONE);
                contentContainer.setVisibility(View.VISIBLE);
                displayAnimeList(cachedItems);
            } else {
                loadAnimeData();
                loadingContainer.setVisibility(View.GONE);
                contentContainer.setVisibility(View.VISIBLE);
            }

            // 延迟重新检测屏幕尺寸（横屏适配）
            delayHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isDestroyed) {
                        recheckScreenSize();
                    }
                }
            }, 1000);
        }
    }

    private void recheckScreenSize() {
        if (isDestroyed) return;

        int newWidth = getResources().getDisplayMetrics().widthPixels;
        int newHeight = getResources().getDisplayMetrics().heightPixels;

        if (isLandscapeDevice() && newWidth < newHeight) {
            int temp = newWidth;
            newWidth = newHeight;
            newHeight = temp;
        }

        if ((newWidth != screenWidth || newHeight != screenHeight) && newWidth > 0 && newHeight > 0) {
            screenWidth = newWidth;
            screenHeight = newHeight;
            refreshLayout();
        }
        sizeConfirmed = true;
    }

    private void refreshLayout() {
        if (isDestroyed || gridContainer == null) {
            return;
        }
        gridContainer.removeAllViews();

        List<AnimeItem> cachedItems = loadLocalCache();
        if (cachedItems != null && cachedItems.size() > 0) {
            displayAnimeList(cachedItems);
        } else {
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

    @Override
    public void onLowMemory() {
        super.onLowMemory();
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
        imageCache.clear();
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
        imageCache.clear();
        loadingMap.clear();
    }

    // ==================== 缓存方法 ====================

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

            // 检查是否过期（超过24小时）
            long lastModified = jsonFile.lastModified();
            long now = System.currentTimeMillis();
            if (now - lastModified > 24 * 60 * 60 * 1000) {
                jsonFile.delete();
                return null;
            }

            String jsonStr = sb.toString();
            List<AnimeItem> items = parseAnimeJson(jsonStr);

            // 检查封面图片缓存目录是否存在
            File coverDir = new File(cacheDir, "covers");
            if (!coverDir.exists() || !coverDir.isDirectory()) {
                jsonFile.delete();
                return null;
            }

            // 检查是否有至少一张封面图片存在
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

    // ==================== 网络加载 ====================

    private void loadAnimeData() {
        if (isDestroyed) return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (isDestroyed) return;

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

                    final List<AnimeItem> items = parseAnimeJson(jsonStr);

                    if (getActivity() != null && !isDestroyed) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (isDestroyed) return;
                                if (items == null || items.size() == 0) {
                                    Toast.makeText(getActivity(), "获取番剧数据失败", Toast.LENGTH_SHORT).show();
                                    loadDefaultAnimeData();
                                    return;
                                }
                                saveToCache(jsonStr);
                                displayAnimeList(items);
                            }
                        });
                    }
                } catch (final Exception e) {
                    e.printStackTrace();
                    if (getActivity() != null && !isDestroyed) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (isDestroyed) return;
                                Toast.makeText(getActivity(), "网络请求失败，使用默认数据", Toast.LENGTH_SHORT).show();
                                loadDefaultAnimeData();
                            }
                        });
                    }
                }
            }
        }).start();
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
                if (title != null && title.length() > 0 && image != null && image.length() > 0) {
                    items.add(new AnimeItem(title, image, isBig));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return items;
    }

    private void loadDefaultAnimeData() {
        if (isDestroyed) return;

        List<AnimeItem> items = new ArrayList<AnimeItem>();
        items.add(new AnimeItem("进击的巨人", "http://www.biliclassic.cn/api/main/bigimg1.jpg", true));
        items.add(new AnimeItem("约会大作战", "http://www.biliclassic.cn/api/main/simg1.jpg", false));
        items.add(new AnimeItem("某科学的超电磁炮S", "http://www.biliclassic.cn/api/main/simg2.jpg", false));
        items.add(new AnimeItem("我的青春恋爱物语果然有问题", "http://www.biliclassic.cn/api/main/bigimg2.jpg", true));
        items.add(new AnimeItem("打工吧！魔王大人", "http://www.biliclassic.cn/api/main/simg3.jpg", false));
        items.add(new AnimeItem("翠星之加尔刚蒂亚", "http://www.biliclassic.cn/api/main/simg4.jpg", false));
        items.add(new AnimeItem("潜行吧！奈亚子W", "http://www.biliclassic.cn/api/main/bigimg3.jpg", true));
        items.add(new AnimeItem("恶魔阿萨谢尔在召唤你Z", "http://www.biliclassic.cn/api/main/simg5.jpg", false));
        items.add(new AnimeItem("旋风管家！Cuties", "http://www.biliclassic.cn/api/main/simg6.jpg", false));
        displayAnimeList(items);
    }

    // ==================== 显示方法 ====================

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

    private View createLargeCard(AnimeItem item, boolean isFirst) {
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

        if (item.coverUrl != null && item.coverUrl.length() > 0 && !isDestroyed) {
            loadImageLazy(ivCover, item.coverUrl, true);
        }

        return card;
    }

    private View createSmallCard(AnimeItem item) {
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

        if (item.coverUrl != null && item.coverUrl.length() > 0 && !isDestroyed) {
            loadImageLazy(ivCover, item.coverUrl, false);
        }

        return card;
    }

    // ==================== 图片加载 ====================

    private void loadImageLazy(final ImageView imageView, final String urlStr, final boolean isLarge) {
        if (isDestroyed || imageView == null || getActivity() == null) return;

        // 1. 检查内存缓存
        if (imageCache.containsKey(urlStr)) {
            SoftReference<Bitmap> ref = imageCache.get(urlStr);
            Bitmap cached = ref.get();
            if (cached != null && !cached.isRecycled()) {
                imageView.setImageBitmap(cached);
                return;
            } else {
                imageCache.remove(urlStr);
            }
        }

        // 2. 检查本地文件缓存
        Bitmap localCached = getBitmapFromCache(urlStr);
        if (localCached != null && !localCached.isRecycled()) {
            imageCache.put(urlStr, new SoftReference<Bitmap>(localCached));
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
                    // 保存到本地缓存
                    saveCoverToCache(urlStr, bitmap);
                    imageCache.put(urlStr, new SoftReference<Bitmap>(bitmap));

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
            // 如果是 https，转为 http
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
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return null;
            }

            InputStream is = conn.getInputStream();

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            is.close();

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

            conn.disconnect();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept-Encoding", "identity");
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.connect();

            if (conn.getResponseCode() != 200) {
                return null;
            }

            is = conn.getInputStream();

            options = new BitmapFactory.Options();
            options.inSampleSize = scale;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inPurgeable = true;
            options.inInputShareable = true;

            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            is.close();
            return bitmap;
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

        AnimeItem(String title, String coverUrl, boolean isLarge) {
            this.title = title;
            this.coverUrl = coverUrl;
            this.isLarge = isLarge;
        }
    }
}