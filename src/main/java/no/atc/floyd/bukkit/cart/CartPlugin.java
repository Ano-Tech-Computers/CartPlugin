package no.atc.floyd.bukkit.cart;


import org.bukkit.entity.Creature;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleUpdateEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;


/**
* WirelessSign plugin for Bukkit
*
* @author FloydATC
*/
public class CartPlugin extends JavaPlugin implements Listener {
    //public static Permissions Permissions = null;
    
	public static final Logger logger = Logger.getLogger("Minecraft.WirelessSign");
	private static ConcurrentHashMap<UUID,Minecart> managed = new ConcurrentHashMap<UUID,Minecart>();
	private static ConcurrentHashMap<UUID,Vector> speed = new ConcurrentHashMap<UUID,Vector>();
    
//    public WirelessSign(PluginLoader pluginLoader, Server instance, PluginDescriptionFile desc, File folder, File plugin, ClassLoader cLoader) {
//        super(pluginLoader, instance, desc, folder, plugin, cLoader);
//        // TODO: Place any custom initialization code here
//
//        // NOTE: Event registration should be done in onEnable not here as all events are unregistered when a plugin is disabled
//    }

    public void onDisable() {
        // TODO: Place any custom disable code here

        // NOTE: All registered events are automatically unregistered when a plugin is disabled
    	
        // EXAMPLE: Custom code, here we just output some info so we can check all is well
    	PluginDescriptionFile pdfFile = this.getDescription();
		logger.info( pdfFile.getName() + " version " + pdfFile.getVersion() + " is disabled!" );
    }

    public void onEnable() {
        // TODO: Place any custom enable code here including the registration of any events

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents((Listener) this, this);

        // EXAMPLE: Custom code, here we just output some info so we can check all is well
        PluginDescriptionFile pdfFile = this.getDescription();
		logger.info( pdfFile.getName() + " version " + pdfFile.getVersion() + " is enabled!" );
	
    }


