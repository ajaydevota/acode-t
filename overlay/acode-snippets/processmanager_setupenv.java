    private void setupEnvironment(Map<String, String> env, boolean useAlpine) {
        env.put("PREFIX", context.getFilesDir().getAbsolutePath());
        env.put("NATIVE_DIR", context.getApplicationInfo().nativeLibraryDir);
        
        TimeZone tz = TimeZone.getDefault();
        env.put("ANDROID_TZ", tz.getID());
        
        env.put("FDROID", String.valueOf(isFdroidBuild()));

        // --- fused with Termux: share the Termux toolchain and home directory so tools
        //     installed with `pkg install ...` in the Termux terminal work here too.
        String filesDir = context.getFilesDir().getAbsolutePath();
        String termuxPrefix = filesDir + "/usr";
        env.put("TERMUX_PREFIX", termuxPrefix);
        env.put("TERMUX_HOME", filesDir + "/home");
        String path = env.get("PATH");
        if (path == null || path.isEmpty()) {
            path = "/system/bin:/system/xbin";
        }
        env.put("PATH", path + ":" + termuxPrefix + "/bin");
        if (!useAlpine) {
            env.put("HOME", filesDir + "/home");
            env.put("TMPDIR", termuxPrefix + "/tmp");
            env.put("LD_LIBRARY_PATH", termuxPrefix + "/lib");
            env.put("LANG", "en_US.UTF-8");
        }

        if (prootDebug) {
            env.put("PROOT_VERBOSE", "2");
        }
    }