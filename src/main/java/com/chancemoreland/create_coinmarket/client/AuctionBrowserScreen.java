package com.chancemoreland.create_coinmarket.client;

import com.chancemoreland.create_coinmarket.data.AuctionConfig;
import com.chancemoreland.create_coinmarket.data.CreateCoinMarketClientConfig;
import com.chancemoreland.create_coinmarket.data.MarketScreenData;
import com.chancemoreland.create_coinmarket.economy.MarketChartService;
import com.chancemoreland.create_coinmarket.network.AuctionNotificationPayload;
import com.chancemoreland.create_coinmarket.network.AuctionNetwork;
import com.chancemoreland.create_coinmarket.util.ItemStackCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class AuctionBrowserScreen extends Screen implements MarketDataReceiver {
    private static final int BACKGROUND = argb(0x07111F);
    private static final int PANEL = argb(0x0B1B2E);
    private static final int RAISED = argb(0x10243A);
    private static final int ACCENT = argb(0x1E90FF);
    private static final int TEXT = argb(0xFFFFFF);
    private static final int MUTED = argb(0xB7C5D8);
    private static final int DANGER = argb(0xFF4D4D);
    private static final int SUCCESS = argb(0x4DFF88);
    private static final int WARNING = argb(0xFFD166);
    private static final int GRID = argb(0x173653);
    private static final int SELL_MAX_CONTENT_WIDTH = 760;
    private static final int SELL_OUTER_PADDING = 14;
    private static final int SELL_PANEL_GAP = 12;
    private static final int SELL_PANEL_PADDING = 12;
    private static final int SELL_TITLE_HEIGHT = 16;
    private static final int SELL_TITLE_TO_CARD_GAP = 8;
    private static final int SELL_ITEM_CARD_HEIGHT = 68;
    private static final int SELL_HEADER_TO_TABS_GAP = 10;
    private static final int SELL_TAB_HEIGHT = 26;
    private static final int SELL_TABS_TO_FORM_GAP = 8;
    private static final int SELL_CARD_TITLE_GAP = 6;
    private static final int SELL_DESCRIPTION_LINE_GAP = 2;
    private static final float SELL_TITLE_SCALE = 1.0F;
    private static final float SELL_DESCRIPTION_SCALE = 0.76F;
    private static final float SELL_DESCRIPTION_SECONDARY_SCALE = 0.70F;
    private static final int SELL_DESCRIPTION_TO_FIELDS_GAP = 8;
    private static final int SELL_FIELD_GAP = 10;
    private static final int SELL_FIELD_ROW_GAP = 8;
    private static final int SELL_FIELD_HEIGHT = 22;
    private static final int SELL_FIELD_LABEL_GAP = 4;
    private static final int SELL_FIELD_HELPER_GAP = 5;
    private static final int SELL_FIELD_PADDING_X = 6;
    private static final float SELL_FIELD_LABEL_SCALE = 0.78F;
    private static final float SELL_FIELD_HELPER_SCALE = 0.62F;
    private static final int SELL_FIELDS_TO_DIVIDER_GAP = 10;
    private static final int SELL_SUMMARY_GAP = 8;
    private static final int SELL_SUMMARY_CARD_PADDING = 8;
    private static final int SELL_SUMMARY_LINE_HEIGHT = 14;
    private static final int SELL_SUMMARY_LABEL_WIDTH = 158;
    private static final float SELL_SUMMARY_TITLE_SCALE = 0.86F;
    private static final float SELL_SUMMARY_TEXT_SCALE = 0.80F;
    private static final int SELL_BUTTON_GAP = 10;
    private static final int SELL_BUTTON_HEIGHT = 26;

    private final List<UiElement> elements = new ArrayList<>();
    private final List<UiElement> sellElements = new ArrayList<>();
    private MarketScreenData data = MarketScreenData.empty("dashboard", 0);
    private String mode = "dashboard";
    private int page;
    private String sort = "newest";
    private String query = "";
    private EditBox searchBox;
    private final List<EditBox> editBoxes = new ArrayList<>();
    private final List<EditBox> sellEditBoxes = new ArrayList<>();
    private EditBox fixedPriceBox;
    private EditBox quantityBox;
    private EditBox fixedDurationBox;
    private EditBox auctionStartBox;
    private EditBox auctionDurationBox;
    private EditBox auctionBuyoutBox;
    private EditBox auctionIncrementBox;
    private EditBox auctionQuantityBox;
    private EditBox bidAmountBox;
    private MarketScreenData.ListingView pendingBuy;
    private String sellListingType = "fixed";
    private int sellScrollOffset;
    private int sellMaxScroll;
    private int sellViewportX;
    private int sellViewportY;
    private int sellViewportW;
    private int sellViewportH;
    private int sellContentHeight;
    private boolean buildingSellContent;
    private boolean draggingSellScrollbar;
    private int sellScrollbarGrabOffset;
    private Boolean showDebugBoundsOverride;
    private Boolean showTextBoundsOverride;
    private Boolean showCenterLinesOverride;
    private int runtimeInputTextYOffset;
    private int runtimeButtonTextYOffset;
    private int runtimeSellContentYOffset;
    private final List<Notification> notifications = new ArrayList<>();

    public AuctionBrowserScreen(String initialMode, int initialPage) {
        super(Component.literal("Create: CoinMarket"));
        String normalized = AuctionConfig.normalizeMode(initialMode);
        this.mode = "all".equals(normalized) ? "browse" : normalized;
        this.page = Math.max(0, initialPage);
    }

    @Override
    protected void init() {
        super.init();
        rebuildLayout();
        requestData();
    }

    @Override
    public void create_coinmarket$acceptMarketData(MarketScreenData data) {
        this.data = data;
        this.mode = data.mode();
        this.page = data.page();
        this.sort = data.sort();
        this.query = data.query();
        rebuildLayout();
    }

    @Override
    public void create_coinmarket$acceptNotification(AuctionNotificationPayload payload) {
        long now = System.currentTimeMillis();
        notifications.add(new Notification(payload.notificationType(), payload.title(), payload.message(), now, Math.max(1000L, payload.durationTicks() * 50L)));
        while (notifications.size() > 6) {
            notifications.removeFirst();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, BACKGROUND);
        for (UiElement element : List.copyOf(elements)) {
            element.render(graphics, mouseX, mouseY, partialTick);
        }
        if (isSellMode()) {
            renderSellViewport(graphics, mouseX, mouseY, partialTick);
        }
        if (pendingBuy == null && searchBox != null) {
            searchBox.render(graphics, mouseX, mouseY, partialTick);
        }
        for (EditBox box : editBoxes) {
            if (isSellMode() && sellEditBoxes.contains(box)) {
                continue;
            }
            if (pendingBuy == null && box != searchBox) {
                box.render(graphics, mouseX, mouseY, partialTick);
            } else if (pendingBuy != null && box == bidAmountBox) {
                box.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        for (UiElement element : List.copyOf(elements)) {
            element.renderTooltip(graphics, mouseX, mouseY);
        }
        if (isSellMode()) {
            renderSellTooltips(graphics, mouseX, mouseY);
        }
        renderDebugOverlay(graphics);
        renderNotifications(graphics);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pendingBuy != null) {
            if (bidAmountBox != null && bidAmountBox.mouseClicked(mouseX, mouseY, button)) {
                setFocused(bidAmountBox);
                return true;
            }
            for (UiElement element : List.copyOf(elements).reversed()) {
                if (element.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            return true;
        }
        if (isSellMode()) {
            for (EditBox box : editBoxes) {
                if (!sellEditBoxes.contains(box) && box.mouseClicked(mouseX, mouseY, button)) {
                    setFocused(box);
                    return true;
                }
            }
            if (startSellScrollbarDrag(mouseX, mouseY)) {
                return true;
            }
            if (insideSellViewport(mouseX, mouseY)) {
                double contentY = mouseY + sellScrollOffset;
                for (EditBox box : sellEditBoxes) {
                    if (box.mouseClicked(mouseX, contentY, button)) {
                        setFocused(box);
                        return true;
                    }
                }
                for (UiElement element : List.copyOf(sellElements).reversed()) {
                    if (element.mouseClicked(mouseX, contentY, button)) {
                        return true;
                    }
                }
                return true;
            }
            for (UiElement element : List.copyOf(elements).reversed()) {
                if (element.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            return true;
        }
        for (EditBox box : editBoxes) {
            if (box.mouseClicked(mouseX, mouseY, button)) {
                setFocused(box);
                return true;
            }
        }
        for (UiElement element : List.copyOf(elements).reversed()) {
            if (element.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSellScrollbar = false;
        if (searchBox != null) {
            searchBox.mouseReleased(mouseX, mouseY, button);
        }
        for (EditBox box : editBoxes) {
            box.mouseReleased(mouseX, mouseY, button);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSellScrollbar) {
            updateSellScrollFromMouse(mouseY);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isSellMode() && insideSellViewport(mouseX, mouseY)) {
            sellScrollOffset = Mth.clamp((int) Math.round(sellScrollOffset - scrollY * cfgScrollSpeed()), 0, sellMaxScroll);
            return true;
        }
        if (scrollY < 0D && page < data.maxPage()) {
            page++;
            requestData();
        } else if (scrollY > 0D && page > 0) {
            page--;
            requestData();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && pendingBuy != null) {
            pendingBuy = null;
            rebuildLayout();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode))) {
            onClose();
            return true;
        }
        if (handleDebugHotkeys(keyCode, modifiers) || handleLayoutNudge(keyCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.query = searchBox == null ? this.query : searchBox.getValue();
            this.page = 0;
            requestData();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN || keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (page < data.maxPage()) {
                page++;
                requestData();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_LEFT) {
            if (page > 0) {
                page--;
                requestData();
            }
            return true;
        }
        for (EditBox box : editBoxes) {
            if (box.isFocused()) {
                box.keyPressed(keyCode, scanCode, modifiers);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (EditBox box : editBoxes) {
            if (box.isFocused()) {
                box.charTyped(codePoint, modifiers);
                return true;
            }
        }
        return true;
    }

    private boolean handleDebugHotkeys(int keyCode, int modifiers) {
        if (!isSellMode()) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_F6) {
            boolean value = !showDebugBounds();
            showDebugBoundsOverride = value;
            notifyLocal("info", "Layout Debug", "showDebugBounds = " + value, 100);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F7) {
            boolean value = !showTextBounds();
            showTextBoundsOverride = value;
            notifyLocal("info", "Layout Debug", "showTextBounds = " + value, 100);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F8) {
            boolean value = !showCenterLines();
            showCenterLinesOverride = value;
            notifyLocal("info", "Layout Debug", "showCenterLines = " + value, 100);
            return true;
        }
        return false;
    }

    private boolean handleLayoutNudge(int keyCode, int modifiers) {
        if (!isSellMode() || (keyCode != GLFW.GLFW_KEY_UP && keyCode != GLFW.GLFW_KEY_DOWN)) {
            return false;
        }
        int delta = keyCode == GLFW.GLFW_KEY_UP ? -1 : 1;
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && hasFocusedSellInput()) {
            runtimeInputTextYOffset = Mth.clamp(runtimeInputTextYOffset + delta, -100, 100);
            notifyLocal("info", "Layout Debug", "inputTextYOffset = " + cfgInputTextYOffset(), 80);
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            runtimeButtonTextYOffset = Mth.clamp(runtimeButtonTextYOffset + delta, -100, 100);
            notifyLocal("info", "Layout Debug", "buttonTextYOffset = " + cfgButtonTextYOffset(), 80);
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) {
            runtimeSellContentYOffset = Mth.clamp(runtimeSellContentYOffset + delta, -100, 100);
            rebuildLayout();
            notifyLocal("info", "Layout Debug", "sellContentYOffset = " + cfgSellContentYOffset(), 80);
            return true;
        }
        return false;
    }

    private boolean hasFocusedSellInput() {
        for (EditBox box : sellEditBoxes) {
            if (box.isFocused()) {
                return true;
            }
        }
        return false;
    }

    private void rebuildLayout() {
        elements.clear();
        sellElements.clear();
        editBoxes.clear();
        sellEditBoxes.clear();
        clearWidgets();
        sellMaxScroll = 0;
        sellContentHeight = 0;
        sellViewportX = 0;
        sellViewportY = 0;
        sellViewportW = 0;
        sellViewportH = 0;
        draggingSellScrollbar = false;
        buildLayout();
    }

    private void requestData() {
        AuctionNetwork.requestData(mode, page, sort, query);
    }

    private void sendAction(String action, String listingId) {
        AuctionNetwork.sendAction(action, listingId, mode, page, sort, query);
    }

    private void sendAction(String action, String listingId, long amount, int quantity, String extra) {
        AuctionNetwork.sendAction(action, listingId, mode, page, sort, query, amount, quantity, extra);
    }

    private void buildLayout() {
        int margin = 14;
        int outerW = Math.min(this.width - margin * 2, 1180);
        int outerH = Math.min(this.height - margin * 2, 720);
        int x = (this.width - outerW) / 2;
        int y = (this.height - outerH) / 2;
        int headerH = 58;
        int sidebarW = 154;
        int footerH = 32;
        int contentX = x + sidebarW + 14;
        int contentY = y + headerH + 12;
        int contentW = outerW - sidebarW - 28;
        int contentH = outerH - headerH - footerH - 24;

        elements.add(new RectElement(x, y, outerW, outerH, PANEL, ACCENT));
        buildHeader(x, y, outerW);
        buildSidebar(x + 10, y + headerH + 8, sidebarW - 18, contentH);
        buildContent(contentX, contentY, contentW, contentH);
        buildFooter(contentX, y + outerH - footerH - 8, contentW);

        if (pendingBuy != null) {
            buildBuyOverlay(x, y, outerW, outerH);
        }
    }

    private void buildHeader(int x, int y, int width) {
        elements.add(new TextElement(x + 18, y + 13, "Create: CoinMarket", 0xFFFFFF, 1.25F));
        elements.add(new TextElement(x + 20, y + 36, "SMP live market dashboard", 0xB7C5D8, 0.85F));
        if (AuctionConfig.uiShowPlayerBalance()) {
            elements.add(new MetricPill(x + 235, y + 12, 142, 34, "Total", format(data.totalSpendableBalance()), SUCCESS));
            String bank = data.hasBankCard() ? format(data.bankBalance()) : "no card";
            if (!AuctionConfig.showBankBalanceInHeader()) {
                bank = "hidden";
            }
            elements.add(new TextElement(x + 235, y + 48, "Coins " + format(data.coinBalance()) + " | Bank " + bank + " | " + data.activePaymentSource(), 0xB7C5D8, 0.72F));
        }
        elements.add(new MetricPill(x + 386, y + 12, 152, 34, "24h Volume", format(data.summary().volume24h()), ACCENT));
        elements.add(new MetricPill(x + 548, y + 12, 142, 34, "Active", String.valueOf(data.summary().totalActiveListings()), WARNING));

        int searchW = Math.min(230, Math.max(130, width - 870));
        searchBox = new EditBox(font, x + width - searchW - 96, y + 17, searchW, 20, Component.literal("Search"));
        searchBox.setValue(query);
        searchBox.setHint(Component.literal("Search item or seller"));
        searchBox.setMaxLength(128);
        searchBox.setTextColor(0xFFFFFF);
        searchBox.setTextColorUneditable(0xB7C5D8);
        editBoxes.add(searchBox);
        elements.add(new ButtonElement(x + width - 84, y + 15, 68, 24, "Refresh", ACCENT, () -> {
            this.query = searchBox.getValue();
            requestData();
        }));
    }

    private void buildSidebar(int x, int y, int width, int height) {
        elements.add(new RectElement(x, y, width, height, argb(0x081827), 0));
        int rowY = y + 10;
        rowY = sidebarButton(x + 8, rowY, width - 16, "Dashboard", "dashboard");
        rowY = sidebarButton(x + 8, rowY, width - 16, "Browse", "browse");
        rowY = sidebarButton(x + 8, rowY, width - 16, "Sell", "sell");
        rowY = sidebarButton(x + 8, rowY, width - 16, "My Listings", "my");
        rowY = sidebarButton(x + 8, rowY, width - 16, "Collection", "collection");
        rowY = sidebarButton(x + 8, rowY, width - 16, "Economy", "economy");
        if (data.admin()) {
            sidebarButton(x + 8, rowY + 6, width - 16, "Admin", "adminpage");
        }
    }

    private void renderNotifications(GuiGraphics graphics) {
        long now = System.currentTimeMillis();
        notifications.removeIf(notification -> notification.expired(now));
        int width = 324;
        int x = Math.max(8, this.width - width - 22);
        int y = 82;
        for (Notification notification : List.copyOf(notifications).reversed()) {
            long remaining = notification.remaining(now);
            int fadeAlpha = remaining < 800L ? Math.max(70, (int) (remaining * 230L / 800L)) : 230;
            int color = notificationColor(notification.type());
            List<String> lines = wrap(notification.message(), width - 30, 2);
            int height = 42 + lines.size() * 10;
            graphics.fill(x, y, x + width, y + height, alpha(PANEL, fadeAlpha));
            graphics.fill(x, y, x + 4, y + height, alpha(color, fadeAlpha));
            border(graphics, x, y, width, height, alpha(color, Math.min(255, fadeAlpha + 20)));
            graphics.enableScissor(x, y, x + width, y + height);
            graphics.drawString(font, notification.title().isBlank() ? notification.type().toUpperCase(Locale.ROOT) : notification.title(), x + 12, y + 8, alpha(color, 255), false);
            int lineY = y + 24;
            for (String line : lines) {
                graphics.drawString(font, line, x + 12, lineY, TEXT, false);
                lineY += 10;
            }
            graphics.disableScissor();
            y += height + 6;
        }
    }

    private void renderSellViewport(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (sellViewportW <= 0 || sellViewportH <= 0) {
            return;
        }
        int contentMouseY = mouseY + sellScrollOffset;
        graphics.enableScissor(sellViewportX, sellViewportY, sellViewportX + sellViewportW, sellViewportY + sellViewportH);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -sellScrollOffset, 0);
        for (UiElement element : List.copyOf(sellElements)) {
            element.render(graphics, mouseX, contentMouseY, partialTick);
        }
        for (EditBox box : sellEditBoxes) {
            if (pendingBuy == null) {
                box.render(graphics, mouseX, contentMouseY, partialTick);
            }
        }
        renderSellDebugInViewport(graphics);
        graphics.pose().popPose();
        graphics.disableScissor();
        renderSellScrollbar(graphics, mouseX, mouseY);
    }

    private void renderSellTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!insideSellViewport(mouseX, mouseY)) {
            return;
        }
        int contentMouseY = mouseY + sellScrollOffset;
        for (UiElement element : List.copyOf(sellElements)) {
            element.renderTooltip(graphics, mouseX, contentMouseY, mouseX, mouseY);
        }
    }

    private void renderSellScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        if (sellMaxScroll <= 0) {
            return;
        }
        int scrollbarW = cfgScrollbarWidth();
        int trackX = sellViewportX + sellViewportW - scrollbarW - 3;
        int trackY = sellViewportY + 4;
        int trackH = Math.max(1, sellViewportH - 8);
        int thumbH = sellScrollbarThumbHeight();
        int thumbY = sellScrollbarThumbY(thumbH);
        graphics.fill(trackX, trackY, trackX + scrollbarW, trackY + trackH, alpha(RAISED, 150));
        graphics.fill(trackX, thumbY, trackX + scrollbarW, thumbY + thumbH, hovered(trackX - 2, thumbY, scrollbarW + 4, thumbH, mouseX, mouseY) ? alpha(ACCENT, 245) : alpha(ACCENT, 190));
    }

    private void renderSellDebugInViewport(GuiGraphics graphics) {
        if (!isSellMode() || !showAnyDebug()) {
            return;
        }
        for (UiElement element : List.copyOf(sellElements)) {
            element.renderDebug(graphics);
        }
        if (showDebugBounds()) {
            for (EditBox box : sellEditBoxes) {
                debugRect(graphics, box.getX(), box.getY(), box.getWidth(), box.getHeight(), WARNING);
                if (showCenterLines()) {
                    debugCenterLines(graphics, box.getX(), box.getY(), box.getWidth(), box.getHeight());
                }
            }
        }
    }

    private void renderDebugOverlay(GuiGraphics graphics) {
        if (!isSellMode() || !showAnyDebug()) {
            return;
        }
        if (sellViewportW > 0 && sellViewportH > 0) {
            if (showDebugBounds()) {
                border(graphics, sellViewportX, sellViewportY, sellViewportW, sellViewportH, alpha(WARNING, 230));
            }
            if (showCenterLines()) {
                int cx = sellViewportX + sellViewportW / 2;
                int cy = sellViewportY + sellViewportH / 2;
                graphics.fill(cx, sellViewportY, cx + 1, sellViewportY + sellViewportH, alpha(SUCCESS, 160));
                graphics.fill(sellViewportX, cy, sellViewportX + sellViewportW, cy + 1, alpha(SUCCESS, 160));
            }
        }
        int overlayW = 236;
        int overlayH = 104;
        int x = Math.max(8, sellViewportX + 8);
        int y = Math.max(8, sellViewportY + 8);
        graphics.fill(x, y, x + overlayW, y + overlayH, alpha(PANEL, 230));
        border(graphics, x, y, overlayW, overlayH, alpha(ACCENT, 220));
        int lineY = y + 8;
        graphics.drawString(font, "UI Layout Debug", x + 8, lineY, TEXT, false);
        lineY += 13;
        graphics.drawString(font, "F6 bounds: " + showDebugBounds() + "  F7 text: " + showTextBounds(), x + 8, lineY, MUTED, false);
        lineY += 12;
        graphics.drawString(font, "F8 center: " + showCenterLines(), x + 8, lineY, MUTED, false);
        lineY += 12;
        graphics.drawString(font, "inputTextYOffset = " + cfgInputTextYOffset(), x + 8, lineY, MUTED, false);
        lineY += 12;
        graphics.drawString(font, "buttonTextYOffset = " + cfgButtonTextYOffset(), x + 8, lineY, MUTED, false);
        lineY += 12;
        graphics.drawString(font, "sellContentYOffset = " + cfgSellContentYOffset(), x + 8, lineY, MUTED, false);
        lineY += 12;
        graphics.drawString(font, "scroll = " + sellScrollOffset + "/" + sellMaxScroll, x + 8, lineY, MUTED, false);
    }

    private void debugRect(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        border(graphics, x, y, width, height, alpha(color, 210));
    }

    private void debugTextRect(GuiGraphics graphics, int x, int y, String text, float scale, int color) {
        if (text == null || text.isBlank()) {
            return;
        }
        int textW = Math.max(1, Math.round(font.width(text) * scale));
        int textH = scaledLineHeight(scale);
        debugRect(graphics, x, y + cfgTextYOffset(), textW, textH, color);
    }

    private void debugCenterLines(GuiGraphics graphics, int x, int y, int width, int height) {
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        graphics.fill(centerX, y, centerX + 1, y + height, alpha(SUCCESS, 150));
        graphics.fill(x, centerY, x + width, centerY + 1, alpha(SUCCESS, 150));
    }

    private int notificationColor(String type) {
        return switch (type == null ? "info" : type.toLowerCase(Locale.ROOT)) {
            case "success" -> SUCCESS;
            case "error" -> DANGER;
            case "warning" -> WARNING;
            default -> ACCENT;
        };
    }

    private int sidebarButton(int x, int y, int width, String label, String targetMode) {
        int color = mode.equals(targetMode) ? ACCENT : RAISED;
        elements.add(new ButtonElement(x, y, width, 24, label, color, () -> {
            this.mode = targetMode;
            this.page = 0;
            this.pendingBuy = null;
            requestData();
        }));
        return y + 29;
    }

    private void buildContent(int x, int y, int width, int height) {
        elements.add(new RectElement(x, y, width, height, argb(0x071522), 0));
        switch (mode) {
            case "dashboard" -> buildDashboard(x, y, width, height);
            case "sell" -> buildSell(x, y, width, height);
            case "collection" -> buildCollection(x, y, width, height);
            case "pricecheck" -> buildPriceCheck(x, y, width, height);
            case "economy" -> buildEconomy(x, y, width, height);
            case "adminpage" -> buildAdmin(x, y, width, height);
            default -> buildMarket(x, y, width, height, false);
        }
    }

    private void buildDashboard(int x, int y, int width, int height) {
        elements.add(new TextElement(x + 14, y + 12, "Dashboard", 0xFFFFFF, 1.05F));
        int cardW = Math.max(132, (width - 64) / 4);
        int cardY = y + 34;
        elements.add(new StatCard(x + 14, cardY, cardW, 54, "Active Listings", data.summary().totalActiveListings(), "all categories", ACCENT));
        elements.add(new StatCard(x + 24 + cardW, cardY, cardW, 54, "Public Listings", data.summary().publicListings(), "player market", SUCCESS));
        elements.add(new StatCard(x + 34 + cardW * 2, cardY, cardW, 54, "Admin Listings", data.summary().adminListings(), "server shop", WARNING));
        elements.add(new StatCard(x + 44 + cardW * 3, cardY, cardW, 54, "Coins Sunk", data.summary().serverSinkTotal(), "admin + taxes", DANGER));

        if (AuctionConfig.enableCharts() && AuctionConfig.uiShowEconomyGraphs()) {
            elements.add(new LineChartElement(x + 14, y + 104, width / 2 - 22, Math.min(180, height - 122), "Market Volume", data.volumeHistory()));
            elements.add(new BarChartElement(x + width / 2 + 8, y + 104, width / 2 - 22, Math.min(180, height - 122), "Top Items", data.topItems()));
        }

        int lowerY = y + Math.min(304, height - 112);
        elements.add(new TextElement(x + 16, lowerY, "Most traded: " + data.summary().mostTradedItem(), 0xB7C5D8, 0.9F));
        elements.add(new TextElement(x + 16, lowerY + 14, "Average sale: " + stat(data.summary().averageSalePrice()) + " | 7d volume: " + format(data.summary().volume7d()), 0xB7C5D8, 0.9F));
    }

    private void buildMarket(int x, int y, int width, int height, boolean adminTools) {
        String title = switch (mode) {
            case "admin" -> "Admin Shop";
            case "public" -> "Player Market";
            case "my" -> "My Listings";
            default -> "Browse";
        };
        elements.add(new TextElement(x + 14, y + 12, title, 0xFFFFFF, 1.05F));
        buildSortRow(x + 14, y + 33);
        if (!"my".equals(mode)) {
            elements.add(new ButtonElement(x + 330, y + 33, 44, 22, "All", mode.equals("browse") || mode.equals("all") ? ACCENT : RAISED, () -> switchMode("browse")));
            elements.add(new ButtonElement(x + 380, y + 33, 54, 22, "Admin", mode.equals("admin") ? ACCENT : RAISED, () -> switchMode("admin")));
            elements.add(new ButtonElement(x + 440, y + 33, 58, 22, "Public", mode.equals("public") ? ACCENT : RAISED, () -> switchMode("public")));
        }

        int cardW = Math.max(220, Math.min(AuctionConfig.uiCardWidth() + 112, (width - 30) / 2));
        int cardH = Math.max(88, AuctionConfig.uiCardHeight() + 22);
        int cols = Math.max(1, width / (cardW + 10));
        int startX = x + 14;
        int startY = y + 66;
        int maxRows = Math.max(1, (height - 84) / (cardH + 10));
        int maxCards = cols * maxRows;
        List<MarketScreenData.ListingView> listings = data.listings().stream().limit(maxCards).toList();
        if (listings.isEmpty()) {
            elements.add(new TextElement(startX, startY + 20, "No listings match this view.", 0xB7C5D8, 1.0F));
            return;
        }
        for (int i = 0; i < listings.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            elements.add(new ListingCard(startX + col * (cardW + 10), startY + row * (cardH + 10), cardW, cardH, listings.get(i), adminTools || "adminpage".equals(mode)));
        }
    }

    private void buildSortRow(int x, int y) {
        elements.add(new ButtonElement(x, y, 74, 22, "Cheapest", sort.equals("cheapest") ? ACCENT : RAISED, () -> setSort("cheapest")));
        elements.add(new ButtonElement(x + 80, y, 64, 22, "Newest", sort.equals("newest") ? ACCENT : RAISED, () -> setSort("newest")));
        elements.add(new ButtonElement(x + 150, y, 84, 22, "Ending Soon", sort.equals("ending") ? ACCENT : RAISED, () -> setSort("ending")));
        elements.add(new ButtonElement(x + 240, y, 72, 22, "Volume", sort.equals("volume") ? ACCENT : RAISED, () -> setSort("volume")));
    }

    private void setSort(String sort) {
        this.sort = sort;
        this.page = 0;
        requestData();
    }

    private void switchMode(String targetMode) {
        this.mode = targetMode;
        this.page = 0;
        this.pendingBuy = null;
        requestData();
    }

    private void buildSell(int x, int y, int width, int height) {
        int baseX = x + cfgGlobalXOffset() + cfgSellTabXOffset();
        int baseY = y + cfgGlobalYOffset() + cfgSellTabYOffset();
        sellViewportX = baseX;
        sellViewportY = baseY + cfgScrollViewportYOffset();
        sellViewportW = width;
        sellViewportH = Math.max(32, height + cfgScrollViewportHeightOffset() - Math.max(0, cfgScrollViewportYOffset()));
        sellElements.clear();
        sellEditBoxes.clear();
        buildingSellContent = true;
        SellTabLayout layout = layoutSellTab(baseX, baseY, width, height);
        try {
            addElement(new TextElement(layout.titleX(), layout.titleY(), "Create Listing", 0xFFFFFF, 1.05F));
            ItemStack hand = minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
            int heldCount = hand.isEmpty() ? 0 : hand.getCount();
            int defaultQuantity = Math.max(1, heldCount);
            long fixedDefaultDuration = Math.max(1L, AuctionConfig.publicDurationMillis() / (60L * 60L * 1000L));

            drawSellItemCard(layout.itemCard(), hand);
            renderListingTypeTabs(layout.tabs(), sellListingType);
            if ("auction".equals(sellListingType)) {
                renderAuctionForm(layout.form(), heldCount, defaultQuantity);
            } else {
                renderFixedPriceForm(layout.form(), heldCount, defaultQuantity, fixedDefaultDuration);
            }
        } finally {
            buildingSellContent = false;
        }
        sellContentHeight = Math.max(0, layout.contentBottom() - sellViewportY);
        sellMaxScroll = Math.max(0, sellContentHeight - sellViewportH);
        sellScrollOffset = Mth.clamp(sellScrollOffset, 0, sellMaxScroll);
    }

    private SellTabLayout layoutSellTab(int x, int y, int width, int height) {
        int contentW = Math.max(320, Math.min(SELL_MAX_CONTENT_WIDTH, width - SELL_OUTER_PADDING * 2));
        int contentX = x + (width - contentW) / 2 + cfgSellContentXOffset();
        int titleX = contentX;
        int titleY = y + SELL_OUTER_PADDING - 2 + cfgSellContentYOffset();
        int cardY = titleY + SELL_TITLE_HEIGHT + SELL_TITLE_TO_CARD_GAP;
        UiRect itemCard = new UiRect(contentX, cardY, contentW, SELL_ITEM_CARD_HEIGHT);
        int tabsY = itemCard.bottom() + cfgSectionGap();
        UiRect tabs = new UiRect(contentX, tabsY, Math.min(320, contentW), SELL_TAB_HEIGHT);
        int formY = tabs.bottom() + Math.max(4, cfgSectionGap() / 2);
        int requiredFormH = "auction".equals(sellListingType) ? requiredAuctionFormHeight() : requiredFixedFormHeight();
        int availableFormH = Math.max(requiredFormH, height - (formY - y) - SELL_OUTER_PADDING);
        UiRect form = new UiRect(contentX, formY, contentW, availableFormH);
        int contentBottom = form.bottom() + SELL_OUTER_PADDING;
        return new SellTabLayout(titleX, titleY, itemCard, tabs, form, contentBottom);
    }

    private int requiredFixedFormHeight() {
        return cfgCardPadding()
            + panelHeaderHeight()
            + fieldBlockHeight()
            + cfgDividerGap()
            + summaryCardHeight(5)
            + cfgButtonGap()
            + cfgButtonHeight()
            + cfgCardPadding();
    }

    private int requiredAuctionFormHeight() {
        return cfgCardPadding()
            + panelHeaderHeight()
            + fieldBlockHeight()
            + cfgFieldRowGap()
            + fieldBlockHeight()
            + cfgDividerGap()
            + summaryCardHeight(7)
            + cfgButtonGap()
            + cfgButtonHeight()
            + cfgCardPadding();
    }

    private int panelHeaderHeight() {
        return scaledLineHeight(SELL_TITLE_SCALE)
            + SELL_CARD_TITLE_GAP
            + scaledLineHeight(SELL_DESCRIPTION_SCALE)
            + SELL_DESCRIPTION_LINE_GAP
            + scaledLineHeight(SELL_DESCRIPTION_SECONDARY_SCALE)
            + SELL_DESCRIPTION_TO_FIELDS_GAP;
    }

    private int fieldBlockHeight() {
        return scaledLineHeight(SELL_FIELD_LABEL_SCALE)
            + cfgFieldLabelGap()
            + cfgFieldHeight()
            + cfgFieldHelperGap()
            + scaledLineHeight(SELL_FIELD_HELPER_SCALE) * 2;
    }

    private int summaryCardHeight(int lineCount) {
        return cfgSummaryCardPadding() * 2
            + scaledLineHeight(SELL_SUMMARY_TITLE_SCALE)
            + cfgSummaryGap()
            + SELL_SUMMARY_LINE_HEIGHT * lineCount;
    }

    private void drawSellItemCard(UiRect card, ItemStack hand) {
        addElement(new RectElement(card.x(), card.y(), card.width(), card.height(), RAISED, alpha(ACCENT, 160)));
        int iconY = card.y() + (card.height() - 16) / 2;
        if (hand.isEmpty()) {
            addElement(new TextElement(card.x() + cfgCardPadding(), card.y() + centeredTextY(SELL_ITEM_CARD_HEIGHT, 0.95F), "Hold an item in your main hand to list it.", 0xB7C5D8, 0.95F));
        } else {
            addElement(new ItemPreviewElement(card.x() + cfgCardPadding(), iconY, hand.copy()));
            int textX = card.x() + cfgCardPadding() + 34;
            int textBlockY = card.y() + (card.height() - scaledLineHeight(1.0F) - scaledLineHeight(0.82F) - 4) / 2;
            addElement(new TextElement(textX, textBlockY, hand.getHoverName().getString() + " x" + hand.getCount(), 0xFFFFFF, 1.0F));
            addElement(new TextElement(textX, textBlockY + scaledLineHeight(1.0F) + 4, "Item is removed only after server validation succeeds.", 0xB7C5D8, 0.82F));
        }
        int balanceX = Math.max(card.x() + 260, card.right() - 252);
        int accountY = card.y() + (card.height() - scaledLineHeight(0.86F) * 2 - 4) / 2;
        addElement(new TextElement(balanceX, accountY, "Balance: " + format(data.totalSpendableBalance()), 0x4DFF88, 0.86F));
        addElement(new TextElement(balanceX, accountY + scaledLineHeight(0.86F) + 4, "Pending Proceeds: " + format(data.pendingProceeds()), 0xB7C5D8, 0.86F));
    }

    private void renderListingTypeTabs(UiRect tabs, String selected) {
        int tabW = (tabs.width() - 8) / 2;
        addElement(new ButtonElement(tabs.x(), tabs.y(), tabW, tabs.height(), "Fixed Price", "fixed".equals(selected) ? ACCENT : RAISED, () -> {
            sellListingType = "fixed";
            rebuildLayout();
        }));
        addElement(new ButtonElement(tabs.x() + tabW + 8, tabs.y(), tabs.width() - tabW - 8, tabs.height(), "Auction", "auction".equals(selected) ? WARNING : RAISED, () -> {
            sellListingType = "auction";
            rebuildLayout();
        }));
    }

    private void renderFixedPriceForm(UiRect panel, int heldCount, int defaultQuantity, long fixedDefaultDuration) {
        drawPanel(panel, alpha(ACCENT, 120));
        int padding = cfgCardPadding();
        int fieldGap = cfgFieldHorizontalGap();
        int innerX = panel.x() + padding;
        int innerW = panel.width() - padding * 2;
        int cursor = drawPanelHeader(innerX, panel.y() + padding, "Fixed Price",
            "Set one price for the whole listing.",
            "Buyer pays once; proceeds go to Pending Proceeds.");

        int fieldW = (innerW - fieldGap * 2) / 3;
        fixedPriceBox = addField(innerX, cursor, fieldW, "Price", "Price in spurs", "Buyer pays this amount.", fixedPriceBox == null ? "100" : fixedPriceBox.getValue(), () -> fixedPriceValid());
        quantityBox = addField(innerX + fieldW + fieldGap, cursor, fieldW, "Quantity", "Quantity", "How many items to list.", quantityBox == null ? String.valueOf(defaultQuantity) : quantityBox.getValue(), () -> fixedQuantityValid(heldCount));
        fixedDurationBox = addField(innerX + (fieldW + fieldGap) * 2, cursor, innerW - fieldW * 2 - fieldGap * 2, "Duration (hours)", "Hours", "How long it stays active.", fixedDurationBox == null ? String.valueOf(fixedDefaultDuration) : fixedDurationBox.getValue(), () -> fixedDurationValid());

        cursor += fieldBlockHeight() + cfgDividerGap();
        cursor = drawSummaryCard(panel, cursor, alpha(ACCENT, 120), "Listing Summary", 5);
        cursor = addSummaryLine(innerX + cfgSummaryCardPadding(), cursor, "Buyer pays", () -> format(Math.max(0L, parseLong(fixedPriceBox, 0L))), TEXT);
        cursor = addSummaryLine(innerX + cfgSummaryCardPadding(), cursor, "Tax", () -> AuctionConfig.publicTaxPercent() + "%", MUTED);
        cursor = addSummaryLine(innerX + cfgSummaryCardPadding(), cursor, "You receive", () -> format(estimatedPayout(parseLong(fixedPriceBox, 0L))), SUCCESS);
        cursor = addSummaryLine(innerX + cfgSummaryCardPadding(), cursor, "Expires in", () -> Math.max(0L, parseLong(fixedDurationBox, 0L)) + " hours", MUTED);
        cursor = addValidationLine(innerX + cfgSummaryCardPadding(), cursor, () -> fixedValidationMessage(heldCount));
        int buttonY = cursor + cfgButtonGap();
        addElement(new ButtonElement(innerX, buttonY, 156, cfgButtonHeight(), "Create Fixed Listing", SUCCESS, () -> {
            String error = fixedValidationMessage(heldCount);
            if (!error.isBlank()) {
                notifyLocal("error", "Fix Fixed Price", error, 160);
                return;
            }
            sendAction("sell_fixed_hand", "", parseLong(fixedPriceBox, 0L), parseInt(quantityBox, 1), String.valueOf(parseLong(fixedDurationBox, fixedDefaultDuration)));
        }));
    }

    private void renderAuctionForm(UiRect panel, int heldCount, int defaultQuantity) {
        drawPanel(panel, alpha(WARNING, 130));
        int padding = cfgCardPadding();
        int fieldGap = cfgFieldHorizontalGap();
        int innerX = panel.x() + padding;
        int innerW = panel.width() - padding * 2;
        int cursor = drawPanelHeader(innerX, panel.y() + padding, "Auction",
            "Players bid until the auction ends.",
            "Highest valid bid wins; 0 means no buyout.");

        int fieldW = (innerW - fieldGap * 2) / 3;
        auctionStartBox = addField(innerX, cursor, fieldW, "Starting Bid", "Starting bid in spurs", "First minimum bid.", auctionStartBox == null ? "100" : auctionStartBox.getValue(), () -> auctionStartValid());
        auctionQuantityBox = addField(innerX + fieldW + fieldGap, cursor, fieldW, "Quantity", "Quantity", "How many items to auction.", auctionQuantityBox == null ? String.valueOf(defaultQuantity) : auctionQuantityBox.getValue(), () -> auctionQuantityValid(heldCount));
        auctionDurationBox = addField(innerX + (fieldW + fieldGap) * 2, cursor, innerW - fieldW * 2 - fieldGap * 2, "Duration (hours)", "Hours", "Auction length.", auctionDurationBox == null ? String.valueOf(AuctionConfig.defaultAuctionDurationHours()) : auctionDurationBox.getValue(), () -> auctionDurationValid());

        cursor += fieldBlockHeight() + cfgFieldRowGap();
        int halfW = (innerW - fieldGap) / 2;
        auctionBuyoutBox = addField(innerX, cursor, halfW, "Buyout Price (Optional)", "0 for none", "0 means no buyout.", auctionBuyoutBox == null ? "0" : auctionBuyoutBox.getValue(), () -> auctionBuyoutValid());
        auctionIncrementBox = addField(innerX + halfW + fieldGap, cursor, innerW - halfW - fieldGap, "Minimum Bid Increment", "Increment", "Each new bid must beat the current bid by this much.", auctionIncrementBox == null ? String.valueOf(AuctionConfig.defaultMinBidIncrement()) : auctionIncrementBox.getValue(), () -> auctionIncrementValid());

        cursor += fieldBlockHeight() + cfgDividerGap();
        cursor = drawSummaryCard(panel, cursor, alpha(WARNING, 130), "Auction Summary", 7);
        cursor = addSummaryLine(innerX + cfgSummaryCardPadding(), cursor, "Starts at", () -> format(Math.max(0L, parseLong(auctionStartBox, 0L))), TEXT);
        cursor = addSummaryLine(innerX + cfgSummaryCardPadding(), cursor, "Minimum raise", () -> format(Math.max(0L, parseLong(auctionIncrementBox, 0L))), MUTED);
        cursor = addSummaryLine(innerX + cfgSummaryCardPadding(), cursor, "Buyout", () -> parseLong(auctionBuyoutBox, 0L) <= 0L ? "none" : format(parseLong(auctionBuyoutBox, 0L)), WARNING);
        cursor = addSummaryLine(innerX + cfgSummaryCardPadding(), cursor, "Quantity", () -> String.valueOf(Math.max(0, parseInt(auctionQuantityBox, 0))), MUTED);
        cursor = addSummaryLine(innerX + cfgSummaryCardPadding(), cursor, "Ends in", () -> Math.max(0L, parseLong(auctionDurationBox, 0L)) + " hours", MUTED);
        cursor = addSummaryLine(innerX + cfgSummaryCardPadding(), cursor, "Estimated payout", () -> format(estimatedPayout(parseLong(auctionStartBox, 0L))), SUCCESS);
        cursor = addValidationLine(innerX + cfgSummaryCardPadding(), cursor, () -> auctionValidationMessage(heldCount));
        int buttonY = cursor + cfgButtonGap();
        addElement(new ButtonElement(innerX, buttonY, 156, cfgButtonHeight(), "Create Auction", SUCCESS, () -> {
            String error = auctionValidationMessage(heldCount);
            if (!error.isBlank()) {
                notifyLocal("error", "Fix Auction", error, 160);
                return;
            }
            String extra = parseLong(auctionDurationBox, AuctionConfig.defaultAuctionDurationHours()) + "|"
                + parseLong(auctionBuyoutBox, 0L) + "|"
                + parseLong(auctionIncrementBox, AuctionConfig.defaultMinBidIncrement());
            sendAction("sell_auction_hand", "", parseLong(auctionStartBox, 0L), parseInt(auctionQuantityBox, defaultQuantity), extra);
        }));
    }

    private void drawPanel(UiRect panel, int outline) {
        addElement(new RectElement(panel.x(), panel.y(), panel.width(), panel.height(), argb(0x081827), outline));
    }

    private int drawPanelHeader(int x, int y, String title, String descriptionLineOne, String descriptionLineTwo) {
        addElement(new TextElement(x, y, title, 0xFFFFFF, SELL_TITLE_SCALE));
        int cursor = y + scaledLineHeight(SELL_TITLE_SCALE) + SELL_CARD_TITLE_GAP;
        addElement(new TextElement(x, cursor, descriptionLineOne, 0xB7C5D8, SELL_DESCRIPTION_SCALE));
        cursor += scaledLineHeight(SELL_DESCRIPTION_SCALE) + SELL_DESCRIPTION_LINE_GAP;
        addElement(new TextElement(x, cursor, descriptionLineTwo, 0xB7C5D8, SELL_DESCRIPTION_SECONDARY_SCALE));
        return cursor + scaledLineHeight(SELL_DESCRIPTION_SECONDARY_SCALE) + SELL_DESCRIPTION_TO_FIELDS_GAP;
    }

    private int drawDivider(UiRect panel, int y, int color) {
        addElement(new DividerElement(panel.x() + cfgCardPadding(), y, panel.width() - cfgCardPadding() * 2, color));
        return y + 1 + cfgSummaryGap();
    }

    private int drawSummaryCard(UiRect panel, int y, int color, String title, int lines) {
        int x = panel.x() + cfgCardPadding();
        int width = panel.width() - cfgCardPadding() * 2;
        addElement(new RectElement(x, y, width, summaryCardHeight(lines), RAISED, alpha(color, 150)));
        int cursor = y + cfgSummaryCardPadding();
        addElement(new TextElement(x + cfgSummaryCardPadding(), cursor, title, 0xFFFFFF, SELL_SUMMARY_TITLE_SCALE));
        return cursor + scaledLineHeight(SELL_SUMMARY_TITLE_SCALE) + cfgSummaryGap();
    }

    private int addSummaryLine(int x, int y, String label, Supplier<String> value, int valueColor) {
        addElement(new SummaryLineElement(x, y + cfgSummaryTextYOffset(), SELL_SUMMARY_LABEL_WIDTH, label, value, valueColor));
        return y + SELL_SUMMARY_LINE_HEIGHT;
    }

    private int addValidationLine(int x, int y, Supplier<String> text) {
        addElement(new DynamicTextElement(x, y + cfgSummaryTextYOffset(), text, DANGER, SELL_SUMMARY_TEXT_SCALE));
        return y + SELL_SUMMARY_LINE_HEIGHT;
    }

    private void buildCollection(int x, int y, int width, int height) {
        elements.add(new TextElement(x + 14, y + 12, "Collection", 0xFFFFFF, 1.05F));
        elements.add(new TextElement(x + 14, y + 30, "Pending Proceeds: " + format(data.pendingProceeds()) + " in " + data.pendingPayoutCount() + " payout(s)", 0xB7C5D8, 0.86F));
        elements.add(new ButtonElement(x + width - 214, y + 10, 96, 24, "Claim Money", SUCCESS, () -> sendAction("collect_money", "")));
        elements.add(new ButtonElement(x + width - 110, y + 10, 92, 24, "Claim Items", SUCCESS, () -> sendAction("collect", "")));
        int rowY = y + 46;
        if (data.collections().isEmpty()) {
            elements.add(new TextElement(x + 16, rowY, "No pending claims.", 0xB7C5D8, 1.0F));
            return;
        }
        for (MarketScreenData.CollectionView entry : data.collections().stream().limit(12).toList()) {
            String title = "coins".equals(entry.type()) || "proceeds".equals(entry.type()) ? format(entry.amount()) : entry.itemName();
            elements.add(new RectElement(x + 14, rowY, width - 28, 42, RAISED, 0));
            elements.add(new TextElement(x + 26, rowY + 8, title, 0xFFFFFF, 0.95F));
            elements.add(new TextElement(x + 26, rowY + 23, entry.reason() + " | " + entry.id(), 0xB7C5D8, 0.82F));
            elements.add(new ButtonElement(x + width - 90, rowY + 10, 62, 22, "Claim", SUCCESS, () -> sendAction("collect_one", entry.id())));
            rowY += 48;
            if (rowY > y + height - 48) {
                break;
            }
        }
    }

    private void buildPriceCheck(int x, int y, int width, int height) {
        MarketScreenData.MarketInsight insight = data.priceCheck();
        elements.add(new TextElement(x + 14, y + 12, "Price Check", 0xFFFFFF, 1.05F));
        elements.add(new TextElement(x + 14, y + 34, insight.itemName() + (insight.itemId().isBlank() ? "" : " (" + insight.itemId() + ")"), 0xB7C5D8, 0.95F));
        int cardW = Math.max(126, (width - 54) / 3);
        elements.add(new StatCard(x + 14, y + 62, cardW, 54, "Last Sold", insight.lastSalePrice(), "recent sale", ACCENT));
        elements.add(new StatCard(x + 24 + cardW, y + 62, cardW, 54, "7d Average", insight.average7d(), insight.trend(), WARNING));
        elements.add(new StatCard(x + 34 + cardW * 2, y + 62, cardW, 54, "Lowest Active", insight.lowestActivePrice(), insight.activeListingCount() + " active", SUCCESS));
        elements.add(new TextElement(x + 16, y + 138, "Sold 24h/7d: " + insight.sold24h() + "/" + insight.sold7d(), 0xB7C5D8, 0.95F));
        elements.add(new TextElement(x + 16, y + 154, "Recommended range: " + recommendedRange(insight), 0xFFFFFF, 0.95F));
    }

    private void buildEconomy(int x, int y, int width, int height) {
        elements.add(new TextElement(x + 14, y + 12, "Economy", 0xFFFFFF, 1.05F));
        int chartH = Math.max(130, (height - 72) / 2);
        elements.add(new LineChartElement(x + 14, y + 40, width / 2 - 22, chartH, "Volume Over Time", data.volumeHistory()));
        elements.add(new LineChartElement(x + width / 2 + 8, y + 40, width / 2 - 22, chartH, "Average Sale Price", data.averagePriceHistory()));
        elements.add(new PieChartElement(x + 14, y + 52 + chartH, width / 2 - 22, height - chartH - 68, "Admin vs Public vs Taxes", data.flowBreakdown()));
        elements.add(new BarChartElement(x + width / 2 + 8, y + 52 + chartH, width / 2 - 22, height - chartH - 68, "Top Sellers", data.topSellers()));
    }

    private void buildAdmin(int x, int y, int width, int height) {
        elements.add(new TextElement(x + 14, y + 12, "Admin Management", 0xFFFFFF, 1.05F));
        elements.add(new ButtonElement(x + width - 300, y + 10, 82, 24, "Repair DB", WARNING, () -> sendAction("admin_repair", "")));
        elements.add(new ButtonElement(x + width - 210, y + 10, 82, 24, "Backup DB", ACCENT, () -> sendAction("admin_backup", "")));
        elements.add(new ButtonElement(x + width - 120, y + 10, 102, 24, "Clear Expired", DANGER, () -> sendAction("clear_expired", "")));
        buildMarket(x, y + 36, width, height - 36, true);
    }

    private void buildFooter(int x, int y, int width) {
        elements.add(new TextElement(x + 2, y + 10, data.totalListings() + " listing(s) | Page " + (data.page() + 1) + " of " + (data.maxPage() + 1), 0xB7C5D8, 0.85F));
        elements.add(new ButtonElement(x + width - 176, y + 5, 72, 22, "Previous", page <= 0 ? GRID : RAISED, () -> {
            if (page > 0) {
                page--;
                requestData();
            }
        }));
        elements.add(new ButtonElement(x + width - 96, y + 5, 72, 22, "Next", page >= data.maxPage() ? GRID : RAISED, () -> {
            if (page < data.maxPage()) {
                page++;
                requestData();
            }
        }));
    }

    private void buildBuyOverlay(int x, int y, int width, int height) {
        elements.add(new RectElement(x, y, width, height, 0xCC000000, 0));
        int boxW = 360;
        int boxH = 154;
        int bx = x + (width - boxW) / 2;
        int by = y + (height - boxH) / 2;
        elements.add(new RectElement(bx, by, boxW, boxH, PANEL, ACCENT));
        boolean auction = "auction".equals(pendingBuy.listingType());
        elements.add(new TextElement(bx + 18, by + 16, auction ? "Place Bid" : "Confirm Purchase", 0xFFFFFF, 1.1F));
        elements.add(new TextElement(bx + 18, by + 42, pendingBuy.itemName(), 0xFFFFFF, 0.95F));
        elements.add(new TextElement(bx + 18, by + 60, "Seller: " + pendingBuy.sellerName() + " | " + pendingBuy.category(), 0xB7C5D8, 0.88F));
        elements.add(new TextElement(bx + 18, by + 78, auction ? "Current Bid: " + format(pendingBuy.currentBid()) + " | Min +" + format(pendingBuy.minIncrement()) : "Price: " + format(pendingBuy.price()), 0xFFD166, 0.95F));
        if (auction) {
            long minimum = pendingBuy.currentBid() > 0L ? pendingBuy.currentBid() + pendingBuy.minIncrement() : pendingBuy.startPrice();
            bidAmountBox = addTextBox(bx + 18, by + 94, 120, "Bid Amount", bidAmountBox == null ? String.valueOf(minimum) : bidAmountBox.getValue());
        }
        elements.add(new ButtonElement(bx + 18, by + 112, 112, 26, "Cancel", DANGER, () -> {
            pendingBuy = null;
            rebuildLayout();
        }));
        if (auction && pendingBuy.buyoutPrice() > 0L) {
            elements.add(new ButtonElement(bx + boxW - 248, by + 112, 106, 26, "Buyout", WARNING, () -> {
                String id = pendingBuy.id();
                pendingBuy = null;
                sendAction("buyout", id);
                rebuildLayout();
            }));
        }
        elements.add(new ButtonElement(bx + boxW - 130, by + 112, 112, 26, auction ? "Bid" : "Buy", SUCCESS, () -> {
            String id = pendingBuy.id();
            long amount = auction ? parseLong(bidAmountBox, 0L) : 0L;
            pendingBuy = null;
            if (auction) {
                sendAction("bid", id, amount, 0, "");
            } else {
                sendAction("buy", id);
            }
            rebuildLayout();
        }));
    }

    private String recommendedRange(MarketScreenData.MarketInsight insight) {
        long anchor = insight.average7d() > 0 ? insight.average7d() : insight.lowestActivePrice();
        if (anchor <= 0) {
            return "not enough sales yet";
        }
        return format(Math.max(1L, Math.round(anchor * 0.90D))) + " - " + format(Math.round(anchor * 1.10D));
    }

    private EditBox addTextBox(int x, int y, int width, String hint, String value) {
        EditBox box = buildingSellContent
            ? new TunedEditBox(font, x, y, width, cfgFieldHeight(), Component.literal(hint))
            : new EditBox(font, x, y, width, SELL_FIELD_HEIGHT, Component.literal(hint));
        box.setValue(value == null ? "" : value);
        box.setHint(Component.literal(hint));
        box.setMaxLength(32);
        box.setTextColor(0xFFFFFF);
        box.setTextColorUneditable(0xB7C5D8);
        box.setBordered(false);
        addEditBox(box);
        return box;
    }

    private EditBox addField(int x, int y, int width, String label, String placeholder, String helper, String value, BooleanSupplier valid) {
        drawFieldLabel(x, y, label);
        int boxY = y + scaledLineHeight(SELL_FIELD_LABEL_SCALE) + cfgFieldLabelGap();
        EditBox box = addTextBox(x, boxY, width, placeholder, value);
        addElement(new FieldBorderElement(x, boxY, width, cfgFieldHeight(), box, valid));
        drawFieldHelper(x, boxY + cfgFieldHeight() + cfgFieldHelperGap(), Math.max(width, 104), helper);
        return box;
    }

    private void addElement(UiElement element) {
        if (buildingSellContent) {
            sellElements.add(element);
        } else {
            elements.add(element);
        }
    }

    private void addEditBox(EditBox box) {
        editBoxes.add(box);
        if (buildingSellContent) {
            sellEditBoxes.add(box);
        }
    }

    private void drawFieldLabel(int x, int y, String label) {
        addElement(new TextElement(x, y + cfgLabelYOffset(), label, TEXT, SELL_FIELD_LABEL_SCALE));
    }

    private void drawFieldHelper(int x, int y, int width, String helper) {
        addElement(new WrappedTextElement(x, y + cfgHelperTextYOffset(), width, helper, MUTED, SELL_FIELD_HELPER_SCALE, 2));
    }

    private int scaledLineHeight(float scale) {
        return Math.max(1, Math.round(font.lineHeight * scale));
    }

    private int centeredTextY(int boxHeight, float scale) {
        return Math.max(0, Math.round((boxHeight - font.lineHeight * scale) / 2F));
    }

    private CreateCoinMarketClientConfig.UiLayout uiLayout() {
        return CreateCoinMarketClientConfig.uiLayout();
    }

    private int cfgGlobalXOffset() {
        return uiLayout().globalXOffset();
    }

    private int cfgGlobalYOffset() {
        return uiLayout().globalYOffset();
    }

    private int cfgSellTabXOffset() {
        return uiLayout().sellTabXOffset();
    }

    private int cfgSellTabYOffset() {
        return uiLayout().sellTabYOffset();
    }

    private int cfgSellContentXOffset() {
        return uiLayout().sellContentXOffset();
    }

    private int cfgSellContentYOffset() {
        return uiLayout().sellContentYOffset() + runtimeSellContentYOffset;
    }

    private int cfgTextYOffset() {
        return uiLayout().textYOffset();
    }

    private int cfgInputTextYOffset() {
        return uiLayout().inputTextYOffset() + runtimeInputTextYOffset;
    }

    private int cfgButtonTextYOffset() {
        return uiLayout().buttonTextYOffset() + runtimeButtonTextYOffset;
    }

    private int cfgLabelYOffset() {
        return uiLayout().labelYOffset();
    }

    private int cfgHelperTextYOffset() {
        return uiLayout().helperTextYOffset();
    }

    private int cfgSummaryTextYOffset() {
        return uiLayout().summaryTextYOffset();
    }

    private int cfgFieldHeight() {
        return uiLayout().fieldHeight();
    }

    private int cfgFieldLabelGap() {
        return uiLayout().fieldLabelGap();
    }

    private int cfgFieldHelperGap() {
        return uiLayout().fieldHelperGap();
    }

    private int cfgFieldRowGap() {
        return uiLayout().fieldRowGap();
    }

    private int cfgFieldHorizontalGap() {
        return uiLayout().fieldHorizontalGap();
    }

    private int cfgFieldPaddingX() {
        return uiLayout().fieldPaddingX();
    }

    private int cfgButtonHeight() {
        return uiLayout().buttonHeight();
    }

    private int cfgButtonGap() {
        return uiLayout().buttonGap();
    }

    private int cfgCardPadding() {
        return uiLayout().cardPadding();
    }

    private int cfgSectionGap() {
        return uiLayout().sectionGap();
    }

    private int cfgDividerGap() {
        return uiLayout().dividerGap();
    }

    private int cfgSummaryGap() {
        return uiLayout().summaryGap();
    }

    private int cfgSummaryCardPadding() {
        return Math.max(6, cfgCardPadding() / 2);
    }

    private int cfgScrollSpeed() {
        return uiLayout().scrollSpeed();
    }

    private int cfgScrollbarWidth() {
        return uiLayout().scrollbarWidth();
    }

    private int cfgScrollViewportYOffset() {
        return uiLayout().scrollViewportYOffset();
    }

    private int cfgScrollViewportHeightOffset() {
        return uiLayout().scrollViewportHeightOffset();
    }

    private boolean showDebugBounds() {
        CreateCoinMarketClientConfig.UiLayout layout = uiLayout();
        boolean configured = layout.debugUi() || layout.showDebugBounds();
        return showDebugBoundsOverride == null ? configured : showDebugBoundsOverride;
    }

    private boolean showTextBounds() {
        CreateCoinMarketClientConfig.UiLayout layout = uiLayout();
        boolean configured = layout.debugUi() || layout.showTextBounds();
        return showTextBoundsOverride == null ? configured : showTextBoundsOverride;
    }

    private boolean showCenterLines() {
        CreateCoinMarketClientConfig.UiLayout layout = uiLayout();
        boolean configured = layout.debugUi() || layout.showCenterLines();
        return showCenterLinesOverride == null ? configured : showCenterLinesOverride;
    }

    private boolean showAnyDebug() {
        return showDebugBounds() || showTextBounds() || showCenterLines();
    }

    private void drawTextLeft(GuiGraphics graphics, String text, int x, int y, int color, float scale) {
        if (text == null || text.isBlank()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y + cfgTextYOffset(), 0);
        graphics.pose().scale(scale, scale, 1F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawTextCentered(GuiGraphics graphics, String text, int x, int y, int width, int height, int color, float scale) {
        int textX = x + Math.max(0, Math.round((width - font.width(text) * scale) / 2F));
        int textY = y + centeredTextY(height, scale);
        drawTextLeft(graphics, text, textX, textY, color, scale);
    }

    private void drawTextRight(GuiGraphics graphics, String text, int rightX, int y, int color, float scale) {
        int textX = rightX - Math.round(font.width(text) * scale);
        drawTextLeft(graphics, text, textX, y, color, scale);
    }

    private void drawTextInBoxLeft(GuiGraphics graphics, String text, int x, int y, int width, int height, int color, float scale) {
        drawTextLeft(graphics, trim(text, Math.max(1, Math.round((width - cfgFieldPaddingX() * 2) / scale))), x + cfgFieldPaddingX(), y + centeredTextY(height, scale) + cfgInputTextYOffset(), color, scale);
    }

    private void drawTextInBoxCentered(GuiGraphics graphics, String text, int x, int y, int width, int height, int color, float scale) {
        drawTextCentered(graphics, text, x, y, width, height, color, scale);
    }

    private void drawButtonLabelCentered(GuiGraphics graphics, String label, int x, int y, int width, int height) {
        drawTextInBoxCentered(graphics, label, x, y + cfgButtonTextYOffset(), width, height, TEXT, 1.0F);
    }

    private void drawWrappedTextInRect(GuiGraphics graphics, String text, int x, int y, int width, int color, float scale, int maxLines) {
        List<String> lines = wrap(text, Math.max(1, Math.round(width / scale)), maxLines);
        int lineY = y;
        for (String line : lines) {
            drawTextLeft(graphics, line, x, lineY, color, scale);
            lineY += scaledLineHeight(scale);
        }
    }

    private boolean isSellMode() {
        return "sell".equals(mode);
    }

    private boolean insideSellViewport(double mouseX, double mouseY) {
        return sellViewportW > 0 && sellViewportH > 0 && hovered(sellViewportX, sellViewportY, sellViewportW, sellViewportH, mouseX, mouseY);
    }

    private boolean startSellScrollbarDrag(double mouseX, double mouseY) {
        if (sellMaxScroll <= 0 || !insideSellScrollbar(mouseX, mouseY)) {
            return false;
        }
        int thumbH = sellScrollbarThumbHeight();
        int thumbY = sellScrollbarThumbY(thumbH);
        int width = cfgScrollbarWidth() + 4;
        sellScrollbarGrabOffset = hovered(sellViewportX + sellViewportW - width - 1, thumbY, width, thumbH, mouseX, mouseY)
            ? (int) mouseY - thumbY
            : thumbH / 2;
        draggingSellScrollbar = true;
        updateSellScrollFromMouse(mouseY);
        return true;
    }

    private boolean insideSellScrollbar(double mouseX, double mouseY) {
        int width = cfgScrollbarWidth() + 6;
        return hovered(sellViewportX + sellViewportW - width - 1, sellViewportY, width, sellViewportH, mouseX, mouseY);
    }

    private int sellScrollbarThumbHeight() {
        if (sellContentHeight <= 0) {
            return sellViewportH;
        }
        return Mth.clamp((int) Math.round((double) sellViewportH * sellViewportH / sellContentHeight), 24, Math.max(24, sellViewportH - 8));
    }

    private int sellScrollbarThumbY(int thumbH) {
        int trackY = sellViewportY + 4;
        int travel = Math.max(1, sellViewportH - 8 - thumbH);
        return trackY + (int) Math.round(travel * (sellScrollOffset / (double) Math.max(1, sellMaxScroll)));
    }

    private void updateSellScrollFromMouse(double mouseY) {
        int thumbH = sellScrollbarThumbHeight();
        int trackY = sellViewportY + 4;
        int travel = Math.max(1, sellViewportH - 8 - thumbH);
        double position = Mth.clamp(mouseY - sellScrollbarGrabOffset - trackY, 0D, travel);
        sellScrollOffset = Mth.clamp((int) Math.round(position / travel * sellMaxScroll), 0, sellMaxScroll);
    }

    private void notifyLocal(String type, String title, String message, int durationTicks) {
        notifications.add(new Notification(type, title, message, System.currentTimeMillis(), Math.max(1000L, durationTicks * 50L)));
        while (notifications.size() > 6) {
            notifications.removeFirst();
        }
    }

    private long estimatedPayout(long price) {
        if (price <= 0L) {
            return 0L;
        }
        long tax = Math.round(price * (AuctionConfig.publicTaxPercent() / 100.0D));
        return Math.max(0L, price - Math.max(0L, Math.min(price, tax)));
    }

    private boolean fixedPriceValid() {
        return parseLong(fixedPriceBox, 0L) >= 1L;
    }

    private boolean fixedQuantityValid(int heldCount) {
        int quantity = parseInt(quantityBox, 0);
        return heldCount > 0 && quantity >= 1 && quantity <= heldCount;
    }

    private boolean fixedDurationValid() {
        return parseLong(fixedDurationBox, 0L) >= 1L;
    }

    private String fixedValidationMessage(int heldCount) {
        if (heldCount < 1) {
            return "Hold an item in your main hand to list it.";
        }
        if (!fixedPriceValid()) {
            return "Price must be at least 1 spur.";
        }
        int quantity = parseInt(quantityBox, 0);
        if (quantity < 1 || quantity > heldCount) {
            return "Quantity must be between 1 and " + heldCount + ".";
        }
        if (!fixedDurationValid()) {
            return "Duration must be at least 1 hour.";
        }
        return "";
    }

    private boolean auctionStartValid() {
        return parseLong(auctionStartBox, 0L) >= 1L;
    }

    private boolean auctionDurationValid() {
        return parseLong(auctionDurationBox, 0L) >= 1L;
    }

    private boolean auctionBuyoutValid() {
        long buyout = parseLong(auctionBuyoutBox, -1L);
        long start = parseLong(auctionStartBox, 0L);
        return buyout == 0L || buyout > start;
    }

    private boolean auctionIncrementValid() {
        return parseLong(auctionIncrementBox, 0L) >= 1L;
    }

    private boolean auctionQuantityValid(int heldCount) {
        int quantity = parseInt(auctionQuantityBox, 0);
        return heldCount > 0 && quantity >= 1 && quantity <= heldCount;
    }

    private String auctionValidationMessage(int heldCount) {
        if (heldCount < 1) {
            return "Hold an item in your main hand to list it.";
        }
        if (!auctionStartValid()) {
            return "Starting bid must be at least 1.";
        }
        if (!auctionDurationValid()) {
            return "Duration must be at least 1 hour.";
        }
        if (!auctionBuyoutValid()) {
            return "Buyout must be greater than starting bid, or 0 for no buyout.";
        }
        if (!auctionIncrementValid()) {
            return "Minimum increment must be at least 1.";
        }
        int quantity = parseInt(auctionQuantityBox, 0);
        if (quantity < 1 || quantity > heldCount) {
            return "Quantity must be between 1 and " + heldCount + ".";
        }
        return "";
    }

    private long parseLong(EditBox box, long fallback) {
        if (box == null) {
            return fallback;
        }
        try {
            return Long.parseLong(box.getValue().trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private int parseInt(EditBox box, int fallback) {
        long value = parseLong(box, fallback);
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, (int) value);
    }

    private ItemStack decode(String encoded) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || encoded == null || encoded.isBlank()) {
            return ItemStack.EMPTY;
        }
        return ItemStackCodec.decodeFromString(encoded, minecraft.level.registryAccess());
    }

    private String trim(String value, int width) {
        if (font.width(value) <= width) {
            return value;
        }
        String result = value;
        while (result.length() > 3 && font.width(result + "...") > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private List<String> wrap(String value, int width, int maxLines) {
        if (value == null || value.isBlank()) {
            return List.of("");
        }
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.width(candidate) <= width) {
                line = new StringBuilder(candidate);
            } else {
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                }
                line = new StringBuilder(trim(word, width));
                if (lines.size() >= maxLines - 1) {
                    break;
                }
            }
        }
        if (!line.isEmpty() && lines.size() < maxLines) {
            lines.add(line.toString());
        }
        if (lines.isEmpty()) {
            lines.add(trim(value, width));
        }
        if (lines.size() == maxLines && font.width(lines.getLast()) > width - font.width("...")) {
            lines.set(lines.size() - 1, trim(lines.getLast(), width));
        }
        return lines;
    }

    private static String format(long value) {
        return value + " spurs";
    }

    private static String stat(long value) {
        return value <= 0L ? "unknown" : format(value);
    }

    private static int argb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static int alpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    private static boolean hovered(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private record Notification(String type, String title, String message, long createdAt, long durationMillis) {
        boolean expired(long now) {
            return remaining(now) <= 0L;
        }

        long remaining(long now) {
            return createdAt + durationMillis - now;
        }
    }

    private record UiRect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }
    }

    private record SellTabLayout(int titleX, int titleY, UiRect itemCard, UiRect tabs, UiRect form, int contentBottom) {
    }

    private class TunedEditBox extends EditBox {
        TunedEditBox(Font font, int x, int y, int width, int height, Component message) {
            super(font, x, y, width, height, message);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, cfgInputTextYOffset(), 0);
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popPose();
        }
    }

    private interface UiElement {
        void render(GuiGraphics graphics, int mouseX, int mouseY, float delta);

        default boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }

        default void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        }

        default void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY, int screenMouseX, int screenMouseY) {
            renderTooltip(graphics, mouseX, mouseY);
        }

        default void renderDebug(GuiGraphics graphics) {
        }
    }

    private class RectElement implements UiElement {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int fill;
        private final int outline;

        RectElement(int x, int y, int width, int height, int fill, int outline) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.fill = fill;
            this.outline = outline;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(x, y, x + width, y + height, fill);
            if (outline != 0) {
                border(graphics, x, y, width, height, outline);
            }
        }

        @Override
        public void renderDebug(GuiGraphics graphics) {
            if (showDebugBounds()) {
                debugRect(graphics, x, y, width, height, ACCENT);
            }
            if (showCenterLines()) {
                debugCenterLines(graphics, x, y, width, height);
            }
        }
    }

    private class TextElement implements UiElement {
        private final int x;
        private final int y;
        private final String text;
        private final int color;
        private final float scale;

        TextElement(int x, int y, String text, int color, float scale) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.color = argb(color);
            this.scale = scale;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            drawTextLeft(graphics, text, x, y, color, scale);
        }

        @Override
        public void renderDebug(GuiGraphics graphics) {
            if (showTextBounds()) {
                debugTextRect(graphics, x, y, text, scale, WARNING);
            }
        }
    }

    private class DynamicTextElement implements UiElement {
        private final int x;
        private final int y;
        private final Supplier<String> text;
        private final int color;
        private final float scale;

        DynamicTextElement(int x, int y, Supplier<String> text, int color, float scale) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.color = color;
            this.scale = scale;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            String value = text.get();
            if (value == null || value.isBlank()) {
                return;
            }
            drawTextLeft(graphics, value, x, y, color, scale);
        }

        @Override
        public void renderDebug(GuiGraphics graphics) {
            if (showTextBounds()) {
                String value = text.get();
                debugTextRect(graphics, x, y, value == null ? "" : value, scale, WARNING);
            }
        }
    }

    private class WrappedTextElement implements UiElement {
        private final int x;
        private final int y;
        private final int width;
        private final String text;
        private final int color;
        private final float scale;
        private final int maxLines;

        WrappedTextElement(int x, int y, int width, String text, int color, float scale, int maxLines) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.text = text;
            this.color = color;
            this.scale = scale;
            this.maxLines = maxLines;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            drawWrappedTextInRect(graphics, text, x, y, width, color, scale, maxLines);
        }

        @Override
        public void renderDebug(GuiGraphics graphics) {
            if (!showTextBounds()) {
                return;
            }
            List<String> lines = wrap(text, Math.max(1, Math.round(width / scale)), maxLines);
            int lineY = y;
            for (String line : lines) {
                debugTextRect(graphics, x, lineY, line, scale, WARNING);
                lineY += scaledLineHeight(scale);
            }
        }
    }

    private class SummaryLineElement implements UiElement {
        private final int x;
        private final int y;
        private final int labelWidth;
        private final String label;
        private final Supplier<String> value;
        private final int valueColor;

        SummaryLineElement(int x, int y, int labelWidth, String label, Supplier<String> value, int valueColor) {
            this.x = x;
            this.y = y;
            this.labelWidth = labelWidth;
            this.label = label;
            this.value = value;
            this.valueColor = valueColor;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            String renderedValue = value.get();
            drawTextLeft(graphics, label + ":", x, y, MUTED, SELL_SUMMARY_TEXT_SCALE);
            drawTextLeft(graphics, renderedValue == null ? "" : renderedValue, x + labelWidth, y, valueColor, SELL_SUMMARY_TEXT_SCALE);
        }

        @Override
        public void renderDebug(GuiGraphics graphics) {
            if (!showTextBounds()) {
                return;
            }
            String renderedValue = value.get();
            debugTextRect(graphics, x, y, label + ":", SELL_SUMMARY_TEXT_SCALE, WARNING);
            debugTextRect(graphics, x + labelWidth, y, renderedValue == null ? "" : renderedValue, SELL_SUMMARY_TEXT_SCALE, SUCCESS);
        }
    }

    private class DividerElement implements UiElement {
        private final int x;
        private final int y;
        private final int width;
        private final int color;

        DividerElement(int x, int y, int width, int color) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.color = color;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(x, y, x + width, y + 1, color);
        }

        @Override
        public void renderDebug(GuiGraphics graphics) {
            if (showDebugBounds()) {
                debugRect(graphics, x, y, width, 1, WARNING);
            }
        }
    }

    private class FieldBorderElement implements UiElement {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final EditBox box;
        private final BooleanSupplier valid;

        FieldBorderElement(int x, int y, int width, int height, EditBox box, BooleanSupplier valid) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.box = box;
            this.valid = valid;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            int borderColor = !valid.getAsBoolean() ? DANGER : box.isFocused() ? ACCENT : alpha(MUTED, 135);
            graphics.fill(x, y, x + width, y + height, PANEL);
            border(graphics, x, y, width, height, borderColor);
        }

        @Override
        public void renderDebug(GuiGraphics graphics) {
            if (showDebugBounds()) {
                debugRect(graphics, x, y, width, height, WARNING);
            }
            if (showCenterLines()) {
                debugCenterLines(graphics, x, y, width, height);
            }
        }
    }

    private class ItemPreviewElement implements UiElement {
        private final int x;
        private final int y;
        private final ItemStack stack;

        ItemPreviewElement(int x, int y, ItemStack stack) {
            this.x = x;
            this.y = y;
            this.stack = stack;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(x - 4, y - 4, x + 24, y + 24, PANEL);
            border(graphics, x - 4, y - 4, 28, 28, alpha(ACCENT, 160));
            if (!stack.isEmpty()) {
                graphics.renderFakeItem(stack, x, y);
                graphics.renderItemDecorations(font, stack, x, y);
            }
        }

        @Override
        public void renderDebug(GuiGraphics graphics) {
            if (showDebugBounds()) {
                debugRect(graphics, x - 4, y - 4, 28, 28, WARNING);
            }
        }

        @Override
        public void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY, int screenMouseX, int screenMouseY) {
            if (!stack.isEmpty() && hovered(x - 4, y - 4, 28, 28, mouseX, mouseY)) {
                graphics.renderComponentTooltip(font, List.of(stack.getHoverName()), screenMouseX, screenMouseY);
            }
        }
    }

    private class ButtonElement implements UiElement {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final String label;
        private final int color;
        private final Runnable action;

        ButtonElement(int x, int y, int width, int height, String label, int color, Runnable action) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.label = label;
            this.color = color;
            this.action = action;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            boolean over = hovered(x, y, width, height, mouseX, mouseY);
            graphics.fill(x, y, x + width, y + height, over ? alpha(color, 245) : alpha(color, 210));
            border(graphics, x, y, width, height, over ? TEXT : alpha(ACCENT, 150));
            drawButtonLabelCentered(graphics, label, x, y, width, height);
        }

        @Override
        public void renderDebug(GuiGraphics graphics) {
            if (showDebugBounds()) {
                debugRect(graphics, x, y, width, height, WARNING);
            }
            if (showTextBounds()) {
                int textX = x + Math.max(0, Math.round((width - font.width(label)) / 2F));
                int textY = y + cfgButtonTextYOffset() + centeredTextY(height, 1.0F);
                debugTextRect(graphics, textX, textY, label, 1.0F, SUCCESS);
            }
            if (showCenterLines()) {
                debugCenterLines(graphics, x, y, width, height);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && hovered(x, y, width, height, mouseX, mouseY)) {
                action.run();
                return true;
            }
            return false;
        }
    }

    private class MetricPill implements UiElement {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final String label;
        private final String value;
        private final int accent;

        MetricPill(int x, int y, int width, int height, String label, String value, int accent) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.label = label;
            this.value = value;
            this.accent = accent;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(x, y, x + width, y + height, RAISED);
            graphics.fill(x, y, x + 3, y + height, accent);
            graphics.drawString(font, label.toUpperCase(Locale.ROOT), x + 9, y + 5, MUTED, false);
            graphics.drawString(font, value, x + 9, y + 18, TEXT, false);
        }
    }

    private class StatCard implements UiElement {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final String title;
        private final long value;
        private final String subtitle;
        private final int accent;

        StatCard(int x, int y, int width, int height, String title, long value, String subtitle, int accent) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.title = title;
            this.value = value;
            this.subtitle = subtitle;
            this.accent = accent;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(x, y, x + width, y + height, RAISED);
            border(graphics, x, y, width, height, alpha(accent, 190));
            graphics.drawString(font, title, x + 10, y + 8, MUTED, false);
            graphics.drawString(font, value > 9999 ? format(value) : String.valueOf(value), x + 10, y + 23, TEXT, false);
            graphics.drawString(font, subtitle, x + 10, y + 38, alpha(MUTED, 210), false);
        }
    }

    private class ListingCard implements UiElement {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final MarketScreenData.ListingView listing;
        private final boolean adminTools;
        private final ItemStack stack;
        private final ButtonElement actionButton;

        ListingCard(int x, int y, int width, int height, MarketScreenData.ListingView listing, boolean adminTools) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.listing = listing;
            this.adminTools = adminTools;
            this.stack = decode(listing.itemStack());
            String action = buttonLabel();
            int actionColor = action.equals("Remove") ? DANGER : action.equals("Cancel") ? WARNING : SUCCESS;
            this.actionButton = new ButtonElement(x + width - 66, y + height - 28, 54, 20, action, actionColor, () -> {
            if (adminTools) {
                sendAction("admin_remove", listing.id());
            } else if ("my".equals(mode)) {
                sendAction("cancel", listing.id());
            } else {
                    pendingBuy = listing;
                    rebuildLayout();
                }
            });
        }

        private String buttonLabel() {
            if (adminTools) {
                return "Remove";
            }
            if ("my".equals(mode)) {
                return "Cancel";
            }
            return isAuction() ? "Bid" : "Buy";
        }

        private boolean isAuction() {
            return "auction".equals(listing.listingType());
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            boolean over = hovered(x, y, width, height, mouseX, mouseY);
            graphics.fill(x, y, x + width, y + height, over ? argb(0x132D49) : RAISED);
            border(graphics, x, y, width, height, listing.category().equals("admin") ? WARNING : alpha(ACCENT, 180));
            if (!stack.isEmpty()) {
                graphics.renderFakeItem(stack, x + 10, y + 11);
                graphics.renderItemDecorations(font, stack, x + 10, y + 11);
            }
            graphics.drawString(font, trim(listing.itemName(), width - 94), x + 34, y + 8, TEXT, false);
            graphics.drawString(font, "Seller: " + listing.sellerName(), x + 34, y + 22, MUTED, false);
            String priceLine = isAuction()
                ? "Current Bid: " + format(listing.currentBid() > 0L ? listing.currentBid() : listing.startPrice())
                : "Price: " + format(listing.price());
            graphics.drawString(font, priceLine + " | " + listing.category(), x + 34, y + 36, listing.category().equals("admin") ? WARNING : SUCCESS, false);
            graphics.drawString(font, (isAuction() ? "Ends In: " : "Ends In: ") + listing.timeRemaining(), x + 34, y + 50, MUTED, false);
            if (isAuction() && listing.buyoutPrice() > 0L) {
                graphics.drawString(font, "Buyout " + format(listing.buyoutPrice()), x + 34, y + 64, WARNING, false);
            }
            if (AuctionConfig.uiShowItemMarketStats()) {
                graphics.drawString(font, "7d avg " + stat(listing.average7d()) + " | low " + stat(listing.lowestActivePrice()), x + 10, y + height - 18, alpha(MUTED, 220), false);
            }
            actionButton.render(graphics, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return actionButton.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
            if (hovered(x, y, width, height, mouseX, mouseY)) {
                List<Component> tooltip = List.of(
                    Component.literal(listing.itemName()),
                    Component.literal("Seller: " + listing.sellerName()),
                    Component.literal(isAuction() ? "Current Bid: " + format(listing.currentBid()) : "Price: " + format(listing.price())),
                    Component.literal(isAuction() ? "Buyout: " + stat(listing.buyoutPrice()) : "Type: Fixed Price"),
                    Component.literal("Listing ID: " + listing.id()),
                    Component.literal("Lowest active: " + stat(listing.lowestActivePrice())),
                    Component.literal("7d average: " + stat(listing.average7d())),
                    Component.literal("Sold 7d: " + listing.sold7d())
                );
                graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            }
        }
    }

    private class LineChartElement implements UiElement {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final String title;
        private final List<MarketScreenData.ChartPoint> points;

        LineChartElement(int x, int y, int width, int height, String title, List<MarketScreenData.ChartPoint> points) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.title = title;
            this.points = points;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(x, y, x + width, y + height, RAISED);
            border(graphics, x, y, width, height, alpha(ACCENT, 150));
            graphics.drawString(font, title, x + 10, y + 8, TEXT, false);
            int left = x + 34;
            int top = y + 28;
            int right = x + width - 12;
            int bottom = y + height - 22;
            for (int i = 0; i < 4; i++) {
                int gy = top + (bottom - top) * i / 3;
                graphics.fill(left, gy, right, gy + 1, GRID);
            }
            long max = Math.max(1L, MarketChartService.maxPointValue(points));
            if (points.size() < 2) {
                graphics.drawString(font, "No sales yet", left, top + 18, MUTED, false);
                return;
            }
            int prevX = left;
            int prevY = bottom - (int) ((points.getFirst().value() * (bottom - top)) / max);
            for (int i = 1; i < points.size(); i++) {
                int px = left + (right - left) * i / (points.size() - 1);
                int py = bottom - (int) ((points.get(i).value() * (bottom - top)) / max);
                drawLine(graphics, prevX, prevY, px, py, ACCENT);
                prevX = px;
                prevY = py;
            }
            graphics.drawString(font, format(max), x + 6, top, MUTED, false);
        }
    }

    private class BarChartElement implements UiElement {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final String title;
        private final List<MarketScreenData.NamedValue> values;

        BarChartElement(int x, int y, int width, int height, String title, List<MarketScreenData.NamedValue> values) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.title = title;
            this.values = values;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(x, y, x + width, y + height, RAISED);
            border(graphics, x, y, width, height, alpha(ACCENT, 150));
            graphics.drawString(font, title, x + 10, y + 8, TEXT, false);
            List<MarketScreenData.NamedValue> sorted = values.stream()
                .sorted(Comparator.comparingLong(MarketScreenData.NamedValue::value).reversed())
                .limit(10)
                .toList();
            long max = Math.max(1L, MarketChartService.maxValue(sorted));
            int rowY = y + 28;
            for (MarketScreenData.NamedValue value : sorted) {
                int barW = (int) ((width - 118L) * value.value() / max);
                graphics.drawString(font, trim(value.label(), 82), x + 10, rowY + 2, MUTED, false);
                graphics.fill(x + 98, rowY, x + 98 + barW, rowY + 10, ACCENT);
                graphics.drawString(font, value.count() > 0 ? String.valueOf(value.count()) : format(value.value()), x + 104 + barW, rowY + 1, TEXT, false);
                rowY += 15;
                if (rowY > y + height - 12) {
                    break;
                }
            }
        }
    }

    private class PieChartElement implements UiElement {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final String title;
        private final List<MarketScreenData.NamedValue> values;
        private final int[] colors = {ACCENT, SUCCESS, DANGER, WARNING, argb(0x8BC6FF)};

        PieChartElement(int x, int y, int width, int height, String title, List<MarketScreenData.NamedValue> values) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.title = title;
            this.values = values;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(x, y, x + width, y + height, RAISED);
            border(graphics, x, y, width, height, alpha(ACCENT, 150));
            graphics.drawString(font, title, x + 10, y + 8, TEXT, false);
            long total = values.stream().mapToLong(MarketScreenData.NamedValue::value).sum();
            if (total <= 0L) {
                graphics.drawString(font, "No transactions yet", x + 12, y + 32, MUTED, false);
                return;
            }
            int radius = Math.max(28, Math.min(height - 52, width / 3) / 2);
            int cx = x + 22 + radius;
            int cy = y + 36 + radius;
            double start = 0D;
            for (int i = 0; i < values.size(); i++) {
                double span = Math.PI * 2D * values.get(i).value() / total;
                drawPieSlice(graphics, cx, cy, radius, start, start + span, colors[i % colors.length]);
                start += span;
            }
            int legendX = cx + radius + 18;
            int legendY = y + 34;
            for (int i = 0; i < values.size(); i++) {
                MarketScreenData.NamedValue value = values.get(i);
                int color = colors[i % colors.length];
                graphics.fill(legendX, legendY, legendX + 8, legendY + 8, color);
                long pct = Math.round(value.value() * 100D / total);
                graphics.drawString(font, value.label() + " " + pct + "%", legendX + 12, legendY, TEXT, false);
                legendY += 14;
            }
        }
    }

    private void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            graphics.fill(x, y, x + 2, y + 2, color);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private void drawPieSlice(GuiGraphics graphics, int cx, int cy, int radius, double start, double end, int color) {
        for (int py = -radius; py <= radius; py += 2) {
            for (int px = -radius; px <= radius; px += 2) {
                if (px * px + py * py > radius * radius) {
                    continue;
                }
                double angle = Math.atan2(py, px);
                if (angle < 0D) {
                    angle += Math.PI * 2D;
                }
                if (angle >= start && angle <= end) {
                    graphics.fill(cx + px, cy + py, cx + px + 2, cy + py + 2, color);
                }
            }
        }
    }
}
