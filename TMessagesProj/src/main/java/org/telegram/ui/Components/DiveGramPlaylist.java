package org.telegram.ui.Components;

import android.text.TextUtils;
import android.util.Base64;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class DiveGramPlaylist {

    public static class Item {
        public String doc;
        public String title;
        public String performer;
        public int duration;
        public long date;
    }

    private static final String PREF_KEY = "dgPlaylistItems";

    public static ArrayList<Item> getItems() {
        final ArrayList<Item> items = new ArrayList<>();
        try {
            final String raw = MessagesController.getGlobalMainSettings().getString(PREF_KEY, null);
            if (TextUtils.isEmpty(raw)) {
                return items;
            }
            final JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                final JSONObject o = array.getJSONObject(i);
                final Item item = new Item();
                item.doc = o.optString("doc");
                item.title = o.optString("title");
                item.performer = o.optString("performer");
                item.duration = o.optInt("duration");
                item.date = o.optLong("date");
                if (!TextUtils.isEmpty(item.doc)) {
                    items.add(item);
                }
            }
        } catch (Throwable ignore) {
        }
        return items;
    }

    private static void saveItems(ArrayList<Item> items) {
        try {
            final JSONArray array = new JSONArray();
            for (int i = 0; i < items.size(); i++) {
                final Item item = items.get(i);
                final JSONObject o = new JSONObject();
                o.put("doc", item.doc);
                o.put("title", item.title);
                o.put("performer", item.performer);
                o.put("duration", item.duration);
                o.put("date", item.date);
                array.put(o);
            }
            MessagesController.getGlobalMainSettings().edit().putString(PREF_KEY, array.toString()).apply();
        } catch (Throwable ignore) {
        }
    }

    public static boolean contains(TLRPC.Document document) {
        if (document == null) {
            return false;
        }
        final ArrayList<Item> items = getItems();
        for (int i = 0; i < items.size(); i++) {
            final TLRPC.Document doc = decode(items.get(i).doc);
            if (doc != null && doc.id == document.id) {
                return true;
            }
        }
        return false;
    }

    public static void add(MessageObject messageObject) {
        if (messageObject == null) {
            return;
        }
        final TLRPC.Document document = messageObject.getDocument();
        if (document == null) {
            toast("Не удалось добавить трек");
            return;
        }
        final String encoded = encode(document);
        if (TextUtils.isEmpty(encoded)) {
            toast("Не удалось добавить трек");
            return;
        }
        final ArrayList<Item> items = getItems();
        for (int i = 0; i < items.size(); i++) {
            if (TextUtils.equals(items.get(i).doc, encoded)) {
                toast("Трек уже в плейлисте");
                return;
            }
        }
        final Item item = new Item();
        item.doc = encoded;
        item.title = messageObject.getMusicTitle();
        item.performer = messageObject.getMusicAuthor();
        item.duration = (int) Math.ceil(messageObject.getDuration());
        item.date = System.currentTimeMillis();
        items.add(0, item);
        saveItems(items);
        toast("Добавлено в плейлист");
    }

    public static void remove(int index) {
        final ArrayList<Item> items = getItems();
        if (index < 0 || index >= items.size()) {
            return;
        }
        items.remove(index);
        saveItems(items);
    }

    public static void play(int account, int index) {
        final ArrayList<Item> items = getItems();
        if (index < 0 || index >= items.size()) {
            return;
        }
        final ArrayList<MessageObject> messages = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            final Item it = items.get(i);
            final TLRPC.Document d = decode(it.doc);
            if (d != null) {
                messages.add(createMessageObject(account, d, it, i));
            }
        }
        if (messages.isEmpty()) {
            toast("Трек недоступен");
            return;
        }
        final int playIndex = Math.min(index, messages.size() - 1);
        try {
            MediaController.getInstance().setPlaylist(messages, messages.get(playIndex), 0, false, null);
        } catch (Throwable e) {
            FileLog.e(e);
            toast("Не удалось воспроизвести трек");
        }
    }

    public static MessageObject toMessageObject(int account, Item item) {
        final TLRPC.Document document = decode(item.doc);
        return document != null ? createMessageObject(account, document, item, 0) : null;
    }

    private static MessageObject createMessageObject(int account, TLRPC.Document document, Item item, int index) {
        final TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = 1_900_000_000 + Math.max(0, index);
        message.out = true;
        message.date = (int) (item.date / 1000L);
        message.peer_id = new TLRPC.TL_peerUser();
        message.peer_id.user_id = UserConfig.getInstance(account).getClientUserId();
        message.from_id = message.peer_id;
        message.dialog_id = message.peer_id.user_id;
        message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        message.media = new TLRPC.TL_messageMediaDocument();
        message.media.flags |= 1;
        message.media.document = document;
        return new MessageObject(account, message, false, false);
    }

    private static String encode(TLRPC.Document document) {
        try {
            final NativeByteBuffer buffer = new NativeByteBuffer(document.getObjectSize());
            document.serializeToStream(buffer);
            final byte[] bytes = new byte[buffer.length()];
            buffer.position(0);
            buffer.readBytes(bytes, false);
            buffer.reuse();
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Throwable e) {
            return null;
        }
    }

    private static TLRPC.Document decode(String encoded) {
        try {
            final byte[] bytes = Base64.decode(encoded, Base64.NO_WRAP);
            final SerializedData data = new SerializedData(bytes);
            return TLRPC.Document.TLdeserialize(data, data.readInt32(false), false);
        } catch (Throwable e) {
            return null;
        }
    }

    private static void toast(final String text) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_SHORT).show();
            } catch (Throwable ignore) {
            }
        });
    }
}
