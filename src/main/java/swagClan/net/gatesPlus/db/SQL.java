package swagClan.net.gatesPlus.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SQL {
	private static Connection c = null;
	public static String url, dbNaam, username, passwoord;

	/**
	 * This will try to create a connection to the database
	 * 
	 * @throws SQLException
	 */
	public static void makeConnection() throws SQLException {
		try {
			// MySql db
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			c = DriverManager.getConnection("jdbc:mysql://" + url, username, passwoord);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Executes the query that is given in the SQL string
	 * @param sql
	 * @return
	 */
	public static ResultSet voerQryUit(String sql){
		Statement stm = null;
		ResultSet rs = null;
		try {
			stm = c.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
			rs = stm.executeQuery(sql);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			return null;
		}
		return rs;
	}
	
	/**
	 * @param sql
	 * @return
	 */
	public static int exicuteUpdate(String sql){
		int cont = -1;
		Statement stm;
		try {
			stm = c.createStatement();
			cont = stm.executeUpdate(sql);
			stm.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return -1;
		}
		return cont;
	}
	
	/**
	 * This will try to close the connection
	 * @throws SQLException
	 */
	public static void closeConnection() throws SQLException{
		c.close();
	}
}
