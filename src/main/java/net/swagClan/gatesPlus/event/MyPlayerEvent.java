package net.swagClan.gatesPlus.event;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.swagClan.gatesPlus.Main;
import net.swagClan.gatesPlus.entity.MyPlayer;

public class MyPlayerEvent {

	private Main main;

	public MyPlayerEvent(Main main) {
		this.main = main;
	}

	/**
	 * Is called when a player joins the server
	 * @param evt
	 */
	@SubscribeEvent	
	public void onLogin(PlayerLoggedInEvent evt) {
		MyPlayer player = new MyPlayer(main);
		player.setPlayer(evt.player);
		main.addPlayer(player);
	}
	
	/**
	 * is called when a player logs out
	 * @param evt
	 */
	@SubscribeEvent
	public void onLogout(PlayerLoggedOutEvent evt){
		main.removePlayer(evt.player);
	}
}
