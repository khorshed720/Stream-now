# Setup Instructions

## 1. Google Cloud Console & Firebase Setup
To use Google Sign-In and the YouTube Data API, you must configure a Firebase Project and enable the required APIs.

1. Go to the [Firebase Console](https://console.firebase.google.com/).
2. Create a new project or select an existing one.
3. Go to **Project Settings** > **General** > **Your apps**, and add an Android app.
   - Set the Android package name to: `com.aistudio.rtmpstreamer.xmopq`
4. Download the `google-services.json` file and place it in the `app/` directory of this project.
5. In the Firebase Console, go to **Authentication** > **Sign-in method** and enable **Google Sign-In**.

## 2. YouTube Data API Setup
1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Select the project you just created in Firebase.
3. Go to **APIs & Services** > **Library**.
4. Search for **YouTube Data API v3** and click **Enable**.
5. Go to **APIs & Services** > **OAuth consent screen** and configure it. You must add the scope: `https://www.googleapis.com/auth/youtube`
6. Go to **APIs & Services** > **Credentials**.
7. Find the **Web client (auto created by Google Service)** OAuth 2.0 Client ID. 
8. Copy that Client ID.

## 3. Configuring the App
1. Create a `.env` file in the root of the project (if it doesn't exist).
2. Add the Client ID you copied in step 2.7 to the `.env` file like this:
```
WEB_CLIENT_ID=YOUR_WEB_CLIENT_ID_HERE
```
3. Sync the project and run the app. The app will use this Client ID to securely request an OAuth token for YouTube API access via Google Sign-In.

## Note on Testing
- Ensure your YouTube channel is verified and enabled for live streaming.
- If your OAuth App is in "Testing" mode in Google Cloud Console, you must add your Google Account email to the "Test users" list.
