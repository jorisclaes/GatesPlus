package swagClan.net.gatesPlus.comand;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import scala.swing.TextComponent;
import scala.tools.nsc.ScalaDoc.Command;
import swagClan.net.gatesPlus.Main;

public class CommandMain implements ICommand {
	public Main main;
	private List commands;

	public CommandMain(Main main, FMLServerStartingEvent event) {
		this.main = main;
		this.commands = new ArrayList();
		this.commands.add("gatesplus");
		this.commands.add("gp");
		event.registerServerCommand(this);
	}

	@Override
	public int compareTo(ICommand o) {
		return 0;
	}

	@Override
	public String getCommandName() {
		return "gp";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/gp";
	}

	@Override
	public List<String> getCommandAliases() {
		return this.commands;
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		sender.addChatMessage(new TextComponentString("test"));
	}

	@Override
	public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
		return true;
	}

	@Override
	public List<String> getTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args,
			BlockPos pos) {
		return null;
	}

	@Override
	public boolean isUsernameIndex(String[] args, int index) {
		return false;
	}

}
