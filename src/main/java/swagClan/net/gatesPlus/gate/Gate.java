package swagClan.net.gatesPlus.gate;

import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayer;
import swagClan.net.gatesPlus.Main;

public class Gate {

	private Main main;
	private EntityPlayer player;
	private ArrayList gateBlocks;
	private ArrayList poweredBlocks;
	private boolean opengate;
	private CheckTimer checkTimer;
	private Animatie animatie;

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
	 * Clears the gateBlocks by setting these blocks to air
	 */
	public void clear() {
		//TODO: clear timers
		//TODO: set all blocks of the gate to air
	}

}
