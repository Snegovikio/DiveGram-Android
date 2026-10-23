package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.tgnet.tl.TL_account;

import java.util.ArrayList;

public class DiveGramOnlinePeek {

    public interface Callback {
        void onResult(CharSequence text, boolean exact);
    }

    private static boolean running;

    public static boolean isRunning() {
        return running;
    }

    public static void peek(int account, long userId, Callback callback) {
        if (running) {
            callback.onResult("Запрос уже выполняется", false);
            return;
        }
        final ContactsController contacts = ContactsController.getInstance(account);
        final ArrayList<TLRPC.PrivacyRule> currentRules = contacts.getPrivacyRules(ContactsController.PRIVACY_RULES_TYPE_LASTSEEN);
        if (currentRules == null) {
            contacts.loadPrivacySettings(true);
            callback.onResult("Настройки приватности ещё загружаются. Повторите через секунду.", false);
            return;
        }
        final ArrayList<TLRPC.InputPrivacyRule> restoreRules = toInputRules(account, currentRules);
        if (restoreRules == null) {
            callback.onResult("Не удалось сохранить текущие настройки приватности. Подсмотр отменён.", false);
            return;
        }
        running = true;

        final TL_account.setPrivacy open = new TL_account.setPrivacy();
        open.key = new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
        open.rules.add(new TLRPC.TL_inputPrivacyValueAllowAll());
        ConnectionsManager.getInstance(account).sendRequest(open, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                running = false;
                callback.onResult(error.text != null && error.text.length() > 0 ? error.text : "Не удалось открыть онлайн", false);
                return;
            }
            if (response instanceof TL_account.privacyRules) {
                contacts.setPrivacyRules(((TL_account.privacyRules) response).rules, ContactsController.PRIVACY_RULES_TYPE_LASTSEEN);
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.privacyRulesUpdated);
            }
            requestStatus(account, userId, (text, exact) -> restore(account, restoreRules, () -> {
                running = false;
                callback.onResult(text, exact);
            }, () -> {
                running = false;
                callback.onResult(text + "\n\nНе удалось вернуть настройки приватности. Проверьте «Последнее посещение» в настройках.", false);
            }));
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private static void requestStatus(int account, long userId, Callback callback) {
        final MessagesController messagesController = MessagesController.getInstance(account);
        final TLRPC.InputUser inputUser = messagesController.getInputUser(userId);
        if (inputUser == null) {
            callback.onResult("Не удалось запросить онлайн", false);
            return;
        }
        final TLRPC.TL_users_getUsers req = new TLRPC.TL_users_getUsers();
        req.id.add(inputUser);
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                callback.onResult(error.text != null && error.text.length() > 0 ? error.text : "Не удалось получить онлайн", false);
                return;
            }
            TLRPC.User user = null;
            if (response instanceof Vector) {
                for (Object object : ((Vector) response).objects) {
                    if (object instanceof TLRPC.User && ((TLRPC.User) object).id == userId) {
                        user = (TLRPC.User) object;
                        break;
                    }
                }
            }
            if (user == null) {
                callback.onResult("Не удалось получить статус", false);
                return;
            }
            final ArrayList<TLRPC.User> users = new ArrayList<>();
            users.add(user);
            messagesController.putUsers(users, false);
            final TLRPC.UserStatus status = user.status;
            if (status instanceof TLRPC.TL_userStatusOffline) {
                callback.onResult(LocaleController.formatDateOnline(status.expires, null), true);
            } else if (status instanceof TLRPC.TL_userStatusOnline) {
                callback.onResult("В сети", true);
            } else {
                callback.onResult("Собеседник скрыл точное время онлайна", false);
            }
        }));
    }

    private static void restore(int account, ArrayList<TLRPC.InputPrivacyRule> rules, Runnable done, Runnable failed) {
        restoreAttempt(account, rules, 0, done, failed);
    }

    private static void restoreAttempt(int account, ArrayList<TLRPC.InputPrivacyRule> rules, int attempt, Runnable done, Runnable failed) {
        final TL_account.setPrivacy req = new TL_account.setPrivacy();
        req.key = new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
        req.rules = new ArrayList<>(rules);
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                if (response instanceof TL_account.privacyRules) {
                    ContactsController.getInstance(account).setPrivacyRules(((TL_account.privacyRules) response).rules, ContactsController.PRIVACY_RULES_TYPE_LASTSEEN);
                }
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.privacyRulesUpdated);
                done.run();
            } else if (attempt < 2) {
                AndroidUtilities.runOnUIThread(() -> restoreAttempt(account, rules, attempt + 1, done, failed), 500);
            } else {
                FileLog.e("DiveGramOnlinePeek: failed to restore last seen privacy: " + (error.text == null ? error.code : error.text));
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.privacyRulesUpdated);
                failed.run();
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private static ArrayList<TLRPC.InputPrivacyRule> toInputRules(int account, ArrayList<TLRPC.PrivacyRule> rules) {
        final MessagesController messagesController = MessagesController.getInstance(account);
        final ArrayList<TLRPC.InputPrivacyRule> result = new ArrayList<>();
        for (TLRPC.PrivacyRule rule : rules) {
            if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
                result.add(new TLRPC.TL_inputPrivacyValueAllowAll());
            } else if (rule instanceof TLRPC.TL_privacyValueDisallowAll) {
                result.add(new TLRPC.TL_inputPrivacyValueDisallowAll());
            } else if (rule instanceof TLRPC.TL_privacyValueAllowContacts) {
                result.add(new TLRPC.TL_inputPrivacyValueAllowContacts());
            } else if (rule instanceof TLRPC.TL_privacyValueDisallowContacts) {
                result.add(new TLRPC.TL_inputPrivacyValueDisallowContacts());
            } else if (rule instanceof TLRPC.TL_privacyValueAllowCloseFriends) {
                result.add(new TLRPC.TL_inputPrivacyValueAllowCloseFriends());
            } else if (rule instanceof TLRPC.TL_privacyValueAllowPremium) {
                result.add(new TLRPC.TL_inputPrivacyValueAllowPremium());
            } else if (rule instanceof TLRPC.TL_privacyValueAllowBots) {
                result.add(new TLRPC.TL_inputPrivacyValueAllowBots());
            } else if (rule instanceof TLRPC.TL_privacyValueDisallowBots) {
                result.add(new TLRPC.TL_inputPrivacyValueDisallowBots());
            } else if (rule instanceof TLRPC.TL_privacyValueAllowUsers) {
                final TLRPC.TL_inputPrivacyValueAllowUsers input = new TLRPC.TL_inputPrivacyValueAllowUsers();
                for (Long id : ((TLRPC.TL_privacyValueAllowUsers) rule).users) {
                    TLRPC.InputUser inputUser = messagesController.getInputUser(id);
                    if (inputUser == null) {
                        return null;
                    }
                    input.users.add(inputUser);
                }
                if (!input.users.isEmpty()) {
                    result.add(input);
                }
            } else if (rule instanceof TLRPC.TL_privacyValueDisallowUsers) {
                final TLRPC.TL_inputPrivacyValueDisallowUsers input = new TLRPC.TL_inputPrivacyValueDisallowUsers();
                for (Long id : ((TLRPC.TL_privacyValueDisallowUsers) rule).users) {
                    TLRPC.InputUser inputUser = messagesController.getInputUser(id);
                    if (inputUser == null) {
                        return null;
                    }
                    input.users.add(inputUser);
                }
                if (!input.users.isEmpty()) {
                    result.add(input);
                }
            } else if (rule instanceof TLRPC.TL_privacyValueAllowChatParticipants) {
                final TLRPC.TL_inputPrivacyValueAllowChatParticipants input = new TLRPC.TL_inputPrivacyValueAllowChatParticipants();
                input.chats.addAll(((TLRPC.TL_privacyValueAllowChatParticipants) rule).chats);
                result.add(input);
            } else if (rule instanceof TLRPC.TL_privacyValueDisallowChatParticipants) {
                final TLRPC.TL_inputPrivacyValueDisallowChatParticipants input = new TLRPC.TL_inputPrivacyValueDisallowChatParticipants();
                input.chats.addAll(((TLRPC.TL_privacyValueDisallowChatParticipants) rule).chats);
                result.add(input);
            }
        }
        if (result.isEmpty()) {
            result.add(new TLRPC.TL_inputPrivacyValueDisallowAll());
        }
        return result;
    }
}
