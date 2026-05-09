package com.chancemoreland.create_coinmarket.data;

import net.neoforged.neoforge.common.ModConfigSpec;

@SuppressWarnings("deprecation")
public final class CreateCoinMarketClientConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue DEBUG_UI;
    private static final ModConfigSpec.IntValue GLOBAL_X_OFFSET;
    private static final ModConfigSpec.IntValue GLOBAL_Y_OFFSET;
    private static final ModConfigSpec.IntValue SELL_TAB_X_OFFSET;
    private static final ModConfigSpec.IntValue SELL_TAB_Y_OFFSET;
    private static final ModConfigSpec.IntValue SELL_CONTENT_X_OFFSET;
    private static final ModConfigSpec.IntValue SELL_CONTENT_Y_OFFSET;
    private static final ModConfigSpec.IntValue TEXT_Y_OFFSET;
    private static final ModConfigSpec.IntValue INPUT_TEXT_Y_OFFSET;
    private static final ModConfigSpec.IntValue BUTTON_TEXT_Y_OFFSET;
    private static final ModConfigSpec.IntValue LABEL_Y_OFFSET;
    private static final ModConfigSpec.IntValue HELPER_TEXT_Y_OFFSET;
    private static final ModConfigSpec.IntValue SUMMARY_TEXT_Y_OFFSET;
    private static final ModConfigSpec.IntValue FIELD_HEIGHT;
    private static final ModConfigSpec.IntValue FIELD_LABEL_GAP;
    private static final ModConfigSpec.IntValue FIELD_HELPER_GAP;
    private static final ModConfigSpec.IntValue FIELD_ROW_GAP;
    private static final ModConfigSpec.IntValue FIELD_HORIZONTAL_GAP;
    private static final ModConfigSpec.IntValue FIELD_PADDING_X;
    private static final ModConfigSpec.IntValue BUTTON_HEIGHT;
    private static final ModConfigSpec.IntValue BUTTON_GAP;
    private static final ModConfigSpec.IntValue CARD_PADDING;
    private static final ModConfigSpec.IntValue SECTION_GAP;
    private static final ModConfigSpec.IntValue DIVIDER_GAP;
    private static final ModConfigSpec.IntValue SUMMARY_GAP;
    private static final ModConfigSpec.IntValue SCROLL_SPEED;
    private static final ModConfigSpec.IntValue SCROLLBAR_WIDTH;
    private static final ModConfigSpec.IntValue SCROLL_VIEWPORT_Y_OFFSET;
    private static final ModConfigSpec.IntValue SCROLL_VIEWPORT_HEIGHT_OFFSET;
    private static final ModConfigSpec.BooleanValue SHOW_DEBUG_BOUNDS;
    private static final ModConfigSpec.BooleanValue SHOW_TEXT_BOUNDS;
    private static final ModConfigSpec.BooleanValue SHOW_CENTER_LINES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("ui");
        builder.push("layout");

        DEBUG_UI = builder.define("debugUi", false);
        GLOBAL_X_OFFSET = offset(builder, "globalXOffset", 0);
        GLOBAL_Y_OFFSET = offset(builder, "globalYOffset", 0);
        SELL_TAB_X_OFFSET = offset(builder, "sellTabXOffset", 0);
        SELL_TAB_Y_OFFSET = offset(builder, "sellTabYOffset", 0);
        SELL_CONTENT_X_OFFSET = offset(builder, "sellContentXOffset", 0);
        SELL_CONTENT_Y_OFFSET = offset(builder, "sellContentYOffset", 0);
        TEXT_Y_OFFSET = offset(builder, "textYOffset", 0);
        INPUT_TEXT_Y_OFFSET = offset(builder, "inputTextYOffset", 0);
        BUTTON_TEXT_Y_OFFSET = offset(builder, "buttonTextYOffset", 0);
        LABEL_Y_OFFSET = offset(builder, "labelYOffset", 0);
        HELPER_TEXT_Y_OFFSET = offset(builder, "helperTextYOffset", 0);
        SUMMARY_TEXT_Y_OFFSET = offset(builder, "summaryTextYOffset", 0);

        FIELD_HEIGHT = builder.defineInRange("fieldHeight", 20, 12, 60);
        FIELD_LABEL_GAP = builder.defineInRange("fieldLabelGap", 4, 0, 40);
        FIELD_HELPER_GAP = builder.defineInRange("fieldHelperGap", 4, 0, 40);
        FIELD_ROW_GAP = builder.defineInRange("fieldRowGap", 18, 0, 80);
        FIELD_HORIZONTAL_GAP = builder.defineInRange("fieldHorizontalGap", 20, 0, 120);
        FIELD_PADDING_X = builder.defineInRange("fieldPaddingX", 6, 0, 40);
        BUTTON_HEIGHT = builder.defineInRange("buttonHeight", 24, 12, 80);
        BUTTON_GAP = builder.defineInRange("buttonGap", 14, 0, 80);
        CARD_PADDING = builder.defineInRange("cardPadding", 18, 4, 80);
        SECTION_GAP = builder.defineInRange("sectionGap", 18, 0, 100);
        DIVIDER_GAP = builder.defineInRange("dividerGap", 12, 0, 80);
        SUMMARY_GAP = builder.defineInRange("summaryGap", 12, 0, 80);
        SCROLL_SPEED = builder.defineInRange("scrollSpeed", 18, 1, 100);
        SCROLLBAR_WIDTH = builder.defineInRange("scrollbarWidth", 6, 2, 24);
        SCROLL_VIEWPORT_Y_OFFSET = offset(builder, "scrollViewportYOffset", 0);
        SCROLL_VIEWPORT_HEIGHT_OFFSET = offset(builder, "scrollViewportHeightOffset", 0);

        SHOW_DEBUG_BOUNDS = builder.define("showDebugBounds", false);
        SHOW_TEXT_BOUNDS = builder.define("showTextBounds", false);
        SHOW_CENTER_LINES = builder.define("showCenterLines", false);

        builder.pop();
        builder.pop();
        SPEC = builder.build();
    }

    private CreateCoinMarketClientConfig() {
    }

    private static ModConfigSpec.IntValue offset(ModConfigSpec.Builder builder, String name, int value) {
        return builder.defineInRange(name, value, -100, 100);
    }

    public static UiLayout uiLayout() {
        return new UiLayout(
            DEBUG_UI.get(),
            GLOBAL_X_OFFSET.get(),
            GLOBAL_Y_OFFSET.get(),
            SELL_TAB_X_OFFSET.get(),
            SELL_TAB_Y_OFFSET.get(),
            SELL_CONTENT_X_OFFSET.get(),
            SELL_CONTENT_Y_OFFSET.get(),
            TEXT_Y_OFFSET.get(),
            INPUT_TEXT_Y_OFFSET.get(),
            BUTTON_TEXT_Y_OFFSET.get(),
            LABEL_Y_OFFSET.get(),
            HELPER_TEXT_Y_OFFSET.get(),
            SUMMARY_TEXT_Y_OFFSET.get(),
            FIELD_HEIGHT.get(),
            FIELD_LABEL_GAP.get(),
            FIELD_HELPER_GAP.get(),
            FIELD_ROW_GAP.get(),
            FIELD_HORIZONTAL_GAP.get(),
            FIELD_PADDING_X.get(),
            BUTTON_HEIGHT.get(),
            BUTTON_GAP.get(),
            CARD_PADDING.get(),
            SECTION_GAP.get(),
            DIVIDER_GAP.get(),
            SUMMARY_GAP.get(),
            SCROLL_SPEED.get(),
            SCROLLBAR_WIDTH.get(),
            SCROLL_VIEWPORT_Y_OFFSET.get(),
            SCROLL_VIEWPORT_HEIGHT_OFFSET.get(),
            SHOW_DEBUG_BOUNDS.get(),
            SHOW_TEXT_BOUNDS.get(),
            SHOW_CENTER_LINES.get()
        );
    }

    public record UiLayout(
        boolean debugUi,
        int globalXOffset,
        int globalYOffset,
        int sellTabXOffset,
        int sellTabYOffset,
        int sellContentXOffset,
        int sellContentYOffset,
        int textYOffset,
        int inputTextYOffset,
        int buttonTextYOffset,
        int labelYOffset,
        int helperTextYOffset,
        int summaryTextYOffset,
        int fieldHeight,
        int fieldLabelGap,
        int fieldHelperGap,
        int fieldRowGap,
        int fieldHorizontalGap,
        int fieldPaddingX,
        int buttonHeight,
        int buttonGap,
        int cardPadding,
        int sectionGap,
        int dividerGap,
        int summaryGap,
        int scrollSpeed,
        int scrollbarWidth,
        int scrollViewportYOffset,
        int scrollViewportHeightOffset,
        boolean showDebugBounds,
        boolean showTextBounds,
        boolean showCenterLines
    ) {
    }
}
