/*
 * copyright 2012, gash
 * 
 * Gash licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package poke.resources;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.protobuf.ByteString;
import com.mongodb.BasicDBObject;

import poke.server.Server;
import poke.server.ServerInitializer;
import poke.server.conf.NodeDesc;
import poke.server.conf.ServerConf;
import poke.server.management.ManagementInitializer;
import poke.server.managers.ElectionManager;
import poke.server.managers.HeartbeatData;
import poke.server.managers.ShardingManager;
import poke.server.resources.Resource;
import poke.server.resources.ResourceFactory;
import poke.server.resources.ResourceUtil;
import poke.server.storage.MongoStorage;
import poke.server.storage.ReplicaDomain;
import eye.Comm.Header;
import eye.Comm.JobOperation;
import eye.Comm.JobOperation.JobAction;
import eye.Comm.JobStatus;
import eye.Comm.NameValueSet;
import eye.Comm.Payload;
import eye.Comm.PhotoHeader;
import eye.Comm.PhotoHeader.ResponseFlag;
import eye.Comm.PhotoPayload;
import eye.Comm.PokeStatus;
import eye.Comm.Request;
import eye.Comm.JobDesc;

public class JobResource implements Resource {

	protected static Logger logger = LoggerFactory.getLogger("server");

	private static Map<String, Request> requestMap = new HashMap<String, Request>();
	private static Map<String, Channel> chMap = new HashMap<String, Channel>();
	private static Map<String, Channel> primaryChannels = new HashMap<String, Channel>();

	private static String addimage = "addimage";
	private static String getimage = "listImages";
	private static String deleteimage = "deleteImages";
	
	boolean checkStoredInPrimary = false;

	//	private SortedMap<Long, Integer> shardedNodes = Collections.synchronizedSortedMap(new TreeMap<Long, Integer>());
	private SortedMap<Integer, Integer> shardedNodes = Collections.synchronizedSortedMap(new TreeMap<Integer, Integer>());
	private HashFunction hf = Hashing.md5();
	ConcurrentHashMap<String, Integer> nodeData = new ConcurrentHashMap<String, Integer>();

	private static ServerConf configFile;

	public JobResource() {
	}

	public void setCfg(ServerConf file) {
		configFile = file;
	}

	public static Map<String, Request> getRequestMap() {
		return requestMap;
	}


	public Channel connectToPublic(InetSocketAddress sa) {
		// Start the connection attempt.
		ChannelFuture channelFuture = null;
		EventLoopGroup group = new NioEventLoopGroup();

		try {
			ServerInitializer initializer = new ServerInitializer(false);
			Bootstrap b = new Bootstrap();

			//write the handler
			b.group(group).channel(NioSocketChannel.class).handler(initializer);
			b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
			b.option(ChannelOption.TCP_NODELAY, true);
			b.option(ChannelOption.SO_KEEPALIVE, true);

			channelFuture = b.connect(sa);
			channelFuture.awaitUninterruptibly(5000l);

		} catch (Exception ex) {
			logger.debug("failed to initialize the election connection");

		}

		if (channelFuture != null && channelFuture.isDone()
				&& channelFuture.isSuccess())
			return channelFuture.channel();
		else
			throw new RuntimeException(
					"Not able to establish connection to server");
	}

	/*
	 * (non-Javadoc)
	 * @see poke.server.resources.Resource#process(eye.Comm.Request)
	 */
	@Override
	public Request process(Request request) {
		// TODO Auto-generated method stub
		Request reply = null;
		String uuid = null;
		String message = "";
		boolean success = false;
		PhotoHeader imageHeader = request.getHeader().getPhotoHeader();
		PhotoPayload imagePayload = request.getBody().getPhotoPayload();

		int read = PhotoHeader.RequestType.read.getNumber();
		int write = PhotoHeader.RequestType.write.getNumber();
		int delete = PhotoHeader.RequestType.delete.getNumber();

		Integer leaderId = ElectionManager.getInstance().whoIsTheLeader();

		if (Server.getMyId() == leaderId) {

			// only if it is a response	
			if(request.getHeader().getPhotoHeader().hasResponseFlag())
			{
				logger.info("\n**********\nRECEIVED JOB STATUS"
						+ "\n\n**********");
				List<JobDesc> list = request.getBody().getJobStatus().getDataList();
				String jobId = request.getBody().getJobStatus().getJobId();

				int succesResponse = PhotoHeader.ResponseFlag.success.getNumber();
				int responseFlag = imageHeader.getResponseFlag().getNumber();
				if(succesResponse == responseFlag)
				{
					logger.info("@MInu -> inside the final response from server.....");
					Request.Builder rep = Request.newBuilder(request);
					reply = rep.build();
					Channel ch = chMap.get(jobId);
					chMap.remove(jobId);
					ch.writeAndFlush(reply);
				}
			}

			else if (request.getBody().hasJobOp()) {
				// apply sharding only if its leader
				logger.info("\n**********\n RECEIVED NEW JOB REQUEST-Leader"
						+ "\n\n**********");
				logger.info("\n**********\n RE-DIRECTING TO A NODE-SHARDING"
						+ "\n\n**********");
				String jobId = request.getBody().getJobOp().getJobId();
				JobOperation jobOp = request.getBody().getJobOp();
				requestMap.put(jobId, request);

				if (imageHeader.hasRequestType()) {

					int requestType = imageHeader.getRequestType().getNumber();


					// check if we need to put jobAction.equals(JobAction.ADDJOB) condition?? 
					if (requestType == write || addimage.equals(jobOp.getData()
							.getNameSpace())) {
						logger.info("ADDJOB received");

						UUID uniqueKey = UUID.randomUUID();
						String key = "T10"+uniqueKey.toString().substring(uniqueKey.toString().length()-4);

						// route the request to server
						shardedNodes = ShardingManager.getInstance().getShardedServers();

						// @Test
						List<Integer> serverIdTestNew = ShardingManager.getInstance().getServerIds();
						for(int i=0; i<serverIdTestNew.size(); i++)
						{
							logger.info(i+" : Sharded node is : "+serverIdTestNew.get(i));
						}
						logger.info("The size of the shardedNodes is ----------> "+ShardingManager.getInstance().getShardedServers().size());
						//@Test 

						int bucket = Hashing.consistentHash(Hashing.md5().hashString(key), ShardingManager.getInstance().getShardedServers().size());
						int server= get(bucket);

						logger.info("Server to which request to be routed - "+server);

						// forward to server(node id)- forwardResource
						Request fwd = ResourceUtil.buildForwardAddMessage(request, server, key);

						for(NodeDesc nn : configFile.getAdjacent().getAdjacentNodes().values())
						{
							if(nn.getNodeId() == server) {
								ChannelFuture fut = createChannelAndForward(nn.getHost(), nn.getPort(), fwd, key);
								fut.awaitUninterruptibly();
								boolean good = fut.isSuccess();
								logger.info("waiting inside the leader for the response-----");
								if(good)
									logger.info("Successfully forwarded the request to the sharded node primary! -- "+nn.getNodeId());
								break;
							}
						}
					}
					// read the image by leader and check the mongodb collection 
					/*
					 * add the delete condition together with the get
					 */
					else if (getimage.equals(jobOp.getData().getNameSpace()) || requestType == read ||
							deleteimage.equals(jobOp.getData().getNameSpace()) || requestType == delete) 
					{

						String key = request.getBody().getPhotoPayload().getUuid();
						// check mongo collection
						ReplicaDomain replicaStorage = MongoStorage.getReplicaById(key);
						// found uuid in my cluster then forward to slave to get response
						if(replicaStorage != null)
						{
							// check if it is a broadcasted message by checking the entryNode field
							if(imageHeader.hasEntryNode()) {
								// store the ip address in entry node with jobid
								String sourceIP = imageHeader.getEntryNode();
								// remove the established channel betweent he sourceIP and destinationIP(me) if it is a broadcasted
								Channel ch = chMap.get(jobId);
								chMap.remove(jobId);
								//establish a new channel with the sourceIP and put it in chmap to be taken whenever a response is ready
								InetSocketAddress socket = new InetSocketAddress(sourceIP, 5570);
								Channel returnChannel = connectToPublic(socket);
								chMap.put(jobId, returnChannel);
							}
							int primary = replicaStorage.getPrimaryNode();
							int secondary1 = replicaStorage.getSecondaryNode1();
							int secondary2 = replicaStorage.getSecondaryNode2();
							List<Integer> serverIdNew = ShardingManager.getInstance().getServerIds();
							int server = 100000;
							if(serverIdNew.contains(primary))
							{
								server = primary; 
							}
							else if(serverIdNew.contains(secondary1))
							{
								server = secondary1;
							}
							else if(serverIdNew.contains(secondary2))
							{
								server = secondary2;
							}
							else
							{
								logger.info("ALl the server with this uuid is down: ");
							}

							// @Test
							shardedNodes = ShardingManager.getInstance().getShardedServers();
							List<Integer> serverIdTestNew = ShardingManager.getInstance().getServerIds();
							for(int i=0; i<serverIdTestNew.size(); i++)
							{
								logger.info(i+" : Sharded node : is : "+serverIdTestNew.get(i));
							}
							// @Test

							logger.info("server to which request to be routed - "+server);
							// forward to server(node id)- forwardResource
							Request fwd = ResourceUtil.buildForwardMessage(request, configFile);

							for(NodeDesc nn : configFile.getAdjacent().getAdjacentNodes().values())
							{
								if(nn.getNodeId() == server) {
									if(server != primary && requestType == delete)
									{
										// forward the request to both the secondary nodes
										ChannelFuture futSec = createChannelAndForward(nn.getHost(), nn.getPort(), fwd, key);
										futSec.awaitUninterruptibly();
										boolean goodSec = futSec.isSuccess();
										if(goodSec)
											logger.info("Forwarded the delete request to the secondary NOde " +server);
										if( server != secondary2 && serverIdNew.contains(secondary2))
											server = secondary2;
										if( server != secondary1 && serverIdNew.contains(secondary1))
											server = secondary1;
									}

									ChannelFuture fut = createChannelAndForward(nn.getHost(), nn.getPort(), fwd, key);
									fut.awaitUninterruptibly();
									boolean good = fut.isSuccess();
									logger.info("waiting inside the leader after ");
									if(good)
										logger.info("successfully forwarded the request to server : "+nn.getNodeId());
									break;
								}
							}
						}
						// Requested uuid not in my cluster
						/* broad cast if no entry point else discard
						 * populate the entryPoint in the request with my id-ip
						 */
						else 
						{
							logger.info("Forward to a different cluster");
							if(!imageHeader.hasEntryNode()) {
								// broad cast to all after putting my id
								String leaderHost = null;
								for(NodeDesc nn : configFile.getAdjacent().getAdjacentNodes().values())
								{
									if(nn.getNodeId() == leaderId) {
										leaderHost = nn.getHost();
									}
								}
								String destHost= null;
								int destPort = 0;
								Request request1 = ResourceUtil.buildBroadcastRequest(request, leaderHost);
								
								List<String> leaderList = new ArrayList<String>();

								 leaderList.add(new String("192.168.0.7:5670"));
								// leaderList.add(new String("192.168.0.60:5673"));
								// leaderList.add(new String("192.168.0.230:5573"));

								for (String destination : leaderList) {
									String[] dest = destination.split(":");
									destHost = dest[0];
									destPort = Integer.parseInt(dest[1]);

								ChannelFuture fut = createChannelAndForward(destHost, destPort, request1, null);
								fut.awaitUninterruptibly();
								boolean good = fut.isSuccess();
								logger.info("waiting inside the leader (connected to client) for the response for broadcasted request-----");
								if(good)
									logger.info("successfully broadcasted the request to servers : ");
								}
							}
							// received request has entry point and uuid couldn't foound in this cluster ignore
							else
							{
								//Discard the request
								//								return null;
								Channel ch = chMap.get(jobId);
								chMap.remove(jobId);
							}
						}
					}
				}
			}
		}
		else {
			// By Slaves only
			logger.info("\n**********\n RECEIVED NEW JOB REQUEST BY A SLAVE NODE"
					+ "\n\n**********");
			JobOperation jobOp = request.getBody().getJobOp();
			
			//Response from Secondary nodes

			// only if it is a response	
			if(request.getHeader().getPhotoHeader().hasResponseFlag() && (request.getHeader().getReplica().equalsIgnoreCase("broadcastReply")))
			{
				
				logger.info("\n**********\nRECEIVED JOB STATUS"
						+ "\n\n**********");
				String jobId = request.getBody().getJobStatus().getJobId();
				int succesResponse = PhotoHeader.ResponseFlag.success.getNumber();
				int failureResponse = PhotoHeader.ResponseFlag.failure.getNumber();
				
				int responseFlag = imageHeader.getResponseFlag().getNumber();
//				if(succesResponse == responseFlag)
//				{
					logger.info("@MInu -> inside the  response from primary server.....");
					Request.Builder rep = Request.newBuilder(request);
					reply = rep.build();
					if(chMap.containsKey(jobId)) {
						Channel ch = chMap.get(jobId);
						//respond back if secondary is succes
						if(succesResponse == responseFlag)
						{
							chMap.remove(jobId);
							ch.writeAndFlush(reply);
						}
						else
						{
							chMap.remove(jobId);
							if(checkStoredInPrimary)
							{
								checkStoredInPrimary = false;
								logger.info("***************Stored to mongodb of Primary node**************");
								// build response
								Request primaryReply = null;
								message = "Successfully store to Primary MongoDB";
								Request.Builder rb = Request.newBuilder();
								Payload.Builder pb = Payload.newBuilder();

								JobStatus.Builder jb = JobStatus.newBuilder();
								jb.setStatus(PokeStatus.SUCCESS);
								jb.setJobId(jobOp.getJobId());
								jb.setJobState(JobDesc.JobCode.JOBRECEIVED);

								pb.setJobStatus(jb.build());

								PhotoPayload.Builder pp=PhotoPayload.newBuilder();
//								pp.setUuid(uuid);
								PhotoPayload.Builder newPhotoBldr = PhotoPayload.newBuilder(pp.build());
								pb.setPhotoPayload(newPhotoBldr);
								rb.setBody(pb.build());
								// check if we can re-use the same method in resourceutil
								rb.setHeader(ResourceUtil.buildHeaderResponse(request
										.getHeader().getRoutingId(),
										PokeStatus.SUCCESS, message, request
										.getHeader().getOriginator(),
										request.getHeader().getTag(), ResponseFlag.success));

								primaryReply = rb.build();
								ch.writeAndFlush(primaryReply);
							}
						}
					}
					else
					{
						logger.info("Ignoring the response from the Secondary 2!");
						return reply;
					}
			}

			else if (imageHeader.hasRequestType() && !(imageHeader.hasEntryNode())) 
			{

				logger.info("ADDJOB received");
				logger.info(jobOp.getData().getNameSpace());
				int requestType = imageHeader.getRequestType().getNumber();
				String jobId = request.getBody().getJobOp().getJobId();

				// check if we need to put jobAction.equals(JobAction.ADDJOB) condition?? 
				if (requestType == write || addimage.equals(jobOp.getData()
						.getNameSpace())) 
				{
					String key = imagePayload.getUuid();
					byte[] image = imagePayload.getData().toByteArray();
					long creationDate = System.currentTimeMillis() % 1000;;
					int contentLength = imageHeader.getContentLength();
					String title = imagePayload.getName();

					logger.info("@Minu--->unique key is-after setting: "+key);
					// TODO -check if there is uuid in the request otherwise forward to the leader
					// if the message is for me

					if(contentLength <= 56000 )
					{
//						if(request.getHeader().hasReplica()) {
						if (request.getHeader().getReplica().equalsIgnoreCase("broadcast")){
							
							logger.info("store to secondary");
							uuid = MongoStorage.addFile(key, title, image, creationDate, image.length);
							if(uuid == null)
							{
								logger.info("Request is not handled by secondary!");
								message = "Request is not handled by secondary!";
								Request.Builder rb = Request.newBuilder();
								Payload.Builder pb = Payload.newBuilder();

								JobStatus.Builder jb = JobStatus.newBuilder();
								jb.setStatus(PokeStatus.FAILURE);
								jb.setJobId(jobOp.getJobId());
								jb.setJobState(JobDesc.JobCode.JOBRECEIVED);

								pb.setJobStatus(jb.build());
								rb.setBody(pb.build());
								// check if we can re-use the same method in resourceutil
								rb.setHeader(ResourceUtil.buildHeaderResponse(request
										.getHeader().getRoutingId(),
										PokeStatus.FAILURE, message, request
										.getHeader().getOriginator(),
										request.getHeader().getTag(), ResponseFlag.failure));

								reply = rb.build();
								return reply;
							}						
							else 
							{
								logger.info("***************Stored to mongodb of secondary node**************");
								// build response
								message = "Successfully stored to Secondary MongoDB";
								Request.Builder rb = Request.newBuilder();
								Payload.Builder pb = Payload.newBuilder();

								JobStatus.Builder jb = JobStatus.newBuilder();
								jb.setStatus(PokeStatus.SUCCESS);
								jb.setJobId(jobOp.getJobId());
								jb.setJobState(JobDesc.JobCode.JOBRECEIVED);

								pb.setJobStatus(jb.build());

								PhotoPayload.Builder pp=PhotoPayload.newBuilder();
								pp.setUuid(uuid);
								PhotoPayload.Builder newPhotoBldr = PhotoPayload.newBuilder(pp.build());
								pb.setPhotoPayload(newPhotoBldr);
								rb.setBody(pb.build());
								// check if we can re-use the same method in resourceutil
								rb.setHeader(ResourceUtil.buildHeaderResponse(request
										.getHeader().getRoutingId(),
										PokeStatus.SUCCESS, message, request
										.getHeader().getOriginator(),
										request.getHeader().getTag(), ResponseFlag.success));

								reply = rb.build();
								return reply;
							}
						} 
						else if(!request.getHeader().getReplica().equalsIgnoreCase("broadcast") || !request.getHeader().hasReplica()
								|| !request.getHeader().getReplica().equalsIgnoreCase("broadcastReply"))
						{
							List<Integer> serverIds = ShardingManager.getInstance().getServerIds();
							int myID = Server.getMyId();
							int prev = 1000; 
							int next = 1000;
							ChannelFuture secondary1 = null,secondary2 = null;
							boolean sec1good = false, sec2good = false;
							logger.info("my ID :" + myID);	
							logger.info("size : " + serverIds.size());

							List<Integer> activeNodes = new ArrayList<Integer>();
							for(int count=0 ; count < serverIds.size(); count ++){
								if(serverIds.get(count) != leaderId){
									logger.info("server added: " + serverIds.get(count));
									activeNodes.add(serverIds.get(count));
								}
							}

							logger.info("active node size:" + activeNodes.size());
							for(int i=0; i<activeNodes.size(); i++)
							{
								logger.info("serverIds : " + activeNodes.get(i) );		
								if(myID == activeNodes.get(i)){

									if (i == 0){
										logger.info("i in if-prev:" + i);	
										prev = activeNodes.get(activeNodes.size() - 1);
										
									}else{
										logger.info("i in else-prev :" + i);
										prev = activeNodes.get(i-1);
										
									}
									logger.info("prev :" + prev);	
									if (activeNodes.size() == i + 1){
										logger.info("i in if-next:" + serverIds.size());
										next = activeNodes.get(0);
										
									}else {
										logger.info("i in if-next:" + serverIds.size());
										next = activeNodes.get(i + 1);
										
									}
									logger.info("next :" + next);
									if(prev != 1000 && next != 1000)
										break;
								}									
							}

							Request fwdPrev = ResourceUtil.buildForwardReplicaMessage(request, prev);
							if(fwdPrev == null){
								logger.info("sec1 request is null");
							}

							Request fwdNext = ResourceUtil.buildForwardReplicaMessage(request, next);
							if(fwdNext == null){
								logger.info("sec2 request is null");
							}
							for(NodeDesc nn : configFile.getAdjacent().getAdjacentNodes().values())
							{
								if(nn.getNodeId() == prev) {
									secondary1 = createChannelAndForward(nn.getHost(), nn.getPort(), fwdPrev, key);
									// add to primary channel map
									secondary1.awaitUninterruptibly();
									sec1good = secondary1.isSuccess();
								}
								logger.info("For lloooooopppp ******** checking to which node it should be replicated : " +nn.getNodeId());

								if(nn.getNodeId() == next){
									logger.info("sending the request to replicaaaaa--------> "+next);
									secondary2 = createChannelAndForward(nn.getHost(), nn.getPort(), fwdNext, key);
									// add to primary channel map
									secondary2.awaitUninterruptibly();
									sec2good = secondary2.isSuccess();

									logger.info("result of forwarding to replicaaaaaaa ------ > "+sec2good);
								}
								if(secondary1!=null && secondary2!=null)
									break;
							}
							if(sec1good && sec2good){
								MongoStorage.addReplicas(key, myID, prev, next);
								logger.info("added to mongo replicaaaaaaaa -------- ");
								uuid = MongoStorage.addFile(key, title, image, creationDate, image.length);
							}
							else{
								logger.error("Replication Failed!!");
							}
							if(uuid == null)
							{
								logger.error("Request is not handled!");
							}
							else {
								logger.info("***************Stored to mongodb of **************");
								// check if it is stored in primary. if so set flag. once you get secondary reponses, check flag and return reply accordingly
								checkStoredInPrimary = true;
								
								
								//=================== Response from Primary
								logger.info("***************Stored to mongodb of secondary node**************");
								// build response
								message = "Successfully stored to Secondary MongoDB";
								Request.Builder rb = Request.newBuilder();
								Payload.Builder pb = Payload.newBuilder();

								JobStatus.Builder jb = JobStatus.newBuilder();
								jb.setStatus(PokeStatus.SUCCESS);
								jb.setJobId(jobOp.getJobId());
								jb.setJobState(JobDesc.JobCode.JOBRECEIVED);

								pb.setJobStatus(jb.build());

								PhotoPayload.Builder pp=PhotoPayload.newBuilder();
								pp.setUuid(uuid);
								PhotoPayload.Builder newPhotoBldr = PhotoPayload.newBuilder(pp.build());
								pb.setPhotoPayload(newPhotoBldr);
								rb.setBody(pb.build());
								// check if we can re-use the same method in resourceutil
								rb.setHeader(ResourceUtil.buildHeaderResponse(request
										.getHeader().getRoutingId(),
										PokeStatus.SUCCESS, message, request
										.getHeader().getOriginator(),
										request.getHeader().getTag(), ResponseFlag.success));

								reply = rb.build();
								return reply;
								
							}
						}
					}
				}
				else if (getimage.equals(jobOp.getData().getNameSpace()) || requestType == read)
				{
					String key = imagePayload.getUuid();
					logger.info("unique key: "+key);

					BasicDBObject getImage = MongoStorage.getFileByfId(key);
					if(getImage == null)
					{
						logger.info("Image is not found! ");
					}
					else {
						message = "image details attached";
						String imgName = getImage.getString("name");
						String uniqueId = getImage.getString("_id");
						byte[] imgFile = (byte[])getImage.get("image");

						Request.Builder rb = Request.newBuilder();
						Payload.Builder pb = Payload.newBuilder();	

						JobStatus.Builder jb = JobStatus.newBuilder();
						jb.setStatus(PokeStatus.SUCCESS);
						jb.setJobId(jobOp.getJobId());
						jb.setJobState(JobDesc.JobCode.JOBRECEIVED);

						pb.setJobStatus(jb.build());

						PhotoPayload.Builder ppb = PhotoPayload.newBuilder();
						ByteString bs=com.google.protobuf.ByteString.copyFrom(imgFile);
						logger.info("getting the data length as : "+bs.size());
						ppb.setData(bs);
						ppb.setName(imgName);
						ppb.setUuid(uniqueId);
						PhotoPayload.Builder newPhotoBldr = PhotoPayload.newBuilder(ppb.build());
						pb.setPhotoPayload(newPhotoBldr);

						rb.setBody(pb.build());
						logger.info("message" + message);
						rb.setHeader(ResourceUtil.buildHeaderResponse(request
								.getHeader().getRoutingId(),
								PokeStatus.SUCCESS, message, request
								.getHeader().getOriginator(),
								request.getHeader().getTag(), ResponseFlag.success));
						reply = rb.build();
						return reply;
					}
				}
				else if (deleteimage.equals(jobOp.getData().getNameSpace()) || requestType == delete){

					String uniKey = imagePayload.getUuid();
					logger.info("unique key: "+uniKey);

					if (request.getHeader().getReplica().equalsIgnoreCase("broadcast")){
						logger.info("uuid: " + imagePayload.getUuid());
						MongoStorage.deleteFile(uniKey);
						logger.info("deleted from secondary replica");
					}
					else
					{
						ReplicaDomain replicaData = MongoStorage.getReplicaById(uniKey);
						int primary = replicaData.getPrimaryNode();
						int secondary1 = replicaData.getSecondaryNode1();
						int secondary2 = replicaData.getSecondaryNode2();
						logger.info("primary -" + primary + ", secondary1- "+ secondary1 + "secondary2 -" + secondary2 );

						if(Server.getMyId() == primary){
							logger.info("inside primary node : " + Server.getMyId());
							Request fwdSec1 = ResourceUtil.buildForwardReplicaMessage(request, secondary1);
							Request fwdSec2 = ResourceUtil.buildForwardReplicaMessage(request, secondary2);
							ChannelFuture sec1 = null, sec2 = null;
							Boolean secResult1 = false, secResult2 = false;
							for(NodeDesc nn : configFile.getAdjacent().getAdjacentNodes().values())
							{
								if(nn.getNodeId() == secondary1) {
									sec1 = createChannelAndForward(nn.getHost(), nn.getPort(), fwdSec1, uniKey);
									sec1.awaitUninterruptibly();
									secResult1 = sec1.isSuccess();
									
								}

								if(nn.getNodeId() == secondary2){
									sec2 = createChannelAndForward(nn.getHost(), nn.getPort(), fwdSec2, uniKey);
									sec2.awaitUninterruptibly();
									secResult2 = sec2.isSuccess();
								}
								if(sec1 !=null && sec2 != null)
									break;
							}
							if(secResult1 && secResult2){
								//							if(secResult2){
								MongoStorage.deleteReplica(uniKey);
							}
						}
						else if(Server.getMyId() == secondary1 || Server.getMyId() == secondary2)
						{
							logger.info("inside secondary node : " + Server.getMyId());
							logger.info("delete from secondary");
						}
						MongoStorage.deleteFile(uniKey);

						Request.Builder rb = Request.newBuilder();
						Payload.Builder pb = Payload.newBuilder();

						JobStatus.Builder jb = JobStatus.newBuilder();
						jb.setStatus(PokeStatus.SUCCESS);
						jb.setJobId(jobOp.getJobId());
						jb.setJobState(JobDesc.JobCode.JOBRECEIVED);

						pb.setJobStatus(jb.build());

						message = "image with "+uniKey+" is removed.";
						rb.setBody(pb.build());
						logger.info("message" + message);
						rb.setHeader(ResourceUtil.buildHeaderResponse(request
								.getHeader().getRoutingId(),
								PokeStatus.SUCCESS, message, request
								.getHeader().getOriginator(),
								request.getHeader().getTag(), ResponseFlag.success));
						reply = rb.build();

						return reply;
					}
				}
			}
		}
		return reply;
	}

	public static Map<String, Channel> getChMap()
	{
		return chMap;
	}

	public int get(Object key) {
		if (shardedNodes.isEmpty()) {
			return 0;
		}
		int hash = hf.hashLong((Integer)key).asInt();
		if (!shardedNodes.containsKey(hash)) {
			SortedMap<Integer, Integer> tailMap =
					shardedNodes.tailMap(hash);
			hash = tailMap.isEmpty() ?
					shardedNodes.firstKey() : tailMap.firstKey();
		}
		return shardedNodes.get(hash);
	}

	public static void addToChMap(String jobId, Channel conn) {
		chMap.put(jobId, conn);
	} 

	public ChannelFuture createChannelAndForward(String destHost, int destPort, Request forward, String key)
	{
		ChannelFuture fut = null;
		InetSocketAddress sa = new InetSocketAddress(destHost,
				destPort);
		Channel ch = connectToPublic(sa);
		if (ch != null && ch.isOpen() && ch.isWritable())
		{
			fut = ch.writeAndFlush(forward);
		}
		return fut;
	}

}
