package com.dungeonbuilder.needinfo;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.dungeonbuilder.utils.message.Message;
import com.dungeonbuilder.utils.needinfo.NeedInfoFlow;
import com.dungeonbuilder.utils.needinfo.NeedInfoFlow.NeedInfo;
import com.dungeonbuilder.utils.needinfo.NeedInfoFlow.NeedInfoRequest;
import com.dungeonbuilder.utils.needinfo.NeedInfoFlow.NeedInfoSpecifier;
import com.dungeonbuilder.utils.needinfo.NeedInfoFlow.NeedInfoType;
import com.dungeonbuilder.utils.serialization.PrimitiveSerializer;
import com.dungeonbuilder.utils.serialization.info.Dungeon;
import com.dungeonbuilder.utils.threads.Threads;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.title.Title;
import net.md_5.bungee.api.ChatColor;

public class NeedEvents implements Listener {

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerQuit(PlayerQuitEvent e) {
		UUID playerId = e.getPlayer().getUniqueId();
		NeedInfo.unassign(playerId);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onBlockBreak(BlockBreakEvent e) {
		Player p = e.getPlayer();
		UUID uuid = p.getUniqueId();
		NeedInfo needInfo = NeedInfo.getAssignedTo(uuid);
		if (needInfo == null)
			return;
		NeedInfoFlow flow = needInfo.currentFlow();
		NeedInfoRequest currentRequest = flow.currentRequest();
		NeedInfoType requestType = currentRequest.type();
		if (requestType != NeedInfoType.LOCATION && requestType != NeedInfoType.BLOCK)
			return;
		if (!currentRequest.specifiers().contains(NeedInfoSpecifier.SELECT_BY_BREAKING))
			return;
		// At this point we know we definitely want to cancel the event.
		e.setCancelled(true);
		Block block = e.getBlock();
		Location loc = block.getLocation();
		if (currentRequest.specifiers().contains(NeedInfoSpecifier.IN_DUNGEONZONE)) {
			Optional<Dungeon> dungeonOpt = Dungeon.in(loc);
			if (dungeonOpt.isEmpty() || !dungeonOpt.get().dungeonZoneContainsLocation(loc)) {
				Message.e("The selected block is &anot&r within a dungeon zone!");
				return;
			}
			loc = dungeonOpt.get().normalizeDungeonZoneLocation(loc);
		}
		Optional<Map<String, Object>> additionalInfoOpt = currentRequest.getAdditionalInfo();
		if (additionalInfoOpt.isPresent()) {
			Map<String, Object> additionalInfo = additionalInfoOpt.get();
			@SuppressWarnings("unchecked")
			Set<Material> validTypes = (Set<Material>) additionalInfo
					.get(NeedInfoRequest.ADDITIONAL_KEY_ALLOWED_BLOCK_TYPES);
			if (validTypes != null && !validTypes.contains(block.getType())) {
				Message.e("The block you selected is not valid, please try again with a valid block!").send(p);
				return;
			}
		}
		if (requestType.equals(NeedInfoType.LOCATION)) {
			flow.obtain(loc);
		} else if (requestType.equals(NeedInfoType.BLOCK)) {
			flow.obtain(loc, block.getBlockData());
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onBlockPlace(BlockPlaceEvent e) {
		Player p = e.getPlayer();
		UUID uuid = p.getUniqueId();
		NeedInfo needInfo = NeedInfo.getAssignedTo(uuid);
		if (needInfo == null)
			return;
		NeedInfoFlow flow = needInfo.currentFlow();
		NeedInfoRequest currentRequest = flow.currentRequest();
		NeedInfoType requestType = currentRequest.type();
		if (requestType != NeedInfoType.LOCATION)
			return;
		if (currentRequest.specifiers().contains(NeedInfoSpecifier.SELECT_BY_BREAKING))
			return;
		// At this point we know we definitely want to cancel the event.
		e.setCancelled(true);
		Location loc = e.getBlock().getLocation();
		if (currentRequest.specifiers().contains(NeedInfoSpecifier.IN_DUNGEONZONE)) {
			Optional<Dungeon> dungeonOpt = Dungeon.in(loc);
			if (dungeonOpt.isEmpty() || !dungeonOpt.get().dungeonZoneContainsLocation(loc)) {
				Message.e("The selected block is &anot&r within a dungeon zone!");
				return;
			}
			loc = dungeonOpt.get().normalizeDungeonZoneLocation(loc);
		}
		flow.obtain(loc);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerDropItem(PlayerDropItemEvent e) {
		Player p = e.getPlayer();
		UUID uuid = p.getUniqueId();
		NeedInfo needInfo = NeedInfo.getAssignedTo(uuid);
		if (needInfo == null)
			return;
		NeedInfoFlow flow = needInfo.currentFlow();
		NeedInfoRequest currentRequest = flow.currentRequest();
		if (currentRequest.type() != NeedInfoType.ITEMSTACK)
			return;

		e.setCancelled(true);
		flow.obtain(e.getItemDrop().getItemStack());
	}

	private record HandleErrorInfo(String title, String subtitle, String chatMsg) {
	};

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerChat(AsyncChatEvent e) {
		Player p = e.getPlayer();
		UUID uuid = p.getUniqueId();
		NeedInfo needInfo = NeedInfo.getAssignedTo(uuid);
		if (needInfo == null)
			return;
		NeedInfoFlow flow = needInfo.currentFlow();
		NeedInfoRequest currentRequest = flow.currentRequest();
		switch (currentRequest.type()) {
			case BOOLEAN:
			case TEXT:
			case NEXT:
			case NUMBER:
				break;
			default:
				return;
		}
		e.setCancelled(true);
		Component originalMessage = e.originalMessage();
		if (!(originalMessage instanceof TextComponent)) {
			return;
		}
		Consumer<HandleErrorInfo> handleError = (info) -> {
			p.showTitle(Title.title(Component.text(info.title()), Component.text(info.subtitle())));
			Message.e(info.chatMsg()).send(p);
			Threads.runMainLater(() -> {
				flow.tryAgain();
			}, flow.duration(2 * 20L, 10L));
		};
		String message = ((TextComponent) originalMessage).content();
		if (currentRequest.type() == NeedInfoType.TEXT) {
			Threads.runMain(() -> {
				if (currentRequest.specifiers().contains(NeedInfoSpecifier.TEXT_FORMAT_COLOR_CODES)) {
					flow.obtain(ChatColor.translateAlternateColorCodes('&', message));
				} else {
					flow.obtain(message);
				}
			});
		} else if (currentRequest.type() == NeedInfoType.BOOLEAN) {
			String trueOption = "true", falseOption = "false";
			if (currentRequest.specifiers().contains(NeedInfoSpecifier.BOOLEAN_ON_OFF)) {
				trueOption = "on";
				falseOption = "off";
			} else if (currentRequest.specifiers().contains(NeedInfoSpecifier.BOOLEAN_YES_NO)) {
				trueOption = "yes";
				falseOption = "no";
			}
			if (message.equalsIgnoreCase(trueOption)) {
				Threads.runMain(() -> {
					flow.obtain(true);
				});
			} else if (message.equalsIgnoreCase(falseOption)) {
				Threads.runMain(() -> {
					flow.obtain(false);
				});
			} else {
				handleError.accept(new HandleErrorInfo("Error", "not a valid conditional!",
						"&a" + message + "&r is not a valid conditional.  Valid options are " + trueOption + " or "
								+ falseOption + "."));
				return;
			}
		} else if (currentRequest.type() == NeedInfoType.NUMBER) {
			if (currentRequest.specifiers().contains(NeedInfoSpecifier.NUMBER_DOUBLE)) {
				if (!PrimitiveSerializer.isDouble(message)) {
					handleError.accept(new HandleErrorInfo("Error", "not a valid number!",
							"&a" + message + "&r is not a valid number!"));
					return;
				}
				double doubleValue = PrimitiveSerializer.doubleFrom(message);
				if (doubleValue == 0D && !currentRequest.specifiers().contains(NeedInfoSpecifier.NUMBER_ALLOW_ZERO)) {
					handleError.accept(new HandleErrorInfo("Error", "number cannot be 0!",
							"&a0&r is not a valid option, please try again!"));
					return;
				}
				if (currentRequest.specifiers().contains(NeedInfoSpecifier.NUMBER_POSITIVE) && doubleValue < 0D) {
					handleError.accept(new HandleErrorInfo("Error", "number must be positive!",
							"&a" + message + "&r is a negative number, please try again!"));
					return;
				}
				Threads.runMain(() -> {
					flow.obtain(doubleValue);
				});
			} else {
				if (!PrimitiveSerializer.isInt(message)) {
					handleError.accept(new HandleErrorInfo("Error", "not a valid integer!",
							"&a" + message + "&r is not a valid integer!  Integers are whole numbers."));
					return;
				}
				int intValue = PrimitiveSerializer.intFrom(message);
				if (intValue == 0D && !currentRequest.specifiers().contains(NeedInfoSpecifier.NUMBER_ALLOW_ZERO)) {
					handleError.accept(new HandleErrorInfo("Error", "number cannot be 0!",
							"&a0&r is not a valid option, please try again!"));
					return;
				}
				if (currentRequest.specifiers().contains(NeedInfoSpecifier.NUMBER_POSITIVE) && intValue < 0D) {
					handleError.accept(new HandleErrorInfo("Error", "number must be positive!",
							"&a" + message + "&r is a negative number, please try again!"));
					return;
				}
				Threads.runMain(() -> {
					flow.obtain(intValue);
				});
			}
		} else if (currentRequest.type() == NeedInfoType.NEXT) {
			if (message.equalsIgnoreCase("next")) {
				Threads.runMain(() -> {
					flow.obtain(message);
				});
			}
		}
	}
}