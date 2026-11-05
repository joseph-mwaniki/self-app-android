# How to Open and Run SelfApp in Android Studio

This guide outlines how to import this codebase into Android Studio, compile it, deploy it to a physical device or emulator, and grant the required system-level permissions.

---

## 1. Importing the Project

### If you are using Android Studio on Windows (WSL environment)
1. Open Android Studio on your Windows machine.
2. Click **File** -> **Open**.
3. In the path bar, enter the WSL network path where this workspace is located:
   `\\wsl$\Ubuntu-22.04\home\jose\self app`
   *(Replace `Ubuntu-22.04` with your active WSL distribution name if different).*
4. Select the `self app` directory and click **OK**.
5. Click **Trust Project** if prompted.

### If you are using Android Studio directly on Linux
1. Open Android Studio.
2. Click **File** -> **Open**.
3. Browse to `/home/jose/self app`.
4. Click **OK** and trust the project.

---

## 2. Syncing and Building
Once imported, Android Studio will automatically recognize the Gradle build files (`build.gradle.kts` and `settings.gradle.kts`) and begin syncing.
- If it doesn't sync automatically, click the **Sync Project with Gradle Files** elephant icon in the top right corner.
- The build targets **SDK 34** and requires **JDK 17**. If Android Studio complains about Java versions, go to **Settings/Preferences** -> **Build, Execution, Deployment** -> **Build Tools** -> **Gradle**, and set the **Gradle JDK** to **JDK 17** (or point it to the local `/home/jose/jdk-17.0.12` folder).

---

## 3. Running the App
1. Connect a physical Android device via USB (with **Developer Options** and **USB Debugging** enabled) or start a Virtual Device (Emulator).
2. Select the `app` configuration in the toolbar dropdown.
3. Click the green **Run** (Play) button or press `Shift + F10`.
4. The app will compile and install.

---

## 4. Granting Blocker Permissions
For the advanced self-discipline features to function, you must enable the following permissions in your device settings:

### A. App Lock Blocker (Accessibility Service)
- **What it does**: Tracks foreground package changes and locks selected apps (e.g. Instagram).
- **How to enable**:
  1. Open the app and go to the **App Lock** sidebar screen.
  2. Tap the **Required Permissions** banner. This opens the system Accessibility page.
  3. Under **Installed Services** or **Downloaded Apps**, select **SelfApp**.
  4. Toggle the service **ON**.

### B. Notification Blocker (Notification Listener Access)
- **What it does**: Silences incoming notification banners from locked apps.
- **How to enable**:
  1. Go to your device **Settings** -> search for **Device & App Notifications** (or **Notification Access**).
  2. Locate **SelfApp** in the list.
  3. Toggle **Allow Notification Access** to **ON**.

### C. DNS Blocker (VPN Service)
- **What it does**: Intercepts UDP port 53 DNS queries locally to block domains.
- **How to enable**:
  1. Go to the **DNS Blocker** screen in the sidebar.
  2. Toggle the **VPN Interceptor Service** switch **ON**.
  3. Press **OK** on the system VPN confirmation dialog.

### D. Overlay Lock Screen (System Alert Window)
- **What it does**: Renders the fullscreen neon-red "Access Denied" countdown overlay.
- **How to enable**:
  1. Go to device **Settings** -> search for **Display over other apps** (or **Draw over other apps**).
  2. Select **SelfApp**.
  3. Toggle **Allow display over other apps** to **ON**.

---

## 5. Setting up the LLM Transformation & Grading Engine
1. Go to the **Settings** screen in the sidebar.
2. Select your AI Provider (**Gemini**, **OpenAI**, or **Anthropic**).
3. Paste your **API Key**.
4. Input the desired model name (e.g. `gemini-1.5-flash`, `gpt-4o`, `claude-3-5-sonnet-20240620`).
5. (Optional) Customize the prompt templates. If you are using a local API mock server or gateway, specify it in the **Custom Base URL** field.
6. Click **Save API Credentials**.
   *(Note: If you leave the API key blank, the app runs in **Offline/Fallback mode** where it generates local mock transformations and mock auto-grades to let you preview the app without consuming API credits).*
