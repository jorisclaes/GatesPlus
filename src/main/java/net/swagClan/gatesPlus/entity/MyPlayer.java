package net.swagClan.gatesPlus.entity;

import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayer;
import net.swagClan.gatesPlus.Main;
import net.swagClan.gatesPlus.entity.gate.Gate;

public class MyPlayer {

	private Main main;
	private EntityPlayer player;
	private ArrayList<Gate> gates;

	public MyPlayer(Main main) {
		this.main = main;
		gates = new ArrayList<Gate>();
	}

	/**
	 * returns the list of gates the player has
	 * 
	 * @return
	 */
	public ArrayList getGates() {
		return gates;
	}

	/**
	 * adds a gate to the gates list of the player
	 * 
	 * @param gate
	 */
	public void addGate(Gate gate) {
		gates.add(gate);
	}

	/**
	 * removes a existing gate in the list of the player
	 * 
	 * @param gate
	 * @return true if success
	 */
	public boolean removeGate(Gate gate) {
		for (Gate g : gates) {
			if (getClass().equals(gate)) {
				g.clear();
				break;
			}
		}
		return gates.remove(gate);
	}
	
	/**
	 * returns the owner of the gates
	 * @return
	 */
	public EntityPlayer getPlayer() {
		return player;
	}

	/**
	 * sets the owner of the gates
	 * @param player
	 */
	public void setPlayer(EntityPlayer player) {
		this.player = player;
	}
	

}
