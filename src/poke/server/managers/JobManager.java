/*
 * copyright 2014, gash
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
package poke.server.managers;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.resources.JobResource;
import poke.server.conf.NodeDesc;
import poke.server.conf.ServerConf;
import poke.server.management.ManagementInitializer;
import eye.Comm.Header;
import eye.Comm.JobBid;
import eye.Comm.JobProposal;
import eye.Comm.Management;
import eye.Comm.Request;

/**
 * The job manager class is used by the system to hold (enqueue) jobs and can be
 * used in conjunction to the voting manager for cooperative, de-centralized job
 * scheduling. This is used to ensure leveling of the servers take into account
 * the diversity of the network.
 * 
 * @author gash
 * 
 */
public class JobManager {
	protected static Logger logger = LoggerFactory.getLogger("job");
	protected static AtomicReference<JobManager> instance = new AtomicReference<JobManager>();
	private int nodeId;
	private ServerConf configFile;
	private HashMap<String, JobBid> bidMap;
	LinkedBlockingDeque<JobBid> bidQueue;
	private String getImage = "Image";
	
	private Map<String, Channel> channelMap = new HashMap<String, Channel>();

	private static ServerConf conf;

	public static JobManager initManager(int id, ServerConf conf) {
		JobManager.conf = conf;
		instance.compareAndSet(null, new JobManager(id, conf));
		return instance.get();
	}

	public static JobManager getInstance() {
		// TODO throw exception if not initialized!
		return instance.get();
	}

	public JobManager(int nodeId, ServerConf configFile) {
		this.nodeId = nodeId;
		this.configFile = configFile;
		this.bidMap = new HashMap<String, JobBid>();
		bidQueue = new LinkedBlockingDeque<JobBid>();
	}
	
	/**
	 * a new job proposal has been sent out that I need to evaluate if I can run
	 * it
	 * 
	 * @param req
	 *            The proposal
	 */
	public void processRequest(Management mgmt) {
		JobProposal req = mgmt.getJobPropose();
		if (req == null)
			return;
		
	}

	/**@Minu
	 * a job bid for my job
	 * 
	 * @param req
	 *            The bid
	 */
	public void processRequest(JobBid req) {
//		logger.info("\n**********\nRECEIVED NEW JOB BID" + "\n\n**********");
//		logger.info("****************Bid value********" + req.getBid());
//
//		int leaderId = ElectionManager.getInstance().whoIsTheLeader();
//		if (leaderId == nodeId) {
//			if (bidMap.containsKey(req.getJobId())) {
//				return;
//			}
//			if (req.getBid() == 1) {
//				bidQueue.add(req);
//				bidMap.put(req.getJobId(), req);
//			}
//
//			if (req.getBid() == 1) {
//				Map<String, Request> requestMap = JobResource.getRequestMap();
//				Request jobOperation = requestMap.get(req.getJobId());
//				int toNodeId = (int) req.getOwnerId();
//					Request.Builder rb = Request.newBuilder(jobOperation);
//					Header.Builder hbldr = rb.getHeaderBuilder();
//					hbldr.setToNode(toNodeId);
//					hbldr.setRoutingId(Header.Routing.JOBS);
//					rb.setHeader(hbldr.build());
//					Request jobDispatched = rb.build();
//
//					NodeDesc slaveNode = configFile.getNearest().getNode(
//							toNodeId);
//
//					InetSocketAddress sa = new InetSocketAddress(
//							slaveNode.getHost(), slaveNode.getPort());
//
//					Channel ch = connectToPublic(sa);
//
//					ChannelQueue queue = QueueFactory.getInstance(ch);
//					logger.info("****************Job Request being dispatched to slave node: ********"
//							+ toNodeId);
//					queue.enqueueResponse(jobDispatched, ch);
//			}
//
//		}
	}
}
