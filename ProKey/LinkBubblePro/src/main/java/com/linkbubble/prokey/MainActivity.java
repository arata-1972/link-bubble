package com.linkbubble.prokey;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings.Secure;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.LicenseChecker;
import com.google.android.vending.licensing.LicenseCheckerCallback;
import com.google.android.vending.licensing.ServerManagedPolicy;
import com.linkbubble.util.Tamper;

import java.util.List;

public class MainActivity extends Activity {
    private static final String BASE64_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtnueRL4Kmisinw9S+HKjyY9m28tTSu8nYGbGH5JXQtD1U34YeHUvhgLPmoUD9kah75f2/T0UzABmNqatXCArMl5XZg0wLNaOi0kHKOuCElIkrlGgVI+ZRYH+ihLXXp2K8wbOYo4huie+6CDpYdvQXKf0KxvemWSirRhIrm3r5tJyDviEVX1MD8bxlgOg1O00P+JKJrl8CTIH2MWcTpgxho86aXudxZY/Rmfic5EUhaWbitQO+8Da/9abSQb5Ai9BUo+2rqUG44TAeg8p9Mp4/++WaODRdMUt8rreRDKxmKeFfY042n5P+7GoOAH8fkhprgGRE1vo8dKnPsgVe+uBqwIDAQAB";

    // Generate your own 20 random bytes, and put them here.
    private static final byte[] SALT = new byte[] {
        -95, -45, 77, -117, -64, 89, -36, -113, -11, 32, -57, 30, -128, -46, 65, -103, 51, 88, 74, -64,
    };

    private TextView mStatusText;
    private TextView mKeepInstalledText;
    private TextView mErrorText;
    private Button mLicenseButton;
    private Button mRetryButton;
    private ImageView mImage;

    private LicenseCheckerCallback mLicenseCheckerCallback;
    private LicenseChecker mChecker;
    // A handler on the UI thread.
    private Handler mHandler;
    
    private ActivityInfo mLinkBubbleApp;
    private int mLicenseState = ProMessengerService.LICENSE_UNKNOWN;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main);

        mStatusText = (TextView) findViewById(R.id.status_text);
        mKeepInstalledText = (TextView)findViewById(R.id.keep_installed_text);
        mErrorText = (TextView)findViewById(R.id.reason_text);
        mLicenseButton = (Button) findViewById(R.id.license_button);
        mImage = (ImageView) findViewById(R.id.thanks_image);
        mLicenseButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (mLicenseState == ProMessengerService.LICENSE_VALID) {
                    if (mLinkBubbleApp != null) {
                        Intent intent = new Intent();
                        intent.setComponent(new ComponentName(mLinkBubbleApp.packageName, mLinkBubbleApp.name));
                        startActivity(intent);
                    } else {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.STORE_FREE_URL));
                        startActivity(intent);
                    }
                    mLicenseButton.setVisibility(View.VISIBLE);
                } else if (mLicenseState == ProMessengerService.LICENSE_INVALID) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.STORE_PRO_URL));
                    startActivity(intent);
                }

            }
        });
        mLicenseButton.setVisibility(View.INVISIBLE);

        mRetryButton = (Button)findViewById(R.id.retry);
        mRetryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doCheck();
            }
        });

        mHandler = new Handler();

        // Try to use more data here. ANDROID_ID is a single point of attack.
        String deviceId = Secure.getString(getContentResolver(), Secure.ANDROID_ID);

        // Library calls this when it's done.
        mLicenseCheckerCallback = new MyLicenseCheckerCallback();
        // Construct the LicenseChecker with a policy.
        mChecker = new LicenseChecker(
            this, new ServerManagedPolicy(this,
                new AESObfuscator(SALT, getPackageName(), deviceId)),
            BASE64_PUBLIC_KEY);
    }

    @Override
	protected void onResume() {
    	
    	super.onResume();

    	doCheck();
    	
    	mLinkBubbleApp = null;
        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mainIntent.setPackage(BuildConfig.FREE_PACKAGE_NAME);
        List<ResolveInfo> apps = getPackageManager().queryIntentActivities(mainIntent, 0);
        if (apps != null && apps.size() > 0) {
            mLinkBubbleApp = apps.get(0).activityInfo;
        }
        
        setLicenseState(mLicenseState, "initial");
    }

    /*
     * 
     */
    void setLicenseState(int licenseState, final String reason) {

        if (Tamper.isTweaked(this)) {
            licenseState = ProMessengerService.LICENSE_INVALID;
        }
    	
    	mLicenseState = licenseState;

    	if (isFinishing() == false) {
	    	mHandler.post(new Runnable() {
	            public void run() {
	            	setProgressBarIndeterminateVisibility(false);
	            	if (mLicenseState == ProMessengerService.LICENSE_VALID) {
                        mImage.setVisibility(View.VISIBLE);
                        mKeepInstalledText.setVisibility(View.VISIBLE);
                        mStatusText.setText(R.string.status_verified);
	            		if (mLinkBubbleApp != null) {
	            			mLicenseButton.setText(R.string.action_load_app);
	            		} else {
	            			mLicenseButton.setText(R.string.action_install_app);
	            		}
	            		mLicenseButton.setVisibility(View.VISIBLE);
	            		mLicenseButton.setEnabled(true);
                        mErrorText.setVisibility(View.GONE);
	            	} else if (mLicenseState == ProMessengerService.LICENSE_INVALID) {
	            		mStatusText.setText(R.string.status_buy);
	            		mLicenseButton.setText(R.string.action_buy);
	            		mLicenseButton.setVisibility(View.VISIBLE);
	            		mLicenseButton.setEnabled(true);
                        mRetryButton.setVisibility(View.VISIBLE);
                        mErrorText.setVisibility(View.VISIBLE);
                        mErrorText.setText(reason);
	            	} else {
	            		mLicenseButton.setVisibility(View.INVISIBLE);
                        mErrorText.setVisibility(View.GONE);
	            	}
	            }
	        });
    	}
    }

    private void doCheck() {
        mRetryButton.setVisibility(View.GONE);
        mLicenseButton.setEnabled(false);
        setProgressBarIndeterminateVisibility(true);
        mStatusText.setText(R.string.status_checking_license);
        mChecker.checkAccess(mLicenseCheckerCallback);
    }

    private class MyLicenseCheckerCallback implements LicenseCheckerCallback {
        public void allow(int policyReason) {
            if (isFinishing()) {
                // Don't update UI if Activity is finishing.
                return;
            }
            // Should allow user access.
            //displayResult(getString(R.string.allow));
            setLicenseState(ProMessengerService.LICENSE_VALID, "policyReason: " + policyReason);
        }

        public void dontAllow(int policyReason) {
            if (isFinishing()) {
                // Don't update UI if Activity is finishing.
                return;
            }
            //displayResult(getString(R.string.dont_allow));
            // Should not allow access. In most cases, the app should assume
            // the user has access unless it encounters this. If it does,
            // the app should inform the user of their unlicensed ways
            // and then either shut down the app or limit the user to a
            // restricted set of features.
            // In this example, we show a dialog that takes the user to Market.
            // If the reason for the lack of license is that the service is
            // unavailable or there is another problem, we display a
            // retry button on the dialog and a different message.
            setLicenseState(ProMessengerService.LICENSE_INVALID, "policyReason: " + policyReason);
        }

        public void applicationError(int errorCode) {
        	setLicenseState(ProMessengerService.LICENSE_INVALID, "app error:" + errorCode);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mChecker.onDestroy();
    }

}
