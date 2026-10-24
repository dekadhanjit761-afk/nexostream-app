package com.nexostream.app;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private FrameLayout customViewContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private View fabShield;

    private static final String ALLOWED_DOMAIN = "streambydhanjit.vercel.app";
    private final Set<String> blockedHosts = new HashSet<>();
    private final Handler autoDimHandler = new Handler(Looper.getMainLooper());
    private Runnable dimRunnable;

    private boolean uBlockEnabled = true;
    private boolean easyListEnabled = true;
    private boolean privacyEnabled = true;
    private SharedPreferences prefs;

    private float dX, dY;
    private boolean isDragging = false;
    private boolean doubleBackToExitPressedOnce = false;

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("NexoStreamSettings", MODE_PRIVATE);
        uBlockEnabled = prefs.getBoolean("uBlock", true);
        easyListEnabled = prefs.getBoolean("easyList", true);
        privacyEnabled = prefs.getBoolean("privacy", true);

        webView = findViewById(R.id.webview);
        customViewContainer = findViewById(R.id.custom_view_container);
        fabShield = findViewById(R.id.fab_shield);

        setupWebView();
        setupFloatingButton();
        fetchLiveFilterRules();

        webView.loadUrl("https://streambydhanjit.vercel.app/");
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                customViewContainer.addView(view);
                customViewContainer.setVisibility(View.VISIBLE);
                webView.setVisibility(View.GONE);
                fabShield.setVisibility(View.GONE); // Fullscreen me floating button hide hoga
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                customViewContainer.removeView(customView);
                customViewContainer.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                fabShield.setVisibility(View.VISIBLE); // Fullscreen exit hote hi button wapas show hoga
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
                customView = null;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String host = request.getUrl().getHost();
                if (host != null && (host.contains(ALLOWED_DOMAIN) || host.contains("vercel.app") || host.contains("stream"))) {
                    return false;
                }
                return true; // 100% Undetectable Redirect Killer (External popups aur ads drop honge)
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String host = request.getUrl().getHost();
                if (host != null) {
                    for (String blocked : blockedHosts) {
                        if (host.contains(blocked)) {
                            return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream("".getBytes()));
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(
                    "javascript:(function() {" +
                    "var style = document.createElement('style');" +
                    "style.innerHTML = 'iframe[src*=\"ads\"], iframe[src*=\"pop\"], div[class*=\"banner\"], div[class*=\"ad-box\"] { display: none !important; }';" +
                    "document.head.appendChild(style);" +
                    "})()", null);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupFloatingButton() {
        dimRunnable = () -> fabShield.animate().alpha(0.20f).setDuration(500).start();
        resetDimTimer();

        fabShield.setOnTouchListener((v, event) -> {
            resetDimTimer();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dX = v.getX() - event.getRawX();
                    dY = v.getY() - event.getRawY();
                    isDragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float newX = event.getRawX() + dX;
                    float newY = event.getRawY() + dY;
                    v.setX(newX);
                    v.setY(newY);
                    isDragging = true;
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!isDragging) {
                        showAdShieldDialog();
                    }
                    return true;
            }
            return false;
        });
    }

    private void resetDimTimer() {
        fabShield.animate().alpha(1.0f).setDuration(200).start();
        autoDimHandler.removeCallbacks(dimRunnable);
        autoDimHandler.postDelayed(dimRunnable, 10000); // 10 second baad transparent hoga
    }

    private void showAdShieldDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_adblock_settings);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        Switch swUblock = dialog.findViewById(R.id.sw_ublock);
        Switch swEasyList = dialog.findViewById(R.id.sw_easylist);
        Switch swPrivacy = dialog.findViewById(R.id.sw_privacy);

        swUblock.setChecked(uBlockEnabled);
        swEasyList.setChecked(easyListEnabled);
        swPrivacy.setChecked(privacyEnabled);

        swUblock.setOnCheckedChangeListener((btn, isChecked) -> {
            uBlockEnabled = isChecked;
            prefs.edit().putBoolean("uBlock", isChecked).apply();
            fetchLiveFilterRules();
        });

        swEasyList.setOnCheckedChangeListener((btn, isChecked) -> {
            easyListEnabled = isChecked;
            prefs.edit().putBoolean("easyList", isChecked).apply();
            fetchLiveFilterRules();
        });

        swPrivacy.setOnCheckedChangeListener((btn, isChecked) -> {
            privacyEnabled = isChecked;
            prefs.edit().putBoolean("privacy", isChecked).apply();
            fetchLiveFilterRules();
        });

        dialog.findViewById(R.id.btn_close_settings).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void fetchLiveFilterRules() {
        new Thread(() -> {
            blockedHosts.clear();
            blockedHosts.add("doubleclick.net");
            blockedHosts.add("googlesyndication.com");
            blockedHosts.add("popads.net");
            blockedHosts.add("adsterra.com");

            if (uBlockEnabled) downloadFilterList("https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/badware.txt");
            if (easyListEnabled) downloadFilterList("https://easylist.to/easylist/easylist.txt");
            if (privacyEnabled) downloadFilterList("https://easylist.to/easylist/easyprivacy.txt");
        }).start();
    }

    private void downloadFilterList(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < 300) {
                line = line.trim();
                if (line.startsWith("||") && line.contains("^")) {
                    String domain = line.substring(2, line.indexOf("^"));
                    blockedHosts.add(domain);
                    count++;
                }
            }
            reader.close();
        } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        // 1. Agar video fullscreen me hai toh pehle fullscreen exit karega
        if (customView != null) {
            if (webView.getWebChromeClient() != null) {
                ((WebChromeClient) webView.getWebChromeClient()).onHideCustomView();
            }
            return;
        }

        // 2. Agar website ke andar navigation history hai toh pichhle page par jayega
        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }

        // 3. Double back dabane par hi app band hoga
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Press BACK again to exit NexoStream", Toast.LENGTH_SHORT).show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
    }
}
