/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui.UsernameDrope;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

public class UsernameDropeActivity extends BaseFragment implements DropeController.DropeCallback {

    private static final int REQUEST_CODE_BLUETOOTH = 7394;

    private DropDropAnimationView animationView;
    private TextView titleView;
    private TextView subtitleView;
    private TextView bottomButton;

    private NFCDropeTransport nfcTransport;
    private BLEDropeTransport bleTransport;
    private boolean transportsStarted;
    private boolean completed;
    private boolean retryReady;
    private boolean pendingPermissions;
    private long foundAt;
    private long lastPeerUserId;
    private String lastPeerUsername;

    public UsernameDropeActivity() {
        super();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.UsernameDrope));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout contentView = new LinearLayout(context);
        contentView.setOrientation(LinearLayout.VERTICAL);
        contentView.setGravity(Gravity.CENTER_HORIZONTAL);
        contentView.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(40), AndroidUtilities.dp(32), AndroidUtilities.dp(24));
        ((FrameLayout) fragmentView).addView(contentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        animationView = new DropDropAnimationView(context);
        animationView.setLayoutParams(new LinearLayout.LayoutParams(AndroidUtilities.dp(300), AndroidUtilities.dp(300)));
        contentView.addView(animationView);

        titleView = new TextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(19);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleView.setLineSpacing(AndroidUtilities.dp(2), 1f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        titleLp.topMargin = AndroidUtilities.dp(28);
        contentView.addView(titleView, titleLp);

        subtitleView = new TextView(context);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleView.setTextSize(14);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setLineSpacing(AndroidUtilities.dp(3), 1f);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        subtitleLp.topMargin = AndroidUtilities.dp(12);
        contentView.addView(subtitleView, subtitleLp);

        bottomButton = createRoundedButton(context);
        bottomButton.setOnClickListener(v -> onBottomButtonPressed());
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(AndroidUtilities.dp(220), AndroidUtilities.dp(48));
        buttonLp.topMargin = AndroidUtilities.dp(40);
        buttonLp.gravity = Gravity.CENTER_HORIZONTAL;
        contentView.addView(bottomButton, buttonLp);

        setUiListening();
        return fragmentView;
    }

    private TextView createRoundedButton(Context context) {
        TextView button = new TextView(context);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(0xFFFFFFFF);
        button.setClickable(true);
        button.setFocusable(true);
        int radius = AndroidUtilities.dp(24);
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setColor(Theme.getColor(Theme.key_avatar_backgroundBlue));
        normal.setCornerRadius(radius);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setColor(Theme.getColor(Theme.key_avatar_backgroundActionBarBlue));
        pressed.setCornerRadius(radius);
        StateListDrawable stateList = new StateListDrawable();
        stateList.addState(new int[]{android.R.attr.state_pressed}, pressed);
        stateList.addState(new int[]{}, normal);
        button.setBackground(stateList);
        return button;
    }

    private void setupTransports() {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        DropeController controller = DropeController.INSTANCE;
        controller.reset();
        controller.setCallback(this);
        DropeBridge.setMyPayload(controller.buildMyPayload());
        DropeBridge.setPeerListener(payload -> {
            if (isPaused || completed) {
                return;
            }
            controller.processPeerPayload(payload);
        });

        nfcTransport = new NFCDropeTransport(activity);
        bleTransport = new BLEDropeTransport(activity);
    }

    private void startTransports() {
        if (completed) {
            return;
        }
        Activity activity = getParentActivity();
        if (fragmentView == null || isPaused || activity == null) {
            org.telegram.messenger.FileLog.d("drope startTransports skip: view=" + (fragmentView != null) + " paused=" + isPaused + " activity=" + (activity != null));
            return;
        }
        if (nfcTransport == null) {
            setupTransports();
            activity = getParentActivity();
            if (activity == null) {
                return;
            }
        }
        if (nfcTransport == null || transportsStarted) {
            org.telegram.messenger.FileLog.d("drope startTransports skip: nfc=" + (nfcTransport != null) + " started=" + transportsStarted);
            return;
        }
        boolean anyStarted = false;
        boolean nfcOk = nfcTransport.isSupported();
        org.telegram.messenger.FileLog.d("drope nfc supported=" + nfcOk);
        if (nfcOk) {
            nfcTransport.start();
            anyStarted = true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && bleTransport != null) {
            boolean bleOk = BLEDropeTransport.isBleSupported(activity);
            boolean blePerms = BLEDropeTransport.hasAllPermissions(activity);
            org.telegram.messenger.FileLog.d("drope ble supported=" + bleOk + " perms=" + blePerms);
            if (bleOk && blePerms) {
                bleTransport.start();
                anyStarted = true;
            } else if (bleOk && !pendingPermissions) {
                pendingPermissions = true;
                activity.requestPermissions(BLEDropeTransport.getMissingPermissions(activity), REQUEST_CODE_BLUETOOTH);
            }
        }
        transportsStarted = anyStarted;
        org.telegram.messenger.FileLog.d("drope transports started=" + anyStarted);
        if (!anyStarted && !pendingPermissions) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!completed && !isPaused) {
                    setUiError();
                }
            });
        }
    }

    private void stopTransports() {
        if (nfcTransport != null) {
            nfcTransport.stop();
        }
        if (bleTransport != null) {
            bleTransport.stop();
        }
        transportsStarted = false;
    }

    private void setUiListening() {
        animationView.setState(DropDropAnimationView.STATE_LISTEN, false);
        titleView.setText(LocaleController.getString(R.string.DropeBringTogether));
        subtitleView.setText(LocaleController.getString(R.string.DropeBringTogetherSub));
        bottomButton.setText(LocaleController.getString(R.string.DropeClose));
        bottomButton.setVisibility(View.INVISIBLE);
    }

    private void setUiFound(String peerName) {
        vibrate();
        playIOSRipple(1.5f);
        foundAt = SystemClock.uptimeMillis();
        animationView.setState(DropDropAnimationView.STATE_MERGE, true);
        titleView.setText(LocaleController.formatString(R.string.DropeFoundPeer, peerName));
        subtitleView.setText(LocaleController.formatString(R.string.DropeAddingToContacts, peerName));
        bottomButton.setVisibility(View.INVISIBLE);
    }

    private void vibrate() {
        try {
            Context context = getParentActivity();
            if (context == null) {
                return;
            }
            android.os.Vibrator vibrator = (android.os.Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(120);
            }
        } catch (Exception ignore) {
        }
    }

    private void playIOSRipple(float intensity) {
        try {
            if (animationView == null || fragmentView == null) {
                return;
            }
            int[] loc = new int[2];
            animationView.getLocationInWindow(loc);
            LaunchActivity.makeRipple(
                    loc[0] + animationView.getWidth() / 2f,
                    loc[1] + animationView.getHeight() / 2f,
                    intensity * 2.5f * SharedConfig.dropeRippleStrength / 100f);
        } catch (Exception ignore) {
        }
    }

    private void setUiDone(String peerName) {
        completed = true;
        retryReady = false;
        stopTransports();
        playIOSRipple(0.9f);
        animationView.setState(DropDropAnimationView.STATE_DONE, true);
        titleView.setText(LocaleController.getString(R.string.DropeDoneTitle));
        subtitleView.setText(LocaleController.formatString(R.string.DropeDoneText, peerName)
                + "\n\n"
                + LocaleController.getString(R.string.DropeDoneSub));
        bottomButton.setText(LocaleController.getString(R.string.DropeClose));
        bottomButton.setVisibility(View.VISIBLE);
        if (lastPeerUserId > 0) {
            final long uid = lastPeerUserId;
            AndroidUtilities.runOnUIThread(() -> presentFragment(ChatActivity.of(uid), true), 800);
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (completed && !isPaused) {
                bottomButton.setText(LocaleController.getString(R.string.DropeRetry));
                retryReady = true;
            }
        }, 2200);
    }

    private void setUiError() {
        animationView.setState(DropDropAnimationView.STATE_ERROR, true);
        titleView.setText(LocaleController.getString(R.string.DropeErrorNoDevice));
        subtitleView.setText(LocaleController.getString(R.string.DropeErrorNoDeviceSub));
        bottomButton.setText(LocaleController.getString(R.string.DropeRetry));
        bottomButton.setVisibility(View.VISIBLE);
    }

    private void onBottomButtonPressed() {
        if (completed) {
            if (retryReady) {
                startNewDropeSession();
            } else {
                finishFragment();
            }
            return;
        }
        startNewDropeSession();
    }

    private void startNewDropeSession() {
        completed = false;
        retryReady = false;
        transportsStarted = false;
        lastPeerUserId = 0;
        lastPeerUsername = null;
        foundAt = 0;
        pendingPermissions = false;
        DropeController.INSTANCE.reset();
        DropeBridge.setMyPayload(DropeController.INSTANCE.buildMyPayload());
        setUiListening();
        startTransports();
    }

    @Override
    public void onDropeStateChanged(int state, String peerName, long peerUserId) {
        if (isPaused || fragmentView == null) {
            return;
        }
        if (state == DropeController.STATE_FOUND) {
            lastPeerUserId = peerUserId;
            lastPeerUsername = peerName.startsWith("@") ? peerName.substring(1) : null;
            stopTransports();
            setUiFound(peerName);
        } else if (state == DropeController.STATE_DONE) {
            if (peerUserId > 0) {
                lastPeerUserId = peerUserId;
            }
            if (lastPeerUserId <= 0 && lastPeerUsername != null) {
                for (TLRPC.User u : getMessagesController().getUsers().values()) {
                    String un = UserObject.getPublicUsername(u);
                    if (un != null && un.equalsIgnoreCase(lastPeerUsername)) {
                        lastPeerUserId = u.id;
                        break;
                    }
                }
            }
            long delay = Math.max(0, 1500 - (SystemClock.uptimeMillis() - foundAt));
            AndroidUtilities.runOnUIThread(() -> {
                if (!isPaused) {
                    setUiDone(peerName);
                }
            }, delay);
        }
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_BLUETOOTH) {
            pendingPermissions = false;
            if (!completed) {
                transportsStarted = false;
                startTransports();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startTransports();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopTransports();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        stopTransports();
        DropeBridge.clear();
        DropeController.INSTANCE.setCallback(null);
    }
}