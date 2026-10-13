	// --- fused with Termux: copy the current file into the Termux home directory ---
	addCommand({
		name: "saveToTermux",
		description: "Save to Termux",
		readOnly: true,
		requiresView: false,
		async run() {
			try {
				const file =
					editorManager?.getActiveFile?.() || editorManager?.activeFile;
				if (!file) {
					toast("No file is open");
					return false;
				}
				const filename = file.filename || file.name || "untitled.txt";
				const doc = file.session?.doc;
				const text =
					doc && typeof doc.toString === "function" ? doc.toString() : "";
				const homeDir = cordova.file.dataDirectory + "home/";
				const target = homeDir + filename;
				try {
					if (!(await fsOperation(homeDir).exists())) {
						await fsOperation(cordova.file.dataDirectory).createDirectory(
							"home",
						);
					}
				} catch (e) {
					/* the directory may already exist */
				}
				await fsOperation(target).writeFile(text, "utf-8");
				toast("Saved to Termux: " + filename);
				return true;
			} catch (err) {
				toast(
					"Save to Termux failed: " +
						(err && err.message ? err.message : err),
				);
				return false;
			}
		},
	});
