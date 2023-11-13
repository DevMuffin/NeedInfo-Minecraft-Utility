package com.dungeonbuilder.needinfo;

import static com.dungeonbuilder.utils.needinfo.NeedInfoFlow.R;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.dungeonbuilder.utils.inventory.ModernInventory;
import com.dungeonbuilder.utils.inventory.inventories.NeedInfoInventories;
import com.dungeonbuilder.utils.item.InventoryItem;
import com.dungeonbuilder.utils.needinfo.NeedInfoFlow.NeedInfo;
import com.dungeonbuilder.utils.needinfo.NeedInfoFlow.NeedInfoType;
import com.dungeonbuilder.utils.needinfo.ObjectArray;

public class TestExecutor implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            System.out.println("Console rejected.");
            return false;
        }
        Player player = (Player) sender;
        switch (label.toLowerCase()) {
            case "needinfoplugintest":
                handleTest(player);
                return true;
            default:
                return false;
        }
    }

    private void handleTest(Player player) {
        ModernInventory modernInv = ModernInventory.create(player.getUniqueId(), pageNum -> {
            return "TITLE";
        });
        modernInv.items(
                new InventoryItem().material(Material.BEDROCK).click(NeedInfoInventories.obtainConsumer("bedrock")),
                new InventoryItem().material(Material.GLASS_PANE).click(e -> {
                    NeedInfo.inject(e.player().getUniqueId(), gots -> {
                        return ObjectArray.of(((int) gots.get(0)) * ((int) gots.get(1)));
                    }, R("2.1", NeedInfoType.NUMBER), R("2.2", NeedInfoType.NUMBER));
                }));
        NeedInfo.assign(player.getUniqueId(), gots -> {
            String total = "";
            for (Object got : gots) {
                total += got.toString();
            }
            return ObjectArray.of(total);
        }, R("1", NeedInfoType.TEXT),
                R("2", NeedInfoType.SELECT_VALUE_FROM_INVENTORY).withSelectionInventory(modernInv),
                R("3", NeedInfoType.BOOLEAN));
    }
}
