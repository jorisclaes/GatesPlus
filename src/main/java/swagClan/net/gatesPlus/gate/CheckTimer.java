package swagClan.net.gatesPlus.gate;

import java.awt.List;
import java.util.ArrayList;

import net.minecraft.block.Block;
import swagClan.net.gatesPlus.Main;

public class CheckTimer extends Thread {

	static int check_interval = 200;

	private Main main;
	private ArrayList poweredBloks;
	private Gate gate;

	public CheckTimer(Main main, Gate gate) {
		this.main = main;
		this.gate = gate;
	}

	/**
	 * 
	 * @return check time in ms
	 */
	public static int getCheck_interval() {
		return check_interval;
	}

	/**
	 * set the check interval in ms
	 * 
	 * @param check_interval
	 *            int
	 */
	public static void setCheck_interval(int check_interval) {
		CheckTimer.check_interval = check_interval;
	}

	@Override
	public void run() {
		boolean nitsgevonden = true;
		for (Object o : poweredBloks) {
			if (o instanceof Block) {
				Block block = ((Block) o);
				// TODO: if block is powered
				gate.setOpengate(true);
				nitsgevonden = false;
				break;
			}
		}
		if (nitsgevonden) {
			gate.setOpengate(false);
		}
	}

}
