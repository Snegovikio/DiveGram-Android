/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui.UsernameDrope;

import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;

public class DropeController {

    public static final int STATE_LISTEN = 0;
    public static final int STATE_FOUND = 1;
    public static final int STATE_DONE = 2;
    public static final int STATE_ERROR = 3;

    public static final String PAYLOAD_MAGIC = "DIVE";

    private final HashSet<String> handledSessions = new HashSet<>();
    private String mySessionId;

    public static final DropeController INSTANCE;

    static {
        INSTANCE = new DropeController();
    }

    private DropeController() {
    }

    public int getCurrentAccount() {
        return UserConfig.selectedAccount;
    }

    public interface DropeCallback {
        void onDropeStateChanged(int state, String peerName, long peerUserId);
    }

    private DropeCallback callback;

    public void setCallback(DropeCallback callback) {
        this.callback = callback;
    }

    public String getMySessionId() {
        if (mySessionId == null) {
            mySessionId = Integer.toHexString(Utilities.random.nextInt()) + "_" + Integer.toHexString(Utilities.random.nextInt());
        }
        return mySessionId;
    }

    public boolean handledSession(String sessionId) {
        if (sessionId == null || handledSessions.contains(sessionId)) {
            return true;
        }
        handledSessions.add(sessionId);
        return false;
    }

    public void reset() {
        handledSessions.clear();
        mySessionId = null;
    }

