package poke.server.storage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.hash.Hashing;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;


public class MongoStorage {
	private static MongoClient mongoClient, sharedClient;
	private static DB db, shareDb;
	private static DBCollection collImages = null;
	private static DBCollection collShared = null;
	
	public MongoStorage(){
		try {
			MongoClientURI uri = new MongoClientURI("mongodb://cmpe275:project1@ds051750.mongolab.com:51750/cmpe275");
			sharedClient = new MongoClient(uri);
			mongoClient = new MongoClient( "localhost" , 27017 );
			db = mongoClient.getDB( "test" );
			shareDb = sharedClient.getDB("cmpe275");
	    	collImages = db.getCollection("images");
	    	collShared = shareDb.getCollection("imageMeta");
	    	
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
	
	private static byte[] getFileBytes(String filename)
	{
		 File imageFile = new File(filename);
		 byte[] b = null;
		 try {
         FileInputStream f = new FileInputStream(imageFile);
         b = new byte[f.available()];
         f.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        return b;
	}
	

	public static void printAll(DBCollection coll){
		DBCursor cursor = coll.find();
		try {
		   while(cursor.hasNext()) {
		       System.out.println(cursor.next());
		   }
		} finally {
		   cursor.close();
		}
	}
		
	public static String addFile(String key, String name, byte[] imageFile, Long uploadDate, double size){
		
//		int bucket = Hashing.consistentHash(Hashing.md5().hashInt(j), ring.size());
		BasicDBObject image = new BasicDBObject();
		image.append("_id", key);
		image.append("name", name);
		image.append("image", imageFile);
		image.append("uploadDate", uploadDate);
		image.append("size", size);
		collImages.insert(image);
		return key;
	}
	public static BasicDBObject getFileByfId(String fId){
		BasicDBObject query = new BasicDBObject("_id", fId);
		return (BasicDBObject) collImages.findOne(query);
	}
	/*public static List<DBObject> getFileByuId(int uId){
		BasicDBObject query = new BasicDBObject("owner", uId);
		return collImages.find(query).toArray();
	}*/
	public static void deleteFile(String fId){
		BasicDBObject file = new BasicDBObject("_id", fId);
		collImages.remove(file);
	}
	public static void updateFile(int fId, String attr, String value){
		BasicDBObject query = new BasicDBObject("_id", fId);
		BasicDBObject change = new BasicDBObject(attr, value);
		BasicDBObject newDoc = new BasicDBObject().append("$set", change);
		collImages.update(query,newDoc);
	}
	
	public static void addReplicas(String uuid, int primaryNode, int secNode1, int secNode2){
		BasicDBObject replica = new BasicDBObject();
		replica.append("_id", uuid);
		replica.append("replica", new BasicDBObject().append("primary", primaryNode).append("secondary1", secNode1).append("secondary2", secNode2));
		collShared.insert(replica);
	}
	
	public static ReplicaDomain getReplicaById(String uuid){
		BasicDBObject query = new BasicDBObject("_id", uuid);
		BasicDBObject result = (BasicDBObject)collShared.findOne(query);
		
		ReplicaDomain replica = new ReplicaDomain();
		if(result != null) {
			BasicDBObject replicaResult = (BasicDBObject) result.get("replica");
			replica.setPrimaryNode(replicaResult.getInt("primary"));
			replica.setSecondaryNode1(replicaResult.getInt("secondary1"));
			replica.setSecondaryNode2(replicaResult.getInt("secondary2"));
			return replica;
		}
		return replica;
	}
	
	public static void deleteReplica(String uId){
		BasicDBObject file = new BasicDBObject("_id", uId);
		collShared.remove(file);
	}

//	public static void addReplicas1(String uuid, int primaryNode, int secNode2) {
//		// TODO Auto-generated method stub
//		BasicDBObject replica = new BasicDBObject();
//		replica.append("_id", uuid);
//		replica.append("replica", new BasicDBObject().append("primary", primaryNode).append("secondary2", secNode2));
//		collShared.insert(replica);
//	}
}
