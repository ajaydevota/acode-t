package __APP_PKG__.termux;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import __APP_PKG__.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;

/**
 * GUI code editor opened by the `eg <filename>` command from the Termux terminal.
 * Has a Save button, a Back button, an enlarged symbol key row and language aware suggestions.
 */
public class EditorActivity extends Activity {

    private EditText mEditor;
    private HorizontalScrollView mSuggestBar;
    private LinearLayout mSuggestRow;
    private CodeSuggest mSuggest;

    private String mPath;
    private String mName = "untitled";
    private int mLearnedLength = -1;
    private float mDensity = 1f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        mDensity = getResources().getDisplayMetrics().density;

        String p = getIntent().getStringExtra("file");
        if (p != null && p.startsWith("/")) {
            mPath = p;
            mName = new File(p).getName();
        } else if (p != null && !p.isEmpty()) {
            mName = p;
        }

        mSuggest = new CodeSuggest(mName);

        mEditor = (EditText) findViewById(R.id.editorText);
        mEditor.setTypeface(Typeface.MONOSPACE);
        mEditor.setTextSize(14f);
        mEditor.setGravity(Gravity.TOP | Gravity.START);
        mEditor.setHorizontallyScrolling(true);
        mEditor.setBackgroundColor(0xFF1E1E1E);
        mEditor.setTextColor(0xFFE6E6E6);

        mSuggestBar = (HorizontalScrollView) findViewById(R.id.suggestBar);
        mSuggestRow = (LinearLayout) findViewById(R.id.suggestRow);

        ((TextView) findViewById(R.id.fileName)).setText(mName);

        findViewById(R.id.backBtn).setOnClickListener(v -> {
            save();
            finish();
        });
        findViewById(R.id.saveButton).setOnClickListener(v -> {
            if (save())
                Toast.makeText(this, "सेव हो गया: " + mName, Toast.LENGTH_SHORT).show();
        });

        buildKeys((LinearLayout) findViewById(R.id.keyRow));

        mEditor.setText(readFile());
        mEditor.setSelection(mEditor.getText().length());
        mSuggest.learn(mEditor.getText());
        mLearnedLength = mEditor.getText().length();

        mEditor.addTextChangedListener(new CodeHighlighter(mEditor));
        mEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateSuggestions();
            }
        });
    }

    // ------------------------------------------------------------------ suggestions

    private void updateSuggestions() {
        try {
            CharSequence text = mEditor.getText();
            int cursor = mEditor.getSelectionStart();

            if (text.length() != mLearnedLength) {
                mSuggest.learn(text);
                mLearnedLength = text.length();
            }

            List<String> items = mSuggest.suggest(text, cursor);
            if (items.isEmpty()) {
                mSuggestBar.setVisibility(View.GONE);
                mSuggestRow.removeAllViews();
                return;
            }

            mSuggestRow.removeAllViews();
            for (final String word : items) {
                TextView chip = new TextView(this);
                chip.setText(word);
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
                chip.setTextColor(0xFFE6E6E6);
                chip.setPadding(dp(18), dp(12), dp(18), dp(12));
                chip.setBackground(chipBackground());
                chip.setOnClickListener(v -> applySuggestion(word));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(dp(4), 0, dp(4), 0);
                mSuggestRow.addView(chip, lp);
            }
            mSuggestBar.setVisibility(View.VISIBLE);
        } catch (Exception ignored) {
        }
    }

    private void applySuggestion(String word) {
        try {
            CharSequence text = mEditor.getText();
            int cursor = Math.max(mEditor.getSelectionStart(), 0);
            int start = cursor;
            while (start > 0) {
                char c = text.charAt(start - 1);
                if (Character.isLetterOrDigit(c) || c == '_') start--;
                else break;
            }
            mEditor.getText().replace(start, cursor, word);
            mEditor.setSelection(start + word.length());
        } catch (Exception ignored) {
        }
    }

    private GradientDrawable chipBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFF2D2D30);
        d.setCornerRadius(dp(18));
        return d;
    }

    private int dp(float value) {
        return (int) (value * mDensity + 0.5f);
    }

    // ------------------------------------------------------------------ file io

    private String readFile() {
        if (mPath == null) return "";
        File f = new File(mPath);
        if (!f.exists() || !f.isFile()) return "";
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return "";
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {
            }
        }
    }

    private boolean save() {
        if (mPath == null) return false;
        FileOutputStream out = null;
        try {
            File f = new File(mPath);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            out = new FileOutputStream(f);
            out.write(mEditor.getText().toString().getBytes("UTF-8"));
            out.flush();
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        } finally {
            try {
                if (out != null) out.close();
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ key row

    private void buildKeys(LinearLayout row) {
        String[] keys = {"(", ")", "[", "]", "{", "}", ":", "=", "+", "-", "*", "/",
                         "%", "\"", "'", "#", "_", ".", ",", "<", ">", "Tab"};
        for (final String k : keys) {
            Button b = new Button(this);
            b.setText(k);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
            b.setAllCaps(false);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setMinimumHeight(0);
            b.setPadding(dp(20), dp(14), dp(20), dp(14));
            b.setOnClickListener(v -> insertKey(k));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(4), 0, dp(4), 0);
            row.addView(b, lp);
        }
    }

    private void insertKey(String k) {
        int start = Math.max(mEditor.getSelectionStart(), 0);
        int end = Math.max(mEditor.getSelectionEnd(), start);
        String text = k.equals("Tab") ? "    " : k;
        mEditor.getText().replace(start, end, text);
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }
}
