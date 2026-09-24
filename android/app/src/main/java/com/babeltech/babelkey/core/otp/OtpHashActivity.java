package com.babeltech.babelkey.core.otp;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.babeltech.babelkey.R;

/**
 * OtpHashActivity — displays the 11-char app hash for SMS Retriever API.
 * Provider must include this hash in OTP SMS: "<#> ... 123456 ... <hash>".
 */
public class OtpHashActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setPadding(32,32,32,32);
        String hash = SmsRetrieverHelper.INSTANCE.getAppHash(this);
        String sample = hash != null ? SmsRetrieverHelper.INSTANCE.sampleSms("123456", hash) : "Hash generation failed — ensure Play Services available and app is signed.";
        tv.setText("BabelKey OTP App Hash:\n" + (hash != null ? hash : "(unavailable)") + "\n\nSample SMS format:\n" + sample);
        setContentView(tv);
    }
}
