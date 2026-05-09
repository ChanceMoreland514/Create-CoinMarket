package com.chancemoreland.create_coinmarket.client;

import com.chancemoreland.create_coinmarket.data.AuctionConfig;
import com.chancemoreland.create_coinmarket.menu.AuctionMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class LegacyAuctionBrowserScreen extends AbstractContainerScreen<AuctionMenu> {
    public LegacyAuctionBrowserScreen(AuctionMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.inventoryLabelY = 128;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int background = argb(AuctionConfig.uiPrimaryColor());
        int panel = argb(AuctionConfig.uiPanelColor());
        int accent = argb(AuctionConfig.uiAccentColor());
        graphics.fill(0, 0, this.width, this.height, background);
        graphics.fill(this.leftPos - 8, this.topPos - 12, this.leftPos + this.imageWidth + 8, this.topPos + this.imageHeight + 8, panel);
        drawBorder(graphics, this.leftPos - 8, this.topPos - 12, this.imageWidth + 16, this.imageHeight + 20, accent);
        graphics.fill(this.leftPos + 4, this.topPos + 14, this.leftPos + this.imageWidth - 4, this.topPos + 122, argb(0x081827));
        drawBorder(graphics, this.leftPos + 4, this.topPos + 14, this.imageWidth - 8, 108, argb(0x12304F));
        graphics.fill(this.leftPos + 4, this.topPos + 126, this.leftPos + this.imageWidth - 4, this.topPos + 216, argb(0x07111F));
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, Component.literal("Create: CoinMarket - " + this.menu.titleSuffix()), this.titleLabelX, this.titleLabelY, AuctionConfig.uiTextColor(), false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, AuctionConfig.uiMutedTextColor(), false);
    }

    private static int argb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
