package net.mcauction.auctionhouse;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.UUID;
import net.mcauction.auctionhouse.gui.GuiManager;
import net.mcauction.auctionhouse.gui.SellItemHolder;
import net.mcauction.auctionhouse.session.SellInputProcessor;
import net.mcauction.auctionhouse.session.SellSession;
import net.mcauction.auctionhouse.session.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** 隔離Paperでスマホを持った出品選択と、選択欄の再確認を検証する。 */
public final class PaperAuctionPhoneProbe extends JavaPlugin {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                runProbe();
                getLogger().info("AUCTION_PHONE_PROBE_PASS");
            } catch (Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE, "AUCTION_PHONE_PROBE_FAIL", error);
            } finally {
                Bukkit.shutdown();
            }
        }, 60L);
    }

    private void runProbe() throws Exception {
        JavaPlugin auction = (JavaPlugin) Bukkit.getPluginManager().getPlugin("AuctionHouse");
        check(auction != null && auction.isEnabled(), "AuctionHouse enabled");
        Object command = auction.getCommand("ah").getExecutor();
        AuctionService service = (AuctionService) field(command, "auctionService");
        GuiManager gui = (GuiManager) field(command, "guiManager");

        ItemStack phone = new ItemStack(Material.CLOCK);
        var meta = phone.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("ecolifeassist", "phone"), PersistentDataType.BYTE, (byte) 1);
        phone.setItemMeta(meta);
        ItemStack[] items = new ItemStack[36];
        items[0] = phone;
        items[5] = new ItemStack(Material.DIAMOND, 3);
        items[6] = new ItemStack(Material.CLOCK);
        Inventory[] opened = new Inventory[1];
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
                new Class<?>[]{PlayerInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getItem" -> items[(int) args[0]];
                    case "getItemInMainHand" -> items[0];
                    default -> null;
                });
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getInventory" -> inventory;
                    case "getUniqueId" -> UUID.nameUUIDFromBytes("auction-phone-probe".getBytes());
                    case "getName" -> "AuctionPhoneProbe";
                    case "openInventory" -> { opened[0] = (Inventory) args[0]; yield null; }
                    default -> null;
                });

        check(service.canStartSell(player) == AuctionService.SellStartResult.BANNED_MATERIAL,
                "phone rejected from main hand");
        check(service.canStartSell(player, 5) == AuctionService.SellStartResult.OK,
                "normal inventory item accepted");
        gui.openSellItemPicker(player);
        check(opened[0] != null && opened[0].getHolder() instanceof SellItemHolder, "picker opened");
        check(opened[0].getItem(0) == null, "phone hidden from picker");
        check(opened[0].getItem(5).getType() == Material.DIAMOND, "diamond shown in picker");
        check(opened[0].getItem(6).getType() == Material.CLOCK, "ordinary clock shown in picker");

        SellSession session = new SellSession(items[5].clone(), 3, 5);
        session.setAmount(1);
        items[5] = null;
        check(service.confirmSell(player, session) == AuctionService.SellConfirmResult.ITEM_CHANGED,
                "moved item rejected before payment");
        items[5] = phone;
        check(service.confirmSell(player, session) == AuctionService.SellConfirmResult.ITEM_CHANGED,
                "phone rejected at confirmation");

        Object conversation = field(command, "sellConversation");
        SellInputProcessor processor = (SellInputProcessor) field(conversation, "processor");
        SessionManager sessions = (SessionManager) field(conversation, "sessionManager");
        SellSession flow = new SellSession(new ItemStack(Material.DIAMOND, 3), 3, 5);
        sessions.startSell(player.getUniqueId(), flow);
        check(flow.getStep() == SellSession.Step.START_PRICE, "sell starts at start price");
        processor.handle(player, "500");
        check(flow.getStep() == SellSession.Step.CONFIRM, "start price leads to confirm");
        check(flow.getAmount() == 3 && flow.getBuyoutPrice() == 0 && flow.getDurationHours() == 24,
                "defaults are all items, no buyout, 24 hours");
        String confirm = processor.promptText(flow);
        check(confirm.contains("3個") && confirm.contains("500") && confirm.contains("なし") && confirm.contains("24時間"),
                "confirm shows defaults: " + confirm);
        processor.handle(player, "えっと");
        check(flow.getStep() == SellSession.Step.CONFIRM, "unknown answer stays at confirm");
        processor.handle(player, "変更");
        check(flow.getStep() == SellSession.Step.AMOUNT, "change asks amount first");
        processor.handle(player, "2");
        check(flow.getStep() == SellSession.Step.BUYOUT_PRICE, "amount leads to buyout");
        processor.handle(player, "500");
        check(flow.getStep() == SellSession.Step.BUYOUT_PRICE, "buyout not above start price rejected");
        processor.handle(player, "900");
        check(flow.getStep() == SellSession.Step.DURATION, "buyout leads to duration");
        processor.handle(player, "48");
        check(flow.getStep() == SellSession.Step.CONFIRM, "duration returns to confirm");
        check(flow.getAmount() == 2 && flow.getStartPrice() == 500 && flow.getBuyoutPrice() == 900
                && flow.getDurationHours() == 48, "changed values kept");
        processor.handle(player, "いいえ");
        check(sessions.getSell(player.getUniqueId()) == null, "no ends the session");

        SellSession single = new SellSession(new ItemStack(Material.DIAMOND), 1, 5);
        sessions.startSell(player.getUniqueId(), single);
        processor.handle(player, "100");
        processor.handle(player, "変更");
        check(single.getStep() == SellSession.Step.BUYOUT_PRICE, "single item skips amount");
        sessions.end(player.getUniqueId());
    }
}
