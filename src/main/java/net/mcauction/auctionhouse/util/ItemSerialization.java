package net.mcauction.auctionhouse.util;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ItemStack#serializeAsBytes() の結果をBase64文字列にしてDBへ保存・復元するためのユーティリティ。
 * 復元に失敗した場合は例外を投げず、呼び出し側がプラグイン全体を落とさず安全側で扱えるようにする。
 */
public final class ItemSerialization {

    private ItemSerialization() {
    }

    public static String serialize(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public static Optional<ItemStack> deserialize(String base64, Logger logger, String context) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return Optional.of(ItemStack.deserializeBytes(bytes));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "アイテムデータの復元に失敗しました(" + context + ")。該当レコードは安全側で無視されます。", e);
            return Optional.empty();
        }
    }
}
