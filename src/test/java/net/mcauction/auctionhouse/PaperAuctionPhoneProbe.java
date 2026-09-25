package net.mcauction.auctionhouse;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.UUID;
import net.mcauction.auctionhouse.gui.GuiManager;
import net.mcauction.auctionhouse.gui.SellItemHolder;
import net.mcauction.auctionhouse.session.SellSession;
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
    }
}