    @EventHandler
    public void onInteractBlock( PlayerInteractEvent event ) {
        if (event.hasBlock()) {
            Player player = event.getPlayer();
            String pname = player.getName();
            Location loc = player.getLocation();
	    	Block block = event.getClickedBlock();
	    	//World world = block.getWorld();
	    	
	    	// Is this a sign?
	    	if (block.getType() == Material.WALL_SIGN) {
	    		Sign sign = (Sign) block.getState();
	    		
	    		// Right-clicking? Otherwise ignore
	    		if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
	    			return;
	    		}
	    		
	    		// Is this a blank sign?
	    		if (blank(sign)) {
	    			// Is there a powered rail below?
	    			Block rail = findRailBelow(sign);
	    			if (rail != null) {
	    				sign.setLine(0, "[Minecart]");
	    				sign.setLine(1, "");
	    				sign.setLine(2, "Right-click");
	    				sign.setLine(3, "to get a cart");
	    				sign.update();
	    			}
	    			
	    		}

	    		// Is this a magic sign?
	    		if (magic(sign)) {
	    			// Is there a powered rail below?
	    			Block rail = findRailBelow(sign);
	    			if (rail != null) {
		            	getLogger().info(""+pname+" entering a minecart at "+loc.getBlockX()+","+loc.getBlockY()+","+loc.getBlockZ());
		    			Minecart cart = rail.getWorld().spawn(sign.getLocation(), Minecart.class);
		    			managed.put(cart.getUniqueId(), cart);
		    			getLogger().info("Now managing "+managed.size()+" minecart"+(managed.size()==1?"":"s"));
		    			cart.setPassenger(player);
		    			return;
	    			}
	    		}
	    	}
        }
        return;
    }
	    
    
    // Player clicking an entity
    @EventHandler
    public void onInteractEntity( PlayerInteractEntityEvent event ) {
    	if (event.getRightClicked() instanceof Minecart) {
    		Minecart cart = (Minecart) event.getRightClicked();
    		if (managed.get(cart.getUniqueId()) != null) {
    			getLogger().info(""+event.getPlayer().getName()+" right-clicked on a managed minecart. Removed.");
    			managed.remove(cart.getUniqueId());
    			speed.remove(cart.getUniqueId());
    			cart.remove();
    		}
    	}
    	return;
    }


    // Player exiting vehicle
    @EventHandler
    public void onVehicleExit( VehicleExitEvent event ) {
    	if (event.getVehicle() instanceof Minecart) {
    		Minecart cart = (Minecart) event.getVehicle();
    		if (managed.get(cart.getUniqueId()) != null) {
    			Player player = (Player) event.getExited();
    			getLogger().info(""+player.getName()+" exited a managed minecart. Removed.");
    			managed.remove(cart.getUniqueId());
    			speed.remove(cart.getUniqueId());
    			cart.remove();
    			safePlace(player);
    		}
    	}
    	return;
    }


    // Minecart doing its thing
    @EventHandler
    public void onMinecartUpdate( VehicleUpdateEvent event ) {
    	if (event.getVehicle() instanceof Minecart) {
    		Minecart cart = (Minecart) event.getVehicle();
			org.bukkit.util.Vector vec = cart.getVelocity();
			speed.put(cart.getUniqueId(), vec);
    		
    		// Check speed - accelerate to max speed
    		if (cart.isEmpty() == false && managed.get(cart.getUniqueId()) != null) {
    			Double v = vec.length();
   				if (v > 1 && v < 5) {
   					vec.multiply(1.1);
   					cart.setVelocity(vec);
   				} else if (v == 0.0) {
   					getLogger().info("Managed minecart v="+v);
   					reap(cart);
   				}
    		}
    		
    		// Clear the way
    		String pname = "";
    		Player p = null;
    		if (cart.isEmpty() == false && cart.getPassenger() instanceof Player) {
    			p = (Player) cart.getPassenger();
    			pname = p.getName();
    		}
    		// Activate the following for NON-OPS only (ops are testing)
/*       		if (p != null && p.isOp() == false) {
	    		org.bukkit.util.Vector scan = new org.bukkit.util.Vector(0.5f, 0.5f, 0.5f);
	    		scan.add(vec);
	    		List<Entity> entities = event.getVehicle().getNearbyEntities(scan.getX(), scan.getY(), scan.getZ());
	    		for (Entity entity : entities) {
	    			if (entity instanceof Creature) {
	        			//Creature critter = (Creature) entity;
						entity.teleport(entity.getLocation().add(vec));
	    				entity.remove();
	       				//getLogger().info("Avoided collision with a critter");
	    			} else if (entity instanceof Minecart) {
	    				if (entity.isEmpty()) {
	    					managed.remove(entity.getUniqueId());
	    					entity.remove();
	           				getLogger().info("Avoided collision with an empty minecart");
	    				}
	    			} else if (entity instanceof Player) {
	    				Player player = (Player) entity;
	    				if (player.equals(event.getVehicle().getPassenger())) {
	    					// Ignore own passenger (duh)
	    				} else {
	    					if (safePlace(player) == false) {
	    						player.teleport(player.getLocation().add(vec));
	    						getLogger().info("Attempting to push "+player.getName()+" out of the way");
	    					}
	    				}
	    			} else {
	       				getLogger().info("Removing "+entity.toString());
	       				entity.remove();
	    			}
	    		}
    		}
*/    	}
    	return;
    }


    // Vehicle colliding with block
    @EventHandler
    public void onVehicleBlockCollision( VehicleBlockCollisionEvent event ) {
    	if (event.getVehicle() instanceof Minecart) {
    		Minecart cart = (Minecart) event.getVehicle();
    		if (managed.get(cart.getUniqueId()) == null) {
    			// Unmanaged cart hit something. We do not care.
    			return;
    		}
    		Block b = event.getBlock();
    		getLogger().info("Managed minecart hit "+b);
    		if (b.getType().isTransparent()) {
        		Vector v = cart.getVelocity();
    	    	v = speed.get(cart.getUniqueId());
    	    	if (v != null) {
    	    		cart.setVelocity(v);
    	    		getLogger().info("Jumpstart successful");
    	    	} else {
    	    		getLogger().info("Jumpstart failed");
    	    	}
    	    	

//    	    	if (v.length() < 0.1) {
//    		    	org.bukkit.util.Vector vec = cart.getVelocity();
//    		    	vec.normalize();
//    		    	vec.multiply(cart.getMaxSpeed());
//    				cart.setVelocity(vec);
//    				getLogger().info("Attempting to jumpstart");
//        		}
    		}
    		String pname = "(Empty)";
    		if (cart.getPassenger() instanceof Player) {
    			Player player = (Player) event.getVehicle().getPassenger();
    			if (player != null) {
    				pname = player.getName();
    			}
    		} else if (cart.getPassenger() != null) {
    			pname = event.getVehicle().getPassenger().toString();
    		} else {
    			// Minecart is empty
    			getLogger().info("Minecart is empty, removing it");
    			managed.remove(cart.getUniqueId());
    			cart.remove();
    			return;
    		}
    		
/*    		if (v.length() < 0.1) {
    	    	getLogger().info("Minecart(" + pname + ") collided and stopped: Removed");
    	    	cart.eject();
    			managed.remove(cart.getUniqueId());
        		cart.remove();
    		}
*/    	}
    	return;
    }


    // Vehicle colliding with entity
    @EventHandler(priority = EventPriority.LOWEST) // Process as early as possible
    public void onVehicleEntityCollision( VehicleEntityCollisionEvent event ) {
    	
    	if (event.getEntity() instanceof Minecart) {
			Minecart cart = (Minecart) event.getEntity();
			if (managed.get(cart.getUniqueId()) != null) {
				//getLogger().info(""+event.getEntity()+" hit a managed minecart (cancelled)");
		    	event.setCancelled(true);
				event.setCollisionCancelled(true);
				return;
			}
    	} else {
    		// Not a minecart at all. Do not process further.
    		return;
    	}
    	
    	if (event.getVehicle() instanceof Minecart) {
			Minecart cart = (Minecart) event.getVehicle();
			if (managed.get(cart.getUniqueId()) == null) {
				// Not a managed minecart. Do not process further.
				return;
			}
			
    		if (event.getEntity() instanceof Player) {
    			// Collided with a player (pass through)
    			Player player = (Player) event.getEntity();
   				getLogger().info("Collided with "+player.getName()+" (cancelled)");
    		} else if (event.getEntity() instanceof Creature) {
    			// Collided with a creature. Kill it.
    			Creature critter = (Creature) event.getEntity();
    			critter.damage(critter.getMaxHealth(), event.getVehicle());
   				getLogger().info("Collided with a critter (killed, collision cancelled)");
		    	event.setCancelled(true);
				event.setCollisionCancelled(true);
    		} else if (event.getEntity() instanceof Minecart) {
    			// Collided with another minecart (destroy if empty, ignore)
    			Minecart othercart = (Minecart) event.getEntity();
    			
    			if (managed.get(othercart.getUniqueId()) == null) {
    				othercart.remove();
    				getLogger().info("Collided with an unmanaged minecart (removed, collision cancelled)");
    		    	event.setCancelled(true);
    				event.setCollisionCancelled(true);
    				return;
    			} else {
    				getLogger().info("Collided with a managed minecart (collision cancelled)");
    		    	event.setCancelled(true);
    				event.setCollisionCancelled(true);
    				return;
    			}
    		} else {
    			getLogger().info("event=" + event.getEventName() + " vehicle=" + event.getVehicle() + " entity=" + event.getEntity() + " (cancelled)");
		    	event.setCancelled(true);
				event.setCollisionCancelled(true);
    		}
    	}
    	return;
    }

    
    private void reap(Minecart cart) {
    	getLogger().info("Removing managed minecart");
    	cart.eject();
		managed.remove(cart.getUniqueId());
		cart.remove();
    }

    
    // Return true if sign is blank
    private boolean blank(Sign sign) {
    	if (sign.getLine(0).equals("") == false) { return false; }
    	if (sign.getLine(1).equals("") == false) { return false; }
    	if (sign.getLine(2).equals("") == false) { return false; }
    	if (sign.getLine(3).equals("") == false) { return false; }
    	return true;
    }
    
    // Return true if sign is magic
    private boolean magic(Sign sign) {
    	if ((sign.getLine(0).equals("Minecart") || sign.getLine(0).equals("[Minecart]")) == false) { return false; }
    	if (sign.getLine(1).equals("") == false) { return false; }
    	if (sign.getLine(2).equals("Right-click") == false) { return false; }
    	if (sign.getLine(3).equals("to get a cart") == false) { return false; }
    	return true;
    }
    
    // Search up to 3 blocks down and return powered rail block (if any)
    private Block findRailBelow(Sign sign) {
    	Integer x = sign.getX();
    	Integer y = sign.getY();
    	Integer z = sign.getZ();
    	for (Integer i=1; i < 4; i++) {
    		Block found = sign.getWorld().getBlockAt(x, y-i, z);
    		if (found.getType() == Material.POWERED_RAIL) {
    			return found;
    		}
    	}
    	return null;
    }
    
    // Find a safe place to eject a player
    private boolean safePlace(Player player) {
    	if (player == null) {
    		return false;
    	}
    	Location loc = player.getLocation();
    	Location safe = null;
    	for (Integer offset_x = -1; offset_x <= 1; offset_x++) {
        	for (Integer offset_y = -1; offset_y <= 1; offset_y++) {
            	for (Integer offset_z = -1; offset_z <= 1; offset_z++) {
            		Location candidate = loc.clone();
            		candidate.add(offset_x, offset_y, offset_z); 
            		if (isSafe(candidate)) {
            			if (isPlatform(candidate)) {
                			getLogger().info("Moved "+player+" to a platform");
            				player.teleport(candidate.add(0,1,0)); // ON this block, not IN it
            				return true;
            			}
            			safe = candidate.clone();
            		}
            	}
        	}
    	}
    	if (safe != null) {
			getLogger().info("Moved "+player+" to a safe place");
    		player.teleport(safe);
    		return true;
    	} else {
			getLogger().info("Could not place "+player+" anywhere safe");
			return false;
    	}
    }
    
    // Check if location is safe (must be solid with 2 blocks of air above)
    private boolean isSafe(Location location) {
    	Location loc = location.clone();
    	Block block = null;
    	World world = loc.getWorld();
    	block = world.getBlockAt(loc);
    	if (block.getType() == Material.AIR) { return false; }
    	if (block.getType() == Material.RAILS) { return false; }
    	if (block.getType() == Material.POWERED_RAIL) { return false; }
    	if (block.getType() == Material.WATER) { return false; }
    	if (block.getType() == Material.LAVA) { return false; }
    	if (block.getType() == Material.STATIONARY_WATER) { return false; }
    	if (block.getType() == Material.STATIONARY_LAVA) { return false; }
    	block = world.getBlockAt(loc.add(0,1,0));
    	if (block.getType() != Material.AIR) { return false; }
    	block = world.getBlockAt(loc.add(0,2,0));
    	if (block.getType() != Material.AIR) { return false; }
    	return true;
    }


    // Check if location is a platform (must be step/halfstep with 2 blocks of air above)
    private boolean isPlatform(Location location) {
    	Location loc = location.clone();
    	Block block = null;
    	World world = loc.getWorld();
    	block = world.getBlockAt(loc);
    	if (block.getType() != Material.STEP && block.getType() != Material.DOUBLE_STEP) { return false; }
    	block = world.getBlockAt(loc.add(0,1,0));
    	if (block.getType() != Material.AIR) { return false; }
    	block = world.getBlockAt(loc.add(0,2,0));
    	if (block.getType() != Material.AIR) { return false; }
    	getLogger().info("Found a platform");
    	return true;
    }
}
