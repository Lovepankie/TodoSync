# TodoSync — Setup Guide

## Prerequisites
- Android Studio Ladybug (2024.2) or newer
- JDK 11+
- A Google account

---

## Step 1 — Create a Google Cloud Project

1. Go to [console.cloud.google.com](https://console.cloud.google.com)
2. Click **New Project**, give it a name (e.g. "TodoSync"), click **Create**
3. Select your new project from the top dropdown

---

## Step 2 — Enable the required APIs

In the left sidebar → **APIs & Services** → **Library**, search for and **Enable** each of:

| API | Purpose |
|-----|---------|
| **Google Tasks API** | Sync Tasks |
| **Google Calendar API** | Sync Events |
| **Gmail API** | Sync Reminders as drafts |

---

## Step 3 — Configure the OAuth Consent Screen

1. Go to **APIs & Services** → **OAuth consent screen**
2. Choose **External**, click **Create**
3. Fill in: App name = `TodoSync`, User support email = your email
4. Add scopes:
   - `https://www.googleapis.com/auth/tasks`
   - `https://www.googleapis.com/auth/calendar`
   - `https://www.googleapis.com/auth/gmail.compose`
5. Add your Google account as a **Test User**
6. Save

---

## Step 4 — Create OAuth 2.0 Credentials

### Android client (for the app)
1. **APIs & Services** → **Credentials** → **Create Credentials** → **OAuth client ID**
2. Application type: **Android**
3. Package name: `com.todoapp`
4. SHA-1 fingerprint — get it by running in a terminal:
   ```bash
   # Debug keystore (for development)
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
   Copy the **SHA1** value
5. Click **Create**, note the **Client ID**

### Web client (required for token validation on device)
1. **Create Credentials** → **OAuth client ID**
2. Application type: **Web application**
3. Click **Create**, note the **Web Client ID** — you will use this in the app

---

## Step 5 — Download google-services.json

1. Go to **APIs & Services** → **Credentials**
   OR via Firebase Console if you added Firebase
2. Download the `google-services.json` for your Android app
3. Replace `app/google-services.json` with the downloaded file

> The placeholder file already exists at `app/google-services.json`.
> Replace it entirely with the real one from Google Cloud / Firebase Console.

---

## Step 6 — Update the Web Client ID in strings.xml

Open `app/src/main/res/values/strings.xml` and replace:

```xml
<string name="google_client_id" translatable="false">YOUR_WEB_CLIENT_ID_HERE.apps.googleusercontent.com</string>
```

with your actual Web Client ID.

---

## Step 7 — Build & Run

1. Open the project root (`To_do_list/`) in Android Studio
2. Let Gradle sync finish (it downloads all dependencies automatically)
3. Connect an Android 9+ device or create an emulator (API 28+)
4. Click **Run**

---

## How it works

| Item Type | Local Storage | Google Service |
|-----------|--------------|----------------|
| **Task**     | Room DB      | Google Tasks API — "My Tasks" list |
| **Event**    | Room DB      | Google Calendar API — primary calendar |
| **Reminder** | Room DB      | Gmail API — saved as a draft |

Sync happens:
- **Immediately** after creating/editing/deleting an item (one-off WorkManager job)
- **Every 30 minutes** in the background (periodic WorkManager job)
- **On demand** via the sync icon in the toolbar

---

## Project Structure

```
app/src/main/kotlin/com/todoapp/
├── MainActivity.kt              # Entry point
├── TodoApplication.kt           # Hilt + WorkManager init
├── data/
│   ├── local/                   # Room database, DAO, entities
│   ├── remote/google/           # Google Tasks / Calendar / Gmail API clients
│   └── repository/              # Repository implementations
├── di/                          # Hilt dependency injection modules
├── domain/
│   ├── model/                   # TodoItem, TodoType, Priority, SyncStatus
│   └── repository/              # Repository interface
├── sync/
│   ├── SyncManager.kt           # WorkManager scheduling
│   └── SyncWorker.kt            # Background sync logic
└── ui/
    ├── navigation/              # Compose Navigation graph
    ├── screens/                 # MainScreen, AddEditItemScreen
    ├── components/              # TodoItemCard
    ├── theme/                   # Material 3 theme
    └── viewmodel/               # MainViewModel, AddEditViewModel
```
