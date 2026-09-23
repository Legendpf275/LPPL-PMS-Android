package com.legendpolyfoams.lpplpms;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int FILE_CHOOSER_REQUEST = 7001;
    private static final int WEB_PERMISSION_REQUEST = 7002;

    private WebView webView;
    private ProgressBar progress;
    private ValueCallback<Uri[]> filePathCallback;
    private PermissionRequest pendingPermissionRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        applySystemBarInsets(root);

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        progress = new ProgressBar(this);
        FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(
                dp(42), dp(42), Gravity.CENTER
        );
        progress.setVisibility(View.GONE);
        root.addView(progress, progressLp);

        setContentView(root);
        configureWebView();

        if (savedInstanceState == null) {
            webView.loadUrl(getString(R.string.pms_url));
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void applySystemBarInsets(View root) {
        getWindow().setStatusBarColor(Color.rgb(57, 168, 68));
        getWindow().setNavigationBarColor(Color.WHITE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(0, bars.top, 0, bars.bottom);
                return insets;
            });
            root.requestApplyInsets();
        } else {
            root.setFitsSystemWindows(true);
        }
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(false);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookies.setAcceptThirdPartyCookies(webView, true);
        }

        webView.addJavascriptInterface(new DownloadBridge(), "LPPLAndroid");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallbackParam,
                    FileChooserParams fileChooserParams
            ) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = filePathCallbackParam;

                Intent intent;
                try {
                    intent = fileChooserParams.createIntent();
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException ex) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "No file picker found on this phone.", Toast.LENGTH_LONG).show();
                    return false;
                }
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean needsMic = false;
                    boolean needsCamera = false;

                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                            needsMic = true;
                        }
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                            needsCamera = true;
                        }
                    }

                    boolean micGranted = !needsMic ||
                            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                    boolean cameraGranted = !needsCamera ||
                            checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

                    if (micGranted && cameraGranted) {
                        request.grant(request.getResources());
                        return;
                    }

                    pendingPermissionRequest = request;
                    java.util.ArrayList<String> permissions = new java.util.ArrayList<>();
                    if (needsMic && !micGranted) permissions.add(Manifest.permission.RECORD_AUDIO);
                    if (needsCamera && !cameraGranted) permissions.add(Manifest.permission.CAMERA);

                    requestPermissions(permissions.toArray(new String[0]), WEB_PERMISSION_REQUEST);
                });
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
                hideAppsScriptWarningBanner();
                installBlobDownloadBridge();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();

                if ("http".equals(scheme) || "https".equals(scheme)) {
                    return false;
                }

                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                } catch (Exception e) {
                    return true;
                }
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error
            ) {
                if (request.isForMainFrame()) {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(
                            MainActivity.this,
                            "PMS could not load. Check internet connection and try again.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url != null && url.startsWith("blob:")) {
                return;
            }

            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                String cookiesHeader = CookieManager.getInstance().getCookie(url);
                if (cookiesHeader != null) request.addRequestHeader("Cookie", cookiesHeader);
                request.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                );
                request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "LPPL_PMS_download"
                );
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(this, "Download started.", Toast.LENGTH_SHORT).show();
            } catch (Exception ex) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {
                    Toast.makeText(this, "Unable to download this file.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void hideAppsScriptWarningBanner() {
        String js =
                "(function(){" +
                "try{" +
                "var style=document.getElementById('lppl-native-clean-style');" +
                "if(!style){" +
                "style=document.createElement('style');" +
                "style.id='lppl-native-clean-style';" +
                "style.textContent='#warning{display:none!important;height:0!important;min-height:0!important;margin:0!important;padding:0!important;overflow:hidden!important;}';" +
                "(document.head||document.documentElement).appendChild(style);" +
                "}" +
                "var hide=function(){" +
                "var w=document.getElementById('warning');" +
                "if(w){w.style.setProperty('display','none','important');w.style.setProperty('height','0','important');}" +
                "};" +
                "hide();" +
                "if(!window.__lpplWarningObserver){" +
                "window.__lpplWarningObserver=new MutationObserver(hide);" +
                "window.__lpplWarningObserver.observe(document.documentElement,{childList:true,subtree:true});" +
                "}" +
                "}catch(e){}" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private void installBlobDownloadBridge() {
        String js =
                "(function(){" +
                "if(window.__lpplAndroidBlobBridge)return;" +
                "window.__lpplAndroidBlobBridge=true;" +
                "document.addEventListener('click',function(e){" +
                "var a=e.target.closest&&e.target.closest('a[download]');" +
                "if(!a||!a.href||a.href.indexOf('blob:')!==0)return;" +
                "e.preventDefault();" +
                "fetch(a.href).then(function(r){return r.blob();}).then(function(b){" +
                "var fr=new FileReader();" +
                "fr.onloadend=function(){" +
                "try{LPPLAndroid.saveBase64(a.download||'LPPL_PMS_download',b.type||'application/octet-stream',fr.result);}catch(x){}" +
                "};" +
                "fr.readAsDataURL(b);" +
                "});" +
                "},true);" +
                "})();";

        webView.evaluateJavascript(js, null);
    }

    public class DownloadBridge {
        @JavascriptInterface
        public void saveBase64(String fileName, String mimeType, String dataUrl) {
            runOnUiThread(() -> {
                try {
                    String safeName = sanitizeFileName(fileName);
                    String payload = dataUrl;
                    int comma = dataUrl.indexOf(',');
                    if (comma >= 0) payload = dataUrl.substring(comma + 1);

                    byte[] bytes = Base64.decode(payload, Base64.DEFAULT);
                    OutputStream os;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                        values.put(MediaStore.Downloads.MIME_TYPE,
                                mimeType == null || mimeType.isEmpty() ? "application/octet-stream" : mimeType);
                        values.put(MediaStore.Downloads.RELATIVE_PATH,
                                Environment.DIRECTORY_DOWNLOADS + "/LPPL PMS");

                        Uri uri = getContentResolver().insert(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                values
                        );
                        if (uri == null) throw new IllegalStateException("Could not create download file.");
                        os = getContentResolver().openOutputStream(uri);
                    } else {
                        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "LPPL PMS");
                        if (!dir.exists()) dir.mkdirs();
                        os = new FileOutputStream(new File(dir, safeName));
                    }

                    if (os == null) throw new IllegalStateException("Could not open download file.");
                    os.write(bytes);
                    os.flush();
                    os.close();

                    Toast.makeText(MainActivity.this, "Saved: " + safeName, Toast.LENGTH_LONG).show();
                } catch (Exception ex) {
                    Toast.makeText(MainActivity.this, "Could not save download.", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private String sanitizeFileName(String name) {
        String n = (name == null || name.trim().isEmpty()) ? "LPPL_PMS_download" : name.trim();
        return n.replaceAll("[\\/:*?\"<>|]", "_");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }

            Uri[] results = null;
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    } else if (data.getData() != null) {
                        results = new Uri[]{data.getData()};
                    }
                }
            }

            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == WEB_PERMISSION_REQUEST && pendingPermissionRequest != null) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
            } else {
                pendingPermissionRequest.deny();
            }
            pendingPermissionRequest = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
