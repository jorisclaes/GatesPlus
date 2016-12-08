package swagClan.net.gatesPlus;

import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import swagClan.net.gatesPlus.comand.CommandMain;
import swagClan.net.gatesPlus.entity.MyPlayer;
import swagClan.net.gatesPlus.event.MyPlayerEvent;
import swagClan.net.gatesPlus.fileHandler.SettingsReader;

@Mod(modid = Main.MODID, version = Main.VERSION)
public class Main {
	public static final String MODID = "GatesPlus";
	public static final String VERSION = "0.1";

	private CommandMain comandMain = null;
	private ArrayList<MyPlayer> players;
	private SettingsReader settingsReader;

	@EventHandler
	public void onInit(FMLServerStartingEvent event) {
		this.comandMain = new CommandMain(this, event);
		players = new ArrayList<MyPlayer>();
		FMLCommonHandler.instance().bus().register(new MyPlayerEvent(this));
		settingsReader = new SettingsReader(this);
	}
	
	@EventHandler
	public void onServerClose(FMLServerStoppingEvent event){
		//TODO: save data
	}

	/**
	 * Ads a player to the list
	 * 
	 * @param player
	 */
	@EventHandler
	public void addPlayer(MyPlayer player) {
		this.players.add(player);
	}

	/**
	 * Removes a player from the list
	 * 
	 * @param player
	 * @return true if successful
	 */
	public boolean removePlayer(EntityPlayer player) {
		for (MyPlayer p : players){
			if (p.getPlayer().equals(player)){
				return players.remove(p);
			}
		}
		return false;
	}
	
	/**
	 * returns the list of players
	 * @return
	 */
	public ArrayList<MyPlayer> getPlayers(){
		return players;
	}
	
	/**
	 * set the list of players
	 * do this if u change some thing only use this when needed
	 * @param players
	 */
	public void setPlayers(ArrayList<MyPlayer> players){
		this.players = players;
	}

}