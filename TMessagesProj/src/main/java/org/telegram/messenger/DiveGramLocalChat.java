package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class DiveGramLocalChat {

    public static final long CHAT_ID = 777000123L;
    public static final long DIALOG_ID = -CHAT_ID;
    public static final int TOP_MESSAGE_ID = 1;

    public static void ensure(int account) {
        final MessagesController controller = MessagesController.getInstance(account);
        final TLRPC.Chat existing = controller.getChat(CHAT_ID);
        final TLRPC.Dialog existingDialog = controller.dialogs_dict.get(DIALOG_ID);
        if (existing != null && existingDialog != null) {
            return;
        }

        final int now = (int) (System.currentTimeMillis() / 1000L);

        final TLRPC.TL_chat chat = new TLRPC.TL_chat();
        chat.id = (int) CHAT_ID;
        chat.title = "Плейлист";
        chat.participants_count = 1;
        chat.date = now;
        chat.version = 1;
        chat.creator = true;
        chat.flags |= 1024;
        chat.photo = new TLRPC.TL_chatPhotoEmpty();

        final TLRPC.TL_peerChat peer = new TLRPC.TL_peerChat();
        peer.chat_id = chat.id;

        final TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = TOP_MESSAGE_ID;
        message.dialog_id = DIALOG_ID;
        message.peer_id = peer;
        message.from_id = peer;
        message.out = true;
        message.date = now;
        message.message = "Локальный плейлист";

        final TLRPC.TL_dialog dialog = new TLRPC.TL_dialog();
        dialog.id = DIALOG_ID;
        dialog.peer = peer;
        dialog.top_message = TOP_MESSAGE_ID;
        dialog.last_message_date = now;
        dialog.read_inbox_max_id = TOP_MESSAGE_ID;
        dialog.read_outbox_max_id = TOP_MESSAGE_ID;
        dialog.pinned = true;
        dialog.pinnedNum = 1;
        DialogObject.initDialog(dialog);

        final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        chats.add(chat);
        controller.putChats(chats, false);

        final TLRPC.TL_messages_dialogs dialogs = new TLRPC.TL_messages_dialogs();
        dialogs.dialogs.add(dialog);
        dialogs.messages.add(message);
        dialogs.chats.add(chat);
        controller.getMessagesStorage().putDialogs(dialogs, 0);

        controller.dialogs_dict.put(DIALOG_ID, dialog);
        if (!controller.allDialogs.contains(dialog)) {
            controller.allDialogs.add(0, dialog);
        }
        controller.dialogMessage.put(DIALOG_ID, new ArrayList<>(java.util.Collections.singletonList(new MessageObject(account, message, false, false))));
        controller.sortDialogs(null);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload, true);
    }

    public static void remove(int account) {
        final MessagesController controller = MessagesController.getInstance(account);
        boolean changed = controller.dialogs_dict.get(DIALOG_ID) != null;
        controller.dialogs_dict.remove(DIALOG_ID);
        controller.dialogMessage.remove(DIALOG_ID);
        for (int i = 0; i < controller.allDialogs.size(); i++) {
            if (controller.allDialogs.get(i).id == DIALOG_ID) {
                controller.allDialogs.remove(i);
                changed = true;
                break;
            }
        }
        if (changed) {
            controller.getMessagesStorage().deleteDialog(DIALOG_ID, 0);
            controller.sortDialogs(null);
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload, true);
        }
    }
}
