package android.app.smartspace.uitemplatedata;

/** Compile-only stub of the {@code @SystemApi} class; the framework's own class is used at runtime. */
public class BaseTemplateData {

    BaseTemplateData() { throw new RuntimeException("Stub"); }

    public int getTemplateType() { throw new RuntimeException("Stub"); }
    public SubItemInfo getPrimaryItem() { throw new RuntimeException("Stub"); }
    public SubItemInfo getSubtitleItem() { throw new RuntimeException("Stub"); }
    public SubItemInfo getSubtitleSupplementalItem() { throw new RuntimeException("Stub"); }
    public SubItemInfo getSupplementalLineItem() { throw new RuntimeException("Stub"); }
    public SubItemInfo getSupplementalAlarmItem() { throw new RuntimeException("Stub"); }
    public int getLayoutWeight() { throw new RuntimeException("Stub"); }

    public static class Builder {
        public Builder(int templateType) { throw new RuntimeException("Stub"); }
        public Builder setPrimaryItem(SubItemInfo item) { throw new RuntimeException("Stub"); }
        public Builder setSubtitleItem(SubItemInfo item) { throw new RuntimeException("Stub"); }
        public Builder setSubtitleSupplementalItem(SubItemInfo item) { throw new RuntimeException("Stub"); }
        public Builder setSupplementalLineItem(SubItemInfo item) { throw new RuntimeException("Stub"); }
        public Builder setSupplementalAlarmItem(SubItemInfo item) { throw new RuntimeException("Stub"); }
        public Builder setLayoutWeight(int weight) { throw new RuntimeException("Stub"); }
        public BaseTemplateData build() { throw new RuntimeException("Stub"); }
    }

    public static final class SubItemInfo {
        SubItemInfo() { throw new RuntimeException("Stub"); }
        public Text getText() { throw new RuntimeException("Stub"); }
        public Icon getIcon() { throw new RuntimeException("Stub"); }

        public static final class Builder {
            public Builder() { throw new RuntimeException("Stub"); }
            public Builder setText(Text text) { throw new RuntimeException("Stub"); }
            public Builder setIcon(Icon icon) { throw new RuntimeException("Stub"); }
            public Builder setTapAction(TapAction action) { throw new RuntimeException("Stub"); }
            public SubItemInfo build() { throw new RuntimeException("Stub"); }
        }
    }
}
