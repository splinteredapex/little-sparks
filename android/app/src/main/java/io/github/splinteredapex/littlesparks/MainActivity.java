package io.github.splinteredapex.littlesparks;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Little Sparks: a full-screen shell around the games.
 * It loads the online version (so updates arrive on their own), falls back to the copy
 * built into the app when there is no connection, and reads questions aloud with the
 * tablet's own voice.
 */
public class MainActivity extends Activity {
    private static final String ONLINE = "https://splinteredapex.github.io/little-sparks/?app=1";
    private static final String OFFLINE = "file:///android_asset/index.html";

    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private boolean usedFallback = false;
    private volatile String wantVoice = "";
    private volatile float wantRate = 0.95f;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#151b2b"));
        w.setNavigationBarColor(Color.parseColor("#151b2b"));
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.UK);
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.US);
                }
                tts.setPitch(1.0f);
                tts.setSpeechRate(wantRate);
                useVoice(wantVoice);
                ttsReady = true;
            }
        });

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#151b2b"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        web.addJavascriptInterface(new Speech(), "LittleSparksTTS");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String host = req.getUrl().getHost();
                // stay inside Little Sparks: nothing opens other websites
                return host != null && !host.equals("splinteredapex.github.io")
                        && !host.equals("fonts.googleapis.com") && !host.equals("fonts.gstatic.com");
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame() && !usedFallback) {
                    usedFallback = true;
                    view.loadUrl(OFFLINE);
                }
            }
        });
        setContentView(web);
        hideBars();
        if (state != null) web.restoreState(state);
        else web.loadUrl(ONLINE);
    }

    private void hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    /** Back goes to the home screen inside the app; on the home screen it leaves the app. */
    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tts != null) tts.stop();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        hideBars();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        if (web != null) web.destroy();
        super.onDestroy();
    }

    /** How natural a voice is likely to sound: higher quality first, British English preferred, offline preferred. */
    private static int score(Voice v) {
        Locale l = v.getLocale();
        if (l == null || !"en".equals(l.getLanguage())) return -1;
        Set<String> f = v.getFeatures();
        if (f != null && f.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) return -1;
        int s = v.getQuality();
        if ("GB".equals(l.getCountry())) s += 60;
        if (!v.isNetworkConnectionRequired()) s += 20;
        return s;
    }

    private List<Voice> rankedVoices() {
        List<Voice> out = new ArrayList<>();
        try {
            Set<Voice> all = tts.getVoices();
            if (all != null) for (Voice v : all) if (score(v) >= 0) out.add(v);
        } catch (Exception ignored) { }
        Collections.sort(out, (a, b) -> score(b) - score(a));
        return out;
    }

    /** Use the named voice, or the most natural one when the name is empty or not found. */
    private void useVoice(String name) {
        try {
            List<Voice> vs = rankedVoices();
            if (vs.isEmpty()) return;
            Voice pick = vs.get(0);
            if (name != null && !name.isEmpty()) for (Voice v : vs) if (v.getName().equals(name)) { pick = v; break; }
            tts.setVoice(pick);
        } catch (Exception ignored) { }
    }

    /** The games call window.LittleSparksTTS.speak(text) to read questions aloud. */
    private class Speech {
        @JavascriptInterface
        public boolean ready() { return ttsReady; }

        @JavascriptInterface
        public void speak(String text) {
            if (ttsReady && text != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "q");
        }

        @JavascriptInterface
        public void stop() { if (ttsReady) tts.stop(); }

        @JavascriptInterface
        public String voices() {
            if (!ttsReady) return "[]";
            StringBuilder sb = new StringBuilder("[");
            int i = 0;
            for (Voice v : rankedVoices()) {
                if (i >= 8) break;
                Locale l = v.getLocale();
                String c = "GB".equals(l.getCountry()) ? "British" : ("US".equals(l.getCountry()) ? "American" : l.getDisplayCountry(Locale.UK));
                String label = c + " " + (i + 1) + (v.getQuality() >= 400 ? " (clear)" : "") + (v.isNetworkConnectionRequired() ? " (online)" : "");
                if (i > 0) sb.append(',');
                sb.append("{\"name\":\"").append(v.getName().replace("\\", "").replace("\"", ""))
                  .append("\",\"label\":\"").append(label.replace("\"", "")).append("\"}");
                i++;
            }
            return sb.append("]").toString();
        }

        @JavascriptInterface
        public void setVoice(String name) {
            wantVoice = name == null ? "" : name;
            if (ttsReady) runOnUiThread(() -> useVoice(wantVoice));
        }

        @JavascriptInterface
        public void setRate(float r) {
            wantRate = Math.max(0.5f, Math.min(1.5f, r));
            if (ttsReady) tts.setSpeechRate(wantRate);
        }

        @JavascriptInterface
        public String voiceName() {
            try { return ttsReady && tts.getVoice() != null ? tts.getVoice().getName() : ""; }
            catch (Exception e) { return ""; }
        }
    }
}
