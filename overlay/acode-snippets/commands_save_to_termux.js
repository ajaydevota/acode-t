	async "save-to-termux"() {
		// fused with Termux: copy the open file into Termux's home directory
		try {
			const { activeFile } = editorManager;
			if (!activeFile) {
				toast("No file is open");
				return;
			}
			const filename = activeFile.filename || activeFile.name || "untitled.txt";
			const doc = activeFile.session?.doc;
			const text =
				doc && typeof doc.toString === "function" ? doc.toString() : "";

			const base = cordova.file.dataDirectory; // file:///data/data/com.termux/files/
			const homeDir = base + "home/";
			const target = homeDir + filename;

			// make sure Termux's home directory exists
			try {
				if (!(await fsOperation(homeDir).exists())) {
					await fsOperation(base).createDirectory("home");
				}
			} catch (e) {
				/* already there */
			}

			// overwrite if the file already exists, otherwise create it
			try {
				await fsOperation(target).writeFile(text, "utf-8");
			} catch (e) {
				await fsOperation(homeDir).createFile(filename, text);
			}

			toast("Saved to Termux: " + filename);
		} catch (error) {
			const msg =
				(error && (error.message || error.name)) || String(error);
			toast("Save to Termux failed: " + msg);
		}
	},
