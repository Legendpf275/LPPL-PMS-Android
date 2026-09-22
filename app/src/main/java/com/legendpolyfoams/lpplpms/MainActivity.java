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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int FILE_CHOOSER_REQUEST = 7001;
    private static final int WEB_PERMISSION_REQUEST = 7002;

    private static final int LPPL_GREEN = Color.rgb(57, 168, 68);
    private static final int LPPL_TEXT = Color.rgb(31, 41, 55);
    private static final int LPPL_MUTED = Color.rgb(107, 114, 128);
    private static final int LPPL_BORDER = Color.rgb(229, 231, 235);

    private WebView webView;
    private ProgressBar progress;
    private LinearLayout bottomNav;
    private TextView navHome;
    private TextView navTasks;
    private TextView navTickets;
    private TextView navMenu;

    private ValueCallback<Uri[]> filePathCallback;
    private PermissionRequest pendingPermissionRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout appRoot = new LinearLayout(this);
        appRoot.setOrientation(LinearLayout.VERTICAL);
        appRoot.setBackgroundColor(Color.WHITE);

        FrameLayout contentRoot = new FrameLayout(this);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );

        webView = new WebView(this);
        contentRoot.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        progress = new ProgressBar(this);
        FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(
                dp(42), dp(42), Gravity.CENTER
        );
        progress.setVisibility(View.GONE);
        contentRoot.addView(progress, progressLp);

        appRoot.addView(contentRoot, contentLp);

        bottomNav = buildBottomNavigation();
        bottomNav.setVisibility(View.GONE);
        appRoot.addView(bottomNav, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
        ));

        setContentView(appRoot);
        configureWebView();

        if (savedInstanceState == null) {
            webView.loadUrl(getString(R.string.pms_url));
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private LinearLayout buildBottomNavigation() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(8), dp(4), dp(8), dp(4));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setStroke(dp(1), LPPL_BORDER);
        bar.setBackground(bg);
        bar.setElevation(dp(10));

        navHome = createNavItem("⌂", "Home", "home");
        navTasks = createNavItem("✓", "Tasks", "tasks");
        navTickets = createNavItem("✉", "Tickets", "tickets");
        navMenu = createNavItem("☰", "Menu", "menu");

        bar.addView(navHome);
        bar.addView(navTasks);
        bar.addView(navTickets);
        bar.addView(navMenu);

        setSelectedNav(navHome);
        return bar;
    }

    private TextView createNavItem(String icon, String label, String action) {
        TextView item = new TextView(this);
        item.setText(icon + "\n" + label);
        item.setTextSize(11.5f);
        item.setTextColor(LPPL_MUTED);
        item.setGravity(Gravity.CENTER);
        item.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        item.setPadding(dp(4), dp(2), dp(4), dp(2));
        item.setLineSpacing(0f, 0.92f);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
        );
        lp.setMargins(dp(2), 0, dp(2), 0);
        item.setLayoutParams(lp);

        item.setOnClickListener(v -> {
            if ("menu".equals(action)) {
                toggleWebMenu();
                setSelectedNav(navMenu);
            } else {
                navigateWeb(action);
                if ("home".equals(action)) setSelectedNav(navHome);
                if ("tasks".equals(action)) setSelectedNav(navTasks);
                if ("tickets".equals(action)) setSelectedNav(navTickets);
            }
        });
        return item;
    }

    private void setSelectedNav(TextView selected) {
        TextView[] items = new TextView[]{navHome, navTasks, navTickets, navMenu};
        for (TextView item : items) {
            if (item == null) continue;
            item.setTextColor(item == selected ? LPPL_GREEN : LPPL_MUTED);
            item.setTypeface(Typeface.DEFAULT, item == selected ? Typeface.BOLD : Typeface.NORMAL);

            GradientDrawable itemBg = new GradientDrawable();
            itemBg.setCornerRadius(dp(12));
            itemBg.setColor(item == selected
                    ? Color.rgb(231, 245, 233)
                    : Color.TRANSPARENT);
            item.setBackground(itemBg);
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
        s.setTextZoom(100);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookies.setAcceptThirdPartyCookies(webView, true);
        }

        webView.addJavascriptInterface(new AppBridge(), "LPPLAndroid");

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
                installMobileAppShell();
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

    private void installMobileAppShell() {
        String js =
                "(function(){" +
                "try{" +
                "document.documentElement.classList.add('lppl-native-app');" +
                "document.body&&document.body.classList.add('lppl-native-app');" +
                "var vp=document.querySelector('meta[name=viewport]');" +
                "if(!vp){vp=document.createElement('meta');vp.name='viewport';document.head.appendChild(vp);}" +
                "vp.content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no';" +

                "var style=document.getElementById('lppl-native-mobile-style');" +
                "if(!style){" +
                "style=document.createElement('style');" +
                "style.id='lppl-native-mobile-style';" +
                "style.textContent=" +
                "'#warning{display:none!important;height:0!important;min-height:0!important;margin:0!important;padding:0!important;overflow:hidden!important;}' +" +
                "'.lppl-native-app body,.lppl-native-app{font-size:13px!important;-webkit-text-size-adjust:100%!important;}' +" +
                "'body{overflow-x:hidden!important;background:#f6f8f6!important;}' +" +
                "'h1{font-size:21px!important;line-height:1.2!important;margin:10px 0!important;}' +" +
                "'h2{font-size:18px!important;line-height:1.25!important;margin:9px 0!important;}' +" +
                "'h3{font-size:16px!important;line-height:1.25!important;margin:8px 0!important;}' +" +
                "'p{line-height:1.35!important;}' +" +
                "'button,.btn,[role=button],input[type=button],input[type=submit]{min-height:34px!important;padding:7px 10px!important;font-size:12.5px!important;border-radius:9px!important;}' +" +
                "'input,select,textarea{min-height:38px!important;padding:7px 9px!important;font-size:13px!important;border-radius:9px!important;box-sizing:border-box!important;}' +" +
                "'textarea{min-height:76px!important;}' +" +
                "'table{font-size:11.5px!important;line-height:1.25!important;border-collapse:collapse!important;}' +" +
                "'th,td{padding:6px 7px!important;vertical-align:middle!important;}' +" +
                "'th{font-size:11px!important;white-space:nowrap!important;}' +" +
                "'.card,[class*=card],[class*=panel]{padding:10px!important;margin:6px 0!important;border-radius:12px!important;}' +" +
                "'.container,[class*=container],.content,[class*=content],main{max-width:100%!important;box-sizing:border-box!important;}' +" +
                "'.main-content,#mainContent,[class*=main-content]{padding:10px!important;margin-left:0!important;width:100%!important;}' +" +
                "'.topbar,.top-bar,[class*=topbar],[class*=top-bar]{min-height:48px!important;padding:6px 10px!important;}' +" +
                "'.lppl-native-sidebar{position:fixed!important;z-index:99999!important;top:0!important;bottom:0!important;left:0!important;max-width:84vw!important;width:280px!important;transform:translateX(-105%)!important;transition:transform .18s ease!important;box-shadow:0 10px 34px rgba(0,0,0,.22)!important;}' +" +
                "'.lppl-native-menu-open .lppl-native-sidebar{transform:translateX(0)!important;}' +" +
                "'.lppl-native-table-wrap{overflow-x:auto!important;-webkit-overflow-scrolling:touch!important;width:100%!important;}' +" +
                "'.lppl-native-table-wrap table{min-width:640px!important;}' +" +
                "'@media(max-width:768px){.grid,[class*=grid]{gap:8px!important;} [class*=stat],[class*=metric],[class*=summary]{padding:9px!important;} }';" +
                "(document.head||document.documentElement).appendChild(style);" +
                "}" +

                "var clean=function(){" +
                "var w=document.getElementById('warning');if(w)w.style.setProperty('display','none','important');" +
                "Array.prototype.forEach.call(document.querySelectorAll('a,button'),function(el){" +
                "var t=(el.textContent||'').trim().toLowerCase();" +
                "if(t==='install on phone'||t==='install app'||t==='add to home screen')el.style.display='none';" +
                "});" +
                "var sb=document.querySelector('#sidebar,.sidebar,.app-sidebar,.side-bar,[class~=sidebar]');" +
                "if(sb)sb.classList.add('lppl-native-sidebar');" +
                "Array.prototype.forEach.call(document.querySelectorAll('table'),function(tbl){" +
                "var p=tbl.parentElement;" +
                "if(p&&!p.classList.contains('lppl-native-table-wrap')){" +
                "var wrap=document.createElement('div');wrap.className='lppl-native-table-wrap';" +
                "p.insertBefore(wrap,tbl);wrap.appendChild(tbl);" +
                "}" +
                "});" +
                "};" +
                "clean();" +

                "var reportState=function(){" +
                "var text=(document.body&&document.body.innerText||'').toLowerCase();" +
                "var hasApp=text.indexOf('dashboard')>=0||text.indexOf('my tasks')>=0||text.indexOf('help tickets')>=0;" +
                "var pwd=document.querySelector('input[type=password]');" +
                "var logged=hasApp&&!(pwd&&pwd.offsetParent!==null&&text.indexOf('employee id')>=0);" +
                "try{LPPLAndroid.setLoggedIn(!!logged);}catch(e){}" +
                "};" +
                "reportState();" +

                "if(!window.__lpplNativeObserver){" +
                "window.__lpplNativeObserver=new MutationObserver(function(){clean();reportState();});" +
                "window.__lpplNativeObserver.observe(document.documentElement,{childList:true,subtree:true});" +
                "setInterval(reportState,1200);" +
                "}" +
                "}catch(e){}" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private void navigateWeb(String target) {
        String names;
        if ("home".equals(target)) {
            names = "['dashboard','home']";
        } else if ("tasks".equals(target)) {
            names = "['my tasks','tasks']";
        } else {
            names = "['help tickets','tickets','help desk']";
        }

        String js =
                "(function(){" +
                "var names=" + names + ";" +
                "var els=document.querySelectorAll('a,button,[role=button],[onclick]');" +
                "for(var i=0;i<els.length;i++){" +
                "var e=els[i];" +
                "var text=((e.textContent||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')).trim().toLowerCase().replace(/\\s+/g,' ');" +
                "for(var j=0;j<names.length;j++){" +
                "if(text===names[j]||text.indexOf(names[j])>=0){" +
                "try{document.documentElement.classList.remove('lppl-native-menu-open');e.click();e.scrollIntoView({block:'nearest'});return true;}catch(x){}" +
                "}" +
                "}" +
                "}" +
                "return false;" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private void toggleWebMenu() {
        String js =
                "(function(){" +
                "document.documentElement.classList.toggle('lppl-native-menu-open');" +
                "var sb=document.querySelector('.lppl-native-sidebar');" +
                "if(sb)return true;" +
                "var els=document.querySelectorAll('button,a,[role=button]');" +
                "for(var i=0;i<els.length;i++){" +
                "var e=els[i];" +
                "var t=((e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')+' '+(e.textContent||'')).toLowerCase();" +
                "if(t.indexOf('menu')>=0||t.indexOf('navigation')>=0||t.indexOf('☰')>=0){try{e.click();return true;}catch(x){}}" +
                "}" +
                "return false;" +
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

    public class AppBridge {
        @JavascriptInterface
        public void setLoggedIn(boolean loggedIn) {
            runOnUiThread(() -> {
                if (bottomNav != null) {
                    bottomNav.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
                }
            });
        }

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
        return n.replaceAll("[\\\\/:*?\"<>|]", "_");
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
