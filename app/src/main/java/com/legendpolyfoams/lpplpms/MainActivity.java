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
                installMobileCompactUi();
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


    private void applySystemBarInsets(View root) {
        getWindow().setStatusBarColor(Color.rgb(57, 168, 68));
        getWindow().setNavigationBarColor(Color.WHITE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars =
                        insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(0, bars.top, 0, bars.bottom);
                return insets;
            });
            root.requestApplyInsets();
        } else {
            root.setFitsSystemWindows(true);
        }
    }

    private void installMobileCompactUi() {
        String js =
                "(function(){" +
                "try{" +
                "if(!document.getElementById('lppl-mobile-compact-style')){" +
                "var st=document.createElement('style');" +
                "st.id='lppl-mobile-compact-style';" +
                "st.textContent=" +
                "'.lppl-native-list-row{background:#fff;border:1px solid #e5e7eb;border-radius:12px;margin:8px 12px;padding:10px 12px;box-shadow:0 1px 2px rgba(0,0,0,.03);font-family:inherit;}' +" +
                "'.lppl-native-row-head{display:flex;align-items:flex-start;justify-content:space-between;gap:8px;}' +" +
                "'.lppl-native-row-id{font-size:12px;font-weight:800;color:#1f2937;white-space:nowrap;}' +" +
                "'.lppl-native-row-title{font-size:14px;font-weight:700;color:#111827;margin-top:4px;line-height:1.25;}' +" +
                "'.lppl-native-row-meta{display:flex;flex-wrap:wrap;gap:5px 10px;margin-top:6px;font-size:11px;color:#6b7280;}' +" +
                "'.lppl-native-row-actions{display:flex;gap:6px;margin-top:8px;flex-wrap:wrap;}' +" +
                "'.lppl-native-row-actions button{min-height:32px!important;padding:5px 10px!important;font-size:12px!important;border-radius:8px!important;}' +" +
                "'.lppl-native-chip{display:inline-block;border-radius:999px;padding:4px 8px;font-size:10px;font-weight:700;background:#eef2f0;color:#374151;white-space:nowrap;}' +" +
                "'.lppl-native-chip.pending{background:#fff3c4;color:#7a5a00;}' +" +
                "'.lppl-native-chip.closed,.lppl-native-chip.completed{background:#dff5e3;color:#238636;}' +" +
                "'.lppl-native-filter-toggle{margin:8px 12px 4px;padding:8px 12px;border:1px solid #d1d5db;background:#fff;color:#238636;border-radius:9px;font-size:12px;font-weight:700;}' +" +
                "'.lppl-native-filter-box{margin:6px 12px!important;padding:10px!important;}' +" +
                "'.lppl-native-filter-box label{font-size:11px!important;margin-bottom:3px!important;}' +" +
                "'.lppl-native-filter-box input,.lppl-native-filter-box select{min-height:36px!important;font-size:12px!important;padding:6px 8px!important;}' +" +
                "'.lppl-native-original-hidden{display:none!important;}' +" +
                "'@media(max-width:700px){h1{font-size:22px!important;margin:10px 0!important;}h2{font-size:18px!important;}body{overflow-x:hidden!important;}}';" +
                "(document.head||document.documentElement).appendChild(st);" +
                "}" +

                "function norm(s){return (s||'').replace(/\\s+/g,' ').trim();}" +
                "function lines(el){return (el.innerText||'').split(/\\n+/).map(norm).filter(Boolean);}" +
                "function after(arr,label){" +
                "var L=label.toUpperCase();" +
                "for(var i=0;i<arr.length-1;i++){if(arr[i].toUpperCase()===L)return arr[i+1];}" +
                "return '';" +
                "}" +
                "function hasAll(t,arr){t=t.toUpperCase();for(var i=0;i<arr.length;i++){if(t.indexOf(arr[i])<0)return false;}return true;}" +
                "function smallestCard(sig){" +
                "var all=document.querySelectorAll('div,article,li,section');var out=[];" +
                "for(var i=0;i<all.length;i++){" +
                "var e=all[i],txt=norm(e.innerText);" +
                "if(!txt||txt.length>2200||e.offsetWidth<180||!hasAll(txt,sig))continue;" +
                "var childMatch=false;" +
                "for(var j=0;j<e.children.length;j++){var ct=norm(e.children[j].innerText);if(ct&&ct.length<txt.length&&hasAll(ct,sig)){childMatch=true;break;}}" +
                "if(!childMatch)out.push(e);" +
                "}" +
                "return out;" +
                "}" +
                "function clickProxy(original,label){" +
                "var b=document.createElement('button');b.textContent=label;b.type='button';" +
                "if((label||'').toLowerCase().indexOf('done')>=0){b.style.background='#39a844';b.style.color='#fff';b.style.border='1px solid #39a844';}" +
                "else{b.style.background='#fff';b.style.color='#238636';b.style.border='1px solid #d1d5db';}" +
                "b.onclick=function(){try{original.click();}catch(x){}};return b;" +
                "}" +
                "function compactTask(card){" +
                "if(card.dataset.lpplCompact==='task')return;" +
                "var a=lines(card),rid=after(a,'RECURRING ID'),desc=after(a,'TASK DESCRIPTION'),cat=after(a,'CATEGORY'),freq=after(a,'FREQUENCY'),date=after(a,'TASK DATE'),status=after(a,'STATUS / RESULT');" +
                "if(!rid||!desc)return;" +
                "var row=document.createElement('div');row.className='lppl-native-list-row';" +
                "var statusClass=(status||'').toLowerCase().replace(/[^a-z]+/g,'-');" +
                "row.innerHTML='<div class=\"lppl-native-row-head\"><div><div class=\"lppl-native-row-id\">Task ID: '+rid+'</div><div class=\"lppl-native-row-title\"></div></div><span class=\"lppl-native-chip '+statusClass+'\"></span></div><div class=\"lppl-native-row-meta\"></div><div class=\"lppl-native-row-actions\"></div>';" +
                "row.querySelector('.lppl-native-row-title').textContent=desc;" +
                "row.querySelector('.lppl-native-chip').textContent=status||'Pending';" +
                "var meta=[];if(date)meta.push(date);if(cat)meta.push(cat);if(freq)meta.push(freq);" +
                "row.querySelector('.lppl-native-row-meta').textContent=meta.join(' • ');" +
                "var acts=row.querySelector('.lppl-native-row-actions');" +
                "var btns=card.querySelectorAll('button');for(var i=0;i<btns.length;i++){var bt=norm(btns[i].innerText);if(bt&&bt.length<30)acts.appendChild(clickProxy(btns[i],bt));}" +
                "if(!acts.children.length)acts.style.display='none';" +
                "card.parentNode.insertBefore(row,card);card.classList.add('lppl-native-original-hidden');card.dataset.lpplCompact='task';" +
                "}" +
                "function compactTicket(card){" +
                "if(card.dataset.lpplCompact==='ticket')return;" +
                "var a=lines(card),id=after(a,'TICKET ID'),desc=after(a,'DESCRIPTION'),dept=after(a,'DEPARTMENT / CATEGORY'),raised=after(a,'RAISED BY'),urg=after(a,'URGENCY'),status=after(a,'STATUS');" +
                "if(!id||!desc)return;" +
                "var row=document.createElement('div');row.className='lppl-native-list-row';" +
                "var statusClass=(status||'').toLowerCase().replace(/[^a-z]+/g,'-');" +
                "row.innerHTML='<div class=\"lppl-native-row-head\"><div><div class=\"lppl-native-row-id\">'+id+'</div><div class=\"lppl-native-row-title\"></div></div><span class=\"lppl-native-chip '+statusClass+'\"></span></div><div class=\"lppl-native-row-meta\"></div><div class=\"lppl-native-row-actions\"></div>';" +
                "row.querySelector('.lppl-native-row-title').textContent=desc;" +
                "row.querySelector('.lppl-native-chip').textContent=status||'';" +
                "var meta=[];if(dept)meta.push(dept);if(urg)meta.push(urg);if(raised)meta.push(raised);" +
                "row.querySelector('.lppl-native-row-meta').textContent=meta.join(' • ');" +
                "var acts=row.querySelector('.lppl-native-row-actions');" +
                "var btns=card.querySelectorAll('button');for(var i=0;i<btns.length;i++){var bt=norm(btns[i].innerText);if(bt&&bt.length<30)acts.appendChild(clickProxy(btns[i],bt));}" +
                "if(!acts.children.length)acts.style.display='none';" +
                "card.parentNode.insertBefore(row,card);card.classList.add('lppl-native-original-hidden');card.dataset.lpplCompact='ticket';" +
                "}" +
                "function collapseFilters(){" +
                "if(document.querySelector('.lppl-native-filter-toggle'))return;" +
                "var all=document.querySelectorAll('div,section,form');var best=null,bestLen=999999;" +
                "for(var i=0;i<all.length;i++){" +
                "var t=norm(all[i].innerText),u=t.toUpperCase();" +
                "if(u.indexOf('DEPARTMENT')>=0&&u.indexOf('RAISED BY')>=0&&u.indexOf('URGENCY')>=0&&u.indexOf('FROM DATE')>=0&&u.indexOf('TO DATE')>=0&&u.indexOf('SEARCH')>=0&&t.length<bestLen&&all[i].querySelector('input,select')){best=all[i];bestLen=t.length;}" +
                "}" +
                "if(!best)return;" +
                "best.classList.add('lppl-native-filter-box');best.style.display='none';" +
                "var b=document.createElement('button');b.className='lppl-native-filter-toggle';b.type='button';b.textContent='Filters ▾';" +
                "b.onclick=function(){var open=best.style.display==='none';best.style.display=open?'block':'none';b.textContent=open?'Filters ▴':'Filters ▾';};" +
                "best.parentNode.insertBefore(b,best);" +
                "}" +
                "function enhance(){" +
                "var taskCards=smallestCard(['RECURRING ID','TASK DESCRIPTION','TASK DATE','STATUS / RESULT']);for(var i=0;i<taskCards.length;i++)compactTask(taskCards[i]);" +
                "var ticketCards=smallestCard(['TICKET ID','CREATED / DUE','DESCRIPTION','DEPARTMENT / CATEGORY','STATUS']);for(var j=0;j<ticketCards.length;j++)compactTicket(ticketCards[j]);" +
                "collapseFilters();" +
                "}" +
                "enhance();" +
                "if(!window.__lpplCompactTimer){window.__lpplCompactTimer=setInterval(enhance,1200);}" +
                "}catch(e){}" +
                "})();";
        webView.evaluateJavascript(js, null);
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
