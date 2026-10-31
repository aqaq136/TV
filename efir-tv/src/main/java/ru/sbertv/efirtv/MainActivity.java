package ru.sbertv.efirtv;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("content://android.media.tv/channel"));
            startActivity(intent);
        } catch (Exception e) {
            android.widget.Toast.makeText(
                this, "Не удалось открыть эфирное ТВ",
                android.widget.Toast.LENGTH_SHORT
            ).show();
        }
        finish();
    }
}
