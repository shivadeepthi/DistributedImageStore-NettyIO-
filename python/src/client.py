import comm_pb2
import socket               
import time
import struct
import datetime
import base64
from ctypes import c_longlong as ll

def buildPing(tag, number):

    r = comm_pb2.Request()

    r.body.ping.tag = str(tag)
    r.body.ping.number = number
    
    
    r.header.originator = 1
    # r.header.originator = 1
    r.header.tag = str(tag + number + int(round(time.time() * 1000)))
    r.header.routing_id = comm_pb2.Header.PING
    r.header.toNode = 3
    
    msg = r.SerializeToString()
    return msg

def buildDeleteImage(ownerId, uuid):
    
    jobId = str(int(round(time.time() * 1000)))
    
    r = comm_pb2.Request()
    
    r.body.job_op.job_id = jobId
    r.body.job_op.action = comm_pb2.JobOperation.ADDJOB
    
    r.body.job_op.data.name_space = "deleteImages"
    r.body.job_op.data.owner_id = ownerId
    r.body.job_op.data.job_id = jobId
    r.body.job_op.data.status = comm_pb2.JobDesc.JOBQUEUED
    
    r.body.job_op.data.options.node_type = comm_pb2.NameValueSet.NODE
    r.body.job_op.data.options.name = "operation"
    r.body.job_op.data.options.value = "deleteImageJob"
    
    r.body.photoPayload.uuid=uuid
    
    r.header.photoHeader.requestType = comm_pb2.PhotoHeader.delete
    r.header.originator = 1
    r.header.routing_id = comm_pb2.Header.JOBS
    r.header.toNode = 3
    
    msg = r.SerializeToString()
    return msg

def buildListImage(ownerId, uuid):

    jobId = str(int(round(time.time() * 1000)))
    
    r = comm_pb2.Request()
    
    r.body.job_op.job_id = jobId
    r.body.job_op.action = comm_pb2.JobOperation.ADDJOB
    
    r.body.job_op.data.name_space = "listImages"
    r.body.job_op.data.owner_id = ownerId
    r.body.job_op.data.job_id = jobId
    r.body.job_op.data.status = comm_pb2.JobDesc.JOBQUEUED
    
    r.body.job_op.data.options.node_type = comm_pb2.NameValueSet.NODE
    r.body.job_op.data.options.name = "operation"
    r.body.job_op.data.options.value = "listImageJob"
    
    r.body.photoPayload.uuid=uuid
    
    r.header.photoHeader.requestType = comm_pb2.PhotoHeader.read
    r.header.originator = 1
    r.header.routing_id = comm_pb2.Header.JOBS
    r.header.toNode = 3
    
    msg = r.SerializeToString()
    return msg

def buildImageJob(myTitle, myImage, ownerId):

    jobId = str(int(round(time.time() * 1000)))
    r = comm_pb2.Request()    

    r.body.job_op.job_id = jobId
    r.body.job_op.action = comm_pb2.JobOperation.ADDJOB
    
    r.body.job_op.data.name_space = "addimage"
    r.body.job_op.data.owner_id = ownerId
    r.body.job_op.data.job_id = jobId
    r.body.job_op.data.status = comm_pb2.JobDesc.JOBQUEUED
    
    r.body.job_op.data.options.node_type = comm_pb2.NameValueSet.NODE
    r.body.job_op.data.options.name = "operation"
    r.body.job_op.data.options.value = "addImageJob"
    
    r.body.photoPayload.name=myTitle
    r.body.photoPayload.data=myImage
    
    r.header.originator = 1
    r.header.routing_id = comm_pb2.Header.JOBS
    r.header.toNode = 3
    r.header.photoHeader.requestType = comm_pb2.PhotoHeader.write
    r.header.photoHeader.contentLength = len(myImage)
    
    msg = r.SerializeToString()
    return msg

def sendMsg(msg_out, port, host):
    s = socket.socket()         
    s.connect((host, port))
    
    msg_len = struct.pack('>L', len(msg_out))
    s.sendall(msg_len + msg_out)
    len_buf = receiveMsg(s, 4)
    msg_in_len = struct.unpack('>L', len_buf)[0]
    msg_in = receiveMsg(s, msg_in_len)
    
    r = comm_pb2.Request()
    r.ParseFromString(msg_in)
    s.close
    return r
def receiveMsg(socket, n):
    buf = ''
    while n > 0:        
        data = socket.recv(n)                  
        if data == '':
            raise RuntimeError('data not received!')
        buf += data
        n -= len(data)
    return buf  


def getBroadcastMsg(port):
    # listen for the broadcast from the leader"
          
    sock = socket.socket(socket.AF_INET,  # Internet
                        socket.SOCK_DGRAM)  # UDP
   
    sock.bind(('', port))
   
    data = sock.recv(1024)  # buffer size is 1024 bytes
    return data

if __name__ == '__main__':
    msg = buildPing(1, 2)
    
    host = raw_input("IP:")
    port = raw_input("Port:")

    port = int(port)
    whoAmI = 1;
    
    while True:
        input = raw_input("\nPlease select your desirable action:\n0.Quit\n1.Upload Image\n2.Retrieve an image\n3.Delete an Image\n")
        if input == "1":
            title = raw_input("Name:")
            path = raw_input("Absolute Path:")
            # file_path =  path + title
            print path
            with open(path, "rb") as imageFile:
                imagestr = base64.b64encode(imageFile.read())
            # createdDate = datetime.datetime.now().date()
            # stringDate = createdDate.strftime('%m/%d/%Y')
            # createdDate = int(datetime.datetime.now().strftime("%s")) * 1000
            # createdDate = time.mktime(datetime.datetime.now().timetuple()) * 1000
            imageJob = buildImageJob(title, imagestr, whoAmI)
            result = sendMsg(imageJob,  port, host)
            print result.body.photoPayload
            print result.header.photoHeader
        if input == "2":
            uniqueId = raw_input("Unique Id : ")
            listImageJob = buildListImage(whoAmI, uniqueId)
            result = sendMsg(listImageJob,  port, host)
            print result.body.photoPayload.uuid
            print result.body.photoPayload.data
            print result.body.photoPayload.name
            print result.header.reply_msg
        if input == "3":
            uniqueId = raw_input("Unique Id : ")
            deleteImageJob = buildDeleteImage(whoAmI, uniqueId)
            result = sendMsg(deleteImageJob,  port, host)
            print result.header.reply_msg

        if input == "0":
            print("Thanks for using! See you soon ...")
            break


