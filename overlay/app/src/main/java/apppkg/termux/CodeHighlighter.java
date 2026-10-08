package __APP_PKG__.termux;

import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.widget.EditText;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Very small, dependency-free syntax colouring for the GUI editor. */
public class CodeHighlighter implements TextWatcher {

    private static final int C_KEYWORD = 0xFFFF7B72;
    private static final int C_STRING  = 0xFFA5D6FF;
    private static final int C_COMMENT = 0xFF7EE787;
    private static final int C_NUMBER  = 0xFFD2A8FF;

    private static final Pattern COMMENT = Pattern.compile(
            "(?m)#[^\\n]*|//[^\\n]*|<!--[\\s\\S]*?-->");
    private static final Pattern STRING = Pattern.compile(
            "\"[^\"\\n]*\"|'[^'\\n]*'");
    private static final Pattern KEYWORD = Pattern.compile(
            "\\b(def|class|import|from|return|if|elif|else|for|while|in|and|or|not|None|True|False|"
            + "try|except|finally|with|as|lambda|pass|break|continue|global|nonlocal|yield|async|await|"
            + "self|print|input|range|len|int|str|float|list|dict|set|tuple|function|var|let|const|"
            + "new|this|typeof|instanceof|do|switch|case|default|throw|catch|void|null|true|false|"
            + "undefined|public|private|protected|static|extends|implements|interface)\\b");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d+(\\.\\d+)?\\b");

    private final EditText mEditor;
    private boolean mBusy = false;

    public CodeHighlighter(EditText editor) {
        mEditor = editor;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(final Editable s) {
        if (mBusy) return;
        mBusy = true;
        try {
            int len = s.length();
            if (len > 60000) return; // keep it responsive on very large files
            for (ForegroundColorSpan sp : s.getSpans(0, len, ForegroundColorSpan.class))
                s.removeSpan(sp);
            String text = s.toString();
            span(s, COMMENT, text, C_COMMENT);
            span(s, STRING, text, C_STRING);
            span(s, KEYWORD, text, C_KEYWORD);
            span(s, NUMBER, text, C_NUMBER);
        } catch (Exception ignored) {
        } finally {
            mBusy = false;
        }
    }

    private static void span(Editable s, Pattern p, String text, int color) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            if (m.start() >= m.end()) continue;
            s.setSpan(new ForegroundColorSpan(color), m.start(), m.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
