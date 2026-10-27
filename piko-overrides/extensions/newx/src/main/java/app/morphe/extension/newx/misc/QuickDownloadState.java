package app.morphe.extension.newx.misc;

import java.util.Objects;

/** Process-local one-shot state. Persistent settings are never used as temporary storage. */
final class QuickDownloadState {
    static final String NATIVE_IMAGES = "piko-native:images";
    static final String NATIVE_VIDEOS = "piko-native:videos";
    static final String NATIVE_TEMPLATE = "{downloadTimestamp}";

    static final class Values {
        final String imagesUri, imagesPath, videosUri, videosPath, template;
        Values(String imagesUri, String imagesPath, String videosUri, String videosPath, String template) {
            this.imagesUri = imagesUri;
            this.imagesPath = imagesPath;
            this.videosUri = videosUri;
            this.videosPath = videosPath;
            this.template = template;
        }
        static Values defaults() {
            return new Values(NATIVE_IMAGES, "Pictures/Twitter/", NATIVE_VIDEOS,
                    "Movies/Twitter/", NATIVE_TEMPLATE);
        }
        Values folder(boolean video, String uri, String label) {
            return video ? new Values(imagesUri, imagesPath, uri, label, template)
                    : new Values(uri, label, videosUri, videosPath, template);
        }
        Values template(String value) {
            return new Values(imagesUri, imagesPath, videosUri, videosPath, value);
        }
        boolean nativeFolder(boolean video) {
            return (video ? NATIVE_VIDEOS : NATIVE_IMAGES).equals(video ? videosUri : imagesUri);
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Values v)) return false;
            return Objects.equals(imagesUri, v.imagesUri) && Objects.equals(imagesPath, v.imagesPath)
                    && Objects.equals(videosUri, v.videosUri) && Objects.equals(videosPath, v.videosPath)
                    && Objects.equals(template, v.template);
        }
        @Override public int hashCode() {
            return Objects.hash(imagesUri, imagesPath, videosUri, videosPath, template);
        }
    }

    private Values pending;
    synchronized void once(Values value) { pending = value; }
    synchronized Values peek(Values saved) { return pending == null ? saved : pending; }
    synchronized Values claim(Values saved) {
        Values result = peek(saved);
        pending = null;
        return result;
    }
    synchronized void clear() { pending = null; }
}
