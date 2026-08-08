# arXiv Paper Finder — GitHub Pages site

This folder is ready to publish with GitHub Pages.

## 1. Put your APK here

Build the APK in Android Studio and copy it into this folder as:

```text
arxiv-paper-finder.apk
```

The website already links to that filename.

For a test build, Android Studio usually creates:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Rename the copied file to:

```text
arxiv-paper-finder.apk
```

## 2. Upload this folder to a GitHub repository

The repository root should look like:

```text
index.html
app-icon.png
app-screenshot.png
arxiv-paper-finder.apk
README.md
```

`PUT_YOUR_APK_HERE.txt` can be deleted after you add the APK.

## 3. Enable GitHub Pages

On GitHub:

1. Open the repository.
2. Go to **Settings → Pages**.
3. Under **Build and deployment**, select **Deploy from a branch**.
4. Choose `main`.
5. Choose `/ (root)`.
6. Click **Save**.

GitHub will show the public URL after deployment.

## Updating the app

Replace `arxiv-paper-finder.apk` with the new APK and change the visible
version in `index.html`:

```html
<span class="version">Version 1.0</span>
```

Then commit/push the changes.
