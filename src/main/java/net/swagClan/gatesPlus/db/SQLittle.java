package net.swagClan.gatesPlus.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import net.swagClan.gatesPlus.fileHandler.FileReaderBase;

public class SQLittle {

	private static Connection c = null;

	/**
	 * Creates connection
	 */
	public static void maakConnectie() {
		try {
			Class.forName("org.sqlite.JDBC");
			c = DriverManager.getConnection("jdbc:sqlite:" + FileReaderBase.getBasePath() + "PirateMod.db");
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * gaat een qry uitvoeren
	 * 
	 * @param SQL
	 *            de sql selectie als String
	 * @return als gelukt is returnt die een ResultSet van de data en anders een
	 *         null
	 */
	public static ResultSet voerQryUit(String SQL) {
		Statement stm;
		try {
			stm = c.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
			return stm.executeQuery(SQL);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			return null;
		}
	}

	/**
	 * gaat een update uit voeren in de DB
	 * 
	 * @param sql
	 *            de sql update selectie als String
	 * @return returnt -1 als het niet gelukt is en returnt X aantal rijen als
	 *         int als het wel geluct is
	 * 
	 *         als je 5 rijen aanpast zou die 5 moetten returnen zo niet heeft
	 *         die niet alle rijen gedaan
	 */
	public static int voerUpdateUit(String sql) {
		int cont = 0;
		try {
			Statement stm = c.createStatement();
			cont = stm.executeUpdate(sql);
			stm.close();
		} catch (SQLException ex) {
			ex.printStackTrace();
		}
		return cont;
	}

	/**
	 * gaat de connectie sluiten met de sql file
	 */
	public static void sluitConnectie() {
		try {
			c.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
