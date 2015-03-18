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
package poke.server.resources;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.server.conf.ServerConf;
import eye.Comm.Header;
import eye.Comm.Header.Routing;
import eye.Comm.JobDesc;
import eye.Comm.JobOperation;
import eye.Comm.JobStatus;
import eye.Comm.NameValueSet;
import eye.Comm.Payload;
import eye.Comm.PhotoHeader;
import eye.Comm.PhotoHeader.ResponseFlag;
import eye.Comm.PhotoPayload;
import eye.Comm.PokeStatus;
import eye.Comm.Request;
import eye.Comm.Request.Builder;
import eye.Comm.RoutingPath;

public class ResourceUtil {

	/**
	 * Build a forwarding request message. Note this will return null if the
	 * server has already seen the request.
	 * 
	 * @param req
	 *            The request to forward
	 * @param cfg
	 *            The server's configuration
	 * @return The request with this server added to the routing path or null
	 */
	public static Request buildForwardMessage(Request req, ServerConf cfg) {

		List<RoutingPath> paths = req.getHeader().getPathList();
		if (paths != null) {
			// if this server has already seen this message return null
			for (RoutingPath rp : paths) {
				if (cfg.getNodeId() == rp.getNodeId())
					return null;
			}
		}

		Request.Builder bldr = Request.newBuilder(req);
		Header.Builder hbldr = bldr.getHeaderBuilder();
		RoutingPath.Builder rpb = RoutingPath.newBuilder();
		rpb.setNodeId(cfg.getNodeId());
		rpb.setTime(System.currentTimeMillis());
//		hbldr.addPath(rpb.build());

		return bldr.build();
	}
	
	public static Request buildMessageNewWithJobId(Request req, String jobId) {
		Request modReq = null;
		
		Request.Builder bldr = Request.newBuilder(req);
		Payload.Builder pbldr = bldr.getBodyBuilder();
		JobOperation.Builder jb = pbldr.getJobOpBuilder();
		JobDesc.Builder jd = jb.getDataBuilder();
		jd.setJobId(jobId);
		jb.setData(jd.build());
		pbldr.setJobOp(jb.build());
		modReq = bldr.build();

		return modReq;
	}
	
	
	public static Request buildForwardAddMessage(Request req, int node, String uId) {
		Request modReq = null;
		Logger logger = LoggerFactory.getLogger("server");
		List<RoutingPath> paths = req.getHeader().getPathList();
		if (paths != null) {
			// if this server has already seen this message return null
			for (RoutingPath rp : paths) {
				if (node == rp.getNodeId())
					return null;
			}
		}
		
		Request.Builder bldr = Request.newBuilder(req);
		Payload.Builder pbldr = bldr.getBodyBuilder();
		Header.Builder hbldr = bldr.getHeaderBuilder();
		RoutingPath.Builder rpb = RoutingPath.newBuilder();
		rpb.setNodeId(node);
		rpb.setTime(System.currentTimeMillis());
		hbldr.addPath(rpb.build());
	
		logger.info("******+++++++++++*******================");
		logger.info("bldr===== >"+bldr.getBody().getJobOp().getData().getOptions());
		
		PhotoPayload.Builder photoBuilder = pbldr.getPhotoPayloadBuilder();
		photoBuilder.setUuid(uId);
		PhotoPayload.Builder newPhotoBldr = PhotoPayload.newBuilder(photoBuilder.build());
		pbldr.setPhotoPayload(newPhotoBldr);

		modReq = bldr.build();
//		logger.info("******+++++++++++*******================");
//		logger.info("******+++++++++++*******================");
//		logger.info("******+++++++++++*******================");
//		logger.info("bldr===== >"+modReq.getBody().getJobOp().getData().getOptions());

		return modReq;
	}
	
