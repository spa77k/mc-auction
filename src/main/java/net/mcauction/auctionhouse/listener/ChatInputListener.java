package net.mcauction.auctionhouse.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mcauction.auctionhouse.session.BidInputProcessor;
import net.mcauction.auctionhouse.session.BidSession;
import net.mcauction.auctionhouse.session.SellInputProcessor;
import net.mcauction.auctionhouse.session.SellSession;
import net.mcauction.auctionhouse.session.SessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

/**
 * 出品・入札の入力は、通常は会話(Conversation)がチャット入力を先に処理するため
 * このリスナーは発火しない。会話が効かないサーバー実装/フォークでも入力が壊れず、
 * かつ入力中の発言が全体チャットやDiscord等の連携先に流れないようにする保険が二段構え。
 * 保険は新式AsyncChatEventだけでなく旧式AsyncPlayerChatEventも塞ぐ。
 * 実際の入力処理(handleInput)は新式ハンドラのみが行い、旧式ハンドラはキャンセルのみを担当する。
 */
public class ChatInputListener implements Listener {

    private final Plugin plugin;
    private final SessionManager sessionManager;
    private final SellInputProcessor sellInputProcessor;
    private final BidInputProcessor bidInputProcessor;

    public ChatInputListener(Plugin plugin, SessionManager sessionManager, SellInputProcessor sellInputProcessor,
                              BidInputProcessor bidInputProcessor) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.sellInputProcessor = sellInputProcessor;
        this.bidInputProcessor = bidInputProcessor;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!sessionManager.has(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        event.viewers().clear();
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> handleInput(player, input));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("deprecation")
    public void onLegacyChat(AsyncPlayerChatEvent event) { // GriefPreventionとDiscordSRVが旧式イベントしか見ていないため、こちらもキャンセルして塞ぐ
        Player player = event.getPlayer();
        if (!sessionManager.has(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        event.getRecipients().clear();
    }

    private void handleInput(Player player, String input) {
        SellSession sellSession = sessionManager.getSell(player.getUniqueId());
        if (sellSession != null) {
            sellInputProcessor.handle(player, input);
            SellSession updated = sessionManager.getSell(player.getUniqueId());
            if (updated != null) {
                player.sendMessage(sellInputProcessor.promptText(updated));
            }
            return;
        }
        BidSession bidSession = sessionManager.getBid(player.getUniqueId());
        if (bidSession != null) {
            bidInputProcessor.handle(player, input);
            BidSession updated = sessionManager.getBid(player.getUniqueId());
            if (updated != null) {
                player.sendMessage(bidInputProcessor.promptText(updated));
            }
        }
    }
}
