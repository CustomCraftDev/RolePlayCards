import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;


public class Cards extends JavaPlugin implements Listener{
	
	private FileConfiguration config;
	protected boolean update;
	private Connection sql;
	
	private boolean useconfirm;
	private boolean useprefix;
	private boolean enforce_gender;
	private boolean enforce_age;
	private boolean must_shift;
	private boolean debug;
	
	private List<String> genders;
	private ItemStack book;
	private String prefix;
	private boolean connection = false;

	
	public void onEnable() {
		config = getConfig();
		config.options().copyDefaults(true);
		saveConfig();
		
		try {
	    	Class.forName("org.sqlite.JDBC");
	    	sql = DriverManager.getConnection("jdbc:sqlite:plugins/RPC/players.db");
	    	connection = true;
		}catch(Exception ex) {
			System.out.println(config.getString("msg.sql-error"));
			if(debug) {ex.printStackTrace();}
		}
		
		if (connection) {
			setupDB();
			
			setupVars();
			
			boolean b = config.getBoolean("settings.updater");
			if(b) {
				new Updater(this);
			}
			
			if(debug) {
				System.out.println(ChatColor.stripColor(prefix + "-----------------------------------------------------"));
				System.out.println(ChatColor.stripColor(prefix + " RolePlayCards DEBUG-MODE was activated."));
				System.out.println(ChatColor.stripColor(prefix + " It is advisable to turn it back off ..."));
				System.out.println(ChatColor.stripColor(prefix + " this can be done in the config.yml"));
				System.out.println(ChatColor.stripColor(prefix + "-----------------------------------------------------"));
			}
			
			getServer().getPluginManager().registerEvents(this, this);
			
			for(Player i : getServer().getOnlinePlayers()) {
				insertUser(i.getUniqueId().toString());
			}
		}else {
			this.setEnabled(false);
		}
	}

	
	public void onDisable() {
		try {
			sql.close();
		}catch(Exception ex) {
			if(debug) {ex.printStackTrace();}
		}
	}
	
	
	// [Command]-----------------------------------------------------------------------------------------------------------------------------
	
	
	@SuppressWarnings("deprecation")
	public boolean onCommand(CommandSender sender, Command cmd, String alias, String[] args) {
		try {
			if(cmd.getName().equalsIgnoreCase("rpc") && args.length > 0){
				if(sender instanceof Player) {
					Player p = (Player) sender;
					switch(args[0]) {
						case "name":
							args[0] = "";
							change(p, 0, join(args), useconfirm);
							return true;
						case "gender":
							args[0] = "";
							change(p, 1, join(args), useconfirm);
							return true;
						case "age":
							args[0] = "";
							change(p, 2, join(args), useconfirm);
							return true;
						case "race":
							args[0] = "";
							change(p, 3, join(args), useconfirm);
							return true;
						case "description":
							description(true, p, null);
							return true;
						case "check":
							display(p, p);
							return true;
						case "toggle":
							if(p.hasPermission("rpc.toggle")) {
								manager(p);
							}
							return true;
						case "reload":
							if(p.hasPermission("rpc.reload")) {
								reloadConfig();
								config = getConfig();
								setupVars();
								
								p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("msg.reload")));
								return true;
							}else {
								p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("msg.no-perm")));
								return true;
							}
						case "set":
							if(p.hasPermission("rpc.set")){
								 p = getServer().getPlayer(args[1]);
								 args[0] = "";
								 args[1] = "";
								 if (p != null) {
									switch(args[2]) {
										case "name":
											args[2] ="";
											change(p, 0, join(args), false);
											return true;
										case "gender":
											args[2] ="";
											change(p, 1, join(args), false);
											return true;
										case "age":
											args[2] ="";
											change(p, 2, join(args), false);
											return true;
										case "race":
											args[2] ="";
											change(p, 3, join(args), false);
											return true;
										case "description":
											description(false, p, (Player) sender);
											return true;
									}
								}
							}else {
								p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("msg.no-perm")));
								return true;
							}
							break;
					}
				}else {
					System.out.println(ChatColor.stripColor(prefix) + "Commands ingame only ...");
					return true;
				}
			}		
		}catch(Exception ex) {
			System.out.println(config.getString("msg.sql-error"));
			if(debug) {ex.printStackTrace();}
		}
		return false;
	}
	

	// [Events]------------------------------------------------------------------------------------------------------------------------------
		

	@EventHandler(priority = EventPriority.HIGHEST)
	public void playerChat(AsyncPlayerChatEvent e){
		if(useprefix) {
			if(e.getPlayer().hasPermission("rpc.prefix")) {
				try {
					Statement stmt = sql.createStatement();
					ResultSet r = stmt.executeQuery("SELECT NAME FROM player WHERE UUID='" + e.getPlayer().getUniqueId().toString() + "';");
				    if(r.next()) {
						String nick = r.getString("NAME");
						String format = ChatColor.translateAlternateColorCodes('&', config.getString("settings.prefix"));
						e.setFormat(format.replace("%PLAYER%",nick).replace("%MSG%",e.getMessage()));
				    }
				}catch(Exception ex) {
					System.out.println(config.getString("msg.sql-error"));
					if(debug) {ex.printStackTrace();}
				}
			}
		}
	}
	
	
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent e) {
		insertUser(e.getPlayer().getUniqueId().toString());
	}
	
	
	@EventHandler
	public void onPlayerInteract(PlayerInteractEntityEvent e) {
		if(e.getRightClicked() instanceof Player) {
			if(must_shift) {
				if(e.getPlayer().isSneaking()) {
					display(e.getPlayer(), (Player)e.getRightClicked());
				}
			}else {
				display(e.getPlayer(), (Player)e.getRightClicked());
			}
		}
	}
	
	
	// [Helper]------------------------------------------------------------------------------------------------------------------------------
		
	
	private String[] getText(Player p) {
		try {
			Statement stmt = sql.createStatement();
			ResultSet r = stmt.executeQuery("SELECT DESCRIPTION FROM player WHERE UUID='" + p.getUniqueId().toString() + "';");
			if(r.next()) {
				String[] desc = r.getString("DESCRIPTION").replace("%AP%","'").split("(?<=\\G.{250})");
				for(String page : desc) {
					page = ChatColor.BLACK + page;
					p.sendMessage(page);
				}
				return desc;
			}
		}catch(Exception ex) {
			System.out.println(config.getString("msg.sql-error"));
			if(debug) {ex.printStackTrace();}
		}
		return new String[] {config.getString("book.inside")};
	}
	
	
	private List<String> getLore() {
		List<String> lore = config.getStringList("book.lore");
		for(String i : lore) {
			i = ChatColor.translateAlternateColorCodes('&', i);
		}
		return lore;
	}
	
	
	private String join(String[] args) {
		String result = "";
		for(String i : args) {
			result += (" " + i.replace("`","%AP%").replace("'","%AP%"));
		}
		return result.trim();
	}
	
	
	private void manager(Player p) {
		try {
			Statement stmt = sql.createStatement();
			ResultSet r1 = stmt.executeQuery("SELECT * FROM player WHERE UUID='" + p.getUniqueId().toString() + "';");
			ResultSet r2 = stmt.executeQuery("SELECT * FROM backup WHERE UUID='" + p.getUniqueId().toString() + "';");
	    if(r1.next()) {
			String nick1 = r1.getString("NAME");
			String gender1 = r1.getString("GENDER");
			String age1 = r1.getString("AGE");
			String race1 = r1.getString("RACE");
			String desc1 = r1.getString("DESCRIPTION");
			
		    if(r2.next()) {
				String nick2 = r2.getString("NAME");
				String gender2 = r2.getString("GENDER");
				String age2 = r2.getString("AGE");
				String race2 = r2.getString("RACE");
				String desc2 = r2.getString("DESCRIPTION");
				
				stmt.executeUpdate("UPDATE player SET NAME='" + nick2 + "', GENDER='" + gender2 + "', AGE='" + age2 + "', RACE='" + race2 + "', DESCRIPTION='" + desc2 + "' WHERE UUID='" + p.getUniqueId().toString() + "';");
				stmt.executeUpdate("UPDATE backup SET NAME='" + nick1 + "', GENDER='" + gender1 + "', AGE='" + age1 + "', RACE='" + race1 + "', DESCRIPTION='" + desc1 + "' WHERE UUID='" + p.getUniqueId().toString() + "';");
					p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("msg.card-toggle")));
		    }
	    }
	    stmt.close();
		}catch(Exception ex) {
			System.out.println(config.getString("msg.sql-error"));
			if(debug) {ex.printStackTrace();}
		}
	}
	
	
	private void description(boolean b, Player p, Player sender) {
		if(b) {
			if(p.getItemInHand().getType().equals(Material.WRITTEN_BOOK)) {
				ItemStack item = p.getItemInHand();
				BookMeta bm = (BookMeta)item.getItemMeta();
				StringBuilder sb = new StringBuilder();
				for (String s : bm.getPages()){
					sb.append(s);
				}
				p.setItemInHand(null);
				change(p, 4, sb.toString(), useconfirm);
			}else {
				ItemStack clone = book.clone();
				BookMeta cm = (BookMeta) clone.getItemMeta();
				cm.setPages(getText(p));
				clone.setItemMeta(cm);
				p.getInventory().addItem(clone);
			}
		}else {
			if(sender.getItemInHand().getType().equals(Material.WRITTEN_BOOK)) {
				ItemStack item = sender.getItemInHand();
				BookMeta bm = (BookMeta)item.getItemMeta();
				StringBuilder sb = new StringBuilder();
				for (String s : bm.getPages()){
				    sb.append(s);
				}
				sender.setItemInHand(null);
				change(p, 4, sb.toString(), false);
			}else {
				ItemStack clone = book.clone();
				BookMeta cm = (BookMeta) clone.getItemMeta();
				cm.setPages(getText(p));
				clone.setItemMeta(cm);
				sender.getInventory().addItem(clone);
				sender.sendMessage(prefix + "Use (/rpc set <playername> description) ... while holding the signed book.");
			}
		}
	}
	
	
	public void insertUser(String id) {
		try {
			Statement stmt = sql.createStatement();
			stmt.executeUpdate("INSERT OR IGNORE INTO player VALUES ('" + id + "','&c-','&c-','&c-','&c-','" + config.getString("book.inside") + "');");
			stmt.executeUpdate("INSERT OR IGNORE INTO backup VALUES ('" + id + "','&c-','&c-','&c-','&c-','" + config.getString("book.inside") + "');");
			stmt.close();
		}catch(Exception ex) {
			System.out.println(config.getString("msg.sql-error"));
			if(debug) {ex.printStackTrace();}
		}
	}
	

	private void change(Player p, int i, String insert, boolean confirm) {
		try {
			Statement stmt = sql.createStatement();
			switch(i){
			case 0:
				stmt.executeUpdate("UPDATE player SET NAME='" + insert + "' WHERE UUID='" + p.getUniqueId().toString() + "';");
				if(confirm) {
					p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("settings.confirm.name").replace("%NEW%", insert)));
				}
				return;
			case 1:
				if(enforce_gender) {
					try {
						if(checkgender(insert)) {
							stmt.executeUpdate("UPDATE player SET GENDER='" + insert + "' WHERE UUID='" + p.getUniqueId().toString() + "';");
							if(confirm) {
								p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("settings.confirm.gender").replace("%NEW%", insert)));
							}
						}else {
							p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("enforce.error-gender")));
						}
						return;
					}catch(Exception ex2) {
						System.out.println(config.getString("msg.sql-error"));
						if(debug) {ex2.printStackTrace();}
					}
				}else {
					stmt.executeUpdate("UPDATE player SET GENDER='" + insert + "' WHERE UUID='" + p.getUniqueId().toString() + "';");
					if(confirm) {
						p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("settings.confirm.gender").replace("%NEW%", insert)));
					}
				}
				return;
			case 2:
				if(enforce_age) {
					try {
						int j = Integer.parseInt(insert);
						stmt.executeUpdate("UPDATE player SET AGE='" + j + "' WHERE UUID='" + p.getUniqueId().toString() + "';");
						if(confirm) {
							p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("settings.confirm.age").replace("%NEW%", insert)));
						}
						return;
					}catch(Exception ex2) {
						System.out.println(config.getString("msg.sql-error"));
						if(debug) {ex2.printStackTrace();}
					}
					p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("enforce.error-age")));
				}else {
					stmt.executeUpdate("UPDATE player SET AGE='" + insert + "' WHERE UUID='" + p.getUniqueId().toString() + "';");
					if(confirm) {
						p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("settings.confirm.age").replace("%NEW%", insert)));
					}
				}
				return;
			case 3:
				stmt.executeUpdate("UPDATE player SET RACE='" + insert + "' WHERE UUID='" + p.getUniqueId().toString() + "';");
				if(confirm) {
					p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("settings.confirm.race").replace("%NEW%", insert)));
				}
				return;
			case 4:	
				insert = insert.replace("`","%AP%").replace("'","%AP%");
				stmt.executeUpdate("UPDATE player SET DESCRIPTION='" + insert + "' WHERE UUID='" + p.getUniqueId().toString() + "';");
				if(confirm) {
					p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("settings.confirm.description").replace("%NEW%", insert)));
				}
				return;
			}
			stmt.close();
		}catch(Exception ex) {
			System.out.println(config.getString("msg.sql-error"));
			if(debug) {ex.printStackTrace();}
		}
	}
	
	
	private void display(Player to, Player from) {
		if(to.hasPermission("rpc.get")) {
			try {
				Statement stmt = sql.createStatement();
				ResultSet r = stmt.executeQuery("SELECT * FROM player WHERE UUID='" + from.getUniqueId().toString() + "';");
			    if(r.next()) {
					String nick = r.getString("NAME");
					String gender = r.getString("GENDER");
					String age = r.getString("AGE");
					String race = r.getString("RACE");
					String description = r.getString("DESCRIPTION");
					
					r.close();
					
					List<String> list;
					if(to.hasPermission("rpc.extended_display")) {
						list = config.getStringList("display-extended");
					}else {
						list = config.getStringList("display");
					}
					display(from, to, nick, gender, age, race, description, list);
			    }
			    stmt.close();
			}catch(Exception ex) {
				System.out.println(config.getString("msg.sql-error"));
				if(debug) {ex.printStackTrace();}
			}
		}else {
			to.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("msg.no-perm")));
		}
	}
	
	
	private void display(Player from, Player to, String nick, String gender, String age, String race, String description, List<String> list) {
		for(String msg : list) {
			msg = msg.replace("%PLAYER%", from.getName());
			msg = msg.replace("%OP%", "" + from.isOp());
			msg = msg.replace("%UUID%", from.getUniqueId().toString());
			msg = msg.replace("%IP%", from.getAddress().getHostName());
			msg = msg.replace("%HEALTH%", "" + from.getHealth());
			msg = msg.replace("%HUNGER%", "" + from.getFoodLevel());
			msg = msg.replace("%LEVEL%", "" + from.getLevel());
			msg = msg.replace("%GAMEMODE%", "" + from.getGameMode().toString());
			
			if(!from.hasPermission("rpc.color.name")) {
				nick = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', nick));
			}
			if(!from.hasPermission("rpc.color.gender")) {
				gender = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', gender));
			}
			if(!from.hasPermission("rpc.color.age")) {
				age = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', age));
			}
			if(!from.hasPermission("rpc.color.race")) {
				race = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', race));
			}
			if(!from.hasPermission("rpc.color.description")) {
				description = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', description));
			}

			msg = msg.replace("%NICK%", nick);
			msg = msg.replace("%GENDER%", gender);
			msg = msg.replace("%AGE%", age);
			msg = msg.replace("%RACE%", race);
			msg = msg.replace("%DESCRIPTION%", description);

			msg = msg.replace("%AP%","'");
			msg = ChatColor.translateAlternateColorCodes('&',msg);
			to.sendMessage(msg);
		}
	}
	
	
	private boolean setupDB() {
		try {
			Statement stmt = sql.createStatement();
			String query = "CREATE TABLE IF NOT EXISTS player" +
	                   "(UUID         CHAR(40)  PRIMARY KEY  NOT NULL, " +
	                   " NAME         CHAR(40), " + 
	                   " GENDER       CHAR(40), " + 
	                   " AGE          CHAR(40), " + 
	                   " RACE         CHAR(40), " + 
	                   " DESCRIPTION  CHAR(999) " +
	                   ");";
			stmt.executeUpdate(query);
			stmt.close();
			
			stmt = sql.createStatement();
			query = "CREATE TABLE IF NOT EXISTS backup" +
	                   "(UUID         CHAR(40)  PRIMARY KEY  NOT NULL, " +
	                   " NAME         CHAR(40), " + 
	                   " GENDER       CHAR(40), " + 
	                   " AGE          CHAR(40), " + 
	                   " RACE         CHAR(40), " + 
	                   " DESCRIPTION  CHAR(999) " +
	                   ");";
			stmt.executeUpdate(query);
			stmt.close();
			return true;
		}catch(Exception ex) {
			System.out.println(config.getString("msg.sql-error"));
			if(debug) {ex.printStackTrace();}
		}
		return false;
	}
	
	
	private void setupVars() {
		debug = config.getBoolean("settings.debug");
		prefix = ChatColor.translateAlternateColorCodes('&', config.getString("msg.prefix"));
				
		useconfirm = config.getBoolean("settings.use-confirm");
		useprefix = config.getBoolean("settings.use-prefix");
		must_shift = config.getBoolean("settings.must-shift-rightclick");
		enforce_age = config.getBoolean("settings.use-confirm");
		enforce_gender = config.getBoolean("settings.use-confirm");
		if(enforce_gender) {
			genders = config.getStringList("enforce.gender-options");
		}
		
		book = new ItemStack(Material.BOOK_AND_QUILL,1);
		BookMeta m = (BookMeta)book.getItemMeta();
		m.setDisplayName(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("book.itemname")));
		m.setPages(new String[] {config.getString("book.inside")});
		m.setLore(getLore());
		book.setItemMeta(m);
	}
	
	
	private boolean checkgender(String insert) {
		String new_insert = ChatColor.stripColor(insert).toLowerCase();
		for(String value : genders) {
			if(value.equalsIgnoreCase(new_insert)) {
				return true;
			}
		}
		return false;
	}
	
	
	// UPDATER ------------------------------------------------------------------------------------------------------------------
	
	
	protected void say(Player p, boolean b) {
		if(b) {
			System.out.println(ChatColor.stripColor(prefix + "-----------------------------------------------------"));
			System.out.println(ChatColor.stripColor(prefix + " RolePlayCards is outdated. Get the new version here:"));
			System.out.println(ChatColor.stripColor(prefix + " http://www.pokemon-online.xyz/plugin"));
			System.out.println(ChatColor.stripColor(prefix + "-----------------------------------------------------"));
		}else {
		   	p.sendMessage(prefix + "------------------------------------------------------");
		   	p.sendMessage(prefix + " RolePlayCards  is outdated. Get the new version here:");
		   	p.sendMessage(prefix + " http://www.pokemon-online.xyz/plugin");
		   	p.sendMessage(prefix + "------------------------------------------------------");
		}
	}
	
}