    public byte[] buildMyPayload() {
        try {
            TLRPC.User user = getUserConfig().getCurrentUser();
            String username = "";
            String firstName = "";
            String lastName = "";
            String phone = "";
            long userId = 0;
            if (user != null) {
                username = UserObject.getPublicUsername(user);
                if (username == null) {
                    username = "";
                }
                firstName = user.first_name == null ? "" : user.first_name;
                lastName = user.last_name == null ? "" : user.last_name;
                phone = user.phone == null ? "" : user.phone;
                userId = user.id;
            }
            JSONObject obj = new JSONObject();
            obj.put("m", PAYLOAD_MAGIC);
            obj.put("v", 1);
            obj.put("sid", getMySessionId());
            obj.put("uid", userId);
            obj.put("un", trim(username, 64));
            obj.put("fn", trim(firstName, 40));
            obj.put("ln", trim(lastName, 40));
            obj.put("ph", trim(phone, 24));
            obj.put("ts", System.currentTimeMillis());
            String json = obj.toString();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 220) {
                json = fitToLimit(obj);
                bytes = json.getBytes(StandardCharsets.UTF_8);
            }
            return bytes;
        } catch (Exception e) {
            FileLog.e(e);
            return new byte[0];
        }
    }

    private static String fitToLimit(JSONObject obj) {
        try {
            String[] keys = {"ln", "ph", "un", "fn"};
            for (String key : keys) {
                if (obj.has(key)) {
                    String value = obj.getString(key);
                    String shortened = trim(value, value.length() / 2);
                    obj.put(key, shortened);
                    if (obj.toString().getBytes(StandardCharsets.UTF_8).length <= 220) {
                        break;
                    }
                }
            }
        } catch (Exception ignore) {
        }
        String json = obj.toString();
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 220) {
            return json;
        }
        for (int cut = bytes.length - 1; cut > 0; cut--) {
            String candidate = new String(bytes, 0, cut, StandardCharsets.UTF_8);
            if (candidate.endsWith("}")) {
                return candidate;
            }
        }
        return json;
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() > max) {
            return s.substring(0, max);
        }
        return s;
    }

    public boolean processPeerPayload(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return false;
        }
        return processPeerPayload(new String(payload, StandardCharsets.UTF_8));
    }

    public boolean processPeerPayload(String json) {
        if (TextUtils.isEmpty(json)) {
            return false;
        }
        try {
            JSONObject obj = new JSONObject(json);
            if (!PAYLOAD_MAGIC.equals(obj.optString("m")) || obj.optInt("v", 0) != 1) {
                return false;
            }
            String session = obj.optString("sid");
            if (session.isEmpty() || session.equals(getMySessionId())) {
                return false;
            }
            if (handledSession(session)) {
                return true;
            }
            long peerUid = obj.optLong("uid", 0);
            if (peerUid == getUserConfig().getClientUserId()) {
                return false;
            }
            String name = peerName(obj.optString("fn"), obj.optString("ln"), obj.optString("un"));
            if (TextUtils.isEmpty(name)) {
                name = "@" + obj.optString("un");
            }
            final String finalPeerName = name;
            final long finalPeerUid = peerUid;
            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) {
                    callback.onDropeStateChanged(STATE_FOUND, finalPeerName, finalPeerUid);
                }
                addContactFromPayload(obj);
            });
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    private void addContactFromPayload(JSONObject obj) {
        String username = obj.optString("un");
        String phone = obj.optString("ph");
        String firstName = obj.optString("fn");
        String lastName = obj.optString("ln");
        final String peerDisplayName = peerName(firstName, lastName, username);

        TLRPC.User foundUser = null;
        if (!TextUtils.isEmpty(username)) {
            for (TLRPC.User u : getMessagesController().getUsers().values()) {
                String un = UserObject.getPublicUsername(u);
                if (un != null && un.equalsIgnoreCase(username)) {
                    foundUser = u;
                    break;
                }
            }
        }

        if (foundUser != null) {
            getContactsController().addContact(foundUser, false);
            final long uid = foundUser.id;
            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) {
                    callback.onDropeStateChanged(STATE_DONE, peerDisplayName, uid);
                }
            });
        } else if (!TextUtils.isEmpty(username)) {
            getMessagesController().getUserNameResolver().resolve(username, resolvedPeerId -> {
                TLRPC.User user = resolvedPeerId != null && resolvedPeerId != 0 ? getMessagesController().getUser(resolvedPeerId) : null;
                long doneUid = 0;
                if (user != null) {
                    getContactsController().addContact(user, false);
                    doneUid = user.id;
                } else if (!TextUtils.isEmpty(phone)) {
                    importByPhone(phone, firstName, lastName);
                }
                final long finalUid = doneUid;
                AndroidUtilities.runOnUIThread(() -> {
                    if (callback != null) {
                        callback.onDropeStateChanged(STATE_DONE, peerDisplayName, finalUid);
                    }
                });
            });
        } else if (!TextUtils.isEmpty(phone)) {
            importByPhone(phone, firstName, lastName);
            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) {
                    callback.onDropeStateChanged(STATE_DONE, peerDisplayName, 0);
                }
            });
        } else {
            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) {
                    callback.onDropeStateChanged(STATE_DONE, peerDisplayName, 0);
                }
            });
        }
    }

    private void importByPhone(String phone, String firstName, String lastName) {
        try {
            TLRPC.TL_inputPhoneContact c = new TLRPC.TL_inputPhoneContact();
            c.client_id = Utilities.random.nextInt();
            c.phone = phone;
            c.first_name = TextUtils.isEmpty(firstName) ? phone : firstName;
            c.last_name = TextUtils.isEmpty(lastName) ? "" : lastName;
            TLRPC.TL_contacts_importContacts req = new TLRPC.TL_contacts_importContacts();
            req.contacts = new ArrayList<>();
            req.contacts.add(c);
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (response instanceof TLRPC.TL_contacts_importedContacts) {
                    TLRPC.TL_contacts_importedContacts res = (TLRPC.TL_contacts_importedContacts) response;
                    getMessagesStorage().putUsersAndChats(res.users, null, true, true);
                    ArrayList<TLRPC.TL_contact> cArr = new ArrayList<>();
                    for (int i = 0; i < res.imported.size(); i++) {
                        TLRPC.TL_contact contact = new TLRPC.TL_contact();
                        contact.user_id = res.imported.get(i).user_id;
                        cArr.add(contact);
                    }
                    getContactsController().processLoadedContacts(cArr, res.users, 2);
                }
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static String peerName(String firstName, String lastName, String username) {
        String name = "";
        if (!TextUtils.isEmpty(firstName) && !"null".equals(firstName)) {
            name = firstName;
        }
        if (!TextUtils.isEmpty(lastName) && !"null".equals(lastName)) {
            name = name.isEmpty() ? lastName : name + " " + lastName;
        }
        if (name.isEmpty() && !TextUtils.isEmpty(username)) {
            name = "@" + username;
        }
        if (name.isEmpty()) {
            name = "Unknown";
        }
        return name;
    }

    public UserConfig getUserConfig() {
        return UserConfig.getInstance(UserConfig.selectedAccount);
    }

    public MessagesController getMessagesController() {
        return MessagesController.getInstance(UserConfig.selectedAccount);
    }

    public ContactsController getContactsController() {
        return ContactsController.getInstance(UserConfig.selectedAccount);
    }

    public ConnectionsManager getConnectionsManager() {
        return ConnectionsManager.getInstance(UserConfig.selectedAccount);
    }

    public MessagesStorage getMessagesStorage() {
        return MessagesStorage.getInstance(UserConfig.selectedAccount);
    }
}