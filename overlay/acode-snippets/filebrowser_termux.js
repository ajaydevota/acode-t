
					// --- fused with Termux: the Termux terminal's own home directory ---
					try {
						const termuxHomeUrl = cordova.file.dataDirectory + "home";
						if (!(await fsOperation(termuxHomeUrl).exists())) {
							await fsOperation(cordova.file.dataDirectory).createDirectory("home");
						}
						if (!allStorages.find((s) => s.uuid === "termux-home" || s.url === termuxHomeUrl)) {
							util.pushFolder(allStorages, "Termux", termuxHomeUrl, {
								uuid: "termux-home",
							});
						}
					} catch (err) {
						console.error("Termux home storage", err);
					}