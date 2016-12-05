package swagClan.net.gatesPlus.gate;

import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import swagClan.net.gatesPlus.Main;

public class Gate {

	private Main main;
	private EntityPlayer player;
	private ArrayList gateBlocks;
	private ArrayList poweredBlocks;
	private boolean opengate;
	private CheckTimer checkTimer;
	private Animatie animatie;
	private String naam;
	private int id;

	public Gate(Main main) {
		this.main = main;
		checkTimer = new CheckTimer(main, this);
		animatie = new Animatie(main, this);
	}

	/**
	 * returns the blocks that need to bee powered to open the gate
	 * 
	 * @return
	 */
	public ArrayList getPowerdBlocks() {
		return poweredBlocks;
	}

	/**
	 * returns true if the gate needs to open and false is the gate needs to
	 * close
	 * 
	 * @return
	 */
	public boolean gateNeedstoOpen() {
		return opengate;
	}

	/**
	 * set true if the gate needs to open set false if the gate needs to close
	 * 
	 * @param opengate
	 *            boolean
	 */
	public void setOpengate(boolean opengate) {
		this.opengate = opengate;
	}

	/**
	 * Clears the gateBlocks by setting these blocks to air and sends a message
	 * to the corresponding player
	 */
	public void clear() {
		// TODO: set all blocks air
		checkTimer.stopTimer();
		animatie.stopTimer();
		player.addChatMessage(new TextComponentString("Your gate " + naam + " has been removed"));
	}

	/**
	 * Returns the name of the gate
	 * 
	 * @return String
	 */
	public String getNaam() {
		return naam;
	}

	/**
	 * Sets the name of the gate
	 * 
	 * @param naam
	 */
	public void setNaam(String naam) {
		this.naam = naam;
	}

	/**
	 * Returns the ID of the gate
	 * 
	 * @return int
	 */
	public int getId() {
		return id;
	}

	/**
	 * Sets the ID of the gate
	 * 
	 * @param id
	 */
	public void setId(int id) {
		this.id = id;
	}

}
