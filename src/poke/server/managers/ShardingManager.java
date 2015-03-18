package poke.server.managers;

import io.netty.channel.Channel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import poke.server.conf.NodeDesc;
import poke.server.conf.ServerConf;
import poke.server.hashing.HashedHBData;
import poke.server.managers.HeartbeatManager;

/* check heartbeat to know which all servers are up and running
 * call heartbeat manager to check the incomingHB- get the heartbeat data and check status is active
 * 
 */

public class ShardingManager {
	protected static Logger logger = LoggerFactory.getLogger("Sharding");
	protected ConcurrentHashMap<Integer, HashedHBData> activeHB = new ConcurrentHashMap<Integer, HashedHBData>();
	protected static AtomicReference<ShardingManager> instance = new AtomicReference<ShardingManager>();
	private static int num_replica;
	protected static String destHost;
	protected static int destPort;
	
	/* Declaration to implement consistent hashing
	 * connectedNodes have the active node set
	 */
	private final static SortedMap<Integer, Integer> connectedNodes = Collections.synchronizedSortedMap(new TreeMap<Integer, Integer>());
//	private final static SortedMap<String, Integer> connectedNodes = Collections.synchronizedSortedMap(new TreeMap<String, Integer>());
	private static HashFunction hf = Hashing.md5();
	private static ArrayList<Integer> servers = new ArrayList<Integer>();
	
	private static ServerConf conf;
	
	public static ShardingManager initManager(ServerConf conf) {
		num_replica = 24;
		ShardingManager.conf = conf;
		addServerIds(conf.getNodeId());
		instance.compareAndSet(null, new ShardingManager());
		return instance.get();
	}
	
	private static void addServerIds(int nodeId) {
		servers.add(nodeId);
	}

	public static ShardingManager getInstance() {
		return instance.get();
	}
	
	public boolean checkExistence(int nodeId)
	{
		if(servers.contains(nodeId))
		{
			return true;
		}
		return false;
	}
	
	public SortedMap<Integer, Integer> getShardedServers()
	{
		return connectedNodes;
	}
	
	public ArrayList<Integer> getServerIds()
	{
		return servers;
	}
	
	public static void hashAndAdd(int nodeId) {
		// TODO Auto-generated method stub
		String address = convertNodeToAddress(nodeId);
		
		logger.info("@MINU ---> Adding the node : "+nodeId);
		addServerIds(nodeId);
		logger.info("@MINU --> server size is (ADD) : "+servers.size());
		for(int k =0 ; k<servers.size() ; k++)
			logger.info("ShardingManager ----- > Server : "+k+" : value --> "+servers.get(k));
		for(int i=0; i< num_replica; i+=64) {
			HashCode hc = hf.hashLong(nodeId+i);
			connectedNodes.put(hc.asInt(), nodeId);
		}
	}
	
	private static String convertNodeToAddress(int nodeId) {
		String address = null;
		for(NodeDesc nn : conf.getAdjacent().getAdjacentNodes().values())
		{
			if(nn.getNodeId() == nodeId) {
				destHost = nn.getHost();
				destPort = nn.getPort();
				address = destHost+":"+destPort;
			}
		}
		return address;
	}

	public void removeFailedNode(int nodeId) {
		
		logger.info("Removing the node ----------> "+nodeId);
		if(servers.contains(nodeId))
			servers.remove(nodeId);
		logger.info("@MINU --> server size is (remove) : "+servers.size());
		for(int j =0 ; j<servers.size() ; j++)
			logger.info("ShardingManager ----- > Server : "+j+" : value --> "+servers.get(j));
		for(int i=0; i< num_replica; i+=64) {
			int hash = hf.hashLong(nodeId+i).asInt();
			connectedNodes.remove(hash);
		}
	}
	
	public int get(Object key) {
	    if (connectedNodes.isEmpty()) {
	      return 0;
	    }
	    int hash = hf.hashLong((Integer)key).asInt();
	    if (!connectedNodes.containsKey(hash)) {
	      SortedMap<Integer, Integer> tailMap =
	    		  connectedNodes.tailMap(hash);
	      hash = tailMap.isEmpty() ?
	    		  connectedNodes.firstKey() : tailMap.firstKey();
	    }
	    return connectedNodes.get(hash);
	  }
}
