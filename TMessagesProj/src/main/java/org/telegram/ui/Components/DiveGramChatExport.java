package org.telegram.ui.Components;

import android.content.ContentValues;
import android.content.Context;
import org.telegram.SQLite.SQLiteCursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class DiveGramChatExport {

    private static final int PAGE_W = 595;
    private static final int PAGE_H = 842;
    private static final int MARGIN = 28;
    private static final int PAD = 9;
    private static final int BUBBLE_MAX_W = 400;
    private static final int RADIUS = 9;

    private static final int COLOR_BG_IN = 0xFFFFFFFF;
    private static final int COLOR_BG_OUT = 0xFFEFFDDE;
    private static final int COLOR_STROKE = 0xFFE2E2E2;
    private static final int COLOR_TEXT = 0xFF171717;
    private static final int COLOR_TIME = 0xFF9A9A9A;
    private static final int COLOR_NAME = 0xFF3A7BD5;
    private static final int COLOR_DATE_BG = 0xFFF0F0F0;
    private static final int COLOR_HEADER = 0xFF171717;

    private static final SimpleDateFormat DAY_FMT = new SimpleDateFormat("d MMMM yyyy", new Locale("ru"));
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm", Locale.US);

    public static void export(BaseFragment fragment, long dialogId) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        final int account = fragment.getCurrentAccount();
        final Context context = fragment.getParentActivity();
        final String title = chatTitle(account, dialogId);
        BulletinFactory.of(fragment.getLayoutContainer(), fragment.getResourceProvider())
                .createSimpleBulletin(org.telegram.messenger.R.raw.saved_messages, "Экспорт чата: подготовка…")
                .show();
        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
            final ArrayList<TLRPC.Message> messages = loadMessages(account, dialogId);
            org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
                try {
                    final String result = exportInternal(context, account, dialogId, title, messages);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (fragment.getParentActivity() == null) {
                            return;
                        }
                        try {
                            BulletinFactory.of(fragment.getLayoutContainer(), fragment.getResourceProvider())
                                    .createSimpleBulletin(org.telegram.messenger.R.raw.saved_messages, result)
                                    .show();
                        } catch (Throwable ignore) {
                        }
                    });
                } catch (Throwable e) {
                    FileLog.e(e);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (fragment.getParentActivity() == null) {
                            return;
                        }
                        try {
                            BulletinFactory.of(fragment.getLayoutContainer(), fragment.getResourceProvider())
                                    .createSimpleBulletin(org.telegram.messenger.R.raw.error, "Не удалось экспортировать чат")
                                    .show();
                        } catch (Throwable ignore) {
                        }
                    });
                }
            });
        });
    }

    private static String chatTitle(int account, long dialogId) {
        try {
            if (dialogId > 0) {
                TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
                if (user != null) {
                    String name = user.first_name;
                    if (!TextUtils.isEmpty(user.last_name)) {
                        name = (name == null ? "" : name + " ") + user.last_name;
                    }
                    if (!TextUtils.isEmpty(name)) {
                        return name;
                    }
                    if (!TextUtils.isEmpty(user.username)) {
                        return "@" + user.username;
                    }
                }
            } else if (dialogId < 0) {
                TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
                if (chat != null && !TextUtils.isEmpty(chat.title)) {
                    return chat.title;
                }
            }
        } catch (Throwable ignore) {
        }
        return "Чат";
    }

    private static String exportInternal(Context context, int account, long dialogId, String title, ArrayList<TLRPC.Message> messages) throws Exception {
        if (messages == null || messages.isEmpty()) {
            return "В чате нет сообщений для экспорта";
        }

        final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(10f);
        textPaint.setColor(COLOR_TEXT);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setTextSize(8.5f);
        namePaint.setColor(COLOR_NAME);
        namePaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

        final TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        timePaint.setTextSize(7.5f);
        timePaint.setColor(COLOR_TIME);
        timePaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        final TextPaint datePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        datePaint.setTextSize(8.5f);
        datePaint.setColor(0xFF616161);
        datePaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

        final TextPaint headerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        headerPaint.setTextSize(16f);
        headerPaint.setColor(COLOR_HEADER);
        headerPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

        final TextPaint subHeaderPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        subHeaderPaint.setTextSize(9.5f);
        subHeaderPaint.setColor(0xFF7A7A7A);
        subHeaderPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(0.8f);
        strokePaint.setColor(COLOR_STROKE);
        final Paint dateBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dateBgPaint.setColor(COLOR_DATE_BG);

        final PdfDocument document = new PdfDocument();
        int pageNumber = 0;
        PdfDocument.Page page = null;
        Canvas canvas = null;
        float y = 0;

        final int contentW = PAGE_W - MARGIN * 2;
        final float bubbleTextMaxW = BUBBLE_MAX_W - PAD * 2;

        int exported = 0;
        int lastDay = 0;
        String lastDate = null;

        try {
            for (int i = 0; i < messages.size(); i++) {
                final TLRPC.Message message = messages.get(i);
                if (message == null) {
                    continue;
                }
                CharSequence text = message.message;
                if (TextUtils.isEmpty(text)) {
                    text = mediaPlaceholder(message);
                }
                if (TextUtils.isEmpty(text) && message.action != null) {
                    text = "[Служебное сообщение]";
                }
                if (TextUtils.isEmpty(text)) {
                    text = "[Медиа]";
                }
                if (text.length() > 6000) {
                    text = text.subSequence(0, 6000) + "…";
                }

                final boolean outgoing = message.out;
                final String sender = outgoing ? "Вы" : senderName(account, message, title);

                if (page == null) {
                    pageNumber++;
                    page = document.startPage(new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create());
                    canvas = page.getCanvas();
                    y = MARGIN;
                    canvas.drawText("Экспорт переписки", MARGIN, y + 14, headerPaint);
                    y += 22;
                    canvas.drawText(title + " · " + DAY_FMT.format(new Date(((long) messages.get(0).date) * 1000L)) + " — " + DAY_FMT.format(new Date(((long) messages.get(messages.size() - 1).date) * 1000L)), MARGIN, y, subHeaderPaint);
                    y += 20;
                    canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, strokePaint);
                    y += 16;
                }

                final int day = message.date / 86400;
                if (day != lastDay) {
                    lastDay = day;
                    lastDate = DAY_FMT.format(new Date(((long) message.date) * 1000L));
                    if (y + 30 > PAGE_H - MARGIN) {
                        document.finishPage(page);
                        pageNumber++;
                        page = document.startPage(new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create());
                        canvas = page.getCanvas();
                        y = MARGIN;
                    }
                    lastDate = lastDate.substring(0, 1).toUpperCase() + lastDate.substring(1);
                    final float tw = datePaint.measureText(lastDate);
                    final float pillW = tw + 20;
                    final RectF pill = new RectF((PAGE_W - pillW) / 2f, y, (PAGE_W + pillW) / 2f, y + 17);
                    canvas.drawRoundRect(pill, 8.5f, 8.5f, dateBgPaint);
                    canvas.drawText(lastDate, (PAGE_W - tw) / 2f, y + 12, datePaint);
                    y += 26;
                }

                final StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, (int) bubbleTextMaxW)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1.08f)
                        .setIncludePad(false)
                        .build();

                final boolean showName = !TextUtils.isEmpty(sender);
                final String time = TIME_FMT.format(new Date(((long) message.date) * 1000L));
                final float timeW = timePaint.measureText(time);
                final float timeH = 10f;

                float bubbleW = 0;
                for (int l = 0; l < layout.getLineCount(); l++) {
                    bubbleW = Math.max(bubbleW, layout.getLineRight(l));
                }
                bubbleW = Math.min(bubbleTextMaxW, bubbleW);
                bubbleW = Math.max(bubbleW, showName ? namePaint.measureText(sender) : 0);
                bubbleW = Math.max(bubbleW, timeW + 8);
                bubbleW += PAD * 2;

                final float nameH = showName ? 12f : 0f;
                final float bubbleH = PAD + nameH + layout.getHeight() + 6 + timeH + PAD;

                if (y + bubbleH > PAGE_H - MARGIN) {
                    document.finishPage(page);
                    pageNumber++;
                    page = document.startPage(new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create());
                    canvas = page.getCanvas();
                    y = MARGIN;
                }

                final float left = outgoing ? PAGE_W - MARGIN - bubbleW : MARGIN;
                final RectF rect = new RectF(left, y, left + bubbleW, y + bubbleH);
                bubblePaint.setColor(outgoing ? COLOR_BG_OUT : COLOR_BG_IN);
                canvas.drawRoundRect(rect, RADIUS, RADIUS, bubblePaint);
                if (!outgoing) {
                    canvas.drawRoundRect(rect, RADIUS, RADIUS, strokePaint);
                }

                float ty = y + PAD;
                if (showName) {
                    canvas.drawText(sender, left + PAD, ty + 9, namePaint);
                    ty += nameH;
                }
                canvas.save();
                canvas.translate(left + PAD, ty);
                layout.draw(canvas);
                canvas.restore();
                ty += layout.getHeight() + 4;

                canvas.drawText(time, left + bubbleW - PAD - timeW, y + bubbleH - PAD + 1, timePaint);

                y += bubbleH + 7;
                exported++;
            }

            if (exported == 0) {
                if (page != null) {
                    document.finishPage(page);
                }
                page = null;
                document.close();
                return "В чате нет текстовых сообщений для экспорта";
            }
        } finally {
            if (page != null) {
                try {
                    document.finishPage(page);
                } catch (Throwable ignore) {
                }
            }
        }

        final File outFile = new File(context.getCacheDir(), "divegram_chat_" + Math.abs(dialogId) + "_" + System.currentTimeMillis() + ".pdf");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(outFile);
            document.writeTo(fos);
        } finally {
            document.close();
            if (fos != null) {
                try {
                    fos.close();
                } catch (Throwable ignore) {
                }
            }
        }

        final String saved = saveToDownloads(context, outFile, "DiveGram_" + sanitize(title) + ".pdf");
        try {
            if (outFile.exists()) {
                outFile.delete();
            }
        } catch (Throwable ignore) {
        }
        return "Экспортировано сообщений: " + exported + "\n" + saved;
    }

    private static String sanitize(String name) {
        if (TextUtils.isEmpty(name)) {
            return "chat";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String saveToDownloads(Context context, File file, String fileName) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                        copy(file, os);
                    }
                    return "PDF сохранён в Downloads/" + fileName;
                }
            }
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) {
                dir = context.getExternalFilesDir(null);
            }
            if (dir == null) {
                dir = context.getFilesDir();
            }
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File out = new File(dir, fileName);
            try (FileOutputStream os = new FileOutputStream(out)) {
                copy(file, os);
            }
            return "PDF сохранён: " + out.getAbsolutePath();
        } catch (Throwable e) {
            FileLog.e(e);
        }
        try {
            File dir = new File(context.getExternalFilesDir(null), "exports");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File out = new File(dir, fileName);
            try (FileOutputStream os = new FileOutputStream(out)) {
                copy(file, os);
            }
            return "PDF сохранён: " + out.getAbsolutePath();
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return "Не удалось сохранить PDF";
    }

    private static void copy(File from, OutputStream to) throws Exception {
        try (java.io.FileInputStream is = new java.io.FileInputStream(from)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = is.read(buf)) > 0) {
                to.write(buf, 0, n);
            }
        }
    }

    private static String senderName(int account, TLRPC.Message message, String chatTitle) {
        try {
            final long peerId = DialogObject.getPeerDialogId(message.from_id);
            if (peerId > 0) {
                TLRPC.User user = MessagesController.getInstance(account).getUser(peerId);
                if (user != null) {
                    String name = user.first_name;
                    if (!TextUtils.isEmpty(user.last_name)) {
                        name = (TextUtils.isEmpty(name) ? "" : name + " ") + user.last_name;
                    }
                    if (!TextUtils.isEmpty(name)) {
                        return name;
                    }
                    if (!TextUtils.isEmpty(user.username)) {
                        return "@" + user.username;
                    }
                }
            } else if (peerId < 0) {
                TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-peerId);
                if (chat != null && !TextUtils.isEmpty(chat.title)) {
                    return chat.title;
                }
            }
        } catch (Throwable ignore) {
        }
        return chatTitle;
    }

    private static CharSequence mediaPlaceholder(TLRPC.Message message) {
        try {
            final TLRPC.MessageMedia media = message.media;
            if (media == null) {
                return null;
            }
            if (media instanceof TLRPC.TL_messageMediaPhoto) {
                return "[Фото]";
            }
            if (media instanceof TLRPC.TL_messageMediaDocument) {
                final TLRPC.Document doc = media.document;
                if (doc != null) {
                    for (int i = 0; i < doc.attributes.size(); i++) {
                        final TLRPC.DocumentAttribute attr = doc.attributes.get(i);
                        if (attr instanceof TLRPC.TL_documentAttributeSticker) {
                            return "[Стикер]";
                        }
                        if (attr instanceof TLRPC.TL_documentAttributeAnimated) {
                            return "[GIF]";
                        }
                        if (attr instanceof TLRPC.TL_documentAttributeVideo) {
                            return message.media.ttl_seconds != 0 ? "[Кружок]" : "[Видео]";
                        }
                        if (attr instanceof TLRPC.TL_documentAttributeAudio) {
                            return ((TLRPC.TL_documentAttributeAudio) attr).voice ? "[Голосовое]" : "[Аудио]";
                        }
                    }
                    if (doc.mime_type != null && doc.mime_type.startsWith("audio/")) {
                        return "[Аудио]";
                    }
                    if (doc.mime_type != null && doc.mime_type.startsWith("image/")) {
                        return "[Изображение]";
                    }
                    if (doc.mime_type != null && doc.mime_type.startsWith("video/")) {
                        return "[Видео]";
                    }
                    return TextUtils.isEmpty(doc.file_name) ? "[Файл]" : "[Файл: " + doc.file_name + "]";
                }
            }
            if (media instanceof TLRPC.TL_messageMediaPoll) {
                return "[Опрос]";
            }
            if (media instanceof TLRPC.TL_messageMediaGeo || media instanceof TLRPC.TL_messageMediaVenue) {
                return "[Геолокация]";
            }
            if (media instanceof TLRPC.TL_messageMediaContact) {
                return "[Контакт]";
            }
            if (media instanceof TLRPC.TL_messageMediaWebPage) {
                return "[Ссылка]";
            }
            if (media instanceof TLRPC.TL_messageMediaUnsupported) {
                return "[Неподдерживаемое сообщение]";
            }
        } catch (Throwable ignore) {
        }
        return "[Медиа]";
    }

    private static void loadMessagesFromCursor(int account, MessagesStorage storage, long dialogId, ArrayList<TLRPC.Message> result, int[] failed) {
        SQLiteCursor cursor = null;
        try {
            cursor = storage.getDatabase().queryFinalized("SELECT data, date FROM messages_v2 WHERE uid = " + dialogId + " ORDER BY date ASC, mid ASC");
            while (cursor.next()) {
                NativeByteBuffer data = null;
                try {
                    data = cursor.byteBufferValue(0);
                    if (data == null) {
                        continue;
                    }
                    final TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    if (message != null) {
                        try {
                            message.readAttachPath(data, UserConfig.getInstance(account).clientUserId);
                        } catch (Throwable ignore) {
                        }
                        result.add(message);
                    }
                } catch (Throwable e) {
                    failed[0]++;
                    FileLog.e(e);
                } finally {
                    if (data != null) {
                        data.reuse();
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    private static ArrayList<TLRPC.Message> loadMessages(int account, long dialogId) {
        final ArrayList<TLRPC.Message> result = new ArrayList<>();
        final int[] failed = new int[1];
        final MessagesStorage storage = MessagesStorage.getInstance(account);
        loadMessagesFromCursor(account, storage, dialogId, result, failed);
        if (result.isEmpty()) {
            loadMessagesFromCursor(account, storage, -dialogId, result, failed);
        }
        FileLog.d("DiveGramExport dialog=" + dialogId + " loaded=" + result.size() + " failed=" + failed[0]);
        return result;
    }
}
