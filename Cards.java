import java.sql.ResultSet;
import java.util.List;

import lib.PatPeter.SQLibrary.Database;
import lib.PatPeter.SQLibrary.SQLite;

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
	private Database sql;
	
	private boolean displayname;
	private boolean useconfirm;
	private boolean useprefix;

	private ItemStack book;
	private String prefix;
	
	
	public void onEnable() {
				
		sql = new SQLite(getServer().getLogger(), "[Cards] ", this.getDataFolder().getAbsolutePath(), "Cards", ".sqlite");
		if (sql.open()) {
			try {
			sql.query("CREATE TABLE IF NOT EXISTS player" +
	                   "(UUID         CHAR(40)  PRIMARY KEY  NOT NULL, " +
	                   " NAME         CHAR(40), " + 
	                   " GENDER       CHAR(40), " + 
	                   " AGE          CHAR(40), " + 
	                   " RACE         CHAR(40), " + 
	                   " DESCRIPTION  CHAR(999) " +
	                   ");"
	                  ); 
			sql.query("CREATE TABLE IF NOT EXISTS backup" +
	                   "(UUID         CHAR(40)  PRIMARY KEY  NOT NULL, " +
	                   " NAME         CHAR(40), " + 
	                   " GENDER       CHAR(40), " + 
	                   " AGE          CHAR(40), " + 
	                   " RACE         CHAR(40), " + 
	                   " DESCRIPTION  CHAR(999) " +
	                   ");"
	                  ); 
			}catch(Exception e) {}
			
			config = getConfig();
			config.options().copyDefaults(true);
			saveConfig();
			
			prefix = ChatColor.translateAlternateColorCodes('&', config.getString("msg.prefix"));
			
			boolean b =config.getBoolean("settings.updater");
			if(b) {
			new Updater(this);
			}
			
			useconfirm = config.getBoolean("settings.use-confirm");
			useprefix = config.getBoolean("settings.use-prefix");
			displayname = config.getBoolean("settings.overwrite-displayname");
			
			
			book = new ItemStack(Material.BOOK_AND_QUILL,1);
			BookMeta m = (BookMeta)book.getItemMeta();
			m.setDisplayName(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("book.itemname")));
			m.setPages(new String[] {config.getString("book.inside")});
			m.setLore(getLore());
			book.setItemMeta(m);
			
			getServer().getPluginManager().registerEvents(this, this);
			
			for(Player i : getServer().getOnlinePlayers()) {
				insertUser(i.getUniqueId().toString());
			}
		}else {
			this.setEnabled(false);
		}
	}

	
	public void onDisable() {
		sql.close();
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
								useconfirm = config.getBoolean("settings.use-confirm");
								useprefix = config.getBoolean("settings.use-prefix");
								displayname = config.getBoolean("settings.overwrite-displayname");
								prefix = ChatColor.translateAlternateColorCodes('&', config.getString("msg.prefix"));
								
								BookMeta m = (BookMeta)book.getItemMeta();
								m.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("book.itemname")));
								m.setLore(getLore());
								book.setItemMeta(m);
								
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
				}
			}		
		}catch(Exception ex) {}
		return false;
	}
	

	// [Events]------------------------------------------------------------------------------------------------------------------------------
		

	@EventHandler(priority = EventPriority.HIGHEST)
	public void playerChat(AsyncPlayerChatEvent e){
		if(useprefix) {
			if(e.getPlayer().hasPermission("rpc.prefix")) {
				try {
					ResultSet r = sql.query("SELECT NAME FROM player WHERE UUID='" + e.getPlayer().getUniqueId().toString() + "';");
				    if(r.next()) {
						String nick = r.getString("NAME");
						String format = ChatColor.translateAlternateColorCodes('&', config.getString("settings.prefix"));
						e.setFormat(format.replace("%PLAYER%",nick).replace("%MSG%",e.getMessage()));
				    }
				}catch(Exception ex) {}
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
			display(e.getPlayer(), (Player)e.getRightClicked());
		}
	}
	
	
	// [Helper]------------------------------------------------------------------------------------------------------------------------------
		
	
	private String[] getText(Player p) {
		try {
		ResultSet r = sql.query("SELECT DESCRIPTION FROM player WHERE UUID='" + p.getUniqueId().toString() + "';");
		if(r.next()) {
			String[] desc = r.getString("DESCRIPTION").replace("%AP%","'").split("(?<=\\G.{250})");
			for(String page : desc) {
				page = ChatColor.BLACK + page;
				p.sendMessage(page);
			}
			return desc;
		}
		}catch(Exception ex) {}
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
		ResultSet r1 = sql.query("SELECT * FROM player WHERE UUID='" + p.getUniqueId().toString() + "';");
		ResultSet r2 = sql.query("SELECT * FROM backup WHERE UUID='" + p.getUniqueId().toString() + "';");
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
				
				sql.query("UPDATE player SET NAME='" + nick2 + "', GENDER='" + gender2 + "', AGE='" + age2 + "', RACE='" + race2 + "', DESCRIPTION='" + desc2 + "' WHERE UUID='" + p.getUniqueId().toString() + "';");
				sql.query("UPDATE backup SET NAME='" + nick1 + "', GENDER='" + gender1 + "', AGE='" + age1 + "', RACE='" + race1 + "', DESCRIPTION='" + desc1 + "' WHERE UUID='" + p.getUniqueId().toString() + "';");
					p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("msg.card-toggle")));
		    }
	    }

		}catch(Exception ex) {}
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
			sql.insert("INSERT OR IGNORE INTO player VALUES ('" + id + "','&c-','&c-','&c-','&c-','" + config.getString("book.inside") + "');");
			sql.insert("INSERT OR IGNORE INTO backup VALUES ('" + id + "','&c-','&c-','&c-','&c-','" + config.getString("book.inside") + "');");
		}catch(Exception ex) {}
	}
	

	private void change(Player p, int i, String insert, boolean confirm) {
		try {
			switch(i){
			case 0:
				sql.query("UPDATE player SET NAME='" + insert + "' WHERE UUID='" + p.getUniqueId().toString() + "';");
				if(displayname) {
					p.setDisplayName(insert);
				}
				if(confirm) {
					p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("settings.confirm.name").replace("%NEW%", insert)));
				}
				return;
			case 1:
				sql.query("UPDATE player SET GENDER='" + insert + "' WHERE UUID='" + p.getUniqueId().toString() + "';");
				if(confirm) {
					p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("settings.confirm.gender").replace("%NEW%", insert)));
				}
				return;
			case 2:
				sql.query("UPDATE player SET AGE='" + insert + "' WHERE UUID='" + p.getUniqueId().toString() + "';");
				if(confirm) {
					p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("settings.confirm.age").replace("%NEW%", insert)));
				}
				return;
			case 3:
				sql.query("UPDATE player SET RACE='" + insert + "' WHERE UUID='" + p.getUniqueId().toString() + "';");
				if(confirm) {
					p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("settings.confirm.race").replace("%NEW%", insert)));
				}
				return;
			case 4:	
				insert = insert.replace("`","%AP%").replace("'","%AP%");
				sql.query("UPDATE player SET DESCRIPTION='" + insert + "' WHERE UUID='" + p.getUniqueId().toString() + "';");
				if(confirm) {
					p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("settings.confirm.description").replace("%NEW%", insert)));
				}
				return;
			}
		} catch (Exception ex) {}
	}
	
	
	private void display(Player to, Player from) {
		if(to.hasPermission("rpc.get")) {
			try {
				ResultSet r = sql.query("SELECT * FROM player WHERE UUID='" + from.getUniqueId().toString() + "';");
			    if(r.next()) {
					String nick = r.getString("NAME");
					String gender = r.getString("GENDER");
					String age = r.getString("AGE");
					String race = r.getString("RACE");
					String description = r.getString("DESCRIPTION");
					
					r.close();
					display(from, to, nick, gender, age, race, description);
			    }
			}catch(Exception ex) {}
		}else {
			to.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', config.getString("msg.no-perm")));
		}
	}
	
	
	private void display(Player from, Player to, String nick, String gender, String age, String race, String description) {
		for(String msg : config.getStringList("display")) {
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
