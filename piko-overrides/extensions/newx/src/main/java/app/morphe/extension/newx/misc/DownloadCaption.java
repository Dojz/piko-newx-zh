package app.morphe.extension.newx.misc;

import android.content.res.Resources;
import android.os.Looper;
import android.os.SystemClock;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

import app.morphe.extension.newx.utils.NewXUtils;

public final class DownloadCaption {
    private static final Map<String, Session> SESSIONS = new LinkedHashMap<>(64, 0.75f, true);
    private static final long TIMEOUT_MS = 20000;

    private DownloadCaption() {
    }

    private static final class Session {
        WeakReference<Object> state = new WeakReference<>(null);
        int waiters;
        WeakReference<Object> sink = new WeakReference<>(null);
        Object event;
        String text;
        boolean requested;
        RuntimeException failure;
    }

    private static String key(Object post) {
        String id = postId(post);
        if (id == null || id.isEmpty()) throw new IllegalStateException("Missing translation post");
        return Resources.getSystem().getConfiguration().getLocales().get(0).toLanguageTag() + ":" + id;
    }

    public static void record(Object post, Object state, Object event) {
        if (post == null || state == null) return;
        Object sink = eventSink(state);
        if (sink == null) return;
        synchronized (SESSIONS) {
            Session session = SESSIONS.computeIfAbsent(key(post), ignored -> new Session());
            session.state = new WeakReference<>(state);
            if (session.requested) {
                String text = stateText(state);
                if (text != null && !text.trim().isEmpty()) session.text = text;
            }
            if (sink != null && !session.requested) {
                session.sink = new WeakReference<>(sink);
                session.event = event;
            }
            while (SESSIONS.size() > 256) {
                String oldest = SESSIONS.keySet().iterator().next();
                if (SESSIONS.get(oldest).requested && SESSIONS.get(oldest).text == null) break;
                SESSIONS.remove(oldest);
            }
            SESSIONS.notifyAll();
        }
    }

    static String translate(Object post, String text) {
        if (text == null || text.trim().isEmpty()) return null;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("Translation must run off the main thread");
        }
        String source = sourceLanguage(post);
        Locale target = Resources.getSystem().getConfiguration().getLocales().get(0);
        if (source != null && !source.isEmpty()
                && Locale.forLanguageTag(source.replace('_', '-')).getLanguage().equals(target.getLanguage())) {
            return text;
        }
        String cached = cachedText(post);
        if (cached != null && !cached.trim().isEmpty()) return cached;
        String key = key(post);
        long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
        synchronized (SESSIONS) {
            Session session = SESSIONS.get(key);
            if (session == null) throw new IllegalStateException("Native translation is unavailable");
            cached = stateText(session.state.get());
            if (cached != null && !cached.trim().isEmpty()) return cached;
            if (session.text != null) return session.text;
            if (!session.requested) {
                Object sink = session.sink.get();
                if (sink == null) throw new IllegalStateException("Native translation is unavailable");
                session.requested = true;
                session.failure = null;
                NewXUtils.runOnUiThread(() -> {
                    try {
                        dispatch(sink, session.event);
                    } catch (RuntimeException exception) {
                        synchronized (SESSIONS) {
                            session.failure = exception;
                            SESSIONS.notifyAll();
                        }
                    }
                });
            }
            session.waiters++;
            try {
                while (session.text == null && session.failure == null) {
                    long remaining = deadline - SystemClock.elapsedRealtime();
                    if (remaining <= 0) throw new IllegalStateException("Native translation timed out");
                    SESSIONS.wait(remaining);
                }
                if (session.failure != null) throw session.failure;
                return session.text;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Native translation was interrupted", exception);
            } finally {
                if (--session.waiters == 0) {
                    session.requested = false;
                    session.text = null;
                    session.failure = null;
                }
            }
        }
    }

    private static String sourceLanguage(Object post) {
        throw new IllegalStateException("Unpatched sourceLanguage");
    }

    private static String postId(Object post) {
        throw new IllegalStateException("Unpatched postId");
    }

    private static String cachedText(Object post) {
        throw new IllegalStateException("Unpatched cachedText");
    }

    private static String stateText(Object state) {
        throw new IllegalStateException("Unpatched stateText");
    }

    private static Object eventSink(Object state) {
        throw new IllegalStateException("Unpatched eventSink");
    }

    private static void dispatch(Object sink, Object event) {
        throw new IllegalStateException("Unpatched dispatch");
    }
}
