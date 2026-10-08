package __APP_PKG__.termux;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import java.io.File;

import __APP_PKG__.R;

/**
 * Real Termux terminal (termux terminal-view + terminal-emulator, native PTY).
 * Launcher screen of the fused app. `eg <file>` opens the simple editor and
 * `acode-t` brings the full Acode editor to the front.
 */
public class TermuxTerminalActivity extends Activity
        implements TerminalViewClient, TerminalSessionClient {

    private TerminalView mTerminalView;
    private TerminalSession mSession;

    private boolean ctrlDown, altDown, shiftDown, fnDown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_termux_terminal);

        mTerminalView = (TerminalView) findViewById(R.id.terminalView);
        mTerminalView.setTerminalViewClient(this);
        mTerminalView.setTextSize(20);
        mTerminalView.requestFocus();

        buildExtraKeys((LinearLayout) findViewById(R.id.extraKeys));
        RequestWatcher.start(this);

        mTerminalView.getViewTreeObserver().addOnGlobalLayoutListener(
            new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (mTerminalView.getWidth() > 0 && mTerminalView.getHeight() > 0) {
                        mTerminalView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        new Thread(() -> {
                            TermuxBootstrap.ensure(TermuxTerminalActivity.this);
                            runOnUiThread(TermuxTerminalActivity.this::createSession);
                        }).start();
                    }
                }
            });
    }

    private void createSession() {
        if (mSession != null) return;
        File prefix = TermuxBootstrap.prefix(this);
        File login = new File(prefix, "bin/login");
        String shell = login.exists()
                ? login.getAbsolutePath()
                : new File(prefix, "bin/bash").getAbsolutePath();
        String[] env = TermuxBootstrap.env(this);
        mSession = new TerminalSession(shell, TermuxBootstrap.home(this).getAbsolutePath(),
                new String[]{"-l"}, env, 2000, this);
        mTerminalView.attachSession(mSession);
    }

    private void buildExtraKeys(LinearLayout row) {
        String[] labels = {"ESC", "CTRL", "ALT", "TAB", "\u2190", "\u2191", "\u2193", "\u2192",
                           "-", "/", "|", "~"};
        for (final String label : labels) {
            Button b = new Button(this);
            b.setText(label);
            b.setTextSize(11f);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setPadding(24, 6, 24, 6);
            b.setOnClickListener(v -> onExtraKey(label));
            row.addView(b);
        }
    }

    private void onExtraKey(String label) {
        switch (label) {
            case "ESC":  send("\u001b"); break;
            case "CTRL": ctrlDown = !ctrlDown; break;
            case "ALT":  altDown = !altDown; break;
            case "TAB":  send("\t"); break;
            case "\u2190": send("\u001b[D"); break;
            case "\u2191": send("\u001b[A"); break;
            case "\u2193": send("\u001b[B"); break;
            case "\u2192": send("\u001b[C"); break;
            default: send(label);
        }
    }

    private void send(String s) {
        TerminalSession s2 = mSession;
        if (s2 != null) {
            byte[] b = s.getBytes();
            s2.write(b, 0, b.length);
        }
        mTerminalView.requestFocus();
    }

    // ---------------- TerminalViewClient ----------------
    @Override public float onScale(float scale) { return scale; }
    @Override public void onSingleTapUp(MotionEvent e) { mTerminalView.requestFocus(); }
    @Override public boolean shouldBackButtonBeMappedToEscape() { return false; }
    @Override public boolean shouldEnforceCharBasedInput() { return true; }
    @Override public boolean shouldUseCtrlSpaceWorkaround() { return false; }
    @Override public boolean isTerminalViewSelected() { return true; }
    @Override public void copyModeChanged(boolean copyMode) { }
    @Override public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) { return false; }
    @Override public boolean onKeyUp(int keyCode, KeyEvent e, TerminalSession session) { return false; }
    @Override public boolean onLongPress(MotionEvent event) { return false; }
    @Override public boolean readControlKey() { return ctrlDown; }
    @Override public boolean readAltKey() { return altDown; }
    @Override public boolean readShiftKey() { return shiftDown; }
    @Override public boolean readFnKey() { return fnDown; }
    @Override public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) { return false; }
    @Override public void onEmulatorSet() { }

    // ---------------- TerminalSessionClient ----------------
    @Override public void onTextChanged(TerminalSession changedSession) { mTerminalView.onScreenUpdated(); }
    @Override public void onTitleChanged(TerminalSession changedSession) { }
    @Override public void onSessionFinished(TerminalSession finishedSession) { }
    @Override public void onBell(TerminalSession session) { }
    @Override public void onColorsChanged(TerminalSession session) { }
    @Override public void onTerminalCursorStateChange(boolean state) { }
    @Override public int getTerminalCursorStyle() { return TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK; }

    @Override
    public void onCopyTextToClipboard(TerminalSession session, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Termux", text));
    }

    @Override
    public void onPasteTextFromClipboard(TerminalSession session) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemCount() == 0) return;
        String text = cm.getPrimaryClip().getItemAt(0).coerceToText(this).toString();
        byte[] b = text.getBytes();
        session.write(b, 0, b.length);
    }

    // ---------------- logging ----------------
    @Override public void logError(String tag, String message) { }
    @Override public void logWarn(String tag, String message) { }
    @Override public void logInfo(String tag, String message) { }
    @Override public void logDebug(String tag, String message) { }
    @Override public void logVerbose(String tag, String message) { }
    @Override public void logStackTraceWithMessage(String tag, String message, Exception e) { }
    @Override public void logStackTrace(String tag, Exception e) { }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSession != null) mSession.finishIfRunning();
    }
}
