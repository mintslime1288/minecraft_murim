# MurimRPG Windows Launcher architecture

The launcher uses Windows PowerShell WPF plus IExpress, both already present on the development PC. This avoids installing Electron, Tauri, Rust, Node package tooling, or .NET solely to create a first editable Windows executable.

The source is under `launcher/windows/`. The IExpress output is an EXE wrapper which extracts and launches the editable PowerShell UI. It validates the official Java Edition launcher profile and manifest file hashes, then opens the official launcher. It never captures account credentials.

The packaging method is suitable for internal testing. A public release should move to a signed Tauri/Electron/.NET launcher after a release host, update service, and code-signing plan are approved.
