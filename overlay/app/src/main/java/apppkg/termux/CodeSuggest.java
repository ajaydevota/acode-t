package __APP_PKG__.termux;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Language aware word suggestions for the `eg` editor.
 *
 * The word pool is built from the language of the file being edited (decided by its extension)
 * plus the identifiers already present in the document, so typing `p` in a .py file offers
 * `print`, `pass`, `pop`, ... while a .html file offers `p`, `pre`, `progress`, ...
 */
public final class CodeSuggest {

    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int MAX_POOL = 900;
    private static final int MAX_SUGGESTIONS = 30;

    private static final String[] PYTHON = {
        "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
        "elif", "else", "except", "False", "finally", "for", "from", "global", "if", "import",
        "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass", "raise", "return",
        "True", "try", "while", "with", "yield",
        "abs", "all", "any", "bin", "bool", "bytes", "callable", "chr", "dict", "dir",
        "divmod", "enumerate", "eval", "filter", "float", "format", "frozenset", "getattr",
        "hasattr", "hash", "hex", "id", "input", "int", "isinstance", "issubclass", "iter",
        "len", "list", "map", "max", "min", "next", "object", "oct", "open", "ord", "pow",
        "print", "range", "repr", "reversed", "round", "set", "setattr", "slice", "sorted",
        "str", "sum", "super", "tuple", "type", "vars", "zip",
        "append", "clear", "copy", "count", "extend", "find", "index", "insert", "items",
        "join", "keys", "lower", "lstrip", "pop", "remove", "replace", "reverse", "rstrip",
        "sort", "split", "startswith", "endswith", "strip", "title", "upper", "values",
        "update", "get", "self", "__init__", "__main__", "main",
        "sys", "os", "json", "math", "random", "time", "datetime", "re", "argparse", "collections"
    };

    private static final String[] JAVASCRIPT = {
        "var", "let", "const", "function", "return", "if", "else", "for", "while", "do",
        "switch", "case", "default", "break", "continue", "new", "this", "typeof",
        "instanceof", "delete", "in", "of", "class", "extends", "super", "static", "get",
        "set", "try", "catch", "finally", "throw", "async", "await", "yield", "import",
        "export", "from", "as", "null", "undefined", "true", "false", "NaN", "Infinity",
        "console", "log", "warn", "error", "document", "window", "alert", "prompt",
        "querySelector", "querySelectorAll", "getElementById", "getElementsByClassName",
        "createElement", "appendChild", "addEventListener", "removeEventListener",
        "setTimeout", "setInterval", "clearTimeout", "clearInterval", "JSON", "parse",
        "stringify", "Object", "Array", "String", "Number", "Boolean", "Math", "Date",
        "Promise", "Map", "Set", "length", "push", "pop", "shift", "unshift", "slice",
        "splice", "concat", "join", "split", "replace", "toUpperCase", "toLowerCase",
        "includes", "indexOf", "forEach", "map", "filter", "reduce", "find", "sort",
        "reverse", "fetch", "then", "innerHTML", "textContent", "style", "classList",
        "value", "checked", "dataset", "localStorage", "sessionStorage"
    };

    private static final String[] HTML = {
        "!DOCTYPE", "html", "head", "body", "title", "meta", "link", "script", "style",
        "base", "div", "span", "p", "pre", "a", "img", "ul", "ol", "li", "dl", "dt", "dd",
        "table", "thead", "tbody", "tfoot", "tr", "td", "th", "caption", "form", "input",
        "button", "select", "option", "optgroup", "textarea", "label", "fieldset",
        "legend", "h1", "h2", "h3", "h4", "h5", "h6", "br", "hr", "header", "footer",
        "nav", "section", "article", "aside", "main", "figure", "figcaption", "video",
        "audio", "source", "track", "canvas", "svg", "iframe", "embed", "object", "param",
        "template", "slot", "class", "id", "href", "src", "alt", "type", "name", "value",
        "placeholder", "action", "method", "rel", "content", "charset", "lang", "onclick",
        "onload", "progress", "picture", "details", "summary", "mark", "time", "output"
    };

