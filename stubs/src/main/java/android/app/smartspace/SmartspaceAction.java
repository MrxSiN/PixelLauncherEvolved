package android.app.smartspace;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;

/** Compile-only stub of the {@code @SystemApi} class; the framework's own class is used at runtime. */
public final class SmartspaceAction {

    private SmartspaceAction() { throw new RuntimeException("Stub"); }

    public String getId() { throw new RuntimeException("Stub"); }

    public static final class Builder {
        public Builder(String id, String title) { throw new RuntimeException("Stub"); }
        public Builder setIcon(Icon icon) { throw new RuntimeException("Stub"); }
        public Builder setSubtitle(CharSequence subtitle) { throw new RuntimeException("Stub"); }
        public Builder setContentDescription(CharSequence description) { throw new RuntimeException("Stub"); }
        public Builder setPendingIntent(PendingIntent intent) { throw new RuntimeException("Stub"); }
        public Builder setIntent(Intent intent) { throw new RuntimeException("Stub"); }
        public SmartspaceAction build() { throw new RuntimeException("Stub"); }
    }
}
