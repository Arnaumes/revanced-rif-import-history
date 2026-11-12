package app.revanced.extension.rifimport;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The "Import history" screen, opened from Settings → Backup.
 * <p>
 * Lets the user pick an export, then shows the progress and result of the import ({@link ImportSession}). It looks
 * like rif's own export/import screen: same theme ({@link RifTheme}), same layout. It is compiled against
 * android.app.Activity; the patch makes it extend rif's base activity, like rif's export/import screen.
 */
public class ImportHistoryActivity extends Activity implements ImportSession.Listener {
    private static final String TAG = "rif import history";
    private static final int REQUEST_CHOOSE_FILE = 1;
    private static final String STATE_AWAITING_FILE = "awaiting_file";
    private static final String STATE_IMPORT_STARTED = "import_started";
    private static final int PROGRESS_MAX = 10000;

    /** Zips and bare SQLite files; file managers report zips with any of these. */
    private static final String[] MIME_TYPES = {
            "application/zip",
            "application/x-zip-compressed",
            "application/x-zip",
            "multipart/x-zip",
            "application/octet-stream",
            "application/x-sqlite3",
            "application/vnd.sqlite3",
    };

    private ProgressBar progress;
    private ProgressBar indeterminateProgress;
    private TextView text;
    private Button okButton;

    /** The file picker is open; its result may arrive even after this screen was recreated. */
    private boolean awaitingFile;
    /** This screen started an import (kept across recreation, to tell a lost import from a fresh start). */
    private boolean importStarted;
    private ImportSession session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(theme());
        super.onCreate(savedInstanceState);
        setTitle(ImportMessages.TITLE);
        setUpViews();

        if (savedInstanceState == null) {
            // Opened anew: an old result was seen already. An import still running is shown below.
            ImportSession.clearFinished();
        } else {
            awaitingFile = savedInstanceState.getBoolean(STATE_AWAITING_FILE);
            importStarted = savedInstanceState.getBoolean(STATE_IMPORT_STARTED);
        }

        session = ImportSession.current();
        if (session != null) {
            session.attach(this);
        } else if (importStarted) {
            // The import was running in a process that has since been killed.
            showResult(ImportMessages.INTERRUPTED);
        } else {
            showMessage(ImportMessages.CHOOSE_FILE);
            if (!awaitingFile) chooseFile();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_AWAITING_FILE, awaitingFile);
        outState.putBoolean(STATE_IMPORT_STARTED, importStarted);
    }

    @Override
    protected void onDestroy() {
        if (session != null) session.detach(this);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CHOOSE_FILE) return;
        awaitingFile = false;

        Uri uri = data == null ? null : data.getData();
        if (resultCode != RESULT_OK || uri == null) {
            finish();
            return;
        }

        importStarted = true;
        session = ImportSession.start(this, uri);
        session.attach(this);
    }

    @Override
    public void onBackPressed() {
        if (session != null && session.isRunning()) {
            Toast.makeText(this, ImportMessages.PLEASE_WAIT, Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }

    /** android:onClick of the OK button in rif's export/import layout. */
    public void onClickOk(View view) {
        ImportSession.clearFinished();
        finish();
    }

    // region ImportSession.Listener

    @Override
    public void onProgress(int processed, int total) {
        showMessage(ImportMessages.IMPORTING);
        if (total <= 0) {
            progress.setVisibility(View.GONE);
            indeterminateProgress.setVisibility(View.VISIBLE);
        } else {
            indeterminateProgress.setVisibility(View.GONE);
            progress.setVisibility(View.VISIBLE);
            progress.setProgress((int) ((long) processed * PROGRESS_MAX / total));
        }
    }

    @Override
    public void onFinished(String message) {
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(PROGRESS_MAX);
        showResult(message);
    }

    // endregion

    private void chooseFile() {
        awaitingFile = true;

        Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, MIME_TYPES);
        Intent getContent = new Intent(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, MIME_TYPES);

        for (Intent intent : new Intent[]{open, getContent}) {
            try {
                startActivityForResult(intent, REQUEST_CHOOSE_FILE);
                return;
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "No activity for " + intent.getAction(), e);
            }
        }
        awaitingFile = false;
        showResult(ImportMessages.NO_FILE_PICKER);
    }

    // region Views

    /**
     * rif's current theme. The fallbacks only matter if rif's theme lookup fails: once patched, this screen is an
     * AppCompat activity and needs an AppCompat theme.
     */
    private int theme() {
        try {
            int theme = RifTheme.activityTheme();
            if (theme != 0) return theme;
        } catch (RuntimeException | LinkageError e) {
            Log.w(TAG, "Could not get rif's theme", e);
        }
        int appCompat = resource("Theme.AppCompat.DayNight", "style");
        if (appCompat != 0) return appCompat;

        int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES
                ? android.R.style.Theme_DeviceDefault
                : android.R.style.Theme_DeviceDefault_Light;
    }

    /** rif's own export/import layout, or the same views built here if it isn't found. */
    private void setUpViews() {
        int layout = resource("activity_settings_export_import", "layout");
        if (layout != 0) {
            setContentView(layout);
            progress = findViewById(resource("progress", "id"));
            indeterminateProgress = findViewById(resource("indeterminate_progress", "id"));
            text = findViewById(resource("text", "id"));
            okButton = findViewById(resource("ok_button", "id"));
        }
        if (progress == null || indeterminateProgress == null || text == null || okButton == null) {
            buildViews();
        }

        progress.setMax(PROGRESS_MAX);
        progress.setProgress(0);
        progress.setVisibility(View.GONE);
        indeterminateProgress.setVisibility(View.GONE);
        okButton.setVisibility(View.GONE);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onClickOk(view);
            }
        });
    }

    private void buildViews() {
        int padding = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(padding, padding, padding, padding);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(false);
        indeterminateProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        indeterminateProgress.setIndeterminate(true);

        text = new TextView(this);
        text.setTextAppearance(this, android.R.style.TextAppearance_Medium);

        okButton = new Button(this);
        okButton.setText(android.R.string.ok);

        ViewGroup.LayoutParams fullWidth = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(progress, fullWidth);
        root.addView(indeterminateProgress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams wrap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wrap.topMargin = dp(8);
        root.addView(text, wrap);
        root.addView(okButton, new LinearLayout.LayoutParams(wrap));
        setContentView(root);
    }

    private void showMessage(String message) {
        text.setText(message);
    }

    private void showResult(String message) {
        indeterminateProgress.setVisibility(View.GONE);
        text.setText(message);
        okButton.setVisibility(View.VISIBLE);
    }

    private int resource(String name, String type) {
        return getResources().getIdentifier(name, type, getPackageName());
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    // endregion
}
