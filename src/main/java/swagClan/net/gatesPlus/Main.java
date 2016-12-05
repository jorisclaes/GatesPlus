package swagClan.net.gatesPlus;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import swagClan.net.gatesPlus.comand.CommandMain;

@Mod(modid = Main.MODID, version = Main.VERSION)
public class Main {
	public static final String MODID = "GatesPlus";
	public static final String VERSION = "0.1";
	
	public CommandMain comandMain = null;

    @EventHandler
    public void init(FMLServerStartingEvent event)
    {
    	this.comandMain = new CommandMain(this, event);
    }

}