    private static final String[] CSS = {
        "color", "background", "background-color", "background-image", "margin",
        "margin-top", "margin-bottom", "margin-left", "margin-right", "padding",
        "padding-top", "padding-bottom", "border", "border-radius", "border-color",
        "font-size", "font-family", "font-weight", "font-style", "line-height",
        "display", "flex", "flex-direction", "justify-content", "align-items", "grid",
        "width", "height", "max-width", "min-height", "position", "absolute", "relative",
        "fixed", "sticky", "top", "left", "right", "bottom", "z-index", "text-align",
        "text-decoration", "gap", "transition", "transform", "opacity", "overflow",
        "box-shadow", "cursor", "content", "visibility", "outline", "letter-spacing"
    };

    private static final String[] GENERIC = {
        "print", "input", "return", "function", "class", "import", "export", "const",
        "let", "var", "if", "else", "for", "while", "break", "continue", "true", "false",
        "null", "None", "True", "False", "def", "self", "new", "this", "try", "catch",
        "except", "finally", "throw", "raise", "public", "private", "static", "void",
        "int", "float", "double", "String", "boolean", "char", "include", "main",
        "echo", "printf", "scanf", "struct", "typedef", "namespace", "using", "package"
    };

    private final LinkedHashSet<String> mPool = new LinkedHashSet<>();
    private final String mLanguage;

    public CodeSuggest(String fileName) {
        mLanguage = detect(fileName);
        addAll(base(mLanguage));
    }

    public String getLanguage() {
        return mLanguage;
    }

    private static String[] base(String language) {
        switch (language) {
            case "python":     return PYTHON;
            case "javascript": return JAVASCRIPT;
            case "html":       return HTML;
            case "css":        return CSS;
            default:           return GENERIC;
        }
    }

    private void addAll(String[] words) {
        for (String w : words) {
            if (mPool.size() >= MAX_POOL) return;
            mPool.add(w);
        }
    }

    /** Language of a file, decided by its extension. */
    public static String detect(String name) {
        if (name == null) return "generic";
        String n = name.toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        String ext = dot >= 0 ? n.substring(dot + 1) : "";
        switch (ext) {
            case "py":
            case "pyw":
                return "python";
            case "js":
            case "mjs":
            case "cjs":
            case "jsx":
            case "ts":
                return "javascript";
            case "html":
            case "htm":
            case "xml":
            case "xhtml":
                return "html";
            case "css":
            case "scss":
            case "less":
                return "css";
            default:
                return "generic";
        }
    }

    /** Remembers identifiers already used in the document. */
    public void learn(CharSequence text) {
        if (text == null || text.length() == 0 || text.length() > 200000) return;
        if (mPool.size() >= MAX_POOL) return;
        Matcher m = IDENT.matcher(text);
        while (m.find()) {
            if (mPool.size() >= MAX_POOL) return;
            String w = m.group();
            if (w.length() >= 3) mPool.add(w);
        }
    }

    /** Words that complete the word currently being typed at {@code cursor}. */
    public List<String> suggest(CharSequence text, int cursor) {
        List<String> out = new ArrayList<>();
        if (text == null || cursor <= 0 || cursor > text.length()) return out;

        int start = cursor;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (Character.isLetterOrDigit(c) || c == '_') start--;
            else break;
        }
        if (start >= cursor) return out;

        String prefix = text.subSequence(start, cursor).toString();
        if (prefix.isEmpty()) return out;
        String lower = prefix.toLowerCase(Locale.ROOT);

        for (String w : mPool) {
            if (w.length() <= prefix.length()) continue;
            if (w.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(w);
                if (out.size() >= MAX_SUGGESTIONS) break;
            }
        }
        return out;
    }
}
