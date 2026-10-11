package app.morphe.extension.newx.misc;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Iterator;

/** Observes X's rendered state. Never requests a translation or retains caption strings. */
public final class DownloadCaption {
    private static final ArrayDeque<RenderedState> STATES = new ArrayDeque<>();
    private static final int MAX_STATES = 256;

    private DownloadCaption() {}

    private static final class RenderedState {
        final WeakReference<Object> post;
        final WeakReference<Object> state;
        RenderedState(Object post, Object state) {
            this.post = new WeakReference<>(post);
            this.state = new WeakReference<>(state);
        }
    }

    public static void record(Object post, Object state) {
        if (post == null) return;
        synchronized (STATES) {
            Iterator<RenderedState> iterator = STATES.iterator();
            while (iterator.hasNext()) {
                RenderedState previous = iterator.next();
                Object owner = previous.post.get();
                if (owner == null || owner == post || previous.state.get() == null) iterator.remove();
            }
            // A null state means original text; it must also clear any previous rendered translation.
            if (state != null) STATES.addLast(new RenderedState(post, state));
            while (STATES.size() > MAX_STATES) STATES.removeFirst();
        }
    }

    /** Snapshot only the tapped post; post identity prevents another view of the same ID leaking in. */
    static String displayedText(Object post, String original) {
        synchronized (STATES) {
            Iterator<RenderedState> iterator = STATES.descendingIterator();
            while (iterator.hasNext()) {
                RenderedState record = iterator.next();
                Object owner = record.post.get();
                Object state = record.state.get();
                if (owner == null || state == null) {
                    iterator.remove();
                } else if (owner == post) {
                    String displayed = stateText(state);
                    return displayed == null ? original : displayed;
                }
            }
        }
        return original;
    }

    // Patched to direct, validated X fields; no reflection, language guesses or cached-text fallback.
    private static String stateText(Object state) {
        throw new IllegalStateException("Unpatched stateText");
    }
}
