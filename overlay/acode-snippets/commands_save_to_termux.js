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
			const homeDir = cordova.file.dataDirectory + "home/";
			const target = homeDir + filename;
			try {
				if (!(await fsOperation(homeDir).exists())) {
					await fsOperation(cordova.file.dataDirectory).createDirectory("home");
				}
			} catch (e) {
				/* the directory may already exist */
			}
			await fsOperation(target).writeFile(text, "utf-8");
			toast("Saved to Termux: " + filename);
		} catch (error) {
			helpers.error(error);
		}
	},
