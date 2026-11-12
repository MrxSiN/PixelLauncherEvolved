package android.app.smartspace;

import android.app.smartspace.uitemplatedata.BaseTemplateData;
import android.content.ComponentName;
import android.os.UserHandle;

import java.util.List;

/** Compile-only stub of the {@code @SystemApi} class; the framework's own class is used at runtime. */
public final class SmartspaceTarget {

    private SmartspaceTarget() { throw new RuntimeException("Stub"); }

    public String getSmartspaceTargetId() { throw new RuntimeException("Stub"); }
    public SmartspaceAction getHeaderAction() { throw new RuntimeException("Stub"); }
    public SmartspaceAction getBaseAction() { throw new RuntimeException("Stub"); }
    public long getCreationTimeMillis() { throw new RuntimeException("Stub"); }
    public long getExpiryTimeMillis() { throw new RuntimeException("Stub"); }
    public float getScore() { throw new RuntimeException("Stub"); }
    public List<SmartspaceAction> getActionChips() { throw new RuntimeException("Stub"); }
    public List<SmartspaceAction> getIconGrid() { throw new RuntimeException("Stub"); }
    public int getFeatureType() { throw new RuntimeException("Stub"); }
    public boolean isSensitive() { throw new RuntimeException("Stub"); }
    public boolean shouldShowExpanded() { throw new RuntimeException("Stub"); }
    public String getSourceNotificationKey() { throw new RuntimeException("Stub"); }
    public ComponentName getComponentName() { throw new RuntimeException("Stub"); }
    public UserHandle getUserHandle() { throw new RuntimeException("Stub"); }
    public String getAssociatedSmartspaceTargetId() { throw new RuntimeException("Stub"); }
    public android.net.Uri getSliceUri() { throw new RuntimeException("Stub"); }
    public android.appwidget.AppWidgetProviderInfo getWidget() { throw new RuntimeException("Stub"); }
    public BaseTemplateData getTemplateData() { throw new RuntimeException("Stub"); }
    public android.widget.RemoteViews getRemoteViews() { throw new RuntimeException("Stub"); }

    public static final class Builder {
        public Builder(String id, ComponentName componentName, UserHandle userHandle) { throw new RuntimeException("Stub"); }
        public Builder setHeaderAction(SmartspaceAction action) { throw new RuntimeException("Stub"); }
        public Builder setBaseAction(SmartspaceAction action) { throw new RuntimeException("Stub"); }
        public Builder setCreationTimeMillis(long millis) { throw new RuntimeException("Stub"); }
        public Builder setExpiryTimeMillis(long millis) { throw new RuntimeException("Stub"); }
        public Builder setScore(float score) { throw new RuntimeException("Stub"); }
        public Builder setActionChips(List<SmartspaceAction> chips) { throw new RuntimeException("Stub"); }
        public Builder setIconGrid(List<SmartspaceAction> grid) { throw new RuntimeException("Stub"); }
        public Builder setFeatureType(int type) { throw new RuntimeException("Stub"); }
        public Builder setSensitive(boolean sensitive) { throw new RuntimeException("Stub"); }
        public Builder setShouldShowExpanded(boolean expanded) { throw new RuntimeException("Stub"); }
        public Builder setSourceNotificationKey(String key) { throw new RuntimeException("Stub"); }
        public Builder setAssociatedSmartspaceTargetId(String id) { throw new RuntimeException("Stub"); }
        public Builder setSliceUri(android.net.Uri uri) { throw new RuntimeException("Stub"); }
        public Builder setWidget(android.appwidget.AppWidgetProviderInfo widget) { throw new RuntimeException("Stub"); }
        public Builder setTemplateData(BaseTemplateData data) { throw new RuntimeException("Stub"); }
        public Builder setRemoteViews(android.widget.RemoteViews views) { throw new RuntimeException("Stub"); }
        public SmartspaceTarget build() { throw new RuntimeException("Stub"); }
    }
}