	public static Request buildForwardReplicaMessage(Request req, int node) {
		Request modReq = null;
		Logger logger = LoggerFactory.getLogger("server");
		List<RoutingPath> paths = req.getHeader().getPathList();
		if (paths != null) {
			// if this server has already seen this message return null
			for (RoutingPath rp : paths) {
				if (node == rp.getNodeId())
					return null;
			}
		}
		
		Request.Builder bldr = Request.newBuilder(req);
		Header.Builder hbldr = bldr.getHeaderBuilder();
		RoutingPath.Builder rpb = RoutingPath.newBuilder();
		rpb.setNodeId(node);
		rpb.setTime(System.currentTimeMillis());
		
		hbldr.addPath(rpb.build());
		hbldr.setReplica("broadcast");
		logger.info("******+++++++++++*******================");
		logger.info("bldr===== >"+bldr.getBody().getJobOp().getData().getOptions());
		
		modReq = bldr.build();
		if (modReq == null){
			logger.info("modreq is null");
		}
		logger.info("mod bldr===== >"+modReq.getBody().getJobOp().getData().getOptions());

		return modReq;
	}

	/**
	 * build the response header from a request
	 * 
	 * @param reqHeader
	 * @param status
	 * @param statusMsg
	 * @return
	 */
	public static Header buildHeaderFrom(Header reqHeader, PokeStatus status, String statusMsg) {
		return buildHeader(reqHeader.getRoutingId(), status, statusMsg, reqHeader.getOriginator(), reqHeader.getTag());
	}
	
	public static Header buildHeader(Routing path, PokeStatus status, String msg, int fromNode, String tag) {
		Header.Builder bldr = Header.newBuilder();
		bldr.setOriginator(fromNode);
		bldr.setRoutingId(path);
		bldr.setTag(tag);
		bldr.setReplyCode(status);

		if (msg != null)
			bldr.setReplyMsg(msg);

		bldr.setTime(System.currentTimeMillis());

		return bldr.build();
	}

	
	public static Header buildHeaderResponse(Routing path, PokeStatus status, String msg, int fromNode, String tag, ResponseFlag resplonseFlag) {
		Header.Builder bldr = Header.newBuilder();
		bldr.setOriginator(fromNode);
		bldr.setRoutingId(path);
		bldr.setTag(tag);
		bldr.setReplyCode(status);
		PhotoHeader.Builder ph=PhotoHeader.newBuilder();
		ph.setResponseFlag(resplonseFlag);
		bldr.setPhotoHeader(ph.build());

		if (msg != null)
			bldr.setReplyMsg(msg);

		bldr.setTime(System.currentTimeMillis());

		return bldr.build();
	}
	
	public static Header buildHeaderReplicaResponse(Routing path, PokeStatus status, String msg, int fromNode, String tag, ResponseFlag resplonseFlag, String replica) {
		Header.Builder bldr = Header.newBuilder();
	
		bldr.setOriginator(fromNode);
		bldr.setRoutingId(path);
		bldr.setTag(tag);
		bldr.setReplyCode(status);
		PhotoHeader.Builder ph=PhotoHeader.newBuilder();
		ph.setResponseFlag(resplonseFlag);
		bldr.setPhotoHeader(ph.build());
		bldr.setReplica("broadcastReply");

		if (msg != null)
			bldr.setReplyMsg(msg);

		bldr.setTime(System.currentTimeMillis());

		return bldr.build();
	}

	public static Request buildError(Header reqHeader, PokeStatus status, String statusMsg) {
		Request.Builder bldr = Request.newBuilder();
		Header hdr = buildHeaderFrom(reqHeader, status, statusMsg);
		bldr.setHeader(hdr);

		// TODO add logging

		return bldr.build();
	}
	
	
	public static Request buildBroadcastRequest(Request request, String host) {
		// TODO Auto-generated method stub
		Request.Builder bldr = Request.newBuilder(request);
		Header.Builder hbldr = bldr.getHeaderBuilder();
		PhotoHeader.Builder photohbldr = hbldr.getPhotoHeaderBuilder();
		photohbldr.setEntryNode(host);
		hbldr.setPhotoHeader(photohbldr.build());
		
		return bldr.build();
	}
}
